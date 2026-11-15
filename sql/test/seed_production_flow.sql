-- ============================================================
-- 生产流程测试种子数据（PostgreSQL 16）
-- 用途：插入最小化黄金测试数据，支持完整生产链测试
-- 前置条件：reset_test_database.sql 已执行
-- 执行方式：
--   psql -U mes -d bike_assembly_mes -v ON_ERROR_STOP=1 -f sql/test/seed_production_flow.sql
-- 可重复执行（ON CONFLICT）
-- ============================================================

-- ============================================================
-- 1. 使用 init_database.sql 已创建的测试角色
--    admin(1), operator(3), inspector(4)
-- ============================================================

-- ============================================================
-- 2. 创建三个测试用户
-- 密码: admin使用init_database.sql已有BCrypt, operator和inspector新设
-- ============================================================

INSERT INTO sys_user (user_id, username, password, nickname, role_id, status, create_time)
VALUES
    (1002, 'test_operator',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '测试操作员', 3, '0', CURRENT_TIMESTAMP),
    (1003, 'test_inspector', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '测试质检员', 4, '0', CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO UPDATE SET
    password = EXCLUDED.password,
    nickname = EXCLUDED.nickname,
    role_id  = EXCLUDED.role_id,
    status   = EXCLUDED.status;

-- test_admin 使用已有的 admin 账号(1)作为管理员

-- ============================================================
-- 3. 产品：测试自行车
-- ============================================================
INSERT INTO base_item (item_id, item_code, item_name, item_type, unit, status) VALUES
(1001, 'BIKE-001', '测试自行车', '1', '台', '0')
ON CONFLICT (item_id) DO NOTHING;

-- ============================================================
-- 4. 生产单元
-- ============================================================
INSERT INTO base_work_unit (unit_id, unit_code, unit_name, unit_type, status) VALUES
(1001, 'WS-001', '组装车间', '1', '0')
ON CONFLICT (unit_id) DO NOTHING;

-- ============================================================
-- 4.5 基础数据 / 工序（route 1001 使用 process 2/6，route 2001 使用 process 1/2）
--     process 1 车架组装(需质检)、2 车轮安装(免检)、6 刹车调试(需质检)
-- ============================================================
INSERT INTO base_process (process_id, process_code, process_name, standard_minutes, status, create_time) VALUES
(1, 'PROC-001', '车架组装', 30, '0', CURRENT_TIMESTAMP),
(2, 'PROC-002', '车轮安装', 20, '0', CURRENT_TIMESTAMP),
(6, 'PROC-006', '刹车调试', 20, '0', CURRENT_TIMESTAMP)
ON CONFLICT (process_id) DO NOTHING;

-- ============================================================
-- 5. 质检项（process 1「车架组装」与 process 6「刹车调试」挂质检项，使其需质检）
-- ============================================================
INSERT INTO qms_inspection_item (inspection_item_id, process_id, item_name, standard, check_method, status) VALUES
(2001, 1, 'QC外观检查', '无划痕、无变形', '目视', '0'),
(2002, 1, 'QC功能测试', '刹车、变速正常', '手动测试', '0'),
(2003, 6, 'QC刹车调试检查', '制动距离达标、无异常声响', '路试', '0')
ON CONFLICT (inspection_item_id) DO UPDATE SET
    process_id   = EXCLUDED.process_id,
    item_name    = EXCLUDED.item_name,
    standard     = EXCLUDED.standard,
    check_method = EXCLUDED.check_method,
    status       = EXCLUDED.status;

-- ============================================================
-- 6. 工艺路线
--    route 1001（ProductionFlow）：step1=process 2 车轮安装(无QC), step2=process 6 刹车调试(有QC)
--    route 2001（QcPass）：step1=process 1 车架组装(需质检), step2=process 2 车轮安装(免检)
-- ============================================================
INSERT INTO base_route (route_id, item_id, route_code, version, steps_json, status) VALUES
(1001, 1001, 'ROUTE-001', 'V1.0',
 '[{"processId":2,"seq":1},{"processId":6,"seq":2}]',
 '0'),
(2001, 1001, 'QC-ROUTE-001', 'V1.0',
 '[{"processId":1,"seq":1},{"processId":2,"seq":2}]',
 '0')
ON CONFLICT (route_id) DO UPDATE SET
    item_id    = EXCLUDED.item_id,
    steps_json = EXCLUDED.steps_json,
    status     = EXCLUDED.status;

-- ============================================================
-- 8. 工单 TEST-WO-001
-- 状态：已创建，指派给测试操作员，关联测试工艺路线
-- ============================================================
INSERT INTO pro_work_order (
    work_order_id, work_order_no, item_id, plan_qty, unit,
    route_id, work_unit_id, assignee_user_id,
    status, version, create_time
) VALUES (
    1001, 'TEST-WO-001', 1001, 10, '台',
    1001, 1001, NULL,
    '0', 0, CURRENT_TIMESTAMP
) ON CONFLICT (work_order_id) DO UPDATE SET
    item_id   = EXCLUDED.item_id,
    plan_qty  = EXCLUDED.plan_qty,
    route_id  = EXCLUDED.route_id,
    status    = EXCLUDED.status,
    assignee_user_id = EXCLUDED.assignee_user_id;

-- ============================================================
-- 9. 推进 identity 序列（显式 id 插入后必须校正）
-- ============================================================
SELECT setval(pg_get_serial_sequence('sys_user', 'user_id'), COALESCE((SELECT MAX(user_id) FROM sys_user), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('base_item', 'item_id'), COALESCE((SELECT MAX(item_id) FROM base_item), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('base_work_unit', 'unit_id'), COALESCE((SELECT MAX(unit_id) FROM base_work_unit), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('base_process', 'process_id'), COALESCE((SELECT MAX(process_id) FROM base_process), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('qms_inspection_item', 'inspection_item_id'), COALESCE((SELECT MAX(inspection_item_id) FROM qms_inspection_item), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('base_route', 'route_id'), COALESCE((SELECT MAX(route_id) FROM base_route), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('pro_work_order', 'work_order_id'), COALESCE((SELECT MAX(work_order_id) FROM pro_work_order), 0) + 1, false);

-- ============================================================
-- 验证
-- ============================================================
SELECT '=== Seed data verification ===' AS status;
SELECT u.user_id, u.username, u.nickname, r.role_name
FROM sys_user u LEFT JOIN sys_role r ON u.role_id = r.role_id
WHERE u.user_id IN (1, 1002, 1003);

SELECT item_id, item_code, item_name FROM base_item WHERE item_id = 1001;
SELECT process_id, process_code, process_name FROM base_process WHERE process_id IN (1, 2, 6);
SELECT route_id, route_code, steps_json FROM base_route WHERE route_id = 1001;
SELECT work_order_id, work_order_no, status, plan_qty FROM pro_work_order WHERE work_order_id = 1001;
SELECT COUNT(*) AS inspection_item_count FROM qms_inspection_item WHERE process_id IN (1, 6) AND status = '0';
