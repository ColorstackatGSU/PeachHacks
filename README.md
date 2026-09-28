# PeachHacks

Monorepo for PeachHacks, the hackathon run by ColorStack at Georgia State University (GSU).

## Packages

| Path        | Description                          | Status  |
| ----------- | ------------------------------------ | ------- |
| `web/`      | Public website (Vite + React)        | Active  |
| `backend/`  | Backend services                     | Planned |
| `platform/` | Hacker/organizer platform            | Planned |

## Running the website

From the repo root:

```sh
npm install --prefix web
npm run dev --prefix web
```

Or from inside the package:

```sh
cd web
npm install
npm run dev
```

Linting: `npm run lint:js`, `npm run lint:css`, `npm run lint:html` (run in `web/`).

More docs live in [`web/docs/`](web/docs/).
