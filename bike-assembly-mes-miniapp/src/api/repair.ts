import http from '@/utils/request'
import type { EqmRepairOrderVO, EqmRepairOrderDTO } from '@/types/repair'

/**
 * 设备报修 API — 调用 /app/eqm-repair-order 端点。
 */

/**
 * 获取维修单列表（非分页，返回纯数组）。
 * GET /app/eqm-repair-order/list
 *
 * scope: mine=我提交的, pending=待我维修, all=全部
 * relation: submitted=我提交的, assigned=待我维修
 */
export function getRepairListApi(params?: { scope?: string; relation?: string; equipmentId?: number }): Promise<EqmRepairOrderVO[]> {
  // 注意：微信小程序运行时没有 URLSearchParams，必须手拼 query 字符串
  const parts: string[] = []
  if (params?.scope) parts.push(`scope=${encodeURIComponent(params.scope)}`)
  if (params?.relation) parts.push(`relation=${encodeURIComponent(params.relation)}`)
  if (params?.equipmentId) parts.push(`equipmentId=${params.equipmentId}`)
  const qs = parts.length > 0 ? '?' + parts.join('&') : ''
  return http.get<EqmRepairOrderVO[]>(`/app/eqm-repair-order/list${qs}`, { showLoading: false })
}

/**
 * 获取维修单详情。
 * GET /app/eqm-repair-order/{id}
 */
export function getRepairDetailApi(id: number): Promise<EqmRepairOrderVO> {
  return http.get<EqmRepairOrderVO>(`/app/eqm-repair-order/${id}`, { showLoading: false })
}

/**
 * 提交设备报修。
 * POST /app/eqm-repair-order
 *
 * 报修人由服务端自动填充。
 */
export function createRepairApi(data: EqmRepairOrderDTO): Promise<EqmRepairOrderVO> {
  return http.post<EqmRepairOrderVO>(
    '/app/eqm-repair-order',
    data as unknown as Record<string, unknown>,
    { showLoading: true, loadingText: '提交中...' },
  )
}

/**
 * 接单维修。
 * PUT /app/eqm-repair-order/{id}/accept
 *
 * 维修处理人由服务端自动填充。
 */
export function acceptRepairApi(id: number): Promise<EqmRepairOrderVO> {
  return http.put<EqmRepairOrderVO>(
    `/app/eqm-repair-order/${id}/accept`,
    {},
    { showLoading: true, loadingText: '接单中...' },
  )
}

/**
 * 完成维修。
 * PUT /app/eqm-repair-order/{id}/complete
 */
export function completeRepairApi(id: number, data: { resultDesc: string }): Promise<EqmRepairOrderVO> {
  return http.put<EqmRepairOrderVO>(
    `/app/eqm-repair-order/${id}/complete`,
    data as unknown as Record<string, unknown>,
    { showLoading: true, loadingText: '完成中...' },
  )
}
