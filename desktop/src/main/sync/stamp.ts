/**
 * 本地 upsert 是否盖 sync_at：只在业务内容变化时推进。
 * 上行按 sync_at > 分实体水位取增量，未变行必须保留旧时间，否则成功批次也会整库重传。
 */

function text(v: unknown): string {
  if (v == null) return ''
  return String(v)
}

function int(v: unknown): number {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

export interface CustomerContent {
  fnumber?: string | null
  fname?: string | null
  address?: string | null
  disabled?: number | null
}

export function customerContentEqual(a: CustomerContent, b: CustomerContent): boolean {
  return (
    text(a.fnumber) === text(b.fnumber) &&
    text(a.fname) === text(b.fname) &&
    text(a.address) === text(b.address) &&
    int(a.disabled) === int(b.disabled)
  )
}

export interface OutboundContent {
  fbillno?: string | null
  fdate?: string | null
  customer_fid?: number | null
  customer_name?: string | null
  status?: string | null
  cancelled?: number | null
  modify_time?: string | null
}

export function outboundContentEqual(a: OutboundContent, b: OutboundContent): boolean {
  return (
    text(a.fbillno) === text(b.fbillno) &&
    text(a.fdate).slice(0, 10) === text(b.fdate).slice(0, 10) &&
    int(a.customer_fid) === int(b.customer_fid) &&
    text(a.customer_name) === text(b.customer_name) &&
    text(a.status) === text(b.status) &&
    int(a.cancelled) === int(b.cancelled) &&
    text(a.modify_time) === text(b.modify_time)
  )
}

/** 未变且已有 sync_at → 保留；新行或历史空 stamp 才盖 now（空 stamp 否则永远不上行） */
export function resolveSyncAt(
  unchanged: boolean,
  previous: string | null | undefined,
  now: string
): string {
  if (unchanged && previous) return previous
  return now
}

/**
 * 表头内容未变则通常不重建分录；但本地没有分录时仍要拉
 * （上一轮 DELETE 之后查询失败会留下空洞，下一轮全量会被 content-equal 跳过）。
 */
export function headerNeedsLineRebuild(input: {
  unchanged: boolean
  existed: boolean
  hasLines: boolean
}): boolean {
  if (!input.unchanged) return true
  return input.existed && !input.hasLines
}
