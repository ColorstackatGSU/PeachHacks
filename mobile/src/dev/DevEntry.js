import { useState } from "react";
import { Pressable, StyleSheet, TextInput, View } from "react-native";

import { T } from "../components/ui";
import { colors, fonts, radii, space } from "../theme";

// Development builds only: stands in for the card reader on a simulator or emulator,
// and for the camera where there is no QR code to point it at. Screens load this file
// behind `__DEV__`, so a release bundle does not contain it. It stays folded away
// behind a small toggle so the screen looks as it will on a real phone.
// Once opened it stays open on every screen until it is folded away again.
let keepOpen = false;

export function Input({ kind, onSubmit }) {
  const [open, setOpen] = useState(keepOpen);
  const [value, setValue] = useState("");
  const submit = () => {
    if (!value.trim()) return;
    onSubmit(value.trim());
    setValue("");
  };
  return (
    <View style={styles.wrap}>
      <Pressable accessibilityRole="button" onPress={() => {
          keepOpen = !open;
          setOpen(keepOpen);
        }} hitSlop={8}>
        <T v="caption" color={colors.textMuted} style={styles.toggle}>
          Dev input {open ? "▾" : "▸"}
        </T>
      </Pressable>
      {open ? (
        <View style={styles.row}>
          <TextInput
            style={styles.input}
            value={value}
            onChangeText={setValue}
            onSubmitEditing={submit}
            placeholder={kind === "uid" ? "Badge UID, e.g. 04A1B2C3D4E5F6" : "Ticket token or URL"}
            placeholderTextColor={colors.textMuted}
            autoCapitalize="none"
            autoCorrect={false}
            accessibilityLabel="Development stand-in input"
          />
          <Pressable accessibilityRole="button" onPress={submit} style={styles.send}>
            <T v="caption">Send</T>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: space.xs },
  toggle: { fontSize: 12, lineHeight: 16, opacity: 0.7, alignSelf: "flex-end" },
  row: { flexDirection: "row", gap: space.sm },
  input: {
    flex: 1,
    height: 40,
    paddingHorizontal: space.md,
    borderRadius: radii.button,
    borderWidth: 1,
    borderStyle: "dashed",
    borderColor: colors.lineStrong,
    color: colors.text,
    fontFamily: fonts.regular,
    fontSize: 14,
  },
  send: {
    height: 40,
    paddingHorizontal: space.md,
    borderRadius: radii.button,
    borderWidth: 1,
    borderColor: colors.lineStrong,
    justifyContent: "center",
  },
});
