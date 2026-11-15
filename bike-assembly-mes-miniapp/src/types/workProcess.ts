/**
 * 工序流转类型定义
 *
 * 字段严格对应后端 ProWorkProcessVO / ProWorkProcessDTO。
 */

/** 工序流转 VO - 对应 com.bikeassembly.vo.ProWorkProcessVO */
export interface WorkProcessVO {
  /** 流转记录ID */
  processRecordId: number
  /** 工单ID */
  workOrderId: number
  /** 工序ID（关联 base_process.process_id） */
  processId: number
  /** 设备ID（关联 base_equipment.equipment_id） */
  equipmentId: number | null
  /** 操作人 */
  operator: string | null
  /** 开始时间 */
  startTime: string | null
  /** 结束时间 */
  endTime: string | null
  /** 报工数量 */
  reportQty: number
  /** 质检结果（CHAR(1)） */
  qcResult: string | null
  /** 状态（CHAR(1)，后端默认 '0'） */
  status: string
  /** 创建时间 */
  createTime: string
  /** 乐观锁版本号 */
  version?: number
  /** 工序顺序号 */
  stepSeq?: number
  /** 返工次数 */
  reworkCount?: number
  /** 质检备注 */
  qcResultNote?: string | null
}

/** 工序报工请求 DTO - 对应 com.bikeassembly.dto.ProWorkProcessDTO */
export interface WorkProcessDTO {
  /** 工单ID（新建必填） */
  workOrderId?: number
  /** 工序ID（新建必填） */
  processId?: number
  /** 设备ID（选填） */
  equipmentId?: number | null
  /** 操作人（服务端自动填充，前端无需传） */
  operator?: string
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 报工数量 */
  reportQty?: number
  /** 质检结果（CHAR(1): 0=待检 1=合格 2=不合格 3=待复检） */
  qcResult?: string
  /** 状态（CHAR(1)） */
  status?: string
}

/**
 * 工序流转状态编码 → 中文标签映射（V3.3 状态机扩展）
 */
export const WORK_PROCESS_STATUS_MAP: Record<string, string> = {
  '0': '待开始',
  '1': '进行中',
  '2': '已完成',
  '3': '待检',
  '4': '检验合格',
  '5': '检验不合格',
  '6': '返工中',
  '7': '已跳过',
  '8': '已取消',
  '9': '可开工',
}

/** 获取工序流转状态中文标签 */
export function getWorkProcessStatusLabel(status: string): string {
  return WORK_PROCESS_STATUS_MAP[status] || status
}

/**
 * 质检结果编码 → 中文标签映射
 */
export const QC_RESULT_MAP: Record<string, string> = {
  '0': '待检',
  '1': '合格',
  '2': '不合格',
  '3': '待复检',
}

/** 获取质检结果中文标签 */
export function getQcResultLabel(qcResult: string | null): string {
  if (qcResult === null || qcResult === '') return '—'
  return QC_RESULT_MAP[qcResult] || qcResult
}
