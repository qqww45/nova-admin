import http from '@/utils/request';
import type { PageResult, RoleOptionResp, UserQuery, UserResp, UserSaveReq } from '@/types/api';
import type { UserInfo } from '@/types/api';

/**
 * 用户管理与个人中心接口。
 */
export const userApi = {
  /** 用户分页列表 */
  page(params: UserQuery): Promise<PageResult<UserResp>> {
    return http.get<PageResult<UserResp>>('/system/user/list', params);
  },

  /** 用户详情 */
  detail(id: string): Promise<UserResp> {
    return http.get<UserResp>(`/system/user/${id}`);
  },

  /** 新增用户 */
  create(data: UserSaveReq): Promise<string> {
    return http.post<string>('/system/user', data);
  },

  /** 修改用户 */
  update(data: UserSaveReq): Promise<void> {
    return http.put<void>('/system/user', data);
  },

  /** 批量删除用户 */
  remove(ids: string[]): Promise<void> {
    return http.delete<void>(`/system/user/${ids.join(',')}`);
  },

  /** 重置密码 */
  resetPassword(id: string, password: string): Promise<void> {
    return http.put<void>('/system/user/resetPwd', { id, password });
  },

  /** 修改状态 */
  updateStatus(id: string, status: number): Promise<void> {
    return http.put<void>('/system/user/status', { id, status });
  },

  /** 角色下拉选项（用户表单用） */
  roleOptions(): Promise<RoleOptionResp[]> {
    return http.get<RoleOptionResp[]>('/system/user/roleOptions');
  },

  /** 个人资料 */
  getProfile(): Promise<UserInfo> {
    return http.get<UserInfo>('/user/profile');
  },

  /** 修改个人资料 */
  updateProfile(data: Partial<UserInfo>): Promise<void> {
    return http.put<void>('/user/profile', data);
  },

  /** 修改密码 */
  changePassword(oldPassword: string, newPassword: string): Promise<void> {
    return http.put<void>('/user/profile/password', { oldPassword, newPassword });
  },

  /** 保存主题偏好（跨设备同步） */
  updateTheme(theme: string): Promise<void> {
    return http.put<void>('/user/profile/theme', { theme });
  },
};
