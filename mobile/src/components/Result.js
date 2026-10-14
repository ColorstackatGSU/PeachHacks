import { useEffect, useState } from "react";
import { AccessibilityInfo, Animated, Easing, Platform, Pressable, StyleSheet, View } from "react-native";

import { colors, radii, results, space } from "../theme";
import { Icon, Name, T } from "./ui";

export function useReducedMotion() {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    let live = true;
    AccessibilityInfo.isReduceMotionEnabled().then((value) => live && setReduced(value));
    const subscription = AccessibilityInfo.addEventListener("reduceMotionChanged", setReduced);
    return () => {
      live = false;
      subscription.remove();
    };
  }, []);
  return reduced;
}

const antennaAtTop = Platform.OS === "ios";

export const HOLD_HINT = `Hold the badge to the ${antennaAtTop ? "top" : "back"} of the phone`;

const RING_MS = 1600;
// One clock drives all three rings, each a third of a turn behind the last, so
// they never drift apart.
const RING_PHASES = [0, 1 / 3, 2 / 3];

function phased(clock, offset, from, to) {
  if (offset === 0) return clock.interpolate({ inputRange: [0, 1], outputRange: [from, to] });
  const at = from + (to - from) * offset;
  return clock.interpolate({
    inputRange: [0, 1 - offset, 1 - offset + 0.0001, 1],
    outputRange: [at, to, from, at],
  });
}

// A phone with rings spreading from where its antenna is: the top edge on an
// iPhone, the middle of the back on Android.
export function WaitingVisual({ small = false }) {
  const reduced = useReducedMotion();
  const [clock] = useState(() => new Animated.Value(0));
  useEffect(() => {
    if (reduced) {
      clock.setValue(0.4);
      return undefined;
    }
    clock.setValue(0);
    const loop = Animated.loop(
      Animated.timing(clock, { toValue: 1, duration: RING_MS, easing: Easing.linear, useNativeDriver: true }),
    );
    loop.start();
    return () => loop.stop();
  }, [clock, reduced]);

  const scale = small ? 0.7 : 1;
  return (
    <View style={[styles.waiting, { height: (PHONE_H + 24) * scale }]}>
      <View style={{ transform: [{ scale }] }}>
        <View style={[styles.rings, antennaAtTop ? styles.ringsTop : styles.ringsCentre]}>
          {RING_PHASES.map((offset) => (
            <Animated.View
              key={offset}
              style={[
                styles.ring,
                { opacity: phased(clock, offset, 0.9, 0), transform: [{ scale: phased(clock, offset, 0.5, 1.6) }] },
              ]}
            />
          ))}
        </View>
        <View style={styles.phone}>
          <View style={[styles.antenna, antennaAtTop ? styles.antennaTop : styles.antennaCentre]} />
        </View>
      </View>
    </View>
  );
}

// The reader is listening: the phone, its rings and where to hold the badge.
export function Ready({ title = "Ready", hint = HOLD_HINT, children }) {
  return (
    <View style={styles.ready}>
      <WaitingVisual />
      <T v="heading" center>
        {title}
      </T>
      {hint ? (
        <T v="secondary" color={colors.textMuted} center>
          {hint}
        </T>
      ) : null}
      {children}
    </View>
  );
}

