-- ============================================================
-- 测试数据库重置脚本
-- 用途：清空所有业务数据，重新执行 init_database.sql（DDL + 系统种子数据）
-- 可重复执行（幂等）
-- 执行命令：
--   mysql -u root -p123456 bike_assembly_mes < sql/test/reset_test_database.sql
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 删除所有业务表（保留系统表 sys_user/sys_role/sys_menu/sys_role_menu/sys_oper_log）
DROP TABLE IF EXISTS `sys_config`;
DROP TABLE IF EXISTS `sys_user_password_history`;
DROP TABLE IF EXISTS `user_refresh_token`;
DROP TABLE IF EXISTS `pro_message_consume_log`;
DROP TABLE IF EXISTS `pro_idempotency_record`;
DROP TABLE IF EXISTS `pro_status_transition_log`;
DROP TABLE IF EXISTS `erp_sync_log`;
DROP TABLE IF EXISTS `saf_training`;
DROP TABLE IF EXISTS `saf_safety_event`;
DROP TABLE IF EXISTS `andon_alert`;
DROP TABLE IF EXISTS `eqm_repair_order`;
DROP TABLE IF EXISTS `eqm_maintenance`;
DROP TABLE IF EXISTS `qms_defect`;
DROP TABLE IF EXISTS `qms_inspection`;
DROP TABLE IF EXISTS `qms_inspection_item`;
DROP TABLE IF EXISTS `wms_stock_doc`;
DROP TABLE IF EXISTS `wms_inventory`;
DROP TABLE IF EXISTS `wms_location`;
DROP TABLE IF EXISTS `pro_work_process`;
DROP TABLE IF EXISTS `pro_work_order`;
DROP TABLE IF EXISTS `base_equipment`;
DROP TABLE IF EXISTS `base_route`;
DROP TABLE IF EXISTS `base_bom`;
DROP TABLE IF EXISTS `base_process`;
DROP TABLE IF EXISTS `base_work_unit`;
DROP TABLE IF EXISTS `base_item`;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 执行 init_database.sql 建表（DDL + 系统种子数据，已合并全部旧 migration）
-- ============================================================
SOURCE sql/init_database.sql;

SELECT '=== Database reset completed ===' AS status;
