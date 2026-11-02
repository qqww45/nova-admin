package com.example.admin.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flyway 迁移可执行性测试。
 *
 * <p>为什么需要：迁移脚本只在应用启动时执行，写错一个列名、用了 SQLite 不认识的语法，
 * 单元测试全绿也发现不了，要等到部署时才炸。这里用临时 SQLite 库把
 * {@code db/migration} 全量跑一遍，至少保证三件事：
 * 语法能过、脚本能按序执行、关键表真的建出来了。
 *
 * <p>不覆盖 prod（MySQL / PostgreSQL）语法差异，那部分仍以部署前的手工验证为准。
 *
 * @author admin-system
 * @since 2026-11-02
 */
class FlywayMigrationTest {

    @Test
    @DisplayName("全部迁移脚本可在空 SQLite 库上顺序执行")
    void allMigrationsShouldApplyOnEmptyDatabase() throws Exception {
        Path dbFile = Files.createTempFile("nova-flyway-", ".db");
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource("jdbc:sqlite:" + dbFile.toAbsolutePath(), null, null)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .validateOnMigrate(true)
                    .load();
            var result = flyway.migrate();
            assertTrue(result.migrationsExecuted > 0, "应至少执行一个迁移脚本");

            MigrationInfo[] applied = flyway.info().applied();
            String lastVersion = applied[applied.length - 1].getVersion().getVersion();
            assertTrue(Integer.parseInt(lastVersion) >= 16,
                    "金蝶同步迁移（V15/V16）应已执行，实际最后版本：" + lastVersion);

            // 关键表存在性：迁移里写错表名不会报错，只会让运行时"表不存在"
            try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
                 var stmt = conn.createStatement()) {
                for (String table : new String[]{"kd_binding", "kd_binding_code", "kd_sync_task",
                        "kd_field_mapping", "kd_customer", "kd_outbound", "kd_outbound_line", "kd_sync_batch"}) {
                    try (var rs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
                        rs.next();
                        assertEquals(1, rs.getInt(1), "缺少表：" + table);
                    }
                }
                try (var rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM kd_sync_task WHERE endpoint = '/kd/sync/outbound-line'")) {
                    rs.next();
                    assertEquals(1, rs.getInt(1), "出库分录任务未被初始化");
                }
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }
}
