package com.example.admin.domain.model.mapping;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入字段映射（历史台账列名 → 系统字段，配置化，见附录A）。
 *
 * <p>存在的意义：用户历史 xlsx 的列头叫法五花八门（"客户名"/"Client"/"客户名称"），
 * 若把别名写死在代码里，每来一种新台账就要发一次版。放到配置表后，
 * 新增别名只是插一条数据。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class ImportMapping {

    /** 主键 */
    private Long id;

    /** 模块：CUSTOMER / ORDER / SHIPMENT */
    private String module;

    /** 系统字段名，如 customer_name */
    private String fieldName;

    /** 表头别名，英文逗号分隔 */
    private String aliases;

    /** 是否必填：1=必填（缺失为阻断级错误）0=选填 */
    private Integer required;

    /** 模板列序（模板下载与导出按此排序） */
    private Integer columnOrder;

    /** 缺省默认值（单元格为空时使用） */
    private String defaultValue;

    /** 值类型：STRING / NUMBER / DATE / RATE */
    private String fieldType;

    /** 备注 */
    private String remark;

    /**
     * 把逗号分隔的别名串拆成列表。
     *
     * @return 别名列表（不会为 null）
     */
    public List<String> aliasList() {
        List<String> list = new ArrayList<>();
        if (aliases == null || aliases.isBlank()) {
            return list;
        }
        for (String part : aliases.split("[,，;；]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    /**
     * 是否必填。
     *
     * @return true 表示必填
     */
    public boolean isRequired() {
        return required != null && required == 1;
    }
}
