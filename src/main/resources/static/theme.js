// Day / night mode. Loaded in <head> (not deferred) so the chosen theme applies before the page
// paints, with no flash of the wrong colours. "system" (the default) follows the device setting;
// "light" or "dark" overrides it. The choice is remembered per browser.
(function () {
  const KEY = 'dsa.theme';
  const root = document.documentElement;
  const darkQuery = window.matchMedia('(prefers-color-scheme: dark)');

  function get() {
    try { return localStorage.getItem(KEY) || 'system'; } catch { return 'system'; }
  }
  function apply(choice) {
    if (choice === 'light' || choice === 'dark') root.dataset.theme = choice;
    else delete root.dataset.theme;
  }
  function isDark() {
    return root.dataset.theme ? root.dataset.theme === 'dark' : darkQuery.matches;
  }
  function set(choice) {
    try {
      if (choice === 'system') localStorage.removeItem(KEY); else localStorage.setItem(KEY, choice);
    } catch { /* private mode: still applies for this page */ }
    apply(choice);
    document.dispatchEvent(new Event('themechange'));
  }
  apply(get());

  const NS = 'http://www.w3.org/2000/svg';
  function icon(dark) {
    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', '16');
    svg.setAttribute('height', '16');
    svg.setAttribute('aria-hidden', 'true');
    const shapes = dark
      ? [['path', { d: 'M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z' }]]          // moon
      : [['circle', { cx: 12, cy: 12, r: 4 }],                                      // sun
        ['path', { d: 'M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4' }]];
    for (const [tag, attrs] of shapes) {
      const s = document.createElementNS(NS, tag);
      for (const [k, v] of Object.entries(attrs)) s.setAttribute(k, v);
      s.setAttribute('fill', 'none');
      s.setAttribute('stroke', 'currentColor');
      s.setAttribute('stroke-width', '2');
      s.setAttribute('stroke-linecap', 'round');
      s.setAttribute('stroke-linejoin', 'round');
      svg.append(s);
    }
    return svg;
  }

  window.theme = { get, set, isDark };

  document.addEventListener('DOMContentLoaded', () => {
    // A one-click toggle: shows the mode you'd switch to.
    document.querySelectorAll('[data-theme-toggle]').forEach((btn) => {
      const draw = () => {
        const dark = isDark();
        btn.replaceChildren(icon(!dark));
        const label = dark ? 'Switch to day mode' : 'Switch to night mode';
        btn.title = label;
        btn.setAttribute('aria-label', label);
      };
      btn.addEventListener('click', () => set(isDark() ? 'light' : 'dark'));
      document.addEventListener('themechange', draw);
      darkQuery.addEventListener('change', draw);
      draw();
    });
    // System / Day / Night, in Settings.
    document.querySelectorAll('[data-theme-select]').forEach((select) => {
      select.value = get();
      select.addEventListener('change', () => set(select.value));
      document.addEventListener('themechange', () => { select.value = get(); });
    });
  });
})();
