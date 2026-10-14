import { useKeepAwake } from "expo-keep-awake";
import { useCallback, useEffect, useRef, useState } from "react";
import { ActivityIndicator, Alert, FlatList, Platform, Pressable, StyleSheet, View } from "react-native";

import { api, isTemporaryFailure } from "../api/client";
import { JobHeader } from "../components/Header";
import { MenuSheet } from "../components/MenuSheet";
import { canRead, DevInput, NfcUnavailable, useNfcStatus } from "../components/NfcState";
import { HOLD_HINT, ResultPanel, WaitingVisual } from "../components/Result";
import { TicketScanner } from "../components/TicketScanner";
import { Bar, Field, IconButton, Name, PrimaryButton, T, TextButton } from "../components/ui";
import { signal } from "../lib/feedback";
import { errorText, fullName } from "../lib/format";
import { lanyardTitle } from "../lib/results";
import { cleanTicketCode } from "../lib/ticket";
import { looksLikeUid } from "../lib/uid";
import { cancelRead, nfcProblemText, readUid } from "../nfc";
import { useAuth } from "../state/Auth";
import { colors, radii, space } from "../theme";
import { SponsorBadge } from "./SponsorBadge";

const SEARCH_SIZE = 20;
// How long a finished check-in stays up before the scanner is back.
const DONE_HOLD_MS = 3000;
const NOT_RECOGNISED_HOLD_MS = 4000;
// A ticket still in frame after its result clears is ignored for this long.
const SAME_CODE_QUIET_MS = 6000;

const NOT_A_BADGE = { text: "That card is not a PeachHacks badge.", again: true };

const DONE_WORD = {
  BOUND: "Checked in",
  ALREADY_BOUND: "Checked in",
  REPLACED: "Badge replaced",
  NO_BADGE: "Checked in without a badge",
};

function Search({ pendingId, onPick, onBack }) {
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
        if (!controller.signal.aborted && error?.status !== 401) setAnswer({ key, items: null, error });
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
    <View style={styles.body}>
      <TextButton title="Scan instead" icon="qrcode-scan" align="start" onPress={onBack} />
      <View>
        <Field
          value={text}
          onChangeText={setText}
          placeholder="Name"
          autoCapitalize="words"
          autoCorrect={false}
          autoFocus
          returnKeyType="search"
          accessibilityLabel="Search by name"
          style={styles.searchField}
        />
        {loading ? <ActivityIndicator color={colors.peach} style={styles.searchSpinner} /> : null}
      </View>
      {state.error ? (
        <>
          <Bar>Could not search: {errorText(state.error)}</Bar>
          <TextButton title="Try again" onPress={() => setAttempt((n) => n + 1)} />
        </>
      ) : null}
      {state.items && state.items.length === 0 ? (
        <T v="secondary" color={colors.textMuted}>
          No one by that name. Check the spelling. They may not be registered.
        </T>
      ) : null}
      <FlatList
        data={state.items || []}
        keyExtractor={(item) => item.id}
        keyboardShouldPersistTaps="handled"
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            disabled={Boolean(pendingId)}
            onPress={() => onPick(item)}
            style={({ pressed }) => [styles.person, pressed && styles.personPressed]}
          >
            <View style={styles.personMain}>
              <Name name={fullName(item)} staff={Boolean(item.staff)} v="row" numberOfLines={1} />
              <T v="secondary" color={colors.textMuted} numberOfLines={1}>
                {item.school}
              </T>
            </View>
            {pendingId === item.id ? <ActivityIndicator color={colors.peach} /> : null}
          </Pressable>
        )}
      />
    </View>
  );
}

