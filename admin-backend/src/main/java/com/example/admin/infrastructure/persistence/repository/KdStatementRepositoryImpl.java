package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.ListUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.repository.KdStatementRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdCustomerMapper;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundLineMapper;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundMapper;
import com.example.admin.infrastructure.persistence.po.KdCustomerPo;
import com.example.admin.infrastructure.persistence.po.KdOutboundLinePo;
import com.example.admin.infrastructure.persistence.po.KdOutboundPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 对账取数仓储实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Repository
@RequiredArgsConstructor
public class KdStatementRepositoryImpl implements KdStatementRepository {

    /** IN 查询分片大小：SQLite 默认变量上限 999，500 是安全值 */
    private static final int IN_CHUNK = 500;

    private final KdOutboundMapper outboundMapper;
    private final KdOutboundLineMapper lineMapper;
    private final KdCustomerMapper customerMapper;
    private final KdConverter converter;

    @Override
    public List<KdOutbound> listBills(Long ownerUserId, String acctId, LocalDateTime from, LocalDateTime to,
                                      Long customerFid) {
        LambdaQueryWrapper<KdOutboundPo> wrapper = new LambdaQueryWrapper<KdOutboundPo>()
                // 作废单与全量差集标失效的单据都不进对账单
                .eq(KdOutboundPo::getInvalid, 0)
                .eq(KdOutboundPo::getCancelled, 0)
                .eq(ownerUserId != null, KdOutboundPo::getOwnerUserId, ownerUserId)
                .eq(acctId != null && !acctId.isBlank(), KdOutboundPo::getAcctId, acctId)
                .eq(customerFid != null, KdOutboundPo::getCustomerFid, customerFid)
                .ge(from != null, KdOutboundPo::getBizDate, from)
                .lt(to != null, KdOutboundPo::getBizDate, to)
                .orderByAsc(KdOutboundPo::getBizDate)
                .orderByAsc(KdOutboundPo::getBillNo);
        return outboundMapper.selectList(wrapper).stream().map(converter::outboundToDomain).toList();
    }

    @Override
    public List<KdOutboundLine> listLines(Long ownerUserId, List<KdOutbound> bills) {
        if (bills == null || bills.isEmpty()) {
            return List.of();
        }
        // 按账套分组：金蝶 FID 只在账套内唯一，跨账套同 FID 会在只按 FID 过滤时串行
        Map<String, List<Long>> fidsByAcct = new LinkedHashMap<>();
        for (KdOutbound bill : bills) {
            String acct = bill.getAcctId() == null ? "" : bill.getAcctId();
            fidsByAcct.computeIfAbsent(acct, k -> new ArrayList<>()).add(bill.getKdFid());
        }
        List<KdOutboundLine> result = new ArrayList<>();
        for (Map.Entry<String, List<Long>> entry : fidsByAcct.entrySet()) {
            for (List<Long> chunk : ListUtil.partition(entry.getValue(), IN_CHUNK)) {
                List<KdOutboundLinePo> list = lineMapper.selectList(new LambdaQueryWrapper<KdOutboundLinePo>()
                        .eq(KdOutboundLinePo::getInvalid, 0)
                        .eq(ownerUserId != null, KdOutboundLinePo::getOwnerUserId, ownerUserId)
                        .eq(KdOutboundLinePo::getAcctId, entry.getKey())
                        .in(KdOutboundLinePo::getOutboundFid, chunk)
                        .orderByAsc(KdOutboundLinePo::getOutboundFid)
                        .orderByAsc(KdOutboundLinePo::getEntryId));
                list.forEach(po -> result.add(converter.lineToDomain(po)));
            }
        }
        return result;
    }

    @Override
    public List<KdOutbound> listAllBillsIncludingExcluded(Long ownerUserId, String acctId,
                                                           LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<KdOutboundPo> wrapper = new LambdaQueryWrapper<KdOutboundPo>()
                // 体检口径：不过滤 invalid / cancelled——它们正是要暴露的问题行
                .eq(ownerUserId != null, KdOutboundPo::getOwnerUserId, ownerUserId)
                .eq(acctId != null && !acctId.isBlank(), KdOutboundPo::getAcctId, acctId)
                .ge(from != null, KdOutboundPo::getBizDate, from)
                .lt(to != null, KdOutboundPo::getBizDate, to)
                .orderByAsc(KdOutboundPo::getBizDate)
                .orderByAsc(KdOutboundPo::getBillNo);
        return outboundMapper.selectList(wrapper).stream().map(converter::outboundToDomain).toList();
    }

    @Override
    public Map<String, String> findCustomerNumbers(Long ownerUserId, List<KdOutbound> bills) {
        if (bills == null || bills.isEmpty()) {
            return Map.of();
        }
        // 金蝶客户内码只在账套内唯一：与取数一致，必须按账套分组过滤
        Map<String, List<Long>> fidsByAcct = new LinkedHashMap<>();
        for (KdOutbound bill : bills) {
            String acct = bill.getAcctId() == null ? "" : bill.getAcctId();
            fidsByAcct.computeIfAbsent(acct, k -> new ArrayList<>()).add(bill.getCustomerFid());
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Long>> entry : fidsByAcct.entrySet()) {
            List<Long> fids = entry.getValue().stream().filter(Objects::nonNull).distinct().toList();
            for (List<Long> chunk : ListUtil.partition(fids, IN_CHUNK)) {
                if (chunk.isEmpty()) {
                    continue;
                }
                List<KdCustomerPo> list = customerMapper.selectList(new LambdaQueryWrapper<KdCustomerPo>()
                        .select(KdCustomerPo::getKdFid, KdCustomerPo::getKdNumber)
                        .eq(ownerUserId != null, KdCustomerPo::getOwnerUserId, ownerUserId)
                        .eq(KdCustomerPo::getAcctId, entry.getKey())
                        .in(KdCustomerPo::getKdFid, chunk));
                for (KdCustomerPo po : list) {
                    if (po.getKdNumber() != null && !po.getKdNumber().isBlank()) {
                        result.put(entry.getKey() + "|" + po.getKdFid(), po.getKdNumber());
                    }
                }
            }
        }
        return result;
    }
}
