/**
 * 统一离线缓存模块 — V3.3。
 * 支持 namespace、schemaVersion、userId 隔离、过期时间、容量控制。
 */

export interface CacheEntry<T = unknown> {
  data: T
  createdAt: number
  expiresAt: number
  schemaVersion: number
  userId: number | null
}

export interface CacheOptions {
  /** 缓存命名空间 */
  namespace: string
  /** 缓存有效期（毫秒），默认 5 分钟 */
  ttl?: number
  /** schema 版本号，不兼容的旧版本自动失效 */
  schemaVersion?: number
  /** 是否按 userId 隔离，默认 true */
  userIsolation?: boolean
  /** 缓存条目上限，默认 200 */
  maxEntries?: number
}

const DEFAULT_TTL = 5 * 60 * 1000 // 5 分钟
const DEFAULT_MAX_ENTRIES = 200
const CACHE_INDEX_KEY = '__cache_index__'
const CURRENT_SCHEMA = 1

/** 缓存索引 */
interface CacheIndex {
  keys: Array<{ key: string; createdAt: number; size: number }>
}

/**
 * 缓存管理器。
 */
class CacheManager {
  private getUserId(): number | null {
    try {
      const info = uni.getStorageSync('userInfo')
      return info?.id ?? null
    } catch { return null }
  }

  /** 构建存储键 */
  private buildKey(namespace: string, key: string, userId: number | null): string {
    const uid = userId != null ? `_u${userId}` : ''
    return `cache:${namespace}:${key}${uid}`
  }

  /** 读取并校验缓存 */
  get<T>(key: string, options: CacheOptions): T | null {
    const { namespace, schemaVersion = CURRENT_SCHEMA, userIsolation = true } = options
    const userId = userIsolation ? this.getUserId() : null
    const storageKey = this.buildKey(namespace, key, userId)

    try {
      const raw = uni.getStorageSync(storageKey)
      if (!raw) return null

      const entry: CacheEntry<T> = JSON.parse(raw)

      // schema 版本不兼容 → 失效
      if (entry.schemaVersion !== schemaVersion) {
        uni.removeStorageSync(storageKey)
        return null
      }

      // 过期
      if (Date.now() > entry.expiresAt) {
        uni.removeStorageSync(storageKey)
        return null
      }

      // userId 校验
      if (userIsolation && entry.userId != null && entry.userId !== userId) {
        return null
      }

      return entry.data
    } catch {
      // 解析失败 → 删除脏数据
      uni.removeStorageSync(storageKey)
      return null
    }
  }

  /** 写入缓存 */
  set<T>(key: string, data: T, options: CacheOptions): void {
    const {
      namespace, ttl = DEFAULT_TTL,
      schemaVersion = CURRENT_SCHEMA, userIsolation = true,
      maxEntries = DEFAULT_MAX_ENTRIES,
    } = options
    const userId = userIsolation ? this.getUserId() : null
    const storageKey = this.buildKey(namespace, key, userId)

    const entry: CacheEntry<T> = {
      data,
      createdAt: Date.now(),
      expiresAt: Date.now() + ttl,
      schemaVersion,
      userId,
    }

    try {
      uni.setStorageSync(storageKey, JSON.stringify(entry))
      this.trackKey(namespace, storageKey, maxEntries)
    } catch (e: any) {
      // 存储空间不足 → LRU 清理
      if (e?.errMsg?.includes('limit')) {
        this.evictLRU(namespace, maxEntries)
        try { uni.setStorageSync(storageKey, JSON.stringify(entry)) } catch { /* give up */ }
      }
    }
  }

  /** 清除命名空间下所有缓存 */
  clearNamespace(namespace: string): void {
    const index = this.getIndex()
    const prefix = `cache:${namespace}:`
    const toRemove = index.keys.filter(k => k.key.startsWith(prefix))
    toRemove.forEach(k => {
      try { uni.removeStorageSync(k.key) } catch { /* ignore */ }
    })
    index.keys = index.keys.filter(k => !k.key.startsWith(prefix))
    this.saveIndex(index)
  }

