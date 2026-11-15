package com.bikeassembly.constant;

/**
 * 权限编码常量。
 * 格式：{domain}:{module}:{action}
 */
public final class PermissionConstant {

    private PermissionConstant() {}

    // ==================== 首页 ====================
    public static final String APP_HOME_VIEW = "app:home:view";

    // ==================== 工单 ====================
    public static final String APP_WORK_ORDER_VIEW = "app:work-order:view";
    public static final String APP_WORK_ORDER_VIEW_ALL = "app:work-order:view-all";

    // ==================== 工序报工 ====================
    public static final String APP_WORK_PROCESS_VIEW = "app:work-process:view";
    public static final String APP_WORK_PROCESS_VIEW_ALL = "app:work-process:view-all";
    public static final String APP_WORK_PROCESS_CREATE = "app:work-process:create";
    public static final String APP_WORK_PROCESS_START = "app:work-process:start";
    public static final String APP_WORK_PROCESS_COMPLETE = "app:work-process:complete";

    // ==================== 质量检验 ====================
    public static final String APP_INSPECTION_VIEW = "app:inspection:view";
    public static final String APP_INSPECTION_VIEW_ALL = "app:inspection:view-all";
    public static final String APP_INSPECTION_VIEW_PENDING = "app:inspection:view-pending";
    public static final String APP_INSPECTION_CREATE = "app:inspection:create";
    public static final String APP_INSPECTION_CONFIRM = "app:inspection:confirm";

    // ==================== 安灯告警 ====================
    public static final String APP_ANDON_VIEW = "app:andon:view";
    public static final String APP_ANDON_VIEW_ALL = "app:andon:view-all";
    public static final String APP_ANDON_VIEW_PENDING = "app:andon:view-pending";
    public static final String APP_ANDON_CREATE = "app:andon:create";
    public static final String APP_ANDON_HANDLE = "app:andon:handle";
    public static final String APP_ANDON_CLOSE = "app:andon:close";

    // ==================== 设备报修 ====================
    public static final String APP_REPAIR_VIEW = "app:repair:view";
    public static final String APP_REPAIR_VIEW_ALL = "app:repair:view-all";
    public static final String APP_REPAIR_VIEW_PENDING = "app:repair:view-pending";
    public static final String APP_REPAIR_CREATE = "app:repair:create";
    public static final String APP_REPAIR_ACCEPT = "app:repair:accept";
    public static final String APP_REPAIR_COMPLETE = "app:repair:complete";

    // ==================== 库存查询 ====================
    public static final String APP_INVENTORY_VIEW = "app:inventory:view";

    // ==================== 管理端 ====================
    public static final String ADMIN_USER_VIEW = "admin:user:view";
    public static final String ADMIN_USER_MANAGE = "admin:user:manage";
    public static final String ADMIN_ROLE_VIEW = "admin:role:view";
    public static final String ADMIN_ROLE_MANAGE = "admin:role:manage";
    public static final String ADMIN_PERMISSION_MANAGE = "admin:permission:manage";
    public static final String ADMIN_WORK_ORDER_ASSIGN = "admin:work-order:assign";

    // 管理端 CRUD 权限
    public static final String ADMIN_WORK_ORDER_VIEW = "admin:work-order:view";
    public static final String ADMIN_WORK_ORDER_MANAGE = "admin:work-order:manage";
    public static final String ADMIN_WORK_PROCESS_VIEW = "admin:work-process:view";
    public static final String ADMIN_WORK_PROCESS_MANAGE = "admin:work-process:manage";
    public static final String ADMIN_INSPECTION_VIEW = "admin:inspection:view";
    public static final String ADMIN_INSPECTION_MANAGE = "admin:inspection:manage";
    public static final String ADMIN_REPAIR_VIEW = "admin:repair:view";
    public static final String ADMIN_REPAIR_MANAGE = "admin:repair:manage";
    public static final String ADMIN_MAINTENANCE_VIEW = "admin:maintenance:view";
    public static final String ADMIN_MAINTENANCE_MANAGE = "admin:maintenance:manage";
    public static final String ADMIN_EQUIPMENT_VIEW = "admin:equipment:view";
    public static final String ADMIN_EQUIPMENT_MANAGE = "admin:equipment:manage";
    public static final String ADMIN_ANDON_VIEW = "admin:andon:view";
    public static final String ADMIN_ANDON_MANAGE = "admin:andon:manage";
    public static final String ADMIN_MESSAGE_TEST = "admin:message-test";

