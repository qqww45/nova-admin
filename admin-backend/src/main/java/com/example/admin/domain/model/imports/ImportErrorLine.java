package com.example.admin.domain.model.imports;

/**
 * 导入失败行明细。
 *
 * <p>为什么要把失败行单独建模并落库：用户在预览页看到红字后，往往不会当场修完——
 * 更常见的是把《错误清单》发给同事去补资料。若只在响应里回传一次，
 * 第二天再想下载就没有了，只能重新上传一遍文件。
 *
 * @param lineNo  在原始文件中的行号（与用户 Excel 的行号一致，便于定位）
 * @param message 失败原因
 * @param raw     该行的原始报文（拼接后的关键列，用于人工核对是不是自己那行）
 * @author admin-system
 * @since 2026-01-01
 */
public record ImportErrorLine(int lineNo, String message, String raw) {
}
