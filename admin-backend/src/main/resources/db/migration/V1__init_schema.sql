-- =============================================================================
-- V1__init_schema.sql  初始化表结构
--
-- 跨库兼容约定（SQLite / MySQL 8 / PostgreSQL 14+）：
--   1. 禁止数据库自增主键：主键为 BIGINT，值由 MyBatis-Plus 雪花算法生成，便于后续分库分表；
--   2. 禁止外键约束、存储过程、触发器、视图：使用逻辑外键，由应用层维护关联完整性；
--   3. 时间字段统一 TIMESTAMP，值由应用层（MyBatis-Plus 自动填充）写入，禁止 DATETIME('now') 等方言函数；
--   4. 布尔/状态统一 INTEGER(0/1)，不用 TINYINT（PostgreSQL 无此类型）；
--   5. 不使用 SQLite 私有语法（AUTOINCREMENT / PRAGMA / IFNULL 等），不使用 COMMENT 关键字（SQLite 不支持）。
--
-- 字段注释约定：
--   每个字段右侧的 "-- xxx" 即该字段的说明（含枚举取值含义），这是跨库都能保留的唯一方式；
--   切换 MySQL / PostgreSQL 后如需把说明写进数据库元数据（SHOW FULL COLUMNS / pg_description 可见），
--   执行 db/comments/ 下对应方言的脚本即可（该脚本不参与 Flyway 自动迁移，避免 SQLite 执行报错）。
--   本文件后续若新增表，请保持"逐字段注释"的风格，字段语义只写一处，避免文档与库结构漂移。
-- =============================================================================

-- ---------------------------- 用户表 ----------------------------
-- 系统账号主体：登录账号、基本资料、主题偏好与最近登录信息；删除为逻辑删除。
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL,  -- 主键（雪花 ID，由 MyBatis-Plus 生成）
    username    VARCHAR(64)  NOT NULL,  -- 登录账号（唯一性由应用层校验，见 UserAppServiceImpl）
    password    VARCHAR(128) NOT NULL,  -- 登录密码（BCrypt 哈希，明文永不上库）
    nickname    VARCHAR(64),            -- 用户昵称（展示用）
    email       VARCHAR(128),           -- 邮箱（列表脱敏展示）
    phone       VARCHAR(32),            -- 手机号（列表脱敏展示）
    sex         INTEGER      DEFAULT 0, -- 性别：0=未知 1=男 2=女
    avatar      VARCHAR(255),           -- 头像地址
    status      INTEGER      DEFAULT 0, -- 账号状态：0=正常 1=停用（停用不可登录）
    theme       VARCHAR(10)  DEFAULT 'auto', -- 主题偏好：light / dark / auto（跨设备同步）
    dept_id     BIGINT       DEFAULT 0, -- 所属部门 ID（0 表示未分配）
    login_ip    VARCHAR(64),            -- 最近一次登录 IP
    login_date  TIMESTAMP,              -- 最近一次登录时间
    remark      VARCHAR(500),           -- 备注
    create_by   BIGINT,                 -- 创建人（用户 ID）
    create_time TIMESTAMP,              -- 创建时间
    update_by   BIGINT,                 -- 更新人（用户 ID）
    update_time TIMESTAMP,              -- 更新时间
    deleted     INTEGER      DEFAULT 0, -- 逻辑删除：0=未删除 1=已删除
    PRIMARY KEY (id)
);
-- 说明：用户名不做数据库唯一索引，因为逻辑删除后旧记录仍占位会导致同名用户无法重建；
--       唯一性由应用层（Service）校验，见 SysUserServiceImpl#checkUsernameUnique。
CREATE INDEX idx_user_username ON sys_user (username);  -- 按账号检索
CREATE INDEX idx_user_status ON sys_user (status);      -- 按状态筛选
CREATE INDEX idx_user_dept ON sys_user (dept_id);       -- 按部门筛选

