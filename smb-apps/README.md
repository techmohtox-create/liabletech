# Small-business photo apps

Five apps for Indian small businesses where the work already happens on WhatsApp. Someone at the
truck, the site gate, the customer's home or the counter sends a photo; the app reads it with
Claude, does the follow-up work, and replies on WhatsApp.

| App | For | What comes in | What the app does | Price | Port | Demo login |
|---|---|---|---|---|---|---|
| [Freight Chaser](freight-chaser/README.md) | Small truck owners | The driver's photo of the signed delivery receipt (POD) | Reads the LR number, date and stamp; sends the invoice with the POD attached to the transporter or broker; follows up until paid | ₹299 / month per fleet | 8081 | `owner@freight.demo` |
| [Material Tracker](material-tracker/README.md) | Small builders | The supervisor's photo of each delivery slip | Reads the quantities, checks the supervisor's count, compares them with supplier bills and flags short deliveries and over-billing in rupees | ₹999 / month per active site | 8082 | `owner@site.demo` |
| [Export Doc Checker](export-doc-checker/README.md) | Small exporters | The letter of credit, invoice, packing list, bill of lading, insurance and origin certificate | Reads all of them and checks them against each other and the credit before the bank sees them | ₹999 / month with 10 shipments (then ₹99 each), or ₹199 per shipment | 8083 | `owner@export.demo` |
| [AMC Renewals](amc-renewals/README.md) | AC and RO service businesses | The technician's photo of the appliance's rating label | Reads model and serial number into the customer's record and contract; sends renewal reminders with a link to renew, pay by UPI or book a visit | ₹199 / month | 8084 | `owner@amc.demo` |
| [Part Finder](part-finder/README.md) | Auto parts shops | A mechanic's photo, voice note or message | Identifies the part and vehicle, matches it to stock, and answers with price in seconds; the mechanic books by replying "1" | ₹299 / month, Pro ₹499 | 8085 | `owner@parts.demo` |

Every demo password is `demo@123`. The demo businesses, people, numbers and brands are made up.

## Run an app

You need Java 21. Everything else (Maven, the database) comes with the project.

```sh
cd smb-apps
./mvnw -pl amc-renewals -am package -DskipTests
java -jar amc-renewals/target/amc-renewals.jar
```

Open http://localhost:8084 and sign in with the demo login. Any app works the same way: replace
`amc-renewals` with `freight-chaser`, `material-tracker`, `export-doc-checker` or `part-finder`.

On first start the app creates its database and photos under `./data` (relative to where you run
it) and a demo business filled with sample data. To start fresh, stop the app and delete `./data`.
For real use, start with `--app.demo.enabled=false` and create an account on the sign-up page.

### Try it without WhatsApp or an AI key

Out of the box nothing leaves your computer:

- **WhatsApp simulator** (top menu): chat as any driver, supervisor, technician, customer or
  mechanic, send the sample photos, and see the app's answers. Messages take the same path as
  real WhatsApp messages; replies are recorded in the **Outbox**, never sent.
- **Without `ANTHROPIC_API_KEY`** every photo still arrives and opens a short form to fill in from
  the photo. What does not need the AI keeps working: Export Doc Checker reads SWIFT MT700 text
  from credit PDFs directly, AMC Renewals reads the customer's number and the AMC sold from the
  caption, and Part Finder understands typed requests such as "Swift 2019 front brake pad".

## Configuration

Settings are Spring Boot properties; set them as environment variables or `--name=value`
arguments. Defaults live in `platform/src/main/resources/platform-defaults.properties`.

