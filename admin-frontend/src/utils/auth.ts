/**
 * 令牌本地存储工具。
 *
 * <p>令牌存 localStorage：后台系统一般为多标签页使用，刷新令牌可跨标签共享，
 * 避免每次刷新页面都要重新登录。若对安全要求极高可换 sessionStorage（关闭标签即失效）。
 */
const ACCESS_TOKEN_KEY = 'admin-access-token';
const REFRESH_TOKEN_KEY = 'admin-refresh-token';

/** 获取访问令牌 */
export function getToken(): string {
  return localStorage.getItem(ACCESS_TOKEN_KEY) ?? '';
}

/** 获取刷新令牌 */
export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) ?? '';
}

/**
 * 保存令牌对。
 *
 * @param accessToken  访问令牌
 * @param refreshToken 刷新令牌
 */
export function setToken(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

/** 清空令牌（登出或刷新失败时调用） */
export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}
