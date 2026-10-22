package com.example.admin.infrastructure.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.constant.Constants;
import com.example.admin.domain.model.order.OrderStatus;
import com.example.admin.domain.model.shipment.ShipmentType;
import com.example.admin.infrastructure.persistence.mapper.BizCustomerContactMapper;
import com.example.admin.infrastructure.persistence.mapper.BizCustomerInvoiceMapper;
import com.example.admin.infrastructure.persistence.mapper.BizCustomerMapper;
import com.example.admin.infrastructure.persistence.mapper.BizCustomerOpeningMapper;
import com.example.admin.infrastructure.persistence.mapper.BizReceiptMapper;
import com.example.admin.infrastructure.persistence.mapper.BizSalesOrderItemMapper;
import com.example.admin.infrastructure.persistence.mapper.BizSalesOrderMapper;
import com.example.admin.infrastructure.persistence.mapper.BizShipmentMapper;
import com.example.admin.infrastructure.persistence.mapper.BizStatementLineMapper;
import com.example.admin.infrastructure.persistence.mapper.BizStatementMapper;
import com.example.admin.infrastructure.persistence.po.BizCustomer;
import com.example.admin.infrastructure.persistence.po.BizCustomerContact;
import com.example.admin.infrastructure.persistence.po.BizCustomerInvoice;
import com.example.admin.infrastructure.persistence.po.BizCustomerOpening;
import com.example.admin.infrastructure.persistence.po.BizReceipt;
import com.example.admin.infrastructure.persistence.po.BizSalesOrder;
import com.example.admin.infrastructure.persistence.po.BizSalesOrderItem;
import com.example.admin.infrastructure.persistence.po.BizShipment;
import com.example.admin.infrastructure.persistence.po.BizStatement;
import com.example.admin.infrastructure.persistence.po.BizStatementLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试数据灌入器（仅 dev / staging 生效）。
 *
 * <p>目的：让客户/订单/出货/收款/期初应收有一套<b>贴近业务、数值自洽</b>的种子数据，
 * 便于直接体验「客户对账单」的预览/生成/签发/导出全流程，以及订单状态、退货/退厂负数行。
 *
 * <p>设计约定：
 * <ul>
 *     <li>仅在生产之外的 profile 运行，绝不污染生产库；</li>
 *     <li>以客户编码前缀 {@code TC-} 作为种子标记，每次启动先清理再重建，保证幂等、数值确定；</li>
 *     <li>整体 try/catch 兜底：灌入失败只记日志，绝不影响应用启动；</li>
 *     <li>金额沿用 {@code AmountCalculator} 约定：税率 &gt; 0 表示单价含税，
 *         价税合计 = 单价 × 数量，不含税 = 价税合计 ÷ (1 + 税率)，税额 = 价税合计 − 不含税。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-10-22
 */
@Slf4j
@Component
@Profile({"dev", "staging"})
@ConditionalOnProperty(name = "app.seed.test-data.enabled", matchIfMissing = true)
public class TestDataSeeder implements ApplicationRunner {

    /** 种子客户编码前缀（同时作为幂等清理标记） */
    private static final String SEED_CODE_PREFIX = "TC-";

    private static final BigDecimal TAX_RATE = new BigDecimal("0.13");
    private static final LocalDateTime OPENING_AS_OF = LocalDateTime.of(2026, 7, 31, 23, 59, 59);

    private final BizCustomerMapper customerMapper;
    private final BizCustomerContactMapper contactMapper;
    private final BizCustomerInvoiceMapper invoiceMapper;
    private final BizSalesOrderMapper orderMapper;
    private final BizSalesOrderItemMapper orderItemMapper;
    private final BizShipmentMapper shipmentMapper;
    private final BizReceiptMapper receiptMapper;
    private final BizCustomerOpeningMapper openingMapper;
    private final BizStatementMapper statementMapper;
    private final BizStatementLineMapper statementLineMapper;

