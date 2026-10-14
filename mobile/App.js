import { StatusBar } from "expo-status-bar";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { ActivityIndicator, Animated, Easing, StyleSheet, View } from "react-native";
import { initialWindowMetrics, SafeAreaProvider, useSafeAreaInsets } from "react-native-safe-area-context";

import { api, isReachable, onConnectionChange } from "./src/api/client";
import { ResultPanel } from "./src/components/Result";
import { Icon, PrimaryButton, T, TextButton } from "./src/components/ui";
import { loadLastJob, saveLastJob } from "./src/lib/session";
import Arrivals from "./src/screens/Arrivals";
import CheckBadge from "./src/screens/CheckBadge";
import EventScreen from "./src/screens/EventScreen";
import Home from "./src/screens/Home";
import WhosHere from "./src/screens/WhosHere";
import SignIn, { Brand, NightSky } from "./src/screens/SignIn";
import { AuthProvider, canCheckIn, canLookUp, useAuth } from "./src/state/Auth";
import { useEvents } from "./src/state/events";
import { colors, space } from "./src/theme";

// Up from the first request that fails to reach the server until one gets through,
// then green for a moment so the recovery is seen.
function ConnectionBanner() {
  const reachable = useSyncExternalStore(onConnectionChange, isReachable);
  const [state, setState] = useState(reachable ? "hidden" : "down");
  const [slide] = useState(() => new Animated.Value(0));
  const wasReachable = useRef(reachable);

  useEffect(() => {
    if (reachable === wasReachable.current) return undefined;
    wasReachable.current = reachable;
    setState(reachable ? "back" : "down");
    if (!reachable) return undefined;
    const timer = setTimeout(() => setState("hidden"), 1500);
    return () => clearTimeout(timer);
  }, [reachable]);

  useEffect(() => {
    if (state !== "down") return;
    slide.setValue(0);
    Animated.timing(slide, { toValue: 1, duration: 200, easing: Easing.out(Easing.cubic), useNativeDriver: true }).start();
  }, [slide, state]);

  if (state === "hidden") return null;
  const down = state === "down";
  return (
    <View style={styles.bannerClip}>
      <Animated.View
        accessibilityRole="alert"
        style={[
          styles.banner,
          down ? styles.bannerDown : styles.bannerBack,
          down && { transform: [{ translateY: slide.interpolate({ inputRange: [0, 1], outputRange: [-40, 0] }) }] },
        ]}
      >
        <Icon name={down ? "wifi-off" : "wifi"} size={20} color={down ? colors.white : colors.night} />
        <T v="secondary" color={down ? colors.white : colors.night}>
          {down ? "No connection. Nothing is being saved." : "Back online"}
        </T>
      </Animated.View>
    </View>
  );
}

// Which job the phone is doing: the home screen, Arrivals, one event or Check a
// badge. The last one is remembered, so the app reopens straight into it.
function Jobs() {
  const insets = useSafeAreaInsets();
  const { admin } = useAuth();
  const staff = canCheckIn(admin);
  const events = useEvents(staff ? api.events : null);
  const [job, setJob] = useState(null);

  useEffect(() => {
    let live = true;
    loadLastJob().then((saved) => live && setJob(saved || { kind: "home" }));
    return () => {
      live = false;
    };
  }, []);

  const go = useCallback((next) => {
    setJob(next);
    saveLastJob(next.kind === "home" ? null : next);
  }, []);
  const home = useCallback(() => go({ kind: "home" }), [go]);

  let current = job;
  if (!staff && current && ["arrivals", "event"].includes(current.kind)) current = { kind: "home" };
  const event = current?.kind === "event" ? (events.events || []).find((item) => item.id === current.eventId) : null;
  if (current?.kind === "event" && events.events && !event) current = { kind: "home" };
  if (!canLookUp(admin)) current = { kind: "none" };

  let screen;
  if (!current || (current.kind === "event" && !event)) {
    screen = <ActivityIndicator color={colors.peach} style={styles.loading} />;
  } else if (current.kind === "none") {
    screen = (
      <View style={styles.padded}>
        <ResultPanel
          view={{ family: "stop", icon: "account-off-outline", word: "No access", detail: "This account cannot use the staff app. Ask an organizer." }}
        />
      </View>
    );
  } else if (current.kind === "arrivals" && staff) {
    screen = <Arrivals onBack={home} />;
  } else if (current.kind === "event" && staff) {
    screen = (
      <EventScreen
        key={event.id}
        event={event}
        version={events.version}
        onBack={home}
        onGone={() => {
          events.reload();
          home();
        }}
      />
    );
  } else if (current.kind === "who") {
    screen = <WhosHere onBack={home} />;
  } else if (current.kind === "check") {
    screen = <CheckBadge onBack={home} />;
  } else {
    screen = (
      <Home
        events={events}
        onArrivals={() => go({ kind: "arrivals" })}
        onEvent={(eventId) => go({ kind: "event", eventId })}
        onWho={() => go({ kind: "who" })}
        onCheck={() => go({ kind: "check" })}
      />
    );
  }

  const homeScreen = current?.kind === "home";
  return (
    <View style={[styles.root, { paddingTop: insets.top }, !homeScreen && { paddingBottom: insets.bottom }]}>
      <ConnectionBanner />
      {screen}
    </View>
  );
}

function Unreachable() {
  const insets = useSafeAreaInsets();
  const { retry, signOut } = useAuth();
  return (
    <View style={[styles.padded, { paddingTop: insets.top + space.lg, paddingBottom: insets.bottom + space.lg }]}>
      <ResultPanel
        view={{
          family: "void",
          icon: "wifi-off",
          word: "No connection",
          detail: "The app needs a connection for everything it does. You are still signed in on this phone.",
        }}
      />
      <View style={styles.bottom}>
        <PrimaryButton title="Try again" icon="refresh" onPress={retry} />
        <TextButton title="Sign out" tone="danger" onPress={signOut} />
      </View>
    </View>
  );
}

function Starting() {
  return (
    <View style={styles.starting}>
      <NightSky />
      <Brand />
      <ActivityIndicator size="large" color={colors.peach} />
    </View>
  );
}

function Shell() {
  const { phase } = useAuth();
  return (
    <View style={styles.root}>
      {phase === "loading" ? <Starting /> : phase === "signed-in" ? <Jobs /> : phase === "unreachable" ? <Unreachable /> : <SignIn />}
    </View>
  );
}

export default function App() {
  return (
    <SafeAreaProvider initialMetrics={initialWindowMetrics}>
      <StatusBar style="light" />
      <AuthProvider>
        <Shell />
      </AuthProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.night },
  loading: { flex: 1 },
  padded: { flex: 1, padding: space.gutter, gap: space.lg },
  bottom: { flex: 1, justifyContent: "flex-end", gap: space.xs },
  starting: { flex: 1, alignItems: "center", justifyContent: "center", gap: space.xl },
  bannerClip: { overflow: "hidden" },
  banner: {
    height: 40,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: space.sm,
    paddingHorizontal: space.lg,
  },
  bannerDown: { backgroundColor: colors.stop },
  bannerBack: { backgroundColor: colors.ok },
});
