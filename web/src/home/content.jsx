import { CODE_OF_CONDUCT_URL, CONTACT_EMAIL, EVENT_DATES, EVENT_DATES_SHORT, EVENT_THEME, PLATFORM_URL, SPONSOR_FORM_PATH, socialLinks } from './site.js';

export const aboutItems = [
  { title: 'What it is', body: 'A weekend where students team up and build an app, site, game or other project from scratch.' },
  { title: 'Who it’s for', body: 'Current college students from any school and any major. First hackathon? That’s who the workshops and mentors are for.' },
  { title: 'What it costs', body: 'Nothing. Admission and meals are free for accepted hackers.' },
];

export const backdropRows = [
  ['PeachHacks', EVENT_THEME, 'Atlanta', 'Build', 'Learn', 'Ship'],
  ['Hack', 'Georgia State', 'Mentors', 'Prizes', EVENT_DATES_SHORT, 'Beginners welcome'],
  ['Ship it', 'PeachHacks', 'Atlanta', 'Build', 'Learn', EVENT_THEME],
];

export const PARTNER_PLACEHOLDERS = 5;

const DISCORD_URL = socialLinks.find((link) => link.label === 'Discord').href;
const contactLink = <a className="faq-link" href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>;

export function getFaqColumns(cta) {
  const signUp = cta.open
    ? <>Registration is open. <a className="faq-link" href={cta.href}>Register here</a> to request your spot.</>
    : <>Registration isn’t open yet. <a className="faq-link" href={cta.href}>Pre-register</a> and we’ll email you the moment it opens.</>;

  const faq = [
    { q: 'What is a hackathon?', a: 'A weekend where students team up to build an app, website, game, or other project from scratch. At PeachHacks you get a full weekend to build, with workshops, mentors, and food along the way. No malicious hacking involved.' },
    { q: 'Who can attend?', a: 'PeachHacks is open to current university students from any school and any major. If you don’t attend Georgia State, you need to be at least 18.' },
    { q: 'Do I need experience?', a: 'No. It’s beginner friendly, with workshops and mentors throughout the weekend.' },
    { q: 'Is PeachHacks free?', a: 'Yes. Admission, meals, and snacks are free for every accepted hacker. We’ll also have swag and prizes.' },
    { q: 'When and where is it?', a: `PeachHacks is planned for ${EVENT_DATES}, in person at Georgia State University in Atlanta. The building and exact times will be posted here once they are confirmed.` },
    { q: 'How do I get there and where do I park?', a: 'Transit and parking details are to be announced with the building.' },
    { q: 'How do I sign up?', a: signUp },
    { q: 'How do acceptances work?', a: 'After you register we review applications and send acceptance emails together, so you may not hear back right away. Space is limited; if we fill up, we’ll open a waitlist. If you’re accepted, your email includes a ticket with a QR code: bring it on your phone to check in.' },
    { q: 'What is the hacker platform?', a: <>Once you’re accepted, you can sign in at <a className="faq-link" href={PLATFORM_URL}>platform.peachhacks.com</a> with the email you applied with, using a password or your Google account. It has your ticket, the other accepted hackers and who’s looking for a team, team sign-up, and a one-click way into our Discord with the Hacker role.</> },
    { q: 'Why do you need my school email?', a: 'PeachHacks is for current students, so we ask for the email address your school gave you and send a link there to confirm it. We’ll use your personal email for everything else.' },
    { q: 'What is the team size limit?', a: 'Teams are 1 to 4 people. If you don’t have a team, come anyway: there’s a team-building activity right after the opening ceremony.' },
    { q: 'What should I bring?', a: 'Your laptop, charger, headphones, and your ticket (the QR code from your acceptance email). If you’re staying overnight, bring a pillow or blanket, and deodorant.' },
    { q: 'Do I have to stay overnight?', a: 'No, but decide before 11:00 PM. The building locks at 11:00 PM, and after that nobody can come in or leave until it reopens in the morning. You’re welcome to stay inside overnight.' },
    { q: 'Will there be food? What about dietary restrictions?', a: 'Meals and snacks are provided all weekend. Tell us about any dietary restrictions when you register so we can order for you.' },
    { q: 'When can we start working? Can I use a previous project or AI tools?', a: 'Building starts after the opening ceremony. You can brainstorm beforehand, but you can’t bring a previous project. Frameworks and AI tools are fine: credit them in your README and make clear what you built yourself.' },
    { q: 'How many challenges can I enter?', a: 'As many as you want.' },
    { q: 'What else happens besides hacking?', a: 'Workshops, activities to take a break and meet other hackers, and time with our sponsors. The full schedule will be posted closer to the event.' },
    { q: 'I need an accommodation. Who do I tell?', a: <>Email {contactLink} or message an organizer in <a className="faq-link" href={DISCORD_URL} target="_blank" rel="noopener noreferrer">Discord</a> and tell us what you need. Building accessibility details will be posted with the venue information.</> },
    { q: 'Will hardware be available?', a: 'We’re still working out whether hardware will be available to borrow, and we’ll update this answer when we know. You’re welcome to bring your own.' },
    { q: 'Are there travel reimbursements?', a: 'No, we aren’t able to offer travel reimbursements.' },
    { q: 'Is there a code of conduct?', a: <>Yes. Everyone at PeachHacks agrees to the <a className="faq-link" href={CODE_OF_CONDUCT_URL} target="_blank" rel="noopener noreferrer">MLH Code of Conduct</a> when they register.</> },
    { q: 'How do I become a mentor, judge, volunteer, or sponsor?', a: <>Sponsors can reach us through the <a className="faq-link" href={SPONSOR_FORM_PATH}>sponsor form</a>. Sign-ups for mentors, judges, and volunteers aren’t open yet; email {contactLink} and we’ll reach out when they are.</> },
    { q: 'I have a different question!', a: <>Ask in our <a className="faq-link" href={DISCORD_URL} target="_blank" rel="noopener noreferrer">Discord</a> or email {contactLink}.</> },
  ];

  const half = Math.ceil(faq.length / 2);
  return [faq.slice(0, half), faq.slice(half)];
}
