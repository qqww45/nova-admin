package com.example.admin.domain.model.imports;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入批次（附录A A.5：可查看历史批次、下载错误清单、一键回滚）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class ImportBatch {

    /** 主键 */
    private Long id;

    /** 导入类型 */
    private ImportType batchType;

    /** 原文件名 */
    private String fileName;

    /** 工作表名 */
    private String sheetName;

    /** 解析总行数 */
    private Integer totalCount;

    /** 新增条数 */
    private Integer insertCount;

    /** 更新条数 */
    private Integer updateCount;

    /** 跳过条数 */
    private Integer skipCount;

    /** 失败条数（阻断级） */
    private Integer failCount;

    /** 警告条数（入库但标记待补全） */
    private Integer warnCount;

    /** 幂等策略 */
    private IdempotentStrategy idempotent;

    /** 错误清单文件名 */
    private String errorFile;

    /** 失败行明细（JSON 字符串，见 {@link ImportErrorLine}） */
    private String errorDetail;

    /** 批次状态 */
    private BatchStatus status;

    /** 备注 */
    private String remark;

    /** 操作人 */
    private Long createBy;

    /** 操作人姓名（展示用，非库字段） */
    private String operatorName;

    /** 导入时间 */
    private LocalDateTime createTime;

    /**
     * 记录失败行明细。
     *
     * <p>只保留失败行：成功行已经落库，没必要在批次里再存一份副本。
     *
     * @param lines 失败行明细
     */
    public void attachErrors(List<ImportErrorLine> lines) {
        if (lines == null || lines.isEmpty()) {
            this.errorDetail = null;
            return;
        }
        JSONArray array = new JSONArray();
        for (ImportErrorLine line : lines) {
            JSONObject json = new JSONObject();
            json.set("lineNo", line.lineNo());
            json.set("message", line.message());
            json.set("raw", line.raw());
            array.add(json);
        }
        this.errorDetail = array.toString();
    }

    /**
     * 取出失败行明细。
     *
     * @return 失败行列表；无失败行返回空列表
     */
    public List<ImportErrorLine> errors() {
        if (!JSONUtil.isJsonArray(errorDetail)) {
            return new ArrayList<>();
        }
        List<ImportErrorLine> result = new ArrayList<>();
        for (Object node : JSONUtil.parseArray(errorDetail)) {
            JSONObject json = (JSONObject) node;
            result.add(new ImportErrorLine(
                    json.getInt("lineNo", 0),
                    json.getStr("message", ""),
                    json.getStr("raw", "")));
        }
        return result;
    }
}
