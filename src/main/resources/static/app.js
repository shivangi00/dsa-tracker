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
/** "tomorrow", "in 3 days" */
const inDays = (n) => (n === 1 ? 'tomorrow' : `in ${n} days`);
/** Short gap for a button: "1 day", "8 days", "2.3 mo" */
const gapLabel = (n) => (n < 60 ? plural(n, 'day') : `${(n / 30).toFixed(1).replace(/\.0$/, '')} mo`);

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
  renderPlan(d);
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
    ? '<span class="icon" aria-hidden="true">·</span><span>Missed yesterday? That\'s fine. Try not to miss twice: one revision today keeps the habit going.</span>'
    : `<span class="icon" aria-hidden="true">·</span><span>Welcome back. Nothing was reset while you were away. Your revisions waited for you, so start with just one.</span>`;
  el.hidden = false;
}

function renderTiles(d) {
  const h = d.consistency, c = d.counts;
  $('revised-count').textContent = c.fullyRevised;
  $('revised-target').textContent = `/ ${c.target}`;
  $('revised-sub').textContent = c.improved
    ? `all 3 revisions done · ${c.improved} improved`
    : 'all 3 revisions done';

  $('due-count').textContent = c.dueToday;
  $('due-sub').innerHTML = c.overdue > 0
    ? `<span class="alert">${c.overdue} overdue</span>`
    : (c.dueToday === 0 ? 'All clear' : 'On time');

  $('solved-count').textContent = c.done;
  $('solved-target').textContent = `/ ${c.target}`;
  $('solved-bar').style.width = `${Math.min(100, (c.done / c.target) * 100)}%`;
  $('solved-sub').textContent = d.plan.newProblemsOpen
    ? `new problems until day ${d.plan.lastNewDayNumber}`
    : `closed on day ${d.plan.lastNewDayNumber}`;

  $('study-days').textContent = h.totalStudyDays;
  $('week-sub').textContent = h.weekNumber > 0
    ? `This week: ${h.daysThisWeek} of ${h.weeklyTarget}${h.daysThisWeek >= h.weeklyTarget ? ' ✓' : ''}`
    : `Your plan starts ${fmt(d.planStart)}`;

  $('calendar-note').textContent = plural(h.totalStudyDays, 'study day');
}

