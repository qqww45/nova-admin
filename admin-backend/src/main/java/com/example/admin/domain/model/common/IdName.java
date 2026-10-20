package com.example.admin.domain.model.common;

/**
 * ID + 名称 的最小配对（下拉选项、批量翻译姓名等场景）。
 *
 * @param id   ID
 * @param name 名称
 * @author admin-system
 * @since 2026-01-01
 */
public record IdName(Long id, String name) {
}
