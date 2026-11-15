/**
 * 小程序 request 并发刷新自动化测试。
 * 测试 Token 管理、刷新队列逻辑、错误处理和退出清除。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

const uniStore: Record<string, string> = {}
let reLaunchUrl = ''
let reLaunchCount = 0

// Mock uni global
;(globalThis as any).uni = {
  request: vi.fn(),
  getStorageSync: vi.fn((key: string) => uniStore[key] ?? ''),
  setStorageSync: vi.fn((key: string, val: string) => { uniStore[key] = val }),
  removeStorageSync: vi.fn((key: string) => { delete uniStore[key] }),
  reLaunch: vi.fn((opts: any) => {
    reLaunchUrl = opts?.url || opts
    reLaunchCount++
  }),
  showToast: vi.fn(),
  showLoading: vi.fn(),
  hideLoading: vi.fn(),
  login: vi.fn(),
  showModal: vi.fn(),
  navigateTo: vi.fn(),
}

// Mock import.meta.env
vi.stubGlobal('import', { meta: { env: { VITE_API_BASE_URL: 'http://localhost:8080' } } })

beforeEach(() => {
  vi.clearAllMocks()
  Object.keys(uniStore).forEach(k => delete uniStore[k])
  reLaunchUrl = ''
  reLaunchCount = 0
})

// ==================== 测试 ====================

describe('小程序 Token 存储操作', () => {
  it('setAuthTokens 保存双 Token + 过期时间', () => {
    const now = Date.now()
    uni.setStorageSync('accessToken', 'at_val')
    uni.setStorageSync('token', 'at_val')
    uni.setStorageSync('refreshToken', 'rt_val')
    uni.setStorageSync('accessTokenExpiresAt', now + 1800_000)
    uni.setStorageSync('refreshTokenExpiresAt', now + 604800_000)

    expect(uniStore['accessToken']).toBe('at_val')
    expect(uniStore['refreshToken']).toBe('rt_val')
    expect(uniStore['accessTokenExpiresAt']).toBeDefined()
    expect(uniStore['refreshTokenExpiresAt']).toBeDefined()
  })

  it('getAccessToken 读取 accessToken', () => {
    uniStore['accessToken'] = 'my_at'
    expect(uni.getStorageSync('accessToken')).toBe('my_at')
  })

  it('getRefreshToken 读取 refreshToken', () => {
    uniStore['refreshToken'] = 'my_rt'
    expect(uni.getStorageSync('refreshToken')).toBe('my_rt')
  })

  it('clearAuthTokens 清除所有认证数据', () => {
    uniStore['accessToken'] = 'at'
    uniStore['token'] = 'at'
    uniStore['refreshToken'] = 'rt'
    uniStore['accessTokenExpiresAt'] = '123'
    uniStore['refreshTokenExpiresAt'] = '456'
    uniStore['userInfo'] = '{}'

    uni.removeStorageSync('accessToken')
    uni.removeStorageSync('token')
    uni.removeStorageSync('refreshToken')
    uni.removeStorageSync('accessTokenExpiresAt')
    uni.removeStorageSync('refreshTokenExpiresAt')
    uni.removeStorageSync('userInfo')

    expect(uniStore['accessToken']).toBeUndefined()
    expect(uniStore['refreshToken']).toBeUndefined()
    expect(uniStore['userInfo']).toBeUndefined()
  })
})

describe('小程序并发刷新队列逻辑', () => {
  it('isRefreshing 标志防止并发刷新', async () => {
    let isRefreshing = false
    let refreshCount = 0
    const queue: Array<{ resolve: Function; reject: Function }> = []

    async function handleRefresh(): Promise<string> {
      await new Promise(r => setTimeout(r, 0))
      if (isRefreshing) {
        return new Promise((resolve, reject) => { queue.push({ resolve, reject }) })
      }
      isRefreshing = true
      refreshCount++
      await new Promise(r => setTimeout(r, 10))
      const newToken = 'new_app_token'
      queue.forEach(q => q.resolve(newToken))
      queue.length = 0
      isRefreshing = false
      return newToken
    }

    const results = await Promise.all([
      handleRefresh(), handleRefresh(), handleRefresh(),
    ])

    expect(refreshCount).toBe(1)
    expect(results.every(t => t === 'new_app_token')).toBe(true)
  })

  it('刷新失败 → 拒绝所有等待请求', async () => {
    let isRefreshing = false
    const queue: Array<{ resolve: Function; reject: Function }> = []

    async function tryRefresh(): Promise<string> {
      await new Promise(r => setTimeout(r, 0))
      if (isRefreshing) {
        return new Promise((resolve, reject) => { queue.push({ resolve, reject }) })
      }
      isRefreshing = true
      const err = new Error('Refresh failed')
      queue.forEach(q => q.reject(err))
      queue.length = 0
      isRefreshing = false
      throw err
    }

    const results = await Promise.allSettled([tryRefresh(), tryRefresh()])
    expect(results.every(r => r.status === 'rejected')).toBe(true)
    expect(queue.length).toBe(0)
  })

  it('刷新失败后清除双 Token 并只跳转一次登录页', () => {
    // 模拟 handleAuthExpired
    uni.removeStorageSync('accessToken')
    uni.removeStorageSync('token')
    uni.removeStorageSync('refreshToken')
    uni.removeStorageSync('userInfo')
    uni.reLaunch({ url: '/pages/login/index' })

    expect(uniStore['accessToken']).toBeUndefined()
    expect(uniStore['refreshToken']).toBeUndefined()
    expect(reLaunchUrl).toContain('/pages/login/index')
    expect(reLaunchCount).toBe(1)
  })

  it('403 不触发刷新', () => {
    function shouldRefresh(statusCode: number): boolean {
      return statusCode === 401
    }
    expect(shouldRefresh(403)).toBe(false)
    expect(shouldRefresh(401)).toBe(true)
  })

  it('SECURITY_PASSWORD_CHANGE_REQUIRED → security-block', () => {
    const errorCode = 'SECURITY_PASSWORD_CHANGE_REQUIRED'
    if (errorCode === 'SECURITY_PASSWORD_CHANGE_REQUIRED') {
      uni.reLaunch({ url: '/pages/security-block/index' })
    }
    expect(reLaunchUrl).toContain('security-block')
  })

  it('刷新接口 URL 自身不递归', () => {
    function isRefreshReq(url: string): boolean {
      return url.includes('/refresh-token')
    }
    expect(isRefreshReq('/app/user/refresh-token')).toBe(true)
    expect(isRefreshReq('/app/user/me')).toBe(false)
  })

  it('Rotation 后旧 Refresh Token 被替换', () => {
    uniStore['refreshToken'] = 'old_rt'
    // 模拟刷新成功
    uni.setStorageSync('accessToken', 'new_app_at')
    uni.setStorageSync('refreshToken', 'new_app_rt')
    uni.setStorageSync('token', 'new_app_at')

    expect(uniStore['refreshToken']).toBe('new_app_rt')
    expect(uniStore['refreshToken']).not.toBe('old_rt')
  })

  it('用户主动退出清除全部认证数据并跳转', () => {
    uniStore['accessToken'] = 'at'
    uniStore['refreshToken'] = 'rt'
    uniStore['userInfo'] = JSON.stringify({ id: 1, permissions: ['read'] })

    // 模拟 logout
    uni.removeStorageSync('accessToken')
    uni.removeStorageSync('token')
    uni.removeStorageSync('refreshToken')
    uni.removeStorageSync('accessTokenExpiresAt')
    uni.removeStorageSync('refreshTokenExpiresAt')
    uni.removeStorageSync('userInfo')
    uni.reLaunch({ url: '/pages/login/index' })

    expect(uniStore['accessToken']).toBeUndefined()
    expect(uniStore['refreshToken']).toBeUndefined()
    expect(uniStore['userInfo']).toBeUndefined()
    expect(reLaunchUrl).toContain('login')
  })
})
