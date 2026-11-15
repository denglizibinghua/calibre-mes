import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, type App } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import axios from 'axios'
import Layout from '../index.vue'
import { useUserStore } from '@/stores/user'
import { connect, disconnect } from '@/utils/useWebSocket'

const mocks = vi.hoisted(() => ({ resetPermission: vi.fn(), replace: vi.fn(), post: vi.fn() }))
vi.mock('axios', () => ({ default: { post: mocks.post } }))
vi.mock('@/stores/permission', () => ({ usePermissionStore: () => ({
  sidebarMenus: [], resetPermission: mocks.resetPermission, getFirstAccessibleRoute: () => '/',
}) }))
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/', meta: {} }), useRouter: () => ({ replace: mocks.replace }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() } }))

let app: App | undefined
let host: HTMLDivElement
let sockets: ReturnType<typeof vi.fn>
beforeEach(() => {
  vi.clearAllMocks()
  vi.useFakeTimers()
  vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.com/gateway/')
  localStorage.clear()
  sockets = vi.fn(function () { return { readyState: 0, close: vi.fn() } })
  vi.stubGlobal('WebSocket', Object.assign(sockets, { OPEN: 1, CONNECTING: 0 }))
  const pinia = createPinia()
  setActivePinia(pinia)
  useUserStore().login({ id: 1, username: 'old', nickname: 'old', roleId: 1, token: 'captured-token', permissions: [], dataScope: 'ALL' })
  host = document.createElement('div')
  app = createApp({ ...Layout, render: () => null })
  app.use(pinia)
})
afterEach(() => {
  app?.unmount(); app = undefined; host?.remove()
  disconnect(); vi.useRealTimers(); vi.unstubAllGlobals(); vi.unstubAllEnvs()
})

describe('logout ownership while remote request is pending', () => {
  it('clears credentials and routes away synchronously so a new page cannot reconnect', async () => {
    let finish!: () => void
    mocks.post.mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve }))
    const instance = app!.mount(host)
    const state = (instance.$ as unknown as { setupState: { handleLogout: () => Promise<void> } }).setupState
    const pending = state.handleLogout()
    expect(localStorage.getItem('token')).toBeNull()
    expect(useUserStore().isLoggedIn).toBe(false)
    expect(mocks.resetPermission).toHaveBeenCalledTimes(1)
    expect(mocks.replace).toHaveBeenCalledWith('/login')
    connect()
    expect(sockets).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(axios.post).toHaveBeenCalled())
    expect(axios.post).toHaveBeenCalledWith('https://api.example.com/gateway/admin/user/logout', null, {
      headers: { token: 'captured-token' }, timeout: 10000,
    })
    finish()
    await pending
  })

  it.each(['network failure', 'timeout'])('keeps local logout complete after %s', async reason => {
    mocks.post.mockRejectedValueOnce(new Error(reason))
    const instance = app!.mount(host)
    const state = (instance.$ as unknown as { setupState: { handleLogout: () => Promise<void> } }).setupState
    await expect(state.handleLogout()).resolves.toBeUndefined()
    connect()
    expect(localStorage.getItem('token')).toBeNull()
    expect(mocks.replace).toHaveBeenCalledWith('/login')
    expect(sockets).not.toHaveBeenCalled()
  })
})
