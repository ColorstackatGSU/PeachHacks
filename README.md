# PeachHacks

The website, organizer tools and API for PeachHacks, the hackathon run by [ColorStack at Georgia State University](https://www.linkedin.com/company/colorstack-gsu/). PeachHacks 2027 runs February 5 to 7, 2027 in Atlanta.

| | |
| --- | --- |
| Public site | https://www.peachhacks.com |
| Organizer admin | https://admin.peachhacks.com |
| API | https://api.peachhacks.com |

## What is in this repo

| Path | What it is | Stack | Deployed on |
| --- | --- | --- | --- |
| [`web/`](web/) | Public site: homepage, pre-registration, registration, ticket, sponsor form | Vite, React | Vercel |
| [`admin/`](admin/) | Organizer site: sign-ups, check-in, email, settings | Vite, React | Vercel |
| [`backend/`](backend/) | API and database | Spring Boot 4, Java 21, PostgreSQL, Flyway | Railway |

Each package has its own README with the details: [backend](backend/README.md), [admin](admin/README.md). Notes for the public site are in [`web/docs/`](web/docs/).

## How it fits together

```
 hackers ──▶ www.peachhacks.com ──┐
                                  ├──▶ api.peachhacks.com ──▶ PostgreSQL
 organizers ─▶ admin.peachhacks.com ┘          │
                                               ├──▶ Resend (email)
                                               └──▶ Google Wallet (ticket passes)
```

1. **Pre-registration.** A student leaves their name, email and school on the public site and gets a confirmation email.
2. **Registration.** An organizer opens registration from the admin site. The public site then shows the full registration form, which follows [MLH's required fields](https://guide.mlh.com/general-information/managing-registrations/registrations). While registration is closed, the site sends visitors to pre-registration.
3. **Acceptance.** An organizer marks a registration as accepted. The hacker is emailed a ticket: a QR code, a ticket page on the public site and, when configured, a Google Wallet pass.
4. **Check-in.** At the event, organizers and volunteers scan tickets in the admin site, for general check-in and for individual workshops.

Organizer accounts are either admins (everything) or volunteers (check-in only). The API enforces the difference.

## Run it locally

You need Node 22, JDK 21 and Docker.

```sh
# 1. API on http://localhost:8080 (starts its own Postgres in Docker)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 2. Public site on http://localhost:5173
npm install --prefix web
npm run dev --prefix web

# 3. Organizer site on http://localhost:5174
npm install --prefix admin
npm run dev --prefix admin
```

On Windows use `mvnw.cmd` in place of `./mvnw`.

- Sign in to the organizer site with the local-only account defined in [`backend/src/main/resources/application-local.yml`](backend/src/main/resources/application-local.yml).
- Registration starts closed. Open it under Settings in the organizer site to see the registration form.
- No email is sent locally. Without `RESEND_API_KEY` the API writes each email to its log instead.
- The organizer site can also run on fake data with no API: see "Mock mode" in the [admin README](admin/README.md).

## Checks

| Package | Commands |
| --- | --- |
| `web/` | `npm run build`, `npm run lint:js`, `npm run lint:css`, `npm run lint:html` |
| `admin/` | `npm run build`, `npm run lint` |
| `backend/` | `./mvnw test` (needs Docker) |

Pull requests into `main` run the three `web/` linters and a merge-conflict check.

## Configuration

Nothing needs configuring for local development. In production:

- **`backend/`** reads its database, first admin account, email and Google Wallet settings from environment variables. They are listed in the [backend README](backend/README.md#configuration) and in [`backend/.env.example`](backend/.env.example).
- **`web/`** and **`admin/`** call `https://api.peachhacks.com` unless `VITE_API_BASE_URL` says otherwise. See [`web/.env.example`](web/.env.example) and [`admin/.env.example`](admin/.env.example).

Secrets live in the hosting providers' settings. Never commit a `.env` file, an API key or a service account key.

## Deployment

- `main` is production. Vercel builds `web/` and `admin/` as two projects, and Railway builds `backend/` from its `Dockerfile`.
- Database changes ship as Flyway migrations in [`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration) and run automatically when the API starts. Add a new migration; never edit one that has already been deployed.

## Contributing

1. Branch from `main` and open a pull request. Keep pull requests small; stack them when a change builds on another.
2. Run the checks for the package you touched before asking for review.
3. Routes on the API have no `/api` prefix, because it is served from its own domain.
4. Write a comment only when it explains something the code cannot.

Questions: ask in the [PeachHacks Discord](https://discord.gg/jksZ2gaZnX).
