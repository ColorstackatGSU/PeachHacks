import { useCallback, useEffect, useRef, useState } from 'react';
import { FIELD_RULES, focusFirstInvalid } from './validation.js';

const DRAFT_PREFIX = 'peachhacks:draft:';
// A File cannot be stored, and the honeypot must never be filled in for anyone.
const NEVER_STORED = ['resume', 'website'];

// Only answers of the type the form expects come back, so a draft written by
// an older version of the form cannot break this one.
function readDraft(key, defaults) {
  const draft = {};
  try {
    const stored = JSON.parse(window.sessionStorage.getItem(DRAFT_PREFIX + key));
    for (const [name, initial] of Object.entries(defaults)) {
      const value = stored?.[name];
      if (NEVER_STORED.includes(name)) continue;
      if (Array.isArray(initial)) {
        if (Array.isArray(value) && value.every((item) => typeof item === 'string')) draft[name] = value;
      } else if (typeof value === typeof initial && (typeof value === 'string' || typeof value === 'boolean')) {
        draft[name] = value;
      }
    }
  } catch {
    // No storage, or nothing usable in it: the form starts empty.
  }
  return draft;
}

function writeDraft(key, values) {
  try {
    const kept = Object.fromEntries(Object.entries(values).filter(([name]) => !NEVER_STORED.includes(name)));
    window.sessionStorage.setItem(DRAFT_PREFIX + key, JSON.stringify(kept));
  } catch {
    // Storage can be unavailable (private mode) or full; the answers stay on the page.
  }
}

function removeDraft(key) {
  try {
    window.sessionStorage.removeItem(DRAFT_PREFIX + key);
  } catch {
    // Nothing was stored.
  }
}

// The answers are kept in sessionStorage under `draftKey` as they are typed and
// restored when the form next mounts in this tab; `overrides` then win over the
// draft. fail(fieldErrors, message) shows both and moves focus to the first
// invalid field. checkOnBlur(name) is an onBlur handler that applies the
// field's rule to what was typed; an empty field waits for submit.
export function useFormFields(defaults, draftKey, overrides = null) {
  const formRef = useRef(null);
  const [values, setValues] = useState(() => ({ ...defaults, ...readDraft(draftKey, defaults), ...overrides }));
  const drafting = useRef(true);
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [focusRequest, setFocusRequest] = useState(0);

  useEffect(() => {
    if (focusRequest > 0) focusFirstInvalid(formRef.current);
  }, [focusRequest]);

  useEffect(() => {
    if (drafting.current) writeDraft(draftKey, values);
  }, [draftKey, values]);

  const clearDraft = () => {
    drafting.current = false;
    removeDraft(draftKey);
  };

  const checkOnBlur = (name) => () => {
    const value = values[name];
    if (!value.trim()) return;
    const problem = FIELD_RULES[name](value);
    if (problem) setErrors((current) => ({ ...current, [name]: problem }));
  };

  const setValue = (name, value) => {
    setValues((current) => ({ ...current, [name]: value }));
    setErrors((current) => {
      if (!current[name]) return current;
      const next = { ...current };
      delete next[name];
      return next;
    });
  };

  const fail = (fieldErrors, message) => {
    setErrors(fieldErrors);
    setFormError(message);
    setFocusRequest((count) => count + 1);
  };

  return { formRef, values, setValues, errors, setErrors, formError, setFormError, setValue, fail, checkOnBlur, clearDraft };
}

// Moves focus to the heading on the first render where `settled` holds after
// requestFocus() was called, so a view the visitor did not ask for never steals focus.
export function useFocusOnChange(settled, initiallyRequested = false) {
  const headingRef = useRef(null);
  const moveFocus = useRef(initiallyRequested);

  useEffect(() => {
    if (!moveFocus.current || !settled) return;
    moveFocus.current = false;
    headingRef.current?.focus();
  });

  const requestFocus = useCallback(() => {
    moveFocus.current = true;
  }, []);

  return { headingRef, requestFocus };
}
