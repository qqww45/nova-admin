package com.example.admin.domain.model.dept;

import com.example.admin.common.constant.Constants;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门聚合根（树形组织）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Data
public class Dept {

    /** 主键 */
    private Long id;

    /** 父部门 ID（顶级为 0） */
    private Long parentId;

    /** 部门名称 */
    private String deptName;

    /** 显示顺序 */
    private Integer sort;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：0 正常 1 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 子部门 */
    private List<Dept> children = new ArrayList<>();

    /**
     * 判断部门是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return Constants.STATUS_NORMAL.equals(this.status);
    }

    /**
     * 挂载子部门。
     *
     * @param child 子部门
     */
    public void addChild(Dept child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
