// Shared fetch helper for every page.
//
// CSRF: the server sets an XSRF-TOKEN cookie. For any request that changes something, we copy
// that cookie into an X-XSRF-TOKEN header. Other websites can't read our cookies, so they can't
// forge this header; the server rejects writes without it (403).

function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : '';
}

/**
 * fetch() with JSON, the CSRF header, and readable errors.
 * @param {object} opts.redirectOn401 send the user to sign in when the session has expired
 */
export async function api(path, options = {}, { redirectOn401 = true } = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (method !== 'GET' && method !== 'HEAD') headers['X-XSRF-TOKEN'] = csrfToken();

  const res = await fetch(path, { ...options, method, headers, credentials: 'same-origin' });
  if (res.status === 401 && redirectOn401) {
    location.href = '/auth.html#signin';
    throw new Error('Please sign in');
  }
  if (res.status === 204) return null;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 403) throw new Error('Your page is out of date. Reload it and try again.');
    throw new Error(body.detail || `Something went wrong (${res.status})`);
  }
  return body;
}
