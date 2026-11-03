/** 出库单字段集：主进程同步与「字段自检」共用同一套候选，避免两处维护 */

export interface OutstockMapping {
  formId?: string
  fieldKeys?: string
  lineFieldKeys?: string
  lineFieldKeysDraft?: string
  customerPoField?: string
  note?: string
}

export const OUT_BASE =
  'FID,FBillNo,FDate,FCustomerID,FCustomerID.FName,FDocumentStatus,FCancelStatus'
export const OUT_MAT = 'FMaterialId.FNumber,FMaterialId.FName'
export const OUT_UNIT = 'FUnitID.FName'
export const OUT_EXTRA = 'FSrcBillNo,FEntryNote'

/** 分录内码：不同账套叫法不同（FEntity_FEntryID / FEntryID），自检会逐个验证 */
export const ENTRY_ID_CANDIDATES = ['FEntity_FEntryID', 'FEntryID']
/** 数量：星空不同版本/行业方案字段不同 */
export const QTY_CANDIDATES = ['FRealQty', 'FSalQty', 'FQty']
export const PRICE_CANDIDATES = ['FTaxPrice', 'FPrice']
export const AMOUNT_CANDIDATES = ['FAllAmount', 'FAmount', 'FTaxAmount']
/** 销售订单号备选 */
export const SRC_CANDIDATES = ['FSrcBillNo', 'FSaleOrderID.FBillNo']
/** 物料：自检会逐个验证，拼装时取可用项 */
export const MAT_CANDIDATES = ['FMaterialId.FNumber', 'FMaterialId.FName']
/** 规格型号：物料基础资料属性为主，个别账套在分录上另有规格字段 */
export const SPEC_CANDIDATES = ['FMaterialId.FSpecification', 'FSpecification']
/** 行备注备选 */
export const NOTE_CANDIDATES = ['FEntryNote', 'FNote']

/**
 * 自检「逐字段验证 + 自动拼装」用：需要单独验证的分录级候选字段。
 * 表头 7 个字段已在表头同步中验证可用，不必重复验证。
 */
export const ENTRY_FIELD_CHECKS = [
  ...ENTRY_ID_CANDIDATES,
  'FMaterialId',
  'FMaterialId.FNumber',
  'FMaterialId.FName',
  ...SRC_CANDIDATES,
  'FSaleOrderID',
  'FEntryNote',
  'FNote',
  'FUnitID.FName',
  'FUnitId.FName',
  ...QTY_CANDIDATES,
  ...PRICE_CANDIDATES,
  ...AMOUNT_CANDIDATES,
  ...SPEC_CANDIDATES
]

/** 从「验证通过的字段集合」里按用途各挑一个，拼成完整字段集 */
export function composeLineKeys(valid: Set<string>): string | undefined {
  const pick = (...cands: string[]) => cands.find((c) => valid.has(c))
  const entryId = pick(...ENTRY_ID_CANDIDATES)
  const qty = pick(...QTY_CANDIDATES)
  const price = pick(...PRICE_CANDIDATES)
  const amount = pick(...AMOUNT_CANDIDATES)
  // 内码 + 数量 + 金额至少要有，否则拼出来的集合没有意义
  if (!entryId || (!qty && !amount)) return undefined
  const parts = [
    OUT_BASE,
    entryId,
    pick(...MAT_CANDIDATES.slice(0, 1)),
    pick(...MAT_CANDIDATES.slice(1, 2)),
    // 规格型号是"按金样板对账"的识别列，账套有就带上；没有则留空，不影响其余字段
    pick(...SPEC_CANDIDATES),
    pick('FUnitID.FName', 'FUnitId.FName'),
    qty,
    price,
    amount,
    pick(...SRC_CANDIDATES),
    pick(...NOTE_CANDIDATES)
  ].filter(Boolean)
  return parts.join(',')
}

/** 静态候选集：映射文件里的字段集失败时的兜底（自动降级逐套试） */
export const LINE_KEY_CANDIDATES = [
  // 前两条带规格型号（物料基础资料属性，绝大多数星空账套可用）；
  // 账套取不到时整套失败，继续降级到下面不含规格的既有候选，行为与旧版一致
  [OUT_BASE, 'FEntity_FEntryID', OUT_MAT, OUT_UNIT, 'FRealQty,FTaxPrice,FAllAmount', OUT_EXTRA, 'FMaterialId.FSpecification'].join(','),
  [OUT_BASE, 'FEntryID', OUT_MAT, OUT_UNIT, 'FRealQty,FTaxPrice,FAllAmount', OUT_EXTRA, 'FMaterialId.FSpecification'].join(','),
  [OUT_BASE, 'FEntity_FEntryID', OUT_MAT, OUT_UNIT, 'FRealQty,FTaxPrice,FAllAmount', OUT_EXTRA].join(','),
  [OUT_BASE, 'FEntryID', OUT_MAT, OUT_UNIT, 'FRealQty,FTaxPrice,FAllAmount', OUT_EXTRA].join(','),
  [OUT_BASE, 'FEntity_FEntryID', OUT_MAT, 'FRealQty,FTaxPrice,FAllAmount', OUT_EXTRA].join(','),
  [OUT_BASE, 'FEntity_FEntryID', OUT_MAT, OUT_UNIT, 'FSalQty,FTaxPrice,FAllAmount', OUT_EXTRA].join(','),
  [OUT_BASE, 'FEntity_FEntryID', OUT_MAT, 'FQty,FPrice,FAmount'].join(','),
  [OUT_BASE, 'FEntryID', OUT_MAT, OUT_UNIT, 'FQty,FTaxPrice,FAllAmount', OUT_EXTRA].join(','),
  [OUT_BASE, 'FEntity_FEntryID', OUT_MAT, 'FRealQty,FTaxPrice,FAllAmount'].join(',')
]

/**
 * 按优先级生成待试字段集：本机自检结果 → 服务端配置 → 草稿 → 内置候选；并补上客户采购订单号字段。
 *
 * <p>本机自检结果优先于服务端配置：那是"这台机器 + 这个账套"实测通过的字段集，
 * 服务端配置是运维在网页上填的，可能只是别的账套的复制品。
 *
 * @param mapping 本机映射
 * @param serverKeys 服务端任务清单里的字段集（可选）
 * @returns 候选字段集，按优先级
 */
export function buildLineCandidates(mapping: OutstockMapping, serverKeys?: string): string[] {
  const poField = (mapping.customerPoField || '').trim()
  const candidates: string[] = []
  const pool = [mapping.lineFieldKeys, serverKeys, mapping.lineFieldKeysDraft, ...LINE_KEY_CANDIDATES]
  for (const raw of pool) {
    let keys = (raw || '').trim()
    if (!keys) continue
    if (poField && !keys.split(',').map((t) => t.trim()).includes(poField)) {
      keys = `${keys},${poField}`
    }
    if (!candidates.includes(keys)) candidates.push(keys)
  }
  return candidates
}
