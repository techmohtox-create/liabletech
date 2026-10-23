import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import type { AddressInfo } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, test } from 'node:test';

import { createApp } from '../src/app.ts';
import { Store, type Item } from '../src/db.ts';
import type { ExtractInput, Extractor } from '../src/extract.ts';
import type { Transcriber } from '../src/transcribe.ts';

/** Stands in for Claude: one line per "<quantity> <unit> <item>" phrase, matched by exact name. */
class FakeExtractor implements Extractor {
  calls: ExtractInput[] = [];
  async extract(input: ExtractInput, catalogue: Item[]) {
    this.calls.push(input);
    if (input.kind === 'image') {
      return { transcript: '2 kg Sugar', lines: [{ itemId: catalogue.find((i) => i.name === 'Sugar')!.id, itemName: 'Sugar', quantity: 2, unit: 'kg', heard: '2 kg sakhar', needsCheck: false, note: '' }] };
    }
    const lines = input.text.split(',').map((part) => {
      const [q, unit, ...name] = part.trim().split(/\s+/);
      const item = catalogue.find((i) => i.name.toLowerCase() === name.join(' ').toLowerCase());
      return { itemId: item?.id ?? null, itemName: item?.name ?? name.join(' '), quantity: Number(q), unit, heard: part.trim(), needsCheck: !item, note: item ? '' : 'Not matched' };
    });
    return { transcript: '', lines };
  }
}

class FakeTranscriber implements Transcriber {
  async transcribe() {
    return '5 kg Toor dal';
  }
}

let dir: string;
let base: string;
let store: Store;
let close: () => void;
const extractor = new FakeExtractor();

