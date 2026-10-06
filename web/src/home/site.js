export const CONTACT_EMAIL = 'hello@peachhacks.com';
export const SPONSOR_EMAIL = 'sponsors@peachhacks.com';
export const SPONSOR_FORM_PATH = '/sponsor-form';
export const REGISTER_PANEL_HASH = '#register';
export const REGISTER_PANEL_HREF = `/${REGISTER_PANEL_HASH}`;

export const EVENT_DATES = 'February 5–7, 2027';
export const EVENT_PLACE = 'Georgia State University, Atlanta';

export const sectionLinks = [
  { id: 'tracks', label: 'Tracks' },
  { id: 'partners', label: 'Partners' },
  { id: 'schedule', label: 'Schedule' },
  { id: 'faq', label: 'FAQ' },
];

export const socialLinks = [
  { label: 'Instagram', href: 'https://www.instagram.com/colorstackatgsu', icon: '/assets/instagram_logo.png' },
  { label: 'LinkedIn', href: 'https://www.linkedin.com/company/colorstack-gsu/', icon: '/assets/linkedin_logo.png' },
  { label: 'Discord', href: 'https://discord.gg/jksZ2gaZnX', icon: '/assets/discord_logo.png' },
];

// The header and footer are shared with the standalone pages, where section
// links have to lead back to the homepage first.
export const isHomePage = () => /^\/(index\.html)?$/.test(window.location.pathname);
export const sectionHref = (id) => (isHomePage() ? `#${id}` : `/#${id}`);

export const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
