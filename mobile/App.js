import { StatusBar } from "expo-status-bar";
import { useState } from "react";
import { ActivityIndicator, Alert, Pressable, StyleSheet, Text, View } from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";

import { Button, Muted, Notice } from "./src/components/ui";
import CheckInDesk from "./src/screens/CheckInDesk";
import EventTaps from "./src/screens/EventTaps";
import Lookup from "./src/screens/Lookup";
import SignIn from "./src/screens/SignIn";
import { AuthProvider, canCheckIn, canLookUp, useAuth } from "./src/state/Auth";
import { colors } from "./src/theme";

const AREAS = [
  { key: "desk", label: "Check-in desk", allowed: canCheckIn, Screen: CheckInDesk },
  { key: "taps", label: "Event taps", allowed: canCheckIn, Screen: EventTaps },
  { key: "lookup", label: "Lookup", allowed: canLookUp, Screen: Lookup },
];

function Header({ title }) {
  const { admin, signOut } = useAuth();

  const confirmSignOut = () =>
    Alert.alert("Sign out?", `You are signed in as ${admin?.name || admin?.email}.`, [
      { text: "Cancel", style: "cancel" },
      { text: "Sign out", style: "destructive", onPress: signOut },
    ]);

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
              </Pressable>
            );
          })}
        </View>
      ) : null}
    </>
  );
}

function Unreachable() {
  const { notice, retry, signOut } = useAuth();
  return (
    <View style={styles.unreachable}>
      <Text style={styles.title}>No connection</Text>
      <Notice tone="bad">{notice}</Notice>
      <Muted>The app needs a connection for everything it does. You are still signed in on this phone.</Muted>
      <Button title="Try again" variant="primary" big onPress={retry} />
      <Button title="Sign out" variant="link" onPress={signOut} />
    </View>
  );
}

function Shell() {
  const { phase } = useAuth();
  return (
    <SafeAreaView style={styles.root}>
      {phase === "loading" ? (
        <View style={styles.loading}>
          <ActivityIndicator size="large" color={colors.peach} />
          <Muted>Starting…</Muted>
        </View>
      ) : phase === "signed-in" ? (
        <SignedIn />
      ) : phase === "unreachable" ? (
        <Unreachable />
      ) : (
        <SignIn />
      )}
    </SafeAreaView>
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
  unreachable: { flex: 1, justifyContent: "center", padding: 16, gap: 14 },
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
  tabs: { flexDirection: "row", borderTopWidth: 1, borderTopColor: colors.line, backgroundColor: colors.deep },
  tab: {
    flex: 1,
    minHeight: 56,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 4,
    borderTopWidth: 3,
    borderTopColor: "transparent",
  },
  tabSelected: { borderTopColor: colors.peach },
  tabText: { color: colors.mist, fontSize: 14, fontWeight: "700", textAlign: "center" },
  tabTextSelected: { color: colors.cream },
});
