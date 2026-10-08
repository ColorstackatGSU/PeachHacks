# PeachHacks Backend

Spring Boot 4 (Java 21, Maven) service for PeachHacks, served at `api.peachhacks.com`. It stores pre-registrations and registrations, gates registration behind an admin switch, issues tickets (QR codes) to accepted hackers, holds acceptances in a bucket until organizers send the acceptance emails together, tracks the host-school share of accepted hackers, records check-ins per event, serves the organizer admin API and sends email through Resend. The public site (`web/`) and the admin site (`admin/`) call it.

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

Without `RESEND_API_KEY` no email leaves the machine: every message (confirmations, test emails, campaigns) is written to the application log instead, body included, so links can be copied from it. That is for development only: with the `prod` profile the application refuses to start without the key.

## Tests

```bash
./mvnw test
```

The integration tests run the real application against Testcontainers (`postgres:16-alpine`) and are skipped automatically when Docker is not available. They cover pre-registration, the registration gate, validation (including the phone, LinkedIn and name rules), duplicate sign-ups, admin authentication and roles, events and check-in (accepted registrations only), tickets and scanning, the acceptance bucket (accepting sends nothing, the send-all run, a provider failure, the host-school share, the age review flag, bulk status changes, and the V6 migration on rows accepted before it), the acceptance email and its idempotency key, Google Wallet links, CSV export and its log line, campaign kinds and audiences (who an event update and an announcement reach, their footers, and the V7 migration), campaign recipients (the per-person record, resuming after a restart, refusing an identical campaign), provider retries, request body limits, rate limits (`RateLimitApiTests`, which has its own context with low limits), resume upload, download, removal and the sponsor resume book, school email confirmation (the link, its expiry, the resend limit, carry-over from pre-registration to registration, and the admin filters and resend), PeachBot (`DiscordApiTests`: signatures, what the Verify button answers, role changes on a status change, writing and editing the Verify message) and the hacker platform (`PlatformApiTests`: both sign-ins, the profile and directory, teams, Connect Discord), the last two against a local stub standing in for Discord and Google.

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
| `RESEND_API_KEY` | Resend API key. Required with the `prod` profile: without it the application stops at startup. Elsewhere, blank means emails are logged, not sent |
| `EMAIL_FROM` | Sender (default `PeachHacks <hello@peachhacks.com>`); the domain must be verified in Resend |
| `WEB_BASE_URL` | Public site URL used for links in emails (unsubscribe, ticket, school email confirmation) and for the ticket URL inside every QR code (default `http://localhost:5173`, `https://www.peachhacks.com` in `prod`). Changing it changes what newly rendered QR codes contain; codes already sent keep working because the scanner only reads the token |
| `ADMIN_BASE_URL` | Admin site URL used for the set-password links emailed to admins and volunteers (default `http://localhost:5174`, `https://admin.peachhacks.com` in `prod`) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins (default `http://localhost:5173`, `http://localhost:5174`, `http://localhost:5176`, `https://www.peachhacks.com`, `https://peachhacks.com`, `https://admin.peachhacks.com`, `https://platform.peachhacks.com`) |
| `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | Connection pool size (5 in `prod`) |
| `GOOGLE_WALLET_ISSUER_ID` | Google Wallet issuer ID. Optional; see [Google Wallet](#google-wallet) |
| `GOOGLE_WALLET_SERVICE_ACCOUNT_EMAIL` | Email of the service account whose key signs the passes. Optional |
| `GOOGLE_WALLET_PRIVATE_KEY` | That service account's private key in PEM form (`-----BEGIN PRIVATE KEY-----`). Real line breaks or literal `\n` sequences both work. Optional |
| `GOOGLE_WALLET_CLASS_ID` | Suffix of the event ticket class created in the Google Pay & Wallet Console (default `peachhacks_2027`, so the class is `<issuerId>.peachhacks_2027`) |
| `DISCORD_BOT_TOKEN` | PeachBot's bot token. Optional; see [PeachBot](#peachbot). With any of the six `DISCORD_*` values other than the client secret missing, the bot is off |
| `DISCORD_PUBLIC_KEY` | The Discord application's public key (hex), used to check that an interaction really came from Discord. A value that is not a valid key stops the application at startup |
| `DISCORD_APPLICATION_ID` | The Discord application ID (also its OAuth2 client ID) |
| `DISCORD_CLIENT_SECRET` | The application's OAuth2 client secret. Only needed for "Connect Discord" on the hacker platform |
| `DISCORD_GUILD_ID` | ID of the PeachHacks Discord server |
| `DISCORD_HACKER_ROLE_ID` | ID of the role accepted hackers get |
| `DISCORD_VERIFICATION_CHANNEL_ID` | ID of the channel the Verify message is posted in |
| `DISCORD_WELCOME_CHANNEL_ID` | ID of the channel where PeachBot welcomes everyone who joins. Optional: without it there are no welcomes and no gateway connection |
| `PLATFORM_BASE_URL` | Hacker platform URL, used for the links in its emails and as the Discord redirect (default `http://localhost:5176`, `https://platform.peachhacks.com` in `prod`) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | The Google OAuth client for "Continue with Google" on the hacker platform. Optional: without both there is email and password only |
| `API_BASE_URL` | This backend's public address (default `http://localhost:8080`, `https://api.peachhacks.com` in `prod`). Google sends the browser back to `<API_BASE_URL>/platform/auth/google/callback` |
| `MAX_TEAM_SIZE` | Most hackers on one team (default `4`) |
| `HOST_SCHOOL_NAME` | The host school (default `Georgia State University`). A registration counts towards the host-school share when its school name equals or starts with this, case-insensitive, so `Georgia State University Perimeter College` counts too |
| `HOST_SCHOOL_TARGET` | The share of accepted hackers that must come from the host school, as a fraction from 0 to 1 (default `0.70`). A value outside that range stops the application at startup |
| `NON_HOST_MINIMUM_AGE` | The minimum age for students of other schools (default `18`); host-school students are eligible at any age. A registration under it from a school that is not the host school is flagged `ageReview` for organizers and is never refused. See [Age review](#age-review) |
| `SIGN_UP_LIMIT_PER_ADDRESS` | How many sign-up POSTs (pre-registration and registration together) one client address may make in 10 minutes (default `30`). Raise it for an event where many people sign up from one network |
| `SIGN_UP_LIMIT_PER_HOUR` | How many sign-up POSTs are accepted per hour from all addresses together (default `1000`) |
| `PORT` | HTTP port (default 8080) |

The URL must be in JDBC form (`jdbc:postgresql://...`), not the `postgres://user:pass@...` form providers display; put the user and password in their own variables.

## API

JSON everywhere, timestamps in ISO-8601 UTC. Errors always look like this (`fieldErrors` only on validation errors):

```json
{ "code": "VALIDATION_ERROR", "message": "Please check the highlighted fields.", "fieldErrors": { "email": "Must be a valid email" } }
```

Codes: `VALIDATION_ERROR` (400), `INVALID_CREDENTIALS` and `UNAUTHORIZED` (401), `REGISTRATION_CLOSED` and `FORBIDDEN` (403), `NOT_FOUND` (404), `ACCEPTANCE_SEND_RUNNING`, `CAMPAIGN_ALREADY_SENDING`, `NOT_ACCEPTED` and `EVENT_HAS_CHECK_INS` (409), `PAYLOAD_TOO_LARGE` (413), `RATE_LIMITED` (429), `EMAIL_FAILED` (502, the test email and the one-person acceptance email), `INTERNAL_ERROR` (500).

### Public

| Endpoint | Purpose |
| --- | --- |
| `GET /public/status` | `{ "registrationOpen": false }` |
| `POST /public/pre-registrations` | Pre-register. `schoolEmail` is required. 201 `{ "id" }`. The first submission for an `email` (case-insensitive) is the one that is kept; see [Repeated sign-ups](#repeated-sign-ups) |
| `POST /public/registrations` | Full MLH registration. `schoolEmail`, `graduationYear` (2026 to 2035) and `graduationMonth` (1 to 12) are required. Optional `resume`, see [Resumes](#resumes). 201 `{ "id" }`, 403 while the gate is closed. An `email` that is already registered is also answered 201; see [Repeated sign-ups](#repeated-sign-ups) |
| `POST /public/unsubscribe` | `{ "token": "..." }` from the link in an announcement; 204, or 404 for an unknown token. It stops announcements only, see [Email](#email) |
| `POST /public/school-email/confirm` | `{ "token": "..." }` from the link mailed to the school address; 200 `{ "schoolEmail": "ada@school.edu" }`, also when that link was already used; 404 `NOT_FOUND` for an unknown or expired token. See [School email confirmation](#school-email-confirmation) |
| `POST /public/school-email/resend` | `{ "email": "<personal email>" }`; always 204, whether or not the email is known. Mails a new link to the school address if there is an unconfirmed one and none was sent in the last 10 minutes |
| `GET /public/tickets/{token}` | The ticket behind a QR code: `{ "firstName", "lastName", "school", "checkedIn", "googleWalletUrl" }`. `checkedIn` is the general check-in; `googleWalletUrl` is null unless Google Wallet is configured. 404 `NOT_FOUND` unless the registration is `ACCEPTED` |
| `GET /public/tickets/{token}/qr.png` | The QR code as a PNG (1-bit, about 640 px, four-module quiet zone, `Cache-Control: public, max-age=86400`). Same 404 rule |

Both forms take two addresses: `email` is the personal one that identifies the person (uniqueness, confirmation and ticket emails), `schoolEmail` is the school-issued one. `schoolEmail` must be a well-formed address of at most 255 characters; it does not have to end in `.edu`, may equal `email`, is stored trimmed and lower-cased, and is not unique. Registrations made before it was collected have `schoolEmail: null`.

#### School email confirmation

PeachHacks is for current students, and the school address is how that is checked: the API mails a link to the school address (not the personal one), and opening it and pressing the button on the page confirms the address. It is plain email confirmation, not a sign-in.

- Confirmation belongs to the pair (personal `email`, `schoolEmail`), kept in `school_email_confirmations`. A pre-registration or registration is confirmed when its own two addresses match a confirmed pair, so someone who confirms after pre-registering and then registers with the same two addresses is not asked again. A submission with a different school email is a different pair and starts unconfirmed.
- A link is mailed on every pre-registration and on every registration whose pair is not confirmed, at most once per pair per 10 minutes, so repeating the idempotent pre-registration call does not fill the inbox. The confirmation email to the personal address tells the person to look in their school inbox.
- The link is `$WEB_BASE_URL/confirm-email?token=<token>`. Tokens are random, stored only as a SHA-256 hash (`school_email_tokens`) and expire after 14 days. A new link does not cancel earlier ones. The token is only ever written to the email; with `RESEND_API_KEY` unset that email, like every other, is printed in the application log.
- Nothing is confirmed by a GET: mail scanners open links. The page posts the token to `POST /public/school-email/confirm`.
- Rows that existed before this feature are unconfirmed and are not mailed automatically; use the admin resend, or the public resend from the page.
- An unconfirmed school email does not block any status change. Organizers see `schoolEmailConfirmed` and decide.

The checks match the ones the forms make in `web/src/forms/validation.js`: every email field uses one pattern (`Patterns.EMAIL`); `firstName` and `lastName` may contain letters of any script, spaces, apostrophes, periods and hyphens only, because names are placed in emails and must not be able to form a link; `phone` is 7 to 15 digits written with digits, spaces and `+ ( ) - . x #`; `linkedinUrl`, when given, must be an `http(s)` URL whose host is `linkedin.com` or a subdomain of it.

#### Repeated sign-ups

Neither form says whether an email is already known, and neither lets a second submission change what the first one stored: whoever sends it has not shown that the address is theirs.

- A repeated pre-registration is answered 201 with an `id` that belongs to nothing. The stored name, school and school email stay as they were. If the stored school email is still unconfirmed, its confirmation link is requested again (at most once per 10 minutes).
- A registration for an email that is already registered is answered 201 with an `id` that belongs to nothing, exactly like a new one. Nothing from it is stored (the resume is discarded) and the address's owner gets a short "You're already registered" email, at most one per hour per address. `ALREADY_REGISTERED` is no longer returned. The unique index on the email is still there underneath.
- Once a registration is committed, a failure while its confirmation emails are prepared is logged and the person still gets their 201.

#### Limits

Both forms accept a hidden `website` honeypot field: when it is filled in, the request gets a normal 201 and nothing is stored.

Rate limits are in memory, per instance, and counted per client address (see `app.rate-limit.*`); over the limit is 429 `RATE_LIMITED`:

| What | Default |
| --- | --- |
| The two sign-up POSTs (`/public/pre-registrations`, `/public/registrations`), together | 30 per 10 minutes per address (`SIGN_UP_LIMIT_PER_ADDRESS`), and 1000 per hour across all addresses (`SIGN_UP_LIMIT_PER_HOUR`), which answers "please try again shortly" |
| Every other public POST | 60 per minute |
| `POST /admin/auth/login`, `forgot-password`, `set-password`, `set-password/check` and `change-password`, together | 10 per minute |
| Failed sign-ins for one email, from any address | 10 in 15 minutes (`app.rate-limit.login-failures-per-account`, `login-failure-window`); further attempts for that email are 429 until the window ends, a successful sign-in or a password set from an emailed link clears it. It counts for emails that have no account too, so it does not reveal which emails have one |
| The ticket endpoints | 300 per minute (a whole door queue can share one venue address) |

The bucket is chosen from the route the request matched, not from the URL as sent, so `/admin/%61uth/login` counts as a login. Every failed sign-in is logged at WARN with the email attempted and the client address, never the password. There is no CAPTCHA. Many people signing up from one campus network share one address: raise `SIGN_UP_LIMIT_PER_ADDRESS` for a sign-up drive.

With `app.rate-limit.trust-forwarded-for` on (the `prod` profile) the client address is the last entry of `X-Forwarded-For`, the one the hosting proxy appended. The first rate-limited request after startup logs one INFO line with how many entries the header carried and which address was used, so the proxy setup can be checked on the live service.

Request bodies are capped by the bytes actually read (`BodyLimitFilter`), so a chunked request with no `Content-Length` is held to the same limit as one that declares its size: 3 MB for `POST /public/registrations` (over it is the 400 `fieldErrors.resume` "Resume must be 2 MB or smaller") and 64 KB for every other POST under `/public` and `/admin/auth` (over it is 413 `PAYLOAD_TOO_LARGE`).

### Admin

Everything under `/admin` except login and the password-link routes needs `Authorization: Bearer <token>`. Tokens are random, opaque, valid for 12 hours and stored only as a SHA-256 hash; passwords are stored as BCrypt hashes. There are no cookies or server sessions.

#### Roles

Every account is an `ADMIN` (full access) or a `VOLUNTEER` (check-in only); the role is returned with the account by login, `/admin/auth/me` and the accounts list. A volunteer may call `/admin/auth/*`, `/admin/check-in` and everything under it, and `GET /admin/events`. Every other `/admin/**` route answers 403 `FORBIDDEN` for a volunteer. The rule lives in `SecurityConfig`, so a route added later is admin-only unless it is listed there.

| Endpoint | Purpose |
| --- | --- |
| `POST /admin/auth/login`, `POST /admin/auth/logout`, `GET /admin/auth/me` | Sign in, invalidate the token, current account (`{ id, email, name, role }`). After 10 failed sign-ins for one email in 15 minutes, login answers 429 `RATE_LIMITED` for that email, see [Limits](#limits) |
| `POST /admin/auth/forgot-password` | Public. `{ email }`; always 204. If the account exists it is emailed a one-time link, valid for 1 hour |
| `POST /admin/auth/set-password/check`, `POST /admin/auth/set-password` | Public. `{ token }` answers `{ email, name, invite }`; `{ token, password }` saves the password, uses up the link and ends every session of that account. A bad, used or expired link is 400 `INVALID_PASSWORD_LINK`. A password is 10 to 72 characters and at most 72 bytes of UTF-8 (accented letters and emoji take more than one); longer is a 400 with `fieldErrors.password` |
| `POST /admin/auth/change-password` | `{ currentPassword, newPassword }` for the signed-in account; its other sessions are ended. Same length rule (`fieldErrors.newPassword`); rate limited with login |
| `GET /admin/stats` | Totals, per school, per day, per level of study and per status; `preRegistrations.schoolEmailConfirmed` and `registrations.schoolEmailConfirmed` (how many have a confirmed school email); `registrations.checkedIn` (general check-in), `registrations.withResume` (resumes uploaded) and `events`, a list of `{ eventId, name, checkedIn }` |
| `GET /admin/pre-registrations?page=&size=&q=&school=&schoolEmailConfirmed=` | Paged list, newest first (`size` is capped at 200). Items carry `schoolEmailConfirmed` and `schoolEmailConfirmedAt` (null until confirmed); `schoolEmailConfirmed=true` or `false` filters on it |
| `GET /admin/pre-registrations/export.csv` | CSV export with the same filters, `school_email_confirmed` appended last. Logged, see [CSV exports](#csv-exports). Pre-registrations cannot be deleted through the API |
| `POST /admin/pre-registrations/{id}/school-email/resend` | Mail a new confirmation link to the school address, ignoring the 10-minute limit; 204, or 400 if it is already confirmed |
| `GET /admin/registrations?page=&size=&q=&school=&status=&checkedIn=&resume=&schoolEmailConfirmed=&ageReview=` | Paged summaries with `schoolEmail`, `schoolEmailConfirmed`, `schoolEmailConfirmedAt`, `acceptedAt`, `acceptanceNotifiedAt`, `checkedInAt` (general check-in), `hasResume` and `ageReview`. `q` matches the name, `email` or `schoolEmail`. `checkedIn=true` or `false` filters on the check-in; `resume=any` (uploaded one) or `none` filters on the resume; `schoolEmailConfirmed=true` or `false` filters on the school email (`false` includes registrations that have none); `ageReview=true` or `false` filters on the [age review](#age-review) flag |
| `GET`, `PATCH /admin/registrations/{id}` | Detail, set `status` (`PENDING`, `ACCEPTED`, `WAITLISTED`, `REJECTED`). Registrations cannot be deleted through the API (`DELETE` answers 405). The detail adds `checkedInAt`, `checkedInBy`, `checkIns` (every event: `{ eventId, name, general, checkedInAt, checkedInBy }`) and, only while `ACCEPTED`, `ticketToken`, `ticketUrl` and `googleWalletUrl`; also `resume` (`null` or `{ fileName, size, uploadedAt }`, never the file itself), and `schoolEmailConfirmed` with `schoolEmailConfirmedAt`, which the admin site turns into a warning on the status control, and `ageReview` (see [Age review](#age-review)). Neither an unconfirmed school email nor an age review blocks `ACCEPTED`. No status change sends an email; `acceptedAt` and `acceptanceNotifiedAt` say where an accepted registration stands, see [Acceptances](#acceptances) |
| `POST /admin/registrations/status` | Bulk status change: `{ "ids": ["..."], "status": "ACCEPTED" }`, 1 to 500 ids, same rules as the single `PATCH`. Returns `{ "changed", "unchanged", "notFound", "acceptedAgeReview" }`: `unchanged` already had the status, `notFound` no longer exist (they do not fail the call); repeated ids count once. `acceptedAgeReview` is how many of the registrations this call moved to `ACCEPTED` need an [age review](#age-review) (0 for any other status) |
| `POST /admin/registrations/{id}/ticket-email` | For someone already told, sends the ticket email again in the background. For someone accepted and still waiting, sends their acceptance email now and marks them told, so one person can be let in early; 502 `EMAIL_FAILED` if the provider does not take it (they stay waiting) and 409 `ACCEPTANCE_SEND_RUNNING` if the send-all run is mailing that person at that moment. 204 otherwise, 400 if the registration is not `ACCEPTED` |
| `GET /admin/acceptances/summary` | Counts, host-school share, age review counts and the state of the last send, recomputed on every call. See [Acceptances](#acceptances) |
| `GET /admin/acceptances/waiting` | The bucket: everyone `ACCEPTED` and not yet told, longest-waiting first, as `[{ id, firstName, lastName, email, school, host, ageReview, acceptedAt }]` |
| `POST /admin/acceptances/send` | Emails everyone in the bucket. 202 `{ "queued": 12, "send": { ... } }` when a run was queued, 200 with `"queued": 0` when nobody is waiting (nothing is sent), 409 `ACCEPTANCE_SEND_RUNNING` while a run is going. Progress is `send` in the summary |
| `POST /admin/registrations/{id}/school-email/resend` | Mail a new confirmation link to the school address, ignoring the 10-minute limit; 204, or 400 if it is already confirmed or the registration has no school email |
| `GET /admin/registrations/export.csv` | Every column plus, appended last, `checked_in_at` (general check-in), `has_resume`, `school_email`, `school_email_confirmed` and `age_review`; same filters. This is the check-in data MLH asks for. Logged, see [CSV exports](#csv-exports) |
| `GET /admin/registrations/{id}/resume` | The uploaded PDF as an attachment. 404 if there is none |
| `DELETE /admin/registrations/{id}/resume` | Deletes the file, for a removal request; 204, or 404 if there is none. The registration stays |
| `GET /admin/resumes/export.zip?checkedIn=` | The sponsor resume book, see [Resumes](#resumes) |
| `GET`, `PUT /admin/settings` | The registration gate, `{ "registrationOpen": true }`; the answer also carries `previewActive` |
| `POST`, `DELETE /admin/settings/registration-preview` | Make (or end) the preview link, `{ "url" }`. A request that sends its key in `X-Registration-Preview` gets `registrationOpen: true` (with `preview: true`) from `GET /public/status` and may `POST /public/registrations` while the gate is closed, so organizers can try the whole flow before opening. Only the hash of the key is stored, the link is shown once, and a new link replaces the old one |
| `POST /admin/emails/recipient-count` | `{ kind, audience, school? }`: how many people a campaign of that kind would reach. `kind` is required; see [Email](#email) |
| `POST /admin/emails/test` | `{ kind?, subject, body }`: send one copy to the signed-in admin, with the footer of that kind (`ANNOUNCEMENT` when omitted) |
| `POST /admin/emails/preview` | `{ kind?, subject, body }`: the draft rendered as the test copy would be, `{ subject, html, text }`. Sends nothing; the admin site shows `html` in a sandboxed frame |
| `POST /admin/emails`, `GET /admin/emails` | `{ kind, audience, school?, subject, body }` starts a campaign (202, sent in the background); 409 `CAMPAIGN_ALREADY_SENDING` if a campaign with the same kind, audience, school, subject and body is still queued or sending. `GET` lists campaigns, newest first, each with its `kind`, `status` (`QUEUED`, `SENDING`, `SENT`, `FAILED`), `recipientCount`, `sentCount` and `failedCount` |
| `GET /admin/emails/{id}/recipients?status=&page=&size=` | Who the campaign went to and what happened: the same page envelope as the lists above, `{ items: [{ email, firstName, lastName, status, sentAt }], total, page, size }`, in sending order. `status` is `PENDING`, `SENT` or `FAILED` and filters when given. Empty for a campaign sent before recipients were recorded (migration V9); 404 for an unknown campaign |
| `GET`, `POST /admin/admins`, `POST /admin/admins/{id}/invite`, `DELETE /admin/admins/{id}` | Accounts. Nobody sets a password for someone else: `POST` takes `name`, `email` and `role` (`ADMIN` or `VOLUNTEER`; required, there is no default) and emails the person a one-time link, valid for 7 days, to choose their own. Until they do, the account is `pending` and cannot sign in. `/invite` sends a pending account a new link, which replaces the old one. Both answer with `setPasswordUrl` so the link can be passed on by hand if the email does not arrive; it is never returned again. Links are `$ADMIN_BASE_URL/#/set-password?token=...`, stored only as a SHA-256 hash. You cannot delete yourself or the last `ADMIN`; volunteers do not count towards that |
| `GET /admin/events` | Both roles. Every check-in event with its count: `[{ id, name, startsAt, general, checkedIn }]`, the built-in general event first |
| `POST /admin/events`, `PATCH /admin/events/{id}` | `{ "name", "startsAt" }` (`startsAt` optional, ISO-8601 UTC). `PATCH` changes only the keys sent; `"startsAt": null` clears it. Names are unique |
| `DELETE /admin/events/{id}` | Deletes a workshop that has no check-ins; 204. 409 `EVENT_HAS_CHECK_INS` when it has any (the message says how many; undo them first), 400 for the general event, which can never be deleted |
| `GET /admin/events/{id}/export.csv` | That event's attendees: name, email, school, status, `checked_in_at`, `checked_in_by`. Logged, see [CSV exports](#csv-exports) |
| `GET /admin/check-in?eventId=&q=&page=&size=` | Both roles. `{ event, items, total, page, size, checkedInTotal, registrationTotal }`; `q` matches first name, last name or email. Not-yet-checked-in people first, then by last name |
| `POST`, `DELETE /admin/check-in/{registrationId}?eventId=` | Both roles. Check in (idempotent: a repeat keeps the first time and name) or undo; returns the item. `POST` answers 409 `NOT_ACCEPTED` unless the registration is `ACCEPTED` |
| `POST /admin/check-in/scan` | Both roles. Check in by scanned ticket, see below |

#### Check-in

Check-ins are recorded per event. One event, "General check-in", is built in (seeded by the migration, cannot be deleted) and is what "attendance" means everywhere else: `checkedInAt` on registrations, the `checkedIn` filter and count, and the `checked_in_at` CSV column. Workshops are extra events an admin adds. Every check-in endpoint takes an optional `eventId` and uses the general event without it; an unknown `eventId` is 404.

Items returned by the check-in endpoints carry only what someone at the door needs: `id`, `firstName`, `lastName`, `email`, `school`, `status`, `checkedInAt` and `checkedInBy` for the event asked about, and `generalCheckedIn`, so a workshop check-in can flag someone who skipped the front desk.

Only an `ACCEPTED` registration can be checked in, to the general event or to a workshop, by an admin or a volunteer. There is no override: `POST /admin/check-in/{registrationId}` answers 409 `NOT_ACCEPTED` ("This person has not been accepted, so they can't be checked in. An organizer has to accept them first.") and a scan reports `NOT_ACCEPTED` without recording anything. An organizer accepts the person first. Undo works whatever the status.

`POST /admin/check-in/scan` takes `{ "code", "eventId" }`, where `code` is whatever the scanner read: the bare ticket token or the whole ticket URL. It always answers 200 with `{ "result", "event", "item" }`:

| `result` | Meaning |
| --- | --- |
| `CHECKED_IN` | Checked in by this call |
| `ALREADY_CHECKED_IN` | Nothing changed; `item.checkedInAt` and `item.checkedInBy` say when and by whom |
| `NOT_ACCEPTED` | The ticket belongs to someone whose status is not `ACCEPTED`. `item` is returned, nobody was checked in, and there is no way to force it |
| `NOT_RECOGNISED` | No ticket matches; `item` is null |

#### Acceptances

Accepting someone sends nothing. A registration that becomes `ACCEPTED` gets `acceptedAt` and joins the bucket of people who are accepted and not yet told (`acceptanceNotifiedAt` is null). Organizers accept gradually, watch the host-school share, and then send every acceptance email in one go with `POST /admin/acceptances/send`.

- Moving someone out of `ACCEPTED` clears both timestamps. Before they were told that simply takes them out of the bucket. After they were told it is still allowed (their ticket stops working and nothing is emailed); the admin site warns first. Accepting them again puts them back in the bucket, so they are told again.
- The send runs in the background on the campaign executor: one recipient at a time, `app.email.campaign-delay` apart, behind a campaign that is already sending. Each person is marked told only once the provider has taken their email; a failed recipient is counted and stays in the bucket, so running the send again retries exactly the failures. Everyone is re-read just before their turn, so someone moved out of `ACCEPTED`, or told individually, after the run started is skipped.
- Only one run at a time: a second `POST` while one is going is refused with 409, and the one-person action cannot mail someone the run is mailing at that moment (it reads the registration again once it holds that person, so someone the run has just told gets a resend, not a second acceptance).
- Each acceptance email carries the Resend `Idempotency-Key` `acceptance-<registration id>-<acceptedAt, epoch seconds>`. If the email went out but could not be recorded (a crash between the two), the next attempt is recognised by the provider and no second copy is delivered. The resend an admin asks for carries no key, so it always goes out.
- On shutdown a run stops after the email in hand; the people it had not reached are still waiting.
- Progress (`send`) is `{ state, queued, sent, failed, skipped, startedAt, finishedAt, startedBy }` with `state` `SENDING` or `IDLE`; while idle it describes the last run. It is kept in memory, so it is empty after a restart. Who was told is in the database: a run cut short by a restart loses only its counters, and the people it had not reached are still waiting.
- The acceptance email goes out even to someone who unsubscribed: unsubscribing only stops announcements (see [Email](#email)).
- Rows that were already `ACCEPTED` when migration V6 ran had been emailed under the old behaviour, so the migration marks them told (with the migration time; their `acceptedAt` is unknown and stays null).

`GET /admin/acceptances/summary`:

```json
{
  "totals": { "registrations": 120, "accepted": 50, "acceptedNotified": 30, "acceptedWaiting": 20, "pending": 60, "waitlisted": 6, "rejected": 4, "acceptanceRate": 0.4166 },
  "hostSchool": { "name": "Georgia State University", "target": 0.70 },
  "shares": {
    "accepted": { "total": 50, "host": 33, "other": 17, "share": 0.66, "met": false, "moreHostNeeded": 7, "fewerOthersNeeded": 3 },
    "registrations": { "...": "the same fields over every registration" },
    "pending": { "...": "over PENDING registrations: who can still be accepted" },
    "checkedIn": { "...": "over everyone with a general check-in" }
  },
  "acceptedBySchool": [ { "school": "Georgia State University", "count": 30, "host": true } ],
  "ageReview": { "minimumAge": 18, "total": 3, "accepted": 1 },
  "send": { "state": "IDLE", "queued": 30, "sent": 30, "failed": 0, "skipped": 0, "startedAt": "...", "finishedAt": "...", "startedBy": "admin@peachhacks.com" }
}
```

`host` counts registrations whose school name equals or starts with `HOST_SCHOOL_NAME` (trimmed, case-insensitive). `met` is `host / total >= HOST_SCHOOL_TARGET`, decided in exact decimal arithmetic, so 7 of 10 meets 70%. When it is not met, `moreHostNeeded` is how many more host-school people the group needs with nobody removed (null when no number is enough, which only happens with a target of 1), and `fewerOthersNeeded` is how many others would have to leave instead. An empty group has `share: null` and `met: true`, with nothing needed. `acceptanceRate` is `accepted / registrations`, null with no registrations. `acceptedBySchool` is the accepted group by school, most first.

The requirement is about who attends; `shares.accepted` is what organizers control before the event and `shares.checkedIn` is the real number during it.

#### Age review

The eligibility rule is: students of the host school may attend at any age, students of any other school must be at least `NON_HOST_MINIMUM_AGE` (18). The registration form does not enforce it, because a stated age can simply be changed; the API flags it for organizers instead. A registration has `ageReview: true` when its age is under the minimum and its school is not the host school, by the same host-school match as above (so a 17-year-old at `Georgia State University Perimeter College` is not flagged).

- The flag is on the registration list items and detail and on the bucket items, `ageReview=true|false` filters the list and the CSV export, and the export's last column is `age_review`.
- `ageReview` in the summary is `{ minimumAge, total, accepted }`: every flagged registration, and how many of them are `ACCEPTED` now. Those are the ones to look at.
- Nothing is blocked. A flagged registration can be accepted on its own or in bulk; the bulk response counts them in `acceptedAgeReview`.
- It is computed from the stored age and school on every call, so there is nothing to migrate. The public API never returns it, and the registrant is not told.

#### CSV exports

Each of the three CSV exports writes one INFO line when it runs: which export, how many rows, the filters that were set and the admin's email, in the same style as the resume book's. Nothing from the rows is logged.

```
Registrations CSV of 212 rows (school=Georgia State University, status=ACCEPTED) exported by admin@peachhacks.com
```

#### Tickets

Every registration has a random `ticket_token` that carries no personal data. The QR code encodes `$WEB_BASE_URL/ticket?t=<token>`, so a phone camera opens the hacker's ticket page on the public site and the admin scanner reads the same code. A ticket only exists while the registration is `ACCEPTED`: the public ticket endpoints answer 404 for every other status exactly as they do for an unknown token.

#### Resumes

A hacker may attach one resume when registering. The registration body stays JSON:

```json
{ "...": "...", "resume": { "fileName": "ada-lovelace.pdf", "contentBase64": "JVBERi0xLjcK..." } }
```

- `resume` is optional (`null` or absent for none). The file must be a PDF of at most 2 MB once decoded, and is recognised by its first bytes (`%PDF-`), not by its name. Anything else is a 400 `VALIDATION_ERROR` with `fieldErrors.resume`, and no registration is created. It is checked with the rest of the validation: after the gate and the honeypot, before the duplicate-email check.
- There is no separate consent flag. The form says at the upload that sponsors receive the resume, so uploading one is the choice; an accepted hacker's resume goes into the resume book, and removing the file takes it out.
- A 2 MB file is about 2.8 MB of base64. A registration body over 3 MB is refused with the same 400: before anything is read when `Content-Length` says so, and otherwise as soon as that many bytes have been read (`BodyLimitFilter`, see [Limits](#limits)), and `server.tomcat.max-swallow-size` is raised to 16 MB so that the refused upload is drained and the client receives the error rather than a reset connection. Tomcat applies no size limit of its own to JSON bodies and Jackson's default limit on one string (20 million characters) is above the 2.8 MB needed, so nothing else has to be configured. A proxy in front of the service must allow 3 MB request bodies.
- The stored file name is the client's with any directory, control and invisible characters and `<>:"|?*` removed, cut to 120 characters, always ending in `.pdf`.
- Files live in Postgres, in `registration_resumes` (one row per registration, removed with it by the foreign key if the row is ever deleted in the database); there is no object storage. Registration queries never read that table's `content` column: the JPA entity does not map it, and only the download and the resume book select it, one file at a time. Neither file contents nor base64 are logged; the request record's `toString()` prints only the length.

Organizers (admins) can download any resume, for running the event. Sponsors only ever get the resume book, `GET /admin/resumes/export.zip`: a ZIP, streamed as it is built, with one PDF per registrant who uploaded a resume **and** is `ACCEPTED`, named `LastName_FirstName_<first 8 characters of the registration id>.pdf` (ASCII only, accents folded), plus `index.csv` with `first_name, last_name, email, school_email, school, level_of_study, graduation_year, graduation_month, major, linkedin_url, github_url, file_name`. `checkedIn=true` narrows it to people with a general check-in. The line under the upload on the public form lists these fields; keep the two in step. Each export and each resume removal is logged with the admin's email.

Storage: resumes are stored uncompressed, so the table grows by the size of each file. The worst case is 2 MB per registrant (about 1 GB for 500 registrants who all upload the maximum); typical resumes are 100 to 300 KB, about 50 to 150 MB for 500. Check that against the database plan before the form opens; lowering `ResumeUpload.MAX_BYTES` (and the matching limit in `web/src/forms/validation.js`) is the lever.

CSV cells that a spreadsheet would treat as a formula (starting with `=`, `+`, `-` or `@`) are prefixed with a single quote.

### Email

Confirmation emails go out to the personal address after a new pre-registration and after a registration; while the school email is unconfirmed they include a sentence pointing to the school inbox, where the separate confirmation link is sent (see [School email confirmation](#school-email-confirmation)). A newly added admin or volunteer gets an email that says which kind of account it is, with a link to choose their own password; "Forgot password?" sends a reset link.

The acceptance email is a "You're in" message that sends the hacker to the hacker platform first (its main button), then shows the QR code itself with a link to the ticket page, embedded in the message (`cid:` image) and listed as a PNG attachment, because many mail clients block images loaded from a server. It is not sent when a registration becomes `ACCEPTED`: it goes out when an admin sends the acceptance emails, or to one person through `POST /admin/registrations/{id}/ticket-email`, which also sends it again afterwards (see [Acceptances](#acceptances)). With Google Wallet configured it also carries an "Add to Google Wallet" link.

#### Essential emails and announcements

Unsubscribing means "no announcements". It never stops the emails a person needs.

- **Essential emails** are always sent and carry no unsubscribe link and no `List-Unsubscribe` header: the pre-registration confirmation, the registration confirmation, the "You're already registered" notice (see [Repeated sign-ups](#repeated-sign-ups)), the school email confirmation link, the acceptance and ticket email (the send-all run, the one-person send and the resend) the invite to a new admin or volunteer and the password reset link. Each ends with one line saying why the person is receiving it.
- **Campaigns** written by organizers have a `kind`, required on `POST /admin/emails` and `POST /admin/emails/recipient-count`:

| `kind` | For | Audiences | Unsubscribed people | Footer |
| --- | --- | --- | --- | --- |
| `EVENT_UPDATE` | Logistics for people who are coming: venue, times, what to bring | `ACCEPTED` only | Included | "You are receiving this because you registered for PeachHacks." No unsubscribe link or header |
| `ANNOUNCEMENT` | Everything else, including "registration is open" | `PRE_REGISTRANTS`, `PRE_REGISTRANTS_NOT_REGISTERED`, `REGISTRANTS`, `ACCEPTED` | Skipped | The reason line, an unsubscribe link (`$WEB_BASE_URL/unsubscribe.html?token=...`) and the `List-Unsubscribe` header |

- `ACCEPTED` is registrations whose status is `ACCEPTED` and whose acceptance email has been sent (`acceptanceNotifiedAt` is set), so nobody learns they are accepted from a logistics email.
- An `EVENT_UPDATE` to any other audience is refused with 400 `VALIDATION_ERROR` and `fieldErrors.audience`. It cannot be unsubscribed from, so it goes only to people who are coming: nobody who is pending, waitlisted, rejected or not yet told ever receives one.
- Any audience can be narrowed to one `school`. Because of the unsubscribed flag, the same audience can count differently for the two kinds.
- The `unsubscribed` flag, `POST /public/unsubscribe` and the admin's unsubscribed indicators are unchanged. Unsubscribing covers both the pre-registration and the registration with that email.
- Campaigns sent before `kind` existed are `ANNOUNCEMENT` (migration V7), which is how they behaved.

The body is plain text: blank lines separate paragraphs, `{{firstName}}` and `{{lastName}}` are filled in per recipient, and bare `http://` and `https://` URLs become links. Everything is escaped; nothing else is markup. Links are made from the organizer's text before the names are filled in, so a name can never become a link.

#### Design

Every email is rendered by `EmailComposer` in one layout: a navy header with the logo, the message on a white card, and a navy footer with "PeachHacks · ColorStack at Georgia State University", the reason line, the unsubscribe link where it belongs and a link to the site. It is a table layout with inline styles, 600px wide, with a hidden preview line, a dark-mode variant for clients that honour `prefers-color-scheme`, and Open Sans loaded by a font link with Arial as the fallback. There are no tracking pixels and links are never rewritten.

- The logo is `$WEB_BASE_URL/assets/email-logo.png` (served by `web/`). When `WEB_BASE_URL` is a local address the live site's copy is used instead, so the image loads in a real inbox during development. With images blocked the alt text "PeachHacks" shows on the navy band.
- System emails are built from structured content (`EmailComposer.Content`): a heading, paragraphs, an optional primary and secondary action and, for the ticket, the inline QR image. An action is a button drawn for Outlook on Windows (VML) as well as for other clients, with the plain URL under it. The actions are "Confirm your school email", "View your ticket", "Add to Google Wallet" (secondary) "Set your password" in the two invite emails and "Choose a new password" in the reset email.
- Every message has a complete plain-text alternative carrying the same links.
- The school email confirmation goes to an inbox that has never heard from PeachHacks, so it opens by saying who is writing and which sign-up it belongs to ("You, or someone using this address, signed up for PeachHacks, a student hackathon run by ColorStack at Georgia State University, with the personal email j***@gmail.com"), uses the person's first name, keeps the link on the site's own domain, shows the URL in full, avoids urgent wording and says it can be ignored. The personal address is always masked.

#### Sending

Campaigns are sent one recipient at a time, about 600 ms apart (`app.email.campaign-delay`), one campaign at a time.

- When a campaign is started its recipients are written to `campaign_recipients` (migration V9), one row per person, `PENDING`. The send works through the pending rows and marks each `SENT` (with `sentAt`) or `FAILED` (with the provider's error, kept in the table) as it goes; `sentCount` and `failedCount` on the campaign are counted from those rows. A failed recipient does not stop the campaign, and neither does a failure to record an outcome.
- A campaign left `QUEUED` or `SENDING` by a restart or redeploy is resumed at the next startup and goes only to the people still `PENDING`. Nobody marked `SENT` is mailed again, and a `FAILED` recipient is not retried. On shutdown the send stops after the email in hand. A campaign from before V9 has no rows to resume from and is marked `FAILED` with the counts it had reached.
- Every campaign email carries the Resend `Idempotency-Key` `campaign-<campaign id>-<recipient row>`, so the one window that is left (the email went out but the mark did not, or the old and the new instance overlapping during a deploy) does not deliver a second copy.
- A campaign finishes `SENT` when at least one person was reached and `FAILED` when nobody was; `GET /admin/emails/{id}/recipients?status=FAILED` lists who was missed.

Every message to Resend, of any kind, is retried on 429, on a 5xx answer and on a timeout or connection error: up to 4 attempts, waiting 1, 2 and 4 seconds, all with the same `Idempotency-Key`, so a retry after a timeout cannot become a second email. Other answers (a 422, say) are final. A send that fails for good is logged at WARN with the recipient, the subject and the provider's status and message; campaigns, the acceptance send and the background system emails add their own line with the campaign or registration it belonged to. On shutdown the mail queues are given 20 seconds to finish what is already queued.

## Google Wallet

Optional. With `GOOGLE_WALLET_ISSUER_ID`, `GOOGLE_WALLET_SERVICE_ACCOUNT_EMAIL` and `GOOGLE_WALLET_PRIVATE_KEY` all set, accepted hackers get an "Add to Google Wallet" link (`googleWalletUrl` on the public ticket and in the acceptance email). With any of them missing the feature is off and nothing mentions it. A key that cannot be parsed logs one error at startup and leaves the feature off. There is no Apple Wallet support.

The link is `https://pay.google.com/gp/v/save/<jwt>`: a JWT signed with the service account key (RS256) that carries the hacker's event ticket object, so the backend never calls Google. The object holds only what is personal: a stable id (`<issuerId>.ticket_<registration id>`, so saving twice does not make two passes), the holder's name, their school, and a QR barcode whose value is the same ticket URL the PNG encodes. Everything shared (event name, dates, venue, logo, colour) comes from the pass class, which the JWT references by id and does not define.

One-time setup in Google's consoles:

1. Create a Google Wallet API issuer account in the [Google Pay & Wallet Console](https://pay.google.com/business/console) and note the issuer ID.
2. In the console's Google Wallet API section create a class with pass type **Event ticket** and id `peachhacks_2027` (or set `GOOGLE_WALLET_CLASS_ID` to the suffix you chose). It must exist before anyone opens a save link, otherwise Google rejects the link.
3. In a Google Cloud project, enable the Google Wallet API, create a service account and create a JSON key for it. `client_email` from that file is `GOOGLE_WALLET_SERVICE_ACCOUNT_EMAIL` and `private_key` is `GOOGLE_WALLET_PRIVATE_KEY`.
4. Back in the Google Pay & Wallet Console, under Users, invite the service account's email with the Developer access level.
5. Until Google grants the issuer publishing access the account is in demo mode: only Google accounts that are admins or developers of the issuer account, or that were added as test accounts in the console, can save the pass, and it is marked as a test. Request publishing access from the console's Google Wallet API page once the business profile is complete.

## PeachBot

Optional. PeachBot gives the Hacker role in the PeachHacks Discord server to people whose registration is `ACCEPTED`, and to nobody else. It is not a separately hosted bot: Discord calls this backend over HTTPS for every button press (`POST /discord/interactions`), and the backend calls Discord's REST API with the bot token. With any of `DISCORD_BOT_TOKEN`, `DISCORD_PUBLIC_KEY`, `DISCORD_APPLICATION_ID`, `DISCORD_GUILD_ID`, `DISCORD_HACKER_ROLE_ID` or `DISCORD_VERIFICATION_CHANNEL_ID` missing it is off and the endpoint answers 404.

A Discord account is tied to a registration in one place only, and the Verify button in Discord reads that record (`discord_links`: one Discord account per registration, one registration per Discord account):

- **Connect Discord on the hacker platform** makes the link. The hacker is signed in, approves PeachBot in Discord (OAuth2, scopes `identify` and `guilds.join`), and the backend adds them to the server with the role. Needs `DISCORD_CLIENT_SECRET`. The account is matched by its Discord ID, not its username, so renaming the account changes nothing.
- **The Verify button in Discord** looks the presser's account up. Connected to an accepted registration: the role is given (again, if they left the server and came back). Connected to a registration that is not accepted: told so. Not connected: an answer only they can see, with a button that opens the platform at its Discord section (`<PLATFORM_BASE_URL>/#/discord`), where signing in and pressing Connect Discord finishes the job.

An admin writes the text above the Verify button in Settings in the admin site and publishes it (`POST /admin/discord/verification-message` with `{ "message" }`, at most 900 characters; without a body the saved text is published as it is). The first publish posts the message; later ones edit it in place, and a message someone deleted in Discord is posted again. `GET /admin/discord` returns `{ configured, verified, messagePostedAt, message }`.

Rules:

- Only `ACCEPTED` registrations. When an admin moves a registration out of `ACCEPTED` (one at a time or in bulk) the role is removed right away; accepting it again gives the role back.
- Connecting a second Discord account to the same registration moves the link: the first account loses the role.
- Interactions are checked against the application's Ed25519 public key; anything unsigned is answered 401, which is also what Discord tests when the endpoint URL is saved.

### Welcomes

With `DISCORD_WELCOME_CHANNEL_ID` set, PeachBot posts a welcome in that channel for everyone who joins the server, accepted or not: a mention, a pointer to the verification channel, and a picture drawn for them (`WelcomeCard`: the night sky and skyline, their Discord avatar and name). Each person is welcomed once (`discord_welcomes`); leaving and rejoining does not repeat it. If the picture cannot be drawn the welcome goes out as text.

Discord only reports joins over its Gateway websocket, so this is the one part of PeachBot that holds a standing connection (`DiscordGateway`). It asks for the Server Members intent and nothing else, heartbeats, and reconnects with a growing wait when the connection drops; a join during a gap is missed. **Server Members Intent** must be switched on under Bot > Privileged Gateway Intents in the Developer Portal, otherwise Discord closes the connection with code 4014 and the log says so. The bot needs to see the welcome channel, send messages and attach files there. If more than one instance runs, each holds its own connection, and the table still keeps anyone from being welcomed twice.

One-time setup:

1. In the [Discord Developer Portal](https://discord.com/developers/applications) create an application named PeachBot. From General Information copy the **Application ID** and **Public Key**. Under Bot, reset and copy the **token**. Under OAuth2 copy the **client secret** and add `<PLATFORM_BASE_URL>/` (for production `https://platform.peachhacks.com/`, with the trailing slash) as a redirect.
2. Invite the bot to the server with the `bot` scope and the **Manage Roles** permission. For "Connect Discord" it also needs **Create Invite** (Discord requires it to add members).
3. In the server, create the Hacker role and drag PeachBot's own role above it; a bot can only give roles below its own. Make the verification channel visible to everyone and the hacker channels visible to the Hacker role only. PeachBot needs to be able to send messages in the verification channel.
4. With Developer Mode on in Discord, copy the server ID, the Hacker role ID and the verification channel ID.
5. Set the seven `DISCORD_*` variables on the backend and redeploy. The log says `PeachBot: on`.
6. Back in the Developer Portal, set **Interactions Endpoint URL** to `https://api.peachhacks.com/discord/interactions`. Discord checks it on save, so the backend must already be running with the public key.
7. In the admin site, Settings > PeachBot, write and post the verification message.

## Hacker platform

The API behind `platform/` (platform.peachhacks.com), for accepted hackers only. There is no sign-up: the account is the registration. A hacker signs in with the email they applied with and a password they choose through an emailed link (`POST /platform/auth/password-link`, valid for an hour, one use), or with a Google account whose address is the one they applied with (or their school address, once confirmed). A session token lasts 30 days and stops working the moment the registration leaves `ACCEPTED`. Passwords are stored as bcrypt hashes and session tokens as SHA-256 hashes, like the admin ones.

| Endpoint | Purpose |
| --- | --- |
| `GET /platform/config` | Public. `{ googleSignIn, discordClientId, discordRedirectUri, maxTeamSize }`; `googleSignIn` is false and `discordClientId` null when that sign-in is not configured |
| `POST /platform/auth/login` | `{ email, password }` to `{ token, expiresAt }`. 401 `INVALID_CREDENTIALS`; failed attempts are throttled per email like admin sign-ins |
| `POST /platform/auth/password-link` | `{ email }`. Always 204; mails a link only to an accepted registration, at most one every 2 minutes |
| `POST /platform/auth/set-password` | `{ token, password }` (10 to 72 characters) to a session. Signs out the account's other sessions |
| `GET /platform/auth/google/start` | A page navigation, not an API call: redirects the browser to Google |
| `GET /platform/auth/google/callback` | Where Google sends the browser back. Redirects to the platform with `?google=<handoff>`, or `?google_error=cancelled`, `expired` or `failed` |
| `POST /platform/auth/google/claim` | `{ handoff }` to a session; the handoff works once, for two minutes. 403 `NOT_ACCEPTED` (naming the address) when no accepted registration has that Google account's address |
| `POST /platform/auth/logout` | Ends the session |
| `GET`, `PATCH /platform/me` | The hacker's own record: name, school, profile (`bio`, `githubUrl`, `linkedinUrl`, `lookingForTeam`, `listed`), Discord username, ticket (token, URL, Google Wallet link, checked in) and team |
| `POST /platform/discord` | `{ code }` from Discord's OAuth2 redirect; see [PeachBot](#peachbot) |
| `GET /platform/hackers` | The directory: accepted hackers who have signed in to the platform and left `listed` on |
| `GET`, `POST /platform/teams` | Every team with its members and open spots, the caller's own team first (with its join requests); create a team |
| `PATCH /platform/teams/mine`, `POST /platform/teams/mine/leave`, `DELETE /platform/teams/mine/members/{id}` | Rename, leave, and (team lead only) remove a member |
| `POST`, `DELETE /platform/teams/{id}/requests` | Ask to join a team (optional `{ message }`), withdraw the request |
| `POST /platform/teams/mine/requests/{id}/accept`, `.../decline` | Team lead only |

Everything except `config` and `auth` needs `Authorization: Bearer <token>`.

- A hacker is on at most one team, and a team has at most `MAX_TEAM_SIZE` members. 409 `ALREADY_ON_TEAM`, `TEAM_FULL` or `TOO_MANY_REQUESTS` (5 open requests per hacker) say why a team action was refused.
- The hacker who creates a team leads it. When the lead leaves, the member who joined first takes over; when the last member leaves, the team is removed.
- Joining a team withdraws the hacker's other requests and switches off their "looking for a team" flag.
- The directory never includes someone who has not signed in to the platform, and `listed: false` takes a hacker out of it again. Teammates always see each other.

Google sign-in is the authorization-code flow run by this backend, so the consent screen shows the PeachHacks app name and no session token ever appears in an address. The state and nonce of a sign-in in progress, and the one-time handoff that carries its result back to the platform page, live in `google_sign_ins`. Setup: in the Google Cloud console create an OAuth client of type Web application, add `https://api.peachhacks.com/platform/auth/google/callback` (and `http://localhost:8080/platform/auth/google/callback` for local work) as Authorized redirect URIs, and set its client ID and secret as `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.

## Deploy to Railway

1. Create a service from this repo and set **Root Directory** to `/backend`.
2. Set the config-as-code path to `/backend/railway.toml` (Railway resolves it from the repo root). It builds with the `Dockerfile`, health-checks `/actuator/health` and only redeploys for commits that touch `backend/**` (`watchPatterns`), so a change to the web or admin site does not restart the API in the middle of a send.
3. Set the datasource variables (below), `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` / `ADMIN_BOOTSTRAP_NAME` for the first admin, `RESEND_API_KEY` (required: the service does not start without it), and optionally `EMAIL_FROM`, `WEB_BASE_URL`, `PLATFORM_BASE_URL`, `GOOGLE_CLIENT_ID`, the `DISCORD_*` variables, `CORS_ALLOWED_ORIGINS`, `HOST_SCHOOL_NAME`, `HOST_SCHOOL_TARGET`, `NON_HOST_MINIMUM_AGE` and the `GOOGLE_WALLET_*` variables. `PORT` is injected by Railway.
4. Point `api.peachhacks.com` at the service under Settings > Networking > Custom Domain.

Run a single instance: rate limiting, the sign-in throttle and the acceptance send (its progress and the guard against two runs at once) are kept in memory, and a campaign is resumed by whichever instance starts.

Registrations with a resume are JSON bodies of up to 3 MB, and each one is held in memory a few times over while it is parsed and decoded (roughly 10 MB for a moment), so leave the instance some headroom.

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
