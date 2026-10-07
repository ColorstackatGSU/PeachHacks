import { plural } from "./format.js";

// Same rule as the API: the school name equals, or starts with, the host school's name,
// so the host's other campuses count too.
export function isHostSchool(school, hostName) {
  const name = String(hostName || "").trim().toLowerCase();
  return name !== "" && String(school || "").trim().toLowerCase().startsWith(name);
}

// Same rule as the API: under the minimum age for students of other schools, and not at
// the host school. Screens read the API's `ageReview`; this is for the mock API.
export const needsAgeReview = (item, hostName, minimumAge) =>
  Number(item.age) < minimumAge && !isHostSchool(item.school, hostName);

// "Under 18, not Georgia State University", from the acceptance summary.
export const ageReviewLabel = (summary) =>
  summary?.ageReview ? `Under ${summary.ageReview.minimumAge}, not ${summary.hostSchool.name}` : "Age review needed";

export const ageRuleText = (summary) =>
  summary?.ageReview
    ? `${summary.hostSchool.name} students are eligible at any age; students of other schools must be at least ${summary.ageReview.minimumAge}.`
    : "Students of other schools must meet the minimum age.";

// Mirrors the API's host-school share so a selection's effect can be shown before it is
// saved. The small tolerance keeps "exactly at target" from failing on float rounding.
export function hostShare(host, total, target) {
  const other = total - host;
  const share = total > 0 ? host / total : null;
  if (host + 1e-9 >= target * total) {
    return { total, host, other, share, met: true, moreHostNeeded: 0, fewerOthersNeeded: 0 };
  }
  return {
    total,
    host,
    other,
    share,
    met: false,
    moreHostNeeded: target >= 1 ? null : Math.ceil((target * total - host) / (1 - target) - 1e-9),
    fewerOthersNeeded: total - Math.floor(host / target + 1e-9),
  };
}

// The accepted share as it would be after giving `items` the status `status`.
// Each item needs its current `status` and `school`.
export function projectAcceptedShare(summary, items, status) {
  let { host, total } = summary.shares.accepted;
  items.forEach((item) => {
    const delta = Number(status === "ACCEPTED") - Number(item.status === "ACCEPTED");
    if (delta === 0) return;
    total += delta;
    if (isHostSchool(item.school, summary.hostSchool.name)) host += delta;
  });
  return hostShare(host, total, Number(summary.hostSchool.target));
}

// Rounds down, so a share just under the target never reads as the target.
export function formatShare(share) {
  if (typeof share !== "number") return "–";
  return `${Math.floor(share * 1000 + 1e-6) / 10}%`;
}

export const formatTarget = (target) => `${Number((Number(target) * 100).toFixed(1))}%`;

export function gapText(share, hostName) {
  if (!share || share.total === 0) return "Nobody accepted yet";
  if (share.met) return "At target";
  const fewer = `${plural(share.fewerOthersNeeded, "fewer acceptance")} from other schools`;
  if (share.moreHostNeeded === null) return `${fewer} needed`;
  return `${plural(share.moreHostNeeded, `more ${hostName} acceptance`)} needed, or ${fewer}`;
}

export const isAccepted = (item) => item?.status === "ACCEPTED";
export const isNotified = (item) => isAccepted(item) && Boolean(item.acceptanceNotifiedAt);
