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

Without `RESEND_API_KEY` no email leaves the machine: every message (confirmations, test emails, campaigns) is written to the application log instead.

## Tests

```bash
./mvnw test
```

The integration tests run the real application against Testcontainers (`postgres:16-alpine`) and are skipped automatically when Docker is not available. They cover pre-registration, the registration gate, validation, admin authentication and roles, events and check-in, tickets and scanning, the acceptance bucket (accepting sends nothing, the send-all run, a provider failure, the host-school share, the age review flag, bulk status changes, and the V6 migration on rows accepted before it), the acceptance email, Google Wallet links, CSV export, campaign kinds and audiences (who an event update and an announcement reach, their footers, and the V7 migration), resume upload, download, removal and the sponsor resume book, and school email confirmation (the link, its expiry, the resend limit, carry-over from pre-registration to registration, and the admin filters and resend).

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
| `WEB_BASE_URL` | Public site URL used for links in emails (unsubscribe, ticket, school email confirmation) and for the ticket URL inside every QR code (default `http://localhost:5173`, `https://www.peachhacks.com` in `prod`). Changing it changes what newly rendered QR codes contain; codes already sent keep working because the scanner only reads the token |
| `ADMIN_BASE_URL` | Admin site URL used for the set-password links emailed to admins and volunteers (default `http://localhost:5174`, `https://admin.peachhacks.com` in `prod`) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins (default `http://localhost:5173`, `http://localhost:5174`, `https://www.peachhacks.com`, `https://peachhacks.com`, `https://admin.peachhacks.com`) |
| `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | Connection pool size (5 in `prod`) |
| `GOOGLE_WALLET_ISSUER_ID` | Google Wallet issuer ID. Optional; see [Google Wallet](#google-wallet) |
| `GOOGLE_WALLET_SERVICE_ACCOUNT_EMAIL` | Email of the service account whose key signs the passes. Optional |
| `GOOGLE_WALLET_PRIVATE_KEY` | That service account's private key in PEM form (`-----BEGIN PRIVATE KEY-----`). Real line breaks or literal `\n` sequences both work. Optional |
| `GOOGLE_WALLET_CLASS_ID` | Suffix of the event ticket class created in the Google Pay & Wallet Console (default `peachhacks_2027`, so the class is `<issuerId>.peachhacks_2027`) |
| `HOST_SCHOOL_NAME` | The host school (default `Georgia State University`). A registration counts towards the host-school share when its school name equals or starts with this, case-insensitive, so `Georgia State University Perimeter College` counts too |
| `HOST_SCHOOL_TARGET` | The share of accepted hackers that must come from the host school, as a fraction from 0 to 1 (default `0.70`). A value outside that range stops the application at startup |
| `NON_HOST_MINIMUM_AGE` | The minimum age for students of other schools (default `18`); host-school students are eligible at any age. A registration under it from a school that is not the host school is flagged `ageReview` for organizers and is never refused. See [Age review](#age-review) |
| `PORT` | HTTP port (default 8080) |

The URL must be in JDBC form (`jdbc:postgresql://...`), not the `postgres://user:pass@...` form providers display; put the user and password in their own variables.

## API

JSON everywhere, timestamps in ISO-8601 UTC. Errors always look like this (`fieldErrors` only on validation errors):

```json
{ "code": "VALIDATION_ERROR", "message": "Please check the highlighted fields.", "fieldErrors": { "email": "Must be a valid email" } }
```

Codes: `VALIDATION_ERROR` (400), `INVALID_CREDENTIALS` and `UNAUTHORIZED` (401), `REGISTRATION_CLOSED` and `FORBIDDEN` (403), `NOT_FOUND` (404), `ALREADY_REGISTERED`, `ACCEPTANCE_SEND_RUNNING` and `EVENT_HAS_CHECK_INS` (409), `RATE_LIMITED` (429), `EMAIL_FAILED` (502, the test email and the one-person acceptance email), `INTERNAL_ERROR` (500).

### Public

