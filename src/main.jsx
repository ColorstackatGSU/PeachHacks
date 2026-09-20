import React, { useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const scheduleData = [
  {
    day: 'Day 1:',
    events: [
      { time: '9:00 AM', title: 'Check-in' },
      { time: '10:00 AM', title: 'Opening Ceremony' },
      { time: '11:00 AM', title: 'Hacking Begins' },
    ],
  },
  {
    day: 'Day 2:',
    events: [
      { time: '9:00 AM', title: 'Mentorship Sessions' },
      { time: '10:00 AM', title: 'Testorship Sessions' },
      { time: '12:00 AM', title: 'Hacking Ceremony' },
      { time: '1:00 AM', title: 'Hacking Ceremony' },
    ],
  },
];

const introStars = [
  ['7%', '12%', '14px', '-1.2s'], ['18%', '24%', '10px', '-3.8s'], ['31%', '9%', '18px', '-2.4s'],
  ['44%', '20%', '12px', '-5.1s'], ['58%', '7%', '16px', '-4.2s'], ['70%', '26%', '11px', '-.8s'],
  ['83%', '13%', '19px', '-6.4s'], ['94%', '29%', '12px', '-2.9s'], ['12%', '42%', '9px', '-4.7s'],
  ['39%', '35%', '13px', '-1.9s'], ['64%', '39%', '10px', '-5.8s'], ['88%', '47%', '15px', '-3.3s'],
];

const partnerStars = [
  ['5%', '8%', '12px', '-2.2s'], ['15%', '18%', '9px', '-4.9s'], ['27%', '6%', '16px', '-1.1s'],
  ['40%', '14%', '11px', '-5.6s'], ['54%', '5%', '14px', '-3.7s'], ['68%', '19%', '10px', '-.4s'],
  ['80%', '9%', '17px', '-6.1s'], ['93%', '23%', '12px', '-2.8s'], ['9%', '33%', '15px', '-4.1s'],
  ['22%', '45%', '10px', '-1.7s'], ['35%', '31%', '13px', '-5.2s'], ['49%', '40%', '9px', '-3.2s'],
  ['63%', '29%', '16px', '-.9s'], ['76%', '48%', '11px', '-4.5s'], ['89%', '38%', '14px', '-2.1s'],
  ['4%', '63%', '10px', '-5.9s'], ['18%', '76%', '15px', '-3.6s'], ['31%', '88%', '9px', '-1.4s'],
  ['47%', '69%', '13px', '-4.8s'], ['61%', '84%', '11px', '-2.6s'], ['78%', '73%', '16px', '-6.5s'],
  ['94%', '91%', '10px', '-.6s'],
];

function StarField({ className, stars }) {
  return (
    <div className={`section-stars ${className}`} aria-hidden="true">
      {stars.map(([x, y, size, delay], index) => (
        <img
          className="section-star"
          key={`${x}-${y}-${index}`}
          src="/assets/star.svg"
          alt=""
          style={{ '--star-x': x, '--star-y': y, '--star-size': size, '--star-delay': delay }}
        />
      ))}
    </div>
  );
}

function App() {
  useEffect(() => {
    const page = document.querySelector('.page-shell');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let frame = 0;
    let pointerFrame = 0;

    const moveStarsOnScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        page.style.setProperty('--star-scroll', `${Math.min(window.scrollY * 0.16, 260)}px`);
      });
    };

    const moveStarsWithPointer = (event) => {
      cancelAnimationFrame(pointerFrame);
      pointerFrame = requestAnimationFrame(() => {
        const x = (event.clientX / window.innerWidth - 0.5) * 2;
        const y = (event.clientY / window.innerHeight - 0.5) * 2;
        page.style.setProperty('--star-pointer-x', `${x.toFixed(3)}`);
        page.style.setProperty('--star-pointer-y', `${y.toFixed(3)}`);
      });
    };

    if (!reducedMotion.matches) {
      moveStarsOnScroll();
      window.addEventListener('scroll', moveStarsOnScroll, { passive: true });
      window.addEventListener('pointermove', moveStarsWithPointer, { passive: true });
    }

    return () => {
      cancelAnimationFrame(frame);
      cancelAnimationFrame(pointerFrame);
      window.removeEventListener('scroll', moveStarsOnScroll);
      window.removeEventListener('pointermove', moveStarsWithPointer);
    };
  }, []);

  return (
    <main className="page-shell">
      <div className="page-stars" aria-hidden="true" />
      <section className="intro" aria-labelledby="page-title">
        <StarField className="intro-stars" stars={introStars} />
        <img className="hero-art" src="/assets/Hero.svg" alt="" aria-hidden="true" />
        <div className="hero-water-lines" aria-hidden="true">
          {Array.from({ length: 15 }, (_, idx) => (
            <span className="hero-water-line" key={idx} />
          ))}
        </div>
        <img className="top-cloud cloud-one" src="/assets/Cloud.svg" alt="" aria-hidden="true" />
        <img className="top-cloud cloud-two" src="/assets/Cloud.svg" alt="" aria-hidden="true" />
        <img className="top-cloud cloud-three" src="/assets/Cloud.svg" alt="" aria-hidden="true" />
        <div className="hero-logo-circle" aria-hidden="true" />
        <img className="hero-logo" src="/assets/logo.svg" alt="PeachHacks" />
        <div className="hero-card window-card">
          <span className="banner-tow-line" aria-hidden="true" />
          <div className="window-content">
            <h1 id="page-title">Join PeachHacks!</h1>
            <p className="intro-copy">We’re excited to bring together students, mentors, and industry professionals for a weekend of learning, building, and networking this February. Exact dates and registration details are on the way.</p>
            <a className="register-button" href="#details">Register</a>
          </div>
        </div>
      </section>

      <section className="schedule-section" id="schedule" aria-labelledby="schedule-title">
        <div className="schedule-board">
          <img className="schedule-art" src="/assets/Brick Wall.svg" alt="" aria-hidden="true" />
          <div className="schedule-heading">
            <h2 id="schedule-title">Schedule</h2>
          </div>
          <div className="schedule-body">
            {scheduleData.map((group) => (
              <div className="schedule-day-group" key={group.day}>
                <span className="schedule-day-label">{group.day}</span>
                <div className="schedule-day-events">
                  {group.events.map((event, idx) => (
                    <div className="schedule-event-row" key={idx}>
                      <span className="schedule-event-time">{event.time}</span>
                      <span className="schedule-event-dash">-</span>
                      <span className="schedule-event-title">{event.title}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="partners-section" id="details" aria-labelledby="partners-title">
        <StarField className="partners-stars" stars={partnerStars} />
        <img className="section-cloud-divider" src="/assets/cloud-divider.svg" alt="" aria-hidden="true" />
        <img className="partners-moon" src="/assets/moon.svg" alt="" aria-hidden="true" />
        <h2 id="partners-title">Our<br /><span>partners.</span></h2>
        <p>PeachHacks is a student-centered, beginner-friendly weekend hosted by ColorStack. We’re looking for partners who want to support the next generation of builders through mentorship, workshops, prizes, and food.</p>
        <div className="partners-grid">
          {Array.from({ length: 6 }, (_, idx) => (
            <div className="partner-box" key={idx}>
              <span className="partner-box-label">Partner</span>
              <span className="partner-box-text">TBA</span>
            </div>
          ))}
        </div>
        <a className="sponsor-email" href="mailto:sponsors@peachhacks.org">This could be YOU! Sponsor PeachHacks <span>↗</span></a>
      </section>

      <section className="faq-section" aria-labelledby="faq-title">
        <h2 id="faq-title">FAQ<span>.</span></h2>
        <div className="faq-list">
          <details><summary>Is PeachHacks free to attend?</summary><p>Yes! Food will be provided for the duration of the event. We’ll also have swag and prizes.</p></details>
          <details><summary>Where is the event? Is it in person or virtual?</summary><p>The event is planned to be in person at Georgia State University, in the XXXX Building at STREET ADDRESS. Parking information will be shared here once confirmed: campus parking site coming soon.</p></details>
          <details><summary>Who can attend? What if I have no experience?</summary><p>PeachHacks is open to students and is beginner friendly, with workshops and mentors available throughout the event. Attendees must be at least 13 years old. If you’re under 18, you’ll need the university liability form: form link coming soon.</p></details>
          <details><summary>What is the team size limit?</summary><p>Teams should be between 1 and 4 people. We’ll have a team-building activity right after opening ceremony if you’d like to find teammates.</p></details>
          <details><summary>Are there travel reimbursements?</summary><p>We are not able to provide travel reimbursements at this time.</p></details>
          <details><summary>What should I bring?</summary><p>Your laptop, charger, headphones, deodorant, and a pillow or blanket.</p></details>
          <details><summary>When can we start working? Can I use a previous project?</summary><p>You cannot start until after opening ceremony. You may brainstorm beforehand, but you cannot work on a previous project. Frameworks are okay if you credit them in your README and clearly distinguish what you made.</p></details>
          <details><summary>How many challenges can I apply for?</summary><p>As many as you want!</p></details>
          <details><summary>Do I have to stay overnight?</summary><p>No. You can leave and come back if you prefer.</p></details>
          <details><summary>What kind of activities will there be?</summary><p>There will be workshops and activities to take a break, meet other hackers, and connect with our wonderful sponsors. The full schedule will be posted closer to the event.</p></details>
          <details><summary>What is a hackathon?</summary><p>A hackathon is an event where students “hack” together to create an app, website, game, or other project in 24–48 hours. There will be no malicious hacking.</p></details>
          <details><summary>Will hardware be available?</summary><p>We do not have hardware available, but you’re welcome to bring your own. Due to building fire codes, soldering kits are not allowed in the venue.</p></details>
          <details><summary>Are you sending acceptances? Is there a deadline or waitlist?</summary><p>We’ll send acceptances XX days before the event. Applications will close once we reach the maximum number of hackers we can support, and a local waitlist will open on event day for unfilled spots.</p></details>
          <details><summary>How do I sign up to be a mentor, judge, or volunteer?</summary><p>You’ll be able to sign up here when those forms open: link coming soon.</p></details>
          <details><summary>I have a different question!</summary><p>Email us at <a href="mailto:hello@peachhacks.org">hello@peachhacks.org</a> and our team will get back to you.</p></details>
        </div>
      </section>

      <footer className="site-footer">
        <img className="footer-art" src="/assets/Footer.svg" alt="" aria-hidden="true" />
        <div className="site-footer-content">
          <p>PeachHacks <span>·</span> Hosted by ColorStack</p>
          <nav className="footer-links" aria-label="Footer navigation">
            <a href="#schedule">Schedule</a>
            <a href="#details">Sponsors</a>
            <a href="mailto:hello@peachhacks.org">Contact</a>
          </nav>
        </div>
      </footer>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
