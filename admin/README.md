# PeachHacks Admin

Organizer site for PeachHacks, served at `admin.peachhacks.com`. A Vite + React
single-page app that talks to the Spring Boot API in `../backend`.

Screens: sign in, overview (headline numbers, sign-ups over time, by-school
breakdowns, check-ins by event), pre-registrations, registrations (with a detail
drawer, status changes and the ticket), check-in (scan a ticket QR code or
search by name, per event), email (composer, test send, campaign history) and
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
- **CSV exports** are fetched with the bearer header and saved from a blob.
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
