import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteRecordRaw,
} from 'vue-router';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import { usePermissionStore } from '@/store/permission';
import { useUserStore } from '@/store/user';
import { BRAND_NAME } from '@/config/brand';

/**
 * 路由配置。
 *
 * <p>静态路由（登录页、404、首页框架）写死在前端；
 * 业务路由由后端菜单动态注入（见 store/permission），实现菜单级权限控制。
 */

/** 根布局（懒加载） */
const Layout = () => import('@/layout/AdminLayout.vue');

/** 静态路由 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    // 根布局路由：动态菜单路由统一挂在这个路由下（name 供 addRoute 使用）
    path: '/',
    name: 'Root',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User', hidden: true },
      },
    ],
  },
  {
    /**
     * "刷新当前页"的中转路由。
     *
     * <p>必须存在：TagsView 的「刷新当前页」会 replace 到 /redirect/<原路径>，
     * 由 Redirect 组件立刻弹回原地址，借"卸载→重挂载"重建页面组件。
     * 缺了这条路由，跳转到 /redirect/... 会匹配不到任何记录 →
     * 守卫判定为不存在的地址 → 跳 /404，表现为"刷新当前页后 404"。
     *
     * <p>放在 /404 之前：/404 是精确路径，不受本条通配影响，
     * 但保持"通配靠后"的顺序更安全。
     */
    path: '/redirect',
    name: 'Redirect',
    component: Layout,
    meta: { title: '刷新', hidden: true },
    children: [
      {
        path: ':path(.*)',
        name: 'RedirectTo',
        component: () => import('@/views/system/redirect/index.vue'),
        meta: { title: '刷新', hidden: true },
      },
    ],
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', hidden: true },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

/** 免登录白名单 */
const WHITE_LIST = ['/login', '/404'];

/**
 * 拉取菜单并把动态路由注册到根布局下。
 *
 * <p>为什么"生成"与"注册"必须放在同一个函数里：
 * 二者一旦分离（例如某处先调 generateRoutes 把 loaded 置为 true），
 * 守卫就会认为路由已就绪而跳过注册，结果菜单能显示、点进去却匹配不到路由 → 白屏。
 * 收敛到这里后，无论从哪个入口进来（刷新 / 登录跳转 / 点击菜单）都走同一条路径。
 *
 * @param to 目标路由
 * @returns 注入后目标是否已可匹配（false 说明该地址确实不存在）
 */
/** 已注册的动态路由名：登出后用于清理，避免换账号时残留上一个账号的路由 */
const registeredNames: string[] = [];

async function injectDynamicRoutes(to: RouteLocationNormalized): Promise<boolean> {
  const userStore = useUserStore();
  const permissionStore = usePermissionStore();

  // 菜单已被重置（登出 / 换账号）时先卸载旧路由，防止权限残留
  if (!permissionStore.loaded && registeredNames.length > 0) {
    registeredNames.forEach((name) => router.removeRoute(name));
    registeredNames.length = 0;
  }

  await userStore.getInfo();
  const routes = await permissionStore.generateRoutes();
  // 业务路由统一挂在根布局下，保证侧边栏与面包屑一致；已存在的路由（如仪表盘）跳过
  routes.forEach((route) => {
    const name = route.name as string | undefined;
    if (name && !router.hasRoute(name)) {
      router.addRoute('Root', route);
      registeredNames.push(name);
    }
  });
  return router.resolve(to.fullPath).matched.length > 0;
}

/**
 * 全局前置守卫：进度条 + 登录校验 + 动态路由注入。
 */
router.beforeEach(async (to, _from, next) => {
  NProgress.start();
  // 浏览器标题：<页面名> - NOVA ADMIN。
  // 品牌名来自配置常量而非硬编码，后续换品牌标识只改一处。
  document.title = to.meta.title ? `${to.meta.title} - ${BRAND_NAME}` : BRAND_NAME;

  const userStore = useUserStore();

  if (WHITE_LIST.includes(to.path)) {
    next();
    return;
  }

  if (!userStore.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } });
    return;
  }

  const permissionStore = usePermissionStore();

  // 菜单未加载，或目标路由匹配不到（说明动态路由尚未注册）→ 注入后再导航
  if (!permissionStore.loaded || to.matched.length === 0) {
    try {
      const matched = await injectDynamicRoutes(to);
      if (!matched) {
        // 注入后依然匹配不到：确属不存在的地址
        next('/404');
        return;
      }
      // addRoute 后需重新导航一次，否则命中的是注入前的匹配结果
      next({ ...to, replace: true });
    } catch {
      userStore.reset();
      next({ path: '/login', query: { redirect: to.fullPath } });
    }
    return;
  }

  next();
});

router.afterEach(() => {
  NProgress.done();
});

export default router;
