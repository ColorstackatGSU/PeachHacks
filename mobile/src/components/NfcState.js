import { useCallback, useEffect, useState } from "react";
import { AppState, View } from "react-native";

import { nfcStatus, openNfcSettings } from "../nfc";
import { space } from "../theme";
import { Bar, PrimaryButton } from "./ui";

const DevEntry = __DEV__ ? require("../dev/DevEntry") : null;

// "checking", then "ready", "off" or "unsupported". Asked again whenever the app
// comes back to the front, which is when someone returns from switching NFC on.
// A development build on a device with no NFC answers "simulated": screens behave
// as if a reader were listening, and the Dev input supplies the UIDs.
export function useNfcStatus() {
  const [status, setStatus] = useState("checking");
  const check = useCallback(() => {
    nfcStatus().then((next) => setStatus(DevEntry && next === "unsupported" ? "simulated" : next));
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

export const canRead = (status) => status === "ready" || status === "simulated";

// Shown in place of the tap controls when the phone cannot read a badge right now.
export function NfcUnavailable({ status }) {
  if (status === "off") {
    return (
      <View style={{ gap: space.md }}>
        <Bar tone="repeat" icon="nfc-variant-off">
          NFC is switched off on this phone.
        </Bar>
        <PrimaryButton title="Open NFC settings" onPress={openNfcSettings} />
      </View>
    );
  }
  if (status === "unsupported") {
    return (
      <Bar tone="stop" icon="nfc-variant-off">
        This phone cannot read NFC badges. Use a phone with NFC.
      </Bar>
    );
  }
  return null;
}

// The folded-away stand-in for a card or a ticket. Renders nothing in a release build.
export function DevInput({ show = true, kind = "uid", onSubmit }) {
  if (!DevEntry || !show) return null;
  return <DevEntry.Input kind={kind} onSubmit={onSubmit} />;
}
