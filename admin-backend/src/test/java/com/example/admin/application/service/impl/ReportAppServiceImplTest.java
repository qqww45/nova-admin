package com.example.admin.application.service.impl;

import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.order.ItemStatus;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.model.order.SalesOrderItem;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.DictRepository;
import com.example.admin.domain.repository.OrderRepository;
import com.example.admin.domain.repository.ShipmentRepository;
import com.example.admin.interfaces.dto.resp.OpenOrderItemResp;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 报表与体检服务测试（催单清单）。
 *
 * <p>回归点：
 * <ul>
 *     <li>只有未交（qtyOpen &gt; 0）且未取消的明细才进催单清单；</li>
 *     <li>行序先按客户再按下单时间——业务员对着一个客户一次看完该催的单。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-03
 */
@ExtendWith(MockitoExtension.class)
class ReportAppServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DictRepository dictRepository;

    @Mock
    private ShipmentRepository shipmentRepository;

    @InjectMocks
    private ReportAppServiceImpl service;

    @Test
    @DisplayName("催单清单：只收未交且未取消的明细，同客户相邻、先下先出")
    void openItemsShouldOnlyIncludeOpenAndActive() {
        when(customerRepository.listAll()).thenReturn(List.of(customer(1L, "客户A"), customer(2L, "客户B")));
        SalesOrder orderA1 = order(101L, 1L, "SO-A1", "2026-10-01T09:00:00");
        orderA1.getItems().add(item(11L, "5", "3", ItemStatus.NORMAL));   // 未交 2 → 进清单
        orderA1.getItems().add(item(12L, "5", "5", ItemStatus.NORMAL));   // 已交完 → 不进
        orderA1.getItems().add(item(13L, "5", "0", ItemStatus.CANCELED)); // 已取消 → 不进
        SalesOrder orderB = order(201L, 2L, "SO-B", "2026-10-03T09:00:00");
        orderB.getItems().add(item(21L, "10", "4", ItemStatus.NORMAL));   // 未交 6 → 进清单
        SalesOrder orderA2 = order(102L, 1L, "SO-A2", "2026-10-05T09:00:00");
        orderA2.getItems().add(item(14L, "8", "1", ItemStatus.NORMAL));   // 未交 7 → 进清单
        when(orderRepository.listForReport(isNull(), isNull()))
                .thenReturn(List.of(orderB, orderA2, orderA1));

        List<OpenOrderItemResp> rows = service.openItems();

        assertEquals(3, rows.size(), "未交且未取消的明细应为 3 行");
        assertEquals("SO-A1", rows.get(0).getOrderNo(), "同客户先下的单应排前面");
        assertEquals("SO-A2", rows.get(1).getOrderNo(), "客户 A 的两单应相邻");
        assertEquals(0, new BigDecimal("2").compareTo(rows.get(0).getQtyOpen()), "未交数量应原样带出");
        assertEquals("SO-B", rows.get(2).getOrderNo(), "客户 A 全部行结束后才轮到客户 B");
        assertEquals("客户B", rows.get(2).getCustomerName());
    }

    /* ---------------- 工具 ---------------- */

    private static Customer customer(Long id, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerName(name);
        return customer;
    }

    private static SalesOrder order(Long id, Long customerId, String orderNo, String orderDate) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setCustomerId(customerId);
        order.setOrderNo(orderNo);
        order.setOrderDate(LocalDateTime.parse(orderDate));
        return order;
    }

    private static SalesOrderItem item(Long id, String ordered, String shipped, ItemStatus status) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setQtyOrdered(new BigDecimal(ordered));
        item.setQtyShipped(new BigDecimal(shipped));
        item.setQtyOpen(new BigDecimal(ordered).subtract(new BigDecimal(shipped)));
        item.setStatus(status);
        item.setModel("M" + id);
        item.setMaterialNo("P" + id);
        return item;
    }
}
