-- ============================================================================
-- Bike Assembly MES — 数据库全量初始化脚本
-- File: sql/init_database.sql
-- Version: 3.1 (全量重建；业务数据另执行 dev_seed_full.sql)
-- Date: 2026-10-24
-- ============================================================================
-- ⚠️  警告：本脚本会删除现有表和数据，仅适用于开发、测试和答辩演示环境！
-- ⚠️  禁止直接用于有真实数据的生产数据库！
-- ============================================================================
-- 用途:
--   1. 删除全部现有表（按外键依赖逆序安全删除）
--   2. 创建全部32张表（基于最终Entity/Mapper/Migration）
--   3. 创建全部外键约束、唯一索引、生成列
--   4. 插入角色、菜单、权限、管理员用户、系统配置种子数据
-- ============================================================================
-- 执行命令:
--   mysql -u root -p bike_assembly_mes < sql/init_database.sql
-- ============================================================================
-- 数据库: bike_assembly_mes (需预先创建)
--   CREATE DATABASE IF NOT EXISTS bike_assembly_mes
--     DEFAULT CHARACTER SET utf8mb4
--     DEFAULT COLLATE utf8mb4_unicode_ci;
-- ============================================================================
-- 密码加密方式: BCrypt (Spring Security BCryptPasswordEncoder, cost=10)
-- 开发密码 Demo@123456 → $2b$10$rTom51Fr.4SmIIh/OrFleu75ZL3VH0j8uCvudJz49k4kKhYSzRBNS
-- 旧密码 123456 → $2b$10$qR.opyVGzeEJV2l.8NU1xeYhYFgna.VGKiHWs0PJlP2dRMOB2R6CK
-- ============================================================================

-- ==================== 1. 删除全部表（按外键依赖逆序） ====================
SET FOREIGN_KEY_CHECKS = 0;

-- Level 5: 最深层叶子表
DROP TABLE IF EXISTS `sys_oper_log`;
DROP TABLE IF EXISTS `qms_defect`;
DROP TABLE IF EXISTS `pro_status_transition_log`;
DROP TABLE IF EXISTS `pro_message_consume_log`;
DROP TABLE IF EXISTS `pro_idempotency_record`;
DROP TABLE IF EXISTS `sys_user_password_history`;
DROP TABLE IF EXISTS `user_refresh_token`;

-- Level 4: 依赖叶子表或中间表
DROP TABLE IF EXISTS `qms_inspection`;
DROP TABLE IF EXISTS `pro_work_process`;
DROP TABLE IF EXISTS `andon_alert`;
DROP TABLE IF EXISTS `eqm_maintenance`;
DROP TABLE IF EXISTS `eqm_repair_order`;
DROP TABLE IF EXISTS `wms_inventory`;
DROP TABLE IF EXISTS `wms_stock_flow`;

-- Level 3: 业务主表
DROP TABLE IF EXISTS `wms_stock_doc`;
DROP TABLE IF EXISTS `pro_work_order`;
DROP TABLE IF EXISTS `erp_sync_log`;
DROP TABLE IF EXISTS `saf_training`;
DROP TABLE IF EXISTS `saf_safety_event`;
DROP TABLE IF EXISTS `sys_role_menu`;

-- Level 2: 基础数据表（有外键依赖）
DROP TABLE IF EXISTS `base_bom`;
DROP TABLE IF EXISTS `base_route`;
DROP TABLE IF EXISTS `base_equipment`;
DROP TABLE IF EXISTS `qms_inspection_item`;

-- Level 1: 基础数据表（被依赖）
DROP TABLE IF EXISTS `base_process`;
DROP TABLE IF EXISTS `base_work_unit`;
DROP TABLE IF EXISTS `base_item`;
DROP TABLE IF EXISTS `wms_location`;
DROP TABLE IF EXISTS `sys_config`;

-- Level 0: 系统根表
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_menu`;
DROP TABLE IF EXISTS `sys_role`;

SET FOREIGN_KEY_CHECKS = 1;

-- ==================== 2. 创建全部表结构 ====================
SET FOREIGN_KEY_CHECKS = 0;

-- ==================================================================
-- 2.1 系统管理模块 (sys_*)
-- ==================================================================

-- 系统管理 / 角色表
CREATE TABLE `sys_role` (
  `role_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
  `role_key` VARCHAR(64) NOT NULL COMMENT '角色编码（唯一）',
  `data_scope` VARCHAR(16) NOT NULL DEFAULT 'SELF' COMMENT '数据范围: ALL=全部 SELF=本人',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_sys_role_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 系统管理 / 菜单权限表（含管理端动态路由字段）
