import React, { useEffect, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import { scroll } from 'motion';
import { motion, useDragControls, useMotionValue, useReducedMotion, useSpring, useTransform, useVelocity } from 'motion/react';
import './styles.css';

const scheduleData = [
  {
    day: 'Fri, Feb 5',
    events: [
      { time: '9:00 AM', title: 'Check-in' },
      { time: '10:00 AM', title: 'Opening Ceremony' },
      { time: '11:00 AM', title: 'Hacking begins' },
    ],
  },
  {
    day: 'Sat, Feb 6',
    events: [
      { time: '9:00 AM', title: 'Mentorship sessions' },
      { time: '11:00 AM', title: 'Workshops' },
      { time: '2:00 PM', title: 'Hacking continues' },
    ],
  },
  {
    day: 'Sun, Feb 7',
    events: [
      { time: 'TBA', title: 'Judging & Closing Ceremony' },
    ],
  },
];
const scheduleNote = 'Placeholder schedule — final times coming soon';
const scheduleGroups = scheduleData.map((group, groupIndex) => ({
  ...group,
  firstRow: scheduleData.slice(0, groupIndex).reduce((count, previous) => count + previous.events.length, 0),
}));

const tracksData = [];

const tracksBackdropRows = [
  ['PeachHacks', 'Spring 2027', 'Atlanta', 'Build', 'Learn', 'Ship'],
  ['Hack', 'Georgia State', 'Mentors', 'Prizes', 'Feb 5–7', 'Beginners welcome'],
  ['Ship it', 'PeachHacks', 'Atlanta', 'Build', 'Learn', 'Spring 2027'],
];

const heroLights = [
  ['13.3%', '35.1%', '1.35s', 'cream'], ['15.2%', '44%', '1.9s', 'peach'], ['17.1%', '52.9%', '1.55s', 'cream'],
  ['22.8%', '28.4%', '2.3s', 'peach'], ['25.3%', '37.3%', '1.45s', 'cream'], ['23.4%', '47.4%', '2.05s', 'cream'],
  ['27.2%', '55.2%', '1.7s', 'peach'], ['31.6%', '39.6%', '2.45s', 'cream'], ['33.1%', '50.7%', '1.6s', 'peach'],
  ['59.4%', '32.9%', '1.5s', 'peach'], ['61.3%', '46.3%', '2.2s', 'cream'], ['59.4%', '57.4%', '1.8s', 'cream'],
  ['68.3%', '39.6%', '1.4s', 'cream'], ['69.6%', '52.9%', '2.35s', 'peach'], ['74.6%', '44%', '1.65s', 'cream'],
  ['77.2%', '55.2%', '2.15s', 'peach'], ['82.8%', '41.8%', '1.95s', 'cream'], ['84.5%', '48.5%', '2.55s', 'peach'],
];

const faqData = [
  { q: 'Is PeachHacks free to attend?', a: 'Yes! Food will be provided for the duration of the event. We’ll also have swag and prizes.' },
  { q: 'Where is the event? Is it in person or virtual?', a: 'The event is planned to be in person at Georgia State University, in the XXXX Building at STREET ADDRESS. Parking information will be shared here once confirmed: campus parking site coming soon.' },
  { q: 'Who can attend? What if I have no experience?', a: 'PeachHacks is open to students and is beginner friendly, with workshops and mentors available throughout the event. Attendees must be at least 13 years old. If you’re under 18, you’ll need the university liability form: form link coming soon.' },
  { q: 'What is the team size limit?', a: 'Teams should be between 1 and 4 people. We’ll have a team-building activity right after opening ceremony if you’d like to find teammates.' },
  { q: 'Are there travel reimbursements?', a: 'We are not able to provide travel reimbursements at this time.' },
  { q: 'What should I bring?', a: 'Your laptop, charger, headphones, deodorant, and a pillow or blanket.' },
  { q: 'When can we start working? Can I use a previous project?', a: 'You cannot start until after opening ceremony. You may brainstorm beforehand, but you cannot work on a previous project. Frameworks are okay if you credit them in your README and clearly distinguish what you made.' },
  { q: 'How many challenges can I apply for?', a: 'As many as you want!' },
  { q: 'Do I have to stay overnight?', a: 'No. You can leave and come back if you prefer.' },
  { q: 'What kind of activities will there be?', a: 'There will be workshops and activities to take a break, meet other hackers, and connect with our wonderful sponsors. The full schedule will be posted closer to the event.' },
  { q: 'What is a hackathon?', a: 'A hackathon is an event where students “hack” together to create an app, website, game, or other project in 24–48 hours. There will be no malicious hacking.' },
  { q: 'Will hardware be available?', a: 'We do not have hardware available, but you’re welcome to bring your own. Due to building fire codes, soldering kits are not allowed in the venue.' },
  { q: 'Are you sending acceptances? Is there a deadline or waitlist?', a: 'We’ll send acceptances XX days before the event. Applications will close once we reach the maximum number of hackers we can support, and a local waitlist will open on event day for unfilled spots.' },
  { q: 'How do I sign up to be a mentor, judge, or volunteer?', a: 'You’ll be able to sign up here when those forms open: link coming soon.' },
  { q: 'I have a different question!', a: <>Email us at <a href="mailto:hello@peachhacks.org">hello@peachhacks.org</a> and our team will get back to you.</> },
];
const faqColumns = [faqData.slice(0, Math.ceil(faqData.length / 2)), faqData.slice(Math.ceil(faqData.length / 2))];

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

const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

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
      threshold = Math.max(pin.offsetHeight - window.innerHeight, 1) * 0.45;
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
    const id = decodeURIComponent(window.location.hash.replace(/^#/, ''));
    const target = id && document.getElementById(id);
    if (target) target.scrollIntoView({ block: 'start', behavior: 'instant' });
  }, []);
}

