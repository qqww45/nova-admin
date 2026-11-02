import { join } from 'path'
import { existsSync, readFileSync, writeFileSync } from 'fs'
import { safeStorage } from 'electron'
import type { AppConfigPublic, AppConfigSaveInput } from '../shared/types'
import { getUserRoots } from './paths'

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
