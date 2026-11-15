/**
 * 幂等键管理工具。
 *
 * [设计原则]
 *   1. Key 与具体提交动作绑定，不在请求拦截器中无脑生成
 *   2. 用户发起新操作 → getOrCreateKey() 生成新 Key
 *   3. 网络超时重试 → getExistingKey() 复用原 Key
 *   4. 操作明确成功 → clearKey() 销毁本地 Key
 *   5. 用户修改关键请求参数 → clearKey() + getOrCreateKey() 重新生成
 *
 * [使用方式]
 *   import { idempotencyKey } from '@/utils/idempotencyKey'
 *   const key = idempotencyKey.getOrCreate('report-work-process')
 *   await apiCall({ ..., headers: { 'Idempotency-Key': key } })
 *   idempotencyKey.clear('report-work-process')
 */

type KeyStore = {
  [actionKey: string]: {
    key: string
    createdAt: number
  }
}

const STORE_KEY = '_idempotency_keys'

function generateUUID(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  // Fallback for older browsers
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

function loadStore(): KeyStore {
  try {
    const raw = sessionStorage.getItem(STORE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

function saveStore(store: KeyStore): void {
  try {
    sessionStorage.setItem(STORE_KEY, JSON.stringify(store))
  } catch {
    // sessionStorage full, ignore
  }
}

export const idempotencyKey = {
  /**
   * 获取或创建幂等键。
   * 如果该 action 已有未过期的 Key，返回已有的（用于重试）；
   * 否则创建新的 UUID。
   */
  getOrCreate(actionKey: string): string {
    const store = loadStore()
    const existing = store[actionKey]
    const now = Date.now()
    // Key 有效期 10 分钟
    const MAX_AGE_MS = 10 * 60 * 1000

    if (existing && (now - existing.createdAt) < MAX_AGE_MS) {
      return existing.key
    }

    const newKey = generateUUID()
    store[actionKey] = { key: newKey, createdAt: now }
    saveStore(store)
    return newKey
  },

  /**
   * 获取当前已存在的 Key（用于重试时复用），不创建新的。
   * 如果没有或已过期，返回 null。
   */
  getExisting(actionKey: string): string | null {
    const store = loadStore()
    const existing = store[actionKey]
    const now = Date.now()
    const MAX_AGE_MS = 10 * 60 * 1000

    if (existing && (now - existing.createdAt) < MAX_AGE_MS) {
      return existing.key
    }
    return null
  },

  /**
   * 清除指定 action 的幂等键（操作成功后调用）。
   */
  clear(actionKey: string): void {
    const store = loadStore()
    delete store[actionKey]
    saveStore(store)
  },

  /**
   * 清除所有过期的幂等键。
   */
  cleanExpired(): void {
    const store = loadStore()
    const now = Date.now()
    const MAX_AGE_MS = 10 * 60 * 1000
    let changed = false

    for (const key of Object.keys(store)) {
      if ((now - store[key].createdAt) > MAX_AGE_MS) {
        delete store[key]
        changed = true
      }
    }

    if (changed) {
      saveStore(store)
    }
  }
}
