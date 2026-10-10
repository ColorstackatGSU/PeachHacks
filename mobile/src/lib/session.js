import * as SecureStore from "expo-secure-store";

const TOKEN_KEY = "peachhacks.staff.token";
const EMAIL_KEY = "peachhacks.staff.email";

async function read(key) {
  try {
    return await SecureStore.getItemAsync(key);
  } catch {
    return null;
  }
}

export const loadToken = () => read(TOKEN_KEY);
export const saveToken = (token) => SecureStore.setItemAsync(TOKEN_KEY, token);
export const clearToken = () => SecureStore.deleteItemAsync(TOKEN_KEY).catch(() => {});

// Only the email is remembered between days, never the password.
export const loadRememberedEmail = async () => (await read(EMAIL_KEY)) || "";
export const rememberEmail = (email) => SecureStore.setItemAsync(EMAIL_KEY, email).catch(() => {});
