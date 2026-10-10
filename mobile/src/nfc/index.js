import { AppState, Platform } from "react-native";

import { cleanUid, createRepeatGuard } from "../lib/uid";

// Screens read badges through this module only. It reads the card's UID and nothing
// else: it never writes to a card and never asks for its NDEF contents.

const REPEAT_WINDOW_MS = 2000;
// Polling restarts at once find a card that is still on the phone, so each read is
// followed by a short wait: longer after a new badge, to give time to lift it away.
const IOS_AFTER_READ_PAUSE_MS = 800;
const IOS_REPEAT_PAUSE_MS = 400;
const IOS_RESTART_PAUSE_MS = 1500;
const IOS_BUSY_PAUSE_MS = 3000;
// After an iOS session times out it is reopened on its own only while badges are
// still arriving; an idle phone is left alone until someone presses Resume.
const IOS_AUTO_RESTART_WITHIN_MS = 5 * 60 * 1000;

const isIos = Platform.OS === "ios";

export class NfcReadError extends Error {
  constructor(kind, message) {
    super(message || PROBLEMS[kind] || PROBLEMS.failed);
    this.name = "NfcReadError";
    this.kind = kind;
  }
}

const PROBLEMS = {
  cancelled: "Badge reading was cancelled.",
  timeout: "No badge was read in time.",
  busy: "The phone's NFC reader is busy. Wait a moment and try again.",
  off: "NFC is switched off on this phone.",
  unsupported: "This phone cannot read NFC badges.",
  failed: "The badge could not be read. Hold it flat against the phone and try again.",
};

let library;
let starting = null;
let current = null;

function load() {
  if (library === undefined) {
    try {
      library = require("react-native-nfc-manager");
    } catch {
      library = null;
    }
  }
  return library;
}

async function ready() {
  const nfc = load();
  if (!nfc) return null;
  if (!starting) {
    starting = nfc.default.start().then(
      () => true,
      () => {
        starting = null;
        return false;
      },
    );
  }
  // On iOS `start` only checks that the phone has NFC, which `isSupported` asks again
  // for the tag reader, so a refusal there is not final.
  const started = await starting;
  return started || isIos ? nfc : null;
}

// "ready", "off" (Android: NFC is switched off in system settings) or "unsupported".
export async function nfcStatus() {
  try {
    const nfc = await ready();
    if (!nfc) return "unsupported";
    const supported = await nfc.default.isSupported(isIos ? nfc.NfcTech.MifareIOS : "");
    if (!supported) return "unsupported";
    if (!isIos && !(await nfc.default.isEnabled())) return "off";
    return "ready";
  } catch {
    return "unsupported";
  }
}

export async function openNfcSettings() {
  const nfc = load();
  if (!nfc || isIos) return false;
  try {
    return await nfc.default.goToNfcSetting();
  } catch {
    return false;
  }
}

function kindOf(nfc, error) {
  if (error instanceof NfcReadError) return error.kind;
  const { NfcError } = nfc;
  if (error instanceof NfcError.UserCancel || error instanceof NfcError.SessionInvalidated) return "cancelled";
  if (error instanceof NfcError.Timeout) return "timeout";
  if (error instanceof NfcError.SystemBusy) return "busy";
  if (error instanceof NfcError.RadioDisabled) return "off";
  if (error instanceof NfcError.UnsupportedFeature) return "unsupported";
  return "failed";
}

const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function available() {
  const status = await nfcStatus();
  if (status !== "ready") throw new NfcReadError(status);
  return load();
}

async function endCurrent() {
  const session = current;
  current = null;
  if (session) await session.end();
}

// NTAG215 answers as NFC-A. Skipping the NDEF check keeps the read to the UID alone,
// and the phone's own sound is replaced by this app's feedback.
function androidListen(nfc, onUid) {
  const { NfcAdapter, NfcEvents } = nfc;
  nfc.default.setEventListener(NfcEvents.DiscoverTag, (tag) => {
    const uid = cleanUid(tag?.id);
    if (uid) onUid(uid);
  });
  return nfc.default.registerTagEvent({
    isReaderModeEnabled: true,
    readerModeFlags:
      NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
    readerModeDelay: 250,
  });
}

async function androidUnlisten(nfc) {
  nfc.default.setEventListener(nfc.NfcEvents.DiscoverTag, null);
  nfc.default.setEventListener(nfc.NfcEvents.StateChanged, null);
  try {
    await nfc.default.unregisterTagEvent();
  } catch {
    // Nothing was registered.
  }
}

// The tag reader session is the only iOS session that reports a card's UID; the NDEF
// session does not. NTAG215 is found as a MIFARE tag. The other two are listed so a
// foreign card is read and refused instead of leaving the session waiting.
const iosTechs = (nfc) => [nfc.NfcTech.MifareIOS, nfc.NfcTech.Iso15693IOS, nfc.NfcTech.IsoDep];

async function iosCancel(nfc) {
  try {
    await nfc.default.cancelTechnologyRequest();
  } catch {
    // No session was open.
  }
}

