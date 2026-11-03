-- =============================================================================
-- V21：金蝶客户 ↔ 内部客户映射（试点批 R4）
--
-- 背景：内部系统（biz_customer）与金蝶侧（kd_customer）是两套客户体系，
--       目前"金蝶出货 vs 内部出货"的任何核对都要业务员人肉对客户名。
--       映射做一次，后续两口径联动（对照页、客户 360）才有地基
--       （docs/业务员提效需求发掘.md R4，解锁 R5）。
--
-- 设计：
--   - 唯一键 (owner_user_id, acct_id, kd_fid)：金蝶内码只在账套内唯一；
--   - match_type：AUTO_SUGGEST = 名称标准化匹配的候选（需人工确认），
--                 MANUAL = 业务员显式指定（视为已确认）；
--   - 不做外键（跨库约定）；biz_customer_id 允许悬空（内部客户停用/删除时
--     映射行保留，页面按"内部客户不存在"展示）。
--
-- 授权：超级管理员(1)、普通管理员(2)、业务员(3)——业务员维护本人账套的映射
--       是自助诉求；服务层强制 owner 隔离（与绑定/对账单一致）。
-- sys_role_menu 固定 ID 沿用 12xxx 段（124xx/125xx/126xx）。
-- =============================================================================

-- ---------------------------- 1. 映射表 ----------------------------
CREATE TABLE kd_customer_mapping (
    id              BIGINT       NOT NULL,  -- 主键（雪花 ID）
    owner_user_id   BIGINT       NOT NULL,  -- 数据归属人（nova 用户）
    acct_id         VARCHAR(64)  NOT NULL,  -- 金蝶账套
    kd_fid          BIGINT       NOT NULL,  -- 金蝶客户内码 FCUSTID
    biz_customer_id BIGINT       NOT NULL,  -- 内部客户 ID（逻辑外键 → biz_customer.id）
    match_type      VARCHAR(20)  NOT NULL,  -- AUTO_SUGGEST / MANUAL
    remark          VARCHAR(500),
    create_by       BIGINT,
    create_time     TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    deleted         INTEGER      DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_kd_cust_mapping_key ON kd_customer_mapping (owner_user_id, acct_id, kd_fid);
CREATE INDEX idx_kd_cust_mapping_biz ON kd_customer_mapping (owner_user_id, biz_customer_id);

-- ---------------------------- 2. 页面菜单 ----------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(48, 40, '客户映射', 2, '/kd/mapping', 'kd/mapping/index', 'kd:customer-mapping:list', 'Link', 8, 1, 0, '金蝶客户 ↔ 内部客户映射（两口径联动的地基）', 1, '2026-11-03 00:00:00', 1, '2026-11-03 00:00:00', 0);

-- ---------------------------- 3. 按钮权限 ----------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(4801, 48, '客户映射查询', 3, '', '', 'kd:customer-mapping:list', '', 1, 1, 0, '', 1, '2026-11-03 00:00:00', 1, '2026-11-03 00:00:00', 0),
(4802, 48, '客户映射保存', 3, '', '', 'kd:customer-mapping:edit',  '', 2, 1, 0, '', 1, '2026-11-03 00:00:00', 1, '2026-11-03 00:00:00', 0);

-- ---------------------------- 4. 角色授权 ----------------------------
-- 超级管理员（role_id=1）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(12401, 1, 48,   1, '2026-11-03 00:00:00'),
(12402, 1, 4801, 1, '2026-11-03 00:00:00'),
(12403, 1, 4802, 1, '2026-11-03 00:00:00');

-- 普通管理员（role_id=2）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(12501, 2, 48,   1, '2026-11-03 00:00:00'),
(12502, 2, 4801, 1, '2026-11-03 00:00:00'),
(12503, 2, 4802, 1, '2026-11-03 00:00:00');

-- 业务员（role_id=3）：只维护本人账套的映射（服务层按 owner_user_id 强制隔离）
INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(12601, 3, 48,   1, '2026-11-03 00:00:00'),
(12602, 3, 4801, 1, '2026-11-03 00:00:00'),
(12603, 3, 4802, 1, '2026-11-03 00:00:00');
