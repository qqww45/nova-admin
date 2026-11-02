package com.example.admin.infrastructure.persistence.converter;

import cn.hutool.core.bean.BeanUtil;
import com.example.admin.domain.model.kd.KdBinding;
import com.example.admin.domain.model.kd.KdBindingCode;
import com.example.admin.domain.model.kd.KdCustomer;
import com.example.admin.domain.model.kd.KdFieldMapping;
import com.example.admin.domain.model.kd.KdOutbound;
import com.example.admin.domain.model.kd.KdOutboundLine;
import com.example.admin.domain.model.kd.KdSyncBatch;
import com.example.admin.domain.model.kd.KdSyncTask;
import com.example.admin.infrastructure.persistence.po.KdBindingCodePo;
import com.example.admin.infrastructure.persistence.po.KdBindingPo;
import com.example.admin.infrastructure.persistence.po.KdCustomerPo;
import com.example.admin.infrastructure.persistence.po.KdFieldMappingPo;
import com.example.admin.infrastructure.persistence.po.KdOutboundLinePo;
import com.example.admin.infrastructure.persistence.po.KdOutboundPo;
import com.example.admin.infrastructure.persistence.po.KdSyncBatchPo;
import com.example.admin.infrastructure.persistence.po.KdSyncTaskPo;
import org.springframework.stereotype.Component;

/**
 * 金蝶同步域 PO ⇄ 领域模型转换器。
 *
 * <p>字段名保持一致，用 BeanUtil 浅拷贝；只做转换，不做业务判断。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Component
public class KdConverter {

    /**
     * PO → 绑定。
     *
     * @param po PO
     * @return 领域模型
     */
    public KdBinding bindingToDomain(KdBindingPo po) {
        return po == null ? null : BeanUtil.copyProperties(po, KdBinding.class);
    }

    /**
     * 绑定 → PO。
     *
     * @param domain 领域模型
     * @return PO
     */
    public KdBindingPo bindingToPo(KdBinding domain) {
        return domain == null ? null : BeanUtil.copyProperties(domain, KdBindingPo.class);
    }

    /**
     * PO → 绑定码。
     *
     * @param po PO
     * @return 领域模型
     */
    public KdBindingCode codeToDomain(KdBindingCodePo po) {
        return po == null ? null : BeanUtil.copyProperties(po, KdBindingCode.class);
    }

    /**
     * 绑定码 → PO。
     *
     * @param domain 领域模型
     * @return PO
     */
    public KdBindingCodePo codeToPo(KdBindingCode domain) {
        return domain == null ? null : BeanUtil.copyProperties(domain, KdBindingCodePo.class);
    }

    /**
     * PO → 任务。
     *
     * @param po PO
     * @return 领域模型
     */
    public KdSyncTask taskToDomain(KdSyncTaskPo po) {
        return po == null ? null : BeanUtil.copyProperties(po, KdSyncTask.class);
    }

    /**
     * 任务 → PO。
     *
     * @param domain 领域模型
     * @return PO
     */
    public KdSyncTaskPo taskToPo(KdSyncTask domain) {
        return domain == null ? null : BeanUtil.copyProperties(domain, KdSyncTaskPo.class);
    }

    /**
     * PO → 字段映射。
     *
     * @param po PO
     * @return 领域模型
     */
    public KdFieldMapping mappingToDomain(KdFieldMappingPo po) {
        return po == null ? null : BeanUtil.copyProperties(po, KdFieldMapping.class);
    }

    /**
     * 字段映射 → PO。
     *
     * @param domain 领域模型
     * @return PO
     */
    public KdFieldMappingPo mappingToPo(KdFieldMapping domain) {
        return domain == null ? null : BeanUtil.copyProperties(domain, KdFieldMappingPo.class);
    }

    /**
     * PO → 客户。
     *
     * @param po PO
     * @return 领域模型
     */
    public KdCustomer customerToDomain(KdCustomerPo po) {
        return po == null ? null : BeanUtil.copyProperties(po, KdCustomer.class);
    }

    /**
     * 客户 → PO。
     *
     * @param domain 领域模型
     * @return PO
     */
    public KdCustomerPo customerToPo(KdCustomer domain) {
        return domain == null ? null : BeanUtil.copyProperties(domain, KdCustomerPo.class);
    }

    /**
     * PO → 出库单头。
     *
     * @param po PO
     * @return 领域模型
     */
    public KdOutbound outboundToDomain(KdOutboundPo po) {
        return po == null ? null : BeanUtil.copyProperties(po, KdOutbound.class);
    }

    /**
     * 出库单头 → PO。
     *
     * @param domain 领域模型
     * @return PO
     */
    public KdOutboundPo outboundToPo(KdOutbound domain) {
        return domain == null ? null : BeanUtil.copyProperties(domain, KdOutboundPo.class);
    }

    /**
     * PO → 分录。
     *
     * @param po PO
     * @return 领域模型
     */
    public KdOutboundLine lineToDomain(KdOutboundLinePo po) {
        return po == null ? null : BeanUtil.copyProperties(po, KdOutboundLine.class);
    }

    /**
     * 分录 → PO。
     *
     * @param domain 领域模型
     * @return PO
     */
    public KdOutboundLinePo lineToPo(KdOutboundLine domain) {
        return domain == null ? null : BeanUtil.copyProperties(domain, KdOutboundLinePo.class);
    }

    /**
     * PO → 批次。
     *
     * @param po PO
     * @return 领域模型
     */
    public KdSyncBatch batchToDomain(KdSyncBatchPo po) {
        return po == null ? null : BeanUtil.copyProperties(po, KdSyncBatch.class);
    }

    /**
     * 批次 → PO。
     *
     * @param domain 领域模型
     * @return PO
     */
    public KdSyncBatchPo batchToPo(KdSyncBatch domain) {
        return domain == null ? null : BeanUtil.copyProperties(domain, KdSyncBatchPo.class);
    }
}
