# PeachHacks Backend

Spring Boot 4 (Java 21, Maven) service for PeachHacks, served at `api.peachhacks.com`. It stores pre-registrations and registrations, gates registration behind an admin switch, serves the organizer admin API and sends email through Resend. The public site (`web/`) and the admin site (`admin/`) call it.

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

The integration tests run the real application against Testcontainers (`postgres:16-alpine`) and are skipped automatically when Docker is not available. They cover pre-registration, the registration gate, validation, admin authentication, CSV export and campaign audiences.

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
| `WEB_BASE_URL` | Public site URL used for links in emails (default `http://localhost:5173`, `https://www.peachhacks.com` in `prod`) |
| `ADMIN_BASE_URL` | Admin site URL used in the email sent to a newly added admin (default `http://localhost:5174`, `https://admin.peachhacks.com` in `prod`) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins (default `http://localhost:5173`, `http://localhost:5174`, `https://www.peachhacks.com`, `https://peachhacks.com`, `https://admin.peachhacks.com`) |
| `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | Connection pool size (5 in `prod`) |
| `PORT` | HTTP port (default 8080) |

The URL must be in JDBC form (`jdbc:postgresql://...`), not the `postgres://user:pass@...` form providers display; put the user and password in their own variables.

## API

JSON everywhere, timestamps in ISO-8601 UTC. Errors always look like this (`fieldErrors` only on validation errors):

```json
{ "code": "VALIDATION_ERROR", "message": "Please check the highlighted fields.", "fieldErrors": { "email": "Must be a valid email" } }
```

Codes: `VALIDATION_ERROR` (400), `INVALID_CREDENTIALS` and `UNAUTHORIZED` (401), `REGISTRATION_CLOSED` (403), `NOT_FOUND` (404), `ALREADY_REGISTERED` (409), `RATE_LIMITED` (429), `EMAIL_FAILED` (502, test email only), `INTERNAL_ERROR` (500).

### Public

| Endpoint | Purpose |
| --- | --- |
| `GET /public/status` | `{ "registrationOpen": false }` |
| `POST /public/pre-registrations` | Pre-register. Idempotent on email (case-insensitive): a repeat updates the row and returns the same `id` with 201 |
| `POST /public/registrations` | Full MLH registration. 403 while the gate is closed, 409 if the email is already registered |
| `POST /public/unsubscribe` | `{ "token": "..." }` from an email link; 204, or 404 for an unknown token |

Both forms accept a hidden `website` honeypot field: when it is filled in, the request gets a normal 201 and nothing is stored. Public POSTs are limited to 60 per minute per client address and login to 10 per minute (in memory, per instance; see `app.rate-limit.*`).

### Admin

Everything under `/admin` except login needs `Authorization: Bearer <token>`. Tokens are random, opaque, valid for 12 hours and stored only as a SHA-256 hash; passwords are stored as BCrypt hashes. There are no cookies or server sessions.

| Endpoint | Purpose |
| --- | --- |
| `POST /admin/auth/login`, `POST /admin/auth/logout`, `GET /admin/auth/me` | Sign in, invalidate the token, current admin |
| `GET /admin/stats` | Totals, per school, per day, per level of study and per status |
| `GET /admin/pre-registrations?page=&size=&q=&school=` | Paged list, newest first (`size` is capped at 200) |
| `GET /admin/pre-registrations/export.csv`, `DELETE /admin/pre-registrations/{id}` | CSV export with the same filters; delete |
| `GET /admin/registrations?page=&size=&q=&school=&status=` | Paged summaries |
| `GET`, `PATCH`, `DELETE /admin/registrations/{id}` | Detail, set `status` (`PENDING`, `ACCEPTED`, `WAITLISTED`, `REJECTED`), delete |
| `GET /admin/registrations/export.csv` | Every column, same filters |
| `GET`, `PUT /admin/settings` | The registration gate, `{ "registrationOpen": true }` |
| `POST /admin/emails/recipient-count` | How many people an audience (and optional school) reaches |
| `POST /admin/emails/test` | Send one copy to the signed-in admin |
| `POST /admin/emails`, `GET /admin/emails` | Start a campaign (202, sent in the background); list campaigns |
| `GET`, `POST /admin/admins`, `DELETE /admin/admins/{id}` | Admin accounts. You cannot delete yourself or the last admin |

CSV cells that a spreadsheet would treat as a formula (starting with `=`, `+`, `-` or `@`) are prefixed with a single quote.

### Email

Confirmation emails go out after a new pre-registration and after a registration, and a newly added admin gets an email with the sign-in link (never the password). Campaign audiences are `PRE_REGISTRANTS`, `PRE_REGISTRANTS_NOT_REGISTERED` and `REGISTRANTS`, optionally narrowed to one school; unsubscribed addresses are skipped. The body is plain text: blank lines separate paragraphs, `{{firstName}}` and `{{lastName}}` are filled in per recipient, and an unsubscribe link (`$WEB_BASE_URL/unsubscribe.html?token=...`) is appended.

Campaigns are sent one recipient at a time, about 600 ms apart (`app.email.campaign-delay`), one campaign at a time. A failed recipient is counted in `failedCount` and does not stop the campaign. Sending happens in memory, so a campaign interrupted by a restart or redeploy is marked `FAILED` at the next startup with the counts it had reached; it is not resumed.

## Deploy to Railway

1. Create a service from this repo and set **Root Directory** to `/backend`.
2. Set the config-as-code path to `/backend/railway.toml` (Railway resolves it from the repo root). It builds with the `Dockerfile` and health-checks `/actuator/health`.
3. Set the datasource variables (below), `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` / `ADMIN_BOOTSTRAP_NAME` for the first admin, `RESEND_API_KEY`, and optionally `EMAIL_FROM`, `WEB_BASE_URL` and `CORS_ALLOWED_ORIGINS`. `PORT` is injected by Railway.
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
