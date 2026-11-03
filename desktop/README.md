# 衡账

金蝶云·星空独立对账桌面工具（Windows）。方案见 `金蝶星空-独立对账桌面工具-方案说明.md`。

## 设计

- 产品名：**衡账**
- 视觉方向：精密台账（深石墨壳 + 青绿强调 + Syne / Noto Sans SC / IBM Plex Mono）
- 主路径：连接 → 同步 → 对账导出 → 自动更新

## 开发

```bash
npm install
npm run rebuild
npm run dev
```

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

本仓库为**私有仓库**，客户端匿名访问 GitHub（`releases.atom` / API）只会得到 404，且更新通道不内置任何 Token。因此自动更新**不走 GitHub Releases**，改用自有静态发布源（`generic` provider，`build.publish` 指向 `https://hz-release.s.ci64.com/`）。

本地打包：

```bash
npm run dist        # 或 dist:cn（走国内镜像下载 Electron / builder 二进制）
```

正式发版：

1. 改 `desktop/package.json` 的 `version`，提交；
2. 推送同名标签 `desktop-v<version>`（如 `desktop-v1.2.2`）触发 `.github/workflows/release-desktop.yml`；
3. CI 在 Windows 构建出 `release/latest.yml` + `hengzhang-setup-<version>.exe` + `.blockmap`：
   - 存档到本仓库 GitHub Releases（鉴权访问，仅运维查看用）；
   - 上传到上海服务器 `release/` 目录，由 `deploy/docker-compose.yml` 的 `update`（nginx）容器对外只读提供。

服务器 / 域名一次性配置（首次发版前完成）：

- `deploy/docker-compose.yml` 已含 `update` 服务（nginx，`${UPDATE_PORT:-8081}:80`，只读挂载 `./release`）；后端下次部署或 `docker compose up -d update` 即可拉起；
- EdgeOne 为 `hz-release.s.ci64.com`（或改 `build.publish` 与 `vars.HENGZHANG_UPDATE_URL` 换域名）添加加速站点，回源上海服务器 8081；**`latest.yml` 缓存 TTL 必须为 0**（否则客户端看不到新版本）。

联调或临时换源：给客户端设环境变量 `HENGZHANG_UPDATE_URL`（如指向测试机），覆盖包内 `app-update.yml`，无需重新打包。

> 存量 1.2.1 及更早客户端内嵌的是 GitHub 源配置，**无法远程修复**：需手动安装一次新版完整安装包，之后回到自动更新通道。

安装包为 NSIS；数据在 `%LOCALAPPDATA%\衡账\`，更新覆盖程序文件、不动数据。
