import { mkdirSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { dirname } from 'node:path';

import { SAMPLE_CATALOGUE } from './seed.ts';

export type Language = 'hi' | 'mr' | 'en';
export type Source = 'voice' | 'photo' | 'text';
/**
 * reading: the AI is working on it. review: lines are ready for staff to check.
 * sent: confirmation sent to the shop, waiting for its reply. confirmed: on the order sheet.
 */
export type OrderStatus = 'reading' | 'review' | 'sent' | 'confirmed' | 'cancelled';
export const ORDER_STATUSES: OrderStatus[] = ['reading', 'review', 'sent', 'confirmed', 'cancelled'];

export interface Item {
  id: number;
  name: string;
  unit: string;
  /** Other names shopkeepers use, comma separated: Hindi, Marathi, local spellings. */
  aliases: string;
  active: boolean;
}

export interface Shop {
  id: number;
  name: string;
  phone: string;
  area: string;
  language: Language;
}

export interface OrderLine {
  id?: number;
  itemId: number | null;
  itemName: string;
  quantity: number | null;
  unit: string;
  /** What the shopkeeper actually said or wrote for this line, as read by the AI. */
  heard: string;
  needsCheck: boolean;
  note: string;
}

export interface Order {
  id: number;
  shopId: number;
  shopName: string;
  orderDate: string;
  source: Source;
  fileName: string | null;
  mime: string | null;
  /** The text message itself, for orders that arrived as text. */
  message: string;
  /** Speech-to-text output for voice notes, or the text the AI read off a photo. */
  transcript: string;
  status: OrderStatus;
  error: string | null;
  createdAt: string;
  lines: OrderLine[];
}

const SCHEMA = `
  CREATE TABLE IF NOT EXISTS item (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    unit TEXT NOT NULL,
    aliases TEXT NOT NULL DEFAULT '',
    active INTEGER NOT NULL DEFAULT 1
  );
  CREATE TABLE IF NOT EXISTS shop (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL DEFAULT '',
    area TEXT NOT NULL DEFAULT '',
    language TEXT NOT NULL DEFAULT 'hi' CHECK (language IN ('hi', 'mr', 'en'))
  );
  CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY,
    shop_id INTEGER NOT NULL REFERENCES shop(id),
    order_date TEXT NOT NULL,
    source TEXT NOT NULL CHECK (source IN ('voice', 'photo', 'text')),
    file_name TEXT,
    mime TEXT,
    message TEXT NOT NULL DEFAULT '',
    transcript TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'reading',
    error TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
  );
  CREATE INDEX IF NOT EXISTS orders_by_date ON orders (order_date);
  CREATE TABLE IF NOT EXISTS order_line (
    id INTEGER PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    item_id INTEGER REFERENCES item(id) ON DELETE SET NULL,
    item_name TEXT NOT NULL,
    quantity REAL,
    unit TEXT NOT NULL DEFAULT '',
    heard TEXT NOT NULL DEFAULT '',
    needs_check INTEGER NOT NULL DEFAULT 0,
    note TEXT NOT NULL DEFAULT ''
  );
`;

type Row = Record<string, unknown>;

const toItem = (r: Row): Item => ({
  id: r.id as number,
  name: r.name as string,
  unit: r.unit as string,
  aliases: r.aliases as string,
  active: r.active === 1,
});

const toShop = (r: Row): Shop => ({
  id: r.id as number,
  name: r.name as string,
  phone: r.phone as string,
  area: r.area as string,
  language: r.language as Language,
});

const toLine = (r: Row): OrderLine => ({
  id: r.id as number,
  itemId: (r.item_id as number | null) ?? null,
  itemName: r.item_name as string,
  quantity: (r.quantity as number | null) ?? null,
  unit: r.unit as string,
  heard: r.heard as string,
  needsCheck: r.needs_check === 1,
  note: r.note as string,
});

/** All reads and writes go through here, so the HTTP layer never sees SQL. */
export class Store {
  private readonly db: DatabaseSync;

  constructor(path: string) {
    if (path !== ':memory:') {
      mkdirSync(dirname(path), { recursive: true });
    }
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL;');
    this.db.exec(SCHEMA);
    // Orders interrupted by a restart would otherwise sit in "reading" for ever.
    this.db
      .prepare(`UPDATE orders SET status = 'review', error = 'The app restarted while reading this order. Press Read again.' WHERE status = 'reading'`)
      .run();
  }

  seedSampleCatalogue(): void {
    const { n } = this.db.prepare('SELECT COUNT(*) AS n FROM item').get() as { n: number };
    if (n === 0) {
      for (const item of SAMPLE_CATALOGUE) {
        this.saveItem(item);
      }
    }
  }

  // Catalogue

  items(activeOnly = false): Item[] {
    const where = activeOnly ? 'WHERE active = 1' : '';
    return this.db.prepare(`SELECT * FROM item ${where} ORDER BY name COLLATE NOCASE`).all().map(toItem);
  }

  saveItem(item: Omit<Item, 'id' | 'active'> & { id?: number; active?: boolean }): Item {
    const active = item.active === false ? 0 : 1;
    if (item.id) {
      this.db
        .prepare('UPDATE item SET name = ?, unit = ?, aliases = ?, active = ? WHERE id = ?')
        .run(item.name, item.unit, item.aliases, active, item.id);
      return this.item(item.id)!;
    }
    const { lastInsertRowid } = this.db
      .prepare('INSERT INTO item (name, unit, aliases, active) VALUES (?, ?, ?, ?)')
      .run(item.name, item.unit, item.aliases, active);
    return this.item(Number(lastInsertRowid))!;
  }

  item(id: number): Item | undefined {
    const r = this.db.prepare('SELECT * FROM item WHERE id = ?').get(id);
    return r ? toItem(r) : undefined;
  }

  deleteItem(id: number): void {
    this.db.prepare('DELETE FROM item WHERE id = ?').run(id);
  }

  // Shops

  shops(): Shop[] {
    return this.db.prepare('SELECT * FROM shop ORDER BY name COLLATE NOCASE').all().map(toShop);
  }

  shop(id: number): Shop | undefined {
    const r = this.db.prepare('SELECT * FROM shop WHERE id = ?').get(id);
    return r ? toShop(r) : undefined;
  }

  saveShop(shop: Omit<Shop, 'id'> & { id?: number }): Shop {
    if (shop.id) {
      this.db
        .prepare('UPDATE shop SET name = ?, phone = ?, area = ?, language = ? WHERE id = ?')
        .run(shop.name, shop.phone, shop.area, shop.language, shop.id);
      return this.shop(shop.id)!;
    }
    const { lastInsertRowid } = this.db
      .prepare('INSERT INTO shop (name, phone, area, language) VALUES (?, ?, ?, ?)')
      .run(shop.name, shop.phone, shop.area, shop.language);
    return this.shop(Number(lastInsertRowid))!;
  }

  shopHasOrders(id: number): boolean {
    return this.db.prepare('SELECT 1 FROM orders WHERE shop_id = ? LIMIT 1').get(id) !== undefined;
  }

  deleteShop(id: number): void {
    this.db.prepare('DELETE FROM shop WHERE id = ?').run(id);
  }

  // Orders

  createOrder(o: { shopId: number; orderDate: string; source: Source; fileName: string | null; mime: string | null; message: string }): number {
    const { lastInsertRowid } = this.db
      .prepare('INSERT INTO orders (shop_id, order_date, source, file_name, mime, message) VALUES (?, ?, ?, ?, ?, ?)')
      .run(o.shopId, o.orderDate, o.source, o.fileName, o.mime, o.message);
    return Number(lastInsertRowid);
  }

  order(id: number): Order | undefined {
    const r = this.db
      .prepare('SELECT o.*, s.name AS shop_name FROM orders o JOIN shop s ON s.id = o.shop_id WHERE o.id = ?')
      .get(id);
    return r ? this.toOrder(r) : undefined;
  }

  ordersOn(date: string, status?: OrderStatus): Order[] {
    const rows = status
      ? this.db
          .prepare('SELECT o.*, s.name AS shop_name FROM orders o JOIN shop s ON s.id = o.shop_id WHERE o.order_date = ? AND o.status = ? ORDER BY s.name COLLATE NOCASE, o.id')
          .all(date, status)
      : this.db
          .prepare('SELECT o.*, s.name AS shop_name FROM orders o JOIN shop s ON s.id = o.shop_id WHERE o.order_date = ? ORDER BY o.id DESC')
          .all(date);
    return rows.map((r) => this.toOrder(r));
  }

  updateOrder(id: number, fields: Partial<{ transcript: string; status: OrderStatus; error: string | null; shopId: number; orderDate: string }>): void {
    const columns: Record<string, string> = { transcript: 'transcript', status: 'status', error: 'error', shopId: 'shop_id', orderDate: 'order_date' };
    const entries = Object.entries(fields).filter(([, v]) => v !== undefined);
    if (entries.length === 0) {
      return;
    }
    const sets = entries.map(([k]) => `${columns[k]} = ?`).join(', ');
    this.db.prepare(`UPDATE orders SET ${sets} WHERE id = ?`).run(...entries.map(([, v]) => v as string | number | null), id);
  }

  replaceLines(orderId: number, lines: OrderLine[]): void {
    this.db.exec('BEGIN');
    try {
      this.db.prepare('DELETE FROM order_line WHERE order_id = ?').run(orderId);
      const insert = this.db.prepare(
        'INSERT INTO order_line (order_id, position, item_id, item_name, quantity, unit, heard, needs_check, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
      );
      lines.forEach((l, i) =>
        insert.run(orderId, i, l.itemId, l.itemName, l.quantity, l.unit, l.heard, l.needsCheck ? 1 : 0, l.note),
      );
      this.db.exec('COMMIT');
    } catch (e) {
      this.db.exec('ROLLBACK');
      throw e;
    }
  }

  deleteOrder(id: number): void {
    this.db.prepare('DELETE FROM orders WHERE id = ?').run(id);
  }

  close(): void {
    this.db.close();
  }

  private toOrder(r: Row): Order {
    const lines = this.db.prepare('SELECT * FROM order_line WHERE order_id = ? ORDER BY position').all(r.id as number).map(toLine);
    return {
      id: r.id as number,
      shopId: r.shop_id as number,
      shopName: r.shop_name as string,
      orderDate: r.order_date as string,
      source: r.source as Source,
      fileName: (r.file_name as string | null) ?? null,
      mime: (r.mime as string | null) ?? null,
      message: r.message as string,
      transcript: r.transcript as string,
      status: r.status as OrderStatus,
      error: (r.error as string | null) ?? null,
      createdAt: r.created_at as string,
      lines,
    };
  }
}
