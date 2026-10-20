package com.example.admin.common.constant;

/**
 * 系统常量。
 *
 * <p>说明：集中定义魔法值，禁止在业务代码中散落硬编码字符串/数字。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public final class Constants {

    /** 私有构造，禁止实例化 */
    private Constants() {
    }

    // ---------------- 权限与账号 ----------------

    /** 超级管理员用户 ID（不可删除、拥有全部权限） */
    public static final Long SUPER_ADMIN_USER_ID = 1L;

    /** 超级管理员角色标识 */
    public static final String SUPER_ADMIN_ROLE_KEY = "admin";

    /** 权限通配符：拥有该标识即视为拥有全部权限 */
    public static final String ALL_PERMISSION = "*:*:*";

    /** 角色前缀（用于 Spring Security 的 hasRole 表达式） */
    public static final String ROLE_PREFIX = "ROLE_";

    // ---------------- 令牌 ----------------

    /** 访问令牌类型 */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** 刷新令牌类型 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** 令牌请求头 */
    public static final String TOKEN_HEADER = "Authorization";

    /** 令牌前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** JWT 载荷：令牌类型 */
    public static final String CLAIM_TYPE = "type";

    /** JWT 载荷：用户 ID */
    public static final String CLAIM_USER_ID = "userId";

    /** JWT 载荷：令牌唯一标识（用于黑名单） */
    public static final String CLAIM_JTI = "jti";

    /** JWT 载荷：设备标识（refresh 令牌携带，用于按设备维度校验与踢人） */
    public static final String CLAIM_DEVICE_ID = "deviceId";

    /** 请求头：前端生成的设备标识 */
    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    /** 请求头：浏览器指纹哈希 */
    public static final String DEVICE_FINGERPRINT_HEADER = "X-Device-Fingerprint";

    // ---------------- 菜单 ----------------

    /** 菜单类型：目录 */
    public static final Integer MENU_TYPE_DIR = 1;

    /** 菜单类型：菜单 */
    public static final Integer MENU_TYPE_MENU = 2;

    /** 菜单类型：按钮 */
    public static final Integer MENU_TYPE_BUTTON = 3;

    /** 顶级父 ID */
    public static final Long TOP_PARENT_ID = 0L;

    // ---------------- 状态 ----------------

    /** 正常状态 */
    public static final Integer STATUS_NORMAL = 0;

    /** 停用状态 */
    public static final Integer STATUS_DISABLE = 1;

    /** 逻辑未删除 */
    public static final Integer NOT_DELETED = 0;

    /** 逻辑已删除 */
    public static final Integer DELETED = 1;

    // ---------------- 主题 ----------------

    /** 主题：亮色 */
    public static final String THEME_LIGHT = "light";

    /** 主题：暗色 */
    public static final String THEME_DARK = "dark";

    /** 主题：跟随系统 */
    public static final String THEME_AUTO = "auto";

    // ---------------- 缓存键 ----------------

    /** 验证码缓存键前缀 */
    public static final String CAPTCHA_KEY_PREFIX = "captcha:";

    /** 登录失败计数缓存键前缀 */
    public static final String LOGIN_FAIL_KEY_PREFIX = "login:fail:";

    /** 登录锁定缓存键前缀 */
    public static final String LOGIN_LOCK_KEY_PREFIX = "login:lock:";

    /** IP 限流缓存键前缀 */
    public static final String IP_LIMIT_KEY_PREFIX = "login:ip:";

    /** 重复提交缓存键前缀 */
    public static final String REPEAT_SUBMIT_KEY_PREFIX = "repeat:submit:";

    /** 可信设备缓存键前缀（完整键：device:trust:{userId}:{deviceId}） */
    public static final String DEVICE_TRUST_KEY_PREFIX = "device:trust:";

    /** 刷新令牌缓存键前缀（完整键：refresh_token:{userId}:{deviceId}） */
    public static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";
}
