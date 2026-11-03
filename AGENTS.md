# AGENTS.md

Project-specific guidance for OpenCode sessions.

## Repository shape

- `admin-backend/` — Spring Boot 3.3 / Java 21 / MyBatis-Plus / Flyway / Spring Security + JWT. Single Maven module.
- `admin-frontend/` — Vue 3 + Vite + TypeScript + Element Plus + Pinia + UnoCSS.
- No root build. `deploy/` holds backend Docker assets; `.github/workflows/` deploys on push to `main`.
- The repo started as a generic admin scaffold. Active product work is the **客户 / 订单 / 出货** system.
  Read `docs/系统提示词-客户订单出货管理系统.md` and `docs/附录A-Excel模板与字段映射.md` before changing business code.
- `README.md` is partly stale: it says 72 tests, migrations V1–V7, and `pnpm type-check`.
 Actual: **198 tests**, migrations through **V20**, script is **`pnpm typecheck`**.

## Commands (Windows / PowerShell)

The system `java` may be 8; this project requires 21. Use the bundled toolchain (`.tools/` is gitignored, so on a fresh clone use any JDK 21 + Maven 3.9+ instead):

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools\jdk\jdk-21.0.12.1+1').Path
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# all backend tests (~20s, pure unit tests, no DB/Spring context)
.\.tools\apache-maven-3.9.16\bin\mvn.cmd -f admin-backend\pom.xml test

# one test class
.\.tools\apache-maven-3.9.16\bin\mvn.cmd -f admin-backend\pom.xml -Dtest=AmountCalculatorTest test

# run backend (no datasource exists in the base config, so activate a profile explicitly)
.\.tools\apache-maven-3.9.16\bin\mvn.cmd -f admin-backend\pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

Frontend:

```powershell
cd admin-frontend
pnpm install
pnpm dev        # http://localhost:5173, /api proxied to :8080 and prefix stripped
pnpm typecheck  # NOT "type-check"
pnpm build      # vue-tsc --noEmit + vite build
```

- There is **no frontend test suite**; typecheck/build are the frontend gate.
- **Do not run `pnpm lint` or `pnpm format`.** No ESLint/Prettier/Stylelint config exists; `lint` exits 1 and `format` would rewrite the codebase with Prettier defaults. If git hooks were installed by `pnpm install`, `lint-staged` will fail for the same reason.
- CI builds the backend with `-Dmaven.test.skip=true`, so **tests are only run locally**. Frontend CI runs `pnpm build`.

## Backend architecture

Dependency direction: `interfaces` (controllers + req/resp DTOs) → `application` (orchestration, `*AppServiceImpl`) → `domain` (models, policies, repository/port interfaces) ← `infrastructure` (MyBatis mappers/POs/converters, security, cache, Excel, mail, config).

- Business rules belong in `domain/service` (e.g. `AmountCalculator`, `QuantityPolicy`, `OrderStatusPolicy`); repository interfaces in `domain/repository` are implemented under `infrastructure/persistence`.
- README claims `domain/` is framework-free, but auth models use Jackson annotations and a few domain services are Spring beans. Follow existing patterns instead of treating that as a hard rule.
- POs live in `infrastructure/persistence/po`, domain models in `domain/model`; convert via `infrastructure/persistence/converter`.
- Write endpoints use `@PreAuthorize("@ss.hasPermi('...')")` (super admin has `*:*:*`); some read-only endpoints are authenticated-only by design.

## Business invariants (easy to break)

- Use `BigDecimal` only for money/qty. `AmountCalculator` is the single source of tax math:
  `taxRate > 0` means `unitPrice` is **tax-inclusive**; null/0 means tax-exclusive. Tax is derived as
  `amountTaxIncluded − amountExTax` so the three amounts always add up.
- `qtyOpen = qtyOrdered − qtyShipped` is derived, never client input. Shipment save requires
  `0 < qty ≤ qtyOpen`, writes back `qty_shipped`, and recalculates order status in the same transaction.
