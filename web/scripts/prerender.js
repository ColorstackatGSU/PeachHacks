// Second half of `npm run build`: renders the homepage to HTML and writes it
// into dist/index.html, so the page is readable before any JavaScript runs.
// src/main.jsx then hydrates that markup instead of rendering from scratch.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { prerender } from 'react-dom/static';
import { createServer } from 'vite';

const ROOT_TAG = '<div id="root"></div>';
const webDir = fileURLToPath(new URL('..', import.meta.url));
const pagePath = fileURLToPath(new URL('../dist/index.html', import.meta.url));

// The static renderer waits for lazily loaded components. Left to its default
// chunk size it would still write the hero tag as a fallback plus a script that
// swaps the real markup in; the page has to be complete as plain HTML.
async function renderToHtml(element) {
  const { prelude } = await prerender(element, { progressiveChunkSize: Infinity });
  return new Response(prelude).text();
}

const vite = await createServer({
  root: webDir,
  mode: 'production',
  appType: 'custom',
  logLevel: 'error',
  server: { middlewareMode: true, hmr: false, ws: false },
  optimizeDeps: { noDiscovery: true },
});

try {
  const { default: App } = await vite.ssrLoadModule('/src/home/App.jsx');
  const markup = await renderToHtml(createElement(App));
  if (markup.includes('<template') || markup.includes('<script')) throw new Error('The pre-rendered markup is incomplete: React left a streaming placeholder in it.');
  const page = await readFile(pagePath, 'utf8');
  if (!page.includes(ROOT_TAG)) throw new Error(`dist/index.html has no ${ROOT_TAG} to fill`);
  await writeFile(pagePath, page.replace(ROOT_TAG, () => `<div id="root">${markup}</div>`));
  console.log(`pre-rendered dist/index.html (${(markup.length / 1024).toFixed(1)} kB of markup)`);
} finally {
  await vite.close();
}
