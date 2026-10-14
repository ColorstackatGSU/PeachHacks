import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Resvg } from '@resvg/resvg-js';

const BRAND = dirname(fileURLToPath(import.meta.url));
const ROOT = join(BRAND, '..');

// Each app deploys from its own folder, so each keeps a committed copy rather than
// reading these sources at build time.
const COPIES = {
  'logo.svg': ['web/public/assets', 'admin/public/assets', 'platform/public/assets'],
  'logo-stacked.svg': ['web/public/assets'],
};

// Email clients do not show SVG, and the backend draws the welcome card with Java2D.
const RASTERS = [
  { source: 'logo.svg', target: 'web/public/assets/email-logo.png', width: 600 },
  { source: 'logo.svg', target: 'backend/src/main/resources/discord/logo.png', width: 660 },
];

async function outputs() {
  const files = [];
  for (const [name, folders] of Object.entries(COPIES)) {
    const svg = await readFile(join(BRAND, name));
    for (const folder of folders) files.push({ target: `${folder}/${name}`, content: svg });
  }
  for (const { source, target, width } of RASTERS) {
    const svg = await readFile(join(BRAND, source));
    const png = new Resvg(svg, { fitTo: { mode: 'width', value: width }, font: { loadSystemFonts: false } }).render().asPng();
    files.push({ target, content: png });
  }
  return files;
}

const check = process.argv.includes('--check');
const stale = [];
for (const { target, content } of await outputs()) {
  const path = join(ROOT, target);
  if (check) {
    const current = await readFile(path).catch(() => null);
    if (!current || !current.equals(content)) stale.push(relative(ROOT, path));
  } else {
    await writeFile(path, content);
    console.log(`wrote ${target}`);
  }
}
if (stale.length) {
  console.error(`Out of date with brand/: ${stale.join(', ')}. Run npm run build --prefix brand and commit the result.`);
  process.exit(1);
}
