import { useCallback, useEffect, useRef, useState } from "react";
import { ActivityIndicator, Alert, FlatList, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";

import { api } from "../api/client";
import { NfcUnavailable, useNfcStatus } from "../components/NfcState";
import { TicketScanner } from "../components/TicketScanner";
import { Button, Muted, Notice, ResultCard, Segmented, styles as ui } from "../components/ui";
import { signal } from "../lib/feedback";
import { errorText, formatWhen, fullName, statusLabel, whenAndWho } from "../lib/format";
import { cleanTicketCode } from "../lib/ticket";
import { looksLikeUid } from "../lib/uid";
import { cancelRead, nfcProblemText, readUid } from "../nfc";
import { useOnline } from "../state/Queue";
import { colors } from "../theme";

const DevEntry = __DEV__ ? require("../dev/DevEntry") : null;

const SEARCH_SIZE = 20;
// How long a finished check-in stays up before the desk is ready for the next person.
const DONE_HOLD_MS = 6000;
const NOT_RECOGNISED_HOLD_MS = 4000;
// A ticket still in frame after its result clears is ignored for this long.
const SAME_CODE_QUIET_MS = 6000;

const NOT_ACCEPTED_TEXT = "They have not been checked in, and can’t be until an organizer accepts them.";

function Lanyard({ lanyard, textColor }) {
  if (!lanyard) return null;
  return (
    <View style={[styles.lanyard, { borderColor: textColor }]}>
      <Text style={[styles.lanyardEyebrow, { color: textColor }]}>LANYARD TO HAND OVER</Text>
      {lanyard.color ? <Text style={[styles.lanyardBig, { color: textColor }]}>{lanyard.color}</Text> : null}
      <Text style={[lanyard.color ? styles.lanyardLabel : styles.lanyardBig, { color: textColor }]}>{lanyard.label}</Text>
    </View>
  );
}

function SearchByName({ onPick }) {
  const [text, setText] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [answer, setAnswer] = useState({ key: null, items: null, error: null });
  const q = text.trim();
  const key = `${attempt}:${q}`;

  useEffect(() => {
    if (!q) return undefined;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      try {
        const page = await api.checkInList({ q, page: 0, size: SEARCH_SIZE }, controller.signal);
        setAnswer({ key, items: page?.items || [], error: null });
      } catch (error) {
        if (!controller.signal.aborted) setAnswer({ key, items: null, error });
      }
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [q, key]);

  const state = q && answer.key === key ? answer : { items: null, error: null };
  const loading = Boolean(q) && answer.key !== key;

  return (
    <View style={{ flex: 1, gap: 10 }}>
      <TextInput
        style={ui.input}
        value={text}
        onChangeText={setText}
        placeholder="Search by name or email"
        placeholderTextColor={colors.mist}
        autoCapitalize="none"
        autoCorrect={false}
        autoFocus
        returnKeyType="search"
        accessibilityLabel="Search by name or email"
      />
      {loading ? <ActivityIndicator color={colors.peach} /> : null}
      {state.error ? (
        <View style={{ gap: 8 }}>
          <Notice tone="bad">Could not load the list: {errorText(state.error)}</Notice>
          <Button title="Try again" onPress={() => setAttempt((n) => n + 1)} />
        </View>
      ) : null}
      {state.items && state.items.length === 0 ? (
        <Muted>No one matches. Check the spelling, try their email, or ask an organizer. They may not be registered.</Muted>
      ) : null}
      <FlatList
        data={state.items || []}
        keyExtractor={(item) => item.id}
        keyboardShouldPersistTaps="handled"
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            onPress={() => onPick(item)}
            style={({ pressed }) => [styles.person, pressed && { opacity: 0.8 }]}
          >
            <Text style={styles.personName}>{fullName(item)}</Text>
            <Muted>{item.school}</Muted>
            <Muted>{item.email}</Muted>
            {item.status !== "ACCEPTED" ? <Text style={styles.personFlag}>Not accepted ({statusLabel(item.status)})</Text> : null}
            {item.checkedInAt ? <Text style={styles.personDone}>Checked in {formatWhen(item.checkedInAt)}</Text> : null}
          </Pressable>
        )}
      />
    </View>
  );
}

