SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO sys_menu
    (menu_id, parent_id, menu_name, path, component, icon, visible, keep_alive,
     redirect, perms, menu_type, order_num, status)
VALUES
    (1601, 0, 'AI助手', 'ai', 'ai/index', 'MagicStick', '1', '0', NULL,
     'admin:ai:view', 'C', 7, '0'),
    (1602, 1601, '知识库管理', NULL, NULL, NULL, '0', '0', NULL,
     'admin:ai:knowledge-manage', 'F', 1, '0'),
    (1603, 1601, '确认排产建议', NULL, NULL, NULL, '0', '0', NULL,
     'admin:ai:plan-confirm', 'F', 2, '0')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    path = VALUES(path),
    component = VALUES(component),
    icon = VALUES(icon),
    visible = VALUES(visible),
    keep_alive = VALUES(keep_alive),
    perms = VALUES(perms),
    menu_type = VALUES(menu_type),
    order_num = VALUES(order_num),
    status = VALUES(status);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms IN (
    'admin:ai:view', 'admin:ai:knowledge-manage', 'admin:ai:plan-confirm'
)
WHERE r.role_key IN ('admin', 'planner')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm
      WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
