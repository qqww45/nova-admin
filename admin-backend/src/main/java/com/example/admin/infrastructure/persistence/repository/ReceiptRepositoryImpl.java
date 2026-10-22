package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.receipt.Receipt;
import com.example.admin.domain.model.receipt.ReceiptQuery;
import com.example.admin.domain.model.receipt.ReceiptStatus;
import com.example.admin.domain.repository.ReceiptRepository;
import com.example.admin.infrastructure.persistence.converter.BizSupportConverter;
import com.example.admin.infrastructure.persistence.mapper.BizReceiptMapper;
import com.example.admin.infrastructure.persistence.po.BizReceipt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收款登记仓储实现。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class ReceiptRepositoryImpl implements ReceiptRepository {

    private final BizReceiptMapper receiptMapper;
    private final BizSupportConverter converter;

    @Override
    public PageResult<Receipt> page(ReceiptQuery query) {
        LambdaQueryWrapper<BizReceipt> wrapper = new LambdaQueryWrapper<BizReceipt>()
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(BizReceipt::getReceiptNo, query.getKeyword())
                        .or().like(BizReceipt::getInvoiceNo, query.getKeyword()))
                .eq(query.getCustomerId() != null, BizReceipt::getCustomerId, query.getCustomerId())
                .eq(StrUtil.isNotBlank(query.getStatus()), BizReceipt::getStatus, query.getStatus())
                .ge(query.getBeginDate() != null, BizReceipt::getReceiptDate, query.getBeginDate())
                .le(query.getEndDate() != null, BizReceipt::getReceiptDate, query.getEndDate())
                .orderByDesc(BizReceipt::getReceiptDate)
                .orderByDesc(BizReceipt::getId);
        IPage<BizReceipt> page = receiptMapper.selectPage(query.buildPage(), wrapper);
        return toPageResult(page, page.getRecords().stream().map(converter::receiptToDomain).toList());
    }

    @Override
    public Receipt findById(Long id) {
        return converter.receiptToDomain(receiptMapper.selectById(id));
    }

    @Override
    public Long save(Receipt receipt) {
        BizReceipt po = converter.receiptToPo(receipt);
        receiptMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(Receipt receipt) {
        receiptMapper.updateById(converter.receiptToPo(receipt));
    }

    @Override
    public Receipt findByReceiptNo(String receiptNo) {
        if (StrUtil.isBlank(receiptNo)) {
            return null;
        }
        List<BizReceipt> list = receiptMapper.selectList(
                new LambdaQueryWrapper<BizReceipt>().eq(BizReceipt::getReceiptNo, receiptNo).last("LIMIT 1"));
        return list.isEmpty() ? null : converter.receiptToDomain(list.get(0));
    }

    @Override
    public List<Receipt> listByCustomerAndPeriod(Long customerId, LocalDateTime start, LocalDateTime end) {
        if (customerId == null) {
            return List.of();
        }
        LambdaQueryWrapper<BizReceipt> wrapper = new LambdaQueryWrapper<BizReceipt>()
                .eq(BizReceipt::getCustomerId, customerId)
                .ge(start != null, BizReceipt::getReceiptDate, start)
                .le(end != null, BizReceipt::getReceiptDate, end)
                .ne(BizReceipt::getStatus, ReceiptStatus.VOID.getCode())
                .orderByAsc(BizReceipt::getReceiptDate);
        return receiptMapper.selectList(wrapper).stream().map(converter::receiptToDomain).toList();
    }

    private <T> PageResult<T> toPageResult(IPage<?> page, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}
