# 通用后台管理系统

基于 **DDD 四层架构** 的后台管理系统脚手架：Java 21 + Spring Boot 3.3（后端）与 Vue 3 + TypeScript + Element Plus（前端）。
核心特性是 **双 Token 无感刷新 + 风险分层的人机验证 + 可信设备免验证**。

---

## 1. 项目用途

给中小型业务系统提供一个"能直接二开"的后台底座：

- **登录认证**：账号密码 + 双 Token（Access / Refresh）无感刷新，前端并发刷新去重；
- **人机验证**：不做图形验证码，改为"风险分层"——正常用户零操作，有风险才验证；
- **权限**：菜单/按钮级功能权限 + 部门级数据权限（行级）；
- **系统管理**：用户、角色、菜单、部门、在线会话、运行期可改的系统配置、在线日志查看。

设计取向：**体验优先但不牺牲安全**——绝大多数登录是"零操作"的，安全压力由风控与二次因子承担，而不是让每个用户都去辨认扭曲的文字。

---

## 2. 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.3、Spring Security、MyBatis-Plus、Flyway、jjwt 0.12、Knife4j（接口文档）、Hutool |
| 前端 | Vue 3、Vite、TypeScript、Element Plus、Pinia、Axios、ECharts |
| 数据库 | 开发 SQLite（零安装）；生产 MySQL / PostgreSQL |
| 分层 | Interface（接口） → Application（编排） → Domain（领域） → Infrastructure（实现） |

---

## 3. 目录结构

```
admin-backend/
  src/main/java/com/example/admin/
    interfaces/     接口层：Controller、请求/响应 DTO（不含业务逻辑）
    application/    应用层：用例编排（AppService）
    domain/         领域层：实体/值对象、领域服务接口、仓储接口（不依赖任何框架）
    infrastructure/ 基础设施层：MyBatis 实现、安全、缓存、邮件
  src/main/resources/
    application.yml          主配置（安全默认值）
    application-dev.yml      开发环境覆盖
    application-prod.yml     生产环境
    db/migration/            Flyway 迁移脚本（V1~V7）
  src/test/java/             单元测试（72 个用例）

admin-frontend/
  src/
    api/          接口封装
    views/        页面（login / dashboard / system/*）
    components/   通用组件（ProTable、TableActionBar、SlideVerify）
    store/        Pinia（user / app / permission）
    utils/        request（401 无感刷新）、auth、device

docs/
  00-方案确认.md      原始需求
  登录方案.md         双 Token 与设备信任设计
  人机验证方案.md     风险分层与滑块判定
  数据库字典.md       表结构与枚举
  系统日志设计.md     日志方案与分布式接入分析
```

---

## 4. 功能模块

### 4.1 登录与人机验证（核心）

两阶段登录：**凭据校验 → 风险评级 → 按需验证 → 发令牌**。

| 风险等级 | 触发条件 | 用户感知 |
|---|---|---|
| **L0 无感** | 已信任设备 + 近期无失败 + 非自动化 | 零操作，直接进 |
| **L1 滑块** | 陌生设备，或失败 ≥ 1 次 | 拖动滑块拼合图片（约 1 秒） |
| **L2 一次性码** | 失败 ≥ 3 次，或请求疑似自动化（无 UA、HeadlessChrome、Selenium 等） | 收邮件验证码并输入 |

关键设计：

- **凭据正确之后才评级**：账号是否存在不会从验证环节泄露，防账号枚举；
- **票据一次一销**：票据绑定账号 + 设备 + 网段，`consume` 先取走再校验，天然抗重放；
- **滑块判定双层**：位置正确性（服务端下发缺口 x，容差 8px）+ 轨迹评分（5 维，满分 5 需 3 分），单点攻破不成立；
- **越打越难**：验证失败与密码错误共用同一套计数，已信任设备也会因失败序列升级。

### 4.2 系统管理

| 模块 | 说明 |
|---|---|
| 用户 | 增删改查、重置密码、启停、分配角色（数据权限按角色过滤） |
| 角色 | 菜单/按钮权限分配 + **数据权限范围**（1 全部 / 2 本部门 / 3 本部门及下级 / 4 仅本人 / 5 自定义） |
| 菜单 | 目录/菜单/按钮三级，前端据此动态生成路由 |
| 部门 | 树形组织，用户归属部门（数据权限的过滤依据） |
| 在线用户 | 在线会话、强制下线、移除可信设备 |
| 系统配置 | 运行期可改的参数（站点标题、登录阈值等），改完立即生效 |
| 系统日志 | 在线查看本机日志尾部（按级别/关键字过滤） |

