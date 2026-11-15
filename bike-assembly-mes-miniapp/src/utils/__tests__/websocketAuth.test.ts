import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { closeWs } = vi.hoisted(() => ({ closeWs: vi.fn() }))
vi.mock('../websocket', () => ({ closeWs }))

beforeEach(() => {
  vi.resetModules()
  vi.useFakeTimers()
  vi.clearAllMocks()
  setActivePinia(createPinia())
  vi.stubGlobal('uni', {
    getStorageSync: vi.fn(() => ''), removeStorageSync: vi.fn(), setStorageSync: vi.fn(),
    request: vi.fn(), showToast: vi.fn(), reLaunch: vi.fn(), showLoading: vi.fn(), hideLoading: vi.fn(),
  })
})
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

describe('认证状态清理实时连接', () => {
  it('login切换账号、clearAuthTokens和logout均关闭旧连接', async () => {
    const { useUserStore } = await import('@/stores/user')
    const user = useUserStore()
    user.login({ id: 1, token: 'new-token' } as Parameters<typeof user.login>[0])
    expect(closeWs).toHaveBeenCalledOnce()
    user.clearAuthTokens()
    expect(closeWs).toHaveBeenCalledTimes(2)
    user.logout()
    expect(closeWs).toHaveBeenCalledTimes(3)
  })

  it('HTTP401刷新失败时关闭连接，认证失效清理停止重连', async () => {
    const { request, handleAuthExpired } = await import('../request')
    vi.mocked(uni.request).mockImplementation((options: any) => {
      options.success?.({ statusCode: 401, data: { code: 401, msg: '登录已失效' } })
      return {} as UniApp.RequestTask
    })
    await expect(request({ url: '/app/andon-alert/list' })).rejects.toThrow('登录已失效')
    expect(closeWs).toHaveBeenCalled()
    closeWs.mockClear()
    handleAuthExpired()
    expect(closeWs).toHaveBeenCalled()
  })

  it('Access Token正常刷新成功时保留活跃页面的连接与订阅', async () => {
    vi.mocked(uni.getStorageSync).mockImplementation((key: string) => key === 'refreshToken' ? 'refresh' : 'access')
    const { request } = await import('../request')
    let attempts = 0
    vi.mocked(uni.request).mockImplementation((options: any) => {
      if (options.url.endsWith('/refresh-token')) {
        options.success?.({ statusCode: 200, data: { code: 1, data: { accessToken: 'updated', refreshToken: 'rotated' } } })
      } else if (attempts++ === 0) {
        options.success?.({ statusCode: 401, data: { code: 401 } })
      } else {
        options.success?.({ statusCode: 200, data: { code: 1, data: [] } })
      }
      return {} as UniApp.RequestTask
    })
    await expect(request({ url: '/app/andon-alert/list' })).resolves.toEqual([])
    expect(closeWs).not.toHaveBeenCalled()
    expect(uni.setStorageSync).toHaveBeenCalledWith('accessToken', 'updated')
  })
})
