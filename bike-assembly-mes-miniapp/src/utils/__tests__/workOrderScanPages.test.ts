import ts from 'typescript'
import * as vue from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import inspectionSource from '@/pages/inspection/index.vue?raw'
import workProcessSource from '@/pages/work-process/index.vue?raw'
import workOrderSource from '@/pages/work-order/detail.vue?raw'
import { getScanActionRoute, parsePositiveSafeId } from '../workOrderScan'
import * as inspectionFilters from '../inspectionFilters'

vi.mock('@/api/scan', () => ({ resolveWorkOrderScanApi: vi.fn() }))

/** 执行真实页面的 setup 脚本，以生命周期钩子和 API 替身验证定位行为。 */
function loadPage(path: string, permissions: string[], members: string[]) {
  const hooks: Record<string, (options?: any) => void> = {}
  const apis = {
    getWorkOrderListApi: vi.fn().mockResolvedValue([]),
    getWorkOrderDetailApi: vi.fn().mockResolvedValue({ workOrderId: 42, itemId: 1 }),
    getWorkProcessListApi: vi.fn().mockResolvedValue([]),
    getInspectionListApi: vi.fn().mockResolvedValue([]),
    getBaseItemListApi: vi.fn().mockResolvedValue([]),
    getBaseProcessListApi: vi.fn().mockResolvedValue([]),
    getBaseEquipmentListApi: vi.fn().mockResolvedValue([]),
  }
  const sources: Record<string, string> = {
    'inspection/index.vue': inspectionSource,
    'work-process/index.vue': workProcessSource,
    'work-order/detail.vue': workOrderSource,
  }
  const source = sources[path].match(/<script setup lang="ts">([\s\S]*?)<\/script>/)![1]
  const script = ts.transpileModule(`${source}\nexport const page = { ${members.join(', ')} }`, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText
  const modules: Record<string, unknown> = {
    vue,
    '@dcloudio/uni-app': {
      onLoad: (callback: any) => { hooks.load = callback },
      onShow: (callback: any) => { hooks.show = callback },
      onPullDownRefresh: (callback: any) => { hooks.refresh = callback },
    },
    '@/stores/user': { useUserStore: () => ({ isLoggedIn: true }) },
    '@/utils/permission': { hasPermission: (permission: string) => permissions.includes(permission), hasAllDataScope: () => false },
    '@/utils/workOrderScan': { getScanActionRoute, parsePositiveSafeId },
    '@/utils/inspectionFilters': inspectionFilters,
  }
  const exports: any = {}
  new Function('require', 'exports', script)((name: string) => {
    if (name.startsWith('@/api/')) return apis
    if (name.startsWith('@/types/')) return {}
    if (name in modules) return modules[name]
    throw new Error(`未配置测试依赖：${name}`)
  }, exports)
  return { page: exports.page, hooks, apis }
}

beforeEach(() => {
  vi.stubGlobal('uni', { showToast: vi.fn(), navigateTo: vi.fn(), navigateBack: vi.fn(), reLaunch: vi.fn(), stopPullDownRefresh: vi.fn() })
})

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

describe('扫码目标页面', () => {
  it('工序清除扫码定位后的新列表不会被旧扫码查询晚返回覆盖', async () => {
    const { page, hooks, apis } = loadPage('work-process/index.vue', ['app:work-process:view'], [
      'fetchData', 'clearScanLocation', 'processList', 'scanWorkOrderId', 'loading', 'loadError',
    ])
    const oldProcesses = deferred<any[]>()
    apis.getWorkProcessListApi.mockReturnValueOnce(oldProcesses.promise)
      .mockResolvedValueOnce([{ processRecordId: 2, workOrderId: 99 }])
    hooks.load({ workOrderId: '42' })
    const oldRequest = page.fetchData()
    page.clearScanLocation()
    await vi.waitFor(() => expect(page.loading.value).toBe(false))
    oldProcesses.resolve([{ processRecordId: 1, workOrderId: 42 }])
    await oldRequest
    expect(apis.getWorkProcessListApi).toHaveBeenNthCalledWith(1, 'mine', 42)
    expect(apis.getWorkProcessListApi).toHaveBeenNthCalledWith(2, 'mine', undefined)
    expect(page.scanWorkOrderId.value).toBeNull()
    expect(page.processList.value.map((item: any) => item.processRecordId)).toEqual([2])
    expect(page.loading.value).toBe(false)
    expect(page.loadError.value).toBe('')
  })

  it('工序旧错误不会结束新请求加载或覆盖新查询状态', async () => {
    const { page, apis } = loadPage('work-process/index.vue', ['app:work-process:view'], [
      'fetchData', 'processList', 'loading', 'loadError',
    ])
    const oldProcesses = deferred<any[]>()
    const newProcesses = deferred<any[]>()
    apis.getWorkProcessListApi.mockReturnValueOnce(oldProcesses.promise).mockReturnValueOnce(newProcesses.promise)
    const oldRequest = page.fetchData()
    const newRequest = page.fetchData()
    oldProcesses.reject(new Error('旧工序请求错误'))
    await oldRequest
    expect(page.loading.value).toBe(true)
    expect(page.loadError.value).toBe('')
    newProcesses.resolve([{ processRecordId: 2, workOrderId: 99 }])
    await newRequest
    expect(page.processList.value.map((item: any) => item.processRecordId)).toEqual([2])
    expect(page.loading.value).toBe(false)
    expect(page.loadError.value).toBe('')
  })

  it('切换待检到已检后，较早的任务与工序响应不能覆盖最新结果', async () => {
    const { page, hooks, apis } = loadPage('inspection/index.vue', ['app:inspection:view'], [
      'fetchData', 'filters', 'inspectionList', 'processRefs', 'loading', 'loadError',
    ])
    const oldInspections = deferred<any[]>()
    const oldProcesses = deferred<any[]>()
    apis.getInspectionListApi.mockReturnValueOnce(oldInspections.promise)
      .mockResolvedValueOnce([{ inspectionId: 2, workOrderId: 42, status: '1' }])
    apis.getBaseProcessListApi.mockReturnValueOnce(oldProcesses.promise)
      .mockResolvedValueOnce([{ processId: 2, processName: '新工序' }])
    hooks.load({ workOrderId: '42', stage: 'pending' })
    const oldRequest = page.fetchData()
    page.filters.stage = 'inspected'
    await page.fetchData()
    oldInspections.resolve([{ inspectionId: 1, workOrderId: 42, status: '0' }])
    oldProcesses.resolve([{ processId: 1, processName: '旧工序' }])
    await oldRequest
    expect(apis.getInspectionListApi).toHaveBeenNthCalledWith(1, 42, 'mine', '0')
    expect(apis.getInspectionListApi).toHaveBeenNthCalledWith(2, 42, 'mine', undefined)
    expect(page.inspectionList.value.map((item: any) => item.inspectionId)).toEqual([2])
    expect(page.processRefs.value.map((item: any) => item.processId)).toEqual([2])
    expect(page.filters.workOrderId).toBe(42)
    expect(page.loading.value).toBe(false)
    expect(page.loadError.value).toBe('')
  })

  it('清除定位后的新查询完成后，旧请求错误不能覆盖当前页面', async () => {
    const { page, hooks, apis } = loadPage('inspection/index.vue', ['app:inspection:view'], [
      'fetchData', 'clearScanLocation', 'inspectionList', 'filters', 'scanWorkOrderId', 'loading', 'loadError',
    ])
    const oldInspections = deferred<any[]>()
    apis.getInspectionListApi.mockReturnValueOnce(oldInspections.promise)
      .mockResolvedValueOnce([{ inspectionId: 2, workOrderId: 99, status: '0' }])
    hooks.load({ workOrderId: '42', stage: 'pending' })
    const oldRequest = page.fetchData()
    page.clearScanLocation()
    await vi.waitFor(() => expect(page.loading.value).toBe(false))
    oldInspections.reject(new Error('旧请求失败'))
    await oldRequest
    expect(apis.getInspectionListApi).toHaveBeenNthCalledWith(2, undefined, 'mine', undefined)
    expect(page.scanWorkOrderId.value).toBeNull()
    expect(page.filters.workOrderId).toBe(0)
    expect(page.inspectionList.value.map((item: any) => item.inspectionId)).toEqual([2])
    expect(page.loadError.value).toBe('')
    expect(page.loading.value).toBe(false)
  })

  it('旧请求完成不结束新请求加载，也不清空新筛选或覆盖新错误', async () => {
    const { page, apis } = loadPage('inspection/index.vue', ['app:inspection:view'], [
      'fetchData', 'filters', 'inspectionList', 'loading', 'loadError',
    ])
    const oldInspections = deferred<any[]>()
    const newInspections = deferred<any[]>()
    apis.getInspectionListApi.mockReturnValueOnce(oldInspections.promise).mockReturnValueOnce(newInspections.promise)
    const oldRequest = page.fetchData()
    page.filters.workOrderId = 99
    const newRequest = page.fetchData()
    oldInspections.resolve([])
    await oldRequest
    expect(page.loading.value).toBe(true)
    expect(page.filters.workOrderId).toBe(99)
    newInspections.reject(new Error('当前请求失败'))
    await newRequest
    expect(page.loadError.value).toBe('当前请求失败')
    expect(page.loading.value).toBe(false)
    expect(page.filters.workOrderId).toBe(99)
  })

  it('纯质检账号首次、刷新及筛选切换均保留服务端工单条件，空结果不展示其他工单', async () => {
    const { page, hooks, apis } = loadPage('inspection/index.vue', ['app:inspection:view'], [
      'fetchData', 'filters', 'scanWorkOrderId', 'selectedWorkOrderLabel', 'setFilter', 'resetFilters', 'clearScanLocation',
    ])
    hooks.load({ workOrderId: '42', stage: 'pending' })
    await page.fetchData()
    await page.fetchData()
    expect(apis.getInspectionListApi).toHaveBeenNthCalledWith(1, 42, 'mine', '0')
    expect(apis.getInspectionListApi).toHaveBeenNthCalledWith(2, 42, 'mine', '0')
    expect(page.filters.workOrderId).toBe(42)
    expect(page.selectedWorkOrderLabel.value).toBe('工单 #42')
    page.setFilter('stage', 'inspected')
    expect(apis.getInspectionListApi).toHaveBeenLastCalledWith(42, 'mine', undefined)
    page.resetFilters()
    expect(page.filters.workOrderId).toBe(42)
    expect(page.scanWorkOrderId.value).toBe(42)
    expect(apis.getWorkOrderListApi).not.toHaveBeenCalled()
    expect(apis.getWorkOrderDetailApi).not.toHaveBeenCalled()
    page.clearScanLocation()
    expect(page.scanWorkOrderId.value).toBeNull()
    expect(page.filters.workOrderId).toBe(0)
    expect(apis.getInspectionListApi).toHaveBeenLastCalledWith(undefined, 'mine', undefined)
  })

  it('无工单查看权限仍能按允许的全部工序范围加载目标工单', async () => {
    const { page, hooks, apis } = loadPage('work-process/index.vue', ['app:work-process:view', 'app:work-process:view-all'], [
      'fetchData', 'activeScope', 'scanWorkOrderId', 'selectedWorkOrderLabel',
    ])
    hooks.load({ workOrderId: '42' })
    await page.fetchData()
    expect(apis.getWorkProcessListApi).toHaveBeenCalledWith('all', 42)
    expect(apis.getWorkOrderListApi).not.toHaveBeenCalled()
    expect(page.selectedWorkOrderLabel.value).toBe('工单 #42')
  })

  it('工序和工单分别使用自身 view-all 权限计算范围', async () => {
    const { page, hooks, apis } = loadPage('work-process/index.vue', ['app:work-process:view', 'app:work-process:view-all', 'app:work-order:view'], ['fetchData'])
    hooks.load({ workOrderId: '42' })
    await page.fetchData()
    expect(apis.getWorkProcessListApi).toHaveBeenCalledWith('all', 42)
    expect(apis.getWorkOrderListApi).toHaveBeenCalledWith('mine')
  })

  it('只有工单查看权限仍可加载详情，不请求工序辅助列表', async () => {
    const { page, hooks, apis } = loadPage('work-order/detail.vue', ['app:work-order:view'], ['fetchDetail', 'workOrderId', 'canViewProcesses', 'order'])
    hooks.load({ id: '42' })
    await page.fetchDetail()
    expect(apis.getWorkOrderDetailApi).toHaveBeenCalledWith(42)
    expect(apis.getWorkProcessListApi).not.toHaveBeenCalled()
    expect(apis.getBaseProcessListApi).not.toHaveBeenCalled()
    expect(page.canViewProcesses.value).toBe(false)
    expect(page.order.value.workOrderId).toBe(42)
  })

  it.each(['inspection/index.vue', 'work-process/index.vue'])('%s 拒绝不安全的扫码参数，不退回全量查询', async (path) => {
    const { page, hooks, apis } = loadPage(path, ['app:inspection:view', 'app:work-process:view'], ['fetchData', 'loadError'])
    hooks.load({ workOrderId: '9007199254740992' })
    await page.fetchData()
    expect(page.loadError.value).toContain('工单 ID 格式错误')
    expect(apis.getInspectionListApi).not.toHaveBeenCalled()
    expect(apis.getWorkProcessListApi).not.toHaveBeenCalled()
  })
})
