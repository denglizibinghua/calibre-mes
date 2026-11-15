import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createWorkOrderScanner, getScanActionRoute, parsePositiveSafeId } from '../workOrderScan'
import type { ScanAction, ScanResolveVO } from '@/types/scan'

vi.mock('@/api/scan', () => ({ resolveWorkOrderScanApi: vi.fn() }))

function response(actions: ScanAction[] = ['WORK_ORDER_DETAIL']): ScanResolveVO {
  return { businessType: 'WORK_ORDER', workOrderId: 42, workOrderNo: 'WO-42', actions, pendingInspectionCount: 1 }
}

let platform: Record<string, ReturnType<typeof vi.fn>>
beforeEach(() => {
  platform = {
    scanCode: vi.fn((options: any) => options.success({ result: 'BIKE-MES:1:WORK_ORDER:42', path: '/pages/evil' })),
    navigateTo: vi.fn((options: any) => options.success()),
    showActionSheet: vi.fn((options: any) => options.success({ tapIndex: 0 })),
    showModal: vi.fn((options: any) => options.success({ confirm: true })),
    openSetting: vi.fn((options: any) => options.complete()),
    showToast: vi.fn(),
  }
  vi.stubGlobal('uni', platform)
})

describe('工单扫码', () => {
  it.each([
    ['WORK_ORDER_DETAIL', '/pages/work-order/detail?id=42'],
    ['WORK_PROCESS', '/pages/work-process/index?workOrderId=42'],
    ['INSPECTION_PENDING', '/pages/inspection/index?workOrderId=42&stage=pending'],
  ] as const)('后端允许 %s 时只跳固定页面', async (action, url) => {
    const resolve = vi.fn().mockResolvedValue(response([action]))
    await createWorkOrderScanner(resolve)()
    expect(platform.scanCode).toHaveBeenCalledWith(expect.objectContaining({ scanType: ['barCode', 'qrCode'], onlyFromCamera: true }))
    expect(resolve).toHaveBeenCalledWith({ content: 'BIKE-MES:1:WORK_ORDER:42' })
    expect(platform.navigateTo).toHaveBeenCalledWith(expect.objectContaining({ url }))
    expect(platform.showActionSheet).not.toHaveBeenCalled()
  })

  it('完整编号条形码原样发送；多入口仅显示后端允许动作', async () => {
    platform.scanCode.mockImplementation((options: any) => options.success({ result: 'WO-2026-42' }))
    platform.showActionSheet.mockImplementation((options: any) => options.success({ tapIndex: 1 }))
    const resolve = vi.fn().mockResolvedValue(response(['WORK_ORDER_DETAIL', 'INSPECTION_PENDING']))
    await createWorkOrderScanner(resolve)()
    expect(resolve).toHaveBeenCalledWith({ content: 'WO-2026-42' })
    expect(platform.showActionSheet).toHaveBeenCalledWith(expect.objectContaining({ itemList: ['查看工单详情', '查看待质检任务'] }))
    expect(platform.navigateTo).toHaveBeenCalledWith(expect.objectContaining({ url: '/pages/inspection/index?workOrderId=42&stage=pending' }))
  })

  it.each(['', ' '.repeat(3), 'A'.repeat(257)])('本地拒绝空或超长内容', async (content) => {
    platform.scanCode.mockImplementation((options: any) => options.success({ result: content }))
    const resolve = vi.fn()
    await createWorkOrderScanner(resolve)()
    expect(resolve).not.toHaveBeenCalled()
    expect(platform.navigateTo).not.toHaveBeenCalled()
    expect(platform.showToast).toHaveBeenCalledOnce()
  })

  it('取消扫码静默且释放防重锁', async () => {
    platform.scanCode.mockImplementation((options: any) => options.fail({ errMsg: 'scanCode:fail cancel' }))
    const resolve = vi.fn()
    const scan = createWorkOrderScanner(resolve)
    await scan()
    await scan()
    expect(platform.scanCode).toHaveBeenCalledTimes(2)
    expect(resolve).not.toHaveBeenCalled()
    expect(platform.showToast).not.toHaveBeenCalled()
  })

  it('权限拒绝显示摄像头提示并可打开设置', async () => {
    platform.scanCode.mockImplementation((options: any) => options.fail({ errMsg: 'scanCode:fail auth deny' }))
    const resolve = vi.fn()
    await createWorkOrderScanner(resolve)()
    expect(platform.showModal).toHaveBeenCalledWith(expect.objectContaining({ title: '需要摄像头权限', confirmText: '打开设置' }))
    expect(platform.openSetting).toHaveBeenCalledOnce()
    expect(resolve).not.toHaveBeenCalled()
    expect(platform.showToast).not.toHaveBeenCalled()
  })

  it('其他扫描失败提示重试', async () => {
    platform.scanCode.mockImplementation((options: any) => options.fail({ errMsg: 'scanCode:fail unavailable' }))
    await createWorkOrderScanner(vi.fn())()
    expect(platform.showToast).toHaveBeenCalledWith(expect.objectContaining({ title: '扫码失败，请重试' }))
  })

  it('解析失败沿用请求层提示并允许再次扫码', async () => {
    const resolve = vi.fn().mockRejectedValue(new Error('您无权访问该工单的任务'))
    const scan = createWorkOrderScanner(resolve)
    await scan()
    await scan()
    expect(resolve).toHaveBeenCalledTimes(2)
    expect(platform.showToast).not.toHaveBeenCalled()
    expect(platform.navigateTo).not.toHaveBeenCalled()
  })

  it('扫描、解析、入口选择和跳转期间始终防重，完成后可再次扫描', async () => {
    let scannerOptions: any
    let chooseOptions: any
    let navigateOptions: any
    let finishResolve!: (value: ScanResolveVO) => void
    platform.scanCode.mockImplementation((options: any) => { scannerOptions = options })
    platform.showActionSheet.mockImplementation((options: any) => { chooseOptions = options })
    platform.navigateTo.mockImplementation((options: any) => { navigateOptions = options })
    const resolve = vi.fn(() => new Promise<ScanResolveVO>((done) => { finishResolve = done }))
    const scan = createWorkOrderScanner(resolve)
    const running = scan()
    await scan()
    scannerOptions.success({ result: 'WO-42' })
    await Promise.resolve()
    await scan()
    finishResolve(response(['WORK_ORDER_DETAIL', 'WORK_PROCESS']))
    await Promise.resolve()
    await scan()
    chooseOptions.success({ tapIndex: 1 })
    await Promise.resolve()
    await scan()
    expect(platform.scanCode).toHaveBeenCalledOnce()
    navigateOptions.success()
    await running
    const next = scan()
    expect(platform.scanCode).toHaveBeenCalledTimes(2)
    scannerOptions.fail({ errMsg: 'scanCode:fail cancel' })
    await next
  })

  it('取消入口选择静默且可以重新扫码', async () => {
    platform.showActionSheet.mockImplementation((options: any) => options.fail({ errMsg: 'showActionSheet:fail cancel' }))
    const scan = createWorkOrderScanner(vi.fn().mockResolvedValue(response(['WORK_ORDER_DETAIL', 'WORK_PROCESS'])))
    await scan()
    await scan()
    expect(platform.scanCode).toHaveBeenCalledTimes(2)
    expect(platform.showToast).not.toHaveBeenCalled()
    expect(platform.navigateTo).not.toHaveBeenCalled()
  })

  it.each([
    { ...response(), actions: ['https://evil.test'] },
    { ...response(), businessType: 'EQUIPMENT' },
    { ...response(), workOrderId: 0 },
    { ...response(), workOrderId: 9007199254740992 },
    { ...response(), actions: [] },
  ])('异常响应不能绕过路由白名单', async (value) => {
    await createWorkOrderScanner(vi.fn().mockResolvedValue(value))()
    expect(platform.navigateTo).not.toHaveBeenCalled()
    expect(platform.showToast).toHaveBeenCalledOnce()
  })

  it.each(['0', '-1', '1.2', '1e2', ' 1', '01', 'Infinity', '9007199254740992', '', undefined])('页面参数 %s 不能作为正安全整数', (value) => {
    expect(parsePositiveSafeId(value)).toBeNull()
  })
  it('支持正安全整数边界并拒绝任意路由枚举', () => {
    expect(parsePositiveSafeId('1')).toBe(1)
    expect(parsePositiveSafeId('9007199254740991')).toBe(9007199254740991)
    expect(getScanActionRoute('/pages/evil' as ScanAction, 42)).toBeNull()
  })
})
