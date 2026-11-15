/** 后端统一返回结构 */
export interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}

/** 分页查询参数 */
export interface PageQuery {
  page?: number
  pageSize?: number
}

/** 分页结果（V2.2 增强，与后端 PageResult<T> 对齐） */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  /** @deprecated 使用 current，保留用于后端兼容 */
  page?: number
  size: number
  pages: number
}

// ==================== 系统用户 ====================
export interface SysUser {
  userId?: number
  username: string
  password?: string
  nickname: string
  phone: string
  roleId: number
  status: string
  createTime?: string
}

// ==================== 系统配置 ====================
export interface SysConfig {
  configId?: number
  configKey: string
  configValue: string
  configName: string
  configType: string
  status: string
  updateTime?: string
}

// ==================== 物料 ====================
export interface BaseItem {
  itemId?: number
  itemCode: string
  itemName: string
  specModel: string
  unit: string
  itemType: string
  status: string
  createTime?: string
}

// ==================== 设备 ====================
export interface BaseEquipment {
  equipmentId?: number
  equipmentCode: string
  equipmentName: string
  model: string
  unitId?: number
  status: string
  runningStatus?: string
  createTime?: string
}

// ==================== 工单 ====================
export interface ProWorkOrder {
  workOrderId?: number
  workOrderNo: string
  orderNo: string
  itemId?: number
  planQty: number
  unit: string
  planStartTime: string
  planEndTime: string
  workUnitId?: number
  routeId?: number
  status: string
  createTime?: string
}

// ==================== 生产工序 ====================
export interface ProWorkProcess {
  processRecordId?: number
  workOrderId?: number
  processId?: number
  equipmentId?: number
  operator: string
  operatorUserId?: number
  startTime: string
  endTime: string
  reportQty: number
  qcResult: string
  qcResultNote?: string
  status: string
  stepSeq?: number
  reworkCount?: number
  version?: number
  createTime?: string
}

// ==================== 库存 ====================
export interface WmsInventory {
  inventoryId?: number
  itemId?: number
  locationId?: number
  qty: number
  lockedQty: number
  batchNo: string
  updateTime?: string
  /** 物料编码（JOIN 展示字段） */
  itemCode?: string
  /** 物料名称（JOIN 展示字段） */
  itemName?: string
  /** 库位编码（JOIN 展示字段） */
  locationCode?: string
  /** 库位名称（JOIN 展示字段） */
  locationName?: string
  /** 物料单位（JOIN 展示字段） */
  unit?: string
}

// ==================== 质检单（只读总览） ====================
export interface QmsInspection {
  inspectionId?: number
  inspectionNo: string
  workOrderId?: number
  processId?: number
  processRecordId?: number
  result: string
  detailsJson: string
  inspector: string
  inspectionTime: string
  status: string
  inspectionRound?: number
  parentInspectionId?: number
  confirmedBy?: string
  confirmedUserId?: number
  confirmTime?: string
  version?: number
  createTime?: string
}

// ==================== 工序 ====================
export interface BaseProcess {
  processId?: number
  processCode: string
  processName: string
  standardMinutes?: number
  status: string
  createTime?: string
}

// ==================== 工作单元 ====================
export interface BaseWorkUnit {
  unitId?: number
  parentId?: number
  unitCode: string
  unitName: string
  unitType: string
  leader?: string
  status: string
  createTime?: string
}

// ==================== 库位 ====================
export interface WmsLocation {
  locationId?: number
  parentId?: number
  locationCode: string
  locationName: string
  locationType: string
  status: string
  createTime?: string
}

// ==================== BOM ====================
export interface BaseBom {
  bomId?: number
  itemId?: number
  bomCode: string
  version: string
  detailsJson: string
  status: string
  createTime?: string
  updateTime?: string
}

// ==================== 工艺路线 ====================
export interface BaseRoute {
  routeId?: number
  itemId?: number
  routeCode: string
  version: string
  stepsJson: string
  status: string
  createTime?: string
  updateTime?: string
}

// ==================== 安灯告警 ====================
export interface AndonAlert {
  alertId?: number
  alertNo: string
  workOrderId?: number
  unitId?: number
  equipmentId?: number
  alertType: string
  alertLevel: string
  status: string
  reporterUserId?: number
  handlerUserId?: number
  createTime?: string
  handleTime?: string
  escalationStatus?: string
  escalatedBy?: number
  escalationTime?: string
  escalationRemark?: string
}

// ==================== 缺陷 ====================
export interface QmsDefect {
  defectId?: number
  inspectionId?: number
  workOrderId?: number
  processId?: number
  defectType: string
  qty: number
  handleResult: string
  status: string
  createTime?: string
}

// ==================== 质检项 ====================
export interface QmsInspectionItem {
  inspectionItemId?: number
  processId?: number
  itemName: string
  standard: string
  checkMethod: string
  status: string
  createTime?: string
}

// ==================== 库存单据 ====================
export interface WmsStockDoc {
  docId?: number
  docNo: string
  docType: string
  relatedOrderNo: string
  status: string
  detailsJson: string
  createTime?: string
}

// ==================== 设备保养 ====================
export interface EqmMaintenance {
  maintenanceId?: number
  equipmentId?: number
  maintenanceType: string
  planTime: string
  finishTime: string
  responsibleUser: string
  status: string
  remark: string
}

// ==================== 设备维修工单 ====================
export interface EqmRepairOrder {
  repairId?: number
  equipmentId?: number
  faultDesc: string
  reportUser: string
  reportTime: string
  repairUser: string
  reporterUserId?: number
  handlerUserId?: number
  finishTime: string
  status: string
  resultDesc: string
}

// ==================== 角色管理 ====================
export interface SysRole {
  roleId?: number
  roleName: string
  roleKey: string
  /** SELF=仅本人数据, ALL=全部数据 */
  dataScope?: string
  status: string
  createTime?: string
}

// ==================== 菜单管理 ====================
export interface SysMenu {
  menuId?: number
  parentId?: number
  menuName: string
  path: string
  perms: string
  /** M=目录, C=菜单, F=按钮 */
  menuType: string
  orderNum: number
  status: string
}

/** 菜单树节点（VO），包含 children 用于 el-tree */
export interface SysMenuVO extends SysMenu {
  children?: SysMenuVO[]
}

// ==================== 操作日志 ====================
export interface SysOperLog {
  logId?: number
  module: string
  operType: string
  operUser: string
  operIp: string
  requestMethod: string
  status: string
  operTime: string
  errorMsg: string
}

// ==================== ERP 同步日志 ====================
export interface ErpSyncLog {
  syncId?: number
  bizType: string
  bizId: string
  syncDirection: string
  syncStatus: string
  requestData: string
  responseData: string
  syncTime: string
}

// ==================== 安全事件 ====================
export interface SafSafetyEvent {
  eventId?: number
  parentId?: number
  eventType: string
  title: string
  level: string
  responsibleUser: string
  status: string
  eventTime: string
  closeTime: string
}

// ==================== 安全培训 ====================
export interface SafTraining {
  trainingId?: number
  trainingTitle: string
  trainingType: string
  trainer: string
  startTime: string
  endTime: string
  usersJson: string
  status: string
}
