import { StatusBar } from "expo-status-bar";
import { useState } from "react";
import { ActivityIndicator, Alert, Pressable, StyleSheet, Text, View } from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";

import { pendingLabel } from "./src/components/QueuePanel";
import { Muted, Notice } from "./src/components/ui";
import CheckInDesk from "./src/screens/CheckInDesk";
import EventTaps from "./src/screens/EventTaps";
import Lookup from "./src/screens/Lookup";
import SignIn from "./src/screens/SignIn";
import { AuthProvider, canCheckIn, canLookUp, useAuth } from "./src/state/Auth";
import { QueueProvider, useQueue } from "./src/state/Queue";
import { colors } from "./src/theme";

const AREAS = [
  { key: "desk", label: "Check-in desk", allowed: canCheckIn, Screen: CheckInDesk },
  { key: "taps", label: "Event taps", allowed: canCheckIn, Screen: EventTaps },
  { key: "lookup", label: "Lookup", allowed: canLookUp, Screen: Lookup },
];

function Header({ title }) {
  const { admin, signOut } = useAuth();
  const { pending } = useQueue();

  const confirmSignOut = () =>
    Alert.alert(
      "Sign out?",
      pending.length > 0
        ? `${pendingLabel(pending.length)}. They stay on this phone and sync after the next sign-in.`
        : `You are signed in as ${admin?.name || admin?.email}.`,
      [
        { text: "Cancel", style: "cancel" },
        { text: "Sign out", style: "destructive", onPress: signOut },
      ],
    );

  return (
    <View style={styles.header}>
      <View style={{ flex: 1 }}>
        <Text style={styles.eyebrow}>PEACHHACKS STAFF</Text>
        <Text style={styles.title}>{title}</Text>
      </View>
      <Pressable accessibilityRole="button" onPress={confirmSignOut} hitSlop={10}>
        <Text style={styles.signOut}>Sign out</Text>
      </Pressable>
    </View>
  );
}

function SignedIn() {
  const { admin } = useAuth();
  const { pending } = useQueue();
  const areas = AREAS.filter((area) => area.allowed(admin));
  const [chosen, setChosen] = useState(null);
  const area = areas.find((item) => item.key === chosen) || areas[0];

  if (!area) {
    return (
      <>
        <Header title="No access" />
        <View style={{ padding: 16 }}>
          <Notice tone="bad">This account cannot use the staff app. Ask an organizer.</Notice>
        </View>
      </>
    );
  }

  return (
    <>
      <Header title={area.label} />
      {pending.length > 0 && area.key !== "taps" && canCheckIn(admin) ? (
        <Pressable accessibilityRole="button" onPress={() => setChosen("taps")} style={styles.pending}>
          <Text style={styles.pendingText}>{pendingLabel(pending.length)}</Text>
        </Pressable>
      ) : null}
      <View style={{ flex: 1 }}>
        <area.Screen key={area.key} />
      </View>
      {areas.length > 1 ? (
        <View style={styles.tabs} accessibilityRole="tablist">
          {areas.map((item) => {
            const selected = item.key === area.key;
            return (
              <Pressable
                key={item.key}
                accessibilityRole="tab"
                accessibilityState={{ selected }}
                onPress={() => setChosen(item.key)}
                style={[styles.tab, selected && styles.tabSelected]}
              >
                <Text style={[styles.tabText, selected && styles.tabTextSelected]}>{item.label}</Text>
                {item.key === "taps" && pending.length > 0 ? (
                  <View style={styles.badge}>
                    <Text style={styles.badgeText}>{pending.length}</Text>
                  </View>
                ) : null}
              </Pressable>
            );
          })}
        </View>
      ) : null}
    </>
  );
}

function Shell() {
  const { phase, admin } = useAuth();
  return (
    <QueueProvider active={phase === "signed-in" && canCheckIn(admin)}>
      <SafeAreaView style={styles.root}>
        {phase === "loading" ? (
          <View style={styles.loading}>
            <ActivityIndicator size="large" color={colors.peach} />
            <Muted>Starting…</Muted>
          </View>
        ) : phase === "signed-in" ? (
          <SignedIn />
        ) : (
          <SignIn />
        )}
      </SafeAreaView>
    </QueueProvider>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <AuthProvider>
        <Shell />
      </AuthProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.navy },
  loading: { flex: 1, alignItems: "center", justifyContent: "center", gap: 12 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colors.line,
  },
  eyebrow: { color: colors.peach, fontSize: 11, fontWeight: "800", letterSpacing: 2 },
  title: { color: colors.cream, fontSize: 22, fontWeight: "800" },
  signOut: { color: colors.peach, fontSize: 15, fontWeight: "700", textDecorationLine: "underline" },
  pending: { paddingHorizontal: 16, paddingVertical: 8, backgroundColor: colors.peach },
  pendingText: { color: colors.navy, fontSize: 15, fontWeight: "800" },
  tabs: { flexDirection: "row", borderTopWidth: 1, borderTopColor: colors.line, backgroundColor: colors.deep },
  tab: {
    flex: 1,
    minHeight: 56,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    paddingHorizontal: 4,
    borderTopWidth: 3,
    borderTopColor: "transparent",
  },
  tabSelected: { borderTopColor: colors.peach },
  tabText: { color: colors.mist, fontSize: 14, fontWeight: "700", textAlign: "center" },
  tabTextSelected: { color: colors.cream },
  badge: { minWidth: 22, paddingHorizontal: 6, paddingVertical: 1, borderRadius: 11, backgroundColor: colors.peach },
  badgeText: { color: colors.navy, fontSize: 12, fontWeight: "800", textAlign: "center" },
});
