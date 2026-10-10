import { useSyncExternalStore } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { isReachable, onConnectionChange } from "../api/client";
import { colors, tones } from "../theme";

export function Button({ title, onPress, variant = "default", big = false, disabled = false, busy = false }) {
  const filled = variant === "primary" || variant === "danger";
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: disabled || busy, busy }}
      disabled={disabled || busy}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        big && styles.buttonBig,
        variant === "primary" && styles.buttonPrimary,
        variant === "danger" && styles.buttonDanger,
        variant === "link" && styles.buttonLink,
        (disabled || busy) && styles.buttonDisabled,
        pressed && styles.buttonPressed,
      ]}
    >
      {busy && <ActivityIndicator color={filled ? colors.navy : colors.cream} />}
      <Text
        style={[
          styles.buttonText,
          big && styles.buttonTextBig,
          variant === "primary" && styles.buttonTextPrimary,
          variant === "danger" && styles.buttonTextDanger,
          variant === "link" && styles.buttonTextLink,
        ]}
      >
        {title}
      </Text>
    </Pressable>
  );
}

// The one thing staff have to see at a glance: a full-width block in the result's colour.
export function ResultCard({ tone = "quiet", title, name, lines = [], children }) {
  const palette = tones[tone] || tones.quiet;
  return (
    <View style={[styles.result, { backgroundColor: palette.background }]} accessibilityRole="alert" accessibilityLiveRegion="assertive">
      <Text style={[styles.resultTitle, { color: palette.text }]}>{title}</Text>
      {name ? <Text style={[styles.resultName, { color: palette.text }]}>{name}</Text> : null}
      {lines.filter(Boolean).map((line) => (
        <Text key={line} style={[styles.resultLine, { color: palette.text }]}>
          {line}
        </Text>
      ))}
      {children}
    </View>
  );
}

export function Notice({ tone = "quiet", children }) {
  return (
    <View style={[styles.notice, tone === "bad" && styles.noticeBad, tone === "warn" && styles.noticeWarn]}>
      <Text style={styles.noticeText}>{children}</Text>
    </View>
  );
}

// Stays up from a request that could not reach the server until one gets through.
export function ConnectionBanner() {
  const reachable = useSyncExternalStore(onConnectionChange, isReachable);
  if (reachable) return null;
  return (
    <View style={styles.offline} accessibilityRole="alert">
      <Text style={styles.offlineTitle}>No connection</Text>
      <Text style={styles.offlineText}>
        The last request did not reach the server. Nothing is recorded without a connection.
      </Text>
    </View>
  );
}

export function Segmented({ options, value, onChange }) {
  return (
    <View style={styles.segmented} accessibilityRole="tablist">
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            onPress={() => onChange(option.value)}
            style={[styles.segment, selected && styles.segmentSelected]}
          >
            <Text style={[styles.segmentText, selected && styles.segmentTextSelected]}>{option.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export const Muted = ({ children, style }) => <Text style={[styles.muted, style]}>{children}</Text>;
export const Heading = ({ children }) => <Text style={styles.heading}>{children}</Text>;

export const styles = StyleSheet.create({
  button: {
    minHeight: 48,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    borderRadius: 4,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
  },
  buttonBig: { minHeight: 68 },
  buttonPrimary: { backgroundColor: colors.peach, borderColor: colors.peach },
  buttonDanger: { backgroundColor: colors.badStrong, borderColor: colors.badStrong },
  buttonLink: { borderColor: "transparent", minHeight: 44 },
  buttonDisabled: { opacity: 0.5 },
  buttonPressed: { opacity: 0.8 },
  buttonText: { color: colors.cream, fontSize: 16, fontWeight: "700", textAlign: "center" },
  buttonTextBig: { fontSize: 20 },
  buttonTextPrimary: { color: colors.navy, fontWeight: "800" },
  buttonTextDanger: { color: "#ffffff", fontWeight: "800" },
  buttonTextLink: { color: colors.peach, textDecorationLine: "underline" },
  result: { borderRadius: 6, padding: 20, gap: 6 },
  resultTitle: { fontSize: 30, fontWeight: "800", lineHeight: 36 },
  resultName: { fontSize: 26, fontWeight: "700", lineHeight: 32 },
  resultLine: { fontSize: 18, lineHeight: 24 },
  notice: {
    padding: 12,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    backgroundColor: colors.panel,
  },
  noticeBad: { borderColor: colors.bad },
  noticeWarn: { borderColor: colors.peach },
  noticeText: { color: colors.cream, fontSize: 15, lineHeight: 21 },
  offline: { padding: 12, borderRadius: 4, backgroundColor: colors.badStrong },
  offlineTitle: { color: "#ffffff", fontSize: 18, fontWeight: "800" },
  offlineText: { color: "#ffffff", fontSize: 15, lineHeight: 21 },
  segmented: {
    flexDirection: "row",
    borderWidth: 1,
    borderColor: colors.lineStrong,
    borderRadius: 4,
    overflow: "hidden",
  },
  segment: { flex: 1, minHeight: 44, alignItems: "center", justifyContent: "center", paddingHorizontal: 8 },
  segmentSelected: { backgroundColor: colors.cream },
  segmentText: { color: colors.mist, fontSize: 15, fontWeight: "700" },
  segmentTextSelected: { color: colors.navy },
  muted: { color: colors.mist, fontSize: 15, lineHeight: 21 },
  heading: { color: colors.cream, fontSize: 22, fontWeight: "800" },
  input: {
    minHeight: 48,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    borderRadius: 4,
    backgroundColor: colors.field,
    color: colors.cream,
    fontSize: 17,
  },
  label: { color: colors.mist, fontSize: 13, fontWeight: "700", marginBottom: 6 },
  screen: { flexGrow: 1, padding: 16, gap: 14 },
});
