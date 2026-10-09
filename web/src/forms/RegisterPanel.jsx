import { useCallback, useState } from 'react';
import { markRegistrationClosed, retryRegistrationStatus, useRegistrationStatus } from '../home/registration.js';
import { useFocusOnChange } from './hooks.js';
import { Card } from './PageShell.jsx';
import PreRegisterForm from './PreRegisterForm.jsx';
import RegisterForm from './RegisterForm.jsx';
import './forms.css';

const CLOSED_ON_SUBMIT_NOTICE = 'Registration closed while you were filling out the form, so your registration wasn’t '
  + 'submitted. Pre-register here and we’ll email you when it opens again.';

// The body of the homepage's sign-up panel, loaded on demand. A form only
// renders once the API has answered: the registration form while registration
// is open, the pre-registration form while it is closed. A failed status check
// shows a retry, never a form.
export default function RegisterPanel({ titleId, onClose, onDone }) {
  const status = useRegistrationStatus();
  const [registered, setRegistered] = useState(null);
  const [carriedOver, setCarriedOver] = useState(null);
  // Focus moves to the new heading when the view changes because of something
  // the visitor did (retry, submit), but not when the panel first opens.
  const { headingRef, requestFocus } = useFocusOnChange(status !== 'loading');

  const retry = () => {
    requestFocus();
    retryRegistrationStatus();
  };

  const handleSuccess = useCallback((email, schoolEmail) => {
    requestFocus();
    setRegistered({ email, schoolEmail });
    onDone();
  }, [onDone, requestFocus]);

  const handleClosed = useCallback((values) => {
    setCarriedOver({
      firstName: values.firstName,
      lastName: values.lastName,
      email: values.email,
      school: values.school,
      schoolEmail: values.schoolEmail,
    });
    markRegistrationClosed();
  }, []);

  if (registered) {
    return (
      <Card title="You're registered!" titleId={titleId} headingRef={headingRef} panel>
        <div className="pf-state">
          <img className="pf-state-art" src="/assets/peach.svg" alt="" aria-hidden="true" />
          <p>
            We got your registration and sent a confirmation to <strong>{registered.email}</strong>.
          </p>
          <p>
            One more step: unless you already confirmed it, check your school inbox (<strong>{registered.schoolEmail}</strong>)
            for a confirmation link and open it, so we know you&apos;re a current student.
          </p>
          <p className="pf-muted">If you don&apos;t see our emails, check your spam folders. We&apos;ll be in touch with next steps.</p>
          <button type="button" className="pf-button" onClick={onClose}>Back to PeachHacks</button>
        </div>
      </Card>
    );
  }

  if (status === 'loading') {
    return (
      <Card title="Sign up" titleId={titleId} headingRef={headingRef} panel>
        <div className="pf-state" role="status">
          <span className="pf-spinner" aria-hidden="true" />
          <p>Checking whether registration is open…</p>
        </div>
      </Card>
    );
  }

  if (status === 'error') {
    return (
      <Card title="We hit a snag" titleId={titleId} headingRef={headingRef} panel>
        <div className="pf-state">
          <p>We couldn&apos;t check whether registration is open. Check your connection and try again.</p>
          <button type="button" className="pf-button" onClick={retry}>Try again</button>
          <button type="button" className="pf-link-button" onClick={onClose}>Back to PeachHacks</button>
        </div>
      </Card>
    );
  }

  if (status === 'closed') {
    return (
      <PreRegisterForm
        titleId={titleId}
        onClose={onClose}
        onDone={onDone}
        notice={carriedOver ? CLOSED_ON_SUBMIT_NOTICE : null}
        initialValues={carriedOver}
      />
    );
  }

  return (
    <Card
      title="Register"
      titleId={titleId}
      headingRef={headingRef}
      intro={'Registration for PeachHacks is open. Fields marked with * are required.'}
      panel
    >
      <RegisterForm titleId={titleId} onSuccess={handleSuccess} onClosed={handleClosed} />
    </Card>
  );
}
