package com.example.admin.domain.model.role;

import java.util.Arrays;

/**
 * 数据权限范围（行级数据可见范围）。
 *
 * <p>与"功能权限"（菜单/按钮，见 {@code sys_menu.permission}）的区别：
 * 功能权限控制"能不能点这个按钮"，数据权限控制"点了之后能看到哪些行"。
 * 两者独立，一个用户可能"能看用户列表"（功能权限）但"只看得到本部门的人"（数据权限）。
 *
 * <p>生效范围（重要）：
 * <ul>
 *     <li>作用于<b>带归属字段的查询</b>：目前落地在用户列表（{@code sys_user.dept_id}）；
 *         后续业务表只要查询时读取 {@code DataScopeContext} 即可接入，无需改动本枚举；</li>
 *     <li><b>不作用于</b>：菜单树、角色列表、配置等无归属字段的系统级数据；</li>
 *     <li>超级管理员（{@code *:*:*} 或内置 admin 角色）不受任何限制，
 *         这是运维必需——否则无人能排查跨部门问题。</li>
 * </ul>
 *
 * <p>多角色时取"最宽松"的一个：用户同时拥有"本部门"和"全部数据"两个角色时按"全部数据"处理，
 * 避免出现"权限越加越小"的反直觉行为。
 *
 * <p>配置入口现状（重要）：目前<b>没有任何界面或接口</b>可以设置角色的 {@code data_scope} ——
 * 前端角色表单没有该字段，角色保存接口也不含该参数。
 * 也就是说下面各档虽然已实现，但只能靠直接修改数据库列 {@code sys_role.data_scope} 来生效
 * （默认值由 V6 迁移设置：超管=1，普通管理员=2，其余=1）。
 * 要让它可配置，需要补：后端角色保存的入参与校验 + 前端下拉。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public enum DataScope {

    /** 全部数据（不限制） */
    ALL(1, "全部数据"),

    /** 仅本部门 */
    DEPT(2, "本部门数据"),

    /** 本部门及其所有下级部门（已实现：按部门树向下展开） */
    DEPT_AND_CHILD(3, "本部门及以下数据"),

    /** 仅本人创建/拥有的数据（已实现） */
    SELF(4, "仅本人数据"),

    /**
     * 自定义部门集合（<b>未实现</b>：范围需存于 {@code sys_role_dept} 表，该表尚未创建）。
     *
     * <p>当前行为：解析时<b>降级为"本部门"</b>——这是收紧而不是放大，
     * 不存在越权风险（真正的危险是降级成"全部数据"）。
     * 该行为已由 {@code DataScopePolicyTest} 中的断言固化，实现替换时那条断言会先失败提醒更新。
     *
     * <p>实现它需要：建 {@code sys_role_dept} 表（V9 迁移）+ 角色分配部门的接口
     * + 前端部门多选组件，属于新增功能，当前未排期。
     *
     * <p>注意：将来任何一处允许设置本值（前端下拉、接口入参）之前，
     * 必须先在入参校验里拒绝它 —— 否则用户选了"自定义部门"却拿到"本部门"的数据，
     * 不是安全漏洞，但是欺骗性行为，比明确报错糟得多。
     */
    CUSTOM(5, "自定义部门数据");

    private final int code;
    private final String label;

    DataScope(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    /**
     * 按数据库存储的 code 解析，未知值一律按 {@link #SELF}（最小权限）处理。
     *
     * <p>为什么未知值取最小的 SELF 而不是 ALL：配置错误时宁可"看得少"也不要"看得多"，
     * 这是权限系统的默认安全取向。
     *
     * @param code 数据库 code
     * @return 数据范围
     */
    public static DataScope of(Integer code) {
        if (code == null) {
            return SELF;
        }
        return Arrays.stream(values())
                .filter(s -> s.code == code)
                .findFirst()
                .orElse(SELF);
    }
}
