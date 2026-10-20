package com.example.admin.interfaces.controller;

import com.example.admin.common.result.R;
import com.example.admin.common.util.SensitiveUtils;
import com.example.admin.interfaces.dto.resp.LogLineResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 在线日志查看控制器（单机排障用）。
 *
 * <p>定位：给运维在"没有分布式日志系统"或"临时需要看一眼本机"时提供快速入口。
 * 它不是日志系统的替代品——多实例环境下每台机器的日志是分开的，
 * 要跨实例检索必须依赖集中式日志（见 {@code docs/系统日志设计.md}）。
 *
 * <p>安全约束：
 * <ul>
 *     <li>只允许读取配置指定的日志文件路径，<b>不接受调用方传入路径</b>（防目录遍历）；</li>
 *     <li>最多返回 1000 行（防止一次拉取过大响应）；</li>
 *     <li>需要 {@code system:log:list} 权限。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@RestController
@RequestMapping("/system/log")
@Tag(name = "系统日志", description = "在线查看本机日志文件")
public class SysLogController {

    /** 单次返回的最大行数 */
    private static final int MAX_LINES = 1000;

    /** 默认返回行数 */
    private static final int DEFAULT_LINES = 200;

    /** 日志文件路径（由配置指定，不接受外部传参） */
    @Value("${logging.file.name:./logs/admin-backend.log}")
    private String logFilePath;

    /**
     * 查看日志文件尾部（可按级别与关键字过滤）。
     *
     * @param lines   返回行数（1~1000，默认 200）
     * @param level   级别过滤：ERROR / WARN / INFO / DEBUG（可为空）
     * @param keyword 关键字过滤（可为空）
     * @return 日志行列表（按文件中的时间顺序，最新在最后）
     */
    @GetMapping("/tail")
    @PreAuthorize("@ss.hasPermi('system:log:list')")
    @Operation(summary = "查看日志尾部", description = "读取配置指定的日志文件；不支持自定义路径")
    public R<List<LogLineResp>> tail(@RequestParam(defaultValue = "200") int lines,
                                     @RequestParam(required = false) String level,
                                     @RequestParam(required = false) String keyword) {
        int want = Math.min(Math.max(lines, 1), MAX_LINES);
        List<String> matched = readTail(Paths.get(logFilePath), want, level, keyword);
        // 日志正文可能包含验证码、令牌、口令（开发模式为便于自测会打印明文），
        // 而"查看日志"这个权限往往给得很松 —— 原样返回等于把凭据开放给所有能看到日志的人。
        // 排查问题需要的是时间、级别、堆栈与业务 ID，不是完整的令牌，所以统一抹掉后再返回。
        return R.ok(matched.stream()
                .map(SensitiveUtils::maskSecrets)
                .map(content -> LogLineResp.builder().content(content).build())
                .toList());
    }

    /**
     * 读取文件尾部满足条件的行。
     *
     * <p>为什么用"循环队列"而不是 {@code Files.readAllLines}：
     * 日志文件可能达到数百 MB，一次性读进内存会直接把应用拖垮；
     * 这里只在内存里保留最近的 N 行，内存占用恒定。
     */
    private List<String> readTail(Path path, int maxLines, String level, String keyword) {
        if (!Files.isRegularFile(path)) {
            log.warn("日志文件不存在或不是普通文件：{}", path);
            return List.of();
        }
        String levelToken = (level == null || level.isBlank()) ? null : " " + level.trim().toUpperCase() + " ";
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        // 只保留最近 maxLines 条匹配行：ArrayDeque 满则丢弃最旧的
        Deque<String> recent = new ArrayDeque<>(maxLines);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (levelToken != null && !line.contains(levelToken)) {
                    continue;
                }
                if (kw != null && !line.contains(kw)) {
                    continue;
                }
                if (recent.size() == maxLines) {
                    recent.pollFirst();
                }
                recent.addLast(line);
            }
        } catch (IOException ex) {
            log.error("读取日志文件失败 path={} cause={}", path, ex.getMessage());
            return List.of();
        }
        return new ArrayList<>(recent);
    }
}
