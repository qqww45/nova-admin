import { spawnSync } from 'child_process'
import { existsSync, readdirSync, readFileSync, statSync } from 'fs'
import { join } from 'path'
import type { DataCenterOption, EnvDetectResult } from '../../shared/types'

const MAX_FILE_BYTES = 512 * 1024
const MAX_FILES = 400
const MAX_DEPTH = 6
const TEXT_EXT = /\.(xml|config|json|txt|ini|cfg)$/i
const SKIP_DIR = /(^|\\)(node_modules|cache|logs?|temp|crash(dumps)?|cef)(\\|$)/i

interface Hit {
  serverUrl: string
  acctId?: string
  username?: string
  mtime: number
}

const EMPTY: EnvDetectResult = {
  serverUrl: '',
  acctId: '',
  username: '',
  dataCenters: [],
  message: ''
}

export async function detectKingdeeEnv(): Promise<EnvDetectResult> {
  const hits = collectHits()
  if (!hits.length) {
    return {
      ...EMPTY,
      message:
        '本机金蝶客户端里没有找到保存过的服务器地址。请在金蝶里登录过一次后再检测，或向实施人员确认。'
    }
  }

  hits.sort((a, b) => b.mtime - a.mtime)
  const serverUrl = hits[0].serverUrl
  const same = hits.filter((hit) => hit.serverUrl === serverUrl)
  const acctFromFile = same.find((hit) => hit.acctId)?.acctId || ''
  const username = same.find((hit) => hit.username)?.username || ''
  const serverCount = new Set(hits.map((hit) => hit.serverUrl)).size

  let dataCenters: DataCenterOption[] = []
  let centerError = ''
  try {
    dataCenters = await listDataCenters(serverUrl)
  } catch (e) {
    centerError = e instanceof Error ? e.message : String(e)
  }

  let acctId = acctFromFile
  if (!acctId && dataCenters.length === 1) acctId = dataCenters[0].id
  if (acctId && dataCenters.length && !dataCenters.some((c) => c.id === acctId)) {
    acctId = dataCenters.length === 1 ? dataCenters[0].id : ''
  }

  const parts: string[] = ['已填入服务器地址']
  if (serverCount > 1) parts.push(`发现 ${serverCount} 个地址，使用最近保存的一个`)
  if (username) parts.push('已填入账号')
  else parts.push('账号未在客户端留下，需要手填')
  if (acctId && dataCenters.length <= 1) parts.push('已填入数据中心')
  else if (dataCenters.length > 1) parts.push(`发现 ${dataCenters.length} 个数据中心，请选择`)
  else if (centerError) parts.push('数据中心列表没有读到，内码需要手填')
  else parts.push('数据中心内码需要手填')

  return { serverUrl, acctId, username, dataCenters, message: parts.join('。') + '。' }
}

function collectHits(): Hit[] {
  const hits: Hit[] = []
  const seen = new Set<string>()
  const budget = { files: 0 }
  const roots = kingdeeRoots()
  for (const root of roots) scanDir(root, 0, hits, seen, budget)

  for (const key of [
    'HKCU\\Software\\Kingdee',
    'HKLM\\Software\\Kingdee',
    'HKLM\\Software\\WOW6432Node\\Kingdee'
  ]) {
    considerText(readRegistry(key), 0, hits)
  }

  for (const root of roots) {
    const mgr = join(root, 'K3Cloud', 'DeskClient', 'App_Data', 'DeskAppManager.xml')
    if (!existsSync(mgr)) continue
    const text = readText(mgr)
    const match = text.match(/<BaseDir>([^<]+)<\/BaseDir>/i)
    const base = match?.[1]?.trim()
    if (base && existsSync(base)) scanDir(base, 0, hits, seen, budget)
  }
  return hits
}

function kingdeeRoots(): string[] {
  const env = process.env
  const candidates = [
    env['ProgramFiles(x86)'] && join(env['ProgramFiles(x86)'], 'Kingdee'),
    env.ProgramFiles && join(env.ProgramFiles, 'Kingdee'),
    env.LOCALAPPDATA && join(env.LOCALAPPDATA, 'Kingdee'),
    env.APPDATA && join(env.APPDATA, 'Kingdee'),
    env.PROGRAMDATA && join(env.PROGRAMDATA, 'Kingdee'),
    join('C:\\Users\\Public', 'Kingdee')
  ]
  return Array.from(new Set(candidates.filter((p): p is string => Boolean(p && existsSync(p)))))
}

function scanDir(
  dir: string,
  depth: number,
  hits: Hit[],
  seen: Set<string>,
  budget: { files: number }
): void {
  if (depth > MAX_DEPTH || budget.files >= MAX_FILES) return
  const key = dir.toLowerCase()
  if (seen.has(key) || SKIP_DIR.test(dir)) return
  seen.add(key)
  let entries: string[] = []
  try {
    entries = readdirSync(dir)
  } catch {
    return
  }
  for (const name of entries) {
    if (budget.files >= MAX_FILES) return
    const full = join(dir, name)
    let st
    try {
      st = statSync(full)
    } catch {
      continue
    }
    if (st.isDirectory()) {
      scanDir(full, depth + 1, hits, seen, budget)
      continue
    }
    if (!st.isFile() || st.size > MAX_FILE_BYTES || !TEXT_EXT.test(name)) continue
    budget.files += 1
    considerText(readText(full), st.mtimeMs, hits)
  }
}

