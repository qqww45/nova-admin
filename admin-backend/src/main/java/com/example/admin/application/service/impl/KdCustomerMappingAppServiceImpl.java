package com.example.admin.application.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.admin.application.service.KdCustomerMappingAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdCustomerBrowseQuery;
import com.example.admin.domain.model.kd.KdCustomerMapping;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.KdCustomerMappingRepository;
import com.example.admin.domain.repository.KdDataRepository;
import com.example.admin.interfaces.dto.req.KdMappingSaveReq;
import com.example.admin.interfaces.dto.resp.KdCustomerMappingResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 金蝶客户 ↔ 内部客户映射应用服务实现。
 *
 * <p>隔离口径与绑定 / 对账单 / 数据查看一致：非超管强制本人（忽略入参），
 * 超管可跨人或显式指定某个业务员。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KdCustomerMappingAppServiceImpl implements KdCustomerMappingAppService {

    private final KdCustomerMappingRepository mappingRepository;
    private final KdDataRepository dataRepository;
    private final CustomerRepository customerRepository;
    private final OperatorContext operatorContext;

    @Override
    public List<KdCustomerMappingResp> list(String acctId, String status, String keyword, String ownerUserId) {
        Long scope = scopeOwner(ownerUserId);
        KdCustomerBrowseQuery query = new KdCustomerBrowseQuery();
        query.setPageNum(1);
        // 业务员客户量在千级以内，映射页一次取全：状态过滤要在"关联映射后"才能判，
        // SQL 分页会在过滤后页数对不上。真正超大时再回退到分页方案。
        query.setPageSize(10_000);
        query.setAcctId(trimToNull(acctId));
        query.setKeyword(trimToNull(keyword));
        query.setOwnerUserId(scope);
        PageResult<KdCustomer> page = dataRepository.pageCustomers(query);

        Map<String, KdCustomerMapping> mappingByKdKey = new HashMap<>();
        for (KdCustomerMapping mapping : mappingRepository.listByOwner(scope)) {
            mappingByKdKey.put(keyOf(mapping.getAcctId(), mapping.getKdFid()), mapping);
        }
        Map<Long, Customer> bizCustomers = new HashMap<>();
        for (Customer customer : customerRepository.listAll()) {
            bizCustomers.put(customer.getId(), customer);
        }

        List<KdCustomerMappingResp> result = new ArrayList<>(page.getRecords().size());
        for (KdCustomer kd : page.getRecords()) {
            KdCustomerMappingResp resp = new KdCustomerMappingResp();
            resp.setId(String.valueOf(kd.getId()));
            resp.setAcctId(kd.getAcctId());
            resp.setKdFid(String.valueOf(kd.getKdFid()));
            resp.setKdNumber(kd.getKdNumber());
            resp.setKdName(kd.getCustomerName());

            KdCustomerMapping mapping = mappingByKdKey.get(keyOf(kd.getAcctId(), kd.getKdFid()));
            if (mapping != null) {
                resp.setBizCustomerId(String.valueOf(mapping.getBizCustomerId()));
                Customer biz = bizCustomers.get(mapping.getBizCustomerId());
                // 内部客户被停用/删除时映射行保留，页面明示而不是静默显示旧名
                resp.setBizCustomerName(biz == null ? "（内部客户不存在）" : biz.getCustomerName());
                resp.setMatchType(mapping.getMatchType());
                resp.setStatus(mapping.isConfirmed() ? "CONFIRMED" : "SUGGESTED");
                resp.setUpdateTime(mapping.getUpdateTime());
            } else {
                resp.setStatus("UNMATCHED");
            }
            result.add(resp);
        }
        String statusFilter = trimToNull(status) == null ? null : status.trim().toUpperCase();
        if (statusFilter != null) {
            result.removeIf(r -> !statusFilter.equals(r.getStatus()));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AutoSuggestResult autoSuggest(String acctId, String ownerUserId) {
        Long scope = scopeOwner(ownerUserId);

        List<KdCustomer> kdCustomers = listAllKdCustomers(scope, trimToNull(acctId));
        Set<String> mappedKeys = new HashSet<>();
        for (KdCustomerMapping mapping : mappingRepository.listByOwner(scope)) {
            mappedKeys.add(keyOf(mapping.getAcctId(), mapping.getKdFid()));
        }

        // 规范化名称 → 内部客户（与"客户唯一性校验"同一口径）。
        // 同名多户会产生歧义：这种客户名不建议，留给人工。
        Map<String, List<Customer>> bizByName = new HashMap<>();
        for (Customer customer : customerRepository.listAll()) {
            // 停用的内部客户仍可被匹配（业务员的历史客户），但删除的不会出现在 listAll
            String key = normalized(customer.getCustomerName());
            if (key == null) {
                continue;
            }
            bizByName.computeIfAbsent(key, k -> new ArrayList<>()).add(customer);
        }

        List<KdCustomerMapping> suggestions = new ArrayList<>();
        int skipped = 0;
        for (KdCustomer kd : kdCustomers) {
            if (mappedKeys.contains(keyOf(kd.getAcctId(), kd.getKdFid()))) {
                continue;
            }
            String key = normalized(kd.getCustomerName());
            List<Customer> hits = key == null ? null : bizByName.get(key);
            if (hits == null || hits.size() != 1) {
                // 无命中或同名歧义：跳过（skipped 计数让业务员知道还有多少要人工）
                skipped++;
                continue;
            }
            KdCustomerMapping mapping = new KdCustomerMapping();
            mapping.setOwnerUserId(scope);
            mapping.setAcctId(kd.getAcctId());
            mapping.setKdFid(kd.getKdFid());
            mapping.setBizCustomerId(hits.get(0).getId());
            mapping.setMatchType(KdCustomerMapping.TYPE_AUTO_SUGGEST);
            mapping.setRemark("名称标准化自动建议，待确认");
            suggestions.add(mapping);
        }
        mappingRepository.saveAll(suggestions);
        log.info("客户映射自动建议：owner={} 生成 {} 条，跳过 {} 条", scope, suggestions.size(), skipped);
        return new AutoSuggestResult(suggestions.size(), skipped);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(KdMappingSaveReq req) {
        LoginUser loginUser = operatorContext.current();
        Long owner;
        if (isAdmin(loginUser)) {
            // 管理员代维护：必须显式指定归属业务员，否则映射行没有归属人
            owner = parseLong(req.getOwnerUserId());
            if (owner == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "管理员代维护时必须指定归属业务员");
            }
        } else {
            owner = operatorContext.currentUserId();
        }
        // 校验内部客户存在（含已停用：历史客户仍可映射）
        if (customerRepository.listAll().stream().noneMatch(c -> req.getBizCustomerId().equals(c.getId()))) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "内部客户不存在，请刷新后重试");
        }
        KdCustomerMapping existing = mappingRepository.find(owner, req.getAcctId(), req.getKdFid());
        if (existing == null) {
            KdCustomerMapping mapping = new KdCustomerMapping();
            mapping.setOwnerUserId(owner);
            mapping.setAcctId(req.getAcctId());
            mapping.setKdFid(req.getKdFid());
            mapping.setBizCustomerId(req.getBizCustomerId());
            mapping.setMatchType(KdCustomerMapping.TYPE_MANUAL);
            mapping.setRemark("业务员人工指定");
            mappingRepository.save(mapping);
            return;
        }
        existing.setBizCustomerId(req.getBizCustomerId());
        existing.setMatchType(KdCustomerMapping.TYPE_MANUAL);
        existing.setRemark("业务员人工指定");
        mappingRepository.update(existing);
    }

    /* ---------------- 内部方法 ---------------- */

    /** 业务员客户量千级以内：分页循环取全 */
    private List<KdCustomer> listAllKdCustomers(Long scope, String acctId) {
        List<KdCustomer> result = new ArrayList<>();
        int pageNum = 1;
        while (true) {
            KdCustomerBrowseQuery query = new KdCustomerBrowseQuery();
            query.setPageNum(pageNum);
            query.setPageSize(10_000);
            query.setOwnerUserId(scope);
            query.setAcctId(acctId);
            PageResult<KdCustomer> page = dataRepository.pageCustomers(query);
            result.addAll(page.getRecords());
            if (result.size() >= page.getTotal() || page.getRecords().isEmpty()) {
                return result;
            }
            pageNum++;
        }
    }

    private String normalized(String name) {
        String normalized = Customer.normalizeName(name);
        return StrUtil.isBlank(normalized) ? null : normalized;
    }

    private Long scopeOwner(String ownerUserIdParam) {
        LoginUser loginUser = operatorContext.current();
        if (isAdmin(loginUser)) {
            return StrUtil.isBlank(ownerUserIdParam) ? null : parseLong(ownerUserIdParam);
        }
        return operatorContext.currentUserId();
    }

    private boolean isAdmin(LoginUser loginUser) {
        return loginUser != null && loginUser.getRoles() != null
                && loginUser.getRoles().contains(Constants.SUPER_ADMIN_ROLE_KEY);
    }

    private static String keyOf(String acctId, Long kdFid) {
        return (acctId == null ? "" : acctId) + "|" + (kdFid == null ? 0L : kdFid);
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
