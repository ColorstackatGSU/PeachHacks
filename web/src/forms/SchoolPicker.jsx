import { useEffect, useMemo, useRef, useState } from 'react';
import { FieldLabel, FieldMessage, fieldId } from './fields.jsx';
import { findExact, loadSchools, searchSchools } from './schools.js';

const NOT_LISTED_LABEL = "My school isn't listed";

// List mode is an ARIA combobox: typing only filters, the committed value is always a listed name.
// Manual mode, a plain text input, is also the fallback when the list fails to load.
export default function SchoolPicker({ name = 'school', label = 'School', value, onChange, error }) {
  const id = fieldId(name);
  const listId = `${id}-list`;
  const inputRef = useRef(null);
  const listRef = useRef(null);

  const [text, setText] = useState(value);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [manual, setManual] = useState(false);
  const [index, setIndex] = useState(null);
  const [loadState, setLoadState] = useState('idle');

  const ensureLoaded = () => {
    if (loadState === 'loading' || loadState === 'ready') return;
    setLoadState('loading');
    loadSchools().then(
      (loaded) => {
        setIndex(loaded);
        setLoadState('ready');
      },
      () => {
        setLoadState('failed');
        setManual(true);
        setOpen(false);
      },
    );
  };

  const { results, total } = useMemo(
    () => (index && !manual ? searchSchools(index, text) : { results: [], total: 0 }),
    [index, manual, text],
  );

  const optionCount = results.length + 1;
  const notListedIndex = results.length;

  useEffect(() => {
    if (!open || activeIndex < 0) return;
    listRef.current?.querySelector(`#${CSS.escape(`${id}-opt-${activeIndex}`)}`)?.scrollIntoView({ block: 'nearest' });
  }, [open, activeIndex, id]);

  const commit = (school) => {
    setText(school);
    onChange(name, school);
    setOpen(false);
    setActiveIndex(-1);
  };

  const switchToManual = () => {
    setManual(true);
    setOpen(false);
    setActiveIndex(-1);
    onChange(name, text.trim());
    inputRef.current?.focus();
  };

  const switchToList = () => {
    setManual(false);
    ensureLoaded();
    const exact = index ? findExact(index, text) : null;
    onChange(name, exact ?? '');
    if (exact) setText(exact);
    setOpen(true);
    inputRef.current?.focus();
  };

  const choose = (optionIndex) => {
    if (optionIndex === notListedIndex) switchToManual();
    else if (results[optionIndex]) commit(results[optionIndex]);
  };

  const handleChange = (event) => {
    const next = event.target.value;
    setText(next);
    if (manual) {
      onChange(name, next);
      return;
    }
    // Typed text is only a filter; nothing is selected until an option is picked.
    if (value) onChange(name, '');
    setOpen(true);
    setActiveIndex(next.trim() ? 0 : -1);
  };

  const handleKeyDown = (event) => {
    if (manual) return;

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      ensureLoaded();
      if (!open) {
        setOpen(true);
        setActiveIndex(event.key === 'ArrowDown' ? 0 : optionCount - 1);
        return;
      }
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setActiveIndex((current) => (current < 0 && step < 0 ? optionCount - 1 : (current + step + optionCount) % optionCount));
    } else if (event.key === 'Enter') {
      if (open && activeIndex >= 0) {
        event.preventDefault();
        choose(activeIndex);
      }
    } else if (event.key === 'Escape') {
      if (open) {
        event.preventDefault();
        setOpen(false);
        setActiveIndex(-1);
      } else if (text) {
        setText('');
        if (value) onChange(name, '');
      }
    }
  };

  const handleBlur = () => {
    if (manual) {
      const trimmed = text.trim();
      if (trimmed !== text) {
        setText(trimmed);
        onChange(name, trimmed);
      }
      return;
    }
    setOpen(false);
    setActiveIndex(-1);
    // Typing a listed name in full counts as picking it.
    const exact = index && text.trim() ? findExact(index, text) : null;
    if (exact && exact !== value) commit(exact);
  };

  const showList = open && !manual;
  const activeId = showList && activeIndex >= 0 ? `${id}-opt-${activeIndex}` : undefined;

  let hint = 'Start typing, then pick your school from the list.';
  if (manual) {
    hint = loadState === 'failed'
      ? "We couldn't load the school list, so type your school's full name."
      : "Type your school's full name.";
  } else if (value) {
    hint = null;
  } else if (text.trim() && !open) {
    hint = `Pick a school from the list, or choose "${NOT_LISTED_LABEL}".`;
  }

  let status = '';
  if (showList) {
    if (loadState === 'loading') status = 'Loading schools…';
    else if (text.trim() && results.length === 0) status = 'No schools match.';
    else if (text.trim()) status = total > results.length ? `Showing ${results.length} of ${total} matches. Keep typing to narrow it down.` : `${total} ${total === 1 ? 'match' : 'matches'}.`;
  }

  const comboboxProps = manual
    ? {}
    : {
        role: 'combobox',
        'aria-expanded': showList,
        'aria-controls': listId,
        'aria-autocomplete': 'list',
        'aria-activedescendant': activeId,
      };

  return (
    <div className="pf-field pf-field-wide">
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <div className="pf-combo">
        <input
          ref={inputRef}
          className="pf-input"
          id={id}
          name={name}
          type="text"
          value={text}
          placeholder={manual ? '' : 'Search schools'}
          autoComplete="off"
          autoCapitalize="words"
          autoCorrect="off"
          spellCheck={false}
          maxLength={255}
          required
          enterKeyHint={manual ? undefined : 'search'}
          aria-invalid={error ? 'true' : undefined}
          aria-describedby={`${id}-msg`}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          onFocus={() => {
            if (manual) return;
            ensureLoaded();
            if (!value) setOpen(true);
          }}
          onClick={() => {
            if (!manual) setOpen(true);
          }}
          onBlur={handleBlur}
          {...comboboxProps}
        />
        {!manual && (
          // A press in the popover must not blur the input, or the list closes before the click lands.
          // Keyboard users drive the list from the input through aria-activedescendant.
          // eslint-disable-next-line jsx-a11y/no-static-element-interactions
          <div className="pf-popover" hidden={!showList} onMouseDown={(event) => event.preventDefault()}>
            {status && <p className="pf-combo-status" aria-hidden="true">{status}</p>}
            <ul className="pf-listbox" id={listId} role="listbox" aria-label="Schools" ref={listRef}>
              {results.map((school, optionIndex) => (
                // eslint-disable-next-line jsx-a11y/click-events-have-key-events
                <li
                  key={school}
                  id={`${id}-opt-${optionIndex}`}
                  role="option"
                  aria-selected={school === value}
                  className={`pf-option${optionIndex === activeIndex ? ' is-active' : ''}`}
                  onClick={() => choose(optionIndex)}
                >
                  {school}
                </li>
              ))}
              {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events */}
              <li
                id={`${id}-opt-${notListedIndex}`}
                role="option"
                aria-selected={false}
                className={`pf-option pf-option-other${notListedIndex === activeIndex ? ' is-active' : ''}`}
                onClick={() => choose(notListedIndex)}
              >
                {NOT_LISTED_LABEL}
              </li>
            </ul>
          </div>
        )}
      </div>
      <p className="pf-sr-only" role="status">{status}</p>
      <FieldMessage id={`${id}-msg`} error={error} hint={hint} />
      {manual && (
        <button type="button" className="pf-link-button" onClick={switchToList}>
          Search the school list instead
        </button>
      )}
    </div>
  );
}
