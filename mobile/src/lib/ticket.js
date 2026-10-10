export const MAX_CODE_LENGTH = 2048;

// A ticket QR holds a bare token or the ticket page's URL; the server parses either.
export function cleanTicketCode(value) {
  const code = typeof value === "string" ? value.trim() : "";
  return code && code.length <= MAX_CODE_LENGTH ? code : null;
}
