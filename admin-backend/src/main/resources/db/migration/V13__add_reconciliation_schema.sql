-- =============================================================================
-- V13：新增「对账 / 收款 / 期初应收」域 + 出货方向标识
--
-- 背景：客户对账单 = 期初应收 + 本期出货 − 本期退货/退厂 + 本期收款 = 期末应收。
--       当前系统仅有出货(VOID 红冲)与订单明细 reconcile_date 标记位，缺：
--         · 收款流水（客户回款）
--         · 期初应收余额入口
--         · 对账单签发快照（含明细流水）
--         · 出货方向（出货 / 退货 / 退厂），原 biz_shipment 只有 出货+作废
--
-- 跨库兼容：沿用 V1/V9 约定（BIGINT 雪花主键 / VARCHAR / INTEGER / TIMESTAMP /
--           DECIMAL(18,4)，无 COMMENT、无自增、无外键、无触发器）。
-- 状态类（对账单状态 / 收款状态 / 出货方向）为 Java 枚举，不入库到 biz_dict_item。
-- =============================================================================

-- ---------------------------- 1. 出货方向标识 ----------------------------
-- 历史出货均为正向出货，回填为 SHIP；新增退货/退厂走独立方向。
-- 取值（Java 枚举 ShipmentType）：SHIP=出货 / RETURN=销售退货 / RETURN_FACTORY=退厂返工
ALTER TABLE biz_shipment ADD COLUMN shipment_type VARCHAR(20) DEFAULT 'SHIP';
UPDATE biz_shipment SET shipment_type = 'SHIP' WHERE shipment_type IS NULL;
CREATE INDEX idx_shipment_type ON biz_shipment (shipment_type);        -- 按方向筛退货/退厂
CREATE INDEX idx_shipment_customer_date ON biz_shipment (customer_id, ship_date); -- 对账单聚合（客户+期间）

