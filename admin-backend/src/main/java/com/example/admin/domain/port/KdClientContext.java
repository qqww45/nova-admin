package com.example.admin.domain.port;

import com.example.admin.domain.model.kd.KdClientIdentity;

/**
 * 当前采集器身份端口。
 *
 * <p>与 {@link OperatorContext} 一样，应用层只声明"我要知道是哪个采集器在上报"，
 * 具体从 {@code X-Kd-Token} 还是别的地方取，留给基础设施层。
 *
 * @author admin-system
 * @since 2026-11-02
 */
public interface KdClientContext {

    /**
     * 取当前采集器身份。
     *
     * @return 身份
     * @throws com.example.admin.common.exception.BusinessException 未认证
     */
    KdClientIdentity require();
}
