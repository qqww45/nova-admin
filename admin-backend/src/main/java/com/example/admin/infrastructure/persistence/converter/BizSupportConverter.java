package com.example.admin.infrastructure.persistence.converter;

import cn.hutool.core.bean.BeanUtil;
import com.example.admin.domain.model.dict.DictItem;
import com.example.admin.domain.model.imports.BatchStatus;
import com.example.admin.domain.model.imports.IdempotentStrategy;
import com.example.admin.domain.model.imports.ImportBatch;
import com.example.admin.domain.model.imports.ImportType;
import com.example.admin.domain.model.log.OperationLog;
import com.example.admin.domain.model.mapping.ImportMapping;
import com.example.admin.domain.model.receipt.Receipt;
import com.example.admin.domain.model.receipt.ReceiptStatus;
import com.example.admin.domain.model.reconciliation.CustomerOpening;
import com.example.admin.domain.model.reconciliation.Statement;
import com.example.admin.domain.model.reconciliation.StatementLine;
import com.example.admin.domain.model.reconciliation.StatementLineType;
import com.example.admin.domain.model.reconciliation.StatementStatus;
import com.example.admin.domain.model.shipment.Shipment;
import com.example.admin.domain.model.shipment.ShipmentSource;
import com.example.admin.domain.model.shipment.ShipmentStatus;
import com.example.admin.domain.model.shipment.ShipmentType;
import com.example.admin.infrastructure.persistence.po.BizCustomerOpening;
import com.example.admin.infrastructure.persistence.po.BizDictItem;
import com.example.admin.infrastructure.persistence.po.BizImportBatch;
import com.example.admin.infrastructure.persistence.po.BizImportMapping;
import com.example.admin.infrastructure.persistence.po.BizOperationLog;
import com.example.admin.infrastructure.persistence.po.BizReceipt;
import com.example.admin.infrastructure.persistence.po.BizShipment;
import com.example.admin.infrastructure.persistence.po.BizStatement;
import com.example.admin.infrastructure.persistence.po.BizStatementLine;
import org.springframework.stereotype.Component;

