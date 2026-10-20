package com.example.admin.application.service;

import com.example.admin.interfaces.dto.resp.ImportBatchResp;
import com.example.admin.interfaces.dto.resp.ImportCommitResp;
import com.example.admin.interfaces.dto.resp.ImportPreviewResp;

import java.util.List;

/**
 * 导入 / 导出应用服务（附录A：三步法导入、双模板导出、错误清单、一键回滚）。
 *
 * <p>导入三步法：
 * <ol>
 *     <li>{@link #preview} 上传解析 → 返回报告，不落库；</li>
 *     <li>前端逐行修正错误行；</li>
 *     <li>{@link #confirm} 确认导入 → 只处理"确认通过的行"，落库并生成批次。</li>
 * </ol>
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ImportExportAppService {

    /**
     * 第一步：上传解析（只报告不落库）。
     *
     * @param type       模块：CUSTOMER / ORDER / SHIPMENT
     * @param fileBytes  文件字节
     * @param fileName   文件名
     * @param sheetName  工作表名（为空取第一个）
     * @param idempotent 幂等策略：SKIP / OVERWRITE / APPEND
     * @return 预览报告
     */
    ImportPreviewResp preview(String type, byte[] fileBytes, String fileName, String sheetName, String idempotent);

    /**
     * 第三步：确认导入（落库并生成批次）。
     *
     * @param type       模块
     * @param fileBytes  文件字节
     * @param fileName   文件名
     * @param sheetName  工作表名
     * @param idempotent 幂等策略
     * @return 导入结果
     */
    ImportCommitResp confirm(String type, byte[] fileBytes, String fileName, String sheetName, String idempotent);

    /**
     * 导出数据。
     *
     * @param type      模块
     * @param template  模板：SYSTEM=系统模板 LEGACY=传统台账
     * @param keyword   关键字
     * @param beginDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @param ids       指定 ID 列表（为空则按条件全量导出）
     * @return xlsx 字节
     */
    byte[] export(String type, String template, String keyword, String beginDate, String endDate, List<String> ids);

    /**
     * 下载导入模板。
     *
     * @param type     模块
     * @param template 模板：SYSTEM / LEGACY
     * @return xlsx 字节
     */
    byte[] template(String type, String template);

    /**
     * 历史导入批次。
     *
     * @param type  模块（为空表示全部）
     * @param limit 条数上限
     * @return 批次列表
     */
    List<ImportBatchResp> batches(String type, int limit);

    /**
     * 回滚某个批次的增量（仅限回滚本批次新增的出货，不做全局撤销）。
     *
     * @param batchId 批次 ID
     * @param reason  回滚原因（必填）
     */
    void rollback(String batchId, String reason);

    /**
     * 下载某个批次的《错误清单 xlsx》。
     *
     * <p>清单内容与预览页标红的行一致（行号 / 失败原因 / 原始报文），
     * 使用者可以直接转发给同事去补资料，不必重新上传一遍文件。
     *
     * @param batchId 批次 ID
     * @return xlsx 字节；该批次没有失败行时返回 null
     */
    byte[] errorReport(String batchId);
}
