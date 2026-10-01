// A side panel that shows a problem's notes as a readable page, instead of a small box you have
// to scroll. Used by both trackers. Plain DOM (no innerHTML), so nothing in your notes can run.

let open = null;   // { panel, backdrop, returnFocus }

/**
 * Opens the reader.
 * @param {object} page
 * @param {string} page.kicker   small line above the title, e.g. "NeetCode · Arrays & Hashing"
 * @param {string} page.title
 * @param {{href: string, label: string}} [page.link]  e.g. the problem on LeetCode
 * @param {Array<{id: *, heading: string, meta: string, body: Node[]}>} page.sections  one per sitting
 * @param {*} [page.focus]  the section to scroll to
 */
export function openReader(page) {
  closeReader();
  const backdrop = el('div', 'reader-backdrop');
  const panel = el('aside', 'reader');
  panel.setAttribute('role', 'dialog');
  panel.setAttribute('aria-modal', 'true');
  panel.setAttribute('aria-label', page.title);

  const bar = el('div', 'reader-bar');
  const close = el('button', 'btn ghost reader-close', 'Close');
  close.type = 'button';
  close.setAttribute('aria-label', 'Close the notes');
  bar.append(el('span', 'reader-bar-title', page.title), close);

  const doc = el('article', 'reader-doc');
  if (page.kicker) doc.append(el('p', 'reader-kicker', page.kicker));
  doc.append(el('h1', 'reader-title', page.title));
  if (page.link) {
    const a = el('a', 'reader-link', `${page.link.label} ↗`);
    a.href = page.link.href; a.target = '_blank'; a.rel = 'noopener';
    doc.append(a);
  }
  if (page.sections.length > 1) {
    const toc = el('nav', 'reader-toc');
    toc.setAttribute('aria-label', 'Sittings');
    page.sections.forEach((s) => {
      const b = el('button', 'reader-toc-item', s.heading);
      b.type = 'button';
      b.addEventListener('click', () => scrollTo(panel, s.id));
      toc.append(b);
    });
    doc.append(toc);
  }
  for (const s of page.sections) {
    const sec = el('section', 'reader-section');
    sec.dataset.readerId = String(s.id);
    const h = el('h2', '', s.heading);
    sec.append(h);
    if (s.meta) sec.append(el('p', 'reader-meta', s.meta));
    s.body.forEach((n) => sec.append(n));
    doc.append(sec);
  }

  const scroller = el('div', 'reader-scroll');
  scroller.append(doc);
  panel.append(bar, scroller);
  document.body.append(backdrop, panel);
  document.body.classList.add('reader-open');
  open = { panel, backdrop, returnFocus: document.activeElement };

  close.addEventListener('click', closeReader);
  backdrop.addEventListener('click', closeReader);
  panel.addEventListener('keydown', trapFocus);
  requestAnimationFrame(() => {
    panel.classList.add('in');
    backdrop.classList.add('in');
    close.focus({ preventScroll: true });
    // Jump to the sitting you came from; the first one is already in view under the title.
    if (page.focus !== undefined && page.sections.length > 1 && page.sections[0].id !== page.focus) scrollTo(panel, page.focus, 'auto');
  });
}

export function closeReader() {
  if (!open) return;
  const { panel, backdrop, returnFocus } = open;
  open = null;
  panel.remove();
  backdrop.remove();
  document.body.classList.remove('reader-open');
  if (returnFocus && document.contains(returnFocus)) returnFocus.focus({ preventScroll: true });
}

document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && open) { e.preventDefault(); closeReader(); }
});

function scrollTo(panel, id, behavior = 'smooth') {
  const target = panel.querySelector(`[data-reader-id="${CSS.escape(String(id))}"]`);
  if (target) target.scrollIntoView({ block: 'start', behavior });
}

/** Keeps Tab inside the panel while it's open. */
function trapFocus(e) {
  if (e.key !== 'Tab' || !open) return;
  const items = [...open.panel.querySelectorAll('a[href], button:not([disabled]), summary, [tabindex]:not([tabindex="-1"])')];
  if (!items.length) return;
  const first = items[0];
  const last = items[items.length - 1];
  if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
  else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
}

// ---------- building blocks for the pages

/**
 * Your notes as readable text. Light formatting is understood, so notes written quickly still read
 * well: blank lines separate paragraphs; lines starting "- ", "* " or "• " are bullets, "1. " numbered;
 * "# Heading"; `code`; **bold**; and links.
 */
