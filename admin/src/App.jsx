import { useCallback, useEffect, useState } from "react";
import { api, getToken, onUnauthorized, setToken } from "./api/client.js";
import { CHECK_IN_PATH, Layout, NAV } from "./components/Layout.jsx";
import { EmptyBlock, ErrorBlock, LoadingBlock, ToastProvider } from "./components/ui.jsx";
import { isAdmin, isVolunteer } from "./lib/format.js";
import { href, navigate, useRoute } from "./lib/router.js";
import Acceptances from "./pages/Acceptances.jsx";
import CheckIn from "./pages/CheckIn.jsx";
import Email from "./pages/Email.jsx";
import Overview from "./pages/Overview.jsx";
import PreRegistrations from "./pages/PreRegistrations.jsx";
import Registrations from "./pages/Registrations.jsx";
import SetPassword from "./pages/SetPassword.jsx";
import Settings from "./pages/Settings.jsx";
import SignIn from "./pages/SignIn.jsx";

const SET_PASSWORD_PATH = "/set-password";

const signedOut = (notice = null) => ({ status: "signedOut", admin: null, notice, error: null });

export default function App() {
  const route = useRoute();
  const [session, setSession] = useState(() =>
    getToken() ? { status: "checking", admin: null, notice: null, error: null } : signedOut(),
  );
  const [signingOut, setSigningOut] = useState(false);

  useEffect(
    () => onUnauthorized(() => setSession(signedOut("Your session has ended. Sign in again to continue."))),
    [],
  );

  useEffect(() => {
    if (session.status !== "checking") return undefined;
    const controller = new AbortController();
    api.me(controller.signal).then(
      (admin) => setSession({ status: "signedIn", admin, notice: null, error: null }),
      (error) => {
        if (error?.name === "AbortError" || error?.code === "UNAUTHORIZED") return;
        setSession({ status: "error", admin: null, notice: null, error });
      },
    );
    return () => controller.abort();
  }, [session.status]);

  const volunteer = session.status === "signedIn" && isVolunteer(session.admin);
  // Anything that is neither an admin nor a volunteer (a lookup account) has no screen here.
  const appOnly = session.status === "signedIn" && !volunteer && !isAdmin(session.admin);

  useEffect(() => {
    const section = NAV.find((item) => item.path === route.path)?.label;
    document.title = session.status === "signedIn" && !appOnly && section ? `${section} · PeachHacks Admin` : "PeachHacks Admin";
  }, [route.path, session.status, appOnly]);

  // Volunteers have one screen; any other address (typed, bookmarked or left over from an
  // admin who used this browser) goes there. The API enforces the same limit.
  useEffect(() => {
    if (volunteer && route.path !== CHECK_IN_PATH && route.path !== SET_PASSWORD_PATH) navigate(CHECK_IN_PATH);
  }, [volunteer, route.path]);

  const handleSignedIn = useCallback((result) => {
    setToken(result.token);
    setSession({ status: "signedIn", admin: result.admin, notice: null, error: null });
  }, []);

  const handleSignOut = useCallback(async () => {
    setSigningOut(true);
    try {
      await api.logout();
    } catch {
      // Signing out locally still matters if the server call fails.
    }
    setToken(null);
    setSigningOut(false);
    setSession(signedOut());
  }, []);

  // Reached from an emailed link, so it shows whether or not this browser has a session.
  // Saving a password ends every session for that account, so the result is a fresh sign-in.
  if (route.path === SET_PASSWORD_PATH) {
    return (
      <SetPassword
        token={route.query.get("token") || ""}
        onDone={(notice) => {
          setToken(null);
          setSession(signedOut(notice));
          navigate("/");
        }}
      />
    );
  }

  if (session.status === "checking") {
    return (
      <div className="fullpage">
        <LoadingBlock label="Checking your session…" />
      </div>
    );
  }

  if (session.status === "error") {
    return (
      <div className="fullpage">
        <ErrorBlock
          title="Could not check your session"
          error={session.error}
          onRetry={() => setSession({ status: "checking", admin: null, notice: null, error: null })}
        />
        <button type="button" className="link-btn" onClick={handleSignOut}>
          Sign out instead
        </button>
      </div>
    );
  }

  if (session.status !== "signedIn") {
    return <SignIn notice={session.notice} onSignedIn={handleSignedIn} />;
  }

  if (appOnly) {
    return (
      <div className="fullpage">
        <div className="state-block">
          <strong>This account works in the PeachHacks staff app only</strong>
          <p>
            {session.admin?.email ? `${session.admin.email} is` : "You are"} signed in, but there is nothing for this
            account on the admin site. Sign in to the staff app instead.
          </p>
          <button type="button" className="btn" onClick={handleSignOut} disabled={signingOut}>
            {signingOut ? "Signing out…" : "Sign out"}
          </button>
        </div>
      </div>
    );
  }

  let page;
  switch (volunteer ? CHECK_IN_PATH : route.path) {
    case CHECK_IN_PATH:
      page = <CheckIn />;
      break;
    case "/":
      page = <Overview />;
      break;
    case "/pre-registrations":
      page = <PreRegistrations />;
      break;
    case "/registrations":
      page = <Registrations query={route.query} />;
      break;
    case "/acceptances":
      page = <Acceptances />;
      break;
    case "/email":
      page = <Email admin={session.admin} query={route.query} />;
      break;
    case "/settings":
      page = <Settings admin={session.admin} />;
      break;
    default:
      page = (
        <EmptyBlock title="Page not found">
          That address does not match a screen. <a href={href("/")}>Go to the overview</a>.
        </EmptyBlock>
      );
  }

  return (
    <ToastProvider>
      <Layout
        admin={session.admin}
        path={volunteer ? CHECK_IN_PATH : route.path}
        onSignOut={handleSignOut}
        signingOut={signingOut}
      >
        {page}
      </Layout>
    </ToastProvider>
  );
}
