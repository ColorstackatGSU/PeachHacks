const PRODUCTION = "https://api.peachhacks.com";

// 10.0.2.2 is how the Android emulator reaches the machine it runs on; the iOS
// simulator shares that machine's network, so localhost works there.
export function pickApiBase({ configured, dev, os }) {
  const chosen = (configured || "").trim();
  const base = chosen || (dev ? (os === "android" ? "http://10.0.2.2:8080" : "http://localhost:8080") : PRODUCTION);
  return base.replace(/\/+$/, "");
}
