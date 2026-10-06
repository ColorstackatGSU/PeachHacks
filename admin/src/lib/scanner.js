let detectorPromise = null;

// The browser's own BarcodeDetector is used where it can read QR codes (Android, macOS).
// Elsewhere (iOS Safari, Chrome on Windows) the same API comes from the `barcode-detector`
// ponyfill, downloaded only then, with its WebAssembly file served from this site
// instead of the package's default third-party CDN.
async function createDetector() {
  if ("BarcodeDetector" in window) {
    try {
      const formats = await window.BarcodeDetector.getSupportedFormats();
      if (formats.includes("qr_code")) return new window.BarcodeDetector({ formats: ["qr_code"] });
    } catch {
      // Fall through to the ponyfill.
    }
  }
  const [{ BarcodeDetector, prepareZXingModule }, { default: wasmUrl }] = await Promise.all([
    import("barcode-detector/ponyfill"),
    import("zxing-wasm/reader/zxing_reader.wasm?url"),
  ]);
  prepareZXingModule({
    overrides: { locateFile: (path, prefix) => (path.endsWith(".wasm") ? wasmUrl : prefix + path) },
  });
  return new BarcodeDetector({ formats: ["qr_code"] });
}

export function loadDetector() {
  if (!detectorPromise) {
    detectorPromise = createDetector().catch((error) => {
      detectorPromise = null;
      throw error;
    });
  }
  return detectorPromise;
}

export const cameraSupported = () =>
  typeof window !== "undefined" && window.isSecureContext && Boolean(navigator.mediaDevices?.getUserMedia);

let audioContext = null;

// Browsers only allow sound after a tap, so this is called from the button that turns
// scanning on; later beeps then work without touching the screen.
export function primeFeedback() {
  try {
    const Context = window.AudioContext || window.webkitAudioContext;
    if (!Context) return;
    if (!audioContext) audioContext = new Context();
    if (audioContext.state === "suspended") audioContext.resume();
  } catch {
    audioContext = null;
  }
}

function beep(frequency, duration, startAt = 0) {
  if (!audioContext || audioContext.state !== "running") return;
  const oscillator = audioContext.createOscillator();
  const gain = audioContext.createGain();
  oscillator.frequency.value = frequency;
  gain.gain.value = 0.12;
  oscillator.connect(gain).connect(audioContext.destination);
  const start = audioContext.currentTime + startAt;
  oscillator.start(start);
  oscillator.stop(start + duration);
}

export function signal(kind) {
  try {
    if (kind === "success") {
      navigator.vibrate?.(80);
      beep(880, 0.12);
    } else {
      navigator.vibrate?.([70, 60, 70]);
      beep(330, 0.14);
      beep(330, 0.14, 0.2);
    }
  } catch {
    // Feedback is a nicety; a browser that refuses it must not break scanning.
  }
}
