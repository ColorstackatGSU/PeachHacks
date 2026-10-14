import { useKeepAwake } from "expo-keep-awake";
import { useCallback, useEffect, useRef, useState } from "react";
import { StyleSheet, View } from "react-native";

import { api, isTemporaryFailure } from "../api/client";
import { JobHeader } from "../components/Header";
import { canRead, DevInput, NfcUnavailable, useNfcStatus } from "../components/NfcState";
import { Ready, ResultPanel } from "../components/Result";
import { PrimaryButton, T } from "../components/ui";
import { signal } from "../lib/feedback";
import { errorText, formatWhen } from "../lib/format";
import { NOT_A_BADGE, NOT_SAVED, tapView } from "../lib/results";
import { looksLikeUid } from "../lib/uid";
import { startContinuous } from "../nfc";
import { colors, space } from "../theme";

const RECENT_KEPT = 3;
// A success goes back to Ready by itself; everything else waits for the next badge.
const CLEAR_MS = 1500;

// One event's door: the reader is on from the moment the screen opens, and every
// badge gets a verdict.
export default function EventScreen({ event, version, onBack, onGone }) {
  useKeepAwake();
  const [nfc, recheckNfc] = useNfcStatus();
  const [reader, setReader] = useState("starting");
  const [attempt, setAttempt] = useState(0);
  const [view, setView] = useState(null);
  const [pulse, setPulse] = useState(0);
  const [recent, setRecent] = useState([]);
  const [counted, setCounted] = useState({ version: 0, n: 0 });

  const session = useRef(null);
  const chain = useRef(Promise.resolve());
  const live = useRef(true);
  const clearTimer = useRef(null);
  const versionRef = useRef(version);
  useEffect(() => {
    versionRef.current = version;
  });

  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
      clearTimeout(clearTimer.current);
    };
  }, []);

  const show = useCallback((next) => {
    if (!live.current) return;
    clearTimeout(clearTimer.current);
    setView(next);
    setPulse((n) => n + 1);
    signal(next.family);
    session.current?.setMessage([next.word, next.title].filter(Boolean).join(": "));
    setRecent((list) => [{ key: `${Date.now()}-${Math.random()}`, at: new Date().toISOString(), ...next }, ...list].slice(0, RECENT_KEPT));
    // Check-ins since the list was last fetched are added to its count; a newer
    // list already includes them.
    if (next.counts) {
      const listVersion = versionRef.current;
      setCounted((prev) => ({ version: listVersion, n: (prev.version === listVersion ? prev.n : 0) + 1 }));
    }
    if (next.clears) clearTimer.current = setTimeout(() => live.current && setView(null), CLEAR_MS);
  }, []);

  const handle = useCallback(
    async (uid) => {
      if (!looksLikeUid(uid)) {
        show(NOT_A_BADGE);
        return;
      }
      try {
        show(tapView(await api.tapBadge({ uid, eventId: event.id }), event));
      } catch (failure) {
        if (failure?.status === 401) return;
        if (failure?.status === 404) onGone();
        else if (failure?.status === 400) show(NOT_A_BADGE);
        else {
          session.current?.allowRetry();
          show(isTemporaryFailure(failure) ? NOT_SAVED : { ...NOT_SAVED, detail: errorText(failure) });
        }
      }
    },
    [event, onGone, show],
  );

  // Cards are handled one after another, in the order they were read.
  const onUid = useCallback(
    (uid) => {
      chain.current = chain.current.then(() => handle(uid)).catch(() => {});
    },
    [handle],
  );

  useEffect(() => {
    if (nfc !== "ready") return undefined;
    const current = startContinuous({
      message: `Hold badges to the top of the phone for ${event.name}.`,
      onUid,
      onState: (state) => {
        if (!live.current) return;
        if (state === "reading") {
          setReader("reading");
          return;
        }
        session.current = null;
        recheckNfc();
        setReader(state === "paused" ? "paused" : "failed");
      },
    });
    session.current = current;
    return () => {
      current.stop();
      if (session.current === current) session.current = null;
    };
  }, [event.id, event.name, attempt, nfc, onUid, recheckNfc]);

  const listening = nfc === "simulated" || (nfc === "ready" && reader === "reading");
  const stopped = nfc === "ready" && (reader === "paused" || reader === "failed");
  const total = typeof event.checkedIn === "number" ? event.checkedIn + (counted.version === version ? counted.n : 0) : null;

  return (
    <View style={styles.root}>
      <JobHeader
        title={event.name}
        onBack={onBack}
        right={
          total === null ? null : (
            <T v="row" color={colors.textMuted} style={styles.numbers}>
              {total} in
            </T>
          )
        }
      />
      <View style={styles.body}>
        <View style={styles.stage}>
          {view ? (
            <ResultPanel view={view} pulse={pulse} />
          ) : stopped ? (
            <Ready title="Reader stopped" hint={reader === "paused" ? "The phone closed the reader. Tap below to keep reading." : null} />
          ) : listening ? (
            <Ready />
          ) : canRead(nfc) || nfc === "checking" ? (
            <Ready title="Starting the reader…" hint={null} />
          ) : null}
        </View>

        {recent.length > 1 ? (
          <View style={styles.recent}>
            {recent.slice(1).map((entry) => (
              <View key={entry.key} style={styles.recentRow}>
                <T v="secondary" color={colors.textMuted} numberOfLines={1} style={styles.recentText}>
                  {[entry.word, entry.titleIsName === false ? null : entry.title].filter(Boolean).join(" · ")}
                </T>
                <T v="secondary" color={colors.textMuted} style={styles.numbers}>
                  {formatWhen(entry.at)}
                </T>
              </View>
            ))}
          </View>
        ) : null}

        <View style={styles.bottom}>
          <NfcUnavailable status={nfc} />
          <DevInput show={nfc === "simulated"} onSubmit={onUid} />
          {stopped ? (
            <PrimaryButton
              title="Tap to keep reading"
              icon="nfc"
              onPress={() => {
                setReader("starting");
                setAttempt((n) => n + 1);
              }}
            />
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
  numbers: { fontVariant: ["tabular-nums"] },
  recent: { gap: space.xs },
  recentRow: { flexDirection: "row", alignItems: "center", gap: space.md },
  recentText: { flex: 1 },
  bottom: { flex: 1, justifyContent: "flex-end", gap: space.sm },
});
