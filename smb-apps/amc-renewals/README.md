# AMC Renewals

For AC and RO water purifier service businesses that earn steady money from annual maintenance
contracts (AMCs) but lose renewals nobody remembered to chase. The technician photographs the
appliance's rating label at the customer's home and sends it on WhatsApp, with the customer's
number and the AMC sold in the caption. The appliance and the contract are saved, and from then on
every contract gets its renewal reminders and service-visit reminders on time, each with a link
where the customer can renew, pay by UPI or book a visit.

**Price:** ₹199 per month. **Port:** 8084. **Demo:** `owner@amc.demo` / `demo@123`.

## How it works

1. **Plans.** A new business starts with four editable plans (AC Care, AC Comprehensive, RO Care,
   RO Gold) with their price, length and included visits. Prices are a starting point, not advice.
2. **Label photo.** The technician sends the photo with a caption such as
   `9812345678 Sharma AMC 1499 paid bedroom`. Claude reads the appliance type, brand, model, serial
   number, capacity, refrigerant and date of manufacture; the caption gives the customer's number
   and name, whether an AMC was sold, the amount, whether it was paid and the room.
   - A clear label (sure reading, brand, model or serial) with the customer's number is saved
     straight to that customer's record, with the contract on the plan the caption names or
     prices. The same serial number photographed again updates the appliance; an AMC sold on an
     appliance that already has one renews it.
   - Anything else waits under **Labels** for the office, who corrects it against the photo.
3. **Reminders.** Every morning at 10 (India time) each contract gets the reminder that has fallen
   due, once: by default 30, 15 and 7 days before it ends, on the day, and 7 and 30 days after.
   A reminder missed by more than three days (the app was off) is skipped rather than sent late.
   Reminders quote today's plan price, not last year's. Included service visits are spread over the
   year and get their own reminder a few days before each is due. The schedule is editable under
   **Setup**, and **Send now** runs it on demand.
4. **The customer's link.** Each reminder carries a private link (no login) showing the appliance
   and contract. The customer picks a plan to renew, and with the business's UPI ID set under
   Settings gets a **Pay by UPI** button for the exact amount; or picks a day and time for a
   service visit; or stops the reminders. Nothing is renewed until the office confirms it.
5. **Requests.** Renewal and visit requests (from the link or WhatsApp replies) land under
   **Requests** and the owner is told on WhatsApp. Confirm a renewal (and the payment) and the
   customer is thanked with the new end date; book a visit with a technician and the customer is
   told the day.

## WhatsApp

| Who | Sends | Gets back |
|---|---|---|
| Technician (under Setup) | Label photo with the customer's number, `AMC 1499`, `paid`, the room | What was saved (appliance, serial number, contract until when), or what the office still needs |
| Technician | `TODAY` | Their booked visits for today and tomorrow |
| Customer | `YES` | The renewal is noted, with the price and the link to pay or pick a day |
| Customer | `SERVICE` | A visit request; the link to pick a day |
| Customer | `STOP` / `START` | Reminders stop / start again |
| Customer | anything else | Passed to the owner on WhatsApp |
| Someone new (on the business's own number) | anything | An enquiry under Requests, and a reply that the office will call |

## WhatsApp templates

Reminders start conversations, so they need approved templates on a real number
(`APP_WHATSAPP_USE_TEMPLATES=true`). Category *Utility*:

| Template | Parameters | Suggested body |
|---|---|---|
| `amc_renewal_reminder` | 1 first name, 2 appliance, 3 end date, 4 price, 5 link | Namaste {{1}} ji, your AMC for the {{2}} ends on {{3}}. Renew for {{4}} to keep your scheduled services: {{5}} Reply YES to renew or STOP to stop these messages. |
| `amc_service_due` | 1 first name, 2 appliance, 3 visit number, 4 link | Namaste {{1}} ji, your {{2}} is due for service visit {{3}} under your AMC. Pick a day: {{4}} or reply SERVICE. |
| `amc_new_request` | 1 customer, 2 what they asked for | {{1}} asked for {{2}}. Open Requests to confirm. |
| `amc_customer_message` | 1 customer, 2 their message | {{1}} wrote: {{2}} |

## Pages

Dashboard (due in 30 days, lapsed, renewed this month, renewal rate, contracts in force, new
requests, visits this week, labels to finish), Renewals (due, in force, lapsed, renewed,
cancelled), Requests, Customers (appliances, contracts, messages), Labels, Setup (plans,
technicians, reminder schedule, message previews).
