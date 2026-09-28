// Dashboard for the DSA tracker. Plain JavaScript, no build step.
// All rules live on the server; this file only shows data and sends your answers.
import { api } from './http.js';

const $ = (id) => document.getElementById(id);
const DAY_MS = 86_400_000;

const LEVEL = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };

// ---------- dates (kept as "YYYY-MM-DD" strings, maths done in UTC so DST can't shift a day)
const toMs = (iso) => Date.parse(iso + 'T00:00:00Z');
const toIso = (ms) => new Date(ms).toISOString().slice(0, 10);
const addDays = (iso, n) => toIso(toMs(iso) + n * DAY_MS);
const diffDays = (a, b) => Math.round((toMs(b) - toMs(a)) / DAY_MS);
const fmt = (iso, opts = { weekday: 'short', day: 'numeric', month: 'short' }) =>
  new Intl.DateTimeFormat('en-GB', { ...opts, timeZone: 'UTC' }).format(toMs(iso));
const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;

let state = null;
let openCats = null;        // categories you've opened; null = not chosen yet, use the default
let doneTarget = null;      // the catalog item the dialog is marking done

// ---------- API

async function load() {
  try {
    state = await api('/api/dashboard');
    render(state);
    if (!load.checkedRecovery) { load.checkedRecovery = true; checkRecoveryCode(); }
  } catch (e) {
    $('plan-line').textContent = `Couldn't load the dashboard: ${e.message}`;
  }
}

// ---------- render
function render(d) {
  const day = Math.min(Math.max(d.dayOfPlan, 0), d.planDays);
  $('plan-line').textContent = d.dayOfPlan < 1
    ? `Plan starts ${fmt(d.planStart)}`
    : `${fmt(d.today, { weekday: 'long', day: 'numeric', month: 'long' })} · Day ${day} of ${d.planDays}`;

  $('who').textContent = d.username;
  renderBanner(d);
  renderTiles(d);
  renderHeatmap(d);
  renderDue(d);
  renderTests(d);
  renderMemory(d);
  renderCatalog(d);
}

function renderBanner(d) {
  const { daysAway, activeToday } = d.consistency;
  const el = $('banner');
  el.hidden = true;
  if (activeToday || daysAway === 0) return;
  // "Never miss twice": a nudge after one missed day, a warm welcome after longer. Nothing resets.
  el.className = 'banner info';
  el.innerHTML = daysAway === 1
    ? '<span class="icon" aria-hidden="true">·</span><span>Missed yesterday? That\'s fine. Try not to miss twice: one review today keeps the habit going.</span>'
    : `<span class="icon" aria-hidden="true">·</span><span>Welcome back. Nothing was reset while you were away. Your reviews waited for you, so start with just one.</span>`;
  el.hidden = false;
}

function renderTiles(d) {
  const h = d.consistency, c = d.counts;
  $('longterm-count').textContent = d.memory.longTerm;

  $('due-count').textContent = c.dueToday;
  $('due-sub').innerHTML = c.overdue > 0
    ? `<span class="alert">${c.overdue} overdue</span>`
    : (c.dueToday === 0 ? 'All clear' : 'On time');

  $('solved-count').textContent = c.done;
  $('solved-target').textContent = `/ ${c.target}`;
  $('solved-bar').style.width = `${Math.min(100, (c.done / c.target) * 100)}%`;

  $('study-days').textContent = h.totalStudyDays;
  $('week-sub').textContent = h.weekNumber > 0
    ? `This week: ${h.daysThisWeek} of ${h.weeklyTarget}${h.daysThisWeek >= h.weeklyTarget ? ' ✓' : ''}`
    : `Your plan starts ${fmt(d.planStart)}`;

  $('calendar-note').textContent = plural(h.totalStudyDays, 'study day');
}

/** Memory panel: a stacked bar of problems by stage, and the recent recall rate. */
function renderMemory(d) {
  const m = d.memory;
  const total = m.learning + m.strengthening + m.longTerm;
  const stages = [
    ['learning', 'Learning', 'gap under 1 week', m.learning],
    ['strengthening', 'Strengthening', '1–3 weeks', m.strengthening],
    ['longterm', 'Long-term', '3+ weeks', m.longTerm],
  ];
  $('mem-total').textContent = total ? plural(total, 'problem') : '';

  const bar = $('stages');
  bar.replaceChildren();
  if (total === 0) bar.append(el('span', 'stage empty-stage'));
  for (const [key, , , n] of stages) {
    if (!n) continue;
    const seg = el('span', `stage ${key}`);
    seg.style.flexGrow = n;
    bar.append(seg);
  }
  bar.setAttribute('aria-label', stages.map(([, label, , n]) => `${label}: ${n}`).join(', '));

  const legend = $('stage-legend');
  legend.replaceChildren(...stages.map(([key, label, hint, n]) => {
    const li = el('li');
    li.append(el('i', `swatch ${key}`), el('span', 'stage-name', label), el('span', 'stage-hint', hint), el('b', '', String(n)));
    return li;
  }));

  const r = d.recall;
  $('recall').textContent = r.reviews
    ? `Remembered ${r.remembered} of ${plural(r.reviews, 'review')} in the last ${r.days} days (${Math.round((100 * r.remembered) / r.reviews)}%).`
    : 'Your recall rate appears after your first reviews.';
}

