import { formatShare, formatTarget, gapText } from "../lib/acceptance.js";
import { formatNumber } from "../lib/format.js";
import { href } from "../lib/router.js";

const tone = (share) => (!share || share.total === 0 ? "" : share.met ? " is-met" : " is-short");

// A bar filled to the share with the target marked on it: green at or above the target,
// amber below, plain while the group is empty.
export function ShareBar({ share, target }) {
  const filled = typeof share?.share === "number" ? Math.min(100, share.share * 100) : 0;
  const mark = Math.min(100, Number(target) * 100);
  return (
    <span
      className={`share-track${tone(share)}`}
      role="img"
      aria-label={
        share?.total > 0
          ? `${formatShare(share.share)} against a target of ${formatTarget(target)}`
          : `No one yet; the target is ${formatTarget(target)}`
      }
    >
      <span className="share-fill" style={{ width: `${filled}%` }} />
      <span className="share-target" style={{ left: `${mark}%` }} />
    </span>
  );
}

export function ShareHeadline({ share, hostSchool }) {
  const empty = share.total === 0;
  return (
    <div className={`share-headline${tone(share)}`}>
      <div className="share-figure">
        <strong>{formatShare(share.share)}</strong>
        <span>
          {empty
            ? `of accepted hackers from ${hostSchool.name}: nobody is accepted yet`
            : `of accepted hackers are from ${hostSchool.name} (${formatNumber(share.host)} of ${formatNumber(share.total)})`}
        </span>
      </div>
      <ShareBar share={share} target={hostSchool.target} />
      <div className="share-legend">
        <span className="share-state">{empty ? "No acceptances yet" : share.met ? "Target met" : "Below target"}</span>
        <span>Target {formatTarget(hostSchool.target)}</span>
      </div>
      {!empty && <p className="share-gap">{gapText(share, hostSchool.name)}</p>}
    </div>
  );
}

export function ShareRow({ label, share, target }) {
  return (
    <div className="share-row">
      <div className="share-row-head">
        <span>{label}</span>
        <strong>{formatShare(share.share)}</strong>
      </div>
      <ShareBar share={share} target={target} />
      <span className="muted small">
        {share.total === 0 ? "Nobody yet" : `${formatNumber(share.host)} of ${formatNumber(share.total)}`}
      </span>
    </div>
  );
}

// One line for screens whose subject is something else. `projected` is the share after a
// change that has not been saved yet.
export function ShareStrip({ summary, projected = null, projectedLabel = "" }) {
  const share = summary.shares.accepted;
  const hostSchool = summary.hostSchool;
  return (
    <section className={`share-strip${tone(projected || share)}`} aria-label="Host-school share of accepted hackers">
      <div className="share-strip-text">
        <strong>
          {hostSchool.name} share of accepted: {formatShare(share.share)}
          {projected && (
            <>
              {" "}
              <span aria-hidden="true">→</span>
              <span className="sr-only">would become</span> {formatShare(projected.share)}
            </>
          )}
        </strong>
        <span>
          Target {formatTarget(hostSchool.target)}.{" "}
          {projected ? `${projectedLabel}: ${gapText(projected, hostSchool.name)}.` : `${gapText(share, hostSchool.name)}.`}
        </span>
      </div>
      <ShareBar share={projected || share} target={hostSchool.target} />
      <a className="btn btn-small" href={href("/acceptances")}>
        Open acceptances
      </a>
    </section>
  );
}
