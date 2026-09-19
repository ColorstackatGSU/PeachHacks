import React from 'react';

function InterestForm() {
  return (
    <section className="interest-form-section" aria-labelledby="interest-form-title">
      <a className="form-logo-link" href="/" aria-label="Back to PeachHacks home">
        <img className="form-logo" src="/assets/logo.svg" alt="PeachHacks" />
      </a>
      <form className="interest-form" id="interest-form">
        <h1 id="interest-form-title">Peachhacks Interest Form</h1>
        <p className="interest-form-copy">Sign up to receive updates about Peachhacks as event details are announced.</p>

        <div className="form-field">
          <label htmlFor="name">Name:</label>
          <input type="text" id="name" name="name" required />
        </div>

        <div className="form-field">
          <label htmlFor="email">Email:</label>
          <input type="email" id="email" name="email" placeholder="example@email.com" required />
        </div>

        <div className="form-field">
          <label htmlFor="phone">Phone Number (optional):</label>
          <input type="tel" id="phone" name="phone" />
        </div>

        <button type="submit" className="form-submit">Submit</button>
      </form>
    </section>
  );
}

export default InterestForm;