function renderHeatmap(d) {
  const grid = $('heatmap');
  grid.replaceChildren();

  const counts = new Map(d.activity.map((a) => [a.date, a.activities]));
  const start = d.planStart;
  const end = addDays(start, d.planDays - 1);

  // Columns are weeks starting on Monday; pad the first week before the plan starts.
  const mondayOffset = (new Date(toMs(start)).getUTCDay() + 6) % 7;
  const gridStart = addDays(start, -mondayOffset);
  const weeks = Math.ceil((mondayOffset + d.planDays) / 7);

  // First column: weekday labels.
  grid.append(el('span', 'month'));
  ['Mon', '', 'Wed', '', 'Fri', '', ''].forEach((t) => grid.append(el('span', 'dow', t)));

  let lastMonth = null;
  let lastLabel = null;
  for (let w = 0; w < weeks; w++) {
    const weekStart = addDays(gridStart, w * 7);
    // Month label above the first week that contains the 1st (or the plan start).
    const label = el('span', 'month');
    for (let i = 0; i < 7; i++) {
      const iso = addDays(weekStart, i);
      const m = iso.slice(0, 7);
      if (iso >= start && iso <= end && m !== lastMonth) {
        // A month with only a column or two (e.g. the plan starts late in a month)
        // would collide with the next label, so the newer month wins.
        if (lastLabel && w - lastLabel.week < 3) lastLabel.node.textContent = '';
        label.textContent = fmt(iso, { month: 'short' });
        lastMonth = m;
        lastLabel = { node: label, week: w };
        break;
      }
    }
    grid.append(label);

    for (let i = 0; i < 7; i++) {
      const iso = addDays(weekStart, i);
      grid.append(dayCell(iso, { start, end, today: d.today, counts }));
    }
  }
}

function dayCell(iso, { start, end, today, counts }) {
  const cell = el('span', 'cell');
  if (iso < start || iso > end) {
    cell.classList.add('pad');
    return cell;
  }
  const n = counts.get(iso) || 0;
  const dayNo = diffDays(start, iso) + 1;
  let text;

  if (iso > today) {
    cell.classList.add('future');
    text = 'Upcoming';
  } else if (n > 0) {
    cell.classList.add(`l${Math.min(n, 4)}`);
    text = plural(n, 'entry').replace('entrys', 'entries');
  } else {
    text = iso === today ? 'Nothing yet today' : 'Rest day';
  }
  if (iso === today) cell.classList.add('today');

  const label = `${fmt(iso)} · Day ${dayNo} · ${text}`;
  cell.setAttribute('role', 'gridcell');
  cell.setAttribute('aria-label', label);
  cell.tabIndex = -1;
  cell.dataset.tip = label;
  return cell;
}

function renderDue(d) {
  const list = $('due-list');
  list.replaceChildren();
  $('today-note').textContent = d.consistency.activeToday
    ? '✓ Studied today'
    : 'Minimum for today: one review or one new problem';

  if (d.due.length === 0) {
    const next = allProgress(d)
      .map((p) => p.nextDueOn)
      .sort()[0];
    list.append(el('li', 'empty', next
      ? `No revisions today. Next one is on ${fmt(next)}.`
      : 'No revisions yet. Mark a problem done to start.'));
    return;
  }

  for (const p of d.due) {
    const li = el('li', 'due-item');

    const main = el('div');
    const title = p.link ? el('a', 'due-title', p.name) : el('span', 'due-title', p.name);
    if (p.link) { title.href = p.link; title.target = '_blank'; title.rel = 'noopener'; }
    main.append(title, metaLine([
      LEVEL[p.difficulty],
      p.reps === 0 && p.lapses === 0 ? 'First review' : `Last gap ${plural(p.intervalDays, 'day')}`,
      p.overdueDays > 0 ? ['overdue', `! Overdue ${plural(p.overdueDays, 'day')}`] : null,
      p.lapses ? `Forgot ${p.lapses}×` : null,
    ]));

    // Try the problem first; your notes are one click away if you need them.
    const { toggle, panel } = notes(p);

    const answer = el('div', 'answer');
    const yes = el('button', 'btn', 'Remembered');
    const no = el('button', 'btn', 'Forgot');
    yes.type = no.type = 'button';
    yes.addEventListener('click', () => revise(p, true, [yes, no]));
    no.addEventListener('click', () => revise(p, false, [yes, no]));
    answer.append(yes, no);

    li.append(main, toggle, answer, panel);
    list.append(li);
  }
}

