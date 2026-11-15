import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { disconnect } from '@/utils/useWebSocket'
import { useUserStore, type UserInfo } from '../user'

vi.mock('@/utils/useWebSocket', () => ({ disconnect: vi.fn() }))

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.clearAllMocks()
})

describe('authentication WebSocket ownership', () => {
  it('closes the previous account connection before saving new credentials', () => {
    localStorage.setItem('token', 'previous-account')
    const user = useUserStore()
    vi.mocked(disconnect).mockImplementationOnce(() => {
      expect(localStorage.getItem('token')).toBe('previous-account')
    })
    const info: UserInfo = {
      id: 2, username: 'operator', nickname: '操作员', roleId: 1,
      token: 'new-account', permissions: [], dataScope: 'SELF',
    }
    user.login(info)
    expect(disconnect).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('token')).toBe('new-account')
    expect(user.userInfo?.id).toBe(2)
  })

  it('logout closes the transport and clears all persisted authentication', () => {
    for (const key of ['token', 'refreshToken', 'userInfo']) localStorage.setItem(key, 'old')
    const user = useUserStore()
    user.logout()
    expect(disconnect).toHaveBeenCalledTimes(1)
    for (const key of ['token', 'refreshToken', 'userInfo']) expect(localStorage.getItem(key)).toBeNull()
    expect(user.isLoggedIn).toBe(false)
  })
})