/** Pace for new problems, and the revisions due over the next two weeks. */
function renderPlan(d) {
  const p = d.pace, plan = d.plan;
  $('plan-note').textContent = `ends ${fmt(plan.end, { day: 'numeric', month: 'short' })}`;
  const pace = $('pace');
  pace.replaceChildren();
  if (!plan.newProblemsOpen) {
    pace.append(`New problems closed on day ${plan.lastNewDayNumber}. The rest of the plan is for revisions, all done by ${fmt(plan.end)}.`);
  } else if (p.left === 0) {
    pace.append('All 150 solved. Now it\'s revisions only.');
  } else {
    pace.append(el('b', '', `${p.neededPerDay} a day`),
      ` needed to solve the last ${p.left} by day ${plan.lastNewDayNumber} (${fmt(plan.lastNewDay, { day: 'numeric', month: 'short' })}).`);
    if (p.projectedDay) {
      const late = p.projectedDay > plan.lastNewDayNumber;
      pace.append(el('br'), `Your pace: ${p.yourPerDay} a day, `,
        el('span', late ? 'pace-late' : 'pace-ok', !late
          ? `on track to finish around day ${p.projectedDay}`
          : p.projectedDay > d.planDays
            ? 'too slow to finish within the plan'
            : `finishing around day ${p.projectedDay}, after day ${plan.lastNewDayNumber}`), '.');
    }
  }

  $('workload-cap').textContent = `daily cap ${plan.maxReviewsPerDay}`;
  const box = $('workload');
  box.replaceChildren();
  const top = Math.max(plan.maxReviewsPerDay, ...d.workload.map((w) => w.reviews));
  for (const w of d.workload) {
    const col = el('div', 'wl-day');
    col.title = `${fmt(w.date)}: ${plural(w.reviews, 'revision')}`;
    const bar = el('div', `wl-bar${w.reviews > plan.maxReviewsPerDay ? ' over' : ''}`);
    bar.style.height = `${(w.reviews / top) * 100}%`;
    const stack = el('div', 'wl-stack');
    stack.append(bar);
    col.append(el('span', 'wl-n', w.reviews ? String(w.reviews) : ''), stack,
      el('span', 'wl-label', w.date === d.today ? 'T' : fmt(w.date, { weekday: 'narrow' })));
    box.append(col);
  }
  const cap = el('div', 'wl-cap');
  cap.style.bottom = `calc(18px + ${(plan.maxReviewsPerDay / top) * 60}px)`;
  box.append(cap);
  box.setAttribute('aria-label', d.workload.map((w) => `${fmt(w.date)}: ${w.reviews}`).join(', '));
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

const REVIEW_RATINGS = [
  ['AGAIN', 'Again', "Couldn't solve it without help"],
  ['HARD', 'Hard', 'Solved it, with real effort'],
  ['GOOD', 'Good', 'Solved it with normal effort'],
  ['EASY', 'Easy', 'Quick and confident'],
];
const SOLVE_LABEL = { AGAIN: 'Forgot', HARD: 'Hard', GOOD: 'Medium', EASY: 'Easy' };
const REVIEW_LABEL = { AGAIN: 'Again', HARD: 'Hard', GOOD: 'Good', EASY: 'Easy' };
const short = (iso) => fmt(iso, { day: 'numeric', month: 'short' });

const slotAttempts = (p, k) => p.attempts.filter((a) => a.revision === k);
/** The attempt that completed revision k (the solve day for k = 0). */
const slotDone = (p, k) => (k === 0 ? slotAttempts(p, 0)[0]
  : [...slotAttempts(p, k)].reverse().find((a) => a.rating && a.rating !== 'AGAIN'));
const isDue = (p) => Boolean(p.nextDueOn) && p.nextDueOn <= state.today;
const nextRevision = (p) => p.revisionsDone + 1;

function renderDue(d) {
  const list = $('due-list');
  list.replaceChildren();
  $('today-note').textContent = d.consistency.activeToday
    ? '✓ Studied today'
    : 'Minimum for today: one revision or one new problem';

  if (d.due.length === 0) {
    const next = allProgress(d).map((p) => p.nextDueOn).filter(Boolean).sort()[0];
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
    const tries = slotAttempts(p, nextRevision(p)).length;
    main.append(title, metaLine([
      `Revision ${nextRevision(p)} of ${state.plan.revisions}${tries ? ` · try ${tries + 1}` : ''}`,
      LEVEL[p.difficulty],
      p.overdueDays > 0 ? ['overdue', `! Overdue ${plural(p.overdueDays, 'day')}`] : null,
    ]));
    const { button, panel } = panelToggle(p, 'today', 'revise', 'Revise');
    button.classList.replace('ghost', 'primary');
    li.append(main, button, panel);
    list.append(li);
  }
}

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
  $('catalog-summary').textContent = `${done} solved · ${d.counts.fullyRevised} fully revised · ${d.catalog.length - done} to do`;
  $('table-legend').textContent = `Each problem gets ${d.plan.revisions} revisions, all done by day ${d.planDays}. `
    + 'Open a ✓ to see that attempt: your notes, saved versions and their analyses.';

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
        difficulty: p.difficulty, progress: p })),
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
  const head = el('li', 'row row-head');
  head.setAttribute('aria-hidden', 'true');
  head.append(el('span', '', 'Problem'), el('span', '', 'NeetCode'), el('span', '', 'Solved'),
    el('span', '', 'Revision 1'), el('span', '', 'Revision 2'), el('span', '', 'Revision 3'), el('span'));
  list.append(head, ...rows);
  box.append(list);
  box.addEventListener('toggle', () => {
    if (box.open) openCats.add(name); else openCats.delete(name);
  });
  return box;
}

/**
 * One problem as a table row: Problem · NeetCode difficulty · Solved · Revision 1–3. A done cell
 * opens that problem's history; the next revision's cell says when it's due, or "Revise".
 */
