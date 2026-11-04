/**
 * 开发态渲染进程启动：避免 Electron 连 localhost 时走 IPv4、
 * Vite 只绑 IPv6（::1）导致 ERR_CONNECTION_REFUSED，窗口只剩标题栏底色。
 */

export function preferIpv4Loopback(url: string): string {
  try {
    const parsed = new URL(url)
    if (parsed.hostname === 'localhost') {
      parsed.hostname = '127.0.0.1'
    }
    return parsed.toString()
  } catch {
    return url
  }
}

export async function waitForHttpOk(
  url: string,
  options: {
    timeoutMs?: number
    intervalMs?: number
    fetchImpl?: (input: string) => Promise<{ ok: boolean }>
    now?: () => number
    sleep?: (ms: number) => Promise<void>
  } = {}
): Promise<boolean> {
  const timeoutMs = options.timeoutMs ?? 20_000
  const intervalMs = options.intervalMs ?? 200
  const fetchImpl =
    options.fetchImpl ??
    ((input) => fetch(input, { signal: AbortSignal.timeout(1500) }).then((r) => ({ ok: r.ok })))
  const now = options.now ?? Date.now
  const sleep = options.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)))
  const start = now()

  while (now() - start <= timeoutMs) {
    try {
      const res = await fetchImpl(url)
      if (res.ok) return true
    } catch {
      /* 开发服务器尚未监听，继续等 */
    }
    if (now() - start + intervalMs > timeoutMs) break
    await sleep(intervalMs)
  }
  return false
}

export function buildStartupErrorHtml(input: { url: string; error: string }): string {
  const url = escapeHtml(input.url)
  const error = escapeHtml(input.error)
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <title>衡账 · 开发界面未就绪</title>
  <style>
    html, body { height: 100%; margin: 0; background: #0B1220; color: #E8EEF7;
      font-family: "Noto Sans SC", system-ui, sans-serif; }
    main { box-sizing: border-box; padding: 72px 48px 48px; max-width: 720px; }
    h1 { font-size: 22px; margin: 0 0 16px; }
    p, li { color: #8FA3BF; line-height: 1.7; }
    code { color: #5EEAD4; }
    .err { color: #FF6B7A; word-break: break-all; }
  </style>
</head>
<body>
  <main>
    <h1>衡账开发界面未就绪</h1>
    <p class="err">${error}</p>
    <p>无法连接渲染进程：<code>${url}</code></p>
    <ul>
      <li>请在 <code>desktop/</code> 目录执行 <code>npm run dev</code>（由 electron-vite 先拉起 Vite 再启动窗口）。</li>
      <li>不要直接运行 <code>electron .</code> 或 <code>npx electron .</code>。</li>
      <li>若 5173 已被占用，关掉占用进程后重试。</li>
    </ul>
  </main>
</body>
</html>`
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}
