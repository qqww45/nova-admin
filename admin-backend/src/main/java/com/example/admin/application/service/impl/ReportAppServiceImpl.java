package com.example.admin.application.service.impl;

import com.example.admin.domain.model.customer.Customer;
import com.example.admin.domain.model.dict.DictItem;
import com.example.admin.domain.model.order.SalesOrder;
import com.example.admin.domain.model.order.SalesOrderItem;
import com.example.admin.domain.model.shipment.ShipmentQuery;
import com.example.admin.domain.repository.CustomerRepository;
import com.example.admin.domain.repository.DictRepository;
import com.example.admin.domain.repository.OrderRepository;
import com.example.admin.domain.repository.ShipmentRepository;
import com.example.admin.interfaces.dto.resp.CustomerSummaryResp;
import com.example.admin.interfaces.dto.resp.HealthReportResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表与体检服务实现（M5 / 附录A A.6）。
 *
 * <p>实现说明：汇总走内存聚合而不是在 SQL 里 GROUP BY。
 * 原因是这些报表要同时用到"订单头的客户"与"明细的数量/金额"两个层级，
 * 跨表聚合要么写视图、要么在应用层拼；本系统的数据量在万级，
 * 内存聚合换来的是规则可读、且和领域模型共用同一套口径。
 * <b>若订单量增长到十万级以上，应改为 SQL 聚合</b>（已在上限保护里留了口子）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class ReportAppServiceImpl implements com.example.admin.application.service.ReportAppService {

    /** 税率字典编码（与 V10 种子数据一致，禁止硬编码税率值） */
    private static final String DICT_TAX_RATE = "tax_rate";

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final DictRepository dictRepository;

    @Override
    public HealthReportResp health() {
        HealthReportResp report = new HealthReportResp();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<HealthReportResp.HealthIssue> issues = new ArrayList<>();

        /* --- 重复客户：按标准化名称分组 --- */
        List<Customer> customers = customerRepository.listAll();
        report.setCustomerCount(customers.size());
        Map<String, List<Customer>> byName = new LinkedHashMap<>();
        for (Customer customer : customers) {
            // 用与"客户唯一性校验"相同的标准化口径，避免"XX公司"与"XX 公司"被判成两家
            String key = Customer.normalizeName(customer.getCustomerName());
            byName.computeIfAbsent(key == null ? "" : key, k -> new ArrayList<>()).add(customer);
        }
        for (Map.Entry<String, List<Customer>> entry : byName.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            addIssue(issues, counts, "DUPLICATE_CUSTOMER", "重复客户",
                    entry.getValue().get(0).getCustomerName(),
                    "同一客户存在 " + entry.getValue().size() + " 条记录，建议合并后停用多余记录");
        }

        /* --- 订单明细扫描：缺料号 / 数量异常 / 税率异常 --- */
        Set<BigDecimal> legalTaxRates = new HashSet<>();
        for (DictItem item : dictRepository.listByCode(DICT_TAX_RATE)) {
            try {
                legalTaxRates.add(new BigDecimal(item.getDictValue()));
            } catch (NumberFormatException ignored) {
                // 字典里配了非数字，忽略：体检报告不该因为配置错误而整体失败
            }
        }

        List<SalesOrder> orders = orderRepository.listForReport(null, null);
        report.setOrderCount(orders.size());
        int itemCount = 0;
        for (SalesOrder order : orders) {
            for (SalesOrderItem item : order.getItems()) {
                itemCount++;
                if (isBlank(item.getMaterialNo())) {
                    addIssue(issues, counts, "MISSING_MATERIAL_NO", "缺料号",
                            order.getOrderNo() + " / " + item.getModel(),
                            "明细未填写料号，出货时将无法按料号匹配订单");
                }
                BigDecimal shipped = nz(item.getQtyShipped());
                BigDecimal ordered = nz(item.getQtyOrdered());
                BigDecimal open = nz(item.getQtyOpen());
                if (shipped.compareTo(ordered) > 0 || open.compareTo(BigDecimal.ZERO) < 0) {
                    addIssue(issues, counts, "QUANTITY_ANOMALY", "数量异常",
                            order.getOrderNo() + " / " + item.getModel(),
                            "下单 " + ordered + "，已出 " + shipped + "，未出 " + open + "（已出超过下单或为负数）");
                }
                BigDecimal taxRate = item.getTaxRate();
                if (taxRate != null && !legalTaxRates.contains(taxRate)) {
                    addIssue(issues, counts, "TAX_RATE_ANOMALY", "税率异常",
                            order.getOrderNo() + " / " + item.getModel(),
                            "税率 " + taxRate + " 不在字典可选值内，请核对或补充字典");
                }
            }
        }
        report.setItemCount(itemCount);

        /* --- 无源出货 --- */
        ShipmentQuery orphanQuery = new ShipmentQuery();
        orphanQuery.setPageNum(1);
        orphanQuery.setPageSize(1);
        long orphanTotal = shipmentRepository.pageOrphan(orphanQuery).getTotal();
        // 明细条目由异常数据池页面逐条处理，体检报告只报数量
        if (orphanTotal > 0) {
            counts.put("ORPHAN_SHIPMENT", (int) orphanTotal);
            addIssue(issues, null, "ORPHAN_SHIPMENT", "无源出货", "异常数据池",
                    "有 " + orphanTotal + " 条出货未匹配到订单明细，请到异常数据池补挂订单");
        }

        ShipmentQuery allQuery = new ShipmentQuery();
        allQuery.setPageNum(1);
        allQuery.setPageSize(1);
        report.setShipmentCount((int) shipmentRepository.page(allQuery).getTotal());
        report.setCounts(counts);
        report.setIssues(issues);
        return report;
    }

    @Override
    public List<CustomerSummaryResp> customerSummary(LocalDate begin, LocalDate end) {
        LocalDateTime from = begin == null ? null : begin.atStartOfDay();
        LocalDateTime to = end == null ? null : LocalDateTime.of(end, LocalTime.MAX);
        Map<Long, CustomerSummaryResp> grouped = new LinkedHashMap<>();
        // 订单实体里只存 customerId，报表要显示客户名；这里一次性取回做映射，避免逐单查库
        Map<Long, String> customerNames = new HashMap<>();
        for (Customer customer : customerRepository.listAll()) {
            customerNames.put(customer.getId(), customer.getCustomerName());
        }
        for (SalesOrder order : orderRepository.listForReport(from, to)) {
            CustomerSummaryResp row = grouped.computeIfAbsent(order.getCustomerId(), id -> {
                CustomerSummaryResp resp = new CustomerSummaryResp();
                resp.setCustomerId(String.valueOf(id));
                resp.setCustomerName(customerNames.getOrDefault(id, order.getCustomerName()));
                return resp;
            });
            row.setOrderCount(row.getOrderCount() + 1);
            for (SalesOrderItem item : order.getItems()) {
                row.setItemCount(row.getItemCount() + 1);
                row.setQtyOrdered(nz(row.getQtyOrdered()).add(nz(item.getQtyOrdered())));
                row.setQtyShipped(nz(row.getQtyShipped()).add(nz(item.getQtyShipped())));
                row.setQtyOpen(nz(row.getQtyOpen()).add(nz(item.getQtyOpen())));
                row.setAmountTaxIncluded(nz(row.getAmountTaxIncluded()).add(nz(item.getAmountTaxIncluded())));
            }
        }
        return grouped.values().stream()
                .sorted((a, b) -> b.getQtyOrdered().compareTo(a.getQtyOrdered()))
                .toList();
    }

    /* ---------------- 私有辅助 ---------------- */

    /**
     * 登记一条问题（受每类上限约束）。
     *
     * @param issues    明细列表
     * @param counts    类别计数（传 null 表示外部已自行计数）
     * @param category  类别编码
     * @param label     类别名称
     * @param subject   问题对象
     * @param detail    说明
     */
    private void addIssue(List<HealthReportResp.HealthIssue> issues, Map<String, Integer> counts,
                          String category, String label, String subject, String detail) {
        if (counts != null) {
            counts.merge(category, 1, Integer::sum);
        }
        if (counts != null && counts.get(category) > HealthReportResp.MAX_ISSUES) {
            return;
        }
        HealthReportResp.HealthIssue issue = new HealthReportResp.HealthIssue();
        issue.setCategory(category);
        issue.setCategoryLabel(label);
        issue.setSubject(subject);
        issue.setDetail(detail);
        issues.add(issue);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
