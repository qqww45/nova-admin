-- =============================================================================
-- MySQL 字段/表注释脚本（8.0+）
--
-- 用途：开发库 SQLite 不支持 COMMENT，字段说明只能以 "--" 注释承载在 DDL 中
--       （见 db/migration/V1__init_schema.sql）；生产切换 MySQL 后执行本脚本，
--       即可在数据库元数据中看到表/字段描述（SHOW FULL COLUMNS / SHOW TABLE STATUS）。
--
-- 使用方式（手动执行，不参与 Flyway 自动迁移）：
--   mysql -u<user> -p <database> < mysql_add_comments.sql
--
-- 注意：MODIFY 必须携带完整列定义，此处与 V1__init_schema.sql 的类型保持一致；
--       若后续 V4+ 迁移修改了列类型，请同步更新本脚本，避免把类型改回旧值。
-- =============================================================================

-- 用户表
ALTER TABLE sys_user
  MODIFY COLUMN id          BIGINT       NOT NULL COMMENT '主键（雪花 ID，由 MyBatis-Plus 生成）',
  MODIFY COLUMN username    VARCHAR(64)  NOT NULL COMMENT '登录账号（唯一性由应用层校验，见 UserAppServiceImpl）',
  MODIFY COLUMN password    VARCHAR(128) NOT NULL COMMENT '登录密码（BCrypt 哈希，明文永不上库）',
  MODIFY COLUMN nickname    VARCHAR(64)  COMMENT '用户昵称（展示用）',
  MODIFY COLUMN email       VARCHAR(128) COMMENT '邮箱（列表脱敏展示）',
  MODIFY COLUMN phone       VARCHAR(32)  COMMENT '手机号（列表脱敏展示）',
  MODIFY COLUMN sex         INTEGER      DEFAULT 0 COMMENT '性别：0=未知 1=男 2=女',
  MODIFY COLUMN avatar      VARCHAR(255) COMMENT '头像地址',
  MODIFY COLUMN status      INTEGER      DEFAULT 0 COMMENT '账号状态：0=正常 1=停用（停用不可登录）',
  MODIFY COLUMN theme       VARCHAR(10)  DEFAULT 'auto' COMMENT '主题偏好：light / dark / auto（跨设备同步）',
  MODIFY COLUMN dept_id     BIGINT       DEFAULT 0 COMMENT '所属部门 ID（0 表示未分配）',
  MODIFY COLUMN login_ip    VARCHAR(64)  COMMENT '最近一次登录 IP',
  MODIFY COLUMN login_date  TIMESTAMP    COMMENT '最近一次登录时间',
  MODIFY COLUMN remark      VARCHAR(500) COMMENT '备注',
  MODIFY COLUMN create_by   BIGINT       COMMENT '创建人（用户 ID）',
  MODIFY COLUMN create_time TIMESTAMP    COMMENT '创建时间',
  MODIFY COLUMN update_by   BIGINT       COMMENT '更新人（用户 ID）',
  MODIFY COLUMN update_time TIMESTAMP    COMMENT '更新时间',
  MODIFY COLUMN deleted     INTEGER      DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  COMMENT = '用户表：系统账号主体（登录账号、资料、主题偏好、最近登录信息），逻辑删除';

-- 角色表
ALTER TABLE sys_role
  MODIFY COLUMN id          BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
  MODIFY COLUMN role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称（展示用，如"普通管理员"）',
  MODIFY COLUMN role_key    VARCHAR(64)  NOT NULL COMMENT '角色权限字符（如 admin / common，代码判断用）',
  MODIFY COLUMN sort        INTEGER      DEFAULT 0 COMMENT '显示排序（越小越靠前）',
  MODIFY COLUMN status      INTEGER      DEFAULT 0 COMMENT '角色状态：0=正常 1=停用',
  MODIFY COLUMN remark     VARCHAR(500) COMMENT '备注',
  MODIFY COLUMN create_by   BIGINT       COMMENT '创建人',
  MODIFY COLUMN create_time TIMESTAMP    COMMENT '创建时间',
  MODIFY COLUMN update_by   BIGINT       COMMENT '更新人',
  MODIFY COLUMN update_time TIMESTAMP    COMMENT '更新时间',
  MODIFY COLUMN deleted     INTEGER      DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  COMMENT = '角色表：权限集合载体，经 sys_role_menu / sys_user_role 关联菜单与用户';

-- 菜单/权限表
ALTER TABLE sys_menu
  MODIFY COLUMN id          BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
  MODIFY COLUMN parent_id   BIGINT       DEFAULT 0 COMMENT '父菜单 ID（0 表示顶级）',
  MODIFY COLUMN menu_name   VARCHAR(64)  NOT NULL COMMENT '菜单名称（同时作为路由标题）',
  MODIFY COLUMN menu_type   INTEGER      DEFAULT 2 COMMENT '类型：1=目录 2=菜单 3=按钮',
  MODIFY COLUMN path        VARCHAR(200) COMMENT '路由地址（如 /system/user；按钮为空）',
  MODIFY COLUMN component   VARCHAR(255) COMMENT '前端组件路径（相对 views 目录；目录为 Layout）',
  MODIFY COLUMN permission  VARCHAR(100) COMMENT '权限标识（如 system:user:add，后端 @PreAuthorize 校验）',
  MODIFY COLUMN icon        VARCHAR(100) COMMENT '图标名称（Element Plus 图标）',
  MODIFY COLUMN sort        INTEGER      DEFAULT 0 COMMENT '显示排序（越小越靠前）',
  MODIFY COLUMN visible     INTEGER      DEFAULT 1 COMMENT '是否显示：1=显示 0=隐藏（隐藏不出现在侧边栏）',
  MODIFY COLUMN status      INTEGER      DEFAULT 0 COMMENT '菜单状态：0=正常 1=停用',
  MODIFY COLUMN remark      VARCHAR(500) COMMENT '备注',
  MODIFY COLUMN create_by   BIGINT       COMMENT '创建人',
  MODIFY COLUMN create_time TIMESTAMP    COMMENT '创建时间',
  MODIFY COLUMN update_by   BIGINT       COMMENT '更新人',
  MODIFY COLUMN update_time TIMESTAMP    COMMENT '更新时间',
  MODIFY COLUMN deleted     INTEGER      DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  COMMENT = '菜单/权限表：菜单树与按钮权限，驱动前端动态路由与后端鉴权';

