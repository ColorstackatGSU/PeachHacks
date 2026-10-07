import { useCallback, useEffect, useRef, useState } from 'react';
import { getRegistrationStatus } from './api.js';
import { Card, PageShell } from './PageShell.jsx';
import RegisterForm from './RegisterForm.jsx';

const TITLE_ID = 'register-title';

// The form only renders once the API confirms registration is open. A failed
// status check shows a retry, never the form.
export default function RegisterPage() {
  // checking | open | closed | closed-on-submit | error | success
  const [view, setView] = useState('checking');
  const [attempt, setAttempt] = useState(0);
  const [registeredEmail, setRegisteredEmail] = useState('');
  const [schoolEmail, setSchoolEmail] = useState('');
  const headingRef = useRef(null);
  const moveFocus = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    getRegistrationStatus(controller.signal).then(
      (open) => setView(open ? 'open' : 'closed'),
      (error) => {
        if (error?.name !== 'AbortError') setView('error');
      },
    );
    return () => controller.abort();
  }, [attempt]);

  // Move focus to the new heading when the view changes because of something
  // the visitor did (retry, submit), but not on the initial page load.
  useEffect(() => {
    if (!moveFocus.current || view === 'checking') return;
    moveFocus.current = false;
    headingRef.current?.focus();
  }, [view]);

  const retry = () => {
    moveFocus.current = true;
    setView('checking');
    setAttempt((count) => count + 1);
  };

  const handleSuccess = useCallback((email, registeredSchoolEmail) => {
    moveFocus.current = true;
    setRegisteredEmail(email);
    setSchoolEmail(registeredSchoolEmail);
    setView('success');
  }, []);

  const handleClosed = useCallback(() => {
    moveFocus.current = true;
    setView('closed-on-submit');
  }, []);

  let card;

  if (view === 'checking') {
    card = (
      <Card title="Register" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state" role="status">
          <span className="pf-spinner" aria-hidden="true" />
          <p>Checking whether registration is open…</p>
        </div>
      </Card>
    );
  } else if (view === 'error') {
    card = (
      <Card title="We hit a snag" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <p>We couldn&apos;t check whether registration is open. Check your connection and try again.</p>
          <button type="button" className="pf-button" onClick={retry}>Try again</button>
          <a className="pf-text-link" href="/">Back to PeachHacks</a>
        </div>
      </Card>
    );
  } else if (view === 'closed' || view === 'closed-on-submit') {
    card = (
      <Card title="Registration isn't open yet" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <img className="pf-state-art" src="/assets/peach.svg" alt="" aria-hidden="true" />
          {view === 'closed-on-submit' ? (
            <p>
              Registration closed while you were filling out the form, so your registration wasn&apos;t submitted.
              Pre-register and we&apos;ll email you when it opens again.
            </p>
          ) : (
            <p>
              We&apos;re still getting things ready. Pre-register now and we&apos;ll email you the moment
              registration opens.
            </p>
          )}
          <a className="pf-button" href="/pre-register">Pre-register</a>
          <a className="pf-text-link" href="/">Back to PeachHacks</a>
        </div>
      </Card>
    );
  } else if (view === 'success') {
    card = (
      <Card title="You're registered!" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <img className="pf-state-art" src="/assets/peach.svg" alt="" aria-hidden="true" />
          <p>
            We got your registration and sent a confirmation to <strong>{registeredEmail}</strong>.
          </p>
          <p>
            One more step: unless you already confirmed it, check your school inbox (<strong>{schoolEmail}</strong>)
            for a confirmation link and open it, so we know you&apos;re a current student.
          </p>
          <p className="pf-muted">If you don&apos;t see our emails, check your spam folders. We&apos;ll be in touch with next steps.</p>
          <a className="pf-button" href="/">Back to PeachHacks</a>
        </div>
      </Card>
    );
  } else {
    card = (
      <Card
        title="Register"
        titleId={TITLE_ID}
        headingRef={headingRef}
        intro="Registration for PeachHacks is open. The first part is required and takes a couple of minutes; the rest is optional."
      >
        <RegisterForm titleId={TITLE_ID} onSuccess={handleSuccess} onClosed={handleClosed} />
      </Card>
    );
  }

  return <PageShell>{card}</PageShell>;
}
