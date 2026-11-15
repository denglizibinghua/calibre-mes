import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { disconnect } from '@/utils/useWebSocket'

const mocks = vi.hoisted(() => {
  const responseUse = vi.fn()
  const create = vi.fn()
  const service = Object.assign(vi.fn().mockResolvedValue({}), {
    interceptors: { request: { use: vi.fn() }, response: { use: responseUse } },
    get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(),
  })
  create.mockReturnValue(service)
  return { responseUse, create, service, post: vi.fn() }
})
vi.mock('axios', () => ({ default: { create: mocks.create, post: mocks.post } }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), warning: vi.fn() } }))
vi.mock('@/utils/useWebSocket', () => ({ disconnect: vi.fn() }))

let rejectResponse: (error: unknown) => Promise<unknown>
beforeEach(async () => {
  vi.resetModules()
  vi.clearAllMocks()
  localStorage.clear()
  window.history.replaceState({}, '', '/login')
  localStorage.setItem('token', 'expired')
  localStorage.setItem('userInfo', '{}')
  vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080')
  await import('../request')
  rejectResponse = mocks.responseUse.mock.calls[0]![1]
})

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('HTTP 401 WebSocket cleanup', () => {
  it('failed refresh closes WebSocket and clears authentication', async () => {
    await expect(rejectResponse({ response: { status: 401 }, config: { url: '/admin/list', headers: {} } }))
      .rejects.toThrow('No refresh token')
    expect(disconnect).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
  })

  it('a second 401 after refresh also ends the session', async () => {
    const error = { response: { status: 401 }, config: { url: '/admin/list', _retry: true, headers: {} } }
    await expect(rejectResponse(error)).rejects.toBe(error)
    expect(disconnect).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('successful access token rotation keeps the active WebSocket', async () => {
    localStorage.setItem('refreshToken', 'refresh')
    mocks.post.mockResolvedValueOnce({ data: { code: 1, data: { accessToken: 'fresh', refreshToken: 'rotated' } } })
    await rejectResponse({ response: { status: 401 }, config: { url: '/admin/list', headers: {} } })
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ baseURL: '' }))
    expect(mocks.post).toHaveBeenCalledWith('/admin/user/refresh-token', null, expect.any(Object))
    expect(disconnect).not.toHaveBeenCalled()
    expect(localStorage.getItem('token')).toBe('fresh')
    expect(mocks.service).toHaveBeenCalledWith(expect.objectContaining({ headers: { token: 'fresh' } }))
  })
})
