import { createRoot, hydrateRoot } from 'react-dom/client';
import './forms.css';
import './page.css';

// The legal pages are pre-rendered into #root by `npm run build`; the others, and the
// dev server, start empty.
export function mountPage(Page) {
  const root = document.getElementById('root');
  if (root.hasChildNodes()) hydrateRoot(root, <Page />);
  else createRoot(root).render(<Page />);
}

export function readQueryParam(name) {
  return new URLSearchParams(window.location.search).get(name)?.trim() || '';
}
