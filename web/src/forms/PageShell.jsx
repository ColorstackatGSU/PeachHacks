/* eslint-disable react/prop-types -- the project has no prop-types dependency and React 19 ignores propTypes */
import { CONTACT_EMAIL } from './options.js';

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
      </footer>
    </div>
  );
}

export function Card({ title, titleId, intro, children, headingRef }) {
  return (
    <section className="pf-card" aria-labelledby={titleId}>
      <div className="pf-tag">
        <span className="pf-eyelet" aria-hidden="true" />
        <h1 id={titleId} tabIndex={-1} ref={headingRef}>{title}</h1>
        {intro && <p className="pf-intro">{intro}</p>}
      </div>
      <div className="pf-card-body">{children}</div>
    </section>
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
