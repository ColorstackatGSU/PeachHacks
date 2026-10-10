import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

import { api, onUnauthorized, setToken } from "../api/client";
import { errorText } from "../lib/format";
import { clearToken, loadToken, rememberEmail, saveToken } from "../lib/session";

const AuthContext = createContext(null);

const SIGNED_OUT = { phase: "signed-out", admin: null, notice: null };

export function AuthProvider({ children }) {
  const [state, setState] = useState({ phase: "loading", admin: null, notice: null });

  const [attempt, setAttempt] = useState(0);

  useEffect(
    () =>
      onUnauthorized(() => {
        clearToken();
        setState({ ...SIGNED_OUT, notice: "Your session has ended. Sign in again to carry on." });
      }),
    [],
  );

  // "unreachable" means a token is stored but the server could not be asked who it
  // belongs to. The token is kept, so trying again needs no password.
  useEffect(() => {
    let live = true;
    (async () => {
      const token = await loadToken();
      if (!live) return;
      if (!token) {
        setState(SIGNED_OUT);
        return;
      }
      setToken(token);
      try {
        const admin = await api.me();
        if (live) setState({ phase: "signed-in", admin, notice: null });
      } catch (error) {
        if (live && error?.status !== 401) setState({ phase: "unreachable", admin: null, notice: errorText(error) });
      }
    })();
    return () => {
      live = false;
    };
  }, [attempt]);

  const retry = useCallback(() => {
    setState({ phase: "loading", admin: null, notice: null });
    setAttempt((n) => n + 1);
  }, []);

  const signIn = useCallback(async (email, password) => {
    const result = await api.login(email.trim(), password);
    setToken(result.token);
    await saveToken(result.token);
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
    await clearToken();
    setState(SIGNED_OUT);
  }, []);

  const value = useMemo(() => ({ ...state, signIn, signOut, retry }), [state, signIn, signOut, retry]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);

export const canCheckIn = (admin) => admin?.role === "ADMIN" || admin?.role === "VOLUNTEER";
export const canLookUp = (admin) => canCheckIn(admin) || admin?.role === "LOOKUP";
