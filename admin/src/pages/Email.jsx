import { useCallback, useEffect, useId, useRef, useState } from "react";
import { WEB_BASE, api } from "../api/client.js";
import { ConfirmDialog } from "../components/Modal.jsx";
import { EmptyBlock, ErrorBlock, LoadingBlock, PageHeader, Spinner, Tag, useToast } from "../components/ui.jsx";
import {
  AUDIENCES,
  CAMPAIGN_KINDS,
  audienceLabel,
  audiencesFor,
  campaignKind,
  errorText,
  formatDateTime,
  formatNumber,
  plural,
} from "../lib/format.js";
import { schoolOptions, useAsync, useDebounced, useStats } from "../lib/hooks.js";

export const REGISTRATION_OPEN_TEMPLATE = {
  kind: "ANNOUNCEMENT",
  audience: "PRE_REGISTRANTS_NOT_REGISTERED",
  subject: "PeachHacks registration is now open",
  body: [
    "Hi {{firstName}},",
    "You pre-registered for PeachHacks, and we promised you would be the first to know: registration is now open.",
    `Complete your registration here:\n${WEB_BASE}/#register`,
    "It only takes a few minutes. Pre-registering does not hold a spot on its own, so please finish the full registration to be considered.",
    "See you in Atlanta,\nThe PeachHacks team",
  ].join("\n\n"),
};

const KNOWN_PLACEHOLDERS = ["firstName", "lastName"];
const isAudience = (kind, value) => audiencesFor(kind).some((a) => a.value === value);

function initialDraft(query) {
  const useTemplate = query.get("template") === "registration-open";
  const kind = !useTemplate && query.get("kind") === "EVENT_UPDATE" ? "EVENT_UPDATE" : "ANNOUNCEMENT";
  const requested = query.get("audience");
  const fallback = useTemplate ? REGISTRATION_OPEN_TEMPLATE.audience : audiencesFor(kind)[0].value;
  return {
    kind,
    audience: isAudience(kind, requested) ? requested : fallback,
    school: "",
    subject: useTemplate ? REGISTRATION_OPEN_TEMPLATE.subject : "",
    body: useTemplate ? REGISTRATION_OPEN_TEMPLATE.body : "",
  };
}

const fill = (text, sample) =>
  text.replaceAll("{{firstName}}", sample.firstName).replaceAll("{{lastName}}", sample.lastName);

function unknownPlaceholders(text) {
  const found = new Set();
  for (const match of text.matchAll(/\{\{\s*([^{}]*?)\s*\}\}/g)) {
    if (!KNOWN_PLACEHOLDERS.includes(match[1])) found.add(`{{${match[1]}}}`);
  }
  // Only the exact spelling is documented, so flag spaced variants too.
  for (const match of text.matchAll(/\{\{(\s+(?:firstName|lastName)\s*|(?:firstName|lastName)\s+)\}\}/g)) {
    found.add(match[0]);
  }
  return [...found];
}

const CAMPAIGN_TONE = { QUEUED: "neutral", SENDING: "waitlisted", SENT: "accepted", FAILED: "rejected" };
const CAMPAIGN_LABEL = { QUEUED: "Queued", SENDING: "Sending", SENT: "Sent", FAILED: "Failed" };
const isActive = (campaign) => campaign.status === "QUEUED" || campaign.status === "SENDING";

