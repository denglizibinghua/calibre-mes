import http from '@/utils/request'
import type { WorkProcessVO } from '@/types/workProcess'

/**
 * 工序流转 API — 调用 /app/pro-work-process 端点。
 */

/**
 * 获取全部工序流转记录（非分页，返回纯数组）。
 * scope=mine 返回当前用户的记录，scope=all 返回全部（需权限）。
 */
export function getWorkProcessListApi(scope: string = 'mine', workOrderId?: number): Promise<WorkProcessVO[]> {
  let url = `/app/pro-work-process/list?scope=${encodeURIComponent(scope)}`
  if (workOrderId !== undefined) url += `&workOrderId=${encodeURIComponent(workOrderId)}`
  return http.get<WorkProcessVO[]>(url, { showLoading: false })
}

/**
 * 获取单条工序流转记录详情。
 */
export function getWorkProcessDetailApi(id: number): Promise<WorkProcessVO> {
  return http.get<WorkProcessVO>(`/app/pro-work-process/${id}`, { showLoading: false })
}

// ==================== 状态机操作 (V3.3) ====================

/**
 * 开始工序：READY → IN_PROGRESS。
 */
export function startWorkProcessApi(id: number): Promise<WorkProcessVO> {
  return http.put<WorkProcessVO>(`/app/pro-work-process/${id}/start`, {}, {
    showLoading: true,
    loadingText: '开始中...',
  })
}

/**
 * 报工并送检：IN_PROGRESS → WAIT_INSPECTION。
 * reportQty 通过 URL query string 传递（后端 @RequestParam）
 */
export function reportWorkProcessApi(id: number, reportQty: number, qcResultNote?: string): Promise<WorkProcessVO> {
  let url = `/app/pro-work-process/${id}/report?reportQty=${encodeURIComponent(reportQty)}`
  if (qcResultNote) {
    url += `&qcResultNote=${encodeURIComponent(qcResultNote)}`
  }
  return http.put<WorkProcessVO>(url, {}, {
    showLoading: true,
    loadingText: '报工中...',
  })
}

/**
 * 开始返工：REJECTED → REWORK。
 */
export function reworkStartWorkProcessApi(id: number): Promise<WorkProcessVO> {
  return http.put<WorkProcessVO>(`/app/pro-work-process/${id}/rework-start`, {}, {
    showLoading: true,
    loadingText: '发起返工...',
  })
}

/**
 * 返工完成：REWORK → WAIT_INSPECTION。不累加报工数量。
 */
export function reworkCompleteWorkProcessApi(id: number): Promise<WorkProcessVO> {
  return http.put<WorkProcessVO>(
    `/app/pro-work-process/${id}/rework-complete`,
    {},
    { showLoading: true, loadingText: '返工完成...' }
  )
}
