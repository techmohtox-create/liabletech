# Export Doc Checker

For small exporters whose payment is held, or charged extra, when the invoice, packing list,
bill of lading and other documents do not match the letter of credit exactly. The team forwards
the LC and each document as it arrives, on WhatsApp or by upload. Every document is read, checked
against the credit and against each other the way a bank's document checker would (UCP 600 and
ISBP), and the discrepancies are listed with who has to fix what, before anything goes to the bank.

**Price:** ₹999 per month including 10 shipments (₹99 each after that), or ₹199 per shipment
checked; the owner picks under Settings. Re-checks after corrections are free.
**Port:** 8083. **Demo:** `owner@export.demo` / `demo@123`.

## How it works

1. **The credit.** A SWIFT MT700 letter of credit (PDF or text) is parsed field by field without
   the AI: number, amount and tolerance, expiry date and place, latest shipment date, presentation
   period, ports, goods (45A), documents required (46A), additional conditions (47A), parties.
   Scanned credits are read by Claude.
2. **The documents.** Commercial invoice, packing list, bill of lading or air waybill, insurance
   certificate and certificate of origin are read by Claude (or typed in on the review screen).
   Documents quoting the LC number join its shipment; others join the latest shipment still
   missing that document.
3. **The check.** Over a hundred rules, each citing its UCP 600 article, ISBP paragraph or LC field: amounts
   and quantities within the credit's tolerance, goods description, parties and their spelling,
   shipment and presentation dates, on-board notation, clean B/L, ports, freight marking, full set
   of originals, insurance cover (110 % from the shipment date), origin, weights, packages, marks and
   container numbers agreeing across documents, and soft clauses in the credit itself.
4. **Fixing.** Each finding says who fixes it: your own documents (invoice, packing list), the
   forwarder or shipping line (bill of lading), the insurer, or the buyer (an amendment to the
   credit). One tap sends the
   forwarder the list of corrections on WhatsApp and email, and the buyer an amendment request by
   email. A finding can be accepted with a note when the buyer agrees to waive it.
5. **Ready for the bank.** When nothing blocking is left the shipment is *ready*. The report PDF
   lists the findings with their rules and the documents the credit asks for, for the file. Mark
   the shipment presented, then paid.

## WhatsApp

| Who | Sends | Gets back |
|---|---|---|
| Export team members (under Team) | The LC, then each document as a PDF or photo | What was read, the discrepancies found so far, and which documents are still missing |
| Export team | `status` or `list` | The shipments in progress and what each is waiting for |
| Forwarder | (nothing needed) | The corrections for the bill of lading |

## WhatsApp templates

Only with `APP_WHATSAPP_USE_TEMPLATES=true`:

| Template | Parameters | Suggested body |
|---|---|---|
| `export_doc_corrections` | 1 forwarder name (or "team"), 2 shipment reference, 3 number of corrections, 4 your business | Dear {{1}}, please make {{3}} corrections to the documents for shipment {{2}} before they go to the bank. The list follows. {{4}} |

## Pages

Dashboard (discrepancies to fix, shipments ready, next deadline), Shipments (each with its
documents, findings, corrections and report), Team (who can send documents on WhatsApp).
