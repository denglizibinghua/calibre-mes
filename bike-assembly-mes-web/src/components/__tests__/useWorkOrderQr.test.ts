import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref, type EffectScope } from 'vue'
import { toDataURL } from 'qrcode'
import type { QRCodeToDataURLOptions } from 'qrcode'
import { getWorkOrderQrCode } from '@/api/proWorkOrder'
import { useWorkOrderQr, workOrderQrFilename } from '../useWorkOrderQr'
import type { WorkOrderQrCodeVO } from '@/types/scan'

vi.mock('qrcode', () => ({ toDataURL: vi.fn() }))
vi.mock('@/api/proWorkOrder', () => ({ getWorkOrderQrCode: vi.fn() }))
const api = vi.mocked(getWorkOrderQrCode)
// qrcode 的重载同时支持回调和 Promise；本组件只使用 Promise。
const render = vi.mocked(toDataURL as (content: string, options?: QRCodeToDataURLOptions) => Promise<string>)
let scope: EffectScope

function metadata(id = 1): WorkOrderQrCodeVO {
  return { workOrderId: id, workOrderNo: `WO-${id}`, content: `BIKE-MES:1:WORK_ORDER:${id}` }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function setup(open = true, id: number | undefined = 1) {
  const visible = ref(open)
  const workOrderId = ref(id)
  scope = effectScope()
  const state = scope.run(() => useWorkOrderQr(visible, workOrderId))!
  return { ...state, visible, workOrderId }
}
async function settle() {
  for (let i = 0; i < 5; i++) await Promise.resolve()
}

beforeEach(() => {
  api.mockReset().mockResolvedValue(metadata())
  render.mockReset().mockResolvedValue('data:image/png;base64,png')
})
afterEach(() => { scope?.stop(); vi.restoreAllMocks() })

describe('工单二维码查询和渲染', () => {
  it('渲染服务端原始 content，保留白色留白和黑色码点', async () => {
    api.mockResolvedValue({ ...metadata(), content: 'server-owned-content' })
    const state = setup()
    expect(state.canDownload.value).toBe(false)
    await settle()
    expect(api).toHaveBeenCalledWith(1)
    expect(render).toHaveBeenCalledWith('server-owned-content', expect.objectContaining({
      type: 'image/png', margin: 4, color: { dark: '#000000', light: '#ffffff' }
    }))
    expect(state.metadata.value?.workOrderNo).toBe('WO-1')
    expect(state.canDownload.value).toBe(true)
  })

  it('弹窗关闭时不请求；同一工单每次重新打开均查询后端', async () => {
    const state = setup(false)
    expect(api).not.toHaveBeenCalled()
    state.visible.value = true
    await settle()
    state.visible.value = false
    expect(state.metadata.value).toBeUndefined()
    expect(state.imageUrl.value).toBe('')
    expect(state.canDownload.value).toBe(false)
    api.mockRejectedValueOnce(new Error('工单不存在或已删除'))
    state.visible.value = true
    await settle()
    expect(api).toHaveBeenCalledTimes(2)
    expect(state.error.value).toBe('工单不存在或已删除')
    expect(state.imageUrl.value).toBe('')
    expect(state.canDownload.value).toBe(false)
  })

  it('连续切换工单时忽略旧元数据响应', async () => {
    const old = deferred<WorkOrderQrCodeVO>()
    api.mockReturnValueOnce(old.promise).mockResolvedValueOnce(metadata(2))
    const state = setup()
    state.workOrderId.value = 2
    await settle()
    old.resolve(metadata(1))
    await settle()
    expect(state.metadata.value?.workOrderId).toBe(2)
    expect(render).toHaveBeenCalledTimes(1)
    expect(state.canDownload.value).toBe(true)
  })

  it('忽略旧工单尚未完成的 PNG 渲染', async () => {
    const oldImage = deferred<string>()
    render.mockReturnValueOnce(oldImage.promise)
    const state = setup()
    await settle()
    api.mockResolvedValueOnce(metadata(2))
    state.workOrderId.value = 2
    expect(state.imageUrl.value).toBe('')
    expect(state.canDownload.value).toBe(false)
    await settle()
    oldImage.resolve('data:image/png;base64,old')
    await settle()
    expect(state.metadata.value?.workOrderId).toBe(2)
    expect(state.imageUrl.value).toBe('data:image/png;base64,png')
  })

  it('关闭再打开同一工单也不接受上一次的延迟响应', async () => {
    const old = deferred<WorkOrderQrCodeVO>()
    api.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ ...metadata(), workOrderNo: 'fresh' })
    const state = setup()
    state.visible.value = false
    state.visible.value = true
    await settle()
    old.resolve(metadata())
    await settle()
    expect(state.metadata.value?.workOrderNo).toBe('fresh')
  })

  it('关闭弹窗后忽略完成的渲染和错误', async () => {
    const image = deferred<string>()
    render.mockReturnValueOnce(image.promise)
    const state = setup()
    await settle()
    state.visible.value = false
    image.reject(new Error('encoding failed'))
    await settle()
    expect(state.error.value).toBe('')
    expect(state.imageUrl.value).toBe('')
    expect(state.loading.value).toBe(false)
    expect(state.canDownload.value).toBe(false)
  })

  it('组件销毁后忽略未完成的响应', async () => {
    const response = deferred<WorkOrderQrCodeVO>()
    api.mockReturnValueOnce(response.promise)
    const state = setup()
    scope.stop()
    response.resolve(metadata())
    await settle()
    expect(render).not.toHaveBeenCalled()
    expect(state.metadata.value).toBeUndefined()
  })

  it.each([0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1, NaN])('拒绝无效 ID %s', async id => {
    const state = setup(true, id)
    await settle()
    expect(api).not.toHaveBeenCalled()
    expect(state.error.value).toContain('工单编号无效')
    expect(state.canDownload.value).toBe(false)
  })

  it('接口失败时显示后端文案并允许重新加载', async () => {
    api.mockRejectedValueOnce({ response: { data: { msg: '您无权访问该工单的任务' } } })
    const state = setup()
    await settle()
    expect(state.error.value).toBe('您无权访问该工单的任务')
    expect(state.canDownload.value).toBe(false)
    await state.reload()
    expect(state.error.value).toBe('')
    expect(state.canDownload.value).toBe(true)
  })

  it('网络错误使用网络文案', async () => {
    api.mockRejectedValueOnce({ request: {}, message: 'Network Error' })
    const state = setup()
    await settle()
    expect(state.error.value).toContain('网络异常')
    expect(state.canDownload.value).toBe(false)
  })

  it('拒绝返回其他工单的元数据', async () => {
    api.mockResolvedValueOnce(metadata(2))
    const state = setup()
    await settle()
    expect(render).not.toHaveBeenCalled()
    expect(state.error.value).toContain('数据无效')
    expect(state.canDownload.value).toBe(false)
  })

  it('图片转换失败时不保留可下载的元数据和图片', async () => {
    render.mockRejectedValueOnce(new Error('encoding failed'))
    const state = setup()
    await settle()
    expect(state.error.value).toBe('二维码图片生成失败，请重试')
    expect(state.metadata.value).toBeUndefined()
    expect(state.imageUrl.value).toBe('')
    expect(state.canDownload.value).toBe(false)
  })

  it('下载当前 PNG 使用清理后的真实工单号，失败状态无法下载', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('工单二维码_WO__42.png')
      expect(this.href).toBe('data:image/png;base64,png')
    })
    api.mockResolvedValueOnce({ ...metadata(), workOrderNo: 'WO:/42' })
    const state = setup()
    state.download()
    expect(click).not.toHaveBeenCalled()
    await settle()
    state.download()
    expect(click).toHaveBeenCalledTimes(1)
    expect(document.querySelector('a')).toBeNull()
    state.visible.value = false
    state.download()
    expect(click).toHaveBeenCalledTimes(1)
  })
})

describe('PNG 文件名', () => {
  it('清理路径、控制字符及结尾空白，不产生路径', () => {
    const name = workOrderQrFilename('../WO\\:<42>?*|\u0000\u202e. ', 1)
    expect(name).not.toMatch(/[\\/:<>?*|\u0000\u202e]/)
    expect(name).toMatch(/\.png$/)
  })
  it('空编号回退到真实 ID，长编号保留有效 Unicode', () => {
    expect(workOrderQrFilename('... ', 12)).toBe('工单二维码_12.png')
    expect(workOrderQrFilename('🚲'.repeat(200), 12)).toBe(`工单二维码_${'🚲'.repeat(100)}.png`)
  })
})
