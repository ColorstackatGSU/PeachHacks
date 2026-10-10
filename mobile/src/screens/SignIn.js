import { useEffect, useRef, useState } from "react";
import { KeyboardAvoidingView, Linking, Platform, ScrollView, Text, TextInput, View } from "react-native";

import { ADMIN_SITE, API_BASE } from "../api/client";
import { Button, Muted, Notice, styles as ui } from "../components/ui";
import { errorText } from "../lib/format";
import { loadRememberedEmail } from "../lib/session";
import { useAuth } from "../state/Auth";
import { colors } from "../theme";

export default function SignIn() {
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
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <ScrollView contentContainerStyle={[ui.screen, { justifyContent: "center" }]} keyboardShouldPersistTaps="handled">
        <Text style={{ color: colors.peach, fontSize: 13, fontWeight: "800", letterSpacing: 2 }}>PEACHHACKS STAFF</Text>
        <Text style={{ color: colors.cream, fontSize: 28, fontWeight: "800" }}>Sign in</Text>
        <Muted>Use your PeachHacks organizer or volunteer account.</Muted>

        {notice ? <Notice tone="warn">{notice}</Notice> : null}
        {error ? <Notice tone="bad">{errorText(error)}</Notice> : null}

        <View>
          <Text style={ui.label}>Email</Text>
          <TextInput
            style={ui.input}
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
        </View>
        <View>
          <Text style={ui.label}>Password</Text>
          <TextInput
            ref={passwordRef}
            style={ui.input}
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
        </View>

        <Button title="Sign in" variant="primary" big busy={busy} disabled={!email.trim() || !password} onPress={submit} />
        <Button title="Forgot password?" variant="link" onPress={() => Linking.openURL(ADMIN_SITE)} />
        <Muted style={{ textAlign: "center" }}>
          Passwords are reset on the admin site, admin.peachhacks.com. The link in the reset email opens there.
        </Muted>
        {__DEV__ ? <Muted style={{ textAlign: "center" }}>Development build. API: {API_BASE}</Muted> : null}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
