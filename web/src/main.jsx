import React, { useCallback, useEffect, useRef, useSyncExternalStore } from 'react';
import { createRoot } from 'react-dom/client';
import { scroll } from 'motion';
import { animateSingleValue, motion, useDragControls, useMotionValue, useMotionValueEvent, useReducedMotion, useSpring, useTransform, useVelocity } from 'motion/react';
import SiteHeader from './home/SiteHeader.jsx';
import SiteFooter from './home/SiteFooter.jsx';
import RegisterLayer, { preloadRegisterPanel } from './home/RegisterLayer.jsx';
import { closeRegisterPanel, openRegisterPanel, openedOnLoad, useRegisterPanelShown } from './home/registerPanel.js';
import { useRegistrationCta } from './home/registration.js';
import { PARTNER_PLACEHOLDERS, getFaqColumns, scheduleNote, tracksBackdropRows, tracksData } from './home/content.jsx';
import { EVENT_DATES, SPONSOR_EMAIL, SPONSOR_FORM_PATH, prefersReducedMotion } from './home/site.js';
import { introStars, partnerStars, renderStarField } from './home/stars.jsx';
import './styles.css';

const supportsScrollTimeline = typeof CSS !== 'undefined' && typeof CSS.supports === 'function' && CSS.supports('animation-timeline: view()');
if (!supportsScrollTimeline) document.documentElement.classList.add('no-scroll-timeline');

const decodeImage = (src) => {
  const image = new Image();
  image.src = src;
  return image.decode().catch(() => {});
};
document.documentElement.classList.add('assets-pending');
Promise.race([
  Promise.all([document.fonts?.load('400 1em Agbalumo').catch(() => {}), decodeImage('/assets/Hero.svg')]),
  new Promise((resolve) => { setTimeout(resolve, 2500); }),
]).then(() => document.documentElement.classList.remove('assets-pending'));

// Old links used #details for the partners section.
const HASH_ALIASES = { details: 'partners' };

function useScrollProgressFallback(rootRef) {
  useEffect(() => {
    if (supportsScrollTimeline || prefersReducedMotion()) return undefined;
    const stops = [...rootRef.current.querySelectorAll('[data-progress]')].map((element) => {
      const pin = element.dataset.progress === 'pin';
      return scroll((progress) => element.style.setProperty(pin ? '--p' : '--vp', progress.toFixed(4)), {
        target: element,
        offset: pin ? ['start start', 'end end'] : ['start end', 'end start'],
      });
    });
    return () => stops.forEach((stop) => stop());
  }, [rootRef]);
}

// Once the pinned hero's card has faded out it must also leave the tab order. On small
// screens the hero is not pinned and the card just scrolls away, so it stays interactive.
function useHeroCardInert(pinRef, shellRef) {
  useEffect(() => {
    const pin = pinRef.current;
    const shell = shellRef.current;
    if (prefersReducedMotion()) return undefined;
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
  }, [pinRef, shellRef]);
}

function useStarPointerParallax(rootRef) {
  useEffect(() => {
    const root = rootRef.current;
    if (prefersReducedMotion() || !window.matchMedia('(pointer: fine)').matches) return undefined;
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
      window.removeEventListener('pointermove', onPointerMove);
    };
  }, [rootRef]);
}

