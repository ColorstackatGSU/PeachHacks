import { createRoot } from 'react-dom/client';
import './forms.css';
import './page.css';

export function mountPage(Page) {
  createRoot(document.getElementById('root')).render(<Page />);
}

export function readQueryParam(name) {
  return new URLSearchParams(window.location.search).get(name)?.trim() || '';
}
