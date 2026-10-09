import { CODE_OF_CONDUCT_URL, CONTACT_EMAIL } from '../home/site.js';
import { MLH_LINKS } from './options.js';
import { Card, PageShell } from './PageShell.jsx';

const UPDATED = 'October 7, 2026';

const contact = <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>;

const external = (href, label) => (
  <a href={href} target="_blank" rel="noopener noreferrer">{label}</a>
);

function Legal({ titleId, title, children }) {
  return (
    <PageShell>
      <Card title={title} titleId={titleId} intro={`Last updated ${UPDATED}`}>
        <div className="pf-prose">{children}</div>
      </Card>
    </PageShell>
  );
}

export function PrivacyPage() {
  return (
    <Legal titleId="privacy-title" title="Privacy Policy">
      <p>
        PeachHacks is a student hackathon run by ColorStack at Georgia State University. This page explains what
        we collect when you use peachhacks.com, the hacker platform at platform.peachhacks.com and PeachBot in our
        Discord server, what we do with it, and who else sees it. Questions or requests go to {contact}.
      </p>

      <h2>What we collect</h2>
      <h3>When you pre-register</h3>
      <p>Your name, email address, school email address and school.</p>

      <h3>When you register</h3>
      <ul>
        <li>Your name, age, phone number, email address, school email address, school, level of study, expected graduation month and year, and country of residence.</li>
        <li>
          If you choose to give them: dietary restrictions, T-shirt size, major, LinkedIn and GitHub links, your resume, and
          answers to demographic questions (gender, pronouns, race or ethnicity, sexual orientation, whether you
          identify with an underrepresented group, and the highest level of education you have completed). The
          demographic questions are optional and do not affect whether you are accepted.
        </li>
        <li>Your answers to the Major League Hacking (MLH) agreements on the form.</li>
      </ul>

      <h3>When you use the hacker platform</h3>
      <ul>
        <li>A password, which we store only in scrambled (hashed) form and cannot read.</li>
        <li>If you sign in with Google: your Google account&rsquo;s identifier and email address, as described under &ldquo;Information from your Google account&rdquo; below.</li>
        <li>What you add to your card: a short bio, a GitHub link and a LinkedIn link.</li>
        <li>Your team, and requests you send to join one.</li>
        <li>If you connect Discord: your Discord account&rsquo;s ID and username. We use them to give you the Hacker role in our server.</li>
      </ul>

      <h3>At the event</h3>
      <p>When your ticket is scanned, we record that you checked in, and at which workshop if it is scanned there.</p>

      <h3>Automatically</h3>
      <p>
        Our servers see your IP address when you use these sites and use it to stop abuse. The hacker platform
        keeps a sign-in token in your browser so you stay signed in. We do not use advertising or analytics
        trackers.
      </p>

      <h2>Information from your Google account</h2>
      <p>
        &ldquo;Continue with Google&rdquo; on the hacker platform is optional. If you use it, Google tells us two
        things about the account you choose: its email address and Google&rsquo;s identifier for it. We ask for
        nothing else, so we cannot see your contacts, files, calendar, mail or anything else in your Google
        account.
      </p>
      <ul>
        <li>We use the email address to find your accepted PeachHacks application, and the identifier to recognise you the next time you sign in. That is all we use them for.</li>
        <li>We store the identifier with your hacker platform account. The email address is one we already have from your application.</li>
        <li>We do not share what we receive from Google with anyone, and we do not use it for advertising.</li>
        <li>To stop, remove PeachHacks Hacker Platform under &ldquo;Third-party apps and services&rdquo; in your Google account, and email {contact} if you want the stored identifier deleted. You can still sign in with your email and a password.</li>
      </ul>

      <h2>How we use it</h2>
      <ul>
        <li>To review applications, decide who we can accept, and tell you the result.</li>
        <li>To run the event: tickets and check-in, food and T-shirt orders, teams, and the Discord server.</li>
        <li>To email you about your application and the event. Announcement emails have an unsubscribe link; emails about your own application or ticket do not, because you need them to take part.</li>
        <li>To understand who PeachHacks reaches, using totals that do not identify anyone.</li>
        <li>To invite you to future PeachHacks events.</li>
      </ul>

      <h2>Who else sees it</h2>
      <h3>Major League Hacking</h3>
      <p>
        We have applied for PeachHacks to be an MLH Member Event. If it is accepted, we share your registration
        information with MLH as described in the MLH agreements you ticked on the registration form, and MLH
        handles it under the {external(MLH_LINKS.privacyPolicy, 'MLH Privacy Policy')}. If PeachHacks does not
        become an MLH Member Event, your information is not shared with MLH.
      </p>

      <h3>Sponsors</h3>
      <p>
        Uploading a resume is optional. If you upload one and are accepted, PeachHacks sponsors receive it for
        recruiting, together with your name, personal and school email, school, level of study, graduation
        month and year, major, and LinkedIn and GitHub links. If you do not upload a resume, sponsors receive nothing about you individually. To take
        your resume back, email {contact} and we will remove it; a sponsor who already received it has to be
        asked separately. We may also give sponsors and Georgia State University totals, such as how many
        people attended, that do not identify anyone.
      </p>

      <h3>Other accepted hackers</h3>
      <p>
        On the hacker platform, other accepted hackers can see your name, school, the card you filled in, your
        Discord username if you connected one, and your team. You can take yourself off the Hackers page from
        your Home page; your teammates still see you on your team. Your email address, phone number, age and
        demographic answers are never shown to other hackers.
      </p>

      <h3>Companies that run things for us</h3>
      <p>These services store or handle data on our behalf so the sites can work:</p>
      <ul>
        <li>Supabase, Railway and Vercel, which host our database, server and websites.</li>
        <li>Resend, which delivers our emails.</li>
        <li>Google, if you sign in with Google or add your ticket to Google Wallet.</li>
        <li>Discord, if you join our server or connect your Discord account.</li>
      </ul>
      <p>We do not sell your information to anyone.</p>

      <h2>How long we keep it</h2>
      <p>
        We keep your information after the event so we can invite you to future PeachHacks events, until you ask
        us to delete it. Email {contact} from the address you applied with and we will delete your application,
        resume and platform account. Information that MLH or a sponsor already received under the sections above
        is theirs to delete; we can tell you who to contact.
      </p>

      <h2>Your choices</h2>
      <ul>
        <li>Unsubscribe from announcements with the link at the bottom of any announcement email.</li>
        <li>Edit or clear your card, and hide yourself from the Hackers page, on the hacker platform.</li>
        <li>Ask us for a copy of what we hold about you, to correct it, or to delete it, by emailing {contact}.</li>
      </ul>

      <h2>If you are under 18</h2>
      <p>
        PeachHacks is for current university students, and a few university students are under 18. We ask your age
        on the registration form so organizers know. We do not knowingly collect information from children under
        13; if you believe a child has registered, email {contact} and we will remove it.
      </p>

      <h2>Keeping it safe</h2>
      <p>
        These sites use encrypted connections, passwords and sign-in tokens are stored in hashed form, and only
        PeachHacks organizers with an admin account can see applications. No system is perfectly secure; if
        something goes wrong that affects your information, we will tell you.
      </p>

      <h2>Changes</h2>
      <p>
        If we change this policy we will update the date at the top, and email registered hackers when a change
        affects how their information is used or shared.
      </p>
    </Legal>
  );
}