-- 用户-角色关联表
ALTER TABLE sys_user_role
  MODIFY COLUMN id          BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
  MODIFY COLUMN user_id     BIGINT       NOT NULL COMMENT '用户 ID（逻辑外键 → sys_user.id）',
  MODIFY COLUMN role_id     BIGINT       NOT NULL COMMENT '角色 ID（逻辑外键 → sys_role.id）',
  MODIFY COLUMN create_by   BIGINT       COMMENT '创建人',
  MODIFY COLUMN create_time TIMESTAMP    COMMENT '创建时间（授权时间）',
  COMMENT = '用户-角色关联表：多对多，一个用户可拥有多个角色，权限取并集';

-- 角色-菜单关联表
ALTER TABLE sys_role_menu
  MODIFY COLUMN id          BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
  MODIFY COLUMN role_id     BIGINT       NOT NULL COMMENT '角色 ID（逻辑外键 → sys_role.id）',
  MODIFY COLUMN menu_id     BIGINT       NOT NULL COMMENT '菜单 ID（逻辑外键 → sys_menu.id）',
  MODIFY COLUMN create_by   BIGINT       COMMENT '创建人',
  MODIFY COLUMN create_time TIMESTAMP    COMMENT '创建时间（授权时间）',
  COMMENT = '角色-菜单关联表：决定角色可见的菜单与可用的按钮';

-- 部门表
ALTER TABLE sys_dept
  MODIFY COLUMN id          BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
  MODIFY COLUMN parent_id   BIGINT       DEFAULT 0 COMMENT '父部门 ID（0 表示顶级）',
  MODIFY COLUMN dept_name   VARCHAR(64)  NOT NULL COMMENT '部门名称',
  MODIFY COLUMN sort        INTEGER      DEFAULT 0 COMMENT '显示排序（越小越靠前）',
  MODIFY COLUMN leader      VARCHAR(64)  COMMENT '部门负责人',
  MODIFY COLUMN phone       VARCHAR(32)  COMMENT '联系电话',
  MODIFY COLUMN email       VARCHAR(128) COMMENT '邮箱',
  MODIFY COLUMN status      INTEGER      DEFAULT 0 COMMENT '部门状态：0=正常 1=停用',
  MODIFY COLUMN remark      VARCHAR(500) COMMENT '备注',
  MODIFY COLUMN create_by   BIGINT       COMMENT '创建人',
  MODIFY COLUMN create_time TIMESTAMP    COMMENT '创建时间',
  MODIFY COLUMN update_by   BIGINT       COMMENT '更新人',
  MODIFY COLUMN update_time TIMESTAMP    COMMENT '更新时间',
  MODIFY COLUMN deleted     INTEGER      DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  COMMENT = '部门表：树形组织架构（parent_id 自关联），用于用户归属与数据范围控制';

-- 登录日志表
ALTER TABLE sys_login_log
  MODIFY COLUMN id         BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
  MODIFY COLUMN username   VARCHAR(64)  COMMENT '登录账号',
  MODIFY COLUMN ip         VARCHAR(64)  COMMENT '登录 IP',
  MODIFY COLUMN location   VARCHAR(255) COMMENT '登录地点（IP 归属地，未接入第三方库时为空）',
  MODIFY COLUMN browser    VARCHAR(128) COMMENT '浏览器（由 User-Agent 解析）',
  MODIFY COLUMN os          VARCHAR(128) COMMENT '操作系统（由 User-Agent 解析）',
  MODIFY COLUMN status     INTEGER      DEFAULT 0 COMMENT '登录结果：0=成功 1=失败',
  MODIFY COLUMN msg        VARCHAR(500) COMMENT '提示信息（失败原因，如"验证码错误"）',
  MODIFY COLUMN login_time TIMESTAMP    COMMENT '登录时间',
  COMMENT = '登录日志表：记录每次登录尝试（成功/失败）的账号、来源与终端，用于安全审计';

-- 令牌黑名单表
ALTER TABLE sys_token_blacklist
  MODIFY COLUMN id          BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
  MODIFY COLUMN jti         VARCHAR(64)  NOT NULL COMMENT '令牌唯一标识（JWT 的 jti 声明）',
  MODIFY COLUMN token_type  VARCHAR(16)  COMMENT '令牌类型：access / refresh',
  MODIFY COLUMN user_id     BIGINT       COMMENT '所属用户 ID（便于按用户批量清理）',
  MODIFY COLUMN expire_time TIMESTAMP    COMMENT '令牌原过期时间（便于定期清理已失效记录）',
  MODIFY COLUMN create_time TIMESTAMP    COMMENT '加入黑名单的时间',
  COMMENT = '令牌黑名单表：登出/轮换/强制下线时登记 jti，使 JWT 提前失效';
