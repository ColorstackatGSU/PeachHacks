import React, { Component, Suspense, lazy, useCallback, useEffect, useRef, useState } from 'react';
import { closeRegisterPanel, isRegisterPanelShown, registerPanelOpener } from './registerPanel.js';

const loadRegisterPanel = () => import('../forms/RegisterPanel.jsx');
const RegisterPanel = lazy(loadRegisterPanel);

export function preloadRegisterPanel() {
  loadRegisterPanel().catch(() => {});
}

const NAME_ID = 'register-panel-name';
const TITLE_ID = 'register-panel-title';
const FOCUSABLE = 'a[href], button, input, select, textarea, [tabindex]';

class LoadBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { failed: false };
  }

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}

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

// The sign-up dialog revealed when the hero tag tows itself aside. Once opened
// it stays mounted, hidden, so closing it never throws away a half-filled form;
// the draft lives only in this page's memory.
function RegisterLayer({ shown, tagCloseRef, returnFocusRef }) {
  const layerRef = useRef(null);
  const panelRef = useRef(null);
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

  useEffect(() => {
    if (!shown) return undefined;
    const layer = layerRef.current;
    const panel = panelRef.current;
    const root = document.documentElement;
    const hero = document.getElementById('hero');
    const returnFocus = returnFocusRef.current;
    const behind = [...layer.parentElement.children].flatMap((child) => {
      if (child === layer) return [];
      return child.contains(hero) ? [...child.children].filter((inner) => !inner.contains(hero)) : [child];
    });

    window.scrollTo({ top: 0, behavior: 'instant' });
    root.classList.add('register-open');
    behind.forEach((element) => { element.inert = true; });
    panel.focus({ preventScroll: true });

    // Scroll restoration and fragment jumps can still move a locked page.
    const onScroll = () => {
      if (isRegisterPanelShown() && window.scrollY !== 0) window.scrollTo({ top: 0, behavior: 'instant' });
    };

    const onKeyDown = (event) => {
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
      layer.style.setProperty('--vv-height', `${viewport.height}px`);
      layer.style.setProperty('--vv-top', `${viewport.offsetTop}px`);
      if (viewport.height < fittedHeight && panel.contains(document.activeElement) && document.activeElement !== panel) {
        document.activeElement.scrollIntoView({ block: 'nearest' });
      }
      fittedHeight = viewport.height;
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    document.addEventListener('keydown', onKeyDown);
    if (viewport) {
      fit();
      viewport.addEventListener('resize', fit);
      viewport.addEventListener('scroll', fit);
    }

    return () => {
      window.removeEventListener('scroll', onScroll);
      document.removeEventListener('keydown', onKeyDown);
      viewport?.removeEventListener('resize', fit);
      viewport?.removeEventListener('scroll', fit);
      layer.style.removeProperty('--vv-height');
      layer.style.removeProperty('--vv-top');
      root.classList.remove('register-open');
      behind.forEach((element) => { element.inert = false; });
      for (const target of [registerPanelOpener(), returnFocus]) {
        if (!target || target === document.body || !target.isConnected) continue;
        target.focus({ preventScroll: true });
        if (document.activeElement === target) break;
      }
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
          {mounted && (
            <LoadBoundary fallback={loadFailed}>
              <Suspense fallback={loading}>
                <RegisterPanel key={bodyKey} titleId={TITLE_ID} onClose={closeRegisterPanel} onDone={markDone} />
              </Suspense>
            </LoadBoundary>
          )}
        </div>
      </div>
    </div>
  );
}

export default RegisterLayer;
