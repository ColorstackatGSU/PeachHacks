# PeachHacks Backend

Spring Boot 4 (Java 21, Maven) service for PeachHacks, served at `api.peachhacks.com`. It stores pre-registrations and registrations, gates registration behind an admin switch, issues tickets (QR codes) to accepted hackers, records check-ins per event, serves the organizer admin API and sends email through Resend. The public site (`web/`) and the admin site (`admin/`) call it.

The schema is managed by Flyway (`src/main/resources/db/migration`) and applied automatically at startup. Hibernate only validates it (`ddl-auto: validate`); change the schema by adding a new `V<n>__*.sql` migration, never by editing an applied one.

## Requirements

- JDK 21
- Docker (local Postgres and the integration tests)

Maven is not required; use the bundled wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

## Run locally

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl localhost:8080/actuator/health
```

The `local` profile starts Postgres from `compose.yaml` automatically (Docker must be running) and wires the datasource to it. `/actuator/health` reports `components.db.status`; `/actuator/health/liveness` and `/actuator/health/readiness` are also exposed.

To use a different Postgres, start without the `local` profile and set the datasource variables below.

### Signing in locally

The `local` profile creates an admin account at startup (see `application-local.yml`):

- Email: `admin@peachhacks.local`
- Password: `peachhacks-local-admin`

Use it on the admin site (`http://localhost:5174`), or directly:

```bash
curl -s localhost:8080/admin/auth/login -H "Content-Type: application/json" \
  -d "{\"email\":\"admin@peachhacks.local\",\"password\":\"peachhacks-local-admin\"}"
# -> { "token": "...", "expiresAt": "...", "admin": { ... } }
curl -s localhost:8080/admin/stats -H "Authorization: Bearer <token>"
```

Registration starts closed. Open it from the admin site, or with `PUT /admin/settings` and `{"registrationOpen": true}`.

Without `RESEND_API_KEY` no email leaves the machine: every message (confirmations, test emails, campaigns) is written to the application log instead.

## Tests

```bash
./mvnw test
```

The integration tests run the real application against Testcontainers (`postgres:16-alpine`) and are skipped automatically when Docker is not available. They cover pre-registration, the registration gate, validation, admin authentication and roles, events and check-in, tickets and scanning, the acceptance email, Google Wallet links, CSV export and campaign audiences.

## Container image

```bash
docker build -t peachhacks-backend .
docker run --rm -p 8080:8080 --env-file .env peachhacks-backend
```

The image runs as a non-root user with the `prod` profile and listens on `$PORT` (default 8080).

## Configuration

Standard Spring Boot environment variables; see `.env.example`.

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` | JDBC URL (default `jdbc:postgresql://localhost:5432/peachhacks`) |
| `SPRING_DATASOURCE_USERNAME` | Database user (default `peachhacks`) |
| `SPRING_DATASOURCE_PASSWORD` | Database password (default `peachhacks`) |
| `SPRING_PROFILES_ACTIVE` | `prod` in deployed environments (set by the Dockerfile) |
| `ADMIN_BOOTSTRAP_EMAIL` | Email of the first admin, created at startup if no admin with that email exists. No default outside `local` |
| `ADMIN_BOOTSTRAP_PASSWORD` | Its password, 10 to 72 characters. Only used when the account is created; it never changes an existing account |
| `ADMIN_BOOTSTRAP_NAME` | Its display name (default `Admin`) |
| `RESEND_API_KEY` | Resend API key. Blank means emails are logged, not sent |
| `EMAIL_FROM` | Sender (default `PeachHacks <hello@peachhacks.com>`); the domain must be verified in Resend |
| `WEB_BASE_URL` | Public site URL used for links in emails and for the ticket URL inside every QR code (default `http://localhost:5173`, `https://www.peachhacks.com` in `prod`). Changing it changes what newly rendered QR codes contain; codes already sent keep working because the scanner only reads the token |
| `ADMIN_BASE_URL` | Admin site URL used in the email sent to a newly added admin or volunteer (default `http://localhost:5174`, `https://admin.peachhacks.com` in `prod`) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins (default `http://localhost:5173`, `http://localhost:5174`, `https://www.peachhacks.com`, `https://peachhacks.com`, `https://admin.peachhacks.com`) |
| `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | Connection pool size (5 in `prod`) |
| `GOOGLE_WALLET_ISSUER_ID` | Google Wallet issuer ID. Optional; see [Google Wallet](#google-wallet) |
| `GOOGLE_WALLET_SERVICE_ACCOUNT_EMAIL` | Email of the service account whose key signs the passes. Optional |
| `GOOGLE_WALLET_PRIVATE_KEY` | That service account's private key in PEM form (`-----BEGIN PRIVATE KEY-----`). Real line breaks or literal `\n` sequences both work. Optional |
| `GOOGLE_WALLET_CLASS_ID` | Suffix of the event ticket class created in the Google Pay & Wallet Console (default `peachhacks_2027`, so the class is `<issuerId>.peachhacks_2027`) |
| `PORT` | HTTP port (default 8080) |

The URL must be in JDBC form (`jdbc:postgresql://...`), not the `postgres://user:pass@...` form providers display; put the user and password in their own variables.

