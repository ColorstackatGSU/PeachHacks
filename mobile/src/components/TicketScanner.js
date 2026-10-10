import { CameraView, useCameraPermissions } from "expo-camera";
import { useState } from "react";
import { ActivityIndicator, Linking, StyleSheet, Text, View } from "react-native";

import { colors } from "../theme";
import { Button, Muted } from "./ui";

// Mount to start the camera, unmount to stop it. `paused` keeps the preview live but
// stops reading, so a code still in frame is not submitted again.
export function TicketScanner({ paused, onRead, onUseSearch }) {
  const [permission, requestPermission] = useCameraPermissions();
  const [failed, setFailed] = useState(false);

  if (!permission) {
    return (
      <View style={styles.block}>
        <ActivityIndicator color={colors.peach} />
      </View>
    );
  }

  if (!permission.granted || failed) {
    const title = failed ? "Could not start the camera" : "Camera access is needed";
    const text = failed
      ? "Something went wrong starting the camera. Try again, or search by name."
      : "The camera is only used to scan ticket QR codes. Search by name works without it.";
    return (
      <View style={styles.block}>
        <Text style={styles.title}>{title}</Text>
        <Muted style={styles.centered}>{text}</Muted>
        {failed ? (
          <Button title="Try again" onPress={() => setFailed(false)} />
        ) : permission.canAskAgain ? (
          <Button title="Allow camera" variant="primary" onPress={requestPermission} />
        ) : (
          <Button title="Open settings" variant="primary" onPress={() => Linking.openSettings()} />
        )}
        <Button title="Search by name" onPress={onUseSearch} />
      </View>
    );
  }

  return (
    <View style={styles.frame}>
      <CameraView
        style={StyleSheet.absoluteFill}
        facing="back"
        barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
        onBarcodeScanned={paused ? undefined : ({ data }) => onRead(data)}
        onMountError={() => setFailed(true)}
      />
      <View style={styles.status}>
        <Text style={styles.statusText}>{paused ? "Checking the ticket…" : "Point the camera at a ticket QR code"}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { flex: 1, minHeight: 320, borderRadius: 6, overflow: "hidden", backgroundColor: colors.deep },
  status: { position: "absolute", left: 0, right: 0, bottom: 0, padding: 10, backgroundColor: "rgba(0, 22, 42, 0.78)" },
  statusText: { color: colors.cream, fontSize: 15, fontWeight: "700", textAlign: "center" },
  block: {
    flex: 1,
    minHeight: 320,
    padding: 20,
    gap: 12,
    borderRadius: 6,
    justifyContent: "center",
    backgroundColor: colors.panel,
  },
  title: { color: colors.cream, fontSize: 20, fontWeight: "800", textAlign: "center" },
  centered: { textAlign: "center" },
});
