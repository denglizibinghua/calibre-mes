import http from '@/utils/request'
import type { QmsInspectionVO, QmsInspectionItemVO, EnterInspectionResultDTO } from '@/types/inspection'

/**
 * 质量检验 API — 调用 /app/qms-inspection 和 /app/qms-inspection-item 端点。
 */

/**
 * 获取质检单列表（非分页，返回纯数组）。
 * GET /app/qms-inspection/list?scope=mine
 *
 * 支持按工单 ID 筛选，不传则获取全部。
 */
export function getInspectionListApi(workOrderId?: number, scope: string = 'mine', status?: '0' | '1' | '2'): Promise<QmsInspectionVO[]> {
  let url = `/app/qms-inspection/list?scope=${encodeURIComponent(scope)}`
  if (workOrderId) {
    url += `&workOrderId=${workOrderId}`
  }
  if (status !== undefined) url += `&status=${encodeURIComponent(status)}`
  return http.get<QmsInspectionVO[]>(url, { showLoading: false })
}

/**
 * 获取质检单详情。
 * GET /app/qms-inspection/{id}
 */
export function getInspectionDetailApi(id: number): Promise<QmsInspectionVO> {
  return http.get<QmsInspectionVO>(`/app/qms-inspection/${id}`, { showLoading: false })
}

/**
 * 录入检验结果：更新已有待检质检单，status: 0 → 1。
 * PUT /app/qms-inspection/{id}/result
 */
export function enterInspectionResultApi(id: number, data: EnterInspectionResultDTO): Promise<QmsInspectionVO> {
  return http.put<QmsInspectionVO>(
    `/app/qms-inspection/${id}/result`,
    data as unknown as Record<string, unknown>,
    { showLoading: true, loadingText: '提交中...' },
  )
}

/**
 * 确认质检结果：status: 1 → 2，联动工序状态机。
 * PUT /app/qms-inspection/{id}/confirm
 */
export function confirmInspectionApi(id: number): Promise<QmsInspectionVO> {
  return http.put<QmsInspectionVO>(
    `/app/qms-inspection/${id}/confirm`,
    {},
    { showLoading: true, loadingText: '确认中...' },
  )
}

/**
 * 获取质检项参考数据（非分页，可按工序筛选）。
 * GET /app/qms-inspection-item/list
 */
export function getInspectionItemListApi(processId?: number): Promise<QmsInspectionItemVO[]> {
  const url = processId
    ? `/app/qms-inspection-item/list?processId=${processId}`
    : '/app/qms-inspection-item/list'
  return http.get<QmsInspectionItemVO[]>(url, { showLoading: false })
}
