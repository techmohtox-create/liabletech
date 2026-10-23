# OrderVaani

Order taking for wholesalers and distributors. Shopkeepers keep ordering the way they do today:
a WhatsApp voice note in Hindi or Marathi, a photo of a handwritten list, or a text message.
Staff drop the note into OrderVaani, and the AI turns it into clean order lines (item, quantity,
unit) matched to your catalogue. Staff check the flagged lines, send a confirmation back to the
shop in its own language, and once the shop says yes the order joins the day's order sheet.

This is a local app: it runs on one office computer and keeps its data there. It is not deployed
anywhere.

## Run it

Needs Node.js 22.18 or newer. ffmpeg is optional but recommended (see below).

```bash
cd ordervaani
npm install
cp .env.example .env      # then add your keys
npm start
```

Open http://localhost:3100.

| Key in `.env`       | What it switches on                                   | Without it                                  |
|---------------------|-------------------------------------------------------|---------------------------------------------|
| `ANTHROPIC_API_KEY` | Claude reads text, voice transcripts and photos       | Staff type the order lines by hand          |
| `SARVAM_API_KEY`    | Hindi and Marathi voice notes become text (Sarvam AI) | Staff play the note and type what they hear |

The header shows **AI on/off** and **Voice on/off** so staff can see what is set up.

**ffmpeg** lets OrderVaani handle WhatsApp `.opus` voice notes of any length and shrink large or
HEIC phone photos. Voice notes are converted and sent to speech-to-text in 25-second pieces,
because Sarvam's live API accepts at most 30 seconds per request. Without ffmpeg, short voice
notes in common formats and JPEG/PNG photos under 3.7 MB still work. Install it with
`winget install ffmpeg` (Windows), `brew install ffmpeg` (Mac) or `sudo apt install ffmpeg` (Linux).

## How staff use it

1. **Shops**: add each shop with its WhatsApp number and language (Hindi, Marathi or English).
2. **Catalogue**: replace the sample grocery items with your own. Put the names shopkeepers
   really say under "other names" (तूर डाळ, arhar, tur), because that is what the AI matches
   against. You can paste a CSV (`name,unit,aliases`).
3. **Orders**: choose the shop, drop in the voice note or photo (or paste the text) and press
   **Read order**. Reading takes a few seconds.
4. **Check**: lines the AI is unsure about are highlighted with the reason, for example an item
   that is not in the catalogue, or "bori" when you sell 10 kg bags. Fix them or press
   **Looks right**. Every line shows what was heard, so staff can compare. If the transcript is
   wrong, correct it and press **Read again**.
5. **Confirm**: **Send on WhatsApp** opens WhatsApp with the order typed out in the shop's
   language. When the shop replies yes, press **Shop confirmed: add to sheet**. An order cannot be
   sent or confirmed while any line is flagged or has no quantity.
6. **Order sheet**: the pick list totals each item across all shops for the day, and the
   "By shop" view lists each order. Export either one as CSV (opens in Excel, Devanagari
   included) or print it.

Editing an order that was already sent or confirmed moves it back to "to check", so the shop
confirms the changed order again.

## How it works

- `src/extract.ts`: one Claude call per order using structured outputs, so the reply always
  parses into order lines. The prompt covers Hindi and Marathi quantities (dhai = 2.5,
  sava = 1.25, paav = 0.25…) and units (peti, bori, dabba), and tells the model to flag a line
  rather than guess. The catalogue is sent with prompt caching, and server-side refusal fallback
  is on (`fallbacks: "default"`).
- `src/transcribe.ts`: Sarvam AI speech-to-text, with the language left on auto-detect.
- `src/db.ts`: SQLite (built into Node), stored in `data/ordervaani.db`, with uploads in
  `data/uploads`. Back up the `data` folder to keep your orders.
- `public/`: the staff screens, in plain HTML, CSS and JavaScript with no build step.

Each order costs one AI call, plus speech-to-text for voice notes. **Read again** makes another.

## Develop

```bash
npm run dev        # restarts on changes
npm test           # unit and API tests; the AI and speech-to-text are replaced by fakes
npm run typecheck
```
