import { Modal, Pressable, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors, radii, space } from "../theme";
import { Icon, T } from "./ui";

// The "⋯" menu: the rare actions of a screen, kept off the screen itself.
export function MenuSheet({ visible, items, onSelect, onClose }) {
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose} statusBarTranslucent navigationBarTranslucent>
      <Pressable accessibilityLabel="Close" style={styles.scrim} onPress={onClose} />
      <View style={[styles.sheet, { paddingBottom: insets.bottom + space.md }]}>
        <View style={styles.grip} />
        {items.map((item) => (
          <Pressable
            key={item.key}
            accessibilityRole="button"
            onPress={() => {
              onClose();
              onSelect(item.key);
            }}
            style={({ pressed }) => [styles.item, pressed && styles.pressed]}
          >
            <Icon name={item.icon} size={24} color={colors.text} />
            <T v="row">{item.label}</T>
          </Pressable>
        ))}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  scrim: { flex: 1, backgroundColor: colors.scrim },
  sheet: {
    backgroundColor: colors.raised,
    borderTopLeftRadius: radii.panel,
    borderTopRightRadius: radii.panel,
    paddingHorizontal: space.gutter,
    paddingTop: space.md,
  },
  grip: { alignSelf: "center", width: 40, height: 4, borderRadius: 2, backgroundColor: colors.lineStrong, marginBottom: space.sm },
  item: { minHeight: 56, flexDirection: "row", alignItems: "center", gap: space.md },
  pressed: { opacity: 0.6 },
});
