import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { preferIpv4Loopback, waitForHttpOk, buildStartupErrorHtml } from './devRenderer.ts'

test('preferIpv4Loopback 把 localhost 改成 127.0.0.1，避免 Windows 上 IPv6 监听、IPv4 连接被拒', () => {
  assert.equal(preferIpv4Loopback('http://localhost:5173/'), 'http://127.0.0.1:5173/')
  assert.equal(preferIpv4Loopback('http://localhost:5173'), 'http://127.0.0.1:5173/')
  assert.equal(preferIpv4Loopback('http://127.0.0.1:5173/'), 'http://127.0.0.1:5173/')
  assert.equal(preferIpv4Loopback('http://192.168.1.8:5173/'), 'http://192.168.1.8:5173/')
})

test('waitForHttpOk 在超时前等到一次 2xx 即成功', async () => {
  let calls = 0
  const ok = await waitForHttpOk('http://127.0.0.1:5173/', {
    timeoutMs: 1000,
    intervalMs: 10,
    now: (() => {
      let t = 0
      return () => t++
    })(),
    sleep: async () => {},
    fetchImpl: async () => {
      calls += 1
      if (calls < 3) throw new Error('ECONNREFUSED')
      return { ok: true }
    }
  })
  assert.equal(ok, true)
  assert.equal(calls, 3)
})

test('waitForHttpOk 一直连不上则返回 false', async () => {
  const ok = await waitForHttpOk('http://127.0.0.1:5173/', {
    timeoutMs: 5,
    intervalMs: 1,
    now: (() => {
      let t = 0
      return () => (t += 3)
    })(),
    sleep: async () => {},
    fetchImpl: async () => {
      throw new Error('ECONNREFUSED')
    }
  })
  assert.equal(ok, false)
})

test('buildStartupErrorHtml 给出可操作的中文说明并转义 URL', () => {
  const html = buildStartupErrorHtml({
    url: 'http://127.0.0.1:5173/</script>',
    error: 'ERR_CONNECTION_REFUSED'
  })
  assert.match(html, /ERR_CONNECTION_REFUSED/)
  assert.match(html, /npm run dev/)
  assert.match(html, /衡账/)
  assert.doesNotMatch(html, /<\/script>/)
  assert.match(html, /127\.0\.0\.1:5173/)
})

test('品牌图标 PNG/SVG/ICO 齐全，ico 含 256（安装包/快捷方式）', () => {
  const dir = resolve(import.meta.dirname, '../../resources')
  const svgPath = resolve(dir, 'icon.svg')
  assert.equal(existsSync(svgPath), true)
  assert.equal(existsSync(resolve(dir, 'icon.png')), true)
  const svg = readFileSync(svgPath, 'utf8')
  assert.match(svg, /<svg\b/)
  assert.match(svg, /<path\b/)
  assert.match(svg, /<linearGradient\b/)
  assert.match(svg, /#3E36C0/)
  assert.match(svg, /#00B2B6/)
  assert.match(svg, /#FFFFFF/)
  assert.doesNotMatch(svg, /<image\b/i)
  assert.doesNotMatch(svg, /<text\b/i)
  assert.doesNotMatch(svg, /xlink:href|data:image/)
  assert.doesNotMatch(svg, /衡/)
  assert.doesNotMatch(svg, /#0F203C|#DDA944|#BE0515/)
  const icoPath = resolve(dir, 'icon.ico')
  assert.equal(existsSync(icoPath), true)
  const ico = readFileSync(icoPath)
  assert.equal(ico.readUInt16LE(0), 0)
  assert.equal(ico.readUInt16LE(2), 1)
  const count = ico.readUInt16LE(4)
  assert.ok(count >= 6, `ico entries ${count}`)
  let has256 = false
  for (let i = 0; i < count; i++) {
    if (ico[6 + i * 16] === 0) has256 = true
  }
  assert.equal(has256, true)
})
