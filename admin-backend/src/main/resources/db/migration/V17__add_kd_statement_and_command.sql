-- =============================================================================
-- V17：金蝶对账单菜单 + 采集器指令队列
--
-- 说明：
--   1. 菜单 id=44「对账单」，挂在「金蝶同步」(40) 下，权限 kd:statement:*；
--   2. 新表 kd_command：网页下发指令（立即同步 / 重载配置 / 吊销 / 升级），
--      采集器轮询取走并回执。用表而不是 WebSocket 通道，是因为
--      部署环境常有反向代理与长连接限制，表 + 轮询在断网重连后天然不丢指令；
--   3. 指令只存"做什么"，参数放 payload（TEXT，JSON 字符串），不回传业务数据。
-- =============================================================================

-- ---------------------------- 1. 指令队列 ----------------------------
CREATE TABLE IF NOT EXISTS kd_command (
    id           BIGINT       NOT NULL PRIMARY KEY,
    binding_id   BIGINT       NOT NULL,
    user_id      BIGINT,
    acct_id      VARCHAR(64),
    command      VARCHAR(32)  NOT NULL,
    payload      TEXT,
    status       VARCHAR(16)  NOT NULL,
    result       VARCHAR(500),
    deliver_time TIMESTAMP,
    done_time    TIMESTAMP,
    expire_time  TIMESTAMP,
    create_by    BIGINT,
    create_time  TIMESTAMP,
    update_by    BIGINT,
    update_time  TIMESTAMP,
    deleted      INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_kd_command_binding ON kd_command (binding_id, status);

-- ---------------------------- 2. 菜单 ----------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(44, 40, '对账单', 2, '/kd/statement', 'kd/statement/index', 'kd:statement:list', 'Document', 4, 1, 0, '按客户/期间汇总金蝶出库数据并导出', 1, '2026-11-02 00:00:00', 1, '2026-11-02 00:00:00', 0);

-- ---------------------------- 3. 按钮权限 ----------------------------
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort, visible, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(4104, 41, '下发指令', 3, '', '', 'kd:binding:command', '', 4, 1, 0, '远程触发采集器同步', 1, '2026-11-02 00:00:00', 1, '2026-11-02 00:00:00', 0),
(4401, 44, '对账查询', 3, '', '', 'kd:statement:list',   '', 1, 1, 0, '', 1, '2026-11-02 00:00:00', 1, '2026-11-02 00:00:00', 0),
(4402, 44, '对账导出', 3, '', '', 'kd:statement:export', '', 2, 1, 0, '', 1, '2026-11-02 00:00:00', 1, '2026-11-02 00:00:00', 0);

-- ---------------------------- 4. 角色授权 ----------------------------
INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(12001, 1, 44,   1, '2026-11-02 00:00:00'),
(12002, 1, 4104, 1, '2026-11-02 00:00:00'),
(12003, 1, 4401, 1, '2026-11-02 00:00:00'),
(12004, 1, 4402, 1, '2026-11-02 00:00:00');

INSERT INTO sys_role_menu (id, role_id, menu_id, create_by, create_time) VALUES
(12011, 2, 44,   1, '2026-11-02 00:00:00'),
(12012, 2, 4104, 1, '2026-11-02 00:00:00'),
(12013, 2, 4401, 1, '2026-11-02 00:00:00'),
(12014, 2, 4402, 1, '2026-11-02 00:00:00');
