import { useState } from 'react';
import { unsubscribe } from './api.js';
import { CONTACT_EMAIL } from '../home/site.js';
import { useFocusOnChange } from './hooks.js';
import { readQueryParam } from './page.jsx';
import { Card, FormAlert, PageShell } from './PageShell.jsx';
import { describeFailure } from './validation.js';

const TITLE_ID = 'unsubscribe-title';

export default function UnsubscribePage() {
  const [token] = useState(() => readQueryParam('token'));
  // confirm | submitting | done | invalid
  const [view, setView] = useState(token ? 'confirm' : 'invalid');
  const [failure, setFailure] = useState('');
  const { headingRef, requestFocus } = useFocusOnChange(view !== 'submitting');

  const handleConfirm = async () => {
    if (view === 'submitting') return;
    setView('submitting');
    setFailure('');
    try {
      await unsubscribe(token);
      requestFocus();
      setView('done');
    } catch (error) {
      // 404 is an unknown token; 400 means the token was malformed.
      if (error?.code === 'NOT_FOUND' || error?.code === 'VALIDATION_ERROR') {
        requestFocus();
        setView('invalid');
      } else {
        setFailure(describeFailure(error));
        setView('confirm');
      }
    }
  };

  let card;

  if (view === 'done') {
    card = (
      <Card title="You're unsubscribed" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <p>We won&apos;t send you any more PeachHacks announcement emails.</p>
          <p className="pf-muted">
            Changed your mind? Email <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> and we&apos;ll add you back.
          </p>
          <a className="pf-button" href="/">Back to PeachHacks</a>
        </div>
      </Card>
    );
  } else if (view === 'invalid') {
    card = (
      <Card title="This link didn't work" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <p>
            This unsubscribe link is invalid or incomplete. Try opening it again straight from the email, or copy the
            whole link into your browser.
          </p>
          <p className="pf-muted">
            Still stuck? Email <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a> and we&apos;ll take you off the
            list.
          </p>
          <a className="pf-button" href="/">Back to PeachHacks</a>
        </div>
      </Card>
    );
  } else {
    const pending = view === 'submitting';
    card = (
      <Card title="Unsubscribe" titleId={TITLE_ID} headingRef={headingRef}>
        <div className="pf-state">
          <p>Stop receiving PeachHacks announcement emails at this address?</p>
          <button type="button" className="pf-button" onClick={handleConfirm} disabled={pending} aria-busy={pending || undefined}>
            {pending ? 'Unsubscribing…' : 'Yes, unsubscribe me'}
          </button>
          <a className="pf-text-link" href="/">No, keep me subscribed</a>
          <FormAlert>{failure}</FormAlert>
        </div>
      </Card>
    );
  }

  return <PageShell>{card}</PageShell>;
}