export default function CheckInDesk() {
  const online = useOnline();
  const [nfc] = useNfcStatus();
  const [mode, setMode] = useState("scan");
  const [step, setStep] = useState({ name: "find" });
  const live = useRef(true);
  const busy = useRef(false);
  const lastCode = useRef({ code: null, at: 0 });
  const holdTimer = useRef(null);

  useEffect(() => {
    live.current = true;
    return () => {
      live.current = false;
      clearTimeout(holdTimer.current);
      cancelRead();
    };
  }, []);

  const show = useCallback((next, holdMs) => {
    if (!live.current) return;
    clearTimeout(holdTimer.current);
    setStep(next);
    if (holdMs) holdTimer.current = setTimeout(() => live.current && setStep({ name: "find" }), holdMs);
  }, []);

  const next = useCallback(() => {
    cancelRead();
    lastCode.current = { ...lastCode.current, at: Date.now() };
    show({ name: "find" });
  }, [show]);

  const resolve = useCallback(
    async (body) => {
      if (busy.current) return;
      busy.current = true;
      show({ name: "busy", label: "Finding them…" });
      try {
        const resolved = await api.resolveBadge(body);
        if (resolved?.result === "FOUND") {
          show({ name: "person", resolved });
        } else if (resolved?.result === "NOT_ACCEPTED") {
          signal("problem");
          show({ name: "dead", kind: "NOT_ACCEPTED", item: resolved.item });
        } else {
          signal("problem");
          show({ name: "dead", kind: "NOT_RECOGNISED" }, NOT_RECOGNISED_HOLD_MS);
        }
      } catch (error) {
        signal("problem");
        show({ name: "dead", kind: "ERROR", error, body });
      } finally {
        busy.current = false;
      }
    },
    [show],
  );

  const onTicket = useCallback(
    (raw) => {
      const code = cleanTicketCode(raw);
      if (busy.current) return;
      if (!code) {
        signal("problem");
        show({ name: "dead", kind: "NOT_RECOGNISED" }, NOT_RECOGNISED_HOLD_MS);
        return;
      }
      if (code === lastCode.current.code && Date.now() - lastCode.current.at < SAME_CODE_QUIET_MS) return;
      lastCode.current = { code, at: Date.now() };
      resolve({ code });
    },
    [resolve, show],
  );

  const bind = useCallback(
    async (resolved, uid, replace) => {
      show({ name: "busy", label: "Saving…" });
      try {
        const bound = await api.bindBadge({ registrationId: resolved.item.id, uid, replace });
        signal("success");
        show({ name: "done", how: bound.result, item: bound.item, lanyard: bound.lanyard }, DONE_HOLD_MS);
      } catch (error) {
        if (error?.code === "HAS_BADGE") {
          signal("warning");
          show({ name: "person", resolved, replace: { uid, text: error.message } });
          return;
        }
        signal("problem");
        if (error?.code === "NOT_ACCEPTED") {
          show({ name: "dead", kind: "NOT_ACCEPTED", item: resolved.item });
        } else if (error?.code === "BADGE_IN_USE") {
          show({ name: "person", resolved, problem: { text: error.message, tapLabel: "Try another badge" } });
        } else if (error?.status === 400) {
          show({
            name: "person",
            resolved,
            problem: { text: "That card is not a PeachHacks badge.", tapLabel: "Try another badge" },
          });
        } else {
          show({
            name: "person",
            resolved,
            problem: { text: `Could not save: ${errorText(error)}`, retry: { uid, replace } },
          });
        }
      }
    },
    [show],
  );

  const onUid = useCallback(
    (resolved, uid) => {
      if (!looksLikeUid(uid)) {
        signal("problem");
        show({
          name: "person",
          resolved,
          problem: { text: "That card is not a PeachHacks badge.", tapLabel: "Try another badge" },
        });
        return;
      }
      bind(resolved, uid, false);
    },
    [bind, show],
  );

  const tapBadge = async (resolved) => {
    show({ name: "person", resolved, reading: true });
    try {
      const uid = await readUid({ message: `Hold the badge for ${fullName(resolved.item)} to the top of the phone.` });
      if (live.current) onUid(resolved, uid);
    } catch (error) {
      if (error.kind === "cancelled") show({ name: "person", resolved });
      else show({ name: "person", resolved, problem: { text: nfcProblemText(error.kind) } });
    }
  };

  const checkInWithoutBadge = (resolved) => {
    Alert.alert(
      "Check in without a badge?",
      `${fullName(resolved.item)} will be checked in with no badge. Use this only when badges cannot be read.`,
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Check in",
          onPress: async () => {
            show({ name: "busy", label: "Checking in…" });
            try {
              const item = await api.checkIn(resolved.item.id);
              signal("success");
              show({ name: "done", how: "NO_BADGE", item, lanyard: resolved.lanyard }, DONE_HOLD_MS);
            } catch (error) {
              signal("problem");
              if (error?.code === "NOT_ACCEPTED") show({ name: "dead", kind: "NOT_ACCEPTED", item: resolved.item });
              else show({ name: "person", resolved, problem: { text: `Could not check in: ${errorText(error)}` } });
            }
          },
        },
      ],
    );
  };

  const offline = !online ? (
    <Notice tone="warn">No connection. The check-in desk needs a connection to find people and give out badges.</Notice>
  ) : null;

  if (step.name === "busy") {
    return (
      <View style={[ui.screen, styles.centre]}>
        <ActivityIndicator size="large" color={colors.peach} />
        <Text style={styles.busy}>{step.label}</Text>
      </View>
    );
  }

  if (step.name === "done") {
    const titles = {
      BOUND: "Checked in",
      ALREADY_BOUND: "Checked in",
      REPLACED: "Badge replaced",
      NO_BADGE: "Checked in without a badge",
    };
    const notes = {
      ALREADY_BOUND: "This badge was already theirs.",
      REPLACED: "The old badge no longer works.",
    };
    return (
      <ScrollView contentContainerStyle={ui.screen}>
        <ResultCard
          tone="good"
          title={titles[step.how] || "Checked in"}
          name={fullName(step.item)}
          lines={[step.item?.school, notes[step.how]]}
        >
          <Lanyard lanyard={step.lanyard} textColor={colors.navy} />
        </ResultCard>
        <Button title="Next person" variant="primary" big onPress={next} />
      </ScrollView>
    );
  }

  if (step.name === "dead") {
    let card = (
      <ResultCard
        tone="bad"
        title="Ticket not recognised"
        lines={["This is not a PeachHacks ticket, or it is no longer valid. Try searching for their name."]}
      />
    );
    if (step.kind === "NOT_ACCEPTED") {
      card = (
        <ResultCard
          tone="bad"
          title="Not accepted"
          name={fullName(step.item)}
          lines={[
            [step.item?.school, step.item?.status && `status: ${statusLabel(step.item.status)}`].filter(Boolean).join(" · "),
            NOT_ACCEPTED_TEXT,
          ]}
        />
      );
    } else if (step.kind === "ERROR") {
      card = <ResultCard tone="bad" title="Could not find them" lines={[`${errorText(step.error)} Nothing was recorded.`]} />;
    }
    return (
      <ScrollView contentContainerStyle={ui.screen}>
        {offline}
        {card}
        {step.body ? <Button title="Try again" variant="primary" big onPress={() => resolve(step.body)} /> : null}
        <Button title="Next person" variant={step.body ? "default" : "primary"} big onPress={next} />
        {step.kind === "NOT_RECOGNISED" ? (
          <Button
            title="Search by name"
            onPress={() => {
              setMode("search");
              next();
            }}
          />
        ) : null}
      </ScrollView>
    );
  }

  if (step.name === "person") {
    const { resolved, problem, reading, replace } = step;
    const { item, badge, lanyard } = resolved;
    const waitingForCard = reading && Platform.OS === "android";
    return (
      <ScrollView contentContainerStyle={ui.screen}>
        {offline}
        <View style={styles.personCard}>
          <Text style={styles.bigName}>{fullName(item)}</Text>
          <Text style={styles.bigSchool}>{item.school}</Text>
          <Text style={styles.idPrompt}>Check their photo ID against this name.</Text>
          {item.checkedInAt ? <Muted>Already checked in {whenAndWho(item)}.</Muted> : null}
          {badge ? (
            <Muted>
              Already has a badge, given {formatWhen(badge.boundAt)}
              {badge.boundBy ? ` by ${badge.boundBy}` : ""}. Tapping a different card asks before replacing it.
            </Muted>
          ) : null}
          <Lanyard lanyard={lanyard} textColor={colors.cream} />
        </View>

        {problem ? <Notice tone="bad">{problem.text}</Notice> : null}
        {problem?.retry ? (
          <Button
            title="Try again"
            variant="primary"
            big
            onPress={() => bind(resolved, problem.retry.uid, problem.retry.replace)}
          />
        ) : null}

        {replace ? (
          <View style={{ gap: 10 }}>
            <Notice tone="warn">{replace.text}</Notice>
            <Text style={styles.hold}>Replace lost badge?</Text>
            <Muted>The old badge stops working. Nothing has been recorded yet.</Muted>
            <Button title="Replace lost badge" variant="danger" big onPress={() => bind(resolved, replace.uid, true)} />
            <Button title="Keep the old badge" big onPress={() => show({ name: "person", resolved })} />
          </View>
        ) : waitingForCard ? (
          <View style={{ gap: 10 }}>
            <Text style={styles.hold}>Hold the badge to the back of the phone</Text>
            <Button title="Cancel" onPress={cancelRead} />
          </View>
        ) : nfc === "off" || nfc === "unsupported" ? (
          <NfcUnavailable status={nfc} onDevUid={(uid) => onUid(resolved, uid)} />
        ) : (
          <Button
            title={problem?.tapLabel || "ID checked — tap badge"}
            variant={problem?.retry ? "default" : "primary"}
            big
            busy={reading || nfc === "checking"}
            onPress={() => tapBadge(resolved)}
          />
        )}

        {!waitingForCard && !replace ? (
          <>
            <Muted>Nothing is recorded until a badge is tapped.</Muted>
            <Button title="Cancel, next person" onPress={next} />
            <Button title="Check in without a badge" variant="link" onPress={() => checkInWithoutBadge(resolved)} />
          </>
        ) : null}
      </ScrollView>
    );
  }

  return (
    <View style={[ui.screen, { flex: 1 }]}>
      {offline}
      <Segmented
        value={mode}
        onChange={setMode}
        options={[
          { value: "scan", label: "Scan ticket" },
          { value: "search", label: "Search by name" },
        ]}
      />
      {mode === "scan" ? (
        <>
          <TicketScanner paused={false} onRead={onTicket} onUseSearch={() => setMode("search")} />
          {DevEntry ? <DevEntry.CodeField onSubmit={onTicket} /> : null}
          <Muted style={{ textAlign: "center" }}>Ready for the next ticket.</Muted>
        </>
      ) : (
        <SearchByName onPick={(item) => resolve({ registrationId: item.id })} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  centre: { flex: 1, alignItems: "center", justifyContent: "center" },
  busy: { color: colors.cream, fontSize: 20, fontWeight: "700" },
  personCard: { padding: 18, gap: 8, borderRadius: 6, backgroundColor: colors.panel },
  bigName: { color: colors.cream, fontSize: 36, fontWeight: "800", lineHeight: 42 },
  bigSchool: { color: colors.mist, fontSize: 22, fontWeight: "700", lineHeight: 28 },
  idPrompt: { color: colors.peach, fontSize: 18, fontWeight: "800", marginTop: 6 },
  hold: { color: colors.cream, fontSize: 24, fontWeight: "800", textAlign: "center" },
  lanyard: { marginTop: 10, padding: 12, borderWidth: 2, borderRadius: 6, gap: 2 },
  lanyardEyebrow: { fontSize: 12, fontWeight: "800", letterSpacing: 1.5 },
  lanyardBig: { fontSize: 30, fontWeight: "800", lineHeight: 36 },
  lanyardLabel: { fontSize: 18, fontWeight: "700" },
  person: { paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: colors.line, gap: 2 },
  personName: { color: colors.cream, fontSize: 19, fontWeight: "800" },
  personFlag: { color: colors.bad, fontSize: 14, fontWeight: "700" },
  personDone: { color: colors.good, fontSize: 14, fontWeight: "700" },
});
