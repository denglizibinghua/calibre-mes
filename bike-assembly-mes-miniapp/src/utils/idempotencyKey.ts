/**
 * 幂等键管理工具（小程序端）。
 *
 * [设计原则]
 *   1. Key 与具体提交动作绑定，不在请求拦截器中无脑生成
 *   2. 用户发起新操作 → getOrCreateKey() 生成新 Key
 *   3. 网络超时重试 → getExistingKey() 复用原 Key
 *   4. 操作明确成功 → clearKey() 销毁本地 Key
 *   5. Key 有效期 10 分钟，过期自动作废
 */

type KeyStore = Record<string, { key: string; createdAt: number }>

const STORE_KEY = '_idm_keys'

function generateUUID(): string {
  // UniApp 小程序环境，使用简单 UUID 生成
  const hex = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
  return hex.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

function loadStore(): KeyStore {
  try {
    const raw = uni.getStorageSync(STORE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

function saveStore(store: KeyStore): void {
  try {
    uni.setStorageSync(STORE_KEY, JSON.stringify(store))
  } catch { /* ignore */ }
}

export const idempotencyKey = {
  /** 获取或创建幂等键 */
  getOrCreate(actionKey: string): string {
    const store = loadStore()
    const existing = store[actionKey]
    const now = Date.now()
    const MAX_AGE = 10 * 60 * 1000

    if (existing && (now - existing.createdAt) < MAX_AGE) {
      return existing.key
    }

    const newKey = generateUUID()
    store[actionKey] = { key: newKey, createdAt: now }
    saveStore(store)
    return newKey
  },

  /** 获取已存在的 Key（用于重试） */
  getExisting(actionKey: string): string | null {
    const store = loadStore()
    const existing = store[actionKey]
    if (existing && (Date.now() - existing.createdAt) < 10 * 60 * 1000) {
      return existing.key
    }
    return null
  },

  /** 清除指定 action 的幂等键 */
  clear(actionKey: string): void {
    const store = loadStore()
    delete store[actionKey]
    saveStore(store)
  },

  /** 清除过期 Key */
  cleanExpired(): void {
    const store = loadStore()
    const now = Date.now()
    let changed = false
    for (const k of Object.keys(store)) {
      if ((now - store[k].createdAt) > 10 * 60 * 1000) {
        delete store[k]
        changed = true
      }
    }
    if (changed) saveStore(store)
  }
}
