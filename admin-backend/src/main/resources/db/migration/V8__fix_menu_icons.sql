-- =============================================================================
-- V8：修正菜单图标（统一为 Element Plus 图标组件名）
-- 说明：
--   1. 原值形如 'ep:odometer'：带 'ep:' 前缀且为短横线命名，
--      而前端侧边栏是按组件名渲染 <component :is="icon" />，
--      前缀与短横线都命中不到已注册的图标组件，结果一律渲染成空白；
--   2. 统一改为 PascalCase 组件名（Odometer / UserFilled / OfficeBuilding ...），
--      与 @element-plus/icons-vue 全局注册的组件名一一对应；
--   3. 「系统配置」原本与「系统管理」目录同为 ep:setting，同层里两个齿轮无法区分，
--      这里把配置改为 Tools（扳手 = 参数维护），目录保留 Setting（齿轮 = 系统）；
--   4. 仅更新 icon 列，不动 id / path / permission，不影响角色授权；
--      按钮级菜单（menu_type=3）不出现在侧边栏，icon 保持为空。
-- =============================================================================

-- 顶级
UPDATE sys_menu SET icon = 'Odometer' WHERE id = 1000; -- 仪表盘
UPDATE sys_menu SET icon = 'Setting'  WHERE id = 1;    -- 系统管理（目录）

-- 系统管理下的功能菜单
UPDATE sys_menu SET icon = 'User'           WHERE id = 2; -- 用户管理
UPDATE sys_menu SET icon = 'UserFilled'     WHERE id = 3; -- 角色管理
UPDATE sys_menu SET icon = 'Menu'           WHERE id = 4; -- 菜单管理
UPDATE sys_menu SET icon = 'OfficeBuilding' WHERE id = 5; -- 部门管理
UPDATE sys_menu SET icon = 'Monitor'        WHERE id = 6; -- 在线用户
UPDATE sys_menu SET icon = 'Tools'          WHERE id = 7; -- 系统配置
UPDATE sys_menu SET icon = 'Document'       WHERE id = 8; -- 系统日志