before(async () => {
  dir = mkdtempSync(join(tmpdir(), 'ordervaani-test-'));
  store = new Store(':memory:');
  store.seedSampleCatalogue();
  const { app } = createApp({ store, uploadDir: join(dir, 'uploads'), publicDir: join(dir, 'public'), extractor, transcriber: new FakeTranscriber() });
  const server = app.listen(0);
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${(server.address() as AddressInfo).port}/api`;
  close = () => server.close();
});

after(() => {
  close();
  store.close();
  rmSync(dir, { recursive: true, force: true });
});

async function call(path: string, init: RequestInit & { json?: unknown } = {}) {
  const { json, ...rest } = init;
  const res = await fetch(base + path, json === undefined ? rest : {
    method: 'POST', ...rest, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(json),
  });
  return { status: res.status, body: res.status === 204 ? null : res.headers.get('content-type')?.includes('json') ? await res.json() : await res.text() };
}

async function settled(id: number) {
  for (let i = 0; i < 50; i++) {
    const { body } = await call(`/orders/${id}`);
    if (body.status !== 'reading') return body;
    await new Promise((r) => setTimeout(r, 20));
  }
  throw new Error('order never finished reading');
}

const DATE = '2026-10-23';

test('a text order goes from reading to the order sheet', async () => {
  const shop = (await call('/shops', { json: { name: 'Shree Kirana', phone: '9876543210', language: 'mr' } })).body;
  const form = new FormData();
  form.append('shopId', String(shop.id));
  form.append('orderDate', DATE);
  form.append('message', '5 kg Toor dal, 2 carton Biscuits carton, 1 kg Kaju');
  const created = await call('/orders', { method: 'POST', body: form });
  assert.equal(created.status, 201);

  let order = await settled(created.body.id);
  assert.equal(order.status, 'review');
  assert.equal(order.lines.length, 3);
  assert.equal(order.lines[2].needsCheck, true);
  assert.match(order.confirmation.text, /नमस्कार Shree Kirana/);
  assert.match(order.confirmation.whatsapp, /^https:\/\/wa\.me\/919876543210\?text=/);

  // A flagged line blocks confirmation until staff deal with it.
  const blocked = await call(`/orders/${order.id}/status`, { json: { status: 'confirmed' } });
  assert.equal(blocked.status, 409);
  assert.match(blocked.body.error, /Line 3 is flagged/);

  const lines = order.lines.slice(0, 2);
  order = (await call(`/orders/${order.id}`, { method: 'PUT', json: { lines } })).body;
  assert.deepEqual(order.problems, []);
  assert.equal((await call(`/orders/${order.id}/status`, { json: { status: 'sent' } })).body.status, 'sent');
  assert.equal((await call(`/orders/${order.id}/status`, { json: { status: 'confirmed' } })).body.status, 'confirmed');

  const sheet = (await call(`/sheet?date=${DATE}`)).body;
  assert.equal(sheet.orders.length, 1);
  assert.deepEqual(sheet.totals.map((t: { itemName: string; quantity: number }) => [t.itemName, t.quantity]), [['Biscuits carton', 2], ['Toor dal', 5]]);

  const csv = await call(`/sheet.csv?date=${DATE}&view=totals`);
  assert.match(csv.body, /Toor dal,5,kg,1,0/);

  // Editing a confirmed order takes it off the sheet until it is confirmed again.
  await call(`/orders/${order.id}`, { method: 'PUT', json: { lines: lines.slice(0, 1) } });
  assert.equal((await call(`/orders/${order.id}`)).body.status, 'review');
  assert.equal((await call(`/sheet?date=${DATE}`)).body.orders.length, 0);
});

test('a voice note is transcribed, then read', async () => {
  const shop = (await call('/shops', { json: { name: 'Gupta Stores' } })).body;
  const form = new FormData();
  form.append('shopId', String(shop.id));
  form.append('orderDate', DATE);
  form.append('file', new Blob([new Uint8Array(Buffer.from('fake'))], { type: 'application/octet-stream' }), 'PTT-20261023-WA0001.opus');
  const created = await call('/orders', { method: 'POST', body: form });
  assert.equal(created.body.source, 'voice');
  const order = await settled(created.body.id);
  assert.equal(order.transcript, '5 kg Toor dal');
  assert.equal(order.lines[0].itemName, 'Toor dal');
  assert.equal(order.confirmation.whatsapp, null, 'no phone number, so no WhatsApp link');

  const file = await fetch(`${base}/orders/${order.id}/file`);
  assert.equal(file.status, 200);

  // Staff correct the transcript and read again.
  await call(`/orders/${order.id}/read`, { json: { transcript: '3 kg Sugar' } });
  const reread = await settled(order.id);
  assert.equal(reread.lines[0].itemName, 'Sugar');
  assert.equal(reread.lines[0].quantity, 3);
});

test('a photo goes to the AI as an image', async () => {
  const shop = (await call('/shops', { json: { name: 'Photo Shop' } })).body;
  const form = new FormData();
  form.append('shopId', String(shop.id));
  // A 1x1 PNG.
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');
  form.append('file', new Blob([new Uint8Array(png)], { type: 'image/png' }), 'list.png');
  const created = await call('/orders', { method: 'POST', body: form });
  const order = await settled(created.body.id);
  assert.equal(order.source, 'photo');
  assert.equal(order.transcript, '2 kg Sugar');
  const call0 = extractor.calls.at(-1)!;
  assert.equal(call0.kind, 'image');
  assert.equal(call0.kind === 'image' && call0.mediaType, 'image/png');
});

test('without an AI key the order waits for lines typed by hand', async () => {
  const s = new Store(':memory:');
  const { read } = createApp({ store: s, uploadDir: join(dir, 'u2'), publicDir: dir });
  const shop = s.saveShop({ name: 'X', phone: '', area: '', language: 'hi' });
  const id = s.createOrder({ shopId: shop.id, orderDate: DATE, source: 'text', fileName: null, mime: null, message: '1 kg Sugar' });
  s.updateOrder(id, { transcript: '1 kg Sugar' });
  await read(id);
  const order = s.order(id)!;
  assert.equal(order.status, 'review');
  assert.match(order.error!, /ANTHROPIC_API_KEY/);
  s.close();
});

test('bad input gets a clear message', async () => {
  const noShop = new FormData();
  noShop.append('message', 'x');
  assert.equal((await call('/orders', { method: 'POST', body: noShop })).status, 400);
  assert.equal((await call('/items', { json: { name: '', unit: 'kg' } })).body.error, 'Enter the item name');
  assert.equal((await call('/orders/99999')).status, 404);
  const imported = await call('/items/import', { json: { csv: 'name,unit,aliases\nSugar,kg,cheeni;sakhar\nCashew,kg,kaju' } });
  assert.deepEqual(imported.body, { added: 1, updated: 1 });
  const shop = (await call('/shops')).body[0];
  assert.equal((await call(`/shops/${shop.id}`, { method: 'DELETE' })).status, 409);
});
