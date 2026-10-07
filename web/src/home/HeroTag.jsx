import { useCallback, useEffect, useRef, useSyncExternalStore } from 'react';
import { animateSingleValue, motion, useDragControls, useMotionValue, useMotionValueEvent, useSpring, useTransform, useVelocity } from 'motion/react';
import { HeroCardContent } from './HeroCard.jsx';
import { closeRegisterPanel, openRegisterPanel, openedOnLoad, useRegisterPanelShown } from './registerPanel.js';
import { useReducedMotion } from './reducedMotion.js';

const TAG_CLICK_SLOP = 6;
const TAG_MAX_TILT = 6;
const TOW_ANGLE = (26 * Math.PI) / 180;
const TAG_TOW_SPRING = { type: 'spring', stiffness: 120, damping: 13 };
const TAG_PEEK = 72;
const ROPE_TUCK = 18;
// No overshoot: while the tag springs past its spot the rope must stay on the panel.
const ROPE_TIE_EASE = { duration: 0.16, ease: 'easeOut' };
const PANEL_CLEARANCE = 48;
// The panel follows the tag in on its own eased path rather than being bolted
// to it, so the tag's bounce at the end of its run does not shake the form.
const PANEL_IN = { duration: 0.5, ease: [0.22, 1, 0.36, 1] };
const PANEL_OUT = { duration: 0.3, ease: [0.55, 0, 1, 0.45] };
const TAG_PULL_OPEN_OFFSET = 150;
const TAG_OFFSCREEN = 120;
const TAG_RELEASE_OFFSET = 80;
const TAG_RELEASE_VELOCITY = 400;
// Where the hero stops pinning (see styles.css); there the sign-up panel is a
// full-screen sheet and the parked tag sits out of sight behind it.
const SHEET_QUERY = '(max-width: 768px), (max-height: 520px)';

function subscribeToSheet(listener) {
  const query = window.matchMedia(SHEET_QUERY);
  query.addEventListener('change', listener);
  return () => query.removeEventListener('change', listener);
}
const isSheet = () => window.matchMedia(SHEET_QUERY).matches;
const notOnServer = () => false;
// Only the tag at rest is rendered at build time, and its markup does not depend on these.
const viewport = () => (typeof window === 'undefined' ? { width: 1440, height: 900 } : { width: window.innerWidth, height: window.innerHeight });

