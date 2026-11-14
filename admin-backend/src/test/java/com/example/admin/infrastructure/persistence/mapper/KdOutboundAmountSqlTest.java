package com.example.admin.infrastructure.persistence.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出库表头金额 set-based 回填：锁住与旧内存循环相同的汇总语义。
 *
 * <p>旧实现按「本批次未失效分录」分组求和，再对每个 {@code kd_fid} 发一次 UPDATE。
 * 这里用同一套夹具跑 Java 参考算法和 SQLite 的一条 UPDATE，结果必须一致。
 *
 * @author admin-system
 * @since 2026-11-14
 */
class KdOutboundAmountSqlTest {

    private static final long OWNER = 7L;
    private static final String ACCT = "ACCT_A";
    private static final String BATCH = "B-FIN";

    @Test
    @DisplayName("mysql / postgresql / sqlite 三套 SQL 含相同过滤与汇总口径")
    void dialectsShouldShareFiltersAndGrouping() {
        for (String sql : List.of(
                KdOutboundAmountSql.sqlFor("mysql"),
                KdOutboundAmountSql.sqlFor("postgresql"),
                KdOutboundAmountSql.sqlFor("sqlite"),
                KdOutboundAmountSql.sqlFor(null))) {
            assertTrue(sql.contains("owner_user_id"), sql);
            assertTrue(sql.contains("acct_id"), sql);
            assertTrue(sql.contains("batch_no"), sql);
            assertTrue(sql.contains("invalid = 0"), sql);
            assertTrue(sql.contains("deleted = 0"), sql);
            assertTrue(sql.contains("COALESCE(amount_cents, 0)"), sql);
            assertTrue(sql.contains("GROUP BY"), sql);
            assertTrue(sql.contains("outbound_fid"), sql);
            assertFalse(sql.contains("o.batch_no"), "表头不得按 batch_no 过滤，否则增量分录回填不到旧单头");
        }
        assertEquals(KdOutboundAmountSql.MYSQL, KdOutboundAmountSql.sqlFor("mysql"));
        assertEquals(KdOutboundAmountSql.POSTGRES, KdOutboundAmountSql.sqlFor("PostgreSQL"));
        assertEquals(KdOutboundAmountSql.SQLITE, KdOutboundAmountSql.sqlFor("sqlite"));
        assertEquals(KdOutboundAmountSql.SQLITE, KdOutboundAmountSql.sqlFor(null));
    }

