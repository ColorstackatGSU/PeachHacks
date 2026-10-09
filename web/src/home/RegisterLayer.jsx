import { Suspense, lazy, memo, startTransition, useCallback, useEffect, useRef, useState } from 'react';
import LoadBoundary from './LoadBoundary.jsx';
import { closeRegisterPanel, isRegisterPanelShown, registerPanelOpener } from './registerPanel.js';

const loadRegisterPanel = () => import('../forms/RegisterPanel.jsx');
const RegisterPanel = lazy(loadRegisterPanel);

export function preloadRegisterPanel() {
  loadRegisterPanel().catch(() => {});
}

const NAME_ID = 'register-panel-name';
const TITLE_ID = 'register-panel-title';
// Long enough for the tag to be back at rest and the panel off-screen.
const RELEASE_DELAY_MS = 520;
const FOCUSABLE = 'a[href], button, input, select, textarea, [tabindex]';

const loading = (
  <div className="register-panel-state" role="status">
    <span className="register-panel-spinner" aria-hidden="true" />
    <p className="register-panel-note">Loading the form…</p>
  </div>
);

// Reloading keeps #register in the URL, so the panel reopens by itself.
const loadFailed = (
  <div className="register-panel-state" role="alert">
    <p className="register-panel-note">We couldn’t load the form. Check your connection and try again.</p>
    <button type="button" className="register-panel-retry" onClick={() => window.location.reload()}>Try again</button>
  </div>
);

// Memoised so opening and closing, which re-render the layer, never re-render
// the whole form in the middle of the tow animation.
const PanelBody = memo(function PanelBody({ onDone }) {
  return (
    <LoadBoundary fallback={loadFailed}>
      <Suspense fallback={loading}>
        <RegisterPanel titleId={TITLE_ID} onClose={closeRegisterPanel} onDone={onDone} />
      </Suspense>
    </LoadBoundary>
  );
});

