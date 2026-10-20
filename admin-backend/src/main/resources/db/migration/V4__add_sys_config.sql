-- =============================================================================
-- V4：新增「系统配置」表与内置配置项
--
-- 设计说明：
--   1. 配置值统一用 VARCHAR(2000) 存储（不做分类型列），由应用层按 config_type 解析；
--      理由：配置项类型在运营期可能变更（NUMBER↔STRING），用强类型列会锁死并破坏兼容性；
--   2. builtin=1 为内置项，SQL 层与应用层双重保护：不可删除（可改值）；
--   3. 初始内置项与 application.yml 的默认值一一对应，运行期以数据库为准（可在管理界面实时调整）；
--      注意：application.yml 的值仍是"数据库不可用时的兜底"，两者含义需保持一致。
--
-- 跨库兼容：沿用 V1 约定（BIGINT / VARCHAR / INTEGER / TIMESTAMP，无 COMMENT、无自增、无外键）。
-- =============================================================================

CREATE TABLE sys_config (
    id           BIGINT        NOT NULL,  -- 主键（雪花 ID）
    config_key   VARCHAR(100)  NOT NULL,  -- 配置键，业务唯一（例：admin.title）
    config_value VARCHAR(2000),           -- 配置值（始终为字符串，按 config_type 解析）
    config_type  VARCHAR(20)   DEFAULT 'STRING', -- 值类型：STRING / NUMBER / BOOLEAN / JSON
    builtin      INTEGER       DEFAULT 0, -- 是否内置：0=否（可删） 1=是（不可删）
    remark       VARCHAR(500),            -- 备注：说明该配置的用途与取值含义
    create_by    BIGINT,                  -- 创建人
    create_time  TIMESTAMP,               -- 创建时间
    update_by    BIGINT,                  -- 更新人
    update_time  TIMESTAMP,               -- 更新时间
    deleted      INTEGER       DEFAULT 0, -- 逻辑删除：0=未删除 1=已删除
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_sys_config_key ON sys_config (config_key);

-- ---------------------------- 内置配置项 ----------------------------
-- 说明：
--   1. 均为 builtin=1（不可删除，可改值），删除会破坏依赖它的代码路径；
--   2. 数值型用 NUMBER，开关型用 BOOLEAN，取值含义见 remark；
--   3. 新增配置请在此处追加（保证各环境初始状态一致），或用管理界面动态新增（builtin=0）。
INSERT INTO sys_config (id, config_key, config_value, config_type, builtin, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(1,  'admin.title',                     '通用后台管理系统',        'STRING',  1, '站点标题（浏览器标签与登录页展示）',          1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(2,  'admin.copyright',                 '© 2026 Admin System',     'STRING',  1, '页脚版权信息',                                 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(3,  'admin.theme.default',             'auto',                    'STRING',  1, '新用户默认主题：light / dark / auto',          1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(4,  'admin.login.max-retry',           '5',                       'NUMBER',  1, '同一账号在统计窗口内允许的最大失败次数',       1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(5,  'admin.login.retry-window-minutes','5',                       'NUMBER',  1, '失败次数统计窗口（分钟）',                     1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(6,  'admin.login.lock-minutes',        '10',                      'NUMBER',  1, '触发失败上限后的锁定时长（分钟）',             1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(7,  'admin.login.ip-limit-per-minute', '20',                      'NUMBER',  1, '同一 IP 每分钟最多登录请求次数',               1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(8,  'admin.password.min-length',       '8',                       'NUMBER',  1, '密码最小长度',                                 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(9,  'admin.verify.code-ttl-seconds',   '300',                     'NUMBER',  1, '一次性验证码有效期（秒）',                     1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(10, 'admin.session.access-token-ttl',  '7200',                    'NUMBER',  1, '访问令牌有效期（秒）',                         1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(11, 'admin.upload.max-size-mb',        '10',                      'NUMBER',  1, '单文件上传大小上限（MB）',                     1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(12, 'admin.upload.allowed-types',      'jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx', 'STRING', 1, '允许上传的文件扩展名（逗号分隔）', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(13, 'admin.security.auto-lock-enabled','true',                    'BOOLEAN', 1, '是否开启失败自动锁定',                         1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);
