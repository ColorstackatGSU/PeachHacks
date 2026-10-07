/* eslint-disable react/prop-types -- the project has no prop-types dependency and React 19 ignores propTypes */

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
      {optional && <span className="pf-optional"> (optional)</span>}
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
        <span className="pf-optional"> (optional, choose any)</span>
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
          <span className="pf-optional">{required ? ' (required)' : ' (optional)'}</span>
        </label>
      </div>
      <FieldMessage id={`${id}-msg`} error={error} />
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