const TAG_CLICK_SLOP = 6;
const TAG_MAX_TILT = 6;
const TOW_ANGLE = (26 * Math.PI) / 180;

function HeroTag() {
  const reduceMotion = useReducedMotion();
  const tagRef = useRef(null);
  const draggedFar = useRef(false);
  const dragControls = useDragControls();
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const tilt = useSpring(useTransform(useVelocity(x), [-600, 600], [-TAG_MAX_TILT, TAG_MAX_TILT]), { stiffness: 180, damping: 12 });
  const towTransform = useTransform(() => {
    const reach = window.innerWidth * 0.5;
    const restX = -reach * Math.cos(TOW_ANGLE);
    const restY = -reach * Math.sin(TOW_ANGLE);
    const towX = restX - x.get();
    const towY = restY - y.get();
    let bend = ((Math.atan2(towY, towX) - Math.atan2(restY, restX)) * 180) / Math.PI;
    if (bend > 180) bend -= 360;
    if (bend < -180) bend += 360;
    return `rotate(${(26 + bend - tilt.get()).toFixed(3)}deg) scaleX(${(Math.hypot(towX, towY) / reach).toFixed(4)})`;
  });
  const limits = { left: -window.innerWidth * 0.3, right: window.innerWidth * 0.3, top: -window.innerHeight * 0.2, bottom: window.innerHeight * 0.2 };
  const setTagState = (add, remove) => {
    tagRef.current?.classList.remove(remove);
    if (add) tagRef.current?.classList.add(add);
  };

  return (
    <motion.div
      ref={tagRef}
      className={reduceMotion ? 'hero-tag' : 'hero-tag is-draggable'}
      style={{ x, y, rotate: tilt }}
      drag={!reduceMotion}
      dragListener={false}
      dragControls={dragControls}
      dragSnapToOrigin
      dragElastic={0.22}
      dragConstraints={limits}
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
      onDragEnd={() => setTagState('is-springing', 'is-dragging')}
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
        <span className="hero-tag-eyelet" aria-hidden="true" />
        <div className="window-content">
          <h1 id="page-title">Join PeachHacks!</h1>
          <p className="intro-copy">We’re excited to bring together students, mentors, and industry professionals for a weekend of learning, building, and networking this February 5–7. Registration details are on the way.</p>
          <a className="register-button" href="/interest-form.html" draggable={false}>Pre-register</a>
          <button type="button" className="scroll-cue" aria-label="Next section: Tracks" onClick={scrollToTracks}>
            <span className="scroll-cue-chevron" aria-hidden="true" />
          </button>
        </div>
      </div>
    </motion.div>
  );
}

