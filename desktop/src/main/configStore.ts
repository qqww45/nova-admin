import { join } from 'path'
import { existsSync, readFileSync, writeFileSync } from 'fs'
import { safeStorage } from 'electron'
import type { AppConfigPublic, AppConfigSaveInput, NovaState } from '../shared/types'
import { getUserRoots } from './paths'

/** 上行同步（nova 对账平台）在本机的状态 */
interface StoredNova {
  baseUrl: string
  /** 机器令牌密文：safeStorage 可用时是系统密钥加密，否则退化为 base64 */
  tokenEnc?: string
  bindingId?: string
  userId?: string
  acctId?: string
  acctName?: string
  lastUploadAt?: string
  lastUploadResult?: string
  /** 本地上行水位：只有该时间之后写入本地库的行才需要上报 */
  uploadWatermark?: string
}

interface StoredConfig {
  serverUrl: string
  acctId: string
  acctName?: string
  username: string
  passwordEnc?: string
  companyName: string
  companyShortName: string
  companyAddress: string
  companyPhone: string
  preparerName: string
  language: number
  nova?: StoredNova
}

const DEFAULTS: StoredConfig = {
  serverUrl: '',
  acctId: '',
  username: '',
  companyName: '深圳市一恒科电子科技有限公司',
  companyShortName: '一恒科',
  companyAddress: '',
  companyPhone: '',
  preparerName: '杨晓蓉',
  language: 2052
}

function configPath() {
  return join(getUserRoots().config, 'app.json')
}

function readRaw(): StoredConfig {
  const p = configPath()
  if (!existsSync(p)) return { ...DEFAULTS }
  try {
    return { ...DEFAULTS, ...JSON.parse(readFileSync(p, 'utf-8')) }
  } catch {
    return { ...DEFAULTS }
  }
}

function writeRaw(cfg: StoredConfig) {
  writeFileSync(configPath(), JSON.stringify(cfg, null, 2), 'utf-8')
}

export function getPublicConfig(): AppConfigPublic {
  const c = readRaw()
  return {
    serverUrl: c.serverUrl,
    acctId: c.acctId,
    acctName: c.acctName || '',
    username: c.username,
    hasPassword: Boolean(c.passwordEnc),
    companyName: c.companyName,
    companyShortName: c.companyShortName,
    companyAddress: c.companyAddress,
    companyPhone: c.companyPhone,
    preparerName: c.preparerName,
    language: c.language
  }
}

export function saveConfig(input: AppConfigSaveInput): AppConfigPublic {
  const cur = readRaw()
  const next: StoredConfig = {
    ...cur,
    serverUrl: input.serverUrl.trim(),
    acctId: input.acctId.trim(),
    acctName: (input.acctName || '').trim(),
    username: input.username.trim(),
    companyName: input.companyName.trim(),
    companyShortName: input.companyShortName.trim(),
    companyAddress: input.companyAddress.trim(),
    companyPhone: input.companyPhone.trim(),
    preparerName: input.preparerName.trim(),
    language: 2052
  }

  if (input.clearPassword) {
    delete next.passwordEnc
  } else if (input.password && input.password.length > 0) {
    if (safeStorage.isEncryptionAvailable()) {
      next.passwordEnc = safeStorage.encryptString(input.password).toString('base64')
    } else {
      next.passwordEnc = Buffer.from(input.password, 'utf-8').toString('base64')
    }
  }

  writeRaw(next)
  return getPublicConfig()
}

export function getPasswordPlain(): string | null {
  const c = readRaw()
  if (!c.passwordEnc) return null
  try {
    if (safeStorage.isEncryptionAvailable()) {
      return safeStorage.decryptString(Buffer.from(c.passwordEnc, 'base64'))
    }
    return Buffer.from(c.passwordEnc, 'base64').toString('utf-8')
  } catch {
    return null
  }
}

export function getFullAuth() {
  const c = readRaw()
  return {
    serverUrl: c.serverUrl,
    acctId: c.acctId,
    username: c.username,
    password: getPasswordPlain() || '',
    language: c.language
  }
}

/* ---------------- 上行同步（nova 对账平台） ---------------- */

