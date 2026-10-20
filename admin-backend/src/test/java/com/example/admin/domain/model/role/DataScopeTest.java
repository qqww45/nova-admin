package com.example.admin.domain.model.role;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据范围枚举与过滤条件单元测试：重点验证未知值的安全取向与三种过滤形态。
 */
class DataScopeTest {

    @Test
    @DisplayName("按 code 解析：五档一一对应")
    void of_mapsAllCodes() {
        assertThat(DataScope.of(1)).isEqualTo(DataScope.ALL);
        assertThat(DataScope.of(2)).isEqualTo(DataScope.DEPT);
        assertThat(DataScope.of(3)).isEqualTo(DataScope.DEPT_AND_CHILD);
        assertThat(DataScope.of(4)).isEqualTo(DataScope.SELF);
        assertThat(DataScope.of(5)).isEqualTo(DataScope.CUSTOM);
    }

    @Test
    @DisplayName("code 为 null：按最小权限 SELF 处理（宁可看得少）")
    void of_null_returnsSelf() {
        assertThat(DataScope.of(null)).isEqualTo(DataScope.SELF);
    }

    @Test
    @DisplayName("未知 code：按最小权限 SELF 处理（配置错误不放大权限）")
    void of_unknownCode_returnsSelf() {
        assertThat(DataScope.of(0)).isEqualTo(DataScope.SELF);
        assertThat(DataScope.of(99)).isEqualTo(DataScope.SELF);
        assertThat(DataScope.of(-1)).isEqualTo(DataScope.SELF);
    }

    @Test
    @DisplayName("枚举携带可读标签（管理界面展示用）")
    void label_isPresent() {
        assertThat(DataScope.ALL.label()).isNotBlank();
        assertThat(DataScope.DEPT.code()).isEqualTo(2);
    }

    @Test
    @DisplayName("unrestricted：不限制，且忽略部门与用户条件")
    void filter_unrestricted() {
        DataScopeFilter filter = DataScopeFilter.unrestricted();

        assertThat(filter.isUnrestricted()).isTrue();
        assertThat(filter.getDeptIds()).isEmpty();
        assertThat(filter.getSelfUserId()).isNull();
    }

    @Test
    @DisplayName("byDept：按部门集合过滤")
    void filter_byDept() {
        DataScopeFilter filter = DataScopeFilter.byDept(java.util.Set.of(1L, 2L));

        assertThat(filter.isUnrestricted()).isFalse();
        assertThat(filter.getDeptIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(filter.getSelfUserId()).isNull();
    }

    @Test
    @DisplayName("byDept：集合为 null 时转为空集合（查询侧据此判定'查不到任何数据'）")
    void filter_byDeptNull_becomesEmpty() {
        DataScopeFilter filter = DataScopeFilter.byDept(null);

        assertThat(filter.getDeptIds()).isEmpty();
    }

    @Test
    @DisplayName("bySelf：按用户本人过滤")
    void filter_bySelf() {
        DataScopeFilter filter = DataScopeFilter.bySelf(42L);

        assertThat(filter.isUnrestricted()).isFalse();
        assertThat(filter.getSelfUserId()).isEqualTo(42L);
    }
}
