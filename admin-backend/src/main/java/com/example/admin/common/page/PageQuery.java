package com.example.admin.common.page;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基类。
 *
 * <p>所有分页接口入参继承本类，统一约束：
 * <ul>
 *     <li>pageNum 最小 1，pageSize 最大 100，防止一次拉全表造成数据库压力；</li>
 *     <li>排序字段必须匹配 {@code ^[A-Za-z_][A-Za-z0-9_]*$}（白名单思想），
 *         因为 MyBatis-Plus 的 {@link OrderItem} 会直接拼接字段名到 ORDER BY，
 *         若不做限制将产生 SQL 注入风险。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "PageQuery", description = "分页查询参数")
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页最大条数（防止恶意大分页拖垮数据库） */
    public static final int MAX_PAGE_SIZE = 100;

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = DEFAULT_PAGE_NUM;

    @Schema(description = "每页条数，最大 100", example = "10")
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数最大为 100")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    @Schema(description = "排序字段（数据库列名，驼峰或下划线均可）", example = "create_time")
    @Pattern(regexp = "^[A-Za-z_][A-Za-z0-9_]*$", message = "排序字段不合法")
    private String sortBy;

    @Schema(description = "排序方向：asc / desc", example = "desc")
    @Pattern(regexp = "^(?i)(asc|desc)$", message = "排序方向只能是 asc 或 desc")
    private String sortOrder;

    /**
     * 构建 MyBatis-Plus 分页对象。
     *
     * @param <T> 记录类型
     * @return 分页对象（已附加排序条件）
     */
    public <T> Page<T> buildPage() {
        int num = (pageNum == null || pageNum < 1) ? DEFAULT_PAGE_NUM : pageNum;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        Page<T> page = new Page<>(num, size);
        if (sortBy != null && !sortBy.isBlank()) {
            // sortBy 已通过正则白名单校验，可安全拼接
            boolean asc = "asc".equalsIgnoreCase(sortOrder);
            page.addOrder(asc ? OrderItem.asc(sortBy) : OrderItem.desc(sortBy));
        }
        return page;
    }
}