/** "Easy · Revision 1 of 2 · ! Overdue 3 days" — parts may be text or [className, text]. */
function metaLine(parts) {
  const line = el('div', 'meta');
  parts.filter(Boolean).forEach((part, i) => {
    if (i > 0) line.append(el('span', 'sep', '·'));
    line.append(Array.isArray(part) ? el('span', part[0], part[1]) : document.createTextNode(part));
  });
  return line;
}

// ---------- weekly tests
function renderTests(d) {
  const list = $('test-list');
  list.replaceChildren();
  if (d.weeklyTests.length === 0) {
    const firstEnd = addDays(d.planStart, 6);
    list.append(el('li', 'empty', d.today > firstEnd
      ? 'Mark problems done during a week and its test appears here at the end of that week.'
      : `Your first test unlocks on ${fmt(firstEnd)}, the last day of week 1.`));
    return;
  }
  for (const t of d.weeklyTests) {
    const li = el('li', 'test-row');
    const main = el('div');
    main.append(el('div', 'test-title', `Week ${t.weekNumber}`),
      el('div', 'meta', `${fmt(t.weekStart, { day: 'numeric', month: 'short' })} – ${fmt(t.weekEnd, { day: 'numeric', month: 'short' })} · ${plural(t.problemsDone, 'problem')} done`));

    const side = el('div', 'test-side');
    if (t.status === 'UPCOMING') {
      side.append(el('span', 'status', `Unlocks ${fmt(t.weekEnd)}`));
    } else if (t.status === 'AVAILABLE') {
      const btn = el('button', 'btn primary', 'Take test');
      btn.type = 'button';
      btn.addEventListener('click', () => startTest(t.weekNumber, btn));
      side.append(btn);
    } else {
      const summary = t.status === 'COMPLETED'
        ? `Patterns ${t.patternsRight}/${t.items} · Solved ${t.solved}/${t.items}${t.withHint ? ` (+${t.withHint} with a hint)` : ''}`
        : `${t.answered} of ${t.items} answered`;
      side.append(el('span', 'status', summary));
      const a = el('a', 'btn', t.status === 'COMPLETED' ? 'Review' : 'Continue');
      a.href = `/test.html?id=${t.testId}`;
      side.append(a);
    }
    li.append(main, side);
    list.append(li);
  }
}

async function startTest(week, btn) {
  btn.disabled = true;
  try {
    const test = await api(`/api/tests/week/${week}`, { method: 'POST' });
    location.href = `/test.html?id=${test.id}`;
  } catch (e) {
    toast(e.message);
    btn.disabled = false;
  }
}

// ---------- NeetCode 150 checklist
function allProgress(d) {
  return [...d.catalog.map((c) => c.progress).filter(Boolean), ...d.earlierEntries];
}

function renderCatalog(d) {
  const root = $('catalog');
  root.replaceChildren();

  const done = d.catalog.filter((c) => c.progress).length;
  $('catalog-summary').textContent = `${done} done · ${d.catalog.length - done} to do`;

  const show = document.querySelector('input[name=show]:checked').value;
  const query = $('catalog-search').value.trim().toLowerCase();
  const visible = (c) =>
    (show === 'all' || (show === 'done') === Boolean(c.progress)) &&
    (!query || c.name.toLowerCase().includes(query));

  // Group in roadmap order; the server already sends the list in that order.
  const groups = new Map();
  for (const c of d.catalog) {
    if (!groups.has(c.category)) groups.set(c.category, []);
    groups.get(c.category).push(c);
  }

  // By default, open the first category that still has work in it.
  if (openCats === null) {
    const first = [...groups].find(([, items]) => items.some((c) => !c.progress));
    openCats = new Set(first ? [first[0]] : []);
  }

  let shown = 0;
  for (const [category, items] of groups) {
    const rows = items.filter(visible);
    if (rows.length === 0) continue;
    shown += rows.length;
    const doneHere = items.filter((c) => c.progress).length;
    root.append(categoryBlock(category, doneHere, items.length, rows.map(catalogRow),
      query !== '' || openCats.has(category)));
  }

  // Problems logged by hand before the NeetCode list existed.
  const earlier = d.earlierEntries.filter((p) => show !== 'todo' &&
    (!query || p.name.toLowerCase().includes(query)));
  if (earlier.length) {
    shown += earlier.length;
    root.append(categoryBlock('Earlier entries', earlier.length, earlier.length,
      earlier.map((p) => catalogRow({ id: null, name: p.name, url: p.link,
        difficulty: p.initialDifficulty, progress: p })),
      query !== '' || openCats.has('Earlier entries')));
  }

  if (shown === 0) root.append(el('p', 'empty', 'No problems match.'));
}

