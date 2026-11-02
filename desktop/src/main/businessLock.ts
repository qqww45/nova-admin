type LockKind = 'idle' | 'sync' | 'export' | 'migrate' | 'update'

let lock: LockKind = 'idle'

export function getLock() {
  return lock
}

export function tryAcquire(kind: Exclude<LockKind, 'idle'>): boolean {
  if (lock !== 'idle') return false
  lock = kind
  return true
}

export function release(kind: Exclude<LockKind, 'idle'>) {
  if (lock === kind) lock = 'idle'
}

export function canInstallUpdate() {
  return lock === 'idle' || lock === 'update'
}
