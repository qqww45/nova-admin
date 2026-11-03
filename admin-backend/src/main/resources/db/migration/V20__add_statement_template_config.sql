-- =============================================================================
-- V20：对账单导出模板化（复刻金样板）配套数据
--
-- 背景：M2 的对账单导出是通用两表（客户汇总 / 出库明细），与业务签字用的
-- 金样板（desktop/resources/templates/statement-golden-sample.xlsx）完全不同。
-- 本次导出改为按样板逐格复刻（KdStatementAppServiceImpl → StatementXlsxWriter），
-- 需要两处数据配套：
--   1. 对账单抬头的公司信息进 sys_config——多客户共用平台，公司名 / 地址电话
--      不能写死在 Java 里，改值也不用发版；默认值与金样板文本逐字一致，
--      未配置时代码自动退回同一文本，行为不变；
--   2. 出库分录任务字段集补「物料规格」（FMaterialId.FSpecification）——
--      样板明细的「规格」列需要它，此前整条链路（采集 → 落库 → 导出）都没有采集该字段，
--      导出的规格列永远为空。桌面端 1.2.1 起随包映射已同步补齐；
--      服务端字段集是采集器降级候选之一，一并更新。
--
-- 跨库兼容：沿用 V1 约定（BIGINT / VARCHAR / INTEGER / TIMESTAMP）。
-- =============================================================================

-- ---------------------------- 1. 对账单抬头配置 ----------------------------
-- builtin=1：不可删除，可改值。默认值 = 金样板抬头原文（含既有排版空格）。
INSERT INTO sys_config (id, config_key, config_value, config_type, builtin, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(20, 'kd.statement.company.name', '深圳市一恒科电子科技有限公司', 'STRING', 1, '对账单抬头：公司中文名（A1）', 1, '2026-11-03 00:00:00', 1, '2026-11-03 00:00:00', 0),
(21, 'kd.statement.company.name-en', 'SHENZHEN YIHENGK ELECTRONICS TECHNOLOGYCO.,LTD', 'STRING', 1, '对账单抬头：公司英文名（A2）', 1, '2026-11-03 00:00:00', 1, '2026-11-03 00:00:00', 0),
(22, 'kd.statement.company.info', '地址：广东省深圳市南山区留仙大道创智云城A3栋A座8-9楼（成谷研发大厦）   电话：0755-26905055     传真：0755-26905050', 'STRING', 1, '对账单抬头：地址 / 电话 / 传真整行（A3）', 1, '2026-11-03 00:00:00', 1, '2026-11-03 00:00:00', 0);

-- ---------------------------- 2. 分录字段集补规格 ----------------------------
-- 只改默认模板（acct_id='*'）；按账套覆盖的任务由运维在配置页自行调整。
-- 规格列在金蝶部分账套可能不可用：采集器按候选集降级重试，拿不到时规格列为空，不影响其余字段。
UPDATE kd_sync_task
SET field_keys = 'FID,FBillNo,FDate,FCustomerID,FCustomerID.FName,FDocumentStatus,FCancelStatus,FEntity_FEntryID,FMaterialId.FNumber,FMaterialId.FName,FMaterialId.FSpecification,FUnitID.FName,FRealQty,FTaxPrice,FAllAmount,FSrcBillNo,FEntryNote',
    update_time = '2026-11-03 00:00:00'
WHERE acct_id = '*' AND entity = 'outbound_line' AND enabled = 1;
