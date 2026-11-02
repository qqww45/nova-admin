package com.example.admin.domain.model.kd;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步任务清单（客户端"同步什么、用什么字段"的唯一来源）。
 *
 * <p>{@code acctId = "*"} 表示全局模板；具体账套可覆盖同 entity 的模板。
 * 新增单据类型 = 加一条任务 + 一张表 + 一个页面，客户端不用发版。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
public class KdSyncTask {

    /** 全局模板标识 */
    public static final String GLOBAL = "*";

    /** 主键 */
    private Long id;

    /** 账套：* 全局 / 具体账套 */
    private String acctId;

    /** 实体标识：customer / outbound / outbound_line */
    private String entity;

    /** 金蝶 FormId */
    private String formId;

    /** 上报接口 */
    private String endpoint;

    /** 金蝶 FieldKeys */
    private String fieldKeys;

    /** 水位字段 */
    private String watermarkField;

    /** 是否启用 */
    private Integer enabled;

    /** 排序 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
