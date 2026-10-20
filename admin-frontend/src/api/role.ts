import http from '@/utils/request';
import type { PageResult, RoleOptionResp, RoleResp, RoleSaveReq } from '@/types/api';

/**
 * 角色管理接口。
 */
export const roleApi = {
  /** 角色分页列表 */
  page(params: Record<string, unknown>): Promise<PageResult<RoleResp>> {
    return http.get<PageResult<RoleResp>>('/system/role/list', params);
  },

  /** 角色详情 */
  detail(id: string): Promise<RoleResp> {
    return http.get<RoleResp>(`/system/role/${id}`);
  },

  /** 角色下拉选项 */
  options(): Promise<RoleOptionResp[]> {
    return http.get<RoleOptionResp[]>('/system/role/options');
  },

  /** 新增角色 */
  create(data: RoleSaveReq): Promise<string> {
    return http.post<string>('/system/role', data);
  },

  /** 修改角色 */
  update(data: RoleSaveReq): Promise<void> {
    return http.put<void>('/system/role', data);
  },

  /** 批量删除角色 */
  remove(ids: string[]): Promise<void> {
    return http.delete<void>(`/system/role/${ids.join(',')}`);
  },

  /** 修改状态 */
  updateStatus(id: string, status: number): Promise<void> {
    return http.put<void>('/system/role/status', { id, status });
  },

  /**
   * 分配菜单权限。
   *
   * @param roleId  角色 ID
   * @param menuIds 菜单 ID 集合
   */
  assignMenus(roleId: string, menuIds: string[]): Promise<void> {
    return http.put<void>('/system/role/assignMenu', { roleId, menuIds });
  },
};