-- ---------------------------- 角色表 ----------------------------
-- 角色即权限集合：通过 sys_role_menu 关联菜单/按钮，通过 sys_user_role 授予用户。
CREATE TABLE sys_role (
    id          BIGINT       NOT NULL,  -- 主键（雪花 ID）
    role_name   VARCHAR(64)  NOT NULL,  -- 角色名称（展示用，如"普通管理员"）
    role_key    VARCHAR(64)  NOT NULL,  -- 角色权限字符（如 admin / common，代码判断用）
    sort        INTEGER      DEFAULT 0, -- 显示排序（越小越靠前）
    status      INTEGER      DEFAULT 0, -- 角色状态：0=正常 1=停用
    remark      VARCHAR(500),           -- 备注
    create_by   BIGINT,                 -- 创建人
    create_time TIMESTAMP,              -- 创建时间
    update_by   BIGINT,                 -- 更新人
    update_time TIMESTAMP,              -- 更新时间
    deleted     INTEGER      DEFAULT 0, -- 逻辑删除：0=未删除 1=已删除
    PRIMARY KEY (id)
);
CREATE INDEX idx_role_key ON sys_role (role_key);      -- 按权限字符查询
CREATE INDEX idx_role_status ON sys_role (status);     -- 按状态筛选

-- ---------------------------- 菜单/权限表 ----------------------------
-- 菜单树 + 按钮权限：目录/菜单节点驱动前端动态路由与侧边栏，按钮节点（menu_type=3）只作权限标识。
CREATE TABLE sys_menu (
    id          BIGINT       NOT NULL,  -- 主键（雪花 ID）
    parent_id   BIGINT       DEFAULT 0, -- 父菜单 ID（0 表示顶级）
    menu_name   VARCHAR(64)  NOT NULL,  -- 菜单名称（同时作为路由标题）
    menu_type   INTEGER      DEFAULT 2, -- 类型：1=目录 2=菜单 3=按钮
    path        VARCHAR(200),           -- 路由地址（如 /system/user；按钮为空）
    component   VARCHAR(255),           -- 前端组件路径（相对 views 目录；目录为 Layout）
    permission  VARCHAR(100),           -- 权限标识（如 system:user:add，后端 @PreAuthorize 校验）
    icon        VARCHAR(100),           -- 图标名称（Element Plus 图标）
    sort        INTEGER      DEFAULT 0, -- 显示排序（越小越靠前）
    visible     INTEGER      DEFAULT 1, -- 是否显示：1=显示 0=隐藏（隐藏不出现在侧边栏）
    status      INTEGER      DEFAULT 0, -- 菜单状态：0=正常 1=停用
    remark      VARCHAR(500),           -- 备注
    create_by   BIGINT,                 -- 创建人
    create_time TIMESTAMP,              -- 创建时间
    update_by   BIGINT,                 -- 更新人
    update_time TIMESTAMP,              -- 更新时间
    deleted     INTEGER      DEFAULT 0, -- 逻辑删除：0=未删除 1=已删除
    PRIMARY KEY (id)
);
CREATE INDEX idx_menu_parent ON sys_menu (parent_id);      -- 按父级构建菜单树
CREATE INDEX idx_menu_permission ON sys_menu (permission); -- 按权限标识查询（鉴权与授权）

