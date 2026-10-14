import { MaterialDesignIcons } from "@react-native-vector-icons/material-design-icons";
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from "react-native";

import { colors, fonts, radii, sizes, space, type } from "../theme";

export const Icon = ({ name, size = 24, color = colors.textMuted, style }) => (
  <MaterialDesignIcons name={name} size={size} color={color} style={style} />
);

// All text goes through here so every string is in an embedded font.
export function T({ v = "body", color = colors.text, center = false, style, children, ...rest }) {
  return (
    <Text
      maxFontSizeMultiplier={v === "name" || v === "title" ? 1.15 : 1.3}
      style={[type[v], { color }, center && styles.center, style]}
      {...rest}
    >
      {children}
    </Text>
  );
}

// The one peach thing on a screen.
export function PrimaryButton({ title, onPress, icon, disabled = false, busy = false }) {
  const off = disabled || busy;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: off, busy }}
      disabled={off}
      onPress={onPress}
      style={({ pressed }) => [styles.primary, pressed && styles.pressed]}
    >
      {busy ? <ActivityIndicator color={colors.night} /> : icon ? <Icon name={icon} size={22} color={colors.night} /> : null}
      <Text maxFontSizeMultiplier={1.2} numberOfLines={1} style={[styles.primaryText, off && !busy && styles.primaryOff]}>
        {title}
      </Text>
    </Pressable>
  );
}

// Secondary and destructive actions are plain text: cream, or red for destructive.
export function TextButton({ title, onPress, icon, tone = "plain", align = "center", disabled = false }) {
  const color = tone === "danger" ? colors.stopSoft : tone === "muted" ? colors.textMuted : colors.text;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      hitSlop={6}
      style={({ pressed }) => [
        styles.textButton,
        align === "start" && styles.textButtonStart,
        (pressed || disabled) && styles.pressed,
      ]}
    >
      {icon ? <Icon name={icon} size={22} color={color} /> : null}
      <Text maxFontSizeMultiplier={1.2} style={[styles.textButtonText, { color }]}>
        {title}
      </Text>
    </Pressable>
  );
}

export function IconButton({ name, label, onPress, color = colors.text }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      hitSlop={8}
      style={({ pressed }) => [styles.iconButton, pressed && styles.iconButtonPressed]}
    >
      <Icon name={name} size={26} color={color} />
    </Pressable>
  );
}

// A one-line notice: a refusal in red, or a caution outlined in yellow.
export function Bar({ tone = "stop", icon, children }) {
  const stop = tone === "stop";
  return (
    <View accessibilityRole="alert" style={[styles.bar, stop ? styles.barStop : styles.barRepeat]}>
      <Icon name={icon || (stop ? "alert-circle-outline" : "alert-outline")} size={22} color={stop ? colors.white : colors.repeat} />
      <T v="secondary" color={stop ? colors.white : colors.text} style={styles.barText}>
        {children}
      </T>
    </View>
  );
}

export function StaffTag({ color = colors.text }) {
  return (
    <View style={[styles.staff, { borderColor: color }]}>
      <Text maxFontSizeMultiplier={1.1} style={[styles.staffText, { color }]}>
        Staff
      </Text>
    </View>
  );
}

// Android splits a long hyphenated surname in the middle of a word; a zero-width
// space after each hyphen lets it break there instead.
const breakable = (name) => String(name || "").replace(/-/g, "-​");

// A name with the Staff tag after it when the person is flagged staff.
export function Name({ name, staff = false, v = "name", color = colors.text, numberOfLines = 3 }) {
  const text = (
    <T v={v} color={color} numberOfLines={numberOfLines} adjustsFontSizeToFit minimumFontScale={0.7} style={styles.nameText}>
      {breakable(name)}
    </T>
  );
  if (!staff) return text;
  return (
    <View style={styles.name}>
      {text}
      <StaffTag color={color} />
    </View>
  );
}

// A plain list row: hairline below, title and optional subtitle, chevron.
export function Row({ title, subtitle, icon, right, onPress, accessibilityLabel }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      onPress={onPress}
      style={({ pressed }) => [styles.row, subtitle && styles.rowTall, pressed && styles.rowPressed]}
    >
      {icon ? <Icon name={icon} size={24} /> : null}
      <View style={styles.rowMain}>
        {typeof title === "string" ? (
          <T v="row" numberOfLines={2}>
            {title}
          </T>
        ) : (
          title
        )}
        {subtitle ? (
          <T v="secondary" color={colors.textMuted} numberOfLines={1}>
            {subtitle}
          </T>
        ) : null}
      </View>
      {right}
      <Icon name="chevron-right" size={22} />
    </Pressable>
  );
}

export function Field({ label, inputRef, style, ...rest }) {
  return (
    <View style={styles.fieldWrap}>
      {label ? (
        <T v="caption" color={colors.textMuted}>
          {label}
        </T>
      ) : null}
      <TextInput
        ref={inputRef}
        style={[styles.field, style]}
        placeholderTextColor={colors.textMuted}
        selectionColor={colors.peach}
        {...rest}
      />
    </View>
  );
}

export const styles = StyleSheet.create({
  center: { textAlign: "center" },
  pressed: { opacity: 0.6 },
  primary: {
    height: sizes.primary,
    paddingHorizontal: space.lg,
    borderRadius: radii.button,
    backgroundColor: colors.peach,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: space.sm,
  },
  primaryOff: { opacity: 0.45 },
  primaryText: { ...type.button, color: colors.night, includeFontPadding: false, flexShrink: 1 },
  textButton: {
    minHeight: sizes.text,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: space.xs,
  },
  textButtonStart: { justifyContent: "flex-start", alignSelf: "flex-start" },
  textButtonText: { ...type.button, includeFontPadding: false },
  iconButton: { width: 44, height: 44, borderRadius: 22, alignItems: "center", justifyContent: "center" },
  iconButtonPressed: { backgroundColor: colors.raised },
  bar: {
    flexDirection: "row",
    alignItems: "center",
    gap: space.sm,
    paddingHorizontal: space.md,
    paddingVertical: space.md,
    borderRadius: radii.button,
  },
  barStop: { backgroundColor: colors.stop },
  barRepeat: { borderWidth: 1.5, borderColor: colors.repeat },
  barText: { flex: 1 },
  staff: { borderWidth: 1.5, borderRadius: radii.pill, paddingHorizontal: space.sm, paddingVertical: 1 },
  staffText: { fontFamily: fonts.semi, fontSize: 13, lineHeight: 18, includeFontPadding: false },
  name: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", columnGap: space.sm, rowGap: space.xs },
  nameText: { maxWidth: "100%" },
  row: {
    minHeight: sizes.row,
    flexDirection: "row",
    alignItems: "center",
    gap: space.md,
    paddingVertical: space.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.lineStrong,
  },
  rowTall: { minHeight: sizes.rowTall },
  rowPressed: { backgroundColor: colors.raised },
  rowMain: { flex: 1, gap: 2 },
  fieldWrap: { gap: space.xs },
  field: {
    height: sizes.field,
    paddingHorizontal: space.lg,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    borderRadius: radii.button,
    backgroundColor: colors.field,
    color: colors.text,
    fontFamily: fonts.regular,
    fontSize: 17,
  },
});
