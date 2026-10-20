package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admin.common.result.PageResult;
import com.example.admin.domain.model.dict.DictItem;
import com.example.admin.domain.model.imports.ImportBatch;
import com.example.admin.domain.model.imports.ImportType;
import com.example.admin.domain.model.log.OperationLog;
import com.example.admin.domain.model.log.OperationLogQuery;
import com.example.admin.domain.model.mapping.ImportMapping;
import com.example.admin.domain.repository.DictRepository;
import com.example.admin.domain.repository.ImportBatchRepository;
import com.example.admin.domain.repository.ImportMappingRepository;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.OperationLogRepository;
import com.example.admin.infrastructure.persistence.converter.BizSupportConverter;
import com.example.admin.infrastructure.persistence.mapper.BizDictItemMapper;
import com.example.admin.infrastructure.persistence.mapper.BizImportBatchMapper;
import com.example.admin.infrastructure.persistence.mapper.BizImportMappingMapper;
import com.example.admin.infrastructure.persistence.mapper.BizOperationLogMapper;
import com.example.admin.infrastructure.persistence.po.BizDictItem;
import com.example.admin.infrastructure.persistence.po.BizImportBatch;
import com.example.admin.infrastructure.persistence.po.BizImportMapping;
import com.example.admin.infrastructure.persistence.po.BizOperationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 字典 / 字段映射 / 导入批次 / 操作日志 的仓储实现。
 *
 * <p>这四个都是"配置与痕迹"类数据，读写模式一致（按编码或模块查、按时间倒序），
 * 合并实现比拆成四个类更少重复代码。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class BizSupportRepositoryImpl implements DictRepository, ImportMappingRepository,
        ImportBatchRepository, OperationLogRepository {

    private final BizDictItemMapper dictMapper;
    private final BizImportMappingMapper mappingMapper;
    private final BizImportBatchMapper batchMapper;
    private final BizOperationLogMapper logMapper;
    private final BizSupportConverter converter;
    /** 用于落库时补齐操作人姓名 */
    private final BizUserRepository userRepository;

    /* ---------------- 字典 ---------------- */

    @Override
    public List<DictItem> listByCode(String dictCode) {
        if (StrUtil.isBlank(dictCode)) {
            return List.of();
        }
        return dictMapper.selectList(new LambdaQueryWrapper<BizDictItem>()
                        .eq(BizDictItem::getDictCode, dictCode)
                        .eq(BizDictItem::getStatus, 1)
                        .orderByAsc(BizDictItem::getSort))
                .stream().map(converter::dictToDomain).toList();
    }

    @Override
    public List<DictItem> listAll() {
        return dictMapper.selectList(new LambdaQueryWrapper<BizDictItem>()
                        .eq(BizDictItem::getStatus, 1)
                        .orderByAsc(BizDictItem::getDictCode)
                        .orderByAsc(BizDictItem::getSort))
                .stream().map(converter::dictToDomain).toList();
    }

    /* ---------------- 字段映射 ---------------- */

    @Override
    public List<ImportMapping> listByModule(String module) {
        if (StrUtil.isBlank(module)) {
            return List.of();
        }
        return mappingMapper.selectList(new LambdaQueryWrapper<BizImportMapping>()
                        .eq(BizImportMapping::getModule, module)
                        .orderByAsc(BizImportMapping::getColumnOrder))
                .stream().map(converter::mappingToDomain).toList();
    }

    /* ---------------- 导入批次 ---------------- */

    @Override
    public Long save(ImportBatch batch) {
        BizImportBatch po = converter.batchToPo(batch);
        batchMapper.insert(po);
        return po.getId();
    }

    @Override
    public void update(ImportBatch batch) {
        batchMapper.updateById(converter.batchToPo(batch));
    }

    @Override
    public ImportBatch findById(Long id) {
        return converter.batchToDomain(batchMapper.selectById(id));
    }

    @Override
    public List<ImportBatch> list(ImportType type, int limit) {
        Page<BizImportBatch> page = new Page<>(1, Math.max(1, limit));
        IPage<BizImportBatch> result = batchMapper.selectPage(page, new LambdaQueryWrapper<BizImportBatch>()
                .eq(type != null, BizImportBatch::getBatchType, type == null ? null : type.getCode())
                .orderByDesc(BizImportBatch::getCreateTime));
        return result.getRecords().stream().map(converter::batchToDomain).toList();
    }

    @Override
    public PageResult<ImportBatch> page(ImportType type, int pageNum, int pageSize) {
        IPage<BizImportBatch> result = batchMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<BizImportBatch>()
                        .eq(type != null, BizImportBatch::getBatchType, type == null ? null : type.getCode())
                        .orderByDesc(BizImportBatch::getCreateTime));
        PageResult<ImportBatch> pageResult = new PageResult<>();
        pageResult.setRecords(new ArrayList<>(result.getRecords().stream().map(converter::batchToDomain).toList()));
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    /* ---------------- 操作日志 ---------------- */

    @Override
    public void save(OperationLog log) {
        // 审计页面上只看 operator_id 是一串雪花数字，没有意义。
        // 姓名在落库时补全并快照：员工离职后用户记录会被停用，但历史日志仍要显示当时的名字。
        if (StrUtil.isBlank(log.getOperatorName()) && log.getOperatorId() != null) {
            userRepository.findNames(List.of(log.getOperatorId())).stream()
                    .findFirst()
                    .ifPresent(user -> log.setOperatorName(user.name()));
        }
        logMapper.insert(converter.logToPo(log));
    }

    @Override
    public List<OperationLog> listByBiz(String module, String bizId, int limit) {
        if (StrUtil.isBlank(bizId)) {
            return List.of();
        }
        Page<BizOperationLog> page = new Page<>(1, Math.max(1, limit));
        IPage<BizOperationLog> result = logMapper.selectPage(page, new LambdaQueryWrapper<BizOperationLog>()
                .eq(StrUtil.isNotBlank(module), BizOperationLog::getModule, module)
                .eq(BizOperationLog::getBizId, bizId)
                .orderByDesc(BizOperationLog::getOperateTime));
        return result.getRecords().stream().map(converter::logToDomain).toList();
    }

    @Override
    public PageResult<OperationLog> page(OperationLogQuery query) {
        LambdaQueryWrapper<BizOperationLog> wrapper = new LambdaQueryWrapper<BizOperationLog>()
                .eq(StrUtil.isNotBlank(query.getModule()), BizOperationLog::getModule, query.getModule())
                .eq(StrUtil.isNotBlank(query.getAction()), BizOperationLog::getAction, query.getAction())
                .eq(query.getOperatorId() != null, BizOperationLog::getOperatorId, query.getOperatorId())
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(BizOperationLog::getBizId, query.getKeyword())
                        .or().like(BizOperationLog::getReason, query.getKeyword())
                        .or().like(BizOperationLog::getOperatorName, query.getKeyword()))
                .ge(query.getBeginTime() != null, BizOperationLog::getOperateTime, query.getBeginTime())
                .le(query.getEndTime() != null, BizOperationLog::getOperateTime, query.getEndTime())
                .orderByDesc(BizOperationLog::getOperateTime);
        IPage<BizOperationLog> result = logMapper.selectPage(query.buildPage(), wrapper);
        PageResult<OperationLog> pageResult = new PageResult<>();
        pageResult.setRecords(new ArrayList<>(result.getRecords().stream().map(converter::logToDomain).toList()));
        pageResult.setTotal(result.getTotal());
        pageResult.setPageNum(result.getCurrent());
        pageResult.setPageSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }
}
