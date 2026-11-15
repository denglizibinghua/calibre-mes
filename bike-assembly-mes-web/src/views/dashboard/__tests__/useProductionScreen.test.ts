// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { getDashboardScreen, type DashboardScreen } from '@/api/dashboard'
import { useProductionScreen } from '../useProductionScreen'

vi.mock('@/api/dashboard', () => ({ getDashboardScreen: vi.fn() }))
const api = vi.mocked(getDashboardScreen)
let app: App | undefined
let host: HTMLDivElement

function fixture(marker: string): DashboardScreen {
  return { generatedAt: marker, summary: { activeWorkOrders: 1 } } as DashboardScreen
}
function mount() {
  let state!: ReturnType<typeof useProductionScreen>
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ setup() { state = useProductionScreen(); return () => h('div') } })
  app.mount(host)
  return state
}
async function settle() { await Promise.resolve(); await nextTick() }

beforeEach(() => {
  vi.useFakeTimers()
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
  api.mockReset()
})
afterEach(() => {
  app?.unmount()
  app = undefined
  host?.remove()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('production screen refresh lifecycle', () => {
  it('keeps the latest successful data visible when a later refresh fails', async () => {
    api.mockResolvedValueOnce(fixture('last success')).mockRejectedValueOnce(new Error('暂时离线'))
    const state = mount()
    await settle()
    await state.refresh()
    expect(state.data.value?.generatedAt).toBe('last success')
    expect(state.error.value).toBe('暂时离线')
    expect(state.loading.value).toBe(false)
  })

  it('ignores a slow response for a previously selected filter', async () => {
    let oldResponse!: (value: DashboardScreen) => void
    let newResponse!: (value: DashboardScreen) => void
    api.mockImplementationOnce(() => new Promise(resolve => { oldResponse = resolve }))
      .mockImplementationOnce(() => new Promise(resolve => { newResponse = resolve }))
    const state = mount()
    state.days.value = 14
    await nextTick()
    newResponse(fixture('14-day data'))
    await settle()
    oldResponse(fixture('old 7-day data'))
    await settle()
    expect(state.data.value?.generatedAt).toBe('14-day data')
    expect(api).toHaveBeenLastCalledWith({ days: 14 })
  })

  it('pauses automatic requests and cleans up timers when the page is left', async () => {
    api.mockResolvedValue(fixture('loaded'))
    const state = mount()
    await settle()
    state.autoRefresh.value = false
    await nextTick()
    await vi.advanceTimersByTimeAsync(60000)
    expect(api).toHaveBeenCalledTimes(1)
    state.autoRefresh.value = true
    await nextTick()
    await vi.advanceTimersByTimeAsync(30000)
    expect(api).toHaveBeenCalledTimes(2)
    app?.unmount()
    app = undefined
    expect(vi.getTimerCount()).toBe(0)
    await vi.advanceTimersByTimeAsync(60000)
    expect(api).toHaveBeenCalledTimes(2)
  })

  it('retains the successful filter attribution after a failed filter change and updates it on retry', async () => {
    api.mockResolvedValueOnce(fixture('old data')).mockRejectedValueOnce(new Error('离线')).mockResolvedValueOnce(fixture('new data'))
    const state = mount()
    await settle()
    state.days.value = 30
    state.workUnitId.value = '99'
    await nextTick()
    await settle()
    expect(state.appliedQuery.value).toEqual({ days: 7, workUnitId: '' })
    expect(state.filtersPending.value).toBe(true)
    expect(state.data.value?.generatedAt).toBe('old data')
    await state.refresh()
    expect(state.appliedQuery.value).toEqual({ days: 30, workUnitId: '99' })
    expect(state.filtersPending.value).toBe(false)
    expect(state.data.value?.generatedAt).toBe('new data')
  })
})
