package com.example.admin.domain.model.role;

import com.example.admin.common.constant.Constants;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 角色聚合根。
 *
 * <p>聚合边界：角色与其菜单权限集合（role_menu）属于同一聚合，
 * 分配权限必须通过 {@link #assignMenus(List)}，保证权限集合与角色状态一致。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class Role {

    /** 主键 */
    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 角色权限字符串（唯一标识，如 common） */
    private String roleKey;

    /** 显示顺序 */
    private Integer sort;

    /** 状态：0 正常 1 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 数据权限范围（行级数据可见范围）。
     * 数据库存储为 Integer，由 {@code com.example.admin.domain.model.role.DataScope} 枚举定义语义。
     * 字段用 Integer 而非枚举：与持久化对象直接对齐，转换在仓储 / 切面层完成。
     */
    private Integer dataScope;

    /** 已分配的菜单 ID 集合 */
    private List<Long> menuIds = new ArrayList<>();

    /**
     * 判断是否为内置角色（超级管理员）。
     *
     * @return 是否内置角色
     */
    public boolean isBuiltin() {
        return Constants.SUPER_ADMIN_ROLE_KEY.equals(this.roleKey);
    }

    /**
     * 判断角色是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return Constants.STATUS_NORMAL.equals(this.status);
    }

    /**
     * 分配菜单权限（整体替换语义）。
     *
     * @param menuIds 菜单 ID 集合
     */
    public void assignMenus(List<Long> menuIds) {
        this.menuIds = menuIds == null ? new ArrayList<>() : new ArrayList<>(menuIds);
    }

    /**
     * 修改启停状态（内置角色不可停用）。
     *
     * @param newStatus 目标状态
     * @throws IllegalArgumentException 尝试停用内置超级管理员角色时抛出
     */
    public void changeStatus(Integer newStatus) {
        if (isBuiltin() && !Constants.STATUS_NORMAL.equals(newStatus)) {
            throw new IllegalArgumentException("BUILTIN_ROLE_PROTECTED");
        }
        this.status = newStatus;
    }

    /**
     * 获取菜单 ID 集合（不可变视图）。
     *
     * @return 菜单 ID 集合
     */
    public List<Long> getMenuIds() {
        // 空值保护：集合字段可能因未加载关联而为 null，领域模型对外一律返回空集合而非 null
        return this.menuIds == null ? Collections.emptyList() : Collections.unmodifiableList(this.menuIds);
    }
}
