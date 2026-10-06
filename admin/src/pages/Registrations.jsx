import { useCallback, useId, useMemo, useState } from "react";
import { MOCK_MODE, api, ticketQrUrl } from "../api/client.js";
import { ConfirmDialog, Modal } from "../components/Modal.jsx";
import {
  EmptyBlock,
  ErrorBlock,
  InlineError,
  LoadingBlock,
  PageHeader,
  Pagination,
  StatusBadge,
  useToast,
} from "../components/ui.jsx";
import { STATUSES, errorText, formatDateTime, formatWhen, fullName, statusLabel } from "../lib/format.js";
import { schoolOptions, useAsync, useDebounced, useStats } from "../lib/hooks.js";

const PAGE_SIZE = 25;

const present = (value) => value !== null && value !== undefined && String(value).trim() !== "";
const yesNo = (value) => (value === true ? "Yes" : value === false ? "No" : null);
const list = (value) => (Array.isArray(value) && value.length > 0 ? value.join(", ") : null);
// Choice fields can carry a free-text companion ("Prefer to self-describe" + text).
const withOther = (value, other) => [value, other].filter(present).join(": ") || null;

function addressLines(address) {
  if (!address) return null;
  const cityLine = [address.city, address.state, address.postalCode].filter(present).join(", ");
  const lines = [address.line1, address.line2, cityLine, address.country].filter(present);
  return lines.length > 0 ? lines : null;
}

function safeUrl(value) {
  if (!present(value)) return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : null;
  } catch {
    return null;
  }
}

function Row({ label, children }) {
  const empty = children === null || children === undefined || children === "";
  return (
    <div className="detail-row">
      <dt>{label}</dt>
      <dd>{empty ? <span className="muted">Not provided</span> : children}</dd>
    </div>
  );
}

function Group({ title, children }) {
  return (
    <section className="detail-group">
      <h3>{title}</h3>
      <dl>{children}</dl>
    </section>
  );
}

function RegistrationDetail({ reg }) {
  const address = addressLines(reg.shippingAddress);
  const linkedin = safeUrl(reg.linkedinUrl);
  return (
    <>
      <Group title="Contact">
        <Row label="First name">{reg.firstName}</Row>
        <Row label="Last name">{reg.lastName}</Row>
        <Row label="Email">{present(reg.email) ? <a href={`mailto:${reg.email}`}>{reg.email}</a> : null}</Row>
        <Row label="Phone">{reg.phone}</Row>
        <Row label="Age">{reg.age}</Row>
        <Row label="Country of residence">{reg.countryOfResidence}</Row>
        <Row label="LinkedIn">
          {linkedin ? (
            <a href={linkedin} target="_blank" rel="noreferrer noopener">
              {reg.linkedinUrl}
            </a>
          ) : (
            reg.linkedinUrl
          )}
        </Row>
      </Group>
      <Group title="Education">
        <Row label="School">{reg.school}</Row>
        <Row label="Level of study">{reg.levelOfStudy}</Row>
        <Row label="Major / field of study">{withOther(reg.majorFieldOfStudy, reg.majorOther)}</Row>
        <Row label="Highest education completed">{withOther(reg.highestEducation, reg.highestEducationOther)}</Row>
      </Group>
      <Group title="MLH agreements">
        <Row label="Code of Conduct">{yesNo(reg.mlhCodeOfConduct)}</Row>
        <Row label="Data sharing">{yesNo(reg.mlhDataSharing)}</Row>
        <Row label="MLH email opt-in">{yesNo(reg.mlhEmailOptIn)}</Row>
      </Group>
      <Group title="Event logistics">
        <Row label="Dietary restrictions">{list(reg.dietaryRestrictions)}</Row>
        <Row label="Dietary details">{reg.dietaryDetails}</Row>
        <Row label="T-shirt size">{reg.tshirtSize}</Row>
        <Row label="Shipping address">
          {address
            ? address.map((line, index) => (
                <span key={index} className="line">
                  {line}
                </span>
              ))
            : null}
        </Row>
      </Group>
      <Group title="Demographics (optional)">
        <Row label="Underrepresented group">{reg.underrepresentedGroup}</Row>
        <Row label="Gender">{withOther(reg.gender, reg.genderSelfDescribe)}</Row>
        <Row label="Pronouns">{withOther(reg.pronouns, reg.pronounsOther)}</Row>
        <Row label="Race / ethnicity">{withOther(list(reg.raceEthnicity), reg.raceEthnicityOther)}</Row>
        <Row label="Sexual orientation">{withOther(reg.sexualOrientation, reg.sexualOrientationOther)}</Row>
      </Group>
      <Group title="Check-in">
        <Row label="General check-in">
          {reg.checkedInAt ? `${formatDateTime(reg.checkedInAt)}${reg.checkedInBy ? ` by ${reg.checkedInBy}` : ""}` : "Not checked in"}
        </Row>
        <Row label="Events attended">
          {(reg.checkIns || []).length > 0
            ? reg.checkIns.map((checkIn) => (
                <span key={checkIn.eventId} className="line">
                  {checkIn.name}: {formatDateTime(checkIn.checkedInAt)}
                  {checkIn.checkedInBy ? ` by ${checkIn.checkedInBy}` : ""}
                </span>
              ))
            : "None yet"}
        </Row>
      </Group>
      <Group title="Record">
        <Row label="Registered">{formatDateTime(reg.createdAt)}</Row>
        <Row label="ID">
          <code>{reg.id}</code>
        </Row>
      </Group>
    </>
  );
}