export function TermsPage() {
  return (
    <Legal titleId="terms-title" title="Terms of Service">
      <p>
        These terms cover peachhacks.com, the hacker platform at platform.peachhacks.com, PeachBot in our Discord
        server, and taking part in PeachHacks, a student hackathon run by ColorStack at Georgia State University.
        By registering or using these sites you agree to them. Questions go to {contact}.
      </p>

      <h2>Who can take part</h2>
      <ul>
        <li>PeachHacks is for current university students.</li>
        <li>Georgia State University students may take part at any age. Students of other schools must be 18 or older.</li>
        <li>Space is limited, so registering does not guarantee a place. We email you when a decision is made.</li>
        <li>The information you give us must be true and your own. One registration per person.</li>
      </ul>

      <h2>Your account and ticket</h2>
      <ul>
        <li>The hacker platform is for accepted hackers. Keep your password to yourself; you are responsible for what is done with your account.</li>
        <li>Your ticket and its QR code are for you alone. Do not give them to someone else.</li>
        <li>You may connect one Discord account to your registration. The Hacker role in our Discord server is for accepted hackers and is removed if your acceptance is withdrawn.</li>
      </ul>

      <h2>How to behave</h2>
      <p>
        Everyone at PeachHacks, in person, on the hacker platform and in our Discord server, follows the{' '}
        {external(CODE_OF_CONDUCT_URL, 'MLH Code of Conduct')}. On the hacker platform that includes what you
        write on your card, your team&rsquo;s name and description, and messages you send with a request to join a
        team. Do not use these sites to harass anyone, to collect other hackers&rsquo; information for anything
        other than forming a team, or to try to break or overload them.
      </p>
      <p>
        We may edit or remove content that breaks these rules, and we may withdraw an acceptance or ask someone to
        leave the event for breaking them.
      </p>

      <h2>What you build</h2>
      <p>
        You and your team own what you build at PeachHacks. If your project uses other people&rsquo;s code, data
        or artwork, make sure you are allowed to use it.
      </p>

      <h2>Photos and video</h2>
      <p>
        We take photos and video at PeachHacks and use them on our website and social media, in recaps, and in
        material we show to sponsors. By attending you agree that you may appear in them. If you would rather not,
        tell an organizer at the event or email {contact}, and we will keep you out of what we publish or take
        down something you are in.
      </p>

      <h2>Your information</h2>
      <p>
        What we collect and who sees it is explained in our <a href="/privacy">Privacy Policy</a>.
      </p>

      <h2>The sites</h2>
      <p>
        We work to keep these sites running and correct, but they are provided as they are, without guarantees,
        and may be unavailable at times. Dates, the schedule and other event details can change; we will tell
        registered hackers by email when they do.
      </p>

      <h2>Changes</h2>
      <p>
        If we change these terms we will update the date at the top. Continuing to use the sites after a change
        means you accept it.
      </p>
    </Legal>
  );
}
