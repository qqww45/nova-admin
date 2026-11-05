import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { UPLOAD_CHUNK, chunkRows } from './client.ts'

/** 服务端 Kd*SyncReq.rows 的 @Size(max = 2000) */
const SERVER_MAX_ROWS = 2000

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

test('上行三实体都按 UPLOAD_CHUNK 切块，水位只在整批成功后推进', () => {
  const uploader = readFileSync(resolve(import.meta.dirname, 'uploader.ts'), 'utf8')
  assert.match(uploader, /chunkRows\(customers/)
  assert.match(uploader, /chunkRows\(outbounds/)
  assert.match(uploader, /chunkRows\(lines/)
  // 分录兜底内码按整批偏移取号，切块后不能从 0 重算
  assert.match(uploader, /lineEntryIds\[base \+ i\]/)
  assert.match(uploader, /lineOffset \+= chunk\.length/)
  // 失败不得推进本地水位（saveNovaUploadResult 成功才带 watermark）
  assert.match(uploader, /saveNovaUploadResult\(\{ ok: true, message, watermark/)
  assert.match(uploader, /saveNovaUploadResult\(\{ ok: false, message, finishedAt \}\)/)
  // 失败仍走 finishBatch FAILED，方便服务端看见现场
  assert.match(uploader, /status: 'FAILED'/)
  // 并发去重：已有上行在跑时复用同一次 Promise
  assert.match(uploader, /if \(uploadInFlight\) return uploadInFlight/)
})
