import { useCallback, useEffect, useState } from "react";
import { api, getToken, onUnauthorized, setToken } from "./api/client.js";
import { CHECK_IN_PATH, Layout, NAV } from "./components/Layout.jsx";
import { EmptyBlock, ErrorBlock, LoadingBlock, ToastProvider } from "./components/ui.jsx";
import { isVolunteer } from "./lib/format.js";
import { href, navigate, useRoute } from "./lib/router.js";
import CheckIn from "./pages/CheckIn.jsx";
import Email from "./pages/Email.jsx";
import Overview from "./pages/Overview.jsx";
import PreRegistrations from "./pages/PreRegistrations.jsx";
import Registrations from "./pages/Registrations.jsx";
import Settings from "./pages/Settings.jsx";
import SignIn from "./pages/SignIn.jsx";

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

  useEffect(() => {
    const section = NAV.find((item) => item.path === route.path)?.label;
    document.title = session.status === "signedIn" && section ? `${section} · PeachHacks Admin` : "PeachHacks Admin";
  }, [route.path, session.status]);

  const volunteer = session.status === "signedIn" && isVolunteer(session.admin);

  // Volunteers have one screen; any other address (typed, bookmarked or left over from an
  // admin who used this browser) goes there. The API enforces the same limit.
  useEffect(() => {
    if (volunteer && route.path !== CHECK_IN_PATH) navigate(CHECK_IN_PATH);
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

  let page;
  switch (volunteer ? CHECK_IN_PATH : route.path) {
    case CHECK_IN_PATH:
      page = <CheckIn admin={session.admin} />;
      break;
    case "/":
      page = <Overview />;
      break;
    case "/pre-registrations":
      page = <PreRegistrations />;
      break;
    case "/registrations":
      page = <Registrations />;
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
