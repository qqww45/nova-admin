package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.kd.KdClientIdentity;
import com.example.admin.domain.port.KdClientContext;
import org.springframework.stereotype.Component;

/**
 * {@link KdClientContext} 的 Spring Security 实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Component
public class KdSecurityClientContext implements KdClientContext {

    @Override
    public KdClientIdentity require() {
        KdPrincipal principal = KdContext.require();
        return new KdClientIdentity(principal.bindingId(), principal.userId(), principal.acctId());
    }
}
