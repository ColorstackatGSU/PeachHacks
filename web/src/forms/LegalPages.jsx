import { CODE_OF_CONDUCT_URL, CONTACT_EMAIL } from '../home/site.js';
import { Card, PageShell } from './PageShell.jsx';

const UPDATED = 'October 14, 2026';

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

// These pages cover the hacker platform and PeachBot only. What registration collects is
// explained on the registration form. Keep every sentence true of the code: check the
// backend before changing a claim here.
export function PrivacyPage() {
  return (
    <Legal titleId="privacy-title" title="Privacy Policy">
      <p>
        This policy covers the PeachHacks hacker platform at platform.peachhacks.com and PeachBot, our bot in the
        PeachHacks Discord server. PeachHacks is run by ColorStack at GSU. What you give us when you register for
        PeachHacks is explained on the registration form. Questions go to {contact}.
      </p>

      <h2>The hacker platform</h2>
      <ul>
        <li>Only accepted hackers can sign in. The platform shows you details from your accepted application, such as your name, school and ticket.</li>
        <li>If you choose a password, we store it only in scrambled (hashed) form and cannot read it.</li>
        <li>Your browser keeps a sign-in token for up to 30 days so you stay signed in. We do not use advertising or analytics trackers.</li>
        <li>Your card holds a short bio, GitHub and LinkedIn links, and whether you are looking for a team. It starts with the GitHub and LinkedIn links from your application, and you can change or clear anything on it.</li>
        <li>We store the team you are on and requests you send to join one.</li>
      </ul>

      <h2>Signing in with Google</h2>
      <p>
        &ldquo;Continue with Google&rdquo; is optional. If you use it, Google tells us the email address of the
        account you choose and Google&rsquo;s identifier for that account. We ask for nothing else, so we cannot
        see your contacts, files, calendar, mail or anything else in your Google account.
      </p>
      <ul>
        <li>We use the email address to find your accepted application and sign you in.</li>
        <li>We store Google&rsquo;s identifier with your platform account.</li>
        <li>We do not share what we receive from Google with anyone, and we do not use it for advertising.</li>
        <li>To stop, remove PeachHacks under &ldquo;Third-party apps and services&rdquo; in your Google account. You can still sign in with your email and a password.</li>
      </ul>

      <h2>Discord and PeachBot</h2>
      <ul>
        <li>
          Connecting Discord on the platform is optional. If you connect it, Discord tells us your Discord user ID
          and username and lets us add you to the PeachHacks server. We store both with your application and use
          them to give you the Hacker role, which is removed if your acceptance is withdrawn.
        </li>
        <li>
          When you join the server, PeachBot posts a welcome picture made from your Discord display name and
          avatar, and remembers your Discord user ID so it welcomes you only once.
        </li>
      </ul>

      <h2>What other hackers see</h2>
      <ul>
        <li>
          Once you sign in, you are listed on the Hackers page, where other accepted hackers see your name, school,
          card, team, and Discord username if you connected one. To leave the list, turn off &ldquo;Show me on the
          Hackers page&rdquo; on your card.
        </li>
        <li>
          If you are on a team, every accepted hacker sees the team on the Teams page with its members&rsquo; names
          and schools, whether or not you are on the Hackers page.
        </li>
        <li>If you ask to join a team, its leader sees your name, school, Discord username and your message.</li>
        <li>Your email address, phone number and age are never shown to other hackers.</li>
      </ul>

      <h2>Companies that handle it for us</h2>
      <ul>
        <li>Vercel hosts our websites, Railway runs our server and Supabase hosts our database.</li>
        <li>Resend delivers our emails, such as platform sign-in links.</li>
        <li>Google, if you sign in with it, and Discord, which runs our server and PeachBot.</li>
      </ul>
      <p>We do not sell your information to anyone.</p>

      <h2>Questions and removal</h2>
      <p>
        To have your platform account, card or Discord connection removed, or for any other question about this
        policy, email {contact}. If we change this policy, we update the date at the top.
      </p>
    </Legal>
  );
}

export function TermsPage() {
  return (
    <Legal titleId="terms-title" title="Terms of Service">
      <p>
        These terms cover the PeachHacks hacker platform at platform.peachhacks.com and PeachBot in the PeachHacks
        Discord server, run by ColorStack at GSU. By signing in to the platform or connecting Discord, you agree to
        them. Questions go to {contact}.
      </p>

      <h2>Your account</h2>
      <ul>
        <li>The platform is for accepted hackers only. One account per person; keep your password to yourself.</li>
        <li>Your ticket and its QR code are for you alone. Do not give them to someone else.</li>
        <li>You may connect one Discord account. The Hacker role is for accepted hackers and is removed if your acceptance is withdrawn.</li>
      </ul>

      <h2>How to behave</h2>
      <p>
        The {external(CODE_OF_CONDUCT_URL, 'MLH Code of Conduct')} applies on the platform and in our Discord server,
        including what you write on your card, your team&rsquo;s name and description, and messages you send with a
        request to join a team. Do not use other hackers&rsquo; information for anything other than forming a team,
        and do not try to break or overload the platform.
      </p>
      <p>
        We may edit or remove content, and remove an account, a team or the Hacker role, for breaking these rules.
      </p>

      <h2>No guarantees</h2>
      <p>
        We work to keep the platform and PeachBot running and correct, but they are provided as they are and may
        be unavailable at times. If we change these terms, we update the date at the top.
      </p>
    </Legal>
  );
}
