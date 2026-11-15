/**
 * 设备报修类型定义
 *
 * 对应后端 com.bikeassembly.vo.EqmRepairOrderVO / EqmRepairOrderDTO
 */

/** 维修单 VO — 对应 EqmRepairOrderVO */
export interface EqmRepairOrderVO {
  repairId: number
  equipmentId: number
  faultDesc: string
  reportUser: string | null
  /** 报修人ID */
  reporterUserId: number | null
  reportTime: string
  repairUser: string | null
  finishTime: string | null
  /** 维修人ID */
  handlerUserId: number | null
  /** 0=待维修, 1=维修中, 2=已完成 */
  status: string
  resultDesc: string | null
}

/** 创建/更新维修单 DTO */
export interface EqmRepairOrderDTO {
  repairId?: number
  equipmentId?: number
  faultDesc?: string
  status?: string
  repairUser?: string | null
  finishTime?: string | null
  resultDesc?: string | null
}

/** 获取维修状态标签 */
export function getRepairStatusLabel(status: string): string {
  switch (status) {
    case '0': return '待维修'
    case '1': return '维修中'
    case '2': return '已完成'
    default: return '未知'
  }
}

/** 获取维修状态颜色类名 */
export function getRepairStatusClass(status: string): string {
  switch (status) {
    case '0': return 'repair--pending'
    case '1': return 'repair--processing'
    case '2': return 'repair--done'
    default: return ''
  }
}
