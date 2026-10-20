package com.example.admin.infrastructure.persistence.converter;

import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.model.config.ConfigType;
import com.example.admin.infrastructure.persistence.po.SysConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 系统配置 PO ↔ 领域模型 转换器。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class ConfigConverter {

    /**
     * PO 转领域值对象。
     *
     * <p>非法 configType 一律按 STRING 处理：避免脏数据导致业务层崩溃。
     */
    public ConfigItem toDomain(SysConfig po) {
        if (po == null) {
            return null;
        }
        ConfigType type;
        try {
            type = ConfigType.valueOf(po.getConfigType());
        } catch (IllegalArgumentException | NullPointerException ex) {
            type = ConfigType.STRING;
        }
        return ConfigItem.builder()
                .id(po.getId())
                .key(po.getConfigKey())
                .value(po.getConfigValue())
                .type(type)
                .builtin(po.getBuiltin() != null && po.getBuiltin() == 1)
                .remark(po.getRemark())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    /**
     * 领域值对象转 PO。
     */
    public SysConfig toPo(ConfigItem item) {
        if (item == null) {
            return null;
        }
        SysConfig po = new SysConfig();
        po.setId(item.getId());
        po.setConfigKey(item.getKey());
        po.setConfigValue(item.getValue());
        po.setConfigType(item.getType() == null ? ConfigType.STRING.name() : item.getType().name());
        po.setBuiltin(item.isBuiltin() ? 1 : 0);
        po.setRemark(item.getRemark());
        return po;
    }

    /**
     * 从零构造新 PO（用于新增）。
     */
    public SysConfig newPo(String key, String value, ConfigType type, boolean builtin, String remark) {
        SysConfig po = new SysConfig();
        po.setConfigKey(key);
        po.setConfigValue(value);
        po.setConfigType(type.name());
        po.setBuiltin(builtin ? 1 : 0);
        po.setRemark(remark);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        return po;
    }
}