function Campaign({ campaign }) {
  const recipients = campaign.recipientCount || 0;
  const sent = campaign.sentCount || 0;
  const failed = campaign.failedCount || 0;
  const done = recipients > 0 ? Math.min(100, Math.round(((sent + failed) / recipients) * 100)) : 0;
  return (
    <li className="campaign">
      <div className="campaign-head">
        <strong>{campaign.subject}</strong>
        <span className="tag-row">
          <Tag tone="neutral">{campaignKind(campaign.kind).label}</Tag>
          <Tag tone={CAMPAIGN_TONE[campaign.status] || "neutral"}>{CAMPAIGN_LABEL[campaign.status] || campaign.status}</Tag>
        </span>
      </div>
      <p className="campaign-meta">
        To {audienceLabel(campaign.audience).toLowerCase()}
        {campaign.school ? ` at ${campaign.school}` : ""} · {formatDateTime(campaign.createdAt)}
        {campaign.createdBy ? ` · by ${campaign.createdBy}` : ""}
      </p>
      <dl className="campaign-counts">
        <div>
          <dt>Recipients</dt>
          <dd>{formatNumber(recipients)}</dd>
        </div>
        <div>
          <dt>Sent</dt>
          <dd>{formatNumber(sent)}</dd>
        </div>
        <div className={failed > 0 ? "has-failed" : undefined}>
          <dt>Failed</dt>
          <dd>{formatNumber(failed)}</dd>
        </div>
        <div>
          <dt>{isActive(campaign) ? "Progress" : "Finished"}</dt>
          <dd>{isActive(campaign) ? `${done}%` : formatDateTime(campaign.completedAt) || "Not recorded"}</dd>
        </div>
      </dl>
      {isActive(campaign) && (
        <div className="progress" aria-hidden="true">
          <span style={{ width: `${done}%` }} />
        </div>
      )}
      <details>
        <summary>Show message</summary>
        <pre className="campaign-body">{campaign.body}</pre>
      </details>
    </li>
  );
}

const loadCampaigns = (signal) => api.campaigns(signal);

