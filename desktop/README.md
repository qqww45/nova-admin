# 衡账

金蝶云·星空独立对账桌面工具（Windows）。方案见 `金蝶星空-独立对账桌面工具-方案说明.md`。

## 设计

- 产品名：**衡账**
- 视觉方向：精密台账（深石墨壳 + 青绿强调 + Syne / Noto Sans SC / IBM Plex Mono）
- 主路径：连接 → 同步 → 对账导出 → 自动更新

## 开发

必须用 `npm run dev`（electron-vite 会先编译主进程、拉起 Vite，再启动窗口）。不要直接 `electron .` / `npx electron .`，否则会连不上 `http://127.0.0.1:5173`，窗口只剩深色标题栏。

```bash
npm install
npm run rebuild
npm run dev
```

Windows 控制台中文乱码时，先切 UTF-8 再启动：

```bat
chcp 65001
npm run dev
```

控制台里 `Unable to create cache` / `Gpu Cache Creation failed` / `Unable to move the cache ... (0x5)` 是 Chromium GPU 磁盘缓存的权限噪音（常见于中文 Windows），一般可忽略；真正导致空白窗口的是后面的 `Failed to load URL ... ERR_CONNECTION_REFUSED`。

## M0 探针

命令行版（开发期用，需要本机 Node，可单独拷到客户机跑）：

```bash
set HZ_SERVER=https://你的金蝶云域名
set HZ_ACCT=数据中心内码
set HZ_USER=账号
set HZ_PASS=密码
npm run m0
```

应用内版（客户用）：第一步「连接金蝶云」页的 **字段自检** 按钮，独立于「测试连接」，无需 Node。
它登录后抽样客户 / 销售订单 / 出库单，并逐套试出库分录字段集，结果可「复制报告」回传，
也可「应用到本机映射」——写入 `%LOCALAPPDATA%\衡账\mappings\outstock.json`，该文件优先于内置
`resources/mappings/outstock.json`，升级不会被覆盖。

## 打包与发版

更新通道：公开仓 **GitHub Releases**（`electron-updater` github provider，`qqww45/nova-admin`）。客户端不内置 Token，匿名读取 `latest.yml`。

本地打包：

```bash
npm run dist        # 或 dist:cn（走国内镜像下载 Electron / builder 二进制）
```

正式发版：

1. 确认 `desktop/package.json` 的 `version` 已提交；
2. 推送同名标签 `desktop-v<version>`（如 `desktop-v1.2.4`）触发 `.github/workflows/release-desktop.yml`；
3. CI 用 `electron-builder --publish always` 把 `latest.yml` + `hengzhang-setup-<version>.exe` + `.blockmap` 发到 GitHub Release **`v<version>`**（非 draft）。客户端读这个 Release，不是 `desktop-v*` git 标签本身。

联调或临时换源：给客户端设环境变量 `HENGZHANG_UPDATE_URL`。

> 存量 **1.2.3** 客户端内嵌的是自有域名源（`hz-release.s.ci64.com`），无法远程改回 GitHub：需手动安装一次 1.2.4 完整包，之后自动更新走 GitHub。**1.2.1 / 1.2.2** 本身已是 GitHub 通道，发布 1.2.4 后可检查更新。

安装包为 NSIS；数据在 `%LOCALAPPDATA%\衡账\`，更新覆盖程序文件、不动数据。

窗口 / 托盘 / 安装包快捷方式图标在 `resources/icon.svg`（源）、`resources/icon.png`、`resources/icon.ico`（16–256）。改标后在本目录跑 `python3 scripts/make-icon.py` 再打包。
