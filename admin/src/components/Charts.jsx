import { useState } from "react";
import { formatDay, formatNumber } from "../lib/format.js";

export function BarList({ rows, labelKey, total, tone = "peach", initialLimit = 10, emptyText = "No data yet.", unit = "people" }) {
  const [expanded, setExpanded] = useState(false);
  if (!rows || rows.length === 0) return <p className="muted">{emptyText}</p>;

  const max = Math.max(...rows.map((row) => row.count), 1);
  const sum = total || rows.reduce((acc, row) => acc + row.count, 0) || 1;
  const visible = expanded ? rows : rows.slice(0, initialLimit);
  const hidden = rows.length - visible.length;

  return (
    <div className="barlist">
      <ol>
        {visible.map((row, index) => {
          const label = row[labelKey] || "(not given)";
          return (
            <li key={`${label}-${index}`}>
              <span className="barlist-rank" aria-hidden="true">
                {index + 1}
              </span>
              <span className="barlist-main">
                <span className="barlist-label">{label}</span>
                <span className="barlist-track" aria-hidden="true">
                  <span className={`barlist-fill tone-${tone}`} style={{ width: `${Math.max((row.count / max) * 100, 1.5)}%` }} />
                </span>
              </span>
              <span className="barlist-value">
                <strong>{formatNumber(row.count)}</strong>
                <span className="sr-only"> {unit}, </span>
                <span className="barlist-share">{Math.round((row.count / sum) * 100)}%</span>
              </span>
            </li>
          );
        })}
      </ol>
      {(hidden > 0 || expanded) && rows.length > initialLimit && (
        <button type="button" className="link-btn" aria-expanded={expanded} onClick={() => setExpanded((v) => !v)}>
          {expanded ? "Show fewer" : `Show all ${formatNumber(rows.length)}`}
        </button>
      )}
    </div>
  );
}

const DAY_MS = 86400000;
const dayToMs = (isoDay) => Date.parse(`${isoDay}T00:00:00Z`);
const msToDay = (ms) => new Date(ms).toISOString().slice(0, 10);

// `byDay` only lists days that have data, so the gaps are filled with zeros.
// Pass the same range to several charts to line their x-axes up.
export function dayRange(...series) {
  const all = series.flat().map((d) => dayToMs(d.date)).filter((ms) => !Number.isNaN(ms));
  if (all.length === 0) return null;
  const start = Math.min(...all);
  const end = Math.max(...all);
  // Guard against a stray far-off date producing an enormous range.
  return { start: Math.max(start, end - 730 * DAY_MS), end };
}

function fillDays(byDay, range) {
  const counts = new Map((byDay || []).map((d) => [d.date, d.count]));
  const days = [];
  let running = 0;
  for (let ms = range.start; ms <= range.end; ms += DAY_MS) {
    const date = msToDay(ms);
    const count = counts.get(date) || 0;
    running += count;
    days.push({ date, count, cumulative: running });
  }
  return days;
}

function niceMax(value) {
  if (value <= 4) return 4;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  const scaled = value / magnitude;
  const step = scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 4 ? 4 : scaled <= 5 ? 5 : 10;
  return step * magnitude;
}

export function DayChart({ title, byDay, range, mode = "daily", tone = "peach", noun = "sign-up" }) {
  const [active, setActive] = useState(null);

  if (!range || !byDay || byDay.length === 0) {
    return (
      <figure className="daychart">
        <figcaption>
          <strong>{title}</strong>
        </figcaption>
        <p className="muted">No sign-ups yet.</p>
      </figure>
    );
  }

  const days = fillDays(byDay, range);
  const key = mode === "cumulative" ? "cumulative" : "count";
  const max = niceMax(Math.max(...days.map((d) => d[key]), 1));
  const peak = days.reduce((best, d) => (d.count > best.count ? d : best), days[0]);
  const last = days[days.length - 1];
  const current = active !== null && days[active] ? days[active] : null;
  const nounFor = (n) => (n === 1 ? noun : `${noun}s`);

  let readout;
  if (current && mode === "cumulative") {
    readout = `${formatDay(current.date)}: ${formatNumber(current.cumulative)} total (+${formatNumber(current.count)} that day)`;
  } else if (current) {
    readout = `${formatDay(current.date)}: ${formatNumber(current.count)} ${nounFor(current.count)}`;
  } else if (mode === "cumulative") {
    readout = `${formatNumber(last.cumulative)} total by ${formatDay(last.date)}`;
  } else {
    readout = `Busiest day: ${formatDay(peak.date)} with ${formatNumber(peak.count)} ${nounFor(peak.count)}`;
  }

  const onMove = (event) => {
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width === 0) return;
    const ratio = (event.clientX - rect.left) / rect.width;
    setActive(Math.min(days.length - 1, Math.max(0, Math.floor(ratio * days.length))));
  };

  const points = days.map((d, i) => {
    const x = days.length === 1 ? 50 : (i / (days.length - 1)) * 100;
    return `${x.toFixed(2)},${(100 - (d.cumulative / max) * 100).toFixed(2)}`;
  });
  const mid = days[Math.floor((days.length - 1) / 2)];

  return (
    <figure className={`daychart tone-${tone}`}>
      <figcaption>
        <strong>{title}</strong>
        <span className="daychart-readout">{readout}</span>
      </figcaption>
      <div className="daychart-frame">
        <div className="daychart-y" aria-hidden="true">
          <span>{formatNumber(max)}</span>
          <span>{formatNumber(max / 2)}</span>
          <span>0</span>
        </div>
        <div
          className="daychart-plot"
          role="img"
          aria-label={`${title}: ${readout}. Full figures are in the data table below.`}
          onPointerMove={onMove}
          onPointerDown={onMove}
          onPointerLeave={() => setActive(null)}
        >
          <span className="daychart-grid" style={{ top: 0 }} />
          <span className="daychart-grid" style={{ top: "50%" }} />
          {mode === "cumulative" ? (
            <>
              <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
                <polygon className="daychart-area" points={`0,100 ${points.join(" ")} 100,100`} />
                <polyline className="daychart-line" points={points.join(" ")} />
              </svg>
              {current && (
                <span
                  className="daychart-cursor"
                  style={{ left: `${days.length === 1 ? 50 : (active / (days.length - 1)) * 100}%` }}
                />
              )}
            </>
          ) : (
            <div className="daychart-cols">
              {days.map((d, i) => (
                <span key={d.date} className={`daychart-col${i === active ? " is-active" : ""}`}>
                  {d.count > 0 && <span className="daychart-bar" style={{ height: `${Math.max((d.count / max) * 100, 2)}%` }} />}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>
      <div className="daychart-x" aria-hidden="true">
        <span>{formatDay(days[0].date)}</span>
        {days.length > 6 && <span>{formatDay(mid.date)}</span>}
        {days.length > 1 && <span>{formatDay(last.date)}</span>}
      </div>
      <details className="daychart-table">
        <summary>View as table</summary>
        <div className="table-scroll">
          <table className="mini-table">
            <thead>
              <tr>
                <th scope="col">Day</th>
                <th scope="col">New</th>
                <th scope="col">Running total</th>
              </tr>
            </thead>
            <tbody>
              {days
                .filter((d) => d.count > 0)
                .map((d) => (
                  <tr key={d.date}>
                    <th scope="row">{formatDay(d.date)}</th>
                    <td>{formatNumber(d.count)}</td>
                    <td>{formatNumber(d.cumulative)}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}
