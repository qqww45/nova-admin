package com.example.admin.common.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 字符串 ID → Long 的解析（DTO 层的 ID 是字符串，领域层用 Long）。
 *
 * <p>为什么要单独抽出来：这段逻辑原先在用户 / 角色 / 菜单 / 部门四个应用服务里各写了一份，
 * 完全一致。重复本身不是大问题，真正的问题是它们会各自漂移 —— 比如批量解析原先没有捕获
 * {@link NumberFormatException}，一旦前端传 "1,abc"，这个未捕获异常会一路冒到全局处理器
 * 变成 500，而单条解析的同一批输入却返回 1001 参数错误。同一类输入两种表现，排查起来很费劲。
 * 收敛到一处后，行为天然一致。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class IdParser {

    private IdParser() {
        // 工具类不允许实例化
    }

    /**
     * 解析单个 ID。
     *
     * @param id ID 字符串
     * @return Long 类型的 ID
     * @throws BusinessException ID 为空或格式不正确（错误码 1001）
     */
    public static Long parseId(String id) {
        if (StrUtil.isBlank(id)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "ID不能为空");
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "ID格式不正确");
        }
    }

    /**
     * 解析可为空的 ID（查询条件里的选填外键用）。
     *
     * <p>与 {@link #parseId(String)} 的区别：空串 / null 返回 null 而不是抛异常，
     * 只有"填了但格式不对"才报错 —— 避免把"没填"误判为参数错误。
     *
     * @param id ID 字符串，可为空
     * @return Long 类型的 ID；入参为空返回 null
     * @throws BusinessException 入参非空但格式不正确（错误码 1001）
     */
    public static Long parseIdOrNull(String id) {
        return StrUtil.isBlank(id) ? null : parseId(id);
    }

    /**
     * 批量解析 ID，忽略空字符串。
     *
     * @param ids ID 字符串集合
     * @return Long 集合（入参为空时返回空集合，不会返回 null）
     * @throws BusinessException 任一 ID 格式不正确（错误码 1001，不再是 500）
     */
    public static List<Long> parseIds(List<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return new ArrayList<>();
        }
        List<Long> result = new ArrayList<>();
        for (String id : ids) {
            if (StrUtil.isNotBlank(id)) {
                // 走 parseId 而不是直接 Long.parseLong：非法格式应返回参数错误，
                // 而不是让 NumberFormatException 冒到全局处理器变成 500
                result.add(parseId(id));
            }
        }
        return result;
    }
}
