-- =============================================================================
-- V9：新增「客户 / 订单 / 出货」业务表 + 字典 + 导入批次 + 操作日志 + 字段映射
--
-- 跨库兼容：沿用 V1 约定（BIGINT 雪花主键 / VARCHAR / INTEGER / TIMESTAMP / DECIMAL，
--           无 COMMENT、无自增、无外键、无触发器）。
--
-- 金额精度说明（重要）：
--   所有金额、数量统一 DECIMAL(18,4)，税率 DECIMAL(6,4)（0.13 表示 13%）。
--   Java 侧一律 BigDecimal 运算，禁止 float/double。
--   注意：SQLite 会把 DECIMAL 归为 NUMERIC 亲和类型并按 REAL 存储，开发库存在二进制浮点误差；
--   生产库（MySQL / PostgreSQL）为定点数，精度无损。因此金额对账类场景请以生产库为准。
--
-- 派生字段说明：
--   biz_sales_order_item.qty_open        = qty_ordered - qty_shipped，服务端计算，禁止录入；
--   biz_sales_order_item.price_is_tax_included = tax_rate > 0 ? 1 : 0，派生；
--   两者落库只是为了查询与导出方便，唯一的真值来源是 AmountCalculator / QuantityPolicy。
-- =============================================================================

-- ---------------------------- 客户表 ----------------------------
-- 主数据：下单方。唯一键为「标准化后的客户名称」（去空格 + 全半角统一），由应用层校验。
CREATE TABLE biz_customer (
    id            BIGINT        NOT NULL,  -- 主键（雪花 ID）
    customer_name VARCHAR(200)  NOT NULL,  -- 客户名称（唯一，标准化后比对）
    customer_code VARCHAR(50),             -- 客户编号（[待确认]暂不需要，预留）
    address       VARCHAR(500),            -- 地址
    remark        VARCHAR(2000),           -- 备注
    status        INTEGER       DEFAULT 1, -- 状态：1=启用 0=停用
    create_by     BIGINT,                  -- 创建人
    create_time   TIMESTAMP,               -- 创建时间
    update_by     BIGINT,                  -- 更新人
    update_time   TIMESTAMP,               -- 更新时间
    deleted       INTEGER       DEFAULT 0, -- 逻辑删除：0=未删除 1=已删除
    PRIMARY KEY (id)
);
CREATE INDEX idx_customer_name ON biz_customer (customer_name);   -- 按名称检索/匹配导入
CREATE INDEX idx_customer_status ON biz_customer (status);        -- 按状态筛选

