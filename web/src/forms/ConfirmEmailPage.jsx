import { useEffect, useRef, useState } from 'react';
import { confirmSchoolEmail, resendSchoolEmailConfirmation } from './api.js';
import { SubmitButton, TextField } from './fields.jsx';
import { CONTACT_EMAIL } from '../home/site.js';
import { useFocusOnChange } from './hooks.js';
import { readQueryParam } from './page.jsx';
import { Card, FormAlert, PageShell } from './PageShell.jsx';
import { describeFailure, focusFirstInvalid, isEmail } from './validation.js';

const TITLE_ID = 'confirm-email-title';

export default function ConfirmEmailPage() {
  const [token] = useState(() => readQueryParam('token'));
  // confirm | submitting | done | invalid | resending | resent
  const [view, setView] = useState(token ? 'confirm' : 'invalid');
  const [schoolEmail, setSchoolEmail] = useState('');
  const [failure, setFailure] = useState('');
  const [retrying, setRetrying] = useState(false);
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const formRef = useRef(null);
  const { headingRef, requestFocus } = useFocusOnChange(view !== 'submitting' && view !== 'resending');

  useEffect(() => {
    if (emailError) focusFirstInvalid(formRef.current);
  }, [emailError]);

  // Confirming needs this click: mail scanners open links, and opening the page
  // alone must not confirm anything.
  const handleConfirm = async () => {
    if (view === 'submitting') return;
    setView('submitting');
    setFailure('');
    try {
      const result = await confirmSchoolEmail(token);
      setSchoolEmail(typeof result?.schoolEmail === 'string' ? result.schoolEmail : '');
      requestFocus();
      setView('done');
    } catch (error) {
      // 404 is an unknown or expired token; 400 means the token was malformed.
      if (error?.code === 'NOT_FOUND' || error?.code === 'VALIDATION_ERROR') {
        requestFocus();
        setView('invalid');
      } else {
        setFailure(describeFailure(error));
        setRetrying(true);
        setView('confirm');
      }
    }
  };

  const handleResend = async (event) => {
    event.preventDefault();
    if (view === 'resending') return;
    if (!isEmail(email)) {
      setEmailError(email.trim() ? 'Enter a valid email, like name@example.com.' : 'Enter your personal email address.');
      return;
    }
    setView('resending');
    setFailure('');
    try {
      await resendSchoolEmailConfirmation(email.trim());
      requestFocus();
      setView('resent');
    } catch (error) {
      setFailure(describeFailure(error));
      setView('invalid');
    }
  };

  let card;

  if (view === 'done') {
    card = (
      <Card title="School email confirmed" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <img className="pf-state-art" src="/assets/peach.svg" alt="" aria-hidden="true" />
          <p>
            {schoolEmail ? (
              <>Thanks! <strong>{schoolEmail}</strong> is confirmed as your school email.</>
            ) : (
              <>Thanks! Your school email is confirmed.</>
            )}
          </p>
          <p className="pf-muted">That&apos;s all we needed. You can close this page.</p>
          <a className="pf-button" href="/">Back to PeachHacks</a>
        </div>
      </Card>
    );
  } else if (view === 'resent') {
    card = (
      <Card title="Check your school inbox" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <p>If that email is registered, we sent a new link to its school address.</p>
          <p className="pf-muted">
            It can take a few minutes, and we send at most one link every 10 minutes. Nothing there? Check the spam
            folder, or email <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
          </p>
          <a className="pf-button" href="/">Back to PeachHacks</a>
        </div>
      </Card>
    );
  } else if (view === 'invalid' || view === 'resending') {
    const pending = view === 'resending';
    card = (
      <Card title="This link didn't work" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <p>
            This confirmation link is invalid or has expired. Links work for 14 days. Enter the personal email you
            signed up with and we&apos;ll send a new link to your school address.
          </p>
        </div>
        <form className="pf-form" ref={formRef} onSubmit={handleResend} noValidate aria-label="Send a new confirmation link">
          <TextField
            name="email" label="Personal email" value={email} error={emailError}
            onChange={(_name, value) => {
              setEmail(value);
              setEmailError('');
            }}
            hint="Not your school email: the one we send your registration news to."
            type="email" inputMode="email" autoComplete="email" autoCapitalize="none" spellCheck={false} maxLength={255}
          />
          <div className="pf-actions">
            <SubmitButton pending={pending} pendingLabel="Sending…">Send a new link</SubmitButton>
            <FormAlert>{failure}</FormAlert>
          </div>
        </form>
      </Card>
    );
  } else {
    const pending = view === 'submitting';
    card = (
      <Card title="Confirm your school email" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <p>
            PeachHacks is open to current university students. Confirm that the school email you gave us is yours.
          </p>
          <button type="button" className="pf-button" onClick={handleConfirm} disabled={pending} aria-busy={pending || undefined}>
            {pending ? 'Confirming…' : retrying ? 'Try again' : 'Confirm my school email'}
          </button>
          <a className="pf-text-link" href="/">Not you? Back to PeachHacks</a>
          <FormAlert>{failure}</FormAlert>
        </div>
      </Card>
    );
  }

  return <PageShell>{card}</PageShell>;
}
