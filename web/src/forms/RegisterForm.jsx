import { useRef, useState } from 'react';
import { submitRegistration } from './api.js';
import { COUNTRIES, PINNED_COUNTRY } from './countries.js';
import {
  CheckboxGroup, ConsentCheckbox, Honeypot, ResumeField, SelectField, SubmitButton, TextField,
} from './fields.jsx';
import { useFormFields } from './hooks.js';
import {
  AGES, DIETARY_RESTRICTIONS, GENDERS, GENDER_SELF_DESCRIBE, HIGHEST_EDUCATION, HIGHEST_EDUCATION_OTHER,
  GRADUATION_MONTHS, GRADUATION_YEARS, LEVELS_OF_STUDY, MAJORS, MAJOR_OTHER, MLH_DISCLAIMER, MLH_LINKS, PRONOUNS, PRONOUNS_OTHER, RACE_ETHNICITY,
  RACE_ETHNICITY_OTHER, SEXUAL_ORIENTATION, SEXUAL_ORIENTATION_OTHER, TSHIRT_SIZES, UNDERREPRESENTED_GROUP,
} from './options.js';
import { FormAlert } from './PageShell.jsx';
import SchoolPicker from './SchoolPicker.jsx';
import { isHostSchool } from './schools.js';
import {
  anotherLookMessage, apiFailure, blankToNull, checkFields, checkResume, normalizeLinkedinUrl, readFileAsBase64,
  schoolEmailHint, validateIdentity,
} from './validation.js';

const INITIAL_VALUES = {
  firstName: '',
  lastName: '',
  email: '',
  schoolEmail: '',
  phone: '',
  age: '',
  countryOfResidence: '',
  school: '',
  levelOfStudy: '',
  graduationMonth: '',
  graduationYear: '',
  mlhCodeOfConduct: false,
  mlhDataSharing: false,
  mlhEmailOptIn: false,
  dietaryRestrictions: [],
  dietaryDetails: '',
  tshirtSize: '',
  highestEducation: '',
  highestEducationOther: '',
  majorFieldOfStudy: '',
  majorOther: '',
  linkedinUrl: '',
  resume: null,
  underrepresentedGroup: '',
  gender: '',
  genderSelfDescribe: '',
  pronouns: '',
  pronounsOther: '',
  raceEthnicity: [],
  raceEthnicityOther: '',
  sexualOrientation: '',
  sexualOrientationOther: '',
  website: '',
};
const FIELD_NAMES = Object.keys(INITIAL_VALUES);

// Which collapsible section each optional field lives in, so a section can be
// opened when the API reports an error inside it.
const SECTION_FIELDS = {
  logistics: ['dietaryRestrictions', 'dietaryDetails', 'tshirtSize'],
  studies: ['highestEducation', 'highestEducationOther', 'majorFieldOfStudy', 'majorOther', 'linkedinUrl', 'resume'],
  demographics: [
    'underrepresentedGroup', 'gender', 'genderSelfDescribe', 'pronouns', 'pronounsOther', 'raceEthnicity',
    'raceEthnicityOther', 'sexualOrientation', 'sexualOrientationOther',
  ],
};

const COUNTRY_OPTIONS = COUNTRIES.map((country) => ({ value: country.code, label: country.name }));
const PINNED_COUNTRY_OPTION = { value: PINNED_COUNTRY.code, label: PINNED_COUNTRY.name };
const OTHER_COUNTRY_OPTIONS = COUNTRY_OPTIONS.filter((option) => option.value !== PINNED_COUNTRY.code);

const ADULT_AGE = 18;

function validate(values) {
  const errors = { ...validateIdentity(values), ...checkFields(values, ['phone', 'linkedinUrl']) };
  if (!AGES.includes(values.age)) errors.age = 'Select your age.';
  if (!values.countryOfResidence) errors.countryOfResidence = 'Select your country of residence.';
  if (!values.levelOfStudy) errors.levelOfStudy = 'Select your level of study.';
  if (!values.graduationMonth) errors.graduationMonth = 'Select the month you expect to graduate.';
  if (!values.graduationYear) errors.graduationYear = 'Select the year you expect to graduate.';
  if (!values.mlhCodeOfConduct) errors.mlhCodeOfConduct = 'You need to agree to the MLH Code of Conduct to register.';
  if (!values.mlhDataSharing) errors.mlhDataSharing = 'You need to agree to this to register.';
  return errors;
}

