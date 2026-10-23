import { randomUUID } from 'node:crypto';
import { mkdirSync, rmSync } from 'node:fs';
import { extname, join } from 'node:path';

import express, { type NextFunction, type Request, type Response } from 'express';
import multer from 'multer';
import { z } from 'zod';

import { confirmationText, whatsappLink } from './confirm.ts';
import { ORDER_STATUSES, type Order, type OrderStatus, type Store } from './db.ts';
import { describeAiError, type ExtractInput, type Extractor } from './extract.ts';
import { kindOf, prepareImage } from './media.ts';
import { linesCsv, parseCsv, sheetTotals, totalsCsv } from './sheet.ts';
import type { Transcriber } from './transcribe.ts';

export interface AppDeps {
  store: Store;
  uploadDir: string;
  publicDir: string;
  /** Missing when no ANTHROPIC_API_KEY is set: orders then need their lines typed in. */
  extractor?: Extractor;
  /** Missing when no SARVAM_API_KEY is set: staff type the voice note's words instead. */
  transcriber?: Transcriber;
  model?: string;
}

const DATE = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Use a date like 2026-10-23');
const ID = z.coerce.number().int().positive();

const ItemBody = z.object({
  name: z.string().trim().min(1, 'Enter the item name').max(120),
  unit: z.string().trim().min(1, 'Enter the unit, such as kg or box').max(30),
  aliases: z.string().trim().max(1000).default(''),
  active: z.boolean().default(true),
});

const ShopBody = z.object({
  name: z.string().trim().min(1, 'Enter the shop name').max(120),
  phone: z.string().trim().max(20).default(''),
  area: z.string().trim().max(120).default(''),
  language: z.enum(['hi', 'mr', 'en']).default('hi'),
});

const LineBody = z.object({
  itemId: z.number().int().positive().nullable(),
  itemName: z.string().trim().max(200),
  quantity: z.number().nonnegative().nullable(),
  unit: z.string().trim().max(30),
  heard: z.string().max(500).default(''),
  needsCheck: z.boolean().default(false),
  note: z.string().max(300).default(''),
});

const OrderUpdate = z.object({
  shopId: z.number().int().positive().optional(),
  orderDate: DATE.optional(),
  transcript: z.string().max(10_000).optional(),
  lines: z.array(LineBody).max(300).optional(),
});

class HttpError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** Reasons a set of lines is not ready to send to the shop or put on the sheet. */
export function readinessProblems(lines: Order['lines']): string[] {
  if (lines.length === 0) {
    return ['The order has no lines.'];
  }
  const problems: string[] = [];
  lines.forEach((l, i) => {
    if (!l.itemName.trim()) problems.push(`Line ${i + 1} has no item.`);
    if (l.quantity === null) problems.push(`Line ${i + 1} has no quantity.`);
    if (l.needsCheck) problems.push(`Line ${i + 1} is flagged for checking.`);
  });
  return problems;
}

