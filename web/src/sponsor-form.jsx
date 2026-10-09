import React, { useEffect, useRef, useState } from 'react';
import SiteHeader from './home/SiteHeader.jsx';
import SiteFooter from './home/SiteFooter.jsx';
import { EVENT_DATES, EVENT_PLACE, SPONSOR_EMAIL } from './home/site.js';
import { partnerStars, renderStarField } from './home/stars.jsx';
import { submitSponsorInquiry } from './forms/api.js';

const WAYS_TO_PARTNER = ['Mentorship', 'Workshops', 'Prizes', 'Food'];

const emailLink = <a className="sponsor-card-link" href={`mailto:${SPONSOR_EMAIL}`}>{SPONSOR_EMAIL}</a>;

function SponsorForm() {
  // idle | submitting | success | error
  const [status, setStatus] = useState('idle');
  const [sentTo, setSentTo] = useState('');
  // The API's own wording when it rejected the input (a field rule, a rate limit).
  const [errorDetail, setErrorDetail] = useState(null);
  const successRef = useRef(null);
  const submitting = status === 'submitting';

  useEffect(() => {
    if (status === 'success') successRef.current?.focus();
  }, [status]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;

    const data = new FormData(event.target);
    const payload = {
      organization: data.get('organization'),
      name: data.get('name'),
      email: data.get('email'),
      message: data.get('message'),
      website: data.get('leave-blank'),
    };
    setStatus('submitting');
    setErrorDetail(null);

    try {
      await submitSponsorInquiry(payload);
      setSentTo(payload.email);
      setStatus('success');
    } catch (error) {
      // What was typed stays in the form so it can be retried or copied.
      const fieldMessage = Object.values(error.fieldErrors ?? {})[0];
      setErrorDetail(error.status >= 400 && error.status < 500 ? fieldMessage ?? error.serverMessage : null);
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

                {status === 'error' && (
                  <p className="sponsor-notice sponsor-notice-error" role="alert">
                    <strong>{errorDetail ?? 'We couldn’t send your message.'}</strong>
                    <span>Nothing you typed was lost. Try again, or email {emailLink} directly.</span>
                  </p>
                )}

                <fieldset className="sponsor-fields" disabled={submitting}>
                  <div className="sponsor-field">
                    <label htmlFor="organization">Organization name</label>
                    <input className="sponsor-input" type="text" id="organization" name="organization" autoComplete="organization" maxLength={200} required />
                  </div>

                  <div className="sponsor-field">
                    <label htmlFor="name">Your name</label>
                    <input className="sponsor-input" type="text" id="name" name="name" autoComplete="name" maxLength={100} required />
                  </div>

                  <div className="sponsor-field">
                    <label htmlFor="email">Contact email</label>
                    <input className="sponsor-input" type="email" id="email" name="email" autoComplete="email" placeholder="you@company.com" maxLength={254} required />
                  </div>

                  <div className="sponsor-field">
                    <label htmlFor="message">About your organization, and any questions <span className="sponsor-optional">(optional)</span></label>
                    <textarea className="sponsor-input" id="message" name="message" rows={5} maxLength={4000} />
                  </div>

                  {/* Honeypot: hidden from people, sent as `website`; the API drops any submission that fills it. */}
                  <div className="sponsor-trap" aria-hidden="true">
                    <label htmlFor="sponsor-leave-blank">Leave this field empty</label>
                    <input type="text" id="sponsor-leave-blank" name="leave-blank" tabIndex={-1} autoComplete="off" />
                  </div>
                </fieldset>

                <div className="sponsor-actions">
                  <button type="submit" className={submitting ? 'sponsor-submit is-busy' : 'sponsor-submit'} disabled={submitting}>
                    {submitting && <span className="sponsor-spinner" aria-hidden="true" />}
                    {submitting ? 'Sending…' : 'Send message'}
                  </button>
                  <p className="sponsor-hint" role="status">
                    {submitting ? 'Sending your message…' : `Goes straight to ${SPONSOR_EMAIL}.`}
                  </p>
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
