package com.example.admin.infrastructure.config;

import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.repository.ConfigRepository;
import com.example.admin.infrastructure.cache.ConfigCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动期把数据库中的配置全量加载到缓存。
 *
 * <p>为什么不"懒加载"（读时按需回源 DB）：
 * 配置是高频读；懒加载会让首屏每个请求都穿透到 DB，缓存就失去意义了。
 * 全量加载是 O(N) 的 DB 查询，N 是配置项数量（一般 < 100），启动期可接受。
 *
 * <p>为什么不用 {@code ApplicationRunner}：要在 {@code CommandLineRunner} 之前完成，
 * 让后续启动校验（如 {@code VerifyConfigGuard}）能直接读到配置。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigBootstrap {

    private final ConfigRepository configRepository;
    private final ConfigCache configCache;

    @PostConstruct
    public void loadAllOnStartup() {
        List<ConfigItem> items = configRepository.findAll();
        configCache.clear();
        for (ConfigItem item : items) {
            configCache.put(item.getKey(), item);
        }
        log.info("系统配置已加载到缓存：共 {} 条", configCache.size());
    }
}