CREATE TABLE `sys_menu` (
  `menu_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父菜单ID',
  `menu_name` VARCHAR(64) NOT NULL COMMENT '菜单名称',
  `path` VARCHAR(128) NULL COMMENT '路由路径',
  `component` VARCHAR(255) NULL COMMENT '前端组件路径',
  `icon` VARCHAR(64) NULL COMMENT '菜单图标',
  `visible` CHAR(1) NOT NULL DEFAULT '1' COMMENT '是否在侧边栏显示：0=隐藏 1=显示',
  `keep_alive` CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否缓存页面：0=否 1=是',
  `redirect` VARCHAR(255) NULL COMMENT '目录默认跳转路径',
  `perms` VARCHAR(128) NULL COMMENT '权限标识（如 admin:user:view）',
  `menu_type` CHAR(1) NOT NULL DEFAULT 'M' COMMENT '菜单类型: D=目录 C=菜单页 M=模块 F=按钮/权限',
  `order_num` INT NULL DEFAULT 0 COMMENT '排序',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单权限表';

-- 系统管理 / 用户信息表
CREATE TABLE `sys_user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID（自增主键）',
  `username` VARCHAR(64) NOT NULL COMMENT '登录账号（唯一）',
  `password` VARCHAR(128) NOT NULL COMMENT '密码密文（BCrypt加密）',
  `nickname` VARCHAR(64) NULL COMMENT '用户姓名/昵称',
  `phone` VARCHAR(20) NULL COMMENT '手机号',
  `role_id` BIGINT NULL COMMENT '角色ID，关联 sys_role.role_id',
  `openid` VARCHAR(128) NULL COMMENT '微信openid（小程序用户唯一标识）',
  `avatar` VARCHAR(512) NULL COMMENT '微信头像URL',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '账号状态（0=启用 1=停用）',
  `password_changed_at` DATETIME NULL COMMENT '密码最后修改时间',
  `password_expire_at` DATETIME NULL COMMENT '密码过期时间（根据password.expire_days计算，null=永不过期）',
  `must_change_password` TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须修改密码: 0=否 1=是（首次登录/管理员重置后）',
  `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数（成功重置为0，达到5次锁定）',
  `locked_until` DATETIME NULL COMMENT '账号锁定截止时间（失败5次后=now+15分钟，null=未锁定）',
  `last_login_time` DATETIME NULL COMMENT '最后登录时间',
  `last_login_ip` VARCHAR(64) NULL COMMENT '最后登录IP地址',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  UNIQUE KEY `uk_sys_user_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- 系统管理 / 角色菜单关联表
CREATE TABLE `sys_role_menu` (
  `role_menu_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `menu_id` BIGINT NOT NULL COMMENT '菜单/权限ID',
  PRIMARY KEY (`role_menu_id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-菜单/权限关联表';

-- 系统管理 / 操作日志表
CREATE TABLE `sys_oper_log` (
  `log_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `module` VARCHAR(64) NULL COMMENT '模块',
  `oper_type` VARCHAR(32) NULL COMMENT '操作类型',
  `oper_user` VARCHAR(64) NULL COMMENT '操作人',
  `oper_user_id` BIGINT NULL COMMENT '操作用户ID',
  `oper_ip` VARCHAR(64) NULL COMMENT 'IP地址',
  `request_method` VARCHAR(16) NULL COMMENT '请求方式',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态',
  `oper_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `error_msg` VARCHAR(255) NULL COMMENT '错误信息',
  `change_summary` VARCHAR(500) NULL COMMENT '变更摘要',
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 系统管理 / 系统参数配置表
CREATE TABLE `sys_config` (
  `config_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置键（唯一）',
  `config_value` VARCHAR(255) NULL COMMENT '配置值',
  `config_name` VARCHAR(128) NOT NULL COMMENT '配置名称',
  `config_type` VARCHAR(64) NULL COMMENT '配置类型',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_sys_config_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置表';

-- 系统管理 / 密码历史表
CREATE TABLE `sys_user_password_history` (
  `history_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `password_hash` VARCHAR(128) NOT NULL COMMENT 'BCrypt密码哈希',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`history_id`),
  INDEX `idx_uph_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密码历史表';

-- 系统管理 / Refresh Token 持久化表
CREATE TABLE `user_refresh_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `client_type` VARCHAR(16) NOT NULL COMMENT '客户端类型: admin / app',
  `token_jti` VARCHAR(64) NOT NULL COMMENT 'JWT JTI 唯一标识',
  `token_hash` VARCHAR(128) NOT NULL COMMENT 'Refresh Token SHA-256 哈希',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `revoked` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已撤销: 0=有效 1=已撤销',
  `revoked_at` DATETIME NULL COMMENT '撤销时间',
  `replaced_by_jti` VARCHAR(64) NULL COMMENT '替换此token的新token JTI（Token Rotation）',
  `device_info` VARCHAR(256) NULL COMMENT '设备信息',
  `ip_address` VARCHAR(64) NULL COMMENT 'IP地址',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_jti` (`token_jti`),
  INDEX `idx_ut_user_client` (`user_id`, `client_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Refresh Token 持久化表';

-- ==================================================================
-- 2.2 基础数据模块 (base_*)
-- ==================================================================

-- 基础数据 / 统一物料/产品表
CREATE TABLE `base_item` (
  `item_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '物料ID',
  `item_code` VARCHAR(64) NOT NULL COMMENT '物料编码（唯一）',
  `item_name` VARCHAR(128) NOT NULL COMMENT '物料名称',
  `item_type` CHAR(1) NOT NULL COMMENT '物料类型: P=成品 S=半成品 M=零部件 R=原材料',
  `spec_model` VARCHAR(128) NULL COMMENT '规格型号',
  `unit` VARCHAR(16) NOT NULL COMMENT '单位（如"台"、"件"、"个"、"kg"）',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uk_base_item_item_code` (`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一物料/产品表';

-- 基础数据 / 生产单元表
CREATE TABLE `base_work_unit` (
  `unit_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '生产单元ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父级ID',
  `unit_code` VARCHAR(64) NOT NULL COMMENT '单元编码（唯一）',
  `unit_name` VARCHAR(128) NOT NULL COMMENT '单元名称',
  `unit_type` CHAR(1) NOT NULL COMMENT '单元类型: D=部门 W=车间 L=产线 S=工位',
  `leader` VARCHAR(64) NULL COMMENT '负责人',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`unit_id`),
  UNIQUE KEY `uk_base_work_unit_unit_code` (`unit_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产单元表';

-- 基础数据 / 工序表
CREATE TABLE `base_process` (
  `process_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工序ID',
  `process_code` VARCHAR(64) NOT NULL COMMENT '工序编码（唯一）',
  `process_name` VARCHAR(128) NOT NULL COMMENT '工序名称',
  `standard_minutes` INT NULL DEFAULT 0 COMMENT '标准工时（分钟）',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`process_id`),
  UNIQUE KEY `uk_base_process_process_code` (`process_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序表';

-- 基础数据 / BOM主表
CREATE TABLE `base_bom` (
  `bom_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'BOM ID',
  `item_id` BIGINT NOT NULL COMMENT '成品物料ID',
  `bom_code` VARCHAR(64) NOT NULL COMMENT 'BOM编码（唯一）',
  `version` VARCHAR(32) NOT NULL DEFAULT 'V1.0' COMMENT '版本',
  `details_json` JSON NOT NULL COMMENT '子件明细JSON',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`bom_id`),
  UNIQUE KEY `uk_base_bom_bom_code` (`bom_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM主表';

-- 基础数据 / 工艺路线主表
CREATE TABLE `base_route` (
  `route_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '路线ID',
  `item_id` BIGINT NOT NULL COMMENT '产品物料ID',
  `route_code` VARCHAR(64) NOT NULL COMMENT '路线编码（唯一）',
  `version` VARCHAR(32) NOT NULL DEFAULT 'V1.0' COMMENT '版本',
  `steps_json` JSON NOT NULL COMMENT '工序步骤JSON',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`route_id`),
  UNIQUE KEY `uk_route_code_version` (`route_code`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工艺路线主表';

-- 基础数据 / 设备台账表
CREATE TABLE `base_equipment` (
  `equipment_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `equipment_code` VARCHAR(64) NOT NULL COMMENT '设备编码（唯一）',
  `equipment_name` VARCHAR(128) NOT NULL COMMENT '设备名称',
  `unit_id` BIGINT NULL COMMENT '所属生产单元ID',
  `model` VARCHAR(128) NULL COMMENT '规格型号',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '设备启用状态（0=启用 1=禁用）',
  `running_status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '设备运行状态（0=可运行 1=不可运行/故障/维修中）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`equipment_id`),
  UNIQUE KEY `uk_base_equipment_equipment_code` (`equipment_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备台账表';

-- ==================================================================
-- 2.3 生产执行模块 (pro_*)
-- ==================================================================

-- 生产执行 / 生产工单表
CREATE TABLE `pro_work_order` (
  `work_order_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工单ID（自增主键）',
  `work_order_no` VARCHAR(64) NOT NULL COMMENT '工单编号（唯一）',
  `order_no` VARCHAR(64) NULL COMMENT '订单号（关联ERP订单）',
  `item_id` BIGINT NOT NULL COMMENT '产品ID（关联 base_item.item_id）',
  `plan_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '计划生产数量',
  `unit` VARCHAR(16) NOT NULL COMMENT '单位（如"台"、"件"）',
  `plan_start_time` DATETIME NULL COMMENT '计划开始时间',
  `plan_end_time` DATETIME NULL COMMENT '计划结束时间',
  `work_unit_id` BIGINT NULL COMMENT '派工单元ID（关联 base_work_unit.unit_id）',
  `route_id` BIGINT NULL COMMENT '工艺路线ID（关联 base_route.route_id）',
  `assignee_user_id` BIGINT NULL COMMENT '指派执行人ID（关联 sys_user.user_id）',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '工单状态: 0=已创建 1=生产中 2=已完工 3=已关闭 4=已下达 5=已暂停 6=已取消',
  `request_id` VARCHAR(64) NULL COMMENT '请求追踪ID（审计用）',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `actual_start_time` DATETIME NULL COMMENT '实际开始时间',
  `actual_end_time` DATETIME NULL COMMENT '实际完工时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`work_order_id`),
  UNIQUE KEY `uk_pro_work_order_work_order_no` (`work_order_no`),
  INDEX `idx_pwo_assignee` (`assignee_user_id`),
  INDEX `idx_pwo_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产工单表';

-- 生产执行 / 工序流转表
CREATE TABLE `pro_work_process` (
  `process_record_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流转记录ID（自增主键）',
  `work_order_id` BIGINT NOT NULL COMMENT '工单ID',
  `process_id` BIGINT NOT NULL COMMENT '工序ID',
  `equipment_id` BIGINT NULL COMMENT '设备ID',
  `operator` VARCHAR(64) NULL COMMENT '操作人姓名',
  `operator_user_id` BIGINT NULL COMMENT '操作人ID',
  `start_time` DATETIME NULL COMMENT '开始时间',
  `end_time` DATETIME NULL COMMENT '结束时间',
  `report_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '报工数量',
  `qc_result` CHAR(1) NULL COMMENT '工序质检结果: 0=待检 1=合格 2=不合格；未送检为空',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '工序状态: 0=待开始 9=可开工 1=进行中 2=已完成 3=待检 4=检验合格 5=不合格 6=返工中 7=跳过 8=取消',
  `request_id` VARCHAR(64) NULL COMMENT '请求追踪ID（审计用）',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `step_seq` INT NOT NULL DEFAULT 0 COMMENT '工序顺序号（从1开始，根据工艺路线steps_json确定）',
  `rework_count` INT NOT NULL DEFAULT 0 COMMENT '返工次数',
  `qc_result_note` VARCHAR(500) NULL COMMENT '质检备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`process_record_id`),
  UNIQUE KEY `uk_wp_wo_step` (`work_order_id`, `step_seq`),
  INDEX `idx_pwp_operator` (`operator_user_id`),
  INDEX `idx_pwp_work_order` (`work_order_id`),
  INDEX `idx_pwp_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序流转表';

-- 生产执行 / 幂等记录表
-- [幂等保护] 唯一索引 (operator_id, business_type, action, idempotency_key) 保证：
--   不同用户相同 Key 互不影响
--   同一用户相同业务+操作用相同 Key 只执行一次
--   同一用户不同业务用相同 Key 可以独立执行
CREATE TABLE `pro_idempotency_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键（客户端生成，建议UUID）',
  `business_type` VARCHAR(32) NOT NULL COMMENT '业务类型: WORK_ORDER/WORK_PROCESS/INSPECTION',
  `business_id` BIGINT NULL COMMENT '业务实体ID',
  `action` VARCHAR(64) NOT NULL COMMENT '操作: unified_report/start/release/rework_start/qc_pass等',
  `request_hash` VARCHAR(128) NOT NULL COMMENT '请求参数SHA-256哈希',
  `process_status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '处理状态: 0=处理中 1=成功 2=失败',
  `response_code` INT NULL COMMENT '业务响应码',
  `response_data` TEXT NULL COMMENT '响应数据快照(JSON)',
  `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `expire_time` DATETIME NOT NULL COMMENT '过期时间（默认24小时后）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idm_user_op_key` (`operator_id`, `business_type`, `action`, `idempotency_key`),
  INDEX `idx_idm_expire` (`expire_time`),
  INDEX `idx_idm_status` (`process_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等记录表';

-- 生产执行 / 状态流转日志表
CREATE TABLE `pro_status_transition_log` (
  `log_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `entity_type` VARCHAR(32) NOT NULL COMMENT '实体类型: WORK_ORDER / WORK_PROCESS',
  `entity_id` BIGINT NOT NULL COMMENT '实体主键ID',
  `from_status` CHAR(1) NOT NULL COMMENT '变更前状态',
  `to_status` CHAR(1) NOT NULL COMMENT '变更后状态',
  `action` VARCHAR(32) NOT NULL COMMENT '操作动作',
  `operator_user_id` BIGINT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(64) NULL COMMENT '操作人姓名',
  `request_id` VARCHAR(64) NULL COMMENT '请求追踪ID',
  `remark` VARCHAR(500) NULL COMMENT '备注',
  `transition_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (`log_id`),
  INDEX `idx_tsl_entity` (`entity_type`, `entity_id`),
  INDEX `idx_tsl_time` (`transition_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='状态流转日志表';

-- 生产执行 / 消息消费记录表（MQ 消费留痕与幂等）
CREATE TABLE `pro_message_consume_log` (
  `log_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `event_id` VARCHAR(128) NOT NULL COMMENT '事件ID（唯一）',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
  `aggregate_type` VARCHAR(32) NOT NULL COMMENT '聚合类型: WORK_ORDER/WORK_PROCESS',
  `aggregate_id` BIGINT NOT NULL COMMENT '聚合ID',
  `work_order_id` BIGINT NULL COMMENT '工单ID',
  `message_body` TEXT NULL COMMENT '消息体快照',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '消费状态: 0=待处理 1=已处理 2=处理失败',
  `consume_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消费时间',
  `remark` VARCHAR(500) NULL COMMENT '备注',
  PRIMARY KEY (`log_id`),
  UNIQUE KEY `uk_mcl_event_id` (`event_id`),
  INDEX `idx_mcl_aggregate` (`aggregate_type`, `aggregate_id`),
  INDEX `idx_mcl_time` (`consume_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息消费记录表（MQ消费留痕与幂等）';

-- ==================================================================
-- 2.4 质量管理模块 (qms_*)
-- ==================================================================

-- 质量管理 / 质检项标准表
CREATE TABLE `qms_inspection_item` (
  `inspection_item_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '质检项ID',
  `process_id` BIGINT NULL COMMENT '工序ID',
  `item_name` VARCHAR(128) NOT NULL COMMENT '质检项名称',
  `standard` VARCHAR(255) NULL COMMENT '检验标准',
  `check_method` VARCHAR(128) NULL COMMENT '检验方式',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`inspection_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质检项标准表';

-- 质量管理 / 质检单表
CREATE TABLE `qms_inspection` (
  `inspection_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '质检单ID',
  `inspection_no` VARCHAR(64) NOT NULL COMMENT '质检单号（唯一）',
  `work_order_id` BIGINT NOT NULL COMMENT '工单ID',
  `process_id` BIGINT NULL COMMENT '工序ID（基础工序）',
  `process_record_id` BIGINT NULL COMMENT '工序执行记录ID（pro_work_process.process_record_id，精确关联）',
  `result` CHAR(1) NOT NULL DEFAULT '0' COMMENT '检验结果: P=合格 F=不合格',
  `details_json` JSON NOT NULL COMMENT '质检明细JSON',
  `inspector` VARCHAR(64) NULL COMMENT '检验人姓名',
  `inspector_user_id` BIGINT NULL COMMENT '检验人ID',
  `inspection_time` DATETIME NULL DEFAULT NULL COMMENT '实际检验时间；待检为空',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '质检状态: 0=待检验 1=已录入 2=已确认',
  `inspection_round` INT NOT NULL DEFAULT 1 COMMENT '质检轮次（从1开始，初检=1，复检递增）',
  `parent_inspection_id` BIGINT NULL COMMENT '上一张质检单ID（复检时关联初检/上一次质检单）',
  `confirmed_by` VARCHAR(64) NULL COMMENT '确认人',
  `confirmed_user_id` BIGINT NULL COMMENT '确认人ID',
  `confirm_time` DATETIME NULL COMMENT '确认时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `active_task_key` BIGINT GENERATED ALWAYS AS (
    CASE WHEN `status` IN ('0', '1') THEN `process_record_id` ELSE NULL END
  ) STORED COMMENT '活动任务唯一键（生成列）：status=0/1时=process_record_id，status=2时为NULL',
  PRIMARY KEY (`inspection_id`),
  UNIQUE KEY `uk_qms_inspection_inspection_no` (`inspection_no`),
  UNIQUE KEY `uk_qms_active_by_record` (`active_task_key`),
  INDEX `idx_qi_inspector` (`inspector_user_id`),
  INDEX `idx_qms_process_record` (`process_record_id`),
  INDEX `idx_qi_work_order` (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质检单表';

-- 质量管理 / 不合格品表
CREATE TABLE `qms_defect` (
  `defect_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '不合格品ID',
  `inspection_id` BIGINT NULL COMMENT '质检单ID',
  `work_order_id` BIGINT NOT NULL COMMENT '工单ID',
  `process_id` BIGINT NULL COMMENT '工序ID',
  `defect_type` VARCHAR(64) NOT NULL COMMENT '缺陷类型',
  `qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '不合格数量',
  `handle_result` VARCHAR(255) NULL COMMENT '处理结果',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=待处理 1=已处理）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`defect_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不合格品表';

-- ==================================================================
-- 2.5 设备管理模块 (eqm_*)
-- ==================================================================

-- 设备管理 / 设备保养表
CREATE TABLE `eqm_maintenance` (
  `maintenance_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '维护ID',
  `equipment_id` BIGINT NOT NULL COMMENT '设备ID',
  `maintenance_type` CHAR(1) NOT NULL COMMENT '维护类型: R=定期保养 P=计划维护 E=紧急维护',
  `plan_time` DATETIME NULL COMMENT '计划时间',
  `finish_time` DATETIME NULL COMMENT '完成时间',
  `responsible_user` VARCHAR(64) NULL COMMENT '责任人',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '保养状态: 0=待执行 1=执行中 2=已完成',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  PRIMARY KEY (`maintenance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备保养表';

-- 设备管理 / 设备维修工单表
CREATE TABLE `eqm_repair_order` (
  `repair_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '维修单ID',
  `equipment_id` BIGINT NOT NULL COMMENT '设备ID',
  `fault_desc` VARCHAR(255) NOT NULL COMMENT '故障描述',
  `report_user` VARCHAR(64) NULL COMMENT '报修人姓名',
  `reporter_user_id` BIGINT NULL COMMENT '报修人ID',
  `report_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报修时间',
  `repair_user` VARCHAR(64) NULL COMMENT '维修人姓名',
  `handler_user_id` BIGINT NULL COMMENT '维修人ID',
  `finish_time` DATETIME NULL COMMENT '完成时间',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '维修状态: 0=待维修 1=维修中 2=已完成',
  `result_desc` VARCHAR(255) NULL COMMENT '维修结果',
  PRIMARY KEY (`repair_id`),
  INDEX `idx_ero_reporter` (`reporter_user_id`),
  INDEX `idx_ero_handler` (`handler_user_id`),
  INDEX `idx_ero_equipment` (`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备维修工单表';

-- ==================================================================
-- 2.6 安灯管理模块 (andon_*)
-- ==================================================================

-- 安灯管理 / 安灯告警表（上报/受理/处理/关闭/升级）
CREATE TABLE `andon_alert` (
  `alert_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '告警ID',
  `alert_no` VARCHAR(64) NOT NULL COMMENT '告警编号（唯一）',
  `work_order_id` BIGINT NULL COMMENT '工单ID',
  `unit_id` BIGINT NULL COMMENT '生产单元ID',
  `equipment_id` BIGINT NULL COMMENT '设备ID',
  `alert_type` VARCHAR(64) NOT NULL COMMENT '告警类型',
  `alert_level` CHAR(1) NOT NULL DEFAULT '1' COMMENT '告警等级: 1=一般 2=紧急 3=严重',
  `reporter_user_id` BIGINT NULL COMMENT '上报人ID',
  `handler_user_id` BIGINT NULL COMMENT '处理人ID',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '告警状态: 0=待处理 1=处理中 2=已关闭',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
  `handle_time` DATETIME NULL COMMENT '处理时间',
  `escalation_status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '升级状态: 0=未升级 1=已升级',
  `escalated_by` BIGINT NULL COMMENT '升级人ID',
  `escalation_time` DATETIME NULL COMMENT '升级时间',
  `escalation_remark` VARCHAR(255) NULL COMMENT '升级备注',
  PRIMARY KEY (`alert_id`),
  UNIQUE KEY `uk_andon_alert_alert_no` (`alert_no`),
  INDEX `idx_aa_reporter` (`reporter_user_id`),
  INDEX `idx_aa_handler` (`handler_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安灯告警表';

-- ==================================================================
-- 2.7 仓储管理模块 (wms_*)
-- ==================================================================

-- 仓储管理 / 仓储位置表
CREATE TABLE `wms_location` (
  `location_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '库位ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父级ID',
  `location_code` VARCHAR(64) NOT NULL COMMENT '位置编码（唯一）',
  `location_name` VARCHAR(128) NOT NULL COMMENT '位置名称',
  `location_type` CHAR(1) NOT NULL COMMENT '位置类型: W=仓库 A=库区 L=库位',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=启用 1=停用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`location_id`),
  UNIQUE KEY `uk_wms_location_location_code` (`location_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓储位置表';

-- 仓储管理 / 库存表
CREATE TABLE `wms_inventory` (
  `inventory_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  `item_id` BIGINT NOT NULL COMMENT '物料ID',
  `location_id` BIGINT NOT NULL COMMENT '库位ID',
  `qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '库存数量',
  `locked_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '锁定数量',
  `batch_no` VARCHAR(64) NULL COMMENT '批次号',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`inventory_id`),
  INDEX `idx_inv_item` (`item_id`),
  INDEX `idx_inv_location` (`location_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';

-- 仓储管理 / 库存单据主表
CREATE TABLE `wms_stock_doc` (
  `doc_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '单据ID',
  `doc_no` VARCHAR(64) NOT NULL COMMENT '单据编号（唯一）',
  `doc_type` CHAR(1) NOT NULL COMMENT '单据类型: I=入库 O=出库 T=移库',
  `related_order_no` VARCHAR(64) NULL COMMENT '关联单号',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '单据状态: 0=草稿 1=已提交 2=已执行',
  `details_json` JSON NOT NULL COMMENT '明细JSON',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`doc_id`),
  UNIQUE KEY `uk_wms_stock_doc_doc_no` (`doc_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存单据主表';

-- 仓储管理 / 库存流水表
CREATE TABLE `wms_stock_flow` (
  `flow_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `doc_id` BIGINT NOT NULL COMMENT '库存单据ID',
  `doc_no` VARCHAR(64) NULL COMMENT '单据编号',
  `inventory_id` BIGINT NOT NULL COMMENT '库存ID',
  `item_id` BIGINT NOT NULL COMMENT '物料ID',
  `location_id` BIGINT NOT NULL COMMENT '库位ID',
  `batch_no` VARCHAR(64) NULL COMMENT '批次号',
  `change_qty` DECIMAL(18,4) NOT NULL COMMENT '变动数量（正=入库/移库入，负=出库/移库出）',
  `before_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '变动前数量',
  `after_qty` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '变动后数量',
  `flow_type` VARCHAR(8) NOT NULL COMMENT 'I=入库 O=出库 T=移库',
  `related_order_no` VARCHAR(64) NULL COMMENT '关联单号',
  `create_by` BIGINT NULL COMMENT '操作人ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水时间',
  PRIMARY KEY (`flow_id`),
  INDEX `idx_wsf_doc` (`doc_id`),
  INDEX `idx_wsf_inventory` (`inventory_id`),
  INDEX `idx_wsf_item` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表';

-- ==================================================================
-- 2.8 安全管理模块 (saf_*)
-- ==================================================================

-- 安全管理 / 安全事件表
CREATE TABLE `saf_safety_event` (
  `event_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父级ID',
  `event_type` CHAR(1) NOT NULL COMMENT '事件类型',
  `title` VARCHAR(128) NOT NULL COMMENT '事件标题',
  `level` CHAR(1) NULL COMMENT '风险等级',
  `responsible_user` VARCHAR(64) NULL COMMENT '责任人',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=待处理 1=处理中 2=已关闭）',
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `close_time` DATETIME NULL COMMENT '关闭时间',
  PRIMARY KEY (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全事件表';

-- 安全管理 / 培训活动表
CREATE TABLE `saf_training` (
  `training_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '培训ID',
  `training_title` VARCHAR(128) NOT NULL COMMENT '培训主题',
  `training_type` VARCHAR(64) NULL COMMENT '培训类型',
  `trainer` VARCHAR(64) NULL COMMENT '讲师',
  `start_time` DATETIME NULL COMMENT '开始时间',
  `end_time` DATETIME NULL COMMENT '结束时间',
  `users_json` JSON NULL COMMENT '参训人员JSON',
  `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态（0=未开始 1=进行中 2=已结束）',
  PRIMARY KEY (`training_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训活动表';

-- ==================================================================
-- 2.9 ERP集成模块 (erp_*)
-- ==================================================================

-- ERP集成 / ERP同步日志表（真实ERP接口对接待扩展）
CREATE TABLE `erp_sync_log` (
  `sync_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '同步ID',
  `biz_type` VARCHAR(64) NOT NULL COMMENT '业务类型',
  `biz_id` VARCHAR(64) NULL COMMENT '业务ID',
  `sync_direction` CHAR(1) NOT NULL COMMENT '同步方向: I=入站 O=出站',
  `sync_status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '同步状态: 0=待同步 1=同步中 2=成功 3=失败',
  `request_data` JSON NULL COMMENT '请求数据',
  `response_data` JSON NULL COMMENT '响应数据',
  `sync_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '同步时间',
  PRIMARY KEY (`sync_id`),
  UNIQUE KEY `uk_erp_sync_log_biz` (`biz_type`, `biz_id`, `sync_direction`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP同步日志表';

-- ==================== 3. 外键约束 ====================

-- 系统管理外键
ALTER TABLE `sys_user` ADD CONSTRAINT `fk_sys_user_role_id` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`role_id`);
ALTER TABLE `sys_role_menu` ADD CONSTRAINT `fk_srm_role_id` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`role_id`);
ALTER TABLE `sys_role_menu` ADD CONSTRAINT `fk_srm_menu_id` FOREIGN KEY (`menu_id`) REFERENCES `sys_menu` (`menu_id`);
ALTER TABLE `sys_user_password_history` ADD CONSTRAINT `fk_uph_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`);
ALTER TABLE `user_refresh_token` ADD CONSTRAINT `fk_urt_user_id` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`);

-- 基础数据外键
ALTER TABLE `base_bom` ADD CONSTRAINT `fk_base_bom_item_id` FOREIGN KEY (`item_id`) REFERENCES `base_item` (`item_id`);
ALTER TABLE `base_route` ADD CONSTRAINT `fk_base_route_item_id` FOREIGN KEY (`item_id`) REFERENCES `base_item` (`item_id`);
ALTER TABLE `base_equipment` ADD CONSTRAINT `fk_base_equipment_unit_id` FOREIGN KEY (`unit_id`) REFERENCES `base_work_unit` (`unit_id`);

-- 生产执行外键
ALTER TABLE `pro_work_order` ADD CONSTRAINT `fk_pwo_item_id` FOREIGN KEY (`item_id`) REFERENCES `base_item` (`item_id`);
ALTER TABLE `pro_work_order` ADD CONSTRAINT `fk_pwo_work_unit_id` FOREIGN KEY (`work_unit_id`) REFERENCES `base_work_unit` (`unit_id`);
ALTER TABLE `pro_work_order` ADD CONSTRAINT `fk_pwo_route_id` FOREIGN KEY (`route_id`) REFERENCES `base_route` (`route_id`);
ALTER TABLE `pro_work_process` ADD CONSTRAINT `fk_pwp_work_order_id` FOREIGN KEY (`work_order_id`) REFERENCES `pro_work_order` (`work_order_id`);
ALTER TABLE `pro_work_process` ADD CONSTRAINT `fk_pwp_process_id` FOREIGN KEY (`process_id`) REFERENCES `base_process` (`process_id`);
ALTER TABLE `pro_work_process` ADD CONSTRAINT `fk_pwp_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `base_equipment` (`equipment_id`);

-- 质量管理外键
ALTER TABLE `qms_inspection_item` ADD CONSTRAINT `fk_qii_process_id` FOREIGN KEY (`process_id`) REFERENCES `base_process` (`process_id`);
ALTER TABLE `qms_inspection` ADD CONSTRAINT `fk_qi_work_order_id` FOREIGN KEY (`work_order_id`) REFERENCES `pro_work_order` (`work_order_id`);
ALTER TABLE `qms_inspection` ADD CONSTRAINT `fk_qi_process_id` FOREIGN KEY (`process_id`) REFERENCES `base_process` (`process_id`);
ALTER TABLE `qms_inspection` ADD CONSTRAINT `fk_qi_process_record_id` FOREIGN KEY (`process_record_id`) REFERENCES `pro_work_process` (`process_record_id`);
ALTER TABLE `qms_defect` ADD CONSTRAINT `fk_qd_inspection_id` FOREIGN KEY (`inspection_id`) REFERENCES `qms_inspection` (`inspection_id`);
ALTER TABLE `qms_defect` ADD CONSTRAINT `fk_qd_work_order_id` FOREIGN KEY (`work_order_id`) REFERENCES `pro_work_order` (`work_order_id`);
ALTER TABLE `qms_defect` ADD CONSTRAINT `fk_qd_process_id` FOREIGN KEY (`process_id`) REFERENCES `base_process` (`process_id`);

-- 设备管理外键
ALTER TABLE `eqm_maintenance` ADD CONSTRAINT `fk_eqm_mtn_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `base_equipment` (`equipment_id`);
ALTER TABLE `eqm_repair_order` ADD CONSTRAINT `fk_ero_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `base_equipment` (`equipment_id`);

-- 安灯管理外键
ALTER TABLE `andon_alert` ADD CONSTRAINT `fk_aa_work_order_id` FOREIGN KEY (`work_order_id`) REFERENCES `pro_work_order` (`work_order_id`);
ALTER TABLE `andon_alert` ADD CONSTRAINT `fk_aa_unit_id` FOREIGN KEY (`unit_id`) REFERENCES `base_work_unit` (`unit_id`);
ALTER TABLE `andon_alert` ADD CONSTRAINT `fk_aa_equipment_id` FOREIGN KEY (`equipment_id`) REFERENCES `base_equipment` (`equipment_id`);

-- 仓储管理外键
ALTER TABLE `wms_inventory` ADD CONSTRAINT `fk_inv_item_id` FOREIGN KEY (`item_id`) REFERENCES `base_item` (`item_id`);
ALTER TABLE `wms_inventory` ADD CONSTRAINT `fk_inv_location_id` FOREIGN KEY (`location_id`) REFERENCES `wms_location` (`location_id`);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- ==================== 4. 系统种子数据（事务包裹） ====================
-- ============================================================================
START TRANSACTION;

-- ==================== 4.1 角色种子数据 ====================
INSERT INTO `sys_role` (`role_id`, `role_key`, `role_name`, `data_scope`, `status`, `create_time`) VALUES
  (1, 'admin',      '系统管理员',   'ALL',  '0', NOW()),
  (2, 'planner',    '生产计划员',   'ALL',  '0', NOW()),
  (3, 'operator',   '生产操作员',   'SELF', '0', NOW()),
  (4, 'inspector',  '质量检验员',   'ALL',  '0', NOW()),
  (5, 'maintainer', '设备维修员',   'SELF', '0', NOW()),
  (6, 'viewer',     '普通查看用户', 'SELF', '0', NOW()),
  (7, 'warehouse',  '仓库管理员',   'SELF', '0', NOW())
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `data_scope` = VALUES(`data_scope`), `status` = VALUES(`status`);

-- ==================== 4.2 菜单/权限种子数据 ====================

-- ===== Section A: 小程序端权限 (ID 100-701, M=模块, F=权限) =====
INSERT INTO `sys_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `icon`, `visible`, `keep_alive`, `redirect`, `perms`, `menu_type`, `order_num`, `status`) VALUES
-- 首页
(100, 0, '首页', NULL, NULL, NULL, '0', '0', NULL, NULL, 'M', 1, '0'),
(101, 100, '查看首页', NULL, NULL, NULL, '0', '0', NULL, 'app:home:view', 'F', 1, '0'),

-- 工单
(200, 0, '工单', NULL, NULL, NULL, '0', '0', NULL, NULL, 'M', 2, '0'),
(201, 200, '查看本人', NULL, NULL, NULL, '0', '0', NULL, 'app:work-order:view', 'F', 1, '0'),
(202, 200, '查看全部', NULL, NULL, NULL, '0', '0', NULL, 'app:work-order:view-all', 'F', 2, '0'),

-- 工序报工
(300, 0, '工序报工', NULL, NULL, NULL, '0', '0', NULL, NULL, 'M', 3, '0'),
(301, 300, '查看本人', NULL, NULL, NULL, '0', '0', NULL, 'app:work-process:view', 'F', 1, '0'),
(302, 300, '查看全部', NULL, NULL, NULL, '0', '0', NULL, 'app:work-process:view-all', 'F', 2, '0'),
(303, 300, '新建报工', NULL, NULL, NULL, '0', '0', NULL, 'app:work-process:create', 'F', 3, '0'),
(304, 300, '开始工序', NULL, NULL, NULL, '0', '0', NULL, 'app:work-process:start', 'F', 4, '0'),
(305, 300, '完成工序', NULL, NULL, NULL, '0', '0', NULL, 'app:work-process:complete', 'F', 5, '0'),
(306, 300, '报工', NULL, NULL, NULL, '0', '0', NULL, 'app:work-process:report', 'F', 6, '0'),
(307, 300, '发起返工', NULL, NULL, NULL, '0', '0', NULL, 'app:work-process:rework-start', 'F', 7, '0'),
(308, 300, '返工完成', NULL, NULL, NULL, '0', '0', NULL, 'app:work-process:rework-complete', 'F', 8, '0'),

-- 质量检验
(400, 0, '质量检验', NULL, NULL, NULL, '0', '0', NULL, NULL, 'M', 4, '0'),
(401, 400, '查看本人', NULL, NULL, NULL, '0', '0', NULL, 'app:inspection:view', 'F', 1, '0'),
(402, 400, '查看全部', NULL, NULL, NULL, '0', '0', NULL, 'app:inspection:view-all', 'F', 2, '0'),
(403, 400, '录入检验', NULL, NULL, NULL, '0', '0', NULL, 'app:inspection:create', 'F', 3, '0'),
(404, 400, '确认结果', NULL, NULL, NULL, '0', '0', NULL, 'app:inspection:confirm', 'F', 4, '0'),

-- 安灯告警
(500, 0, '安灯告警', NULL, NULL, NULL, '0', '0', NULL, NULL, 'M', 5, '0'),
(501, 500, '查看本人', NULL, NULL, NULL, '0', '0', NULL, 'app:andon:view', 'F', 1, '0'),
(502, 500, '查看全部', NULL, NULL, NULL, '0', '0', NULL, 'app:andon:view-all', 'F', 2, '0'),
(503, 500, '上报告警', NULL, NULL, NULL, '0', '0', NULL, 'app:andon:create', 'F', 3, '0'),
(504, 500, '处理告警', NULL, NULL, NULL, '0', '0', NULL, 'app:andon:handle', 'F', 4, '0'),
(505, 500, '关闭告警', NULL, NULL, NULL, '0', '0', NULL, 'app:andon:close', 'F', 5, '0'),
(506, 500, '查看待处理', NULL, NULL, NULL, '0', '0', NULL, 'app:andon:view-pending', 'F', 6, '0'),

-- 设备报修
(600, 0, '设备报修', NULL, NULL, NULL, '0', '0', NULL, NULL, 'M', 6, '0'),
(601, 600, '查看本人', NULL, NULL, NULL, '0', '0', NULL, 'app:repair:view', 'F', 1, '0'),
(602, 600, '查看全部', NULL, NULL, NULL, '0', '0', NULL, 'app:repair:view-all', 'F', 2, '0'),
(603, 600, '提交报修', NULL, NULL, NULL, '0', '0', NULL, 'app:repair:create', 'F', 3, '0'),
(604, 600, '受理维修', NULL, NULL, NULL, '0', '0', NULL, 'app:repair:accept', 'F', 4, '0'),
(605, 600, '完成维修', NULL, NULL, NULL, '0', '0', NULL, 'app:repair:complete', 'F', 5, '0'),
(606, 600, '查看待处理', NULL, NULL, NULL, '0', '0', NULL, 'app:repair:view-pending', 'F', 6, '0'),

-- 库存查询
(700, 0, '库存查询', NULL, NULL, NULL, '0', '0', NULL, NULL, 'M', 7, '0'),
(701, 700, '查看库存', NULL, NULL, NULL, '0', '0', NULL, 'app:inventory:view', 'F', 1, '0')

ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `component` = VALUES(`component`), `icon` = VALUES(`icon`), `visible` = VALUES(`visible`), `keep_alive` = VALUES(`keep_alive`), `redirect` = VALUES(`redirect`), `perms` = VALUES(`perms`), `menu_type` = VALUES(`menu_type`), `order_num` = VALUES(`order_num`), `status` = VALUES(`status`);

-- ===== Section B: 管理端目录/菜单页 (ID 1000-1600, D=目录 C=菜单页) =====
INSERT INTO `sys_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `icon`, `visible`, `keep_alive`, `redirect`, `perms`, `menu_type`, `order_num`, `status`) VALUES
-- 首页
(1600, 0, '首页', '/dashboard', 'dashboard/index', 'HomeFilled', '1', '0', NULL, NULL, 'C', 0, '0'),

-- 系统管理
(1000, 0, '系统管理', '/system', 'Layout', 'Setting', '1', '0', '/system/user', NULL, 'D', 1, '0'),
(1001, 1000, '用户管理', 'user', 'system/user', 'User', '1', '0', NULL, 'admin:user:view', 'C', 1, '0'),
(1002, 1000, '角色管理', 'role', 'system/role', 'UserFilled', '1', '1', NULL, 'admin:role:view', 'C', 2, '0'),
(1003, 1000, '菜单管理', 'menu', 'system/menu', 'Menu', '1', '1', NULL, 'admin:permission:manage', 'C', 3, '0'),
(1004, 1000, '参数配置', 'config', 'system/config', 'Tools', '1', '0', NULL, 'admin:config:view', 'C', 4, '0'),
(1005, 1000, '操作日志', 'oper-log', 'system/operLog', 'Notebook', '1', '0', NULL, 'admin:oper-log:view', 'C', 5, '0'),
(1006, 1000, 'ERP同步日志', 'erp-sync-log', 'system/erpSyncLog', 'Refresh', '1', '0', NULL, 'admin:erp-sync-log:view', 'C', 6, '0'),

-- 基础数据
(1100, 0, '基础数据', '/base', 'Layout', 'Collection', '1', '0', '/base/item', NULL, 'D', 2, '0'),
(1101, 1100, '物料管理', 'item', 'base/item', 'Box', '1', '1', NULL, 'admin:item:view', 'C', 1, '0'),
(1102, 1100, '设备台账', 'equipment', 'base/equipment', 'Monitor', '1', '1', NULL, 'admin:equipment:view', 'C', 2, '0'),
(1103, 1100, '工序管理', 'process', 'base/process', 'SetUp', '1', '1', NULL, 'admin:process:view', 'C', 3, '0'),
(1104, 1100, '工作单元', 'work-unit', 'base/workUnit', 'Grid', '1', '0', NULL, 'admin:work-unit:view', 'C', 4, '0'),
(1105, 1100, 'BOM管理', 'bom', 'base/bom', 'Files', '1', '0', NULL, 'admin:bom:view', 'C', 5, '0'),
(1106, 1100, '工艺路线', 'route', 'base/route', 'Share', '1', '0', NULL, 'admin:route:view', 'C', 6, '0'),

-- 生产执行
(1200, 0, '生产执行', '/production', 'Layout', 'Operation', '1', '0', '/production/work-order', NULL, 'D', 3, '0'),
(1201, 1200, '工单管理', 'work-order', 'production/workOrder', 'Document', '1', '1', NULL, 'admin:work-order:view', 'C', 1, '0'),
(1202, 1200, '生产工序', 'work-process', 'production/workProcess', 'Connection', '1', '1', NULL, 'admin:work-process:view', 'C', 2, '0'),
(1203, 1200, '安灯告警管理', 'andon-alert', 'production/andonAlert', 'Bell', '1', '1', NULL, 'admin:andon:view', 'C', 3, '0'),
(1204, 1200, '设备保养', 'maintenance', 'production/maintenance', 'Tool', '1', '0', NULL, 'admin:maintenance:view', 'C', 4, '0'),
(1205, 1200, '设备维修工单', 'repair-order', 'production/repairOrder', 'Wrench', '1', '1', NULL, 'admin:repair:view', 'C', 5, '0'),
(1206, 1200, '安全事件', 'safety-event', 'production/safetyEvent', 'WarningFilled', '1', '0', NULL, 'admin:safety-event:view', 'C', 6, '0'),
(1207, 1200, '安全培训', 'safety-training', 'production/safetyTraining', 'School', '1', '0', NULL, 'admin:safety-training:view', 'C', 7, '0'),

-- 仓储管理
(1300, 0, '仓储管理', '/warehouse', 'Layout', 'Goods', '1', '0', '/warehouse/location', NULL, 'D', 4, '0'),
(1301, 1300, '库位管理', 'location', 'warehouse/location', 'Location', '1', '0', NULL, 'admin:location:view', 'C', 1, '0'),
(1302, 1300, '库存台账', 'inventory', 'warehouse/inventory', 'List', '1', '1', NULL, 'admin:inventory:view', 'C', 2, '0'),
(1303, 1300, '出入库单', 'stock-doc', 'warehouse/stockDoc', 'Tickets', '1', '0', NULL, 'admin:stock-doc:view', 'C', 3, '0'),

-- 质量管理
(1400, 0, '质量管理', '/quality', 'Layout', 'Checked', '1', '0', '/quality/inspection', NULL, 'D', 5, '0'),
(1401, 1400, '质检单', 'inspection', 'quality/inspection', 'Search', '1', '1', NULL, 'admin:inspection:view', 'C', 1, '0'),
(1402, 1400, '缺陷管理', 'defect', 'quality/defect', 'Warning', '1', '1', NULL, 'admin:defect:view', 'C', 2, '0'),
(1403, 1400, '质检项管理', 'inspection-item', 'quality/inspectionItem', 'List', '1', '1', NULL, 'admin:inspection-item:view', 'C', 3, '0'),

-- 报表中心
(1500, 0, '报表中心', '/report', 'Layout', 'DataAnalysis', '1', '0', '/report/work-order', NULL, 'D', 6, '0'),
(1501, 1500, '生产工单报表', 'work-order', 'report/workOrder', 'Document', '1', '0', NULL, 'admin:report:view', 'C', 1, '0'),
(1502, 1500, '库存报表', 'inventory', 'report/inventory', 'Goods', '1', '0', NULL, 'admin:report:view', 'C', 2, '0'),
(1503, 1500, '质量检验报表', 'quality', 'report/quality', 'Checked', '1', '0', NULL, 'admin:report:view', 'C', 3, '0'),
(1504, 1500, '设备维修报表', 'equipment-repair', 'report/equipmentRepair', 'Monitor', '1', '0', NULL, 'admin:report:view', 'C', 4, '0'),
(1505, 1500, '安灯告警报表', 'andon', 'report/andon', 'Warning', '1', '0', NULL, 'admin:report:view', 'C', 5, '0')

ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `component` = VALUES(`component`), `icon` = VALUES(`icon`), `visible` = VALUES(`visible`), `keep_alive` = VALUES(`keep_alive`), `redirect` = VALUES(`redirect`), `perms` = VALUES(`perms`), `menu_type` = VALUES(`menu_type`), `order_num` = VALUES(`order_num`), `status` = VALUES(`status`);

-- ===== Section C: 管理端按钮/权限 (ID 10000+) =====
INSERT INTO `sys_menu` (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `icon`, `visible`, `keep_alive`, `redirect`, `perms`, `menu_type`, `order_num`, `status`) VALUES
-- 用户管理按钮
(10011, 1001, '新增用户', NULL, NULL, NULL, '0', '0', NULL, 'admin:user:manage', 'F', 1, '0'),
(10012, 1001, '修改用户', NULL, NULL, NULL, '0', '0', NULL, 'admin:user:manage', 'F', 2, '0'),
(10013, 1001, '删除用户', NULL, NULL, NULL, '0', '0', NULL, 'admin:user:manage', 'F', 3, '0'),
(10014, 1001, '解锁用户', NULL, NULL, NULL, '0', '0', NULL, 'admin:user:unlock', 'F', 4, '0'),

-- 角色管理按钮
(10021, 1002, '新增角色', NULL, NULL, NULL, '0', '0', NULL, 'admin:role:manage', 'F', 1, '0'),
(10022, 1002, '修改角色', NULL, NULL, NULL, '0', '0', NULL, 'admin:role:manage', 'F', 2, '0'),
(10023, 1002, '删除角色', NULL, NULL, NULL, '0', '0', NULL, 'admin:role:manage', 'F', 3, '0'),
(10024, 1002, '角色授权', NULL, NULL, NULL, '0', '0', NULL, 'admin:role:manage', 'F', 4, '0'),

-- 菜单管理按钮
(10031, 1003, '新增菜单', NULL, NULL, NULL, '0', '0', NULL, 'admin:permission:manage', 'F', 1, '0'),
(10032, 1003, '修改菜单', NULL, NULL, NULL, '0', '0', NULL, 'admin:permission:manage', 'F', 2, '0'),
(10033, 1003, '删除菜单', NULL, NULL, NULL, '0', '0', NULL, 'admin:permission:manage', 'F', 3, '0'),

-- 生产工单按钮
(12011, 1201, '新增工单', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-order:manage', 'F', 1, '0'),
(12012, 1201, '修改工单', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-order:manage', 'F', 2, '0'),
(12013, 1201, '删除工单', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-order:manage', 'F', 3, '0'),
(12014, 1201, '指派执行人', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-order:assign', 'F', 4, '0'),
(12015, 1201, '下达工单', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-order:release', 'F', 5, '0'),
(12016, 1201, '暂停工单', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-order:pause', 'F', 6, '0'),
(12017, 1201, '恢复工单', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-order:resume', 'F', 7, '0'),
(12018, 1201, '取消工单', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-order:cancel', 'F', 8, '0'),
(12019, 1201, '关闭工单', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-order:close', 'F', 9, '0'),

-- 生产工序按钮
(12021, 1202, '新增工序记录', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-process:manage', 'F', 1, '0'),
(12022, 1202, '修改工序记录', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-process:manage', 'F', 2, '0'),
(12023, 1202, '删除工序记录', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-process:manage', 'F', 3, '0'),
(12024, 1202, '跳过工序', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-process:skip', 'F', 4, '0'),
(12025, 1202, '取消工序', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-process:cancel', 'F', 5, '0'),
(12026, 1202, '质检通过', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-process:qc-pass', 'F', 6, '0'),
(12027, 1202, '质检驳回', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-process:qc-fail', 'F', 7, '0'),
(12028, 1202, '返工操作', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-process:rework', 'F', 8, '0'),

-- 安灯告警按钮
(12031, 1203, '处理告警', NULL, NULL, NULL, '0', '0', NULL, 'admin:andon:manage', 'F', 1, '0'),
(12032, 1203, '关闭告警', NULL, NULL, NULL, '0', '0', NULL, 'admin:andon:manage', 'F', 2, '0'),

-- 设备维修按钮
(12051, 1205, '新增维修单', NULL, NULL, NULL, '0', '0', NULL, 'admin:repair:manage', 'F', 1, '0'),
(12052, 1205, '受理维修', NULL, NULL, NULL, '0', '0', NULL, 'admin:repair:manage', 'F', 2, '0'),
(12053, 1205, '完成维修', NULL, NULL, NULL, '0', '0', NULL, 'admin:repair:manage', 'F', 3, '0'),

-- 缺陷管理按钮
(14021, 1402, '新增缺陷', NULL, NULL, NULL, '0', '0', NULL, 'admin:defect:manage', 'F', 1, '0'),
(14022, 1402, '修改缺陷', NULL, NULL, NULL, '0', '0', NULL, 'admin:defect:manage', 'F', 2, '0'),
(14023, 1402, '删除缺陷', NULL, NULL, NULL, '0', '0', NULL, 'admin:defect:manage', 'F', 3, '0'),

-- 物料管理按钮
(11011, 1101, '新增物料', NULL, NULL, NULL, '0', '0', NULL, 'admin:item:manage', 'F', 1, '0'),
(11012, 1101, '修改物料', NULL, NULL, NULL, '0', '0', NULL, 'admin:item:manage', 'F', 2, '0'),
(11013, 1101, '删除物料', NULL, NULL, NULL, '0', '0', NULL, 'admin:item:manage', 'F', 3, '0'),

-- 设备台账按钮
(11021, 1102, '新增设备', NULL, NULL, NULL, '0', '0', NULL, 'admin:equipment:manage', 'F', 1, '0'),
(11022, 1102, '修改设备', NULL, NULL, NULL, '0', '0', NULL, 'admin:equipment:manage', 'F', 2, '0'),
(11023, 1102, '删除设备', NULL, NULL, NULL, '0', '0', NULL, 'admin:equipment:manage', 'F', 3, '0'),

-- 库存台账按钮
(13021, 1302, '修改库存', NULL, NULL, NULL, '0', '0', NULL, 'admin:inventory:manage', 'F', 1, '0'),

-- 报表导出按钮
(15011, 1501, '导出工单报表', NULL, NULL, NULL, '0', '0', NULL, 'admin:report:export', 'F', 1, '0'),
(15021, 1502, '导出库存报表', NULL, NULL, NULL, '0', '0', NULL, 'admin:report:export', 'F', 1, '0'),
(15031, 1503, '导出质量报表', NULL, NULL, NULL, '0', '0', NULL, 'admin:report:export', 'F', 1, '0'),
(15041, 1504, '导出维修报表', NULL, NULL, NULL, '0', '0', NULL, 'admin:report:export', 'F', 1, '0'),
(15051, 1505, '导出安灯报表', NULL, NULL, NULL, '0', '0', NULL, 'admin:report:export', 'F', 1, '0'),

-- 参数配置/操作日志/ERP同步日志按钮
(10041, 1004, '新增参数', NULL, NULL, NULL, '0', '0', NULL, 'admin:config:manage', 'F', 1, '0'),
(10042, 1004, '修改参数', NULL, NULL, NULL, '0', '0', NULL, 'admin:config:manage', 'F', 2, '0'),
(10043, 1004, '删除参数', NULL, NULL, NULL, '0', '0', NULL, 'admin:config:manage', 'F', 3, '0'),
(10051, 1005, '删除日志', NULL, NULL, NULL, '0', '0', NULL, 'admin:oper-log:manage', 'F', 1, '0'),
(10061, 1006, '新增同步日志', NULL, NULL, NULL, '0', '0', NULL, 'admin:erp-sync-log:manage', 'F', 1, '0'),
(10062, 1006, '修改同步日志', NULL, NULL, NULL, '0', '0', NULL, 'admin:erp-sync-log:manage', 'F', 2, '0'),
(10063, 1006, '删除同步日志', NULL, NULL, NULL, '0', '0', NULL, 'admin:erp-sync-log:manage', 'F', 3, '0'),

-- 工序/工作单元/BOM/工艺路线按钮
(11031, 1103, '新增工序', NULL, NULL, NULL, '0', '0', NULL, 'admin:process:manage', 'F', 1, '0'),
(11032, 1103, '修改工序', NULL, NULL, NULL, '0', '0', NULL, 'admin:process:manage', 'F', 2, '0'),
(11033, 1103, '删除工序', NULL, NULL, NULL, '0', '0', NULL, 'admin:process:manage', 'F', 3, '0'),
(11041, 1104, '新增工作单元', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-unit:manage', 'F', 1, '0'),
(11042, 1104, '修改工作单元', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-unit:manage', 'F', 2, '0'),
(11043, 1104, '删除工作单元', NULL, NULL, NULL, '0', '0', NULL, 'admin:work-unit:manage', 'F', 3, '0'),
(11051, 1105, '新增BOM', NULL, NULL, NULL, '0', '0', NULL, 'admin:bom:manage', 'F', 1, '0'),
(11052, 1105, '修改BOM', NULL, NULL, NULL, '0', '0', NULL, 'admin:bom:manage', 'F', 2, '0'),
(11053, 1105, '删除BOM', NULL, NULL, NULL, '0', '0', NULL, 'admin:bom:manage', 'F', 3, '0'),
(11061, 1106, '新增工艺路线', NULL, NULL, NULL, '0', '0', NULL, 'admin:route:manage', 'F', 1, '0'),
(11062, 1106, '修改工艺路线', NULL, NULL, NULL, '0', '0', NULL, 'admin:route:manage', 'F', 2, '0'),
(11063, 1106, '删除工艺路线', NULL, NULL, NULL, '0', '0', NULL, 'admin:route:manage', 'F', 3, '0'),

-- 设备保养/安全事件/安全培训按钮
(12041, 1204, '新增保养', NULL, NULL, NULL, '0', '0', NULL, 'admin:maintenance:manage', 'F', 1, '0'),
(12042, 1204, '修改保养', NULL, NULL, NULL, '0', '0', NULL, 'admin:maintenance:manage', 'F', 2, '0'),
(12043, 1204, '删除保养', NULL, NULL, NULL, '0', '0', NULL, 'admin:maintenance:manage', 'F', 3, '0'),
(12061, 1206, '新增安全事件', NULL, NULL, NULL, '0', '0', NULL, 'admin:safety-event:manage', 'F', 1, '0'),
(12062, 1206, '修改安全事件', NULL, NULL, NULL, '0', '0', NULL, 'admin:safety-event:manage', 'F', 2, '0'),
(12063, 1206, '删除安全事件', NULL, NULL, NULL, '0', '0', NULL, 'admin:safety-event:manage', 'F', 3, '0'),
(12071, 1207, '新增安全培训', NULL, NULL, NULL, '0', '0', NULL, 'admin:safety-training:manage', 'F', 1, '0'),
(12072, 1207, '修改安全培训', NULL, NULL, NULL, '0', '0', NULL, 'admin:safety-training:manage', 'F', 2, '0'),
(12073, 1207, '删除安全培训', NULL, NULL, NULL, '0', '0', NULL, 'admin:safety-training:manage', 'F', 3, '0'),

-- 库位/库存/出入库单按钮
(13011, 1301, '新增库位', NULL, NULL, NULL, '0', '0', NULL, 'admin:location:manage', 'F', 1, '0'),
(13012, 1301, '修改库位', NULL, NULL, NULL, '0', '0', NULL, 'admin:location:manage', 'F', 2, '0'),
(13013, 1301, '删除库位', NULL, NULL, NULL, '0', '0', NULL, 'admin:location:manage', 'F', 3, '0'),
(13022, 1302, '新增库存', NULL, NULL, NULL, '0', '0', NULL, 'admin:inventory:manage', 'F', 2, '0'),
(13023, 1302, '删除库存', NULL, NULL, NULL, '0', '0', NULL, 'admin:inventory:manage', 'F', 3, '0'),
(13031, 1303, '新增单据', NULL, NULL, NULL, '0', '0', NULL, 'admin:stock-doc:manage', 'F', 1, '0'),
(13032, 1303, '修改单据', NULL, NULL, NULL, '0', '0', NULL, 'admin:stock-doc:manage', 'F', 2, '0'),
(13033, 1303, '删除单据', NULL, NULL, NULL, '0', '0', NULL, 'admin:stock-doc:manage', 'F', 3, '0'),
(13034, 1303, '执行单据', NULL, NULL, NULL, '0', '0', NULL, 'admin:stock-doc:manage', 'F', 4, '0'),

-- 质检项管理按钮
(14031, 1403, '新增质检项', NULL, NULL, NULL, '0', '0', NULL, 'admin:inspection-item:manage', 'F', 1, '0'),
(14032, 1403, '修改质检项', NULL, NULL, NULL, '0', '0', NULL, 'admin:inspection-item:manage', 'F', 2, '0'),
(14033, 1403, '删除质检项', NULL, NULL, NULL, '0', '0', NULL, 'admin:inspection-item:manage', 'F', 3, '0')

ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `path` = VALUES(`path`), `component` = VALUES(`component`), `icon` = VALUES(`icon`), `visible` = VALUES(`visible`), `keep_alive` = VALUES(`keep_alive`), `redirect` = VALUES(`redirect`), `perms` = VALUES(`perms`), `menu_type` = VALUES(`menu_type`), `order_num` = VALUES(`order_num`), `status` = VALUES(`status`);

-- ==================== 4.3 演示用户种子数据 ====================
-- 统一开发密码: Demo@123456 (所有演示账号共用)
-- BCrypt hash: $2b$10$rTom51Fr.4SmIIh/OrFleu75ZL3VH0j8uCvudJz49k4kKhYSzRBNS
INSERT INTO `sys_user` (`user_id`, `username`, `password`, `nickname`, `phone`, `role_id`, `openid`, `avatar`, `status`, `must_change_password`, `login_fail_count`, `create_time`) VALUES
  (1, 'admin',        '$2b$10$rTom51Fr.4SmIIh/OrFleu75ZL3VH0j8uCvudJz49k4kKhYSzRBNS', '系统管理员',   '13800000001', 1, NULL, NULL, '0', 0, 0, NOW()),
  (2, 'planner',      '$2b$10$rTom51Fr.4SmIIh/OrFleu75ZL3VH0j8uCvudJz49k4kKhYSzRBNS', '生产计划员',   '13800000002', 2, NULL, NULL, '0', 0, 0, NOW()),
  (3, 'operator01',   '$2b$10$rTom51Fr.4SmIIh/OrFleu75ZL3VH0j8uCvudJz49k4kKhYSzRBNS', '操作员张三',   '13800000003', 3, 'demo_openid_operator01', NULL, '0', 0, 0, NOW()),
  (4, 'inspector01',  '$2b$10$rTom51Fr.4SmIIh/OrFleu75ZL3VH0j8uCvudJz49k4kKhYSzRBNS', '质检员李四',   '13800000004', 4, 'demo_openid_inspector01', NULL, '0', 0, 0, NOW()),
  (5, 'maintainer01', '$2b$10$rTom51Fr.4SmIIh/OrFleu75ZL3VH0j8uCvudJz49k4kKhYSzRBNS', '维修员工五',   '13800000005', 5, 'demo_openid_maintainer01', NULL, '0', 0, 0, NOW()),
  (6, 'viewer01',     '$2b$10$rTom51Fr.4SmIIh/OrFleu75ZL3VH0j8uCvudJz49k4kKhYSzRBNS', '查看用户赵六', '13800000006', 6, 'demo_openid_viewer01', NULL, '0', 0, 0, NOW())
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `nickname` = VALUES(`nickname`),
  `phone` = VALUES(`phone`),
  `role_id` = VALUES(`role_id`),
  `status` = VALUES(`status`);

-- ==================== 4.4 管理员角色绑定全部权限 ====================
-- a) 管理员获取所有小程序端权限（menu_type=F, status=0, menu_id < 1000）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_key = 'admin'
  AND m.menu_type = 'F'
  AND m.status = '0'
  AND m.menu_id < 1000
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- b) 管理员获取所有管理端菜单和按钮权限（menu_id >= 1000）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_key = 'admin'
  AND m.menu_type IN ('D', 'C', 'F')
  AND m.status = '0'
  AND m.menu_id >= 1000
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- ==================== 4.5 各角色权限分配 ====================

-- 生产计划员：管理端全部查看权限 + 工单管理
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id FROM sys_role r, sys_menu m
WHERE r.role_key = 'planner' AND m.perms IN (
    'app:home:view',
    'app:work-order:view', 'app:work-order:view-all',
    'app:work-process:view', 'app:work-process:view-all',
    'app:inspection:view', 'app:inspection:view-all',
    'app:andon:view', 'app:andon:view-all', 'app:andon:view-pending',
    'app:andon:create', 'app:andon:handle', 'app:andon:close',
    'app:repair:view', 'app:repair:view-all',
    'app:inventory:view',
    'admin:work-order:manage',
    'admin:work-order:assign', 'admin:work-order:release',
    'admin:work-order:pause', 'admin:work-order:resume',
    'admin:work-order:cancel', 'admin:work-order:close',
    'admin:item:view', 'admin:equipment:view', 'admin:process:view',
    'admin:work-unit:view', 'admin:bom:view', 'admin:route:view',
    'admin:work-process:view', 'admin:andon:view', 'admin:maintenance:view',
    'admin:repair:view', 'admin:safety-event:view', 'admin:safety-training:view',
    'admin:location:view', 'admin:inventory:view', 'admin:stock-doc:view',
    'admin:inspection:view', 'admin:defect:view', 'admin:inspection-item:view',
    'admin:report:view', 'admin:report:export', 'admin:user:view', 'admin:role:view',
    'admin:config:view', 'admin:oper-log:view', 'admin:erp-sync-log:view'
) AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 生产操作员：工单查看 / 工序执行全权限 / 报修上报 / 库存查看
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id FROM sys_role r, sys_menu m
WHERE r.role_key = 'operator' AND m.perms IN (
    'app:home:view', 'app:work-order:view',
    'app:work-process:view', 'app:work-process:create',
    'app:work-process:start', 'app:work-process:complete',
    'app:work-process:report',
    'app:work-process:rework-start', 'app:work-process:rework-complete',
    'app:andon:view', 'app:andon:view-pending', 'app:andon:create',
    'app:repair:view', 'app:repair:create',
    'app:inventory:view'
) AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 质量检验员：检验全权限 + 基础功能
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id FROM sys_role r, sys_menu m
WHERE r.role_key = 'inspector' AND m.perms IN (
    'app:home:view', 'app:work-order:view',
    'app:work-process:view',
    'app:andon:view',
    'app:inspection:view', 'app:inspection:view-all',
    'app:inspection:create', 'app:inspection:confirm',
    'app:inventory:view'
) AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 设备维修员：报修全权限 + 基础功能
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id FROM sys_role r, sys_menu m
WHERE r.role_key = 'maintainer' AND m.perms IN (
    'app:home:view',
    'app:repair:view', 'app:repair:view-all',
    'app:repair:accept', 'app:repair:complete',
    'app:repair:view-pending',
    'app:andon:view', 'app:andon:view-all', 'app:andon:view-pending',
    'app:andon:create', 'app:andon:handle', 'app:andon:close',
    'app:inventory:view'
) AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 普通查看用户：所有模块只读
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id FROM sys_role r, sys_menu m
WHERE r.role_key = 'viewer' AND m.perms IN (
    'app:home:view',
    'app:work-order:view',
    'app:work-process:view',
    'app:inspection:view',
    'app:andon:view',
    'app:repair:view',
    'app:inventory:view',
    'admin:item:view', 'admin:equipment:view', 'admin:process:view',
    'admin:work-unit:view', 'admin:bom:view', 'admin:route:view',
    'admin:work-order:view', 'admin:work-process:view', 'admin:andon:view',
    'admin:maintenance:view', 'admin:repair:view',
    'admin:safety-event:view', 'admin:safety-training:view',
    'admin:location:view', 'admin:inventory:view', 'admin:stock-doc:view',
    'admin:inspection:view', 'admin:defect:view', 'admin:inspection-item:view',
    'admin:report:view', 'admin:report:export', 'admin:user:view', 'admin:role:view',
    'admin:config:view', 'admin:oper-log:view', 'admin:erp-sync-log:view'
) AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- 仓库管理员：库存功能
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id FROM sys_role r, sys_menu m
WHERE r.role_key = 'warehouse' AND m.perms IN (
    'app:home:view', 'app:inventory:view'
) AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- ==================== 4.5.1 补全角色菜单父目录（D） ====================
-- 角色被授予菜单页（C）时，若父目录（D）未分配，自动补齐，
-- 避免 buildTree 时页面因缺少父节点而从菜单树中丢失。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT DISTINCT rm.role_id, parent.menu_id
FROM sys_role_menu rm
INNER JOIN sys_menu child ON child.menu_id = rm.menu_id
INNER JOIN sys_menu parent ON parent.menu_id = child.parent_id
WHERE parent.menu_type = 'D'
  AND parent.status = '0'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = parent.menu_id);

-- ==================== 4.5.2 计划员/查看用户：补齐管理端目录与菜单页 ====================
-- 这两个角色的定位是"管理端全部查看"，但按 perms IN(...) 授种子会漏掉
-- perms 为空的首页(1600)以及按权限码枚举不到的页面，因此直接全量授予 D/C。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_key IN ('planner', 'viewer')
  AND m.menu_type IN ('D', 'C')
  AND m.status = '0'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

-- ==================== 4.6 系统配置种子数据 ====================
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_name`, `config_type`, `remark`, `status`) VALUES
  ('system.name', 'Bike Assembly MES', '系统名称', 'system', '自行车组装MES系统', '0'),
  ('system.version', '3.7.3', '系统版本', 'system', '当前数据库schema版本', '0'),
  ('password.min_length', '8', '密码最小长度', 'security', '默认8位', '0'),
  ('password.max_length', '64', '密码最大长度', 'security', '默认64位', '0'),
  ('password.history_count', '5', '密码历史保留数', 'security', '最近N次密码不可重复使用', '0'),
  ('password.expire_days', '90', '密码过期天数', 'security', '0=永不过期', '0'),
  ('login.max_fail_count', '5', '登录最大失败次数', 'security', '连续失败N次锁定', '0'),
  ('login.lock_minutes', '15', '登录锁定分钟数', 'security', '锁定N分钟后自动解锁', '0')
ON DUPLICATE KEY UPDATE `config_name` = VALUES(`config_name`), `config_value` = VALUES(`config_value`), `remark` = VALUES(`remark`);

-- ==================== 5. 提交事务 ====================
COMMIT;

-- ============================================================================
-- DONE: 数据库初始化完成
-- 创建了 32 张表
-- 插入了角色、菜单、权限、用户、系统配置种子数据
-- ============================================================================
-- 验证 SQL:
--   SELECT 'Tables created:' AS checkpoint, COUNT(*) AS count FROM information_schema.TABLES WHERE table_schema = DATABASE();
--   SELECT user_id, username, nickname, role_id, status FROM sys_user;
--   SELECT role_id, role_key, role_name FROM sys_role;
--   SELECT COUNT(*) AS menu_count FROM sys_menu;
--   SELECT COUNT(*) AS role_menu_count FROM sys_role_menu;
-- ============================================================================
