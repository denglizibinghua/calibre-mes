import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, type App, type Component } from 'vue'
import request from '@/api/request'
import Andon from '../production/andonAlert.vue'
import WorkOrder from '../production/workOrder.vue'
import Repair from '../production/repairOrder.vue'
import Inspection from '../quality/inspection.vue'
import Inventory from '../warehouse/inventory.vue'
import StockDoc from '../warehouse/stockDoc.vue'

const socket = vi.hoisted(() => ({ connected: [] as Array<() => Promise<void>> }))
vi.mock('@/api/request', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
vi.mock('@/utils/useWebSocket', () => ({
  useWebSocket: () => ({ connect: vi.fn(), onWsMessage: () => vi.fn() }),
  onWsConnected: (handler: () => Promise<void>) => {
    socket.connected.push(handler)
    return () => { socket.connected = socket.connected.filter(value => value !== handler) }
  },
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() }, ElMessageBox: {} }))

interface Result { records: Array<{ id: number }>; total: number }
interface PageState { tableData: Result['records']; total: number; loading: boolean; fetchList: () => Promise<void> }
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const pages: Array<[string, Component, string]> = [
  ['andon', Andon, '/admin/andon-alert/list'],
  ['work-order', WorkOrder, '/admin/pro-work-order/list'],
  ['repair', Repair, '/admin/eqm-repair-order/list'],
  ['inspection', Inspection, '/admin/qms-inspection/list'],
  ['inventory', Inventory, '/admin/wms-inventory/list'],
  ['stock-doc', StockDoc, '/admin/wms-stock-doc/list'],
]
let app: App | undefined
let host: HTMLDivElement
function mount(component: Component) {
  host = document.createElement('div')
  document.body.appendChild(host)
  // Execute the real SFC setup, fetchList and lifecycle; presentation is irrelevant here.
  app = createApp({ ...component, render: () => null })
  const instance = app.mount(host)
  return (instance.$ as unknown as { setupState: PageState }).setupState
}
beforeEach(() => { vi.clearAllMocks(); socket.connected = [] })
afterEach(() => { app?.unmount(); app = undefined; host?.remove() })

describe.each(pages)('%s connection recovery queries', (_name, component, endpoint) => {
  function prepare() {
    const old = deferred<Result>()
    const fresh = deferred<Result>()
    const pending = [old, fresh]
    vi.mocked(request.get).mockImplementation((url: string) => {
      return url === endpoint ? pending.shift()!.promise : Promise.resolve({ records: [], total: 0 })
    })
    const state = mount(component)
    return { old, fresh, state }
  }

  it('keeps recovery data when an older pre-disconnect snapshot arrives later', async () => {
    const { old, fresh, state } = prepare()
    const recovered = socket.connected[0]!()
    fresh.resolve({ records: [{ id: 2 }], total: 20 })
    await recovered
    expect(state.tableData).toEqual([{ id: 2 }])
    old.resolve({ records: [{ id: 1 }], total: 10 })
    await old.promise
    await Promise.resolve()
    expect(state.tableData).toEqual([{ id: 2 }])
    expect(state.total).toBe(20)
  })

  it('an older failure cannot finish the newer recovery loading state', async () => {
    const { old, fresh, state } = prepare()
    const recovered = socket.connected[0]!()
    old.reject(new Error('old request failed'))
    await old.promise.catch(() => {})
    await Promise.resolve()
    expect(state.loading).toBe(true)
    fresh.resolve({ records: [{ id: 2 }], total: 20 })
    await recovered
    expect(state.loading).toBe(false)
  })

  it('ignores pending results after unmount and removes recovery subscriptions', async () => {
    const { old, state } = prepare()
    app!.unmount()
    app = undefined
    const loadingAtUnmount = state.loading
    old.resolve({ records: [{ id: 1 }], total: 10 })
    await old.promise
    await Promise.resolve()
    expect(state.tableData).toEqual([])
    expect(state.total).toBe(0)
    expect(state.loading).toBe(loadingAtUnmount)
    expect(socket.connected).toHaveLength(0)
  })
})
