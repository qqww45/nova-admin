package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 金蝶客户数据响应（排查页）。
 *
 * <p>字段与 kd_customer 一一对应，金额类字段无——客户主数据不涉及金额。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@Schema(name = "KdCustomerDataResp", description = "金蝶客户数据")
public class KdCustomerDataResp {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "数据归属人")
    private String ownerUserId;

    @Schema(description = "数据归属人姓名（管理员排查时辨认业务员）")
    private String ownerUserName;

    @Schema(description = "账套")
    private String acctId;

    @Schema(description = "金蝶 FCUSTID")
    private String kdFid;

    @Schema(description = "金蝶客户编码 FNumber")
    private String kdNumber;

    @Schema(description = "客户名称 FName")
    private String customerName;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "是否禁用：1 是 0 否（FForbidStatus = B）")
    private Integer disabled;

    @Schema(description = "最近上报批次")
    private String batchNo;

    @Schema(description = "最近同步时间")
    private LocalDateTime syncAt;
}