// The sign-up dialog revealed when the hero tag tows itself aside. Once opened
// it stays mounted, hidden, so closing it never throws away a half-filled form
// or a chosen resume; the forms also keep a draft of the typed answers in
// sessionStorage (see src/forms/hooks.js).
function RegisterLayer({ shown, tagCloseRef, returnFocusRef }) {
  const layerRef = useRef(null);
  const panelRef = useRef(null);
  const release = useRef(0);
  const [mounted, setMounted] = useState(shown);
  const [done, setDone] = useState(false);
  const [bodyKey, setBodyKey] = useState(0);
  const [wasShown, setWasShown] = useState(shown);
  const markDone = useCallback(() => setDone(true), []);

  if (shown !== wasShown) {
    setWasShown(shown);
    if (shown) {
      setMounted(true);
      // A finished sign-up is not a draft: the next visit starts a fresh form.
      if (done) {
        setDone(false);
        setBodyKey((key) => key + 1);
      }
    }
  }

  // The form is built ahead of time, out of sight, while the page is idle:
  // building it on the click would hold the tag still for a noticeable beat.
  useEffect(() => {
    if (mounted) return undefined;
    const whenIdle = window.requestIdleCallback ?? ((task) => setTimeout(task, 1500));
    const cancel = window.cancelIdleCallback ?? clearTimeout;
    const id = whenIdle(() => startTransition(() => setMounted(true)), { timeout: 4000 });
    return () => cancel(id);
  }, [mounted]);

  useEffect(() => {
    if (!shown) return undefined;
    const layer = layerRef.current;
    const panel = panelRef.current;
    const hero = document.getElementById('hero');
    const returnFocus = returnFocusRef.current;
    const behind = [...layer.parentElement.children].flatMap((child) => {
      if (child === layer) return [];
      return child.contains(hero) ? [...child.children].filter((inner) => !inner.contains(hero)) : [child];
    });

    if (window.scrollY !== 0) window.scrollTo({ top: 0, behavior: 'instant' });
    const scrollArea = panel.querySelector('.register-panel-scroll');

    // The page is held still by refusing the gestures that scroll it. Hiding
    // the root scrollbar instead would resize everything measured in vw.
    const holdPage = (event) => {
      if (!scrollArea.contains(event.target)) event.preventDefault();
    };
    const SCROLL_KEYS = ['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End'];
    // The rest of the page is hidden from assistive technology and kept out of
    // the Tab order by the trap below. `inert` would do both, but toggling it
    // restyles the whole document on the very frame the tag should start
    // moving. It is undone only after the tag is home again.
    clearTimeout(release.current);
    behind.forEach((element) => element.setAttribute('aria-hidden', 'true'));
    panel.focus({ preventScroll: true });

    // Scroll restoration and fragment jumps can still move a locked page.
    const onScroll = () => {
      if (isRegisterPanelShown() && window.scrollY !== 0) window.scrollTo({ top: 0, behavior: 'instant' });
    };

    const onKeyDown = (event) => {
      if (SCROLL_KEYS.includes(event.key) && !panel.contains(event.target)) event.preventDefault();
      if (event.key === 'Escape') {
        if (!event.defaultPrevented) closeRegisterPanel();
        return;
      }
      if (event.key !== 'Tab') return;
      const stops = [tagCloseRef.current, ...panel.querySelectorAll(FOCUSABLE)].filter((element) => (
        element && !element.disabled && element.tabIndex >= 0 && element.getClientRects().length > 0
      ));
      if (stops.length === 0) return;
      const first = stops[0];
      const last = stops[stops.length - 1];
      const active = document.activeElement;
      const escaped = active !== panel && !stops.includes(active);
      if (event.shiftKey ? (active === first || escaped) : (active === last || escaped)) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus();
      }
    };

    // An on-screen keyboard shrinks the visual viewport, not the layout one;
    // the layer follows it so the form scrolls above the keys.
    const viewport = window.visualViewport;
    let fittedHeight = 0;
    const fit = () => {
      if (Math.abs(viewport.scale - 1) > 0.01) {
        layer.style.removeProperty('--vv-height');
        layer.style.removeProperty('--vv-top');
        return;
      }
      // Only when a keyboard (or similar) has actually taken part of the
      // screen: setting these restyles the whole form.
      const covered = window.innerHeight - viewport.height > 1 || viewport.offsetTop > 0;
      if (!covered && fittedHeight === 0) return;
      layer.style.setProperty('--vv-height', `${viewport.height}px`);
      layer.style.setProperty('--vv-top', `${viewport.offsetTop}px`);
      if (viewport.height < fittedHeight && panel.contains(document.activeElement) && document.activeElement !== panel) {
        document.activeElement.scrollIntoView({ block: 'nearest' });
      }
      fittedHeight = viewport.height;
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('wheel', holdPage, { passive: false });
    window.addEventListener('touchmove', holdPage, { passive: false });
    document.addEventListener('keydown', onKeyDown);
    if (viewport) {
      fit();
      viewport.addEventListener('resize', fit);
      viewport.addEventListener('scroll', fit);
    }

    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('wheel', holdPage);
      window.removeEventListener('touchmove', holdPage);
      document.removeEventListener('keydown', onKeyDown);
      viewport?.removeEventListener('resize', fit);
      viewport?.removeEventListener('scroll', fit);
      layer.style.removeProperty('--vv-height');
      layer.style.removeProperty('--vv-top');
      const opener = registerPanelOpener();
      release.current = setTimeout(() => {
        behind.forEach((element) => element.removeAttribute('aria-hidden'));
        for (const target of [opener, returnFocus]) {
          if (!target || target === document.body || !target.isConnected) continue;
          target.focus({ preventScroll: true });
          if (document.activeElement === target) break;
        }
      }, RELEASE_DELAY_MS);
    };
  }, [shown, tagCloseRef, returnFocusRef]);

  return (
    <div className={shown ? 'register-layer is-open' : 'register-layer'} ref={layerRef}>
      <div className="register-panel" ref={panelRef} role="dialog" aria-labelledby={NAME_ID} tabIndex={-1}>
        <div className="register-panel-bar">
          <p className="register-panel-name" id={NAME_ID}>PeachHacks sign-up</p>
          <button type="button" className="register-panel-close" onClick={closeRegisterPanel}>
            <span aria-hidden="true">✕</span> Close
          </button>
        </div>
        <div className="register-panel-scroll">
          {mounted && <PanelBody key={bodyKey} onDone={markDone} />}
        </div>
      </div>
    </div>
  );
}

export default RegisterLayer;
