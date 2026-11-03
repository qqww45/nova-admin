package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 采集器字段自检结果回传请求。
 *
 * <p>现场跑一次「字段自检」拼出的分录字段集，此前要业务员复制报告给运维、
 * 运维手抄进后台配置页。此接口让采集器直接把自检结果写到本绑定的字段映射，
 * 运维在网页上确认即可——砍掉一次现场往返。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@Schema(name = "KdMappingUploadReq", description = "采集器字段自检结果回传")
public class KdMappingUploadReq {

    @Schema(description = "自检拼出的出库分录字段集（FieldKeys，逗号分隔）")
    @Size(max = 2000, message = "字段集过长")
    private String lineFieldKeys;

    @Schema(description = "客户采购订单号字段名（如 F_XBDR_PONo）")
    @Size(max = 64, message = "字段名过长")
    private String customerPoField;

    @Schema(description = "备注（如自检报告时间 / 账套名）")
    @Size(max = 200, message = "备注过长")
    private String note;
}
