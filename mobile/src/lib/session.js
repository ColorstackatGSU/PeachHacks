import * as SecureStore from "expo-secure-store";

import { readJson, writeJson } from "./storage";

const TOKEN_KEY = "peachhacks.staff.token";
const PROFILE_KEY = "peachhacks.staff.profile";
const EMAIL_KEY = "peachhacks.staff.email";

export async function loadSession() {
  let token = null;
  try {
    token = await SecureStore.getItemAsync(TOKEN_KEY);
  } catch {
    token = null;
  }
  return { token, admin: token ? await readJson(PROFILE_KEY) : null };
}

export async function saveSession(token, admin) {
  await SecureStore.setItemAsync(TOKEN_KEY, token);
  await writeJson(PROFILE_KEY, admin).catch(() => {});
}

export const saveProfile = (admin) => writeJson(PROFILE_KEY, admin).catch(() => {});

export async function clearSession() {
  await SecureStore.deleteItemAsync(TOKEN_KEY).catch(() => {});
  await writeJson(PROFILE_KEY, null).catch(() => {});
}

// Only the email is remembered between days, never the password.
export const loadRememberedEmail = async () => (await readJson(EMAIL_KEY)) || "";
export const rememberEmail = (email) => writeJson(EMAIL_KEY, email).catch(() => {});