- Business rows are never physically deleted. Shipments are voided with a reason and quantities reversed;
  items with shipments can only be cancelled.
- Order status is recalculated server-side; do not trust or accept a status from the client.
- UI, clipboard paste, and Excel import must share the same service path. Shipment import deliberately
  delegates to `ShipmentAppService.create` — keep it that way.
- Tax rates, currencies, settlement/delivery methods, and invoice types come from `biz_dict_item`;
  import header aliases come from `biz_import_mapping`. Do not hardcode these in Java/TypeScript.
  (Order/shipment statuses are Java enums, not dictionary rows.)

## API and frontend conventions

- IDs are snowflake `BIGINT` in the DB but **strings in JSON/DTOs** (`JacksonConfig` serializes `Long` to string,
  `IdParser` parses). Do not change IDs to numbers.
- Responses are wrapped in `R<T>` with `code === 200`. The Axios interceptor in `src/utils/request.ts` already
  unwraps `data`, so API functions return the business object directly. Use that `http` wrapper, not raw Axios.
  Code `1013` is the login-challenge branch and is intentionally not shown as an error.
- Dynamic routes come from backend `sys_menu`: `component` is a path under `src/views` without `.vue`
  (e.g. `biz/shipment/index`). If the file does not exist, the route is silently skipped.
  New pages require a Flyway menu migration; do not add routes to `src/router/index.ts` (only login/dashboard/profile/redirect/404 are static).
- Reuse `ProTable` + `useTable` + `TableActionBar` for lists, and `EditableTable` + `parseClipboardTable`
  for Excel-like grids. The product spec forbids parallel component sets.
- Element Plus is auto-imported on demand with SCSS theme variables; theme tokens live in
  `src/styles/element/index.scss` and `src/styles/theme.css`. Use CSS variables/UnoCSS theme colors, not hardcoded colors.
- Comments, UI text, and commit messages are Simplified Chinese; use the terminology in spec §2.
- 与用户沟通、说明和总结一律使用简体中文。

## Database and migrations

- Flyway scripts are `src/main/resources/db/migration/V1…V12`. Add a **new** `V13__*.sql`; never edit an applied migration
  (prod runs with `validate-on-migrate=true`).
- Keep migrations cross-DB (SQLite dev / MySQL & PostgreSQL prod): no `COMMENT`, `AUTOINCREMENT`, foreign keys,
  triggers, or DB date functions; use `BIGINT` IDs, `INTEGER` 0/1 booleans, `TIMESTAMP` written by the app.
- `db/comments/{mysql,postgres}_add_comments.sql` are manual, not Flyway. If a column type changes, sync those scripts.
- SQLite stores `DECIMAL` as floating-point NUMERIC, so money can drift in dev. Verify reconciliation math on MySQL/PostgreSQL.
- Data-scope filtering is currently wired only to the system user list (`@DataScopeAnno`); biz tables are not scoped yet.
- Excel read/write is the dependency-free `infrastructure/excel/XlsxReader|XlsxWriter` (no POI) and supports **.xlsx only**,
  despite the appendix mentioning `.xls`. Export is capped at 5000 rows.

## Config and deployment gotchas

- `dev`: SQLite + log verification codes + Swagger; `staging`: SQLite + in-memory state + log codes (intentionally weakened);
  `prod`: MySQL + Redis + email and hard startup guards (JWT ≥ 32 bytes, `reveal-code=false`, mail configured, Redis reachable).
  Do not copy staging settings into prod.
- Staging excludes Redis auto-configuration and disables mail/redis health indicators; otherwise `/actuator/health` stays DOWN
  and the deploy health check fails.
- Push to `main` triggers path-based deploys: `admin-backend/**` or `deploy/**` → backend server; `admin-frontend/**` → EdgeOne hook.
  Deployment env lives only on the server (`.env`); never commit secrets.
- Frontend build-time `VITE_API_BASE_URL`: `/api` in dev, `https://nova-api.s.ci64.com` in prod (no `/api` prefix, CORS handles it).
