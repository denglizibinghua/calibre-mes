import http from '@/utils/request'
import type { WorkOrderVO } from '@/types/workOrder'

/**
 * 生产工单 API — 调用 /app/** 端点。
 */

/**
 * 获取工单列表（支持 scope=mine|all 和搜索条件）。
 * GET /app/pro-work-order/list?scope=mine
 */
export function getWorkOrderListApi(scope: string = 'mine'): Promise<WorkOrderVO[]> {
  return http.get<WorkOrderVO[]>(`/app/pro-work-order/list?scope=${encodeURIComponent(scope)}`, { showLoading: false })
}

/**
 * 获取工单详情。
 * GET /app/pro-work-order/{id}
 */
export function getWorkOrderDetailApi(id: number): Promise<WorkOrderVO> {
  return http.get<WorkOrderVO>(`/app/pro-work-order/${id}`, { showLoading: false })
}