function catalogRow(c) {
  const p = c.progress;
  const li = el('li', p ? 'row done' : 'row');

  const title = el('a', 'row-title', c.name);
  title.href = c.url; title.target = '_blank'; title.rel = 'noopener';
  title.title = c.name;
  const level = el('span', 'level', LEVEL[c.difficulty]);

  if (!p) {
    const mark = el('button', 'btn link', 'Mark done');
    mark.type = 'button';
    if (!state.plan.newProblemsOpen) {
      mark.disabled = true;
      mark.title = `New problems stopped on day ${state.plan.lastNewDayNumber}, so every revision fits before the plan ends.`;
    }
    mark.addEventListener('click', () => openDone(c));
    const solved = el('div', 'tcell solved');
    solved.append(mark);
    li.append(title, level, solved, el('div', 'tcell rev muted', '—'), el('div', 'tcell rev muted', '—'),
      el('div', 'tcell rev muted', '—'), el('div', 'actions'));
    return li;
  }

  const { button: toggle, panel, open } = panelToggle(p, 'list', 'history', 'History');
  const cells = [0, 1, 2, 3].map((k) => stageCell(p, k, open));

  const compact = el('div', 'tcell compact');
  const dots = el('span', 'dots');
  dots.setAttribute('aria-label', `${p.revisionsDone} of ${state.plan.revisions} revisions done`);
  for (let k = 1; k <= state.plan.revisions; k++) dots.append(el('i', k <= p.revisionsDone ? 'dot on' : 'dot'));
  if (isDue(p)) {
    const go = el('button', `btn link next${p.overdueDays > 0 ? ' overdue' : ''}`, nextLabel(p));
    go.type = 'button';
    go.addEventListener('click', () => open('revise'));
    compact.append(dots, go);
  } else {
    compact.append(dots, el('span', 'next', nextLabel(p)));
  }

  const undo = el('button', 'btn icon', '✕');
  undo.type = 'button';
  undo.title = `Undo "${c.name}"`;
  undo.setAttribute('aria-label', `Undo ${c.name}`);
  undo.addEventListener('click', () => remove(p));
  const actions = el('div', 'actions');
  actions.append(toggle, undo);

  li.append(title, level, ...cells, compact, actions, panel);
  return li;
}

function nextLabel(p) {
  if (p.fullyRevised) return '✓ Fully revised';
  if (p.overdueDays > 0) return `Revise · ${plural(p.overdueDays, 'day')} late`;
  if (isDue(p)) return 'Revise today';
  return `next ${short(p.nextDueOn)}`;
}

/** A Solved / Revision k cell. */
function stageCell(p, k, open) {
  const cell = el('div', k === 0 ? 'tcell solved' : 'tcell rev');
  const done = k === 0 || k <= p.revisionsDone ? slotDone(p, k) : null;
  if (done) {
    const tries = k === 0 ? 1 : slotAttempts(p, k).length;
    const b = el('button', 'stage done-stage');
    b.type = 'button';
    const rating = done.rating ? (k === 0 ? SOLVE_LABEL : REVIEW_LABEL)[done.rating] : '';
    b.append(el('span', 'stage-date', `✓ ${short(done.attemptedOn)}`),
      el('span', `stage-rating r-${(done.rating || '').toLowerCase()}`, rating + (tries > 1 ? ` · ${tries} tries` : '')));
    b.title = `${k === 0 ? 'Solved' : `Revision ${k}`} on ${fmt(done.attemptedOn)}. Open the history.`;
    b.addEventListener('click', () => open('history', done.id));
    cell.append(b);
  } else if (k === nextRevision(p) && !p.fullyRevised) {
    const tries = slotAttempts(p, k).length;
    if (isDue(p)) {
      const b = el('button', `btn stage-due${p.overdueDays > 0 ? ' overdue' : ''}`, 'Revise');
      b.type = 'button';
      b.title = p.overdueDays > 0 ? `Due ${fmt(p.nextDueOn)}, ${plural(p.overdueDays, 'day')} ago` : 'Due today';
      b.addEventListener('click', () => open('revise'));
      cell.append(b);
      if (p.overdueDays > 0) cell.append(el('span', 'stage-late', `${p.overdueDays}d late`));
    } else {
      cell.append(el('span', 'stage-next', `due ${short(p.nextDueOn)}`));
    }
    if (tries) cell.append(el('span', 'stage-tries', `↻ ${tries}`));
  } else {
    cell.classList.add('muted');
    cell.textContent = '—';
  }
  return cell;
}

// ---------- the panel under a problem: revise form and/or its attempt history
const openPanels = new Map();   // "where:id" → 'history' | 'revise', kept across reloads
const drafts = new Map();       // unsaved text in the forms, kept across reloads
const LANGUAGES = { JAVA: 'Java', PYTHON: 'Python', JAVASCRIPT: 'JavaScript', CPP: 'C++' };
const CONFIDENCE = { high: 'High confidence', medium: 'Medium confidence', low: 'Low confidence' };
let panelSeq = 0;
let focusAttempt = null;

function lastLanguage() {
  try { return localStorage.getItem('dsa.codeLanguage') || 'JAVA'; } catch { return 'JAVA'; }
}
function rememberLanguage(lang) {
  try { localStorage.setItem('dsa.codeLanguage', lang); } catch { /* private mode: fine */ }
}

