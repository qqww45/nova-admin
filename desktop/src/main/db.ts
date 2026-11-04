import Database from 'better-sqlite3'
import { join } from 'path'
import { copyFileSync, existsSync } from 'fs'
import { getUserRoots } from './paths'

let db: Database.Database | null = null

const SCHEMA_VERSION = 5

export function getDb(): Database.Database {
  if (db) return db
  const { db: dbDir, backups } = getUserRoots()
  const file = join(dbDir, 'app.sqlite')
  if (existsSync(file)) {
    const bak = join(backups, `app-${Date.now()}.sqlite.bak`)
    try {
      copyFileSync(file, bak)
    } catch {
      /* ignore backup failure */
    }
  }
  db = new Database(file)
  db.pragma('journal_mode = WAL')
  db.pragma('foreign_keys = ON')
  migrate(db)
  return db
}

function migrate(database: Database.Database) {
  database.exec(`
    CREATE TABLE IF NOT EXISTS meta (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );
  `)

  const row = database.prepare(`SELECT value FROM meta WHERE key = 'schema_version'`).get() as
    | { value: string }
    | undefined
  const current = row ? Number(row.value) : 0

  if (current < 1) {
    database.exec(`
      CREATE TABLE IF NOT EXISTS customer (
        fid INTEGER PRIMARY KEY,
        fnumber TEXT,
        fname TEXT NOT NULL,
        address TEXT,
        disabled INTEGER DEFAULT 0,
        sync_at TEXT
      );
      CREATE TABLE IF NOT EXISTS sale_order (
        fid INTEGER PRIMARY KEY,
        fbillno TEXT,
        fdate TEXT,
        customer_fid INTEGER,
        customer_name TEXT,
        amount_cents INTEGER DEFAULT 0,
        status TEXT,
        sync_at TEXT
      );
      CREATE TABLE IF NOT EXISTS sale_order_line (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        order_fid INTEGER NOT NULL,
        material_number TEXT,
        material_name TEXT,
        unit TEXT,
        qty_milli INTEGER DEFAULT 0,
        price_cents INTEGER DEFAULT 0,
        amount_cents INTEGER DEFAULT 0,
        customer_po TEXT
      );
      CREATE TABLE IF NOT EXISTS outbound (
        fid INTEGER PRIMARY KEY,
        fbillno TEXT,
        fdate TEXT,
        customer_fid INTEGER,
        customer_name TEXT,
        amount_cents INTEGER DEFAULT 0,
        status TEXT,
        cancelled INTEGER DEFAULT 0,
        sync_at TEXT
      );
      CREATE TABLE IF NOT EXISTS outbound_line (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        outbound_fid INTEGER NOT NULL,
        material_number TEXT,
        material_name TEXT,
        unit TEXT,
        qty_milli INTEGER DEFAULT 0,
        price_cents INTEGER DEFAULT 0,
        amount_cents INTEGER DEFAULT 0,
        src_order_no TEXT,
        customer_po TEXT,
        remark TEXT,
        remark2 TEXT
      );
      CREATE TABLE IF NOT EXISTS month_settle (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        customer_fid INTEGER NOT NULL,
        month_key TEXT NOT NULL,
        paid_cents INTEGER DEFAULT 0,
        invoiced_cents INTEGER DEFAULT 0,
        note TEXT,
        source TEXT DEFAULT 'manual',
        updated_at TEXT,
        UNIQUE(customer_fid, month_key)
      );
      CREATE TABLE IF NOT EXISTS statement_line_extra (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        customer_fid INTEGER NOT NULL,
        month_key TEXT NOT NULL,
        line_date TEXT,
        delivery_no TEXT,
        purchase_order_no TEXT,
        sales_order_no TEXT,
        material_number TEXT,
        material_name TEXT,
        unit TEXT,
        qty_milli INTEGER DEFAULT 0,
        price_cents INTEGER DEFAULT 0,
        amount_cents INTEGER DEFAULT 0,
        remark TEXT,
        remark2 TEXT
      );
      CREATE TABLE IF NOT EXISTS sync_run (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        entity TEXT NOT NULL,
        status TEXT NOT NULL,
        row_count INTEGER DEFAULT 0,
        message TEXT,
        started_at TEXT,
        finished_at TEXT
      );
    `)
    database
      .prepare(
        `INSERT INTO meta(key, value) VALUES('schema_version', ?)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value`
      )
      .run(String(SCHEMA_VERSION))
  }

  // v2：为上行同步补字段。
  //   entry_id —— 金蝶分录内码，作为服务端幂等键（取不到时由上行侧按单据+行序号生成）
  //   sync_at  —— 本行写入本地库的时间，用于"只上报上次之后变化的行"
  if (current < 2) {
    const cols = database.prepare(`PRAGMA table_info(outbound_line)`).all() as Array<{ name: string }>
    const has = (name: string) => cols.some((c) => c.name === name)
    if (!has('entry_id')) database.exec(`ALTER TABLE outbound_line ADD COLUMN entry_id INTEGER`)
    if (!has('sync_at')) database.exec(`ALTER TABLE outbound_line ADD COLUMN sync_at TEXT`)
    database.exec(`CREATE INDEX IF NOT EXISTS idx_outbound_line_sync ON outbound_line(sync_at)`)
    database
      .prepare(`UPDATE meta SET value = ? WHERE key = 'schema_version'`)
      .run(String(SCHEMA_VERSION))
  }

  // v3：补「规格」列。对账单（金样板）明细的规格列需要 FMaterialId.FSpecification，
  // 此前整条链路（采集 → 落库 → 导出）都没有采集该字段，导出的规格永远为空。
  // 老库补列后为 NULL，下一次全量同步（分录按单 DELETE+INSERT 重建）会自动回填。
  if (current < 3) {
    const cols = database.prepare(`PRAGMA table_info(outbound_line)`).all() as Array<{ name: string }>
    const has = (name: string) => cols.some((c) => c.name === name)
    if (!has('spec')) database.exec(`ALTER TABLE outbound_line ADD COLUMN spec TEXT`)
    database
      .prepare(`UPDATE meta SET value = ? WHERE key = 'schema_version'`)
      .run(String(SCHEMA_VERSION))
  }

  // v4：双水位增量（方案 §3.1 目标形态）。存金蝶 FModifyDate（单据修改时间），
  // 有了它增量才能"抓到改过的历史单"，不再依赖用户手填的 FDate 起点。
  if (current < 4) {
    const cols = database.prepare(`PRAGMA table_info(outbound)`).all() as Array<{ name: string }>
    const has = (name: string) => cols.some((c) => c.name === name)
    if (!has('modify_time')) database.exec(`ALTER TABLE outbound ADD COLUMN modify_time TEXT`)
    database
      .prepare(`UPDATE meta SET value = ? WHERE key = 'schema_version'`)
      .run(String(SCHEMA_VERSION))
  }

  // v5：同步可视化。sync_run 补 source 列（手动 / 托盘定时 / 远程指令），
  // 新增 nova_upload_run 表记录每次上行批次（此前只存最后一次结果，历史无从看起）。
  // 老数据 source 为 NULL，历史页显示为「—」。
  if (current < 5) {
    const cols = database.prepare(`PRAGMA table_info(sync_run)`).all() as Array<{ name: string }>
    const has = (name: string) => cols.some((c) => c.name === name)
    if (!has('source')) database.exec(`ALTER TABLE sync_run ADD COLUMN source TEXT`)
    database.exec(`
      CREATE TABLE IF NOT EXISTS nova_upload_run (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        batch_id TEXT,
        status TEXT NOT NULL,
        message TEXT,
        received INTEGER DEFAULT 0,
        upserted INTEGER DEFAULT 0,
        skipped INTEGER DEFAULT 0,
        started_at TEXT,
        finished_at TEXT
      );
    `)
    database
      .prepare(`UPDATE meta SET value = ? WHERE key = 'schema_version'`)
      .run(String(SCHEMA_VERSION))
  }
}

export function closeDb() {
  if (db) {
    db.close()
    db = null
  }
}
