import { getDb } from '../db'
import { getPublicConfig } from '../configStore'
import type { StatementDTO, StatementLine, StatementSheet, MonthFooter } from '../../shared/types'

function monthKeys(fromMonth: string, toMonth: string): string[] {
  const keys: string[] = []
  const [fy, fm] = fromMonth.split('-').map(Number)
  const [ty, tm] = toMonth.split('-').map(Number)
  let y = fy
  let m = fm
  while (y < ty || (y === ty && m <= tm)) {
    keys.push(`${y}-${String(m).padStart(2, '0')}`)
    m += 1
    if (m > 12) {
      m = 1
      y += 1
    }
  }
  return keys
}

function monthLabel(key: string) {
  const [y, m] = key.split('-')
  const yy = y.slice(2)
  return `${Number(yy)}年${Number(m)}月份对账月份`
}

function sheetName(key: string) {
  return `${Number(key.split('-')[1])}月`
}

function centsToYuan(cents: number) {
  return Math.round(cents) / 100
}

export function buildStatement(customerFid: number, fromMonth: string, toMonth: string): StatementDTO {
  const cfg = getPublicConfig()
  const db = getDb()
  const customer = db
    .prepare(`SELECT fid, fname, address FROM customer WHERE fid = ?`)
    .get(customerFid) as { fid: number; fname: string; address?: string } | undefined

  const keys = monthKeys(fromMonth, toMonth)
  const sheets: StatementSheet[] = []
  const cumulative: MonthFooter[] = []

  for (const key of keys) {
    const start = `${key}-01`
    const [y, m] = key.split('-').map(Number)
    const endDate = new Date(y, m, 0)
    const end = `${key}-${String(endDate.getDate()).padStart(2, '0')}`

    const autoRows = db
      .prepare(
        `
      SELECT o.fdate as line_date, o.fbillno as delivery_no,
             l.customer_po as purchase_order_no, l.src_order_no as sales_order_no,
             l.material_number, l.material_name, l.unit,
             l.qty_milli, l.price_cents, l.amount_cents, l.remark, l.remark2
      FROM outbound o
      JOIN outbound_line l ON l.outbound_fid = o.fid
      WHERE o.customer_fid = ?
        AND o.cancelled = 0
        AND o.status = 'C'
        AND o.fdate >= ? AND o.fdate <= ?
      ORDER BY o.fdate, o.fbillno, l.id
    `
      )
      .all(customerFid, start, end) as Array<Record<string, unknown>>

    const extraRows = db
      .prepare(
        `
      SELECT line_date, delivery_no, purchase_order_no, sales_order_no,
             material_number, material_name, unit,
             qty_milli, price_cents, amount_cents, remark, remark2
      FROM statement_line_extra
      WHERE customer_fid = ? AND month_key = ?
      ORDER BY id
    `
      )
      .all(customerFid, key) as Array<Record<string, unknown>>

    const mapRow = (r: Record<string, unknown>, source: 'kingdee' | 'manual'): StatementLine => ({
      date: String(r.line_date ?? ''),
      deliveryNo: String(r.delivery_no ?? ''),
      purchaseOrderNo: String(r.purchase_order_no ?? ''),
      salesOrderNo: String(r.sales_order_no ?? ''),
      materialNumber: String(r.material_number ?? ''),
      materialName: String(r.material_name ?? ''),
      unit: String(r.unit ?? 'pcs'),
      qty: Number(r.qty_milli ?? 0) / 1000,
      price: centsToYuan(Number(r.price_cents ?? 0)),
      amount: centsToYuan(Number(r.amount_cents ?? 0)),
      remark: String(r.remark ?? ''),
      remark2: String(r.remark2 ?? ''),
      source
    })
    const lines: StatementLine[] = [
      ...autoRows.map((r) => mapRow(r, 'kingdee')),
      ...extraRows.map((r) => mapRow(r, 'manual'))
    ]

    // head-only fallback when lines empty but outbound heads exist
    if (!lines.length) {
      const heads = db
        .prepare(
          `
        SELECT fdate, fbillno, amount_cents, status, cancelled
        FROM outbound
        WHERE customer_fid = ? AND fdate >= ? AND fdate <= ?
        ORDER BY fdate, fbillno
      `
        )
        .all(customerFid, start, end) as Array<Record<string, unknown>>
      for (const h of heads) {
        if (Number(h.cancelled) === 1) continue
        lines.push({
          date: String(h.fdate ?? ''),
          deliveryNo: String(h.fbillno ?? ''),
          purchaseOrderNo: '',
          salesOrderNo: '',
          materialNumber: '',
          materialName: '（出库头 — 分录待同步）',
          unit: '',
          qty: 0,
          price: 0,
          amount: centsToYuan(Number(h.amount_cents ?? 0)),
          remark: String(h.status ?? ''),
          source: 'kingdee'
        })
      }
    }

    const totalCents = Math.round(lines.reduce((s, l) => s + l.amount, 0) * 100)
    const settle = db
      .prepare(`SELECT paid_cents, invoiced_cents, note, source FROM month_settle WHERE customer_fid=? AND month_key=?`)
      .get(customerFid, key) as
      | { paid_cents: number; invoiced_cents: number; note?: string; source?: string }
      | undefined

    const paid = settle?.paid_cents ?? 0
    const invoiced = settle?.invoiced_cents ?? 0
    const footer: MonthFooter = {
      month: sheetName(key),
      statementAmount: centsToYuan(totalCents),
      paidAmount: centsToYuan(paid),
      unpaidAmount: centsToYuan(totalCents - paid),
      invoicedAmount: centsToYuan(invoiced),
      uninvoicedAmount: centsToYuan(totalCents - invoiced),
      note: settle?.note,
      paidSource: (settle?.source as 'manual' | 'kingdee_hint') || 'manual',
      invoicedSource: (settle?.source as 'manual' | 'kingdee_hint') || 'manual'
    }
    cumulative.push({ ...footer })

    sheets.push({
      monthLabel: monthLabel(key),
      monthKey: key,
      lines,
      totalAmount: footer.statementAmount,
      footer,
      cumulative: cumulative.map((c) => ({ ...c }))
    })
  }

  return {
    companyName: cfg.companyName,
    companyAddress: cfg.companyAddress,
    companyPhone: cfg.companyPhone,
    customerName: customer?.fname || '未知客户',
    customerAddress: customer?.address || '',
    preparerName: cfg.preparerName,
    sheets
  }
}
