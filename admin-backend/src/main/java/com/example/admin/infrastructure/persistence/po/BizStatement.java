package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对账单头持久化对象（对应 biz_statement，签发快照）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_statement")
@Schema(name = "BizStatement", description = "对账单头")
public class BizStatement extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 对账单号（唯一，生成后只读） */
    @Schema(description = "对账单号")
    private String stmtNo;

    /** 客户 ID */
    @Schema(description = "客户ID")
    private Long customerId;

    /** 对账期间起 */
    @Schema(description = "对账期间起")
    private LocalDateTime startDate;

    /** 对账期间止 */
    @Schema(description = "对账期间止")
    private LocalDateTime endDate;

    /** 期初应收 */
    @Schema(description = "期初应收")
    private BigDecimal openingBalance;

    /** 本期出货（正金额合计） */
    @Schema(description = "本期出货金额")
    private BigDecimal shippedAmount;

    /** 本期退货/退厂（正金额合计，展示用；计算取负） */
    @Schema(description = "本期退货金额")
    private BigDecimal returnedAmount;

    /** 本期收款（正金额合计） */
    @Schema(description = "本期收款金额")
    private BigDecimal receivedAmount;

    /** 期末应收 */
    @Schema(description = "期末应收")
    private BigDecimal endingBalance;

    /** 状态：DRAFT / ISSUED / CONFIRMED / DIFFERENT */
    @Schema(description = "状态：DRAFT草稿 ISSUED已签发 CONFIRMED已确认 DIFFERENT有差异")
    private String status;

    /** 客户确认/回签日 */
    @Schema(description = "确认日期")
    private LocalDateTime confirmDate;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 乐观锁版本号 */
    @Version
    @Schema(description = "版本号")
    private Integer version;
}
