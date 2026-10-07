import { useCallback, useEffect, useState } from "react";
import { api, getToken, MOCK_MODE, onSignedOut, setToken } from "./api.js";
import { href, navigate, useLoad, useRoute } from "./hooks.js";
import Hackers from "./pages/Hackers.jsx";
import Home from "./pages/Home.jsx";
import SetPassword from "./pages/SetPassword.jsx";
import SignIn from "./pages/SignIn.jsx";
import Teams from "./pages/Teams.jsx";
import { ErrorNote, ToastProvider, useToast } from "./ui.jsx";

const DISCORD_STATE_KEY = "peachhacks.platform.discord-state";

// Where PeachBot's Verify button sends people: Home, scrolled to the Discord card.
const DISCORD_PATH = "/discord";

const NAV = [
  { path: "/", label: "Home" },
  { path: "/hackers", label: "Hackers" },
  { path: "/teams", label: "Teams" },
];

// Discord sends the browser back to the bare origin with ?code=&state= (or ?error= when
// the person pressed Cancel). Read once, then taken out of the address bar.
function takeDiscordReturn() {
  const params = new URLSearchParams(window.location.search);
  if (!params.has("code") && !params.has("error")) return null;
  let expected = null;
  try {
    expected = window.sessionStorage.getItem(DISCORD_STATE_KEY);
    window.sessionStorage.removeItem(DISCORD_STATE_KEY);
  } catch {
    expected = null;
  }
  window.history.replaceState(null, "", `${window.location.pathname}${window.location.hash}`);
  if (params.has("error")) return { cancelled: true };
  return { code: params.get("code"), trusted: Boolean(expected) && expected === params.get("state") };
}

const discordReturn = takeDiscordReturn();
let discordReturnHandled = false;

// The state value ties the answer from Discord to this browser: only a connection that
// was started here is finished here.
function startDiscordConnect(config) {
  const state = crypto.randomUUID();
  window.sessionStorage.setItem(DISCORD_STATE_KEY, state);
  const params = new URLSearchParams({
    client_id: config.discordClientId,
    response_type: "code",
    redirect_uri: config.discordRedirectUri,
    scope: "identify guilds.join",
    state,
    prompt: "consent",
  });
  window.location.assign(`https://discord.com/oauth2/authorize?${params}`);
}

const loadConfig = (signal) => api.config(signal);
const loadMe = (signal) => api.me(signal);

function Shell({ onSignOut }) {
  const route = useRoute();
  const notify = useToast();
  const config = useLoad(loadConfig);
  const me = useLoad(loadMe);
  const reloadMe = me.reload;

  useEffect(() => {
    if (!discordReturn || discordReturnHandled) return;
    discordReturnHandled = true;
    if (discordReturn.cancelled) {
      notify("Discord was not connected.", "error");
    } else if (!discordReturn.trusted) {
      notify("That Discord link did not start here. Press Connect Discord and try again.", "error");
    } else {
      api
        .connectDiscord(discordReturn.code)
        .then((result) => {
          notify(`Connected as ${result.discordUsername}. You are in the server with the Hacker role.`);
          reloadMe();
        })
        .catch((error) => {
          notify(error.message, "error");
          reloadMe();
        });
    }
  }, [notify, reloadMe]);

  const signOut = async () => {
    try {
      await api.logout();
    } catch {
      // The session is dropped on this device either way.
    }
    onSignOut();
  };

  const page = NAV.some((item) => item.path === route.path) ? route.path : "/";
  const connectDiscord = config.data?.discordClientId ? () => startDiscordConnect(config.data) : null;

  return (
    <div className="shell">
      <header className="topbar">
        <a className="brand" href={href("/")}>
          <img src="/assets/logo.svg" alt="PeachHacks" height="34" />
          <span className="brand-tag">Hacker Platform</span>
        </a>
        <nav aria-label="Sections">
          {NAV.map((item) => (
            <a key={item.path} href={href(item.path)} aria-current={page === item.path ? "page" : undefined}>
              {item.label}
            </a>
          ))}
        </nav>
        <button type="button" className="btn btn-small" onClick={signOut}>
          Sign out
        </button>
      </header>
      <main className="content">
        {MOCK_MODE && <p className="note">Mock mode: this is sample data, and nothing is saved.</p>}
        {!me.data && <ErrorNote error={me.error} onRetry={me.reload} />}
        {!me.data && !me.error && <p className="muted">Loading…</p>}
        {me.data && page === "/" && (
          <Home
            me={me.data}
            connectDiscord={connectDiscord}
            reloadMe={me.reload}
            focusDiscord={route.path === DISCORD_PATH}
          />
        )}
        {me.data && page === "/hackers" && <Hackers me={me.data} />}
        {me.data && page === "/teams" && (
          <Teams me={me.data} maxTeamSize={config.data?.maxTeamSize} reloadMe={me.reload} />
        )}
      </main>
    </div>
  );
}

export default function App() {
  const route = useRoute();
  const [signedIn, setSignedIn] = useState(() => Boolean(getToken()));

  const signOut = useCallback(() => {
    setToken(null);
    setSignedIn(false);
  }, []);

  useEffect(() => {
    onSignedOut(() => setSignedIn(false));
    return () => onSignedOut(null);
  }, []);

  const signIn = useCallback((session) => {
    setToken(session.token);
    setSignedIn(true);
    // Someone who arrived on a link into the platform lands where the link pointed.
    if (window.location.hash.startsWith("#/set-password")) navigate("/");
  }, []);

  let view;
  if (route.path === "/set-password") view = <SetPassword token={route.query.get("token") || ""} onSignedIn={signIn} />;
  else if (!signedIn) view = <SignIn onSignedIn={signIn} />;
  else view = <Shell onSignOut={signOut} />;

  return <ToastProvider>{view}</ToastProvider>;
}
