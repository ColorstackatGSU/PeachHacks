import { useState } from "react";
import { Text, TextInput, View } from "react-native";

import { Button, styles as ui } from "../components/ui";
import { colors } from "../theme";

// Development builds only: stands in for the card reader on a simulator or emulator,
// and for the camera where there is no QR code to point it at. Screens load this file
// behind `__DEV__`, so a release bundle does not contain it.
function DevField({ label, placeholder, button, onSubmit }) {
  const [value, setValue] = useState("");
  const submit = () => {
    if (!value.trim()) return;
    onSubmit(value.trim());
    setValue("");
  };
  return (
    <View style={{ gap: 6, padding: 12, borderWidth: 1, borderStyle: "dashed", borderColor: colors.peach, borderRadius: 4 }}>
      <Text style={ui.label}>{label}</Text>
      <TextInput
        style={ui.input}
        value={value}
        onChangeText={setValue}
        onSubmitEditing={submit}
        placeholder={placeholder}
        placeholderTextColor={colors.mist}
        autoCapitalize="none"
        autoCorrect={false}
      />
      <Button title={button} onPress={submit} disabled={!value.trim()} />
    </View>
  );
}

export const UidField = ({ onSubmit }) => (
  <DevField
    label="Development stand-in: this device has no NFC. Type a badge UID."
    placeholder="04:A1:B2:C3:D4:E5:F6"
    button="Use this UID"
    onSubmit={onSubmit}
  />
);

export const CodeField = ({ onSubmit }) => (
  <DevField
    label="Development stand-in: type a ticket token or ticket URL."
    placeholder="Ticket token or URL"
    button="Use this code"
    onSubmit={onSubmit}
  />
);
