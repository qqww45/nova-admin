import Database from 'better-sqlite3'
import { join } from 'path'
import { copyFileSync, existsSync } from 'fs'
import { getUserRoots } from './paths'

let db: Database.Database | null = null

const SCHEMA_VERSION = 1

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
}

export function closeDb() {
  if (db) {
    db.close()
    db = null
  }
}
