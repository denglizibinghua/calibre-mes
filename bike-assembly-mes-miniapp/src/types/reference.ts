/**
 * 基础参考数据类型定义
 *
 * 用于本地映射 ID → 名称。
 */

/** 物料 VO - 对应 com.bikeassembly.vo.BaseItemVO */
export interface BaseItemVO {
  itemId: number
  itemCode: string
  itemName: string
  itemType: string | null
  specModel: string | null
  unit: string | null
  status: string
  createTime: string
}

/** 工序 VO - 对应 com.bikeassembly.vo.BaseProcessVO */
export interface BaseProcessVO {
  processId: number
  processCode: string
  processName: string
  standardMinutes: number | null
  status: string
  createTime: string
}

/** 设备 VO - 对应 com.bikeassembly.vo.BaseEquipmentVO */
export interface BaseEquipmentVO {
  equipmentId: number
  equipmentCode: string
  equipmentName: string
  unitId: number | null
  model: string | null
  status: string
  createTime: string
}
