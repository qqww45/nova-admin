package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 日志文件的一行（在线日志查看响应）。
 *
 * <p>为什么返回原始行而不是结构化字段：日志格式由 logback pattern 决定，
 * 强行解析结构化字段会在调整 pattern 时静默失效；保留原始行最稳，也便于复制排障。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
@Schema(name = "LogLineResp", description = "日志行")
public class LogLineResp {

    @Schema(description = "日志原文（含时间戳、级别、线程、内容）")
    private String content;
}
