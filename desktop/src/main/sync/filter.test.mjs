import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  decideSyncFilter,
  nextModifyWatermark,
  decideLinePull,
  normalizeKdTime,
  maxKdTime,
  WATERMARK_BUFFER_MINUTES,
  SWEEP_WINDOW_DAYS,
  MAX_LINE_FID_IN
} from './filter.ts'

const NOW = new Date('2026-11-06T12:00:00')

test('显式日期永远走 FDate，忽略水位与是否支持修改时间', () => {
  const d = decideSyncFilter({
    explicitSinceDate: '2026-10-01',
    supportsModify: true,
    watermark: '2026-11-01 10:00:00',
    now: NOW
  })
  assert.equal(d.mode, 'fdate')
  assert.equal(d.filter, "FDate>='2026-10-01'")
  assert.equal(d.watermark, undefined)
})

test('无水位或账套不支持 FModifyDate 时全量，filter 为空', () => {
  assert.deepEqual(
    decideSyncFilter({ supportsModify: false, watermark: '2026-11-01 10:00:00', now: NOW }),
    { mode: 'full', filter: '' }
  )
  assert.equal(decideSyncFilter({ supportsModify: true, now: NOW }).mode, 'full')
  assert.equal(decideSyncFilter({ supportsModify: true, watermark: '  ', now: NOW }).filter, '')
})

test('有水位且支持修改时间：FModifyDate 减去 15 分钟缓冲', () => {
  const d = decideSyncFilter({
    supportsModify: true,
    watermark: '2026-11-03 10:00:00',
    lastSweepAt: '2026-11-01T00:00:00.000Z',
    now: NOW
  })
  assert.equal(d.mode, 'fmodify')
  assert.equal(d.watermark, '2026-11-03 10:00:00')
  assert.equal(WATERMARK_BUFFER_MINUTES, 15)
  assert.match(d.filter, /FModifyDate>='2026-11-03 09:45:00'/)
})

test('超过月度间隔且未关闭回看时，OR 最近 30 天 FDate', () => {
  const d = decideSyncFilter({
    supportsModify: true,
    watermark: '2026-11-03 10:00:00',
    now: NOW
  })
  assert.equal(d.mode, 'fmodify')
  assert.equal(d.sweepFrom, '2026-10-07')
  assert.equal(SWEEP_WINDOW_DAYS, 30)
  assert.equal(
    d.filter,
    "(FModifyDate>='2026-11-03 09:45:00' OR FDate>='2026-10-07')"
  )
})

test('客户主数据没有 FDate：enableSweep=false 只按 FModifyDate 增量', () => {
  const d = decideSyncFilter({
    supportsModify: true,
    watermark: '2026-11-03 10:00:00',
    now: NOW,
    enableSweep: false
  })
  assert.equal(d.mode, 'fmodify')
  assert.equal(d.filter, "FModifyDate>='2026-11-03 09:45:00'")
  assert.equal(d.sweepFrom, undefined)
})

test('全量成功拿到 maxModify 必须写出水位，否则下一轮永远全量', () => {
  assert.equal(
    nextModifyWatermark({ mode: 'full', maxModify: '2026-11-06 08:00:00' }),
    '2026-11-06 08:00:00'
  )
  assert.equal(
    nextModifyWatermark({
      mode: 'full',
      existingWatermark: '2026-11-01 00:00:00',
      maxModify: '2026-11-06 08:00:00'
    }),
    '2026-11-06 08:00:00'
  )
})

test('fmodify 水位只进不退；没有 maxModify 则保持原水位', () => {
  assert.equal(
    nextModifyWatermark({
      mode: 'fmodify',
      existingWatermark: '2026-11-03 10:00:00',
      maxModify: '2026-11-06 08:00:00'
    }),
    '2026-11-06 08:00:00'
  )
  assert.equal(
    nextModifyWatermark({
      mode: 'fmodify',
      existingWatermark: '2026-11-06 08:00:00',
      maxModify: '2026-11-01 00:00:00'
    }),
    '2026-11-06 08:00:00'
  )
  assert.equal(
    nextModifyWatermark({
      mode: 'fmodify',
      existingWatermark: '2026-11-03 10:00:00',
      maxModify: ''
    }),
    '2026-11-03 10:00:00'
  )
})

test('显式 FDate 纠错：无水位时用本轮 maxModify 建水位；已有水位不推进', () => {
  assert.equal(
    nextModifyWatermark({ mode: 'fdate', maxModify: '2026-11-06 08:00:00' }),
    '2026-11-06 08:00:00'
  )
  assert.equal(
    nextModifyWatermark({
      mode: 'fdate',
      existingWatermark: '2026-11-03 10:00:00',
      maxModify: '2026-11-06 08:00:00'
    }),
    '2026-11-03 10:00:00',
    '窗口外改单不能被子集 maxModify 跳过'
  )
})

test('全量但取不到 maxModify（账套无 FModifyDate）不凭空写水位', () => {
  assert.equal(nextModifyWatermark({ mode: 'full', maxModify: '' }), undefined)
  assert.equal(nextModifyWatermark({ mode: 'full' }), undefined)
})

test('表头无变化则跳过分录查询；少量变化用 FID IN，避免月度回看再拉几万行', () => {
  assert.deepEqual(decideLinePull('', []), { action: 'skip' })
  assert.deepEqual(decideLinePull("FModifyDate>='x'", [11, 22]), {
    action: 'query',
    filter: 'FID IN (11,22)'
  })
  const many = Array.from({ length: MAX_LINE_FID_IN + 1 }, (_, i) => i + 1)
  assert.deepEqual(decideLinePull("FDate>='2026-01-01'", many), {
    action: 'query',
    filter: "FDate>='2026-01-01'"
  })
})

test('normalizeKdTime / maxKdTime 截断到秒并取较大值', () => {
  assert.equal(normalizeKdTime('2026-11-03T10:00:00'), '2026-11-03 10:00:00')
  assert.equal(normalizeKdTime('2026-11-03 10:00:00.123+08:00'), '2026-11-03 10:00:00')
  assert.equal(normalizeKdTime('garbage'), '')
  assert.equal(maxKdTime('', '2026-11-03 10:00:00'), '2026-11-03 10:00:00')
  assert.equal(maxKdTime('2026-11-03 10:00:00', '2026-11-01 00:00:00'), '2026-11-03 10:00:00')
})
