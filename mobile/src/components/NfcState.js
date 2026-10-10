import { useCallback, useEffect, useState } from "react";
import { AppState, View } from "react-native";

import { nfcStatus, openNfcSettings } from "../nfc";
import { Button, Notice } from "./ui";

const DevEntry = __DEV__ ? require("../dev/DevEntry") : null;

// "checking", then "ready", "off" or "unsupported". Asked again whenever the app
// comes back to the front, which is when someone returns from switching NFC on.
export function useNfcStatus() {
  const [status, setStatus] = useState("checking");
  const check = useCallback(() => {
    nfcStatus().then(setStatus);
  }, []);

  useEffect(() => {
    check();
    const subscription = AppState.addEventListener("change", (next) => {
      if (next === "active") check();
    });
    return () => subscription.remove();
  }, [check]);

  return [status, check];
}

// Shown in place of the tap button when the phone cannot read a badge right now.
// `onDevUid` receives a typed UID in a development build on a device without NFC.
export function NfcUnavailable({ status, onDevUid }) {
  if (status === "off") {
    return (
      <View style={{ gap: 10 }}>
        <Notice tone="warn">NFC is switched off on this phone. Switch it on to read badges.</Notice>
        <Button title="Open NFC settings" variant="primary" onPress={openNfcSettings} />
      </View>
    );
  }
  if (status === "unsupported") {
    return (
      <View style={{ gap: 10 }}>
        <Notice tone="bad">This phone cannot read NFC badges. Use a phone with NFC.</Notice>
        {DevEntry && onDevUid ? <DevEntry.UidField onSubmit={onDevUid} /> : null}
      </View>
    );
  }
  return null;
}
