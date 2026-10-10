import { useCallback, useEffect, useRef, useState } from "react";
import { Platform, ScrollView, Text, View } from "react-native";

import { api } from "../api/client";
import { NfcUnavailable, useNfcStatus } from "../components/NfcState";
import { Button, ConnectionBanner, Muted, ResultCard, styles as ui } from "../components/ui";
import { signal } from "../lib/feedback";
import { errorText, fullName } from "../lib/format";
import { looksLikeUid } from "../lib/uid";
import { cancelRead, nfcProblemText, readUid } from "../nfc";
import { colors } from "../theme";

const NOT_A_BADGE = {
  tone: "bad",
  title: "Not a PeachHacks badge",
  lines: ["That card is not a PeachHacks badge."],
};

function viewOf(response) {
  if (response?.result === "FOUND" && response.holder) {
    const { holder } = response;
    const tone = !holder.accepted ? "bad" : holder.checkedIn ? "good" : "warn";
    return {
      tone,
      title: !holder.accepted ? "Not accepted" : holder.checkedIn ? "Checked in" : "Not checked in yet",
      name: fullName(holder),
      lines: [
        holder.school,
        `Accepted: ${holder.accepted ? "yes" : "no"}`,
        `Checked in: ${holder.checkedIn ? "yes" : "no"}`,
      ],
    };
  }
  if (response?.result === "REVOKED_BADGE") {
    return {
      tone: "bad",
      title: "Badge no longer valid",
      lines: ["This badge was replaced or revoked. Send them to the check-in desk."],
    };
  }
  return {
    tone: "bad",
    title: "Unknown badge",
    lines: ["This badge is not linked to anyone. Send them to the check-in desk."],
  };
}

export default function Lookup() {
  const [nfc] = useNfcStatus();
  const [phase, setPhase] = useState("idle");
  const [view, setView] = useState(null);
  const live = useRef(true);

  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
      cancelRead();
    };
  }, []);

  const lookUp = useCallback(async (uid) => {
    if (!looksLikeUid(uid)) {
      setView(NOT_A_BADGE);
      setPhase("idle");
      signal("problem");
      return;
    }
    setPhase("asking");
    try {
      const response = await api.lookupBadge(uid);
      if (!live.current) return;
      const next = viewOf(response);
      setView(next);
      signal(next.tone === "good" ? "success" : "problem");
    } catch (error) {
      if (!live.current) return;
      setView(
        error?.status === 400
          ? NOT_A_BADGE
          : { tone: "bad", title: "Could not look up the badge", lines: [errorText(error)] },
      );
      signal("problem");
    } finally {
      if (live.current) setPhase("idle");
    }
  }, []);

  const tap = async () => {
    setView(null);
    setPhase("reading");
    try {
      const uid = await readUid({ message: "Hold the badge to the top of the phone." });
      if (live.current) await lookUp(uid);
    } catch (error) {
      if (!live.current) return;
      setPhase("idle");
      if (error.kind !== "cancelled") {
        setView({ tone: "bad", title: "Badge not read", lines: [nfcProblemText(error.kind)] });
      }
    }
  };

  return (
    <ScrollView contentContainerStyle={ui.screen}>
      <ConnectionBanner />

      {view ? <ResultCard tone={view.tone} title={view.title} name={view.name} lines={view.lines} /> : null}

      {nfc === "ready" ? (
        phase === "reading" && Platform.OS === "android" ? (
          <View style={{ gap: 10 }}>
            <Text style={{ color: colors.cream, fontSize: 22, fontWeight: "800", textAlign: "center" }}>
              Hold the badge to the back of the phone
            </Text>
            <Button title="Cancel" onPress={cancelRead} />
          </View>
        ) : (
          <Button
            title={view ? "Tap another badge" : "Tap a badge"}
            variant="primary"
            big
            busy={phase !== "idle"}
            onPress={tap}
          />
        )
      ) : (
        <NfcUnavailable status={nfc} onDevUid={lookUp} />
      )}

      {!view ? <Muted>Tap a badge to see whose it is and whether they are checked in.</Muted> : null}
    </ScrollView>
  );
}
