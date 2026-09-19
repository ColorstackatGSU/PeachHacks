import React, { useRef } from 'react';

function SponsorForm() {
  const growWrapRef = useRef(null);

  const handleMessageInput = (event) => {
    if (growWrapRef.current) {
      growWrapRef.current.dataset.replicatedValue = event.target.value;
    }
  };

  return (
    <section className="form-section" aria-labelledby="sponsor-form-title">
      <a className="form-logo-link" href="/" aria-label="Back to PeachHacks home">
        <img className="form-logo" src="/assets/logo.svg" alt="PeachHacks" />
      </a>
      <form className="form" id="sponsor-form">
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

        <button type="submit" className="form-submit">Submit</button>
      </form>
    </section>
  );
}

export default SponsorForm;