function categoryBlock(name, doneCount, total, rows, open) {
  const box = el('details', 'cat');
  box.open = open;
  const summary = el('summary');
  summary.append(el('span', 'cat-name', name), el('span', 'cat-count', `${doneCount} / ${total}`));
  const bar = el('span', 'cat-bar');
  bar.setAttribute('aria-hidden', 'true');
  const fill = el('span', 'cat-bar-fill');
  fill.style.width = `${(doneCount / total) * 100}%`;
  bar.append(fill);
  summary.append(bar);
  box.append(summary);
  const list = el('ul', 'cat-list');
  list.append(...rows);
  box.append(list);
  box.addEventListener('toggle', () => {
    if (box.open) openCats.add(name); else openCats.delete(name);
  });
  return box;
}

function catalogRow(c) {
  const p = c.progress;
  const li = el('li', p ? 'row done' : 'row');

  const mark = el('span', 'check', p ? '✓' : '');
  mark.setAttribute('aria-hidden', 'true');

  const title = el('a', 'row-title', c.name);
  title.href = c.url; title.target = '_blank'; title.rel = 'noopener';
  title.title = c.name;

  // Level and status: their own columns on wide screens, one line under the title on phones.
  const sub = el('div', 'sub');
  sub.append(el('span', 'level', LEVEL[c.difficulty]));
  let status;
  if (!p) status = el('span', 'status', '');
  else if (p.overdueDays > 0) status = el('span', 'status overdue', `! Overdue ${plural(p.overdueDays, 'day')}`);
  else if (p.nextDueOn === state.today) status = el('span', 'status', 'Review today');
  else if (p.mature) status = el('span', 'status mastered', `✓ Next ${fmt(p.nextDueOn)}`);
  else status = el('span', 'status', `Next ${fmt(p.nextDueOn)}`);
  if (p) status.title = `Remembered ${plural(p.reps, 'time')}, forgot ${plural(p.lapses, 'time')}. Current gap ${plural(p.intervalDays, 'day')}.`;
  sub.append(status);

  const actions = el('div', 'actions');
  let panel = null;
  if (!p) {
    const btn = el('button', 'btn link', 'Mark done');
    btn.type = 'button';
    btn.addEventListener('click', () => openDone(c));
    actions.append(btn);
  } else {
    const n = notes(p);
    panel = n.panel;
    const undo = el('button', 'btn icon', '✕');
    undo.type = 'button';
    undo.title = `Undo "${c.name}"`;
    undo.setAttribute('aria-label', `Undo ${c.name}`);
    undo.addEventListener('click', () => remove(p));
    actions.append(n.toggle, undo);
  }

  li.append(mark, title, sub, actions);
  if (panel) li.append(panel);
  return li;
}

let notesSeq = 0;
const openNotes = new Set();   // problem ids whose notes panel is open, kept across reloads

const LANGUAGES = { JAVA: 'Java', PYTHON: 'Python', JAVASCRIPT: 'JavaScript', CPP: 'C++' };
const CONFIDENCE = { high: 'High confidence', medium: 'Medium confidence', low: 'Low confidence' };

function lastLanguage() {
  try { return localStorage.getItem('dsa.codeLanguage') || 'JAVA'; } catch { return 'JAVA'; }
}
function rememberLanguage(lang) {
  try { localStorage.setItem('dsa.codeLanguage', lang); } catch { /* private mode: fine */ }
}

/**
 * A "Notes" button and the panel it opens. On the day the problem was solved the panel is a form
 * (notes, drawing link, code, Analyse); after that it's a read-only record, frozen.
 */
function notes(p) {
  const panel = el('div', 'notes');
  panel.id = `notes-${++notesSeq}`;   // the same problem can appear in Today and in the list
  panel.hidden = !openNotes.has(p.id);

  const reviewed = `reviewed ${plural(p.reps + p.lapses, 'time')} · current gap ${plural(p.intervalDays, 'day')}`;
  const when = el('p', 'notes-when');
  if (p.editable) {
    when.append(`Done today · ${reviewed} · `, el('span', 'editable-tag', 'Editable until midnight, then frozen'));
  } else {
    const lock = el('span', 'frozen-tag', 'Frozen');
    lock.title = `Notes can only be edited on the day you solve a problem (${fmt(p.solvedOn)}).`;
    when.append(`Done ${fmt(p.solvedOn)} · ${reviewed} · `, lock);
  }
  panel.append(when);
  panel.append(p.editable ? notesForm(p) : notesRecord(p));

  const toggle = el('button', 'btn ghost', 'Notes');
  toggle.type = 'button';
  toggle.setAttribute('aria-expanded', String(!panel.hidden));
  toggle.setAttribute('aria-controls', panel.id);
  toggle.addEventListener('click', () => {
    panel.hidden = !panel.hidden;
    if (panel.hidden) openNotes.delete(p.id); else openNotes.add(p.id);
    toggle.setAttribute('aria-expanded', String(!panel.hidden));
  });
  return { toggle, panel };
}

