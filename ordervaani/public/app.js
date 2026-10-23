// OrderVaani staff screens: plain JavaScript, no build step. Routes live in the URL hash.

const view = document.getElementById('view');
let status = { ai: false, speech: false, today: '' };
let timer = null; // the current page's polling timer, stopped on navigation

// ---------- helpers ----------

/** Creates an element: h('a', { href: '#/' }, 'text', child). Attributes starting with "on" become listeners. */
function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs ?? {})) {
    if (v === false || v === null || v === undefined) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className = v;
    else if (k === 'value') el.value = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : String(c));
  }
  return el;
}

async function api(path, options = {}) {
  const init = { ...options };
  if (options.json !== undefined) {
    init.method ??= 'POST';
    init.headers = { 'Content-Type': 'application/json' };
    init.body = JSON.stringify(options.json);
  }
  const res = await fetch('/api' + path, init);
  if (res.status === 204) return null;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error || `Request failed (${res.status})`);
  return body;
}

function toast(message, isError = false) {
  const t = document.getElementById('toast');
  t.textContent = message;
  t.className = 'toast show' + (isError ? ' error' : '');
  clearTimeout(toast.t);
  toast.t = setTimeout(() => (t.className = 'toast'), isError ? 6000 : 2500);
}

/** Runs an action and reports its error, so a failed click never fails silently. */
async function attempt(fn) {
  try {
    return await fn();
  } catch (e) {
    toast(e.message, true);
  }
}

const qty = (q) => (q === null || q === undefined ? '?' : String(Math.round(q * 1000) / 1000));
const SOURCE_ICON = { voice: '🎙', photo: '📷', text: '💬' };
const SOURCE_LABEL = { voice: 'Voice note', photo: 'Photo of list', text: 'Text message' };
const STATUS_LABEL = { reading: 'Reading', review: 'To check', sent: 'Sent to shop', confirmed: 'On sheet', cancelled: 'Cancelled' };
const LANG_LABEL = { hi: 'Hindi', mr: 'Marathi', en: 'English' };
const badge = (s) => h('span', { class: 'badge ' + s }, s === 'reading' ? [h('span', { class: 'spinner' }), ' '] : null, STATUS_LABEL[s]);
const params = () => new URLSearchParams(location.hash.split('?')[1] ?? '');
const dateParam = () => params().get('date') || status.today;

function dateInput(value, onChange) {
  return h('input', { type: 'date', value, 'aria-label': 'Date', onchange: (e) => e.target.value && onChange(e.target.value) });
}

// ---------- router ----------