const introStars = [
  ['7%', '12%', '14px', '-1.2s'], ['18%', '24%', '10px', '-3.8s'], ['31%', '9%', '18px', '-2.4s'],
  ['44%', '20%', '12px', '-5.1s'], ['58%', '7%', '16px', '-4.2s'], ['70%', '26%', '11px', '-.8s'],
  ['83%', '13%', '19px', '-6.4s'], ['94%', '29%', '12px', '-2.9s'], ['12%', '42%', '9px', '-4.7s'],
  ['39%', '35%', '13px', '-1.9s'], ['64%', '39%', '10px', '-5.8s'], ['88%', '47%', '15px', '-3.3s'],
];

const partnerStars = [
  ['5%', '8%', '12px', '-2.2s'], ['15%', '18%', '9px', '-4.9s'], ['27%', '6%', '16px', '-1.1s'],
  ['40%', '14%', '11px', '-5.6s'], ['54%', '5%', '14px', '-3.7s'], ['68%', '19%', '10px', '-.4s'],
  ['80%', '9%', '17px', '-6.1s'], ['93%', '23%', '12px', '-2.8s'], ['9%', '33%', '15px', '-4.1s'],
  ['22%', '45%', '10px', '-1.7s'], ['35%', '31%', '13px', '-5.2s'], ['49%', '40%', '9px', '-3.2s'],
  ['63%', '29%', '16px', '-.9s'], ['76%', '48%', '11px', '-4.5s'], ['89%', '38%', '14px', '-2.1s'],
  ['4%', '63%', '10px', '-5.9s'], ['18%', '76%', '15px', '-3.6s'], ['31%', '88%', '9px', '-1.4s'],
  ['47%', '69%', '13px', '-4.8s'], ['61%', '84%', '11px', '-2.6s'], ['78%', '73%', '16px', '-6.5s'],
  ['94%', '91%', '10px', '-.6s'],
];

