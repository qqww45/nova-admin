import http from '@/utils/request';
import type { DeptVo, MenuVo } from '@/types/api';

/**
 * 菜单与部门接口（均为树结构）。
 */
export const menuApi = {
  /** 菜单列表（平铺，用于表格展示） */
  list(params?: Record<string, unknown>): Promise<MenuVo[]> {
    return http.get<MenuVo[]>('/system/menu/list', params);
  },

  /** 菜单树（下拉选择、权限分配用） */
  tree(): Promise<MenuVo[]> {
    return http.get<MenuVo[]>('/system/menu/tree');
  },

  /**
   * 菜单详情。
   *
   * <p>注意：后端 <code>GET /system/menu/{id}</code> 返回的是<b>单元素数组</b>
   * （SysMenuController 的注释说"便于前端复用树形选择器逻辑"，但前端并无该用法）。
   * 这里在 API 层拆包为单个对象，让调用方拿到 MenuVo 而不是数组 ——
   * 之前把数组直接 Object.assign 进表单，键变成 "0"，编辑弹窗全部字段空白。
   */
  detail(id: string): Promise<MenuVo> {
    return http.get<MenuVo[]>(`/system/menu/${id}`).then((list) => list?.[0]);
  },

  /** 新增菜单 */
  create(data: Partial<MenuVo>): Promise<string> {
    return http.post<string>('/system/menu', data);
  },

  /** 修改菜单 */
  update(data: Partial<MenuVo>): Promise<void> {
    return http.put<void>('/system/menu', data);
  },

  /** 删除菜单 */
  remove(ids: string[]): Promise<void> {
    return http.delete<void>(`/system/menu/${ids.join(',')}`);
  },
};

export const deptApi = {
  /** 部门树 */
  tree(deptName?: string, status?: number): Promise<DeptVo[]> {
    return http.get<DeptVo[]>('/system/dept/tree', { deptName, status });
  },

  /** 新增部门 */
  create(data: Partial<DeptVo>): Promise<string> {
    return http.post<string>('/system/dept', data);
  },

  /** 修改部门 */
  update(data: Partial<DeptVo>): Promise<void> {
    return http.put<void>('/system/dept', data);
  },

  /** 删除部门（含子部门） */
  remove(id: string): Promise<void> {
    return http.delete<void>(`/system/dept/${id}`);
  },
};

export const dashboardApi = {
  /** 首页汇总统计 */
  summary(): Promise<import('@/types/api').DashboardSummary> {
    return http.get<import('@/types/api').DashboardSummary>('/dashboard/summary');
  },

  /** 最近登录记录 */
  recentLogin(limit = 10): Promise<import('@/types/api').LoginLogResp[]> {
    return http.get<import('@/types/api').LoginLogResp[]>('/dashboard/recentLogin', { limit });
  },

  /** 系统信息与关键运行参数（不含任何凭据） */
  systemInfo(): Promise<import('@/types/api').SystemInfoResp> {
    return http.get<import('@/types/api').SystemInfoResp>('/dashboard/systemInfo');
  },
};
