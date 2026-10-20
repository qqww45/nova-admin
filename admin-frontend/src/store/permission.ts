import { defineStore } from 'pinia';
import type { RouteRecordRaw } from 'vue-router';
import { authApi } from '@/api/auth';
import type { MenuVo } from '@/types/api';
import { MENU_TYPE } from '@/utils/menu';

/**
 * 动态路由：把后端返回的菜单树转换为前端路由。
 *
 * <p>为什么用后端下发菜单而不是前端写死：
 * 权限是运行期数据（不同角色看到的菜单不同），前端写死等于把权限逻辑暴露在浏览器里，
 * 任何人都可以通过改 localStorage 绕过。后端下发 + 后端 @PreAuthorize 校验才是可信闭环。
 *
 * <p>路由结构：所有业务路由都挂到根布局路由（name = 'Root'）的 children 下，
 * 这样侧边栏、面包屑、标签栏的表现完全一致；目录节点渲染为空布局（仅承载子路由出口）。
 *
 * <p>注意：前端路由只用于"展示控制"，真正的权限校验永远以后端为准。
 */

/** 视图组件映射：后端 component 字段形如 'system/user/index' */
const viewModules = import.meta.glob('@/views/**/*.vue');

/** 目录节点使用的空布局（只放 router-view） */
const EmptyLayout = () => import('@/views/system/redirect/Empty.vue');

/**
 * 解析后端 component 字段为真实组件。
 *
 * @param component 组件路径（相对 views 目录，不含扩展名）
 */
function resolveComponent(component?: string): (() => Promise<unknown>) | undefined {
  if (!component || component === 'Layout') {
    return undefined;
  }
  const normalized = component.replace(/^\//, '').replace(/\.vue$/, '');
  return viewModules[`/src/views/${normalized}.vue`];
}

/**
 * 路径 → 路由名（system/user → system-user）。
 *
 * @param path 路由路径
 */
function toName(path: string): string {
  return path.replace(/^\//, '').replace(/\//g, '-') || 'home';
}

/**
 * 菜单树 → 路由表（递归）。
 *
 * @param menus 菜单节点集合
 * @param parentPath 父级绝对路径（用于把子菜单路径转为相对路径）
 */
function buildRoutes(menus: MenuVo[], parentPath = ''): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = [];

  for (const menu of menus) {
    // 停用节点不生成路由；按钮不生成路由（只是权限标识）
    if (menu.status !== 0 || menu.menuType === MENU_TYPE.BUTTON) {
      continue;
    }

    const path = menu.path?.startsWith('/') ? menu.path : `/${menu.path ?? ''}`;
    const meta = {
      title: menu.menuName,
      icon: menu.icon,
      permission: menu.permission || '',
    };

    if (menu.menuType === MENU_TYPE.DIR) {
      const children = buildRoutes(menu.children ?? [], path);
      routes.push({
        // 挂在根布局下，故使用相对路径（system → /system）
        path: path.replace(/^\//, ''),
        name: toName(path),
        component: EmptyLayout,
        redirect: children.length ? `${path}/${String(children[0].path)}` : undefined,
        meta,
        children,
      });
      continue;
    }

    const component = resolveComponent(menu.component);
    if (!component) {
      // 组件不存在（例如前端尚未实现该页面）时跳过，避免打开空白页
      continue;
    }
    const relative =
      parentPath && path.startsWith(`${parentPath}/`)
        ? path.slice(parentPath.length + 1)
        : path.replace(/^\//, '');

    routes.push({ path: relative, name: toName(path), component, meta });
  }

  return routes;
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    /** 后端下发的菜单树（侧边栏数据源） */
    menus: [] as MenuVo[],
    /** 动态生成的路由 */
    dynamicRoutes: [] as RouteRecordRaw[],
    /** 是否已加载（避免重复请求） */
    loaded: false,
  }),

  actions: {
    /** 加载菜单并生成路由 */
    async generateRoutes(): Promise<RouteRecordRaw[]> {
      const menus = await authApi.getRouters();
      this.menus = menus ?? [];
      this.dynamicRoutes = buildRoutes(this.menus);
      this.loaded = true;
      return this.dynamicRoutes;
    },

    /** 重置（登出时调用） */
    reset(): void {
      this.menus = [];
      this.dynamicRoutes = [];
      this.loaded = false;
    },
  },
});
