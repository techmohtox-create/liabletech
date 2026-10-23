import { formatQuantity } from './confirm.ts';
import type { Order } from './db.ts';

export interface SheetTotal {
  itemName: string;
  unit: string;
  quantity: number;
  /** Lines with no quantity are counted here so they are not silently dropped from the pick list. */
  missingQuantity: number;
  shops: number;
}

/**
 * Totals per item and unit across all confirmed orders, for picking and loading. Lines are
 * grouped by catalogue item when matched, otherwise by name, and kept apart by unit: 2 kg and
 * 1 bag of the same item are not added together.
 */
export function sheetTotals(orders: Order[]): SheetTotal[] {
  const totals = new Map<string, SheetTotal & { shopIds: Set<number> }>();
  for (const order of orders) {
    for (const line of order.lines) {
      const unit = line.unit.trim().toLowerCase();
      const key = `${line.itemId ?? line.itemName.trim().toLowerCase()}|${unit}`;
      let t = totals.get(key);
      if (!t) {
        t = { itemName: line.itemName.trim(), unit, quantity: 0, missingQuantity: 0, shops: 0, shopIds: new Set() };
        totals.set(key, t);
      }
      if (line.quantity === null) {
        t.missingQuantity++;
      } else {
        t.quantity += line.quantity;
      }
      t.shopIds.add(order.shopId);
    }
  }
  return [...totals.values()]
    .map(({ shopIds, ...t }) => ({ ...t, quantity: Math.round(t.quantity * 1000) / 1000, shops: shopIds.size }))
    .sort((a, b) => a.itemName.localeCompare(b.itemName) || a.unit.localeCompare(b.unit));
}

function csvCell(value: string | number): string {
  const s = String(value);
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

function csv(rows: (string | number)[][]): string {
  // The byte-order mark makes Excel open Devanagari text correctly.
  return '﻿' + rows.map((r) => r.map(csvCell).join(',')).join('\r\n') + '\r\n';
}

export function linesCsv(date: string, orders: Order[]): string {
  const rows: (string | number)[][] = [['Date', 'Order', 'Shop', 'Item', 'Quantity', 'Unit']];
  for (const o of orders) {
    for (const l of o.lines) {
      rows.push([date, o.id, o.shopName, l.itemName, formatQuantity(l.quantity), l.unit]);
    }
  }
  return csv(rows);
}

export function totalsCsv(date: string, totals: SheetTotal[]): string {
  const rows: (string | number)[][] = [['Date', 'Item', 'Total quantity', 'Unit', 'Shops', 'Lines without quantity']];
  for (const t of totals) {
    rows.push([date, t.itemName, formatQuantity(t.quantity), t.unit, t.shops, t.missingQuantity]);
  }
  return csv(rows);
}

/** Parses CSV text (quoted fields, commas and newlines inside quotes) into rows of cells. */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;
  const src = text.replace(/^﻿/, '');
  for (let i = 0; i < src.length; i++) {
    const c = src[i];
    if (quoted) {
      if (c === '"' && src[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (c === '"') {
        quoted = false;
      } else {
        cell += c;
      }
    } else if (c === '"') {
      quoted = true;
    } else if (c === ',') {
      row.push(cell);
      cell = '';
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && src[i + 1] === '\n') {
        i++;
      }
      row.push(cell);
      rows.push(row);
      row = [];
      cell = '';
    } else {
      cell += c;
    }
  }
  if (cell !== '' || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows.filter((r) => r.some((c) => c.trim() !== ''));
}