### 4.3 仪表盘

统计卡片（用户/角色/菜单/部门/今日登录/今日失败）、近 7 日登录趋势图、最近登录记录，以及**系统参数**卡片（版本、运行时长、JVM 堆、数据库、连接池、在线会话、关键开关）。

---

## 5. 运行方式

### 5.1 环境要求

- JDK 21（项目自带便携版：`.tools/jdk`）
- Maven 3.9+（自带：`.tools/apache-maven-3.9.16`）
- Node 18+ / pnpm

### 5.2 启动后端

```bash
# 方式一：使用自带的 Maven（Windows）
.tools\apache-maven-3.9.16\bin\mvn.cmd -f admin-backend\pom.xml spring-boot:run

# 方式二：系统已装 Maven
cd admin-backend && mvn spring-boot:run
```

启动后：http://localhost:8080 ，接口文档：http://localhost:8080/doc.html

默认账号：`admin` / `admin123`

### 5.3 启动前端

```bash
cd admin-frontend
pnpm install
pnpm dev
```

访问：http://localhost:5173 （已配置 `/api` 代理到 8080）

### 5.4 运行测试

```bash
# 后端单元测试（72 个用例）
mvn -f admin-backend/pom.xml test

# 前端类型检查
cd admin-frontend && pnpm type-check
```

### 5.5 生产启动

```bash
mvn -f admin-backend/pom.xml -Pprod spring-boot:run -Dspring-boot.run.profiles=prod
```

> **注意**：`-Pprod` 只是 Maven profile（控制数据库驱动依赖），
> **必须**同时用 `-Dspring-boot.run.profiles=prod` 才会让应用激活 prod profile。

### 5.6 部署环境（staging）

当前线上采用"先跑起来"的过渡配置（`application-staging.yml`）：

| 项 | staging（当前线上） | dev（本地） | prod（正式） |
|---|---|---|---|
| 数据库 | SQLite（`/app/data/admin.db`） | SQLite | MySQL |
| 会话态 | 内存（单实例） | 内存 | Redis |
| 验证码通道 | `log` + 回传 | `log` + 回传 | `email`、不回传 |
| 接口文档 | 关闭 | 开启 | 关闭 |
| SQL 日志 | 关闭 | 打印 | 关闭 |
| 启动守卫 | 跳过验证码/初始口令检查，JWT 密钥仍强校验 | 全部跳过 | 全部强制 |

> staging 仅用于内部验收：不能存真实数据，不能水平扩容，验证码可被前端读到。
> 正式上线切到 `prod` profile，`VerifyConfigGuard` / `DefaultCredentialGuard` 会自动恢复强制校验。

**拓扑**

- 前端：EdgeOne 静态托管 → `https://nova-admin.s.ci64.com`
- 后端：上海 Docker 服务器 → `https://nova-api.s.ci64.com`（容器内 8080）
- 跨域：后端 `CORS_ALLOWED_ORIGINS` 精确放行前端域名；前端构建期注入 `VITE_API_BASE_URL`

**流水线**（`.github/workflows/`，push 到 `main` 按路径触发）

| 工作流 | 触发路径 | 动作 |
|---|---|---|
| `deploy-backend.yml` | `admin-backend/**`、`deploy/**` | Maven 打包 → SCP jar → 服务器 `docker compose up -d --build` → 健康检查 → 域名探测 |
| `deploy-frontend.yml` | `admin-frontend/**` | 本地构建校验 → POST EdgeOne 部署钩子 → 轮询站点可访问 |

仓库需配置：

- Secrets：`SSH_HOST`、`SSH_USER`、`SSH_PRIVATE_KEY`、`SSH_PORT`（可选）、`JWT_SECRET`（staging 启动强校验，服务器已存在 `.env` 时不会用到）、`EDGEONE_DEPLOY_HOOK`（EdgeOne 项目 → 设置 → 构建部署钩子的 POST 地址）
- Variables：`VITE_API_BASE_URL`（默认 `https://nova-api.s.ci64.com`）、`API_URL`（默认 `https://nova-api.s.ci64.com`）、`FRONTEND_URL`（默认 `https://nova-admin.s.ci64.com`）、`CORS_ALLOWED_ORIGINS`（默认 `https://nova-admin.s.ci64.com`）、`DEPLOY_PATH`（默认空 → 服务器上该 SSH 账号的 `~/nova-admin`；写 `/opt` 等需 root 权限的目录时请确保 SSH 账号有写权限）

