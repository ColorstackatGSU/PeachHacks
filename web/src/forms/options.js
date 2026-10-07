// The label text is exactly what the MLH organizer guide specifies, and it is
// also the value sent to the API.

export const AGE_MIN = 13;
export const AGE_MAX = 100;
export const AGES = Array.from({ length: AGE_MAX - AGE_MIN + 1 }, (_, index) => String(AGE_MIN + index));

export const LEVELS_OF_STUDY = [
  'Less than Secondary / High School',
  'Secondary / High School',
  'Undergraduate University (2 year - community college or similar)',
  'Undergraduate University (3+ year)',
  'Graduate University (Masters, Professional, Doctoral, etc)',
  'Code School / Bootcamp',
  'Other Vocational / Trade Program or Apprenticeship',
  'Post Doctorate',
  'Other',
  "I'm not currently a student",
  'Prefer not to answer',
];

export const DIETARY_RESTRICTIONS = ['Vegetarian', 'Vegan', 'Celiac Disease', 'Allergies', 'Kosher', 'Halal'];

export const UNDERREPRESENTED_GROUP = ['Yes', 'No', 'Unsure'];

export const GENDER_SELF_DESCRIBE = 'Prefer to self-describe';
export const GENDERS = ['Man', 'Woman', 'Non-Binary', GENDER_SELF_DESCRIBE, 'Prefer Not to Answer'];

export const PRONOUNS_OTHER = 'Other';
export const PRONOUNS = ['She/Her', 'He/Him', 'They/Them', 'She/They', 'He/They', 'Prefer Not to Answer', PRONOUNS_OTHER];

export const RACE_ETHNICITY_OTHER = 'Other (Please Specify)';
export const RACE_ETHNICITY = [
  'Asian Indian',
  'Black or African',
  'Chinese',
  'Filipino',
  'Guamanian or Chamorro',
  'Hispanic / Latino / Spanish Origin',
  'Japanese',
  'Korean',
  'Middle Eastern',
  'Native American or Alaskan Native',
  'Native Hawaiian',
  'Samoan',
  'Vietnamese',
  'White',
  'Other Asian (Thai, Cambodian, etc)',
  'Other Pacific Islander',
  RACE_ETHNICITY_OTHER,
  'Prefer Not to Answer',
];

export const SEXUAL_ORIENTATION_OTHER = 'Different identity';
export const SEXUAL_ORIENTATION = [
  'Heterosexual or straight',
  'Gay or lesbian',
  'Bisexual',
  SEXUAL_ORIENTATION_OTHER,
  'Prefer Not to Answer',
];

export const HIGHEST_EDUCATION_OTHER = 'Other (please specify)';
export const HIGHEST_EDUCATION = [
  'Less than Secondary / High School',
  'Secondary / High School',
  'Undergraduate University (2 year - community college or similar)',
  'Undergraduate University (3+ year)',
  'Graduate University (Masters, Professional, Doctoral, etc)',
  'Code School / Bootcamp',
  'Other Vocational / Trade Program or Apprenticeship',
  HIGHEST_EDUCATION_OTHER,
  "I'm not currently a student",
  'Prefer not to answer',
];

export const TSHIRT_SIZES = ['XS', 'S', 'M', 'L', 'XL', '2XL', '3XL'];

export const MAJOR_OTHER = 'Other (please specify)';
export const MAJORS = [
  'Computer science, computer engineering, or software engineering',
  'Another engineering discipline (such as civil, electrical, mechanical, etc.)',
  'Information systems, information technology, or system administration',
  'A natural science (such as biology, chemistry, physics, etc.)',
  'Mathematics or statistics',
  'Web development or web design',
  'Business discipline (such as accounting, finance, marketing, etc.)',
  'Humanities discipline (such as literature, history, philosophy, etc.)',
  'Social science (such as anthropology, psychology, political science, etc.)',
  'Fine arts or performing arts (such as graphic design, music, studio art, etc.)',
  'Health science (such as nursing, pharmacy, radiology, etc.)',
  MAJOR_OTHER,
  'Undecided / No Declared Major',
  'My school does not offer majors / primary areas of study',
  'Prefer not to answer',
];

export const MLH_DISCLAIMER =
  'We have applied to be a Major League Hacking (MLH) Member Event. The following checkboxes are only applicable if our application is accepted. Your information will not be shared if we do not become an MLH Member Event.';

export const MLH_LINKS = {
  codeOfConduct: 'https://github.com/MLH/mlh-policies/blob/main/code-of-conduct.md',
  contestTerms: 'https://github.com/MLH/mlh-policies/blob/main/contest-terms.md',
  privacyPolicy: 'https://github.com/MLH/mlh-policies/blob/main/privacy-policy.md',
};

export const CONTACT_EMAIL = 'hello@peachhacks.com';
