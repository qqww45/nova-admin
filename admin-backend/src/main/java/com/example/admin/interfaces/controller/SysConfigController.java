package com.example.admin.interfaces.controller;

import com.example.admin.application.service.ConfigService;
import com.example.admin.common.result.R;
import com.example.admin.domain.model.config.ConfigItem;
import com.example.admin.domain.model.config.ConfigType;
import com.example.admin.interfaces.dto.req.ConfigSaveReq;
import com.example.admin.interfaces.dto.resp.ConfigResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置控制器（Interfaces 层）。
 *
 * <p>只做三件事：接收并校验参数、调用应用服务、封装统一响应。
 * 业务编排在 {@link ConfigService}，持久化在 {@code ConfigRepository}，缓存由 {@code ConfigCache} 抽象。
 *
 * <p>为什么配置要能"运行期改"：
 * 登录失败上限、锁定时长这类安全参数，业务方常需要在不发版的前提下调整；
 * 改 DB 值 + 刷缓存即可生效，比改 yml 重启要安全和快得多。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/config")
@Tag(name = "系统配置", description = "运行期可调整的系统参数")
public class SysConfigController {

    private final ConfigService configService;

    /**
     * 查询配置列表（支持按键名模糊过滤）。
     *
     * @param keyword 关键字（可为空）
     * @return 配置列表
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:config:list')")
    @Operation(summary = "查询配置列表")
    public R<List<ConfigResp>> list(@RequestParam(required = false) String keyword) {
        List<ConfigItem> items = configService.listAll();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            items = items.stream()
                    .filter(i -> i.getKey().toLowerCase().contains(kw)
                            || (i.getRemark() != null && i.getRemark().toLowerCase().contains(kw)))
                    .toList();
        }
        return R.ok(items.stream().map(SysConfigController::toResp).toList());
    }

    /**
     * 查询单条配置。
     *
     * @param key 配置键
     * @return 配置（不存在返回 null）
     */
    @GetMapping("/{key}")
    @PreAuthorize("@ss.hasPermi('system:config:list')")
    @Operation(summary = "查询单条配置")
    public R<ConfigResp> get(@PathVariable String key) {
        ConfigItem item = configService.getItem(key);
        return R.ok(item == null ? null : toResp(item));
    }

    /**
     * 新增配置。
     *
     * @param req 配置参数
     * @return 新配置
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:config:add')")
    @Operation(summary = "新增配置")
    public R<ConfigResp> add(@Valid @RequestBody ConfigSaveReq req) {
        ConfigItem saved = configService.create(ConfigItem.builder()
                .key(req.getConfigKey())
                .value(req.getConfigValue())
                .type(parseType(req.getConfigType()))
                .builtin(false)
                .remark(req.getRemark())
                .build());
        return R.ok(toResp(saved));
    }

    /**
     * 修改配置值。
     *
     * @param req 配置参数（configKey 为准）
     * @return 空响应
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    @Operation(summary = "修改配置值", description = "内置项可改值不可删除")
    public R<Void> edit(@Valid @RequestBody ConfigSaveReq req) {
        configService.update(req.getConfigKey(), req.getConfigValue(), req.getRemark());
        return R.ok();
    }

    /**
     * 删除配置。
     *
     * @param key 配置键
     * @return 空响应
     */
    @DeleteMapping("/{key}")
    @PreAuthorize("@ss.hasPermi('system:config:remove')")
    @Operation(summary = "删除配置", description = "内置配置不可删除")
    public R<Void> remove(@PathVariable String key) {
        configService.delete(key);
        return R.ok();
    }

    /**
     * 刷新缓存（多实例部署时用于同步各实例的配置缓存）。
     *
     * @return 缓存中的配置条数
     */
    @PostMapping("/refresh")
    @PreAuthorize("@ss.hasPermi('system:config:refresh')")
    @Operation(summary = "刷新配置缓存", description = "从数据库全量重新加载")
    public R<Integer> refresh() {
        configService.refreshAll();
        return R.ok(configService.snapshot().size());
    }

    /**
     * 解析类型字符串，非法值按 STRING 处理。
     */
    private ConfigType parseType(String type) {
        if (type == null || type.isBlank()) {
            return ConfigType.STRING;
        }
        try {
            return ConfigType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ConfigType.STRING;
        }
    }

    private static ConfigResp toResp(ConfigItem item) {
        return ConfigResp.builder()
                .id(item.getId())
                .configKey(item.getKey())
                .configValue(item.getValue())
                .configType(item.getType() == null ? ConfigType.STRING.name() : item.getType().name())
                .builtin(item.isBuiltin())
                .remark(item.getRemark())
                .createTime(item.getCreateTime())
                .updateTime(item.getUpdateTime())
                .build();
    }
}
