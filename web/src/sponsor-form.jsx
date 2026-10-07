import React, { useEffect, useRef, useState } from 'react';
import SiteHeader from './home/SiteHeader.jsx';
import SiteFooter from './home/SiteFooter.jsx';
import { EVENT_DATES, EVENT_PLACE, SPONSOR_EMAIL } from './home/site.js';
import { partnerStars, renderStarField } from './home/stars.jsx';

const SPONSOR_FORM_ENDPOINT = import.meta.env.VITE_SPONSOR_FORM_ENDPOINT;
const WAYS_TO_PARTNER = ['Mentorship', 'Workshops', 'Prizes', 'Food'];

const emailLink = <a className="sponsor-card-link" href={`mailto:${SPONSOR_EMAIL}`}>{SPONSOR_EMAIL}</a>;

function SponsorForm() {
  // idle | submitting | success | error. A missing endpoint is not a status:
  // it is known up front, so the form is disabled before anyone fills it in.
  const [status, setStatus] = useState('idle');
  const [sentTo, setSentTo] = useState('');
  const successRef = useRef(null);
  const configured = Boolean(SPONSOR_FORM_ENDPOINT);
  const submitting = status === 'submitting';

  useEffect(() => {
    if (status === 'success') successRef.current?.focus();
  }, [status]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!configured || submitting) return;

    const form = event.target;
    const payload = Object.fromEntries(new FormData(form).entries());
    setStatus('submitting');

    try {
      const response = await fetch(SPONSOR_FORM_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!response.ok) throw new Error(`Request failed with status ${response.status}`);

      setSentTo(payload.email);
      setStatus('success');
    } catch {
      // What was typed stays in the form so it can be retried or copied.
      setStatus('error');
    }
  };

  return (
    <div className="sponsor-page">
      <a className="skip-link" href="#main">Skip to content</a>
      <SiteHeader />
      <main className="sponsor-main" id="main" tabIndex={-1}>
        {renderStarField('sponsor-stars', partnerStars)}
        <img className="sponsor-moon" src="/assets/moon.svg" alt="" aria-hidden="true" width="140" height="140" />
        <div className="sponsor-layout">
          <div className="sponsor-intro">
            <p className="sponsor-kicker">{EVENT_DATES} <span aria-hidden="true">·</span> {EVENT_PLACE}</p>
            <h1 id="sponsor-form-title">Sponsor PeachHacks.</h1>
            <p className="sponsor-lede">PeachHacks is a student-centered, beginner-friendly weekend hosted by ColorStack. We’re looking for partners who want to support the next generation of builders.</p>
            <ul className="sponsor-ways" aria-label="Ways to partner">
              {WAYS_TO_PARTNER.map((way) => <li key={way}>{way}</li>)}
            </ul>
            <p className="sponsor-direct">Prefer email? Reach the team at <a className="sponsor-link" href={`mailto:${SPONSOR_EMAIL}`}>{SPONSOR_EMAIL}</a>.</p>
          </div>

          <section className="sponsor-card" aria-labelledby="sponsor-card-title">
            {status === 'success' ? (
              <div className="sponsor-success" ref={successRef} tabIndex={-1} role="status">
                <span className="sponsor-success-mark" aria-hidden="true">✓</span>
                <h2 id="sponsor-card-title">Thank you, we got it.</h2>
                <p className="sponsor-success-copy">Your message is on its way to the PeachHacks team. We’ll reply{sentTo ? <> to <strong>{sentTo}</strong></> : null} soon.</p>
                <p className="sponsor-success-copy">Need to add something? Email {emailLink}.</p>
                <div className="sponsor-success-actions">
                  <a className="sponsor-submit" href="/">Back to PeachHacks</a>
                  <button type="button" className="sponsor-link-button" onClick={() => setStatus('idle')}>Send another message</button>
                </div>
              </div>
            ) : (
              <form id="sponsor-form" onSubmit={handleSubmit} aria-busy={submitting}>
                <h2 id="sponsor-card-title">Start the conversation</h2>
                <p className="sponsor-card-copy">Tell us a little about your organization and we’ll follow up with next steps.</p>

                {!configured && (
                  <p className="sponsor-notice" role="status">
                    <strong>The online form is unavailable right now.</strong>
                    <span>Please email {emailLink} and we’ll get right back to you.</span>
                  </p>
                )}
                {status === 'error' && (
                  <p className="sponsor-notice sponsor-notice-error" role="alert">
                    <strong>We couldn’t send your message.</strong>
                    <span>Nothing you typed was lost. Try again, or email {emailLink} directly.</span>
                  </p>
                )}

                <fieldset className="sponsor-fields" disabled={!configured || submitting}>
                  <div className="sponsor-field">
                    <label htmlFor="name">Organization name</label>
                    <input className="sponsor-input" type="text" id="name" name="name" autoComplete="organization" maxLength={200} required />
                  </div>

                  <div className="sponsor-field">
                    <label htmlFor="email">Contact email</label>
                    <input className="sponsor-input" type="email" id="email" name="email" autoComplete="email" placeholder="you@company.com" maxLength={254} required />
                  </div>

                  <div className="sponsor-field">
                    <label htmlFor="message">About your organization, and any questions <span className="sponsor-optional">(optional)</span></label>
                    <textarea className="sponsor-input" id="message" name="message" rows={5} maxLength={4000} />
                  </div>
                </fieldset>

                <div className="sponsor-actions">
                  <button type="submit" className={submitting ? 'sponsor-submit is-busy' : 'sponsor-submit'} disabled={!configured || submitting}>
                    {submitting && <span className="sponsor-spinner" aria-hidden="true" />}
                    {submitting ? 'Sending…' : 'Send message'}
                  </button>
                  {configured && (
                    <p className="sponsor-hint" role="status">
                      {submitting ? 'Sending your message…' : `Goes straight to ${SPONSOR_EMAIL}.`}
                    </p>
                  )}
                </div>
              </form>
            )}
          </section>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}

export default SponsorForm;
