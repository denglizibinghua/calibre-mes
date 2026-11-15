import ts from 'typescript'
import * as vue from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import andonSource from '@/pages/andon/index.vue?raw'
import repairSource from '@/pages/repair/index.vue?raw'

function loadPage(kind: 'ANDON' | 'REPAIR', token = 'access-token', loggedIn = true) {
  const hooks: Record<string, () => void> = {}
  const handlers: Record<string, () => void> = {}
  let connected: () => void = () => {}
  const offMessage = vi.fn(), offConnected = vi.fn()
  const socket = {
    connectWs: vi.fn(), closeWs: vi.fn(),
    onWsMessage: vi.fn((biz: string, callback: () => void) => { handlers[biz] = callback; return offMessage }),
    onWsConnected: vi.fn((callback: () => void) => { connected = callback; return offConnected }),
  }
  const apis = {
    getAndonListApi: vi.fn().mockResolvedValue([]), getRepairListApi: vi.fn().mockResolvedValue([]),
    getBaseEquipmentListApi: vi.fn().mockResolvedValue([]), getWorkOrderListApi: vi.fn().mockResolvedValue([]),
  }
  const user = { isLoggedIn: loggedIn, getAccessToken: () => token }
  const source = (kind === 'ANDON' ? andonSource : repairSource).match(/<script setup lang="ts">([\s\S]*?)<\/script>/)![1]
  const script = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText
  const scope = vue.effectScope()
  const state = scope.run(() => new Function('require', 'exports', script + `\nreturn { list: ${kind === 'ANDON' ? 'alertList' : 'repairList'}, loading, loadError };`)((name: string) => {
    if (name === 'vue') return vue
    if (name === '@dcloudio/uni-app') return Object.fromEntries(['onShow', 'onHide', 'onUnload', 'onPullDownRefresh'].map(hook => [hook, (callback: () => void) => { hooks[hook] = callback }]))
    if (name === '@/stores/user') return { useUserStore: () => user }
    if (name === '@/utils/permission') return { hasPermission: () => true }
    if (name === '@/utils/websocket') return socket
    if (name.startsWith('@/api/')) return apis
    if (name.startsWith('@/types/')) return {}
    throw new Error(`未配置依赖 ${name}`)
  }, {}))
  return { hooks, socket, apis, handlers, reconnect: () => connected(), offMessage, offConnected, scope, state }
}

beforeEach(() => {
  vi.stubGlobal('uni', { showToast: vi.fn(), reLaunch: vi.fn(), navigateBack: vi.fn(), stopPullDownRefresh: vi.fn() })
})
afterEach(() => vi.unstubAllGlobals())

describe.each(['ANDON', 'REPAIR'] as const)('%s 页面实时刷新', kind => {
  it('恢复补查的新结果不会被迟到旧响应覆盖，隐藏后不再写入', async () => {
    const page = loadPage(kind)
    const api = kind === 'ANDON' ? page.apis.getAndonListApi : page.apis.getRepairListApi
    let oldResolve!: (value: unknown[]) => void
    let freshResolve!: (value: unknown[]) => void
    api.mockImplementationOnce(() => new Promise(resolve => { oldResolve = resolve }))
    api.mockImplementationOnce(() => new Promise(resolve => { freshResolve = resolve }))
    page.hooks.onShow()
    page.reconnect()
    freshResolve([{ status: 'fresh' }])
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(page.state.list.value).toEqual([{ status: 'fresh' }])
    oldResolve([{ status: 'old' }])
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(page.state.list.value).toEqual([{ status: 'fresh' }])
    expect(page.state.loading.value).toBe(false)
    let hiddenResolve!: (value: unknown[]) => void
    api.mockImplementationOnce(() => new Promise(resolve => { hiddenResolve = resolve }))
    page.reconnect()
    page.hooks.onHide()
    hiddenResolve([{ status: 'hidden' }])
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(page.state.list.value).toEqual([{ status: 'fresh' }])
    const closeCount = page.socket.closeWs.mock.calls.length
    page.hooks.onUnload()
    expect(page.socket.closeWs).toHaveBeenCalledTimes(closeCount)
    page.scope.stop()
  })

  it('仅订阅本业务，连接恢复重新调用现有列表 API，隐藏及卸载释放全部回调', async () => {
    const page = loadPage(kind)
    const api = kind === 'ANDON' ? page.apis.getAndonListApi : page.apis.getRepairListApi
    page.hooks.onShow()
    await Promise.resolve()
    expect(page.socket.onWsMessage).toHaveBeenCalledWith(kind, expect.any(Function))
    expect(page.socket.onWsMessage).toHaveBeenCalledTimes(1)
    expect(api).toHaveBeenCalledOnce()
    page.handlers[kind]()
    page.reconnect()
    expect(api).toHaveBeenCalledTimes(3)
    page.hooks.onHide()
    expect(page.offMessage).toHaveBeenCalledOnce()
    expect(page.offConnected).toHaveBeenCalledOnce()
    page.handlers[kind]()
    page.reconnect()
    expect(api).toHaveBeenCalledTimes(3)
    expect(page.socket.closeWs).toHaveBeenCalled()
    page.hooks.onShow()
    page.hooks.onUnload()
    expect(page.offMessage).toHaveBeenCalledTimes(2)
    page.scope.stop()
  })

  it('本地 token 已清理但 store 仍显示登录时不创建匿名连接', () => {
    const page = loadPage(kind, '')
    page.hooks.onShow()
    expect(uni.reLaunch).toHaveBeenCalledWith({ url: '/pages/login/index' })
    expect(page.socket.connectWs).not.toHaveBeenCalled()
    page.scope.stop()
  })

  it('底层连接失败不阻断已有API加载，展示明确本地提示', () => {
    const page = loadPage(kind)
    page.socket.connectWs.mockImplementation(() => { throw new Error('实时连接暂不可用，将自动重试') })
    expect(() => page.hooks.onShow()).not.toThrow()
    expect(uni.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '实时连接暂不可用，将自动重试' }))
    expect(kind === 'ANDON' ? page.apis.getAndonListApi : page.apis.getRepairListApi).toHaveBeenCalledOnce()
    page.scope.stop()
  })
})
