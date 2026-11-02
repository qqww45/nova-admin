package com.example.admin.interfaces.controller;

import com.example.admin.application.service.KdCommandAppService;
import com.example.admin.application.service.KdSyncAppService;
import com.example.admin.common.result.PageResult;
import com.example.admin.common.result.R;
import com.example.admin.interfaces.dto.req.KdBatchPageQuery;
import com.example.admin.interfaces.dto.req.KdCommandAckReq;
import com.example.admin.interfaces.dto.req.KdCustomerSyncReq;
import com.example.admin.interfaces.dto.req.KdOutboundLineSyncReq;
import com.example.admin.interfaces.dto.req.KdOutboundSyncReq;
import com.example.admin.interfaces.dto.req.KdSyncBatchFinishReq;
import com.example.admin.interfaces.dto.req.KdSyncBatchStartReq;
import com.example.admin.interfaces.dto.resp.KdClientTaskResp;
import com.example.admin.interfaces.dto.resp.KdCommandResp;
import com.example.admin.interfaces.dto.resp.KdSyncBatchResp;
import com.example.admin.interfaces.dto.resp.KdSyncResultResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采集器上行同步控制器。
 *
 * <p>鉴权分两套，路径前缀不同：
 * <ul>
 *     <li>{@code /kd/sync/tasks}、{@code /kd/sync/batch}、{@code /kd/sync/customer|outbound|outbound-line}
 *         走 {@code X-Kd-Token}（采集器令牌）；</li>
 *     <li>{@code /kd/sync/batch/list} 是后台页面，走 JWT + {@code kd:sync:list} 权限。</li>
 * </ul>
 *
 * <p>成功返回 200 是客户端的"可以推进本地水位"信号，因此落库失败会直接抛异常返回非 200。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/kd/sync")
@Tag(name = "金蝶同步", description = "任务清单下发与批量上报")
public class KdSyncController {

    private final KdSyncAppService syncService;
    private final KdCommandAppService commandService;

    /**
     * 下发任务清单。
     *
     * @return 任务列表
     */
    @GetMapping("/tasks")
    @Operation(summary = "下发任务清单（采集器令牌）")
    public R<List<KdClientTaskResp>> tasks() {
        return R.ok(syncService.clientTasks());
    }

    /**
     * 开启批次。
     *
     * @param req 请求
     * @return 操作结果
     */
    @PostMapping("/batch")
    @Operation(summary = "开启同步批次（采集器令牌）")
    public R<Void> startBatch(@Valid @RequestBody KdSyncBatchStartReq req) {
        syncService.startBatch(req);
        return R.ok();
    }

    /**
     * 上报客户。
     *
     * @param req 请求
     * @return 结果
     */
    @PostMapping("/customer")
    @Operation(summary = "上报客户（采集器令牌）")
    public R<KdSyncResultResp> uploadCustomers(@Valid @RequestBody KdCustomerSyncReq req) {
        return R.ok(syncService.uploadCustomers(req));
    }

    /**
     * 上报出库单头。
     *
     * @param req 请求
     * @return 结果
     */
    @PostMapping("/outbound")
    @Operation(summary = "上报出库单头（采集器令牌）")
    public R<KdSyncResultResp> uploadOutbounds(@Valid @RequestBody KdOutboundSyncReq req) {
        return R.ok(syncService.uploadOutbounds(req));
    }

    /**
     * 上报出库分录。
     *
     * @param req 请求
     * @return 结果
     */
    @PostMapping("/outbound-line")
    @Operation(summary = "上报出库分录（采集器令牌）")
    public R<KdSyncResultResp> uploadLines(@Valid @RequestBody KdOutboundLineSyncReq req) {
        return R.ok(syncService.uploadLines(req));
    }

    /**
     * 批次结束回报。
     *
     * @param batchId 批次号
     * @param req 请求
     * @return 操作结果
     */
    @PostMapping("/batch/{batchId}/finish")
    @Operation(summary = "批次结束回报（采集器令牌）")
    public R<Void> finishBatch(@PathVariable String batchId,
                               @Valid @RequestBody KdSyncBatchFinishReq req) {
        syncService.finishBatch(batchId, req);
        return R.ok();
    }

    /**
     * 取一条待执行指令（采集器令牌）。
     *
     * <p>同时充当心跳：服务端会刷新 {@code kd_binding.last_seen_at}。
     * 没有指令时返回 {@code data = null}，客户端按"空闲"处理即可。
     *
     * @return 指令或 null
     */
    @GetMapping("/commands")
    @Operation(summary = "取一条待执行指令（采集器令牌）")
    public R<KdCommandResp> pollCommand() {
        return R.ok(commandService.poll());
    }

    /**
     * 指令回执（采集器令牌）。
     *
     * @param id 指令 ID
     * @param req 回执
     * @return 操作结果
     */
    @PostMapping("/commands/{id}/ack")
    @Operation(summary = "指令回执（采集器令牌）")
    public R<Void> ackCommand(@PathVariable String id, @Valid @RequestBody KdCommandAckReq req) {
        commandService.ack(id, req);
        return R.ok();
    }

    /**
     * 分页查询批次（后台页面）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/batch/list")
    @PreAuthorize("@ss.hasPermi('kd:sync:list')")
    @Operation(summary = "分页查询同步批次")
    public R<PageResult<KdSyncBatchResp>> batchList(@Valid KdBatchPageQuery query) {
        return R.ok(syncService.batchPage(query));
    }
}
