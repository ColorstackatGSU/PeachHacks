import AsyncStorage from "@react-native-async-storage/async-storage";

export async function readJson(key) {
  try {
    const text = await AsyncStorage.getItem(key);
    return text ? JSON.parse(text) : null;
  } catch {
    return null;
  }
}

export async function writeJson(key, value) {
  if (value === null || value === undefined) await AsyncStorage.removeItem(key);
  else await AsyncStorage.setItem(key, JSON.stringify(value));
}