export default function HeroTag({ registerButtonRef, tagCloseRef }) {
  const parked = useRegisterPanelShown();
  const sheet = useSyncExternalStore(subscribeToSheet, isSheet, notOnServer);
  const reduceMotion = useReducedMotion();
  const tagRef = useRef(null);
  const draggedFar = useRef(false);
  const towing = useRef([]);
  const settled = useRef(false);
  const dragControls = useDragControls();
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  const tilt = useSpring(useTransform(useVelocity(x), [-600, 600], [-TAG_MAX_TILT, TAG_MAX_TILT]), { stiffness: 180, damping: 12 });
  const ropeTie = useRef({ panel: 0, eyeletX: 0, eyeletY: 0 });
  const tied = useMotionValue(0);
  const panelX = useMotionValue(-viewport().width * 2);
  const panelMove = useRef(null);
  const towTransform = useTransform(() => {
    const reach = viewport().width * 0.5;
    const restX = -reach * Math.cos(TOW_ANGLE);
    const restY = -reach * Math.sin(TOW_ANGLE);
    const blend = tied.get();
    const { panel, eyeletX, eyeletY } = ropeTie.current;
    const shiftX = eyeletX * blend;
    const shiftY = eyeletY * blend;
    const towX = (restX - x.get()) * (1 - blend) + (panel + panelX.get() - x.get() - eyeletX) * blend;
    const towY = restY * (1 - blend) - y.get();
    let bend = ((Math.atan2(towY, towX) - Math.atan2(restY, restX)) * 180) / Math.PI;
    if (bend > 180) bend -= 360;
    if (bend < -180) bend += 360;
    return `translate(${shiftX.toFixed(2)}px, ${shiftY.toFixed(2)}px) rotate(${(26 + bend - tilt.get()).toFixed(3)}deg) scaleX(${(Math.hypot(towX, towY) / reach).toFixed(4)})`;
  });
  const { width, height } = viewport();
  const limits = { left: -width * 0.3, right: width * 0.3, top: -height * 0.2, bottom: height * 0.2 };
  const parkedLimits = { ...limits, left: 0, right: width };
  const setTagState = (add, remove) => {
    tagRef.current?.classList.remove(remove);
    if (add) tagRef.current?.classList.add(add);
  };

  // Parked, a strip of the tag stays on screen at the right edge of the hero.
  const restLeft = () => {
    const tag = tagRef.current;
    return tag.offsetParent.getBoundingClientRect().left + tag.offsetLeft + tag.firstElementChild.offsetLeft;
  };
  const parkedX = useCallback(() => {
    const edge = tagRef.current.closest('.intro').getBoundingClientRect().right;
    return (isSheet() ? edge + TAG_OFFSCREEN : edge - TAG_PEEK) - restLeft();
  }, []);

  // The panel slides in from beyond the left edge as the tag leaves, and back
  // out as it returns. Written straight to the element: a custom property on
  // the root would restyle the whole page on every frame.
  useMotionValueEvent(panelX, 'change', (offset) => {
    const panel = document.querySelector('.register-panel');
    if (panel) panel.style.transform = `translate3d(${offset.toFixed(1)}px, 0, 0)`;
  });
  const panelAway = () => {
    const panel = document.querySelector('.register-panel');
    return panel ? -(panel.offsetLeft + panel.offsetWidth + PANEL_CLEARANCE) : -window.innerWidth;
  };
  const movePanel = useCallback((open, instant) => {
    const away = panelAway();
    panelMove.current?.stop();
    if (instant) {
      panelX.jump(open ? 0 : away);
      return;
    }
    if (panelX.get() < away) panelX.jump(away);
    panelMove.current = animateSingleValue(panelX, open ? 0 : away, open ? PANEL_IN : PANEL_OUT);
  }, [panelX]);

  // While the panel is open the rope runs from the tag's eyelet to the panel's
  // right edge, its tapered end tucked under the panel. offsetLeft/offsetWidth
  // ignore the panel's own transform, which getBoundingClientRect would not.
  const tieRopeToPanel = useCallback(() => {
    const panel = document.querySelector('.register-panel');
    const card = tagRef.current.firstElementChild;
    const rope = card.querySelector('.banner-tow-line');
    const eyelet = card.querySelector('.hero-tag-eyelet');
    if (!panel) return;
    ropeTie.current = {
      panel: panel.offsetLeft + panel.offsetWidth - ROPE_TUCK - restLeft(),
      eyeletX: eyelet.offsetLeft + eyelet.offsetWidth / 2,
      eyeletY: eyelet.offsetTop + eyelet.offsetHeight / 2 - (rope.offsetTop + rope.offsetHeight / 2),
    };
  }, []);

  // The tag drives itself with the motion values the drag uses, so the tow
  // line and the tilt follow along and a grab mid-flight simply takes over.
  const towTo = useCallback((targetX, instant) => {
    towing.current.forEach((animation) => animation.stop());
    const tag = tagRef.current;
    tag.classList.remove('is-dragging');
    if (instant) {
      towing.current = [];
      tag.classList.remove('is-springing');
      x.jump(targetX);
      y.jump(0);
      return;
    }
    tag.classList.add('is-springing');
    const animations = [animateSingleValue(x, targetX, TAG_TOW_SPRING), animateSingleValue(y, 0, TAG_TOW_SPRING)];
    towing.current = animations;
    Promise.all(animations).then(() => {
      if (towing.current === animations) tag.classList.remove('is-springing');
    });
  }, [x, y]);

  useEffect(() => {
    const first = !settled.current;
    settled.current = true;
    if (first && !parked) return undefined;
    const instant = reduceMotion || (first && openedOnLoad);
    const tie = parked && !sheet ? 1 : 0;
    const go = () => {
      if (tie) tieRopeToPanel();
      if (instant) tied.jump(tie);
      else animateSingleValue(tied, tie, ROPE_TIE_EASE);
      movePanel(parked, instant);
      towTo(parked ? parkedX() : 0, instant);
    };
    if (!parked) {
      go();
      return undefined;
    }
    // Opening restyles most of the page (the form becomes visible, the rest
    // goes inert) and the form has to be painted for the first time. For two
    // frames the form sits in place, all but transparent, so that work is
    // done before anything moves; then it starts from off-screen with the tag.
    const panel = document.querySelector('.register-panel');
    const stage = (staged) => {
      if (panel) panel.style.opacity = staged ? '0.01' : '';
    };
    let waiting = 0;
    if (instant) go();
    else {
      panelMove.current?.stop();
      stage(true);
      panelX.jump(0);
      waiting = requestAnimationFrame(() => {
        waiting = requestAnimationFrame(() => {
          waiting = 0;
          stage(false);
          panelX.jump(panelAway());
          go();
        });
      });
    }
    const onResize = () => {
      if (waiting) return;
      tieRopeToPanel();
      towTo(parkedX(), true);
    };
    window.addEventListener('resize', onResize);
    return () => {
      if (waiting) {
        cancelAnimationFrame(waiting);
        stage(false);
        panelX.jump(panelAway());
      }
      window.removeEventListener('resize', onResize);
    };
  }, [parked, sheet, reduceMotion, parkedX, towTo, tieRopeToPanel, movePanel, tied, panelX]);

  return (
    <motion.div
      ref={tagRef}
      className={reduceMotion ? 'hero-tag' : 'hero-tag is-draggable'}
      style={{ x, y, rotate: tilt }}
      drag={!reduceMotion}
      dragListener={false}
      dragControls={dragControls}
      dragSnapToOrigin={!parked}
      dragMomentum={!parked}
      dragElastic={0.22}
      dragConstraints={parked ? parkedLimits : limits}
      dragTransition={{ bounceStiffness: 120, bounceDamping: 9 }}
      onPointerDown={(event) => {
        draggedFar.current = false;
        if (!reduceMotion && event.button === 0) dragControls.start(event);
      }}
      onDragStart={() => {
        window.getSelection()?.removeAllRanges();
        setTagState('is-dragging', 'is-springing');
      }}
      onDrag={(event, info) => {
        if (Math.hypot(info.offset.x, info.offset.y) > TAG_CLICK_SLOP) draggedFar.current = true;
      }}
      onDragEnd={(event, info) => {
        if (!parked && info.offset.x > TAG_PULL_OPEN_OFFSET) {
          // Carry on to the parking spot now, rather than starting back to rest
          // for the frame or two before the panel state arrives.
          towTo(parkedX(), false);
          openRegisterPanel(registerButtonRef.current);
        }
        else if (!parked) setTagState('is-springing', 'is-dragging');
        else if (info.offset.x < -TAG_RELEASE_OFFSET || info.velocity.x < -TAG_RELEASE_VELOCITY) closeRegisterPanel();
        else towTo(parkedX(), false);
      }}
      onDragTransitionEnd={() => setTagState(null, 'is-springing')}
      onClickCapture={(event) => {
        if (!draggedFar.current) return;
        draggedFar.current = false;
        event.preventDefault();
        event.stopPropagation();
      }}
    >
      <div className="hero-card">
        <motion.span className="banner-tow-line" aria-hidden="true" style={{ transform: towTransform }} />
        <span className="hero-tag-eyelet" aria-hidden="true" />
        {parked && !sheet && (
          <button type="button" className="tag-close" ref={tagCloseRef} aria-label="Close the sign-up form" onClick={closeRegisterPanel}>
            <span className="tag-close-mark" aria-hidden="true">✕</span>
          </button>
        )}
        <HeroCardContent registerButtonRef={registerButtonRef} inert={parked} />
      </div>
    </motion.div>
  );
}
