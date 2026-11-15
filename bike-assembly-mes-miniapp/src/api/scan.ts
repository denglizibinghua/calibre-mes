import http from '@/utils/request'
import type { ScanResolveDTO, ScanResolveVO } from '@/types/scan'

/** 只解析工单和可访问入口，不领取任务或变更业务状态。 */
export function resolveWorkOrderScanApi(data: ScanResolveDTO): Promise<ScanResolveVO> {
  return http.post<ScanResolveVO>('/app/scan/resolve', { content: data.content }, {
    loadingText: '识别工单...',
  })
}
