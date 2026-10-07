/* eslint-disable react/prop-types -- the project has no prop-types dependency and React 19 ignores propTypes */
import { useRef } from 'react';
import { formatFileSize } from './validation.js';

export function fieldId(name) {
  return `pf-${name.replace(/\./g, '-')}`;
}

// Always renders and reserves one line, so showing an error never pushes the form down.
export function FieldMessage({ id, error, hint }) {
  return (
    <p className="pf-msg" id={id}>
      {error ? (
        <span className="pf-error">
          <span className="pf-error-mark" aria-hidden="true">!</span>
          {error}
        </span>
      ) : (
        hint || null
      )}
    </p>
  );
}

export function FieldLabel({ htmlFor, children, optional }) {
  return (
    <label className="pf-label" htmlFor={htmlFor}>
      {children}
      {!optional && <span className="pf-required" aria-hidden="true"> *</span>}
    </label>
  );
}

export function TextField({ name, label, value, onChange, error, hint, optional = false, wide = false, ...inputProps }) {
  const id = fieldId(name);
  return (
    <div className={`pf-field${wide ? ' pf-field-wide' : ''}`}>
      <FieldLabel htmlFor={id} optional={optional}>{label}</FieldLabel>
      <input
        className="pf-input"
        id={id}
        name={name}
        type="text"
        value={value}
        onChange={(event) => onChange(name, event.target.value)}
        required={!optional}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={`${id}-msg`}
        {...inputProps}
      />
      <FieldMessage id={`${id}-msg`} error={error} hint={hint} />
    </div>
  );
}

// `options` is a list of strings (label doubles as value) or { value, label }.
export function SelectField({
  name, label, value, onChange, options, error, hint, optional = false, wide = false,
  placeholder = 'Select…', pinned = null, ...selectProps
}) {
  const id = fieldId(name);
  const toOption = (option) => (typeof option === 'string' ? { value: option, label: option } : option);
  return (
    <div className={`pf-field${wide ? ' pf-field-wide' : ''}`}>
      <FieldLabel htmlFor={id} optional={optional}>{label}</FieldLabel>
      <select
        className="pf-input pf-select"
        id={id}
        name={name}
        value={value}
        onChange={(event) => onChange(name, event.target.value)}
        required={!optional}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={`${id}-msg`}
        {...selectProps}
      >
        <option value="">{placeholder}</option>
        {pinned && (
          <>
            <option value={pinned.value}>{pinned.label}</option>
            <option disabled value="-">──────────</option>
          </>
        )}
        {options.map(toOption).map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
      <FieldMessage id={`${id}-msg`} error={error} hint={hint} />
    </div>
  );
}

export function CheckboxGroup({ name, legend, options, value, onChange, hint, error }) {
  const id = fieldId(name);
  const toggle = (option, checked) => {
    onChange(name, checked ? [...value, option] : value.filter((item) => item !== option));
  };
  return (
    <fieldset className="pf-field pf-field-wide pf-group" aria-describedby={`${id}-msg`}>
      <legend className="pf-label">
        {legend}
      </legend>
      <div className="pf-chips">
        {options.map((option, index) => (
          <label className="pf-chip" key={option}>
            <input
              type="checkbox"
              id={index === 0 ? id : undefined}
              name={name}
              value={option}
              checked={value.includes(option)}
              onChange={(event) => toggle(option, event.target.checked)}
              aria-invalid={error && index === 0 ? 'true' : undefined}
            />
            <span>{option}</span>
          </label>
        ))}
      </div>
      <FieldMessage id={`${id}-msg`} error={error} hint={hint} />
    </fieldset>
  );
}

export function ConsentCheckbox({ name, checked, onChange, error, required = false, children }) {
  const id = fieldId(name);
  return (
    <div className="pf-field pf-field-wide">
      <div className="pf-consent">
        <input
          type="checkbox"
          id={id}
          name={name}
          checked={checked}
          onChange={(event) => onChange(name, event.target.checked)}
          required={required}
          aria-invalid={error ? 'true' : undefined}
          aria-describedby={`${id}-msg`}
        />
        <label htmlFor={id}>
          {children}
          {required && <span className="pf-required" aria-hidden="true"> *</span>}
        </label>
      </div>
      <FieldMessage id={`${id}-msg`} error={error} />
    </div>
  );
}

// `file` is the chosen File or null. onChoose(file) and onRemove() leave validation to the form.
// The native input stays in the page (visually hidden inside its label) so it keeps
// keyboard, screen reader and phone file-picker behaviour.
export function ResumeField({ name, file, onChoose, onRemove, error, hint }) {
  const id = fieldId(name);
  const inputRef = useRef(null);
  const handleChange = (event) => {
    const chosen = event.target.files?.[0] ?? null;
    // Cleared so that picking the same file again still fires a change event.
    event.target.value = '';
    if (chosen) onChoose(chosen);
  };
  const handleRemove = () => {
    onRemove();
    inputRef.current?.focus();
  };
  return (
    <div className="pf-field pf-field-wide">
      <FieldLabel htmlFor={id} optional>Resume</FieldLabel>
      <div className="pf-file">
        <label className="pf-file-button">
          <input
            ref={inputRef}
            className="pf-file-input"
            type="file"
            id={id}
            name={name}
            accept="application/pdf,.pdf"
            onChange={handleChange}
            aria-invalid={error ? 'true' : undefined}
            aria-describedby={`${id}-msg${file ? ` ${id}-chosen` : ''}`}
          />
          <span>{file ? 'Choose a different PDF' : 'Choose a PDF'}</span>
        </label>
        {file && (
          <p className="pf-file-chosen" id={`${id}-chosen`}>
            <span className="pf-file-name">{file.name}</span>
            <span className="pf-file-size">{formatFileSize(file.size)}</span>
            <button type="button" className="pf-link-button" onClick={handleRemove} aria-label={`Remove ${file.name}`}>
              Remove
            </button>
          </p>
        )}
      </div>
      <FieldMessage id={`${id}-msg`} error={error} hint={hint} />
    </div>
  );
}

// Bots fill every input they find; people never see this one.
export function Honeypot({ value, onChange }) {
  return (
    <div className="pf-trap" aria-hidden="true">
      <label htmlFor="pf-website">Leave this field empty</label>
      <input
        type="text"
        id="pf-website"
        name="website"
        tabIndex={-1}
        autoComplete="off"
        value={value}
        onChange={(event) => onChange('website', event.target.value)}
      />
    </div>
  );
}

export function SubmitButton({ pending, children, pendingLabel }) {
  return (
    <button type="submit" className="pf-button" disabled={pending} aria-busy={pending || undefined}>
      {pending ? pendingLabel : children}
    </button>
  );
}
