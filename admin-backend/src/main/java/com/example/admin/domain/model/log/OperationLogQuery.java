package com.example.admin.domain.model.log;

import com.example.admin.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作日志查询条件。
 *
 * <p>审计查询的三个常用入口：按模块（这个客户/订单发生过什么）、
 * 按动作（谁做过作废）、按时间（某天下午的异常操作）。
 * 关键字同时匹配业务 ID 与操作原因，便于"搜单号"直接定位。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 模块：CUSTOMER / ORDER / SHIPMENT / IMPORT */
    private String module;

    /** 动作：CREATE / UPDATE / VOID / IMPORT / ROLLBACK / RECONCILE */
    private String action;

    /** 关键字：业务 ID / 操作原因 / 操作人姓名 */
    private String keyword;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作时间起 */
    private LocalDateTime beginTime;

    /** 操作时间止 */
    private LocalDateTime endTime;
}
