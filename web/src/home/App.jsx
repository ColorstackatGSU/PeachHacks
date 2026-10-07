import { Suspense, lazy, useEffect, useRef } from 'react';
import { StaticHeroTag } from './HeroCard.jsx';
import LoadBoundary from './LoadBoundary.jsx';
import RegisterLayer from './RegisterLayer.jsx';
import SiteFooter from './SiteFooter.jsx';
import SiteHeader from './SiteHeader.jsx';
import { PARTNER_PLACEHOLDERS, aboutItems, backdropRows, getFaqColumns } from './content.jsx';
import { useReducedMotion } from './reducedMotion.js';
import { useRegisterPanelShown } from './registerPanel.js';
import { useRegistrationCta } from './registration.js';
import { SPONSOR_EMAIL, SPONSOR_FORM_PATH } from './site.js';
import { introStars, partnerStars, renderStarField } from './stars.jsx';

// The drag and tow behaviour brings the animation library with it, so it loads
// after the page has painted. On the pre-rendered page React keeps the server's
// markup for the tag in place until then, so nothing moves when it arrives.
const HeroTag = lazy(() => import('./HeroTag.jsx'));

// Old links used #details for the partners section, and the tracks and schedule
// sections have since been folded into other ones.
const HASH_ALIASES = { details: 'partners', tracks: 'about', schedule: 'upcoming' };

const pageTop = (element) => {
  let top = 0;
  for (let node = element; node; node = node.offsetParent) top += node.offsetTop;
  return top;
};

