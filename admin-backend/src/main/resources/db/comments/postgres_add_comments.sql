-- =============================================================================
-- PostgreSQL 字段/表注释脚本（14+）
--
-- 用途：开发库 SQLite 不支持 COMMENT，字段说明只能以 "--" 注释承载在 DDL 中
--       （见 db/migration/V1__init_schema.sql）；生产切换 PostgreSQL 后执行本脚本，
--       即可在数据库元数据中看到表/字段描述（\d+ 或 pg_description）。
--
-- 使用方式（手动执行，不参与 Flyway 自动迁移）：
--   psql -U <user> -d <database> -f postgres_add_comments.sql
--
-- 说明：PostgreSQL 的 COMMENT ON 只写注释、不涉及列类型，因此不存在"改回旧类型"的风险。
-- =============================================================================

-- 用户表
COMMENT ON TABLE sys_user IS '用户表：系统账号主体（登录账号、资料、主题偏好、最近登录信息），逻辑删除';
COMMENT ON COLUMN sys_user.id IS '主键（雪花 ID，由 MyBatis-Plus 生成）';
COMMENT ON COLUMN sys_user.username IS '登录账号（唯一性由应用层校验，见 UserAppServiceImpl）';
COMMENT ON COLUMN sys_user.password IS '登录密码（BCrypt 哈希，明文永不上库）';
COMMENT ON COLUMN sys_user.nickname IS '用户昵称（展示用）';
COMMENT ON COLUMN sys_user.email IS '邮箱（列表脱敏展示）';
COMMENT ON COLUMN sys_user.phone IS '手机号（列表脱敏展示）';
COMMENT ON COLUMN sys_user.sex IS '性别：0=未知 1=男 2=女';
COMMENT ON COLUMN sys_user.avatar IS '头像地址';
COMMENT ON COLUMN sys_user.status IS '账号状态：0=正常 1=停用（停用不可登录）';
COMMENT ON COLUMN sys_user.theme IS '主题偏好：light / dark / auto（跨设备同步）';
COMMENT ON COLUMN sys_user.dept_id IS '所属部门 ID（0 表示未分配）';
COMMENT ON COLUMN sys_user.login_ip IS '最近一次登录 IP';
COMMENT ON COLUMN sys_user.login_date IS '最近一次登录时间';
COMMENT ON COLUMN sys_user.remark IS '备注';
COMMENT ON COLUMN sys_user.create_by IS '创建人（用户 ID）';
COMMENT ON COLUMN sys_user.create_time IS '创建时间';
COMMENT ON COLUMN sys_user.update_by IS '更新人（用户 ID）';
COMMENT ON COLUMN sys_user.update_time IS '更新时间';
COMMENT ON COLUMN sys_user.deleted IS '逻辑删除：0=未删除 1=已删除';

-- 角色表
COMMENT ON TABLE sys_role IS '角色表：权限集合载体，经 sys_role_menu / sys_user_role 关联菜单与用户';
COMMENT ON COLUMN sys_role.id IS '主键（雪花 ID）';
COMMENT ON COLUMN sys_role.role_name IS '角色名称（展示用，如"普通管理员"）';
COMMENT ON COLUMN sys_role.role_key IS '角色权限字符（如 admin / common，代码判断用）';
COMMENT ON COLUMN sys_role.sort IS '显示排序（越小越靠前）';
COMMENT ON COLUMN sys_role.status IS '角色状态：0=正常 1=停用';
COMMENT ON COLUMN sys_role.remark IS '备注';
COMMENT ON COLUMN sys_role.create_by IS '创建人';
COMMENT ON COLUMN sys_role.create_time IS '创建时间';
COMMENT ON COLUMN sys_role.update_by IS '更新人';
COMMENT ON COLUMN sys_role.update_time IS '更新时间';
COMMENT ON COLUMN sys_role.deleted IS '逻辑删除：0=未删除 1=已删除';

-- 菜单/权限表
COMMENT ON TABLE sys_menu IS '菜单/权限表：菜单树与按钮权限，驱动前端动态路由与后端鉴权';
COMMENT ON COLUMN sys_menu.id IS '主键（雪花 ID）';
COMMENT ON COLUMN sys_menu.parent_id IS '父菜单 ID（0 表示顶级）';
COMMENT ON COLUMN sys_menu.menu_name IS '菜单名称（同时作为路由标题）';
COMMENT ON COLUMN sys_menu.menu_type IS '类型：1=目录 2=菜单 3=按钮';
COMMENT ON COLUMN sys_menu.path IS '路由地址（如 /system/user；按钮为空）';
COMMENT ON COLUMN sys_menu.component IS '前端组件路径（相对 views 目录；目录为 Layout）';
COMMENT ON COLUMN sys_menu.permission IS '权限标识（如 system:user:add，后端 @PreAuthorize 校验）';
COMMENT ON COLUMN sys_menu.icon IS '图标名称（Element Plus 图标）';
COMMENT ON COLUMN sys_menu.sort IS '显示排序（越小越靠前）';
COMMENT ON COLUMN sys_menu.visible IS '是否显示：1=显示 0=隐藏（隐藏不出现在侧边栏）';
COMMENT ON COLUMN sys_menu.status IS '菜单状态：0=正常 1=停用';
COMMENT ON COLUMN sys_menu.remark IS '备注';
COMMENT ON COLUMN sys_menu.create_by IS '创建人';
COMMENT ON COLUMN sys_menu.create_time IS '创建时间';
COMMENT ON COLUMN sys_menu.update_by IS '更新人';
COMMENT ON COLUMN sys_menu.update_time IS '更新时间';
COMMENT ON COLUMN sys_menu.deleted IS '逻辑删除：0=未删除 1=已删除';

