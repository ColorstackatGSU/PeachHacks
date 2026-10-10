import { useCallback, useEffect, useRef, useState } from "react";
import { ActivityIndicator, Platform, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";

import { api, isTemporaryFailure } from "../api/client";
import { NfcUnavailable, useNfcStatus } from "../components/NfcState";
import { Button, ConnectionBanner, Muted, Notice, ResultCard, styles as ui } from "../components/ui";
import { signal } from "../lib/feedback";
import { errorText, formatWhen, fullName, whenAndWho } from "../lib/format";
import { looksLikeUid } from "../lib/uid";
import { startContinuous } from "../nfc";
import { colors, tones } from "../theme";

const RECENT_KEPT = 8;

const NOT_A_BADGE = { tone: "bad", title: "Not a PeachHacks badge", lines: ["That card is not a PeachHacks badge."] };

const NOT_RECORDED = {
  tone: "bad",
  title: "NOT RECORDED",
  lines: ["This tap did not reach the server and was not saved.", "Check the connection, then tap the badge again."],
};

function viewOf(response, event) {
  const { result, item } = response || {};
  if (result === "CHECKED_IN") {
    return {
      tone: "good",
      feedback: "success",
      counts: true,
      title: "Checked in",
      name: fullName(item),
      lines: [
        item?.school,
        !event.general && item && !item.generalCheckedIn
          ? "Has not done general check-in yet. Send them to the front desk afterwards."
          : null,
      ],
    };
  }
  if (result === "ALREADY_CHECKED_IN") {
    return {
      tone: "warn",
      feedback: "warning",
      title: "ALREADY CHECKED IN",
      name: fullName(item),
      lines: [`Checked in for ${event.name} ${whenAndWho(item)}`.trim()],
    };
  }
  if (result === "NOT_ACCEPTED") {
    return {
      tone: "bad",
      title: "Not accepted",
      name: fullName(item),
      lines: ["They have not been checked in, and can’t be until an organizer accepts them."],
    };
  }
  if (result === "REVOKED_BADGE") {
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

function useEvents() {
  const [state, setState] = useState({ events: null, error: null });

  const load = useCallback(async () => {
    try {
      const events = await api.events();
      setState({ events: Array.isArray(events) ? events : [], error: null });
    } catch (error) {
      setState((prev) => ({ ...prev, error }));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return { ...state, reload: load };
}

export default function EventTaps() {
  const [nfc, recheckNfc] = useNfcStatus();
  const { events, error: eventsError, reload } = useEvents();
  const [chosen, setChosen] = useState(null);
  const [reading, setReading] = useState("stopped");
  const [view, setView] = useState(null);
  const [recent, setRecent] = useState([]);
  const [count, setCount] = useState(0);

  const reader = useRef(null);
  const chain = useRef(Promise.resolve());
  const live = useRef(true);
  const eventRef = useRef(null);

  const event = (events || []).find((item) => item.id === chosen) || null;
  useEffect(() => {
    eventRef.current = event;
  });

  const stop = useCallback(() => {
    reader.current?.stop();
    reader.current = null;
    setReading("stopped");
  }, []);

  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
      reader.current?.stop();
      reader.current = null;
    };
  }, []);

  const show = useCallback((next) => {
    if (!live.current) return;
    setView(next);
    signal(next.feedback || (next.tone === "good" ? "success" : "problem"));
    reader.current?.setMessage([next.title, next.name].filter(Boolean).join(": "));
    setRecent((list) =>
      [{ key: `${Date.now()}-${Math.random()}`, at: new Date().toISOString(), ...next }, ...list].slice(0, RECENT_KEPT),
    );
    if (next.counts) setCount((prev) => prev + 1);
  }, []);

  const handle = useCallback(
    async (uid) => {
      const current = eventRef.current;
      if (!current) return;
      if (!looksLikeUid(uid)) {
        show(NOT_A_BADGE);
        return;
      }
      try {
        show(viewOf(await api.tapBadge({ uid, eventId: current.id }), current));
      } catch (error) {
        if (isTemporaryFailure(error)) {
          reader.current?.allowRetry();
          show(NOT_RECORDED);
        } else if (error?.status === 401) {
          stop();
        } else if (error?.status === 404) {
          stop();
          show({ tone: "bad", title: "Event not found", lines: ["This event no longer exists. Pick another event."] });
          setChosen(null);
          reload();
        } else if (error?.status === 400) {
          show(NOT_A_BADGE);
        } else {
          reader.current?.allowRetry();
          show({ tone: "bad", title: "NOT RECORDED", lines: [errorText(error)] });
        }
      }
    },
    [reload, show, stop],
  );

  // Cards are handled one after another, in the order they were read.
  const onUid = useCallback(
    (uid) => {
      chain.current = chain.current.then(() => handle(uid)).catch(() => {});
    },
    [handle],
  );

  const start = () => {
    reader.current?.stop();
    setReading("starting");
    reader.current = startContinuous({
      message: `Tap badges for ${event.name}.`,
      onUid,
      onState: (state) => {
        if (!live.current) return;
        if (state !== "reading") {
          reader.current = null;
          recheckNfc();
        }
        setReading(state === "reading" ? "reading" : state === "paused" ? "paused" : "stopped");
        if (state === "failed") setView({ tone: "bad", title: "Badge reading stopped", lines: ["Start tapping again."] });
      },
    });
  };

  if (!event) {
    return (
      <ScrollView contentContainerStyle={ui.screen}>
        <ConnectionBanner />
        <Text style={styles.heading}>Which event are you tapping for?</Text>
        {!events && !eventsError ? <ActivityIndicator color={colors.peach} /> : null}
        {eventsError ? (
          <>
            <Notice tone="bad">Could not load the event list: {errorText(eventsError)}</Notice>
            <Button title="Try again" onPress={reload} />
          </>
        ) : null}
        {events && events.length === 0 ? <Muted>There are no events yet. An organizer adds them on the admin site.</Muted> : null}
        {(events || []).map((item) => (
          <Pressable
            key={item.id}
            accessibilityRole="button"
            onPress={() => {
              setChosen(item.id);
              setView(null);
              setRecent([]);
              setCount(0);
            }}
            style={({ pressed }) => [styles.event, pressed && { opacity: 0.8 }]}
          >
            <Text style={styles.eventName}>{item.name}</Text>
            {item.general ? <Muted>General check-in</Muted> : null}
          </Pressable>
        ))}
      </ScrollView>
    );
  }

  return (
    <ScrollView contentContainerStyle={ui.screen}>
      {view ? (
        <ResultCard tone={view.tone} title={view.title} name={view.name} lines={view.lines} />
      ) : (
        <View style={styles.waiting}>
          <Text style={styles.waitingText}>
            {reading === "reading" ? "Ready. Tap a badge." : "Press Start tapping, then tap badges one after another."}
          </Text>
        </View>
      )}

      <ConnectionBanner />

      <View style={styles.bar}>
        <View style={{ flex: 1 }}>
          <Text style={styles.eventName}>{event.name}</Text>
          <Muted>{count} checked in on this phone</Muted>
        </View>
        <Button
          title="Change"
          onPress={() => {
            stop();
            setChosen(null);
          }}
        />
      </View>

      {nfc === "off" || nfc === "unsupported" ? (
        <NfcUnavailable status={nfc} onDevUid={onUid} />
      ) : reading === "reading" ? (
        <View style={{ gap: 10 }}>
          {Platform.OS === "android" ? <Text style={styles.hold}>Hold each badge to the back of the phone</Text> : null}
          <Button title="Stop tapping" big onPress={stop} />
        </View>
      ) : (
        <>
          {reading === "paused" ? <Muted>Badge reading paused. iOS closes the reader after a minute.</Muted> : null}
          <Button
            title={reading === "paused" ? "Resume tapping" : "Start tapping"}
            variant="primary"
            big
            busy={reading === "starting" || nfc === "checking"}
            onPress={start}
          />
        </>
      )}

      {recent.length > 1 ? (
        <View style={styles.recent}>
          <Text style={styles.recentTitle}>Recent</Text>
          {recent.slice(1).map((entry) => (
            <View key={entry.key} style={styles.recentRow}>
              <View style={[styles.dot, { backgroundColor: (tones[entry.tone] || tones.quiet).background }]} />
              <Text style={styles.recentText} numberOfLines={1}>
                {formatWhen(entry.at)} · {entry.title}
                {entry.name ? ` · ${entry.name}` : ""}
              </Text>
            </View>
          ))}
        </View>
      ) : null}

      {view ? null : <Muted>Every tap is checked with the server, so this needs a connection.</Muted>}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  heading: { color: colors.cream, fontSize: 22, fontWeight: "800" },
  event: { padding: 16, borderRadius: 6, borderWidth: 1, borderColor: colors.lineStrong, backgroundColor: colors.panel },
  eventName: { color: colors.cream, fontSize: 19, fontWeight: "800" },
  waiting: { padding: 20, borderRadius: 6, borderWidth: 1, borderStyle: "dashed", borderColor: colors.lineStrong },
  waitingText: { color: colors.mist, fontSize: 20, fontWeight: "700", textAlign: "center" },
  bar: { flexDirection: "row", alignItems: "center", gap: 12 },
  hold: { color: colors.cream, fontSize: 20, fontWeight: "800", textAlign: "center" },
  recent: { gap: 6 },
  recentTitle: { color: colors.peach, fontSize: 12, fontWeight: "800", letterSpacing: 1.5, textTransform: "uppercase" },
  recentRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  recentText: { flex: 1, color: colors.cream, fontSize: 15 },
  dot: { width: 12, height: 12, borderRadius: 6 },
});