    @Autowired
    public TestDataSeeder(BizCustomerMapper customerMapper,
                          BizCustomerContactMapper contactMapper,
                          BizCustomerInvoiceMapper invoiceMapper,
                          BizSalesOrderMapper orderMapper,
                          BizSalesOrderItemMapper orderItemMapper,
                          BizShipmentMapper shipmentMapper,
                          BizReceiptMapper receiptMapper,
                          BizCustomerOpeningMapper openingMapper,
                          BizStatementMapper statementMapper,
                          BizStatementLineMapper statementLineMapper) {
        this.customerMapper = customerMapper;
        this.contactMapper = contactMapper;
        this.invoiceMapper = invoiceMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.shipmentMapper = shipmentMapper;
        this.receiptMapper = receiptMapper;
        this.openingMapper = openingMapper;
        this.statementMapper = statementMapper;
        this.statementLineMapper = statementLineMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            cleanupSeeded();
            seedAll();
            log.info("[测试数据] 已灌入种子客户及关联订单/出货/收款/期初数据（编码前缀 {}）", SEED_CODE_PREFIX);
        } catch (Exception ex) {
            // 测试数据只是锦上添花，任何失败都不能影响应用启动
            log.warn("[测试数据] 灌入失败（不影响启动）：{}", ex.getMessage(), ex);
        }
    }

    /* ==================== 清理（幂等） ==================== */

    private void cleanupSeeded() {
        List<BizCustomer> seeded = customerMapper.selectList(new LambdaQueryWrapper<BizCustomer>()
                .likeRight(BizCustomer::getCustomerCode, SEED_CODE_PREFIX));
        if (seeded.isEmpty()) {
            return;
        }
        List<Long> customerIds = seeded.stream().map(BizCustomer::getId).toList();

        // 对账单快照（若用户已生成）一并清理，避免悬挂明细
        List<BizStatement> statements = statementMapper.selectList(new LambdaQueryWrapper<BizStatement>()
                .in(BizStatement::getCustomerId, customerIds));
        if (!statements.isEmpty()) {
            List<Long> statementIds = statements.stream().map(BizStatement::getId).toList();
            statementLineMapper.delete(new LambdaQueryWrapper<BizStatementLine>()
                    .in(BizStatementLine::getStatementId, statementIds));
        }
        statementMapper.delete(new LambdaQueryWrapper<BizStatement>()
                .in(BizStatement::getCustomerId, customerIds));

        // 订单明细 → 订单
        List<BizSalesOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<BizSalesOrder>()
                .in(BizSalesOrder::getCustomerId, customerIds));
        if (!orders.isEmpty()) {
            List<Long> orderIds = orders.stream().map(BizSalesOrder::getId).toList();
            orderItemMapper.delete(new LambdaQueryWrapper<BizSalesOrderItem>()
                    .in(BizSalesOrderItem::getOrderId, orderIds));
        }
        orderMapper.delete(new LambdaQueryWrapper<BizSalesOrder>()
                .in(BizSalesOrder::getCustomerId, customerIds));

        // 联系人 / 开票信息（子表先于客户删除，避免悬挂数据）
        contactMapper.delete(new LambdaQueryWrapper<BizCustomerContact>()
                .in(BizCustomerContact::getCustomerId, customerIds));
        invoiceMapper.delete(new LambdaQueryWrapper<BizCustomerInvoice>()
                .in(BizCustomerInvoice::getCustomerId, customerIds));

        // 出货 / 收款 / 期初 / 客户
        shipmentMapper.delete(new LambdaQueryWrapper<BizShipment>()
                .in(BizShipment::getCustomerId, customerIds));
        receiptMapper.delete(new LambdaQueryWrapper<BizReceipt>()
                .in(BizReceipt::getCustomerId, customerIds));
        openingMapper.delete(new LambdaQueryWrapper<BizCustomerOpening>()
                .in(BizCustomerOpening::getCustomerId, customerIds));
        customerMapper.delete(new LambdaQueryWrapper<BizCustomer>()
                .in(BizCustomer::getId, customerIds));
    }

    /* ==================== 灌入 ==================== */

    private void seedAll() {
        Product p1 = new Product("G138-4L", "M-1001", "160×120mm", money("432.00"));
        Product p2 = new Product("DM-220", "M-1002", "85×55mm", money("168.00"));
        Product p3 = new Product("IF-485", "M-1003", "90×70mm", money("96.50"));
        Product p4 = new Product("DD-7IN", "M-1004", "165×100mm", money("215.00"));

        // 1) 深圳华强电子：期初 12 万，两行全出货，回款 8 万（对账后仍欠款）
        {
            long cid = seedCustomer("深圳市华强电子科技有限公司", "TC-0001",
                    "广东省深圳市福田区振华路 45 号", money("120000"));
            seedContact(cid, "王志强", "采购经理", "0755-83210011", "13823668801", "wangzq@hq-dz.com.cn", true);
            seedContact(cid, "刘晓敏", "财务主管", "0755-83210012", "13723451122", "liuxm@hq-dz.com.cn", false);
            seedInvoice(cid, "深圳市华强电子科技有限公司", "91440300MA5F2K1X9B", "招商银行深圳分行营业部",
                    "755901248310801", "广东省深圳市福田区振华路 45 号", "0755-83210011", "SPECIAL", true);
            seedInvoice(cid, "深圳市华强电子科技有限公司", "91440300MA5F2K1X9B", "招商银行深圳分行营业部",
                    "755901248310801", "广东省深圳市福田区振华路 45 号", "0755-83210011", "NORMAL", false);
            BizSalesOrder order = seedOrder(cid, 1001, "PO-KH-2026-0801", "MONTHLY", "LOGISTICS",
                    LocalDateTime.of(2026, 8, 5, 10, 0));
            List<BizSalesOrderItem> items = new ArrayList<>();
            items.add(seedItemWithShips(cid, order, 1, p1, money("300"), "C-8801", List.of(
                    new Ship(money("300"), ShipmentType.SHIP, LocalDateTime.of(2026, 8, 20, 9, 0), 2001, null))));
            items.add(seedItemWithShips(cid, order, 2, p2, money("200"), "C-8802", List.of(
                    new Ship(money("200"), ShipmentType.SHIP, LocalDateTime.of(2026, 8, 22, 9, 0), 2002, null))));
            finalizeOrder(order, items);
            newReceipt(cid, 3001, LocalDateTime.of(2026, 9, 15, 11, 0), money("80000"), "MONTHLY", "INV-2026-0801");
        }

        // 2) 广州联控自动化：无期初，第一行部分出货，第二行未出（部分出货状态）
        {
            long cid = seedCustomer("广州联控自动化设备有限公司", "TC-0002",
                    "广东省广州市黄埔区科学城彩频路 7 号", money("0"));
            seedContact(cid, "陈立群", "采购总监", "020-82336601", "13902253366", "chenlq@liankong-gz.com", true);
            seedContact(cid, "黄美玲", "采购员", "020-82336602", "13622889901", "huangml@liankong-gz.com", false);
            seedInvoice(cid, "广州联控自动化设备有限公司", "91440101MA5W3L2Y8C", "中国工商银行广州黄埔支行",
                    "3602000619200034567", "广东省广州市黄埔区科学城彩频路 7 号", "020-82336600", "SPECIAL", true);
            BizSalesOrder order = seedOrder(cid, 1002, "PO-GZ-2026-0802", "MONTHLY60", "FACTORY",
                    LocalDateTime.of(2026, 8, 8, 10, 0));
            List<BizSalesOrderItem> items = new ArrayList<>();
            items.add(seedItemWithShips(cid, order, 1, p1, money("400"), "C-8803", List.of(
                    new Ship(money("150"), ShipmentType.SHIP, LocalDateTime.of(2026, 9, 2, 9, 0), 2003, null))));
            items.add(seedItemWithShips(cid, order, 2, p2, money("150"), "C-8804", List.of()));
            finalizeOrder(order, items);
        }

        // 3) 苏州精密仪器：期初 5 万，一行全出、一行部分出，回款 3 万
        {
            long cid = seedCustomer("苏州精密仪器股份有限公司", "TC-0003",
                    "江苏省苏州市工业园区金鸡湖大道 1355 号", money("50000"));
            seedContact(cid, "周文斌", "供应链经理", "0512-62881101", "13862098811", "zhouwb@sz-precision.com", true);
            seedContact(cid, "顾丽华", "财务经理", "0512-62881102", "13962117782", "gulh@sz-precision.com", false);
            seedInvoice(cid, "苏州精密仪器股份有限公司", "91320505MA5N4M3Z7D", "中国银行苏州工业园区支行",
                    "497758849211", "江苏省苏州市工业园区金鸡湖大道 1355 号", "0512-62881100", "SPECIAL", true);
            BizSalesOrder order = seedOrder(cid, 1003, "PO-SZ-2026-0803", "MONTHLY", "EXPRESS",
                    LocalDateTime.of(2026, 8, 10, 10, 0));
            List<BizSalesOrderItem> items = new ArrayList<>();
            items.add(seedItemWithShips(cid, order, 1, p1, money("120"), "C-8805", List.of(
                    new Ship(money("120"), ShipmentType.SHIP, LocalDateTime.of(2026, 8, 28, 9, 0), 2004, null))));
            items.add(seedItemWithShips(cid, order, 2, p2, money("200"), "C-8806", List.of(
                    new Ship(money("80"), ShipmentType.SHIP, LocalDateTime.of(2026, 9, 5, 9, 0), 2005, null))));
            finalizeOrder(order, items);
            newReceipt(cid, 3002, LocalDateTime.of(2026, 9, 20, 11, 0), money("30000"), "MONTHLY", "INV-2026-0803");
        }

        // 4) 北京中创系统集成：期初 3 万，已确认未出货（验证仅有期初的对账单）
        {
            long cid = seedCustomer("北京中创系统集成有限公司", "TC-0004",
                    "北京市海淀区上地信息路 2 号", money("30000"));
            seedContact(cid, "孙浩", "项目经理", "010-82890011", "13810169901", "sunhao@zc-system.com", true);
            seedInvoice(cid, "北京中创系统集成有限公司", "91110108MA5P6N4A6E", "中国建设银行北京上地支行",
                    "11050188360000002345", "北京市海淀区上地信息路 2 号", "010-82890000", "SPECIAL", true);
            BizSalesOrder order = seedOrder(cid, 1004, "PO-BJ-2026-0804", "CASH", "EXPRESS",
                    LocalDateTime.of(2026, 8, 12, 10, 0));
            List<BizSalesOrderItem> items = new ArrayList<>();
            items.add(seedItemWithShips(cid, order, 1, p1, money("60"), "C-8807", List.of()));
            finalizeOrder(order, items);
        }

        // 5) 东莞智造电子：含销售退货 + 退厂返工（验证对账单负数行）
        {
            long cid = seedCustomer("东莞智造电子有限公司", "TC-0005",
                    "广东省东莞市长安镇振安中路 8 号", money("20000"));
            seedContact(cid, "梁嘉欣", "采购主管", "0769-85551101", "13790336655", "liangjx@dg-zhizao.com", true);
            seedContact(cid, "罗德全", "总经理助理", "0769-85551102", "13527998811", "luodq@dg-zhizao.com", false);
            seedInvoice(cid, "东莞智造电子有限公司", "91441900MA5Q7P5B3F", "中国农业银行东莞长安支行",
                    "44272801040012345", "广东省东莞市长安镇振安中路 8 号", "0769-85551100", "SPECIAL", true);
            BizSalesOrder order = seedOrder(cid, 1005, "PO-DG-2026-0805", "MONTHLY", "LOGISTICS",
                    LocalDateTime.of(2026, 8, 15, 10, 0));
            List<BizSalesOrderItem> items = new ArrayList<>();
            items.add(seedItemWithShips(cid, order, 1, p1, money("250"), "C-8808", List.of(
                    new Ship(money("250"), ShipmentType.SHIP, LocalDateTime.of(2026, 9, 1, 9, 0), 2006, null),
                    new Ship(money("20"), ShipmentType.RETURN, LocalDateTime.of(2026, 10, 8, 9, 0), 2007, "客户抽样不良退货"))));
            items.add(seedItemWithShips(cid, order, 2, p2, money("100"), "C-8809", List.of(
                    new Ship(money("100"), ShipmentType.SHIP, LocalDateTime.of(2026, 9, 3, 9, 0), 2008, null),
                    new Ship(money("30"), ShipmentType.RETURN_FACTORY, LocalDateTime.of(2026, 10, 10, 9, 0), 2009, "焊点缺陷退厂返工"))));
            finalizeOrder(order, items);
            newReceipt(cid, 3003, LocalDateTime.of(2026, 10, 12, 11, 0), money("60000"), "MONTHLY", "INV-2026-0805");
        }

        // 6) 武汉光谷测控：期初 8 千，两行全出，回款 1 万（多品种）
        {
            long cid = seedCustomer("武汉光谷测控技术有限公司", "TC-0006",
                    "湖北省武汉市东湖高新区光谷大道 77 号", money("8000"));
            seedContact(cid, "程雨欣", "商务经理", "027-87108801", "13871559901", "chengyx@wh-guanggu.com", true);
            seedContact(cid, "叶志明", "技术工程师", "027-87108802", "13607117782", "yezm@wh-guanggu.com", false);
            seedInvoice(cid, "武汉光谷测控技术有限公司", "91420100MA5R8Q6C2G", "交通银行武汉光谷支行",
                    "42186988800123456789", "湖北省武汉市东湖高新区光谷大道 77 号", "027-87108800", "SPECIAL", true);
            BizSalesOrder order = seedOrder(cid, 1006, "PO-WH-2026-0806", "MONTHLY", "LOGISTICS",
                    LocalDateTime.of(2026, 8, 18, 10, 0));
            List<BizSalesOrderItem> items = new ArrayList<>();
            items.add(seedItemWithShips(cid, order, 1, p3, money("120"), "C-8810", List.of(
                    new Ship(money("120"), ShipmentType.SHIP, LocalDateTime.of(2026, 9, 8, 9, 0), 2010, null))));
            items.add(seedItemWithShips(cid, order, 2, p4, money("40"), "C-8811", List.of(
                    new Ship(money("40"), ShipmentType.SHIP, LocalDateTime.of(2026, 9, 10, 9, 0), 2011, null))));
            finalizeOrder(order, items);
            newReceipt(cid, 3004, LocalDateTime.of(2026, 10, 15, 11, 0), money("10000"), "MONTHLY", "INV-2026-0806");
        }
    }

    /* ==================== 基础插入 helper ==================== */

    private long seedCustomer(String name, String code, String address, BigDecimal opening) {
        BizCustomer customer = new BizCustomer();
        customer.setCustomerName(name);
        customer.setCustomerCode(code);
        customer.setAddress(address);
        customer.setStatus(1);
        customerMapper.insert(customer);

        BizCustomerOpening openingPo = new BizCustomerOpening();
        openingPo.setCustomerId(customer.getId());
        openingPo.setAsOfDate(OPENING_AS_OF);
        openingPo.setOpeningBalance(opening);
        openingPo.setCurrency("CNY");
        openingPo.setRemark("期初应收（种子数据）");
        openingMapper.insert(openingPo);
        return customer.getId();
    }

    /** 灌入一条联系人（默认唯一性由业务接口保证，种子数据直接写库） */
    private void seedContact(long customerId, String name, String title, String phone,
                             String mobile, String email, boolean isDefault) {
        BizCustomerContact po = new BizCustomerContact();
        po.setCustomerId(customerId);
        po.setContactName(name);
        po.setTitle(title);
        po.setPhone(phone);
        po.setMobile(mobile);
        po.setEmail(email);
        po.setIsDefault(isDefault ? 1 : 0);
        po.setStatus(1);
        contactMapper.insert(po);
    }

    /** 灌入一条开票信息 */
    private void seedInvoice(long customerId, String title, String taxNo, String bankName,
                             String bankAccount, String address, String phone, String type, boolean isDefault) {
        BizCustomerInvoice po = new BizCustomerInvoice();
        po.setCustomerId(customerId);
        po.setInvoiceTitle(title);
        po.setTaxNo(taxNo);
        po.setBankName(bankName);
        po.setBankAccount(bankAccount);
        po.setInvoiceAddress(address);
        po.setInvoicePhone(phone);
        po.setInvoiceType(type);
        po.setIsDefault(isDefault ? 1 : 0);
        po.setStatus(1);
        invoiceMapper.insert(po);
    }

    private BizSalesOrder seedOrder(long customerId, int seq, String customerOrderNo, String settle,
                                    String delivery, LocalDateTime orderDate) {
        BizSalesOrder order = new BizSalesOrder();
        order.setOrderNo("SO2026" + pad(seq));
        order.setCustomerId(customerId);
        order.setCustomerOrderNo(customerOrderNo);
        order.setSalesmanId(Constants.SUPER_ADMIN_USER_ID);
        order.setOrderDate(orderDate);
        order.setExpectedDeliveryDate(orderDate.plusDays(20));
        order.setStatus(OrderStatus.CONFIRMED.getCode());
        order.setCurrency("CNY");
        order.setSettlementMethod(settle);
        order.setDeliveryMethod(delivery);
        order.setRequirement("按客户签样生产，批量稳定交付");
        order.setMainboardLabel("MB" + seq);
        order.setCartonLabel("CT" + seq);
        orderMapper.insert(order);
        return order;
    }

    private BizSalesOrderItem seedItemWithShips(long customerId, BizSalesOrder order, int lineNo, Product product,
                                                BigDecimal qtyOrdered, String customerMaterialNo, List<Ship> ships) {
        BizSalesOrderItem item = new BizSalesOrderItem();
        item.setOrderId(order.getId());
        item.setLineNo(lineNo);
        item.setModel(product.model());
        item.setMaterialNo(product.materialNo());
        item.setCustomerMaterialNo(customerMaterialNo);
        item.setSpec(product.spec());
        item.setQtyOrdered(qtyOrdered);
        item.setUnitPrice(product.unitPrice());
        item.setTaxRate(TAX_RATE);
        item.setPriceIsTaxIncluded(1);
        BigDecimal incl = incl(product.unitPrice(), qtyOrdered);
        item.setAmountTaxIncluded(incl);
        item.setAmountExTax(exTax(incl));
        item.setTaxAmount(incl.subtract(exTax(incl)));
        item.setQtyShipped(BigDecimal.ZERO);
        item.setQtyOpen(qtyOrdered);
        item.setStatus("NORMAL");
        item.setReconcileStatus("UNRECONCILED");
        orderItemMapper.insert(item);

        BigDecimal shipped = BigDecimal.ZERO;
        for (Ship ship : ships) {
            seedShipment(customerId, order, item, product, ship);
            shipped = shipped.add(ship.type().isReturn() ? ship.qty().negate() : ship.qty());
        }
        item.setQtyShipped(shipped);
        item.setQtyOpen(qtyOrdered.subtract(shipped));
        orderItemMapper.updateById(item);
        return item;
    }

    private void seedShipment(long customerId, BizSalesOrder order, BizSalesOrderItem item, Product product, Ship ship) {
        BizShipment po = new BizShipment();
        po.setCustomerId(customerId);
        po.setOrderItemId(item.getId());
        po.setShipDate(ship.date());
        po.setProcessOrderNo("JG2026" + pad(ship.seq()));
        po.setSalesmanId(Constants.SUPER_ADMIN_USER_ID);
        po.setCustomerOrderNo(order.getCustomerOrderNo());
        po.setCustomerMaterialNo(item.getCustomerMaterialNo());
        po.setModel(product.model());
        po.setMaterialNo(product.materialNo());
        po.setSpec(product.spec());
        po.setQty(ship.qty());
        po.setShipmentType(ship.type().getCode());
        BigDecimal incl = incl(product.unitPrice(), ship.qty());
        po.setAmountTaxIncluded(incl);
        po.setAmount(exTax(incl));
        po.setTaxAmount(incl.subtract(exTax(incl)));
        po.setStatus("NORMAL");
        po.setSource("MANUAL");
        po.setRemark(ship.remark());
        shipmentMapper.insert(po);
    }

    private void finalizeOrder(BizSalesOrder order, List<BizSalesOrderItem> items) {
        boolean anyShipped = items.stream().anyMatch(i -> i.getQtyShipped().compareTo(BigDecimal.ZERO) > 0);
        boolean allClosed = items.stream().allMatch(i -> i.getQtyOpen().compareTo(BigDecimal.ZERO) <= 0);
        OrderStatus status = allClosed ? OrderStatus.COMPLETED
                : anyShipped ? OrderStatus.PARTIAL_SHIPPED : OrderStatus.CONFIRMED;
        order.setStatus(status.getCode());
        orderMapper.updateById(order);
    }

    private void newReceipt(long customerId, int seq, LocalDateTime date, BigDecimal amount,
                            String settleMethod, String invoiceNo) {
        BizReceipt po = new BizReceipt();
        po.setReceiptNo("SK2026" + pad(seq));
        po.setCustomerId(customerId);
        po.setReceiptDate(date);
        po.setAmount(amount);
        po.setCurrency("CNY");
        po.setSettleMethod(settleMethod);
        po.setInvoiceNo(invoiceNo);
        po.setStatus("NORMAL");
        receiptMapper.insert(po);
    }

    /* ==================== 工具 ==================== */

    private static String pad(int seq) {
        return String.format("%04d", seq);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /** 价税合计 = 含税单价 × 数量（保留 2 位） */
    private static BigDecimal incl(BigDecimal unitPriceIncluded, BigDecimal qty) {
        return unitPriceIncluded.multiply(qty).setScale(2, RoundingMode.HALF_UP);
    }

    /** 不含税金额 = 价税合计 ÷ (1 + 税率) */
    private static BigDecimal exTax(BigDecimal amountIncluded) {
        return amountIncluded.divide(BigDecimal.ONE.add(TAX_RATE), 2, RoundingMode.HALF_UP);
    }

    /** 产品快照 */
    private record Product(String model, String materialNo, String spec, BigDecimal unitPrice) {
    }

    /** 一笔出货计划 */
    private record Ship(BigDecimal qty, ShipmentType type, LocalDateTime date, int seq, String remark) {
    }
}
