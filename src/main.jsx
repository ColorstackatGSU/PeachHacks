import React, { useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const events = [
  { day: 'FRI', time: '6:00 PM', title: 'Check-in + welcome', note: 'Find your people' },
  { day: 'FRI', time: '8:00 PM', title: 'Opening ceremony', note: 'Let’s begin' },
  { day: 'SAT', time: '9:00 AM', title: 'Workshops + hacking', note: 'Make the thing' },
  { day: 'SAT', time: '7:00 PM', title: 'Dinner + demos', note: 'Share the progress' },
  { day: 'SUN', time: '2:00 PM', title: 'Judging + closing', note: 'Big finish' },
];

function App() {
  useEffect(() => {
    const page = document.querySelector('.page-shell');
    const moveStars = (event) => {
      page.style.setProperty('--star-x', `${(event.clientX / window.innerWidth - 0.5) * 14}px`);
      page.style.setProperty('--star-y', `${(event.clientY / window.innerHeight - 0.5) * 10}px`);
    };
    const scrollStars = () => page.style.setProperty('--star-scroll', `${Math.min(window.scrollY * 0.08, 120)}px`);
    window.addEventListener('pointermove', moveStars);
    window.addEventListener('scroll', scrollStars, { passive: true });
    return () => {
      window.removeEventListener('pointermove', moveStars);
      window.removeEventListener('scroll', scrollStars);
    };
  }, []);

  return (
    <main className="page-shell">
      <section className="intro" aria-labelledby="page-title">
        <img className="hero-logo" src="/assets/logo.svg" alt="PeachHacks" />
        <img className="hero-moon" src="/assets/moon.svg" alt="" aria-hidden="true" />
        <div className="hero-card">
          <h1 id="page-title">Join PeachHacks!</h1>
          <p className="intro-copy">We're excited to bring together students, mentors, and industry professionals for a weekend of learning, building, and networking this February. Exact dates and registration details are on the way.</p>
          <a className="register-button" href="#details">Register your interest <span>↗</span></a>
        </div>
      </section>

      <section className="about-section" aria-labelledby="about-title">
        <img className="about-cloud" src="/assets/cloud-divider.svg" alt="" aria-hidden="true" />
        <p className="about-copy">A weekend for curious minds, bold ideas, and the people who make tech feel more like home. Come with a team, a sketch, or just yourself — and make something useful, weird, and yours.</p>
      </section>

      <section className="schedule-section" id="schedule" aria-labelledby="schedule-title">
        <img className="schedule-art" src="/assets/schedule-rocks.svg" alt="" aria-hidden="true" />
        <div className="schedule-content">
          <div className="schedule-heading">
            <h2 id="schedule-title">Schedule</h2>
          </div>
          <div className="event-list">
            {events.map((event, index) => (
              <article className="event" key={`${event.day}-${event.time}`} style={{ '--delay': `${index * 80}ms` }}>
                <div className="event-time"><b>{event.day}</b><span>{event.time}</span></div>
                <h3>{event.title}</h3>
                <p>{event.note}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="partners-section" id="details" aria-labelledby="partners-title">
        <h2 id="partners-title">Our<br /><span>partners.</span></h2>
        <p>PeachHacks is a student-centered, beginner-friendly weekend hosted by ColorStack. We’re looking for partners who want to support the next generation of builders through mentorship, workshops, prizes, and food.</p>
        <a className="sponsor-email" href="mailto:sponsors@peachhacks.org">Sponsor PeachHacks <span>↗</span></a>
      </section>

      <section className="faq-section" aria-labelledby="faq-title">
        <h2 id="faq-title">FAQ<span>.</span></h2>
        <div className="faq-list">
          <details><summary>Is PeachHacks free to attend?</summary><p>Yes! Food will be provided for the duration of the event. We’ll also have swag and prizes.</p></details>
          <details><summary>Where is the event? Is it in person or virtual?</summary><p>The event is planned to be in person at Georgia State University, in the XXXX Building at STREET ADDRESS. Parking information will be shared here once confirmed: <a href="#">campus parking site ↗</a></p></details>
          <details><summary>Who can attend? What if I have no experience?</summary><p>PeachHacks is open to students and is beginner friendly, with workshops and mentors available throughout the event. Attendees must be at least 13 years old. If you’re under 18, you’ll need the university liability form: <a href="#">form link coming soon ↗</a></p></details>
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
          <details><summary>How do I sign up to be a mentor, judge, or volunteer?</summary><p>You’ll be able to sign up here when those forms open: <a href="#">link coming soon ↗</a></p></details>
          <details><summary>I have a different question!</summary><p>Email us at <a href="mailto:hello@peachhacks.org">hello@peachhacks.org</a> and our team will get back to you.</p></details>
        </div>
      </section>

      <footer className="site-footer">
        <p>PeachHacks <span>·</span> Hosted by ColorStack</p>
        <a href="mailto:hello@colorstack.org">Say hello <span>↗</span></a>
      </footer>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