**服务器首次部署**

服务器上没有 `.env` 时，流水线会用 `secrets.JWT_SECRET` + `vars.CORS_ALLOWED_ORIGINS` 自动生成一份，
无需手工准备；已存在 `.env` 则以服务器为准（流水线永不覆盖）。

```bash
mkdir -p /opt/nova-admin/{data,logs}     # 目录创建即可，其余由流水线上传并启动
```

**EdgeOne 侧配置**（依据官方文档「触发部署」）

1. 项目关联本 GitHub 仓库（`qqww45/nova-admin`），根目录为 `admin-frontend`，
   构建读取仓库内的 `edgeone.json`（`buildCommand: pnpm build`、`outputDirectory: dist`、SPA rewrites）。
2. 项目环境变量 `VITE_API_BASE_URL=https://nova-api.s.ci64.com`（与 `.env.production` 一致）。
3. 绑定域名 `nova-admin.s.ci64.com`。
4. 生成"部署钩子"（项目设置 → 部署钩子 → 新建，分支选 `main`），把 POST 地址配到仓库 Secrets `EDGEONE_DEPLOY_HOOK`。

> 文档要点：项目**关联 Git 仓库后，push 到主分支就会自动触发部署**，钩子是可选的补充手段；
> 部署钩子的 URL 与项目唯一关联、**无需额外认证**，所以它等同于密码，泄露就要删掉重建。
> 本项目的流水线采用钩子方式（构建校验通过后才触发），钩子 URL 只存在 GitHub Secrets 里。

**常见问题：部署步骤长时间不结束**

服务器在国内时，`docker build` 拉 `eclipse-temurin:21-jre` 可能极慢甚至挂住。现在流水线已加保护：
基础镜像单独拉取（15 分钟超时）、`docker compose up` 15 分钟超时、整个 deploy job 30 分钟超时。
若日志提示拉取超时，任选其一：

```bash
# 1) 服务器配镜像加速
sudo tee /etc/docker/daemon.json <<'EOF'
{"registry-mirrors": ["https://<你的加速地址>"]}
EOF
sudo systemctl restart docker

# 2) 或直接换镜像：服务器 .env 里加
BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre
```

**服务器账号**

流水线默认用 `~/nova-admin`（即 `/home/ubuntu/nova-admin`）作为部署目录，不需要 root。
Docker 访问按以下顺序兜底：直接访问 → `sudo -n docker`（ubuntu 云镜像默认有免密 sudo）。
若两者都不通，会在日志里提示 `usermod -aG docker`。
若确实要用 `/opt/nova-admin`，先 `sudo mkdir -p /opt/nova-admin && sudo chown ubuntu:ubuntu /opt/nova-admin`，
并把仓库变量 `DEPLOY_PATH` 设为 `/opt/nova-admin`。


**域名**

`*.s.ci64.com` 已在 DNS 层 CNAME 到 EdgeOne，因此 `nova-admin.s.ci64.com` 直接可用。
`nova-api.s.ci64.com` 需要二选一：

- 在 EdgeOne 把 `nova-api.s.ci64.com` 配为加速域名，源站指向上海服务器公网 IP 的 8080（关闭该域名下 API 路径缓存）；
- 或为 `nova-api` 单独加一条指向服务器 IP 的 A 记录（需自备证书）。

无论哪种，最终都要与 `VITE_API_BASE_URL` / `CORS_ALLOWED_ORIGINS` 保持一致，否则前端请求会跨域失败。


---

## 6. 配置说明

配置文件在 `admin-backend/src/main/resources/`：


| 文件 | 用途 |
|---|---|
| `application.yml` | 主配置，**默认值取"最安全"而非"最方便"** |
| `application-dev.yml` | 开发环境覆盖（SQLite + 日志通道 + 回传验证码） |
| `application-staging.yml` | 过渡环境（SQLite + 内存缓存 + 日志通道，供公网验收） |
| `application-prod.yml` | 生产配置（MySQL + 邮件通道 + 不回传验证码） |

### 6.1 安全默认值（重要）

