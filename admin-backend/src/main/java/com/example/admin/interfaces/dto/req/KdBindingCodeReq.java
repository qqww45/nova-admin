package com.example.admin.interfaces.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 生成一次性绑定码请求。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Data
@Schema(name = "KdBindingCodeReq", description = "生成绑定码请求")
public class KdBindingCodeReq {

    @Schema(description = "备注，如“客户现场电脑”")
    private String remark;
}
