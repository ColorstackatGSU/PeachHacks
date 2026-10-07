export const CONTACT_EMAIL = 'hello@peachhacks.com';
export const SPONSOR_EMAIL = 'sponsors@peachhacks.com';
export const SPONSOR_FORM_PATH = '/sponsor-form';
export const REGISTER_PANEL_HASH = '#register';
export const REGISTER_PANEL_HREF = `/${REGISTER_PANEL_HASH}`;

// The year and dates also appear in the HTML entry pages (titles, meta tags,
// JSON-LD), which cannot import this file.
export const EVENT_YEAR = 2027;
export const EVENT_DATES = `February 5–7, ${EVENT_YEAR}`;
export const EVENT_DATES_SHORT = 'Feb 5–7';
export const EVENT_PLACE = 'Georgia State University, Atlanta';
export const EVENT_THEME = 'Midnight in the City';
export const CODE_OF_CONDUCT_URL = 'https://github.com/MLH/mlh-policies/blob/main/code-of-conduct.md';

export const sectionLinks = [
  { id: 'about', label: 'About' },
  { id: 'faq', label: 'FAQ' },
  { id: 'partners', label: 'Partners' },
];

export const socialLinks = [
  { label: 'Instagram', href: 'https://www.instagram.com/colorstackatgsu', icon: '/assets/instagram_logo.png' },
  { label: 'LinkedIn', href: 'https://www.linkedin.com/company/colorstack-gsu/', icon: '/assets/linkedin_logo.png' },
  { label: 'Discord', href: 'https://discord.gg/jksZ2gaZnX', icon: '/assets/discord_logo.png' },
];

// The homepage is the only page rendered at build time, so without a window
// this is the homepage.
const onServer = typeof window === 'undefined';

// The header and footer are shared with the standalone pages, where section
// links have to lead back to the homepage first.
export const isHomePage = () => onServer || /^\/(index\.html)?$/.test(window.location.pathname);
export const sectionHref = (id) => (isHomePage() ? `#${id}` : `/#${id}`);