/** Frozen notes: what you wrote on the day, your code and its analysis. */
function notesRecord(p) {
  const box = el('div', 'notes-record');
  if (p.learnings) box.append(el('p', 'notes-text', p.learnings));
  if (p.excalidrawUrl) {
    const a = el('a', '', 'Open drawing in Excalidraw ↗');
    a.href = p.excalidrawUrl; a.target = '_blank'; a.rel = 'noopener';
    box.append(a);
  }
  if (p.code) {
    box.append(el('p', 'code-label', `Your code · ${LANGUAGES[p.codeLanguage] || ''}`));
    const pre = el('pre', 'code-view');
    pre.append(el('code', '', p.code));
    box.append(pre);
  }
  if (p.analysis) box.append(analysisBox(p.analysis));
  return box;
}

/** Today's notes: editable, with a code box and Analyse. */
function notesForm(p) {
  const form = el('form', 'notes-form');
  form.noValidate = true;
  const uid = `nf-${notesSeq}`;

  const learnings = el('textarea');
  learnings.name = 'learnings'; learnings.rows = 4; learnings.maxLength = 2000; learnings.value = p.learnings || '';
  const drawing = el('input');
  drawing.name = 'excalidrawUrl'; drawing.type = 'url'; drawing.maxLength = 500;
  drawing.placeholder = 'https://excalidraw.com/#json=…'; drawing.value = p.excalidrawUrl || '';

  const lang = el('select', 'lang-select');
  lang.name = 'codeLanguage';
  lang.id = `${uid}-lang`;
  for (const [value, label] of Object.entries(LANGUAGES)) {
    const o = el('option', '', label);
    o.value = value;
    lang.append(o);
  }
  lang.value = p.codeLanguage || lastLanguage();

  const code = el('textarea', 'code-input');
  code.name = 'code'; code.rows = 10; code.maxLength = 10000; code.spellcheck = false;
  code.id = `${uid}-code`;
  code.value = p.code || '';
  code.placeholder = 'Paste or type your solution';
  code.setAttribute('autocapitalize', 'off');
  code.setAttribute('autocomplete', 'off');
  code.setAttribute('aria-describedby', `${uid}-hint`);
  code.addEventListener('keydown', indentWithTab);

  const codeHead = el('div', 'code-head');
  const codeLabel = el('label', 'code-label', 'Your code');
  codeLabel.htmlFor = code.id;
  const langLabel = el('label', 'sr-only', 'Language');
  langLabel.htmlFor = lang.id;
  codeHead.append(codeLabel, langLabel, lang);
  const hint = el('p', 'hint', 'Tab indents. Press Esc, then Tab, to move on.');
  hint.id = `${uid}-hint`;

  const error = el('p', 'form-error');
  error.setAttribute('role', 'alert');
  error.hidden = true;
  const status = el('span', 'save-status');
  status.setAttribute('aria-live', 'polite');

  const save = el('button', 'btn', 'Save');
  save.type = 'submit';
  const analyse = el('button', 'btn primary', 'Analyse');
  analyse.type = 'button';
  analyse.title = 'Estimate the time and space complexity of your code';
  const actions = el('div', 'notes-actions');
  actions.append(save, analyse, status);

  const result = el('div', 'analysis-slot');
  if (p.analysis) result.append(analysisBox(p.analysis));

  form.append(
    field('What you learned', learnings),
    field('Excalidraw link', drawing, 'optional'),
    codeHead, code, hint, error, actions, result);

  const saved = () => ({
    learnings: learnings.value.trim(),
    excalidrawUrl: drawing.value.trim(),
    code: code.value.trim() ? code.value : '',
    codeLanguage: code.value.trim() ? lang.value : null,
  });
  let last = JSON.stringify(saved());
  const dirty = () => JSON.stringify(saved()) !== last;

  // Changing the code makes the old analysis wrong, so hide it until the next Analyse.
  const markStale = () => { if (dirty()) result.replaceChildren(); status.textContent = dirty() ? 'Unsaved changes' : ''; };
  [learnings, drawing, code, lang].forEach((x) => x.addEventListener('input', markStale));

  async function persist() {
    const body = saved();
    if (!body.learnings) throw new Error('Write down what you learned.');
    if (body.excalidrawUrl && !/^https:\/\/\S+$/.test(body.excalidrawUrl)) throw new Error('The Excalidraw link must start with https://');
    const updated = await api(`/api/problems/${p.id}/notes`, { method: 'PATCH', body: JSON.stringify(body) });
    last = JSON.stringify(saved());
    if (body.codeLanguage) rememberLanguage(body.codeLanguage);
    Object.assign(p, updated);
    return updated;
  }

  async function run(button, work) {
    error.hidden = true;
    save.disabled = analyse.disabled = true;
    const label = button.textContent;
    button.textContent = button === analyse ? 'Analysing…' : 'Saving…';
    try {
      await work();
    } catch (e) {
      error.textContent = e.message;
      error.hidden = false;
      if (/frozen/i.test(e.message)) await load();   // midnight passed: show the frozen record
    } finally {
      button.textContent = label;
      save.disabled = analyse.disabled = false;
    }
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    run(save, async () => {
      await persist();
      status.textContent = 'Saved';
      syncCopies(p);
    });
  });

  analyse.addEventListener('click', () => run(analyse, async () => {
    if (!code.value.trim()) throw new Error('Add your code first, then analyse it.');
    if (dirty()) await persist();
    const updated = await api(`/api/problems/${p.id}/analysis`, { method: 'POST' });
    Object.assign(p, updated);
    status.textContent = '';
    result.replaceChildren(analysisBox(updated.analysis));
    syncCopies(p);
  }));

  return form;
}

