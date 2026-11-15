import http from '@/utils/request'
import type { WmsInventoryVO } from '@/types/inventory'

/**
 * 库存查询 API — 调用 /app/wms-inventory 端点。
 * 只读查询。
 */

/**
 * 获取库存列表（非分页，返回纯数组）。
 * GET /app/wms-inventory/list
 *
 * 支持按物料名称/编码搜索。
 */
export function getInventoryListApi(itemName?: string, itemCode?: string): Promise<WmsInventoryVO[]> {
  const params: string[] = []
  if (itemName) params.push(`itemName=${encodeURIComponent(itemName)}`)
  if (itemCode) params.push(`itemCode=${encodeURIComponent(itemCode)}`)
  const query = params.length > 0 ? '?' + params.join('&') : ''
  return http.get<WmsInventoryVO[]>(`/app/wms-inventory/list${query}`, { showLoading: false })
}

/**
 * 获取库存详情。
 * GET /app/wms-inventory/{id}
 */
export function getInventoryDetailApi(id: number): Promise<WmsInventoryVO> {
  return http.get<WmsInventoryVO>(`/app/wms-inventory/${id}`, { showLoading: false })
}
