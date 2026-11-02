/**
 * 单据元数据探测：拿不到 BOS 设计器时，用金蝶 WebAPI 直接取字段名 + 中文标题。
 * 不同版本服务名不同，逐个试；拿到后即可确认数量/单价/金额/采购订单号的真实字段名。
 */
import { getKingdee } from './client'
import type { MetaField, MetaProbeResult } from '../../shared/types'

const META_SERVICES = [
  'Kingdee.BOS.WebApi.ServicesStub.MetaDataService.GetFormMetadata',
  'Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.QueryBusinessInfo',
  'Kingdee.BOS.WebApi.ServicesStub.MetaDataService.GetFormMetaData'
]

const MAX_NODES = 200000

/** 递归收集形如 { Key/FieldName, Name/Caption } 的字段定义 */
function collectKeyName(node: unknown, out: Map<string, string>, state: { nodes: number }) {
  if (!node || typeof node !== 'object' || out.size > 4000) return
  if (state.nodes++ > MAX_NODES) return

  if (Array.isArray(node)) {
    for (const child of node) collectKeyName(child, out, state)
    return
  }

  const o = node as Record<string, unknown>
  const key =
    typeof o.Key === 'string'
      ? o.Key
      : typeof o.FieldName === 'string'
        ? o.FieldName
        : typeof o.key === 'string'
          ? o.key
          : ''
  const name =
    typeof o.Name === 'string'
      ? o.Name
      : typeof o.Caption === 'string'
        ? o.Caption
        : typeof o.name === 'string'
          ? o.name
          : ''

  if (key && name && /^[A-Za-z_]/.test(key) && key.length <= 64 && !out.has(key)) {
    out.set(key, name)
  }

  for (const v of Object.values(o)) collectKeyName(v, out, state)
}

/** 字段太多，只保留对账用得到的部分（中文标题命中或自定义字段） */
const KEEP_NAME =
  /数量|单价|价格|金额|单位|物料|产品|商品|订单|备注|采购|客户|客户PO|PO|税率|含税|规格|型号/
const CUSTOM_KEY = /^F_[A-Za-z0-9]+$/

export function filterUseful(fields: MetaField[], limit = 120): MetaField[] {
  return fields
    .filter((f) => KEEP_NAME.test(f.name) || CUSTOM_KEY.test(f.key))
    .slice(0, limit)
}

export const PO_NAME_HINT = /采购订单|客户订单|客户PO|PO号|客户单号|订单号/

/** 从字段清单里猜客户采购订单号字段：优先自定义字段 + 名称命中 */
export function suggestPoFields(fields: MetaField[]): MetaField[] {
  const scored = fields
    .map((f) => {
      let score = 0
      if (PO_NAME_HINT.test(f.name)) score += 5
      if (/采购/.test(f.name)) score += 3
      if (CUSTOM_KEY.test(f.key)) score += 2
      if (/^F[A-Za-z]*Qty|Amount|Price|Date|Status/.test(f.key)) score -= 3
      return { f, score }
    })
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score)
  return scored.slice(0, 12).map((x) => x.f)
}

/** 读一张真实单据的完整内容（含自定义字段），用于在没有元数据服务时找 PO 字段 */
export interface FieldValue {
  key: string
  value: string
}

const VIEW_SERVICE = 'Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View'

function collectLeaves(node: unknown, out: Map<string, string>, state: { n: number }) {
  if (node === null || node === undefined || out.size > 3000) return
  if (state.n++ > 200000) return
  if (typeof node === 'string' || typeof node === 'number' || typeof node === 'boolean') return
  if (Array.isArray(node)) {
    for (const c of node) collectLeaves(c, out, state)
    return
  }
  if (typeof node === 'object') {
    for (const [k, v] of Object.entries(node as Record<string, unknown>)) {
      if (v === null || v === undefined) continue
      if (typeof v === 'object') {
        collectLeaves(v, out, state)
      } else if (!out.has(k)) {
        const s = String(v)
        if (s.trim()) out.set(k, s.length > 80 ? `${s.slice(0, 80)}…` : s)
      }
    }
  }
}

/** 用 View 服务取一张单据的所有字段值（含 F_ 自定义字段），返回疑似 PO 的字段 */
export async function probeBillFields(formId: string): Promise<{
  formId: string
  total: number
  custom: FieldValue[]
  poHints: FieldValue[]
  error?: string
}> {
  const client = getKingdee()
  let fid: number | undefined
  let billNo = ''
  try {
    const rows = await client.executeBillQuery({
      FormId: formId,
      FieldKeys: 'FID,FBillNo',
      StartRow: 0,
      Limit: 1
    })
    const r = rows[0]
    fid = Number(Array.isArray(r) ? r[0] : NaN)
    billNo = String(Array.isArray(r) ? (r[1] ?? '') : '')
  } catch (e) {
    return {
      formId,
      total: 0,
      custom: [],
      poHints: [],
      error: `取样例单据失败：${e instanceof Error ? e.message : String(e)}`
    }
  }

  const payloads: unknown[][] = []
  if (billNo) {
    payloads.push([{ FormId: formId, Number: billNo }])
    payloads.push([{ FormId: formId, Numbers: [billNo] }])
  }
  if (Number.isFinite(fid)) {
    payloads.push([{ FormId: formId, Ids: String(fid) }])
    payloads.push([{ FormId: formId, FID: fid }])
    payloads.push([formId, { FID: fid }])
  }

  let lastError = ''
  for (const parameters of payloads) {
    try {
      const data = await client.callService(VIEW_SERVICE, parameters)
      const leaves = new Map<string, string>()
      collectLeaves(data, leaves, { n: 0 })
      if (leaves.size < 3) {
        lastError = 'View 返回内容过少'
        continue
      }
      const all = Array.from(leaves.entries()).map(([key, value]) => ({ key, value }))
      const custom = all.filter((f) => /^F_[A-Za-z0-9]+$/.test(f.key)).slice(0, 40)
      const poHints = all
        .filter((f) => /PO|采购|Order|_No|No$/i.test(f.key) && f.value.trim())
        .slice(0, 20)
      return { formId, total: all.length, custom, poHints }
    } catch (e) {
      lastError = e instanceof Error ? e.message : String(e)
    }
  }
  return {
    formId,
    total: 0,
    custom: [],
    poHints: [],
    error: lastError || 'View 服务不可用'
  }
}

export async function probeFormMeta(formId: string): Promise<MetaProbeResult> {
  const client = getKingdee()
  let lastError = ''
  for (const service of META_SERVICES) {
    try {
      const data = await client.callService(service, [formId])
      const found = new Map<string, string>()
      collectKeyName(data, found, { nodes: 0 })
      if (!found.size) {
        lastError = `${service} 返回成功但未解析到字段`
        continue
      }
      const all = Array.from(found.entries())
        .map(([key, name]) => ({ key, name }))
        .sort((a, b) => a.key.localeCompare(b.key))
      return {
        formId,
        service,
        total: all.length,
        fields: filterUseful(all)
      }
    } catch (e) {
      lastError = e instanceof Error ? e.message : String(e)
    }
  }
  return { formId, total: 0, fields: [], error: lastError || '无可用元数据服务' }
}