| Endpoint | Purpose |
| --- | --- |
| `GET /public/status` | `{ "registrationOpen": false }` |
| `POST /public/pre-registrations` | Pre-register. `schoolEmail` is required. Idempotent on `email` (case-insensitive): a repeat updates the row and returns the same `id` with 201 |
| `POST /public/registrations` | Full MLH registration. `schoolEmail` is required. Optional `resume` and `resumeOptIn`, see [Resumes](#resumes). 403 while the gate is closed, 409 if the `email` is already registered |
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

Both forms accept a hidden `website` honeypot field: when it is filled in, the request gets a normal 201 and nothing is stored. Public POSTs are limited to 60 per minute per client address, login to 10 per minute and the ticket endpoints to 300 per minute (a whole door queue can share one venue address); all in memory, per instance, see `app.rate-limit.*`.

### Admin

Everything under `/admin` except login and the password-link routes needs `Authorization: Bearer <token>`. Tokens are random, opaque, valid for 12 hours and stored only as a SHA-256 hash; passwords are stored as BCrypt hashes. There are no cookies or server sessions.

#### Roles

Every account is an `ADMIN` (full access) or a `VOLUNTEER` (check-in only); the role is returned with the account by login, `/admin/auth/me` and the accounts list. A volunteer may call `/admin/auth/*`, `/admin/check-in` and everything under it, and `GET /admin/events`. Every other `/admin/**` route answers 403 `FORBIDDEN` for a volunteer. The rule lives in `SecurityConfig`, so a route added later is admin-only unless it is listed there.

| Endpoint | Purpose |
| --- | --- |
| `POST /admin/auth/login`, `POST /admin/auth/logout`, `GET /admin/auth/me` | Sign in, invalidate the token, current account (`{ id, email, name, role }`) |
| `POST /admin/auth/forgot-password` | Public. `{ email }`; always 204. If the account exists it is emailed a one-time link, valid for 1 hour |
| `POST /admin/auth/set-password/check`, `POST /admin/auth/set-password` | Public. `{ token }` answers `{ email, name, invite }`; `{ token, password }` saves the password, uses up the link and ends every session of that account. A bad, used or expired link is 400 `INVALID_PASSWORD_LINK` |
| `POST /admin/auth/change-password` | `{ currentPassword, newPassword }` for the signed-in account; its other sessions are ended |
| `GET /admin/stats` | Totals, per school, per day, per level of study and per status; `preRegistrations.schoolEmailConfirmed` and `registrations.schoolEmailConfirmed` (how many have a confirmed school email); `registrations.checkedIn` (general check-in), `registrations.withResume` and `registrations.resumeOptIn` (resumes uploaded, and how many of those may be shared with sponsors) and `events`, a list of `{ eventId, name, checkedIn }` |
| `GET /admin/pre-registrations?page=&size=&q=&school=&schoolEmailConfirmed=` | Paged list, newest first (`size` is capped at 200). Items carry `schoolEmailConfirmed` and `schoolEmailConfirmedAt` (null until confirmed); `schoolEmailConfirmed=true` or `false` filters on it |
| `GET /admin/pre-registrations/export.csv` | CSV export with the same filters, `school_email_confirmed` appended last. Pre-registrations cannot be deleted through the API |
| `POST /admin/pre-registrations/{id}/school-email/resend` | Mail a new confirmation link to the school address, ignoring the 10-minute limit; 204, or 400 if it is already confirmed |
| `GET /admin/registrations?page=&size=&q=&school=&status=&checkedIn=&resume=&schoolEmailConfirmed=&ageReview=` | Paged summaries with `schoolEmail`, `schoolEmailConfirmed`, `schoolEmailConfirmedAt`, `acceptedAt`, `acceptanceNotifiedAt`, `checkedInAt` (general check-in), `hasResume`, `resumeOptIn` and `ageReview`. `q` matches the name, `email` or `schoolEmail`. `checkedIn=true` or `false` filters on the check-in; `resume=any` (uploaded one), `none`, or `opted-in` (uploaded one and agreed to share it) filters on the resume; `schoolEmailConfirmed=true` or `false` filters on the school email (`false` includes registrations that have none); `ageReview=true` or `false` filters on the [age review](#age-review) flag |
| `GET`, `PATCH /admin/registrations/{id}` | Detail, set `status` (`PENDING`, `ACCEPTED`, `WAITLISTED`, `REJECTED`). Registrations cannot be deleted through the API (`DELETE` answers 405). The detail adds `checkedInAt`, `checkedInBy`, `checkIns` (every event: `{ eventId, name, general, checkedInAt, checkedInBy }`) and, only while `ACCEPTED`, `ticketToken`, `ticketUrl` and `googleWalletUrl`; also `resume` (`null` or `{ fileName, size, uploadedAt }`, never the file itself) and `resumeOptIn`, and `schoolEmailConfirmed` with `schoolEmailConfirmedAt`, which the admin site turns into a warning on the status control, and `ageReview` (see [Age review](#age-review)). Neither an unconfirmed school email nor an age review blocks `ACCEPTED`. No status change sends an email; `acceptedAt` and `acceptanceNotifiedAt` say where an accepted registration stands, see [Acceptances](#acceptances) |
| `POST /admin/registrations/status` | Bulk status change: `{ "ids": ["..."], "status": "ACCEPTED" }`, 1 to 500 ids, same rules as the single `PATCH`. Returns `{ "changed", "unchanged", "notFound", "acceptedAgeReview" }`: `unchanged` already had the status, `notFound` no longer exist (they do not fail the call); repeated ids count once. `acceptedAgeReview` is how many of the registrations this call moved to `ACCEPTED` need an [age review](#age-review) (0 for any other status) |
| `POST /admin/registrations/{id}/ticket-email` | For someone already told, sends the ticket email again in the background. For someone accepted and still waiting, sends their acceptance email now and marks them told, so one person can be let in early; 502 `EMAIL_FAILED` if the provider does not take it (they stay waiting) and 409 `ACCEPTANCE_SEND_RUNNING` if the send-all run is mailing that person at that moment. 204 otherwise, 400 if the registration is not `ACCEPTED` |
| `GET /admin/acceptances/summary` | Counts, host-school share, age review counts and the state of the last send, recomputed on every call. See [Acceptances](#acceptances) |
| `GET /admin/acceptances/waiting` | The bucket: everyone `ACCEPTED` and not yet told, longest-waiting first, as `[{ id, firstName, lastName, email, school, host, ageReview, acceptedAt }]` |
| `POST /admin/acceptances/send` | Emails everyone in the bucket. 202 `{ "queued": 12, "send": { ... } }` when a run was queued, 200 with `"queued": 0` when nobody is waiting (nothing is sent), 409 `ACCEPTANCE_SEND_RUNNING` while a run is going |
| `GET /admin/acceptances/send` | Progress of the run, the same object as `send` in the summary |
| `POST /admin/registrations/{id}/school-email/resend` | Mail a new confirmation link to the school address, ignoring the 10-minute limit; 204, or 400 if it is already confirmed or the registration has no school email |
| `GET /admin/registrations/export.csv` | Every column plus, appended last, `checked_in_at` (general check-in), `has_resume`, `resume_opt_in`, `school_email` and `school_email_confirmed`; same filters. This is the check-in data MLH asks for |
| `GET /admin/registrations/{id}/resume` | The uploaded PDF as an attachment, whether or not the person opted in to sponsor sharing. 404 if there is none |
| `DELETE /admin/registrations/{id}/resume` | Deletes the file and the opt-in, for a removal request; 204, or 404 if there is none. The registration stays |
| `GET /admin/resumes/export.zip?checkedIn=` | The sponsor resume book, see [Resumes](#resumes) |
| `GET`, `PUT /admin/settings` | The registration gate, `{ "registrationOpen": true }` |
| `POST /admin/emails/recipient-count` | `{ kind, audience, school? }`: how many people a campaign of that kind would reach. `kind` is required; see [Email](#email) |
| `POST /admin/emails/test` | `{ kind?, subject, body }`: send one copy to the signed-in admin, with the footer of that kind (`ANNOUNCEMENT` when omitted) |
| `POST /admin/emails/preview` | `{ kind?, subject, body }`: the draft rendered as the test copy would be, `{ subject, html, text }`. Sends nothing; the admin site shows `html` in a sandboxed frame |
| `POST /admin/emails`, `GET /admin/emails` | `{ kind, audience, school?, subject, body }` starts a campaign (202, sent in the background); list campaigns, each with its `kind` |
| `GET`, `POST /admin/admins`, `POST /admin/admins/{id}/invite`, `DELETE /admin/admins/{id}` | Accounts. Nobody sets a password for someone else: `POST` takes `name`, `email` and an optional `role` (`ADMIN` by default, or `VOLUNTEER`) and emails the person a one-time link, valid for 7 days, to choose their own. Until they do, the account is `pending` and cannot sign in. `/invite` sends a pending account a new link, which replaces the old one. Both answer with `setPasswordUrl` so the link can be passed on by hand if the email does not arrive; it is never returned again. Links are `$ADMIN_BASE_URL/#/set-password?token=...`, stored only as a SHA-256 hash. You cannot delete yourself or the last `ADMIN`; volunteers do not count towards that |
| `GET /admin/events` | Both roles. Every check-in event with its count: `[{ id, name, startsAt, general, checkedIn }]`, the built-in general event first |
| `POST /admin/events`, `PATCH /admin/events/{id}` | `{ "name", "startsAt" }` (`startsAt` optional, ISO-8601 UTC). `PATCH` changes only the keys sent; `"startsAt": null` clears it. Names are unique |
| `DELETE /admin/events/{id}` | Deletes a workshop that has no check-ins; 204. 409 `EVENT_HAS_CHECK_INS` when it has any (the message says how many; undo them first), 400 for the general event, which can never be deleted |
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

#### Acceptances

Accepting someone sends nothing. A registration that becomes `ACCEPTED` gets `acceptedAt` and joins the bucket of people who are accepted and not yet told (`acceptanceNotifiedAt` is null). Organizers accept gradually, watch the host-school share, and then send every acceptance email in one go with `POST /admin/acceptances/send`.

- Moving someone out of `ACCEPTED` clears both timestamps. Before they were told that simply takes them out of the bucket. After they were told it is still allowed (their ticket stops working and nothing is emailed); the admin site warns first. Accepting them again puts them back in the bucket, so they are told again.
- The send runs in the background on the campaign executor: one recipient at a time, `app.email.campaign-delay` apart, behind a campaign that is already sending. Each person is marked told only once the provider has taken their email; a failed recipient is counted and stays in the bucket, so running the send again retries exactly the failures. Everyone is re-read just before their turn, so someone moved out of `ACCEPTED`, or told individually, after the run started is skipped.
- Only one run at a time: a second `POST` while one is going is refused with 409, and the one-person action cannot mail someone the run is mailing at that moment.
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
    "checkedIn": { "...": "over everyone with a general check-in, whatever their status" }
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

#### Tickets

Every registration has a random `ticket_token` that carries no personal data. The QR code encodes `$WEB_BASE_URL/ticket?t=<token>`, so a phone camera opens the hacker's ticket page on the public site and the admin scanner reads the same code. A ticket only exists while the registration is `ACCEPTED`: the public ticket endpoints answer 404 for every other status exactly as they do for an unknown token.

#### Resumes

A hacker may attach one resume when registering. The registration body stays JSON:

```json
{ "...": "...", "resume": { "fileName": "ada-lovelace.pdf", "contentBase64": "JVBERi0xLjcK..." }, "resumeOptIn": true }
```

- `resume` is optional (`null` or absent for none). The file must be a PDF of at most 2 MB once decoded, and is recognised by its first bytes (`%PDF-`), not by its name. Anything else is a 400 `VALIDATION_ERROR` with `fieldErrors.resume`, and no registration is created. It is checked with the rest of the validation: after the gate and the honeypot, before the duplicate-email check.
- `resumeOptIn` (default `false`) is the hacker's consent to pass the resume to sponsors. It is stored with the file, so `true` without a file is stored as `false`, and removing the file removes the consent.
- A 2 MB file is about 2.8 MB of base64. A registration whose `Content-Length` is over 3 MB is refused with the same 400 before the body is read (`RegistrationBodyLimitInterceptor`), and `server.tomcat.max-swallow-size` is raised to 16 MB so that the refused upload is drained and the client receives the error rather than a reset connection. Tomcat applies no size limit of its own to JSON bodies and Jackson's default limit on one string (20 million characters) is above the 2.8 MB needed, so nothing else has to be configured. A proxy in front of the service must allow 3 MB request bodies.
- The stored file name is the client's with any directory, control and invisible characters and `<>:"|?*` removed, cut to 120 characters, always ending in `.pdf`.
- Files live in Postgres, in `registration_resumes` (one row per registration, removed with it by the foreign key if the row is ever deleted in the database); there is no object storage. Registration queries never read that table's `content` column: the JPA entity does not map it, and only the download and the resume book select it, one file at a time. Neither file contents nor base64 are logged; the request record's `toString()` prints only the length.

Organizers (admins) can download any resume, for running the event. Sponsors only ever get the resume book, `GET /admin/resumes/export.zip`: a ZIP, streamed as it is built, with one PDF per registrant who uploaded a resume **and** opted in **and** is `ACCEPTED`, named `LastName_FirstName_<first 8 characters of the registration id>.pdf` (ASCII only, accents folded), plus `index.csv` with `first_name, last_name, email, school_email, school, level_of_study, major, linkedin_url, file_name`. `checkedIn=true` narrows it to people with a general check-in. The public form's consent text lists exactly these fields; keep the two in step. Each export and each resume removal is logged with the admin's email.

Storage: resumes are stored uncompressed, so the table grows by the size of each file. The worst case is 2 MB per registrant (about 1 GB for 500 registrants who all upload the maximum); typical resumes are 100 to 300 KB, about 50 to 150 MB for 500. Check that against the database plan before the form opens; lowering `ResumeUpload.MAX_BYTES` (and the matching limit in `web/src/forms/validation.js`) is the lever.

CSV cells that a spreadsheet would treat as a formula (starting with `=`, `+`, `-` or `@`) are prefixed with a single quote.

### Email

Confirmation emails go out to the personal address after a new pre-registration and after a registration; while the school email is unconfirmed they include a sentence pointing to the school inbox, where the separate confirmation link is sent (see [School email confirmation](#school-email-confirmation)). A newly added admin or volunteer gets an email that says which kind of account it is, with a link to choose their own password; "Forgot password?" sends a reset link.

The acceptance email is a "You're in" message with a link to the hacker's ticket page and the QR code itself, embedded in the message (`cid:` image) and listed as a PNG attachment, because many mail clients block images loaded from a server. It is not sent when a registration becomes `ACCEPTED`: it goes out when an admin sends the acceptance emails, or to one person through `POST /admin/registrations/{id}/ticket-email`, which also sends it again afterwards (see [Acceptances](#acceptances)). With Google Wallet configured it also carries an "Add to Google Wallet" link.

#### Essential emails and announcements

Unsubscribing means "no announcements". It never stops the emails a person needs.

- **Essential emails** are always sent and carry no unsubscribe link and no `List-Unsubscribe` header: the pre-registration confirmation, the registration confirmation, the school email confirmation link, the acceptance and ticket email (the send-all run, the one-person send and the resend) the invite to a new admin or volunteer and the password reset link. Each ends with one line saying why the person is receiving it.
- **Campaigns** written by organizers have a `kind`, required on `POST /admin/emails` and `POST /admin/emails/recipient-count`:

| `kind` | For | Audiences | Unsubscribed people | Footer |
| --- | --- | --- | --- | --- |
| `EVENT_UPDATE` | Logistics for people who are coming: venue, times, what to bring | `REGISTRANTS`, `ACCEPTED` | Included | "You are receiving this because you registered for PeachHacks." No unsubscribe link or header |
| `ANNOUNCEMENT` | Everything else, including "registration is open" | `PRE_REGISTRANTS`, `PRE_REGISTRANTS_NOT_REGISTERED`, `REGISTRANTS`, `ACCEPTED` | Skipped | The reason line, an unsubscribe link (`$WEB_BASE_URL/unsubscribe.html?token=...`) and the `List-Unsubscribe` header |

- `ACCEPTED` is registrations whose status is `ACCEPTED` and whose acceptance email has been sent (`acceptanceNotifiedAt` is set), so nobody learns they are accepted from a logistics email.
- An `EVENT_UPDATE` to a pre-registrant audience is refused with 400 `VALIDATION_ERROR` and `fieldErrors.audience`: people who only pre-registered can always opt out.
- Any audience can be narrowed to one `school`. Because of the unsubscribed flag, the same audience can count differently for the two kinds.
- The `unsubscribed` flag, `POST /public/unsubscribe` and the admin's unsubscribed indicators are unchanged. Unsubscribing covers both the pre-registration and the registration with that email.
- Campaigns sent before `kind` existed are `ANNOUNCEMENT` (migration V7), which is how they behaved.

The body is plain text: blank lines separate paragraphs, `{{firstName}}` and `{{lastName}}` are filled in per recipient, and bare `http://` and `https://` URLs become links. Everything is escaped; nothing else is markup.

#### Design

Every email is rendered by `EmailComposer` in one layout: a navy header with the logo, the message on a white card, and a navy footer with "PeachHacks · ColorStack at Georgia State University", the reason line, the unsubscribe link where it belongs and a link to the site. It is a table layout with inline styles, 600px wide, with a hidden preview line, a dark-mode variant for clients that honour `prefers-color-scheme`, and Open Sans loaded by a font link with Arial as the fallback. There are no tracking pixels and links are never rewritten.

- The logo is `$WEB_BASE_URL/assets/email-logo.png` (served by `web/`). When `WEB_BASE_URL` is a local address the live site's copy is used instead, so the image loads in a real inbox during development. With images blocked the alt text "PeachHacks" shows on the navy band.
- System emails are built from structured content (`EmailComposer.Content`): a heading, paragraphs, an optional primary and secondary action and, for the ticket, the inline QR image. An action is a button drawn for Outlook on Windows (VML) as well as for other clients, with the plain URL under it. The actions are "Confirm your school email", "View your ticket", "Add to Google Wallet" (secondary) "Set your password" in the two invite emails and "Choose a new password" in the reset email.
- Every message has a complete plain-text alternative carrying the same links.
- The school email confirmation goes to an inbox that has never heard from PeachHacks, so it opens by saying who is writing and which sign-up it belongs to ("You, or someone using this address, signed up for PeachHacks, a student hackathon run by ColorStack at Georgia State University, with the personal email j***@gmail.com"), uses the person's first name, keeps the link on the site's own domain, shows the URL in full, avoids urgent wording and says it can be ignored. The personal address is always masked.

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
3. Set the datasource variables (below), `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` / `ADMIN_BOOTSTRAP_NAME` for the first admin, `RESEND_API_KEY`, and optionally `EMAIL_FROM`, `WEB_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `HOST_SCHOOL_NAME`, `HOST_SCHOOL_TARGET`, `NON_HOST_MINIMUM_AGE` and the `GOOGLE_WALLET_*` variables. `PORT` is injected by Railway.
4. Point `api.peachhacks.com` at the service under Settings > Networking > Custom Domain.

Run a single instance: rate limiting, campaign sending and the acceptance send (its progress and the guard against two runs at once) are kept in memory.

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
