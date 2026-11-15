import http from '@/utils/request'
import type { BaseItemVO, BaseProcessVO, BaseEquipmentVO } from '@/types/reference'

/**
 * 基础参考数据 API — 调用 /app/** 端点。
 *
 * 用于获取物料、工序、设备等基础数据，
 * 在前端构建 ID → 名称的映射表。
 * 所有端点返回非分页的纯数组。
 */

/** GET /app/base-item/list */
export function getBaseItemListApi(): Promise<BaseItemVO[]> {
  return http.get<BaseItemVO[]>('/app/base-item/list', { showLoading: false })
}

/** GET /app/base-process/list */
export function getBaseProcessListApi(): Promise<BaseProcessVO[]> {
  return http.get<BaseProcessVO[]>('/app/base-process/list', { showLoading: false })
}

/** GET /app/base-equipment/list */
export function getBaseEquipmentListApi(): Promise<BaseEquipmentVO[]> {
  return http.get<BaseEquipmentVO[]>('/app/base-equipment/list', { showLoading: false })
}