// One answer, in its colour, with an icon and a word, so colour is never the only
// signal. `pulse` changes with every new result, so two identical results in a row
// still visibly land. `drainMs` draws a bar that empties until the panel goes.
export function ResultPanel({ view, pulse, drainMs = 0, onPress, children }) {
  const palette = results[view.family] || results.calm;
  const reduced = useReducedMotion();
  const [enter] = useState(() => new Animated.Value(1));
  const [shake] = useState(() => new Animated.Value(0));
  const [drain] = useState(() => new Animated.Value(0));

  useEffect(() => {
    if (pulse === undefined || reduced) return;
    enter.setValue(0);
    Animated.timing(enter, { toValue: 1, duration: 180, easing: Easing.out(Easing.cubic), useNativeDriver: true }).start();
    if (view.family === "repeat") {
      shake.setValue(0);
      Animated.sequence(
        [1, -1, 1, -1, 0].map((toValue) =>
          Animated.timing(shake, { toValue, duration: 60, easing: Easing.linear, useNativeDriver: true }),
        ),
      ).start();
    }
    // Only a new result replays the entrance.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pulse]);

  useEffect(() => {
    if (!drainMs) return undefined;
    drain.setValue(1);
    const running = Animated.timing(drain, { toValue: 0, duration: drainMs, easing: Easing.linear, useNativeDriver: true });
    running.start();
    return () => running.stop();
  }, [drain, drainMs, pulse]);

  const hasTitle = Boolean(view.title);
  const body = (
    <Animated.View
      accessibilityRole="alert"
      accessibilityLiveRegion="assertive"
      style={[
        styles.panel,
        { backgroundColor: palette.background },
        {
          opacity: enter,
          transform: [
            { translateY: enter.interpolate({ inputRange: [0, 1], outputRange: [24, 0] }) },
            { translateX: shake.interpolate({ inputRange: [-1, 1], outputRange: [-8, 8] }) },
          ],
        },
      ]}
    >
      {view.family === "void" ? <View style={styles.voidBar} /> : null}
      <Icon name={view.icon} size={48} color={palette.accent} />
      {hasTitle ? (
        <>
          <T v="row" color={palette.ink}>
            {view.word}
          </T>
          {view.titleIsName === false ? (
            <T v="name" color={palette.ink}>
              {view.title}
            </T>
          ) : (
            <Name name={view.title} staff={view.staff} color={palette.ink} />
          )}
        </>
      ) : (
        <T v="name" color={palette.ink}>
          {view.word}
        </T>
      )}
      {view.detail ? (
        <T v="body" color={palette.ink}>
          {view.detail}
        </T>
      ) : null}
      {children}
      {drainMs ? (
        <Animated.View
          pointerEvents="none"
          style={[styles.drain, { backgroundColor: palette.ink, transform: [{ scaleX: drain }] }]}
        />
      ) : null}
    </Animated.View>
  );

  if (!onPress) return body;
  return (
    <Pressable accessibilityRole="button" onPress={onPress}>
      {body}
    </Pressable>
  );
}

const PHONE_W = 54;
const PHONE_H = 96;
const RING = 84;

const styles = StyleSheet.create({
  panel: { borderRadius: radii.panel, padding: space.xl, gap: space.xs, overflow: "hidden" },
  voidBar: { position: "absolute", left: 0, top: 0, bottom: 0, width: 6, backgroundColor: colors.stop },
  drain: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    height: 4,
    opacity: 0.5,
    transformOrigin: "left center",
  },
  ready: { alignItems: "center", gap: space.sm, paddingVertical: space.lg },
  waiting: { alignItems: "center", justifyContent: "center" },
  phone: {
    width: PHONE_W,
    height: PHONE_H,
    borderRadius: 12,
    borderWidth: 2.5,
    borderColor: colors.textMuted,
    backgroundColor: colors.field,
    alignItems: "center",
  },
  antenna: { width: 10, height: 10, borderRadius: 5, backgroundColor: colors.sky, position: "absolute" },
  antennaTop: { top: 6 },
  antennaCentre: { top: PHONE_H / 2 - 8 },
  rings: {
    position: "absolute",
    width: RING,
    height: RING,
    left: (PHONE_W - RING) / 2,
    alignItems: "center",
    justifyContent: "center",
  },
  ringsTop: { top: 11 - RING / 2 },
  ringsCentre: { top: PHONE_H / 2 - 3 - RING / 2 },
  ring: {
    position: "absolute",
    width: RING,
    height: RING,
    borderRadius: RING / 2,
    borderWidth: 2.5,
    borderColor: colors.sky,
  },
});
