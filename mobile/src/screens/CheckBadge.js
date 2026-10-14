import { useCallback, useEffect, useRef, useState } from "react";
import { Platform, StyleSheet, View } from "react-native";

import { api } from "../api/client";
import { JobHeader } from "../components/Header";
import { canRead, DevInput, NfcUnavailable, useNfcStatus } from "../components/NfcState";
import { Ready, ResultPanel } from "../components/Result";
import { PrimaryButton } from "../components/ui";
import { signal } from "../lib/feedback";
import { errorText } from "../lib/format";
import { lookupView, NOT_A_BADGE } from "../lib/results";
import { looksLikeUid } from "../lib/uid";
import { cancelRead, nfcProblemText, readUid, startContinuous } from "../nfc";
import { space } from "../theme";

// Android reads for as long as the screen is open. iOS needs its scan sheet opened
// for each read, so it has a button.
const continuous = Platform.OS === "android";

export default function CheckBadge({ onBack }) {
  const [nfc] = useNfcStatus();
  const [view, setView] = useState(null);
  const [busy, setBusy] = useState(false);
  const [pulse, setPulse] = useState(0);
  const live = useRef(true);
  const reader = useRef(null);

  const show = useCallback((next) => {
    if (!live.current) return;
    setView(next);
    setPulse((n) => n + 1);
    signal(next.family);
  }, []);

  const lookUp = useCallback(
    async (uid) => {
      if (!looksLikeUid(uid)) {
        show(NOT_A_BADGE);
        return;
      }
      setBusy(true);
      try {
        show(lookupView(await api.lookupBadge(uid)));
      } catch (error) {
        reader.current?.allowRetry();
        if (error?.status === 400) show(NOT_A_BADGE);
        else if (error?.status !== 401) {
          show({ family: "void", icon: "wifi-off", word: "Could not check the badge", detail: `${errorText(error)} Hold it to the phone again.` });
        }
      } finally {
        if (live.current) setBusy(false);
      }
    },
    [show],
  );

  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
      cancelRead();
    };
  }, []);

  useEffect(() => {
    if (!continuous || nfc !== "ready") return undefined;
    reader.current = startContinuous({ onUid: lookUp });
    return () => {
      reader.current?.stop();
      reader.current = null;
    };
  }, [nfc, lookUp]);

  const readOnce = async () => {
    setBusy(true);
    try {
      const uid = await readUid({ message: "Hold the badge to the top of the phone." });
      if (live.current) await lookUp(uid);
    } catch (error) {
      if (live.current && error.kind !== "cancelled") {
        show({ family: "void", icon: "nfc-variant-off", word: "Badge not read", detail: nfcProblemText(error.kind) });
      }
    } finally {
      if (live.current) setBusy(false);
    }
  };

  return (
    <View style={styles.root}>
      <JobHeader title="Check a badge" onBack={onBack} />
      <View style={styles.body}>
        <View style={styles.stage}>
          {view ? <ResultPanel view={view} pulse={pulse} /> : canRead(nfc) ? <Ready title={busy ? "Checking…" : "Ready"} /> : null}
        </View>
        <View style={styles.bottom}>
          <NfcUnavailable status={nfc} />
          <DevInput show={nfc === "simulated"} onSubmit={lookUp} />
          {!continuous && nfc === "ready" ? (
            <PrimaryButton title={view ? "Check another badge" : "Check a badge"} icon="nfc" busy={busy} onPress={readOnce} />
          ) : null}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  body: { flex: 1, paddingHorizontal: space.gutter, paddingBottom: space.lg, gap: space.lg },
  stage: { minHeight: 260, justifyContent: "center" },
  bottom: { flex: 1, justifyContent: "flex-end", gap: space.sm },
});
