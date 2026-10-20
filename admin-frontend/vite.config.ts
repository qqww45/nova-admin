import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import UnoCSS from 'unocss/vite';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

/**
 * Vite 工程配置。
 *
 * <p>要点：
 * <ul>
 *     <li>Element Plus 采用按需引入（AutoImport + Components），构建体积更小；</li>
 *     <li>组件样式走 <code>importStyle: 'sass'</code> + <code>additionalData</code>，
 *         使 src/styles/element/index.scss 中的官方 SCSS 变量覆盖在编译期生效，
 *         而不是运行期用更高优先级的选择器去覆盖（详见该文件注释）；</li>
 *     <li>UnoCSS 提供原子类，配合 SCSS 变量实现"设计令牌 → 组件"的一致性；</li>
 *     <li>开发代理：前端 /api 前缀转发到后端 8080，同时剥离 /api，
 *         这样代码里写 '/login' 即可，部署时用 Nginx 做同样的转发；</li>
 *     <li><b>appType: 'spa'</b> 显式声明 SPA 模式：开启 Vite 的 HTML 历史回退，
 *         刷新任意子路由（如 /system/user）都会落到 index.html，而不是 404。
 *         即便这是默认值，显式写出来能避免被后续改动悄悄关掉。</li>
 * </ul>
 */
export default defineConfig({
  /**
   * 显式启用 SPA HTML 历史回退。
   *
   * <p>Vite 内部用 connect-history-api-fallback 处理「请求的不是真实文件、Accept 含 text/html」
   * 的情况，自动回退到 index.html。默认即为 'spa'，但显式声明更稳妥。
   */
  appType: 'spa',
  plugins: [
    vue(),
    UnoCSS(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/types/auto-imports.d.ts',
      eslintrc: { enabled: false },
    }),
    Components({
      // sass：让 Element Plus 编译自己的 SCSS 源码，从而接受我们的变量覆盖
      resolvers: [ElementPlusResolver({ importStyle: 'sass' })],
      dts: 'src/types/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        /**
         * 把主题变量文件注入到每一个被编译的 SCSS 入口（含 Element Plus 组件自身的 SCSS）。
         * 这是 Element Plus 官方推荐的自定义主题方式：编译期改写变量，而非运行期覆盖选择器。
         */
        additionalData: '@use "@/styles/element/index.scss" as *;\n',
      },
    },
  },
  server: {
    host: true,
    port: 5173,
    open: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  build: {
    target: 'es2020',
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 手工分包：把体积大且不常变的依赖单独切出，利于浏览器长期缓存
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          element: ['element-plus', '@element-plus/icons-vue'],
          echarts: ['echarts'],
        },
      },
    },
  },
});
