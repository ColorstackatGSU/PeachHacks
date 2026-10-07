import { CONTACT_EMAIL } from '../home/site.js';

export function PageShell({ children }) {
  return (
    <div className="pf-page">
      <div className="pf-sky" aria-hidden="true">
        <img className="pf-moon" src="/assets/moon.svg" alt="" />
      </div>
      <header className="pf-header">
        <a className="pf-brand" href="/" aria-label="PeachHacks home">
          <img src="/assets/logo.svg" alt="PeachHacks" width="210" height="79" />
        </a>
        <a className="pf-home-link" href="/">
          <span aria-hidden="true">←</span> Back to home
        </a>
      </header>
      <main className="pf-main">{children}</main>
      <footer className="pf-footer">
        <p>
          Questions? Email <a className="pf-footer-link" href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
        </p>
        <p className="pf-footer-legal">
          <a className="pf-footer-link" href="/privacy">Privacy Policy</a>
          <span aria-hidden="true"> · </span>
          <a className="pf-footer-link" href="/terms">Terms of Service</a>
        </p>
      </footer>
    </div>
  );
}

// `panel` is the card inside the homepage's sign-up dialog: the dialog is
// already the labelled region and the page already has its h1.
export function Card({ title, titleId, intro, children, headingRef, panel = false }) {
  const Root = panel ? 'div' : 'section';
  const Heading = panel ? 'h2' : 'h1';
  return (
    <Root className="pf-card" aria-labelledby={panel ? undefined : titleId}>
      <div className="pf-tag">
        <span className="pf-eyelet" aria-hidden="true" />
        <Heading className="pf-title" id={titleId} tabIndex={-1} ref={headingRef}>{title}</Heading>
        {intro && <p className="pf-intro">{intro}</p>}
      </div>
      <div className="pf-card-body">{children}</div>
    </Root>
  );
}

// Always rendered so the live region exists before its text changes.
export function FormAlert({ children }) {
  return (
    <div className="pf-alert-slot" role="alert">
      {children ? <p className="pf-alert">{children}</p> : null}
    </div>
  );
}
