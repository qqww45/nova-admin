-- =============================================================================
-- V5：新增「系统配置」菜单与按钮权限
-- 说明：
--   1. 菜单 id=7（系统管理目录 id=1 下），按钮 701~704；
--      沿用 V3 的 id 规划：6 已被在线用户占用，故配置用 7；
--   2. 授权：超级管理员(role_id=1) 全量；普通管理员(role_id=2) 只给查看，
--      按最小权限原则——多数管理员不需要改系统级配置；
--   3. 本迁移只加权限数据，不改业务逻辑。
-- =============================================================================

-- 二级菜单：系统配置
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(7, 1, '系统配置', 2, '/system/config', 'system/config/index', 'system:config:list', 'ep:setting', 6, 1, 0, '运行期可调的系统参数', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- 三级：按钮级权限
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(701, 7, '新增配置', 3, '', '', 'system:config:add',    '', 1, 1, 0, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(702, 7, '修改配置', 3, '', '', 'system:config:edit',   '', 2, 1, 0, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(703, 7, '删除配置', 3, '', '', 'system:config:remove', '', 3, 1, 0, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(704, 7, '刷新缓存', 3, '', '', 'system:config:refresh','', 4, 1, 0, '从数据库重新加载全部配置到缓存', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- 角色授权：超级管理员（全量）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(9101, 1, 7,   1, '2026-01-01 00:00:00'),
(9102, 1, 701, 1, '2026-01-01 00:00:00'),
(9103, 1, 702, 1, '2026-01-01 00:00:00'),
(9104, 1, 703, 1, '2026-01-01 00:00:00'),
(9105, 1, 704, 1, '2026-01-01 00:00:00');

-- 角色授权：普通管理员（仅查看 + 刷新）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(9111, 2, 7,   1, '2026-01-01 00:00:00'),
(9112, 2, 704, 1, '2026-01-01 00:00:00');
