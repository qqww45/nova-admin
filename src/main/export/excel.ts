import ExcelJS from 'exceljs'
import { join } from 'path'
import { existsSync, mkdirSync, renameSync, unlinkSync } from 'fs'
import { homedir } from 'os'
import type { StatementDTO } from '../../shared/types'
import { getPublicConfig } from '../configStore'
import { tryAcquire, release } from '../businessLock'
import { getResourcesRoot } from '../paths'

function defaultExportDir() {
  const dir = join(homedir(), 'Documents', '衡账', '导出')
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true })
  return dir
}

export async function exportStatementXlsx(dto: StatementDTO): Promise<string> {
  if (!tryAcquire('export')) throw new Error('导出或其它任务进行中')
  try {
    const cfg = getPublicConfig()
    const templatePath = join(getResourcesRoot(), 'templates', 'statement.xlsx')
    const wb = new ExcelJS.Workbook()

    if (existsSync(templatePath)) {
      await wb.xlsx.readFile(templatePath)
      // 一期：若有空壳模板则克隆首 sheet；否则自建可读结构（版式精修随模板迭代）
      while (wb.worksheets.length > 1) wb.removeWorksheet(wb.worksheets[1].id)
    }

    // 清空后按月重建（模板仅作样式参考；无模板时直接生成结构清晰的表）
    for (const ws of [...wb.worksheets]) wb.removeWorksheet(ws.id)

    for (const sheet of dto.sheets) {
      const name = sheet.footer.month.slice(0, 31) || sheet.monthKey
      const ws = wb.addWorksheet(name)
      ws.mergeCells('A1:K1')
      ws.getCell('A1').value = dto.companyName
      ws.getCell('A1').font = { size: 16, bold: true }
      ws.mergeCells('A2:K2')
      ws.getCell('A2').value = sheet.monthLabel
      ws.getCell('A3').value = '公司地址:'
      ws.getCell('B3').value = dto.companyAddress
      ws.getCell('F3').value = '客户名称：'
      ws.getCell('G3').value = dto.customerName
      ws.getCell('A4').value = '联系人电话:'
      ws.getCell('B4').value = dto.companyPhone
      ws.getCell('F4').value = '地址：'
      ws.getCell('G4').value = dto.customerAddress

      const headers = [
        '送货日期',
        '送货单号',
        '我司采购订单号',
        '我司销售订单号',
        '我司物料号',
        '产品名称',
        '单位',
        '数量',
        '单价',
        '金额',
        '备注'
      ]
      ws.addRow([])
      const headerRow = ws.addRow(headers)
      headerRow.font = { bold: true }

      for (const line of sheet.lines) {
        ws.addRow([
          line.date,
          line.deliveryNo,
          line.purchaseOrderNo,
          line.salesOrderNo,
          line.materialNumber,
          line.materialName,
          line.unit,
          line.qty,
          line.price,
          line.amount,
          line.remark || ''
        ])
      }

      ws.addRow([])
      const totalRow = ws.addRow(['', '', '', '', '', '', '', '', '合计:', sheet.totalAmount])
      totalRow.font = { bold: true }

      ws.addRow([])
      ws.addRow(['月份', '对账金额', '已付金额', '未付金额', '已开票金额', '未开票金额', '备注'])
      for (const c of sheet.cumulative) {
        ws.addRow([
          c.month,
          c.statementAmount,
          c.paidAmount,
          c.unpaidAmount,
          c.invoicedAmount,
          c.uninvoicedAmount,
          c.note || ''
        ])
      }
      ws.addRow([`制单人:`, dto.preparerName, '', '复核:'])

      ws.columns.forEach((col) => {
        col.width = 14
      })
    }

    const fileName = `${cfg.companyShortName || '公司'}---${dto.customerName}对帐单${formatStamp()}.xlsx`
    return await writeWithLockFallback(wb, defaultExportDir(), fileName)
  } finally {
    release('export')
  }
}

function formatStamp() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}`
}

function timeHms() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
}

/**
 * 先写临时文件，再改名到目标名；目标名被 Excel/WPS 占用时自动换带时刻的新文件名。
 */
async function writeWithLockFallback(
  wb: ExcelJS.Workbook,
  dir: string,
  fileName: string
): Promise<string> {
  const tmpPath = join(dir, `.${fileName}.${process.pid}-${Date.now()}.tmp.xlsx`)
  await wb.xlsx.writeFile(tmpPath)

  const altName = `${fileName.replace(/\.xlsx$/i, '')}_${timeHms()}.xlsx`
  const targets = [fileName, altName]

  for (let i = 0; i < targets.length; i++) {
    const target = join(dir, targets[i])
    try {
      renameSync(tmpPath, target)
      return target
    } catch (e) {
      const code = (e as NodeJS.ErrnoException)?.code ?? ''
      const locked = code === 'EBUSY' || code === 'EPERM' || code === 'EACCES'
      if (i === targets.length - 1) {
        try {
          unlinkSync(tmpPath)
        } catch {
          /* ignore */
        }
        if (locked) {
          throw new Error('导出失败：对账单文件正被 Excel/WPS 打开占用，请先关闭后重试')
        }
        throw e
      }
      if (!locked) {
        try {
          unlinkSync(tmpPath)
        } catch {
          /* ignore */
        }
        throw e
      }
    }
  }
  throw new Error('导出失败：无法写入目标文件')
}
