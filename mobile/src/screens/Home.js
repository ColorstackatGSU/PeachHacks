import { useState } from "react";
import { ActivityIndicator, Modal, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { API_BASE } from "../api/client";
import { Bar, Icon, Row, T, TextButton } from "../components/ui";
import { errorText } from "../lib/format";
import { eventTime, homeEvents, initials } from "../lib/results";
import { canCheckIn, useAuth } from "../state/Auth";
import { colors, radii, space, type } from "../theme";

const ROLE_NAMES = { ADMIN: "Organizer", VOLUNTEER: "Volunteer", LOOKUP: "Badge checks" };

const count = (event) => (typeof event.checkedIn === "number" ? `${event.checkedIn} in` : null);

export function AccountSheet({ visible, onClose }) {
  const insets = useSafeAreaInsets();
  const { admin, signOut } = useAuth();
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose} statusBarTranslucent navigationBarTranslucent>
      <Pressable accessibilityLabel="Close" style={styles.scrim} onPress={onClose} />
      <View style={[styles.sheet, { paddingBottom: insets.bottom + space.lg }]}>
        <View style={styles.grip} />
        <T v="heading">{admin?.name || admin?.email}</T>
        <T v="secondary" color={colors.textMuted}>
          {[ROLE_NAMES[admin?.role] || admin?.role, admin?.email].filter(Boolean).join(" · ")}
        </T>
        {__DEV__ ? (
          <T v="caption" color={colors.textMuted}>
            Development build · {API_BASE}
          </T>
        ) : null}
        <View style={styles.sheetActions}>
          <TextButton title="Sign out" icon="logout" tone="danger" align="start" onPress={signOut} />
        </View>
      </View>
    </Modal>
  );
}

// The event to press, impossible to miss without shouting: a raised card with a
// peach "Happening now", or a calmer "Up next".
function FeaturedCard({ event, kind, half, onPress }) {
  const now = kind === "now";
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${now ? "Happening now" : "Up next"}: ${event.name}`}
      onPress={onPress}
      style={({ pressed }) => [styles.featured, half ? styles.featuredHalf : styles.featuredFull, pressed && styles.pressed]}
    >
      <View style={styles.featuredLabel}>
        {now ? <View style={styles.dot} /> : <Icon name="clock-outline" size={16} />}
        <T v="caption" color={now ? colors.peach : colors.textMuted}>
          {now ? "Happening now" : "Up next"}
        </T>
      </View>
      <T v={half ? "heading" : "title"} numberOfLines={3}>
        {event.name}
      </T>
      <T v="secondary" color={colors.textMuted}>
        {eventTime(event)}
      </T>
      <View style={styles.featuredFoot}>
        <T v="heading" style={styles.numbers}>
          {count(event) || ""}
        </T>
        <Icon name="chevron-right" size={26} color={colors.text} />
      </View>
    </Pressable>
  );
}

export default function Home({ events, onArrivals, onEvent, onCheck }) {
  const insets = useSafeAreaInsets();
  const { admin } = useAuth();
  const [sheet, setSheet] = useState(false);
  const staff = canCheckIn(admin);
  const { featured, others } = homeEvents(events.events, events.loadedAt);

  return (
    <View style={styles.root}>
      <View style={styles.top}>
        <Text maxFontSizeMultiplier={1.1} style={styles.wordmark}>
          PeachHacks
        </Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Account"
          onPress={() => setSheet(true)}
          hitSlop={10}
          style={({ pressed }) => [styles.avatar, pressed && styles.pressed]}
        >
          <Text maxFontSizeMultiplier={1.1} style={styles.avatarText}>
            {initials(admin?.name || admin?.email)}
          </Text>
        </Pressable>
      </View>

      <ScrollView
        contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + space.xl }]}
        refreshControl={
          staff ? (
            <RefreshControl
              refreshing={Boolean(events.events) && events.loading}
              onRefresh={events.reload}
              tintColor={colors.peach}
              colors={[colors.peach]}
              progressBackgroundColor={colors.raised}
            />
          ) : undefined
        }
      >
        <T v="title">What are you doing?</T>

        {staff ? (
          <Row
            icon="card-account-details-outline"
            title="Arrivals"
            subtitle="Scan ticket, check ID, give badge"
            onPress={onArrivals}
          />
        ) : null}

        {staff ? (
          <View style={styles.section}>
            {events.error ? <Bar>Could not load the events: {errorText(events.error)}</Bar> : null}
            {!events.events && !events.error ? <ActivityIndicator color={colors.peach} style={styles.loading} /> : null}
            {featured.events.length > 0 ? (
              <View style={styles.featuredRow}>
                {featured.events.map((event) => (
                  <FeaturedCard
                    key={event.id}
                    event={event}
                    kind={featured.kind}
                    half={featured.events.length > 1}
                    onPress={() => onEvent(event.id)}
                  />
                ))}
              </View>
            ) : null}
            {others.length > 0 ? (
              <>
                <T v="caption" color={colors.textMuted} style={styles.sectionTitle}>
                  {featured.events.length > 0 ? "Other events" : "Events"}
                </T>
                {others.map((event) => (
                  <Row
                    key={event.id}
                    title={event.name}
                    subtitle={eventTime(event)}
                    right={
                      count(event) ? (
                        <T v="secondary" color={colors.textMuted} style={styles.numbers}>
                          {count(event)}
                        </T>
                      ) : null
                    }
                    onPress={() => onEvent(event.id)}
                  />
                ))}
              </>
            ) : null}
          </View>
        ) : null}

        <Row icon="account-search-outline" title="Check a badge" subtitle="See whose badge it is" onPress={onCheck} />
      </ScrollView>

      <AccountSheet visible={sheet} onClose={() => setSheet(false)} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  top: {
    minHeight: 56,
    paddingHorizontal: space.gutter,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  wordmark: { ...type.wordmark, color: colors.peach },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: colors.raised,
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { ...type.caption, color: colors.text, includeFontPadding: false },
  pressed: { opacity: 0.7 },
  content: { paddingHorizontal: space.gutter, paddingTop: space.sm, gap: space.md },
  section: { gap: space.md, marginTop: space.sm },
  sectionTitle: { marginTop: space.sm },
  loading: { marginVertical: space.lg },
  featuredRow: { flexDirection: "row", gap: space.md },
  featured: { flex: 1, borderRadius: radii.panel, backgroundColor: colors.raised, padding: space.gutter, gap: space.xs },
  featuredFull: { height: 210 },
  featuredHalf: { height: 220 },
  featuredLabel: { flexDirection: "row", alignItems: "center", gap: space.sm, marginBottom: space.xs },
  dot: { width: 10, height: 10, borderRadius: 5, backgroundColor: colors.peach },
  featuredFoot: { flex: 1, flexDirection: "row", alignItems: "flex-end", justifyContent: "space-between" },
  numbers: { fontVariant: ["tabular-nums"] },
  scrim: { flex: 1, backgroundColor: colors.scrim },
  sheet: {
    backgroundColor: colors.raised,
    borderTopLeftRadius: radii.panel,
    borderTopRightRadius: radii.panel,
    paddingHorizontal: space.xl,
    paddingTop: space.md,
    gap: space.xs,
  },
  grip: { alignSelf: "center", width: 40, height: 4, borderRadius: 2, backgroundColor: colors.lineStrong, marginBottom: space.md },
  sheetActions: { marginTop: space.lg },
});
