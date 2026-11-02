package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 金蝶客户（按业务员隔离）。
 *
 * <p>幂等键：{@code (ownerUserId, acctId, kdFid)}。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
public class KdCustomer {

    /** 主键 */
    private Long id;

    /** 数据归属人 */
    private Long ownerUserId;

    /** 账套 */
    private String acctId;

    /** 金蝶 FCUSTID */
    private Long kdFid;

    /** 金蝶客户编码 */
    private String kdNumber;

    /** 客户名称 */
    private String customerName;

    /** 地址 */
    private String address;

    /** 是否禁用：1 是 0 否 */
    private Integer disabled;

    /** 最近上报批次 */
    private String batchNo;

    /** 最近同步时间 */
    private LocalDateTime syncAt;
}