function panelToggle(p, where, mode, label) {
  const key = `${where}:${p.id}`;
  const panel = el('div', 'panel');
  panel.id = `panel-${++panelSeq}`;
  const button = el('button', 'btn ghost', label);
  button.type = 'button';
  button.setAttribute('aria-controls', panel.id);

  const draw = () => {
    const current = openPanels.get(key);
    panel.hidden = !current;
    button.setAttribute('aria-expanded', String(Boolean(current)));
    button.textContent = current ? 'Close' : label;
    panel.replaceChildren();
    if (!current) return;
    if (current === 'revise' && isDue(p)) panel.append(reviseForm(p, key));
    else if (isDue(p)) {
      const go = el('button', 'btn primary revise-now', `Start revision ${nextRevision(p)}`);
      go.type = 'button';
      go.addEventListener('click', () => { openPanels.set(key, 'revise'); draw(); });
      panel.append(go);
    }
    panel.append(history(p, current === 'revise'));
    if (focusAttempt) {
      const target = panel.querySelector(`[data-attempt="${focusAttempt}"]`);
      focusAttempt = null;
      if (target) requestAnimationFrame(() => target.scrollIntoView({ block: 'nearest', behavior: 'smooth' }));
    }
  };
  const open = (m, attemptId) => {
    focusAttempt = attemptId || null;
    openPanels.set(key, m);
    draw();
  };
  button.addEventListener('click', () => {
    if (openPanels.has(key)) openPanels.delete(key); else openPanels.set(key, mode);
    draw();
  });
  draw();
  return { button, panel, open };
}

/** A language picker and code box; Tab indents. */
function codeEditor(draftKey, initial, initialLang) {
  const d = drafts.get(draftKey) || {};
  const uid = `ce-${++panelSeq}`;
  const lang = el('select', 'lang-select');
  lang.id = `${uid}-lang`;
  for (const [value, label] of Object.entries(LANGUAGES)) {
    const o = el('option', '', label);
    o.value = value;
    lang.append(o);
  }
  lang.value = d.lang || initialLang || lastLanguage();
  const code = el('textarea', 'code-input');
  code.rows = 9; code.maxLength = 10000; code.spellcheck = false;
  code.id = `${uid}-code`;
  code.value = d.code ?? initial ?? '';
  code.placeholder = 'Paste or type your solution';
  code.setAttribute('autocapitalize', 'off');
  code.setAttribute('autocomplete', 'off');
  code.addEventListener('keydown', indentWithTab);
  const remember = () => drafts.set(draftKey, { ...(drafts.get(draftKey) || {}), code: code.value, lang: lang.value });
  code.addEventListener('input', remember);
  lang.addEventListener('change', remember);
  return { lang, code, uid };
}

/**
 * Analyse (a preview: nothing saved) for a code box. Returns the button and a function telling
 * whether the current code is exactly what was analysed.
 */
function previewButton(code, lang, catalogId, slot, error) {
  let analysed = null;
  const btn = el('button', 'btn', 'Analyse');
  btn.type = 'button';
  btn.title = 'Estimate the time and space complexity of this code (nothing is saved)';
  const clear = () => { if (analysed !== null && code.value + lang.value !== analysed) { analysed = null; slot.replaceChildren(); } };
  code.addEventListener('input', clear);
  lang.addEventListener('change', clear);
  btn.addEventListener('click', async () => {
    error.hidden = true;
    if (!code.value.trim()) { error.textContent = 'Add your code first, then analyse it.'; error.hidden = false; return; }
    btn.disabled = true;
    btn.textContent = 'Analysing…';
    try {
      const result = await api('/api/analysis/preview', {
        method: 'POST',
        body: JSON.stringify({ code: code.value, codeLanguage: lang.value, catalogId }),
      });
      analysed = code.value + lang.value;
      slot.replaceChildren(analysisBox(result));
    } catch (e) {
      error.textContent = e.message; error.hidden = false;
    } finally {
      btn.disabled = false;
      btn.textContent = 'Analyse';
    }
  });
  return { btn, isAnalysed: () => analysed !== null && analysed === code.value + lang.value };
}

function errorLine() {
  const e = el('p', 'form-error');
  e.setAttribute('role', 'alert');
  e.hidden = true;
  return e;
}

