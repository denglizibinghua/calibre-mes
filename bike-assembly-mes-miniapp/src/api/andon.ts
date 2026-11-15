import http from '@/utils/request'
import type { AndonAlertVO, AndonAlertDTO } from '@/types/andon'

/**
 * 安灯告警 API — 调用 /app/andon-alert 端点。
 */

/**
 * 获取安灯告警列表（非分页，返回纯数组）。
 * GET /app/andon-alert/list
 *
 * scope: mine=我提交的, pending=待我处理, all=全部
 * relation: submitted=我提交的, assigned=待我处理
 */
export function getAndonListApi(params?: { scope?: string; relation?: string; workOrderId?: number }): Promise<AndonAlertVO[]> {
  // 注意：微信小程序运行时没有 URLSearchParams，必须手拼 query 字符串
  const parts: string[] = []
  if (params?.scope) parts.push(`scope=${encodeURIComponent(params.scope)}`)
  if (params?.relation) parts.push(`relation=${encodeURIComponent(params.relation)}`)
  if (params?.workOrderId) parts.push(`workOrderId=${params.workOrderId}`)
  const qs = parts.length > 0 ? '?' + parts.join('&') : ''
  return http.get<AndonAlertVO[]>(`/app/andon-alert/list${qs}`, { showLoading: false })
}

/**
 * 获取安灯告警详情。
 * GET /app/andon-alert/{id}
 */
export function getAndonDetailApi(id: number): Promise<AndonAlertVO> {
  return http.get<AndonAlertVO>(`/app/andon-alert/${id}`, { showLoading: false })
}

/**
 * 上报安灯告警。
 * POST /app/andon-alert
 *
 * 告警编号由服务端自动生成。
 */
export function createAndonApi(data: AndonAlertDTO): Promise<AndonAlertVO> {
  return http.post<AndonAlertVO>(
    '/app/andon-alert',
    data as unknown as Record<string, unknown>,
    { showLoading: true, loadingText: '上报中...' },
  )
}

/**
 * 受理安灯告警。
 * PUT /app/andon-alert/{id}/accept
 *
 * 处理人由服务端自动填充。
 */
export function acceptAndonApi(id: number): Promise<AndonAlertVO> {
  return http.put<AndonAlertVO>(
    `/app/andon-alert/${id}/accept`,
    {},
    { showLoading: true, loadingText: '受理中...' },
  )
}

/**
 * 关闭安灯告警。
 * PUT /app/andon-alert/{id}/close
 */
export function closeAndonApi(id: number): Promise<AndonAlertVO> {
  return http.put<AndonAlertVO>(
    `/app/andon-alert/${id}/close`,
    {},
    { showLoading: true, loadingText: '关闭中...' },
  )
}

/**
 * 升级安灯告警。
 * PUT /app/andon-alert/{id}/escalate
 */
export function escalateAndonApi(id: number, remark?: string): Promise<AndonAlertVO> {
  return http.put<AndonAlertVO>(
    `/app/andon-alert/${id}/escalate`,
    remark ? { remark } : {},
    { showLoading: true, loadingText: '升级中...' },
  )
}