function TicketPanel({ reg }) {
  const notify = useToast();
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);

  if (reg.status !== "ACCEPTED" || !reg.ticketToken) {
    return (
      <section className="ticket-panel" aria-label="Ticket">
        <span className="tile-label">Ticket</span>
        <p className="muted small">
          No ticket yet. Marking this registration Accepted creates the ticket and emails it to the applicant.
        </p>
      </section>
    );
  }

  const resend = async () => {
    setSending(true);
    setError(null);
    try {
      await api.resendTicketEmail(reg.id);
      notify(`Ticket email sent to ${reg.email}.`);
    } catch (err) {
      setError(err);
    } finally {
      setSending(false);
    }
  };

  return (
    <section className="ticket-panel" aria-label="Ticket">
      <span className="tile-label">Ticket</span>
      <div className="ticket-body">
        {MOCK_MODE ? (
          <p className="ticket-qr ticket-qr-mock">QR preview needs the real API</p>
        ) : (
          <img className="ticket-qr" src={ticketQrUrl(reg.ticketToken)} alt={`Ticket QR code for ${fullName(reg)}`} width="160" height="160" />
        )}
        <div className="ticket-info">
          <p>
            <a href={reg.ticketUrl} target="_blank" rel="noreferrer noopener">
              Open ticket page
            </a>
          </p>
          <p className="muted small">
            Token: <code>{reg.ticketToken}</code>
          </p>
          <p className="muted small">
            Google Wallet: {reg.googleWalletUrl ? "the ticket page and email offer “Add to Google Wallet”." : "not set up, so no wallet link is offered."}
          </p>
          <button type="button" className="btn btn-small" disabled={sending} onClick={resend}>
            {sending ? "Sending…" : "Resend ticket email"}
          </button>
          <InlineError error={error} />
        </div>
      </div>
    </section>
  );
}

