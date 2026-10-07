## PeachHacks pre-registration and registration forms

Reference: [MLH Guide - Registrations](https://guide.mlh.com/general-information/managing-registrations/registrations)

### Where the forms live

The pre-registration and registration forms have no page of their own. They open in the sign-up panel on the homepage: the register button on the hero tag tows the tag to the right edge and the panel takes its place (a full-screen sheet on phones and tablets).

| URL | What it does |
| --- | ------------ |
| `/#register` | The homepage with the sign-up panel open. Every call to action on the site links here. While `GET /public/status` reports `registrationOpen: false` the panel holds the pre-registration form (`POST /public/pre-registrations`); while it reports `true`, the full registration form (`POST /public/registrations`). |
| `/?register=1` | Rewritten to `/#register` on load. The redirects land here because a redirect destination with a `#fragment` is not documented by Vercel. |
| `/pre-register`, `/register`, `/interest-form` (and `.html`) | Old form pages. `vercel.json` redirects them to `/?register=1` so old links and emails keep working. |
| `/unsubscribe.html?token=...` | Confirms, then posts the token to `POST /public/unsubscribe`. |
| `/confirm-email?token=...` | School email confirmation, reached from the link mailed to the school address. See [School email confirmation](#school-email-confirmation). |
| `/ticket?t=...` | The check-in ticket linked from the acceptance email. |

How the panel behaves:

- The URL is the state. Opening pushes `/#register`, so the browser's Back button closes the panel; Escape, the panel's Close button, clicking the parked tag or dragging it back to the left do the same. Opening from further down the page scrolls back to the hero first.
- The panel waits for the API before showing a form. A failed status check shows a retry, never a form. If registration closes while someone is filling in the registration form, the submit is refused and the panel switches to the pre-registration form with their name, emails and school carried over.
- Closing the panel keeps what was typed (including a chosen resume) for as long as the page stays open. Nothing is written to storage. After a successful submit the next opening starts with a fresh form.
- The panel is a dialog: focus moves into it and stays there, the rest of the page is inert and cannot scroll, and focus returns to the control that opened it.

The code lives in `src/forms/`, with the panel shell in `src/home/RegisterLayer.jsx` (dialog, focus, scroll lock), `src/home/registerPanel.js` (the URL state) and `src/forms/RegisterPanel.jsx` (which form to show). The form code is loaded the first time the panel opens, not with the homepage.

Styles are split in two. `src/forms/forms.css` holds the form components and only styles `.pf-*` classes, so the homepage can load it without its own global styles changing. `src/forms/page.css` is the shell of the standalone pages (ticket, unsubscribe, confirm-email) and the only place those pages set global styles; they load both files and do not load `src/styles.css`.

The API base URL comes from `VITE_API_BASE_URL`, falling back to `http://localhost:8080` in dev and `https://api.peachhacks.com` in production builds.

### Pre-registration fields

- First name (required)
- Last name (required)
- Personal email (required)
- School (required, school picker)
- School email (required; any well-formed address, `.edu` is not required)

If the school email matches the personal email the form says so in the field's hint and still submits.

The success message names the school address and tells the person to open the confirmation link sent there.

### Registration: required fields

- First Name and Last Name (collected separately)
- Personal email (where confirmations and tickets are sent; one registration per personal email)
- School email (the address issued by the school; any well-formed address, `.edu` is not required, and it may match the personal email, in which case the field's hint points that out)
- Phone Number
- Age (dropdown of specific ages, 13 through 100, not a date of birth)
- Country of Residence (dropdown of ISO 3166-1 countries, United States pinned first; the alpha-2 code is submitted)
- School (school picker)
- Level of Study

The success message names the personal address the confirmation went to and the school address, and tells the person to open the confirmation link in their school inbox unless they already confirmed it (the API does not tell the form whether they did).

### School email confirmation

PeachHacks is for current students, so the API mails a link to the school address after a pre-registration, and after a registration whose school email is not already confirmed for that personal email. The link opens `/confirm-email?token=...` (`confirm-email.html`, `src/forms/ConfirmEmailPage.jsx`), built from the same shell and card as the unsubscribe page:

- **Confirm**: one button that posts the token to `POST /public/school-email/confirm`. Opening the page confirms nothing, because mail scanners open links; the click is what counts.
- **Confirmed**: names the address the API returned. Using the same link again shows this state again.
- **Link didn't work** (no token, or the API answered 404 for an unknown or expired one; links last 14 days): a one-field form asking for the personal email, which posts to `POST /public/school-email/resend` and then always says "If that email is registered, we sent a new link to its school address." The API answers the same way for every email, so the page cannot reveal who is registered.
- **Network or server error**: the message appears under the button, which becomes "Try again".

### Registration: MLH checkboxes

Shown under a note explaining that the MLH partnership is still in progress.

1. MLH Code of Conduct (required)
2. Sharing registration information with MLH, plus the MLH Contest Terms and Privacy Policy (required)
3. Occasional emails from MLH + DEV (optional)

### Registration: optional fields

Required fields carry a red asterisk; optional fields have no marker.

Grouped into three collapsible sections. Nothing here is needed to register.

- **Food and shirt**: Dietary Restrictions (multi-select) with a free-text details field, T-shirt Size (US unisex XS to 3XL). MLH's optional Shipping Address is not collected; the API still accepts `shippingAddress` but the form never sends it.
- **Studies and career**: Highest level of formal education completed, Major / Field of Study, LinkedIn URL, Resume
- **Demographics**: underrepresented group in tech, Gender, Pronouns, Race / Ethnicity (multi-select), Sexual orientation (MLH words this "Do you consider yourself to be any of the following?"; the form labels it plainly)

Options that say "self-describe", "other" or "please specify" reveal a text input. Its text is only submitted while that option is selected.

Choice fields are submitted as the exact label text from the MLH guide. The option lists are in `src/forms/options.js` and the country list in `src/forms/countries.js`.

### Resume

Optional but marked "Highly recommended", in the "Studies and career" section. One PDF of at most 2 MB.

- The file is checked when it is chosen and again on submit: its type (or a `.pdf` name when the browser reports no type), its size, and that it starts with `%PDF-`. Failures show under the field like any other error and the file is dropped. The API repeats the size and `%PDF-` checks and answers with `fieldErrors.resume`.
- The chosen file's name and size are shown with a "Remove" control.
- The file is read to base64 only when the form is submitted and sent inside the registration JSON as `resume: { fileName, contentBase64 }` (`null` without a file).
- Once a file is chosen, an unticked checkbox asks whether sponsors may have it. Ticking it sends `resumeOptIn: true`: sponsors then receive the resume with the hacker's name, personal and school email, school, level of study, major and LinkedIn link. Left unticked, only organizers can see the resume. Removing the file clears the checkbox. If the wording of that checkbox changes, keep it in line with what the resume book actually contains (see the backend README).

### School picker

MLH asks for a fixed list of schools instead of free typing. The picker searches MLH's verified schools list:

- Source: [`schools.csv` in MLH/mlh-policies](https://github.com/MLH/mlh-policies/blob/main/schools.csv)
- Shipped as `public/data/schools.json` (`{ source, updated, schools }`), de-duplicated and sorted, and fetched the first time the field is used
- "My school isn't listed" switches the field to free text; the same fallback is used if the list fails to load

To refresh the list, download the raw CSV, drop the first line (a note with the last-updated date), strip the quotes around each name, remove duplicates, and write the names to the `schools` array.

### Spam protection

Both forms include a hidden `website` field. People never see it; the API accepts and discards submissions that fill it in.
