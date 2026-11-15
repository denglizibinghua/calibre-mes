/**
 * 库存查询类型定义
 *
 * 对应后端 com.bikeassembly.vo.WmsInventoryVO
 */

/** 库存 VO — 对应 WmsInventoryVO（含关联字段） */
export interface WmsInventoryVO {
  inventoryId: number
  itemId: number
  locationId: number
  /** 库存数量 */
  qty: number
  /** 锁定数量 */
  lockedQty: number
  /** 可用数量 = qty - lockedQty */
  batchNo: string | null
  updateTime: string
  // 关联字段（来自 base_item / wms_location JOIN）
  itemCode: string
  itemName: string
  locationCode: string
  locationName: string
  unit: string
}

/** 库存状态标签 */
export function getStockStatus(qty: number): { label: string; class: string } {
  if (qty <= 0) return { label: '缺货', class: 'stock--out' }
  if (qty < 10) return { label: '低库存', class: 'stock--low' }
  return { label: '充足', class: 'stock--ok' }
}
