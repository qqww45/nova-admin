import { defineStore } from 'pinia';
import { userApi } from '@/api/user';

/** 主题模式 */
export type ThemeMode = 'light' | 'dark' | 'auto';

const THEME_KEY = 'admin-theme';

/**
 * 应用级状态：主题、侧边栏折叠、布局尺寸。
 *
 * <p>主题切换做了三件事，保证"切换即时生效 + 刷新不闪烁 + 换设备同步"：
 * <ol>
 *     <li>切换 <html> 的 class（dark）与 data-theme 属性，驱动 CSS 变量；</li>
 *     <li>写入 localStorage，配合 index.html 的内联脚本实现首帧无闪烁；</li>
 *     <li>异步保存到后端用户偏好，下次在别的设备登录也是同样的主题。</li>
 * </ol>
 */
export const useAppStore = defineStore('app', {
  state: () => ({
    theme: (localStorage.getItem(THEME_KEY) as ThemeMode) || 'auto',
    sidebarCollapsed: false,
    device: 'desktop' as 'desktop' | 'mobile',
  }),

  getters: {
    /** 当前是否处于暗色（auto 时跟随系统） */
    isDark(state): boolean {
      if (state.theme === 'auto') {
        return window.matchMedia('(prefers-color-scheme: dark)').matches;
      }
      return state.theme === 'dark';
    },
  },

  actions: {
    /** 切换侧边栏折叠状态 */
    toggleSidebar(): void {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    },

    /**
     * 设置主题。
     *
     * @param theme 主题模式
     */
    setTheme(theme: ThemeMode): void {
      this.theme = theme;
      localStorage.setItem(THEME_KEY, theme);
      this.applyTheme();
      // 保存偏好失败不影响本地体验，故静默处理
      void userApi.updateTheme(theme).catch(() => undefined);
    },

    /** 应用主题到 DOM（供初始化与切换时调用） */
    applyTheme(): void {
      const root = document.documentElement;
      const dark = this.isDark;
      root.classList.toggle('dark', dark);
      root.setAttribute('data-theme', dark ? 'dark' : 'light');
    },

    /** 初始化：监听系统主题变化（仅 auto 模式生效） */
    initTheme(): void {
      this.applyTheme();
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (this.theme === 'auto') {
          this.applyTheme();
        }
      });
    },
  },
});
