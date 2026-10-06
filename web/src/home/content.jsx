import React from 'react';
import { CONTACT_EMAIL } from './site.js';

// Tentative until the organizers publish the final run of show.
export const scheduleData = [
  {
    day: 'Fri, Feb 5',
    events: [
      { time: '9:00 AM', title: 'Check-in' },
      { time: '10:00 AM', title: 'Opening Ceremony' },
      { time: '11:00 AM', title: 'Hacking begins' },
    ],
  },
  {
    day: 'Sat, Feb 6',
    events: [
      { time: '9:00 AM', title: 'Mentorship sessions' },
      { time: '11:00 AM', title: 'Workshops' },
      { time: '2:00 PM', title: 'Hacking continues' },
    ],
  },
  {
    day: 'Sun, Feb 7',
    events: [
      { time: 'TBA', title: 'Judging & Closing Ceremony' },
    ],
  },
];
export const scheduleNote = 'Tentative schedule. Final times will be announced closer to the event.';
export const scheduleGroups = scheduleData.map((group, groupIndex) => ({
  ...group,
  firstRow: scheduleData.slice(0, groupIndex).reduce((count, previous) => count + previous.events.length, 0),
}));

export const tracksData = [];

export const tracksBackdropRows = [
  ['PeachHacks', 'Spring 2027', 'Atlanta', 'Build', 'Learn', 'Ship'],
  ['Hack', 'Georgia State', 'Mentors', 'Prizes', 'Feb 5–7', 'Beginners welcome'],
  ['Ship it', 'PeachHacks', 'Atlanta', 'Build', 'Learn', 'Spring 2027'],
];

export const PARTNER_PLACEHOLDERS = 5;

const contactLink = <a className="faq-link" href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>;

export function getFaqColumns(cta) {
  const signUp = cta.open
    ? <>Registration is open. <a className="faq-link" href={cta.href}>Register here</a> to request your spot.</>
    : <>Registration isn’t open yet. <a className="faq-link" href={cta.href}>Pre-register</a> and we’ll email you the moment it opens.</>;

  const faq = [
    { q: 'How do I sign up?', a: signUp },
    { q: 'Is PeachHacks free to attend?', a: 'Yes! Food will be provided for the duration of the event. We’ll also have swag and prizes.' },
    { q: 'Where is the event? Is it in person or virtual?', a: 'PeachHacks is planned as an in-person event at Georgia State University in Atlanta. The building, address, and parking details will be posted here once they are confirmed.' },
    { q: 'Who can attend? What if I have no experience?', a: 'PeachHacks is open to students and is beginner friendly, with workshops and mentors available throughout the event. Attendees must be at least 13 years old. If you’re under 18, you’ll need a signed university liability form, which we’ll share before the event.' },
    { q: 'What is the team size limit?', a: 'Teams should be between 1 and 4 people. We’ll have a team-building activity right after opening ceremony if you’d like to find teammates.' },
    { q: 'Are there travel reimbursements?', a: 'We are not able to provide travel reimbursements at this time.' },
    { q: 'What should I bring?', a: 'Your laptop, charger, headphones, deodorant, and a pillow or blanket.' },
    { q: 'When can we start working? Can I use a previous project?', a: 'You cannot start until after opening ceremony. You may brainstorm beforehand, but you cannot work on a previous project. Frameworks are okay if you credit them in your README and clearly distinguish what you made.' },
    { q: 'How many challenges can I apply for?', a: 'As many as you want!' },
    { q: 'Do I have to stay overnight?', a: 'No. You can leave and come back if you prefer.' },
    { q: 'What kind of activities will there be?', a: 'There will be workshops and activities to take a break, meet other hackers, and connect with our wonderful sponsors. The full schedule will be posted closer to the event.' },
    { q: 'What is a hackathon?', a: 'A hackathon is an event where students “hack” together to create an app, website, game, or other project in 24–48 hours. There will be no malicious hacking.' },
    { q: 'Will hardware be available?', a: 'We do not have hardware available, but you’re welcome to bring your own. Due to building fire codes, soldering kits are not allowed in the venue.' },
    { q: 'Are you sending acceptances? Is there a deadline or waitlist?', a: 'Yes, we’ll confirm spots by email before the event; the exact date will be announced when registration opens. Registration closes once we reach the maximum number of hackers we can support, and a local waitlist will open on event day for unfilled spots.' },
    { q: 'How do I sign up to be a mentor, judge, or volunteer?', a: <>Those sign-up forms aren’t open yet. Email {contactLink} and we’ll reach out as soon as they are.</> },
    { q: 'I have a different question!', a: <>Email us at {contactLink} and our team will get back to you.</> },
  ];

  const half = Math.ceil(faq.length / 2);
  return [faq.slice(0, half), faq.slice(half)];
}
