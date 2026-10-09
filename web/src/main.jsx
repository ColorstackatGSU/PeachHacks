import { createRoot, hydrateRoot } from 'react-dom/client';
import App from './home/App.jsx';
import { isHeroCardInteractive } from './home/HeroCard.jsx';
import { openRegisterPanel } from './home/registerPanel.js';
import './styles.css';

// The hero tag's own handlers arrive with its lazily loaded drag code; until
// then a click on the pre-rendered button still has to open the form. In the
// capture phase, because React swallows clicks on markup it has yet to hydrate.
document.addEventListener('click', (event) => {
  const button = event.target.closest?.('.register-button');
  if (button && !isHeroCardInteractive()) openRegisterPanel(button);
}, true);

// `npm run build` pre-renders the page into #root (scripts/prerender.js); the
// dev server serves it empty.
const root = document.getElementById('root');
if (root.hasChildNodes()) hydrateRoot(root, <App />);
else createRoot(root).render(<App />);