export function notesBlock(text) {
  const wrap = el('div', 'reader-notes');
  if (!text || !text.trim()) {
    wrap.append(el('p', 'reader-empty', 'No notes for this sitting.'));
    return wrap;
  }
  let list = null;
  let para = [];
  const flushPara = () => {
    if (para.length) {
      const p = el('p');
      para.forEach((line, i) => { if (i) p.append(el('br')); inline(p, line); });
      wrap.append(p);
      para = [];
    }
  };
  for (const raw of text.replace(/\r\n?/g, '\n').split('\n')) {
    const line = raw.trimEnd();
    const bullet = line.match(/^\s*(?:[-*•])\s+(.*)$/);
    const numbered = line.match(/^\s*\d+[.)]\s+(.*)$/);
    const heading = line.match(/^\s*#{1,3}\s+(.*)$/);
    if (bullet || numbered) {
      flushPara();
      const tag = bullet ? 'ul' : 'ol';
      if (!list || list.tagName.toLowerCase() !== tag) { list = el(tag); wrap.append(list); }
      const li = el('li');
      inline(li, (bullet || numbered)[1]);
      list.append(li);
      continue;
    }
    list = null;
    if (heading) { flushPara(); const h = el('h3'); inline(h, heading[1]); wrap.append(h); continue; }
    if (!line.trim()) { flushPara(); continue; }
    para.push(line);
  }
  flushPara();
  return wrap;
}

/** A labelled block, e.g. "Your answer" or "Requirements". */
export function labelled(label, ...nodes) {
  const wrap = el('div', 'reader-block');
  wrap.append(el('p', 'reader-label', label), ...nodes);
  return wrap;
}

export function codeBlock(code, caption) {
  const wrap = el('div', 'reader-code');
  if (caption) wrap.append(el('p', 'reader-label', caption));
  const pre = el('pre');
  pre.append(el('code', '', code));
  wrap.append(pre);
  return wrap;
}

export function linkLine(href, label) {
  const a = el('a', 'reader-link', `${label} ↗`);
  a.href = href; a.target = '_blank'; a.rel = 'noopener';
  return a;
}

/** Quiz questions with your answer and the right one. */
export function quizBlock(questions) {
  const ol = el('ol', 'reader-quiz');
  for (const q of questions) {
    const li = el('li', q.right ? 'right' : 'wrong');
    li.append(el('p', 'rq-q', q.q));
    if (q.right) li.append(el('p', 'rq-a', `✓ ${q.answer}`));
    else {
      if (q.chosen) li.append(el('p', 'rq-w', `✗ ${q.chosen}`));
      li.append(el('p', 'rq-a', `✓ ${q.answer}`));
    }
    if (q.why) li.append(el('p', 'rq-why', q.why));
    ol.append(li);
  }
  return ol;
}

/** A small "Read" button with an open-book icon, for attempt headers. */
export function readButton(onClick) {
  const b = el('button', 'btn ghost read-btn');
  b.type = 'button';
  b.title = 'Read the notes as a page';
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', '14');
  svg.setAttribute('height', '14');
  svg.setAttribute('aria-hidden', 'true');
  for (const d of ['M2 5h6a4 4 0 0 1 4 4v11a3 3 0 0 0-3-3H2z', 'M22 5h-6a4 4 0 0 0-4 4v11a3 3 0 0 1 3-3h7z']) {
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', d);
    path.setAttribute('fill', 'none');
    path.setAttribute('stroke', 'currentColor');
    path.setAttribute('stroke-width', '2');
    path.setAttribute('stroke-linejoin', 'round');
    svg.append(path);
  }
  b.append(svg, document.createTextNode('Read'));
  b.addEventListener('click', onClick);
  return b;
}

// `code`, **bold** and links inside a line
function inline(parent, text) {
  const re = /(`[^`]+`)|(\*\*[^*]+\*\*)|(https?:\/\/[^\s)]+)/g;
  let last = 0;
  let m;
  while ((m = re.exec(text))) {
    if (m.index > last) parent.append(document.createTextNode(text.slice(last, m.index)));
    if (m[1]) parent.append(el('code', '', m[1].slice(1, -1)));
    else if (m[2]) parent.append(el('strong', '', m[2].slice(2, -2)));
    else {
      const a = el('a', '', m[3]);
      a.href = m[3]; a.target = '_blank'; a.rel = 'noopener';
      parent.append(a);
    }
    last = m.index + m[0].length;
  }
  if (last < text.length) parent.append(document.createTextNode(text.slice(last)));
}

function el(tag, cls = '', text) {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined) node.textContent = text;
  return node;
}