| Setting | Environment variable | Default | What it does |
|---|---|---|---|
| `app.ai.api-key` | `ANTHROPIC_API_KEY` | (none) | Turns on reading photos and PDFs with Claude |
| `app.ai.model` | `APP_AI_MODEL` | `claude-opus-5-5` | The Claude model that reads documents |
| `app.ai.effort` | `APP_AI_EFFORT` | `medium` | How hard the model thinks (`low`, `medium`, `high`) |
| `app.ai.fallbacks` | `APP_AI_FALLBACKS` | `true` | Opts requests in to server-side fallback: if the model declines a request, the API retries it on the recommended fallback model. If your key does not have the feature, the app notices and carries on without it; set `false` to never ask |
| `app.whatsapp.mode` | `APP_WHATSAPP_MODE` | `simulator` | `cloud` to use the WhatsApp Business Cloud API |
| `app.whatsapp.access-token` | `WHATSAPP_ACCESS_TOKEN` | | Cloud API token |
| `app.whatsapp.phone-number-id` | `WHATSAPP_PHONE_NUMBER_ID` | | The shared business number messages are sent from |
| `app.whatsapp.verify-token` | `WHATSAPP_VERIFY_TOKEN` | | Webhook verification token you choose in Meta's app settings |
| `app.whatsapp.app-secret` | `WHATSAPP_APP_SECRET` | | When set, webhook calls without Meta's valid signature are refused |
| `app.whatsapp.use-templates` | `APP_WHATSAPP_USE_TEMPLATES` | `false` | Send the messages the business starts (reminders, invoices) as approved templates; see each app's README for the template wording |
| `app.whatsapp.template-language` | `APP_WHATSAPP_TEMPLATE_LANGUAGE` | `en` | Language code the templates were approved in |
| `app.public-base-url` | `APP_PUBLIC_BASE_URL` | `http://localhost:<port>` | Start of the links sent to customers (renewal and request links) |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | H2 file under `./data` | Use `jdbc:postgresql://host/db` (plus `SPRING_DATASOURCE_USERNAME` / `PASSWORD`) for PostgreSQL |
| `spring.mail.host` | `SPRING_MAIL_HOST` | (none) | Email goes out only when set; otherwise it is recorded in the outbox |
| `app.mail.from` | `APP_MAIL_FROM` | `no-reply@localhost` | Sender address for invoices and dispute emails |
| `server.port` | `PORT` | per app | |

**WhatsApp Cloud API.** Point the webhook in Meta's developer console at
`https://<your-domain>/webhooks/whatsapp` with your verify token, and subscribe to `messages`.
Each business can also connect its own WhatsApp Business number under **Settings → WhatsApp**
(the phone number ID); messages to that number then belong to that business. On the shared
number, people are recognised by their mobile number (a registered driver, supervisor,
technician, customer or mechanic).

WhatsApp only allows a business to start a conversation with an approved template; replies within
24 hours of the other person's last message can be free text. The apps reply in free text and use
templates only for the messages they start. With `use-templates` on, those go out as the named
template with its parameters, and the first attachment (an invoice PDF, say) as its document
header.

`docker compose up` starts PostgreSQL and Mailpit (a local mailbox at http://localhost:8025) if
you want to try those; see `docker-compose.yml`.

## Reading cost

Each photo or PDF read is one Claude request with the image attached. **Settings → Photo reading**
shows this month's number of documents read and tokens used, and every call is logged in the
`ai_call` table, so the cost can be checked against the plan price. On the low-priced plans
(₹199 and ₹299) a smaller model may fit the margin better: set `APP_AI_MODEL` and compare the
readings on your own documents.

Readings are suggestions shown on a review screen. A reading never sends an invoice, a dispute or a
quote by itself, with two exceptions the apps say plainly: Part Finder's automatic quotes (Pro
plan, a setting, and only for sure matches) and AMC Renewals saving a clearly read label with the
customer's number straight to that customer's record.

## Tests

```sh
cd smb-apps
./mvnw verify
```

runs every app's tests against an in-memory database, with Claude and the clock stubbed. They
cover the reading rules, the WhatsApp conversations, the pages and the PDFs.

## Layout

```
smb-apps/
  platform/            accounts, sign-in, WhatsApp (webhook, simulator, sending), email, files,
                       Claude document reading, PDFs, outbox, settings, shared look
  freight-chaser/      each app: its own package, database tables (db/migration/app),
  material-tracker/    pages (templates/<app>), WhatsApp conversation, demo data and tests
  export-doc-checker/
  amc-renewals/
  part-finder/
```

Each app is a Spring Boot 4 application (Java 21, server-rendered Thymeleaf pages, JDBC with
Flyway migrations, H2 or PostgreSQL). Pages work without JavaScript; `app.js` only adds
confirmations, live refresh and small conveniences.

Nothing here is deployed: the apps run where you start them. Plan amounts are shown on each
business's Settings page; collecting the payment is not part of these apps.
