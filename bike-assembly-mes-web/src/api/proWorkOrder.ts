import request from './request'
import type { PageResult, ProWorkOrder } from '@/types/api'
import type { WorkOrderQrCodeVO } from '@/types/scan'

/** [前端调用] 管理端工单 API 基础路径 */
const BASE = '/admin/pro-work-order'

/**
 * [前端调用] 工单分页查询。
 * 完整路径：GET /admin/pro-work-order/list
 * [请求参数] page, size, orderNo（可选）, productName（可选）
 * [数据转换] 返回 PageResult<ProWorkOrder>，其中 records 渲染到 el-table
 */
export function getProWorkOrderList(params?: Record<string, unknown>) {
  return request.get<PageResult<ProWorkOrder>>(`${BASE}/list`, { params })
}

export function getProWorkOrderById(id: number) {
  return request.get(`${BASE}/${id}`)
}

/** 查询真实工单码；码内容由服务端统一生成，前端只负责渲染。 */
export function getWorkOrderQrCode(id: number): Promise<WorkOrderQrCodeVO> {
  return request.get<WorkOrderQrCodeVO>(`${BASE}/${id}/qr-code`)
}

//创建工单
export function createProWorkOrder(data: ProWorkOrder) {
  return request.post(BASE, data)
}

export function updateProWorkOrder(id: number, data: ProWorkOrder) {
  return request.put(`${BASE}/${id}`, data)
}

export function deleteProWorkOrder(id: number) {
  return request.delete(`${BASE}/${id}`)
}

// ==================== V3.4 状态转换操作 ====================

/** [幂等保护] 工单下达：已创建(0) → 已下达(4) */
export function releaseWorkOrder(id: number, idempotencyKey?: string) {
  return request.put(`${BASE}/${id}/release`, null, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  })
}

export function pauseWorkOrder(id: number) {
  return request.put(`${BASE}/${id}/pause`)
}

export function resumeWorkOrder(id: number) {
  return request.put(`${BASE}/${id}/resume`)
}

export function closeWorkOrder(id: number) {
  return request.put(`${BASE}/${id}/close`)
}

export function cancelWorkOrder(id: number, remark?: string) {
  return request.put(`${BASE}/${id}/cancel`, null, { params: { remark } })
}

export function getActiveUsers() {
  return request.get(`${BASE}/users/active`)
}

export function assignAssignee(id: number, userId: number) {
  return request.put(`${BASE}/${id}/assign`, null, { params: { userId } })
}

export function reassignAssignee(id: number, userId: number) {
  return request.put(`${BASE}/${id}/reassign`, null, { params: { userId } })
}

export function unassignAssignee(id: number) {
  return request.put(`${BASE}/${id}/unassign`)
}
