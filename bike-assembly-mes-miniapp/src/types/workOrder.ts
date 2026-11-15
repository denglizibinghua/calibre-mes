/**
 * 生产工单类型定义
 *
 * 字段严格对应后端 ProWorkOrderVO。
 * 后端返回的日期为 ISO 字符串（LocalDateTime → JSON），
 * 前端统一按 string 处理，展示时再格式化。
 */

/** 工单 VO - 对应 com.bikeassembly.vo.ProWorkOrderVO */
export interface WorkOrderVO {
  /** 工单ID */
  workOrderId: number
  /** 工单编号 */
  workOrderNo: string
  /** 订单号 */
  orderNo: string | null
  /** 产品ID（关联 base_item.item_id） */
  itemId: number
  /** 计划数量 */
  planQty: number
  /** 单位 */
  unit: string
  /** 计划开始时间 */
  planStartTime: string | null
  /** 计划结束时间 */
  planEndTime: string | null
  /** 派工单元 */
  workUnitId: number | null
  /** 工艺路线ID */
  routeId: number | null
  /** 工单状态（CHAR(1)，后端默认 '0'） */
  status: string
  /** 创建时间 */
  createTime: string
}

/**
 * 工单状态编码 → 中文标签。
 *
 * 当前映射基于通用 MES 约定，与后端 CHAR(1) 字段对应：
 *   '0' - 待生产
 *   '1' - 生产中
 *   '2' - 已完成
 *   '3' - 已关闭
 *
 * 如果后端实际编码不同，仅需修改此映射表。
 */
/**
 * 工单状态编码 → 中文标签（V3.3 状态机扩展）。
 */
export const WORK_ORDER_STATUS_MAP: Record<string, string> = {
  '0': '已创建',
  '1': '生产中',
  '2': '已完工',
  '3': '已关闭',
  '4': '已下达',
  '5': '已暂停',
  '6': '已取消',
}

/** 获取工单状态中文标签 */
export function getWorkOrderStatusLabel(status: string): string {
  return WORK_ORDER_STATUS_MAP[status] || status
}

/** 工单状态对应的 CSS 类名后缀 */
export function getWorkOrderStatusClass(status: string): string {
  const classMap: Record<string, string> = {
    '0': 'pending',
    '1': 'in-progress',
    '2': 'completed',
    '3': 'closed',
    '4': 'released',
    '5': 'paused',
    '6': 'cancelled',
  }
  return classMap[status] || 'default'
}