    @Test
    @DisplayName("多单头批次：set-based UPDATE 与旧内存循环得到相同表头金额")
    void setBasedUpdateShouldMatchLegacyInMemorySums() throws Exception {
        Path dbFile = Files.createTempFile("kd-amount-recalc-", ".db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath())) {
            createTables(conn);
            List<Header> headers = seedHeaders(conn);
            List<Line> lines = seedLines(conn);

            Map<Long, Long> expected = legacySums(lines, OWNER, ACCT, BATCH);
            applyLegacy(headers, expected, OWNER, ACCT);
            long expectedUpdated = headers.stream()
                    .filter(h -> h.deleted == 0 && h.ownerUserId == OWNER && ACCT.equals(h.acctId))
                    .filter(h -> expected.containsKey(h.kdFid))
                    .count();

            int updated = executeSqliteUpdate(conn, OWNER, ACCT, BATCH);
            assertEquals(expectedUpdated, updated, "回填行数应等于可更新的未删除表头数：" + expected);

            Map<Long, Long> actual = readHeaderAmounts(conn);
            for (Header header : headers) {
                assertEquals(header.amountCents, actual.get(header.id),
                        "单头 id=" + header.id + " fid=" + header.kdFid + " 金额应与旧逻辑一致");
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    @Test
    @DisplayName("本批次没有有效分录时不改任何表头")
    void emptyBatchShouldUpdateNothing() throws Exception {
        Path dbFile = Files.createTempFile("kd-amount-recalc-empty-", ".db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath())) {
            createTables(conn);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                        "INSERT INTO kd_outbound(id, owner_user_id, acct_id, kd_fid, amount_cents, batch_no, deleted)"
                                + " VALUES (1, 7, 'ACCT_A', 100, 42, 'B-OTHER', 0)");
            }
            int updated = executeSqliteUpdate(conn, OWNER, ACCT, BATCH);
            assertEquals(0, updated);
            assertEquals(42L, readHeaderAmounts(conn).get(1L));
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    /**
     * 旧仓储循环的汇总口径：过滤后按 outboundFid 求和，null 金额当 0。
     */
    static Map<Long, Long> legacySums(List<Line> lines, long ownerUserId, String acctId, String batchNo) {
        Map<Long, Long> sums = new HashMap<>();
        for (Line line : lines) {
            if (line.ownerUserId != ownerUserId
                    || !Objects.equals(line.acctId, acctId)
                    || !Objects.equals(line.batchNo, batchNo)
                    || line.invalid != 0
                    || line.deleted != 0) {
                continue;
            }
            long amount = line.amountCents == null ? 0L : line.amountCents;
            sums.merge(line.outboundFid, amount, Long::sum);
        }
        return sums;
    }

    /**
     * 旧仓储循环的回填口径：只改归属人+账套匹配且未删除的表头，不看表头 batch_no。
     */
    static void applyLegacy(List<Header> headers, Map<Long, Long> sums, long ownerUserId, String acctId) {
        for (Header header : headers) {
            if (header.deleted != 0
                    || header.ownerUserId != ownerUserId
                    || !Objects.equals(header.acctId, acctId)) {
                continue;
            }
            Long sum = sums.get(header.kdFid);
            if (sum != null) {
                header.amountCents = sum;
            }
        }
    }

    private static int executeSqliteUpdate(Connection conn, long ownerUserId, String acctId, String batchNo)
            throws Exception {
        String sql = KdOutboundAmountSql.SQLITE
                .replace("#{ownerUserId}", "?")
                .replace("#{acctId}", "?")
                .replace("#{batchNo}", "?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerUserId);
            ps.setString(2, acctId);
            ps.setString(3, batchNo);
            ps.setLong(4, ownerUserId);
            ps.setString(5, acctId);
            return ps.executeUpdate();
        }
    }

    private static void createTables(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE kd_outbound (
                        id INTEGER PRIMARY KEY,
                        owner_user_id BIGINT NOT NULL,
                        acct_id VARCHAR(64) NOT NULL,
                        kd_fid BIGINT NOT NULL,
                        amount_cents BIGINT DEFAULT 0,
                        batch_no VARCHAR(64),
                        deleted INTEGER DEFAULT 0
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE kd_outbound_line (
                        id INTEGER PRIMARY KEY,
                        owner_user_id BIGINT NOT NULL,
                        acct_id VARCHAR(64) NOT NULL,
                        outbound_fid BIGINT NOT NULL,
                        amount_cents BIGINT,
                        invalid INTEGER DEFAULT 0,
                        batch_no VARCHAR(64),
                        deleted INTEGER DEFAULT 0
                    )
                    """);
        }
    }

    private static List<Header> seedHeaders(Connection conn) throws Exception {
        List<Header> headers = List.of(
                new Header(1L, OWNER, ACCT, 101L, 999L, BATCH, 0),
                new Header(2L, OWNER, ACCT, 102L, 0L, BATCH, 0),
                new Header(3L, OWNER, ACCT, 103L, 50L, BATCH, 0),
                new Header(4L, OWNER, ACCT, 104L, 77L, BATCH, 0),
                // 上一批上报的单头：本批次分录仍应回填
                new Header(5L, OWNER, ACCT, 105L, 1L, "B-OLD", 0),
                new Header(6L, OWNER, ACCT, 106L, 8L, BATCH, 1),
                new Header(7L, 8L, ACCT, 101L, 123L, BATCH, 0),
                new Header(8L, OWNER, "ACCT_B", 101L, 222L, BATCH, 0)
        );
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO kd_outbound(id, owner_user_id, acct_id, kd_fid, amount_cents, batch_no, deleted)"
                        + " VALUES (?,?,?,?,?,?,?)")) {
            for (Header header : headers) {
                ps.setLong(1, header.id);
                ps.setLong(2, header.ownerUserId);
                ps.setString(3, header.acctId);
                ps.setLong(4, header.kdFid);
                ps.setLong(5, header.amountCents);
                ps.setString(6, header.batchNo);
                ps.setInt(7, header.deleted);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return new ArrayList<>(headers);
    }

    private static List<Line> seedLines(Connection conn) throws Exception {
        List<Line> lines = List.of(
                new Line(1L, OWNER, ACCT, 101L, 100L, 0, BATCH, 0),
                new Line(2L, OWNER, ACCT, 101L, 250L, 0, BATCH, 0),
                new Line(3L, OWNER, ACCT, 102L, 40L, 0, BATCH, 0),
                new Line(4L, OWNER, ACCT, 102L, null, 0, BATCH, 0),
                new Line(5L, OWNER, ACCT, 103L, 10L, 0, BATCH, 0),
                new Line(6L, OWNER, ACCT, 103L, 999L, 1, BATCH, 0),
                new Line(7L, OWNER, ACCT, 104L, 500L, 0, "B-OLD", 0),
                new Line(8L, OWNER, ACCT, 105L, 888L, 0, BATCH, 0),
                new Line(9L, OWNER, ACCT, 106L, 321L, 0, BATCH, 0),
                new Line(10L, OWNER, ACCT, 101L, 10_000L, 0, BATCH, 1),
                new Line(11L, 8L, ACCT, 101L, 500L, 0, BATCH, 0),
                new Line(12L, OWNER, "ACCT_B", 101L, 300L, 0, BATCH, 0)
        );
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO kd_outbound_line(id, owner_user_id, acct_id, outbound_fid, amount_cents,"
                        + " invalid, batch_no, deleted) VALUES (?,?,?,?,?,?,?,?)")) {
            for (Line line : lines) {
                ps.setLong(1, line.id);
                ps.setLong(2, line.ownerUserId);
                ps.setString(3, line.acctId);
                ps.setLong(4, line.outboundFid);
                if (line.amountCents == null) {
                    ps.setObject(5, null);
                } else {
                    ps.setLong(5, line.amountCents);
                }
                ps.setInt(6, line.invalid);
                ps.setString(7, line.batchNo);
                ps.setInt(8, line.deleted);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return lines;
    }

    private static Map<Long, Long> readHeaderAmounts(Connection conn) throws Exception {
        Map<Long, Long> amounts = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, amount_cents FROM kd_outbound")) {
            while (rs.next()) {
                amounts.put(rs.getLong("id"), rs.getLong("amount_cents"));
            }
        }
        return amounts;
    }

    private static final class Header {
        final long id;
        final long ownerUserId;
        final String acctId;
        final long kdFid;
        long amountCents;
        final String batchNo;
        final int deleted;

        Header(long id, long ownerUserId, String acctId, long kdFid, long amountCents, String batchNo, int deleted) {
            this.id = id;
            this.ownerUserId = ownerUserId;
            this.acctId = acctId;
            this.kdFid = kdFid;
            this.amountCents = amountCents;
            this.batchNo = batchNo;
            this.deleted = deleted;
        }
    }

    private record Line(long id, long ownerUserId, String acctId, long outboundFid, Long amountCents,
                        int invalid, String batchNo, int deleted) {
    }
}