/** A revision: optional notes and code (with Analyse), then your rating saves it. */
function reviseForm(p, key) {
  const k = nextRevision(p);
  const tries = slotAttempts(p, k).length;
  const box = el('section', 'revise');
  box.append(el('h3', '', `Revision ${k} of ${state.plan.revisions}${tries ? ` · try ${tries + 1}` : ''}`),
    el('p', 'hint', 'Solve it again first. Your earlier attempts are below if you get stuck.'));

  const draftKey = `rev:${p.id}`;
  const d = drafts.get(draftKey) || {};
  const notes = el('textarea');
  notes.rows = 3; notes.maxLength = 2000; notes.value = d.notes || '';
  notes.placeholder = 'e.g. Forgot the length check; remembered the hash map straight away';
  notes.addEventListener('input', () => drafts.set(draftKey, { ...(drafts.get(draftKey) || {}), notes: notes.value }));
  box.append(field('What did you notice this time?', notes, 'optional'));

  const { lang, code } = codeEditor(draftKey, '', null);
  const head = el('div', 'code-head');
  head.append(el('span', 'code-label', 'Your code (optional)'), lang);
  const error = errorLine();
  const slot = el('div', 'analysis-slot');
  const { btn: analyse } = previewButton(code, lang, p.catalogId, slot, error);
  const codeActions = el('div', 'notes-actions');
  codeActions.append(analyse, el('span', 'save-status', 'Saved with this revision when you rate it.'));
  box.append(head, code, codeActions, slot);

  box.append(el('p', 'rate-prompt', 'How did it go?'));
  const answer = el('div', 'answer');
  answer.setAttribute('role', 'group');
  answer.setAttribute('aria-label', `Rate revision ${k} of ${p.name}`);
  const buttons = REVIEW_RATINGS.map(([rating, label, hint]) => {
    const b = el('button', `btn rate rate-${rating.toLowerCase()}`);
    b.type = 'button';
    const gap = p.reviewGaps ? p.reviewGaps[rating] : undefined;
    b.append(el('span', 'rate-name', label));
    if (gap === 0) b.append(el('span', 'rate-gap', '✓ done'));
    else if (gap) b.append(el('span', 'rate-gap', gapLabel(gap)));
    b.title = gap === 0 ? `${hint}. Completes all ${state.plan.revisions} revisions.`
      : gap ? `${hint}. Next ${rating === 'AGAIN' ? 'try' : 'revision'} ${inDays(gap)}.` : hint;
    return b;
  });
  buttons.forEach((b, i) => b.addEventListener('click', async () => {
    error.hidden = true;
    buttons.forEach((x) => (x.disabled = true));
    const rating = REVIEW_RATINGS[i][0];
    try {
      const hasCode = code.value.trim() !== '';
      const updated = await api(`/api/problems/${p.id}/reviews`, {
        method: 'POST',
        body: JSON.stringify({ rating, learnings: notes.value.trim(), code: hasCode ? code.value : '', codeLanguage: hasCode ? lang.value : null }),
      });
      if (hasCode) rememberLanguage(lang.value);
      drafts.delete(draftKey);
      openPanels.delete(key);
      toast(updated.fullyRevised
        ? `All ${state.plan.revisions} revisions done: "${p.name}" is fully revised.`
        : rating === 'AGAIN'
          ? `No problem. Try revision ${k} again ${inDays(updated.intervalDays)} (${fmt(updated.nextDueOn)}).`
          : `Revision ${k} done. Revision ${k + 1} ${inDays(updated.intervalDays)} (${fmt(updated.nextDueOn)}).`);
      await load();
    } catch (e) {
      error.textContent = e.message; error.hidden = false;
      buttons.forEach((x) => (x.disabled = false));
    }
  }));
  answer.append(...buttons);
  box.append(answer, error);
  return box;
}

/** Every attempt, oldest first: notes, saved versions with their analyses, and today's editors. */
function history(p, collapsed) {
  const wrap = el('div', 'history');
  if (collapsed) {
    const d = el('details', 'history-toggle');
    d.append(el('summary', '', `Earlier attempts (${p.attempts.length})`));
    d.append(historyBody(p));
    wrap.append(d);
  } else {
    wrap.append(historyBody(p));
  }
  return wrap;
}

function historyBody(p) {
  const body = el('div', 'history-body');
  const trail = el('p', 'trail');
  p.attempts.forEach((a, i) => {
    const best = a.versions.filter((v) => v.analysis).map((v) => v.analysis.time);
    if (i) trail.append(el('span', 'trail-sep', '→'));
    trail.append(el('span', 'trail-step', `${attemptTitle(a, true)}${best.length ? ` ${best[best.length - 1]}` : ''}`));
  });
  body.append(trail);
  for (const a of p.attempts) body.append(attemptBlock(p, a));
  return body;
}

function attemptTitle(a, compact) {
  if (a.revision === 0) return 'Solved';
  return compact ? `Rev ${a.revision}${a.tryNo > 1 ? `.${a.tryNo}` : ''}` : `Revision ${a.revision}${a.tryNo > 1 ? ` · try ${a.tryNo}` : ''}`;
}

