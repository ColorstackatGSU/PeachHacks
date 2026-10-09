import { useCallback, useEffect, useState } from "react";
import { api, onAcceptanceChange } from "../api/client.js";
import { BarList } from "../components/Charts.jsx";
import { ShareHeadline, ShareRow } from "../components/HostShare.jsx";
import { ConfirmDialog } from "../components/Modal.jsx";
import { EmptyBlock, ErrorBlock, LoadingBlock, PageHeader, Spinner, Tag, Tile, useToast } from "../components/ui.jsx";
import { ageReviewLabel, ageRuleText, formatShare, formatTarget, gapText } from "../lib/acceptance.js";
import { errorText, formatDateTime, formatNumber, fullName, plural } from "../lib/format.js";
import { useAcceptanceSummary, useAsync } from "../lib/hooks.js";
import { href } from "../lib/router.js";

const loadWaiting = (signal) => api.acceptancesWaiting(signal);

function SendPanel({ summary, onSent }) {
  const notify = useToast();
  const [confirming, setConfirming] = useState(false);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState(null);
  const send = summary.send;
  const waiting = summary.totals.acceptedWaiting;
  const share = summary.shares.accepted;
  const sending = send.state === "SENDING";
  const done = send.sent + send.failed + send.skipped;

  const start = async () => {
    setStarting(true);
    setError(null);
    try {
      const result = await api.sendAcceptanceEmails();
      setConfirming(false);
      notify(
        result.queued > 0
          ? `Sending ${plural(result.queued, "acceptance email")}.`
          : "Nobody is waiting, so nothing was sent.",
      );
      onSent();
    } catch (err) {
      setError(err);
    } finally {
      setStarting(false);
    }
  };

  return (
    <section className="card" aria-labelledby="send-heading">
      <div className="card-head">
        <h2 id="send-heading">Send acceptance emails</h2>
        <span className="muted">{plural(waiting, "person", "people")} waiting to be told</span>
      </div>
      <p className="muted">
        Accepting someone sends nothing. They wait here until you send the acceptance emails, which go to everyone
        waiting at once: the “You’re in” message with their ticket QR code. Each person is emailed once.
      </p>

      {sending && (
        <div className="send-progress" role="status">
          <strong>
            Sending: {formatNumber(done)} of {formatNumber(send.queued)}
          </strong>
          <div className="progress" aria-hidden="true">
            <span style={{ width: `${send.queued > 0 ? (done / send.queued) * 100 : 0}%` }} />
          </div>
          <span className="muted small">
            {formatNumber(send.sent)} sent, {formatNumber(send.failed)} failed
            {send.startedBy ? `. Started by ${send.startedBy}` : ""}. You can leave this screen; sending continues.
          </span>
        </div>
      )}
      {!sending && send.finishedAt && (
        <p className={`notice${send.failed > 0 ? " notice-warn" : ""}`} role="status">
          <strong>Last send finished {formatDateTime(send.finishedAt)}:</strong> {formatNumber(send.sent)} sent,{" "}
          {formatNumber(send.failed)} failed
          {send.skipped > 0 ? `, ${formatNumber(send.skipped)} skipped because they were no longer waiting` : ""}.
          {send.failed > 0 && " The people whose email failed are still waiting below; send again to retry them."}
        </p>
      )}

      <div className="composer-actions">
        <button type="button" className="btn btn-primary" disabled={sending || waiting === 0} onClick={() => setConfirming(true)}>
          {sending ? "Sending…" : waiting === 0 ? "Nobody is waiting" : `Send to ${plural(waiting, "person", "people")}`}
        </button>
      </div>

      {confirming && (
        <ConfirmDialog
          title="Send the acceptance emails?"
          confirmLabel={`Email ${plural(waiting, "person", "people")}`}
          busy={starting}
          error={error}
          onConfirm={start}
          onCancel={() => {
            setConfirming(false);
            setError(null);
          }}
        >
          <p>
            <strong>{plural(waiting, "person", "people")}</strong> will be emailed their acceptance and ticket now. This
            cannot be taken back once it starts.
          </p>
          {!share.met && (
            <p className="notice notice-warn">
              <strong>The host-school target is not met.</strong> {formatShare(share.share)} of accepted hackers are from{" "}
              {summary.hostSchool.name}; the target is {formatTarget(summary.hostSchool.target)} (
              {gapText(share, summary.hostSchool.name).toLowerCase()}). You can still send.
            </p>
          )}
        </ConfirmDialog>
      )}
    </section>
  );
}