    // ==================== 管理端基础数据 ====================
    public static final String ADMIN_ITEM_VIEW = "admin:item:view";
    public static final String ADMIN_ITEM_MANAGE = "admin:item:manage";
    public static final String ADMIN_BOM_VIEW = "admin:bom:view";
    public static final String ADMIN_BOM_MANAGE = "admin:bom:manage";
    public static final String ADMIN_PROCESS_VIEW = "admin:process:view";
    public static final String ADMIN_PROCESS_MANAGE = "admin:process:manage";
    public static final String ADMIN_ROUTE_VIEW = "admin:route:view";
    public static final String ADMIN_ROUTE_MANAGE = "admin:route:manage";
    public static final String ADMIN_WORK_UNIT_VIEW = "admin:work-unit:view";
    public static final String ADMIN_WORK_UNIT_MANAGE = "admin:work-unit:manage";

    // ==================== 管理端质量 ====================
    public static final String ADMIN_DEFECT_VIEW = "admin:defect:view";
    public static final String ADMIN_DEFECT_MANAGE = "admin:defect:manage";
    public static final String ADMIN_INSPECTION_ITEM_VIEW = "admin:inspection-item:view";
    public static final String ADMIN_INSPECTION_ITEM_MANAGE = "admin:inspection-item:manage";

    // ==================== 管理端仓库 ====================
    public static final String ADMIN_LOCATION_VIEW = "admin:location:view";
    public static final String ADMIN_LOCATION_MANAGE = "admin:location:manage";
    public static final String ADMIN_INVENTORY_VIEW = "admin:inventory:view";
    public static final String ADMIN_INVENTORY_MANAGE = "admin:inventory:manage";
    public static final String ADMIN_STOCK_DOC_VIEW = "admin:stock-doc:view";
    public static final String ADMIN_STOCK_DOC_MANAGE = "admin:stock-doc:manage";

    // ==================== 管理端安全 ====================
    public static final String ADMIN_SAFETY_EVENT_VIEW = "admin:safety-event:view";
    public static final String ADMIN_SAFETY_EVENT_MANAGE = "admin:safety-event:manage";
    public static final String ADMIN_SAFETY_TRAINING_VIEW = "admin:safety-training:view";
    public static final String ADMIN_SAFETY_TRAINING_MANAGE = "admin:safety-training:manage";

    // ==================== 管理端系统 ====================
    public static final String ADMIN_CONFIG_VIEW = "admin:config:view";
    public static final String ADMIN_CONFIG_MANAGE = "admin:config:manage";
    public static final String ADMIN_OPER_LOG_VIEW = "admin:oper-log:view";
    public static final String ADMIN_OPER_LOG_MANAGE = "admin:oper-log:manage";
    public static final String ADMIN_ERP_SYNC_LOG_VIEW = "admin:erp-sync-log:view";
    public static final String ADMIN_ERP_SYNC_LOG_MANAGE = "admin:erp-sync-log:manage";
    public static final String ADMIN_USER_UNLOCK = "admin:user:unlock";

    // ==================== 管理端报表 ====================
    public static final String ADMIN_REPORT_VIEW = "admin:report:view";
    public static final String ADMIN_REPORT_EXPORT = "admin:report:export";
    public static final String ADMIN_AI_VIEW = "admin:ai:view";
    public static final String ADMIN_AI_KNOWLEDGE_MANAGE = "admin:ai:knowledge-manage";
    public static final String ADMIN_AI_PLAN_CONFIRM = "admin:ai:plan-confirm";

    // ==================== 管理端状态机 ====================
    public static final String ADMIN_WORK_ORDER_RELEASE = "admin:work-order:release";
    public static final String ADMIN_WORK_ORDER_PAUSE = "admin:work-order:pause";
    public static final String ADMIN_WORK_ORDER_RESUME = "admin:work-order:resume";
    public static final String ADMIN_WORK_ORDER_CANCEL = "admin:work-order:cancel";
    public static final String ADMIN_WORK_ORDER_CLOSE = "admin:work-order:close";
    public static final String ADMIN_WORK_PROCESS_SKIP = "admin:work-process:skip";
    public static final String ADMIN_WORK_PROCESS_CANCEL = "admin:work-process:cancel";
    public static final String ADMIN_WORK_PROCESS_QC_PASS = "admin:work-process:qc-pass";
    public static final String ADMIN_WORK_PROCESS_QC_FAIL = "admin:work-process:qc-fail";
    public static final String ADMIN_WORK_PROCESS_REWORK = "admin:work-process:rework";

    // ==================== 小程序状态机 ====================
    public static final String APP_WORK_PROCESS_REPORT = "app:work-process:report";
    public static final String APP_WORK_PROCESS_REWORK_START = "app:work-process:rework-start";
    public static final String APP_WORK_PROCESS_REWORK_COMPLETE = "app:work-process:rework-complete";

    /** 通配权限：管理员拥有全部权限 */
    public static final String WILDCARD = "*:*:*";
}