async function iosUid(nfc) {
  const tag = await nfc.default.getTag();
  return cleanUid(tag?.id);
}

// Reads one badge and resolves with its UID. Rejects with an NfcReadError whose
// `kind` is one of the keys of PROBLEMS.
export async function readUid({ message = "Hold the badge to the top of the phone." } = {}) {
  const nfc = await available();
  await endCurrent();

  if (isIos) {
    let cancelled = false;
    current = {
      end: async () => {
        cancelled = true;
        await iosCancel(nfc);
      },
    };
    const mine = current;
    try {
      await nfc.default.requestTechnology(iosTechs(nfc), { alertMessage: message });
      const uid = await iosUid(nfc);
      if (!uid) throw new NfcReadError("failed");
      await nfc.default.setAlertMessageIOS("Badge read.").catch(() => {});
      return uid;
    } catch (error) {
      throw new NfcReadError(cancelled ? "cancelled" : kindOf(nfc, error));
    } finally {
      if (current === mine) current = null;
      await iosCancel(nfc);
    }
  }

  return new Promise((resolve, reject) => {
    const mine = {
      end: async () => {
        await androidUnlisten(nfc);
        reject(new NfcReadError("cancelled"));
      },
    };
    current = mine;
    const finish = (settle, value) => {
      if (current === mine) current = null;
      androidUnlisten(nfc).then(() => settle(value));
    };
    nfc.default.setEventListener(nfc.NfcEvents.StateChanged, (event) => {
      if (event?.state === "off" && current === mine) finish(reject, new NfcReadError("off"));
    });
    androidListen(nfc, (uid) => {
      if (current === mine) finish(resolve, uid);
    }).catch(() => {
      if (current === mine) finish(reject, new NfcReadError("failed"));
    });
  });
}

// Stops whatever read is in progress. Call it when a screen is left.
export const cancelRead = () => endCurrent();

// Keeps reading badges until `stop()` is called. `onUid(uid)` is called once per
// card; the same card resting on the phone is ignored for two seconds at a time.
// `onState` hears "reading", then "paused" (iOS closed the session: call start
// again), "off", "unsupported" or "failed" when reading has ended.
// `setMessage(text)` puts text on the iOS scan sheet, which covers the lower half of
// the screen while a session is open.
export function startContinuous({ message = "Hold a badge to the top of the phone.", onUid, onState = () => {} }) {
  let running = true;
  let lastUidAt = Date.now();
  let nfc = null;
  const isRepeat = createRepeatGuard(REPEAT_WINDOW_MS);

  const deliver = (uid) => {
    if (!running) return false;
    if (isRepeat(uid)) return false;
    lastUidAt = Date.now();
    onUid(uid);
    return true;
  };

  const session = {
    end: async () => {
      if (!running) return;
      running = false;
      if (!nfc) return;
      if (isIos) await iosCancel(nfc);
      else await androidUnlisten(nfc);
    },
  };

  const finish = async (state) => {
    if (!running) return;
    await session.end();
    if (current === session) current = null;
    onState(state);
  };

  async function runIos() {
    let open = false;
    let busyRetries = 1;
    while (running) {
      try {
        if (open) await nfc.default.restartTechnologyRequestIOS();
        else await nfc.default.requestTechnology(iosTechs(nfc), { alertMessage: message });
        open = true;
        const uid = await iosUid(nfc);
        await pause(uid && deliver(uid) ? IOS_AFTER_READ_PAUSE_MS : IOS_REPEAT_PAUSE_MS);
      } catch (error) {
        if (!running) return;
        const kind = kindOf(nfc, error);
        open = false;
        await iosCancel(nfc);
        const active = AppState.currentState === "active";
        if (kind === "timeout" && active && Date.now() - lastUidAt < IOS_AUTO_RESTART_WITHIN_MS) {
          await pause(IOS_RESTART_PAUSE_MS);
        } else if (kind === "busy" && active && busyRetries > 0) {
          busyRetries -= 1;
          await pause(IOS_BUSY_PAUSE_MS);
        } else {
          await finish(["off", "unsupported"].includes(kind) ? kind : "paused");
          return;
        }
      }
    }
  }

  async function runAndroid() {
    nfc.default.setEventListener(nfc.NfcEvents.StateChanged, (event) => {
      if (event?.state === "off") finish("off");
    });
    try {
      await androidListen(nfc, deliver);
    } catch {
      await finish("failed");
    }
  }

  (async () => {
    try {
      nfc = await available();
    } catch (error) {
      running = false;
      onState(error.kind);
      return;
    }
    if (!running) return;
    await endCurrent();
    if (!running) return;
    current = session;
    onState("reading");
    if (isIos) runIos();
    else runAndroid();
  })();

  return {
    stop: async () => {
      await session.end();
      if (current === session) current = null;
    },
    setMessage: (text) => {
      if (isIos && running && nfc) nfc.default.setAlertMessageIOS(text).catch(() => {});
    },
  };
}

export const nfcProblemText = (kind) => PROBLEMS[kind] || PROBLEMS.failed;
