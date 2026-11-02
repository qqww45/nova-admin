package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.kd.KdBinding;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.service.KdSecrets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 采集器机器令牌校验。
 *
 * <p>令牌 32 字节随机、库里只存 SHA-256；校验就是"摘要查表 + 状态判断"，O(1)。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KdTokenService {

    private final KdBindingRepository bindingRepository;

    /**
     * 校验令牌。
     *
     * @param token 令牌原文
     * @return 身份；令牌为空 / 不存在 / 已吊销时返回 empty
     */
    public Optional<KdPrincipal> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        KdBinding binding = bindingRepository.findByTokenHash(KdSecrets.sha256Hex(token.trim()));
        if (binding == null || !binding.isActive()) {
            log.debug("采集器令牌无效或已吊销");
            return Optional.empty();
        }
        bindingRepository.touch(binding.getId(), LocalDateTime.now());
        return Optional.of(new KdPrincipal(binding.getId(), binding.getUserId(), binding.getAcctId()));
    }
}
