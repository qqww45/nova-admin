package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史数据体检报告（附录A A.6）。
 *
 * <p>设计取舍：报告按"问题类别 + 明细条目"返回，而不是四个各自独立的接口。
 * 使用者要的是"我这份历史数据能不能直接用"，一份报告一次看完；
 * 拆成四个接口只会让人点四次、再自己拼结论。
 *
 * <p>明细条目有上限（每类 {@code MAX_ISSUES} 条）：体检是给人看的清单，
 * 不是数据导出，真要全量修应该去异常数据池分页处理。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "HealthReportResp", description = "历史数据体检报告")
public class HealthReportResp {

    /** 每类问题最多返回的明细条数 */
    public static final int MAX_ISSUES = 200;

    @Schema(description = "客户总数")
    private int customerCount;

    @Schema(description = "订单总数")
    private int orderCount;

    @Schema(description = "明细总行数")
    private int itemCount;

    @Schema(description = "出货记录总数")
    private int shipmentCount;

    @Schema(description = "各类问题条数：key=类别编码，value=条数")
    private Map<String, Integer> counts = new LinkedHashMap<>();

    @Schema(description = "问题明细")
    private List<HealthIssue> issues = new ArrayList<>();

    /**
     * 体检问题条目。
     */
    @Data
    @Schema(name = "HealthIssue", description = "体检问题条目")
    public static class HealthIssue {

        @Schema(description = "类别：DUPLICATE_CUSTOMER / MISSING_MATERIAL_NO / QUANTITY_ANOMALY / TAX_RATE_ANOMALY / ORPHAN_SHIPMENT")
        private String category;

        @Schema(description = "类别名称")
        private String categoryLabel;

        @Schema(description = "问题对象（客户名 / 订单号 等）")
        private String subject;

        @Schema(description = "具体说明")
        private String detail;
    }
}