主配置的默认值是任何未显式配置的 profile 都会落到的值，因此一律取安全值：

```yaml
admin:
  verify:
    channel: ${ADMIN_VERIFY_CHANNEL:email}      # 默认邮件通道（而非日志）
    reveal-code: ${ADMIN_VERIFY_REVEAL_CODE:false}  # 默认不回传验证码
```

开发环境的便利由 `application-dev.yml` 显式覆盖（`log` + `true`），不靠主配置默认值。

**三层防护**（防止"发布时忘了改"）：

1. 安全默认值——忘了配，落到的是安全值；
2. profile 覆盖——切环境即切换，无需手工改开关；
3. `VerifyConfigGuard`——生产 profile 启动时校验，**配错 = 起不来**（reveal-code 必须 false、channel 不能是 log、邮件三项必填）。

### 6.2 邮件服务（一次性验证码）

```yaml
spring:
  mail:
    host: ${MAIL_HOST:}          # 例：smtp.qq.com
    port: ${MAIL_PORT:465}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}  # 多数邮件商用"授权码"而非登录密码
admin:
  verify:
    channel: email               # log=开发 / email=生产
```

`spring.mail.host` **刻意不留示例默认值**——留了会让"没配"看起来像"配好了"。
465 端口走 SSL；587 端口改用 `mail.smtp.starttls.enable=true`。

### 6.3 数据权限

角色表 `sys_role.data_scope`：

| 值 | 含义 | 当前实现 |
|---|---|---|
| 1 | 全部数据 | ✅ |
| 2 | 本部门 | ✅ |
| 3 | 本部门及下级 | ⚠️ 枚举已定义，切面暂按"本部门"降级（待接入部门树递归） |
| 4 | 仅本人 | ✅ |
| 5 | 自定义部门 | ⚠️ 待建 `sys_role_dept` 表 |

生效范围：作用于带归属字段的查询（当前落地在用户列表 `dept_id`）。
超级管理员不受限制（运维排查需要）。多角色取最宽松。

### 6.4 系统配置（运行期可调）

配置项存在数据库 `sys_config`，启动时全量加载到缓存，读路径走缓存：

- 默认缓存实现：`InMemoryConfigCache`（单进程）；
- 切 Redis：见 `RedisConfigCache` 类注释（引入依赖 → 设 `admin.config.cache=redis` → 实现方法）。

内置项（`builtin=1`）只可改值不可删除。

---

## 7. 生产上线清单

- [ ] 注入 `JWT_SECRET`（≥ 32 字节，禁止用默认值）
- [ ] 配置数据库（MySQL/PostgreSQL）与 `DB_USERNAME` / `DB_PASSWORD`
- [ ] 配置邮件：`MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD`
- [ ] 确认 `ADMIN_VERIFY_CHANNEL=email`、`ADMIN_VERIFY_REVEAL_CODE=false`（配错应用会拒绝启动）
- [ ] 配置 `CORS_ALLOWED_ORIGINS`（禁止 `*`）
- [ ] **多实例部署**：将 `ADMIN_CACHE_TYPE=redis` 并配置 `REDIS_HOST/REDIS_PORT/REDIS_PASSWORD`。
      启动时 `CacheConfigGuard` 会主动 ping Redis，连不上直接拒绝启动（避免运行到一半才失败）。
- [ ] 关闭 Knife4j（prod profile 已默认关闭）

---

## 8. 已知边界

- **票据 / 刷新令牌 / 在线会话 / 可信设备 / 登录风控计数** 5 个状态组件：默认 `InMemory*` 实现（单进程，开箱即用），
  Redis 实现已就绪（`infrastructure/cache/redis/*`）—— 切到 `admin.cache.type=redis` 即可启用，
  业务代码零改动。Lua 脚本保证票据消费原子性、SCAN 用于"按用户遍历"避免阻塞主线程。
- **自研滑块 ≠ 商业验证**：商业方案的价值在风控模型与海量样本，UI 只是载体。真实浏览器 + 拟人脚本仍可能通过 L1。若业务会被黑产盯上，建议接入 Cloudflare Turnstile / hCaptcha / 极验。
- **数据权限**仅落地在用户列表；新业务表需自行接入（读 `DataScopeContext` 并在查询方法上加 `@DataScopeAnno`）。
- **在线日志**只读本机文件，不能替代集中式日志（见 `docs/系统日志设计.md`）。
