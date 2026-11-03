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
                            "kd_field_mapping", "kd_customer", "kd_outbound", "kd_outbound_line", "kd_sync_batch",
                            "kd_customer_mapping"}) {
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
                // V20：分录任务模板必须含规格字段——采集器把任务清单里的字段集当候选之一，
                // 模板缺字段则新装采集器可能整条链路采不到「规格型号」，
                // 服务端 spec 列、对账单导出与数据查看页的该列全为空
                try (var rs = stmt.executeQuery(
                        "SELECT field_keys FROM kd_sync_task WHERE acct_id = '*' AND entity = 'outbound_line'")) {
                    rs.next();
                    assertTrue(rs.getString(1).contains("FMaterialId.FSpecification"),
                            "出库分录任务模板应包含规格型号字段 FMaterialId.FSpecification");
                }

                // V20：对账单抬头公司信息进配置、分录字段集补规格（FMaterialId.FSpecification）。
                // 导出版式以金样板为准，抬头文本默认值与样板逐字一致；规格列缺了整条链路都填不出来。
                for (String key : new String[]{"kd.statement.company.name", "kd.statement.company.name-en",
                        "kd.statement.company.info"}) {
                    try (var rs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM sys_config WHERE config_key = '" + key + "'")) {
                        rs.next();
                        assertEquals(1, rs.getInt(1), "对账单抬头配置缺失：" + key);
                    }
                }
                try (var rs = stmt.executeQuery(
                        "SELECT field_keys FROM kd_sync_task WHERE entity = 'outbound_line' AND acct_id = '*'")) {
                    rs.next();
                    assertTrue(rs.getString(1).contains("FMaterialId.FSpecification"),
                            "分录字段集应包含物料规格字段");
                }

                // 业务员（role_id=3）必须拿到对账单与绑定/批次的查询、导出、指令权限：
                // V16/V17 只授权给管理员角色时，功能对真正的使用者（业务员）整体不可用
                // ——页面不可达 + 接口 403。此断言锁定 V18 的授权不被后续迁移悄悄拿掉。
                // 同一 permission 会同时命中菜单行与按钮行（如 44 与 4401），断言 >= 1。
                // V19 新增的三个数据查看页（客户/出库/分录）同样授权业务员：
                // 排查"数据为什么没上来"是业务员的自查诉求，断言一并锁定。
                for (String permission : new String[]{"kd:statement:list", "kd:statement:export",
                        "kd:binding:list", "kd:binding:code", "kd:binding:command", "kd:sync:list",
                        "kd:outbound:list", "kd:customer:list", "kd:line:list",
                        "kd:customer-mapping:list", "kd:customer-mapping:edit"}) {
                    try (var rs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id = m.id "
                                    + "WHERE rm.role_id = 3 AND m.permission = '" + permission + "'")) {
                        rs.next();
                        assertTrue(rs.getInt(1) >= 1, "业务员角色缺少权限：" + permission);
                    }
                }
                try (var rs = stmt.executeQuery(
                        "SELECT data_scope FROM sys_role WHERE id = 3")) {
                    rs.next();
                    assertEquals(4, rs.getInt(1), "业务员角色的数据权限应为「仅本人」");
                }
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }
}
