import * as Haptics from "expo-haptics";

const TYPES = {
  success: Haptics.NotificationFeedbackType.Success,
  warning: Haptics.NotificationFeedbackType.Warning,
  problem: Haptics.NotificationFeedbackType.Error,
};

// Feedback is a nicety; a phone that refuses it must not break check-in.
export function signal(kind) {
  Haptics.notificationAsync(TYPES[kind] || TYPES.problem).catch(() => {});
}
