import { useEffect, useRef, useState } from "react";
import { Image, KeyboardAvoidingView, Linking, Platform, ScrollView, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { ADMIN_SITE, API_BASE } from "../api/client";
import { Bar, Field, PrimaryButton, T, TextButton } from "../components/ui";
import { errorText } from "../lib/format";
import { loadRememberedEmail } from "../lib/session";
import { useAuth } from "../state/Auth";
import { colors, radii, space, type } from "../theme";

const LOGO_WIDTH = 168;
const LOGO_RATIO = 46 / 78;

// A fixed scatter, as [left %, top, size], so the sky is the same every time.
const STARS = [
  [6, 26, 10], [22, 78, 6], [38, 18, 8], [52, 96, 6], [64, 40, 12], [12, 142, 8],
  [84, 150, 8], [44, 168, 6], [92, 214, 10], [4, 232, 6], [72, 262, 6], [30, 250, 8],
];

export function NightSky() {
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      {STARS.map(([left, top, size]) => (
        <Image
          key={`${left}-${top}`}
          source={require("../../assets/brand/star.png")}
          style={{ position: "absolute", left: `${left}%`, top: top + 40, width: size, height: size, opacity: 0.8 }}
        />
      ))}
      <Image source={require("../../assets/brand/moon.png")} style={styles.moon} />
    </View>
  );
}

export function Brand() {
  return (
    <View style={styles.brand}>
      <Image
        source={require("../../assets/brand/logo-stacked.png")}
        accessibilityLabel="PeachHacks"
        resizeMode="contain"
        style={styles.logo}
      />
      <View style={styles.theme}>
        <T color={colors.peach} style={styles.themeText}>
          Midnight in the City
        </T>
      </View>
      <T v="row" color={colors.textMuted}>
        Staff app
      </T>
    </View>
  );
}

export default function SignIn() {
  const insets = useSafeAreaInsets();
  const { signIn, notice } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const passwordRef = useRef(null);

  useEffect(() => {
    let live = true;
    loadRememberedEmail().then((saved) => {
      if (live && saved) setEmail((current) => current || saved);
    });
    return () => {
      live = false;
    };
  }, []);

  const submit = async () => {
    if (busy || !email.trim() || !password) return;
    setBusy(true);
    setError(null);
    try {
      await signIn(email, password);
    } catch (cause) {
      setError(cause);
      setBusy(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.root} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <NightSky />
      <ScrollView
        contentContainerStyle={[styles.content, { paddingTop: insets.top + space.xl, paddingBottom: insets.bottom + space.xl }]}
        keyboardShouldPersistTaps="handled"
      >
        <Brand />

        <View style={styles.form}>
          {error ? <Bar tone="stop">{errorText(error)}</Bar> : null}
          {notice && !error ? <Bar tone="repeat">{notice}</Bar> : null}

          <Field
            label="Email"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="email"
            keyboardType="email-address"
            textContentType="username"
            returnKeyType="next"
            onSubmitEditing={() => passwordRef.current?.focus()}
          />
          <Field
            label="Password"
            inputRef={passwordRef}
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="current-password"
            textContentType="password"
            returnKeyType="go"
            onSubmitEditing={submit}
          />

          <View style={styles.submit}>
            <PrimaryButton title="Sign in" busy={busy} disabled={!email.trim() || !password} onPress={submit} />
          </View>
          <TextButton title="Forgot password?" tone="muted" onPress={() => Linking.openURL(ADMIN_SITE)} />
          {__DEV__ ? (
            <T v="caption" color={colors.textMuted} center style={styles.dev}>
              Development build · {API_BASE}
            </T>
          ) : null}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.night },
  content: { flexGrow: 1, justifyContent: "center", paddingHorizontal: space.xl, gap: space.xxl },
  moon: { position: "absolute", top: 64, right: -18, width: 96, height: 96 },
  brand: { alignItems: "center", gap: space.md },
  logo: { width: LOGO_WIDTH, height: Math.round(LOGO_WIDTH * LOGO_RATIO) },
  theme: {
    paddingHorizontal: 18,
    paddingTop: 4,
    paddingBottom: 6,
    borderRadius: radii.pill,
    backgroundColor: colors.field,
  },
  themeText: { ...type.wordmark, fontSize: 18, lineHeight: 24 },
  form: { gap: space.lg },
  submit: { marginTop: space.sm },
  dev: { opacity: 0.6 },
});
