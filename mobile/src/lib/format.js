const timeFormat = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });
const dateTimeFormat = new Intl.DateTimeFormat(undefined, {
  month: "short",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
});

function toDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatWhen(value) {
  const date = toDate(value);
  if (!date) return "";
  return date.toDateString() === new Date().toDateString() ? timeFormat.format(date) : dateTimeFormat.format(date);
}

export const fullName = (person) => [person?.firstName, person?.lastName].filter(Boolean).join(" ") || "(no name)";

export const plural = (count, one, many = `${one}s`) => `${count} ${count === 1 ? one : many}`;

export const errorText = (error) => error?.message || "Something went wrong.";

export const statusLabel = (value) => (value ? value.charAt(0) + value.slice(1).toLowerCase() : "");

export const whenAndWho = (item) =>
  [item?.checkedInAt && `at ${formatWhen(item.checkedInAt)}`, item?.checkedInBy && `by ${item.checkedInBy}`]
    .filter(Boolean)
    .join(" ");
