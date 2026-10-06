import { useEffect, useRef, useState } from 'react';
import { submitPreRegistration } from './api.js';
import { Honeypot, SubmitButton, TextField } from './fields.jsx';
import { Card, FormAlert, PageShell } from './PageShell.jsx';
import SchoolPicker from './SchoolPicker.jsx';
import { describeFailure, focusFirstInvalid, isEmail, sameEmail, splitFieldErrors } from './validation.js';

const INITIAL_VALUES = { firstName: '', lastName: '', email: '', school: '', schoolEmail: '', website: '' };
const FIELD_NAMES = Object.keys(INITIAL_VALUES);

function validate(values) {
  const errors = {};
  if (!values.firstName.trim()) errors.firstName = 'Enter your first name.';
  if (!values.lastName.trim()) errors.lastName = 'Enter your last name.';
  if (!values.email.trim()) errors.email = 'Enter your email address.';
  else if (!isEmail(values.email)) errors.email = 'Enter a valid email, like name@example.com.';
  if (!values.school.trim()) errors.school = 'Pick your school from the list.';
  if (!values.schoolEmail.trim()) errors.schoolEmail = 'Enter your school email address.';
  else if (!isEmail(values.schoolEmail)) errors.schoolEmail = 'Enter a valid email, like name@school.edu.';
  return errors;
}

export default function PreRegisterForm() {
  const formRef = useRef(null);
  const headingRef = useRef(null);
  const [values, setValues] = useState(INITIAL_VALUES);
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [status, setStatus] = useState('idle');
  const [focusRequest, setFocusRequest] = useState(0);

  useEffect(() => {
    if (focusRequest > 0) focusFirstInvalid(formRef.current);
  }, [focusRequest]);

  useEffect(() => {
    if (status === 'success') headingRef.current?.focus();
  }, [status]);

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
    setStatus('idle');
    setFocusRequest((count) => count + 1);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (status === 'submitting') return;

    const clientErrors = validate(values);
    const errorCount = Object.keys(clientErrors).length;
    if (errorCount > 0) {
      fail(clientErrors, errorCount === 1 ? 'One field needs another look.' : `${errorCount} fields need another look.`);
      return;
    }

    setStatus('submitting');
    setFormError('');

    try {
      await submitPreRegistration({
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        email: values.email.trim(),
        school: values.school.trim(),
        schoolEmail: values.schoolEmail.trim(),
        website: values.website,
      });
      setStatus('success');
    } catch (error) {
      const { matched, unmatched } = splitFieldErrors(error?.fieldErrors, FIELD_NAMES);
      fail(matched, [describeFailure(error), ...unmatched].join(' '));
    }
  };

  if (status === 'success') {
    return (
      <PageShell>
        <Card title="You're on the list!" titleId="pre-register-title" headingRef={headingRef}>
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
            <a className="pf-button" href="/">Back to PeachHacks</a>
          </div>
        </Card>
      </PageShell>
    );
  }

  const pending = status === 'submitting';

  return (
    <PageShell>
      <Card
        title="Pre-register"
        titleId="pre-register-title"
        intro="Be the first to know when PeachHacks registration opens. It takes less than a minute."
      >
        <form className="pf-form" ref={formRef} onSubmit={handleSubmit} noValidate aria-labelledby="pre-register-title">
          <div className="pf-grid">
            <TextField
              name="firstName" label="First name" value={values.firstName} onChange={setValue} error={errors.firstName}
              autoComplete="given-name" autoCapitalize="words" maxLength={255}
            />
            <TextField
              name="lastName" label="Last name" value={values.lastName} onChange={setValue} error={errors.lastName}
              autoComplete="family-name" autoCapitalize="words" maxLength={255}
            />
            <TextField
              name="email" label="Personal email" value={values.email} onChange={setValue} error={errors.email}
              hint="We'll send registration news here." wide
              type="email" inputMode="email" autoComplete="email" autoCapitalize="none" spellCheck={false} maxLength={255}
            />
            <SchoolPicker value={values.school} onChange={setValue} error={errors.school} />
            <TextField
              name="schoolEmail" label="School email" value={values.schoolEmail} onChange={setValue}
              error={errors.schoolEmail} wide
              hint={sameEmail(values.schoolEmail, values.email)
                ? "Same as your personal email. That's fine if it's the only one you use."
                : 'The address your school gave you.'}
              type="email" inputMode="email" autoComplete="off" autoCapitalize="none" spellCheck={false} maxLength={255}
            />
          </div>
          <Honeypot value={values.website} onChange={setValue} />
          <div className="pf-actions">
            <SubmitButton pending={pending} pendingLabel="Saving your spot…">Pre-register</SubmitButton>
            <FormAlert>{formError}</FormAlert>
          </div>
        </form>
      </Card>
    </PageShell>
  );
}
