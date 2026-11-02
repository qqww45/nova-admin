package com.example.admin.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 采集器凭证测试。
 *
 * <p>令牌与绑定码是这条链路唯一的身份凭据，出错的方式都很安静：
 * 摘要不是 64 位十六进制 → 库里查不到；绑定码带易混字符 → 用户念错；
 * 随机源重复 → 两台机器拿到同一个令牌。这里把这些都钉住。
 *
 * @author admin-system
 * @since 2026-11-02
 */
class KdSecretsTest {

    @Test
    @DisplayName("SHA-256 摘要固定为 64 位小写十六进制，且同一输入结果一致")
    void sha256ShouldBeStableHex() {
        String a = KdSecrets.sha256Hex("abc");
        String b = KdSecrets.sha256Hex("abc");
        assertEquals(a, b);
        assertEquals(64, a.length());
        assertTrue(a.matches("^[0-9a-f]{64}$"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", a);
    }

    @Test
    @DisplayName("令牌：URL 安全、非空且连续生成不重复")
    void tokenShouldBeUrlSafeAndUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = KdSecrets.newToken();
            assertTrue(token.matches("^[A-Za-z0-9_-]+$"), "令牌应为 URL 安全字符：" + token);
            assertTrue(tokens.add(token), "令牌出现重复：" + token);
        }
    }

    @Test
    @DisplayName("绑定码：8 位、不含易混字符 0/O/1/I/L")
    void bindCodeShouldAvoidAmbiguousChars() {
        for (int i = 0; i < 200; i++) {
            String code = KdSecrets.newBindCode();
            assertEquals(8, code.length());
            assertFalse(code.matches(".*[0O1IL].*"), "绑定码不应包含易混字符：" + code);
        }
    }

    @Test
    @DisplayName("绑定码归一化：去空格并转大写，小写输入也能兑换")
    void normalizeCodeShouldTrimAndUpperCase() {
        assertEquals("ABCD2345", KdSecrets.normalizeCode(" abcd 2345 "));
        assertEquals("ABCD2345", KdSecrets.normalizeCode("ABCD2345"));
        assertEquals("", KdSecrets.normalizeCode(null));
        assertNotEquals(KdSecrets.sha256Hex("ABCD2345"), KdSecrets.sha256Hex("abcd2345"));
    }
}
