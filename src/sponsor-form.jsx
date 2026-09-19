import React, { useRef, useState } from 'react';

const SPONSOR_FORM_ENDPOINT = import.meta.env.VITE_SPONSOR_FORM_ENDPOINT;

function SponsorForm() {
  const growWrapRef = useRef(null);
  const [status, setStatus] = useState('idle');

  const handleMessageInput = (event) => {
    if (growWrapRef.current) {
      growWrapRef.current.dataset.replicatedValue = event.target.value;
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!SPONSOR_FORM_ENDPOINT) {
      console.error('VITE_SPONSOR_FORM_ENDPOINT is not set; sponsor form cannot submit.');
      setStatus('error');
      return;
    }

    const payload = Object.fromEntries(new FormData(event.target).entries());
    setStatus('submitting');

    try {
      const response = await fetch(SPONSOR_FORM_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!response.ok) throw new Error(`Request failed with status ${response.status}`);

      setStatus('success');
      event.target.reset();
      if (growWrapRef.current) growWrapRef.current.dataset.replicatedValue = '';
    } catch (error) {
      console.error('Sponsor form submission failed:', error);
      setStatus('error');
    }
  };

  return (
    <section className="form-section" aria-labelledby="sponsor-form-title">
      <a className="form-logo-link" href="/" aria-label="Back to PeachHacks home">
        <img className="form-logo" src="/assets/logo.svg" alt="PeachHacks" />
      </a>
      <form className="form" id="sponsor-form" onSubmit={handleSubmit}>
        <h1 id="sponsor-form-title">Peachhacks Sponsor Form</h1>
        <p className="form-copy">
          Thank you for your interest in sponsoring Peachhacks! Fill out this sponsor form to get in contact with us.
        </p>
        <p className="form-copy">
          Submissions will be emailed to <a className="sponsor-email" href="mailto:sponsors@peachhacks.org">sponsors@peachhacks.org</a>
        </p>

        <div className="form-field">
          <label htmlFor="name">Organization name:</label>
          <input type="text" id="name" name="name" required />
        </div>

        <div className="form-field">
          <label htmlFor="email">Email:</label>
          <input type="email" id="email" name="email" placeholder="example@email.com" required />
        </div>

        <div className="form-field">
          <label htmlFor="message">Tell us about your organization and any questions you have:</label>
          <div className="grow-wrap" ref={growWrapRef}>
            <textarea id="message" name="message" rows={5} onInput={handleMessageInput}></textarea>
          </div>
        </div>

        <button type="submit" className="form-submit" disabled={status === 'submitting'}>
          {status === 'submitting' ? 'Submitting…' : 'Submit'}
        </button>

        {status === 'success' && (
          <p className="form-status form-status-success" role="status">
            Thanks! We&apos;ll be in touch soon.
          </p>
        )}
        {status === 'error' && (
          <p className="form-status form-status-error" role="alert">
            Something went wrong. Please email us directly at{' '}
            <a className="sponsor-email" href="mailto:sponsors@peachhacks.org">sponsors@peachhacks.org</a>.
          </p>
        )}
      </form>
    </section>
  );
}

export default SponsorForm;