-- ---------------------------- 2. 收款登记表 ----------------------------
-- 客户级回款登记。v1 不强制关联到具体订单/对账单，分配（"分 XXX 元"）留接口。
CREATE TABLE biz_receipt (
    id               BIGINT        NOT NULL,  -- 主键（雪花 ID）
    receipt_no       VARCHAR(50)   NOT NULL,  -- 收款单号（唯一，生成后只读）
    customer_id      BIGINT        NOT NULL,  -- 客户 ID（逻辑外键 → biz_customer.id）
    receipt_date     TIMESTAMP     NOT NULL,  -- 收款日期
    amount           DECIMAL(18,4) NOT NULL,  -- 收款金额（>0）
    currency         VARCHAR(10)   DEFAULT 'CNY', -- 币种（字典 currency）
    settle_method    VARCHAR(50),             -- 结算方式（字典 settlement_method）
    invoice_no       VARCHAR(100),            -- 对应发票号
    allocated_amount DECIMAL(18,4) DEFAULT 0, -- 已分配到对账单的金额
    remark           VARCHAR(500),            -- 备注
    status           VARCHAR(20)   DEFAULT 'NORMAL', -- 状态：NORMAL=正常 VOID=已作废（Java 枚举 ReceiptStatus）
    version          INTEGER       DEFAULT 0,  -- 乐观锁版本号
    create_by        BIGINT,                  -- 创建人
    create_time      TIMESTAMP,               -- 创建时间
    update_by        BIGINT,                  -- 更新人
    update_time      TIMESTAMP,               -- 更新时间
    deleted          INTEGER       DEFAULT 0,  -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_receipt_no ON biz_receipt (receipt_no);          -- 收款单号唯一
CREATE INDEX idx_receipt_customer ON biz_receipt (customer_id);        -- 按客户查
CREATE INDEX idx_receipt_date ON biz_receipt (receipt_date);           -- 按日期区间查

-- ---------------------------- 3. 期初应收余额 ----------------------------
-- 对账周期开始前的历史欠款。每客户唯一一条，作为对账单"期初"来源。
CREATE TABLE biz_customer_opening (
    id               BIGINT        NOT NULL,  -- 主键（雪花 ID）
    customer_id      BIGINT        NOT NULL,  -- 客户 ID（逻辑外键 → biz_customer.id）
    as_of_date       TIMESTAMP     NOT NULL,  -- 期初基准日（如 2026-01-01）
    opening_balance  DECIMAL(18,4) NOT NULL,  -- 期初应收
    currency         VARCHAR(10)   DEFAULT 'CNY', -- 币种
    remark           VARCHAR(500),            -- 备注
    version          INTEGER       DEFAULT 0,  -- 乐观锁版本号
    create_by        BIGINT,                  -- 创建人
    create_time      TIMESTAMP,               -- 创建时间
    update_by        BIGINT,                  -- 更新人
    update_time      TIMESTAMP,               -- 更新时间
    deleted          INTEGER       DEFAULT 0,  -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_opening_customer ON biz_customer_opening (customer_id); -- 每客户唯一条

-- ---------------------------- 4. 对账单头（签发快照） ----------------------------
CREATE TABLE biz_statement (
    id                BIGINT        NOT NULL,  -- 主键（雪花 ID）
    stmt_no           VARCHAR(50)   NOT NULL,  -- 对账单号（唯一，生成后只读）
    customer_id       BIGINT        NOT NULL,  -- 客户 ID
    start_date        TIMESTAMP     NOT NULL,  -- 对账期间起
    end_date          TIMESTAMP     NOT NULL,  -- 对账期间止
    opening_balance   DECIMAL(18,4) DEFAULT 0, -- 期初应收
    shipped_amount    DECIMAL(18,4) DEFAULT 0, -- 本期出货（正金额合计）
    returned_amount   DECIMAL(18,4) DEFAULT 0, -- 本期退货/退厂（正金额合计，展示用；计算取负）
    received_amount   DECIMAL(18,4) DEFAULT 0, -- 本期收款（正金额合计）
    ending_balance    DECIMAL(18,4) DEFAULT 0, -- 期末应收
    status            VARCHAR(20)   DEFAULT 'DRAFT', -- DRAFT/ISSUED/CONFIRMED/DIFFERENT（Java 枚举 StatementStatus）
    confirm_date      TIMESTAMP,                -- 客户确认/回签日
    remark            VARCHAR(500),            -- 备注
    version           INTEGER       DEFAULT 0,  -- 乐观锁版本号
    create_by         BIGINT,                  -- 创建人
    create_time       TIMESTAMP,               -- 创建时间
    update_by         BIGINT,                  -- 更新人
    update_time       TIMESTAMP,               -- 更新时间
    deleted           INTEGER       DEFAULT 0,  -- 逻辑删除
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_stmt_no ON biz_statement (stmt_no);             -- 对账单号唯一
CREATE INDEX idx_statement_customer ON biz_statement (customer_id);   -- 按客户查
CREATE INDEX idx_statement_date ON biz_statement (start_date, end_date); -- 按期间查

-- ---------------------------- 5. 对账单明细（流水快照） ----------------------------
-- line_type：SHIP=出货 / RETURN=退货 / RECEIVE=收款；数量/金额在退货与收款时取负向值（与样本一致）。
CREATE TABLE biz_statement_line (
    id            BIGINT        NOT NULL,  -- 主键（雪花 ID）
    statement_id  BIGINT        NOT NULL,  -- 对账单 ID（逻辑外键 → biz_statement.id）
    line_type     VARCHAR(20)   NOT NULL,  -- SHIP / RETURN / RECEIVE
    biz_date      TIMESTAMP     NOT NULL,  -- 业务日期
    doc_no        VARCHAR(100),            -- 出货单号 / 收款单号
    customer_order_no VARCHAR(100),        -- 客户订单号
    material_no   VARCHAR(100),            -- 物料编号（退货/出货有，收款为空）
    material_name VARCHAR(200),            -- 物料名称
    spec          VARCHAR(300),            -- 规格
    unit          VARCHAR(20),             -- 单位
    qty           DECIMAL(18,4) DEFAULT 0, -- 数量（退货为负）
    unit_price    DECIMAL(18,4) DEFAULT 0, -- 单价
    amount        DECIMAL(18,4) DEFAULT 0, -- 金额（退货/收款方向取负）
    remark        VARCHAR(500),            -- 备注
    source_id     BIGINT,                  -- 关联 shipment.id / receipt.id（追溯与作废联动）
    sort_no       INTEGER      DEFAULT 0,  -- 排序
    PRIMARY KEY (id)
);
CREATE INDEX idx_stmt_line_statement ON biz_statement_line (statement_id); -- 按对账单查明细