// `resume` is null or { fileName, contentBase64 }, read from the chosen file at submit time.
function buildPayload(values, resume) {
  const otherText = (selected, text) => (selected ? blankToNull(text) : null);

  return {
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    age: Number.parseInt(values.age, 10),
    phone: values.phone.trim(),
    email: values.email.trim(),
    schoolEmail: values.schoolEmail.trim(),
    school: values.school.trim(),
    levelOfStudy: values.levelOfStudy,
    graduationMonth: Number.parseInt(values.graduationMonth, 10),
    graduationYear: Number.parseInt(values.graduationYear, 10),
    countryOfResidence: values.countryOfResidence,
    mlhCodeOfConduct: values.mlhCodeOfConduct,
    mlhDataSharing: values.mlhDataSharing,
    mlhEmailOptIn: values.mlhEmailOptIn,

    dietaryRestrictions: values.dietaryRestrictions,
    dietaryDetails: blankToNull(values.dietaryDetails),
    underrepresentedGroup: blankToNull(values.underrepresentedGroup),
    gender: blankToNull(values.gender),
    genderSelfDescribe: otherText(values.gender === GENDER_SELF_DESCRIBE, values.genderSelfDescribe),
    pronouns: blankToNull(values.pronouns),
    pronounsOther: otherText(values.pronouns === PRONOUNS_OTHER, values.pronounsOther),
    raceEthnicity: values.raceEthnicity,
    raceEthnicityOther: otherText(values.raceEthnicity.includes(RACE_ETHNICITY_OTHER), values.raceEthnicityOther),
    sexualOrientation: blankToNull(values.sexualOrientation),
    sexualOrientationOther: otherText(values.sexualOrientation === SEXUAL_ORIENTATION_OTHER, values.sexualOrientationOther),
    highestEducation: blankToNull(values.highestEducation),
    highestEducationOther: otherText(values.highestEducation === HIGHEST_EDUCATION_OTHER, values.highestEducationOther),
    tshirtSize: blankToNull(values.tshirtSize),
    majorFieldOfStudy: blankToNull(values.majorFieldOfStudy),
    majorOther: otherText(values.majorFieldOfStudy === MAJOR_OTHER, values.majorOther),
    linkedinUrl: blankToNull(normalizeLinkedinUrl(values.linkedinUrl) ?? ''),
    resume,
    website: values.website,
  };
}

function Section({ id, title, summary, open, onToggle, children }) {
  return (
    <section className="pf-section">
      <h3 className="pf-section-heading">
        <button
          type="button"
          className="pf-section-toggle"
          aria-expanded={open}
          aria-controls={`pf-section-${id}`}
          onClick={() => onToggle(id)}
        >
          <span className="pf-section-title">{title}</span>
          {summary && <span className="pf-section-summary">{summary}</span>}
          <span className="pf-section-chevron" aria-hidden="true" />
        </button>
      </h3>
      <div className="pf-section-body" id={`pf-section-${id}`} hidden={!open}>
        {children}
      </div>
    </section>
  );
}

