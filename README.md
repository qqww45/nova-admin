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

## 打包

先在 `package.json` → `build.publish` 填好 GitHub `owner` / `repo`，再：

```bash
npm run dist
```

公开仓 Releases + `latest.yml`；安装包为 NSIS，数据在 `%LOCALAPPDATA%\衡账\`。
