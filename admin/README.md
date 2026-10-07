# PeachHacks Admin

Organizer site for PeachHacks, served at `admin.peachhacks.com`. A Vite + React
single-page app that talks to the Spring Boot API in `../backend`.

Screens: sign in, overview (headline numbers, sign-ups over time, by-school
breakdowns, check-ins by event, resume counts, the host-school share),
pre-registrations, registrations (with a detail drawer, status changes for one
person or a selection, the ticket and the resume), acceptances (the host-school
share, the bucket of accepted people not yet told, and the button that emails
them all), check-in (scan a ticket QR code or
search by name, per event), email (event updates and announcements: composer,
test send, campaign history) and
settings (the registration gate, check-in events and accounts).

Accounts are admins (everything) or volunteers (the Check-in screen only). A
volunteer sees only Check-in in the navigation and is sent there from any other
address; the API enforces the same limit, and a `403 FORBIDDEN` shows a "you
don't have access" message instead of signing the user out.

## Run

Requires Node 22.

```sh
cd admin
npm install
npm run dev        # http://localhost:5174 (fixed port; fails if it is taken)
```

The dev server expects the API at `http://localhost:8080`. Start the backend
with its `local` profile and sign in with the local admin account that profile
bootstraps (see `backend/`).

Other scripts:

```sh
npm run build      # production bundle in dist/
npm run preview    # serve dist/ on http://localhost:5174
npm run lint       # ESLint
```

## Environment variables

Copy `.env.example` to `.env.local` to override anything. All are optional.

| Variable            | Purpose                                                              | Default                                                                  |
| ------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| `VITE_API_BASE_URL` | Base URL of the API                                                  | `http://localhost:8080` in dev, `https://api.peachhacks.com` in a build  |
| `VITE_WEB_BASE_URL` | Public site URL used in the "Registration is open" email template    | `https://www.peachhacks.com`                                             |
| `VITE_MOCK_API`     | Dev only. `1` serves fake in-memory data instead of calling the API  | off                                                                      |

`VITE_*` values are baked in at build time, so changing one means rebuilding.

### Mock mode (dev only)

To look at the screens without the backend running:

```sh
# PowerShell
$env:VITE_MOCK_API = "1"; npm run dev
# bash
VITE_MOCK_API=1 npm run dev
```

Any email and password sign in (use the password `wrong` to see the error
state). An email starting with `volunteer`, for example
`volunteer@peachhacks.local`, signs in as a check-in volunteer; anything else
is a full admin. Mock mode has no camera, so the Check-in screen's Scan tab shows
a text field instead: paste a ticket token (shown in an accepted registration's
detail drawer under Ticket) or a ticket URL to get each scan result. Data is generated in the browser, shaped like the real API, and resets
on reload. A peach banner marks every screen while it is on.

In mock mode about a third of the accepted registrations start in the acceptance
bucket, and "Send acceptance emails" works through it at roughly three people a
second so the progress can be watched; nothing fails.

Mock mode stores no files: "Download resume" saves a placeholder PDF and the resume
book is an empty ZIP.

Mock mode cannot be turned on in production: it is gated on Vite's
`import.meta.env.DEV`, which is `false` in `npm run build`, so the mock module
(`src/api/mock.js`) is dropped from the bundle regardless of `VITE_MOCK_API`.

## How it works

- **Auth**: the token from `POST /admin/auth/login` is kept in
  `sessionStorage` (cleared when the tab closes) and sent as
  `Authorization: Bearer <token>`. Any `401 UNAUTHORIZED` from the API clears it
  and returns to the sign-in screen.
- **Routing**: hash based (`/#/registrations`), so the host needs no rewrite
  rules and a refresh on any screen works.
- **CSV exports** are fetched with the bearer header and saved from a blob. Resumes
  and the resume book are downloaded the same way.
