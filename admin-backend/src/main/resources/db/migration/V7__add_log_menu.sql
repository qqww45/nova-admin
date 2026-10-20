-- =============================================================================
-- V7：新增「系统日志」菜单与查看权限
-- 说明：
--   1. 菜单 id=8（系统管理目录 id=1 下）；id=7 已被 V5 的「系统配置」占用；
--   2. 仅授予超级管理员(role_id=1)：日志含用户 IP、UA 等敏感信息，按最小权限原则，
--      普通管理员不应具备查看能力；
--   3. 本迁移只加权限数据，在线查看功能由 SysLogController 提供。
-- =============================================================================

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(8, 1, '系统日志', 2, '/system/log', 'system/log/index', 'system:log:list', 'ep:document', 7, 1, 0, '在线查看本机日志（只读）', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- 角色授权：仅超级管理员
INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(9201, 1, 8, 1, '2026-01-01 00:00:00');
