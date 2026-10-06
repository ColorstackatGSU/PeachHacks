import { useState } from "react";
import { BarList, DayChart, dayRange } from "../components/Charts.jsx";
import { ErrorBlock, LoadingBlock, PageHeader, Spinner } from "../components/ui.jsx";
import { formatNumber, statusLabel } from "../lib/format.js";
import { useStats } from "../lib/hooks.js";
import { href } from "../lib/router.js";

function StatTile({ label, value, note, to }) {
  return (
    <div className="tile">
      <span className="tile-label">{label}</span>
      <strong className="tile-value">{formatNumber(value)}</strong>
      {note && <span className="tile-note">{note}</span>}
      {to && (
        <a className="tile-link" href={href(to)}>
          View list
        </a>
      )}
    </div>
  );
}

export default function Overview() {
  const stats = useStats();
  const [mode, setMode] = useState("daily");
  const data = stats.data;

  if (!data) {
    return (
      <>
        <PageHeader title="Overview" description="Sign-ups at a glance." />
        {stats.error ? <ErrorBlock error={stats.error} onRetry={stats.reload} /> : <LoadingBlock label="Loading numbers…" />}
      </>
    );
  }

  const pre = data.preRegistrations || {};
  const reg = data.registrations || {};
  const preTotal = pre.total || 0;
  const regTotal = reg.total || 0;
  const notRegistered = data.preRegisteredNotRegistered || 0;
  const range = dayRange(pre.byDay || [], reg.byDay || []);
  const converted = preTotal > 0 ? Math.round(((preTotal - notRegistered) / preTotal) * 100) : null;
  const statusRows = (reg.byStatus || [])
    .map((row) => ({ ...row, label: statusLabel(row.label) }))
    .sort((a, b) => b.count - a.count);

  return (
    <>
      <PageHeader title="Overview" description="Sign-ups at a glance.">
        <button type="button" className="btn" onClick={stats.reload} disabled={stats.loading}>
          {stats.loading ? <Spinner label="Refreshing" /> : null}
          Refresh
        </button>
      </PageHeader>

      {stats.error && <ErrorBlock title="Could not refresh; showing the last numbers loaded" error={stats.error} onRetry={stats.reload} />}

      <section className={`gate-strip ${data.registrationOpen ? "is-open" : "is-closed"}`} aria-label="Registration status">
        <span className="gate-dot" aria-hidden="true" />
        <div>
          <strong>Registration is {data.registrationOpen ? "open" : "closed"}</strong>
          <span>
            {data.registrationOpen
              ? "Anyone can submit the full registration form on the public site."
              : "The public registration page points visitors to pre-registration."}
          </span>
        </div>
        <a className="btn btn-small" href={href("/settings")}>
          {data.registrationOpen ? "Manage" : "Open registration"}
        </a>
      </section>

      <section className="tiles" aria-label="Headline numbers">
        <StatTile label="Pre-registrations" value={preTotal} to="/pre-registrations" />
        <StatTile label="Registrations" value={regTotal} to="/registrations" />
        <StatTile
          label="Pre-registered, not registered"
          value={notRegistered}
          note={converted === null ? "No pre-registrations yet" : `${converted}% of pre-registrants have registered`}
        />
        <StatTile label="Unsubscribed" value={pre.unsubscribed || 0} note="Pre-registrants skipped by bulk email" />
      </section>

      <section className="card" aria-labelledby="over-time-heading">
        <div className="card-head">
          <h2 id="over-time-heading">Sign-ups over time</h2>
          <div className="segmented" role="group" aria-label="Chart mode">
            <button type="button" aria-pressed={mode === "daily"} onClick={() => setMode("daily")}>
              Per day
            </button>
            <button type="button" aria-pressed={mode === "cumulative"} onClick={() => setMode("cumulative")}>
              Running total
            </button>
          </div>
        </div>
        <div className="chart-grid">
          <DayChart title="Pre-registrations" byDay={pre.byDay} range={range} mode={mode} tone="peach" noun="pre-registration" />
          <DayChart title="Registrations" byDay={reg.byDay} range={range} mode={mode} tone="sky" noun="registration" />
        </div>
      </section>

      <div className="two-col">
        <section className="card" aria-labelledby="pre-school-heading">
          <div className="card-head">
            <h2 id="pre-school-heading">Pre-registrations by school</h2>
            <span className="muted">{formatNumber((pre.bySchool || []).length)} {(pre.bySchool || []).length === 1 ? "school" : "schools"}</span>
          </div>
          <BarList rows={pre.bySchool} labelKey="school" total={preTotal} tone="peach" emptyText="No pre-registrations yet." />
        </section>
        <section className="card" aria-labelledby="reg-school-heading">
          <div className="card-head">
            <h2 id="reg-school-heading">Registrations by school</h2>
            <span className="muted">{formatNumber((reg.bySchool || []).length)} {(reg.bySchool || []).length === 1 ? "school" : "schools"}</span>
          </div>
          <BarList rows={reg.bySchool} labelKey="school" total={regTotal} tone="sky" emptyText="No registrations yet." />
        </section>
      </div>

      <div className="two-col">
        <section className="card" aria-labelledby="status-heading">
          <div className="card-head">
            <h2 id="status-heading">Registrations by status</h2>
          </div>
          <BarList rows={statusRows} labelKey="label" total={regTotal} tone="sky" emptyText="No registrations yet." />
        </section>
        <section className="card" aria-labelledby="level-heading">
          <div className="card-head">
            <h2 id="level-heading">Registrations by level of study</h2>
          </div>
          <BarList rows={reg.byLevelOfStudy} labelKey="label" total={regTotal} tone="sky" emptyText="No registrations yet." />
        </section>
      </div>
    </>
  );
}
