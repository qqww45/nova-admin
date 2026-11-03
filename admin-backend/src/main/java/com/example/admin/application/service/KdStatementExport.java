package com.example.admin.application.service;

/**
 * 对账单导出结果。
 *
 * @param fileName 下载文件名（含扩展名，不含路径）
 * @param content xlsx 文件字节
 * @author admin-system
 * @since 2026-11-03
 */
public record KdStatementExport(String fileName, byte[] content) {
}