export default function Arrivals({ onBack }) {
  useKeepAwake();
  const [nfc] = useNfcStatus();
  const { admin } = useAuth();
  // The API lets only a full admin issue sponsor badges.
  const mayIssueSponsorBadges = admin?.role === "ADMIN";
  const [mode, setMode] = useState("scan");
  const [step, setStep] = useState({ name: "find" });
  const [pulse, setPulse] = useState(0);
  const [checking, setChecking] = useState(null);
  const [menu, setMenu] = useState(false);
  const [sponsor, setSponsor] = useState(false);
  const live = useRef(true);
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

  const show = useCallback((next, { holdMs = 0, feel } = {}) => {
    if (!live.current) return;
    clearTimeout(holdTimer.current);
    setStep(next);
    setPulse((n) => n + 1);
    if (feel) signal(feel);
    if (holdMs) holdTimer.current = setTimeout(() => live.current && setStep({ name: "find" }), holdMs);
  }, []);

  const next = useCallback(() => {
    cancelRead();
    lastCode.current = { ...lastCode.current, at: Date.now() };
    show({ name: "find" });
  }, [show]);

  const finish = useCallback(
    (how, item, lanyard) => {
      setMode("scan");
      show({ name: "done", how, item, lanyard }, { holdMs: DONE_HOLD_MS, feel: "ok" });
    },
    [show],
  );

  // Finds the person behind a ticket or a search row. Records nothing.
  const resolve = useCallback(
    async (body) => {
      setChecking(body.registrationId || "ticket");
      try {
        const resolved = await api.resolveBadge(body);
        if (resolved?.result === "FOUND") show({ name: "person", resolved });
        else if (resolved?.result === "NOT_ACCEPTED") show({ name: "dead", kind: "NOT_ACCEPTED", item: resolved.item }, { feel: "stop" });
        else show({ name: "dead", kind: "NOT_RECOGNISED" }, { holdMs: NOT_RECOGNISED_HOLD_MS, feel: "stop" });
      } catch (error) {
        if (error?.status !== 401) show({ name: "dead", kind: "ERROR", error, body }, { feel: "void" });
      } finally {
        if (live.current) setChecking(null);
      }
    },
    [show],
  );

  const onTicket = useCallback(
    (raw) => {
      if (checking) return;
      const code = cleanTicketCode(raw);
      if (!code) {
        show({ name: "dead", kind: "NOT_RECOGNISED" }, { holdMs: NOT_RECOGNISED_HOLD_MS, feel: "stop" });
        return;
      }
      if (code === lastCode.current.code && Date.now() - lastCode.current.at < SAME_CODE_QUIET_MS) return;
      lastCode.current = { code, at: Date.now() };
      resolve({ code });
    },
    [checking, resolve, show],
  );

  const bind = useCallback(
    async (resolved, uid, replace) => {
      show({ name: "person", resolved, saving: true });
      try {
        const bound = await api.bindBadge({ registrationId: resolved.item.id, uid, replace });
        finish(bound.result, bound.item, bound.lanyard);
      } catch (error) {
        if (error?.status === 401) return;
        if (error?.code === "HAS_BADGE") {
          show({ name: "person", resolved, replace: { uid } }, { feel: "repeat" });
        } else if (error?.code === "NOT_ACCEPTED") {
          show({ name: "dead", kind: "NOT_ACCEPTED", item: resolved.item }, { feel: "stop" });
        } else if (error?.code === "BADGE_IN_USE") {
          show({ name: "person", resolved, problem: { text: error.message, again: true } }, { feel: "stop" });
        } else if (error?.status === 400) {
          show({ name: "person", resolved, problem: NOT_A_BADGE }, { feel: "stop" });
        } else {
          show(
            { name: "person", resolved, problem: { text: `Not saved: ${errorText(error)}`, retry: { uid, replace } } },
            { feel: isTemporaryFailure(error) ? "void" : "stop" },
          );
        }
      }
    },
    [finish, show],
  );

  const onUid = useCallback(
    (resolved, uid) => {
      if (looksLikeUid(uid)) bind(resolved, uid, false);
      else show({ name: "person", resolved, problem: NOT_A_BADGE }, { feel: "stop" });
    },
    [bind, show],
  );

  const tapBadge = async (resolved) => {
    show({ name: "person", resolved, reading: true });
    if (nfc !== "ready") return;
    try {
      const uid = await readUid({ message: `Hold the badge for ${fullName(resolved.item)} to the top of the phone.` });
      if (live.current) onUid(resolved, uid);
    } catch (error) {
      if (error.kind === "cancelled") show({ name: "person", resolved });
      else show({ name: "person", resolved, problem: { text: nfcProblemText(error.kind), readFailed: true } }, { feel: "stop" });
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
            show({ name: "person", resolved, saving: true });
            try {
              finish("NO_BADGE", await api.checkIn(resolved.item.id), resolved.lanyard);
            } catch (error) {
              if (error?.status === 401) return;
              if (error?.code === "NOT_ACCEPTED") show({ name: "dead", kind: "NOT_ACCEPTED", item: resolved.item }, { feel: "stop" });
              else show({ name: "person", resolved, problem: { text: `Not checked in: ${errorText(error)}` } }, { feel: "void" });
            }
          },
        },
      ],
    );
  };

  const onPerson = step.name === "person" && !step.saving && !step.replace;
  const menuItems = [
    mayIssueSponsorBadges ? { key: "sponsor", label: "Issue sponsor badge", icon: "handshake-outline" } : null,
    onPerson ? { key: "no-badge", label: "Check in without a badge", icon: "account-check-outline" } : null,
  ].filter(Boolean);
  const onMenu = (key) => {
    if (key === "sponsor") setSponsor(true);
    else if (key === "no-badge" && step.name === "person") checkInWithoutBadge(step.resolved);
  };

  const header = (
    <JobHeader
      title="Arrivals"
      onBack={() => {
        cancelRead();
        onBack();
      }}
      right={menuItems.length ? <IconButton name="dots-horizontal" label="More" onPress={() => setMenu(true)} /> : null}
    />
  );

  let content;
  if (step.name === "done") {
    const lanyard = lanyardTitle(step.lanyard);
    content = (
      <Pressable accessibilityRole="button" accessibilityLabel="Next person" style={styles.body} onPress={next}>
        <ResultPanel
          view={{
            family: "ok",
            icon: "check-circle-outline",
            word: DONE_WORD[step.how] || DONE_WORD.BOUND,
            title: lanyard || fullName(step.item),
            titleIsName: !lanyard,
            staff: !lanyard && Boolean(step.item?.staff),
          }}
          pulse={pulse}
          drainMs={DONE_HOLD_MS}
        >
          {lanyard ? <Name name={fullName(step.item)} staff={Boolean(step.item?.staff)} v="heading" color={colors.night} /> : null}
        </ResultPanel>
        <View style={styles.bottom}>
          <T v="secondary" color={colors.textMuted} center>
            Back to scanning in a moment. Tap to go now.
          </T>
        </View>
      </Pressable>
    );
  } else if (step.name === "dead") {
    let view;
    let actions;
    if (step.kind === "NOT_ACCEPTED") {
      view = {
        family: "stop",
        icon: "close-circle-outline",
        word: "Not accepted",
        title: fullName(step.item),
        staff: Boolean(step.item?.staff),
        detail: "An organizer has to accept them first",
      };
      actions = <PrimaryButton title="Scan next ticket" icon="qrcode-scan" onPress={next} />;
    } else if (step.kind === "ERROR") {
      view = { family: "void", icon: "wifi-off", word: "Could not look them up", detail: `${errorText(step.error)} Nothing was saved.` };
      actions = (
        <>
          <PrimaryButton title="Try again" icon="refresh" busy={Boolean(checking)} onPress={() => resolve(step.body)} />
          <TextButton title="Back to scanning" onPress={next} />
        </>
      );
    } else {
      view = { family: "stop", icon: "qrcode-remove", word: "Ticket not recognised", detail: "Search for their name instead" };
      actions = (
        <>
          <PrimaryButton
            title="Search by name"
            icon="magnify"
            onPress={() => {
              setMode("search");
              next();
            }}
          />
          <TextButton title="Scan again" onPress={next} />
        </>
      );
    }
    content = (
      <View style={styles.body}>
        <ResultPanel view={view} pulse={pulse} drainMs={step.kind === "NOT_RECOGNISED" ? NOT_RECOGNISED_HOLD_MS : 0} />
        <View style={styles.bottom}>{actions}</View>
      </View>
    );
  } else if (step.name === "person") {
    const { resolved, problem, reading, saving, replace } = step;
    const { item } = resolved;
    const name = fullName(item);
    const staff = Boolean(item.staff);
    const listening = reading && canRead(nfc) && (Platform.OS === "android" || nfc === "simulated");

    if (replace) {
      content = (
        <View style={styles.body}>
          <ResultPanel
            view={{
              family: "repeat",
              icon: "alert-outline",
              word: "Already has a badge",
              title: name,
              staff,
              detail: "Replace it only if the old one is lost. The old badge stops working.",
            }}
            pulse={pulse}
          />
          <View style={styles.bottom}>
            <TextButton title="Replace lost badge" tone="danger" onPress={() => bind(resolved, replace.uid, true)} />
            <TextButton title="Keep the old badge" onPress={() => show({ name: "person", resolved })} />
          </View>
        </View>
      );
    } else {
      let actions;
      if (listening) {
        actions = (
          <>
            <DevInput show={nfc === "simulated"} onSubmit={(uid) => onUid(resolved, uid)} />
            <TextButton title="Cancel" onPress={() => {
                cancelRead();
                show({ name: "person", resolved });
              }} />
          </>
        );
      } else if (problem?.retry) {
        actions = (
          <>
            <PrimaryButton title="Try again" icon="refresh" onPress={() => bind(resolved, problem.retry.uid, problem.retry.replace)} />
            <TextButton title="Tap a different badge" onPress={() => tapBadge(resolved)} />
          </>
        );
      } else if (canRead(nfc) || nfc === "checking") {
        actions = (
          <>
            <PrimaryButton
              title={problem?.again ? "Tap another badge" : "ID matches, tap badge"}
              icon="nfc"
              busy={saving || nfc === "checking" || Boolean(reading)}
              onPress={() => tapBadge(resolved)}
            />
            {problem?.readFailed ? (
              <TextButton title="Check in without a badge" tone="muted" onPress={() => checkInWithoutBadge(resolved)} />
            ) : null}
            <TextButton title="Not them" disabled={saving} onPress={next} />
          </>
        );
      } else {
        actions = (
          <>
            <NfcUnavailable status={nfc} />
            <TextButton title="Check in without a badge" onPress={() => checkInWithoutBadge(resolved)} />
            <TextButton title="Not them" onPress={next} />
          </>
        );
      }
      content = (
        <View style={styles.body}>
          <View style={styles.pass}>
            <T v="caption" color={colors.textMuted}>
              {saving ? "Saving…" : "Check photo ID"}
            </T>
            <Name name={name} staff={staff} />
            <T v="body" color={colors.textMuted}>
              {item.school}
            </T>
          </View>
          {problem ? <Bar>{problem.text}</Bar> : null}
          <View style={styles.hint}>
            {canRead(nfc) ? (
              <>
                <WaitingVisual small={!listening} />
                <T v="secondary" color={listening ? colors.text : colors.textMuted} center>
                  {HOLD_HINT}
                </T>
              </>
            ) : null}
          </View>
          <View style={styles.actions}>{actions}</View>
        </View>
      );
    }
  } else if (mode === "search") {
    content = <Search pendingId={checking} onPick={(item) => resolve({ registrationId: item.id })} onBack={() => setMode("scan")} />;
  } else {
    content = (
      <View style={styles.body}>
        <TicketScanner checking={Boolean(checking)} onRead={onTicket} />
        <DevInput kind="code" onSubmit={onTicket} />
        <TextButton title="Search by name" icon="magnify" onPress={() => setMode("search")} />
      </View>
    );
  }

  return (
    <View style={styles.root}>
      {header}
      {content}
      <MenuSheet visible={menu} items={menuItems} onSelect={onMenu} onClose={() => setMenu(false)} />
      {mayIssueSponsorBadges ? <SponsorBadge visible={sponsor} onClose={() => setSponsor(false)} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  body: { flex: 1, paddingHorizontal: space.gutter, paddingBottom: space.lg, gap: space.md },
  bottom: { flex: 1, justifyContent: "flex-end", gap: space.xs },
  actions: { gap: space.xs },
  hint: { flex: 1, alignItems: "center", justifyContent: "center", gap: space.sm },
  pass: { borderRadius: radii.panel, backgroundColor: colors.raised, padding: space.xl, gap: space.xs },
  searchField: { paddingRight: space.huge },
  searchSpinner: { position: "absolute", right: space.lg, top: 16 },
  person: {
    minHeight: 64,
    flexDirection: "row",
    alignItems: "center",
    gap: space.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.lineStrong,
  },
  personPressed: { backgroundColor: colors.raised },
  personMain: { flex: 1, gap: 2 },
});
