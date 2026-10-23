import assert from 'node:assert/strict';
import { test } from 'node:test';

import { confirmationText, formatQuantity, whatsappLink } from '../src/confirm.ts';
import type { Order, OrderLine } from '../src/db.ts';
import { linesCsv, parseCsv, sheetTotals } from '../src/sheet.ts';

const line = (itemName: string, quantity: number | null, unit: string, itemId: number | null = null): OrderLine => ({
  itemId, itemName, quantity, unit, heard: '', needsCheck: false, note: '',
});

const order = (id: number, shopId: number, shopName: string, lines: OrderLine[]): Order => ({
  id, shopId, shopName, orderDate: '2026-10-23', source: 'text', fileName: null, mime: null,
  message: '', transcript: '', status: 'confirmed', error: null, createdAt: '', lines,
});

test('quantities drop trailing noise', () => {
  assert.equal(formatQuantity(2.5), '2.5');
  assert.equal(formatQuantity(3), '3');
  assert.equal(formatQuantity(0.1 + 0.2), '0.3');
  assert.equal(formatQuantity(null), '?');
});

test('confirmation is written in the shop language', () => {
  const lines = [line('Toor dal', 5, 'kg'), line('Sugar', 2.5, 'kg')];
  const mr = confirmationText('Shree Kirana', 'mr', lines);
  assert.match(mr, /^नमस्कार Shree Kirana/);
  assert.match(mr, /1\. Toor dal - 5 kg\n2\. Sugar - 2.5 kg/);
  assert.match(confirmationText('A', 'hi', lines), /आपका ऑर्डर मिला/);
  assert.match(confirmationText('A', 'en', lines), /Reply "yes"/);
});

test('WhatsApp links take Indian mobile numbers in any common format', () => {
  assert.equal(whatsappLink('98765 43210', 'hi'), 'https://wa.me/919876543210?text=hi');
  assert.equal(whatsappLink('+91-98765-43210', 'hi'), 'https://wa.me/919876543210?text=hi');
  assert.equal(whatsappLink('09876543210', 'hi'), 'https://wa.me/919876543210?text=hi');
  assert.equal(whatsappLink('', 'hi'), null);
  assert.equal(whatsappLink('12345', 'hi'), null);
});

test('sheet totals add up per item and unit across shops', () => {
  const totals = sheetTotals([
    order(1, 1, 'A', [line('Toor dal', 5, 'kg', 1), line('Sugar', 2, 'kg', 2)]),
    order(2, 2, 'B', [line('Toor dal', 2.5, 'KG', 1), line('Toor dal', 1, 'bag', 1), line('Sugar', null, 'kg', 2)]),
  ]);
  assert.deepEqual(totals, [
    { itemName: 'Sugar', unit: 'kg', quantity: 2, missingQuantity: 1, shops: 2 },
    { itemName: 'Toor dal', unit: 'bag', quantity: 1, missingQuantity: 0, shops: 1 },
    { itemName: 'Toor dal', unit: 'kg', quantity: 7.5, missingQuantity: 0, shops: 2 },
  ]);
});

test('unmatched items are grouped by name', () => {
  const totals = sheetTotals([
    order(1, 1, 'A', [line('Kaju', 1, 'kg')]),
    order(2, 2, 'B', [line(' kaju ', 2, 'kg')]),
  ]);
  assert.equal(totals.length, 1);
  assert.equal(totals[0].quantity, 3);
});

test('CSV export quotes awkward cells and opens in Excel', () => {
  const out = linesCsv('2026-10-23', [order(7, 1, 'Patil, Sons', [line('Oil "15 L"', 1, 'tin')])]);
  assert.ok(out.startsWith('﻿Date,Order,Shop,Item,Quantity,Unit\r\n'));
  assert.match(out, /2026-10-23,7,"Patil, Sons","Oil ""15 L""",1,tin/);
});

test('CSV import handles quotes, blank lines and Devanagari', () => {
  assert.deepEqual(parseCsv('﻿name,unit,aliases\r\n"Toor dal",kg,"tur, अरहर"\n\nSugar,kg,\n'), [
    ['name', 'unit', 'aliases'],
    ['Toor dal', 'kg', 'tur, अरहर'],
    ['Sugar', 'kg', ''],
  ]);
});