export function createApp(deps: AppDeps) {
  const { store, uploadDir } = deps;
  mkdirSync(uploadDir, { recursive: true });

  const upload = multer({
    storage: multer.diskStorage({
      destination: uploadDir,
      filename: (_req, file, cb) => cb(null, randomUUID() + extname(file.originalname).toLowerCase().slice(0, 8)),
    }),
    limits: { fileSize: 25 * 1024 * 1024, files: 1 },
  });

  const parse = <T>(schema: z.ZodType<T>, value: unknown): T => {
    const result = schema.safeParse(value);
    if (!result.success) {
      throw new HttpError(400, result.error.issues[0]?.message ?? 'Invalid input');
    }
    return result.data;
  };

  const findOrder = (idParam: unknown): Order => {
    const order = store.order(parse(ID, idParam));
    if (!order) throw new HttpError(404, 'Order not found');
    return order;
  };

  const withConfirmation = (order: Order) => {
    const shop = store.shop(order.shopId)!;
    const text = confirmationText(shop.name, shop.language, order.lines);
    return {
      ...order,
      shop,
      problems: readinessProblems(order.lines),
      confirmation: { text, whatsapp: whatsappLink(shop.phone, text) },
    };
  };

  /** Runs speech-to-text (voice notes) and the AI (all orders), then leaves the order for review. */
  const read = async (id: number): Promise<void> => {
    const order = store.order(id)!;
    try {
      let input: ExtractInput;
      if (order.source === 'photo') {
        input = { kind: 'image', shopName: order.shopName, ...(await prepareImage(join(uploadDir, order.fileName!), order.mime ?? '')) };
      } else {
        let text = order.transcript.trim();
        if (order.source === 'voice' && !text) {
          if (!deps.transcriber) {
            throw new Error('Speech-to-text is not set up (no SARVAM_API_KEY). Play the voice note, type what the shopkeeper says into the text box and press Read again.');
          }
          text = await deps.transcriber.transcribe(join(uploadDir, order.fileName!), order.mime ?? 'audio/ogg');
          store.updateOrder(id, { transcript: text });
          if (!text) throw new Error('No speech was found in this voice note.');
        }
        input = { kind: 'text', shopName: order.shopName, text, fromVoice: order.source === 'voice' };
      }
      if (!deps.extractor) {
        throw new Error('The AI is not set up (no ANTHROPIC_API_KEY). Add the lines by hand, or set the key and press Read again.');
      }
      const result = await deps.extractor.extract(input, store.items(true));
      store.replaceLines(id, result.lines);
      store.updateOrder(id, {
        ...(order.source === 'photo' ? { transcript: result.transcript } : {}),
        status: 'review',
        error: result.lines.length === 0 ? 'No order lines were found. Check the note and add lines by hand if needed.' : null,
      });
    } catch (e) {
      store.updateOrder(id, { status: 'review', error: describeAiError(e) });
    }
  };

  const app = express();
  app.use(express.json({ limit: '1mb' }));
  app.use(express.static(deps.publicDir));

  app.get('/api/status', (_req, res) => {
    res.json({ ai: Boolean(deps.extractor), speech: Boolean(deps.transcriber), model: deps.model ?? null, today: today() });
  });

  // Catalogue

  app.get('/api/items', (_req, res) => {
    res.json(store.items());
  });
  app.post('/api/items', (req, res) => {
    res.status(201).json(store.saveItem(parse(ItemBody, req.body)));
  });
  app.put('/api/items/:id', (req, res) => {
    const id = parse(ID, req.params.id);
    if (!store.item(id)) throw new HttpError(404, 'Item not found');
    res.json(store.saveItem({ ...parse(ItemBody, req.body), id }));
  });
  app.delete('/api/items/:id', (req, res) => {
    store.deleteItem(parse(ID, req.params.id));
    res.status(204).end();
  });
  /** CSV with a header row: name, unit, aliases (aliases separated by semicolons or commas inside quotes). */
  app.post('/api/items/import', (req, res) => {
    const { csv } = parse(z.object({ csv: z.string().max(500_000) }), req.body);
    const rows = parseCsv(csv);
    const header = (rows.shift() ?? []).map((h) => h.trim().toLowerCase());
    const col = (name: string) => header.indexOf(name);
    if (col('name') < 0 || col('unit') < 0) {
      throw new HttpError(400, 'The first row must name the columns, including "name" and "unit" (and optionally "aliases").');
    }
    const existing = new Map(store.items().map((i) => [i.name.toLowerCase(), i]));
    let added = 0;
    let updated = 0;
    rows.forEach((r, n) => {
      const name = (r[col('name')] ?? '').trim();
      const unit = (r[col('unit')] ?? '').trim();
      if (!name || !unit) throw new HttpError(400, `Row ${n + 2} needs both a name and a unit.`);
      const aliases = col('aliases') >= 0 ? (r[col('aliases')] ?? '').replace(/;/g, ',').trim() : '';
      const match = existing.get(name.toLowerCase());
      store.saveItem({ id: match?.id, name, unit, aliases, active: true });
      if (match) updated++;
      else added++;
    });
    res.json({ added, updated });
  });

  // Shops

  app.get('/api/shops', (_req, res) => {
    res.json(store.shops());
  });
  app.post('/api/shops', (req, res) => {
    res.status(201).json(store.saveShop(parse(ShopBody, req.body)));
  });
  app.put('/api/shops/:id', (req, res) => {
    const id = parse(ID, req.params.id);
    if (!store.shop(id)) throw new HttpError(404, 'Shop not found');
    res.json(store.saveShop({ ...parse(ShopBody, req.body), id }));
  });
  app.delete('/api/shops/:id', (req, res) => {
    const id = parse(ID, req.params.id);
    if (store.shopHasOrders(id)) throw new HttpError(409, 'This shop has orders, so it cannot be deleted.');
    store.deleteShop(id);
    res.status(204).end();
  });

  // Orders

  app.get('/api/orders', (req, res) => {
    const date = parse(DATE, req.query.date ?? today());
    res.json(store.ordersOn(date).map(withConfirmation));
  });

  app.post('/api/orders', upload.single('file'), (req, res) => {
    const removeUpload = () => req.file && rmSync(req.file.path, { force: true });
    try {
      const body = parse(
        z.object({ shopId: ID, orderDate: DATE.default(today()), message: z.string().trim().max(10_000).default('') }),
        req.body,
      );
      if (!store.shop(body.shopId)) throw new HttpError(400, 'Choose a shop');
      let source: 'voice' | 'photo' | 'text' = 'text';
      if (req.file) {
        const kind = kindOf(req.file.originalname, req.file.mimetype);
        if (!kind) throw new HttpError(400, 'Upload a voice note (audio) or a photo of the list.');
        source = kind;
      } else if (!body.message) {
        throw new HttpError(400, 'Upload a voice note or photo, or paste the order text.');
      }
      const id = store.createOrder({
        shopId: body.shopId,
        orderDate: body.orderDate,
        source,
        fileName: req.file?.filename ?? null,
        mime: req.file?.mimetype ?? null,
        message: body.message,
      });
      if (source === 'text') store.updateOrder(id, { transcript: body.message });
      void read(id);
      res.status(201).json(withConfirmation(store.order(id)!));
    } catch (e) {
      removeUpload();
      throw e;
    }
  });

  app.get('/api/orders/:id', (req, res) => {
    res.json(withConfirmation(findOrder(req.params.id)));
  });

  app.get('/api/orders/:id/file', (req, res) => {
    const order = findOrder(req.params.id);
    if (!order.fileName) throw new HttpError(404, 'This order has no file');
    if (order.mime) res.type(order.mime);
    res.sendFile(order.fileName, { root: uploadDir });
  });

  app.put('/api/orders/:id', (req, res) => {
    const order = findOrder(req.params.id);
    if (order.status === 'reading') throw new HttpError(409, 'The AI is still reading this order.');
    const body = parse(OrderUpdate, req.body);
    if (body.shopId !== undefined && !store.shop(body.shopId)) throw new HttpError(400, 'Choose a shop');
    if (body.lines) {
      store.replaceLines(order.id, body.lines);
      // Changing a confirmed order takes it off the sheet until it is confirmed again.
      if (order.status === 'sent' || order.status === 'confirmed') store.updateOrder(order.id, { status: 'review' });
    }
    store.updateOrder(order.id, { shopId: body.shopId, orderDate: body.orderDate, transcript: body.transcript });
    res.json(withConfirmation(store.order(order.id)!));
  });

  /** Reads the order again, from the (possibly corrected) text, replacing the current lines. */
  app.post('/api/orders/:id/read', (req, res) => {
    const order = findOrder(req.params.id);
    if (order.status === 'reading') throw new HttpError(409, 'The AI is already reading this order.');
    const { transcript } = parse(z.object({ transcript: z.string().max(10_000).optional() }), req.body ?? {});
    store.updateOrder(order.id, { status: 'reading', error: null, transcript });
    void read(order.id);
    res.json(withConfirmation(store.order(order.id)!));
  });

  app.post('/api/orders/:id/status', (req, res) => {
    const order = findOrder(req.params.id);
    const { status } = parse(z.object({ status: z.enum(ORDER_STATUSES.filter((s) => s !== 'reading') as [OrderStatus, ...OrderStatus[]]) }), req.body);
    if (order.status === 'reading') throw new HttpError(409, 'The AI is still reading this order.');
    if (status === 'sent' || status === 'confirmed') {
      const problems = readinessProblems(order.lines);
      if (problems.length) throw new HttpError(409, problems.join(' '));
    }
    store.updateOrder(order.id, { status });
    res.json(withConfirmation(store.order(order.id)!));
  });

  app.delete('/api/orders/:id', (req, res) => {
    const order = findOrder(req.params.id);
    if (order.status === 'reading') throw new HttpError(409, 'The AI is still reading this order.');
    store.deleteOrder(order.id);
    if (order.fileName) rmSync(join(uploadDir, order.fileName), { force: true });
    res.status(204).end();
  });

  // Order sheet

  app.get('/api/sheet', (req, res) => {
    const date = parse(DATE, req.query.date ?? today());
    const confirmed = store.ordersOn(date, 'confirmed');
    const waiting = store.ordersOn(date).filter((o) => o.status !== 'confirmed' && o.status !== 'cancelled');
    res.json({ date, orders: confirmed, totals: sheetTotals(confirmed), waiting: waiting.length });
  });

  app.get('/api/sheet.csv', (req, res) => {
    const date = parse(DATE, req.query.date ?? today());
    const view = parse(z.enum(['lines', 'totals']).default('lines'), req.query.view);
    const confirmed = store.ordersOn(date, 'confirmed');
    const body = view === 'totals' ? totalsCsv(date, sheetTotals(confirmed)) : linesCsv(date, confirmed);
    res.type('text/csv; charset=utf-8').attachment(`order-sheet-${date}-${view}.csv`).send(body);
  });

  app.use('/api', (_req, _res, next) => next(new HttpError(404, 'Not found')));

  app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
    if (err instanceof HttpError) {
      res.status(err.status).json({ error: err.message });
    } else if (err instanceof multer.MulterError) {
      res.status(400).json({ error: err.code === 'LIMIT_FILE_SIZE' ? 'The file is larger than 25 MB.' : err.message });
    } else if (err instanceof SyntaxError) {
      res.status(400).json({ error: 'Invalid JSON' });
    } else {
      console.error(err);
      res.status(500).json({ error: 'Something went wrong. Check the app window for details.' });
    }
  });

  return { app, read };
}
