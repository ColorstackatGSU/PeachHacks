# Brand

`logo.svg` and `logo-stacked.svg` are the only files to edit for the PeachHacks logo.

Each app deploys from its own folder, so it keeps its own copy:

- `logo.svg` in `web/`, `admin/` and `platform/` (`public/assets/`)
- `logo-stacked.svg` in `web/`
- `web/public/assets/email-logo.png` (600 px wide; email clients do not show SVG)
- `backend/src/main/resources/discord/logo.png` (660 px wide, for the Discord welcome card)

You do not update those by hand. When a push to `main` changes anything in this folder, the Update Logo Copies workflow (`.github/workflows/brand-sync.yml`) regenerates every copy and commits them. A copy edited by hand is overwritten the same way.

To see the result locally before pushing:

```sh
npm install --prefix brand
npm run build --prefix brand
```
