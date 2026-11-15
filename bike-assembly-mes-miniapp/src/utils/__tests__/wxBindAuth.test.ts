import { beforeEach, describe, expect, it, vi } from 'vitest'
import { wxBindApi } from '@/api/auth'
import http from '@/utils/request'
const storage: Record<string, string> = {}
const requestMock = vi.fn((options: any) => options.success({ statusCode: 200, data: { code: 1, data: { userId: 4 } } }))
beforeEach(() => {
  Object.keys(storage).forEach((key) => delete storage[key])
  storage.accessToken = 'old-formal-access-token'
  requestMock.mockClear()
  vi.stubGlobal('uni', {
    request: requestMock,
    getStorageSync: vi.fn((key: string) => storage[key] || ''),
    setStorageSync: vi.fn((key: string, value: string) => { storage[key] = value }),
    removeStorageSync: vi.fn((key: string) => { delete storage[key] }),
    showLoading: vi.fn(), hideLoading: vi.fn(), showToast: vi.fn(),
  })
})
describe('微信绑定使用临时凭据', () => {
  it('旧 accessToken 不覆盖绑定凭据，临时凭据不写成正式登录态', async () => {
    await wxBindApi({ bindType: 'existing', username: 'inspector01', password: 'test-only' }, 'binding-temp-token')
    expect(requestMock.mock.calls[0][0].header.token).toBe('binding-temp-token')
    expect(storage.accessToken).toBe('old-formal-access-token')
    expect(storage.token).toBeUndefined()
  })
  it('普通请求仍自动使用已有 accessToken', async () => {
    await http.get('/app/user/me')
    expect(requestMock.mock.calls[0][0].header.token).toBe('old-formal-access-token')
  })
})
