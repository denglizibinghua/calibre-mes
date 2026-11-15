-- ============================================================
-- 测试数据库重置脚本（PostgreSQL 16 / psql）
-- 用途：清空所有业务数据，重新执行 init_database.sql（DDL + 系统种子数据）
-- 可重复执行（幂等）
-- 执行方式（从仓库根目录）：
--   psql -U mes -d bike_assembly_mes -v ON_ERROR_STOP=1 -f sql/test/reset_test_database.sql
-- ============================================================

\set ON_ERROR_STOP on

-- 删除所有业务表（保留系统表 sys_user/sys_role/sys_menu/sys_role_menu/sys_oper_log；
-- init_database.sql 随后会以 CASCADE 重建全部表）
DROP TABLE IF EXISTS sys_config CASCADE;
DROP TABLE IF EXISTS sys_user_password_history CASCADE;
DROP TABLE IF EXISTS user_refresh_token CASCADE;
DROP TABLE IF EXISTS pro_message_consume_log CASCADE;
DROP TABLE IF EXISTS pro_idempotency_record CASCADE;
DROP TABLE IF EXISTS pro_status_transition_log CASCADE;
DROP TABLE IF EXISTS erp_sync_log CASCADE;
DROP TABLE IF EXISTS saf_training CASCADE;
DROP TABLE IF EXISTS saf_safety_event CASCADE;
DROP TABLE IF EXISTS andon_alert CASCADE;
DROP TABLE IF EXISTS eqm_repair_order CASCADE;
DROP TABLE IF EXISTS eqm_maintenance CASCADE;
DROP TABLE IF EXISTS qms_defect CASCADE;
DROP TABLE IF EXISTS qms_inspection CASCADE;
DROP TABLE IF EXISTS qms_inspection_item CASCADE;
DROP TABLE IF EXISTS wms_stock_doc CASCADE;
DROP TABLE IF EXISTS wms_inventory CASCADE;
DROP TABLE IF EXISTS wms_location CASCADE;
DROP TABLE IF EXISTS pro_work_process CASCADE;
DROP TABLE IF EXISTS pro_work_order CASCADE;
DROP TABLE IF EXISTS base_equipment CASCADE;
DROP TABLE IF EXISTS base_route CASCADE;
DROP TABLE IF EXISTS base_bom CASCADE;
DROP TABLE IF EXISTS base_process CASCADE;
DROP TABLE IF EXISTS base_work_unit CASCADE;
DROP TABLE IF EXISTS base_item CASCADE;

-- ============================================================
-- 执行 init_database.sql 建表（DDL + 系统种子数据，已合并全部旧 migration）
-- ============================================================
\ir ../init_database.sql

SELECT '=== Database reset completed ===' AS status;
