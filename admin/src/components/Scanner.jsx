import { useEffect, useRef, useState } from "react";
import { cameraSupported, loadDetector } from "../lib/scanner.js";

const SCAN_INTERVAL_MS = 180;

const PROBLEMS = {
  insecure: {
    title: "The camera needs a secure page",
    text: "Browsers only allow the camera on https pages. Open the admin site at its https address, or use Search below.",
  },
  denied: {
    title: "Camera access is blocked",
    text: "Allow the camera for this site in your browser’s site settings, then try again. Search still works without it.",
  },
  nocamera: {
    title: "No camera found",
    text: "This device has no camera the browser can use. Use Search to check people in by name.",
  },
  failed: {
    title: "Could not start the scanner",
    text: "Something went wrong starting the camera or the QR reader. Try again, or use Search.",
  },
};

function problemFor(error) {
  if (error?.name === "NotAllowedError" || error?.name === "SecurityError") return "denied";
  if (error?.name === "NotFoundError" || error?.name === "OverconstrainedError") return "nocamera";
  return "failed";
}

// Mount to start the camera, unmount to stop it. `paused` keeps the preview live but
// stops reading, so a code still in frame is not submitted again while a result shows.
export function Scanner({ paused, onRead, onUseSearch }) {
  const videoRef = useRef(null);
  const onReadRef = useRef(onRead);
  const pausedRef = useRef(paused);
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState(() => ({ attempt: 0, phase: cameraSupported() ? "starting" : "insecure" }));

  useEffect(() => {
    onReadRef.current = onRead;
    pausedRef.current = paused;
  });

  useEffect(() => {
    if (!cameraSupported()) return undefined;
    let live = true;
    let stream = null;
    let timer = null;

    const stop = () => {
      window.clearTimeout(timer);
      stream?.getTracks().forEach((track) => track.stop());
      stream = null;
    };

    const loop = (detector) => {
      timer = window.setTimeout(async () => {
        if (!live) return;
        const video = videoRef.current;
        if (video && video.readyState >= 2 && !pausedRef.current && !document.hidden) {
          try {
            const codes = await detector.detect(video);
            const value = codes.find((code) => code.rawValue)?.rawValue;
            if (live && value && !pausedRef.current) onReadRef.current(value);
          } catch {
            // A frame that cannot be read (for example mid-resize) is skipped.
          }
        }
        if (live) loop(detector);
      }, SCAN_INTERVAL_MS);
    };

    (async () => {
      try {
        const [media, detector] = await Promise.all([
          navigator.mediaDevices.getUserMedia({
            audio: false,
            video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
          }),
          loadDetector(),
        ]);
        if (!live) {
          media.getTracks().forEach((track) => track.stop());
          return;
        }
        stream = media;
        const video = videoRef.current;
        video.srcObject = media;
        await video.play().catch(() => {});
        if (!live) return;
        setState({ attempt, phase: "running" });
        loop(detector);
      } catch (error) {
        if (live) setState({ attempt, phase: problemFor(error) });
      }
    })();

    return () => {
      live = false;
      stop();
    };
  }, [attempt]);

  const phase = state.attempt === attempt ? state.phase : "starting";
  const problem = PROBLEMS[phase];

  if (problem) {
    return (
      <div className="state-block state-error" role="alert">
        <strong>{problem.title}</strong>
        <p>{problem.text}</p>
        <div className="scanner-actions">
          {phase !== "insecure" && phase !== "nocamera" && (
            <button type="button" className="btn" onClick={() => setAttempt((n) => n + 1)}>
              Try again
            </button>
          )}
          <button type="button" className="btn btn-primary" onClick={onUseSearch}>
            Use search
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={`scanner${paused ? " is-paused" : ""}`}>
      <video ref={videoRef} playsInline muted aria-label="Camera preview" />
      <span className="scanner-frame" aria-hidden="true" />
      <p className="scanner-status" role="status">
        {phase === "starting" ? "Starting the camera…" : paused ? "Paused" : "Point the camera at a ticket QR code"}
      </p>
    </div>
  );
}
