# PayoutCheck

Swiggy and Zomato payout reconciliation for restaurants. Upload your orders and the weekly payout
statement; PayoutCheck checks every row against your contract terms and lists the deductions worth
disputing, writes the dispute message, and tracks what you get back.

Built by LiableTech. Priced at ₹199 / ₹349 / ₹499 a month (Starter / Growth / Pro).

## Run it locally

Needs Java 21 and Node 20 or newer.

```sh
./run.sh
```

Open http://localhost:8080 and sign in with the demo account **demo@payoutcheck.local / demo1234**
(owner of "Spice Route Kitchens", with four weeks of Swiggy and Zomato statements already checked).
A read-only viewer, **accounts@payoutcheck.local / demo1234**, is seeded too. Or create your own account
from the sign-up page and upload the files in [`samples/`](samples).

Data is stored in `./data` (an H2 file database). Set `PAYOUTCHECK_DEMO=false` to skip the demo business.

### With Docker and PostgreSQL

```sh
POSTGRES_PASSWORD=something-long docker compose up --build
```

### Developing

```sh
cd api && ./mvnw spring-boot:run      # API on :8080
cd web && npm start                   # UI on :4200, proxying /api to :8080
cd api && ./mvnw test                 # 30 tests: engine, parsers, and HTTP flows
```

The tests run on H2 by default. Set `SPRING_DATASOURCE_URL` (and the username and password) to run
them against PostgreSQL. They pass on PostgreSQL 16.

## What it checks

Every statement is checked when it is uploaded, and again whenever orders, terms or a neighbouring
statement change.

| Check | What it catches |
|---|---|
| Order not paid | A delivered order that is on no statement at all |
| Delivered order settled as cancelled | Your records say delivered, the statement says cancelled or rejected |
| Order value lower than billed | Item total or packaging settled below what was billed (net of fees) |
| Restaurant discount higher than agreed | More restaurant-funded discount charged than the order carried |
| Commission above contracted rate | Commission over your agreed % (excess plus the GST on it) |
| Payment gateway fee above contracted rate | Same, for the payment collection fee |
| GST on fees miscalculated | GST above 18% of the fees actually charged |
| TDS / TCS above the rate | TDS over 0.1% (section 194-O), or TCS over your terms |
| Customer refund on a delivered order | Complaint refunds recovered from you; dispute when it wasn't your fault |
| Cancellation deducted | Cancellation charges, rated by whether you were at fault |
| Penalty deducted | Penalties to question, with the reason given |
| Same charge deducted twice | Commission, refund or penalty repeated for one order, within or across statements |
| Net payout does not add up | Row net below its own order value minus its listed deductions |
| Unexplained adjustment / ad deduction | Deductions with no order reference |
| Bank credit lower than statement | Money credited to the bank below the statement's net payout |

Rules are written so the same rupee is never counted twice, and differences within a per-platform
tolerance (₹1 by default) are ignored as rounding. The engine is a pure function
(`api/src/main/java/com/liabletech/payoutcheck/recon/ReconciliationEngine.java`) with its own tests.

## Files it reads

CSV (comma, semicolon or tab, with or without a BOM) and Excel (`.xlsx`, `.xls`; the sheet with most
rows is used). The header row is found automatically below any title rows, and columns are matched by
name with common POS and annexure wording (`Order No`, `Payment Collection Charges`,
`Taxes on Service & Payment Collection Charges`, `TDS u/s 194O`, `Customer Complaints`...). Anything
not recognised can be renamed to the template header; templates are on the Upload page.

- **Orders**: Order ID, order date, item total (required); status, platform, packaging, restaurant
  discount (optional). Re-importing overlapping exports updates orders in place.
- **Statements**: net payout plus whatever breakdown columns the file has. A statement with unreadable
  rows is refused as a whole, so findings are never based on half a file. Uploading the same outlet,
  platform and period twice is refused.

Platform export layouts change from time to time; the column aliases live in
`api/src/main/java/com/liabletech/payoutcheck/ingest/Formats.java`.

## Contract terms

Each outlet has terms per platform: commission %, payment gateway fee %, GST on fees, TDS, TCS,
whether commission applies to packaging, and the rounding tolerance. New outlets start with
placeholders (18% commission, 2% / 1.84% PG, 18% GST, 0.1% TDS, 0% TCS). **Replace them with the rates
in your own agreement**; the checks are only as good as these numbers.

## Product features

- Multi-outlet businesses, each outlet with Swiggy and Zomato IDs and terms
- Dispute workflow: Open, Disputed (with ticket reference), Recovered (full or partial), Rejected, Ignored;
  bulk actions; CSV export
- Dispute letters grouped by issue, ready to paste into a partner support ticket (Growth and Pro)
- Dashboard: open, disputed and recovered amounts, weekly trend, breakdown by kind and platform
- Roles: Owner (plan, team, outlets), Manager (uploads, terms, disputes), Viewer (read and export)
- Plans enforce outlet and user limits; billing itself is outside the app
- Audit log of every upload, check, status change and setting

## Security

- Session cookie (HttpOnly, SameSite=Lax; set `PAYOUTCHECK_SECURE_COOKIES=true` behind HTTPS),
  CSRF protection, passwords hashed with bcrypt
- Lockout for 15 minutes after 5 failed sign-ins
- Every query is scoped to the signed-in user's business; role checks on every write
- Strict Content-Security-Policy, no third-party scripts, fonts or trackers
- Uploads limited to 10 MB and 50,000 rows

## Layout

```
api/       Spring Boot 4 service (Java 21, JDBC, Flyway, Spring Security)
web/       Angular 21 app, built into api/src/main/resources/static
samples/   A month of sample orders and statements with planted leaks
```

To regenerate the samples: `java -cp "api/target/classes:<classpath>" com.liabletech.payoutcheck.demo.SampleData samples 2026-10-19`.
