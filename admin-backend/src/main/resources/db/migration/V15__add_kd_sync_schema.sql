-- =============================================================================
-- V15：新增「金蝶对账同步」域（桌面采集器上行数据）
--
-- 背景：桌面工具（desktop/）退化为通用采集器，只负责把业务员本人在金蝶可见的
--       客户 / 销售出库单增量上报上来；对账单及后续功能全部在 nova-admin 实现。
--       方案见 docs/金蝶对账同步方案.md。
--
-- 跨库兼容：沿用 V1/V9/V13 约定（BIGINT 雪花主键 / VARCHAR / INTEGER / TIMESTAMP，
--           无 COMMENT、无自增、无外键、无触发器）。
--
-- 金额/数量精度（与客户端 amount_cents / qty_milli / price_cents 一致）：
--   *_cents   BIGINT 存"分"
--   qty_milli BIGINT 存"千分之一"
--   为什么不复用 DECIMAL：客户端 SQLite 里已经是整数，上行 JSON 也是整数，
--   中途换 DECIMAL 只会引入除 100 的舍入点；单位在字段名里写清楚即可。
--
-- 数据隔离：所有业务表都带 (owner_user_id, acct_id)；查询强制 owner_user_id = 当前用户。
-- =============================================================================

-- ---------------------------- 1. 绑定与机器令牌 ----------------------------
-- 一个 nova 用户可绑定多个账套（多行）；同一账套只保留一行，重新绑定即换令牌。
-- 令牌只存 SHA-256 摘要，原文只在换取时返回一次。
CREATE TABLE kd_binding (
    id                BIGINT       NOT NULL,  -- 主键（雪花 ID）
    user_id           BIGINT       NOT NULL,  -- nova 用户 ID（逻辑外键 → sys_user.id）
    acct_id           VARCHAR(64)  NOT NULL,  -- 金蝶账套 ID
    acct_name         VARCHAR(200),           -- 账套名称（客户端填写，仅展示）
    kd_username       VARCHAR(100),           -- 金蝶登录账号（仅展示，密码不上行）
    kd_server_url     VARCHAR(300),           -- 金蝶服务器地址（仅展示）
    machine_name      VARCHAR(200),           -- 客户端机器名
    client_version    VARCHAR(50),            -- 客户端版本
    token_hash        VARCHAR(128) NOT NULL,  -- 机器令牌 SHA-256（十六进制）
    token_prefix      VARCHAR(16),            -- 令牌前 8 位，便于用户辨认是哪台机器
    status            VARCHAR(20)  DEFAULT 'ACTIVE', -- ACTIVE / REVOKED（Java 枚举 KdBindingStatus）
    last_used_at      TIMESTAMP,              -- 最近一次带令牌请求时间
    last_sync_at      TIMESTAMP,              -- 最近一次成功同步时间
    last_sync_result  VARCHAR(500),           -- 最近一次同步结果摘要
    watermark_customer TIMESTAMP,             -- 客户水位（FModifyDate 或 FDate）
    watermark_outbound TIMESTAMP,             -- 出库水位（头 + 分录共用；以头为准）
    version           INTEGER      DEFAULT 0, -- 乐观锁版本号
    create_by         BIGINT,
    create_time       TIMESTAMP,
    update_by         BIGINT,
    update_time       TIMESTAMP,
    deleted           INTEGER      DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_kd_binding_user_acct ON kd_binding (user_id, acct_id); -- 同人同账套唯一
CREATE UNIQUE INDEX uk_kd_binding_token ON kd_binding (token_hash);           -- 令牌摘要唯一（鉴权查找）

-- 一次性绑定码：nova 端生成 → 客户端输入换取令牌。5 分钟有效、用后即焚。
CREATE TABLE kd_binding_code (
    id          BIGINT       NOT NULL,  -- 主键（雪花 ID）
    user_id     BIGINT       NOT NULL,  -- 生成人
    code_hash   VARCHAR(128) NOT NULL,  -- 绑定码 SHA-256
    expire_at   TIMESTAMP    NOT NULL,  -- 过期时间
    used_at     TIMESTAMP,              -- 使用时间（非空表示已用）
    remark      VARCHAR(200),           -- 备注（如"客户现场电脑"）
    create_by   BIGINT,
    create_time TIMESTAMP,
    update_by   BIGINT,
    update_time TIMESTAMP,
    deleted     INTEGER      DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_kd_binding_code_hash ON kd_binding_code (code_hash);
CREATE INDEX idx_kd_binding_code_user ON kd_binding_code (user_id);

-- ---------------------------- 2. 同步任务清单 ----------------------------
-- 客户端不再硬编码"同步哪些单据"，启动 / 收到 config.reload 时拉取本表。
-- acct_id = '*' 表示全局模板；按账套覆盖时同样的 entity 以账套行为准。
CREATE TABLE kd_sync_task (
    id              BIGINT        NOT NULL,  -- 主键（雪花 ID）
    acct_id         VARCHAR(64)   DEFAULT '*', -- '*' 全局模板 / 具体账套
    entity          VARCHAR(32)   NOT NULL,  -- customer / outbound / outbound_line
    form_id         VARCHAR(64)   NOT NULL,  -- 金蝶 FormId，如 SAL_OUTSTOCK
    endpoint        VARCHAR(200)  NOT NULL,  -- 上报接口，如 /kd/sync/outbound
    field_keys      VARCHAR(2000) NOT NULL,  -- 金蝶 FieldKeys
    watermark_field VARCHAR(64),             -- 水位字段（FModifyDate / FDate）
    enabled         INTEGER       DEFAULT 1, -- 是否启用
    sort_no         INTEGER       DEFAULT 0, -- 执行顺序
    remark          VARCHAR(500),
    create_by       BIGINT,
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    deleted         INTEGER       DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_kd_task_acct_entity ON kd_sync_task (acct_id, entity);
CREATE INDEX idx_kd_task_enabled ON kd_sync_task (enabled);

-- ---------------------------- 3. 字段映射 ----------------------------
-- 记录"这家账套的分录字段到底叫什么"，改字段只改这里，客户端不发版。
CREATE TABLE kd_field_mapping (
    id            BIGINT        NOT NULL,  -- 主键（雪花 ID）
    binding_id    BIGINT        NOT NULL,  -- 绑定 ID（逻辑外键 → kd_binding.id）
    mapping_key   VARCHAR(64)   NOT NULL,  -- 如 line_field_keys / customer_po_field
    mapping_value VARCHAR(2000),           -- 映射值
    remark        VARCHAR(500),
    create_by     BIGINT,
    create_time   TIMESTAMP,
    update_by     BIGINT,
    update_time   TIMESTAMP,
    deleted       INTEGER       DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_kd_mapping_binding_key ON kd_field_mapping (binding_id, mapping_key);

-- ---------------------------- 4. 客户 ----------------------------
CREATE TABLE kd_customer (
    id            BIGINT       NOT NULL,  -- 主键（雪花 ID）
    owner_user_id BIGINT       NOT NULL,  -- 数据归属人（nova 用户）
    acct_id       VARCHAR(64)  NOT NULL,  -- 金蝶账套
    kd_fid        BIGINT       NOT NULL,  -- 金蝶 FCUSTID
    kd_number     VARCHAR(100),           -- 金蝶客户编码 FNumber
    customer_name VARCHAR(200),           -- 客户名称 FName
    address       VARCHAR(500),           -- 地址
    disabled      INTEGER      DEFAULT 0, -- 是否禁用（FForbidStatus = B）
    batch_no      VARCHAR(64),            -- 最近一次上报批次
    sync_at       TIMESTAMP,              -- 最近同步时间
    create_by     BIGINT,
    create_time   TIMESTAMP,
    update_by     BIGINT,
    update_time   TIMESTAMP,
    deleted       INTEGER      DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_kd_customer_key ON kd_customer (owner_user_id, acct_id, kd_fid); -- 幂等 upsert 依据
CREATE INDEX idx_kd_customer_name ON kd_customer (customer_name);

-- ---------------------------- 5. 销售出库单头 ----------------------------
CREATE TABLE kd_outbound (
    id            BIGINT       NOT NULL,  -- 主键（雪花 ID）
    owner_user_id BIGINT       NOT NULL,  -- 数据归属人
    acct_id       VARCHAR(64)  NOT NULL,  -- 金蝶账套
    kd_fid        BIGINT       NOT NULL,  -- 金蝶 FID
    bill_no       VARCHAR(100),           -- 单据编号 FBillNo
    biz_date      TIMESTAMP,              -- 业务日期 FDate
    customer_fid  BIGINT,                 -- 金蝶客户内码
    customer_name VARCHAR(200),           -- 客户名称
    doc_status    VARCHAR(20),            -- FDocumentStatus：A 创建 / B 审核中 / C 已审核 / D 重新审核
    cancel_status VARCHAR(20),            -- FCancelStatus：A 正常 / B 已作废
    cancelled     INTEGER      DEFAULT 0, -- 派生：cancel_status = 'B'
    amount_cents  BIGINT       DEFAULT 0, -- 表头金额（分录汇总）
    remark        VARCHAR(500),
    kd_modify_time TIMESTAMP,             -- 金蝶 FModifyDate（水位原始值）
    invalid       INTEGER      DEFAULT 0, -- 批次差集标记失效（1 = 金蝶侧已删除）
    batch_no      VARCHAR(64),            -- 最近一次上报批次
    sync_at       TIMESTAMP,
    create_by     BIGINT,
    create_time   TIMESTAMP,
    update_by     BIGINT,
    update_time   TIMESTAMP,
    deleted       INTEGER      DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_kd_outbound_key ON kd_outbound (owner_user_id, acct_id, kd_fid);
CREATE INDEX idx_kd_outbound_owner_date ON kd_outbound (owner_user_id, biz_date); -- 对账按人按月取数
CREATE INDEX idx_kd_outbound_batch ON kd_outbound (batch_no);

-- ---------------------------- 6. 销售出库单分录 ----------------------------
-- entry_id 是幂等键：优先用金蝶分录内码；账套拿不到内码时由客户端生成
-- （outbound_fid * 100000 + 行序号），保证重复上报仍落到同一行。
CREATE TABLE kd_outbound_line (
    id              BIGINT       NOT NULL,  -- 主键（雪花 ID）
    owner_user_id   BIGINT       NOT NULL,  -- 数据归属人
    acct_id         VARCHAR(64)  NOT NULL,  -- 金蝶账套
    outbound_fid    BIGINT       NOT NULL,  -- 金蝶出库单 FID
    entry_id        BIGINT       NOT NULL,  -- 金蝶分录内码（或客户端生成的稳定键）
    material_number VARCHAR(100),           -- 物料编码
    material_name   VARCHAR(200),           -- 物料名称
    spec            VARCHAR(300),           -- 规格型号
    unit            VARCHAR(20),            -- 单位
    qty_milli       BIGINT       DEFAULT 0, -- 数量 × 1000
    price_cents     BIGINT       DEFAULT 0, -- 单价 × 100
    amount_cents    BIGINT       DEFAULT 0, -- 金额 × 100
    src_order_no    VARCHAR(100),           -- 源单号（销售订单号）
    customer_po     VARCHAR(100),           -- 客户采购订单号
    remark          VARCHAR(500),           -- 行备注
    invalid         INTEGER      DEFAULT 0, -- 批次差集标记失效
    batch_no        VARCHAR(64),            -- 最近一次上报批次
    sync_at         TIMESTAMP,
    create_by       BIGINT,
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    deleted         INTEGER      DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_kd_outbound_line_key ON kd_outbound_line (owner_user_id, acct_id, entry_id);
CREATE INDEX idx_kd_line_outbound ON kd_outbound_line (owner_user_id, outbound_fid);
CREATE INDEX idx_kd_line_batch ON kd_outbound_line (batch_no);

-- ---------------------------- 7. 同步批次 ----------------------------
-- 客户端每次同步生成一个 batchId，逐个实体上报，最后回写结果。
-- 用途：排障（哪批慢/失败）、全量差集（批次内未见到的单据标记失效）。
CREATE TABLE kd_sync_batch (
    id             BIGINT       NOT NULL,  -- 主键（雪花 ID）
    batch_no       VARCHAR(64)  NOT NULL,  -- 客户端批次号（客户端生成，唯一）
    binding_id     BIGINT       NOT NULL,  -- 绑定 ID
    owner_user_id  BIGINT       NOT NULL,  -- 数据归属人
    acct_id        VARCHAR(64)  NOT NULL,  -- 金蝶账套
    entity         VARCHAR(32),            -- customer / outbound / outbound_line / ALL
    mode           VARCHAR(20)  DEFAULT 'INCREMENTAL', -- INCREMENTAL / FULL
    status         VARCHAR(20)  DEFAULT 'RUNNING',     -- RUNNING / SUCCESS / FAILED（Java 枚举 KdBatchStatus）
    row_count      INTEGER      DEFAULT 0, -- 上行总行数
    upsert_count   INTEGER      DEFAULT 0, -- 落库行数
    skipped_count  INTEGER      DEFAULT 0, -- 跳过行数（缺主键等）
    watermark_from TIMESTAMP,              -- 本次起点水位
    watermark_to   TIMESTAMP,              -- 本次终点水位
    started_at     TIMESTAMP,              -- 客户端开始时间
    finished_at    TIMESTAMP,              -- 客户端结束时间
    cost_ms        BIGINT,                 -- 耗时毫秒
    client_version VARCHAR(50),
    message        VARCHAR(1000),          -- 结果 / 错误信息
    create_by      BIGINT,
    create_time    TIMESTAMP,
    update_by      BIGINT,
    update_time    TIMESTAMP,
    deleted        INTEGER      DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_kd_batch_no ON kd_sync_batch (batch_no);
CREATE INDEX idx_kd_batch_owner ON kd_sync_batch (owner_user_id, create_time);
CREATE INDEX idx_kd_batch_binding ON kd_sync_batch (binding_id);

-- ---------------------------- 8. 任务清单初始数据 ----------------------------
-- 字段集与 desktop/src/main/sync/runner.ts 现有实现保持一致；后续改字段只改本表。
INSERT INTO kd_sync_task (id, acct_id, entity, form_id, endpoint, field_keys, watermark_field, enabled, sort_no, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(1, '*', 'customer',      'BD_Customer',  '/kd/sync/customer',      'FCUSTID,FNumber,FName,FADDRESS,FForbidStatus', 'FModifyDate', 1, 1, '客户主数据', 1, '2026-11-02 00:00:00', 1, '2026-11-02 00:00:00', 0),
(2, '*', 'outbound',      'SAL_OUTSTOCK', '/kd/sync/outbound',      'FID,FBillNo,FDate,FCustomerID,FCustomerID.FName,FDocumentStatus,FCancelStatus', 'FModifyDate', 1, 2, '销售出库单头', 1, '2026-11-02 00:00:00', 1, '2026-11-02 00:00:00', 0),
(3, '*', 'outbound_line', 'SAL_OUTSTOCK', '/kd/sync/outbound-line', 'FID,FBillNo,FDate,FCustomerID,FCustomerID.FName,FDocumentStatus,FCancelStatus,FEntity_FEntryID,FMaterialId.FNumber,FMaterialId.FName,FUnitID.FName,FRealQty,FTaxPrice,FAllAmount,FSrcBillNo,FEntryNote', 'FModifyDate', 1, 3, '销售出库单分录', 1, '2026-11-02 00:00:00', 1, '2026-11-02 00:00:00', 0);