function attemptBlock(p, a) {
  const box = el('section', a.editable ? 'attempt editable' : 'attempt');
  box.dataset.attempt = a.id;
  const head = el('div', 'attempt-head');
  head.append(el('strong', '', attemptTitle(a, false)),
    el('span', 'meta', `${fmt(a.attemptedOn)}${a.rating ? ` · ${(a.revision === 0 ? SOLVE_LABEL : REVIEW_LABEL)[a.rating]}` : ''}`));
  if (a.editable) head.append(el('span', 'editable-tag', 'Editable until midnight'));
  else {
    const lock = el('span', 'frozen-tag', 'Frozen');
    lock.title = `Attempts can only be changed on their own day (${fmt(a.attemptedOn)}).`;
    head.append(lock);
  }
  if (a.versions.some((v) => v.improved)) head.append(el('span', 'improved-tag', 'Improved'));
  box.append(head);

  if (a.editable) box.append(notesEditor(a));
  else {
    if (a.learnings) box.append(el('p', 'notes-text', a.learnings));
    else if (a.revision > 0) box.append(el('p', 'muted small', 'No notes for this revision.'));
    if (a.excalidrawUrl) {
      const link = el('a', 'drawing-link', 'Open drawing in Excalidraw ↗');
      link.href = a.excalidrawUrl; link.target = '_blank'; link.rel = 'noopener';
      box.append(link);
    }
  }

  a.versions.forEach((v, i) => box.append(versionBlock(p, a, v, i === a.versions.length - 1)));
  if (a.editable) box.append(newVersionEditor(p, a));
  return box;
}

function notesEditor(a) {
  const form = el('form', 'notes-form');
  form.noValidate = true;
  const draftKey = `notes:${a.id}`;
  const d = drafts.get(draftKey) || {};
  const notes = el('textarea');
  notes.rows = 3; notes.maxLength = 2000; notes.value = d.notes ?? a.learnings ?? '';
  const drawing = el('input');
  drawing.type = 'url'; drawing.maxLength = 500; drawing.placeholder = 'https://excalidraw.com/#json=…';
  drawing.value = d.drawing ?? a.excalidrawUrl ?? '';
  const remember = () => drafts.set(draftKey, { notes: notes.value, drawing: drawing.value });
  notes.addEventListener('input', remember);
  drawing.addEventListener('input', remember);
  const error = errorLine();
  const save = el('button', 'btn', 'Save notes');
  save.type = 'submit';
  const status = el('span', 'save-status');
  const actions = el('div', 'notes-actions');
  actions.append(save, status);
  form.append(field(a.revision === 0 ? 'What you learned' : 'What you noticed', notes, a.revision === 0 ? null : 'optional'),
    field('Excalidraw link', drawing, 'optional'), actions, error);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    error.hidden = true;
    if (a.revision === 0 && !notes.value.trim()) { error.textContent = 'Write down what you learned.'; error.hidden = false; return; }
    if (drawing.value.trim() && !/^https:\/\/\S+$/.test(drawing.value.trim())) {
      error.textContent = 'The Excalidraw link must start with https://'; error.hidden = false; return;
    }
    save.disabled = true;
    try {
      await api(`/api/attempts/${a.id}`, { method: 'PATCH', body: JSON.stringify({ learnings: notes.value.trim(), excalidrawUrl: drawing.value.trim() }) });
      drafts.delete(draftKey);
      status.textContent = 'Saved';
      await load();
    } catch (err) {
      error.textContent = err.message; error.hidden = false;
      if (/frozen/i.test(err.message)) await load();
    } finally {
      save.disabled = false;
    }
  });
  return form;
}

/** One saved version: its code and analysis behind a toggle, and today's actions. */
function versionBlock(p, a, v, latest) {
  const box = el('details', 'version');
  box.open = latest && a.editable;
  const summary = el('summary');
  summary.append(el('span', 'v-name', `Version ${v.versionNo}`), el('span', 'v-lang', LANGUAGES[v.codeLanguage] || ''));
  summary.append(v.analysis
    ? el('span', 'v-cx', `${v.analysis.time} time · ${v.analysis.space} space`)
    : el('span', 'v-cx muted', 'not analysed'));
  if (v.improved) summary.append(el('span', 'improved-tag', 'Improved'));
  box.append(summary);
  const pre = el('pre', 'code-view');
  pre.append(el('code', '', v.code));
  box.append(pre);
  if (v.analysis) box.append(analysisBox(v.analysis));
  if (a.editable) {
    const error = errorLine();
    const actions = el('div', 'notes-actions');
    const analyse = el('button', 'btn', v.analysis ? 'Analyse again' : 'Analyse');
    analyse.type = 'button';
    const del = el('button', 'btn ghost', 'Delete version');
    del.type = 'button';
    const busy = async (btn, label, work) => {
      error.hidden = true;
      btn.disabled = true;
      const old = btn.textContent;
      btn.textContent = label;
      try { await work(); await load(); } catch (e) { error.textContent = e.message; error.hidden = false; btn.disabled = false; btn.textContent = old; }
    };
    analyse.addEventListener('click', () => busy(analyse, 'Analysing…', () => api(`/api/versions/${v.id}/analysis`, { method: 'POST' })));
    del.addEventListener('click', () => {
      if (!confirm(`Delete version ${v.versionNo}?`)) return;
      busy(del, 'Deleting…', () => api(`/api/versions/${v.id}`, { method: 'DELETE' }));
    });
    actions.append(analyse, del);
    box.append(actions, error);
  }
  return box;
}

