package com.example.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.infrastructure.persistence.po.BizReceipt;

/**
 * 收款登记数据访问接口。
 *
 * <p>全部查询走 BaseMapper + LambdaQueryWrapper（本工程不使用 mapper XML）。
 *
 * @author admin-system
 * @since 2026-10-22
 */
public interface BizReceiptMapper extends BaseMapper<BizReceipt> {
}
