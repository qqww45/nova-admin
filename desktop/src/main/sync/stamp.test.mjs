import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  customerContentEqual,
  outboundContentEqual,
  resolveSyncAt,
  headerNeedsLineRebuild
} from './stamp.ts'

test('客户业务字段相同则视为未变，空与 null 同等', () => {
  assert.equal(
    customerContentEqual(
      { fnumber: 'C1', fname: '甲', address: null, disabled: 0 },
      { fnumber: 'C1', fname: '甲', address: '', disabled: 0 }
    ),
    true
  )
  assert.equal(
    customerContentEqual(
      { fnumber: 'C1', fname: '甲', address: '', disabled: 0 },
      { fnumber: 'C1', fname: '甲改', address: '', disabled: 0 }
    ),
    false
  )
  assert.equal(
    customerContentEqual(
      { fnumber: 'C1', fname: '甲', address: '', disabled: 0 },
      { fnumber: 'C1', fname: '甲', address: '', disabled: 1 }
    ),
    false
  )
})

test('出库表头比较含 modify_time：改分录会抬 FModifyDate，从而重建行', () => {
  const base = {
    fbillno: 'OUT1',
    fdate: '2026-11-01',
    customer_fid: 9,
    customer_name: '甲',
    status: 'C',
    cancelled: 0,
    modify_time: '2026-11-03 10:00:00'
  }
  assert.equal(outboundContentEqual(base, { ...base }), true)
  assert.equal(
    outboundContentEqual(base, { ...base, modify_time: '2026-11-06 08:00:00' }),
    false
  )
  assert.equal(outboundContentEqual(base, { ...base, fbillno: 'OUT2' }), false)
  assert.equal(
    outboundContentEqual(
      { ...base, modify_time: null },
      { ...base, modify_time: '' }
    ),
    true
  )
})

test('内容未变且已有 sync_at 则保留；新行或旧行无 sync_at 才盖当前时间', () => {
  assert.equal(resolveSyncAt(true, '2026-01-01T00:00:00.000Z', 'now'), '2026-01-01T00:00:00.000Z')
  assert.equal(resolveSyncAt(true, '', 'now'), 'now', '历史空 sync_at 需要盖一次才能被上行水位看见')
  assert.equal(resolveSyncAt(false, 'old', 'now'), 'now')
  assert.equal(resolveSyncAt(false, undefined, 'now'), 'now')
})

test('表头未变且已有分录不重建；本地无分录的旧单仍要补拉', () => {
  assert.equal(headerNeedsLineRebuild({ unchanged: false, existed: false, hasLines: false }), true)
  assert.equal(headerNeedsLineRebuild({ unchanged: true, existed: true, hasLines: true }), false)
  assert.equal(headerNeedsLineRebuild({ unchanged: true, existed: true, hasLines: false }), true)
  assert.equal(headerNeedsLineRebuild({ unchanged: true, existed: false, hasLines: false }), false)
})
