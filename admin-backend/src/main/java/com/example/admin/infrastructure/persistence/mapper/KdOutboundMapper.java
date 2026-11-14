package com.example.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.infrastructure.persistence.po.KdOutboundPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.UpdateProvider;

/**
 * KdOutboundPo 数据访问接口。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdOutboundMapper extends BaseMapper<KdOutboundPo> {

    /**
     * 按本批次未失效分录汇总，一条 SQL 回填匹配表头的 {@code amount_cents}。
     *
     * @param ownerUserId 数据归属人
     * @param acctId 账套
     * @param batchNo 批次号
     * @return 被回填的表头行数
     */
    @UpdateProvider(type = KdOutboundAmountSql.class, method = "updateAmountFromLinesByBatch")
    int updateAmountFromLinesByBatch(@Param("ownerUserId") Long ownerUserId,
                                     @Param("acctId") String acctId,
                                     @Param("batchNo") String batchNo);
}