-- 用户-角色关联表
COMMENT ON TABLE sys_user_role IS '用户-角色关联表：多对多，一个用户可拥有多个角色，权限取并集';
COMMENT ON COLUMN sys_user_role.id IS '主键（雪花 ID）';
COMMENT ON COLUMN sys_user_role.user_id IS '用户 ID（逻辑外键 → sys_user.id）';
COMMENT ON COLUMN sys_user_role.role_id IS '角色 ID（逻辑外键 → sys_role.id）';
COMMENT ON COLUMN sys_user_role.create_by IS '创建人';
COMMENT ON COLUMN sys_user_role.create_time IS '创建时间（授权时间）';

-- 角色-菜单关联表
COMMENT ON TABLE sys_role_menu IS '角色-菜单关联表：决定角色可见的菜单与可用的按钮';
COMMENT ON COLUMN sys_role_menu.id IS '主键（雪花 ID）';
COMMENT ON COLUMN sys_role_menu.role_id IS '角色 ID（逻辑外键 → sys_role.id）';
COMMENT ON COLUMN sys_role_menu.menu_id IS '菜单 ID（逻辑外键 → sys_menu.id）';
COMMENT ON COLUMN sys_role_menu.create_by IS '创建人';
COMMENT ON COLUMN sys_role_menu.create_time IS '创建时间（授权时间）';

-- 部门表
COMMENT ON TABLE sys_dept IS '部门表：树形组织架构（parent_id 自关联），用于用户归属与数据范围控制';
COMMENT ON COLUMN sys_dept.id IS '主键（雪花 ID）';
COMMENT ON COLUMN sys_dept.parent_id IS '父部门 ID（0 表示顶级）';
COMMENT ON COLUMN sys_dept.dept_name IS '部门名称';
COMMENT ON COLUMN sys_dept.sort IS '显示排序（越小越靠前）';
COMMENT ON COLUMN sys_dept.leader IS '部门负责人';
COMMENT ON COLUMN sys_dept.phone IS '联系电话';
COMMENT ON COLUMN sys_dept.email IS '邮箱';
COMMENT ON COLUMN sys_dept.status IS '部门状态：0=正常 1=停用';
COMMENT ON COLUMN sys_dept.remark IS '备注';
COMMENT ON COLUMN sys_dept.create_by IS '创建人';
COMMENT ON COLUMN sys_dept.create_time IS '创建时间';
COMMENT ON COLUMN sys_dept.update_by IS '更新人';
COMMENT ON COLUMN sys_dept.update_time IS '更新时间';
COMMENT ON COLUMN sys_dept.deleted IS '逻辑删除：0=未删除 1=已删除';

-- 登录日志表
COMMENT ON TABLE sys_login_log IS '登录日志表：记录每次登录尝试（成功/失败）的账号、来源与终端，用于安全审计';
COMMENT ON COLUMN sys_login_log.id IS '主键（雪花 ID）';
COMMENT ON COLUMN sys_login_log.username IS '登录账号';
COMMENT ON COLUMN sys_login_log.ip IS '登录 IP';
COMMENT ON COLUMN sys_login_log.location IS '登录地点（IP 归属地，未接入第三方库时为空）';
COMMENT ON COLUMN sys_login_log.browser IS '浏览器（由 User-Agent 解析）';
COMMENT ON COLUMN sys_login_log.os IS '操作系统（由 User-Agent 解析）';
COMMENT ON COLUMN sys_login_log.status IS '登录结果：0=成功 1=失败';
COMMENT ON COLUMN sys_login_log.msg IS '提示信息（失败原因，如"验证码错误"）';
COMMENT ON COLUMN sys_login_log.login_time IS '登录时间';

-- 令牌黑名单表
COMMENT ON TABLE sys_token_blacklist IS '令牌黑名单表：登出/轮换/强制下线时登记 jti，使 JWT 提前失效';
COMMENT ON COLUMN sys_token_blacklist.id IS '主键（雪花 ID）';
COMMENT ON COLUMN sys_token_blacklist.jti IS '令牌唯一标识（JWT 的 jti 声明）';
COMMENT ON COLUMN sys_token_blacklist.token_type IS '令牌类型：access / refresh';
COMMENT ON COLUMN sys_token_blacklist.user_id IS '所属用户 ID（便于按用户批量清理）';
COMMENT ON COLUMN sys_token_blacklist.expire_time IS '令牌原过期时间（便于定期清理已失效记录）';
COMMENT ON COLUMN sys_token_blacklist.create_time IS '加入黑名单的时间';
