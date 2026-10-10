import { Alert, StyleSheet, Text, View } from "react-native";

import { formatWhen, plural } from "../lib/format";
import { syncSummaryLines } from "../lib/tapQueue";
import { useOnline, useQueue } from "../state/Queue";
import { colors } from "../theme";
import { Button, Muted } from "./ui";

const STATUS_TEXT = {
  syncing: "Syncing now…",
  waiting: "Could not reach the server. Trying again shortly.",
  "signed-out": "Sign in again to sync them.",
  forbidden: "This account is not allowed to record taps. Sign in as an organizer or volunteer to sync them.",
};

export const pendingLabel = (count) => `${plural(count, "tap")} waiting to sync`;

// Everything staff need to know about taps saved while offline: how many are still
// on the phone, what the synced ones turned out to be, and which could not be sent.
export function QueuePanel() {
  const { pending, failed, synced, status, queue } = useQueue();
  const online = useOnline();
  if (pending.length === 0 && failed.length === 0 && synced.length === 0) return null;

  const confirmRemove = () =>
    Alert.alert(
      "Remove these taps?",
      `${plural(failed.length, "tap")} that could not be synced will be removed from this phone. They were never recorded.`,
      [
        { text: "Cancel", style: "cancel" },
        { text: "Remove", style: "destructive", onPress: () => queue.clearFailed() },
      ],
    );

  return (
    <View style={styles.panel}>
      <Text style={styles.title}>Saved taps</Text>

      {pending.length > 0 ? (
        <View style={styles.block}>
          <Text style={styles.strong}>{pendingLabel(pending.length)}</Text>
          <Muted>
            {status === "signed-out" || status === "forbidden" || online
              ? STATUS_TEXT[status] || "They sync on their own."
              : "No connection. They stay on this phone and sync when it is back."}
          </Muted>
          <Button title="Sync now" onPress={() => queue.sync()} busy={status === "syncing"} />
        </View>
      ) : null}

      {synced.length > 0 ? (
        <View style={styles.block}>
          <Text style={styles.strong}>Synced</Text>
          {syncSummaryLines(synced).map((line) => (
            <Text key={line} style={styles.line}>
              {line}
            </Text>
          ))}
          {synced
            .filter((tap) => tap.result !== "CHECKED_IN")
            .slice(0, 10)
            .map((tap) => (
              <Muted key={tap.id}>
                {formatWhen(tap.tappedAt)} · {tap.eventName} · {tap.name || tap.uid}
              </Muted>
            ))}
          <Button title="Clear this list" variant="link" onPress={() => queue.clearSynced()} />
        </View>
      ) : null}

      {failed.length > 0 ? (
        <View style={styles.block}>
          <Text style={[styles.strong, { color: colors.bad }]}>Could not sync {plural(failed.length, "tap")}</Text>
          {failed.slice(0, 10).map((tap) => (
            <Muted key={tap.id}>
              {formatWhen(tap.tappedAt)} · {tap.eventName} · {tap.uid}: {tap.reason}
            </Muted>
          ))}
          <Muted>These were not recorded. Tell an organizer.</Muted>
          <Button title="Try them again" onPress={() => queue.retryFailed()} />
          <Button title="Remove them" variant="link" onPress={confirmRemove} />
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { padding: 14, gap: 12, borderRadius: 6, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.panel },
  title: { color: colors.peach, fontSize: 12, fontWeight: "800", letterSpacing: 1.5, textTransform: "uppercase" },
  block: { gap: 6 },
  strong: { color: colors.cream, fontSize: 17, fontWeight: "800" },
  line: { color: colors.cream, fontSize: 15 },
});
