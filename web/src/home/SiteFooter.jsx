import { setMotionPaused, useMotionPaused } from './reducedMotion.js';
import { useRegistrationCta } from './registration.js';
import {
  CODE_OF_CONDUCT_URL, CONTACT_EMAIL, EVENT_DATES, EVENT_PLACE, EVENT_YEAR, SPONSOR_EMAIL, SPONSOR_FORM_PATH, isHomePage,
  sectionHref, sectionLinks, socialLinks,
} from './site.js';

function SiteFooter() {
  const cta = useRegistrationCta();
  const motionPaused = useMotionPaused();

  // Pausing also unpins the hero, which changes the height of the page above
  // the footer; the button stays where the visitor pressed it.
  const toggleMotion = (event) => {
    const button = event.currentTarget;
    const before = button.getBoundingClientRect().top;
    setMotionPaused(!motionPaused);
    window.scrollBy({ top: button.getBoundingClientRect().top - before, behavior: 'instant' });
  };

  return (
    <footer className="site-footer">
      <div className="footer-art-band" aria-hidden="true">
        <img className="footer-art" src="/assets/Footer.svg" alt="" width="904" height="57" loading="lazy" decoding="async" />
      </div>
      <div className="footer-body">
        <div className="footer-inner">
          <div className="footer-brand">
            <a className="footer-logo-link" href={isHomePage() ? '#top' : '/'} aria-label="PeachHacks home">
              <img className="footer-logo" src="/assets/logo.svg" alt="" width="210" height="79" loading="lazy" decoding="async" />
            </a>
            <p className="footer-event">{EVENT_DATES}<br />{EVENT_PLACE}</p>
            <ul className="footer-social">
              {socialLinks.map((social) => (
                <li key={social.label}>
                  <a className="footer-social-link" href={social.href} target="_blank" rel="noopener noreferrer" aria-label={`ColorStack at GSU on ${social.label} (opens in a new tab)`}>
                    <img src={social.icon} alt="" width="32" height="32" loading="lazy" decoding="async" />
                  </a>
                </li>
              ))}
            </ul>
          </div>

          <nav className="footer-column" aria-label="Sections">
            <p className="footer-heading">Explore</p>
            <ul>
              {sectionLinks.map((link) => (
                <li key={link.id}><a className="footer-link" href={sectionHref(link.id)}>{link.label}</a></li>
              ))}
            </ul>
          </nav>

          <nav className="footer-column" aria-label="Take part">
            <p className="footer-heading">Take part</p>
            <ul>
              <li><a className="footer-link" href={cta.href}>{cta.label}</a></li>
              <li><a className="footer-link" href={SPONSOR_FORM_PATH}>Sponsor PeachHacks</a></li>
              <li><a className="footer-link" href={CODE_OF_CONDUCT_URL} target="_blank" rel="noopener noreferrer" aria-label="Code of Conduct (opens in a new tab)">Code of Conduct</a></li>
            </ul>
          </nav>

          <div className="footer-column footer-contact">
            <p className="footer-heading">Contact</p>
            <ul>
              <li><span className="footer-contact-label">General</span><a className="footer-link" href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a></li>
              <li><span className="footer-contact-label">Sponsorship</span><a className="footer-link" href={`mailto:${SPONSOR_EMAIL}`}>{SPONSOR_EMAIL}</a></li>
            </ul>
          </div>
        </div>
        <div className="footer-legal">
          <p className="footer-legal-text">PeachHacks {EVENT_YEAR} <span aria-hidden="true">·</span> Hosted by ColorStack at Georgia State University</p>
          <button type="button" className="motion-toggle" aria-pressed={motionPaused} onClick={toggleMotion}>
            {motionPaused ? 'Play animation' : 'Pause animation'}
          </button>
        </div>
      </div>
    </footer>
  );
}

export default SiteFooter;
