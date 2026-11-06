import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { UPLOAD_CHUNK, chunkRows } from './client.ts'

/** 服务端 Kd*SyncReq.rows 的 @Size(max = 2000) */
const SERVER_MAX_ROWS = 2000

test('桌面端版本 1.2.5，便于现场区分本次 524 韧性修复', () => {
  const pkg = JSON.parse(readFileSync(resolve(import.meta.dirname, '../../../package.json'), 'utf8'))
  assert.equal(pkg.version, '1.2.5')
})

test('UPLOAD_CHUNK 远小于服务端上限，避免单请求压到 EdgeOne 源站超时（HTTP 524）', () => {
  assert.equal(UPLOAD_CHUNK, 100)
  assert.ok(UPLOAD_CHUNK > 0)
  assert.ok(UPLOAD_CHUNK < SERVER_MAX_ROWS)
  // 现场 500 行一批约 15–16s 撞 524；100 行约为原先 1/5，给源站超时留余量
  assert.ok(UPLOAD_CHUNK <= 100)
})

test('chunkRows 按块切分且不丢行', () => {
  assert.deepEqual(chunkRows([], 100), [])
  assert.deepEqual(chunkRows([1, 2, 3], 100), [[1, 2, 3]])
  assert.deepEqual(
    chunkRows([1, 2, 3, 4, 5], 2),
    [
      [1, 2],
      [3, 4],
      [5]
    ]
  )
  const rows = Array.from({ length: 250 }, (_, i) => i)
  const chunks = chunkRows(rows)
  assert.equal(chunks.length, 3)
  assert.equal(chunks[0].length, UPLOAD_CHUNK)
  assert.equal(chunks[1].length, UPLOAD_CHUNK)
  assert.equal(chunks[2].length, 50)
  assert.deepEqual(chunks.flat(), rows)
})

test('chunkRows 默认用 UPLOAD_CHUNK', () => {
  const rows = Array.from({ length: UPLOAD_CHUNK + 1 }, (_, i) => i)
  const chunks = chunkRows(rows)
  assert.equal(chunks.length, 2)
  assert.equal(chunks[0].length, UPLOAD_CHUNK)
  assert.equal(chunks[1].length, 1)
})

test('上行三实体都按 UPLOAD_CHUNK 切块，块确认后推进分实体水位，524 会重试', () => {
  const uploader = readFileSync(resolve(import.meta.dirname, 'uploader.ts'), 'utf8')
  const configStore = readFileSync(resolve(import.meta.dirname, '../configStore.ts'), 'utf8')
  assert.match(uploader, /chunkRows\(customers/)
  assert.match(uploader, /chunkRows\(outbounds/)
  assert.match(uploader, /chunkRows\(lines/)
  assert.match(uploader, /uploadWithRetry/)
  assert.match(uploader, /retryTransient/)
  assert.match(uploader, /persistEntityWatermark\('customer'/)
  assert.match(uploader, /persistEntityWatermark\('outbound'/)
  assert.match(uploader, /persistEntityWatermark\('line'/)
  assert.match(uploader, /saveNovaUploadProgress/)
  // 分录兜底内码用本地 id，半块重跑不得按下标重算
  assert.match(uploader, /resolveLocalLineEntryId/)
  assert.match(uploader, /outbound_fid \* 1_000_000/)
  assert.doesNotMatch(uploader, /lineEntryIds\[base \+ i\]/)
  assert.doesNotMatch(uploader, /lineOffset \+= chunk\.length/)
  // 整批成功才推进展示用总水位；失败不得回拨分实体进度
  assert.match(uploader, /saveNovaUploadResult\(\{ ok: true, message, watermark/)
  assert.match(uploader, /saveNovaUploadResult\(\{ ok: false, message, finishedAt \}\)/)
  assert.match(configStore, /saveNovaUploadProgress/)
  assert.match(configStore, /uploadWatermarkCustomer/)
  assert.match(configStore, /uploadWatermarkOutbound/)
  assert.match(configStore, /uploadWatermarkLine/)
  // 失败仍走 finishBatch FAILED，方便服务端看见现场
  assert.match(uploader, /status: 'FAILED'/)
  // 并发去重：已有上行在跑时复用同一次 Promise
  assert.match(uploader, /if \(uploadInFlight\) return uploadInFlight/)
})