function considerText(text: string, mtime: number, hits: Hit[]): void {
  if (!text) return
  const urls = extractServerUrls(text)
  if (!urls.length) return
  const acctId = extractLabeled(text, ['AcctID', 'AcctId', 'DBId', 'DataCenterID', 'DataCenterId', 'DcId'])
  const username = extractLabeled(text, ['UserName', 'LoginName', 'UserAccount', 'FUserAccount'])
  for (const serverUrl of urls) {
    hits.push({ serverUrl, acctId, username, mtime })
  }
}

function extractServerUrls(text: string): string[] {
  const found = new Set<string>()
  const re = /https?:\/\/[^\s"'<>\\]+/gi
  let match: RegExpExecArray | null
  while ((match = re.exec(text))) {
    const normalized = normalizeServer(match[0])
    if (normalized) found.add(normalized)
  }
  return Array.from(found)
}

function normalizeServer(raw: string): string | null {
  const cleaned = raw.replace(/[),.;]+$/, '')
  let url: URL
  try {
    url = new URL(cleaned)
  } catch {
    return null
  }
  const host = url.hostname.toLowerCase()
  if (host === 'localhost' || host === '127.0.0.1' || host.endsWith('.digicert.com')) return null
  if (host === 'api.kingdee.com' || host === 'www.kingdee.com' || host.endsWith('yunzhijia.com')) return null
  const path = url.pathname
  const idx = path.toLowerCase().indexOf('/k3cloud')
  if (idx >= 0) return trimSlash(`${url.origin}${path.slice(0, idx)}`)
  if (host.endsWith('.kingdee.com') || host.endsWith('.ik3cloud.com')) return url.origin
  return null
}

function trimSlash(value: string): string {
  return value.replace(/\/+$/, '')
}

function extractLabeled(text: string, names: string[]): string | undefined {
  for (const name of names) {
    const patterns = [
      new RegExp(`<${name}\\b[^>]*>([^<]{1,80})</${name}>`, 'i'),
      new RegExp(`<setting\\s+name="${name}"[^>]*>\\s*<value>([^<]{1,80})</value>`, 'i'),
      new RegExp(`\\b${name}\\s*=\\s*"([^"]{1,80})"`, 'i'),
      new RegExp(`"${name}"\\s*:\\s*"([^"\\\\]{1,80})"`, 'i')
    ]
    for (const pattern of patterns) {
      const value = sanitizeValue(name, pattern.exec(text)?.[1])
      if (value) return value
    }
  }
  return undefined
}

function sanitizeValue(name: string, raw: string | undefined): string | undefined {
  if (!raw) return undefined
  const value = raw.trim()
  if (!value || value.length > 80) return undefined
  if (/password|passwd|secret|token/i.test(name)) return undefined
  if (/^(username|loginname|administrator|system|local service)$/i.test(value)) return undefined
  if (/https?:\/\//i.test(value) || /[\\/]/.test(value)) return undefined
  if (name.toLowerCase().includes('acct') || name.toLowerCase().includes('db') || name.toLowerCase().includes('center') || name.toLowerCase() === 'dcid') {
    if (!/^[A-Za-z0-9-]{4,64}$/.test(value)) return undefined
  }
  return value
}

function readText(file: string): string {
  try {
    const buf = readFileSync(file)
    if (buf.length >= 2 && buf[0] === 0xff && buf[1] === 0xfe) return buf.toString('utf16le')
    return buf.toString('utf8')
  } catch {
    return ''
  }
}

function readRegistry(key: string): string {
  const result = spawnSync('reg', ['query', key, '/s'], {
    encoding: 'utf8',
    timeout: 8000,
    windowsHide: true
  })
  if (result.status !== 0) return ''
  return result.stdout || ''
}

async function listDataCenters(serverUrl: string): Promise<DataCenterOption[]> {
  const base = trimSlash(serverUrl)
  const url = `${base}/K3Cloud/Kingdee.BOS.ServiceFacade.ServicesStub.Account.AccountService.GetDataCenterList.common.kdsvc`
  const bodies = ['{}', '', '{"parameters":[]}']
  for (const body of bodies) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
      signal: AbortSignal.timeout(8000)
    })
    if (!res.ok) continue
    const centers = parseCenters(await res.text())
    if (centers.length) return centers
  }
  return []
}

function parseCenters(text: string): DataCenterOption[] {
  let data: unknown
  try {
    data = JSON.parse(text)
  } catch {
    return []
  }
  const rows = asObjects(data)
  const centers: DataCenterOption[] = []
  for (const row of rows) {
    const id = String(row.Id ?? row.ID ?? row.id ?? row.AcctID ?? row.DBId ?? '').trim()
    if (!/^[A-Za-z0-9-]{4,64}$/.test(id)) continue
    const name = String(row.Name ?? row.name ?? row.Number ?? id).trim() || id
    const number = row.Number != null ? String(row.Number) : undefined
    if (!centers.some((item) => item.id === id)) centers.push({ id, name, number })
  }
  return centers
}

function asObjects(data: unknown): Record<string, unknown>[] {
  if (typeof data === 'string') {
    try {
      return asObjects(JSON.parse(data))
    } catch {
      return []
    }
  }
  if (Array.isArray(data)) {
    return data.filter((item) => item && typeof item === 'object') as Record<string, unknown>[]
  }
  if (data && typeof data === 'object') {
    const record = data as Record<string, unknown>
    for (const key of ['Result', 'Data', 'data', 'result']) {
      if (key in record) return asObjects(record[key])
    }
  }
  return []
}
