package com.example.admin.infrastructure.persistence.converter;

import cn.hutool.core.bean.BeanUtil;
import com.example.admin.domain.model.loginlog.LoginLog;
import com.example.admin.infrastructure.persistence.po.SysLoginLog;
import org.springframework.stereotype.Component;

/**
 * 登录日志 PO ↔ 领域模型 转换器。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Component
public class LoginLogConverter {

    /**
     * PO 转领域模型。
     *
     * @param po 持久化对象
     * @return 登录日志领域对象
     */
    public LoginLog toDomain(SysLoginLog po) {
        if (po == null) {
            return null;
        }
        LoginLog loginLog = new LoginLog();
        BeanUtil.copyProperties(po, loginLog);
        return loginLog;
    }

    /**
     * 领域模型转 PO。
     *
     * @param loginLog 登录日志领域对象
     * @return 持久化对象
     */
    public SysLoginLog toPo(LoginLog loginLog) {
        if (loginLog == null) {
            return null;
        }
        SysLoginLog po = new SysLoginLog();
        BeanUtil.copyProperties(loginLog, po);
        return po;
    }
}
