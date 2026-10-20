-- =============================================================================
-- V10：业务字典种子数据 + 导入字段映射（附录A 落地）
--
-- 设计原则：
--   1. 税率、币种、结算方式、交货方式一律从 biz_dict_item 读取，禁止在代码里硬编码；
--   2. 导入字段映射配置化：历史台账列名 → 系统字段写在 biz_import_mapping，
--      新增一种列头叫法只需插一条数据，不改代码（见附录A 通用导入规则）。
--
-- [待确认] 默认值说明（本文档采用的是附录A 的"建议默认"，确认后可直接改数据，无需改代码）：
--   · 税率候选 0 / 1% / 3% / 6% / 9% / 13%，默认 13%；
--   · 币种暂仅 CNY；
--   · 发票类型：增值税专用发票 / 增值税普通发票。
-- =============================================================================

-- ---------------------------- 税率 ----------------------------
INSERT INTO biz_dict_item (id, dict_code, dict_value, dict_label, sort, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(1, 'tax_rate', '0',    '0%（不含税）', 1, 1, '税率为空或 0 时，单价视为不含税单价', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(2, 'tax_rate', '0.01', '1%',          2, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(3, 'tax_rate', '0.03', '3%',          3, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(4, 'tax_rate', '0.06', '6%',          4, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(5, 'tax_rate', '0.09', '9%',          5, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(6, 'tax_rate', '0.13', '13%',         6, 1, '默认税率', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- ---------------------------- 币种 ----------------------------
INSERT INTO biz_dict_item (id, dict_code, dict_value, dict_label, sort, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(11, 'currency', 'CNY', '人民币 CNY', 1, 1, '[待确认]暂仅支持单币种', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- ---------------------------- 结算方式 ----------------------------
INSERT INTO biz_dict_item (id, dict_code, dict_value, dict_label, sort, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(21, 'settlement_method', 'PREPAID',   '预付',     1, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(22, 'settlement_method', 'CASH',      '现结',     2, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(23, 'settlement_method', 'MONTHLY',   '月结30天', 3, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(24, 'settlement_method', 'MONTHLY60', '月结60天', 4, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- ---------------------------- 交货方式 ----------------------------
INSERT INTO biz_dict_item (id, dict_code, dict_value, dict_label, sort, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(31, 'delivery_method', 'SELF_PICKUP', '自提',     1, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(32, 'delivery_method', 'EXPRESS',     '快递',     2, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(33, 'delivery_method', 'LOGISTICS',   '物流',     3, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(34, 'delivery_method', 'FACTORY',     '送货上门', 4, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- ---------------------------- 发票类型 ----------------------------
INSERT INTO biz_dict_item (id, dict_code, dict_value, dict_label, sort, status, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(41, 'invoice_type', 'SPECIAL', '增值税专用发票', 1, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(42, 'invoice_type', 'NORMAL',  '增值税普通发票', 2, 1, '', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- =============================================================================
-- 导入字段映射（附录A A.2 / A.3 / A.4）
--   aliases 为英文逗号分隔的表头别名；匹配规则：去空格/换行 + 全半角统一 + 忽略括号内容后精确匹配，
--   精确未命中再按包含匹配。因此「备注」与「备注(订单)」能区分（后者归一化后为「备注订单」）。
-- =============================================================================

-- ---------------------------- 客户导入（A.2） ----------------------------
INSERT INTO biz_import_mapping (id, module, field_name, aliases, required, column_order, default_value, field_type, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(1001, 'CUSTOMER', 'customer_name', '客户名称,客户名,客户,Client,Customer,Customer Name', 1, 1,  NULL, 'STRING', '唯一键（标准化后比对）',         1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1002, 'CUSTOMER', 'customer_code', '客户编号,客户代码,Customer Code,Code',               0, 2,  NULL, 'STRING', '[待确认]暂不需要，预留',        1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1003, 'CUSTOMER', 'address',       '地址,客户地址,Address',                              0, 3,  NULL, 'STRING', '',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1004, 'CUSTOMER', 'contact_name',  '联系人,Contact,联系人姓名',                          0, 4,  NULL, 'STRING', '非空则创建默认联系人',          1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1005, 'CUSTOMER', 'phone',         '电话,联系电话,Tel,Telephone',                        0, 5,  NULL, 'STRING', '',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1006, 'CUSTOMER', 'mobile',        '手机,手机号,Mobile,Phone',                           0, 6,  NULL, 'STRING', '',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1007, 'CUSTOMER', 'email',         '邮箱,电子邮箱,Email,E-mail',                         0, 7,  NULL, 'STRING', '格式校验',                      1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1008, 'CUSTOMER', 'invoice_title', '开票名称,发票抬头,Invoice Title',                    0, 8,  NULL, 'STRING', '非空则创建默认开票信息',        1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1009, 'CUSTOMER', 'tax_no',        '纳税人识别号,税号,Tax No,TaxNo',                     0, 9,  NULL, 'STRING', '与开票名称成对',                1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1010, 'CUSTOMER', 'bank_name',     '开户行,开户银行,Bank,Bank Name',                     0, 10, NULL, 'STRING', '',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1011, 'CUSTOMER', 'bank_account',  '银行账号,银行帐号,账号,Account,Bank Account',        0, 11, NULL, 'STRING', '',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1012, 'CUSTOMER', 'invoice_address','开票地址,发票地址,Invoice Address',                 0, 12, NULL, 'STRING', '',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1013, 'CUSTOMER', 'invoice_phone', '开票电话,发票电话,Invoice Phone',                    0, 13, NULL, 'STRING', '',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1014, 'CUSTOMER', 'remark',        '备注,Remark,Comment',                                0, 14, NULL, 'STRING', '',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- ---------------------------- 订单导入（A.3，头+明细合一） ----------------------------
INSERT INTO biz_import_mapping (id, module, field_name, aliases, required, column_order, default_value, field_type, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(1101, 'ORDER', 'order_no',               '订单号,订单编号,Order No,OrderNo',                 1, 1,  NULL, 'STRING', '头：唯一键，同单多行必须一致',      1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1102, 'ORDER', 'customer_name',          '客户名称,客户名,客户,Customer',                    1, 2,  NULL, 'STRING', '头：按名称匹配客户，未匹配→警告级',  1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1103, 'ORDER', 'customer_order_no',      '客户订单号,PO号,客户PO,Customer PO',               0, 3,  NULL, 'STRING', '头',                                1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1104, 'ORDER', 'requirement',            '客户要求,要求,Requirement',                        0, 4,  NULL, 'STRING', '头',                                1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1105, 'ORDER', 'mainboard_label',        '主板标签,主板标,Mainboard Label',                  0, 5,  NULL, 'STRING', '头',                                1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1106, 'ORDER', 'carton_label',           '外箱标签,外箱标,Carton Label',                     0, 6,  NULL, 'STRING', '头',                                1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1107, 'ORDER', 'salesman_name',          '业务员,业务,销售,销售人员,Salesman',               1, 7,  NULL, 'STRING', '头：按姓名匹配用户',                1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1108, 'ORDER', 'order_date',             '下单时间,下单日期,订单日期,Order Date',            1, 8,  NULL, 'DATE',   '头：yyyy-MM-dd / yyyy/M/d / Excel 序列', 1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1109, 'ORDER', 'expected_delivery_date', '预计交货时间,预计交货日期,交期,Delivery Date',     0, 9,  NULL, 'DATE',   '头：非必填',                        1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1110, 'ORDER', 'model',                  '型号,Model',                                       1, 10, NULL, 'STRING', '明细',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1111, 'ORDER', 'material_no',            '物料编号,料号,物料号,Material No',                 0, 11, NULL, 'STRING', '明细',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1112, 'ORDER', 'customer_material_no',   '客户物料号,客户料号,Customer Material No',         0, 12, NULL, 'STRING', '明细',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1113, 'ORDER', 'spec',                   '规格型号,规格,Spec,Specification',                 0, 13, NULL, 'STRING', '明细',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1114, 'ORDER', 'qty_ordered',            '下单数量,数量,订单数量,Qty,Quantity',              1, 14, NULL, 'NUMBER', '明细：>0，支持千分位字符串',       1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1115, 'ORDER', 'tax_rate',               '税率,Tax Rate,Tax',                                0, 15, '0',   'RATE',   '明细：13% / 0.13 / 空',            1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1116, 'ORDER', 'unit_price',             '单价,含税单价,Unit Price,Price',                   1, 16, NULL, 'NUMBER', '明细：含税与否由税率决定',         1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1117, 'ORDER', 'reconcile_date',         '对账日期,对账日,Reconcile Date',                   0, 17, NULL, 'DATE',   '明细',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1118, 'ORDER', 'remark',                 '备注,Remark,明细备注',                             0, 18, NULL, 'STRING', '明细',                              1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1119, 'ORDER', 'order_remark',           '备注订单,订单备注,订单备注信息,Order Remark',      0, 19, NULL, 'STRING', '头：[待确认]与明细备注区分',       1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);

-- ---------------------------- 出货导入（A.4，列序与现有台账一致） ----------------------------
INSERT INTO biz_import_mapping (id, module, field_name, aliases, required, column_order, default_value, field_type, remark, create_by, create_time, update_by, update_time, deleted) VALUES
(1201, 'SHIPMENT', 'ship_date',            '出货日期,出货日,发货日期,Ship Date',              1, 1,  NULL, 'DATE',   '默认今天',               1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1202, 'SHIPMENT', 'process_order_no',     '加工单号,加工单,Process Order No',                0, 2,  NULL, 'STRING', '幂等键组成部分',         1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1203, 'SHIPMENT', 'salesman_name',        '业务,业务员,销售,Salesman',                       1, 3,  NULL, 'STRING', '按姓名匹配用户',         1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1204, 'SHIPMENT', 'customer_name',        '客户,客户名称,客户名,Customer',                   1, 4,  NULL, 'STRING', '按名称匹配客户',         1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1205, 'SHIPMENT', 'customer_order_no',    '客户订单号,PO号,客户PO,Customer PO',              0, 5,  NULL, 'STRING', '用于匹配订单',           1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1206, 'SHIPMENT', 'customer_material_no', '客户物料号,客户料号,Customer Material No',        0, 6,  NULL, 'STRING', '匹配明细的优先键',       1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1207, 'SHIPMENT', 'model',                '型号,Model',                                     1, 7,  NULL, 'STRING', '',                       1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1208, 'SHIPMENT', 'qty',                  '出货数量,数量,发货数量,Qty,Quantity',             1, 8,  NULL, 'NUMBER', '>0 且 ≤ 未出货数',      1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1209, 'SHIPMENT', 'material_no',          '料号,物料编号,物料号,Material No',                0, 9,  NULL, 'STRING', '',                       1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1210, 'SHIPMENT', 'spec',                 '规格型号,规格,Spec',                              0, 10, NULL, 'STRING', '',                       1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0),
(1211, 'SHIPMENT', 'remark',               '备注,Remark',                                     0, 11, NULL, 'STRING', '',                       1, '2026-01-01 00:00:00', 1, '2026-01-01 00:00:00', 0);
