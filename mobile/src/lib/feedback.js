import * as Haptics from "expo-haptics";

const { NotificationFeedbackType: Notify, ImpactFeedbackStyle: Impact } = Haptics;

// Feedback is a nicety; a phone that refuses it must not break check-in.
const quiet = (promise) => promise.catch(() => {});

// Each result family feels different, so the answer is known without looking:
// one short pulse for a success, two for "already", a hard one for a refusal, and
// a refusal plus a thud when nothing was recorded.
const PATTERNS = {
  ok: () => quiet(Haptics.notificationAsync(Notify.Success)),
  sponsor: () => quiet(Haptics.notificationAsync(Notify.Success)),
  repeat: () => {
    quiet(Haptics.notificationAsync(Notify.Warning));
    setTimeout(() => quiet(Haptics.notificationAsync(Notify.Warning)), 200);
  },
  stop: () => quiet(Haptics.notificationAsync(Notify.Error)),
  void: () => {
    quiet(Haptics.notificationAsync(Notify.Error));
    setTimeout(() => quiet(Haptics.impactAsync(Impact.Heavy)), 250);
  },
};

export function signal(family) {
  (PATTERNS[family] || PATTERNS.stop)();
}

export const press = () => quiet(Haptics.selectionAsync());
