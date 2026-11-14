package com.example.admin.infrastructure.persistence.mapper;

import org.apache.ibatis.builder.annotation.ProviderContext;

/**
 * 按批次把出库分录金额汇总回填表头的跨库 SQL。
 *
 * <p>语义必须与旧的内存循环一致：
 * <ul>
 *     <li>只汇总本归属人、本账套、本批次、未失效、未逻辑删除的分录；</li>
 *     <li>{@code amount_cents} 为 null 时按 0；按 {@code outbound_fid} 分组求和；</li>
 *     <li>回填匹配归属人 + 账套 + {@code kd_fid} 的未删除表头，<strong>不</strong>按表头
 *     {@code batch_no} 过滤（增量批次的分录可能挂在上一批上报的单头上）。</li>
 * </ul>
 *
 * <p>生产曾在 {@code finishBatch} 里对每个单头发一次 UPDATE，数万行分录会拖过
 * EdgeOne 源站超时（HTTP 524）。这里改成一条 set-based UPDATE。
 *
 * @author admin-system
 * @since 2026-11-14
 */
public class KdOutboundAmountSql {

    /**
     * MySQL / MariaDB：UPDATE … JOIN。
     *
     * <p>生产默认库。子查询先按分录汇总，再按唯一键
     * {@code (owner_user_id, acct_id, kd_fid)} 回填。
     */
    public static final String MYSQL = """
            UPDATE kd_outbound o
            INNER JOIN (
                SELECT owner_user_id, acct_id, outbound_fid,
                       SUM(COALESCE(amount_cents, 0)) AS amount_cents
                FROM kd_outbound_line
                WHERE owner_user_id = #{ownerUserId}
                  AND acct_id = #{acctId}
                  AND batch_no = #{batchNo}
                  AND invalid = 0
                  AND deleted = 0
                GROUP BY owner_user_id, acct_id, outbound_fid
            ) s ON o.owner_user_id = s.owner_user_id
               AND o.acct_id = s.acct_id
               AND o.kd_fid = s.outbound_fid
               AND o.deleted = 0
            SET o.amount_cents = s.amount_cents
            """;

    /**
     * PostgreSQL：UPDATE … FROM。
     */
    public static final String POSTGRES = """
            UPDATE kd_outbound AS o
            SET amount_cents = s.amount_cents
            FROM (
                SELECT outbound_fid, SUM(COALESCE(amount_cents, 0)) AS amount_cents
                FROM kd_outbound_line
                WHERE owner_user_id = #{ownerUserId}
                  AND acct_id = #{acctId}
                  AND batch_no = #{batchNo}
                  AND invalid = 0
                  AND deleted = 0
                GROUP BY outbound_fid
            ) s
            WHERE o.kd_fid = s.outbound_fid
              AND o.owner_user_id = #{ownerUserId}
              AND o.acct_id = #{acctId}
              AND o.deleted = 0
            """;

    /**
     * SQLite 3.33+：UPDATE … FROM（dev / staging）。
     *
     * <p>目标表不能起别名，与 PostgreSQL 语法略有差别。
     */
    public static final String SQLITE = """
            UPDATE kd_outbound
            SET amount_cents = s.amount_cents
            FROM (
                SELECT outbound_fid, SUM(COALESCE(amount_cents, 0)) AS amount_cents
                FROM kd_outbound_line
                WHERE owner_user_id = #{ownerUserId}
                  AND acct_id = #{acctId}
                  AND batch_no = #{batchNo}
                  AND invalid = 0
                  AND deleted = 0
                GROUP BY outbound_fid
            ) AS s
            WHERE kd_outbound.kd_fid = s.outbound_fid
              AND kd_outbound.owner_user_id = #{ownerUserId}
              AND kd_outbound.acct_id = #{acctId}
              AND kd_outbound.deleted = 0
            """;

    /**
     * MyBatis {@code @UpdateProvider} 入口：按连接的 databaseId 选方言。
     *
     * @param context 当前语句上下文（含 databaseId）
     * @return 对应方言的 UPDATE
     */
    public String updateAmountFromLinesByBatch(ProviderContext context) {
        return sqlFor(context == null ? null : context.getDatabaseId());
    }

    /**
     * 按 MyBatis databaseId 选择 SQL。
     *
     * @param databaseId {@code mysql} / {@code postgresql} / {@code sqlite}；null 时走 SQLite
     *                   （本地未配 DatabaseIdProvider 时的兜底，生产由 VendorDatabaseIdProvider 注入）
     * @return SQL 文本
     */
    public static String sqlFor(String databaseId) {
        if ("mysql".equalsIgnoreCase(databaseId)) {
            return MYSQL;
        }
        if ("postgresql".equalsIgnoreCase(databaseId)) {
            return POSTGRES;
        }
        return SQLITE;
    }
}