/** "Save as new version": starts from your latest code, so you can improve it and keep both. */
function newVersionEditor(p, a) {
  const box = el('div', 'new-version');
  const last = a.versions[a.versions.length - 1];
  const full = a.versions.length >= 5;
  const { lang, code } = codeEditor(`ver:${a.id}`, last ? last.code : '', last ? last.codeLanguage : null);
  const head = el('div', 'code-head');
  head.append(el('span', 'code-label', a.versions.length ? 'New version' : 'Your code'), lang);
  const error = errorLine();
  const slot = el('div', 'analysis-slot');
  const { btn: analyse, isAnalysed } = previewButton(code, lang, p.catalogId, slot, error);
  const save = el('button', 'btn primary', 'Save as new version');
  save.type = 'button';
  const status = el('span', 'save-status', full ? 'You have 5 versions (the most). Delete one to save another.'
    : last ? 'Your earlier versions stay as they are.' : '');
  save.disabled = full;
  save.addEventListener('click', async () => {
    error.hidden = true;
    if (!code.value.trim()) { error.textContent = 'Add your code first.'; error.hidden = false; return; }
    if (last && code.value.trim() === last.code.trim() && lang.value === last.codeLanguage) {
      error.textContent = 'This is the same as your latest version. Change it first.'; error.hidden = false; return;
    }
    save.disabled = true;
    save.textContent = 'Saving…';
    try {
      await api(`/api/attempts/${a.id}/versions`, {
        method: 'POST',
        body: JSON.stringify({ code: code.value, codeLanguage: lang.value, analyse: isAnalysed() }),
      });
      rememberLanguage(lang.value);
      drafts.delete(`ver:${a.id}`);
      toast(isAnalysed() ? 'Saved as a new version, with its analysis.' : 'Saved as a new version.');
      await load();
    } catch (e) {
      error.textContent = e.message; error.hidden = false;
      save.disabled = false;
      save.textContent = 'Save as new version';
    }
  });
  const actions = el('div', 'notes-actions');
  actions.append(analyse, save, status);
  box.append(head, code, el('p', 'hint', 'Tab indents. Press Esc, then Tab, to move on.'), actions, error, slot);
  return box;
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

/**
 * The analysis of YOUR code (time, space and the working), then, separately, a nudge towards a
 * better approach if one exists, with the approach itself behind a hint so you can try first.
 */
function analysisBox(a) {
  const wrap = el('div', 'analysis-wrap');
  const box = el('section', 'analysis');
  box.setAttribute('aria-label', 'Complexity of your code');
  box.append(el('p', 'analysis-title', 'Your code'));
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
  if (a.reasons && a.reasons.length) box.append(workingBox(a.reasons));
  wrap.append(box);
  if (a.recommendation) wrap.append(recommendationBox(a.recommendation, a.confidence));
  const aboutYourCode = (a.reasons || []).filter((r) => r.startsWith('Interview: ')).map((r) => r.slice(11));
  if (aboutYourCode.length || (a.interviewTips && a.interviewTips.length)) {
    wrap.append(talkingPoints(aboutYourCode, a.interviewTips || []));
  }
  return wrap;
}

/** What to say out loud in an interview: points about your code (Claude only), then the problem's. */
function talkingPoints(aboutYourCode, tips) {
  const details = el('details', 'talking');
  details.append(el('summary', '', 'Talking points for an interview'));
  for (const [title, items] of [['About your code', aboutYourCode], ['For this problem', tips]]) {
    if (!items.length) continue;
    if (aboutYourCode.length) details.append(el('p', 'why-head', title));
    const list = el('ul');
    for (const t of items) list.append(el('li', '', t));
    details.append(list);
  }
  return details;
}

/** The working, grouped into Time and Space steps (each ending with its total), then any notes. */
function workingBox(reasons) {
  const details = el('details', 'analysis-why');
  details.open = true;
  details.append(el('summary', '', "How your code's complexity was worked out"));
  const groups = { Time: [], Space: [] };
  const notes = [];
  for (const r of reasons) {
    if (r.startsWith('Interview: ')) continue;   // shown under Talking points
    const m = /^(Time|Space): (.*)$/s.exec(r);
    if (m) groups[m[1]].push(m[2]); else notes.push(r);
  }
  if (!groups.Time.length && !groups.Space.length) {
    // analysed before steps were grouped: show them as they are
    const list = el('ul');
    for (const r of notes) list.append(el('li', '', r));
    details.append(list);
    return details;
  }
  for (const [name, steps] of Object.entries(groups)) {
    if (!steps.length) continue;
    const group = el('div', 'why-group');
    group.append(el('p', 'why-head', name));
    const list = el('ol');
    for (const step of steps) {
      if (/^total\b/i.test(step)) {
        // "total → O(n³). Why…": the result in bold, the why in normal text
        const [, result, why] = /^total\s*(.*?\.)(\s.*)?$/is.exec(step) || [null, step.replace(/^total\s*/i, ''), ''];
        const li = el('li', 'why-total');
        li.append(el('strong', '', `Total ${result}`), document.createTextNode(why || ''));
        list.append(li);
      } else {
        list.append(el('li', '', step));
      }
    }
    group.append(list);
    details.append(group);
  }
  if (notes.length) {
    const small = el('div', 'why-notes');
    for (const n of notes) small.append(el('p', '', n));
    details.append(small);
  }
  return details;
}

const VERDICTS = {
  faster: 'Can you make it faster?',
  leaner: 'Can you use less memory?',
  optimal: 'Already optimal',
  check: 'Worth a second look',
  fixed: 'Fixed-size input',
};

/** A nudge, not an answer: what's possible, with the approach behind a toggle. */
function recommendationBox(r, confidence) {
  const box = el('div', `rec rec-${r.verdict}`);
  box.append(el('p', 'rec-title', VERDICTS[r.verdict] || 'Best known approach'));
  box.append(el('p', 'rec-msg', r.message));
  const nudge = r.verdict === 'faster' || r.verdict === 'leaner';
  if (confidence === 'low' && nudge) {
    box.append(el('p', 'rec-hedge', 'The analysis above is a low-confidence estimate, so check it before rewriting.'));
  }
  box.append(approachHint(nudge ? 'Show a hint' : 'Compare with the known approach', r.approach));
  if (r.further) box.append(approachHint('Going further (advanced)', r.further));
  return box;
}

function approachHint(label, a) {
  const idea = el('details', 'rec-idea');
  idea.append(el('summary', '', label));
  const line = el('p', 'rec-line');
  line.append(el('strong', '', a.name), el('span', 'rec-cx', `${a.time} time · ${a.space} space`));
  idea.append(line, el('p', 'rec-text', a.idea));
  return idea;
}

document.querySelectorAll('input[name=show]').forEach((r) =>
  r.addEventListener('change', () => state && renderCatalog(state)));
$('catalog-search').addEventListener('input', () => state && renderCatalog(state));

// ---------- actions
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
    `${LEVEL[c.difficulty]} on NeetCode. Rate it for yourself: your rating decides the first review.`;
  document.querySelectorAll('#done-rating .rating-when').forEach((span) => {
    const gap = state.firstGaps && state.firstGaps[span.dataset.rating];
    span.textContent = gap ? `Review ${inDays(gap)}` : '';
  });
  dialog.showModal();
  doneForm.learnings.focus();
}

$('done-cancel').addEventListener('click', () => dialog.close());
$('done-rating').addEventListener('change', () => { $('done-error').hidden = true; });

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
  const rating = doneForm.rating.value;
  if (!rating) {
    err.textContent = 'Choose how it went: Forgot, Hard, Medium or Easy.';
    err.hidden = false;
    doneForm.querySelector('input[name=rating]').focus();
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
      body: JSON.stringify({ learnings, excalidrawUrl, code, codeLanguage, rating }),
    });
    if (codeLanguage) rememberLanguage(codeLanguage);
    dialog.close();
    const firstVersion = saved.attempts[0] && saved.attempts[0].versions[0];
    const a = firstVersion && firstVersion.analysis;
    const complexity = a ? ` Time ${a.time}, space ${a.space}.` : '';
    toast(`Done. First review ${inDays(saved.intervalDays)}, ${fmt(saved.nextDueOn)}.${complexity}`);
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
