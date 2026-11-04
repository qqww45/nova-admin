package com.example.admin.application.service.impl;

import com.example.admin.application.service.KdBindingAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.common.IdName;
import com.example.admin.domain.model.kd.KdBinding;
import com.example.admin.domain.model.kd.KdBindingCode;
import com.example.admin.domain.model.kd.KdBindingStatus;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.domain.repository.KdBindingCodeRepository;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.repository.KdCustomerRepository;
import com.example.admin.domain.repository.KdOutboundRepository;
import com.example.admin.domain.service.KdSecrets;
import com.example.admin.interfaces.dto.req.KdBindingCodeReq;
import com.example.admin.interfaces.dto.req.KdBindingRedeemReq;
import com.example.admin.interfaces.dto.resp.KdBindingCodeResp;
import com.example.admin.interfaces.dto.resp.KdBindingRedeemResp;
import com.example.admin.interfaces.dto.resp.KdBindingResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采集器绑定应用服务实现。
 *
 * <p>不变量：
 * <ul>
 *     <li>令牌原文不落库，只在换取 / 轮换时返回一次；</li>
 *     <li>绑定码一次一用、5 分钟过期，兑换用 {@code used_at IS NULL} 做抢占；</li>
 *     <li>重新绑定沿用旧水位，避免"换台电脑就全量重拉"。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KdBindingAppServiceImpl implements KdBindingAppService {

    /** 绑定码有效期（分钟） */
    private static final int CODE_TTL_MINUTES = 5;

    /** 令牌展示前缀长度 */
    private static final int PREFIX_LENGTH = 8;

    /** 与 JacksonConfig 保持一致的输出格式（客户端直接拿去拼 FilterString） */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KdBindingRepository bindingRepository;
    private final KdBindingCodeRepository codeRepository;
    private final KdCustomerRepository customerRepository;
    private final KdOutboundRepository outboundRepository;
    private final BizUserRepository bizUserRepository;
    private final OperatorContext operatorContext;

    @Override
    public KdBindingCodeResp createCode(KdBindingCodeReq req) {
        Long userId = operatorContext.currentUserId();
        String code = KdSecrets.newBindCode();
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES);

        KdBindingCode entity = new KdBindingCode();
        entity.setUserId(userId);
        entity.setCodeHash(KdSecrets.sha256Hex(code));
        entity.setExpireAt(expireAt);
        entity.setRemark(req == null ? null : req.getRemark());
        codeRepository.save(entity);

        KdBindingCodeResp resp = new KdBindingCodeResp();
        resp.setCode(code);
        resp.setExpireAt(expireAt);
        resp.setExpireSeconds(Duration.between(LocalDateTime.now(), expireAt).toSeconds());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KdBindingRedeemResp redeem(KdBindingRedeemReq req) {
        String normalized = KdSecrets.normalizeCode(req.getCode());
        if (normalized.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "绑定码不能为空");
        }
        KdBindingCode code = codeRepository.findByHash(KdSecrets.sha256Hex(normalized));
        LocalDateTime now = LocalDateTime.now();
        if (code == null || !code.isUsable(now)) {
            // 不区分"不存在 / 已用 / 过期"：绑定码是短凭证，差异化提示只会帮攻击者试探
            throw new BusinessException(ResultCode.CHALLENGE_INVALID, "绑定码无效或已过期，请重新生成");
        }
        if (!codeRepository.markUsed(code.getId(), now)) {
            throw new BusinessException(ResultCode.CHALLENGE_INVALID, "绑定码已被使用，请重新生成");
        }

        String token = KdSecrets.newToken();
        KdBinding binding = bindingRepository.findByUserAndAcct(code.getUserId(), req.getAcctId());
        if (binding == null) {
            binding = new KdBinding();
            binding.setUserId(code.getUserId());
            binding.setAcctId(req.getAcctId());
            binding.setStatus(KdBindingStatus.ACTIVE.getCode());
            fillClientInfo(binding, req);
            binding.setTokenHash(KdSecrets.sha256Hex(token));
            binding.setTokenPrefix(token.substring(0, PREFIX_LENGTH));
            // 仓储只返回主键，不回填领域对象；不补这一步 toRedeemResp 会返回 bindingId=null，
            // 客户端据此判断"是否已绑定"，于是明明换了令牌却显示未绑定
            Long newId = bindingRepository.save(binding);
            if (binding.getId() == null) {
                binding.setId(newId);
            }
        } else {
            // 重新绑定：换令牌、恢复启用，但水位与历史数据保留
            fillClientInfo(binding, req);
            binding.setStatus(KdBindingStatus.ACTIVE.getCode());
            binding.setTokenHash(KdSecrets.sha256Hex(token));
            binding.setTokenPrefix(token.substring(0, PREFIX_LENGTH));
            bindingRepository.update(binding);
        }
        log.info("账套 {} 绑定成功：userId={}, bindingId={}", req.getAcctId(), binding.getUserId(), binding.getId());
        return toRedeemResp(binding, token);
    }

    @Override
    public List<KdBindingResp> list() {
        LoginUser loginUser = operatorContext.current();
        Long userId = loginUser == null ? null : loginUser.getUserId();
        boolean admin = isAdmin(loginUser);
        List<KdBinding> bindings = bindingRepository.listByUser(admin ? null : userId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = loadUserNames(bindings);
        List<KdBindingResp> result = new ArrayList<>(bindings.size());
        for (KdBinding binding : bindings) {
            KdBindingResp resp = new KdBindingResp();
            resp.setId(String.valueOf(binding.getId()));
            resp.setUserId(String.valueOf(binding.getUserId()));
            resp.setUserName(names.getOrDefault(binding.getUserId(), ""));
            resp.setAcctId(binding.getAcctId());
            resp.setAcctName(binding.getAcctName());
            resp.setKdUsername(binding.getKdUsername());
            resp.setKdServerUrl(binding.getKdServerUrl());
            resp.setMachineName(binding.getMachineName());
            resp.setClientVersion(binding.getClientVersion());
            resp.setTokenPrefix(binding.getTokenPrefix());
            resp.setStatus(binding.getStatus());
            resp.setStatusLabel(statusLabel(binding.getStatus()));
            resp.setLastUsedAt(binding.getLastUsedAt());
            resp.setLastSyncAt(binding.getLastSyncAt());
            resp.setLastSyncResult(binding.getLastSyncResult());
            resp.setWatermarkCustomer(binding.getWatermarkCustomer());
            resp.setWatermarkOutbound(binding.getWatermarkOutbound());
            resp.setCreateTime(binding.getCreateTime());
            if (binding.getAcctId() != null) {
                resp.setCustomerCount(customerRepository.countByOwner(binding.getUserId(), binding.getAcctId()));
                resp.setOutboundCount(outboundRepository.countByOwner(binding.getUserId(), binding.getAcctId()));
                fillSyncDisplay(resp, binding);
            }
            result.add(resp);
        }
        return result;
    }

    @Override
    public void revoke(String id) {
        KdBinding binding = requireOwnedBinding(IdParser.parseId(id));
        KdBinding update = new KdBinding();
        update.setId(binding.getId());
        update.setStatus(KdBindingStatus.REVOKED.getCode());
        bindingRepository.update(update);
        log.info("吊销采集器令牌：bindingId={}, userId={}", binding.getId(), binding.getUserId());
    }

    @Override
    public KdBindingRedeemResp refresh(String id) {
        KdBinding binding = requireOwnedBinding(IdParser.parseId(id));
        String token = KdSecrets.newToken();
        KdBinding update = new KdBinding();
        update.setId(binding.getId());
        update.setTokenHash(KdSecrets.sha256Hex(token));
        update.setTokenPrefix(token.substring(0, PREFIX_LENGTH));
        update.setStatus(KdBindingStatus.ACTIVE.getCode());
        bindingRepository.update(update);
        binding.setTokenPrefix(update.getTokenPrefix());
        return toRedeemResp(binding, token);
    }

    /**
     * 校验绑定是否存在且当前用户有权操作（管理员不受限）。
     *
     * @param id 绑定 ID
     * @return 绑定
     */
    private KdBinding requireOwnedBinding(Long id) {
        KdBinding binding = bindingRepository.findById(id);
        if (binding == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "绑定不存在");
        }
        LoginUser loginUser = operatorContext.current();
        if (!isAdmin(loginUser) && !binding.getUserId().equals(operatorContext.currentUserId())) {
            // 返回"不存在"而不是"无权限"：不暴露他人绑定 ID 是否存在
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "绑定不存在");
        }
        return binding;
    }

    /**
     * 绑定表水位 / 上次同步为空时，用已落库数据回填展示字段。
     *
     * <p>采集器 1.2.x 上报客户/出库块时不带 {@code watermarkTo}，绑定行上的水位列
     * 从未被推进；批次 finish 若因 ISO 时间解析失败，{@code last_sync_at} 也不会写上。
     * 但客户/出库行已经落库（列表「客户 / 出库」计数有值），这里取真实的
     * {@code sync_at} / {@code kd_modify_time}，避免页面显示「—」「从未同步」。
     *
     * @param resp 列表项
     * @param binding 绑定
     */
    private void fillSyncDisplay(KdBindingResp resp, KdBinding binding) {
        boolean needCustomerWm = resp.getWatermarkCustomer() == null;
        boolean needOutboundWm = resp.getWatermarkOutbound() == null;
        boolean needLastSync = resp.getLastSyncAt() == null;
        if (!needCustomerWm && !needOutboundWm && !needLastSync) {
            return;
        }
        LocalDateTime customerSync = null;
        LocalDateTime outboundSync = null;
        if (needCustomerWm || needLastSync) {
            customerSync = customerRepository.maxSyncAt(binding.getUserId(), binding.getAcctId());
        }
        if (needOutboundWm || needLastSync) {
            outboundSync = outboundRepository.maxSyncAt(binding.getUserId(), binding.getAcctId());
        }
        if (needCustomerWm) {
            resp.setWatermarkCustomer(customerSync);
        }
        if (needOutboundWm) {
            LocalDateTime modifyTime = outboundRepository.maxModifyTime(binding.getUserId(), binding.getAcctId());
            resp.setWatermarkOutbound(modifyTime != null ? modifyTime : outboundSync);
        }
        if (needLastSync) {
            resp.setLastSyncAt(KdBinding.maxWatermark(customerSync, outboundSync));
        }
    }

    /**
     * 回填客户端信息。
     *
     * @param binding 绑定
     * @param req 请求
     */
    private void fillClientInfo(KdBinding binding, KdBindingRedeemReq req) {
        binding.setAcctName(req.getAcctName());
        binding.setKdUsername(req.getKdUsername());
        binding.setKdServerUrl(req.getKdServerUrl());
        binding.setMachineName(req.getMachineName());
        binding.setClientVersion(req.getClientVersion());
    }

    /**
     * 构造换取令牌响应。
     *
     * @param binding 绑定
     * @param token 令牌原文
     * @return 响应
     */
    private KdBindingRedeemResp toRedeemResp(KdBinding binding, String token) {
        KdBindingRedeemResp resp = new KdBindingRedeemResp();
        resp.setToken(token);
        resp.setBindingId(String.valueOf(binding.getId()));
        resp.setUserId(String.valueOf(binding.getUserId()));
        resp.setAcctId(binding.getAcctId());
        resp.setAcctName(binding.getAcctName());
        resp.setWatermarkCustomer(format(binding.getWatermarkCustomer()));
        resp.setWatermarkOutbound(format(binding.getWatermarkOutbound()));
        return resp;
    }

    /**
     * 时间格式化（客户端直接拿去做金蝶 FilterString）。
     *
     * @param time 时间
     * @return 字符串；空返回 null
     */
    private String format(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    /**
     * 状态标签。
     *
     * @param status 状态编码
     * @return 标签
     */
    private String statusLabel(String status) {
        return KdBindingStatus.REVOKED.getCode().equalsIgnoreCase(status)
                ? KdBindingStatus.REVOKED.getLabel()
                : KdBindingStatus.ACTIVE.getLabel();
    }

    /**
     * 是否超级管理员。
     *
     * @param loginUser 登录用户
     * @return 是否管理员
     */
    private boolean isAdmin(LoginUser loginUser) {
        return loginUser != null && loginUser.getRoles() != null
                && loginUser.getRoles().contains(Constants.SUPER_ADMIN_ROLE_KEY);
    }

    /**
     * 批量取用户姓名。
     *
     * @param bindings 绑定列表
     * @return 用户 ID → 姓名
     */
    private Map<Long, String> loadUserNames(List<KdBinding> bindings) {
        List<Long> ids = bindings.stream().map(KdBinding::getUserId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<IdName> names = bizUserRepository.findNames(ids);
        return names.stream().collect(Collectors.toMap(IdName::id, IdName::name, (a, b) -> a));
    }
}
