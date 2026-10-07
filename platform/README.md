# PeachHacks Hacker Platform

The site for accepted hackers (platform.peachhacks.com): their ticket and QR code, Connect Discord, the directory of other hackers, and teams. Vite + React, hash-routed, one `index.html`.

## Run locally

```sh
npm install
npm run dev                # http://localhost:5176, talks to the backend on http://localhost:8080
npm run dev -- --mode mock # sample data, no backend; any email and password sign in
```

To sign in against the local backend you need a registration with status `ACCEPTED`: register on the public site, accept it in the admin site, then use "Email me a link" on the sign-in page. Without `RESEND_API_KEY` the backend writes the email, link included, to its log.

## Checks

```sh
npm run lint
npm run build
```

## Configuration

| Variable | Purpose |
| --- | --- |
| `VITE_API_BASE_URL` | API base URL (default `http://localhost:8080` in dev, `https://api.peachhacks.com` in a build) |
| `VITE_MOCK_API` | Dev only: `1` serves in-memory sample data instead of calling the API |

Whether "Sign in with Google" and "Connect Discord" appear is decided by the backend (`GET /platform/config`); see the backend README, "Hacker platform" and "PeachBot".

## Pages

- **Sign in** (`#/`, signed out): Google, email and password, and "Email me a link" for a first sign-in or a forgotten password. **Choose your password** (`#/set-password?token=`) is where that link lands.
- **Home** (`#/`, and `#/discord`, which opens it at the Discord card; PeachBot's Verify button links there): the ticket with its QR code and Google Wallet link, Discord, the hacker's card (bio, GitHub, LinkedIn, looking for a team, shown in the directory or not) and password.
- **Hackers** (`#/hackers`): everyone who has signed in and is listed, with search and filters by school and team status.
- **Teams** (`#/teams`): start a team, ask to join one, and for a team lead, answer requests and remove members.

Discord sends the browser back to the bare origin with `?code=&state=`; `App.jsx` finishes the connection and removes the query from the address bar. The `state` value is kept in `sessionStorage`, so only a connection started in this browser is completed.

## Deploy

A static build (`dist/`). On Vercel, set the root directory to `platform`; `vercel.json` adds the security headers. The Content-Security-Policy allows Google's sign-in script and `api.peachhacks.com`; if the API or site moves, change it there.
