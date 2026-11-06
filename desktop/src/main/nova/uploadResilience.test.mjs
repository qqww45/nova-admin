import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  isTransientUploadError,
  safeWatermarkAfterPrefix,
  mergeWatermark,
  combineUploadResults,
  retryTransient,
  uploadWithRetry,
  TRANSIENT_RETRY_ATTEMPTS
} from './uploadResilience.ts'

test('524 / 非 JSON / 超时 / 网关错误视为瞬时，业务错误不重试', () => {
  assert.equal(isTransientUploadError(new Error('对账平台返回非 JSON（HTTP 524）：<html>')), true)
  assert.equal(isTransientUploadError(new Error('对账平台返回非 JSON（HTTP 502）：error')), true)
  assert.equal(isTransientUploadError(new Error('对账平台返回非 JSON（HTTP 503）：')), true)
  assert.equal(isTransientUploadError(new Error('对账平台返回非 JSON（HTTP 504）：')), true)
  assert.equal(isTransientUploadError(new Error('请求超时（30s）：/kd/sync/outbound-line')), true)
  assert.equal(isTransientUploadError(new Error('无法连接对账平台：fetch failed')), true)
  assert.equal(isTransientUploadError(new Error('采集器令牌已失效，请在「连接设置」里重新绑定')), false)
  assert.equal(isTransientUploadError(new Error('请求失败（HTTP 400）')), false)
  assert.equal(isTransientUploadError(new Error('规格超长')), false)
})

test('整段前缀上传完才推进水位；同秒未传完的行不能被跳过', () => {
  const rows = ['t1', 't1', 't2', 't2', 't3']
  assert.equal(safeWatermarkAfterPrefix(rows, 0), undefined)
  assert.equal(safeWatermarkAfterPrefix(rows, 1), undefined, 't1 还剩一行，不能推进')
  assert.equal(safeWatermarkAfterPrefix(rows, 2), 't1')
  assert.equal(safeWatermarkAfterPrefix(rows, 3), 't1', 't2 还剩一行，停在 t1')
  assert.equal(safeWatermarkAfterPrefix(rows, 4), 't2')
  assert.equal(safeWatermarkAfterPrefix(rows, 5), 't3')
  assert.equal(safeWatermarkAfterPrefix(['t1', null, 't1', 't2'], 3), 't1')
})

test('水位只进不退', () => {
  assert.equal(mergeWatermark('2026-01-01T00:00:00.000Z', '2026-01-02T00:00:00.000Z'), '2026-01-02T00:00:00.000Z')
  assert.equal(mergeWatermark('2026-01-02T00:00:00.000Z', '2026-01-01T00:00:00.000Z'), '2026-01-02T00:00:00.000Z')
  assert.equal(mergeWatermark(undefined, 't1'), 't1')
  assert.equal(mergeWatermark('t1', undefined), 't1')
  assert.equal(mergeWatermark(undefined, undefined), undefined)
})

test('上行计数可累加，便于半块重试合并', () => {
  assert.deepEqual(
    combineUploadResults({ received: 10, upserted: 9, skipped: 1 }, { received: 5, upserted: 5, skipped: 0 }),
    { received: 15, upserted: 14, skipped: 1 }
  )
})

test('瞬时错误有限次回退重试，成功即停；次数用尽抛最后一次错误', async () => {
  assert.equal(TRANSIENT_RETRY_ATTEMPTS, 3)
  const sleeps = []
  let attempts = 0
  const ok = await retryTransient(
    async () => {
      attempts += 1
      if (attempts < 3) throw new Error('对账平台返回非 JSON（HTTP 524）：x')
      return 'ok'
    },
    { sleep: async (ms) => sleeps.push(ms) }
  )
  assert.equal(ok, 'ok')
  assert.equal(attempts, 3)
  assert.deepEqual(sleeps, [1000, 2000])

  let fails = 0
  await assert.rejects(
    () =>
      retryTransient(
        async () => {
          fails += 1
          throw new Error('请求超时（30s）：/kd/sync/customer')
        },
        { sleep: async () => {} }
      ),
    /请求超时/
  )
  assert.equal(fails, TRANSIENT_RETRY_ATTEMPTS)
})

test('业务错误不重试、不空等', async () => {
  let attempts = 0
  await assert.rejects(
    () =>
      retryTransient(
        async () => {
          attempts += 1
          throw new Error('采集器令牌已失效，请在「连接设置」里重新绑定')
        },
        { sleep: async () => assert.fail('不应退避') }
      ),
    /令牌已失效/
  )
  assert.equal(attempts, 1)
})

test('块上传 524 后先原块重试，再对半切分；不再无限循环', async () => {
  const calls = []
  const acked = []
  const result = await uploadWithRetry(
    [1, 2, 3, 4],
    async (chunk) => {
      calls.push(chunk.slice())
      if (chunk.length >= 4) throw new Error('对账平台返回非 JSON（HTTP 524）：edge')
      return { received: chunk.length, upserted: chunk.length, skipped: 0 }
    },
    { sleep: async () => {}, onAck: (rows) => acked.push(rows.slice()) }
  )
  assert.deepEqual(result, { received: 4, upserted: 4, skipped: 0 })
  // 原块耗尽 3 次后切成 [1,2] / [3,4]
  assert.equal(calls.filter((c) => c.length === 4).length, TRANSIENT_RETRY_ATTEMPTS)
  assert.deepEqual(
    calls.filter((c) => c.length < 4),
    [
      [1, 2],
      [3, 4]
    ]
  )
  assert.deepEqual(acked, [
    [1, 2],
    [3, 4]
  ])
})

test('半块成功后后半失败，onAck 只报告已确认前缀', async () => {
  const acked = []
  await assert.rejects(
    () =>
      uploadWithRetry(
        [1, 2, 3, 4],
        async (chunk) => {
          if (chunk[0] === 1 && chunk.length <= 2) {
            return { received: chunk.length, upserted: chunk.length, skipped: 0 }
          }
          throw new Error('对账平台返回非 JSON（HTTP 524）：right')
        },
        { sleep: async () => {}, onAck: (rows) => acked.push(rows.slice()) }
      ),
    /HTTP 524/
  )
  assert.deepEqual(acked, [[1, 2]])
})

test('半块仍 524 会继续对半；前半单行耗尽即放弃，避免水位出现空洞', async () => {
  const calls = []
  await assert.rejects(
    () =>
      uploadWithRetry(
        [7, 8],
        async (chunk) => {
          calls.push(chunk.slice())
          throw new Error('对账平台返回非 JSON（HTTP 524）：still')
        },
        { sleep: async () => {} }
      ),
    /HTTP 524/
  )
  const singles = calls.filter((c) => c.length === 1)
  assert.equal(singles.length, TRANSIENT_RETRY_ATTEMPTS)
  assert.deepEqual(
    singles,
    Array.from({ length: TRANSIENT_RETRY_ATTEMPTS }, () => [7])
  )
  assert.ok(
    calls.every((c) => c.length !== 1 || c[0] === 7),
    '前半失败不得接着传后半，否则已确认前缀不连续'
  )
})