/** The same problem can be shown twice (Today and the list): refresh the data behind both. */
function syncCopies(p) {
  if (!state) return;
  for (const list of [state.due, state.catalog.map((c) => c.progress).filter(Boolean)]) {
    for (const q of list) if (q.id === p.id && q !== p) Object.assign(q, p);
  }
}

function field(label, control, optional) {
  const wrap = el('label', 'field');
  const span = el('span', '', label);
  if (optional) span.append(el('em', '', optional));
  wrap.append(span, control);
  return wrap;
}

/** Tab inserts four spaces in the code box; Esc then Tab leaves it, so keyboard users aren't trapped. */
function indentWithTab(e) {
  const box = e.currentTarget;
  if (e.key === 'Escape') { box.dataset.tabExit = '1'; return; }
  if (e.key !== 'Tab' || e.shiftKey || box.dataset.tabExit) { delete box.dataset.tabExit; return; }
  e.preventDefault();
  const { selectionStart: a, selectionEnd: b, value } = box;
  box.value = value.slice(0, a) + '    ' + value.slice(b);
  box.selectionStart = box.selectionEnd = a + 4;
  box.dispatchEvent(new Event('input'));
}

/** Time and space, how sure the analysis is, and the steps behind it. */
function analysisBox(a) {
  const box = el('section', 'analysis');
  box.setAttribute('aria-label', 'Complexity analysis');
  const head = el('div', 'analysis-head');
  const time = el('div', 'metric');
  time.append(el('span', 'metric-label', 'Time'), el('span', 'metric-value', a.time));
  const space = el('div', 'metric');
  space.append(el('span', 'metric-label', 'Space'), el('span', 'metric-value', a.space));
  const meta = el('div', 'analysis-meta');
  meta.append(
    el('span', `confidence ${a.confidence}`, CONFIDENCE[a.confidence] || a.confidence),
    el('span', 'source', a.source === 'claude' ? 'Analysed by Claude' : 'Built-in estimate'));
  head.append(time, space, meta);
  box.append(head);
  if (a.recommendation) box.append(recommendationBox(a.recommendation, a.confidence));
  if (a.reasons && a.reasons.length) {
    const details = el('details', 'analysis-why');
    details.append(el('summary', '', 'How this was worked out'));
    const list = el('ul');
    for (const r of a.reasons) list.append(el('li', '', r));
    details.append(list);
    box.append(details);
  }
  return box;
}

const VERDICTS = {
  faster: 'Faster approach available',
  leaner: 'Less memory, same speed',
  optimal: 'Matches the best known',
  check: 'Worth a second look',
  fixed: 'Fixed-size input',
};

/** How the solution compares with the best known approaches, with the idea behind a toggle. */
function recommendationBox(r, confidence) {
  const box = el('div', `rec rec-${r.verdict}`);
  box.append(el('p', 'rec-title', VERDICTS[r.verdict] || 'Best known approach'));
  box.append(el('p', 'rec-msg', r.message));
  if (confidence === 'low' && (r.verdict === 'faster' || r.verdict === 'leaner')) {
    box.append(el('p', 'rec-hedge', 'The analysis above is a low-confidence estimate, so check it before rewriting.'));
  }
  box.append(approachLine(r.verdict === 'optimal' ? 'Best known' : r.verdict === 'fixed' ? 'Usual approach' : 'Try', r.approach));
  if (r.further) box.append(approachLine('Going further', r.further));
  return box;
}

