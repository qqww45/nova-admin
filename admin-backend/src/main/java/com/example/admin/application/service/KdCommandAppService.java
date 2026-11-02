package com.example.admin.application.service;

import com.example.admin.interfaces.dto.req.KdCommandAckReq;
import com.example.admin.interfaces.dto.req.KdCommandSendReq;
import com.example.admin.interfaces.dto.resp.KdCommandResp;

import java.util.List;

/**
 * 采集器指令应用服务。
 *
 * <p>为什么是指令表 + 短轮询，而不是方案里的 WebSocket：
 * 现场部署普遍在客户内网的反向代理后面，长连接会被中间设备掐断，
 * 而且采集器可能休眠/断网数小时。指令落库 + 15 秒轮询的语义更简单：
 * 断线期间下发的指令不会丢，客户端重启后照样能取到（未过期的话）。
 * 消息形态与 WS 一致（command + payload + 回执），将来换成 WS 只是传输层替换。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdCommandAppService {

    /**
     * 网页下发指令。
     *
     * @param bindingId 绑定 ID
     * @param req 指令
     * @return 已入队的指令
     */
    KdCommandResp send(String bindingId, KdCommandSendReq req);

    /**
     * 采集器取一条待执行指令（同时作为心跳）。
     *
     * @return 指令；没有待执行指令时返回 null
     */
    KdCommandResp poll();

    /**
     * 采集器回执。
     *
     * @param id 指令 ID
     * @param req 回执
     */
    void ack(String id, KdCommandAckReq req);

    /**
     * 最近指令（页面展示）。
     *
     * @param bindingId 绑定 ID
     * @return 指令列表，按时间倒序
     */
    List<KdCommandResp> recent(String bindingId);
}
