package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置持久化对象（对应表 {@code sys_config}）。
 *
 * <p>为什么 value 用 String 而不是按 type 分字段：
 * 配置项类型在运行期可变（运营可能把 NUMBER 改成 STRING 等等），
 * 用泛型或 JSON 列会破坏 SQL 兼容性 + 阻碍部分场景的 LIKE 查询。字符串按需解析更稳。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
@Schema(name = "SysConfig", description = "系统配置")
public class SysConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 配置键（业务唯一） */
    @Schema(description = "配置键")
    private String configKey;

    /** 配置值（字符串，按 type 解析） */
    @Schema(description = "配置值")
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
    @Schema(description = "值类型")
    private String configType;

    /** 是否内置：1 是 0 否（内置项不可删除） */
    @Schema(description = "是否内置")
    private Integer builtin;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;
}
