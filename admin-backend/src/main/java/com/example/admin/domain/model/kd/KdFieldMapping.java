package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账套字段映射（按绑定配置）。
 *
 * <p>典型 key：{@code line_field_keys}（出库分录字段集）、{@code customer_po_field}
 * （客户采购订单号字段名）。金蝶每家账套字段名不同，改这里即可，无需发版。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
public class KdFieldMapping {

    /** 主键 */
    private Long id;

    /** 绑定 ID */
    private Long bindingId;

    /** 映射键 */
    private String mappingKey;

    /** 映射值 */
    private String mappingValue;

    /** 备注 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
