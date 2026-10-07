/* eslint-disable react/prop-types -- the project has no prop-types dependency and React 19 ignores propTypes */
import { useEffect, useRef, useState } from 'react';
import { submitRegistration } from './api.js';
import { COUNTRIES, PINNED_COUNTRY } from './countries.js';
import {
  CheckboxGroup, ConsentCheckbox, Honeypot, ResumeField, SelectField, SubmitButton, TextField,
} from './fields.jsx';
import {
  AGES, CONTACT_EMAIL, DIETARY_RESTRICTIONS, GENDERS, GENDER_SELF_DESCRIBE, HIGHEST_EDUCATION, HIGHEST_EDUCATION_OTHER,
  LEVELS_OF_STUDY, MAJORS, MAJOR_OTHER, MLH_DISCLAIMER, MLH_LINKS, PRONOUNS, PRONOUNS_OTHER, RACE_ETHNICITY,
  RACE_ETHNICITY_OTHER, SEXUAL_ORIENTATION, SEXUAL_ORIENTATION_OTHER, TSHIRT_SIZES, UNDERREPRESENTED_GROUP,
} from './options.js';
import { FormAlert } from './PageShell.jsx';
import SchoolPicker from './SchoolPicker.jsx';
import {
  blankToNull, checkResume, describeFailure, focusFirstInvalid, isEmail, isPhone, normalizeLinkedinUrl,
  readFileAsBase64, sameEmail, splitFieldErrors,
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
  resumeOptIn: false,
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
  studies: ['highestEducation', 'highestEducationOther', 'majorFieldOfStudy', 'majorOther', 'linkedinUrl', 'resume', 'resumeOptIn'],
  demographics: [
    'underrepresentedGroup', 'gender', 'genderSelfDescribe', 'pronouns', 'pronounsOther', 'raceEthnicity',
    'raceEthnicityOther', 'sexualOrientation', 'sexualOrientationOther',
  ],
};

const COUNTRY_OPTIONS = COUNTRIES.map((country) => ({ value: country.code, label: country.name }));
const PINNED_COUNTRY_OPTION = { value: PINNED_COUNTRY.code, label: PINNED_COUNTRY.name };
const OTHER_COUNTRY_OPTIONS = COUNTRY_OPTIONS.filter((option) => option.value !== PINNED_COUNTRY.code);

