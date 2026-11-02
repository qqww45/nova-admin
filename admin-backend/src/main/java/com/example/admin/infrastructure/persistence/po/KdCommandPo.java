package com.example.admin.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 采集器指令持久化对象（对应 kd_command）。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kd_command")
@Schema(name = "KdCommandPo", description = "采集器指令")
public class KdCommandPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "绑定ID")
    private Long bindingId;

    @Schema(description = "数据归属人")
    private Long userId;

    @Schema(description = "账套")
    private String acctId;

    @Schema(description = "指令名")
    private String command;

    @Schema(description = "指令参数")
    private String payload;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "执行结果")
    private String result;

    @Schema(description = "取走时间")
    private LocalDateTime deliverTime;

    @Schema(description = "完成时间")
    private LocalDateTime doneTime;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;
}
