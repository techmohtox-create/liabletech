# Material Tracker

For small builders who lose money to short deliveries and over-billing of cement, steel, sand and
aggregate. The site supervisor photographs each delivery slip (challan) at the gate. The app reads
the quantities, asks the supervisor on WhatsApp to confirm what actually arrived, and checks every
supplier bill against the deliveries, the agreed rates and its own arithmetic. Every gap is shown
in rupees, and the dispute goes to the supplier with one tap.

**Price:** ₹999 per month per active site. **Port:** 8082. **Demo:** `owner@site.demo` / `demo@123`.

## How it works

1. **Sites, suppliers, materials.** Each site has its supervisors (their WhatsApp numbers). Each
   supplier has agreed rates per material. Materials know their unit (bags, kg, MT, cft, brass,
   m³, nos, litres, sq ft, running m) and conversions: a bag's weight, a TMT bar's diameter (12 m
   bars), 100 cft to a brass.
2. **Slip at the gate.** The supervisor sends the challan photo. Claude reads the supplier, challan
   number and date, vehicle and each line's material and quantity. The supervisor is asked to
   confirm the count: `OK` if everything arrived, or the counted quantity per line, e.g. `1=148`
   (line 1: 148 bags) or `2=4.8`. A count below the slip is a short delivery, in rupees at the
   agreed rate.
3. **Bills.** Supplier bills are photographed or uploaded the same way. Each bill line is matched
   to the deliveries it covers and checked for:
   - quantity billed above what was received (weighed steel within its tolerance),
   - a rate above the agreed rate,
   - arithmetic (quantity × rate, totals, tax),
   - the same challan billed twice, or the same bill entered twice,
   - challans on the bill that never reached the site.
4. **Leakage.** Every finding has an amount. Raise the dispute (WhatsApp and/or email to the
   supplier with the gaps listed), mark it recovered when the credit note comes, or dismiss it
   with a reason.

## WhatsApp

| Who | Sends | Gets back |
|---|---|---|
| Site supervisor | Photo of the delivery slip (or of a bill) | What was read, and a request to confirm the count |
| Site supervisor | `OK`, or `1=148` (line and counted quantity) | The count is recorded; any shortage is flagged in rupees |
| Site supervisor | `short` or `kam` without numbers | How to give the count, e.g. `1=195` |
| Supplier | (nothing needed) | The dispute when the owner raises it |

## WhatsApp templates

Only with `APP_WHATSAPP_USE_TEMPLATES=true`:

| Template | Parameters | Suggested body |
|---|---|---|
| `material_bill_discrepancy` | 1 supplier, 2 bill number, 3 amount in dispute, 4 your business | Dear {{1}}, we found differences of {{3}} in your bill {{2}} against the deliveries received at our site. Details follow. Please send a corrected bill or credit note. {{4}} |

## Pages

Dashboard (money at risk, recovered, caught this month, slips to confirm, bills to check),
Deliveries, Bills, Leakage (open, raised, recovered), Sites, Suppliers (agreed rates), Materials.
