import { useEffect, useState } from 'react';
import { API_BASE_URL, getTicket } from './api.js';
import { CONTACT_EMAIL, EVENT_DATES } from '../home/site.js';
import { readQueryParam } from './page.jsx';
import { Card, PageShell } from './PageShell.jsx';
import { describeFailure } from './validation.js';

const TITLE_ID = 'ticket-title';

export default function TicketPage() {
  const [token] = useState(() => readQueryParam('t'));
  // loading | ready | invalid | error
  const [view, setView] = useState(token ? 'loading' : 'invalid');
  const [ticket, setTicket] = useState(null);
  const [failure, setFailure] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!token) return undefined;
    const controller = new AbortController();
    getTicket(token, controller.signal)
      .then((data) => {
        setTicket(data);
        setView('ready');
      })
      .catch((error) => {
        if (error?.name === 'AbortError') return;
        if (error?.code === 'NOT_FOUND') {
          setView('invalid');
          return;
        }
        setFailure(describeFailure(error));
        setView('error');
      });
    return () => controller.abort();
  }, [token, attempt]);

  const retry = () => {
    setView('loading');
    setAttempt((n) => n + 1);
  };

  let card;

  if (view === 'ready') {
    const name = `${ticket.firstName} ${ticket.lastName}`.trim();
    card = (
      <Card title="Your ticket" titleId={TITLE_ID}>
        <div className="pf-state">
          <p className="pf-ticket-name">{name}</p>
          <p className="pf-muted">{ticket.school}</p>
          {ticket.checkedIn && <p className="pf-ticket-badge">Checked in</p>}
          <img
            className="pf-ticket-qr"
            src={`${API_BASE_URL}/public/tickets/${encodeURIComponent(token)}/qr.png`}
            alt={`Check-in QR code for ${name}`}
            width="280"
            height="280"
          />
          <p>Show this code at check-in and at workshops. Turning your screen brightness up helps it scan.</p>
          {ticket.googleWalletUrl && (
            <a className="pf-button" href={ticket.googleWalletUrl}>Add to Google Wallet</a>
          )}
          <p className="pf-muted">
            PeachHacks · {EVENT_DATES} · Atlanta, GA. Bookmark this page or take a screenshot so you have it
            offline.
          </p>
        </div>
      </Card>
    );
  } else if (view === 'invalid') {
    card = (
      <Card title="We couldn't find this ticket" titleId={TITLE_ID}>
        <div className="pf-state">
          <p>
            This ticket link is incomplete or no longer valid. Open it again straight from your acceptance email, or
            copy the whole link into your browser.
          </p>
          <p className="pf-muted">
            Still stuck? Email <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
          </p>
          <a className="pf-button" href="/">Back to PeachHacks</a>
        </div>
      </Card>
    );
  } else if (view === 'error') {
    card = (
      <Card title="Your ticket didn't load" titleId={TITLE_ID}>
        <div className="pf-state">
          <p>{failure}</p>
          <button type="button" className="pf-button" onClick={retry}>Try again</button>
        </div>
      </Card>
    );
  } else {
    card = (
      <Card title="Your ticket" titleId={TITLE_ID}>
        <div className="pf-state" role="status">
          <p>Loading your ticket…</p>
        </div>
      </Card>
    );
  }

  return <PageShell>{card}</PageShell>;
}