function approachLine(label, a) {
  const wrap = el('div', 'rec-approach');
  const line = el('p', 'rec-line');
  line.append(el('span', 'rec-label', label), el('strong', '', a.name),
    el('span', 'rec-cx', `${a.time} time · ${a.space} space`));
  const idea = el('details', 'rec-idea');
  idea.append(el('summary', '', 'Show the idea'), el('p', '', a.idea));
  wrap.append(line, idea);
  return wrap;
}

document.querySelectorAll('input[name=show]').forEach((r) =>
  r.addEventListener('change', () => state && renderCatalog(state)));
$('catalog-search').addEventListener('input', () => state && renderCatalog(state));

// ---------- actions
async function revise(p, remembered, buttons) {
  buttons.forEach((b) => (b.disabled = true));
  try {
    const updated = await api(`/api/problems/${p.id}/reviews`, {
      method: 'POST',
      body: JSON.stringify({ remembered }),
    });
    const when = `${plural(updated.intervalDays, 'day')} (${fmt(updated.nextDueOn)})`;
    toast(remembered ? `Nice. Next review in ${when}.` : `No problem. You'll see it again in ${when} to relearn it.`);
    await load();
  } catch (e) {
    toast(e.message);
    buttons.forEach((b) => (b.disabled = false));
  }
}

async function remove(p) {
  if (!confirm(`Undo "${p.name}"? Your notes and revision history for it will be deleted.`)) return;
  try {
    await api(`/api/problems/${p.id}`, { method: 'DELETE' });
    await load();
  } catch (e) {
    toast(e.message);
  }
}

// ---------- "Mark done" dialog
const dialog = $('done-dialog');
const doneForm = $('done-form');

function openDone(c) {
  doneTarget = c;
  doneForm.reset();
  $('done-error').hidden = true;
  $('done-lang').value = lastLanguage();
  $('done-analysis').replaceChildren();
  $('done-analyse-status').textContent = '';
  $('done-title').textContent = c.name;
  $('done-meta').textContent =
    `${LEVEL[c.difficulty]} · first review tomorrow, ${fmt(addDays(state.today, 1))}. The gaps then grow as you remember it.`;
  dialog.showModal();
  doneForm.learnings.focus();
}

$('done-cancel').addEventListener('click', () => dialog.close());

// Your code, analysed before anything is saved. Esc in the code box first ends Tab-indenting;
// it only closes the window if pressed again.
const doneCode = $('done-code');
// First Esc: stop Tab-indenting (and keep the window open). Registered before indentWithTab,
// which is what records that first Esc; a second Esc then closes the window as usual.
doneCode.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !doneCode.dataset.tabExit) e.preventDefault();
});
doneCode.addEventListener('keydown', indentWithTab);
const clearDoneAnalysis = () => { $('done-analysis').replaceChildren(); $('done-analyse-status').textContent = ''; };
doneCode.addEventListener('input', clearDoneAnalysis);
$('done-lang').addEventListener('change', clearDoneAnalysis);

$('done-analyse').addEventListener('click', async () => {
  const err = $('done-error');
  err.hidden = true;
  if (!doneCode.value.trim()) {
    err.textContent = 'Add your code first, then analyse it.';
    err.hidden = false;
    doneCode.focus();
    return;
  }
  const btn = $('done-analyse');
  btn.disabled = true;
  btn.textContent = 'Analysing…';
  try {
    const result = await api('/api/analysis/preview', {
      method: 'POST',
      body: JSON.stringify({ code: doneCode.value, codeLanguage: $('done-lang').value, catalogId: doneTarget && doneTarget.id }),
    });
    $('done-analysis').replaceChildren(analysisBox(result));
    $('done-analyse-status').textContent = 'Saved with your notes when you mark it done.';
  } catch (e) {
    err.textContent = e.message;
    err.hidden = false;
  } finally {
    btn.disabled = false;
    btn.textContent = 'Analyse';
  }
});

doneForm.addEventListener('submit', async (ev) => {
  ev.preventDefault();
  const err = $('done-error');
  err.hidden = true;
  const learnings = doneForm.learnings.value.trim();
  const excalidrawUrl = doneForm.excalidrawUrl.value.trim();

  if (!learnings) {
    err.textContent = 'Write down what you learned before marking it done.';
    err.hidden = false;
    return;
  }
  if (excalidrawUrl && !/^https:\/\/\S+$/.test(excalidrawUrl)) {
    err.textContent = 'The Excalidraw link must start with https://';
    err.hidden = false;
    return;
  }

  const btn = $('done-save');
  btn.disabled = true;
  try {
    const code = doneCode.value.trim() ? doneCode.value : '';
    const codeLanguage = code ? $('done-lang').value : null;
    const saved = await api(`/api/catalog/${doneTarget.id}/done`, {
      method: 'POST',
      body: JSON.stringify({ learnings, excalidrawUrl, code, codeLanguage }),
    });
    if (codeLanguage) rememberLanguage(codeLanguage);
    dialog.close();
    const complexity = saved.analysis ? ` Time ${saved.analysis.time}, space ${saved.analysis.space}.` : '';
    toast(`Done. First review tomorrow, ${fmt(saved.nextDueOn)}.${complexity}`);
    await load();
  } catch (e) {
    err.textContent = e.message;
    err.hidden = false;
  } finally {
    btn.disabled = false;
  }
});