export default function Email({ admin, query }) {
  const notify = useToast();
  const ids = useId();
  const bodyRef = useRef(null);
  const [draft, setDraft] = useState(() => initialDraft(query));
  const [fieldErrors, setFieldErrors] = useState({});
  const [replaceTemplate, setReplaceTemplate] = useState(false);
  const [test, setTest] = useState({ status: "idle", message: "" });
  const [confirming, setConfirming] = useState(false);
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState(null);

  const { kind, audience, school, subject, body } = draft;
  const ready = subject.trim() !== "" && body.trim() !== "";
  const kindInfo = campaignKind(kind);
  const audiences = audiencesFor(kind);
  const update = (patch) => {
    setDraft((prev) => ({ ...prev, ...patch }));
    setTest({ status: "idle", message: "" });
  };

  const stats = useStats();
  const audienceInfo = AUDIENCES.find((a) => a.value === audience) || audiences[0];
  const schools = schoolOptions(stats.data, audienceInfo.schoolsFrom);

  const loadCount = useCallback((signal) => api.recipientCount(kind, audience, school, signal), [kind, audience, school]);
  const count = useAsync(loadCount);
  const recipientCount = !count.loading && !count.error ? (count.data?.recipientCount ?? null) : null;

  const draftSubject = useDebounced(subject, 500);
  const draftBody = useDebounced(body, 500);
  const loadRendered = useCallback(
    (signal) =>
      draftSubject.trim() && draftBody.trim() ? api.emailPreview(kind, draftSubject, draftBody, signal) : Promise.resolve(null),
    [kind, draftSubject, draftBody],
  );
  const rendered = useAsync(loadRendered);
  const renderedHtml = !rendered.error && ready ? rendered.data?.html : null;

  const history = useAsync(loadCampaigns);
  const campaigns = Array.isArray(history.data) ? history.data : [];
  const anyActive = campaigns.some(isActive);
  const reloadHistory = history.reload;
  useEffect(() => {
    if (!anyActive) return undefined;
    const timer = window.setInterval(reloadHistory, 4000);
    return () => window.clearInterval(timer);
  }, [anyActive, reloadHistory]);

  const [first = "Alex", ...rest] = (admin?.name || "").trim().split(/\s+/).filter(Boolean);
  const sample = { firstName: first, lastName: rest.join(" ") || "Rivera" };
  const paragraphs = fill(body, sample)
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter(Boolean);
  const unknown = unknownPlaceholders(`${subject}\n${body}`);

  const validate = () => {
    const errors = {};
    if (subject.trim() === "") errors.subject = "Add a subject.";
    if (body.trim() === "") errors.body = "Write the message.";
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const applyTemplate = () => {
    setDraft({ ...REGISTRATION_OPEN_TEMPLATE, school: "" });
    setFieldErrors({});
    setTest({ status: "idle", message: "" });
    setReplaceTemplate(false);
  };

  const insertPlaceholder = (token) => {
    const el = bodyRef.current;
    const start = el ? el.selectionStart : body.length;
    const end = el ? el.selectionEnd : body.length;
    update({ body: body.slice(0, start) + token + body.slice(end) });
    window.requestAnimationFrame(() => {
      if (!el) return;
      el.focus();
      el.setSelectionRange(start + token.length, start + token.length);
    });
  };

  const sendTest = async () => {
    if (!validate()) return;
    setTest({ status: "busy", message: "" });
    try {
      await api.sendTestEmail(kind, subject.trim(), body);
      setTest({ status: "done", message: `Test sent to ${admin?.email || "your email"}. Check your inbox.` });
    } catch (error) {
      setFieldErrors(error?.fieldErrors || {});
      setTest({ status: "error", message: `Test failed: ${errorText(error)}` });
    }
  };

  const openConfirm = () => {
    if (!validate()) return;
    setSendError(null);
    setConfirming(true);
  };

  const confirmSend = async () => {
    setSending(true);
    setSendError(null);
    try {
      const campaign = await api.sendEmail({ kind, audience, school, subject: subject.trim(), body });
      setConfirming(false);
      notify(`${kindInfo.label} queued for ${plural(campaign?.recipientCount ?? recipientCount ?? 0, "recipient")}.`);
      setDraft((prev) => ({ ...prev, subject: "", body: "" }));
      setTest({ status: "idle", message: "" });
      history.reload();
    } catch (error) {
      if (error?.fieldErrors) setFieldErrors(error.fieldErrors);
      setSendError(error);
    } finally {
      setSending(false);
    }
  };

  const canSend = ready && recipientCount !== null && recipientCount > 0;

  return (
    <>
      <PageHeader title="Email" description="Write to pre-registrants or registrants. Choose the kind first: it decides who can receive it and whether they can unsubscribe.">
        <button
          type="button"
          className="btn"
          onClick={() => (subject.trim() || body.trim() ? setReplaceTemplate(true) : applyTemplate())}
        >
          Use “Registration is open” template
        </button>
      </PageHeader>

      <div className="composer">
        <form
          className="card composer-form"
          aria-label="Compose email"
          onSubmit={(e) => {
            e.preventDefault();
            openConfirm();
          }}
        >
          <fieldset className="kind-choice">
            <legend>Kind of email</legend>
            {CAMPAIGN_KINDS.map((option) => (
              <div key={option.value} className="check-line">
                <input
                  id={`${ids}-kind-${option.value}`}
                  type="radio"
                  name={`${ids}-kind`}
                  value={option.value}
                  checked={kind === option.value}
                  onChange={() =>
                    update({
                      kind: option.value,
                      ...(isAudience(option.value, audience) ? {} : { audience: audiencesFor(option.value)[0].value, school: "" }),
                    })
                  }
                />
                <label htmlFor={`${ids}-kind-${option.value}`}>
                  <strong>{option.label}</strong>
                  <span className="hint">{option.hint}</span>
                </label>
              </div>
            ))}
          </fieldset>

          <div className="field">
            <label htmlFor={`${ids}-audience`}>Audience</label>
            <select id={`${ids}-audience`} value={audience} aria-describedby={`${ids}-audience-hint`} onChange={(e) => update({ audience: e.target.value, school: "" })}>
              {audiences.map((a) => (
                <option key={a.value} value={a.value}>
                  {a.label}
                </option>
              ))}
            </select>
            <p id={`${ids}-audience-hint`} className="hint">
              {audienceInfo.hint}
              {kind === "EVENT_UPDATE" && " Pre-registrants cannot be sent an event update."}
            </p>
          </div>

          <div className="field">
            <label htmlFor={`${ids}-school`}>School (optional)</label>
            <select id={`${ids}-school`} value={school} onChange={(e) => update({ school: e.target.value })}>
              <option value="">All schools</option>
              {schools.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </div>

          <div className="recipient-count" role="status">
            {count.loading && (
              <>
                <Spinner label="Counting recipients" /> Counting recipients…
              </>
            )}
            {!count.loading && count.error && (
              <>
                <span>Could not count recipients: {errorText(count.error)}</span>
                <button type="button" className="link-btn" onClick={count.reload}>
                  Retry
                </button>
              </>
            )}
            {recipientCount !== null && (
              <span>
                <strong>{formatNumber(recipientCount)}</strong> {recipientCount === 1 ? "person" : "people"} will receive this email
                {recipientCount === 0
                  ? ". Nobody matches this audience yet."
                  : kindInfo.unsubscribe
                    ? " (unsubscribed and duplicate addresses excluded)."
                    : " (including people who unsubscribed from announcements; duplicate addresses excluded)."}
              </span>
            )}
          </div>

          <div className="field">
            <label htmlFor={`${ids}-subject`}>Subject</label>
            <input
              id={`${ids}-subject`}
              type="text"
              maxLength={200}
              value={subject}
              aria-invalid={fieldErrors.subject ? true : undefined}
              aria-describedby={fieldErrors.subject ? `${ids}-subject-error` : undefined}
              onChange={(e) => update({ subject: e.target.value })}
            />
            {fieldErrors.subject && (
              <p id={`${ids}-subject-error`} className="inline-error">
                {fieldErrors.subject}
              </p>
            )}
          </div>

          <div className="field">
            <label htmlFor={`${ids}-body`}>Message</label>
            <textarea
              id={`${ids}-body`}
              ref={bodyRef}
              rows={12}
              value={body}
              aria-invalid={fieldErrors.body ? true : undefined}
              aria-describedby={`${ids}-body-hint${fieldErrors.body ? ` ${ids}-body-error` : ""}`}
              onChange={(e) => update({ body: e.target.value })}
            />
            {fieldErrors.body && (
              <p id={`${ids}-body-error`} className="inline-error">
                {fieldErrors.body}
              </p>
            )}
            <div id={`${ids}-body-hint`} className="hint placeholder-hint">
              <span>Plain text. Leave a blank line between paragraphs. Personalize with:</span>
              <span className="placeholder-buttons">
                <button type="button" className="chip" onClick={() => insertPlaceholder("{{firstName}}")}>
                  {"{{firstName}}"}
                </button>
                <button type="button" className="chip" onClick={() => insertPlaceholder("{{lastName}}")}>
                  {"{{lastName}}"}
                </button>
              </span>
            </div>
            {unknown.length > 0 && (
              <p className="notice notice-warn">
                {unknown.join(", ")} {unknown.length === 1 ? "is" : "are"} not a supported placeholder and will be sent exactly as
                written. Use {"{{firstName}}"} or {"{{lastName}}"}.
              </p>
            )}
          </div>

          <div className="composer-actions">
            <button type="button" className="btn" onClick={sendTest} disabled={test.status === "busy" || sending}>
              {test.status === "busy" ? "Sending test…" : "Send test to me"}
            </button>
            <button type="submit" className="btn btn-primary" disabled={!canSend || sending}>
              {recipientCount !== null && recipientCount > 0 ? `Send to ${plural(recipientCount, "person", "people")}…` : "Send…"}
            </button>
          </div>
          {test.message && (
            <p className={test.status === "error" ? "inline-error" : "notice"} role={test.status === "error" ? "alert" : "status"}>
              {test.message}
            </p>
          )}
          <p className="hint">Nothing is sent to the audience until you confirm on the next step.</p>
        </form>

        <section className="card preview" aria-labelledby={`${ids}-preview`}>
          <div className="card-head">
            <h2 id={`${ids}-preview`}>Preview</h2>
            <span className="muted">
              As seen by {sample.firstName} {sample.lastName}
            </span>
          </div>
          {renderedHtml && (
            <>
              <p className="preview-subject">{rendered.data.subject}</p>
              {/* An empty sandbox: the rendered email can run no script and open no link in this page. */}
              <iframe className="preview-frame" title="Email preview" sandbox="" srcDoc={renderedHtml} />
            </>
          )}
          <div className="preview-mail" hidden={Boolean(renderedHtml)}>
            <p className="preview-subject">{fill(subject, sample) || <span className="preview-empty">Subject</span>}</p>
            <div className="preview-body">
              {paragraphs.length === 0 && <p className="preview-empty">Your message will appear here as you type.</p>}
              {paragraphs.map((paragraph, index) => (
                <p key={index}>{paragraph}</p>
              ))}
            </div>
            <p className="preview-footer">
              {kindInfo.footer}
              {kindInfo.unsubscribe && <> <u>Unsubscribe</u></>}
            </p>
          </div>
          <p className="hint">
            {renderedHtml
              ? "This is the email as the server renders it, with your name filled in. Mail apps differ a little; use “Send test to me” to see it in a real inbox."
              : "This shows the wording with placeholders filled in. Once there is a subject and a message, the email appears here in the PeachHacks design."}
            {rendered.error && ` The designed preview could not be loaded: ${errorText(rendered.error)}`}
          </p>
        </section>
      </div>

      <section className="card" aria-labelledby={`${ids}-history`}>
        <div className="card-head">
          <h2 id={`${ids}-history`}>Sent emails</h2>
          <span className="muted" role="status">
            {anyActive ? "Updating while emails are sending…" : ""}
          </span>
        </div>
        {history.error && (
          <ErrorBlock
            title={history.data ? "Could not refresh; showing the last list loaded" : "Could not load sent emails"}
            error={history.error}
            onRetry={history.reload}
          />
        )}
        {!history.data && !history.error && <LoadingBlock label="Loading sent emails…" />}
        {history.data && campaigns.length === 0 && <EmptyBlock title="No emails sent yet">Campaigns you send will be listed here with their delivery counts.</EmptyBlock>}
        {campaigns.length > 0 && (
          <ul className="campaigns">
            {campaigns.map((campaign) => (
              <Campaign key={campaign.id} campaign={campaign} />
            ))}
          </ul>
        )}
      </section>

      {replaceTemplate && (
        <ConfirmDialog
          title="Replace your draft?"
          confirmLabel="Replace draft"
          onConfirm={applyTemplate}
          onCancel={() => setReplaceTemplate(false)}
        >
          <p>The “Registration is open” template will replace the subject and message you have written, make this an announcement, and set the audience to people who pre-registered but have not registered yet.</p>
        </ConfirmDialog>
      )}

      {confirming && (
        <ConfirmDialog
          title={`Send this ${kindInfo.label.toLowerCase()} to ${plural(recipientCount ?? 0, "person", "people")}?`}
          confirmLabel={`Send to ${plural(recipientCount ?? 0, "person", "people")}`}
          busy={sending}
          error={sendError}
          onConfirm={confirmSend}
          onCancel={() => setConfirming(false)}
        >
          <dl className="confirm-summary">
            <div>
              <dt>Kind</dt>
              <dd>
                <strong>{kindInfo.label}</strong>
              </dd>
            </div>
            <div>
              <dt>Recipients</dt>
              <dd>
                <strong>{formatNumber(recipientCount ?? 0)}</strong>
              </dd>
            </div>
            <div>
              <dt>Audience</dt>
              <dd>
                {audienceInfo.label}
                {school ? `, ${school} only` : ", all schools"}
              </dd>
            </div>
            <div>
              <dt>Subject</dt>
              <dd>{subject.trim()}</dd>
            </div>
          </dl>
          <p>
            {kindInfo.unsubscribe
              ? "People who unsubscribed are skipped, and the email carries an unsubscribe link."
              : "This goes to everyone in the audience, including people who unsubscribed from announcements, and has no unsubscribe link. Use it only for information they need."}
          </p>
          <p>Sending starts right away and cannot be undone or recalled.</p>
        </ConfirmDialog>
      )}
    </>
  );
}
