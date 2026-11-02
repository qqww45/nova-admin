/**
 * 国内网络打包：走 npmmirror，避免 GitHub 下载 Electron / builder 二进制超时
 */
import { spawn } from 'node:child_process'
import process from 'node:process'

process.env.ELECTRON_MIRROR =
  process.env.ELECTRON_MIRROR || 'https://npmmirror.com/mirrors/electron/'
process.env.ELECTRON_CUSTOM_DIR = process.env.ELECTRON_CUSTOM_DIR || '{{ version }}'
process.env.ELECTRON_BUILDER_BINARIES_MIRROR =
  process.env.ELECTRON_BUILDER_BINARIES_MIRROR ||
  'https://npmmirror.com/mirrors/electron-builder-binaries/'
// 无代码签名证书时跳过自动发现，避免拉取/解压 winCodeSign（需管理员创建符号链接）
process.env.CSC_IDENTITY_AUTO_DISCOVERY = 'false'

const child = spawn('npx', ['electron-builder', '--win', 'nsis'], {
  stdio: 'inherit',
  env: process.env,
  shell: true
})

child.on('exit', (code) => process.exit(code ?? 1))
