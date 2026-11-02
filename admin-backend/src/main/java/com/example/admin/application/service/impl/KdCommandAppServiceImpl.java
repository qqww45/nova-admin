package com.example.admin.application.service.impl;

import com.example.admin.application.service.KdCommandAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.kd.KdBinding;
import com.example.admin.domain.model.kd.KdClientIdentity;
import com.example.admin.domain.model.kd.KdCommand;
import com.example.admin.domain.port.KdClientContext;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.repository.KdCommandRepository;
import com.example.admin.interfaces.dto.req.KdCommandAckReq;
import com.example.admin.interfaces.dto.req.KdCommandSendReq;
import com.example.admin.interfaces.dto.resp.KdCommandResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 采集器指令应用服务实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KdCommandAppServiceImpl implements KdCommandAppService {

    /** 指令有效期：过了一天还没取走，说明机器早已离线，执行也没有意义 */
    private static final Duration TTL = Duration.ofHours(24);

    /** 允许的指令白名单：防止网页侧写入未知指令把老版本客户端搞崩 */
    private static final Set<String> ALLOWED = Set.of(
            KdCommand.CMD_SYNC_NOW,
            KdCommand.CMD_CONFIG_RELOAD,
            KdCommand.CMD_TOKEN_REVOKE,
            KdCommand.CMD_CLIENT_UPDATE);

    private final KdCommandRepository commandRepository;
    private final KdBindingRepository bindingRepository;
    private final OperatorContext operatorContext;
    private final KdClientContext kdClientContext;

    @Override
    public KdCommandResp send(String bindingId, KdCommandSendReq req) {
        Long id = IdParser.parseIdOrNull(bindingId);
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "绑定 ID 不合法");
        }
        String command = req.getCommand() == null ? "" : req.getCommand().trim();
        if (!ALLOWED.contains(command)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的指令：" + command);
        }
        KdBinding binding = bindingRepository.findById(id);
        if (binding == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "绑定不存在");
        }
        LoginUser loginUser = operatorContext.current();
        if (!isAdmin(loginUser) && !binding.getUserId().equals(operatorContext.currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作别人的采集器");
        }

        LocalDateTime now = LocalDateTime.now();
        KdCommand entity = new KdCommand();
        entity.setBindingId(binding.getId());
        entity.setUserId(binding.getUserId());
        entity.setAcctId(binding.getAcctId());
        entity.setCommand(command);
        entity.setPayload(req.getPayload());
        entity.setStatus(KdCommand.PENDING);
        entity.setExpireTime(now.plus(TTL));
        Long commandId = commandRepository.save(entity);

        KdCommandResp resp = toResp(entity);
        resp.setId(String.valueOf(commandId));
        return resp;
    }

    @Override
    public KdCommandResp poll() {
        KdClientIdentity identity = kdClientContext.require();
        LocalDateTime now = LocalDateTime.now();
        // 顺手清理过期指令，省得另开定时任务
        commandRepository.expireBefore(identity.bindingId(), now.minus(TTL));
        // 心跳：网页上能看出"这台机器还活着"
        bindingRepository.touch(identity.bindingId(), now);

        for (int attempt = 0; attempt < 3; attempt++) {
            KdCommand pending = commandRepository.findPending(identity.bindingId());
            if (pending == null) {
                return null;
            }
            // 竞争失败说明被同一令牌的另一个连接取走了，继续取下一条
            if (commandRepository.markSent(pending.getId(), now) > 0) {
                pending.setStatus(KdCommand.SENT);
                pending.setDeliverTime(now);
                return toResp(pending);
            }
        }
        return null;
    }

    @Override
    public void ack(String id, KdCommandAckReq req) {
        KdClientIdentity identity = kdClientContext.require();
        Long commandId = IdParser.parseIdOrNull(id);
        if (commandId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "指令 ID 不合法");
        }
        KdCommand command = commandRepository.findById(commandId);
        // 只能回执自己绑定下的指令：令牌泄漏时不能靠猜 ID 改别人的记录
        if (command == null || !identity.bindingId().equals(command.getBindingId())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "指令不存在");
        }
        boolean ok = req.getOk() == null || Boolean.TRUE.equals(req.getOk());
        String message = (ok ? "成功" : "失败")
                + (req.getMessage() == null || req.getMessage().isBlank() ? "" : "：" + req.getMessage());
        commandRepository.markDone(commandId, message, LocalDateTime.now());
        log.info("采集器回执 binding={} command={} result={}", identity.bindingId(),
                command.getCommand(), message);
    }

    @Override
    public List<KdCommandResp> recent(String bindingId) {
        Long id = IdParser.parseIdOrNull(bindingId);
        if (id == null) {
            return List.of();
        }
        KdBinding binding = bindingRepository.findById(id);
        if (binding == null) {
            return List.of();
        }
        LoginUser loginUser = operatorContext.current();
        if (!isAdmin(loginUser) && !binding.getUserId().equals(operatorContext.currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看别人的采集器");
        }
        return commandRepository.listByBinding(id, 20).stream().map(this::toResp).toList();
    }

    /**
     * 领域模型 → 响应（ID 一律转字符串，与全局 Long→String 序列化保持一致）。
     *
     * @param command 指令
     * @return 响应
     */
    private KdCommandResp toResp(KdCommand command) {
        KdCommandResp resp = new KdCommandResp();
        resp.setId(command.getId() == null ? null : String.valueOf(command.getId()));
        resp.setBindingId(command.getBindingId() == null ? null : String.valueOf(command.getBindingId()));
        resp.setCommand(command.getCommand());
        resp.setPayload(command.getPayload());
        resp.setStatus(command.getStatus());
        resp.setResult(command.getResult());
        resp.setCreateTime(command.getCreateTime());
        resp.setDeliverTime(command.getDeliverTime());
        resp.setDoneTime(command.getDoneTime());
        return resp;
    }

    private boolean isAdmin(LoginUser loginUser) {
        return loginUser != null && loginUser.getRoles() != null
                && loginUser.getRoles().contains(Constants.SUPER_ADMIN_ROLE_KEY);
    }
}
