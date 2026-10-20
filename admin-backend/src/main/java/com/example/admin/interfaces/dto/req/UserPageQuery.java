package com.example.admin.interfaces.dto.req;

import com.example.admin.common.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户分页查询条件。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "UserPageQuery", description = "用户分页查询条件")
public class UserPageQuery extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 用户名/昵称模糊匹配 */
    @Schema(description = "用户名或昵称（模糊匹配）")
    private String keyword;

    /** 手机号精确/模糊匹配 */
    @Schema(description = "手机号")
    private String phone;

    /** 状态：0 正常 1 停用 */
    @Schema(description = "状态：0正常 1停用")
    private Integer status;

    /** 部门 ID（查询该部门及子部门时可传多个，由 Service 处理） */
    @Schema(description = "部门ID")
    private String deptId;

    /** 创建时间起（yyyy-MM-dd HH:mm:ss） */
    @Schema(description = "创建时间起", example = "2026-01-01 00:00:00")
    private LocalDateTime beginTime;

    /** 创建时间止（yyyy-MM-dd HH:mm:ss） */
    @Schema(description = "创建时间止", example = "2026-12-31 23:59:59")
    private LocalDateTime endTime;
}
