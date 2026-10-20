package com.example.admin.domain.model.dict;

import lombok.Data;

/**
 * 字典项（税率、币种、结算方式、交货方式等）。
 *
 * <p>存在的意义：需求明确"税率禁止硬编码，从字典读取"，
 * 业务字典与系统菜单共用一套"编码 + 值 + 标签"三元组的最小模型。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class DictItem {

    /** 主键 */
    private Long id;

    /** 字典编码：tax_rate / currency / settlement_method / delivery_method / invoice_type */
    private String dictCode;

    /** 字典值（存库值，如 0.13） */
    private String dictValue;

    /** 字典标签（展示值，如 13%） */
    private String dictLabel;

    /** 显示排序 */
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