function StarField({ className, stars }) {
  return (
    <div className={`section-stars ${className}`} aria-hidden="true">
      {[0, 1, 2].map((group) => (
        <div className="section-star-group" key={group}>
          {stars.filter((star, index) => index % 3 === group).map(([x, y, size]) => (
            <img
              className="section-star"
              key={`${x}-${y}`}
              src="/assets/star.svg"
              alt=""
              style={{ '--star-x': x, '--star-y': y, '--star-size': size }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

function TracksSection() {
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
              <p className="tracks-tbd">TBD</p>
            </div>
            <p className="tracks-copy scroll-fx">Tracks and prizes will be announced closer to the event. <a href="/interest-form.html">Pre-register</a> to be the first to hear.</p>
          </>
        )}
      </div>
    </section>
  );
}

function SchedulePanel() {
  return (
    <section className="schedule-section" id="schedule" aria-labelledby="schedule-title">
      <div className="schedule-stage">
        <div className="schedule-board scroll-scope scroll-fx scroll-fx-self" data-progress="cover">
          <img className="schedule-art" src="/assets/Brick Wall.svg" alt="" aria-hidden="true" />
          <div className="schedule-heading scroll-fx">
            <h2 id="schedule-title">Schedule</h2>
          </div>
          <div className="schedule-body">
            {scheduleGroups.map((group) => (
              <div className="schedule-day-group" key={group.day}>
                <span className="schedule-day-label scroll-fx" style={{ '--i': group.firstRow }}>{group.day}</span>
                <div className="schedule-day-events">
                  {group.events.map((event, eventIndex) => (
                    <div className="schedule-event-row scroll-fx" key={`${group.day}-${event.title}`} style={{ '--i': group.firstRow + eventIndex }}>
                      <span className="schedule-event-time">{event.time}</span>
                      <span className="schedule-event-dash">-</span>
                      <span className="schedule-event-title">{event.title}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
            <p className="schedule-note scroll-fx">{scheduleNote}</p>
          </div>
        </div>
      </div>
    </section>
  );
}

function FaqPanel() {
  return (
    <div className="faq-panel" id="faq">
      <section className="faq-section" aria-labelledby="faq-title">
        <h2 className="faq-title scroll-fx scroll-fx-self" id="faq-title" data-progress="cover">FAQ<span className="faq-dot scroll-fx scroll-fx-self" data-progress="cover">.</span></h2>
        <div className="faq-list">
          {faqColumns.map((column, columnIndex) => (
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
      </section>

      <footer className="site-footer scroll-fx scroll-fx-self" data-progress="cover">
        <img className="footer-art" src="/assets/Footer.svg" alt="" aria-hidden="true" />
        <div className="site-footer-content">
          <p>PeachHacks <span>·</span> Hosted by ColorStack</p>
          <nav className="footer-links" aria-label="Footer navigation">
            <a href="#schedule">Schedule</a>
            <a href="#tracks">Tracks</a>
            <a href="#details">Sponsors</a>
            <a href="mailto:hello@peachhacks.org">Contact</a>
          </nav>
        </div>
      </footer>
    </div>
  );
}

function scrollToTracks() {
  document.getElementById('tracks')?.scrollIntoView({ block: 'start', behavior: prefersReducedMotion() ? 'auto' : 'smooth' });
}

function App() {
  const pageRef = useRef(null);
  const pinRef = useRef(null);
  const cardShellRef = useRef(null);
  useScrollProgressFallback(pageRef);
  useHeroCardInert(pinRef, cardShellRef);
  useStarPointerParallax(pageRef);
  useInitialHashScroll();

  return (
    <main className="page-shell" ref={pageRef}>
      <div className="hero-pin" ref={pinRef} data-progress="pin">
        <section className="intro" id="hero" aria-labelledby="page-title">
          <StarField className="intro-stars hero-fx" stars={introStars} />
          <div className="hero-scene hero-fx" aria-hidden="true">
            <img className="hero-art" src="/assets/Hero.svg" alt="" />
            <div className="hero-lights">
              {heroLights.map(([x, y, delay, tone]) => (
                <span
                  className={`hero-light hero-light-${tone}`}
                  key={`${x}-${y}`}
                  style={{ '--light-x': x, '--light-y': y, '--light-delay': delay }}
                />
              ))}
            </div>
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
              <img className="hero-logo" src="/assets/logo.svg" alt="PeachHacks" />
            </div>
            <div className="hero-card-shell hero-fx" ref={cardShellRef}>
              <HeroTag />
            </div>
          </div>
        </section>
      </div>

      <TracksSection />

      <section className="partners-section scroll-scope" id="details" data-progress="cover" aria-labelledby="partners-title">
        <StarField className="partners-stars scroll-fx" stars={partnerStars} />
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
        <img className="partners-moon scroll-fx" src="/assets/moon.svg" alt="" aria-hidden="true" />
        <div className="partners-body">
          <div className="partners-layout">
            <div className="partners-intro scroll-fx scroll-fx-self" data-progress="cover">
              <h2 id="partners-title">Our<br /><span>partners.</span></h2>
              <p className="partners-copy">PeachHacks is a student-centered, beginner-friendly weekend hosted by ColorStack. We’re looking for partners who want to support the next generation of builders through mentorship, workshops, prizes, and food.</p>
              <a className="sponsor-email" href="/sponsor-form.html">This could be YOU! Sponsor PeachHacks <span className="sponsor-email-arrow" aria-hidden="true">↗</span></a>
            </div>
            <div className="partners-grid">
              {Array.from({ length: 6 }, (_, idx) => (
                <div className="partner-box scroll-fx scroll-fx-self" key={idx} data-progress="cover" style={{ '--i': idx % 2 }}>
                  <span className="partner-box-label">Partner</span>
                  <span className="partner-box-text">TBA</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <SchedulePanel />

      <FaqPanel />
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
