package com.example.admin.interfaces.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 有采集器绑定的用户（业务员）下拉项。
 *
 * <p>管理员在排查页按业务员过滤时使用；只暴露 ID + 姓名，
 * 不含账号、令牌等敏感信息。
 *
 * @author admin-system
 * @since 2026-11-03
 */
@Data
@Schema(name = "KdOwnerResp", description = "金蝶数据归属人（业务员）")
public class KdOwnerResp {

    @Schema(description = "用户 ID")
    private String id;

    @Schema(description = "用户姓名")
    private String name;
}
