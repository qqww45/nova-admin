package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量操作逐行结果（需求 8：批量接口必须返回逐行成功/失败明细）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BatchLineResult", description = "批量操作逐行结果")
public class BatchLineResult {

    /** 行号（对应提交时的第几行，从 1 开始；导入时对应 Excel 原始行号） */
    @Schema(description = "行号")
    private Integer lineNo;

    /** 是否成功 */
    @Schema(description = "是否成功")
    private boolean success;

    /** 成功时为业务 ID */
    @Schema(description = "业务ID")
    private String bizId;

    /** 失败原因（可直接展示给用户） */
    @Schema(description = "失败原因")
    private String message;

    /** 错误列（导入场景定位到具体列） */
    @Schema(description = "错误列")
    private String errorField;

    /** 原始行数据（导入场景回显，便于就地修正） */
    @Schema(description = "原始行数据")
    private String rawData;

    /**
     * 构造一条成功结果。
     *
     * @param lineNo 行号
     * @param bizId  业务 ID
     * @return 行结果
     */
    public static BatchLineResult success(int lineNo, String bizId) {
        return new BatchLineResult(lineNo, true, bizId, null, null, null);
    }

    /**
     * 构造一条失败结果。
     *
     * @param lineNo     行号
     * @param message    失败原因
     * @param errorField 错误列
     * @param rawData    原始行数据
     * @return 行结果
     */
    public static BatchLineResult fail(int lineNo, String message, String errorField, String rawData) {
        return new BatchLineResult(lineNo, false, null, message, errorField, rawData);
    }
}
