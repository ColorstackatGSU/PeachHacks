Lint checking catches real defects before they ship, such as broken markup, invalid CSS, accessibility violations, unsafe JSX patterns. These kind of bugs never throw build errors but will silently degrade the site. [PR #3](https://github.com/ColorstackatGSU/cs-gsu_peachhacks_website/pull/3) added CI workflows to make these checks so every PR gets the same baseline check regardless of who wrote it or what editor they used, which matters a lot for multi-contributor projects like this one. 

The CI checks will catch issues before they reach main, allowing us to focus on actual logic and design rather than nitpicking style and prevents small inconsistencies from compounding as we develop.

## Linting commands for local testing

| Language | Command |
|---|---|
| JSX/JS (ESLint) | `npm run lint:js` |
| CSS (Stylelint) | `npm run lint:css` |
| HTML (html-validate) | `npm run lint:html` |

Run all three: `npm run lint:js && npm run lint:css && npm run lint:html`

**Note on `npm ci` on Windows:** if `npm ci` fails locally with an `EPERM: operation not permitted, unlink ... @rolldown\...rolldown-binding.win32-x64-msvc.node` error, it's a Windows file lock on a native binary (usually held by a running dev server or antivirus scan), not a problem with the lockfile or dependencies. Close any running `npm run dev`/Vite process, then use `npm install` instead of `npm ci`. This doesn't affect the GitHub Actions workflows, which run on `ubuntu-latest` and don't hit this issue.
