import { defineConfig, presetUno, presetIcons, presetAttributify } from 'unocss';

/**
 * UnoCSS 配置。
 *
 * <p>主题色不写死在原子类里，而是复用 CSS 变量（见 src/styles/theme.css），
 * 这样"浅色 / 深色 / 品牌色"切换时，原子类样式会随变量一起变化，无需重新生成样式表。
 */
export default defineConfig({
  presets: [
    presetUno(),
    presetAttributify(),
    presetIcons({
      scale: 1.2,
      warn: true,
      extraProperties: {
        display: 'inline-block',
        'vertical-align': 'middle',
      },
    }),
  ],
  theme: {
    colors: {
      primary: 'var(--el-color-primary)',
      'layout-bg': 'var(--admin-bg-color)',
      'layout-header': 'var(--admin-header-bg)',
      'layout-sidebar': 'var(--admin-sidebar-bg)',
    },
  },
  shortcuts: {
    // 常用布局组合：减少模板里的类名噪音
    'flex-center': 'flex items-center justify-center',
    'flex-between': 'flex items-center justify-between',
    'card-shadow': 'shadow-[0_1px_4px_rgba(0,21,41,0.08)]',
  },
});