function RegistrationDrawer({ id, fallbackName, onClose, onChanged, onDeleted }) {
  const notify = useToast();
  const load = useCallback((signal) => api.registration(id, signal), [id]);
  const detail = useAsync(load);
  const [updated, setUpdated] = useState(null);
  const [savingStatus, setSavingStatus] = useState(null);
  const [statusError, setStatusError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);
  const [accepting, setAccepting] = useState(false);

  // The PATCH response is the freshest copy of the record.
  const reg = updated || detail.data;

  const changeStatus = async (status) => {
    if (!reg || status === reg.status || savingStatus) return;
    setSavingStatus(status);
    setStatusError(null);
    try {
      const result = await api.setRegistrationStatus(id, status);
      setUpdated(result || { ...reg, status });
      notify(`${fullName(reg)} marked ${statusLabel(status).toLowerCase()}.`);
      onChanged();
    } catch (error) {
      setStatusError(error);
    } finally {
      setSavingStatus(null);
    }
  };

  const confirmDelete = async () => {
    setDeleting(true);
    setDeleteError(null);
    try {
      await api.deleteRegistration(id);
      notify(`Deleted the registration for ${fullName(reg)}.`);
      setConfirming(false);
      onDeleted();
    } catch (error) {
      setDeleteError(error);
      setDeleting(false);
    }
  };

  return (
    <Modal
      variant="drawer"
      title={reg ? fullName(reg) : fallbackName || "Registration"}
      onDismiss={onClose}
      busy={deleting}
      footer={
        reg && (
          <>
            <button type="button" className="btn btn-danger-quiet" onClick={() => setConfirming(true)}>
              Delete registration
            </button>
            <button type="button" className="btn" onClick={onClose}>
              Close
            </button>
          </>
        )
      }
    >
      {!reg && detail.error && <ErrorBlock error={detail.error} onRetry={detail.reload} />}
      {!reg && !detail.error && <LoadingBlock label="Loading registration…" />}
      {reg && (
        <>
          <section className="status-panel" aria-label="Application status">
            <div className="status-panel-head">
              <span className="tile-label">Status</span>
              <StatusBadge status={reg.status} />
            </div>
            <div className="status-buttons" role="group" aria-label="Change status">
              {STATUSES.map((status) => (
                <button
                  key={status}
                  type="button"
                  className={`btn btn-small status-choice status-${status.toLowerCase()}`}
                  aria-pressed={reg.status === status}
                  disabled={Boolean(savingStatus)}
                  onClick={() => (status === "ACCEPTED" && reg.status !== "ACCEPTED" ? setAccepting(true) : changeStatus(status))}
                >
                  {savingStatus === status ? "Saving…" : statusLabel(status)}
                </button>
              ))}
            </div>
            <p className="muted small">
              Changing the status saves immediately. Accepting someone emails them their ticket; other changes send
              nothing.
            </p>
            <InlineError error={statusError} />
          </section>
          <TicketPanel reg={reg} />
          <RegistrationDetail reg={reg} />
        </>
      )}
      {accepting && reg && (
        <ConfirmDialog
          title="Accept and send the ticket?"
          confirmLabel="Accept and email ticket"
          onConfirm={() => {
            setAccepting(false);
            changeStatus("ACCEPTED");
          }}
          onCancel={() => setAccepting(false)}
        >
          <p>
            <strong>{fullName(reg)}</strong> will be marked accepted and emailed a “You’re in” message at {reg.email} with
            their ticket QR code right away.
          </p>
        </ConfirmDialog>
      )}
      {confirming && reg && (
        <ConfirmDialog
          title="Delete this registration?"
          confirmLabel="Delete"
          danger
          busy={deleting}
          error={deleteError}
          onConfirm={confirmDelete}
          onCancel={() => setConfirming(false)}
        >
          <p>
            The full registration for <strong>{fullName(reg)}</strong> ({reg.email}) will be permanently deleted,
            including every answer on the form. This cannot be undone.
          </p>
        </ConfirmDialog>
      )}
    </Modal>
  );
}

