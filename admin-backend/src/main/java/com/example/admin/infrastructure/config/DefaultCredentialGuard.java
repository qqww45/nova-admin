package com.example.admin.infrastructure.config;

import com.example.admin.domain.model.user.User;
import com.example.admin.domain.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 初始口令守卫：生产环境若内置账号仍在使用脚本里的默认口令，拒绝启动。
 *
 * <p>为什么需要它：迁移脚本为了本地能一键跑起来，落了一批带口令的内置账号
 * （{@code admin/admin123} 等），口令明文就写在脚本注释里、随仓库分发。
 * 只要部署时忘记改密，攻击者直接用这些凭据登录，而 {@code admin} 角色持有
 * {@code *:*:*} 通配权限 —— 一步拿到全部后台能力，前面所有的登录风控都被绕开
 * （风控防的是"猜密码"，而这里密码是已知的）。
 *
 * <p>与"首次登录强制改密"相比，这个方案更轻：不需要改登录主链路，
 * 也不需要新增字段与前端流程，就能把"忘记改密"从"静默的完全沦陷"
 * 变成"启动失败 + 一条明确的原因"。
 *
 * <p>只在非 dev profile 生效：开发就是要用这些账号，不该拦。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Component
// staging 例外：过渡环境需要保留初始口令供验收人员登录。
// 该 profile 仅用于内部验收，不得存放真实数据；正式上线切回 prod 后本守卫生效。
@Profile("!dev & !staging")
@RequiredArgsConstructor
public class DefaultCredentialGuard {

    /** 迁移脚本里出现过的初始口令（与 V2__init_data.sql 保持一致） */
    private static final List<String> DEFAULT_PASSWORDS = List.of("admin123", "test123", "123456");

    /** 需要检查的内置账号 ID（1=admin，2=manager，3=test） */
    private static final List<Long> SEED_USER_IDS = List.of(1L, 2L, 3L);

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    /**
     * 检查内置账号口令。
     *
     * @throws IllegalStateException 任一内置账号仍在使用初始口令时抛出，终止启动
     */
    @PostConstruct
    public void check() {
        List<String> weak = new java.util.ArrayList<>();
        for (Long id : SEED_USER_IDS) {
            Optional<User> opt = userRepository.findById(id);
            if (opt.isEmpty()) {
                continue;
            }
            User user = opt.get();
            if (user.getPasswordHash() == null) {
                continue;
            }
            for (String pwd : DEFAULT_PASSWORDS) {
                if (passwordEncoder.matches(pwd, user.getPasswordHash())) {
                    weak.add(user.getUsername());
                    break;
                }
            }
        }
        if (!weak.isEmpty()) {
            throw new IllegalStateException("内置账号仍在使用初始口令：" + String.join("、", weak)
                    + "。生产环境必须先修改这些账号的密码（登录后到「个人中心 - 修改密码」修改，"
                    + "或在部署脚本中通过环境变量注入新密码后再启动）");
        }
        log.info("内置账号口令检查通过（未发现初始口令）");
    }
}
