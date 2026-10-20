package com.example.admin.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应体。
 *
 * <p>统一封装 {@code { records, total, pageNum, pageSize, pages }}，前端表格组件
 * 只依赖这一个结构即可完成分页渲染。
 *
 * @param <T> 记录类型
 * @author admin-system
 * @since 2026-01-01
 */
@Data
@Schema(name = "PageResult", description = "分页响应体")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页数据")
    private List<T> records;

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "当前页码")
    private long pageNum;

    @Schema(description = "每页条数")
    private long pageSize;

    @Schema(description = "总页数")
    private long pages;

    /**
     * 由 MyBatis-Plus 分页对象转换为统一分页响应。
     *
     * @param page MyBatis-Plus 分页结果
     * @param <T>  记录类型
     * @return 分页响应体
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords() == null ? Collections.emptyList() : page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }

    /**
     * 构造空分页结果（用于无数据场景，避免前端处理 null）。
     *
     * @param <T> 记录类型
     * @return 空分页响应体
     */
    public static <T> PageResult<T> empty() {
        PageResult<T> result = new PageResult<>();
        result.setRecords(Collections.emptyList());
        return result;
    }
}
