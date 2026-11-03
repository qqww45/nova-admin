-- =============================================================================
-- V20：分录任务模板补「规格型号」字段
--
-- 背景：服务端 kd_outbound_line.spec 建表（V15）、对账单导出的「规格型号」列、
--       数据查看页的 spec 列此前从未有数据——桌面采集端的字段集一直没有采这个字段
--       （链路断点在采集侧，桌面端已同步修复：lineKeys.ts 候选 + 本地库 v3 列 +
--       上行带 spec）。本迁移把全局模板对齐，让未做过字段自检的新采集器
--       拉任务清单时也能拿到含规格的字段集。
--
-- 只更新全局模板（acct_id='*'，id=3）：按账套覆盖的任务行是运维显式配置，
--       不应被自动改写。客户端对服务端字段集只当"候选之一"（方案 §12.2 第 9 条），
--       账套取不到 FMaterialId.FSpecification 时整套失败自动降级到本机映射与
--       内置候选，行为与旧版一致，不存在"改配置把采集器搞挂"的风险。
-- =============================================================================

UPDATE kd_sync_task
   SET field_keys = 'FID,FBillNo,FDate,FCustomerID,FCustomerID.FName,FDocumentStatus,FCancelStatus,FEntity_FEntryID,FMaterialId.FNumber,FMaterialId.FName,FMaterialId.FSpecification,FUnitID.FName,FRealQty,FTaxPrice,FAllAmount,FSrcBillNo,FEntryNote',
       update_time = '2026-11-03 00:00:00'
 WHERE acct_id = '*'
   AND entity = 'outbound_line';