// ---------- account: settings and sign out
const settings = $('settings-dialog');
const settingsForm = $('settings-form');

let hasRecoveryCode = true;   // assume yes until /api/me says otherwise, so the reminder never flashes

async function checkRecoveryCode() {
  try {
    const me = await api('/api/me');
    hasRecoveryCode = me.hasRecoveryCode;
  } catch { /* the dashboard already shows load errors */ }
  $('rc-banner').hidden = hasRecoveryCode;
}

function openSettings() {
  if (!state) return;
  settingsForm.startDate.value = state.planStart;
  $('settings-meta').textContent = `Signed in as ${state.username}`;
  $('settings-error').hidden = true;
  $('rc-status').textContent = hasRecoveryCode
    ? 'If you forget your password, your username and recovery code let you set a new one. Lost the code? Make a new one; the old one then stops working.'
    : 'You don’t have a recovery code yet. Make one now so you can reset your password if you ever forget it.';
  $('rc-create').hidden = false;
  $('rc-result').hidden = true;
  $('rc-error').hidden = true;
  $('rc-password').value = '';
  settings.showModal();
}
$('open-settings').addEventListener('click', openSettings);
$('rc-banner-open').addEventListener('click', () => { openSettings(); $('rc-password').focus(); });
$('settings-cancel').addEventListener('click', () => settings.close());

async function createRecoveryCode() {
  const err = $('rc-error');
  err.hidden = true;
  const password = $('rc-password').value;
  if (!password) { err.textContent = 'Enter your current password.'; err.hidden = false; return; }
  $('rc-button').disabled = true;
  try {
    const { recoveryCode } = await api('/api/me/recovery-code', { method: 'POST', body: JSON.stringify({ password }) });
    $('rc-code').textContent = recoveryCode;
    $('rc-status').textContent = 'Here’s your new recovery code.';
    $('rc-create').hidden = true;
    $('rc-result').hidden = false;
    hasRecoveryCode = true;
    $('rc-banner').hidden = true;
  } catch (e) {
    err.textContent = e.message;
    err.hidden = false;
  } finally {
    $('rc-button').disabled = false;
    $('rc-password').value = '';
  }
}
$('rc-button').addEventListener('click', createRecoveryCode);
// Enter in the password box makes the code instead of saving the start date
$('rc-password').addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); createRecoveryCode(); } });
$('rc-copy').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText($('rc-code').textContent);
    $('rc-copy').textContent = 'Copied ✓';
    setTimeout(() => ($('rc-copy').textContent = 'Copy'), 2000);
  } catch { /* select-and-copy by hand still works */ }
});

settingsForm.addEventListener('submit', async (ev) => {
  ev.preventDefault();
  const err = $('settings-error');
  err.hidden = true;
  if (!settingsForm.startDate.value) {
    err.textContent = 'Pick a start date.';
    err.hidden = false;
    return;
  }
  try {
    await api('/api/me', { method: 'PATCH', body: JSON.stringify({ startDate: settingsForm.startDate.value }) });
    settings.close();
    toast(`Start date set to ${fmt(settingsForm.startDate.value)}.`);
    await load();
  } catch (e) {
    err.textContent = e.message;
    err.hidden = false;
  }
});

$('sign-out').addEventListener('click', async () => {
  await api('/api/auth/signout', { method: 'POST' }).catch(() => {});
  location.href = '/auth.html#signin';
});

// ---------- heatmap tooltip (mouse + keyboard)
const tip = $('tooltip');
const heatmap = $('heatmap');
function showTip(cell) {
  if (!cell?.dataset.tip) return hideTip();
  const card = heatmap.closest('.card').getBoundingClientRect();
  const r = cell.getBoundingClientRect();
  tip.textContent = cell.dataset.tip;
  tip.style.left = `${r.left - card.left + r.width / 2}px`;
  tip.style.top = `${r.top - card.top}px`;
  tip.hidden = false;
}
function hideTip() { tip.hidden = true; }
heatmap.addEventListener('mouseover', (e) => showTip(e.target.closest('.cell')));
heatmap.addEventListener('mouseleave', hideTip);
heatmap.addEventListener('focusin', (e) => showTip(e.target.closest('.cell')));
heatmap.addEventListener('focusout', hideTip);

// ---------- helpers
function el(tag, cls = '', text) {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined) node.textContent = text;
  return node;
}

let toastTimer;
function toast(msg) {
  const t = $('toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), 3500);
}

load();
