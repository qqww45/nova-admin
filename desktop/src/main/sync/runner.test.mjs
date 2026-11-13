import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const runner = readFileSync(resolve(import.meta.dirname, 'runner.ts'), 'utf8')
const uploader = readFileSync(resolve(import.meta.dirname, '../nova/uploader.ts'), 'utf8')

test('出库水位：全量/增量都走 nextModifyWatermark，不再只在 fmodify 模式落盘', () => {
  assert.match(runner, /nextModifyWatermark/)
  assert.match(runner, /last_modify_watermark:\$\{acctId\}/)
  assert.doesNotMatch(
    runner,
    /if \(decision\.mode === 'fmodify' && head\.maxModify\)/
  )
})

test('客户按账套探测 FModifyDate，增量过滤，不再写死空 FilterString', () => {
  assert.match(runner, /customer_modify_supported:\$\{acctId\}/)
  assert.match(runner, /last_customer_modify_watermark:\$\{acctId\}/)
  assert.match(runner, /enableSweep:\s*false/)
  assert.doesNotMatch(runner, /FilterString: sinceDate \? '' : ''/)
  assert.match(runner, /FormId: 'BD_Customer'/)
  assert.match(runner, /FModifyDate/)
})

test('客户/出库 upsert 用 resolveSyncAt，未变行不得盖 sync_at', () => {
  assert.match(runner, /customerContentEqual/)
  assert.match(runner, /outboundContentEqual/)
  assert.match(runner, /resolveSyncAt/)
  assert.match(runner, /headerNeedsLineRebuild/)
})

test('分录只重建内容变化的表头，不再按本轮 sync_at>=batchStartIso 整表删除', () => {
  assert.match(runner, /decideLinePull/)
  assert.doesNotMatch(
    runner,
    /DELETE FROM outbound_line WHERE outbound_fid IN \(SELECT fid FROM outbound WHERE sync_at >= \?\)/
  )
})

test('上行仍按分实体 sync_at 水位取增量，失败不得改成全量重传', () => {
  assert.match(uploader, /entitySince\(rt, 'uploadWatermarkCustomer'\)/)
  assert.match(uploader, /entitySince\(rt, 'uploadWatermarkOutbound'\)/)
  assert.match(uploader, /entitySince\(rt, 'uploadWatermarkLine'\)/)
  assert.match(uploader, /sync_at > \?/)
  assert.match(uploader, /persistEntityWatermark\('customer'/)
  assert.match(uploader, /saveNovaUploadResult\(\{ ok: false, message, finishedAt \}\)/)
  assert.doesNotMatch(uploader, /mode: 'FULL'/)
})
