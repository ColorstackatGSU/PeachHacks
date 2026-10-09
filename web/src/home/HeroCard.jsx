import { useEffect } from 'react';
import { preloadRegisterPanel } from './RegisterLayer.jsx';
import { openRegisterPanel } from './registerPanel.js';
import { useRegistrationCta } from './registration.js';
import { EVENT_DATES, EVENT_PLACE, EVENT_THEME } from './site.js';

// False until React has attached this component's handlers; see src/main.jsx.
let interactive = false;
export const isHeroCardInteractive = () => interactive;

export function HeroCardContent({ registerButtonRef, inert = false }) {
  const cta = useRegistrationCta();

  useEffect(() => {
    interactive = true;
    return () => { interactive = false; };
  }, []);

  return (
    <div className="window-content" inert={inert}>
      <p className="hero-kicker">
        <span className="hero-kicker-part">{EVENT_DATES}</span>{' '}
        <span className="hero-kicker-dot" aria-hidden="true">·</span>{' '}
        <span className="hero-kicker-part">{EVENT_PLACE}</span>
      </p>
      <p className="hero-theme">{EVENT_THEME}</p>
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
      <a className="scroll-cue" href="#about" aria-label="Next section: About">
        <span className="scroll-cue-chevron" aria-hidden="true" />
      </a>
    </div>
  );
}

// The tag without its drag and tow behaviour: what shows while that code is
// still loading on a page that was not pre-rendered. `standalone` means the
// code failed to load, so the sign-up panel must appear without being towed in.
export function StaticHeroTag({ registerButtonRef, standalone = false }) {
  useEffect(() => {
    if (!standalone) return undefined;
    document.documentElement.classList.add('tag-static');
    return () => document.documentElement.classList.remove('tag-static');
  }, [standalone]);

  return (
    <div className="hero-tag">
      <div className="hero-card">
        <span className="banner-tow-line" aria-hidden="true" />
        <HeroCardContent registerButtonRef={registerButtonRef} />
      </div>
    </div>
  );
}