const routes = [
  [/^#?\/?$/, ordersPage, 'orders'],
  [/^#\/order\/(\d+)$/, orderPage, 'orders'],
  [/^#\/sheet$/, sheetPage, 'sheet'],
  [/^#\/catalogue$/, cataloguePage, 'catalogue'],
  [/^#\/shops$/, shopsPage, 'shops'],
];

async function route() {
  clearTimeout(timer);
  const hash = location.hash.split('?')[0];
  const [re, page, nav] = routes.find(([r]) => r.test(hash)) ?? routes[0];
  const match = hash.match(re);
  document.querySelectorAll('[data-nav]').forEach((a) => a.classList.toggle('active', a.dataset.nav === nav));
  view.replaceChildren(h('p', { class: 'muted' }, h('span', { class: 'spinner' }), ' Loading…'));
  try {
    await page(...(match?.slice(1) ?? []));
  } catch (e) {
    view.replaceChildren(h('div', { class: 'alert error' }, e.message));
  }
}

window.addEventListener('hashchange', route);

async function start() {
  status = await api('/status');
  const setup = document.getElementById('setup');
  setup.replaceChildren(
    h('span', { class: 'chip ' + (status.ai ? 'on' : 'off'), title: status.ai ? `AI reading with ${status.model}` : 'Set ANTHROPIC_API_KEY in .env and restart' }, status.ai ? 'AI on' : 'AI off'),
    h('span', { class: 'chip ' + (status.speech ? 'on' : 'off'), title: status.speech ? 'Voice notes are converted to text' : 'Set SARVAM_API_KEY in .env and restart' }, status.speech ? 'Voice on' : 'Voice off'),
  );
  route();
}

// ---------- Orders (inbox) ----------

async function ordersPage() {
  const date = dateParam();
  const [orders, shops] = await Promise.all([api('/orders?date=' + date), api('/shops')]);

  const counts = { review: 0, sent: 0, confirmed: 0 };
  orders.forEach((o) => (counts[o.status] = (counts[o.status] ?? 0) + 1));

  const list = orders.length
    ? h('ul', { class: 'order-list' },
        orders.map((o) => {
          const flagged = o.lines.filter((l) => l.needsCheck).length;
          return h('li', {},
            h('a', { href: '#/order/' + o.id },
              h('span', { class: 'src', title: SOURCE_LABEL[o.source] }, SOURCE_ICON[o.source]),
              h('span', {},
                h('div', { class: 'who' }, o.shopName),
                h('div', { class: 'meta' },
                  `#${o.id} · ${SOURCE_LABEL[o.source]} · ${o.lines.length} line${o.lines.length === 1 ? '' : 's'}`,
                  flagged ? h('span', { class: 'flagcount' }, ` · ${flagged} to check`) : null,
                  o.error && o.status === 'review' ? h('span', { class: 'flagcount' }, ' · needs attention') : null),
              ),
              badge(o.status),
            ));
        }))
    : h('p', { class: 'muted' }, 'No orders for this day yet. Add the first one on the left.');

  view.replaceChildren(
    h('div', { class: 'title-row' },
      h('h1', {}, 'Orders'),
      h('div', { class: 'row' },
        dateInput(date, (d) => (location.hash = '#/?date=' + d)),
        h('a', { class: 'btn', href: '#/sheet?date=' + date }, 'Order sheet →')),
    ),
    h('div', { class: 'grid cols-2' },
      newOrderForm(shops, date),
      h('section', { class: 'card' },
        h('div', { class: 'row spread' },
          h('h2', {}, `${orders.length} order${orders.length === 1 ? '' : 's'}`),
          h('span', { class: 'muted small' }, `${counts.review} to check · ${counts.sent} waiting for shop · ${counts.confirmed} on sheet`)),
        list),
    ),
  );

  if (orders.some((o) => o.status === 'reading')) {
    timer = setTimeout(() => location.hash.startsWith('#/order') || ordersPage().catch(() => {}), 2500);
  }
}

function newOrderForm(shops, date) {
  let mode = 'file';
  let file = null;

  const shopSelect = h('select', { id: 'shop', required: true },
    h('option', { value: '' }, shops.length ? 'Choose a shop…' : 'Add a shop first'),
    shops.map((s) => h('option', { value: s.id }, s.area ? `${s.name} (${s.area})` : s.name)));
  try {
    const last = localStorage.getItem('lastShop');
    if (last && shops.some((s) => String(s.id) === last)) shopSelect.value = last;
  } catch {}

  const quickShop = h('div', { class: 'stack hidden' },
    h('div', {}, h('label', { for: 'qs-name' }, 'Shop name'), h('input', { id: 'qs-name' })),
    h('div', { class: 'row' },
      h('div', { style: 'flex:1' }, h('label', { for: 'qs-phone' }, 'WhatsApp number'), h('input', { id: 'qs-phone', inputmode: 'tel' })),
      h('div', { style: 'flex:1' }, h('label', { for: 'qs-lang' }, 'Language'),
        h('select', { id: 'qs-lang' }, Object.entries(LANG_LABEL).map(([v, l]) => h('option', { value: v }, l))))),
    h('div', { class: 'row' },
      h('button', { type: 'button', class: 'btn sm primary', onclick: () => attempt(async () => {
        const shop = await api('/shops', { json: { name: qs('qs-name'), phone: qs('qs-phone'), language: qs('qs-lang') } });
        shopSelect.append(h('option', { value: shop.id }, shop.name));
        shopSelect.value = shop.id;
        quickShop.classList.add('hidden');
        toast('Shop added');
      }) }, 'Add shop'),
      h('button', { type: 'button', class: 'btn sm ghost', onclick: () => quickShop.classList.add('hidden') }, 'Cancel')),
  );
  const qs = (id) => quickShop.querySelector('#' + id).value;

  const fileInput = h('input', { type: 'file', accept: 'audio/*,image/*,.opus,.ogg,.m4a,.amr,.heic', class: 'hidden', onchange: (e) => pick(e.target.files[0]) });
  const dropLabel = h('div', {}, h('strong', {}, 'Drop a voice note or photo here'), h('div', { class: 'small' }, 'or click to choose · .opus, .ogg, .m4a, .mp3, .jpg, .png'));
  const drop = h('div', {
    class: 'drop', tabindex: 0, role: 'button',
    onclick: () => fileInput.click(),
    onkeydown: (e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), fileInput.click()),
    ondragover: (e) => (e.preventDefault(), drop.classList.add('over')),
    ondragleave: () => drop.classList.remove('over'),
    ondrop: (e) => (e.preventDefault(), drop.classList.remove('over'), pick(e.dataTransfer.files[0])),
  }, dropLabel);
  function pick(f) {
    if (!f) return;
    file = f;
    dropLabel.replaceChildren(h('strong', {}, f.name), h('div', { class: 'small' }, `${(f.size / 1024).toFixed(0)} KB · click to change`));
  }

  const text = h('textarea', { placeholder: 'Paste the order message, e.g.\n5 kilo tur dal, 2 peti biscuit, 10 kg sakhar', rows: 5 });
  const fileBox = h('div', {}, drop, fileInput);
  const textBox = h('div', { class: 'hidden' }, text);
  const tabs = h('div', { class: 'tabs', role: 'tablist' },
    h('button', { type: 'button', class: 'active', onclick: (e) => setMode('file', e.target) }, 'Voice note / photo'),
    h('button', { type: 'button', onclick: (e) => setMode('text', e.target) }, 'Text message'));
  function setMode(m, btn) {
    mode = m;
    tabs.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b === btn));
    fileBox.classList.toggle('hidden', m !== 'file');
    textBox.classList.toggle('hidden', m !== 'text');
  }

  const submit = h('button', { class: 'btn primary', type: 'submit' }, 'Read order');
  const form = h('form', { class: 'card stack', onsubmit: (e) => {
    e.preventDefault();
    attempt(async () => {
      if (!shopSelect.value) throw new Error('Choose the shop this order is from.');
      const data = new FormData();
      data.append('shopId', shopSelect.value);
      data.append('orderDate', date);
      if (mode === 'file') {
        if (!file) throw new Error('Choose the voice note or photo first.');
        data.append('file', file);
      } else {
        if (!text.value.trim()) throw new Error('Paste the order text first.');
        data.append('message', text.value);
      }
      submit.disabled = true;
      submit.replaceChildren(h('span', { class: 'spinner' }), ' Uploading…');
      try {
        const order = await api('/orders', { method: 'POST', body: data });
        try { localStorage.setItem('lastShop', shopSelect.value); } catch {}
        location.hash = '#/order/' + order.id;
      } finally {
        submit.disabled = false;
        submit.replaceChildren('Read order');
      }
    });
  } },
    h('h2', {}, 'New order'),
    h('div', {},
      h('div', { class: 'row spread' }, h('label', { for: 'shop' }, 'Shop'),
        h('button', { type: 'button', class: 'btn sm ghost', onclick: () => quickShop.classList.toggle('hidden') }, '+ New shop')),
      shopSelect),
    quickShop,
    tabs, fileBox, textBox,
    h('div', { class: 'row spread' }, h('span', { class: 'muted small' }, `For ${date}`), submit),
  );
  return form;
}

// ---------- Order (review and confirm) ----------

async function orderPage(id) {
  const [order, items, shops] = await Promise.all([api('/orders/' + id), api('/items'), api('/shops')]);
  const active = items.filter((i) => i.active);
  const byName = new Map(active.map((i) => [i.name.toLowerCase(), i]));
  let lines = order.lines.map((l) => ({ ...l }));
  let current = order;
  let saving = null;
  // Not the page timer: a pending save must still run if staff move to another page.
  let saveTimer = null;

  const datalist = h('datalist', { id: 'catalogue-names' }, active.map((i) => h('option', { value: i.name })));

  // Source panel: what the shopkeeper sent, and the text the AI reads from.
  const transcript = h('textarea', { rows: 6, value: order.transcript, 'aria-label': 'Text the AI reads' });
  const transcriptLabel = {
    voice: 'What the shopkeeper said (correct it if needed)',
    photo: 'What the AI read from the photo',
    text: 'Order text (correct it if needed)',
  }[order.source];
  const readAgain = h('button', { class: 'btn', onclick: () => attempt(async () => {
    if (lines.length && !confirm('Reading again replaces the current lines. Continue?')) return;
    await api(`/orders/${id}/read`, { json: { transcript: transcript.value } });
    orderPage(id);
  }) }, order.source === 'photo' ? 'Read photo again' : 'Read again');

  const source = h('section', { class: 'card stack source' },
    h('h2', {}, SOURCE_ICON[order.source], ' ', SOURCE_LABEL[order.source]),
    order.source === 'voice' ? h('audio', { controls: true, preload: 'metadata', src: `/api/orders/${id}/file` }) : null,
    order.source === 'photo' ? h('a', { href: `/api/orders/${id}/file`, target: '_blank' }, h('img', { src: `/api/orders/${id}/file`, alt: 'Photo of the order list' })) : null,
    order.source === 'text' && order.message !== order.transcript ? h('pre', { class: 'message' }, order.message) : null,
    h('div', {}, h('label', {}, transcriptLabel), transcript),
    h('div', { class: 'row' }, readAgain, h('span', { class: 'muted small' }, 'Replaces the lines below.')),
  );

  // Lines table, edited in place and saved automatically.
  const tbody = h('tbody');
  const saveState = h('span', { class: 'muted small' });
  const sidePanel = h('div', { class: 'stack' });

  function scheduleSave() {
    saveState.textContent = 'Saving…';
    clearTimeout(saveTimer);
    saveTimer = setTimeout(save, 600);
  }
  async function save() {
    clearTimeout(saveTimer);
    saving = api('/orders/' + id, { method: 'PUT', json: { lines } })
      .then((o) => {
        current = o;
        saveState.textContent = 'Saved';
        renderSide();
      })
      .catch((e) => {
        saveState.textContent = 'Not saved';
        toast(e.message, true);
      })
      .finally(() => (saving = null));
    return saving;
  }
  async function flush() {
    if (saveState.textContent === 'Saving…') await save();
    else if (saving) await saving;
  }

  function lineRow(line, index) {
    const item = h('input', { value: line.itemName, list: 'catalogue-names', 'aria-label': 'Item', placeholder: 'Item' });
    const q = h('input', { class: 'qty', type: 'number', min: 0, step: 'any', value: line.quantity ?? '', 'aria-label': 'Quantity' });
    const unit = h('input', { class: 'unit', value: line.unit, 'aria-label': 'Unit', placeholder: 'kg' });
    const tag = h('span');
    const showMatch = () => tag.replaceChildren(line.itemId
      ? h('span', { class: 'matched' }, '✓ in catalogue')
      : h('span', { class: 'unmatched' }, 'not in catalogue'));
    showMatch();

    const edited = () => {
      if (line.needsCheck) {
        line.needsCheck = false;
        line.note = '';
        row.classList.remove('flagged');
        noteEl.remove();
      }
      scheduleSave();
    };
    item.addEventListener('input', () => {
      line.itemName = item.value;
      const hit = byName.get(item.value.trim().toLowerCase());
      line.itemId = hit ? hit.id : null;
      if (hit && (!unit.value || unit.value !== hit.unit)) {
        unit.value = hit.unit;
        line.unit = hit.unit;
      }
      showMatch();
      edited();
    });
    q.addEventListener('input', () => {
      line.quantity = q.value === '' ? null : Number(q.value);
      edited();
    });
    unit.addEventListener('input', () => {
      line.unit = unit.value;
      edited();
    });

    const noteEl = h('div', { class: 'note' },
      '⚠ ', line.note || 'Check this line',
      h('button', { type: 'button', class: 'btn sm', onclick: () => {
        line.needsCheck = false;
        line.note = '';
        row.classList.remove('flagged');
        noteEl.remove();
        scheduleSave();
      } }, 'Looks right'));

    const row = h('tr', { class: line.needsCheck ? 'flagged' : '' },
      h('td', {}, item, h('div', { class: 'row' }, tag, line.heard ? h('span', { class: 'heard' }, `heard: “${line.heard}”`) : null), line.needsCheck ? noteEl : null),
      h('td', {}, q),
      h('td', {}, unit),
      h('td', {}, h('button', { type: 'button', class: 'btn sm ghost', title: 'Remove line', 'aria-label': `Remove line ${index + 1}`, onclick: () => {
        lines.splice(lines.indexOf(line), 1);
        renderLines();
        scheduleSave();
      } }, '✕')),
    );
    return row;
  }

  function renderLines() {
    tbody.replaceChildren(...lines.map(lineRow));
    if (!lines.length) {
      tbody.append(h('tr', {}, h('td', { colspan: 4, class: 'muted' }, current.status === 'reading' ? 'Reading the order…' : 'No lines yet.')));
    }
  }

  const addLine = h('button', { class: 'btn sm', onclick: () => {
    lines.push({ itemId: null, itemName: '', quantity: null, unit: '', heard: '', needsCheck: false, note: '' });
    renderLines();
    tbody.lastElementChild.querySelector('input').focus();
  } }, '+ Add line');

  const setStatus = (s) => attempt(async () => {
    await flush();
    current = await api(`/orders/${id}/status`, { json: { status: s } });
    toast(s === 'confirmed' ? 'Added to the order sheet' : `Marked as ${STATUS_LABEL[s].toLowerCase()}`);
    renderHeader();
    renderSide();
  });

  function renderSide() {
    const o = current;
    const ready = o.problems.length === 0;
    const onSheet = o.status === 'confirmed';
    sidePanel.replaceChildren(
      h('section', { class: 'card stack' },
        h('h2', {}, 'Confirm with the shop'),
        h('div', { class: 'steps' },
          ['Check lines', 'Send to shop', 'Shop says yes'].map((label, i) => {
            const step = { review: 0, sent: 1, confirmed: 3, cancelled: -1, reading: 0 }[o.status];
            return h('span', { class: i < step ? 'done' : i === step ? 'now' : '' }, `${i < step ? '✓' : i + 1} ${label}`, i < 2 ? '  →' : '');
          })),
        ready ? null : h('ul', { class: 'problems' }, o.problems.map((p) => h('li', {}, p))),
        h('pre', { class: 'confirm-text' }, o.confirmation.text),
        h('div', { class: 'row' },
          o.confirmation.whatsapp
            ? h('a', { class: 'btn primary' + (ready ? '' : ' disabled'), href: ready ? o.confirmation.whatsapp : null, target: '_blank', rel: 'noopener',
                'aria-disabled': ready ? null : 'true',
                onclick: (e) => { if (!ready) return e.preventDefault(); if (o.status === 'review') setStatus('sent'); } }, 'Send on WhatsApp')
            : h('span', { class: 'muted small' }, `Add a WhatsApp number for ${o.shop.name} on the Shops page to send from here.`),
          h('button', { class: 'btn', disabled: !ready, onclick: () => attempt(async () => {
            await navigator.clipboard.writeText(o.confirmation.text);
            toast('Copied');
          }) }, 'Copy'),
        ),
        h('div', { class: 'row' },
          o.status === 'review' ? h('button', { class: 'btn', disabled: !ready, onclick: () => setStatus('sent') }, 'Mark as sent') : null,
          onSheet
            ? h('a', { class: 'btn', href: '#/sheet?date=' + o.orderDate }, 'View order sheet →')
            : h('button', { class: 'btn success', disabled: !ready || o.status === 'cancelled', onclick: () => setStatus('confirmed') }, 'Shop confirmed: add to sheet'),
        ),
        h('p', { class: 'muted small' }, `Message in ${LANG_LABEL[o.shop.language]}. Change the language on the Shops page.`),
      ),
      h('section', { class: 'card stack' },
        h('h2', {}, 'Order details'),
        h('div', {}, h('label', { for: 'o-shop' }, 'Shop'),
          h('select', { id: 'o-shop', onchange: (e) => attempt(async () => { await flush(); current = await api('/orders/' + id, { method: 'PUT', json: { shopId: Number(e.target.value) } }); renderHeader(); renderSide(); }) },
            shops.map((s) => h('option', { value: s.id, selected: s.id === o.shopId }, s.name)))),
        h('div', {}, h('label', { for: 'o-date' }, 'Order sheet date'),
          h('input', { id: 'o-date', type: 'date', value: o.orderDate, onchange: (e) => attempt(async () => { current = await api('/orders/' + id, { method: 'PUT', json: { orderDate: e.target.value } }); renderHeader(); renderSide(); }) })),
        h('div', { class: 'row' },
          o.status !== 'cancelled'
            ? h('button', { class: 'btn danger', onclick: () => setStatus('cancelled') }, 'Cancel order')
            : h('button', { class: 'btn', onclick: () => setStatus('review') }, 'Restore order'),
          h('button', { class: 'btn ghost danger', onclick: () => attempt(async () => {
            if (!confirm('Delete this order and its file for good?')) return;
            await api('/orders/' + id, { method: 'DELETE' });
            location.hash = '#/?date=' + o.orderDate;
          }) }, 'Delete')),
      ),
    );
  }

  const header = h('div', { class: 'title-row' });
  function renderHeader() {
    header.replaceChildren(
      h('div', {},
        h('a', { href: '#/?date=' + current.orderDate, class: 'small' }, '← Orders for ' + current.orderDate),
        h('h1', {}, current.shop.name, ' ', h('span', { class: 'muted', style: 'font-weight:500' }, `#${id}`))),
      badge(current.status),
    );
  }

  const errorBox = order.error ? h('div', { class: 'alert warn' }, order.error) : null;
  const editWarning = ['sent', 'confirmed'].includes(order.status)
    ? h('div', { class: 'alert info' }, 'Changing the lines takes this order back to “to check”, so it is confirmed with the shop again.')
    : null;

  renderHeader();
  renderLines();
  renderSide();
  view.replaceChildren(
    header,
    h('div', { class: 'grid cols-order' },
      source,
      h('div', { class: 'stack' },
        errorBox,
        h('section', { class: 'card stack' },
          h('div', { class: 'row spread' }, h('h2', { style: 'margin:0' }, 'Order lines'), saveState),
          editWarning,
          h('table', { class: 'lines' },
            h('thead', {}, h('tr', {}, h('th', {}, 'Item'), h('th', { class: 'num c-qty' }, 'Qty'), h('th', { class: 'c-unit' }, 'Unit'), h('th', { class: 'c-x' }))),
            tbody),
          addLine, datalist),
        sidePanel),
    ),
  );

  if (order.status === 'reading') {
    const poll = async () => {
      const o = await api('/orders/' + id).catch(() => null);
      if (!location.hash.startsWith('#/order/' + id)) return;
      if (o && o.status !== 'reading') orderPage(id);
      else timer = setTimeout(poll, 2000);
    };
    timer = setTimeout(poll, 2000);
  }
}

// ---------- Order sheet ----------

async function sheetPage() {
  const date = dateParam();
  const sheet = await api('/sheet?date=' + date);
  let tab = params().get('view') === 'shops' ? 'shops' : 'totals';

  const totals = h('table', { class: 'totals' },
    h('thead', {}, h('tr', {}, h('th', {}, 'Item'), h('th', { class: 'num' }, 'Total'), h('th', {}, 'Unit'), h('th', { class: 'num' }, 'Shops'))),
    h('tbody', {}, sheet.totals.map((t) => h('tr', {},
      h('td', {}, t.itemName, t.missingQuantity ? h('div', { class: 'small flagcount' }, `+ ${t.missingQuantity} line(s) without quantity`) : null),
      h('td', { class: 'num' }, qty(t.quantity)),
      h('td', {}, t.unit),
      h('td', { class: 'num' }, t.shops)))));

  const byShop = h('div', {}, sheet.orders.map((o) => h('div', { class: 'shop-block' },
    h('h3', {}, h('a', { href: '#/order/' + o.id }, o.shopName), h('span', { class: 'muted small' }, ` · order #${o.id}`)),
    h('table', {}, h('tbody', {}, o.lines.map((l) => h('tr', {},
      h('td', {}, l.itemName), h('td', { class: 'num', style: 'width:110px' }, qty(l.quantity)), h('td', { style: 'width:110px' }, l.unit))))))));

  const body = h('div');
  const tabs = h('div', { class: 'tabs no-print', style: 'max-width:360px' });
  function show() {
    tabs.replaceChildren(
      h('button', { class: tab === 'totals' ? 'active' : '', onclick: () => ((tab = 'totals'), show()) }, 'Pick list (totals)'),
      h('button', { class: tab === 'shops' ? 'active' : '', onclick: () => ((tab = 'shops'), show()) }, 'By shop'));
    body.replaceChildren(tab === 'totals' ? totals : byShop);
  }
  show();

  view.replaceChildren(
    h('div', { class: 'title-row' },
      h('h1', {}, 'Order sheet · ', date),
      h('div', { class: 'row no-print' },
        dateInput(date, (d) => (location.hash = '#/sheet?date=' + d)),
        h('a', { class: 'btn', href: `/api/sheet.csv?date=${date}&view=totals` }, 'Totals CSV'),
        h('a', { class: 'btn', href: `/api/sheet.csv?date=${date}&view=lines` }, 'All lines CSV'),
        h('button', { class: 'btn', onclick: () => print() }, 'Print'))),
    sheet.waiting
      ? h('div', { class: 'alert warn no-print', style: 'margin-bottom:16px' }, `${sheet.waiting} order(s) for this day are not confirmed yet and are not on the sheet. `, h('a', { href: '#/?date=' + date }, 'See orders'))
      : null,
    h('section', { class: 'card stack' },
      h('div', { class: 'row spread' }, tabs, h('span', { class: 'muted small' }, `${sheet.orders.length} confirmed order(s) · ${sheet.totals.length} item line(s)`)),
      sheet.orders.length ? body : h('p', { class: 'muted' }, 'No confirmed orders for this day yet.')),
  );
}

// ---------- Catalogue ----------

async function cataloguePage() {
  const items = await api('/items');

  const row = (it) => {
    const name = h('input', { value: it.name, 'aria-label': 'Name' });
    const unit = h('input', { value: it.unit, 'aria-label': 'Unit', style: 'width:90px' });
    const aliases = h('input', { value: it.aliases, 'aria-label': 'Other names' });
    const active = h('input', { type: 'checkbox', checked: it.active, 'aria-label': 'In use', style: 'width:auto' });
    const save = () => attempt(async () => {
      await api('/items/' + it.id, { method: 'PUT', json: { name: name.value, unit: unit.value, aliases: aliases.value, active: active.checked } });
      toast('Saved');
    });
    [name, unit, aliases].forEach((i) => i.addEventListener('change', save));
    active.addEventListener('change', save);
    return h('tr', {},
      h('td', {}, name), h('td', {}, unit), h('td', {}, aliases), h('td', { style: 'text-align:center' }, active),
      h('td', {}, h('button', { class: 'btn sm ghost danger', 'aria-label': 'Delete ' + it.name, onclick: () => attempt(async () => {
        if (!confirm(`Delete ${it.name}? Untick "In use" instead to keep it on past orders.`)) return;
        await api('/items/' + it.id, { method: 'DELETE' });
        cataloguePage();
      }) }, '✕')));
  };

  const add = h('form', { class: 'row', onsubmit: (e) => {
    e.preventDefault();
    const f = e.target.elements;
    attempt(async () => {
      await api('/items', { json: { name: f.name.value, unit: f.unit.value, aliases: f.aliases.value } });
      toast('Item added');
      cataloguePage();
    });
  } },
    h('input', { name: 'name', placeholder: 'Item name, e.g. Toor dal', style: 'flex:2;min-width:180px' }),
    h('input', { name: 'unit', placeholder: 'Unit, e.g. kg', style: 'flex:1;min-width:90px' }),
    h('input', { name: 'aliases', placeholder: 'Other names: तूर डाळ, arhar…', style: 'flex:3;min-width:200px' }),
    h('button', { class: 'btn primary' }, 'Add'));

  const csv = h('textarea', { rows: 5, placeholder: 'name,unit,aliases\nToor dal,kg,tur dal; अरहर दाल\nSugar,kg,cheeni; साखर' });
  const importBox = h('details', { class: 'card' },
    h('summary', { style: 'cursor:pointer;font-weight:650;color:var(--navy)' }, 'Import from CSV'),
    h('div', { class: 'stack', style: 'margin-top:12px' },
      h('p', { class: 'muted small', style: 'margin:0' }, 'First row: name,unit,aliases. Separate aliases with semicolons. Items with the same name are updated, others are added.'),
      csv,
      h('button', { class: 'btn', onclick: () => attempt(async () => {
        const r = await api('/items/import', { json: { csv: csv.value } });
        toast(`${r.added} added, ${r.updated} updated`);
        cataloguePage();
      }) }, 'Import')));

  view.replaceChildren(
    h('div', { class: 'title-row' }, h('h1', {}, 'Catalogue'), h('span', { class: 'muted' }, `${items.length} items`)),
    h('p', { class: 'muted' }, 'The AI matches every order against this list. Add the names shopkeepers really use, in Hindi, Marathi or Latin letters, so it matches them reliably.'),
    h('div', { class: 'stack' },
      h('section', { class: 'card stack' }, h('h2', {}, 'Add an item'), add),
      importBox,
      h('section', { class: 'card', style: 'overflow-x:auto' },
        h('table', {},
          h('thead', {}, h('tr', {}, h('th', {}, 'Name'), h('th', {}, 'Unit'), h('th', {}, 'Other names (comma separated)'), h('th', {}, 'In use'), h('th'))),
          h('tbody', {}, items.map(row))))));
}

// ---------- Shops ----------

async function shopsPage() {
  const shops = await api('/shops');
  const langSelect = (value) => h('select', { 'aria-label': 'Language' }, Object.entries(LANG_LABEL).map(([v, l]) => h('option', { value: v, selected: v === value }, l)));

  const row = (s) => {
    const name = h('input', { value: s.name, 'aria-label': 'Name' });
    const phone = h('input', { value: s.phone, 'aria-label': 'WhatsApp number', inputmode: 'tel' });
    const area = h('input', { value: s.area, 'aria-label': 'Area' });
    const lang = langSelect(s.language);
    const save = () => attempt(async () => {
      await api('/shops/' + s.id, { method: 'PUT', json: { name: name.value, phone: phone.value, area: area.value, language: lang.value } });
      toast('Saved');
    });
    [name, phone, area, lang].forEach((i) => i.addEventListener('change', save));
    return h('tr', {}, h('td', {}, name), h('td', {}, phone), h('td', {}, area), h('td', {}, lang),
      h('td', {}, h('button', { class: 'btn sm ghost danger', 'aria-label': 'Delete ' + s.name, onclick: () => attempt(async () => {
        if (!confirm(`Delete ${s.name}?`)) return;
        await api('/shops/' + s.id, { method: 'DELETE' });
        shopsPage();
      }) }, '✕')));
  };

  const add = h('form', { class: 'row', onsubmit: (e) => {
    e.preventDefault();
    const f = e.target.elements;
    attempt(async () => {
      await api('/shops', { json: { name: f.name.value, phone: f.phone.value, area: f.area.value, language: f.language.value } });
      toast('Shop added');
      shopsPage();
    });
  } },
    h('input', { name: 'name', placeholder: 'Shop name', style: 'flex:2;min-width:180px' }),
    h('input', { name: 'phone', placeholder: 'WhatsApp number', inputmode: 'tel', style: 'flex:1;min-width:140px' }),
    h('input', { name: 'area', placeholder: 'Area / route', style: 'flex:1;min-width:120px' }),
    Object.assign(langSelect('hi'), { name: 'language', style: 'flex:0 0 120px' }),
    h('button', { class: 'btn primary' }, 'Add'));

  view.replaceChildren(
    h('div', { class: 'title-row' }, h('h1', {}, 'Shops'), h('span', { class: 'muted' }, `${shops.length} shops`)),
    h('p', { class: 'muted' }, 'The language sets how the order is read back to the shopkeeper. The number is used for the “Send on WhatsApp” button.'),
    h('div', { class: 'stack' },
      h('section', { class: 'card stack' }, h('h2', {}, 'Add a shop'), add),
      h('section', { class: 'card', style: 'overflow-x:auto' },
        shops.length
          ? h('table', {},
              h('thead', {}, h('tr', {}, h('th', {}, 'Name'), h('th', {}, 'WhatsApp number'), h('th', {}, 'Area'), h('th', {}, 'Language'), h('th'))),
              h('tbody', {}, shops.map(row)))
          : h('p', { class: 'muted' }, 'No shops yet.'))));
}

start().catch((e) => view.replaceChildren(h('div', { class: 'alert error' }, 'Could not reach the OrderVaani server: ' + e.message)));
