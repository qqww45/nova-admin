package com.example.admin.infrastructure.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.example.admin.common.constant.Constants;
import com.example.admin.infrastructure.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充处理器。
 *
 * <p>统一为实体填充 {@code createBy / createTime / updateBy / updateTime / deleted}，
 * 业务代码无需关心审计字段，避免遗漏造成的数据不完整。
 *
 * <p>实现说明：这里采用手动 {@code hasSetter + 空值判断} 而非
 * {@code strictInsertFill}，原因是部分关联实体（如 {@code sys_user_role}）只有部分审计字段，
 * 手动判断可兼容所有实体，且不依赖 TableInfo 解析结果，行为更可预期。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
public class FillMetaObjectHandler implements MetaObjectHandler {

    /** 创建者字段名 */
    private static final String FIELD_CREATE_BY = "createBy";

    /** 创建时间字段名 */
    private static final String FIELD_CREATE_TIME = "createTime";

    /** 更新者字段名 */
    private static final String FIELD_UPDATE_BY = "updateBy";

    /** 更新时间字段名 */
    private static final String FIELD_UPDATE_TIME = "updateTime";

    /** 逻辑删除字段名 */
    private static final String FIELD_DELETED = "deleted";

    /**
     * 插入时填充。
     *
     * @param metaObject 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Long userId = SecurityUtils.getUserIdOrNull();
        LocalDateTime now = LocalDateTime.now();

        fillIfAbsent(metaObject, FIELD_CREATE_TIME, now);
        fillIfAbsent(metaObject, FIELD_CREATE_BY, userId == null ? Constants.SUPER_ADMIN_USER_ID : userId);
        fillIfAbsent(metaObject, FIELD_UPDATE_TIME, now);
        fillIfAbsent(metaObject, FIELD_UPDATE_BY, userId == null ? Constants.SUPER_ADMIN_USER_ID : userId);
        fillIfAbsent(metaObject, FIELD_DELETED, Constants.NOT_DELETED);
    }

    /**
     * 更新时填充。
     *
     * @param metaObject 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        Long userId = SecurityUtils.getUserIdOrNull();
        LocalDateTime now = LocalDateTime.now();

        setIfPresent(metaObject, FIELD_UPDATE_TIME, now);
        setIfPresent(metaObject, FIELD_UPDATE_BY, userId == null ? Constants.SUPER_ADMIN_USER_ID : userId);
    }

    /**
     * 当实体存在该字段且当前值为空时填充（插入场景使用）。
     *
     * @param metaObject 元对象
     * @param fieldName  字段名
     * @param value      填充值
     */
    private void fillIfAbsent(MetaObject metaObject, String fieldName, Object value) {
        if (metaObject.hasSetter(fieldName) && metaObject.getValue(fieldName) == null) {
            metaObject.setValue(fieldName, value);
        }
    }

    /**
     * 当实体存在该字段时强制覆盖（更新场景使用）。
     *
     * @param metaObject 元对象
     * @param fieldName  字段名
     * @param value      填充值
     */
    private void setIfPresent(MetaObject metaObject, String fieldName, Object value) {
        if (metaObject.hasSetter(fieldName)) {
            metaObject.setValue(fieldName, value);
        }
    }
}
