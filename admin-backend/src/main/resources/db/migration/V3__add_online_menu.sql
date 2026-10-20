-- =============================================================================
-- V3：新增「在线用户」菜单与按钮权限
-- 说明：
--   1. 菜单 id 沿用 V2 规划（1 系统管理目录、2~5 二级菜单），故新增菜单用 6，按钮用 601/602；
--   2. menu_type：2=菜单、3=按钮；visible=1 显示、status=0 正常（与领域模型一致）；
--   3. 授权给「超级管理员」(role_id=1) 与「普通管理员」(role_id=2)，
--      超级管理员在后端还拥有 *:*:* 通配权限，此处一并授权是为了菜单树结构完整。
-- =============================================================================

-- 二级菜单：在线用户（挂在系统管理目录下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(6, 1, '在线用户', 2, '/system/online', 'system/online/index', 'system:online:list', 'ep:monitor', 5, 1, 0, '在线会话与可信设备管理', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- 三级：按钮级权限
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(601, 6, '强制下线', 3, '', '', 'system:online:kick',        '', 1, 1, 0, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(602, 6, '移除信任', 3, '', '', 'system:online:trust:remove', '', 2, 1, 0, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- 角色授权：超级管理员
-- 说明：id 采用 9xxx 高位段，避免与 V2 中角色授权行的 id 段冲突（主键冲突会导致迁移失败）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(9001, 1, 6,   1, '2026-01-01 00:00:00'),
(9002, 1, 601, 1, '2026-01-01 00:00:00'),
(9003, 1, 602, 1, '2026-01-01 00:00:00');

-- 角色授权：普通管理员（不给"移除信任"权限，按最小权限原则）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(9011, 2, 6,   1, '2026-01-01 00:00:00'),
(9012, 2, 601, 1, '2026-01-01 00:00:00');