function Bucket({ waiting, flagLabel }) {
  const notify = useToast();
  const [moving, setMoving] = useState(null);
  const items = waiting.data || [];

  const moveBack = async (person) => {
    setMoving(person.id);
    try {
      await api.setRegistrationStatus(person.id, "PENDING");
      notify(`${fullName(person)} moved back to pending.`);
    } catch (error) {
      notify(`Could not move ${fullName(person)}: ${errorText(error)}`, "error");
    } finally {
      setMoving(null);
    }
  };

  return (
    <section className="card" aria-labelledby="bucket-heading">
      <div className="card-head">
        <h2 id="bucket-heading">Accepted, not yet told</h2>
        <a className="tile-link" href={href("/registrations")}>
          Accept more in Registrations
        </a>
      </div>
      {waiting.error && <ErrorBlock error={waiting.error} onRetry={waiting.reload} />}
      {!waiting.error && !waiting.data && <LoadingBlock label="Loading the bucket…" />}
      {!waiting.error && waiting.data && items.length === 0 && (
        <EmptyBlock title="Nobody is waiting">Everyone who is accepted has been sent their acceptance email.</EmptyBlock>
      )}
      {items.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <caption className="sr-only">Accepted registrations whose acceptance email has not been sent</caption>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
                <th scope="col">School</th>
                <th scope="col">Accepted</th>
                <th scope="col">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {items.map((person) => (
                <tr key={person.id}>
                  <th scope="row" data-label="Name">
                    {fullName(person)}
                  </th>
                  <td data-label="Email" className="cell-break">
                    {person.email}
                  </td>
                  <td data-label="School">
                    {person.school} {person.host && <Tag tone="accepted">Host school</Tag>}
                    {person.ageReview && <span className="cell-note cell-flag">{flagLabel}</span>}
                  </td>
                  <td data-label="Accepted" className="cell-nowrap">
                    {person.acceptedAt ? formatDateTime(person.acceptedAt) : <span className="muted">Not recorded</span>}
                  </td>
                  <td className="cell-actions">
                    <button type="button" className="btn btn-small" disabled={moving !== null} onClick={() => moveBack(person)}>
                      {moving === person.id ? "Moving…" : "Move back to pending"}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export default function Acceptances() {
  const summary = useAcceptanceSummary();
  const waiting = useAsync(loadWaiting);
  const data = summary.data;
  const reloadSummary = summary.reload;
  const reloadWaiting = waiting.reload;
  const reloadAll = useCallback(() => {
    reloadSummary();
    reloadWaiting();
  }, [reloadSummary, reloadWaiting]);

  useEffect(() => onAcceptanceChange(reloadWaiting), [reloadWaiting]);
  // The bucket also changes without a click here: a send works through it, and another
  // organizer may be accepting people. The summary is polled, so follow its counts.
  const waitingCount = data?.totals.acceptedWaiting;
  const notifiedCount = data?.totals.acceptedNotified;
  useEffect(() => {
    if (waitingCount !== undefined) reloadWaiting();
  }, [waitingCount, notifiedCount, reloadWaiting]);

  const description = "Who is accepted, how much of that is the host school, and the emails that tell them.";
  if (!data) {
    return (
      <>
        <PageHeader title="Acceptances" description={description} />
        {summary.error ? <ErrorBlock error={summary.error} onRetry={summary.reload} /> : <LoadingBlock label="Loading acceptances…" />}
      </>
    );
  }

  const { totals, shares, hostSchool } = data;
  const flaggedAccepted = data.ageReview?.accepted || 0;
  return (
    <>
      <PageHeader title="Acceptances" description={description}>
        <button type="button" className="btn" onClick={reloadAll} disabled={summary.loading}>
          {summary.loading ? <Spinner label="Refreshing" /> : null}
          Refresh
        </button>
      </PageHeader>

      {summary.error && (
        <ErrorBlock title="Could not refresh; showing the last numbers loaded" error={summary.error} onRetry={summary.reload} />
      )}

      {flaggedAccepted > 0 && (
        <p className="notice notice-warn" role="note">
          <strong>
            {plural(flaggedAccepted, "accepted person is", "accepted people are")} under {data.ageReview.minimumAge} and not
            at {hostSchool.name}.
          </strong>{" "}
          {ageRuleText(data)} Nothing is blocked; check that they are eligible.{" "}
          <a href={href("/registrations?status=ACCEPTED&ageReview=true")}>Review them in Registrations</a>
        </p>
      )}

      <section className="card" aria-labelledby="share-heading">
        <div className="card-head">
          <h2 id="share-heading">Host-school share</h2>
          <span className="muted">
            At least {formatTarget(hostSchool.target)} of attendees must be from {hostSchool.name}
          </span>
        </div>
        <div className="share-layout">
          <ShareHeadline share={shares.accepted} hostSchool={hostSchool} />
          <div className="share-rows">
            <ShareRow label="Checked in (who actually came)" share={shares.checkedIn} target={hostSchool.target} />
            <ShareRow label="Still pending (who you can accept)" share={shares.pending} target={hostSchool.target} />
            <ShareRow label="All registrations" share={shares.registrations} target={hostSchool.target} />
          </div>
        </div>
      </section>

      <section className="tiles" aria-label="Acceptance numbers">
        <Tile label="Registrations" value={totals.registrations} />
        <Tile
          label="Accepted"
          value={totals.accepted}
          note={totals.acceptanceRate === null ? "No registrations yet" : `${formatShare(totals.acceptanceRate)} of registrations`}
        />
        <Tile label="Waiting to be told" value={totals.acceptedWaiting} note="Accepted, no email yet" />
        <Tile label="Told" value={totals.acceptedNotified} note="Acceptance email sent" />
        <Tile label="Pending" value={totals.pending} />
        <Tile label="Waitlisted" value={totals.waitlisted} />
        <Tile label="Rejected" value={totals.rejected} />
      </section>

      <SendPanel summary={data} onSent={reloadAll} />
      <Bucket waiting={waiting} flagLabel={ageReviewLabel(data)} />

      <section className="card" aria-labelledby="accepted-school-heading">
        <div className="card-head">
          <h2 id="accepted-school-heading">Accepted by school</h2>
          <span className="muted">{plural(data.acceptedBySchool.length, "school")}</span>
        </div>
        <BarList rows={data.acceptedBySchool} labelKey="school" total={totals.accepted} tone="sky" emptyText="Nobody is accepted yet." />
      </section>
    </>
  );
}
