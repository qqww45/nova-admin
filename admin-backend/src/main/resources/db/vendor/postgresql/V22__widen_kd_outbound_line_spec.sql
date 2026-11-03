-- =============================================================================
-- V22：放宽 kd_outbound_line.spec 列宽 300 → 1000
--
-- Background and dispatch notes: see vendor/mysql/V22__widen_kd_outbound_line_spec.sql.
-- =============================================================================

ALTER TABLE kd_outbound_line ALTER COLUMN spec TYPE VARCHAR(1000);
