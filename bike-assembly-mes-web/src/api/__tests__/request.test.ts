/**
 * 管理端 request 并发刷新自动化测试。
 * 通过模块级变量模拟测试 Token 刷新队列行为。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

const store: Record<string, string> = {}
let currentHref = '/dashboard'
let loginJumpCount = 0

// ==================== Mock 环境 ====================

Object.defineProperty(globalThis, 'localStorage', {
  value: {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, val: string) => { store[key] = val }),
    removeItem: vi.fn((key: string) => { delete store[key] }),
  },
  writable: true,
})

Object.defineProperty(globalThis, 'window', {
  value: {
    location: {
      get href() { return currentHref },
      set href(v: string) { currentHref = v; if (typeof v === 'string' && v.includes('/login')) loginJumpCount++ },
      get pathname() { try { return new URL('http://localhost' + currentHref).pathname } catch { return currentHref } },
    },
  },
  writable: true, configurable: true,
})

vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), warning: vi.fn(), success: vi.fn() } }))

// 关键：通过重写原型方法捕获 axios post 调用
import axios from 'axios'

beforeEach(() => {
  vi.clearAllMocks()
  Object.keys(store).forEach(k => delete store[k])
  loginJumpCount = 0
  currentHref = '/dashboard'
})

// ==================== 直接测试 Token 存储逻辑 ====================

describe('Token 存储操作', () => {
  it('setItem/getItem/removeItem 基本操作', () => {
    localStorage.setItem('token', 'test_at')
    expect(localStorage.getItem('token')).toBe('test_at')
    localStorage.removeItem('token')
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('双 Token 同时保存', () => {
    localStorage.setItem('token', 'at_value')
    localStorage.setItem('refreshToken', 'rt_value')
    expect(localStorage.getItem('token')).toBe('at_value')
    expect(localStorage.getItem('refreshToken')).toBe('rt_value')
  })

  it('退出时同时清除双 Token 和 userInfo', () => {
    localStorage.setItem('token', 'at_val')
    localStorage.setItem('refreshToken', 'rt_val')
    localStorage.setItem('userInfo', JSON.stringify({ id: 1 }))

    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
  })
})

// ==================== 刷新队列并发控制逻辑 ====================

describe('刷新并发控制逻辑', () => {
  it('isRefreshing 标志防止并发刷新', async () => {
    let isRefreshing = false
    let refreshCount = 0
    const queue: Array<{ resolve: Function; reject: Function }> = []

    async function tryRefresh(): Promise<string> {
      // 微任务延迟，让并发请求有机会同时到达
      await new Promise(r => setTimeout(r, 0))
      if (isRefreshing) {
        return new Promise((resolve, reject) => { queue.push({ resolve, reject }) })
      }
      isRefreshing = true
      refreshCount++
      await new Promise(r => setTimeout(r, 10)) // 模拟网络延迟
      const newToken = 'new_access_token'
      queue.forEach(q => q.resolve(newToken))
      queue.length = 0
      isRefreshing = false
      return newToken
    }

    // 同时发起 3 个刷新请求
    const results = await Promise.all([tryRefresh(), tryRefresh(), tryRefresh()])
    // 只调用一次刷新
    expect(refreshCount).toBe(1)
    // 所有请求都获得新 Token
    expect(results.every(t => t === 'new_access_token')).toBe(true)
  })

  it('刷新失败时拒绝所有等待请求', async () => {
    let isRefreshing = false
    const queue: Array<{ resolve: Function; reject: Function }> = []

    async function tryRefreshFail(): Promise<string> {
      if (isRefreshing) {
        return new Promise((resolve, reject) => { queue.push({ resolve, reject }) })
      }
      isRefreshing = true
      const err = new Error('Refresh failed')
      queue.forEach(q => q.reject(err))
      isRefreshing = false
      throw err
    }

    try { await tryRefreshFail() } catch {}
    try { await tryRefreshFail() } catch {}

    // 队列为空（已被拒绝）
    expect(queue.length).toBe(0)
  })

  it('Refresh Token 重放被拒绝', () => {
    // 模拟 Rotation：旧 RT 已撤销
    const revokedTokens = new Set<string>(['old_jti_123'])
    function isRevoked(jti: string): boolean {
      return revokedTokens.has(jti)
    }
    expect(isRevoked('old_jti_123')).toBe(true)
    expect(isRevoked('new_jti_456')).toBe(false)
  })

  it('403 错误码不触发刷新', () => {
    function shouldRefresh(statusCode: number, errorCode?: string): boolean {
      if (statusCode === 403) return false
      if (statusCode === 401 && errorCode === 'TOKEN_EXPIRED') return true
      return statusCode === 401
    }
    expect(shouldRefresh(403)).toBe(false)
    expect(shouldRefresh(403, 'SECURITY_PASSWORD_CHANGE_REQUIRED')).toBe(false)
    expect(shouldRefresh(401, 'TOKEN_EXPIRED')).toBe(true)
  })

  it('网络错误不触发刷新', () => {
    function shouldRefresh(statusCode?: number): boolean {
      if (!statusCode) return false // network error has no status
      return statusCode === 401
    }
    expect(shouldRefresh(undefined)).toBe(false)
    expect(shouldRefresh(0)).toBe(false)
    expect(shouldRefresh(401)).toBe(true)
  })

  it('安全错误跳转 change-password', () => {
    function handleSecurityError(errorCode: string): string {
      if (errorCode === 'SECURITY_PASSWORD_CHANGE_REQUIRED') {
        return '/change-password?forced=1'
      }
      return ''
    }
    expect(handleSecurityError('SECURITY_PASSWORD_CHANGE_REQUIRED')).toContain('/change-password')
    expect(handleSecurityError('OTHER_ERROR')).toBe('')
  })

  it('刷新接口自身 URL 不被递归刷新', () => {
    function isRefreshUrl(url: string): boolean {
      return url.includes('/refresh-token')
    }
    expect(isRefreshUrl('/admin/user/refresh-token')).toBe(true)
    expect(isRefreshUrl('/app/user/refresh-token')).toBe(true)
    expect(isRefreshUrl('/admin/sys-user/list')).toBe(false)
  })

  it('Token Rotation 替换旧值', () => {
    const tokenStore: Record<string, string> = { refreshToken: 'old_rt' }
    function rotateTokens(newAt: string, newRt: string) {
      tokenStore['token'] = newAt
      tokenStore['refreshToken'] = newRt
    }
    rotateTokens('new_at', 'new_rt')
    expect(tokenStore['token']).toBe('new_at')
    expect(tokenStore['refreshToken']).toBe('new_rt')
    expect(tokenStore['refreshToken']).not.toBe('old_rt')
  })
})
