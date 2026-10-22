package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.reconciliation.Statement;
import com.example.admin.domain.model.reconciliation.StatementLine;
import com.example.admin.domain.model.reconciliation.StatementQuery;
import com.example.admin.domain.repository.StatementRepository;
import com.example.admin.infrastructure.persistence.converter.BizSupportConverter;
import com.example.admin.infrastructure.persistence.mapper.BizStatementLineMapper;
import com.example.admin.infrastructure.persistence.mapper.BizStatementMapper;
import com.example.admin.infrastructure.persistence.po.BizStatement;
import com.example.admin.infrastructure.persistence.po.BizStatementLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对账单仓储实现。
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class StatementRepositoryImpl implements StatementRepository {

    private final BizStatementMapper statementMapper;
    private final BizStatementLineMapper lineMapper;
    private final BizSupportConverter converter;

    @Override
    public PageResult<Statement> page(StatementQuery query) {
        LambdaQueryWrapper<BizStatement> wrapper = new LambdaQueryWrapper<BizStatement>()
                .like(StrUtil.isNotBlank(query.getKeyword()), BizStatement::getStmtNo, query.getKeyword())
                .eq(query.getCustomerId() != null, BizStatement::getCustomerId, query.getCustomerId())
                .eq(StrUtil.isNotBlank(query.getStatus()), BizStatement::getStatus, query.getStatus())
                .ge(query.getBeginDate() != null, BizStatement::getEndDate, query.getBeginDate())
                .le(query.getEndDate() != null, BizStatement::getEndDate, query.getEndDate())
                .orderByDesc(BizStatement::getEndDate)
                .orderByDesc(BizStatement::getId);
        IPage<BizStatement> page = statementMapper.selectPage(query.buildPage(), wrapper);
        return toPageResult(page, page.getRecords().stream().map(converter::statementToDomain).toList());
    }

    @Override
    public Statement findById(Long id) {
        return converter.statementToDomain(statementMapper.selectById(id));
    }

    @Override
    public List<StatementLine> findLines(Long statementId) {
        if (statementId == null) {
            return List.of();
        }
        return lineMapper.selectList(new LambdaQueryWrapper<BizStatementLine>()
                        .eq(BizStatementLine::getStatementId, statementId)
                        .orderByAsc(BizStatementLine::getSortNo))
                .stream().map(converter::statementLineToDomain).toList();
    }

    @Override
    public Long save(Statement statement) {
        BizStatement po = converter.statementToPo(statement);
        statementMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(Statement statement) {
        statementMapper.updateById(converter.statementToPo(statement));
    }

    @Override
    public void saveLine(StatementLine line) {
        lineMapper.insert(converter.statementLineToPo(line));
    }

    @Override
    public void deleteLines(Long statementId) {
        if (statementId == null) {
            return;
        }
        lineMapper.delete(new LambdaQueryWrapper<BizStatementLine>().eq(BizStatementLine::getStatementId, statementId));
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
