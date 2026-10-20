package com.example.admin.domain.model.config;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置项（领域值对象）。
 *
 * <p>为什么做成值对象而不是实体：配置项无业务行为（不会被修改、不会有事件），
 * 全部字段是"展示用数据"，由 {@link com.example.admin.domain.service.ConfigService} 编排读写。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Builder
public class ConfigItem {

    /** 主键（新增时为 null） */
    private final Long id;

    /** 配置键（业务唯一，例：{@code admin.title}） */
    private final String key;

    /** 配置值（始终是字符串，由服务层按 type 解析） */
    private final String value;

    /** 值类型 */
    private final ConfigType type;

    /** 是否内置：内置项不允许删除（可改值） */
    private final boolean builtin;

    /** 备注（说明用途） */
    private final String remark;

    /** 创建时间 */
    private final LocalDateTime createTime;

    /** 更新时间 */
    private final LocalDateTime updateTime;
}
