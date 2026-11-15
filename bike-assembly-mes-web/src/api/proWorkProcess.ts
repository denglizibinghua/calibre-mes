import request from './request'
import type { PageResult, ProWorkProcess } from '@/types/api'

const BASE = '/admin/pro-work-process'

export function getProWorkProcessList(params?: Record<string, unknown>) {
  return request.get<PageResult<ProWorkProcess>>(`${BASE}/list`, { params })
}

export function getProWorkProcessById(id: number) {
  return request.get(`${BASE}/${id}`)
}

export function updateProWorkProcess(id: number, data: { equipmentId?: number; version?: number }) {
  return request.put(`${BASE}/${id}`, data)
}

// ==================== 状态转换操作 ====================
// qcPassWorkProcess / qcFailWorkProcess 已废弃：
// 所有质检判定必须通过质检单确认流程（小程序端 /app/qms-inspection/{id}/confirm）

/** [幂等保护] 管理端强制返工——需填写原因，不校验工单执行人归属 */
export function reworkStartWorkProcess(id: number, reason: string, idempotencyKey?: string) {
  return request.put(`${BASE}/${id}/rework-start`, null, {
    params: { reason },
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  })
}

/** [幂等保护] 管理端强制返工完成——remark 为可选备注 */
export function reworkCompleteWorkProcess(id: number, remark?: string, idempotencyKey?: string) {
  return request.put(`${BASE}/${id}/rework-complete`, null, {
    params: { remark },
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  })
}

export function skipWorkProcess(id: number, remark?: string) {
  return request.put(`${BASE}/${id}/skip`, null, { params: { remark } })
}

export function cancelWorkProcess(id: number, remark?: string) {
  return request.put(`${BASE}/${id}/cancel`, null, { params: { remark } })
}
