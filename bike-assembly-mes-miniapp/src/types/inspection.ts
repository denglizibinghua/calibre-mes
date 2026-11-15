/**
 * 质量检验类型定义
 *
 * 对应后端 com.bikeassembly.vo.QmsInspectionVO / QmsInspectionItemVO / QmsDefectVO
 */

/** 质检单 VO — 对应 QmsInspectionVO */
export interface QmsInspectionVO {
  inspectionId: number
  inspectionNo: string
  workOrderId: number
  /** 质检任务关联的工单编号，无需生产工单查询权限。 */
  workOrderNo?: string
  processId: number | null
  /** P=合格, F=不合格, 0=尚未录入 */
  result: string
  /** 质检明细 JSON 字符串 */
  detailsJson: string
  inspector: string | null
  inspectorUserId: number | null
  inspectionTime: string | null
  /** 0=待检, 1=已录入, 2=已确认 */
  status: string
  /** 乐观锁版本号 */
  version: number
}

/** 质检项 VO — 对应 QmsInspectionItemVO */
export interface QmsInspectionItemVO {
  inspectionItemId: number
  processId: number | null
  itemName: string
  standard: string | null
  checkMethod: string | null
  status: string
  createTime: string
}

/** 质检明细单项（从 detailsJson 解析） */
export interface InspectionDetailItem {
  /** 质检项名称 */
  itemName: string
  /** 检验标准 */
  standard: string
  /** P=合格, F=不合格 */
  result: 'P' | 'F'
  /** 实际情况描述 */
  actual: string
}

/** 录入质检结果请求 DTO — 更新已有待检质检单 */
export interface EnterInspectionResultDTO {
  /** P=合格, F=不合格 */
  result: string
  /** 质检明细 JSON 字符串 */
  detailsJson: string
}

/** 获取质检结果标签 */
export function getInspectionResultLabel(result: string): string {
  switch (result) {
    case 'P':
      return '合格'
    case 'F':
      return '不合格'
    case '0':
      return '待检'
    default:
      return '未知'
  }
}

/** 获取质检结果颜色类名 */
export function getInspectionResultClass(result: string): string {
  switch (result) {
    case 'P':
      return 'qc--pass'
    case 'F':
      return 'qc--fail'
    default:
      return 'qc--pending'
  }
}

/** 获取质检状态标签：0=待检, 1=已录入, 2=已确认 */
export function getInspectionStatusLabel(status: string): string {
  switch (status) {
    case '0':
      return '待检'
    case '1':
      return '已录入'
    case '2':
      return '已确认'
    default:
      return '未知'
  }
}