function useInitialHashScroll() {
  useEffect(() => {
    const hash = decodeURIComponent(window.location.hash.replace(/^#/, ''));
    const target = hash && document.getElementById(HASH_ALIASES[hash] ?? hash);
    if (target) target.scrollIntoView({ block: 'start', behavior: 'instant' });
  }, []);
}

function scrollToTracks() {
  document.getElementById('tracks')?.scrollIntoView({ block: 'start', behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
}

const TAG_CLICK_SLOP = 6;
const TAG_MAX_TILT = 6;
const TOW_ANGLE = (26 * Math.PI) / 180;
const TAG_TOW_SPRING = { type: 'spring', stiffness: 120, damping: 13 };
const TAG_PEEK = 72;
const ROPE_TUCK = 18;
// No overshoot: while the tag springs past its spot the rope must stay on the panel.
const ROPE_TIE_EASE = { duration: 0.16, ease: 'easeOut' };
const PANEL_CLEARANCE = 48;
// The panel follows the tag in on its own eased path rather than being bolted
// to it, so the tag's bounce at the end of its run does not shake the form.
const PANEL_IN = { duration: 0.5, ease: [0.22, 1, 0.36, 1] };
const PANEL_OUT = { duration: 0.3, ease: [0.55, 0, 1, 0.45] };
const TAG_PULL_OPEN_OFFSET = 150;
const TAG_OFFSCREEN = 120;
const TAG_RELEASE_OFFSET = 80;
const TAG_RELEASE_VELOCITY = 400;
// Where the hero stops pinning (see styles.css); there the sign-up panel is a
// full-screen sheet and the parked tag sits out of sight behind it.
const SHEET_QUERY = '(max-width: 768px), (max-height: 520px)';

function subscribeToSheet(listener) {
  const query = window.matchMedia(SHEET_QUERY);
  query.addEventListener('change', listener);
  return () => query.removeEventListener('change', listener);
}
const isSheet = () => window.matchMedia(SHEET_QUERY).matches;

function HeroTag({ registerButtonRef, tagCloseRef }) {
  const cta = useRegistrationCta();
  const parked = useRegisterPanelShown();
  const sheet = useSyncExternalStore(subscribeToSheet, isSheet);
  const reduceMotion = useReducedMotion();
  const tagRef = useRef(null);
  const draggedFar = useRef(false);
  const towing = useRef([]);
  const settled = useRef(false);
  const dragControls = useDragControls();
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const tilt = useSpring(useTransform(useVelocity(x), [-600, 600], [-TAG_MAX_TILT, TAG_MAX_TILT]), { stiffness: 180, damping: 12 });
  const ropeTie = useRef({ panel: 0, eyeletX: 0, eyeletY: 0 });
  const tied = useMotionValue(0);
  const panelX = useMotionValue(-window.innerWidth * 2);
  const panelMove = useRef(null);
  const towTransform = useTransform(() => {
    const reach = window.innerWidth * 0.5;
    const restX = -reach * Math.cos(TOW_ANGLE);
    const restY = -reach * Math.sin(TOW_ANGLE);
    const blend = tied.get();
    const { panel, eyeletX, eyeletY } = ropeTie.current;
    const shiftX = eyeletX * blend;
    const shiftY = eyeletY * blend;
    const towX = (restX - x.get()) * (1 - blend) + (panel + panelX.get() - x.get() - eyeletX) * blend;
    const towY = restY * (1 - blend) - y.get();
    let bend = ((Math.atan2(towY, towX) - Math.atan2(restY, restX)) * 180) / Math.PI;
    if (bend > 180) bend -= 360;
    if (bend < -180) bend += 360;
    return `translate(${shiftX.toFixed(2)}px, ${shiftY.toFixed(2)}px) rotate(${(26 + bend - tilt.get()).toFixed(3)}deg) scaleX(${(Math.hypot(towX, towY) / reach).toFixed(4)})`;
  });
  const limits = { left: -window.innerWidth * 0.3, right: window.innerWidth * 0.3, top: -window.innerHeight * 0.2, bottom: window.innerHeight * 0.2 };
  const parkedLimits = { ...limits, left: 0, right: window.innerWidth };
  const setTagState = (add, remove) => {
    tagRef.current?.classList.remove(remove);
    if (add) tagRef.current?.classList.add(add);
  };

  // Parked, a strip of the tag stays on screen at the right edge of the hero.
  const restLeft = () => {
    const tag = tagRef.current;
    return tag.offsetParent.getBoundingClientRect().left + tag.offsetLeft + tag.firstElementChild.offsetLeft;
  };
  const parkedX = useCallback(() => {
    const edge = tagRef.current.closest('.intro').getBoundingClientRect().right;
    return (isSheet() ? edge + TAG_OFFSCREEN : edge - TAG_PEEK) - restLeft();
  }, []);

  // The panel slides in from beyond the left edge as the tag leaves, and back
  // out as it returns. Written straight to the element: a custom property on
  // the root would restyle the whole page on every frame.
  useMotionValueEvent(panelX, 'change', (offset) => {
    const panel = document.querySelector('.register-panel');
    if (panel) panel.style.transform = `translate3d(${offset.toFixed(1)}px, 0, 0)`;
  });
  const panelAway = () => {
    const panel = document.querySelector('.register-panel');
    return panel ? -(panel.offsetLeft + panel.offsetWidth + PANEL_CLEARANCE) : -window.innerWidth;
  };
  const movePanel = useCallback((open, instant) => {
    const away = panelAway();
    panelMove.current?.stop();
    if (instant) {
      panelX.jump(open ? 0 : away);
      return;
    }
    if (panelX.get() < away) panelX.jump(away);
    panelMove.current = animateSingleValue(panelX, open ? 0 : away, open ? PANEL_IN : PANEL_OUT);
  }, [panelX]);

  // While the panel is open the rope runs from the tag's eyelet to the panel's
  // right edge, its tapered end tucked under the panel. offsetLeft/offsetWidth
  // ignore the panel's own transform, which getBoundingClientRect would not.
  const tieRopeToPanel = useCallback(() => {
    const panel = document.querySelector('.register-panel');
    if (!panel) return;
    ropeTie.current = {
      panel: panel.offsetLeft + panel.offsetWidth - ROPE_TUCK - restLeft(),
      eyeletX: 0,
      eyeletY: 0,
    };
  }, []);

  // The tag drives itself with the motion values the drag uses, so the tow
  // line and the tilt follow along and a grab mid-flight simply takes over.
  const towTo = useCallback((targetX, instant) => {
    towing.current.forEach((animation) => animation.stop());
    const tag = tagRef.current;
    tag.classList.remove('is-dragging');
    if (instant) {
      towing.current = [];
      tag.classList.remove('is-springing');
      x.jump(targetX);
      y.jump(0);
      return;
    }
    tag.classList.add('is-springing');
    const animations = [animateSingleValue(x, targetX, TAG_TOW_SPRING), animateSingleValue(y, 0, TAG_TOW_SPRING)];
    towing.current = animations;
    Promise.all(animations).then(() => {
      if (towing.current === animations) tag.classList.remove('is-springing');
    });
  }, [x, y]);

  useEffect(() => {
    const first = !settled.current;
    settled.current = true;
    if (first && !parked) return undefined;
    const instant = reduceMotion || (first && openedOnLoad);
    const tie = parked && !sheet ? 1 : 0;
    const go = () => {
      if (tie) tieRopeToPanel();
      if (instant) tied.jump(tie);
      else animateSingleValue(tied, tie, ROPE_TIE_EASE);
      movePanel(parked, instant);
      towTo(parked ? parkedX() : 0, instant);
    };
    if (!parked) {
      go();
      return undefined;
    }
    // Opening restyles most of the page (the form becomes visible, the rest
    // goes inert) and the form has to be painted for the first time. For two
    // frames the form sits in place, all but transparent, so that work is
    // done before anything moves; then it starts from off-screen with the tag.
    const panel = document.querySelector('.register-panel');
    const stage = (staged) => {
      if (panel) panel.style.opacity = staged ? '0.01' : '';
    };
    let waiting = 0;
    if (instant) go();
    else {
      panelMove.current?.stop();
      stage(true);
      panelX.jump(0);
      waiting = requestAnimationFrame(() => {
        waiting = requestAnimationFrame(() => {
          waiting = 0;
          stage(false);
          panelX.jump(panelAway());
          go();
        });
      });
    }
    const onResize = () => {
      if (waiting) return;
      tieRopeToPanel();
      towTo(parkedX(), true);
    };
    window.addEventListener('resize', onResize);
    return () => {
      if (waiting) {
        cancelAnimationFrame(waiting);
        stage(false);
        panelX.jump(panelAway());
      }
      window.removeEventListener('resize', onResize);
    };
  }, [parked, sheet, reduceMotion, parkedX, towTo, tieRopeToPanel, movePanel, tied]);

  return (
    <motion.div
      ref={tagRef}
      className={reduceMotion ? 'hero-tag' : 'hero-tag is-draggable'}
      style={{ x, y, rotate: tilt }}
      drag={!reduceMotion}
      dragListener={false}
      dragControls={dragControls}
      dragSnapToOrigin={!parked}
      dragMomentum={!parked}
      dragElastic={0.22}
      dragConstraints={parked ? parkedLimits : limits}
      dragTransition={{ bounceStiffness: 120, bounceDamping: 9 }}
      onPointerDown={(event) => {
        draggedFar.current = false;
        if (!reduceMotion && event.button === 0) dragControls.start(event);
      }}
      onDragStart={() => {
        window.getSelection()?.removeAllRanges();
        setTagState('is-dragging', 'is-springing');
      }}
      onDrag={(event, info) => {
        if (Math.hypot(info.offset.x, info.offset.y) > TAG_CLICK_SLOP) draggedFar.current = true;
      }}
      onDragEnd={(event, info) => {
        if (!parked && info.offset.x > TAG_PULL_OPEN_OFFSET) {
          // Carry on to the parking spot now, rather than starting back to rest
          // for the frame or two before the panel state arrives.
          towTo(parkedX(), false);
          openRegisterPanel(registerButtonRef.current);
        }
        else if (!parked) setTagState('is-springing', 'is-dragging');
        else if (info.offset.x < -TAG_RELEASE_OFFSET || info.velocity.x < -TAG_RELEASE_VELOCITY) closeRegisterPanel();
        else towTo(parkedX(), false);
      }}
      onDragTransitionEnd={() => setTagState(null, 'is-springing')}
      onClickCapture={(event) => {
        if (!draggedFar.current) return;
        draggedFar.current = false;
        event.preventDefault();
        event.stopPropagation();
      }}
    >
      <div className="hero-card window-card">
        <motion.span className="banner-tow-line" aria-hidden="true" style={{ transform: towTransform }} />
        {parked && !sheet && (
          <button type="button" className="tag-close" ref={tagCloseRef} aria-label="Close the sign-up form" onClick={closeRegisterPanel}>
            <span className="tag-close-mark" aria-hidden="true">✕</span>
          </button>
        )}
        <div className="window-content" inert={parked}>
          <p className="hero-kicker">{EVENT_DATES} <span aria-hidden="true">·</span> Atlanta, GA</p>
          <h1 id="page-title">Join PeachHacks!</h1>
          <p className="intro-copy">
            A weekend of learning, building, and networking for students, mentors, and industry professionals, hosted by ColorStack at Georgia State University.{' '}
            {cta.open ? 'Registration is open.' : 'Pre-register and we’ll email you the moment registration opens.'}
          </p>
          <button
            type="button"
            className="register-button"
            ref={registerButtonRef}
            aria-haspopup="dialog"
            onClick={(event) => openRegisterPanel(event.currentTarget)}
            onPointerEnter={preloadRegisterPanel}
            onFocus={preloadRegisterPanel}
          >
            {cta.label}
          </button>
          <button type="button" className="scroll-cue" aria-label="Next section: Tracks" onClick={scrollToTracks}>
            <span className="scroll-cue-chevron" aria-hidden="true" />
          </button>
        </div>
      </div>
    </motion.div>
  );
}

function TracksSection() {
  const cta = useRegistrationCta();

  return (
    <section className="tracks-section scroll-scope" id="tracks" data-progress="cover" aria-labelledby="tracks-title">
      <div className="tracks-backdrop" aria-hidden="true">
        {tracksBackdropRows.map((row, rowIndex) => (
          <div className="tracks-backdrop-row scroll-fx" key={row.join('-')}>
            {[0, 1].map((copy) => (
              <span className="tracks-backdrop-copy" key={copy}>
                {row.map((word) => <span className="tracks-backdrop-word" key={`${rowIndex}-${copy}-${word}`}>{word}</span>)}
              </span>
            ))}
          </div>
        ))}
      </div>
      <div className="tracks-content">
        <h2 className="tracks-title scroll-fx" id="tracks-title">Tracks</h2>
        {tracksData.length === 0 && (
          <>
            <div className="tracks-tbd-frame scroll-fx">
              <p className="tracks-tbd">Coming soon</p>
            </div>
            <p className="tracks-copy scroll-fx">
              Tracks and prizes will be announced closer to the event.{' '}
              {cta.open
                ? <><a className="tracks-link" href={cta.href}>Register</a> to claim your spot.</>
                : <><a className="tracks-link" href={cta.href}>Pre-register</a> to be the first to hear.</>}
            </p>
          </>
        )}
      </div>
    </section>
  );
}

function PartnersSection() {
  return (
    <section className="partners-section scroll-scope" id="partners" data-progress="cover" aria-labelledby="partners-title">
      {renderStarField('partners-stars scroll-fx', partnerStars)}
      <svg className="section-cloud-divider" viewBox="0 0 2400 220" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
        <defs>
          <path id="cloud-puffs" d="M0 78A60 60 0 0 1 96 52A74 74 0 0 1 226 44A56 56 0 0 1 318 60A70 70 0 0 1 432 50A50 50 0 0 1 480 78V124A80 80 0 0 1 360 130A110 110 0 0 1 200 126A90 90 0 0 1 70 132A70 70 0 0 1 0 124Z" />
          <pattern id="cloud-row-back" width="480" height="400" patternUnits="userSpaceOnUse" patternTransform="translate(210 0) scale(.86)">
            <use href="#cloud-puffs" y="6" fill="#b9d3dc" opacity=".32" />
          </pattern>
          <pattern id="cloud-row-front" width="480" height="400" patternUnits="userSpaceOnUse">
            <use href="#cloud-puffs" y="46" fill="#F4F6EC" />
          </pattern>
        </defs>
        <rect className="cloud-row-back scroll-fx" x="-720" width="3840" height="220" fill="url(#cloud-row-back)" />
        <rect className="cloud-row-front scroll-fx" x="-720" width="3840" height="220" fill="url(#cloud-row-front)" />
      </svg>
      <img className="partners-moon scroll-fx" src="/assets/moon.svg" alt="" aria-hidden="true" width="140" height="140" loading="lazy" decoding="async" />
      <div className="partners-body">
        <div className="partners-layout">
          <div className="partners-intro scroll-fx scroll-fx-self" data-progress="cover">
            <h2 id="partners-title">Our <br /><span>partners.</span></h2>
            <p className="partners-copy">PeachHacks is a student-centered, beginner-friendly weekend hosted by ColorStack. We’re looking for partners who want to support the next generation of builders through mentorship, workshops, prizes, and food.</p>
            <a className="sponsor-email" href={SPONSOR_FORM_PATH}>This could be YOU! Sponsor PeachHacks <span className="sponsor-email-arrow" aria-hidden="true">↗</span></a>
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

function SchedulePanel() {
  return (
    <section className="schedule-section" id="schedule" aria-labelledby="schedule-title">
      <div className="schedule-stage">
        <div className="schedule-board scroll-scope scroll-fx scroll-fx-self" data-progress="cover">
          <img className="schedule-art" src="/assets/Brick Wall.svg" alt="" aria-hidden="true" width="720" height="400" loading="lazy" decoding="async" />
          <div className="schedule-heading scroll-fx">
            <h2 id="schedule-title">Schedule</h2>
          </div>
          <div className="schedule-body">
            <p className="schedule-tba scroll-fx">To be announced</p>
            <p className="schedule-note scroll-fx">{scheduleNote}</p>
          </div>
        </div>
      </div>
    </section>
  );
}

function FaqSection() {
  const cta = useRegistrationCta();

  return (
    <section className="faq-panel" id="faq" aria-labelledby="faq-title">
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
  useScrollProgressFallback(pageRef);
  useHeroCardInert(pinRef, cardShellRef);
  useStarPointerParallax(pageRef);
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
            <div className="hero-stack">
              <div className="hero-circle-shell hero-fx" aria-hidden="true">
                <div className="hero-logo-circle" />
              </div>
              <div className="hero-logo-shell hero-fx">
                <img className="hero-logo" src="/assets/logo.svg" alt="PeachHacks" width="210" height="79" />
              </div>
              <div className="hero-card-shell hero-fx" ref={cardShellRef}>
                <HeroTag registerButtonRef={registerButtonRef} tagCloseRef={tagCloseRef} />
              </div>
            </div>
          </section>
        </div>

        <TracksSection />
        <PartnersSection />
        <SchedulePanel />
        <FaqSection />
      </main>
      <SiteFooter />
      <RegisterLayerHost tagCloseRef={tagCloseRef} returnFocusRef={registerButtonRef} />
    </div>
  );
}

createRoot(document.getElementById('root')).render(<App />);
