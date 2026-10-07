import { useEffect, useRef, useState } from 'react';
import { useRegistrationCta } from './registration.js';
import { isHomePage, sectionHref, sectionLinks } from './site.js';

const HEADER_HEIGHT = 56;
const FLOW_SCROLL_THRESHOLD = 24;

// Over the pinned hero the bar stays transparent so the artwork reads as one
// scene; it turns solid once the page content starts sliding underneath it.
function useSolidHeader() {
  const [solid, setSolid] = useState(() => !isHomePage());

  useEffect(() => {
    if (!isHomePage()) return undefined;
    let frame = 0;
    const update = () => {
      frame = 0;
      const pin = document.querySelector('.hero-pin');
      const hero = document.getElementById('hero');
      const afterHero = document.getElementById('faq');
      const pinned = pin && hero && afterHero && pin.offsetHeight > hero.offsetHeight + 8;
      setSolid(pinned ? afterHero.getBoundingClientRect().top <= HEADER_HEIGHT : window.scrollY > FLOW_SCROLL_THRESHOLD);
    };
    const schedule = () => { if (!frame) frame = requestAnimationFrame(update); };
    update();
    window.addEventListener('scroll', schedule, { passive: true });
    window.addEventListener('resize', schedule);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule);
    };
  }, []);

  return solid;
}

function SiteHeader() {
  const cta = useRegistrationCta();
  const solid = useSolidHeader();
  const [menuOpen, setMenuOpen] = useState(false);
  const headerRef = useRef(null);
  const menuButtonRef = useRef(null);

  useEffect(() => {
    if (!menuOpen) return undefined;
    const onKeyDown = (event) => {
      if (event.key !== 'Escape') return;
      setMenuOpen(false);
      menuButtonRef.current?.focus();
    };
    const onPointerDown = (event) => {
      if (!headerRef.current?.contains(event.target)) setMenuOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('pointerdown', onPointerDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('pointerdown', onPointerDown);
    };
  }, [menuOpen]);

  const classes = ['site-header', (solid || menuOpen) && 'is-solid', menuOpen && 'is-open'].filter(Boolean).join(' ');

  return (
    <header className={classes} ref={headerRef}>
      <a className="site-brand" href={isHomePage() ? '#top' : '/'} aria-label="PeachHacks home">
        <img src="/assets/logo.svg" alt="" width="210" height="79" />
      </a>
      <div className="site-header-actions">
        <nav className="site-nav" id="site-nav" aria-label="Main">
          {sectionLinks.map((link) => (
            <a className="site-nav-link" key={link.id} href={sectionHref(link.id)} onClick={() => setMenuOpen(false)}>{link.label}</a>
          ))}
        </nav>
        <a className="site-cta" href={cta.href}>{cta.label}</a>
        <button
          type="button"
          className="site-menu-button"
          ref={menuButtonRef}
          aria-expanded={menuOpen}
          aria-controls="site-nav"
          aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span className="site-menu-icon" aria-hidden="true" />
        </button>
      </div>
    </header>
  );
}

export default SiteHeader;
