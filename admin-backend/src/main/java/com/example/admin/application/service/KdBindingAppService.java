package com.example.admin.application.service;

import com.example.admin.interfaces.dto.req.KdBindingCodeReq;
import com.example.admin.interfaces.dto.req.KdBindingRedeemReq;
import com.example.admin.interfaces.dto.resp.KdBindingCodeResp;
import com.example.admin.interfaces.dto.resp.KdBindingRedeemResp;
import com.example.admin.interfaces.dto.resp.KdBindingResp;

import java.util.List;

/**
 * 采集器绑定应用服务：绑定码、令牌换取 / 轮换 / 吊销。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdBindingAppService {

    /**
     * 生成一次性绑定码（5 分钟有效）。
     *
     * @param req 请求
     * @return 绑定码
     */
    KdBindingCodeResp createCode(KdBindingCodeReq req);

    /**
     * 客户端用绑定码换取机器令牌。
     *
     * @param req 请求
     * @return 令牌与水位
     */
    KdBindingRedeemResp redeem(KdBindingRedeemReq req);

    /**
     * 查询绑定列表（非管理员只看自己）。
     *
     * @return 绑定列表
     */
    List<KdBindingResp> list();

    /**
     * 吊销令牌（离职 / 换电脑）。
     *
     * @param id 绑定 ID
     */
    void revoke(String id);

    /**
     * 轮换令牌（令牌疑似泄露时使用，水位与历史数据保留）。
     *
     * @param id 绑定 ID
     * @return 新令牌
     */
    KdBindingRedeemResp refresh(String id);
}
