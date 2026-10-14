import { useCallback, useEffect, useRef, useState } from "react";
import { Modal, Platform, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { api, isTemporaryFailure } from "../api/client";
import { JobHeader } from "../components/Header";
import { canRead, DevInput, NfcUnavailable, useNfcStatus } from "../components/NfcState";
import { Ready, ResultPanel } from "../components/Result";
import { IconButton, PrimaryButton, T, TextButton } from "../components/ui";
import { signal } from "../lib/feedback";
import { errorText } from "../lib/format";
import { lanyardTitle } from "../lib/results";
import { looksLikeUid } from "../lib/uid";
import { cancelRead, nfcProblemText, readUid } from "../nfc";
import { colors, space } from "../theme";

const HOLD_BLANK = `Hold a blank badge to the ${Platform.OS === "ios" ? "top" : "back"} of the phone`;

const NOT_A_BADGE = { family: "stop", icon: "help-circle-outline", word: "Not a PeachHacks badge", again: true };

// Issues badges that are not tied to a person: a sponsor's tap is let through at
// events and records nothing.
export function SponsorBadge({ visible, onClose }) {
  const insets = useSafeAreaInsets();
  const [nfc] = useNfcStatus();
  const [view, setView] = useState(null);
  const [saving, setSaving] = useState(false);
  const [pulse, setPulse] = useState(0);
  const [issued, setIssued] = useState(0);
  const open = useRef(false);

  const show = useCallback((next) => {
    if (!open.current) return;
    setView(next);
    setPulse((n) => n + 1);
    signal(next.family);
  }, []);

  const bind = useCallback(
    async (uid) => {
      if (!looksLikeUid(uid)) {
        show(NOT_A_BADGE);
        return;
      }
      setSaving(true);
      try {
        const bound = await api.bindSponsorBadge(uid);
        const already = bound?.result === "ALREADY_BOUND";
        if (!already) setIssued((n) => n + 1);
        show({
          family: "sponsor",
          icon: "handshake-outline",
          word: already ? "Already a sponsor badge" : "Sponsor badge ready",
          title: lanyardTitle(bound?.lanyard) || "Sponsor lanyard",
          titleIsName: false,
          done: true,
        });
      } catch (error) {
        if (error?.status === 401) return;
        if (error?.code === "BADGE_IN_USE") {
          show({
            family: "stop",
            icon: "card-account-details-outline",
            word: "This badge belongs to a hacker",
            detail: "Use a blank badge",
            again: true,
          });
        } else if (error?.status === 400) {
          show(NOT_A_BADGE);
        } else {
          show({
            family: "void",
            icon: isTemporaryFailure(error) ? "wifi-off" : "alert-circle-outline",
            word: "Not saved",
            detail: errorText(error),
            retryUid: uid,
          });
        }
      } finally {
        setSaving(false);
      }
    },
    [show],
  );

  const read = useCallback(async () => {
    setView(null);
    if (nfc !== "ready") return;
    try {
      const uid = await readUid({ message: "Hold a blank badge to the top of the phone." });
      if (open.current) await bind(uid);
    } catch (error) {
      if (open.current && error.kind !== "cancelled") {
        show({ family: "void", icon: "nfc-variant-off", word: "Badge not read", detail: nfcProblemText(error.kind), again: true });
      }
    }
  }, [bind, nfc, show]);

  // The reader arms as the screen opens.
  useEffect(() => {
    open.current = visible;
    if (!visible) return undefined;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    read();
    return () => {
      open.current = false;
      cancelRead();
    };
  }, [visible, read]);

  const close = () => {
    cancelRead();
    setView(null);
    setIssued(0);
    onClose();
  };

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={close} statusBarTranslucent navigationBarTranslucent>
      <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
        <JobHeader title="Sponsor badge" right={<IconButton name="close" label="Close" onPress={close} />} />
        <View style={styles.body}>
          <View style={styles.stage}>
            {saving ? (
              <Ready title="Saving…" hint={null} />
            ) : view ? (
              <ResultPanel view={view} pulse={pulse} />
            ) : canRead(nfc) ? (
              <Ready title="Ready for a blank badge" hint={HOLD_BLANK} />
            ) : null}
          </View>
          {issued > 0 ? (
            <T v="secondary" color={colors.textMuted} center>
              {issued} issued on this phone
            </T>
          ) : null}

          <View style={styles.bottom}>
            <NfcUnavailable status={nfc} />
            <DevInput show={nfc === "simulated" && !saving && !view} onSubmit={bind} />
            {view?.retryUid ? (
              <>
                <PrimaryButton title="Try again" icon="refresh" busy={saving} onPress={() => bind(view.retryUid)} />
                <TextButton title="Tap a different badge" onPress={read} />
              </>
            ) : view?.done ? (
              <>
                <PrimaryButton title="Issue another" icon="nfc" onPress={read} />
                <TextButton title="Done" onPress={close} />
              </>
            ) : view?.again ? (
              <PrimaryButton title="Tap another badge" icon="nfc" onPress={read} />
            ) : (
              <TextButton title="Cancel" onPress={close} />
            )}
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.night },
  body: { flex: 1, paddingHorizontal: space.gutter, paddingBottom: space.lg, gap: space.lg },
  stage: { minHeight: 260, justifyContent: "center" },
  bottom: { flex: 1, justifyContent: "flex-end", gap: space.xs },
});
