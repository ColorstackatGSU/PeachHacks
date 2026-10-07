import { useState } from 'react';
import { submitPreRegistration } from './api.js';
import { Honeypot, SubmitButton, TextField } from './fields.jsx';
import { useFocusOnChange, useFormFields } from './hooks.js';
import { Card, FormAlert } from './PageShell.jsx';
import SchoolPicker from './SchoolPicker.jsx';
import { anotherLookMessage, apiFailure, schoolEmailHint, validateIdentity } from './validation.js';

const INITIAL_VALUES = { firstName: '', lastName: '', email: '', school: '', schoolEmail: '', website: '' };
const FIELD_NAMES = Object.keys(INITIAL_VALUES);

// Lives in the homepage's sign-up panel. `notice` explains why this form is
// showing in place of another one, and `initialValues` carries over what was
// already typed there. onDone() fires once the pre-registration is saved.
export default function PreRegisterForm({ titleId, onClose, onDone, notice = null, initialValues = null }) {
  const form = useFormFields(INITIAL_VALUES, 'pre-register', initialValues);
  const { formRef, values, errors, formError, setFormError, setValue, checkOnBlur } = form;
  const [status, setStatus] = useState('idle');
  const { headingRef, requestFocus } = useFocusOnChange(status !== 'submitting', Boolean(notice));

  const fail = (fieldErrors, message) => {
    setStatus('idle');
    form.fail(fieldErrors, message);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (status === 'submitting') return;

    const clientErrors = validateIdentity(values);
    const errorCount = Object.keys(clientErrors).length;
    if (errorCount > 0) {
      fail(clientErrors, anotherLookMessage(errorCount));
      return;
    }

    setStatus('submitting');
    setFormError(null);

    try {
      await submitPreRegistration({
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        email: values.email.trim(),
        school: values.school.trim(),
        schoolEmail: values.schoolEmail.trim(),
        website: values.website,
      });
      form.clearDraft();
      requestFocus();
      setStatus('success');
      onDone();
    } catch (error) {
      fail(...apiFailure(error, FIELD_NAMES));
    }
  };

  if (status === 'success') {
    return (
      <Card title="You're on the list!" titleId={titleId} headingRef={headingRef} panel>
        <div className="pf-state">
          <img className="pf-state-art" src="/assets/peach.svg" alt="" aria-hidden="true" />
          <p>
            Thanks, {values.firstName.trim()}. We&apos;ll email <strong>{values.email.trim()}</strong> as soon as
            registration opens.
          </p>
          <p>
            One more step: check your school inbox (<strong>{values.schoolEmail.trim()}</strong>) for a
            confirmation link and open it, so we know you&apos;re a current student.
          </p>
          <p className="pf-muted">If you don&apos;t see our emails, check your spam folders.</p>
          <button type="button" className="pf-button" onClick={onClose}>Back to PeachHacks</button>
        </div>
      </Card>
    );
  }

  const pending = status === 'submitting';

  return (
    <Card
      title="Pre-register"
      titleId={titleId}
      headingRef={headingRef}
      intro="Five quick fields now, so registering later is faster. We'll email you when registration opens."
      panel
    >
      <form className="pf-form" ref={formRef} onSubmit={handleSubmit} noValidate aria-labelledby={titleId}>
        {notice && <p className="pf-note pf-callout">{notice}</p>}
        <div className="pf-grid">
          <TextField
            name="firstName" label="First name" value={values.firstName} onChange={setValue} error={errors.firstName}
            autoComplete="given-name" autoCapitalize="words" maxLength={100} onBlur={checkOnBlur('firstName')}
          />
          <TextField
            name="lastName" label="Last name" value={values.lastName} onChange={setValue} error={errors.lastName}
            autoComplete="family-name" autoCapitalize="words" maxLength={100} onBlur={checkOnBlur('lastName')}
          />
          <TextField
            name="email" label="Personal email" value={values.email} onChange={setValue} error={errors.email}
            hint="We'll send registration news here." wide
            type="email" inputMode="email" autoComplete="email" autoCapitalize="none" spellCheck={false} maxLength={255}
            onBlur={checkOnBlur('email')}
          />
          <SchoolPicker value={values.school} onChange={setValue} error={errors.school} />
          <TextField
            name="schoolEmail" label="School email" value={values.schoolEmail} onChange={setValue}
            error={errors.schoolEmail} wide hint={schoolEmailHint(values)}
            type="email" inputMode="email" autoComplete="email" autoCapitalize="none" spellCheck={false} maxLength={255}
            onBlur={checkOnBlur('schoolEmail')}
          />
        </div>
        <Honeypot value={values.website} onChange={setValue} />
        <div className="pf-actions">
          <SubmitButton pending={pending} pendingLabel="Saving your spot…">Pre-register</SubmitButton>
          <FormAlert>{formError}</FormAlert>
        </div>
      </form>
    </Card>
  );
}
