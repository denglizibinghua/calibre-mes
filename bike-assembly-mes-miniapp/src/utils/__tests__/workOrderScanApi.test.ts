import { beforeEach, describe, expect, it, vi } from 'vitest'
import http from '@/utils/request'
import { resolveWorkOrderScanApi } from '@/api/scan'
import { getWorkProcessListApi } from '@/api/workProcess'
import { getInspectionListApi } from '@/api/inspection'

vi.mock('@/utils/request', () => ({ default: { get: vi.fn().mockResolvedValue([]), post: vi.fn().mockResolvedValue({}) } }))
beforeEach(() => vi.clearAllMocks())

describe('扫码接口契约', () => {
  it('解析仅发送内容，使用统一请求封装', async () => {
    await resolveWorkOrderScanApi({ content: 'BIKE-MES:1:WORK_ORDER:42' })
    expect(http.post).toHaveBeenCalledWith('/app/scan/resolve', { content: 'BIKE-MES:1:WORK_ORDER:42' }, expect.any(Object))
  })
  it('工序精确工单过滤与原有调用兼容', async () => {
    await getWorkProcessListApi('all', 42)
    await getWorkProcessListApi()
    expect(http.get).toHaveBeenNthCalledWith(1, '/app/pro-work-process/list?scope=all&workOrderId=42', expect.any(Object))
    expect(http.get).toHaveBeenNthCalledWith(2, '/app/pro-work-process/list?scope=mine', expect.any(Object))
  })
  it('质检服务端同时限制工单、身份范围与待检状态', async () => {
    await getInspectionListApi(42, 'mine', '0')
    await getInspectionListApi()
    expect(http.get).toHaveBeenNthCalledWith(1, '/app/qms-inspection/list?scope=mine&workOrderId=42&status=0', expect.any(Object))
    expect(http.get).toHaveBeenNthCalledWith(2, '/app/qms-inspection/list?scope=mine', expect.any(Object))
  })
})
