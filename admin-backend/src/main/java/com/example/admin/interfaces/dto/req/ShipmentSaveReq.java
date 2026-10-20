package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 出货批量保存请求（界面录入、粘贴、导入三种入口共用）。
 *
 * <p>为什么批量接口要返回逐行结果：出货是高频操作，一次粘贴可能几十行，
 * 只要有一行超量就让整批失败、还要求用户重填，是不可接受的体验。
 * 因此这里"能过的先过，过不了的原样带回原因"，失败行可就地修正重提。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "ShipmentSaveReq", description = "出货批量保存请求")
public class ShipmentSaveReq {

    /** 来源：MANUAL / PASTE / IMPORT（服务端可覆盖） */
    @Schema(description = "来源：MANUAL手工 PASTE粘贴 IMPORT导入")
    private String source;

    /** 出货行 */
    @NotEmpty(message = "出货明细不能为空")
    @Valid
    @Schema(description = "出货明细", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ShipmentRowReq> rows;
}
