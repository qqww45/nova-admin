package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 出货批量保存结果（成功行 / 失败行分别带回，失败行可就地修正重提）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ShipmentBatchResultResp", description = "出货批量保存结果")
public class ShipmentBatchResultResp {

    @Schema(description = "提交总行数")
    private int total;

    @Schema(description = "成功行数")
    private int successCount;

    @Schema(description = "失败行数")
    private int failCount;

    @Schema(description = "逐行结果")
    private List<BatchLineResult> results;
}
