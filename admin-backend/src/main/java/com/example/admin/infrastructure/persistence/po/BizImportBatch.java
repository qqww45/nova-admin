package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导入批次持久化对象（对应 biz_import_batch）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_import_batch")
@Schema(name = "BizImportBatch", description = "导入批次")
public class BizImportBatch extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 导入类型：CUSTOMER / ORDER / SHIPMENT */
    @Schema(description = "导入类型")
    private String batchType;

    /** 原文件名 */
    @Schema(description = "原文件名")
    private String fileName;

    /** 工作表名 */
    @Schema(description = "工作表名")
    private String sheetName;

    /** 解析总行数 */
    @Schema(description = "解析总行数")
    private Integer totalCount;

    /** 新增条数 */
    @Schema(description = "新增条数")
    private Integer insertCount;

    /** 更新条数 */
    @Schema(description = "更新条数")
    private Integer updateCount;

    /** 跳过条数 */
    @Schema(description = "跳过条数")
    private Integer skipCount;

    /** 失败条数 */
    @Schema(description = "失败条数")
    private Integer failCount;

    /** 警告条数 */
    @Schema(description = "警告条数")
    private Integer warnCount;

    /** 幂等策略：SKIP / OVERWRITE / APPEND */
    @Schema(description = "幂等策略")
    private String idempotent;

    /** 错误清单文件名 */
    @Schema(description = "错误清单文件名")
    private String errorFile;

    /** 失败行明细（JSON） */
    @Schema(description = "失败行明细（JSON）")
    private String errorDetail;

    /** 批次状态：PREVIEW / COMMITTED / ROLLED_BACK */
    @Schema(description = "批次状态")
    private String status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
