// The due-today counts on the "DSA | System design" switch, and in this browser tab's title,
// so you can see at a glance what's waiting in either tracker.
import { api } from './http.js';

const baseTitle = document.title;
let watching = false;

/**
 * Refreshes both badges. {@code current} is 'dsa' or 'systemDesign': the tracker on this page,
 * whose count also goes in the tab title, e.g. "(3) DSA Tracker".
 */
export async function refreshDueBadges(current) {
  if (!watching) {
    // Coming back to the tab (maybe the next morning, or after revising in the other tracker): update.
    watching = true;
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') refreshDueBadges(current);
    });
  }
  let counts;
  try {
    counts = await api('/api/due-counts', {}, { redirectOn401: false });
  } catch {
    return;   // a badge is a nicety: never break the page over it
  }
  for (const [key, n] of Object.entries(counts)) {
    const badge = document.querySelector(`[data-due-badge="${key}"]`);
    if (!badge) continue;
    badge.textContent = n > 99 ? '99+' : String(n);
    badge.hidden = n === 0;
    const label = n === 1 ? '1 revision due today' : `${n} revisions due today`;
    badge.setAttribute('aria-label', label);
    badge.title = label;
  }
  const mine = counts[current] || 0;
  document.title = mine > 0 ? `(${mine}) ${baseTitle}` : baseTitle;
}
