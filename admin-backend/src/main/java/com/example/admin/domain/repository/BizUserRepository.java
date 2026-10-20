package com.example.admin.domain.repository;

import com.example.admin.domain.model.common.IdName;

import java.util.Collection;
import java.util.List;

/**
 * 业务侧对系统用户（业务员）的只读查询接口。
 *
 * <p>为什么单独开一个接口而不是往 {@code UserRepository} 里加方法：
 * 客户-订单-出货只需要"按 ID 取姓名"和"按姓名取 ID"两个只读能力，
 * 单独定义可以避免改动基础框架已有的仓储接口（需求 10.6：不破坏既有）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface BizUserRepository {

    /**
     * 按 ID 批量取用户姓名。
     *
     * @param ids ID 集合
     * @return ID + 姓名列表（未找到的 ID 不出现）
     */
    List<IdName> findNames(Collection<Long> ids);

    /**
     * 按姓名（昵称或登录账号）查用户，用于导入时"按姓名匹配用户"。
     *
     * @param name 姓名
     * @return ID + 姓名，未找到返回 null
     */
    IdName findByName(String name);

    /**
     * 列出可下单的业务员（供下拉与导入字典页使用）。
     *
     * @param limit 条数上限
     * @return ID + 姓名列表
     */
    List<IdName> listSalesmen(int limit);
}
