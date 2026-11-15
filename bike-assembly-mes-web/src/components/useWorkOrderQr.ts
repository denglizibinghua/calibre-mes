import { computed, onScopeDispose, ref, watch, type Ref } from 'vue'
import { toDataURL } from 'qrcode'
import { getWorkOrderQrCode } from '@/api/proWorkOrder'
import type { WorkOrderQrCodeVO } from '@/types/scan'

export function workOrderQrFilename(workOrderNo: string, workOrderId: number): string {
  const safeNo = Array.from(workOrderNo.replace(/[<>:"/\\|?*\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/g, '_'))
    .slice(0, 100).join('').trim().replace(/[. ]+$/g, '')
  return `工单二维码_${safeNo || workOrderId}.png`
}

function requestErrorMessage(error: unknown): string {
  const failure = error as { response?: { data?: { msg?: string; message?: string } }; request?: unknown; message?: string }
  const serverMessage = failure?.response?.data?.msg || failure?.response?.data?.message
  if (serverMessage) return serverMessage
  if (failure?.request && !failure.response) return '网络异常，请检查连接后重试'
  return failure?.message || '工单二维码加载失败，请重试'
}

/** 一次打开对应一次查询；元数据和 PNG 都通过版本检查后才一起展示。 */
export function useWorkOrderQr(visible: Ref<boolean>, workOrderId: Ref<number | undefined>) {
  const metadata = ref<WorkOrderQrCodeVO>()
  const imageUrl = ref('')
  const loading = ref(false)
  const error = ref('')
  let version = 0

  function reset() {
    metadata.value = undefined
    imageUrl.value = ''
    error.value = ''
    loading.value = false
  }

  async function load() {
    const current = ++version
    reset()
    if (!visible.value) return
    const id = workOrderId.value
    if (!Number.isSafeInteger(id) || id == null || id <= 0) {
      error.value = '工单编号无效，请关闭后重新选择工单'
      return
    }
    loading.value = true
    const isCurrent = () => current === version && visible.value && workOrderId.value === id
    let rendering = false
    try {
      const data = await getWorkOrderQrCode(id)
      if (!isCurrent()) return
      if (data?.workOrderId !== id || typeof data.workOrderNo !== 'string' || !data.workOrderNo.trim()
        || typeof data.content !== 'string' || !data.content.trim()) {
        throw new Error('工单二维码数据无效，请重试')
      }
      rendering = true
      const png = await toDataURL(data.content, {
        type: 'image/png', width: 320, margin: 4, errorCorrectionLevel: 'M',
        color: { dark: '#000000', light: '#ffffff' }
      })
      if (!isCurrent()) return
      metadata.value = data
      imageUrl.value = png
    } catch (failure) {
      if (isCurrent()) error.value = rendering ? '二维码图片生成失败，请重试' : requestErrorMessage(failure)
    } finally {
      if (isCurrent()) loading.value = false
    }
  }

  const canDownload = computed(() => visible.value && !loading.value && !error.value
    && !!imageUrl.value && metadata.value?.workOrderId === workOrderId.value)

  function download() {
    if (!canDownload.value || !metadata.value) return
    const anchor = document.createElement('a')
    anchor.href = imageUrl.value
    anchor.download = workOrderQrFilename(metadata.value.workOrderNo, metadata.value.workOrderId)
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
  }

  watch([visible, workOrderId], load, { immediate: true, flush: 'sync' })
  onScopeDispose(() => { ++version })

  return { metadata, imageUrl, loading, error, canDownload, reload: load, download }
}