/**
 * 出货 / 字典 / 导入批次 / 操作日志 / 字段映射 的 PO ⇄ 领域模型转换器。
 *
 * <p>这几个对象都是扁平结构，合并到一个转换器里比拆成五个空壳类更好维护。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class BizSupportConverter {

    /**
     * 出货 PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public Shipment shipmentToDomain(BizShipment po) {
        if (po == null) {
            return null;
        }
        Shipment shipment = new Shipment();
        BeanUtil.copyProperties(po, shipment);
        shipment.setStatus(ShipmentStatus.ofCode(po.getStatus()));
        shipment.setSource(ShipmentSource.ofCode(po.getSource()));
        shipment.setShipmentType(ShipmentType.ofCode(po.getShipmentType()));
        return shipment;
    }

    /**
     * 出货领域模型 → PO。
     *
     * @param domain 领域模型
     * @return 持久化对象
     */
    public BizShipment shipmentToPo(Shipment domain) {
        if (domain == null) {
            return null;
        }
        BizShipment po = new BizShipment();
        BeanUtil.copyProperties(domain, po);
        po.setStatus(domain.getStatus() == null ? ShipmentStatus.NORMAL.getCode() : domain.getStatus().getCode());
        po.setSource(domain.getSource() == null ? ShipmentSource.MANUAL.getCode() : domain.getSource().getCode());
        po.setShipmentType(domain.getShipmentType() == null ? ShipmentType.SHIP.getCode() : domain.getShipmentType().getCode());
        return po;
    }

    /**
     * 字典 PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public DictItem dictToDomain(BizDictItem po) {
        if (po == null) {
            return null;
        }
        DictItem item = new DictItem();
        BeanUtil.copyProperties(po, item);
        return item;
    }

    /**
     * 字段映射 PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public ImportMapping mappingToDomain(BizImportMapping po) {
        if (po == null) {
            return null;
        }
        ImportMapping mapping = new ImportMapping();
        BeanUtil.copyProperties(po, mapping);
        return mapping;
    }

    /**
     * 批次 PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public ImportBatch batchToDomain(BizImportBatch po) {
        if (po == null) {
            return null;
        }
        ImportBatch batch = new ImportBatch();
        BeanUtil.copyProperties(po, batch);
        batch.setBatchType(ImportType.ofCode(po.getBatchType()));
        batch.setIdempotent(IdempotentStrategy.ofCode(po.getIdempotent()));
        batch.setStatus(BatchStatus.ofCode(po.getStatus()));
        return batch;
    }

    /**
     * 批次领域模型 → PO。
     *
     * @param domain 领域模型
     * @return 持久化对象
     */
    public BizImportBatch batchToPo(ImportBatch domain) {
        if (domain == null) {
            return null;
        }
        BizImportBatch po = new BizImportBatch();
        BeanUtil.copyProperties(domain, po);
        po.setBatchType(domain.getBatchType() == null ? null : domain.getBatchType().getCode());
        po.setIdempotent(domain.getIdempotent() == null
                ? IdempotentStrategy.DEFAULT.getCode() : domain.getIdempotent().getCode());
        po.setStatus(domain.getStatus() == null ? BatchStatus.COMMITTED.getCode() : domain.getStatus().getCode());
        return po;
    }

    /**
     * 操作日志 PO → 领域模型。
     *
     * @param po 持久化对象
     * @return 领域模型
     */
    public OperationLog logToDomain(BizOperationLog po) {
        if (po == null) {
            return null;
        }
        OperationLog log = new OperationLog();
        BeanUtil.copyProperties(po, log);
        return log;
    }

    /**
     * 操作日志领域模型 → PO。
     *
     * @param domain 领域模型
     * @return 持久化对象
     */
    public BizOperationLog logToPo(OperationLog domain) {
        if (domain == null) {
            return null;
        }
        BizOperationLog po = new BizOperationLog();
        BeanUtil.copyProperties(domain, po);
        return po;
    }

    /* ---------------- 收款登记 ---------------- */

    public Receipt receiptToDomain(BizReceipt po) {
        if (po == null) {
            return null;
        }
        Receipt receipt = new Receipt();
        BeanUtil.copyProperties(po, receipt);
        receipt.setStatus(ReceiptStatus.ofCode(po.getStatus()));
        return receipt;
    }

    public BizReceipt receiptToPo(Receipt domain) {
        if (domain == null) {
            return null;
        }
        BizReceipt po = new BizReceipt();
        BeanUtil.copyProperties(domain, po);
        po.setStatus(domain.getStatus() == null ? ReceiptStatus.NORMAL.getCode() : domain.getStatus().getCode());
        return po;
    }

    /* ---------------- 客户期初应收 ---------------- */

    public CustomerOpening openingToDomain(BizCustomerOpening po) {
        if (po == null) {
            return null;
        }
        CustomerOpening opening = new CustomerOpening();
        BeanUtil.copyProperties(po, opening);
        return opening;
    }

    public BizCustomerOpening openingToPo(CustomerOpening domain) {
        if (domain == null) {
            return null;
        }
        BizCustomerOpening po = new BizCustomerOpening();
        BeanUtil.copyProperties(domain, po);
        return po;
    }

    /* ---------------- 对账单头 ---------------- */

    public Statement statementToDomain(BizStatement po) {
        if (po == null) {
            return null;
        }
        Statement statement = new Statement();
        BeanUtil.copyProperties(po, statement);
        statement.setStatus(StatementStatus.ofCode(po.getStatus()));
        return statement;
    }

    public BizStatement statementToPo(Statement domain) {
        if (domain == null) {
            return null;
        }
        BizStatement po = new BizStatement();
        BeanUtil.copyProperties(domain, po);
        po.setStatus(domain.getStatus() == null ? StatementStatus.DRAFT.getCode() : domain.getStatus().getCode());
        return po;
    }

    /* ---------------- 对账单明细 ---------------- */

    public StatementLine statementLineToDomain(BizStatementLine po) {
        if (po == null) {
            return null;
        }
        StatementLine line = new StatementLine();
        BeanUtil.copyProperties(po, line);
        line.setLineType(StatementLineType.ofCode(po.getLineType()));
        return line;
    }

    public BizStatementLine statementLineToPo(StatementLine domain) {
        if (domain == null) {
            return null;
        }
        BizStatementLine po = new BizStatementLine();
        BeanUtil.copyProperties(domain, po);
        po.setLineType(domain.getLineType() == null ? StatementLineType.SHIP.getCode() : domain.getLineType().getCode());
        return po;
    }
}
