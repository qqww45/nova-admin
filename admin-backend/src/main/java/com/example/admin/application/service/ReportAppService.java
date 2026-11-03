package com.example.admin.application.service;

import com.example.admin.interfaces.dto.resp.CustomerSummaryResp;
import com.example.admin.interfaces.dto.resp.HealthReportResp;
import com.example.admin.interfaces.dto.resp.OpenOrderItemResp;

import java.time.LocalDate;
import java.util.List;

/**
 * 报表与体检服务（M5 / 附录A A.6）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface ReportAppService {

    /**
     * 生成历史数据体检报告：重复客户、缺料号、数量异常、税率异常、无源出货。
     *
     * @return 体检报告
     */
    HealthReportResp health();

    /**
     * 客户出货汇总。
     *
     * @param begin 下单时间起（为空表示不限）
     * @param end   下单时间止（为空表示不限）
     * @return 按客户聚合的汇总列表（按下单总量倒序）
     */
    List<CustomerSummaryResp> customerSummary(LocalDate begin, LocalDate end);

    /**
     * 催单清单：全部未交订单明细（qtyOpen > 0 且未取消），按客户聚合排序。
     *
     * <p>业务员催单的真实动作是"按客户要货"，因此行序先按客户再按下单时间，
     * 相邻行就是同一客户的待交明细，打印或截图即可去催生产、回复客户。
     *
     * @return 未交明细列表
     */
    List<OpenOrderItemResp> openItems();
}