export default function Registrations() {
  const notify = useToast();
  const ids = useId();
  const [search, setSearch] = useState("");
  const [school, setSchool] = useState("");
  const [status, setStatus] = useState("");
  const [checkedIn, setCheckedIn] = useState("");
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const [open, setOpen] = useState(null);

  const q = useDebounced(search.trim(), 300);
  const filters = useMemo(() => ({ q, school, status, checkedIn }), [q, school, status, checkedIn]);
  const load = useCallback((signal) => api.registrations({ page, size: PAGE_SIZE, ...filters }, signal), [page, filters]);
  const result = useAsync(load);
  const stats = useStats();
  const schools = schoolOptions(stats.data, "registrations");

  const items = result.data?.items || [];
  const total = result.data?.total || 0;
  const filtered = Boolean(q || school || status || checkedIn);

  const exportCsv = async () => {
    setExporting(true);
    try {
      await api.exportRegistrations(filters);
    } catch (error) {
      notify(`Export failed: ${errorText(error)}`, "error");
    } finally {
      setExporting(false);
    }
  };

  const resetTo = (setter) => (event) => {
    setter(event.target.value);
    setPage(0);
  };

  return (
    <>
      <PageHeader title="Registrations" description="Full registrations with every MLH field. Select a name to see the details.">
        <button type="button" className="btn" onClick={exportCsv} disabled={exporting}>
          {exporting ? "Preparing…" : filtered ? "Export filtered CSV" : "Export CSV"}
        </button>
      </PageHeader>

      <form className="filters" role="search" onSubmit={(e) => e.preventDefault()}>
        <div className="field field-grow">
          <label htmlFor={`${ids}-q`}>Search</label>
          <input id={`${ids}-q`} type="search" placeholder="Name or email" value={search} onChange={resetTo(setSearch)} />
        </div>
        <div className="field">
          <label htmlFor={`${ids}-school`}>School</label>
          <select id={`${ids}-school`} value={school} onChange={resetTo(setSchool)}>
            <option value="">All schools</option>
            {schools.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={`${ids}-status`}>Status</label>
          <select id={`${ids}-status`} value={status} onChange={resetTo(setStatus)}>
            <option value="">All statuses</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {statusLabel(value)}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={`${ids}-checked-in`}>Attendance</label>
          <select id={`${ids}-checked-in`} value={checkedIn} onChange={resetTo(setCheckedIn)}>
            <option value="">Everyone</option>
            <option value="true">Checked in</option>
            <option value="false">Not checked in</option>
          </select>
        </div>
        {filtered && (
          <button
            type="button"
            className="btn"
            onClick={() => {
              setSearch("");
              setSchool("");
              setStatus("");
              setCheckedIn("");
              setPage(0);
            }}
          >
            Clear
          </button>
        )}
      </form>

      {result.error && <ErrorBlock error={result.error} onRetry={result.reload} />}
      {!result.error && !result.data && <LoadingBlock label="Loading registrations…" />}
      {!result.error && result.data && items.length === 0 && (
        <EmptyBlock title={filtered ? "No registrations match" : "No registrations yet"}>
          {filtered
            ? "Try a different search or clear the filters."
            : "Registrations appear here once the gate is open and people submit the form."}
        </EmptyBlock>
      )}

      {result.data && items.length > 0 && (
        <div className={`table-wrap${result.loading ? " is-loading" : ""}`} aria-busy={result.loading}>
          <table className="data-table">
            <caption className="sr-only">Registrations, newest first</caption>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
                <th scope="col">School</th>
                <th scope="col">Level of study</th>
                <th scope="col">Country</th>
                <th scope="col">Age</th>
                <th scope="col">Status</th>
                <th scope="col">Checked in</th>
                <th scope="col">Registered</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <th scope="row" data-label="Name">
                    <button type="button" className="link-btn row-link" onClick={() => setOpen(item)}>
                      {fullName(item)}
                      <span className="sr-only">, view details</span>
                    </button>
                  </th>
                  <td data-label="Email" className="cell-break">
                    {item.email}
                  </td>
                  <td data-label="School">{item.school}</td>
                  <td data-label="Level of study">{item.levelOfStudy}</td>
                  <td data-label="Country">{item.countryOfResidence}</td>
                  <td data-label="Age">{item.age}</td>
                  <td data-label="Status">
                    <StatusBadge status={item.status} />
                  </td>
                  <td data-label="Checked in" className="cell-nowrap">
                    {item.checkedInAt ? (
                      <span className="checked-in-cell">✓ {formatWhen(item.checkedInAt)}</span>
                    ) : (
                      <span className="muted">No</span>
                    )}
                  </td>
                  <td data-label="Registered" className="cell-nowrap">
                    {formatDateTime(item.createdAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {result.data && total > 0 && (
        <Pagination page={page} size={result.data.size || PAGE_SIZE} total={total} onPage={setPage} disabled={result.loading} />
      )}

      {open && (
        <RegistrationDrawer
          key={open.id}
          id={open.id}
          fallbackName={fullName(open)}
          onClose={() => setOpen(null)}
          onChanged={() => {
            result.reload();
            stats.reload();
          }}
          onDeleted={() => {
            setOpen(null);
            if (items.length === 1 && page > 0) setPage(page - 1);
            else result.reload();
            stats.reload();
          }}
        />
      )}
    </>
  );
}
