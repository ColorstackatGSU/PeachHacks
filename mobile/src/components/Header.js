import { StyleSheet, View } from "react-native";

import { colors, sizes, space } from "../theme";
import { IconButton, T } from "./ui";

// A job's header: a way back home, the job's name, and room on the right for a
// count or a menu.
export function JobHeader({ title, onBack, right }) {
  return (
    <View style={styles.header}>
      {onBack ? <IconButton name="chevron-left" label="Back to home" onPress={onBack} /> : null}
      <T v="heading" numberOfLines={1} style={[styles.title, !onBack && styles.titleAlone]}>
        {title}
      </T>
      {right}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    minHeight: sizes.header,
    paddingLeft: space.sm,
    paddingRight: space.md,
    flexDirection: "row",
    alignItems: "center",
    gap: space.xs,
  },
  title: { flex: 1, color: colors.text },
  titleAlone: { paddingLeft: space.md },
});
