# Part Finder

For auto spare parts shops, where the shop that answers a mechanic first gets the sale. Mechanics
send a photo of the old part or its box, a voice note, or a message ("Swift ka front brake pad
chahiye"). The part and vehicle are identified and matched to the shop's stock and what each part
fits; the counter answers with price in one tap, or on Pro the app answers sure matches by itself
in seconds. The mechanic books by replying with the option's number.

**Price:** Counter ₹299 per month; Pro ₹499 per month (automatic quotes and waiting alerts).
**Port:** 8085. **Demo:** `owner@parts.demo` / `demo@123` (on Pro).

## How it works

1. **Stock.** Import the stock list as CSV (most billing software exports one) or add parts one by
   one: part number, name, type, brand, price to mechanics, MRP, stock, rack, the vehicles it fits
   (`Swift 2018-2024; Dzire 2017-2024; Bolero (diesel)`) and the numbers it replaces. Columns are
   found by their heading (`Part No`, `Item Name`, `Qty`, `Rate` work); re-importing updates parts by
   number and leaves alone the columns the file does not have.
2. **Enquiry.** A mechanic writes to the shop's WhatsApp number:
   - A **typed** request in English or Hindi is understood without the AI: about 30 kinds of part
     with the words mechanics use (shocker, self, dynamo, liner), 40 common cars and two-wheelers,
     the year, front or rear, left or right, and how many. Unclear words go to Claude when a key is
     set.
   - A **photo** is read by Claude: part type, vehicle, any number on the part or its box. Without a
     key, the caption is used and the photo waits for the counter.
   - A **voice note** is transcribed when a speech-to-text service is set up (see below), then
     understood like a message; otherwise it is played at the counter.
3. **Match.** A number seen on the part or box finds the part directly. Otherwise the type must
   match and the part must fit the vehicle; the right year and side add to the score, the wrong
   side rules a part out. Parts that can stand in (a clutch kit for a plate) are offered too, but
   never quoted automatically.
4. **Answer.** The mechanic hears back at once: "Got it: Swift front brake pads. Checking stock",
   or the one question that settles it ("Front or rear?"); their answer is added to the same
   enquiry. On the **Counter** page the enquiry appears with the photo, the guess and the matching
   parts with stock and rack; tick one or two and send. On **Pro**, a sure match in stock is quoted
   straight away, and the owner gets a WhatsApp alert when an enquiry waits longer than allowed.
5. **Booking.** The mechanic replies `1` or `2`; the part is set aside from stock until handed over
   (record cash, UPI or credit). `CANCEL` puts it back. A quote not booked within a day closes.
6. **Reports.** Typical answer time and share answered within 2 minutes, enquiries and bookings by
   day, conversion, what sold, and **missed demand**: parts asked for that were not in stock,
   grouped by part and vehicle, so the shop knows what to keep.

## WhatsApp

| Who | Sends | Gets back |
|---|---|---|
| Mechanic | Photo, voice note or message | "Got it" with the guess (or a question), then the quote with numbered options |
| Mechanic | `1`, `2`, `option 2`, `2 wala`, or `yes` to a single option | The booking, ready for pickup in N minutes |
| Mechanic | `CANCEL` | The last booking is cancelled |
| Mechanic | `hi`, `namaste` | How to ask |
| Owner (Pro) | | "Raju has waited 4 min for Bolero clutch plate" |

On the shared demo number a mechanic is recognised by number, so add them under Mechanics first;
on the shop's own WhatsApp number (Settings) anyone who writes is added.

## Voice notes

Set `STT_URL` to a speech-to-text endpoint that takes the widely copied
`POST /v1/audio/transcriptions` form (a self-hosted Whisper server, for one), plus `STT_API_KEY`,
`STT_MODEL` (default `whisper-1`) and `STT_LANGUAGE` (e.g. `hi`) as it needs. Without it, voice
notes are played at the counter.

## WhatsApp templates

Replies to mechanics are within WhatsApp's 24-hour window and need no template. Only the owner's
alert does, with `APP_WHATSAPP_USE_TEMPLATES=true`:

| Template | Parameters | Suggested body |
|---|---|---|
| `parts_waiting_nudge` | 1 mechanic, 2 minutes waited, 3 the part | {{1}} has waited {{2}} min for {{3}}. Answer before another shop does. |

## Pages

Dashboard (waiting now, typical answer time today, booked today, missed this week, running low),
Counter (live: refreshes every few seconds, pauses while you type), Bookings, Stock (with CSV
import and export), Mechanics, Reports (7, 30 or 90 days), Setup (answers, automatic quotes, alert
time, what mechanics receive).
