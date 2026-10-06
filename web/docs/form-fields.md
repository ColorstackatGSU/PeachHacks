## PeachHacks pre-registration and registration forms

Reference: [MLH Guide - Registrations](https://guide.mlh.com/general-information/managing-registrations/registrations)

### Pages

| Page | What it does |
| ---- | ------------ |
| `/pre-register.html` | Pre-registration form. Posts to `POST /public/pre-registrations`. |
| `/register.html` | Full registration form, shown only while `GET /public/status` reports `registrationOpen: true`. Posts to `POST /public/registrations`. |
| `/unsubscribe.html?token=...` | Confirms, then posts the token to `POST /public/unsubscribe`. |
| `/interest-form.html` | Redirects to `/pre-register.html` so old links keep working. |

The code lives in `src/forms/`; styles are in `src/forms/forms.css` (these pages do not load `src/styles.css`). The API base URL comes from `VITE_API_BASE_URL`, falling back to `http://localhost:8080` in dev and `https://api.peachhacks.com` in production builds.

### Pre-registration fields

- First name (required)
- Last name (required)
- Personal email (required)
- School (required, school picker)
- School email (optional)

### Registration: required fields

- First Name and Last Name (collected separately)
- Email
- Phone Number
- Age (dropdown of specific ages, 13 through 100, not a date of birth)
- Country of Residence (dropdown of ISO 3166-1 countries, United States pinned first; the alpha-2 code is submitted)
- School (school picker)
- Level of Study

### Registration: MLH checkboxes

Shown under a note explaining that the MLH partnership is still in progress.

1. MLH Code of Conduct (required)
2. Sharing registration information with MLH, plus the MLH Contest Terms and Privacy Policy (required)
3. Occasional emails from MLH + DEV (optional)

### Registration: optional fields

Grouped into three collapsible sections. Nothing here is needed to register.

- **Food, shirt and shipping**: Dietary Restrictions (multi-select) with a free-text details field, T-shirt Size (US unisex XS to 3XL), Shipping Address (line 1, line 2, city, state, country, postal code)
- **Studies and career**: Highest level of formal education completed, Major / Field of Study, LinkedIn URL
- **Demographics** (used only in aggregate): underrepresented group in tech, Gender, Pronouns, Race / Ethnicity (multi-select), sexual orientation

Options that say "self-describe", "other" or "please specify" reveal a text input. Its text is only submitted while that option is selected.

Choice fields are submitted as the exact label text from the MLH guide. The option lists are in `src/forms/options.js` and the country list in `src/forms/countries.js`.

### School picker

MLH asks for a fixed list of schools instead of free typing. The picker searches MLH's verified schools list:

- Source: [`schools.csv` in MLH/mlh-policies](https://github.com/MLH/mlh-policies/blob/main/schools.csv)
- Shipped as `public/data/schools.json` (`{ source, updated, schools }`), de-duplicated and sorted, and fetched the first time the field is used
- "My school isn't listed" switches the field to free text; the same fallback is used if the list fails to load

To refresh the list, download the raw CSV, drop the first line (a note with the last-updated date), strip the quotes around each name, remove duplicates, and write the names to the `schools` array.

### Spam protection

Both forms include a hidden `website` field. People never see it; the API accepts and discards submissions that fill it in.