function validate(values) {
  const errors = {};
  if (!values.firstName.trim()) errors.firstName = 'Enter your first name.';
  if (!values.lastName.trim()) errors.lastName = 'Enter your last name.';
  if (!values.email.trim()) errors.email = 'Enter your email address.';
  else if (!isEmail(values.email)) errors.email = 'Enter a valid email, like name@example.com.';
  if (!values.schoolEmail.trim()) errors.schoolEmail = 'Enter your school email address.';
  else if (!isEmail(values.schoolEmail)) errors.schoolEmail = 'Enter a valid email, like name@school.edu.';
  if (!values.phone.trim()) errors.phone = 'Enter your phone number.';
  else if (!isPhone(values.phone)) errors.phone = 'Enter a valid phone number, with area code.';
  if (!AGES.includes(values.age)) errors.age = 'Select your age.';
  if (!values.countryOfResidence) errors.countryOfResidence = 'Select your country of residence.';
  if (!values.school.trim()) errors.school = 'Choose your school.';
  if (!values.levelOfStudy) errors.levelOfStudy = 'Select your level of study.';
  if (!values.mlhCodeOfConduct) errors.mlhCodeOfConduct = 'You need to agree to the MLH Code of Conduct to register.';
  if (!values.mlhDataSharing) errors.mlhDataSharing = 'You need to agree to this to register.';
  if (values.linkedinUrl.trim() && normalizeLinkedinUrl(values.linkedinUrl) === null) {
    errors.linkedinUrl = 'Enter a LinkedIn link, like linkedin.com/in/yourname, or leave this blank.';
  }
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
    resumeOptIn: resume !== null && values.resumeOptIn,
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
  const formRef = useRef(null);
  const [values, setValues] = useState(INITIAL_VALUES);
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [pending, setPending] = useState(false);
  const [focusRequest, setFocusRequest] = useState(0);
  const [openSections, setOpenSections] = useState({ logistics: true, studies: false, demographics: false });

  useEffect(() => {
    if (focusRequest > 0) focusFirstInvalid(formRef.current);
  }, [focusRequest]);

  const setValue = (name, value) => {
    setValues((current) => ({ ...current, [name]: value }));
    setErrors((current) => {
      if (!current[name]) return current;
      const next = { ...current };
      delete next[name];
      return next;
    });
  };

  const removeResume = () => {
    setValues((current) => ({ ...current, resume: null, resumeOptIn: false }));
    setErrors((current) => {
      const next = { ...current };
      delete next.resume;
      delete next.resumeOptIn;
      return next;
    });
  };

  const chooseResume = async (file) => {
    const problem = await checkResume(file);
    if (problem) {
      setValues((current) => ({ ...current, resume: null, resumeOptIn: false }));
      setErrors((current) => ({ ...current, resume: problem }));
      return;
    }
    setValue('resume', file);
  };

  const toggleSection = (id) => setOpenSections((current) => ({ ...current, [id]: !current[id] }));

  const fail = (fieldErrors, message) => {
    setErrors(fieldErrors);
    setFormError(message);
    setPending(false);
    // A collapsed section would hide the field that needs fixing.
    setOpenSections((current) => {
      const next = { ...current };
      for (const [section, fields] of Object.entries(SECTION_FIELDS)) {
        if (fields.some((field) => fieldErrors[field])) next[section] = true;
      }
      return next;
    });
    setFocusRequest((count) => count + 1);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (pending) return;

    const clientErrors = validate(values);
    // Checked again here because the file can change on disk after it was chosen.
    const resumeProblem = values.resume ? await checkResume(values.resume) : null;
    if (resumeProblem) clientErrors.resume = resumeProblem;
    const errorCount = Object.keys(clientErrors).length;
    if (errorCount > 0) {
      fail(clientErrors, errorCount === 1 ? 'One field needs another look.' : `${errorCount} fields need another look.`);
      return;
    }

    setPending(true);
    setFormError(null);

    let resume = null;
    if (values.resume) {
      try {
        resume = { fileName: values.resume.name, contentBase64: await readFileAsBase64(values.resume) };
      } catch {
        fail({ resume: "We couldn't read that file. Choose it again." }, 'One field needs another look.');
        return;
      }
    }

    try {
      await submitRegistration(buildPayload(values, resume));
      onSuccess(values.email.trim(), values.schoolEmail.trim());
    } catch (error) {
      if (error?.code === 'REGISTRATION_CLOSED') {
        onClosed(values);
        return;
      }
      if (error?.code === 'ALREADY_REGISTERED') {
        fail(
          { email: 'This email is already registered.' },
          <>
            This email is already registered for PeachHacks, so you&apos;re all set. Need to change something? Email{' '}
            <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>.
          </>,
        );
        return;
      }
      const { matched, unmatched } = splitFieldErrors(error?.fieldErrors, FIELD_NAMES);
      fail(matched, [describeFailure(error), ...unmatched].join(' '));
    }
  };

  const field = (name) => ({ name, value: values[name], onChange: setValue, error: errors[name] });

  return (
    <form className="pf-form" ref={formRef} onSubmit={handleSubmit} noValidate aria-labelledby={titleId}>
      <fieldset className="pf-fieldset">
        <legend className="pf-legend">About you</legend>
        <div className="pf-grid">
          <TextField {...field('firstName')} label="First Name" autoComplete="given-name" autoCapitalize="words" maxLength={255} />
          <TextField {...field('lastName')} label="Last Name" autoComplete="family-name" autoCapitalize="words" maxLength={255} />
          <TextField
            {...field('email')} label="Personal email" wide hint="Where we'll contact you. Your confirmation goes here."
            type="email" inputMode="email" autoComplete="email" autoCapitalize="none" spellCheck={false} maxLength={255}
          />
          <TextField
            {...field('schoolEmail')} label="School email" wide
            hint={sameEmail(values.schoolEmail, values.email)
              ? "Same as your personal email. That's fine if it's the only one you use."
              : 'The address your school gave you.'}
            type="email" inputMode="email" autoComplete="off" autoCapitalize="none" spellCheck={false} maxLength={255}
          />
          <TextField
            {...field('phone')} label="Phone Number"
            type="tel" inputMode="tel" autoComplete="tel" maxLength={32}
          />
          <SelectField {...field('age')} label="Age" options={AGES} placeholder="Select your age" />
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
            hint="Specific allergies or anything else our caterers should know." maxLength={255}
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
          />
          <ResumeField
            name="resume" file={values.resume} onChoose={chooseResume} onRemove={removeResume} error={errors.resume}
            hint="Highly recommended. PDF only, up to 2 MB."
          />
          {values.resume && (
            <ConsentCheckbox
              name="resumeOptIn" checked={values.resumeOptIn} onChange={setValue} error={errors.resumeOptIn}
            >
              Share my resume with PeachHacks sponsors for recruiting. If you tick this, sponsors receive your resume
              together with your name, personal and school email, school, level of study, major and LinkedIn link.
              Leave it unticked and your resume stays with the PeachHacks organizers, who can always see it.
            </ConsentCheckbox>
          )}
        </div>
      </Section>

      <Section
        id="demographics" title="Demographics" summary=""
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
