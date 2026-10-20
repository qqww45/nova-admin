package com.example.admin.application.service.impl;

import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.log.OperationLog;
import com.example.admin.domain.model.log.OperationLogQuery;
import com.example.admin.domain.repository.OperationLogRepository;
import com.example.admin.interfaces.dto.resp.OperationLogResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 操作日志查询服务实现（M6 审计）。
 *
 * <p>模块与动作的中文名来自本类的常量表，而不是字典表：
 * 这两组是系统内固定的动作枚举，不会由使用者维护，
 * 放进字典反而会让人误以为可以随便改（改了就和后端 switch 对不上）。
 * 真正需要使用者配置的业务字典（税率等）才进 biz_dict_item。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class LogAppServiceImpl implements com.example.admin.application.service.LogAppService {

    private static final Map<String, String> MODULE_LABELS = Map.of(
            "CUSTOMER", "客户",
            "ORDER", "订单",
            "SHIPMENT", "出货",
            "IMPORT", "导入");

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("CREATE", "新增"),
            Map.entry("UPDATE", "修改"),
            Map.entry("REMOVE", "删除"),
            Map.entry("CONFIRM", "确认"),
            Map.entry("CANCEL", "取消"),
            Map.entry("SHIP", "出货"),
            Map.entry("VOID", "作废"),
            Map.entry("IMPORT", "导入"),
            Map.entry("ROLLBACK", "回滚"),
            Map.entry("RECONCILE", "对账"),
            Map.entry("LINK", "补挂订单"));

    private final OperationLogRepository operationLogRepository;

    @Override
    public PageResult<OperationLogResp> page(String module, String action, String keyword,
                                             int pageNum, int pageSize) {
        OperationLogQuery query = new OperationLogQuery();
        query.setModule(module);
        query.setAction(action);
        query.setKeyword(keyword);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        PageResult<OperationLog> page = operationLogRepository.page(query);
        List<OperationLogResp> records = new ArrayList<>();
        for (OperationLog log : page.getRecords()) {
            records.add(toResp(log));
        }
        PageResult<OperationLogResp> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        return result;
    }

    @Override
    public List<OperationLogResp> listByBiz(String module, String bizId, int limit) {
        return operationLogRepository.listByBiz(module, bizId, limit).stream().map(this::toResp).toList();
    }

    private OperationLogResp toResp(OperationLog log) {
        OperationLogResp resp = new OperationLogResp();
        resp.setId(String.valueOf(log.getId()));
        resp.setModule(log.getModule());
        resp.setModuleLabel(MODULE_LABELS.getOrDefault(log.getModule(), log.getModule()));
        resp.setBizId(log.getBizId());
        resp.setAction(log.getAction());
        resp.setActionLabel(ACTION_LABELS.getOrDefault(log.getAction(), log.getAction()));
        resp.setBeforeJson(log.getBeforeJson());
        resp.setAfterJson(log.getAfterJson());
        resp.setReason(log.getReason());
        resp.setOperatorId(log.getOperatorId() == null ? null : String.valueOf(log.getOperatorId()));
        resp.setOperatorName(log.getOperatorName());
        resp.setOperateTime(log.getOperateTime());
        return resp;
    }
}