## API

JSON everywhere, timestamps in ISO-8601 UTC. Errors always look like this (`fieldErrors` only on validation errors):

```json
{ "code": "VALIDATION_ERROR", "message": "Please check the highlighted fields.", "fieldErrors": { "email": "Must be a valid email" } }
```

Codes: `VALIDATION_ERROR` (400), `INVALID_CREDENTIALS` and `UNAUTHORIZED` (401), `REGISTRATION_CLOSED` and `FORBIDDEN` (403), `NOT_FOUND` (404), `ALREADY_REGISTERED` (409), `RATE_LIMITED` (429), `EMAIL_FAILED` (502, test email only), `INTERNAL_ERROR` (500).

### Public

| Endpoint | Purpose |
| --- | --- |
| `GET /public/status` | `{ "registrationOpen": false }` |
| `POST /public/pre-registrations` | Pre-register. Idempotent on email (case-insensitive): a repeat updates the row and returns the same `id` with 201 |
| `POST /public/registrations` | Full MLH registration. 403 while the gate is closed, 409 if the email is already registered |
| `POST /public/unsubscribe` | `{ "token": "..." }` from an email link; 204, or 404 for an unknown token |
| `GET /public/tickets/{token}` | The ticket behind a QR code: `{ "firstName", "lastName", "school", "checkedIn", "googleWalletUrl" }`. `checkedIn` is the general check-in; `googleWalletUrl` is null unless Google Wallet is configured. 404 `NOT_FOUND` unless the registration is `ACCEPTED` |
| `GET /public/tickets/{token}/qr.png` | The QR code as a PNG (1-bit, about 640 px, four-module quiet zone, `Cache-Control: public, max-age=86400`). Same 404 rule |

Both forms accept a hidden `website` honeypot field: when it is filled in, the request gets a normal 201 and nothing is stored. Public POSTs are limited to 60 per minute per client address, login to 10 per minute and the ticket endpoints to 300 per minute (a whole door queue can share one venue address); all in memory, per instance, see `app.rate-limit.*`.

### Admin

Everything under `/admin` except login needs `Authorization: Bearer <token>`. Tokens are random, opaque, valid for 12 hours and stored only as a SHA-256 hash; passwords are stored as BCrypt hashes. There are no cookies or server sessions.

#### Roles

Every account is an `ADMIN` (full access) or a `VOLUNTEER` (check-in only); the role is returned with the account by login, `/admin/auth/me` and the accounts list. A volunteer may call `/admin/auth/*`, `/admin/check-in` and everything under it, and `GET /admin/events`. Every other `/admin/**` route answers 403 `FORBIDDEN` for a volunteer. The rule lives in `SecurityConfig`, so a route added later is admin-only unless it is listed there.

