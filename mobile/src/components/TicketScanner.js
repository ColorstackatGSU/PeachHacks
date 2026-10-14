import { CameraView, useCameraPermissions } from "expo-camera";
import { useState } from "react";
import { ActivityIndicator, Linking, StyleSheet, View } from "react-native";

import { colors, radii, space } from "../theme";
import { Icon, IconButton, PrimaryButton, T, TextButton } from "./ui";

const RETICLE = 220;
const BRACKET = 32;

function Reticle({ color }) {
  const corner = (extra) => <View style={[styles.bracket, { borderColor: color }, extra]} />;
  return (
    <View pointerEvents="none" style={styles.reticle}>
      {corner({ top: 0, left: 0, borderTopWidth: 3, borderLeftWidth: 3, borderTopLeftRadius: 12 })}
      {corner({ top: 0, right: 0, borderTopWidth: 3, borderRightWidth: 3, borderTopRightRadius: 12 })}
      {corner({ bottom: 0, left: 0, borderBottomWidth: 3, borderLeftWidth: 3, borderBottomLeftRadius: 12 })}
      {corner({ bottom: 0, right: 0, borderBottomWidth: 3, borderRightWidth: 3, borderBottomRightRadius: 12 })}
    </View>
  );
}

// Mount to start the camera, unmount to stop it. While `checking` the preview stays
// live but reads are ignored, so a code still in frame is not submitted again.
export function TicketScanner({ checking, onRead }) {
  const [permission, requestPermission] = useCameraPermissions();
  const [failed, setFailed] = useState(false);
  const [torch, setTorch] = useState(false);

  if (!permission) {
    return (
      <View style={[styles.frame, styles.centre]}>
        <ActivityIndicator color={colors.peach} />
      </View>
    );
  }

  if (failed) {
    return (
      <View style={[styles.frame, styles.centre]}>
        <Icon name="camera-off-outline" size={40} />
        <T v="row" center>
          Could not start the camera
        </T>
        <TextButton title="Try again" onPress={() => setFailed(false)} />
      </View>
    );
  }

  if (!permission.granted) {
    return (
      <View style={[styles.frame, styles.centre]}>
        <Icon name="camera-outline" size={40} />
        <T v="row" center>
          Camera access is needed
        </T>
        <T v="secondary" color={colors.textMuted} center>
          It is only used to scan ticket QR codes.
        </T>
        <View style={styles.action}>
          {permission.canAskAgain ? (
            <PrimaryButton title="Allow camera" onPress={requestPermission} />
          ) : (
            <PrimaryButton title="Open settings" onPress={() => Linking.openSettings()} />
          )}
        </View>
      </View>
    );
  }

  return (
    <View style={styles.frame}>
      <CameraView
        style={StyleSheet.absoluteFill}
        facing="back"
        enableTorch={torch}
        barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
        onBarcodeScanned={checking ? undefined : ({ data }) => onRead(data)}
        onMountError={() => setFailed(true)}
      />
      <View pointerEvents="none" style={styles.centreFill}>
        <Reticle color={checking ? colors.sky : colors.text} />
      </View>
      <View style={styles.torch}>
        <IconButton
          name={torch ? "flashlight-off" : "flashlight"}
          label={torch ? "Turn the light off" : "Turn the light on"}
          onPress={() => setTorch((on) => !on)}
        />
      </View>
      <View style={styles.caption}>
        {checking ? <ActivityIndicator color={colors.sky} /> : null}
        <T v="secondary" center>
          {checking ? "Checking the ticket…" : "Point at the ticket QR code"}
        </T>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { flex: 1, borderRadius: radii.panel, overflow: "hidden", backgroundColor: colors.field },
  centre: { alignItems: "center", justifyContent: "center", gap: space.sm, padding: space.xl },
  centreFill: {
    position: "absolute",
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    alignItems: "center",
    justifyContent: "center",
  },
  action: { alignSelf: "stretch", marginTop: space.md },
  reticle: { width: RETICLE, height: RETICLE, maxWidth: "80%", maxHeight: "80%" },
  bracket: { position: "absolute", width: BRACKET, height: BRACKET },
  torch: { position: "absolute", top: space.sm, right: space.sm, borderRadius: 22, backgroundColor: colors.scrim },
  caption: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: space.sm,
    paddingVertical: space.md,
    backgroundColor: colors.scrim,
  },
});