  /** 清除当前用户所有缓存（登出时调用） */
  clearAllForUser(): void {
    const userId = this.getUserId()
    const index = this.getIndex()
    const toRemove = index.keys.filter(k => k.key.includes(`_u${userId}`))
    toRemove.forEach(k => {
      try { uni.removeStorageSync(k.key) } catch { /* ignore */ }
    })
    index.keys = index.keys.filter(k => !toRemove.includes(k))
    this.saveIndex(index)
  }

  /** 获取缓存元信息（用于展示） */
  getMeta(key: string, options: CacheOptions): { cachedAt: string; isExpired: boolean } | null {
    const { namespace, userIsolation = true } = options
    const userId = userIsolation ? this.getUserId() : null
    const storageKey = this.buildKey(namespace, key, userId)

    try {
      const raw = uni.getStorageSync(storageKey)
      if (!raw) return null
      const entry: CacheEntry = JSON.parse(raw)
      return {
        cachedAt: new Date(entry.createdAt).toLocaleString(),
        isExpired: Date.now() > entry.expiresAt,
      }
    } catch { return null }
  }

  // ============ 内部方法 ============

  private getIndex(): CacheIndex {
    try {
      const raw = uni.getStorageSync(CACHE_INDEX_KEY)
      return raw ? JSON.parse(raw) : { keys: [] }
    } catch { return { keys: [] } }
  }

  private saveIndex(index: CacheIndex): void {
    try { uni.setStorageSync(CACHE_INDEX_KEY, JSON.stringify(index)) } catch { /* ignore */ }
  }

  private trackKey(namespace: string, storageKey: string, maxEntries: number): void {
    const index = this.getIndex()
    // 移除同名旧条目
    index.keys = index.keys.filter(k => k.key !== storageKey)
    index.keys.push({ key: storageKey, createdAt: Date.now(), size: 0 })
    // 超过上限 → LRU 淘汰
    const nsKeys = index.keys.filter(k => k.key.startsWith(`cache:${namespace}:`))
    if (nsKeys.length > maxEntries) {
      nsKeys.sort((a, b) => a.createdAt - b.createdAt)
      const toEvict = nsKeys.slice(0, nsKeys.length - maxEntries)
      toEvict.forEach(k => {
        try { uni.removeStorageSync(k.key) } catch { /* ignore */ }
        index.keys = index.keys.filter(ik => ik.key !== k.key)
      })
    }
    this.saveIndex(index)
  }

  private evictLRU(namespace: string, maxEntries: number): void {
    const index = this.getIndex()
    const nsKeys = index.keys.filter(k => k.key.startsWith(`cache:${namespace}:`))
    if (nsKeys.length > 0) {
      nsKeys.sort((a, b) => a.createdAt - b.createdAt)
      const half = Math.max(1, Math.floor(nsKeys.length / 2))
      const toEvict = nsKeys.slice(0, half)
      toEvict.forEach(k => {
        try { uni.removeStorageSync(k.key) } catch { /* ignore */ }
        index.keys = index.keys.filter(ik => ik.key !== k.key)
      })
      this.saveIndex(index)
    }
  }
}

export const cache = new CacheManager()

/** 预定义缓存配置 */
export const CACHE_CONFIGS = {
  workOrders: { namespace: 'workOrders', ttl: 5 * 60 * 1000 },
  workOrderDetail: { namespace: 'workOrderDetail', ttl: 10 * 60 * 1000 },
  workProcesses: { namespace: 'workProcesses', ttl: 3 * 60 * 1000 },
  referenceData: { namespace: 'reference', ttl: 30 * 60 * 1000 },
  inventory: { namespace: 'inventory', ttl: 5 * 60 * 1000 },
  inspections: { namespace: 'inspections', ttl: 5 * 60 * 1000 },
  userProfile: { namespace: 'userProfile', ttl: 10 * 60 * 1000 },
  searchFilters: { namespace: 'searchFilters', ttl: 24 * 60 * 60 * 1000 },
} as const
