const STORAGE_KEY = 'peachhacks:registration-preview';
const PARAMETER = 'preview';

export const PREVIEW_HEADER = 'X-Registration-Preview';

// Organizers open the site once through a link from the admin site that carries a
// preview key. The key is kept for the tab and taken out of the address bar, so it
// is not shared by accident with a copied URL.
function takeFromAddress() {
  const params = new URLSearchParams(window.location.search);
  const key = params.get(PARAMETER);
  if (!key) return null;
  params.delete(PARAMETER);
  const query = params.toString();
  window.history.replaceState(null, '', `${window.location.pathname}${query ? `?${query}` : ''}${window.location.hash}`);
  return key;
}

function read() {
  if (typeof window === 'undefined') return null;
  try {
    const fromAddress = takeFromAddress();
    if (fromAddress) window.sessionStorage.setItem(STORAGE_KEY, fromAddress);
    return window.sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

const previewKey = read();

export const previewHeaders = () => (previewKey ? { [PREVIEW_HEADER]: previewKey } : {});