-- ---------------------------- 用户-角色关联表 ----------------------------
-- 用户与角色的多对多关系；一个用户可拥有多个角色，权限取其并集。
CREATE TABLE sys_user_role (
    id          BIGINT       NOT NULL,  -- 主键（雪花 ID）
    user_id     BIGINT       NOT NULL,  -- 用户 ID（逻辑外键 → sys_user.id）
    role_id     BIGINT       NOT NULL,  -- 角色 ID（逻辑外键 → sys_role.id）
    create_by   BIGINT,                 -- 创建人
    create_time TIMESTAMP,              -- 创建时间（授权时间）
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_role ON sys_user_role (user_id, role_id); -- 防止重复授权
CREATE INDEX idx_ur_role ON sys_user_role (role_id);                  -- 按角色反查用户

-- ---------------------------- 角色-菜单关联表 ----------------------------
-- 角色与菜单（含按钮）的多对多关系；决定该角色可见哪些菜单、可用哪些按钮。
CREATE TABLE sys_role_menu (
    id          BIGINT       NOT NULL,  -- 主键（雪花 ID）
    role_id     BIGINT       NOT NULL,  -- 角色 ID（逻辑外键 → sys_role.id）
    menu_id     BIGINT       NOT NULL,  -- 菜单 ID（逻辑外键 → sys_menu.id）
    create_by   BIGINT,                 -- 创建人
    create_time TIMESTAMP,              -- 创建时间（授权时间）
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_role_menu ON sys_role_menu (role_id, menu_id); -- 防止重复授权
CREATE INDEX idx_rm_menu ON sys_role_menu (menu_id);                  -- 按菜单反查角色

-- ---------------------------- 部门表 ----------------------------
-- 组织架构（树形）：parent_id 自关联；用于用户归属与数据范围控制。
CREATE TABLE sys_dept (
    id          BIGINT       NOT NULL,  -- 主键（雪花 ID）
    parent_id   BIGINT       DEFAULT 0, -- 父部门 ID（0 表示顶级）
    dept_name   VARCHAR(64)  NOT NULL,  -- 部门名称
    sort        INTEGER      DEFAULT 0, -- 显示排序（越小越靠前）
    leader      VARCHAR(64),            -- 部门负责人
    phone       VARCHAR(32),            -- 联系电话
    email       VARCHAR(128),           -- 邮箱
    status      INTEGER      DEFAULT 0, -- 部门状态：0=正常 1=停用
    remark      VARCHAR(500),           -- 备注
    create_by   BIGINT,                 -- 创建人
    create_time TIMESTAMP,              -- 创建时间
    update_by   BIGINT,                 -- 更新人
    update_time TIMESTAMP,              -- 更新时间
    deleted     INTEGER      DEFAULT 0, -- 逻辑删除：0=未删除 1=已删除
    PRIMARY KEY (id)
);
CREATE INDEX idx_dept_parent ON sys_dept (parent_id);  -- 按父级构建部门树

-- ---------------------------- 登录日志表 ----------------------------
-- 登录审计：记录每次登录尝试（成功/失败）的账号、来源与终端，用于安全追溯与仪表盘展示。
CREATE TABLE sys_login_log (
    id          BIGINT       NOT NULL,  -- 主键（雪花 ID）
    username    VARCHAR(64),            -- 登录账号
    ip          VARCHAR(64),            -- 登录 IP
    location    VARCHAR(255),           -- 登录地点（IP 归属地，未接入第三方库时为空）
    browser     VARCHAR(128),           -- 浏览器（由 User-Agent 解析）
    os          VARCHAR(128),           -- 操作系统（由 User-Agent 解析）
    status      INTEGER      DEFAULT 0, -- 登录结果：0=成功 1=失败
    msg         VARCHAR(500),           -- 提示信息（失败原因，如"验证码错误"）
    login_time  TIMESTAMP,              -- 登录时间
    PRIMARY KEY (id)
);
CREATE INDEX idx_login_time ON sys_login_log (login_time);      -- 按时间倒序展示
CREATE INDEX idx_login_username ON sys_login_log (username);     -- 按账号追溯

-- ---------------------------- 令牌黑名单表 ----------------------------
-- JWT 失效登记：登出、刷新轮换、强制下线时登记令牌 jti，使其提前失效（JWT 本身无状态）。
CREATE TABLE sys_token_blacklist (
    id          BIGINT       NOT NULL,  -- 主键（雪花 ID）
    jti         VARCHAR(64)  NOT NULL,  -- 令牌唯一标识（JWT 的 jti 声明）
    token_type  VARCHAR(16),            -- 令牌类型：access / refresh
    user_id     BIGINT,                 -- 所属用户 ID（便于按用户批量清理）
    expire_time TIMESTAMP,              -- 令牌原过期时间（便于定期清理已失效记录）
    create_time TIMESTAMP,              -- 加入黑名单的时间
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_blacklist_jti ON sys_token_blacklist (jti);      -- 同一令牌只登记一次
CREATE INDEX idx_blacklist_expire ON sys_token_blacklist (expire_time); -- 按过期时间清理
