import { resolve } from 'path'
import { defineConfig, externalizeDepsPlugin } from 'electron-vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  main: {
    // 仅外置原生模块；exceljs 等打进主进程包，避免安装包缺传递依赖
    plugins: [
      externalizeDepsPlugin({
        exclude: ['exceljs', 'electron-updater']
      })
    ],
    build: {
      rollupOptions: {
        input: {
          index: resolve('src/main/index.ts')
        }
      }
    }
  },
  preload: {
    plugins: [externalizeDepsPlugin({ exclude: [] })],
    build: {
      rollupOptions: {
        input: {
          index: resolve('src/preload/index.ts')
        }
      }
    }
  },
  renderer: {
    // 固定 IPv4 回环：默认 localhost 常只绑 ::1，Windows 上 Electron 走 127.0.0.1 会
    // ERR_CONNECTION_REFUSED，窗口只剩标题栏底色。strictPort 避免静默换端口。
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true
    },
    resolve: {
      alias: {
        '@renderer': resolve('src/renderer/src')
      }
    },
    plugins: [vue()]
  }
})
