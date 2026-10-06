import { useEffect, useRef } from "react";
import { MOCK_MODE } from "../api/client.js";
import { isVolunteer } from "../lib/format.js";
import { href } from "../lib/router.js";

export const CHECK_IN_PATH = "/check-in";

export const NAV = [
  { path: "/", label: "Overview" },
  { path: "/pre-registrations", label: "Pre-registrations" },
  { path: "/registrations", label: "Registrations" },
  { path: CHECK_IN_PATH, label: "Check-in", volunteer: true },
  { path: "/email", label: "Email" },
  { path: "/settings", label: "Settings" },
];

export const navFor = (account) => (isVolunteer(account) ? NAV.filter((item) => item.volunteer) : NAV);

export function Layout({ admin, path, onSignOut, signingOut, children }) {
  const mainRef = useRef(null);
  const firstRender = useRef(true);

  // Move focus to the new screen on navigation so keyboard and screen reader
  // users are not left on the link they just activated.
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    mainRef.current?.focus();
    window.scrollTo(0, 0);
  }, [path]);

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <aside className="sidebar">
        <a
          className="brand"
          href={href(isVolunteer(admin) ? CHECK_IN_PATH : "/")}
          aria-label={isVolunteer(admin) ? "PeachHacks Admin, check-in" : "PeachHacks Admin, overview"}
        >
          <img src="/assets/logo.svg" alt="" width="210" height="79" />
          <span>Admin</span>
        </a>
        <nav className="nav" aria-label="Sections">
          {navFor(admin).map((item) => (
            <a key={item.path} href={href(item.path)} aria-current={item.path === path ? "page" : undefined}>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="sidebar-foot">
          <div className="who">
            <strong>{admin?.name || "Signed in"}</strong>
            <span>{admin?.email}</span>
          </div>
          <button type="button" className="btn btn-small" onClick={onSignOut} disabled={signingOut}>
            {signingOut ? "Signing out…" : "Sign out"}
          </button>
        </div>
      </aside>
      <div className="content">
        {MOCK_MODE && (
          <p className="mock-banner" role="note">
            Mock data. Nothing on this screen comes from, or is saved to, the real API.
          </p>
        )}
        <main id="main" ref={mainRef} tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
