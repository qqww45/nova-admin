package com.example.admin.application.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.admin.application.service.KdSyncAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.ResultCode;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.common.IdName;
import com.example.admin.domain.model.kd.KdBatchQuery;
import com.example.admin.domain.model.kd.KdBatchStatus;
import com.example.admin.domain.model.kd.KdBinding;
import com.example.admin.domain.model.kd.KdClientIdentity;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdFieldMapping;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.model.kd.KdSyncBatch;
import com.example.admin.domain.model.kd.KdSyncMode;
import com.example.admin.domain.model.kd.KdSyncTask;
import com.example.admin.domain.model.kd.KdUpsertResult;
import com.example.admin.domain.port.KdClientContext;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.repository.KdCustomerRepository;
import com.example.admin.domain.repository.KdFieldMappingRepository;
import com.example.admin.domain.repository.KdOutboundLineRepository;
import com.example.admin.domain.repository.KdOutboundRepository;
import com.example.admin.domain.repository.KdSyncBatchRepository;
import com.example.admin.domain.repository.KdSyncTaskRepository;
import com.example.admin.interfaces.dto.req.KdBatchPageQuery;
import com.example.admin.interfaces.dto.req.KdCustomerSyncReq;
import com.example.admin.interfaces.dto.req.KdMappingUploadReq;
import com.example.admin.interfaces.dto.req.KdOutboundLineSyncReq;
import com.example.admin.interfaces.dto.req.KdOutboundSyncReq;
import com.example.admin.interfaces.dto.req.KdSyncBatchFinishReq;
import com.example.admin.interfaces.dto.req.KdSyncBatchStartReq;
import com.example.admin.interfaces.dto.resp.KdClientTaskResp;
import com.example.admin.interfaces.dto.resp.KdSyncBatchResp;
import com.example.admin.interfaces.dto.resp.KdSyncResultResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 采集器上行同步应用服务实现。
 *
 * <p>时序（方案 §3.2，顺序不能反）：
 * 客户端读本地水位 → 分页拉金蝶 → 写本地缓存 → 上报（本类落库并返回 200）→ 客户端才推进水位。
 * 服务端在这里做幂等 upsert + 水位只进不退，断电重跑安全。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KdSyncAppServiceImpl implements KdSyncAppService {

    /** 实体：客户 */
    public static final String ENTITY_CUSTOMER = "customer";

    /** 实体：出库单头 */
    public static final String ENTITY_OUTBOUND = "outbound";

    /** 实体：出库分录 */
    public static final String ENTITY_OUTBOUND_LINE = "outbound_line";

    /** 实体：整批 */
    public static final String ENTITY_ALL = "ALL";

    /** 字段映射键：出库分录字段集（与管理端配置页 / 文档约定一致） */
    public static final String MAPPING_KEY_LINE_FIELDS = "line_field_keys";

    /** 字段映射键：客户采购订单号字段 */
    public static final String MAPPING_KEY_CUSTOMER_PO = "customer_po_field";

    private final KdBindingRepository bindingRepository;
    private final KdSyncTaskRepository taskRepository;
    private final KdFieldMappingRepository mappingRepository;
    private final KdCustomerRepository customerRepository;
    private final KdOutboundRepository outboundRepository;
    private final KdOutboundLineRepository lineRepository;
    private final KdSyncBatchRepository batchRepository;
    private final BizUserRepository bizUserRepository;
    private final OperatorContext operatorContext;
    private final KdClientContext kdClientContext;

    @Override
    public List<KdClientTaskResp> clientTasks() {
        KdClientIdentity principal = kdClientContext.require();
        KdBinding binding = bindingRepository.findById(principal.bindingId());
        if (binding == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "绑定不存在，请重新绑定");
        }
        Map<String, String> mappings = new LinkedHashMap<>();
        for (KdFieldMapping mapping : mappingRepository.listByBinding(binding.getId())) {
            mappings.put(mapping.getMappingKey(), mapping.getMappingValue());
        }
        List<KdClientTaskResp> result = new ArrayList<>();
        for (KdSyncTask task : taskRepository.listEffective(binding.getAcctId())) {
            KdClientTaskResp resp = new KdClientTaskResp();
            resp.setEntity(task.getEntity());
            resp.setFormId(task.getFormId());
            resp.setEndpoint(task.getEndpoint());
            resp.setFieldKeys(task.getFieldKeys());
            resp.setWatermarkField(task.getWatermarkField());
            resp.setWatermark(watermarkOf(binding, task.getEntity()));
            resp.setMappings(mappings);
            result.add(resp);
        }
        return result;
    }

    @Override
    public void startBatch(KdSyncBatchStartReq req) {
        KdClientIdentity principal = kdClientContext.require();
        KdSyncBatch existing = batchRepository.findByBatchNo(req.getBatchId());
        if (existing != null) {
            // 客户端断网重试会再次调用：同批次号视为同一批次，不新建、不改归属
            if (!existing.getBindingId().equals(principal.bindingId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "批次号已被其它采集器占用");
            }
            log.debug("批次 {} 已存在，幂等返回", req.getBatchId());
            return;
        }
        KdSyncBatch batch = new KdSyncBatch();
        batch.setBatchNo(req.getBatchId());
        batch.setBindingId(principal.bindingId());
        batch.setOwnerUserId(principal.userId());
        batch.setAcctId(principal.acctId());
        batch.setEntity(StrUtil.isBlank(req.getEntity()) ? ENTITY_ALL : req.getEntity());
        batch.setMode(KdSyncMode.ofCode(req.getMode()).getCode());
        batch.setStatus(KdBatchStatus.RUNNING.getCode());
        batch.setRowCount(0);
        batch.setUpsertCount(0);
        batch.setSkippedCount(0);
        batch.setWatermarkFrom(req.getWatermarkFrom());
        batch.setClientVersion(req.getClientVersion());
        batch.setStartedAt(LocalDateTime.now());
        batchRepository.save(batch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KdSyncResultResp uploadCustomers(KdCustomerSyncReq req) {
        KdClientIdentity principal = kdClientContext.require();
        requireBatch(req.getBatchId(), principal.bindingId());

        List<KdCustomer> rows = new ArrayList<>(req.getRows().size());
        for (KdCustomerSyncReq.Row row : req.getRows()) {
            KdCustomer customer = new KdCustomer();
            customer.setKdFid(row.getKdFid());
            customer.setKdNumber(row.getNumber());
            customer.setCustomerName(row.getName());
            customer.setAddress(row.getAddress());
            customer.setDisabled(row.getDisabled() == null ? 0 : row.getDisabled());
            rows.add(customer);
        }
        KdUpsertResult result = customerRepository.upsertAll(
                principal.userId(), principal.acctId(), req.getBatchId(), rows);
        accumulateBatch(req.getBatchId(), result);
        LocalDateTime watermark = advanceWatermark(principal.bindingId(), ENTITY_CUSTOMER, req.getWatermarkTo());
        return toResult(result, watermark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KdSyncResultResp uploadOutbounds(KdOutboundSyncReq req) {
        KdClientIdentity principal = kdClientContext.require();
        requireBatch(req.getBatchId(), principal.bindingId());

        List<KdOutbound> rows = new ArrayList<>(req.getRows().size());
        for (KdOutboundSyncReq.Row row : req.getRows()) {
            KdOutbound outbound = new KdOutbound();
            outbound.setKdFid(row.getFid());
            outbound.setBillNo(row.getBillNo());
            outbound.setBizDate(row.getBizDate());
            outbound.setCustomerFid(row.getCustomerFid());
            outbound.setCustomerName(row.getCustomerName());
            outbound.setDocStatus(row.getDocStatus());
            outbound.setCancelStatus(row.getCancelStatus());
            outbound.setCancelled(KdOutbound.cancelledOf(row.getCancelStatus()));
            outbound.setAmountCents(row.getAmountCents() == null ? 0L : row.getAmountCents());
            outbound.setRemark(row.getRemark());
            outbound.setKdModifyTime(row.getModifyTime());
            outbound.setInvalid(0);
            rows.add(outbound);
        }
        KdUpsertResult result = outboundRepository.upsertAll(
                principal.userId(), principal.acctId(), req.getBatchId(), rows);
        accumulateBatch(req.getBatchId(), result);
        LocalDateTime watermark = advanceWatermark(principal.bindingId(), ENTITY_OUTBOUND, req.getWatermarkTo());
        return toResult(result, watermark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KdSyncResultResp uploadLines(KdOutboundLineSyncReq req) {
        KdClientIdentity principal = kdClientContext.require();
        requireBatch(req.getBatchId(), principal.bindingId());

        List<KdOutboundLine> rows = new ArrayList<>(req.getRows().size());
        // 缺内码时按「单据 + 单内行序号」生成兜底键；序号按单据各自累加，
        // 不能用请求数组下标（否则第二块上报会从 0 重来，覆盖其它行）
        Map<Long, Integer> seqByBill = new java.util.HashMap<>();
        for (KdOutboundLineSyncReq.Row row : req.getRows()) {
            long outboundFid = row.getOutboundFid() == null ? 0L : row.getOutboundFid();
            int seq = seqByBill.merge(outboundFid, 1, Integer::sum) - 1;
            KdOutboundLine line = new KdOutboundLine();
            line.setOutboundFid(row.getOutboundFid());
            line.setEntryId(KdOutboundLine.resolveEntryId(row.getEntryId(), outboundFid, seq));
            line.setMaterialNumber(row.getMaterialNumber());
            line.setMaterialName(row.getMaterialName());
            line.setSpec(row.getSpec());
            line.setUnit(row.getUnit());
            line.setQtyMilli(row.getQtyMilli() == null ? 0L : row.getQtyMilli());
            line.setPriceCents(row.getPriceCents() == null ? 0L : row.getPriceCents());
            line.setAmountCents(row.getAmountCents() == null ? 0L : row.getAmountCents());
            line.setSrcOrderNo(row.getSrcOrderNo());
            line.setCustomerPo(row.getCustomerPo());
            line.setRemark(row.getRemark());
            line.setInvalid(0);
            rows.add(line);
        }
        KdUpsertResult result = lineRepository.upsertAll(
                principal.userId(), principal.acctId(), req.getBatchId(), rows);
        accumulateBatch(req.getBatchId(), result);
        // 表头金额以分录为准：客户端上报的头金额可能为空或未税
        outboundRepository.recalculateAmountByBatch(principal.userId(), principal.acctId(), req.getBatchId());
        LocalDateTime watermark = advanceWatermark(principal.bindingId(), ENTITY_OUTBOUND_LINE, req.getWatermarkTo());
        return toResult(result, watermark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishBatch(String batchNo, KdSyncBatchFinishReq req) {
        KdClientIdentity principal = kdClientContext.require();
        KdSyncBatch batch = requireBatch(batchNo, principal.bindingId());
        String status = req.getStatus();
        boolean success = KdBatchStatus.SUCCESS.getCode().equalsIgnoreCase(status);

        KdSyncBatch update = new KdSyncBatch();
        update.setId(batch.getId());
        update.setStatus(success ? KdBatchStatus.SUCCESS.getCode() : KdBatchStatus.FAILED.getCode());
        update.setRowCount(req.getRowCount() == null ? batch.getRowCount() : req.getRowCount());
        update.setUpsertCount(batch.getUpsertCount());
        update.setSkippedCount(req.getSkippedCount() == null ? batch.getSkippedCount() : req.getSkippedCount());
        update.setWatermarkTo(req.getWatermarkTo());
        update.setStartedAt(req.getStartedAt() == null ? batch.getStartedAt() : req.getStartedAt());
        update.setFinishedAt(req.getFinishedAt() == null ? LocalDateTime.now() : req.getFinishedAt());
        update.setCostMs(req.getCostMs());
        update.setMessage(StrUtil.maxLength(req.getMessage(), 900));
        batchRepository.update(update);

        if (!success) {
            log.warn("批次 {} 失败：{}", batchNo, req.getMessage());
            return;
        }

        // 全量模式：本批次没见到的单据/分录标记失效（金蝶侧物理删除兜底），不做物理删除
        if (KdSyncMode.FULL.getCode().equalsIgnoreCase(batch.getMode())) {
            int invalidOutbounds = outboundRepository.markInvalidNotInBatch(
                    principal.userId(), principal.acctId(), batchNo);
            int invalidLines = lineRepository.markInvalidNotInBatch(
                    principal.userId(), principal.acctId(), batchNo);
            log.info("批次 {} 全量兜底：失效单据 {} 条、分录 {} 条", batchNo, invalidOutbounds, invalidLines);
        }

        KdBinding bindingUpdate = new KdBinding();
        bindingUpdate.setId(principal.bindingId());
        bindingUpdate.setLastSyncAt(LocalDateTime.now());
        bindingUpdate.setLastSyncResult(StrUtil.maxLength(
                StrUtil.blankToDefault(req.getMessage(), "同步成功"), 400));
        bindingRepository.update(bindingUpdate);
    }

    /**
     * 采集器字段自检结果回传。
     *
     * <p>写到本绑定（采集器令牌即身份，无需也不能指定别人的绑定）的
     * {@code kd_field_mapping}，key 与管理端配置页约定一致；运维在网页确认后
     * 通过 {@code config.reload} 让全部采集器生效。
     */
    @Override
    public void uploadMapping(KdMappingUploadReq req) {
        KdClientIdentity principal = kdClientContext.require();
        Long bindingId = principal.bindingId();

        upsertMapping(bindingId, MAPPING_KEY_LINE_FIELDS, req.getLineFieldKeys(), req.getNote());
        upsertMapping(bindingId, MAPPING_KEY_CUSTOMER_PO, req.getCustomerPoField(), req.getNote());
        log.info("采集器回传字段映射：bindingId={}, lineFieldKeys={}, customerPoField={}",
                bindingId, StrUtil.maxLength(req.getLineFieldKeys(), 80), req.getCustomerPoField());
    }

    /**
     * 幂等写一条映射：不存在则新增，存在则覆盖值与备注。
     *
     * @param bindingId 绑定 ID
     * @param key 映射键
     * @param value 值；空值跳过（只回传了部分字段时不动其余键）
     * @param note 备注
     */
    private void upsertMapping(Long bindingId, String key, String value, String note) {
        String trimmed = StrUtil.trim(value);
        if (StrUtil.isBlank(trimmed)) {
            return;
        }
        KdFieldMapping mapping = mappingRepository.find(bindingId, key);
        if (mapping == null) {
            mapping = new KdFieldMapping();
            mapping.setBindingId(bindingId);
            mapping.setMappingKey(key);
            mapping.setMappingValue(trimmed);
            mapping.setRemark(StrUtil.blankToDefault(note, "采集器字段自检回传"));
            mappingRepository.save(mapping);
            return;
        }
        mapping.setMappingValue(trimmed);
        mapping.setRemark(StrUtil.blankToDefault(note, "采集器字段自检回传"));
        mappingRepository.update(mapping);
    }

    @Override
    public PageResult<KdSyncBatchResp> batchPage(KdBatchPageQuery query) {
        LoginUser loginUser = operatorContext.current();
        KdBatchQuery domainQuery = new KdBatchQuery();
        domainQuery.setPageNum(query.getPageNum());
        domainQuery.setPageSize(query.getPageSize());
        domainQuery.setEntity(query.getEntity());
        domainQuery.setStatus(query.getStatus());
        domainQuery.setAcctId(query.getAcctId());
        domainQuery.setKeyword(query.getKeyword());
        domainQuery.setOwnerUserId(isAdmin(loginUser) ? null : operatorContext.currentUserId());
        PageResult<KdSyncBatch> page = batchRepository.page(domainQuery);

        // 批次归属人姓名：管理员跨业务员排查时辨认"这是谁同步的"
        Map<Long, String> ownerNames = page.getRecords().isEmpty()
                ? Map.of()
                : bizUserRepository.findNames(page.getRecords().stream()
                        .map(KdSyncBatch::getOwnerUserId).filter(Objects::nonNull)
                        .distinct().toList())
                .stream().collect(Collectors.toMap(IdName::id, IdName::name, (a, b) -> a));

        PageResult<KdSyncBatchResp> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        result.setRecords(page.getRecords().stream()
                .map(batch -> toBatchResp(batch, ownerNames)).toList());
        return result;
    }

    /* ---------------- 内部方法 ---------------- */

    /**
     * 校验批次存在且属于当前采集器。
     *
     * @param batchNo 批次号
     * @param bindingId 绑定 ID
     * @return 批次
     */
    private KdSyncBatch requireBatch(String batchNo, Long bindingId) {
        KdSyncBatch batch = batchRepository.findByBatchNo(batchNo);
        if (batch == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "批次不存在，请先调用 /kd/sync/batch");
        }
        if (!batch.getBindingId().equals(bindingId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权向该批次上报数据");
        }
        return batch;
    }

    /**
     * 累加批次计数（每次上报后回写，失败也能看到进度）。
     *
     * @param batchNo 批次号
     * @param result 本次结果
     */
    private void accumulateBatch(String batchNo, KdUpsertResult result) {
        KdSyncBatch batch = batchRepository.findByBatchNo(batchNo);
        if (batch == null) {
            return;
        }
        KdSyncBatch update = new KdSyncBatch();
        update.setId(batch.getId());
        update.setRowCount((batch.getRowCount() == null ? 0 : batch.getRowCount()) + result.received());
        update.setUpsertCount((batch.getUpsertCount() == null ? 0 : batch.getUpsertCount()) + result.upserted());
        update.setSkippedCount((batch.getSkippedCount() == null ? 0 : batch.getSkippedCount()) + result.skipped());
        batchRepository.update(update);
    }

    /**
     * 推进水位（只进不退）。
     *
     * @param bindingId 绑定 ID
     * @param entity 实体
     * @param incoming 本次水位
     * @return 推进后的水位
     */
    private LocalDateTime advanceWatermark(Long bindingId, String entity, LocalDateTime incoming) {
        KdBinding binding = bindingRepository.findById(bindingId);
        if (binding == null) {
            return incoming;
        }
        boolean customer = ENTITY_CUSTOMER.equals(entity);
        LocalDateTime current = customer ? binding.getWatermarkCustomer() : binding.getWatermarkOutbound();
        LocalDateTime next = KdBinding.maxWatermark(current, incoming);
        if (next != null && !next.equals(current)) {
            KdBinding update = new KdBinding();
            update.setId(bindingId);
            if (customer) {
                update.setWatermarkCustomer(next);
            } else {
                update.setWatermarkOutbound(next);
            }
            bindingRepository.update(update);
        }
        return next;
    }

    /**
     * 取某个实体的当前水位。
     *
     * @param binding 绑定
     * @param entity 实体
     * @return 水位
     */
    private LocalDateTime watermarkOf(KdBinding binding, String entity) {
        return ENTITY_CUSTOMER.equals(entity) ? binding.getWatermarkCustomer() : binding.getWatermarkOutbound();
    }

    /**
     * 组装上报结果。
     *
     * @param result 写入结果
     * @param watermark 当前水位
     * @return 响应
     */
    private KdSyncResultResp toResult(KdUpsertResult result, LocalDateTime watermark) {
        KdSyncResultResp resp = new KdSyncResultResp();
        resp.setReceived(result.received());
        resp.setUpserted(result.upserted());
        resp.setSkipped(result.skipped());
        resp.setWatermark(watermark);
        return resp;
    }

    /**
     * 批次领域模型 → 响应。
     *
     * @param batch 批次
     * @param ownerNames 归属人 ID → 姓名
     * @return 响应
     */
    private KdSyncBatchResp toBatchResp(KdSyncBatch batch, Map<Long, String> ownerNames) {
        KdSyncBatchResp resp = new KdSyncBatchResp();
        resp.setId(str(batch.getId()));
        resp.setBatchNo(batch.getBatchNo());
        resp.setBindingId(str(batch.getBindingId()));
        resp.setOwnerUserId(str(batch.getOwnerUserId()));
        resp.setOwnerUserName(ownerNames.get(batch.getOwnerUserId()));
        resp.setAcctId(batch.getAcctId());
        resp.setEntity(batch.getEntity());
        resp.setMode(batch.getMode());
        resp.setStatus(batch.getStatus());
        resp.setRowCount(batch.getRowCount());
        resp.setUpsertCount(batch.getUpsertCount());
        resp.setSkippedCount(batch.getSkippedCount());
        resp.setWatermarkFrom(batch.getWatermarkFrom());
        resp.setWatermarkTo(batch.getWatermarkTo());
        resp.setStartedAt(batch.getStartedAt());
        resp.setFinishedAt(batch.getFinishedAt());
        resp.setCostMs(batch.getCostMs());
        resp.setClientVersion(batch.getClientVersion());
        resp.setMessage(batch.getMessage());
        resp.setCreateTime(batch.getCreateTime());
        return resp;
    }

    /**
     * Long → String（前端 ID 精度保护）。
     *
     * @param value 值
     * @return 字符串
     */
    private String str(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 是否超级管理员。
     *
     * @param loginUser 登录用户
     * @return 是否管理员
     */
    private boolean isAdmin(LoginUser loginUser) {
        return loginUser != null && loginUser.getRoles() != null
                && loginUser.getRoles().contains(Constants.SUPER_ADMIN_ROLE_KEY);
    }
}