-- ---------------------------- 客户联系人表 ----------------------------
-- 客户子表：同一客户有且仅有一个默认联系人（is_default=1），由应用层保证。
CREATE TABLE biz_customer_contact (
    id           BIGINT       NOT NULL,  -- 主键（雪花 ID）
    customer_id  BIGINT       NOT NULL,  -- 客户 ID（逻辑外键 → biz_customer.id）
    contact_name VARCHAR(50)  NOT NULL,  -- 姓名
    title        VARCHAR(50),            -- 职务
    phone        VARCHAR(30),            -- 电话（与 mobile 至少一个非空）
    mobile       VARCHAR(30),            -- 手机
    email        VARCHAR(100),           -- 邮箱
    is_default   INTEGER      DEFAULT 0, -- 是否默认联系人：1=是 0=否
    status       INTEGER      DEFAULT 1, -- 状态：1=启用 0=停用
    remark       VARCHAR(500),           -- 备注
    create_by    BIGINT,                 -- 创建人
    create_time  TIMESTAMP,              -- 创建时间
    update_by    BIGINT,                 -- 更新人
    update_time  TIMESTAMP,              -- 更新时间
    deleted      INTEGER      DEFAULT 0, -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE INDEX idx_contact_customer ON biz_customer_contact (customer_id); -- 按客户查子表

-- ---------------------------- 客户开票信息表 ----------------------------
-- 客户子表：多套开票资料，默认规则同联系人。
CREATE TABLE biz_customer_invoice (
    id              BIGINT       NOT NULL,  -- 主键（雪花 ID）
    customer_id     BIGINT       NOT NULL,  -- 客户 ID（逻辑外键 → biz_customer.id）
    invoice_title   VARCHAR(200) NOT NULL,  -- 开票名称
    tax_no          VARCHAR(50),            -- 纳税人识别号
    bank_name       VARCHAR(200),           -- 开户行
    bank_account    VARCHAR(200),           -- 银行账号
    invoice_address VARCHAR(200),           -- 开票地址
    invoice_phone   VARCHAR(200),           -- 开票电话
    invoice_type    VARCHAR(20),            -- 发票类型：SPECIAL=增值税专用发票 NORMAL=普通发票
    is_default      INTEGER      DEFAULT 0, -- 是否默认：1=是 0=否
    status          INTEGER      DEFAULT 1, -- 状态：1=启用 0=停用
    remark          VARCHAR(500),           -- 备注
    create_by       BIGINT,                 -- 创建人
    create_time     TIMESTAMP,              -- 创建时间
    update_by       BIGINT,                 -- 更新人
    update_time     TIMESTAMP,              -- 更新时间
    deleted         INTEGER      DEFAULT 0, -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE INDEX idx_invoice_customer ON biz_customer_invoice (customer_id); -- 按客户查子表

-- ---------------------------- 订单头表 ----------------------------
-- 一次客户下单。订单号生成后只读，是系统内的唯一键。
CREATE TABLE biz_sales_order (
    id                     BIGINT        NOT NULL,  -- 主键（雪花 ID）
    order_no               VARCHAR(50)   NOT NULL,  -- 订单号（唯一，生成后只读）
    customer_id            BIGINT        NOT NULL,  -- 客户 ID（逻辑外键）
    customer_order_no      VARCHAR(100),            -- 客户订单号（客户 PO 号）
    requirement            VARCHAR(2000),           -- 客户要求
    mainboard_label        VARCHAR(200),            -- 主板标签
    carton_label           VARCHAR(200),            -- 外箱标签
    salesman_id            BIGINT        NOT NULL,  -- 业务员（逻辑外键 → sys_user.id）
    order_date             TIMESTAMP     NOT NULL,  -- 下单时间
    expected_delivery_date TIMESTAMP,               -- 预计交货时间（非必填）
    status                 VARCHAR(20)   NOT NULL,  -- 订单状态：见 OrderStatus 枚举
    currency               VARCHAR(10)   DEFAULT 'CNY', -- 币种（[待确认]暂仅 CNY）
    settlement_method      VARCHAR(50),             -- 结算方式（字典 settlement_method）
    delivery_method        VARCHAR(50),             -- 交货方式（字典 delivery_method）
    remark                 VARCHAR(2000),           -- 备注
    version                INTEGER       DEFAULT 0, -- 乐观锁版本号
    create_by              BIGINT,                  -- 创建人
    create_time            TIMESTAMP,               -- 创建时间
    update_by              BIGINT,                  -- 更新人
    update_time            TIMESTAMP,               -- 更新时间
    deleted                INTEGER       DEFAULT 0, -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_order_no ON biz_sales_order (order_no);       -- 订单号唯一
CREATE INDEX idx_order_customer ON biz_sales_order (customer_id);    -- 按客户查订单
CREATE INDEX idx_order_salesman ON biz_sales_order (salesman_id);    -- 按业务员过滤（数据权限）
CREATE INDEX idx_order_date ON biz_sales_order (order_date);         -- 按下单时间区间查
CREATE INDEX idx_order_status ON biz_sales_order (status);           -- 按状态筛选
CREATE INDEX idx_order_customer_no ON biz_sales_order (customer_order_no); -- 按客户 PO 匹配出货

-- ---------------------------- 订单明细表 ----------------------------
-- 订单下的物料行，是出货与对账的最小单位。
CREATE TABLE biz_sales_order_item (
    id                     BIGINT         NOT NULL,  -- 主键（雪花 ID）
    order_id               BIGINT         NOT NULL,  -- 订单头 ID（逻辑外键）
    line_no                INTEGER        NOT NULL,  -- 行号（展示顺序，从 1 开始）
    model                  VARCHAR(200)   NOT NULL,  -- 型号
    material_no            VARCHAR(100),             -- 物料编号（我方料号）
    customer_material_no   VARCHAR(100),             -- 客户物料号（出货时对照客户 PO）
    spec                   VARCHAR(300),             -- 规格型号
    qty_ordered            DECIMAL(18,4)  NOT NULL,  -- 下单数量（>0）
    qty_shipped            DECIMAL(18,4)  DEFAULT 0, -- 出货数（累计，由出货记录回写）
    qty_open               DECIMAL(18,4)  DEFAULT 0, -- 未出货数 = 下单数量 - 出货数（派生，禁止录入）
    tax_rate               DECIMAL(6,4),             -- 税率（0.13=13%；空或 0 表示单价为不含税）
    unit_price             DECIMAL(18,4)  NOT NULL,  -- 单价（含税与否由 tax_rate 决定）
    price_is_tax_included  INTEGER        DEFAULT 0, -- 派生：1=含税单价 0=不含税单价
    amount_ex_tax          DECIMAL(18,4)  DEFAULT 0, -- 不含税金额
    tax_amount             DECIMAL(18,4)  DEFAULT 0, -- 税额
    amount_tax_included    DECIMAL(18,4)  DEFAULT 0, -- 价税合计
    reconcile_date         TIMESTAMP,                -- 对账日期（明细级）
    reconcile_status       VARCHAR(20)    DEFAULT 'UNRECONCILED', -- 对账状态：UNRECONCILED / RECONCILED
    status                 VARCHAR(20)    DEFAULT 'NORMAL',       -- 行状态：NORMAL=正常 CANCELED=已取消
    remark                 VARCHAR(500),             -- 备注
    version                INTEGER        DEFAULT 0, -- 乐观锁版本号
    create_by              BIGINT,                   -- 创建人
    create_time            TIMESTAMP,                -- 创建时间
    update_by              BIGINT,                   -- 更新人
    update_time            TIMESTAMP,                -- 更新时间
    deleted                INTEGER        DEFAULT 0, -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE INDEX idx_item_order ON biz_sales_order_item (order_id);          -- 按订单查明细
CREATE INDEX idx_item_customer_material ON biz_sales_order_item (customer_material_no); -- 出货匹配优先键
CREATE INDEX idx_item_model ON biz_sales_order_item (model);             -- 出货匹配降级键
CREATE INDEX idx_item_material ON biz_sales_order_item (material_no);    -- 出货匹配降级键

-- ---------------------------- 出货记录表 ----------------------------
-- 一次出货。禁止物理删除，只允许作废（status=VOID）并回写扣减明细出货数。
CREATE TABLE biz_shipment (
    id                   BIGINT        NOT NULL,  -- 主键（雪花 ID）
    ship_date            TIMESTAMP     NOT NULL,  -- 出货日期
    process_order_no     VARCHAR(100),            -- 加工单号（内部流转单号，重要溯源键）
    salesman_id          BIGINT        NOT NULL,  -- 业务（逻辑外键 → sys_user.id）
    customer_id          BIGINT        NOT NULL,  -- 客户 ID（逻辑外键）
    customer_order_no    VARCHAR(100),            -- 客户订单号
    customer_material_no VARCHAR(100),            -- 客户物料号
    model                VARCHAR(200)  NOT NULL,  -- 型号
    material_no          VARCHAR(100),            -- 料号
    spec                 VARCHAR(300),            -- 规格型号
    qty                  DECIMAL(18,4) NOT NULL,  -- 出货数量（>0 且 ≤ 未出货数）
    order_item_id        BIGINT,                  -- 关联订单明细（为空 = 无源出货，进异常池）
    amount               DECIMAL(18,4),           -- 本次出货不含税金额（快照）
    tax_amount           DECIMAL(18,4),           -- 本次出货税额（快照）
    amount_tax_included  DECIMAL(18,4),           -- 本次出货价税合计（快照）
    status               VARCHAR(20)   DEFAULT 'NORMAL', -- 状态：NORMAL=正常 VOID=已作废
    source               VARCHAR(20)   DEFAULT 'MANUAL', -- 来源：MANUAL=手工 IMPORT=导入 PASTE=粘贴
    import_batch_id      BIGINT,                  -- 导入批次 ID（逻辑外键 → biz_import_batch.id）
    remark               VARCHAR(500),            -- 备注
    create_by            BIGINT,                  -- 创建人
    create_time          TIMESTAMP,               -- 创建时间
    update_by            BIGINT,                  -- 更新人
    update_time          TIMESTAMP,               -- 更新时间
    deleted              INTEGER       DEFAULT 0, -- 逻辑删除
    PRIMARY KEY (id)
);
-- 幂等键：加工单号 + 客户订单号 + 料号 + 出货日期 + 数量，重复导入据此去重
CREATE UNIQUE INDEX uk_shipment_idem ON biz_shipment (process_order_no, customer_order_no, material_no, ship_date, qty);
CREATE INDEX idx_shipment_date ON biz_shipment (ship_date);            -- 按日期区间查
CREATE INDEX idx_shipment_customer ON biz_shipment (customer_id);      -- 按客户查
CREATE INDEX idx_shipment_salesman ON biz_shipment (salesman_id);      -- 按业务员过滤（数据权限）
CREATE INDEX idx_shipment_item ON biz_shipment (order_item_id);        -- 反查明细的出货流水
CREATE INDEX idx_shipment_batch ON biz_shipment (import_batch_id);     -- 按批次回滚
CREATE INDEX idx_shipment_orphan ON biz_shipment (status, order_item_id); -- 异常池：无源出货

-- ---------------------------- 导入批次表 ----------------------------
-- 每次导入留痕，支持下载错误清单与一键回滚该批次增量。
CREATE TABLE biz_import_batch (
    id           BIGINT       NOT NULL,  -- 主键（雪花 ID）
    batch_type   VARCHAR(30)  NOT NULL,  -- 导入类型：CUSTOMER / ORDER / SHIPMENT
    file_name    VARCHAR(255),           -- 原文件名
    sheet_name   VARCHAR(100),           -- 使用的工作表名
    total_count  INTEGER      DEFAULT 0, -- 解析总行数
    insert_count INTEGER      DEFAULT 0, -- 新增条数
    update_count INTEGER      DEFAULT 0, -- 更新条数
    skip_count   INTEGER      DEFAULT 0, -- 跳过条数（幂等命中且策略为跳过）
    fail_count   INTEGER      DEFAULT 0, -- 失败条数（阻断级错误）
    warn_count   INTEGER      DEFAULT 0, -- 警告条数（入库但标记待补全）
    idempotent   VARCHAR(20)  DEFAULT 'SKIP', -- 幂等策略：SKIP=跳过 OVERWRITE=覆盖更新 APPEND=追加
    error_file   VARCHAR(500),           -- 错误清单文件名（可下载）
    status       VARCHAR(20)  DEFAULT 'COMMITTED', -- 状态：PREVIEW=仅校验 COMMITTED=已导入 ROLLED_BACK=已回滚
    remark       VARCHAR(500),           -- 备注
    create_by    BIGINT,                 -- 操作人
    create_time  TIMESTAMP,              -- 导入时间
    update_by    BIGINT,                 -- 更新人
    update_time  TIMESTAMP,              -- 更新时间
    deleted      INTEGER      DEFAULT 0, -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE INDEX idx_batch_type ON biz_import_batch (batch_type);  -- 按模块查批次
CREATE INDEX idx_batch_time ON biz_import_batch (create_time); -- 按时间倒序

-- ---------------------------- 操作日志表 ----------------------------
-- 出货 / 导入 / 回滚 / 作废 / 改价 / 对账 等敏感动作必须留痕（变更前后 JSON）。
CREATE TABLE biz_operation_log (
    id           BIGINT       NOT NULL,  -- 主键（雪花 ID）
    module       VARCHAR(30)  NOT NULL,  -- 模块：CUSTOMER / ORDER / SHIPMENT / IMPORT
    biz_id       VARCHAR(64),            -- 业务 ID（批量操作可为空，见 remark）
    action       VARCHAR(40)  NOT NULL,  -- 动作：CREATE / UPDATE / VOID / IMPORT / ROLLBACK / RECONCILE ...
    before_json  VARCHAR(4000),          -- 变更前快照（JSON）
    after_json   VARCHAR(4000),          -- 变更后快照（JSON）
    reason       VARCHAR(500),           -- 操作原因（敏感操作必填）
    operator_id  BIGINT,                 -- 操作人
    operate_time TIMESTAMP,              -- 操作时间
    PRIMARY KEY (id)
);
CREATE INDEX idx_oplog_module ON biz_operation_log (module);        -- 按模块查
CREATE INDEX idx_oplog_biz ON biz_operation_log (biz_id);           -- 按业务单据追溯
CREATE INDEX idx_oplog_time ON biz_operation_log (operate_time);    -- 按时间倒序

-- ---------------------------- 字典表 ----------------------------
-- 税率、币种、结算方式、交货方式、订单状态等统一走字典，禁止硬编码。
CREATE TABLE biz_dict_item (
    id         BIGINT       NOT NULL,  -- 主键（雪花 ID）
    dict_code  VARCHAR(50)  NOT NULL,  -- 字典编码：tax_rate / currency / settlement_method / delivery_method ...
    dict_value VARCHAR(50)  NOT NULL,  -- 字典值（存库值，如 0.13）
    dict_label VARCHAR(100) NOT NULL,  -- 字典标签（展示值，如 13%）
    sort       INTEGER      DEFAULT 0, -- 显示排序（越小越靠前）
    status     INTEGER      DEFAULT 1, -- 状态：1=启用 0=停用
    remark     VARCHAR(500),           -- 备注
    create_by  BIGINT,                 -- 创建人
    create_time TIMESTAMP,             -- 创建时间
    update_by  BIGINT,                 -- 更新人
    update_time TIMESTAMP,             -- 更新时间
    deleted    INTEGER      DEFAULT 0, -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_dict_code_value ON biz_dict_item (dict_code, dict_value); -- 同字典内值唯一
CREATE INDEX idx_dict_code ON biz_dict_item (dict_code);                         -- 按编码取字典项

-- ---------------------------- 导入字段映射表 ----------------------------
-- 历史 xlsx 列名 → 系统字段的映射配置化（见附录A），支持别名、默认值、必填与缺省容忍。
-- 不写死在代码里：新增一种台账列头只需插一条数据。
CREATE TABLE biz_import_mapping (
    id           BIGINT       NOT NULL,  -- 主键（雪花 ID）
    module       VARCHAR(30)  NOT NULL,  -- 模块：CUSTOMER / ORDER / SHIPMENT
    field_name   VARCHAR(64)  NOT NULL,  -- 系统字段名（如 customer_name）
    aliases      VARCHAR(500) NOT NULL,  -- 表头别名，英文逗号分隔（如 客户名称,客户名,Client）
    required     INTEGER      DEFAULT 0, -- 是否必填：1=必填（缺失为阻断级） 0=选填
    column_order INTEGER      DEFAULT 0, -- 模板列序（导出与模板下载按此排序）
    default_value VARCHAR(200),          -- 缺省默认值（单元格为空时使用）
    field_type   VARCHAR(20)  DEFAULT 'STRING', -- 值类型：STRING / NUMBER / DATE / RATE
    remark       VARCHAR(500),           -- 备注
    create_by    BIGINT,                 -- 创建人
    create_time  TIMESTAMP,              -- 创建时间
    update_by    BIGINT,                 -- 更新人
    update_time  TIMESTAMP,              -- 更新时间
    deleted      INTEGER      DEFAULT 0, -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE INDEX idx_mapping_module ON biz_import_mapping (module);  -- 按模块取映射
