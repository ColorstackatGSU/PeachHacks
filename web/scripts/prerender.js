// Second half of `npm run build`: renders the homepage and the two legal pages to
// HTML and writes them into dist, so they are readable before any JavaScript runs
// (and by crawlers that never run it, such as the one Google uses to check a
// privacy policy). The page's own script then hydrates that markup.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createElement } from 'react';
import { prerender } from 'react-dom/static';
import { createServer } from 'vite';

const ROOT_TAG = '<div id="root"></div>';
const webDir = fileURLToPath(new URL('..', import.meta.url));
const PAGES = [
  { file: 'index.html', module: '/src/home/App.jsx', component: 'default' },
  { file: 'privacy.html', module: '/src/forms/LegalPages.jsx', component: 'PrivacyPage' },
  { file: 'terms.html', module: '/src/forms/LegalPages.jsx', component: 'TermsPage' },
];

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
  for (const { file, module, component } of PAGES) {
    const Page = (await vite.ssrLoadModule(module))[component];
    const markup = await renderToHtml(createElement(Page));
    if (markup.includes('<template') || markup.includes('<script')) throw new Error(`The pre-rendered markup of ${file} is incomplete: React left a streaming placeholder in it.`);
    const pagePath = fileURLToPath(new URL(`../dist/${file}`, import.meta.url));
    const page = await readFile(pagePath, 'utf8');
    if (!page.includes(ROOT_TAG)) throw new Error(`dist/${file} has no ${ROOT_TAG} to fill`);
    await writeFile(pagePath, page.replace(ROOT_TAG, () => `<div id="root">${markup}</div>`));
    console.log(`pre-rendered dist/${file} (${(markup.length / 1024).toFixed(1)} kB of markup)`);
  }
} finally {
  await vite.close();
}
