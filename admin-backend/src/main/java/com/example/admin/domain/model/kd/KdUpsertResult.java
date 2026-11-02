package com.example.admin.domain.model.kd;

/**
 * 批量 upsert 结果。
 *
 * @param received 上行行数
 * @param upserted 实际落库行数（新增 + 更新）
 * @param skipped 跳过行数（缺主键等无法入库的行）
 * @author admin-system
 * @since 2026-11-02
 */
public record KdUpsertResult(int received, int upserted, int skipped) {

    /**
     * 空结果。
     *
     * @return 全 0 结果
     */
    public static KdUpsertResult empty() {
        return new KdUpsertResult(0, 0, 0);
    }

    /**
     * 合并两次结果（同一实体分批上报时累加）。
     *
     * @param other 另一次结果
     * @return 合并结果
     */
    public KdUpsertResult plus(KdUpsertResult other) {
        return new KdUpsertResult(received + other.received,
                upserted + other.upserted,
                skipped + other.skipped);
    }
}
