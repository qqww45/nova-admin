package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdCustomerBrowseQuery;
import com.example.admin.domain.model.kd.KdLineBrowseQuery;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundBrowseQuery;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.repository.KdDataRepository;
import com.example.admin.infrastructure.persistence.converter.KdConverter;
import com.example.admin.infrastructure.persistence.mapper.KdCustomerMapper;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundLineMapper;
import com.example.admin.infrastructure.persistence.mapper.KdOutboundMapper;
import com.example.admin.infrastructure.persistence.po.KdCustomerPo;
import com.example.admin.infrastructure.persistence.po.KdOutboundLinePo;
import com.example.admin.infrastructure.persistence.po.KdOutboundPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 金蝶同步数据浏览仓储实现。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Repository
@RequiredArgsConstructor
public class KdDataRepositoryImpl implements KdDataRepository {

    /** IN 查询分片大小：SQLite 默认变量上限 999，500 是安全值 */
    private static final int IN_CHUNK = 500;

    private final KdCustomerMapper customerMapper;
    private final KdOutboundMapper outboundMapper;
    private final KdOutboundLineMapper lineMapper;
    private final KdConverter converter;

    @Override
    public PageResult<KdCustomer> pageCustomers(KdCustomerBrowseQuery query) {
        LambdaQueryWrapper<KdCustomerPo> wrapper = new LambdaQueryWrapper<KdCustomerPo>()
                .eq(query.getOwnerUserId() != null, KdCustomerPo::getOwnerUserId, query.getOwnerUserId())
                .eq(StrUtil.isNotBlank(query.getAcctId()), KdCustomerPo::getAcctId, query.getAcctId())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(KdCustomerPo::getCustomerName, query.getKeyword())
                        .or().like(KdCustomerPo::getKdNumber, query.getKeyword()))
                .eq(query.getDisabled() != null, KdCustomerPo::getDisabled, query.getDisabled())
                .eq(StrUtil.isNotBlank(query.getBatchNo()), KdCustomerPo::getBatchNo, query.getBatchNo())
                .ge(query.getSyncFrom() != null, KdCustomerPo::getSyncAt, query.getSyncFrom())
                .lt(query.getSyncTo() != null, KdCustomerPo::getSyncAt, query.getSyncTo())
                .orderByDesc(KdCustomerPo::getSyncAt)
                .orderByDesc(KdCustomerPo::getId);
        IPage<KdCustomerPo> page = customerMapper.selectPage(query.buildPage(), wrapper);
        List<KdCustomer> records = page.getRecords().stream().map(converter::customerToDomain).toList();
        return toPage(page, records);
    }

    @Override
    public PageResult<KdOutbound> pageOutbounds(KdOutboundBrowseQuery query) {
        LambdaQueryWrapper<KdOutboundPo> wrapper = new LambdaQueryWrapper<KdOutboundPo>()
                .eq(query.getOwnerUserId() != null, KdOutboundPo::getOwnerUserId, query.getOwnerUserId())
                .eq(StrUtil.isNotBlank(query.getAcctId()), KdOutboundPo::getAcctId, query.getAcctId())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(KdOutboundPo::getBillNo, query.getKeyword())
                        .or().like(KdOutboundPo::getCustomerName, query.getKeyword()))
                .ge(query.getBizFrom() != null, KdOutboundPo::getBizDate, query.getBizFrom())
                .lt(query.getBizTo() != null, KdOutboundPo::getBizDate, query.getBizTo())
                .eq(StrUtil.isNotBlank(query.getDocStatus()), KdOutboundPo::getDocStatus, query.getDocStatus())
                .eq(query.getCancelled() != null, KdOutboundPo::getCancelled, query.getCancelled())
                .eq(query.getInvalid() != null, KdOutboundPo::getInvalid, query.getInvalid())
                .eq(StrUtil.isNotBlank(query.getBatchNo()), KdOutboundPo::getBatchNo, query.getBatchNo())
                .orderByDesc(KdOutboundPo::getBizDate)
                .orderByDesc(KdOutboundPo::getId);
        IPage<KdOutboundPo> page = outboundMapper.selectPage(query.buildPage(), wrapper);
        List<KdOutbound> records = page.getRecords().stream().map(converter::outboundToDomain).toList();
        return toPage(page, records);
    }

    @Override
    public PageResult<KdOutboundLine> pageLines(KdLineBrowseQuery query) {
        LambdaQueryWrapper<KdOutboundLinePo> wrapper = new LambdaQueryWrapper<KdOutboundLinePo>()
                .eq(query.getOwnerUserId() != null, KdOutboundLinePo::getOwnerUserId, query.getOwnerUserId())
                .eq(StrUtil.isNotBlank(query.getAcctId()), KdOutboundLinePo::getAcctId, query.getAcctId())
                .eq(query.getOutboundFid() != null, KdOutboundLinePo::getOutboundFid, query.getOutboundFid())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(KdOutboundLinePo::getMaterialNumber, query.getKeyword())
                        .or().like(KdOutboundLinePo::getMaterialName, query.getKeyword())
                        .or().like(KdOutboundLinePo::getSrcOrderNo, query.getKeyword())
                        .or().like(KdOutboundLinePo::getCustomerPo, query.getKeyword()))
                .eq(query.getInvalid() != null, KdOutboundLinePo::getInvalid, query.getInvalid())
                .eq(StrUtil.isNotBlank(query.getBatchNo()), KdOutboundLinePo::getBatchNo, query.getBatchNo())
                .ge(query.getSyncFrom() != null, KdOutboundLinePo::getSyncAt, query.getSyncFrom())
                .lt(query.getSyncTo() != null, KdOutboundLinePo::getSyncAt, query.getSyncTo())
                .orderByDesc(KdOutboundLinePo::getSyncAt)
                .orderByDesc(KdOutboundLinePo::getId);
        IPage<KdOutboundLinePo> page = lineMapper.selectPage(query.buildPage(), wrapper);
        List<KdOutboundLine> records = page.getRecords().stream().map(converter::lineToDomain).toList();
        return toPage(page, records);
    }

    @Override
    public KdOutbound findBillById(Long id) {
        if (id == null) {
            return null;
        }
        return converter.outboundToDomain(outboundMapper.selectById(id));
    }

    @Override
    public List<KdOutboundLine> listLinesOfBill(Long ownerUserId, String acctId, Long outboundFid) {
        if (acctId == null || outboundFid == null) {
            return List.of();
        }
        // 浏览口径：不过滤 invalid——失效行正是排查对象
        return lineMapper.selectList(new LambdaQueryWrapper<KdOutboundLinePo>()
                        .eq(ownerUserId != null, KdOutboundLinePo::getOwnerUserId, ownerUserId)
                        .eq(KdOutboundLinePo::getAcctId, acctId)
                        .eq(KdOutboundLinePo::getOutboundFid, outboundFid)
                        .orderByAsc(KdOutboundLinePo::getEntryId))
                .stream().map(converter::lineToDomain).toList();
    }

    @Override
    public List<KdOutbound> findBillsByFids(Long ownerUserId, Map<String, List<Long>> fidsByAcct) {
        if (fidsByAcct == null || fidsByAcct.isEmpty()) {
            return List.of();
        }
        List<KdOutbound> result = new ArrayList<>();
        // 按账套分组 + 分片：金蝶 FID 只在账套内唯一，只按 FID 过滤会跨账套串行
        for (Map.Entry<String, List<Long>> entry : fidsByAcct.entrySet()) {
            for (List<Long> chunk : ListUtil.partition(entry.getValue(), IN_CHUNK)) {
                outboundMapper.selectList(new LambdaQueryWrapper<KdOutboundPo>()
                                .eq(ownerUserId != null, KdOutboundPo::getOwnerUserId, ownerUserId)
                                .eq(KdOutboundPo::getAcctId, entry.getKey())
                                .in(KdOutboundPo::getKdFid, chunk))
                        .forEach(po -> result.add(converter.outboundToDomain(po)));
            }
        }
        return result;
    }

    @Override
    public Map<String, Integer> countLinesByBill(Long ownerUserId, Map<String, List<Long>> fidsByAcct) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (fidsByAcct == null || fidsByAcct.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, List<Long>> entry : fidsByAcct.entrySet()) {
            for (List<Long> chunk : ListUtil.partition(entry.getValue(), IN_CHUNK)) {
                QueryWrapper<KdOutboundLinePo> wrapper = new QueryWrapper<KdOutboundLinePo>()
                        .select("outbound_fid", "COUNT(*) AS line_count")
                        .eq(ownerUserId != null, "owner_user_id", ownerUserId)
                        .eq("acct_id", entry.getKey())
                        .in("outbound_fid", chunk)
                        .groupBy("outbound_fid");
                for (Map<String, Object> row : lineMapper.selectMaps(wrapper)) {
                    Object fid = row.get("outbound_fid");
                    Object count = row.get("line_count");
                    if (fid != null && count != null) {
                        result.put(billKey(entry.getKey(), Long.valueOf(fid.toString())),
                                Integer.valueOf(count.toString()));
                    }
                }
            }
        }
        return result;
    }

    /* ---------------- 内部方法 ---------------- */

    /** 通用分页结果组装 */
    private <T> PageResult<T> toPage(IPage<?> page, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    /** 单据关联键：账套 + FID（金蝶 FID 只在账套内唯一） */
    private static String billKey(String acctId, Long fid) {
        return (acctId == null ? "" : acctId) + "#" + (fid == null ? 0L : fid);
    }
}
