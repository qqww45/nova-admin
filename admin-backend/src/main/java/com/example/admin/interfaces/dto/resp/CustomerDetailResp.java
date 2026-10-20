package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 客户详情响应（含联系人、开票信息、关联订单）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "CustomerDetailResp", description = "客户详情")
public class CustomerDetailResp {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "客户编号")
    private String customerCode;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    @Schema(description = "联系人列表")
    private List<CustomerContactResp> contacts;

    @Schema(description = "开票信息列表")
    private List<CustomerInvoiceResp> invoices;

    @Schema(description = "关联订单")
    private List<OrderBriefResp> orders;

    @Schema(description = "关联订单数")
    private Integer orderCount;

    @Schema(description = "累计下单金额（价税合计）")
    private BigDecimal totalAmount;
}
