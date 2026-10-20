package com.example.admin.domain.model.log;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务操作日志（需求 9：出货、导入、回滚、作废、改价、对账 必须留痕）。
 *
 * <p>与 {@code sys_login_log}（登录审计）职责分离：本表记的是"谁改了哪张业务单据、
 * 改前是什么、改后是什么"，是数据可回退与责任可追溯的依据。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class OperationLog {

    /** 主键 */
    private Long id;

    /** 模块：CUSTOMER / ORDER / SHIPMENT / IMPORT */
    private String module;

    /** 业务 ID */
    private String bizId;

    /** 动作：CREATE / UPDATE / DELETE / CONFIRM / CANCEL / SHIP / VOID / IMPORT / ROLLBACK / RECONCILE */
    private String action;

    /** 变更前快照（JSON） */
    private String beforeJson;

    /** 变更后快照（JSON） */
    private String afterJson;

    /** 操作原因（敏感操作必填） */
    private String reason;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人姓名（展示用，非库字段） */
    private String operatorName;

    /** 操作时间 */
    private LocalDateTime operateTime;

    /**
     * 构造一条操作日志（时间由调用方传入，便于批量操作统一时间）。
     *
     * @param module  模块
     * @param bizId   业务 ID
     * @param action  动作
     * @param before  变更前 JSON
     * @param after   变更后 JSON
     * @param reason  原因
     * @param operatorId 操作人
     * @param time    操作时间
     * @return 操作日志
     */
    public static OperationLog of(String module, String bizId, String action, String before, String after,
                                  String reason, Long operatorId, LocalDateTime time) {
        OperationLog log = new OperationLog();
        log.setModule(module);
        log.setBizId(bizId);
        log.setAction(action);
        log.setBeforeJson(before);
        log.setAfterJson(after);
        log.setReason(reason);
        log.setOperatorId(operatorId);
        log.setOperateTime(time);
        return log;
    }
}