// onSuccess(email, schoolEmail); onClosed(values) when the gate turns out to be shut at submit.
export default function RegisterForm({ titleId, onSuccess, onClosed }) {
  const form = useFormFields(INITIAL_VALUES, 'register');
  const { formRef, values, setValues, errors, setErrors, formError, setFormError, setValue, checkOnBlur } = form;
  const [pending, setPending] = useState(false);
  // `pending` only updates on the next render; this closes the gap while the resume is being checked.
  const submitting = useRef(false);
  const [openSections, setOpenSections] = useState({ logistics: true, studies: false, demographics: false });

  const removeResume = () => {
    setValues((current) => ({ ...current, resume: null }));
    setErrors((current) => {
      const next = { ...current };
      delete next.resume;
      return next;
    });
  };

  const chooseResume = async (file) => {
    const problem = await checkResume(file);
    if (problem) {
      setValues((current) => ({ ...current, resume: null }));
      setErrors((current) => ({ ...current, resume: problem }));
      return;
    }
    setValue('resume', file);
  };

  const toggleSection = (id) => setOpenSections((current) => ({ ...current, [id]: !current[id] }));

  const fail = (fieldErrors, message) => {
    submitting.current = false;
    setPending(false);
    // A collapsed section would hide the field that needs fixing.
    setOpenSections((current) => {
      const next = { ...current };
      for (const [section, fields] of Object.entries(SECTION_FIELDS)) {
        if (fields.some((field) => fieldErrors[field])) next[section] = true;
      }
      return next;
    });
    form.fail(fieldErrors, message);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (pending || submitting.current) return;
    submitting.current = true;

    const clientErrors = validate(values);
    // Checked again here because the file can change on disk after it was chosen.
    const resumeProblem = values.resume ? await checkResume(values.resume) : null;
    if (resumeProblem) clientErrors.resume = resumeProblem;
    const errorCount = Object.keys(clientErrors).length;
    if (errorCount > 0) {
      fail(clientErrors, anotherLookMessage(errorCount));
      return;
    }

    setPending(true);
    setFormError(null);

    let resume = null;
    if (values.resume) {
      try {
        resume = { fileName: values.resume.name, contentBase64: await readFileAsBase64(values.resume) };
      } catch {
        fail({ resume: "We couldn't read that file. Choose it again." }, anotherLookMessage(1));
        return;
      }
    }

    try {
      await submitRegistration(buildPayload(values, resume));
      form.clearDraft();
      onSuccess(values.email.trim(), values.schoolEmail.trim());
    } catch (error) {
      if (error?.code === 'REGISTRATION_CLOSED') {
        onClosed(values);
        return;
      }
      fail(...apiFailure(error, FIELD_NAMES));
    }
  };

  const field = (name) => ({ name, value: values[name], onChange: setValue, error: errors[name] });
  const needsAdultNote = values.age !== '' && Number(values.age) < ADULT_AGE
    && values.school.trim() !== '' && !isHostSchool(values.school);

  return (
    <form className="pf-form" ref={formRef} onSubmit={handleSubmit} noValidate aria-labelledby={titleId}>
      <fieldset className="pf-fieldset">
        <legend className="pf-legend">About you</legend>
        <div className="pf-grid">
          <TextField
            {...field('firstName')} label="First Name" autoComplete="given-name" autoCapitalize="words" maxLength={100}
            onBlur={checkOnBlur('firstName')}
          />
          <TextField
            {...field('lastName')} label="Last Name" autoComplete="family-name" autoCapitalize="words" maxLength={100}
            onBlur={checkOnBlur('lastName')}
          />
          <TextField
            {...field('email')} label="Personal email" wide hint="Where we'll contact you. Your confirmation goes here."
            type="email" inputMode="email" autoComplete="email" autoCapitalize="none" spellCheck={false} maxLength={255}
            onBlur={checkOnBlur('email')}
          />
          <TextField
            {...field('schoolEmail')} label="School email" wide hint={schoolEmailHint(values)}
            type="email" inputMode="email" autoComplete="email" autoCapitalize="none" spellCheck={false} maxLength={255}
            onBlur={checkOnBlur('schoolEmail')}
          />
          <TextField
            {...field('phone')} label="Phone Number" hint="With area code, e.g. 404 555 0123"
            type="tel" inputMode="tel" autoComplete="tel" maxLength={40} onBlur={checkOnBlur('phone')}
          />
          <SelectField
            {...field('age')} label="Age" options={AGES} placeholder="Select your age"
            hint={needsAdultNote ? "If you don't attend Georgia State, you need to be at least 18 to take part." : undefined}
          />
          <SelectField
            {...field('countryOfResidence')} label="Country of Residence" wide
            options={OTHER_COUNTRY_OPTIONS} pinned={PINNED_COUNTRY_OPTION} placeholder="Select a country"
            autoComplete="country"
          />
        </div>
      </fieldset>

      <fieldset className="pf-fieldset">
        <legend className="pf-legend">Your school</legend>
        <div className="pf-grid">
          <SchoolPicker value={values.school} onChange={setValue} error={errors.school} />
          <SelectField
            {...field('levelOfStudy')} label="Level of Study" wide
            options={LEVELS_OF_STUDY} placeholder="Select your level of study"
          />
          <SelectField
            {...field('graduationMonth')} label="Expected Graduation Month"
            options={GRADUATION_MONTHS} placeholder="Month"
          />
          <SelectField
            {...field('graduationYear')} label="Expected Graduation Year"
            options={GRADUATION_YEARS} placeholder="Year"
          />
        </div>
      </fieldset>

      <fieldset className="pf-fieldset">
        <legend className="pf-legend">MLH agreements</legend>
        <p className="pf-note pf-callout">{MLH_DISCLAIMER}</p>
        <div className="pf-grid">
          <ConsentCheckbox
            name="mlhCodeOfConduct" checked={values.mlhCodeOfConduct} onChange={setValue}
            error={errors.mlhCodeOfConduct} required
          >
            I have read and agree to the{' '}
            <a href={MLH_LINKS.codeOfConduct} target="_blank" rel="noopener noreferrer">MLH Code of Conduct</a>.
          </ConsentCheckbox>
          <ConsentCheckbox
            name="mlhDataSharing" checked={values.mlhDataSharing} onChange={setValue}
            error={errors.mlhDataSharing} required
          >
            I authorize you to share my application/registration information with Major League Hacking for event
            administration, ranking, and MLH/DEV administration (including the creation of linked accounts on MLH and DEV
            (dev.to)) in line with the{' '}
            <a href={MLH_LINKS.privacyPolicy} target="_blank" rel="noopener noreferrer">MLH Privacy Policy</a>. I
            further agree to the terms of both the{' '}
            <a href={MLH_LINKS.contestTerms} target="_blank" rel="noopener noreferrer">MLH Contest Terms and Conditions</a>{' '}
            and the{' '}
            <a href={MLH_LINKS.privacyPolicy} target="_blank" rel="noopener noreferrer">MLH Privacy Policy</a>.
          </ConsentCheckbox>
          <ConsentCheckbox
            name="mlhEmailOptIn" checked={values.mlhEmailOptIn} onChange={setValue} error={errors.mlhEmailOptIn}
          >
            I authorize MLH and DEV to send me occasional emails about relevant events, career opportunities, and
            community announcements.
          </ConsentCheckbox>
        </div>
      </fieldset>

      <div className="pf-optional-intro">
        <h3 className="pf-legend">A little more information:</h3>
      </div>

      <Section
        id="logistics" title="Food and shirt" summary="Helps us order the right meals and swag."
        open={openSections.logistics} onToggle={toggleSection}
      >
        <div className="pf-grid">
          <CheckboxGroup {...field('dietaryRestrictions')} legend="Dietary Restrictions" options={DIETARY_RESTRICTIONS} />
          <TextField
            {...field('dietaryDetails')} label="Dietary details" optional wide
            hint="Specific allergies or anything else our caterers should know." maxLength={1000}
          />
          <SelectField {...field('tshirtSize')} label="T-shirt Size" optional options={TSHIRT_SIZES} hint="US unisex sizing." />
        </div>
      </Section>

      <Section
        id="studies" title="Studies and career" summary="Education, major, LinkedIn and resume."
        open={openSections.studies} onToggle={toggleSection}
      >
        <div className="pf-grid">
          <SelectField
            {...field('highestEducation')} label="Highest level of formal education completed" optional wide
            options={HIGHEST_EDUCATION}
          />
          {values.highestEducation === HIGHEST_EDUCATION_OTHER && (
            <TextField {...field('highestEducationOther')} label="Please specify your education" optional wide maxLength={255} />
          )}
          <SelectField {...field('majorFieldOfStudy')} label="Major / Field of Study" optional wide options={MAJORS} />
          {values.majorFieldOfStudy === MAJOR_OTHER && (
            <TextField {...field('majorOther')} label="Please specify your major" optional wide maxLength={255} />
          )}
          <TextField
            {...field('linkedinUrl')} label="LinkedIn URL" optional wide placeholder="linkedin.com/in/yourname"
            type="url" inputMode="url" autoComplete="url" autoCapitalize="none" spellCheck={false} maxLength={255}
            onBlur={checkOnBlur('linkedinUrl')}
          />
          <ResumeField
            name="resume" file={values.resume} onChoose={chooseResume} onRemove={removeResume} error={errors.resume}
            hint="Optional. PDF only, up to 2 MB. Sponsors receive it for recruiting, with your name, emails, school, level of study, graduation date, major and LinkedIn."
          />
        </div>
      </Section>

      <Section
        id="demographics" title="Demographics" summary="Optional. Skip any question."
        open={openSections.demographics} onToggle={toggleSection}
      >
        <div className="pf-grid">
          <SelectField
            {...field('underrepresentedGroup')} optional wide options={UNDERREPRESENTED_GROUP}
            label="Do you identify as part of an underrepresented group in the technology industry?"
          />
          <SelectField {...field('gender')} label="Gender" optional options={GENDERS} />
          <SelectField {...field('pronouns')} label="Pronouns" optional options={PRONOUNS} />
          {values.gender === GENDER_SELF_DESCRIBE && (
            <TextField {...field('genderSelfDescribe')} label="Describe your gender" optional wide maxLength={255} />
          )}
          {values.pronouns === PRONOUNS_OTHER && (
            <TextField {...field('pronounsOther')} label="Your pronouns" optional wide maxLength={255} />
          )}
          <CheckboxGroup {...field('raceEthnicity')} legend="Race / Ethnicity" options={RACE_ETHNICITY} />
          {values.raceEthnicity.includes(RACE_ETHNICITY_OTHER) && (
            <TextField {...field('raceEthnicityOther')} label="Please specify your race / ethnicity" optional wide maxLength={255} />
          )}
          <SelectField
            {...field('sexualOrientation')} optional wide options={SEXUAL_ORIENTATION}
            label="Sexual orientation"
          />
          {values.sexualOrientation === SEXUAL_ORIENTATION_OTHER && (
            <TextField {...field('sexualOrientationOther')} label="Describe your identity" optional wide maxLength={255} />
          )}
        </div>
      </Section>

      <Honeypot value={values.website} onChange={setValue} />
      <div className="pf-actions">
        <SubmitButton pending={pending} pendingLabel="Sending your registration…">Register for PeachHacks</SubmitButton>
        <FormAlert>{formError}</FormAlert>
      </div>
    </form>
  );
}