// Browsers without scroll-driven animations get the same effects from --p and
// --vp, which styles.css reads under html.no-scroll-timeline (set in index.html).
function useScrollProgressFallback(rootRef, reducedMotion) {
  useEffect(() => {
    if (reducedMotion || !document.documentElement.classList.contains('no-scroll-timeline')) return undefined;
    const elements = [...rootRef.current.querySelectorAll('[data-progress]')];
    let frame = 0;
    const update = () => {
      frame = 0;
      const viewport = window.innerHeight;
      // Layout positions, not bounding boxes: the effects move the elements they are measured on.
      const measured = elements.map((element) => ({ element, top: pageTop(element) - window.scrollY, height: element.offsetHeight }));
      measured.forEach(({ element, top, height }) => {
        const pin = element.dataset.progress === 'pin';
        const range = pin ? height - viewport : height + viewport;
        const progress = range > 0 ? (pin ? -top : viewport - top) / range : 0;
        element.style.setProperty(pin ? '--p' : '--vp', Math.min(1, Math.max(0, progress)).toFixed(4));
      });
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
  }, [rootRef, reducedMotion]);
}

// Once the pinned hero's card has faded out it must also leave the tab order. On small
// screens the hero is not pinned and the card just scrolls away, so it stays interactive.
function useHeroCardInert(pinRef, shellRef, reducedMotion) {
  useEffect(() => {
    const pin = pinRef.current;
    const shell = shellRef.current;
    if (reducedMotion) return undefined;
    let threshold = Infinity;
    let hidden = false;
    let frame = 0;
    const check = () => {
      frame = 0;
      const next = window.scrollY > threshold;
      if (next !== hidden) {
        hidden = next;
        shell.inert = next;
      }
    };
    const measure = () => {
      const pinnedDistance = pin.offsetHeight - window.innerHeight;
      const pinned = getComputedStyle(pin.firstElementChild).position === 'sticky' && pinnedDistance > 8;
      threshold = pinned ? pinnedDistance * 0.45 : Infinity;
      check();
    };
    const onScroll = () => { if (!frame) frame = requestAnimationFrame(check); };
    measure();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', measure);
    return () => {
      cancelAnimationFrame(frame);
      shell.inert = false;
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', measure);
    };
  }, [pinRef, shellRef, reducedMotion]);
}

function useStarPointerParallax(rootRef, reducedMotion) {
  useEffect(() => {
    const root = rootRef.current;
    if (reducedMotion || !window.matchMedia('(pointer: fine)').matches) return undefined;
    let frame = 0;
    let pointer = null;
    const apply = () => {
      frame = 0;
      root.style.setProperty('--star-pointer-x', ((pointer.x / window.innerWidth - 0.5) * 2).toFixed(3));
      root.style.setProperty('--star-pointer-y', ((pointer.y / window.innerHeight - 0.5) * 2).toFixed(3));
    };
    const onPointerMove = (event) => {
      pointer = { x: event.clientX, y: event.clientY };
      if (!frame) frame = requestAnimationFrame(apply);
    };
    window.addEventListener('pointermove', onPointerMove, { passive: true });
    return () => {
      cancelAnimationFrame(frame);
      root.style.removeProperty('--star-pointer-x');
      root.style.removeProperty('--star-pointer-y');
      window.removeEventListener('pointermove', onPointerMove);
    };
  }, [rootRef, reducedMotion]);
}

function useInitialHashScroll() {
  useEffect(() => {
    const hash = decodeURIComponent(window.location.hash.replace(/^#/, ''));
    const target = hash && document.getElementById(HASH_ALIASES[hash] ?? hash);
    if (target) target.scrollIntoView({ block: 'start', behavior: 'instant' });
  }, []);
}

const CLOUD_PUFFS = 'M0 78A60 60 0 0 1 96 52A74 74 0 0 1 226 44A56 56 0 0 1 318 60A70 70 0 0 1 432 50A50 50 0 0 1 480 78V124A80 80 0 0 1 360 130A110 110 0 0 1 200 126A90 90 0 0 1 70 132A70 70 0 0 1 0 124Z';

// A bank of cream clouds over the seam between two sections. `hanging` turns it
// over, for a cream section that ends above a dark one.
function CloudDivider({ id, hanging = false }) {
  return (
    <div className={`section-divider scroll-scope${hanging ? ' is-hanging' : ''}`} data-progress="cover" aria-hidden="true">
      <svg className="section-cloud-divider" viewBox="0 0 2400 220" preserveAspectRatio="xMidYMid slice" focusable="false">
        <defs>
          <path id={`${id}-puffs`} d={CLOUD_PUFFS} />
          <pattern id={`${id}-back`} width="480" height="400" patternUnits="userSpaceOnUse" patternTransform="translate(210 0) scale(.86)">
            <use href={`#${id}-puffs`} y="6" fill="#b9d3dc" opacity=".32" />
          </pattern>
          <pattern id={`${id}-front`} width="480" height="400" patternUnits="userSpaceOnUse">
            <use href={`#${id}-puffs`} y="46" fill="#F4F6EC" />
          </pattern>
        </defs>
        <rect className="cloud-row-back scroll-fx" x="-720" width="3840" height="220" fill={`url(#${id}-back)`} />
        {/* Keeps the back row from showing through the notches where the front puffs meet. */}
        <rect y="150" width="2400" height="70" fill="#F4F6EC" />
        <rect className="cloud-row-front scroll-fx" x="-720" width="3840" height="220" fill={`url(#${id}-front)`} />
      </svg>
    </div>
  );
}

function AboutSection() {
  return (
    <section className="about-section scroll-scope" id="about" data-progress="cover" aria-labelledby="about-title">
      <div className="about-backdrop" aria-hidden="true">
        {backdropRows.map((row, rowIndex) => (
          <div className="about-backdrop-row scroll-fx" key={row.join('-')}>
            {[0, 1].map((copy) => (
              <span className="about-backdrop-copy" key={copy}>
                {row.map((word) => <span className="about-backdrop-word" key={`${rowIndex}-${copy}-${word}`}>{word}</span>)}
              </span>
            ))}
          </div>
        ))}
      </div>
      <div className="about-content">
        <h2 className="about-title scroll-fx" id="about-title">About</h2>
        <ul className="about-list">
          {aboutItems.map((item, idx) => (
            <li className="about-item scroll-fx" key={item.title} style={{ '--i': idx }}>
              <h3>{item.title}</h3>
              <p>{item.body}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function FaqSection() {
  const cta = useRegistrationCta();

  return (
    <section className="faq-panel" id="faq" aria-labelledby="faq-title">
      <CloudDivider id="faq-clouds" />
      <div className="faq-section">
        <h2 className="faq-title scroll-fx scroll-fx-self" id="faq-title" data-progress="cover">FAQ<span className="faq-dot scroll-fx scroll-fx-self" data-progress="cover">.</span></h2>
        <div className="faq-list">
          {getFaqColumns(cta).map((column, columnIndex) => (
            <div className="faq-column" key={columnIndex}>
              {column.map((item) => (
                <details className="faq-item scroll-fx scroll-fx-self" key={item.q} data-progress="cover">
                  <summary><span className="faq-question">{item.q}</span></summary>
                  <p>{item.a}</p>
                </details>
              ))}
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function PartnersSection() {
  return (
    <section className="partners-section scroll-scope" id="partners" data-progress="cover" aria-labelledby="partners-title">
      {renderStarField('partners-stars scroll-fx', partnerStars)}
      <CloudDivider id="partners-clouds" hanging />
      <img className="partners-moon scroll-fx" src="/assets/moon.svg" alt="" aria-hidden="true" width="140" height="140" loading="lazy" decoding="async" />
      <div className="partners-body">
        <div className="partners-layout">
          <div className="partners-intro scroll-fx scroll-fx-self" data-progress="cover">
            <h2 id="partners-title">Our <br /><span>partners.</span></h2>
            <p className="partners-copy">PeachHacks is a student-centered, beginner-friendly weekend hosted by ColorStack. We’re looking for partners who want to support the next generation of builders through mentorship, workshops, prizes, and food. Tell us what you’d like to support and we’ll follow up.</p>
            <a className="sponsor-email" href={SPONSOR_FORM_PATH}>Sponsor PeachHacks <span className="sponsor-email-arrow" aria-hidden="true">↗</span></a>
            <p className="partners-contact">Prefer email? <a className="partners-contact-link" href={`mailto:${SPONSOR_EMAIL}`}>{SPONSOR_EMAIL}</a></p>
          </div>
          <ul className="partners-grid" aria-label="Partners">
            {Array.from({ length: PARTNER_PLACEHOLDERS }, (_, idx) => (
              <li className="partner-box scroll-fx scroll-fx-self" key={idx} data-progress="cover" style={{ '--i': idx % 2 }}>
                <span className="partner-box-label">Partner</span>
                <span className="partner-box-text">TBA</span>
              </li>
            ))}
            <li className="partner-box partner-box-open scroll-fx scroll-fx-self" data-progress="cover" style={{ '--i': PARTNER_PLACEHOLDERS % 2 }}>
              <a className="partner-box-link" href={SPONSOR_FORM_PATH}>
                <span className="partner-box-label">Your logo here</span>
                <span className="partner-box-text">Sponsor <span aria-hidden="true">↗</span></span>
              </a>
            </li>
          </ul>
        </div>
      </div>
    </section>
  );
}

function UpcomingSection() {
  const cta = useRegistrationCta();

  return (
    <section className="upcoming-section" id="upcoming" aria-labelledby="upcoming-title">
      <CloudDivider id="upcoming-clouds" />
      <div className="upcoming-card scroll-fx scroll-fx-self" data-progress="cover">
        <span className="upcoming-eyelet" aria-hidden="true" />
        <h2 id="upcoming-title">Still to come</h2>
        <p className="upcoming-copy">
          Tracks, prizes, the schedule and the building are to be announced.{' '}
          <a className="upcoming-link" href={cta.href}>{cta.label}</a> and we’ll email you as each one is confirmed.
        </p>
      </div>
    </section>
  );
}

// Subscribes on its own so opening the form re-renders this and the tag, not
// every section of the page.
function RegisterLayerHost({ tagCloseRef, returnFocusRef }) {
  const shown = useRegisterPanelShown();
  return <RegisterLayer shown={shown} tagCloseRef={tagCloseRef} returnFocusRef={returnFocusRef} />;
}

function App() {
  const pageRef = useRef(null);
  const pinRef = useRef(null);
  const cardShellRef = useRef(null);
  const registerButtonRef = useRef(null);
  const tagCloseRef = useRef(null);
  const reducedMotion = useReducedMotion();
  useScrollProgressFallback(pageRef, reducedMotion);
  useHeroCardInert(pinRef, cardShellRef, reducedMotion);
  useStarPointerParallax(pageRef, reducedMotion);
  useInitialHashScroll();

  return (
    <div className="page-shell" ref={pageRef}>
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader />
      <main id="main" tabIndex={-1}>
        <div className="hero-pin" ref={pinRef} data-progress="pin">
          <section className="intro" id="hero" aria-labelledby="page-title">
            {renderStarField('intro-stars hero-fx', introStars)}
            <div className="hero-scene hero-fx" aria-hidden="true">
              <img className="hero-art" src="/assets/Hero.svg" alt="" width="705" height="1968" fetchPriority="high" />
              <div className="hero-water-lines">
                {Array.from({ length: 15 }, (_, idx) => (
                  <span className="hero-water-line" key={idx} />
                ))}
              </div>
            </div>
            <div className="hero-sky hero-fx" aria-hidden="true">
              <img className="top-cloud cloud-one" src="/assets/Cloud.svg" alt="" />
              <img className="top-cloud cloud-two" src="/assets/Cloud.svg" alt="" />
              <img className="top-cloud cloud-three" src="/assets/Cloud.svg" alt="" />
            </div>
            <img className="hero-front-cloud hero-front-cloud-left hero-fx" src="/assets/Cloud.svg" alt="" aria-hidden="true" />
            <img className="hero-front-cloud hero-front-cloud-right hero-fx" src="/assets/Cloud.svg" alt="" aria-hidden="true" />
            <div className="hero-stack">
              <div className="hero-logo-shell hero-fx">
                <div className="hero-logo-circle" aria-hidden="true" />
                <picture className="hero-logo-picture">
                  <source media="(max-width: 620px)" srcSet="/assets/logo-stacked.svg" width="78" height="46" />
                  <img className="hero-logo" src="/assets/logo.svg" alt="PeachHacks" width="210" height="79" />
                </picture>
              </div>
              <div className="hero-card-shell hero-fx" ref={cardShellRef}>
                <LoadBoundary fallback={<StaticHeroTag registerButtonRef={registerButtonRef} standalone />}>
                  <Suspense fallback={<StaticHeroTag registerButtonRef={registerButtonRef} />}>
                    <HeroTag registerButtonRef={registerButtonRef} tagCloseRef={tagCloseRef} />
                  </Suspense>
                </LoadBoundary>
              </div>
            </div>
          </section>
        </div>

        <AboutSection />
        <FaqSection />
        <PartnersSection />
        <UpcomingSection />
      </main>
      <SiteFooter />
      <RegisterLayerHost tagCloseRef={tagCloseRef} returnFocusRef={registerButtonRef} />
    </div>
  );
}

export default App;