| Endpoint | Purpose |
| --- | --- |
| `POST /admin/auth/login`, `POST /admin/auth/logout`, `GET /admin/auth/me` | Sign in, invalidate the token, current account (`{ id, email, name, role }`) |
| `GET /admin/stats` | Totals, per school, per day, per level of study and per status; `registrations.checkedIn` (general check-in) and `events`, a list of `{ eventId, name, checkedIn }` |
| `GET /admin/pre-registrations?page=&size=&q=&school=` | Paged list, newest first (`size` is capped at 200) |
| `GET /admin/pre-registrations/export.csv`, `DELETE /admin/pre-registrations/{id}` | CSV export with the same filters; delete |
| `GET /admin/registrations?page=&size=&q=&school=&status=&checkedIn=` | Paged summaries with `checkedInAt` (general check-in). `checkedIn=true` or `false` filters on it |
| `GET`, `PATCH`, `DELETE /admin/registrations/{id}` | Detail, set `status` (`PENDING`, `ACCEPTED`, `WAITLISTED`, `REJECTED`), delete. The detail adds `checkedInAt`, `checkedInBy`, `checkIns` (every event: `{ eventId, name, general, checkedInAt, checkedInBy }`) and, only while `ACCEPTED`, `ticketToken`, `ticketUrl` and `googleWalletUrl`. Changing the status to `ACCEPTED` from anything else emails the ticket |
| `POST /admin/registrations/{id}/ticket-email` | Send the ticket email again; 204, or 400 if the registration is not `ACCEPTED` |
| `GET /admin/registrations/export.csv` | Every column plus `checked_in_at` (general check-in), same filters. This is the check-in data MLH asks for |
| `GET`, `PUT /admin/settings` | The registration gate, `{ "registrationOpen": true }` |
| `POST /admin/emails/recipient-count` | How many people an audience (and optional school) reaches |
| `POST /admin/emails/test` | Send one copy to the signed-in admin |
| `POST /admin/emails`, `GET /admin/emails` | Start a campaign (202, sent in the background); list campaigns |
| `GET`, `POST /admin/admins`, `DELETE /admin/admins/{id}` | Accounts. `POST` takes an optional `role` (`ADMIN` by default, or `VOLUNTEER`). You cannot delete yourself or the last `ADMIN`; volunteers do not count towards that |
| `GET /admin/events` | Both roles. Every check-in event with its count: `[{ id, name, startsAt, general, checkedIn }]`, the built-in general event first |
| `POST /admin/events`, `PATCH /admin/events/{id}` | `{ "name", "startsAt" }` (`startsAt` optional, ISO-8601 UTC). `PATCH` changes only the keys sent; `"startsAt": null` clears it. Names are unique |
| `DELETE /admin/events/{id}` | Deletes a workshop and its check-ins. 400 for the general event |
| `GET /admin/events/{id}/export.csv` | That event's attendees: name, email, school, status, `checked_in_at`, `checked_in_by` |
| `GET /admin/check-in?eventId=&q=&page=&size=` | Both roles. `{ event, items, total, page, size, checkedInTotal, registrationTotal }`; `q` matches first name, last name or email. Not-yet-checked-in people first, then by last name |
| `POST`, `DELETE /admin/check-in/{registrationId}?eventId=` | Both roles. Check in (idempotent: a repeat keeps the first time and name) or undo; returns the item |
| `POST /admin/check-in/scan` | Both roles. Check in by scanned ticket, see below |

#### Check-in

Check-ins are recorded per event. One event, "General check-in", is built in (seeded by the migration, cannot be deleted) and is what "attendance" means everywhere else: `checkedInAt` on registrations, the `checkedIn` filter and count, and the `checked_in_at` CSV column. Workshops are extra events an admin adds. Every check-in endpoint takes an optional `eventId` and uses the general event without it; an unknown `eventId` is 404.

Items returned by the check-in endpoints carry only what someone at the door needs: `id`, `firstName`, `lastName`, `email`, `school`, `status`, `checkedInAt` and `checkedInBy` for the event asked about, and `generalCheckedIn`, so a workshop check-in can flag someone who skipped the front desk. Checking in by registration id works for any status; `status` is there for the screen to warn with.

`POST /admin/check-in/scan` takes `{ "code", "eventId", "override" }`, where `code` is whatever the scanner read: the bare ticket token or the whole ticket URL. It always answers 200 with `{ "result", "event", "item" }`:

| `result` | Meaning |
| --- | --- |
| `CHECKED_IN` | Checked in by this call |
| `ALREADY_CHECKED_IN` | Nothing changed; `item.checkedInAt` and `item.checkedInBy` say when and by whom |
| `NOT_ACCEPTED` | The ticket belongs to someone whose status is not `ACCEPTED`. `item` is returned, nobody was checked in. Repeat the call with `"override": true` to check them in anyway |
| `NOT_RECOGNISED` | No ticket matches; `item` is null |

#### Tickets

Every registration has a random `ticket_token` that carries no personal data. The QR code encodes `$WEB_BASE_URL/ticket?t=<token>`, so a phone camera opens the hacker's ticket page on the public site and the admin scanner reads the same code. A ticket only exists while the registration is `ACCEPTED`: the public ticket endpoints answer 404 for every other status exactly as they do for an unknown token.

CSV cells that a spreadsheet would treat as a formula (starting with `=`, `+`, `-` or `@`) are prefixed with a single quote.

### Email

Confirmation emails go out after a new pre-registration and after a registration, and a newly added admin or volunteer gets an email with the sign-in link (never the password) that says which kind of account it is.

