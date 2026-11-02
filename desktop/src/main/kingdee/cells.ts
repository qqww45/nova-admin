/**
 * 金蝶 ExecuteBillQuery 返回的单元格可能是标量，也可能是对象/数组
 * （如 { FNumber, FName }、{ Id, Name } 等）。这里统一安全提取，避免
 * Number(对象) → NaN、String(对象) → "[object Object]" 导致明细全是 0。
 */

const SCALAR_KEYS = ['FNumber', 'Number', 'FName', 'Name', 'Value', 'value', 'Id', 'FID']
const NUMERIC_KEYS = ['Value', 'value', 'Amount', 'Qty', 'Number', 'FNumber', 'Price']

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v)
}

export function cellScalar(v: unknown): string {
  if (v === null || v === undefined) return ''
  if (typeof v === 'string') return v
  if (typeof v === 'number' || typeof v === 'boolean') return String(v)
  if (Array.isArray(v)) return v.length ? cellScalar(v[0]) : ''
  if (isRecord(v)) {
    for (const k of SCALAR_KEYS) {
      if (typeof v[k] === 'string' || typeof v[k] === 'number') return String(v[k])
    }
    try {
      const json = JSON.stringify(v)
      return json.length > 120 ? `${json.slice(0, 120)}…` : json
    } catch {
      return ''
    }
  }
  return ''
}

export function cellNumber(v: unknown): number {
  if (typeof v === 'number') return Number.isFinite(v) ? v : 0
  if (typeof v === 'string') {
    const n = Number(v)
    return Number.isFinite(n) ? n : 0
  }
  if (Array.isArray(v)) return v.length ? cellNumber(v[0]) : 0
  if (isRecord(v)) {
    for (const k of NUMERIC_KEYS) {
      const n = Number(v[k])
      if (Number.isFinite(n)) return n
    }
  }
  return 0
}

/** 单元格类型描述，用于自检报告定位「哪个字段返回了对象」 */
export function cellTypeName(v: unknown): string {
  if (v === null || v === undefined) return 'null'
  if (Array.isArray(v)) return `array(${v.length})`
  if (typeof v === 'object') return `object{${Object.keys(v as object).slice(0, 4).join(',')}}`
  return typeof v
}

/** 取一行（可能是数组，也可能是只有一个对象的数组） */
export function asRow(raw: unknown): unknown[] {
  if (Array.isArray(raw)) return raw
  if (isRecord(raw)) return [raw]
  return []
}
