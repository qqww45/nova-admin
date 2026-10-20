package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典项持久化对象（对应 biz_dict_item）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_dict_item")
@Schema(name = "BizDictItem", description = "业务字典项")
public class BizDictItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 字典编码 */
    @Schema(description = "字典编码")
    private String dictCode;

    /** 字典值 */
    @Schema(description = "字典值")
    private String dictValue;

    /** 字典标签 */
    @Schema(description = "字典标签")
    private String dictLabel;

    /** 显示排序 */
    @Schema(description = "显示排序")
    private Integer sort;

    /** 状态：1 启用 0 停用 */
    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
