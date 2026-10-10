import { useCallback, useEffect, useRef, useState } from "react";
import { ActivityIndicator, Platform, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";

import { api } from "../api/client";
import { NfcUnavailable, useNfcStatus } from "../components/NfcState";
import { QueuePanel } from "../components/QueuePanel";
import { Button, Muted, Notice, ResultCard, styles as ui } from "../components/ui";
import { signal } from "../lib/feedback";
import { errorText, formatWhen, fullName, whenAndWho } from "../lib/format";
import { readJson, writeJson } from "../lib/storage";
import { classifyError } from "../lib/tapQueue";
import { looksLikeUid } from "../lib/uid";
import { startContinuous } from "../nfc";
import { useOnline, useQueue } from "../state/Queue";
import { colors, tones } from "../theme";

const EVENTS_KEY = "peachhacks.staff.events";
const RECENT_KEPT = 8;

const NOT_A_BADGE = { tone: "bad", title: "Not a PeachHacks badge", lines: ["That card is not a PeachHacks badge."] };

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
  const [state, setState] = useState({ events: null, error: null, fromCache: false });

  const load = useCallback(async () => {
    const cached = await readJson(EVENTS_KEY);
    if (Array.isArray(cached)) setState((prev) => (prev.events ? prev : { events: cached, error: null, fromCache: true }));
    try {
      const events = await api.events();
      if (!Array.isArray(events)) return;
      writeJson(EVENTS_KEY, events).catch(() => {});
      setState({ events, error: null, fromCache: false });
    } catch (error) {
      setState((prev) => ({ ...prev, error, fromCache: Boolean(prev.events) }));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return { ...state, reload: load };
}

export default function EventTaps() {
  const online = useOnline();
  const { queue, pending } = useQueue();
  const [nfc, recheckNfc] = useNfcStatus();
  const { events, error: eventsError, fromCache, reload } = useEvents();
  const [chosen, setChosen] = useState(null);
  const [reading, setReading] = useState("stopped");
  const [view, setView] = useState(null);
  const [recent, setRecent] = useState([]);
  const [counts, setCounts] = useState({ checkedIn: 0, saved: 0 });

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
    if (next.counts) setCounts((prev) => ({ ...prev, checkedIn: prev.checkedIn + 1 }));
    if (next.saved) setCounts((prev) => ({ ...prev, saved: prev.saved + 1 }));
  }, []);

  const handle = useCallback(
    async (uid) => {
      const current = eventRef.current;
      if (!current) return;
      const tappedAt = new Date().toISOString();
      if (!looksLikeUid(uid)) {
        show(NOT_A_BADGE);
        return;
      }
      try {
        const response = await api.tapBadge({ uid, eventId: current.id, tappedAt });
        if (response?.result === "CHECKED_IN" || response?.result === "ALREADY_CHECKED_IN") {
          queue.noteTap({ uid, eventId: current.id, tappedAt });
        }
        show(viewOf(response, current));
      } catch (error) {
        const kind = classifyError(error);
        if (kind === "retry") {
          const { queued, earlierTapAt } = await queue.enqueue({
            uid,
            eventId: current.id,
            eventName: current.name,
            tappedAt,
          });
          if (earlierTapAt) {
            show({
              tone: "warn",
              feedback: "warning",
              title: "ALREADY TAPPED ON THIS PHONE",
              lines: [
                `This badge was tapped for ${current.name} at ${formatWhen(earlierTapAt)}.`,
                "No connection, so the name cannot be shown and other phones cannot be checked.",
              ],
            });
          } else {
            show({
              tone: "saved",
              feedback: "success",
              saved: queued,
              title: "Saved, will sync",
              lines: ["No connection, so the name cannot be shown. The tap is saved on this phone."],
            });
          }
        } else if (kind === "signed-out") {
          stop();
        } else if (error?.status === 404) {
          stop();
          show({ tone: "bad", title: "Event not found", lines: ["This event no longer exists. Pick another event."] });
          setChosen(null);
          reload();
        } else if (error?.status === 400) {
          show(NOT_A_BADGE);
        } else {
          show({ tone: "bad", title: "Tap not recorded", lines: [errorText(error)] });
        }
      }
    },
    [queue, reload, show, stop],
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
        {!online ? (
          <Notice tone="warn">No connection. Taps are saved on this phone and synced when it is back.</Notice>
        ) : null}
        <Text style={styles.heading}>Which event are you tapping for?</Text>
        {!events && !eventsError ? <ActivityIndicator color={colors.peach} /> : null}
        {eventsError && !events ? (
          <>
            <Notice tone="bad">
              Could not load the event list: {errorText(eventsError)} The list is saved on this phone after it loads
              once.
            </Notice>
            <Button title="Try again" onPress={reload} />
          </>
        ) : null}
        {events && fromCache ? <Muted>Showing the event list saved on this phone.</Muted> : null}
        {events && events.length === 0 ? <Muted>There are no events yet. An organizer adds them on the admin site.</Muted> : null}
        {(events || []).map((item) => (
          <Pressable
            key={item.id}
            accessibilityRole="button"
            onPress={() => {
              setChosen(item.id);
              setView(null);
              setRecent([]);
              setCounts({ checkedIn: 0, saved: 0 });
            }}
            style={({ pressed }) => [styles.event, pressed && { opacity: 0.8 }]}
          >
            <Text style={styles.eventName}>{item.name}</Text>
            {item.general ? <Muted>General check-in</Muted> : null}
          </Pressable>
        ))}
        <QueuePanel />
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

      <View style={styles.bar}>
        <View style={{ flex: 1 }}>
          <Text style={styles.eventName}>{event.name}</Text>
          <Muted>
            {counts.checkedIn} checked in here
            {counts.saved ? ` · ${counts.saved} saved to sync` : ""}
            {pending.length ? ` · ${pending.length} waiting` : ""}
          </Muted>
        </View>
        <Button
          title="Change"
          onPress={() => {
            stop();
            setChosen(null);
          }}
        />
      </View>

      {!online ? (
        <Notice tone="warn">
          No connection. Taps are saved and synced later. Names cannot be shown, and a badge already used on another
          phone cannot be caught until then.
        </Notice>
      ) : null}

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

      <QueuePanel />
      {view ? null : <Muted>Each tap is checked with the server. Without a connection it is saved on this phone.</Muted>}
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
