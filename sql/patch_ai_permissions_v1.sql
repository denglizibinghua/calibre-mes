SET client_encoding TO 'UTF8';

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
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    icon = EXCLUDED.icon,
    visible = EXCLUDED.visible,
    keep_alive = EXCLUDED.keep_alive,
    perms = EXCLUDED.perms,
    menu_type = EXCLUDED.menu_type,
    order_num = EXCLUDED.order_num,
    status = EXCLUDED.status;

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
