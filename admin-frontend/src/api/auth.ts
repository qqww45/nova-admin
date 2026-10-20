import http from '@/utils/request';
import type {
  ChallengeVerifyReq,
  GetInfoResp,
  LoginReq,
  LoginResp,
  MenuVo,
  OneTimeCodeReq,
  OneTimeCodeResp,
} from '@/types/api';

/**
 * 认证相关接口。
 */
export const authApi = {
  /**
   * 登录。
   *
   * <p>silentError：登录失败属于"用户必须当场处理"的信息，
   * 由登录页以内联告警条呈现（停留在视线焦点、不会自动消失），
   * 不用顶部 toast——否则一闪而过，用户根本看不清哪里错了。
   *
   * @param data 登录参数（含设备信息；验证码在可信设备上可省略）
   */
  login(data: LoginReq): Promise<LoginResp> {
    return http.post<LoginResp>('/login', data, { silentError: true });
  },

  /**
   * 完成人机验证并登录（两阶段的第二步）。
   *
   * <p>票据由服务端原子消费：无论成功或失败都不可能再用第二次（抗重放）。
   *
   * @param data 票据 + 验证证据（轻验证提交交互样本，强验证提交一次性码）
   */
  verifyChallenge(data: ChallengeVerifyReq): Promise<LoginResp> {
    return http.post<LoginResp>('/login/verify', data, { silentError: true });
  },

  /**
   * 请求下发一次性验证码（强验证通道，也是无障碍降级路径）。
   *
   * @param data 票据与用户名
   */
  sendOneTimeCode(data: OneTimeCodeReq): Promise<OneTimeCodeResp> {
    return http.post<OneTimeCodeResp>('/login/code', data, { silentError: true });
  },

  /**
   * 刷新令牌（无感刷新）。
   *
   * <p>说明：日常刷新由 request 拦截器自动完成（见 utils/request.ts），
   * 这里单独暴露是为了支持主动刷新与测试。
   *
   * @param refreshToken 刷新令牌
   * @param deviceId     设备标识
   */
  refresh(refreshToken: string, deviceId: string): Promise<LoginResp> {
    return http.post<LoginResp>('/refresh', { refreshToken, deviceId });
  },

  /**
   * 退出登录。
   *
   * <p>声明 silentError：通知后端拉黑令牌是"尽力而为"的操作，
   * 失败时由 store 层静默处理（本地登出照常完成），不应弹网络错误打扰用户。
   *
   * @param refreshToken 刷新令牌（一并拉黑）
   */
  logout(refreshToken: string): Promise<void> {
    return http.post<void>('/logout', { refreshToken }, { silentError: true });
  },

  /** 获取当前用户信息（角色 + 权限 + 主题） */
  getInfo(): Promise<GetInfoResp> {
    return http.get<GetInfoResp>('/getInfo');
  },

  /** 获取当前用户菜单树（动态路由数据源） */
  getRouters(): Promise<MenuVo[]> {
    return http.get<MenuVo[]>('/getRouters');
  },
};
