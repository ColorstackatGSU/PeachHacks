import { useCallback, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, FlatList, Modal, Pressable, RefreshControl, ScrollView, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { api } from "../api/client";
import { useEvents } from "../state/events";
import { JobHeader } from "../components/Header";
import { Bar, Field, Name, T, TextButton } from "../components/ui";
import { ARRIVED, arrivedLine, attendanceScopes, emptyText, mergePage, personEvents, rowTime, scopeTotal } from "../lib/attendance";
import { errorText, fullName } from "../lib/format";
import { colors, radii, space } from "../theme";

const PAGE_SIZE = 30;

function PersonSheet({ person, onClose }) {
  const insets = useSafeAreaInsets();
  const events = personEvents(person);
  return (
    <Modal visible={Boolean(person)} transparent animationType="slide" onRequestClose={onClose} statusBarTranslucent navigationBarTranslucent>
      <Pressable accessibilityLabel="Close" style={styles.scrim} onPress={onClose} />
      {person ? (
        <View style={[styles.sheet, { paddingBottom: insets.bottom + space.md }]}>
          <View style={styles.grip} />
          <Name name={fullName(person)} staff={Boolean(person.staff)} v="heading" />
          {person.school ? (
            <T v="secondary" color={colors.textMuted}>
              {person.school}
            </T>
          ) : null}
          {arrivedLine(person) ? (
            <T v="row" style={styles.arrived}>
              {arrivedLine(person)}
            </T>
          ) : null}
          {events.length > 0 ? (
            <View style={styles.sheetEvents}>
              {events.map((event) => (
                <View key={event.key} style={styles.sheetRow}>
                  <T v="body" numberOfLines={1} style={styles.flex}>
                    {event.name}
                  </T>
                  <T v="secondary" color={colors.textMuted} style={styles.numbers}>
                    {event.time}
                  </T>
                </View>
              ))}
            </View>
          ) : person.arrivedAt ? (
            <T v="secondary" color={colors.textMuted} style={styles.arrived}>
              Not checked in to any event yet.
            </T>
          ) : null}
          <TextButton title="Close" onPress={onClose} />
        </View>
      ) : null}
    </Modal>
  );
}

// Read only: who has arrived, and who is at each event. Nothing here changes anything.
export default function WhosHere({ onBack }) {
  const events = useEvents(api.attendanceEvents);
  const scopes = useMemo(() => attendanceScopes(events.events, events.loadedAt), [events.events, events.loadedAt]);
  const [scopeKey, setScopeKey] = useState(ARRIVED);
  const scope = scopes.find((item) => item.key === scopeKey) || scopes[0];
  const [text, setText] = useState("");
  const [q, setQ] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [list, setList] = useState({ key: null, items: [], total: null, page: 0, error: null, more: false });
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [person, setPerson] = useState(null);

  useEffect(() => {
    const timer = setTimeout(() => setQ(text.trim()), 300);
    return () => clearTimeout(timer);
  }, [text]);

  const key = `${scope.key}|${q}|${attempt}`;

  const fetchPage = useCallback(
    (page, signal) => {
      const query = { q, page, size: PAGE_SIZE };
      return scope.key === ARRIVED ? api.attendance(query, signal) : api.eventAttendance(scope.key, query, signal);
    },
    [q, scope.key],
  );

  useEffect(() => {
    const controller = new AbortController();
    fetchPage(0, controller.signal).then(
      (answer) => {
        const items = answer?.items || [];
        const total = typeof answer?.total === "number" ? answer.total : items.length;
        setList({ key, items, total, page: 0, error: null, more: items.length < total });
        setRefreshing(false);
      },
      (error) => {
        if (controller.signal.aborted || error?.status === 401) return;
        setList({ key, items: [], total: null, page: 0, error, more: false });
        setRefreshing(false);
      },
    );
    return () => controller.abort();
  }, [fetchPage, key]);

  const loadMore = async () => {
    if (loadingMore || !list.more || list.key !== key) return;
    setLoadingMore(true);
    try {
      const answer = await fetchPage(list.page + 1);
      setList((prev) => {
        if (prev.key !== key) return prev;
        const items = mergePage(prev.items, answer?.items);
        return { ...prev, items, page: prev.page + 1, more: (answer?.items || []).length > 0 && items.length < prev.total };
      });
    } catch {
      // The next scroll to the end tries again.
    } finally {
      setLoadingMore(false);
    }
  };

  const ready = list.key === key;

  return (
    <View style={styles.root}>
      <JobHeader title="Who's here" onBack={onBack} />
      <View style={styles.top}>
        <Field
          value={text}
          onChangeText={setText}
          placeholder="Search by name"
          autoCapitalize="words"
          autoCorrect={false}
          returnKeyType="search"
          accessibilityLabel="Search by name"
        />
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.chipRow}
          contentContainerStyle={styles.chips}
        >
          {scopes.map((item) => {
            const selected = item.key === scope.key;
            return (
              <Pressable
                key={item.key}
                accessibilityRole="button"
                accessibilityState={{ selected }}
                onPress={() => setScopeKey(item.key)}
                style={[styles.chip, selected && styles.chipOn]}
              >
                {item.now ? <View style={[styles.dot, selected && styles.dotOn]} /> : null}
                <T v="caption" color={selected ? colors.night : colors.text} numberOfLines={1}>
                  {item.label}
                </T>
              </Pressable>
            );
          })}
        </ScrollView>
        <T v="secondary" color={colors.textMuted} style={styles.numbers}>
          {ready && list.total ? scopeTotal(scope, list.total) : " "}
        </T>
      </View>

      {ready && list.error ? (
        <View style={styles.message}>
          <Bar>Could not load the list: {errorText(list.error)}</Bar>
          <TextButton title="Try again" onPress={() => setAttempt((n) => n + 1)} />
        </View>
      ) : null}

      <FlatList
        style={styles.flex}
        contentContainerStyle={styles.list}
        data={ready ? list.items : []}
        keyExtractor={(item) => item.id}
        keyboardShouldPersistTaps="handled"
        onEndReached={loadMore}
        onEndReachedThreshold={0.4}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              setRefreshing(true);
              setAttempt((n) => n + 1);
            }}
            tintColor={colors.peach}
            colors={[colors.peach]}
            progressBackgroundColor={colors.raised}
          />
        }
        ListEmptyComponent={
          !ready ? (
            <ActivityIndicator color={colors.peach} style={styles.loading} />
          ) : list.error ? null : (
            <T v="body" color={colors.textMuted} center style={styles.empty}>
              {emptyText(scope, q)}
            </T>
          )
        }
        ListFooterComponent={loadingMore ? <ActivityIndicator color={colors.peach} style={styles.loading} /> : null}
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            onPress={() => setPerson(scope.key === ARRIVED ? item : { ...item, events: [{ eventId: scope.key, name: scope.label, checkedInAt: item.checkedInAt }] })}
            style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
          >
            <View style={styles.flex}>
              <Name name={fullName(item)} staff={Boolean(item.staff)} v="row" numberOfLines={1} />
              {item.school ? (
                <T v="secondary" color={colors.textMuted} numberOfLines={1}>
                  {item.school}
                </T>
              ) : null}
            </View>
            <T v="secondary" color={colors.textMuted} style={styles.numbers}>
              {rowTime(item)}
            </T>
          </Pressable>
        )}
      />
      <PersonSheet person={person} onClose={() => setPerson(null)} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  flex: { flex: 1 },
  top: { paddingHorizontal: space.gutter, gap: space.md, paddingBottom: space.sm },
  chipRow: { marginHorizontal: -space.gutter },
  chips: { gap: space.sm, paddingHorizontal: space.gutter },
  chip: {
    height: 36,
    maxWidth: 240,
    paddingHorizontal: space.md,
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    flexDirection: "row",
    alignItems: "center",
    gap: space.xs,
  },
  chipOn: { backgroundColor: colors.text, borderColor: colors.text },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.peach },
  dotOn: { backgroundColor: colors.night },
  numbers: { fontVariant: ["tabular-nums"] },
  message: { paddingHorizontal: space.gutter, gap: space.xs },
  list: { paddingHorizontal: space.gutter, paddingBottom: space.xl },
  loading: { marginVertical: space.xl },
  empty: { marginTop: space.xxl },
  row: {
    minHeight: 64,
    flexDirection: "row",
    alignItems: "center",
    gap: space.md,
    paddingVertical: space.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.lineStrong,
  },
  rowPressed: { backgroundColor: colors.raised },
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
  arrived: { marginTop: space.md },
  sheetEvents: { marginTop: space.sm, marginBottom: space.sm },
  sheetRow: {
    minHeight: 44,
    flexDirection: "row",
    alignItems: "center",
    gap: space.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.lineStrong,
  },
});
