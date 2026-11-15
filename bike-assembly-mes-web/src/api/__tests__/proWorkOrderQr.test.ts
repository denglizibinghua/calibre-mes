import { expect, it, vi } from 'vitest'
import request from '../request'
import { getWorkOrderQrCode } from '../proWorkOrder'

vi.mock('../request', () => ({ default: { get: vi.fn() } }))

it('通过 GET 精确工单路径查询二维码元数据', async () => {
  const metadata = { workOrderId: 42, workOrderNo: 'WO-42', content: 'BIKE-MES:1:WORK_ORDER:42' }
  vi.mocked(request.get).mockResolvedValue(metadata)
  await expect(getWorkOrderQrCode(42)).resolves.toEqual(metadata)
  expect(request.get).toHaveBeenCalledWith('/admin/pro-work-order/42/qr-code')
})
