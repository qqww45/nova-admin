/**
 * M0 字段自检（应用内版本）
 * 登录后抽样三个单据 + 逐个验证分录字段 + 自动拼装可用字段集 + 兜底逐套试。
 * 报告可直接复制回传；拼装结果可一键写入本机映射。
 */
import type {
  ProbeEntityResult,
  ProbeFieldCheck,
  ProbeFieldSetResult,
  ProbeMeta,
  ProbeReport
} from '../../shared/types'
import { getKingdee } from './client'
import {
  ENTRY_FIELD_CHECKS,
  buildLineCandidates,
  composeLineKeys
} from './lineKeys'
import { loadOutstockMapping, userMappingPath } from '../mappingStore'
import { probeFormMeta, probeBillFields, suggestPoFields } from './metaProbe'
import { asRow, cellScalar, cellTypeName } from './cells'

const ENTITIES: Array<{ name: string; label: string; formId: string; fieldKeys: string }> = [
  { name: 'customer', label: '客户', formId: 'BD_Customer', fieldKeys: 'FCUSTID,FNumber,FName' },
  {
    name: 'sale_order',
    label: '销售订单',
    formId: 'SAL_SaleOrder',
    fieldKeys: 'FID,FBillNo,FDate'
  },
  { name: 'outbound', label: '销售出库单', formId: 'SAL_OUTSTOCK', fieldKeys: 'FID,FBillNo,FDate' }
]

function errText(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

function toSample(row: unknown[] | undefined): string[] | undefined {
  if (!row) return undefined
  return row.map((v) => cellScalar(v))
}

function toTypes(row: unknown[] | undefined): string[] | undefined {
  if (!row) return undefined
  return row.map((v) => cellTypeName(v))
}

function toByField(fieldKeys: string, row: unknown[] | undefined) {
  if (!row) return undefined
  const fields = fieldKeys.split(',').map((t) => t.trim())
  return fields.map((field, i) => ({
    field,
    type: cellTypeName(row[i]),
    value: cellScalar(row[i])
  }))
}

async function probeField(
  client: ReturnType<typeof getKingdee>,
  field: string
): Promise<ProbeFieldCheck> {
  try {
    await client.executeBillQuery({
      FormId: 'SAL_OUTSTOCK',
      FieldKeys: field,
      StartRow: 0,
      Limit: 1
    })
    return { field, ok: true }
  } catch (err) {
    return { field, ok: false, error: errText(err) }
  }
}

export async function runM0Probe(): Promise<ProbeReport> {
  const client = getKingdee()
  const finishedAt = new Date().toISOString()

  const entities: ProbeEntityResult[] = []
  for (const e of ENTITIES) {
    try {
      const rows = await client.executeBillQuery({
        FormId: e.formId,
        FieldKeys: e.fieldKeys,
        StartRow: 0,
        Limit: 3
      })
      entities.push({
        name: e.name,
        label: e.label,
        formId: e.formId,
        ok: true,
        rowCount: rows.length,
        // 金蝶无权限时多返回空数组而非报错，这里只能提示而不是判失败
        message: rows.length ? '' : '未查到数据（可能暂无数据，或该账号无此单据查看权限）',
        sample: toSample(asRow(rows[0]))
      })
    } catch (err) {
      entities.push({
        name: e.name,
        label: e.label,
        formId: e.formId,
        ok: false,
        rowCount: 0,
        message: errText(err)
      })
    }
  }

  const mapping = loadOutstockMapping()

  // 1) 逐字段验证：确定这套账套里到底哪些分录字段存在
  const fieldChecks: ProbeFieldCheck[] = []
  const validFields = new Set<string>()
  for (const field of ENTRY_FIELD_CHECKS) {
    const check = await probeField(client, field)
    fieldChecks.push(check)
    if (check.ok) validFields.add(field)
  }

  const lineFieldSets: ProbeFieldSetResult[] = []
  let suggestedLineFieldKeys: string | undefined

  // 2) 用验证过的字段自动拼装，并实测一次
  const composed = composeLineKeys(validFields)
  if (composed) {
    try {
      const rows = await client.executeBillQuery({
        FormId: 'SAL_OUTSTOCK',
        FieldKeys: composed,
        StartRow: 0,
        Limit: 1
      })
      const row = rows.length ? asRow(rows[0]) : undefined
      lineFieldSets.push({
        index: 0,
        fieldKeys: `${composed}（自动拼装）`,
        ok: true,
        rowCount: rows.length,
        sample: toSample(row),
        sampleTypes: toTypes(row),
        sampleByField: toByField(composed, row)
      })
      suggestedLineFieldKeys = composed
    } catch (err) {
      lineFieldSets.push({
        index: 0,
        fieldKeys: `${composed}（自动拼装）`,
        ok: false,
        error: errText(err)
      })
    }
  }

  // 3) 自动拼装失败时，退回静态候选逐套试（能跑通且数值列多者优先）
  if (!suggestedLineFieldKeys) {
    const candidates = buildLineCandidates(mapping)
    let bestNumeric = -1
    for (let index = 0; index < candidates.length; index++) {
      const fieldKeys = candidates[index]
      try {
        const rows = await client.executeBillQuery({
          FormId: 'SAL_OUTSTOCK',
          FieldKeys: fieldKeys,
          StartRow: 0,
          Limit: 1
        })
        const row = rows.length ? asRow(rows[0]) : undefined
        lineFieldSets.push({
          index,
          fieldKeys,
          ok: true,
          rowCount: rows.length,
          sample: toSample(row),
          sampleTypes: toTypes(row),
          sampleByField: toByField(fieldKeys, row)
        })
        const numericCount = (row || []).filter((v) => typeof v === 'number').length
        if (!suggestedLineFieldKeys || numericCount > bestNumeric) {
          suggestedLineFieldKeys = fieldKeys
          bestNumeric = numericCount
        }
        if (numericCount >= 3) break
      } catch (err) {
        lineFieldSets.push({ index, fieldKeys, ok: false, error: errText(err) })
      }
    }
  }

  const okLineFields = fieldChecks.filter((c) => c.ok).length

  // 4) 元数据探测：直接把字段名 + 中文标题拉回来（替代 BOS 设计器）
  let meta: ProbeMeta | undefined
  try {
    const saleOrder = await probeFormMeta('SAL_SaleOrder')
    const outstock = await probeFormMeta('SAL_OUTSTOCK')
    // 元数据服务不可用时（老版本常见），改用 View 读一张真实单据的全部字段值
    const billFields =
      !saleOrder.total && !outstock.total
        ? {
            saleOrder: await probeBillFields('SAL_SaleOrder'),
            outstock: await probeBillFields('SAL_OUTSTOCK')
          }
        : undefined

    meta = {
      saleOrder,
      outstock,
      poCandidates: suggestPoFields([...(saleOrder.fields || []), ...(outstock.fields || [])]),
      billFields
    }
  } catch {
    /* 元数据服务不可用不影响其它自检项 */
  }

  return {
    ok: entities.every((e) => e.ok) && Boolean(suggestedLineFieldKeys),
    loginMessage: suggestedLineFieldKeys
      ? `登录成功；表头字段可用，分录验证通过 ${okLineFields}/${fieldChecks.length} 个`
      : '登录成功，但未能拼出可用的分录字段集（见逐字段验证结果）',
    entities,
    fieldChecks,
    meta,
    lineFieldSets,
    suggestedLineFieldKeys,
    customerPoField: (mapping.customerPoField || '').trim(),
    mappingPath: userMappingPath(),
    finishedAt
  }
}
