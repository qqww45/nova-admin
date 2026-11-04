import { test } from 'node:test'
import assert from 'node:assert/strict'
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
