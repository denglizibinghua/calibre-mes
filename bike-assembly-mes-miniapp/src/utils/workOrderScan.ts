import { resolveWorkOrderScanApi } from '@/api/scan'
import type { ScanAction, ScanResolveDTO, ScanResolveVO } from '@/types/scan'

const actionLabels: Record<ScanAction, string> = {
  WORK_ORDER_DETAIL: '查看工单详情',
  WORK_PROCESS: '进入工序报工',
  INSPECTION_PENDING: '查看待质检任务',
}

export function parsePositiveSafeId(value: unknown): number | null {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

/** 只使用固定枚举映射，绝不接受扫码结果中的 path 或 URL。 */
export function getScanActionRoute(action: ScanAction, workOrderId: number): string | null {
  if (!Number.isSafeInteger(workOrderId) || workOrderId <= 0) return null
  switch (action) {
    case 'WORK_ORDER_DETAIL': return `/pages/work-order/detail?id=${workOrderId}`
    case 'WORK_PROCESS': return `/pages/work-process/index?workOrderId=${workOrderId}`
    case 'INSPECTION_PENDING': return `/pages/inspection/index?workOrderId=${workOrderId}&stage=pending`
    default: return null
  }
}

function errorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'errMsg' in error) return String(error.errMsg)
  return error instanceof Error ? error.message : ''
}

function toast(title: string): void {
  uni.showToast({ title, icon: 'none', duration: 2000 })
}

async function handleCameraFailure(error: unknown): Promise<void> {
  const message = errorMessage(error)
  if (/cancel/i.test(message)) return
  if (/auth.*(deny|denied)|permission|authorize|scope\.camera|camera.*(deny|denied)/i.test(message)) {
    await new Promise<void>((resolve) => {
      uni.showModal({
        title: '需要摄像头权限',
        content: '请在微信设置中开启摄像头权限后重新扫码。',
        confirmText: '打开设置',
        success: (result) => {
          if (result.confirm) uni.openSetting({ complete: () => resolve() })
          else resolve()
        },
        fail: () => resolve(),
      })
    })
    return
  }
  toast('扫码失败，请重试')
}

/** 一个实例覆盖扫描、解析、入口选择和跳转的完整防重周期。 */
export function createWorkOrderScanner(
  resolveScan: (data: ScanResolveDTO) => Promise<ScanResolveVO> = resolveWorkOrderScanApi,
): () => Promise<void> {
  let busy = false
  return async () => {
    if (busy) return
    busy = true
    try {
      let content: string
      try {
        content = await new Promise<string>((resolve, reject) => {
          uni.scanCode({
            onlyFromCamera: true,
            scanType: ['barCode', 'qrCode'],
            success: (result) => resolve(result.result),
            fail: reject,
          })
        })
      } catch (error) {
        await handleCameraFailure(error)
        return
      }
      if (typeof content !== 'string' || !content.trim() || content.length > 256) {
        toast('码内容无效，请扫描工单二维码或工单编号条形码')
        return
      }
      let result: ScanResolveVO
      try {
        result = await resolveScan({ content })
      } catch {
        // 请求层已展示后端 msg，并统一处理网络异常和登录失效。
        return
      }
      if (!result || result.businessType !== 'WORK_ORDER' || !Array.isArray(result.actions)
        || !result.actions.length || result.actions.some((action) => !getScanActionRoute(action, result.workOrderId))) {
        toast('暂无可访问的工单入口，请重新扫码')
        return
      }
      const actions = [...new Set(result.actions)]
      let action = actions[0]
      if (actions.length > 1) {
        try {
          const index = await new Promise<number>((resolve, reject) => {
            uni.showActionSheet({
              itemList: actions.map((item) => actionLabels[item]),
              success: (selection) => resolve(selection.tapIndex),
              fail: reject,
            })
          })
          action = actions[index]
        } catch (error) {
          if (!/cancel/i.test(errorMessage(error))) toast('入口选择失败，请重试')
          return
        }
      }
      const url = getScanActionRoute(action, result.workOrderId)
      if (!url) {
        toast('暂无可访问的工单入口，请重新扫码')
        return
      }
      try {
        await new Promise<void>((resolve, reject) => uni.navigateTo({ url, success: () => resolve(), fail: reject }))
      } catch {
        toast('页面打开失败，请重试')
      }
    } finally {
      busy = false
    }
  }
}
