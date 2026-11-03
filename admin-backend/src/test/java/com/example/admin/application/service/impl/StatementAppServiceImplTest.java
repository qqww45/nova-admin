package com.example.admin.application.service.impl;

import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.reconciliation.CustomerOpening;
import com.example.admin.domain.model.receipt.Receipt;
import com.example.admin.domain.model.receipt.ReceiptStatus;
import com.example.admin.domain.model.shipment.Shipment;
import com.example.admin.domain.model.shipment.ShipmentType;
import com.example.admin.domain.repository.CustomerOpeningRepository;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.OperationLogRepository;
import com.example.admin.domain.repository.ReceiptRepository;
import com.example.admin.domain.repository.ShipmentRepository;
import com.example.admin.domain.repository.StatementRepository;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.interfaces.dto.resp.ReceivableSummaryResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * 客户应收速览测试。
 *
 * <p>回归点：
 * <ul>
 *     <li>口径与对账单完全一致：期末 = 期初 + 累计出货 − 退货 − 累计收款
 *         （聚合走 {@code StatementGenerator} 真实现，不 mock）；</li>
 *     <li>按余额倒序：欠得最多的排最前；</li>
 *     <li>无期初、无出货、无收款的客户不出现在清单里。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
class StatementAppServiceImplTest {

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private CustomerOpeningRepository openingRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OperationLogRepository operationLogRepository;

    @Mock
    private OperatorContext operatorContext;

    @InjectMocks
    private StatementAppServiceImpl service;

    @Test
    @DisplayName("应收速览：期初+出货−退货−收款，余额倒序，无往来客户不出现")
    void receivableSummariesShouldFollowStatementRules() {
        when(customerRepository.listAll()).thenReturn(List.of(
                customer(1L, "客户A"), customer(2L, "客户B"), customer(3L, "无往来客户")));
        // 客户A：期初 100 + 出货 500 − 退货 60 − 收款 300 = 余额 240
        when(openingRepository.listAll()).thenReturn(List.of(opening(1L, "100")));
        when(shipmentRepository.listAll()).thenReturn(java.util.Arrays.asList(
                shipment(1L, 1L, "500", ShipmentType.SHIP, "2026-09-01T00:00:00"),
                shipment(2L, 1L, "60", ShipmentType.RETURN, "2026-10-01T00:00:00")));
        when(receiptRepository.listAll()).thenReturn(List.of(receipt(1L, 1L, "300", "2026-10-05T00:00:00")));

        List<ReceivableSummaryResp> rows = service.receivableSummaries();

        assertEquals(1, rows.size(), "只有客户A有往来，无往来客户不应出现");
        ReceivableSummaryResp a = rows.get(0);
        assertEquals("客户A", a.getCustomerName());
        assertEquals(0, new BigDecimal("100").compareTo(a.getOpeningBalance()), "期初应原样带出");
        assertEquals(0, new BigDecimal("500").compareTo(a.getShippedAmount()), "累计出货应为正数口径");
        assertEquals(0, new BigDecimal("60").compareTo(a.getReturnedAmount()), "退货金额应取绝对值单列");
        assertEquals(0, new BigDecimal("300").compareTo(a.getReceivedAmount()));
        assertEquals(0, new BigDecimal("240").compareTo(a.getEndingBalance()),
                "期末余额 = 期初 + 出货 − 退货 − 收款（与对账单同口径）");
        assertEquals(LocalDateTime.parse("2026-10-01T00:00:00"), a.getLastShipmentDate(),
                "最近出货时间应取退货那笔的日期（listAll 按日期升序取最后一条）");
        assertEquals(LocalDateTime.parse("2026-10-05T00:00:00"), a.getLastReceiptDate());
    }

    @Test
    @DisplayName("应收速览：多客户按余额倒序，催款优先级一眼可见")
    void receivableSummariesShouldOrderByBalanceDesc() {
        when(customerRepository.listAll()).thenReturn(List.of(customer(1L, "欠得多"), customer(2L, "欠得少")));
        when(openingRepository.listAll()).thenReturn(List.of());
        when(shipmentRepository.listAll()).thenReturn(java.util.Arrays.asList(
                shipment(1L, 1L, "900", ShipmentType.SHIP, "2026-09-01T00:00:00"),
                shipment(2L, 2L, "100", ShipmentType.SHIP, "2026-09-02T00:00:00")));
        when(receiptRepository.listAll()).thenReturn(List.of());

        List<ReceivableSummaryResp> rows = service.receivableSummaries();

        assertEquals(2, rows.size());
        assertEquals("欠得多", rows.get(0).getCustomerName(), "余额最大的客户应排第一");
        assertEquals("欠得少", rows.get(1).getCustomerName());
    }

    /* ---------------- 工具 ---------------- */

    private static Customer customer(Long id, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerName(name);
        return customer;
    }

    private static CustomerOpening opening(Long customerId, String balance) {
        CustomerOpening opening = new CustomerOpening();
        opening.setCustomerId(customerId);
        opening.setOpeningBalance(new BigDecimal(balance));
        return opening;
    }

    private static Shipment shipment(Long id, Long customerId, String amount,
                                     ShipmentType type, String shipDate) {
        Shipment shipment = new Shipment();
        shipment.setId(id);
        shipment.setCustomerId(customerId);
        shipment.setShipmentType(type);
        shipment.setAmountTaxIncluded(new BigDecimal(amount));
        shipment.setQty(BigDecimal.TEN);
        shipment.setShipDate(LocalDateTime.parse(shipDate));
        return shipment;
    }

    private static Receipt receipt(Long id, Long customerId, String amount, String receiptDate) {
        Receipt receipt = new Receipt();
        receipt.setId(id);
        receipt.setCustomerId(customerId);
        receipt.setAmount(new BigDecimal(amount));
        receipt.setReceiptDate(LocalDateTime.parse(receiptDate));
        receipt.setStatus(ReceiptStatus.NORMAL);
        return receipt;
    }
}
