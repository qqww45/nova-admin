package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 金蝶销售出库单分录。
 *
 * <p>幂等键：{@code (ownerUserId, acctId, entryId)}。数量以千分之一、金额/单价以分存储，
 * 与客户端 {@code qty_milli / amount_cents / price_cents} 完全一致，中途不做单位换算。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
public class KdOutboundLine {

    /** 主键 */
    private Long id;

    /** 数据归属人 */
    private Long ownerUserId;

    /** 账套 */
    private String acctId;

    /** 金蝶出库单 FID */
    private Long outboundFid;

    /** 金蝶分录内码（或客户端生成的稳定键） */
    private Long entryId;

    /** 物料编码 */
    private String materialNumber;

    /** 物料名称 */
    private String materialName;

    /** 规格 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 数量 × 1000 */
    private Long qtyMilli;

    /** 单价 × 100 */
    private Long priceCents;

    /** 金额 × 100 */
    private Long amountCents;

    /** 源单号 */
    private String srcOrderNo;

    /** 客户采购订单号 */
    private String customerPo;

    /** 行备注 */
    private String remark;

    /** 是否失效：1 是 0 否 */
    private Integer invalid;

    /** 最近上报批次 */
    private String batchNo;

    /** 最近同步时间 */
    private LocalDateTime syncAt;

    /**
     * 计算稳定的分录键。
     *
     * <p>部分账套的 FieldKeys 取不到分录内码。此时用
     * {@code outboundFid * 100000 + 单内行序号} 生成，保证同一条分录重复上报仍落到同一行；
     * 单行数超过 10 万的单据极罕见，冲突概率可忽略。
     *
     * <p><b>注意</b>：{@code seq} 必须是"该单据内的行序号"，而不是请求里的数组下标。
     * 客户端分块上报时若按请求下标生成，第二块会从 0 重新开始，把同一张单的其它行覆盖掉。
     * 官方采集器（desktop）已在切块前按单据累加序号。
     *
     * @param entryId 金蝶分录内码，可为空
     * @param outboundFid 出库单 FID
     * @param seq 单据内行序号（从 0 开始）
     * @return 稳定键
     */
    public static long resolveEntryId(Long entryId, long outboundFid, int seq) {
        if (entryId != null && entryId > 0) {
            return entryId;
        }
        return outboundFid * 100_000L + seq;
    }
}
