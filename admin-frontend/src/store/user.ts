import { defineStore } from 'pinia';
import { authApi } from '@/api/auth';
import type {
  ChallengeVerifyReq,
  GetInfoResp,
  LoginReq,
  LoginResp,
  OneTimeCodeReq,
  OneTimeCodeResp,
  UserInfo,
} from '@/types/api';
import { clearTokens, getRefreshToken, getToken, setToken } from '@/utils/auth';
import { getDeviceId, getFingerprint } from '@/utils/device';
import { usePermissionStore } from '@/store/permission';

/**
 * 用户状态：令牌、设备标识、基本信息、角色与权限。
 *
 * <p>设备相关设计（可信设备免验证码）：
 * <ul>
 *     <li>{@code deviceId} 由前端生成并持久化，登录时随请求上报；服务端确认后回传，
 *         以服务端值为准，保证与令牌内携带的 deviceId 一致；</li>
 *     <li>{@code trusted} 表示当前设备是否已纳入可信列表，可信设备登录时无需验证码；</li>
 *     <li>登出时<b>不清空</b> deviceId：否则每次登出再登录都会被当成新设备，失去"低打扰"的意义。</li>
 * </ul>
 *
 * <p>权限集合只在登录后加载一次，角色变更需重新登录生效（也可改为刷新时重新拉取）。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    refreshToken: getRefreshToken(),
    /** 设备标识 */
    deviceId: getDeviceId(),
    /** 当前设备是否已可信 */
    trusted: false,
    userInfo: null as UserInfo | null,
    roles: [] as string[],
    permissions: [] as string[],
  }),

  getters: {
    /** 是否已登录（以令牌为准） */
    isLogin: (state) => Boolean(state.token),

    /** 是否为超级管理员 */
    isAdmin: (state) => state.roles.includes('admin'),

    /**
     * 是否拥有指定权限（超级管理员通配）。
     *
     * @param state 状态
     */
    hasPermission: (state) => (permission: string): boolean =>
      state.permissions.includes('*:*:*') || state.permissions.includes(permission),
  },

  actions: {
    /**
     * 登录。
     *
     * <p>自动补齐设备信息：deviceId 与指纹由本地生成，trustDevice 由用户在登录页勾选决定。
     *
     * @param data 登录参数
     */
    /**
     * 保存登录结果（登录与"完成验证后登录"共用）。
     *
     * <p>抽成单独方法的原因：两阶段登录有两个入口会拿到令牌，
     * 若各写一遍保存逻辑，将来新增字段（如会话信息）极易漏改其中一处。
     *
     * @param resp 登录响应
     */
    saveLogin(resp: LoginResp): void {
      this.token = resp.accessToken;
      this.refreshToken = resp.refreshToken;
      // 以服务端确认的设备标识为准
      if (resp.deviceId) {
        this.deviceId = resp.deviceId;
      }
      this.trusted = Boolean(resp.trusted);
      setToken(resp.accessToken, resp.refreshToken);
    },

    async login(data: LoginReq): Promise<LoginResp> {
      const resp = await authApi.login({
        ...data,
        deviceId: this.deviceId || getDeviceId(),
        fingerprint: getFingerprint(),
      });
      this.saveLogin(resp);
      return resp;
    },

    /**
     * 完成人机验证并登录（两阶段登录的第二步）。
     *
     * @param data 票据与验证证据
     */
    async verifyChallenge(data: ChallengeVerifyReq): Promise<LoginResp> {
      const resp = await authApi.verifyChallenge({
        ...data,
        deviceId: data.deviceId || this.deviceId || getDeviceId(),
      });
      this.saveLogin(resp);
      return resp;
    },

    /**
     * 请求下发一次性验证码（强验证通道与无障碍降级路径）。
     *
     * @param data 票据与用户名
     */
    async sendOneTimeCode(data: OneTimeCodeReq): Promise<OneTimeCodeResp> {
      return authApi.sendOneTimeCode({
        ...data,
        deviceId: data.deviceId || this.deviceId || getDeviceId(),
      });
    },

    /**
     * 主动刷新令牌。
     *
     * <p>日常的无感刷新由请求拦截器完成（401 → 刷新 → 重放原请求），
     * 这里提供显式入口，便于主动续期与测试。
     *
     * @returns 新的访问令牌
     */
    async refreshAccessToken(): Promise<string> {
      const resp = await authApi.refresh(this.refreshToken, this.deviceId);
      this.token = resp.accessToken;
      this.refreshToken = resp.refreshToken;
      setToken(resp.accessToken, resp.refreshToken);
      return resp.accessToken;
    },

    /** 拉取用户信息（角色 + 权限 + 主题偏好） */
    async getInfo(): Promise<GetInfoResp> {
      const info = await authApi.getInfo();
      this.userInfo = info.user;
      this.roles = info.roles ?? [];
      this.permissions = info.permissions ?? [];
      return info;
    },

    /**
     * 退出登录：清空本地令牌并通知后端拉黑（保留设备标识）。
     *
     * <p>"通知后端"是尽力而为：登出的核心目标是本地状态清理与跳转登录页，
     * 后端不可达或请求失败都不应阻塞登出，更不能把异常抛给调用方——
     * 否则会出现"右上角已是未登录、页面却跳不走、还弹网络异常"的怪状态。
     */
    async logout(): Promise<void> {
      await authApi.logout(this.refreshToken).catch(() => undefined);
      this.reset();
    },

    /**
     * 清空状态（登出或令牌失效）。
     *
     * <p>注意：保留 deviceId 与 trusted，避免下次登录被误判为陌生设备。
     */
    reset(): void {
      this.token = '';
      this.refreshToken = '';
      this.userInfo = null;
      this.roles = [];
      this.permissions = [];
      this.trusted = false;
      clearTokens();
      // 同时清空菜单：否则换账号登录会短暂看到上一个账号的菜单
      // （已注册的动态路由由路由守卫在下次注入前卸载，见 router/index.ts）
      usePermissionStore().reset();
    },
  },
});
