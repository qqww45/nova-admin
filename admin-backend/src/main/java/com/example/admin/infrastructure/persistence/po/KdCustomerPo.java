package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 金蝶客户持久化对象（对应 kd_customer）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_customer")
@Schema(name = "KdCustomerPo", description = "金蝶客户")
public class KdCustomerPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "数据归属人")
    private Long ownerUserId;

    @Schema(description = "金蝶账套")
    private String acctId;

    @Schema(description = "金蝶 FCUSTID")
    private Long kdFid;

    @Schema(description = "金蝶客户编码")
    private String kdNumber;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "是否禁用")
    private Integer disabled;

    @Schema(description = "最近批次")
    private String batchNo;

    @Schema(description = "最近同步时间")
    private LocalDateTime syncAt;
}
