package com.example.admin.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产安全开关回归测试：锁死 application-prod.yml（及主配置兜底值）的关键安全默认值。
 *
 * <p>为什么直接解析 YAML 而不是起 Spring 上下文：
 * 本仓库的测试约定是纯单元测试（无 DB / 无 Spring 上下文，全套约 20 秒跑完），
 * 而这些开关的风险恰恰是<b>有人顺手改配置文件</b>——
 * 直接对着文件断言，正好守住这个风险面。
 *
 * <p>锁定的不变量（与 VerifyConfigGuard / 生产部署文档一致）：
 * <ul>
 *     <li>prod：验证码回传（reveal-code）默认关闭、通道默认 email、接口文档默认关闭；</li>
 *     <li>主配置兜底值同样安全：未显式配置任何 profile 时也绝不回传验证码；</li>
 *     <li>dev 的弱化值只存在于 application-dev.yml（显式覆盖），且测试将其文档化。</li>
 * </ul>
 *
 * <p>改动这些默认值前请先读 application.yml 中
 * 【为什么这里的默认值取"最安全"而不是"最方便"】的注释。
 */
class ProdProfileSecurityConfigTest {

    /** 从 classpath（src/main/resources）加载一个 YAML 配置文件为 Map */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(String name) {
        InputStream in = ProdProfileSecurityConfigTest.class.getClassLoader().getResourceAsStream(name);
        assertThat(in).as("classpath 下应存在 %s", name).isNotNull();
        try (in) {
            return new Yaml().loadAs(in, Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + name + " 失败", e);
        }
    }

    /** 按 a.b.c 路径取嵌套值；断言沿途每一级都存在，避免拼错路径时测试静默通过 */
    @SuppressWarnings("unchecked")
    private static Object dig(Map<String, Object> root, String path) {
        Object cur = root;
        for (String key : path.split("\\.")) {
            assertThat(cur).as("路径 %s：%s 这一级应是 Map", path, key).isInstanceOf(Map.class);
            cur = ((Map<String, Object>) cur).get(key);
            assertThat(cur).as("路径 %s 应存在", path).isNotNull();
        }
        return cur;
    }

    /** 提取 ${VAR:default} 占位符的默认值部分 */
    private static String placeholderDefault(Object value) {
        String s = String.valueOf(value);
        assertThat(s).as("应是 ${VAR:default} 占位符写法").matches("\\$\\{[^}]+}");
        int idx = s.indexOf(':');
        return idx < 0 ? "" : s.substring(idx + 1, s.length() - 1);
    }

    // ---------------------------------------------------------------
    // prod profile：全部安全开关必须默认开启/关闭到位
    // ---------------------------------------------------------------

    @Test
    @DisplayName("prod：验证码回传默认关闭（reveal-code=false）")
    void prod_revealCode_defaultOff() {
        Map<String, Object> prod = loadYaml("application-prod.yml");
        assertThat(placeholderDefault(dig(prod, "admin.verify.reveal-code"))).isEqualTo("false");
    }

    @Test
    @DisplayName("prod：验证码通道默认 email（log 通道在 prod 会被守卫拒绝）")
    void prod_verifyChannel_defaultEmail() {
        Map<String, Object> prod = loadYaml("application-prod.yml");
        assertThat(placeholderDefault(dig(prod, "admin.verify.channel"))).isEqualTo("email");
    }

    @Test
    @DisplayName("prod：接口文档全部关闭（springdoc / swagger-ui / knife4j）")
    void prod_apiDocs_disabled() {
        Map<String, Object> prod = loadYaml("application-prod.yml");
        assertThat(dig(prod, "springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(dig(prod, "springdoc.swagger-ui.enabled")).isEqualTo(false);
        assertThat(dig(prod, "knife4j.enable")).isEqualTo(false);
        assertThat(dig(prod, "knife4j.production")).isEqualTo(true);
    }

    @Test
    @DisplayName("prod：Flyway 校验开启、clean 禁用（防迁移被篡改/误删）")
    void prod_flyway_hardened() {
        Map<String, Object> prod = loadYaml("application-prod.yml");
        assertThat(dig(prod, "spring.flyway.validate-on-migrate")).isEqualTo(true);
        assertThat(dig(prod, "spring.flyway.clean-disabled")).isEqualTo(true);
    }

    // ---------------------------------------------------------------
    // 主配置兜底值：忘配 profile 时也要落在安全值上
    // ---------------------------------------------------------------

    @Test
    @DisplayName("主配置兜底：reveal-code 默认 false、channel 默认 email（未显式配置 profile 时的落点）")
    void baseConfig_safeFallback() {
        Map<String, Object> base = loadYaml("application.yml");
        assertThat(placeholderDefault(dig(base, "admin.verify.reveal-code"))).isEqualTo("false");
        assertThat(placeholderDefault(dig(base, "admin.verify.channel"))).isEqualTo("email");
    }

    // ---------------------------------------------------------------
    // dev：弱化值只允许出现在 dev 的显式覆盖里（文档化，防止扩散到其它文件）
    // ---------------------------------------------------------------

    @Test
    @DisplayName("dev：reveal-code=true / channel=log 仅作为开发环境的显式覆盖存在")
    void dev_weakenedValuesOnlyInDev() {
        Map<String, Object> dev = loadYaml("application-dev.yml");
        assertThat(dig(dev, "admin.verify.reveal-code")).isEqualTo(true);
        assertThat(dig(dev, "admin.verify.channel")).isEqualTo("log");
    }
}
