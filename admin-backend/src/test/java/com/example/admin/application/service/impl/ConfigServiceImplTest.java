package com.example.admin.application.service.impl;

import com.example.admin.common.exception.BusinessException;
import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.model.config.ConfigType;
import com.example.admin.domain.repository.ConfigRepository;
import com.example.admin.infrastructure.cache.ConfigCache;
import com.example.admin.infrastructure.cache.InMemoryConfigCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置服务单元测试：覆盖读路径（缓存命中 / 回源 / 默认值）、写路径与异常场景。
 */
class ConfigServiceImplTest {

    private ConfigRepository repository;
    private ConfigCache cache;
    private ConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(ConfigRepository.class);
        cache = new InMemoryConfigCache();
        service = new ConfigServiceImpl(repository, cache, new ObjectMapper());
    }

    private static ConfigItem item(String key, String value, ConfigType type, boolean builtin) {
        return ConfigItem.builder().key(key).value(value).type(type).builtin(builtin).build();
    }

    // ---------------- 读：正常流程 ----------------

    @Test
    @DisplayName("缓存命中：直接返回缓存值，不查询数据库")
    void getString_cacheHit_doesNotQueryRepository() {
        cache.put("admin.title", item("admin.title", "缓存中的标题", ConfigType.STRING, true));

        assertThat(service.getString("admin.title", "默认")).isEqualTo("缓存中的标题");
        verify(repository, never()).findByKey(any());
    }

    @Test
    @DisplayName("缓存未命中：回源数据库并回填缓存")
    void getString_cacheMiss_loadsFromRepositoryAndFillsCache() {
        when(repository.findByKey("admin.title"))
                .thenReturn(Optional.of(item("admin.title", "库中的标题", ConfigType.STRING, true)));

        assertThat(service.getString("admin.title", "默认")).isEqualTo("库中的标题");
        // 回填后第二次读应命中缓存（repository 只被调用一次）
        assertThat(service.getString("admin.title", "默认")).isEqualTo("库中的标题");
        verify(repository, org.mockito.Mockito.times(1)).findByKey("admin.title");
    }

    @Test
    @DisplayName("配置不存在：返回调用方给的默认值")
    void getString_missing_returnsDefault() {
        when(repository.findByKey(any())).thenReturn(Optional.empty());

        assertThat(service.getString("not.exist", "兜底值")).isEqualTo("兜底值");
    }

    // ---------------- 读：类型转换与边界 ----------------

    @Test
    @DisplayName("getInt：合法值正常解析")
    void getInt_valid() {
        cache.put("n", item("n", "42", ConfigType.NUMBER, true));
        assertThat(service.getInt("n", 0)).isEqualTo(42);
    }

    @Test
    @DisplayName("getInt：非法值回退默认值（不让脏数据阻塞业务）")
    void getInt_invalid_fallsBackToDefault() {
        cache.put("n", item("n", "不是数字", ConfigType.NUMBER, true));
        assertThat(service.getInt("n", 7)).isEqualTo(7);
    }

    @Test
    @DisplayName("getInt：配置不存在回退默认值")
    void getInt_missing_fallsBackToDefault() {
        when(repository.findByKey(any())).thenReturn(Optional.empty());
        assertThat(service.getInt("n", 7)).isEqualTo(7);
    }

    @Test
    @DisplayName("getBoolean：支持 true/1/yes/false/0/no（大小写不敏感）")
    void getBoolean_acceptsCommonForms() {
        cache.put("b1", item("b1", "TRUE", ConfigType.BOOLEAN, true));
        cache.put("b2", item("b2", "1", ConfigType.BOOLEAN, true));
        cache.put("b3", item("b3", "yes", ConfigType.BOOLEAN, true));
        cache.put("b4", item("b4", "false", ConfigType.BOOLEAN, true));
        cache.put("b5", item("b5", "0", ConfigType.BOOLEAN, true));
        cache.put("b6", item("b6", "No", ConfigType.BOOLEAN, true));

        assertThat(service.getBoolean("b1", false)).isTrue();
        assertThat(service.getBoolean("b2", false)).isTrue();
        assertThat(service.getBoolean("b3", false)).isTrue();
        assertThat(service.getBoolean("b4", true)).isFalse();
        assertThat(service.getBoolean("b5", true)).isFalse();
        assertThat(service.getBoolean("b6", true)).isFalse();
    }

    @Test
    @DisplayName("getBoolean：非法值回退默认值")
    void getBoolean_invalid_fallsBackToDefault() {
        cache.put("b", item("b", "maybe", ConfigType.BOOLEAN, true));
        assertThat(service.getBoolean("b", true)).isTrue();
    }

    @Test
    @DisplayName("getJson：合法 JSON 正常反序列化；非法回退默认")
    void getJson_various() {
        cache.put("j", item("j", "{\"name\":\"a\"}", ConfigType.JSON, true));
        assertThat(service.getJson("j", java.util.Map.class, null)).isNotNull();

        cache.put("bad", item("bad", "{不是json", ConfigType.JSON, true));
        assertThat(service.getJson("bad", java.util.Map.class, null)).isNull();
    }

    // ---------------- 写：正常流程 ----------------

    @Test
    @DisplayName("create：写入数据库后同步缓存")
    void create_persistsAndCaches() {
        when(repository.findByKey("new.key")).thenReturn(Optional.empty());
        when(repository.insert(any())).thenAnswer(inv -> {
            ConfigItem arg = inv.getArgument(0);
            return ConfigItem.builder().id(1L).key(arg.getKey()).value(arg.getValue())
                    .type(arg.getType()).builtin(false).remark(arg.getRemark()).build();
        });

        ConfigItem saved = service.create(item("new.key", "v", ConfigType.STRING, false));

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(cache.get("new.key")).isPresent();
        verify(repository).insert(any());
    }

    @Test
    @DisplayName("update：库与缓存一并更新")
    void update_updatesRepositoryAndCache() {
        when(repository.findByKey("k"))
                .thenReturn(Optional.of(item("k", "旧值", ConfigType.STRING, true)))
                .thenReturn(Optional.of(item("k", "新值", ConfigType.STRING, true)));

        service.update("k", "新值", "备注");

        verify(repository).update(any());
        assertThat(cache.get("k")).isPresent();
        assertThat(cache.get("k").get().getValue()).isEqualTo("新值");
    }

    @Test
    @DisplayName("delete：非内置项删除成功并清理缓存")
    void delete_nonBuiltin_evictsCache() {
        cache.put("k", item("k", "v", ConfigType.STRING, false));
        when(repository.findByKey("k")).thenReturn(Optional.of(item("k", "v", ConfigType.STRING, false)));
        when(repository.deleteByKey("k")).thenReturn(true);

        assertThat(service.delete("k")).isTrue();
        assertThat(cache.get("k")).isEmpty();
    }

    @Test
    @DisplayName("refreshAll：清空缓存并从库全量重载")
    void refreshAll_reloadsFromRepository() {
        when(repository.findAll()).thenReturn(List.of(
                item("a", "1", ConfigType.STRING, true),
                item("b", "2", ConfigType.STRING, true)));

        service.refreshAll();

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("a")).isPresent();
        assertThat(cache.get("b")).isPresent();
    }

    // ---------------- 写：异常场景 ----------------

    @Test
    @DisplayName("create：key 已存在抛业务异常")
    void create_duplicateKey_throws() {
        when(repository.findByKey("dup")).thenReturn(Optional.of(item("dup", "v", ConfigType.STRING, false)));

        assertThatThrownBy(() -> service.create(item("dup", "v", ConfigType.STRING, false)))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("create：空 key 抛业务异常")
    void create_blankKey_throws() {
        assertThatThrownBy(() -> service.create(item("  ", "v", ConfigType.STRING, false)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("create：NUMBER 类型写入非数字被拒绝")
    void create_typeMismatch_throws() {
        when(repository.findByKey("n")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(item("n", "abc", ConfigType.NUMBER, false)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("delete：内置项不允许删除")
    void delete_builtin_throws() {
        when(repository.findByKey("builtin")).thenReturn(Optional.of(item("builtin", "v", ConfigType.STRING, true)));

        assertThatThrownBy(() -> service.delete("builtin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内置配置不可删除");
        verify(repository, never()).deleteByKey(any());
    }

    @Test
    @DisplayName("update：配置不存在抛业务异常")
    void update_missing_throws() {
        when(repository.findByKey("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("ghost", "v", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("snapshot：返回缓存快照且不可被外部修改")
    void snapshot_isUnmodifiable() {
        cache.put("k", item("k", "v", ConfigType.STRING, true));

        var snap = service.snapshot();
        assertThat(snap).containsKey("k");
        assertThatThrownBy(() -> snap.put("x", item("x", "y", ConfigType.STRING, false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
