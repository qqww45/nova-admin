package com.example.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.repository.ConfigRepository;
import com.example.admin.infrastructure.persistence.converter.ConfigConverter;
import com.example.admin.infrastructure.persistence.mapper.SysConfigMapper;
import com.example.admin.infrastructure.persistence.po.SysConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 配置仓储实现（MyBatis-Plus + 数据库）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@RequiredArgsConstructor
public class ConfigRepositoryImpl implements ConfigRepository {

    private final SysConfigMapper mapper;
    private final ConfigConverter converter;

    @Override
    public Optional<ConfigItem> findByKey(String key) {
        SysConfig po = mapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .last("LIMIT 1"));
        return Optional.ofNullable(converter.toDomain(po));
    }

    @Override
    public List<ConfigItem> findAll() {
        List<SysConfig> pos = mapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getConfigKey));
        return pos.stream().map(converter::toDomain).toList();
    }

    @Override
    public ConfigItem insert(ConfigItem item) {
        SysConfig po = converter.newPo(item.getKey(), item.getValue(), item.getType(), item.isBuiltin(), item.getRemark());
        mapper.insert(po);
        return converter.toDomain(po);
    }

    @Override
    public void update(ConfigItem item) {
        mapper.update(null, new LambdaUpdateWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, item.getKey())
                .set(SysConfig::getConfigValue, item.getValue())
                .set(SysConfig::getRemark, item.getRemark())
                .set(SysConfig::getUpdateTime, java.time.LocalDateTime.now()));
    }

    @Override
    public boolean deleteByKey(String key) {
        // 内置项不能删：在 SQL 层硬保护（即使绕过 service 也保不住）
        int rows = mapper.delete(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getBuiltin, 0));
        return rows > 0;
    }
}
