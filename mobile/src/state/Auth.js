import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

import { api, onUnauthorized, setToken } from "../api/client";
import { errorText } from "../lib/format";
import { clearSession, loadSession, rememberEmail, saveProfile, saveSession } from "../lib/session";

const AuthContext = createContext(null);

const SIGNED_OUT = { phase: "signed-out", admin: null, notice: null };

export function AuthProvider({ children }) {
  const [state, setState] = useState({ phase: "loading", admin: null, notice: null });

  useEffect(() => {
    let live = true;
    // Only the session is cleared here. Taps waiting to sync are stored separately
    // and stay on the phone.
    const off = onUnauthorized(() => {
      clearSession();
      setState({ ...SIGNED_OUT, notice: "Your session has ended. Sign in again to carry on." });
    });

    (async () => {
      const { token, admin } = await loadSession();
      if (!live) return;
      if (!token) {
        setState(SIGNED_OUT);
        return;
      }
      setToken(token);
      // The saved profile lets the app open without a connection, so taps can still be
      // saved; the server has the last word as soon as it can be reached.
      if (admin) setState({ phase: "signed-in", admin, notice: null });
      try {
        const fresh = await api.me();
        if (!live) return;
        saveProfile(fresh);
        setState({ phase: "signed-in", admin: fresh, notice: null });
      } catch (error) {
        if (!live || error?.status === 401 || admin) return;
        setToken(null);
        setState({ ...SIGNED_OUT, notice: errorText(error) });
      }
    })();

    return () => {
      live = false;
      off();
    };
  }, []);

  const signIn = useCallback(async (email, password) => {
    const result = await api.login(email.trim(), password);
    setToken(result.token);
    await saveSession(result.token, result.admin);
    rememberEmail(email.trim());
    setState({ phase: "signed-in", admin: result.admin, notice: null });
  }, []);

  const signOut = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      // The token is dropped from this phone either way.
    }
    setToken(null);
    await clearSession();
    setState(SIGNED_OUT);
  }, []);

  const value = useMemo(() => ({ ...state, signIn, signOut }), [state, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);

export const canCheckIn = (admin) => admin?.role === "ADMIN" || admin?.role === "VOLUNTEER";
export const canLookUp = (admin) => canCheckIn(admin) || admin?.role === "LOOKUP";
