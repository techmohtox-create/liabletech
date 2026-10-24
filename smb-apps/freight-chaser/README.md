# Freight Chaser

For small truck owners who wait 30 to 90 days for freight money, often because the signed
proof-of-delivery copy was lost or arrived late. The driver photographs the signed lorry receipt
on WhatsApp at the unloading point. The app reads the LR number, delivery date, stamp and
signature, matches it to the trip, puts the invoice together with the POD attached, sends it to
the transporter or broker, and follows up until the money is in.

**Price:** ₹299 per month per fleet. **Port:** 8081. **Demo:** `owner@freight.demo` / `demo@123`.

## How it works

1. **Trips.** The owner (or office) enters trips: party (transporter, broker or company), LR
   number, route, truck, freight amount. Trucks and drivers are under **Fleet**.
2. **POD on WhatsApp.** The driver sends the photo of the signed receipt (or forwards a PDF scan).
   Claude reads the consignment (LR/GR/bilty) number, LR and delivery dates, consignor and
   consignee, cities, truck number, packages, any freight amount written on it, the receiver's
   remarks, and whether a stamp and signature are visible.
   - A clear photo with a stamp or signature that matches exactly one trip by LR number marks the
     trip delivered straight away, and the driver is told.
   - Anything else waits under **PODs** for the owner to check against the photo.
3. **Invoice.** Delivered trips for a party become an invoice (GST: none, reverse charge, or
   charged at a rate as CGST+SGST or IGST). The PDF carries every POD photo as proof.
4. **Sending and chasing.** The invoice goes to the party on WhatsApp and/or email with the PDF.
   Reminders follow on a schedule (by default 2 days before the due date, on it, then 7, 15, 30,
   45, 60 and 90 days after, then every 15 days), each with the invoice and PODs again, in a tone
   that firms up as the invoice gets older. Record payments (part payments too), a date the party
   promised to pay (reminders wait for it), or pause chasing on a disputed invoice.

## WhatsApp

| Who | Sends | Gets back |
|---|---|---|
| Driver (registered under Fleet) | Photo or PDF of the signed POD | What was read, and whether the trip is now marked delivered or waits for the office |
| Driver | `status` | What the office has received from them |
| Party (transporter, broker) | (nothing needed) | The invoice and the reminders |

Try it on the **WhatsApp simulator**: pick a driver and send the sample POD photo.

## WhatsApp templates

Needed only with `APP_WHATSAPP_USE_TEMPLATES=true` (see the main README). Create them in Meta
Business Manager, category *Utility*, with a **document header** (the invoice PDF is attached
there) and these body parameters:

| Template | Parameters | Suggested body |
|---|---|---|
| `freight_invoice` | 1 party, 2 invoice number, 3 amount, 4 due date, 5 your business | Dear {{1}}, please find our invoice {{2}} for {{3}}, due by {{4}}, with the signed proof of delivery attached. Thank you, {{5}} |
| `freight_payment_reminder` | 1 party, 2 invoice number, 3 balance, 4 days past due, 5 your business | Dear {{1}}, a reminder that invoice {{2}} for {{3}} is {{4}} days past due. The invoice and signed proof of delivery are attached. Thank you, {{5}} |

## Pages

Dashboard (money outstanding by age, reminders due today), PODs (waiting for review), Trips,
Invoices (send, record payment or promise, pause), Parties, Fleet (trucks and drivers), Chasing
(the schedule, channels, GST and invoice terms).
