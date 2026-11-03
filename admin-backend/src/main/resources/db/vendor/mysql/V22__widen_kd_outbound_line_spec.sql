-- =============================================================================
-- V22：放宽 kd_outbound_line.spec 列宽 300 → 1000
--
-- 背景：金蝶 FSpecification 为可配置长度文本，真实账套存在超 300 字的规格。
--       2026-11-03 起客户端 1.2.1 全链路采集规格后，超长规格被上行 DTO 的
--       @Size(max=300) 整批拒绝（生产批次失败：rows[472].spec: size must be
--       between 0 and 300），批次反复重试、水位无法推进。
-- 处理：规格是对账依据，禁止截断——列宽与 DTO 上限一起放宽到 1000。
--
-- Column width change DDL syntax differs across databases; this script goes through db/vendor/{vendor} and is dispatched per database:
--   mysql        → MODIFY COLUMN（本文件）
--   postgresql   → ALTER COLUMN TYPE
--   sqlite       → 空操作（SQLite 不强制 VARCHAR 长度，仅靠 DTO 约束）
-- 跨库约定沿用 V1/V9/V15：无 COMMENT、无外键、无触发器。
-- =============================================================================

ALTER TABLE kd_outbound_line MODIFY COLUMN spec VARCHAR(1000);
