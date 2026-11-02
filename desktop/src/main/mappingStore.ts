import { existsSync, readFileSync, writeFileSync } from 'fs'
import { join } from 'path'
import { getResourcesRoot, getUserRoots } from './paths'
import type { OutstockMapping } from './kingdee/lineKeys'

const FILE_NAME = 'outstock.json'

/** 用户目录优先（可直接改写，升级不被覆盖），本机无映射时回退内置 resources */
export function userMappingPath(): string {
  return join(getUserRoots().root, 'mappings', FILE_NAME)
}

export function builtinMappingPath(): string {
  return join(getResourcesRoot(), 'mappings', FILE_NAME)
}

export function loadOutstockMapping(): OutstockMapping {
  for (const p of [userMappingPath(), builtinMappingPath()]) {
    try {
      if (existsSync(p)) return JSON.parse(readFileSync(p, 'utf-8')) as OutstockMapping
    } catch {
      /* ignore */
    }
  }
  return {}
}

export function hasUserMapping(): boolean {
  return existsSync(userMappingPath())
}

/** 合并写入用户目录；传 undefined 的字段保持原值 */
export function saveUserOutstockMapping(patch: Partial<OutstockMapping>): OutstockMapping {
  const prev = loadOutstockMapping()
  const next: OutstockMapping = { ...prev }
  for (const key of Object.keys(patch) as Array<keyof OutstockMapping>) {
    const v = patch[key]
    if (v === undefined) continue
    ;(next as Record<string, unknown>)[key] = v
  }
  writeFileSync(userMappingPath(), JSON.stringify(next, null, 2), 'utf-8')
  return next
}

export function clearUserOutstockMapping(): void {
  try {
    if (existsSync(userMappingPath())) writeFileSync(userMappingPath(), '{}', 'utf-8')
  } catch {
    /* ignore */
  }
}