/** 默认对账平台地址：生产前端与后端同域，客户端直接打后端域名 */
const DEFAULT_NOVA_BASE_URL = 'https://nova-api.s.ci64.com'

function encryptSecret(plain: string): string {
  if (safeStorage.isEncryptionAvailable()) {
    return safeStorage.encryptString(plain).toString('base64')
  }
  return Buffer.from(plain, 'utf-8').toString('base64')
}

function decryptSecret(enc?: string): string | null {
  if (!enc) return null
  try {
    if (safeStorage.isEncryptionAvailable()) {
      return safeStorage.decryptString(Buffer.from(enc, 'base64'))
    }
    return Buffer.from(enc, 'base64').toString('utf-8')
  } catch {
    return null
  }
}

/** 上行运行时参数（含令牌，仅主进程内部使用，绝不下发渲染进程） */
export function getNovaRuntime() {
  const nova = readRaw().nova
  return {
    baseUrl: (nova?.baseUrl || DEFAULT_NOVA_BASE_URL).replace(/\/+$/, ''),
    token: decryptSecret(nova?.tokenEnc),
    bindingId: nova?.bindingId,
    userId: nova?.userId,
    acctId: nova?.acctId,
    acctName: nova?.acctName,
    uploadWatermark: nova?.uploadWatermark
  }
}

/** 上行状态（可下发渲染进程：不含令牌） */
export function getNovaState(): NovaState {
  const rt = getNovaRuntime()
  const nova = readRaw().nova
  return {
    baseUrl: rt.baseUrl,
    bound: Boolean(rt.token && rt.bindingId),
    bindingId: nova?.bindingId,
    userId: nova?.userId,
    acctId: nova?.acctId,
    acctName: nova?.acctName,
    lastUploadAt: nova?.lastUploadAt,
    lastUploadResult: nova?.lastUploadResult,
    uploadWatermark: nova?.uploadWatermark
  }
}

/**
 * 绑定成功后的落盘。
 *
 * <p>注意：服务端水位是"金蝶修改时间"，本地水位是"本地写入时间"，两者不同域，
 * 不能互相赋值。重新绑定后本地会重传一遍（服务端按内码幂等），这比漏传安全。
 */
export function saveNovaBinding(input: {
  baseUrl: string
  token: string
  bindingId: string
  userId: string
  acctId: string
  acctName?: string
}) {
  const cur = readRaw()
  const sameAcct =
    cur.nova?.bindingId === input.bindingId || (cur.nova?.acctId || '') === input.acctId
  const next: StoredConfig = {
    ...cur,
    nova: {
      baseUrl: input.baseUrl.replace(/\/+$/, ''),
      tokenEnc: encryptSecret(input.token),
      bindingId: input.bindingId,
      userId: input.userId,
      acctId: input.acctId,
      acctName: input.acctName,
      // 只有同一账套的旧水位才可沿用，避免 A 账套的水位污染 B 账套
      uploadWatermark: sameAcct ? cur.nova?.uploadWatermark : undefined,
      lastUploadAt: sameAcct ? cur.nova?.lastUploadAt : undefined,
      lastUploadResult: sameAcct ? cur.nova?.lastUploadResult : undefined
    }
  }
  writeRaw(next)
  return getNovaState()
}

/** 记录一次上报结果（成功才推进水位） */
export function saveNovaUploadResult(input: {
  ok: boolean
  message: string
  watermark?: string
  finishedAt: string
}) {
  const cur = readRaw()
  const nova = cur.nova
  if (!nova) return getNovaState()
  writeRaw({
    ...cur,
    nova: {
      ...nova,
      lastUploadAt: input.finishedAt,
      lastUploadResult: input.message.slice(0, 400),
      uploadWatermark: input.ok && input.watermark ? input.watermark : nova.uploadWatermark
    }
  })
  return getNovaState()
}

/** 解除绑定：令牌立即从本机删除，服务端需在网页上吊销 */
export function clearNovaBinding() {
  const cur = readRaw()
  const baseUrl = cur.nova?.baseUrl || DEFAULT_NOVA_BASE_URL
  writeRaw({ ...cur, nova: { baseUrl } })
  return getNovaState()
}

export { DEFAULT_NOVA_BASE_URL }
