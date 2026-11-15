/**
 * 安灯告警类型定义
 *
 * 对应后端 com.bikeassembly.vo.AndonAlertVO / AndonAlertDTO
 */

/** 安灯告警 VO — 对应 AndonAlertVO */
export interface AndonAlertVO {
  alertId: number
  alertNo: string
  workOrderId: number | null
  unitId: number | null
  equipmentId: number | null
  /** 告警类型（如：设备异常、物料短缺、质检异常、工具故障） */
  alertType: string
  /** 1=一般, 2=紧急, 3=严重 */
  alertLevel: string
  /** 0=待处理, 1=处理中, 2=已处理 */
  status: string
  /** 上报人ID */
  reporterUserId: number | null
  /** 处理人ID */
  handlerUserId: number | null
  /** 告警时间 */
  createTime: string
  /** 处理时间 */
  handleTime: string | null
  /** 升级状态：0=未升级 1=已升级 */
  escalationStatus?: string
  /** 升级人ID */
  escalatedBy?: number | null
  /** 升级时间 */
  escalationTime?: string | null
  /** 升级备注 */
  escalationRemark?: string | null
}

/** 创建/更新告警 DTO */
export interface AndonAlertDTO {
  alertId?: number
  workOrderId?: number | null
  unitId?: number | null
  equipmentId?: number | null
  alertType?: string
  alertLevel?: string
  status?: string
  handleTime?: string | null
}

/** 获取告警等级标签 */
export function getAlertLevelLabel(level: string): string {
  switch (level) {
    case '1': return '一般'
    case '2': return '紧急'
    case '3': return '严重'
    default: return '未知'
  }
}

/** 获取告警等级颜色类名 */
export function getAlertLevelClass(level: string): string {
  switch (level) {
    case '1': return 'level--info'
    case '2': return 'level--warn'
    case '3': return 'level--critical'
    default: return ''
  }
}

/** 获取告警状态标签 */
export function getAndonStatusLabel(status: string): string {
  switch (status) {
    case '0': return '待处理'
    case '1': return '处理中'
    case '2': return '已关闭'
    default: return '未知'
  }
}

/** 获取告警状态颜色类名 */
export function getAndonStatusClass(status: string): string {
  switch (status) {
    case '0': return 'andon--pending'
    case '1': return 'andon--processing'
    case '2': return 'andon--closed'
    default: return ''
  }
}

/** 告警类型选项 */
export const ALERT_TYPE_OPTIONS = [
  '设备异常',
  '物料短缺',
  '质检异常',
  '工具故障',
  '安全事故',
  '人员缺勤',
  '工艺异常',
  '其他',
]