When a registration becomes `ACCEPTED` the hacker gets a "You're in" email with a link to their ticket page and the QR code itself, embedded in the message (`cid:` image) and listed as a PNG attachment, because many mail clients block images loaded from a server. It is sent once per change to `ACCEPTED`, not when a save leaves the status at `ACCEPTED`; `POST /admin/registrations/{id}/ticket-email` sends it again. With Google Wallet configured it also carries an "Add to Google Wallet" link. Campaign audiences are `PRE_REGISTRANTS`, `PRE_REGISTRANTS_NOT_REGISTERED` and `REGISTRANTS`, optionally narrowed to one school; unsubscribed addresses are skipped. The body is plain text: blank lines separate paragraphs, `{{firstName}}` and `{{lastName}}` are filled in per recipient, and an unsubscribe link (`$WEB_BASE_URL/unsubscribe.html?token=...`) is appended.

Campaigns are sent one recipient at a time, about 600 ms apart (`app.email.campaign-delay`), one campaign at a time. A failed recipient is counted in `failedCount` and does not stop the campaign. Sending happens in memory, so a campaign interrupted by a restart or redeploy is marked `FAILED` at the next startup with the counts it had reached; it is not resumed.

## Google Wallet

Optional. With `GOOGLE_WALLET_ISSUER_ID`, `GOOGLE_WALLET_SERVICE_ACCOUNT_EMAIL` and `GOOGLE_WALLET_PRIVATE_KEY` all set, accepted hackers get an "Add to Google Wallet" link (`googleWalletUrl` on the public ticket and in the acceptance email). With any of them missing the feature is off and nothing mentions it. A key that cannot be parsed logs one error at startup and leaves the feature off. There is no Apple Wallet support.

The link is `https://pay.google.com/gp/v/save/<jwt>`: a JWT signed with the service account key (RS256) that carries the hacker's event ticket object, so the backend never calls Google. The object holds only what is personal: a stable id (`<issuerId>.ticket_<registration id>`, so saving twice does not make two passes), the holder's name, their school, and a QR barcode whose value is the same ticket URL the PNG encodes. Everything shared (event name, dates, venue, logo, colour) comes from the pass class, which the JWT references by id and does not define.

One-time setup in Google's consoles:

1. Create a Google Wallet API issuer account in the [Google Pay & Wallet Console](https://pay.google.com/business/console) and note the issuer ID.
2. In the console's Google Wallet API section create a class with pass type **Event ticket** and id `peachhacks_2027` (or set `GOOGLE_WALLET_CLASS_ID` to the suffix you chose). It must exist before anyone opens a save link, otherwise Google rejects the link.
3. In a Google Cloud project, enable the Google Wallet API, create a service account and create a JSON key for it. `client_email` from that file is `GOOGLE_WALLET_SERVICE_ACCOUNT_EMAIL` and `private_key` is `GOOGLE_WALLET_PRIVATE_KEY`.
4. Back in the Google Pay & Wallet Console, under Users, invite the service account's email with the Developer access level.
5. Until Google grants the issuer publishing access the account is in demo mode: only Google accounts that are admins or developers of the issuer account, or that were added as test accounts in the console, can save the pass, and it is marked as a test. Request publishing access from the console's Google Wallet API page once the business profile is complete.

## Deploy to Railway

1. Create a service from this repo and set **Root Directory** to `/backend`.
2. Set the config-as-code path to `/backend/railway.toml` (Railway resolves it from the repo root). It builds with the `Dockerfile` and health-checks `/actuator/health`.
3. Set the datasource variables (below), `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` / `ADMIN_BOOTSTRAP_NAME` for the first admin, `RESEND_API_KEY`, and optionally `EMAIL_FROM`, `WEB_BASE_URL`, `CORS_ALLOWED_ORIGINS` and the `GOOGLE_WALLET_*` variables. `PORT` is injected by Railway.
4. Point `api.peachhacks.com` at the service under Settings > Networking > Custom Domain.

Run a single instance: rate limiting and campaign sending are kept in memory.

**Railway Postgres:** add a Postgres service and reference its variables on the backend service:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
```

**Supabase:** take the host, user and password from Project Settings > Database (Connect).

```
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0
SPRING_DATASOURCE_USERNAME=postgres.<project-ref>
SPRING_DATASOURCE_PASSWORD=<database-password>
```

- The transaction pooler (port 6543, user `postgres.<project-ref>`) is IPv4-reachable, which Railway needs. `prepareThreshold=0` is required because transaction-mode pooling does not support server-side prepared statements.
- The direct connection (`db.<project-ref>.supabase.co:5432`, user `postgres`) is IPv6 only unless the IPv4 add-on is enabled; use it for long-lived sessions or schema work.
- Keep the pool small on the Supabase free tier.