- **Resumes**: the registration drawer shows whether a resume was uploaded (file
  name and size), whether the person opted in to sharing it with sponsors, a
  "Download resume" button and a "Remove resume" action (confirmed first; use it to
  honour a removal request). The Registrations table has a Resume column
  ("Opted in", "Organizers only" or "None") and a Resume filter.
- **Resume book**: "Download resume book (ZIP)" on the Registrations screen is the
  file promised to sponsors. Its confirmation states how many resumes the ZIP will
  hold (the count of accepted registrants who opted in, read from the registrations
  list with `resume=opted-in&status=ACCEPTED`) and offers "Attended only", which
  limits it to people with a general check-in. Resumes of people who did not opt in,
  or are not accepted, are never in it.
- **Acceptances**: accepting someone sends no email. They join the acceptance
  bucket and are told when an organizer presses "Send acceptance emails" on the
  Acceptances screen, which emails everyone waiting once. Its confirmation states
  exactly how many people will be emailed and warns, without blocking, when the
  host-school target is not met. While a send runs the screen shows progress and
  afterwards how many were sent and how many failed; failures stay in the bucket
  and are retried by sending again. The bucket table lists who is waiting, with
  "Move back to pending".
- **Host-school share**: the Acceptances screen leads with the percentage of
  accepted hackers from the host school on a bar with the target marked: green at
  or above the target, amber below, with the gap in words ("12 more Georgia State
  University acceptances needed, or 5 fewer acceptances from other schools"). The
  same share is shown for people checked in (the real attendance figure during
  the event), for pending registrations and for all registrations. The host
  school and target come from the API (`HOST_SCHOOL_NAME`, `HOST_SCHOOL_TARGET`
  in `backend/`). The Overview has a compact tile and the Registrations screen a
  one-line strip. All three read `GET /admin/acceptances/summary` through
  `useAcceptanceSummary` (`src/lib/hooks.js`), which refetches after every
  status change, check-in or send made from this browser (those calls
  report through `changing` in `src/api/client.js`), every 2 seconds while a
  send is running and every 15 seconds otherwise, so another organizer's work
  shows up without a reload.
- **Accepted, told or not**: the Registrations table and the drawer show "Told"
  or "Not told yet" beside the Accepted badge. In the drawer the ticket panel
  offers "Send acceptance email now" for someone still waiting (it tells only
  that person) and "Resend ticket email" once they know. Moving someone who was
  already told out of Accepted asks for confirmation first, because their ticket
  stops working and nothing tells them.
- **Bulk status changes**: tick rows in the Registrations table (the header box
  selects the page; the selection survives paging and filtering) and use Accept,
  Waitlist, Reject or Move to pending. While rows are selected the strip shows
  what the host-school share would become if they were accepted, computed in the
  browser from the summary and the selection (`src/lib/acceptance.js`, the same
  arithmetic as the API), and the confirmation shows the share before and after
  the chosen change and how many of the selection were already told. At most 500
  at a time, the API's limit.
- **School email** is shown in the registration drawer and the pre-registrations
  table; the Registrations search matches it as well as the personal email.
- **School email confirmation**: the API mails a link to the school address and
  records when it is opened. The pre-registrations table shows "Confirmed" or
  "Unconfirmed" beside the address, with a "Resend link" action on unconfirmed
  rows. The Registrations table is too wide for another column, so it only marks
  the unconfirmed ones under the personal email; the drawer has a School email
  panel with the state, the date it was confirmed and "Resend confirmation". Both
  screens have a School email filter, which also applies to the CSV export, and
  both exports end with a `school_email_confirmed` column. An unconfirmed school
  email never blocks a status change: the drawer shows a warning on the status
  control and in the accept confirmation, and the organizer decides. The Overview
  tiles for pre-registrations and registrations say how many have a confirmed
  school email. Registrations made before school emails were collected have none
  and count as unconfirmed.
- **Age eligibility**: host-school students are eligible at any age; students of
  other schools must be at least the minimum age (`NON_HOST_MINIMUM_AGE` in
  `backend/`, 18). The form does not enforce it, so the API flags a registration
  under that age from another school with `ageReview`, and the admin site shows it
  as "Under 18, not Georgia State University" (both values come from
  `GET /admin/acceptances/summary`): under the school in the Registrations table,
  on the status control in the drawer, and beside the school in the bucket table.
  The Registrations screen has an Age eligibility filter, which also applies to
  the CSV export, and the export ends with an `age_review` column. Nothing is
  blocked: the accept confirmation says the person is flagged and lets the
  organizer proceed; a bulk accept states how many of the selection are flagged
  before confirming and, afterwards, the count the API returned
  (`acceptedAgeReview`). When anyone accepted is flagged, the Acceptances screen
  shows a notice with the count and a link to
  `/#/registrations?status=ACCEPTED&ageReview=true`, which opens the list with
  those two filters set.
- **Deleting**: there is no way to delete a pre-registration or a registration
  from the admin site, and the API has no endpoint for it. A workshop event can
  be deleted in Settings only while it has no check-ins: with any, its Delete
  button is disabled and the row says why, and if someone is checked in between
  loading the list and confirming, the API refuses (409 `EVENT_HAS_CHECK_INS`)
  and the dialog shows its message. The general check-in event has no Delete
  button. Removing a resume, undoing a check-in and removing an account are
  unchanged.
- **Email preview**: once a draft has a subject and a message, the composer shows
  the email exactly as the API renders it (`POST /admin/emails/preview`, which
  sends nothing), in an `<iframe sandbox="">` so the rendered HTML can run no
  script. Until then, or if that call fails, it shows the wording only. In mock
  mode the frame holds a simplified stand-in.
- **Email kinds**: the composer's first choice is the kind of email. An **event
  update** is logistics for people who are coming: it is always delivered, has no
  unsubscribe link, and can only go to Registrants or Accepted hackers (accepted
  and already sent their acceptance email). An **announcement** is news and
  promotion: people who unsubscribed are skipped and it carries the unsubscribe
  link. The audience list shows only the audiences the kind allows, the live
  recipient count is asked for that kind (so the same audience can count
  differently), the preview shows that kind's footer, the confirmation names the
  kind and the count, "Send test to me" sends the kind so the test has the right
  footer, and the history tags each campaign with its kind. The "Registration is
  open" template selects Announcement. Confirmations, the school email link, the
  acceptance and ticket email and account welcomes are not campaigns: the API
  always sends them, without an unsubscribe link. "Unsubscribed" in the
  pre-registrations table therefore means "no announcements".
- **Scanning** uses the camera through `getUserMedia`, which browsers only allow
  on https (or localhost). QR codes are read with the browser's own
  `BarcodeDetector` where it supports them (Android, macOS); elsewhere (iOS
  Safari, Chrome on Windows) the `barcode-detector` package provides the same
  API from a WebAssembly build of ZXing. That code and its `.wasm` file are
  separate chunks, downloaded only on browsers that need them and served from
  this site rather than the package's default CDN. The camera stops when the
  volunteer switches to Search or leaves the screen.
- All API calls live in `src/api/client.js`.

## Deploy (Vercel)

Create a Vercel project for this directory, separate from the public site:

- Root Directory: `admin`
- Framework Preset: Vite (build command `npm run build`, output `dist`)
- Environment variable: `VITE_API_BASE_URL=https://api.peachhacks.com`
  (optional in production, since that is the default)
- Domain: `admin.peachhacks.com`

The API must allow CORS from `https://admin.peachhacks.com` (and
`http://localhost:5174` for local development), including the `Authorization`
header and reading `Content-Disposition` on the CSV export responses if the
server-chosen file names should be used.

The page sends `<meta name="robots" content="noindex">`, and `vercel.json` adds
a matching `X-Robots-Tag` header plus basic security headers.
