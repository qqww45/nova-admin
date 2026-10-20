package com.example.admin.application.service;

import com.example.admin.interfaces.dto.resp.CustomerSummaryResp;
import com.example.admin.interfaces.dto.resp.HealthReportResp;

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
}
