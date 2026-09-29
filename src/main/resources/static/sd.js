// System design tracker. Plain JavaScript, no build step. All rules live on the server
// (/api/sd/...); this file shows the data and sends your answers.
import { api } from './http.js';

const $ = (id) => document.getElementById(id);
const DAY_MS = 86_400_000;
const toMs = (iso) => Date.parse(iso + 'T00:00:00Z');
const fmt = (iso, opts = { weekday: 'short', day: 'numeric', month: 'short' }) =>
  new Intl.DateTimeFormat('en-GB', { ...opts, timeZone: 'UTC' }).format(toMs(iso));
const short = (iso) => fmt(iso, { day: 'numeric', month: 'short' });
const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;
const inDays = (n) => (n === 1 ? 'tomorrow' : `in ${n} days`);
const daysBetween = (a, b) => Math.round((toMs(b) - toMs(a)) / DAY_MS);
const gapLabel = (n) => (n < 60 ? plural(n, 'day') : `${(n / 30).toFixed(1).replace(/\.0$/, '')} mo`);

const LEVEL = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };
const FIRST_LABEL = { AGAIN: 'Forgot', HARD: 'Hard', GOOD: 'Medium', EASY: 'Easy' };
const REVIEW_LABEL = { AGAIN: 'Again', HARD: 'Hard', GOOD: 'Good', EASY: 'Easy' };
const FIRST_RATINGS = [
  ['AGAIN', 'Forgot', 'Needed the solution for most of it'],
  ['HARD', 'Hard', 'Got there, with real effort'],
  ['GOOD', 'Medium', 'A reasonable design in about 45 minutes'],
  ['EASY', 'Easy', 'Confident, with time for deep dives'],
];
const REVIEW_RATINGS = [
  ['AGAIN', 'Again', 'Couldn\'t rebuild it'],
  ['HARD', 'Hard', 'Rebuilt it, with effort'],
  ['GOOD', 'Good', 'Rebuilt it well'],
  ['EASY', 'Easy', 'Quick and confident'],
];
const SECTIONS = [
  ['requirements', 'Requirements', 'Functional: what users can do (3–4 core features). Non-functional: scale, latency, availability, consistency. Rough numbers if they shape the design.'],
  ['entities', 'Core entities', 'The main things the system stores, e.g. User, Link, Click.'],
  ['api', 'API', 'One endpoint per functional requirement, e.g. POST /links { url } → { shortCode }'],
  ['highLevel', 'High-level design', 'The components (clients, services, databases, caches, queues) and how each request flows through them.'],
  ['deepDives', 'Deep dives', 'Where it breaks at scale and how you fix it: bottlenecks, trade-offs, failure cases.'],
];
const TIMER_SECONDS = 45 * 60;
const CLAUDE_URL = 'https://claude.ai/new';

let state = null;
const openPanels = new Map();   // "topic:key" / "problem:key" → 'quiz' | 'design' | 'history'
const openSources = new Map();  // same keys → which control opened it, so the same control closes it
const openGroups = new Set();
let groupsChosen = false;
const quizzes = new Map();      // topic key → the quiz being taken (questions and chosen answers)
const quizResults = new Map();  // topic key → the result just graded, shown until closed
const scores = new Map();       // draft key → last "Analyse" result
const timers = new Map();       // draft key → { startedAt, elapsed }
let panelSeq = 0;

// ---------- drafts: unsaved designs survive a reload (this browser only)
function draft(key) {
  try { return JSON.parse(localStorage.getItem(`sd.draft.${key}`)) || {}; } catch { return {}; }
}
function saveDraft(key, value) {
  try { localStorage.setItem(`sd.draft.${key}`, JSON.stringify(value)); } catch { /* private mode: fine */ }
}
function dropDraft(key) {
  try { localStorage.removeItem(`sd.draft.${key}`); } catch { /* fine */ }
  scores.delete(key);
  timers.delete(key);
}

// ---------- load and render
async function load() {
  try {
    state = await api('/api/sd/dashboard');
    render(state);
  } catch (e) {
    $('plan-line').textContent = `Couldn't load: ${e.message}`;
  }
}

function render(d) {
  const day = Math.min(Math.max(d.plan.day, 0), d.plan.days);
  $('plan-line').textContent = d.plan.day < 1
    ? `Plan starts ${fmt(d.plan.start)}`
    : `${fmt(d.today, { weekday: 'long', day: 'numeric', month: 'long' })} · Day ${day} of ${d.plan.days}`;
  renderStats(d);
  renderDue(d);
  renderTopics(d);
  renderProblems(d);
}

function renderStats(d) {
  const s = d.stats;
  $('topics-count').textContent = s.topicsStudied;
  $('topics-target').textContent = `/ ${s.topicsTotal}`;
  $('topics-bar').style.width = `${(s.topicsStudied / s.topicsTotal) * 100}%`;
  $('topics-sub').textContent = `${s.topicsRevised} fully revised`;
  $('problems-count').textContent = s.problemsDesigned;
  $('problems-target').textContent = `/ ${s.problemsTotal}`;
  $('problems-bar').style.width = `${(s.problemsDesigned / s.problemsTotal) * 100}%`;
  $('problems-sub').textContent = `${s.problemsRevised} fully revised`;
  $('due-count').textContent = s.dueToday;
  $('due-sub').textContent = d.plan.newOpen
    ? `New topics and problems until day ${d.plan.lastNewDayNo}`
    : `Day ${d.plan.lastNewDayNo} passed: revisions only`;
  $('accuracy').textContent = s.quizAccuracy == null ? '–' : `${s.quizAccuracy}%`;
  $('score-sub').textContent = s.averageScore == null ? 'Average design score: –' : `Average design score: ${s.averageScore} / 10`;
}

// ---------- today
function renderDue(d) {
  const list = $('due-list');
  list.replaceChildren();
  $('today-note').textContent = d.due.length ? `${plural(d.due.length, 'revision')} due` : '';
  if (!d.due.length) {
    list.append(el('li', 'empty', d.stats.topicsStudied + d.stats.problemsDesigned === 0
      ? 'Nothing due yet. Study a topic on Hello Interview, then take its quiz below.'
      : 'Nothing due today.'));
    return;
  }
  for (const r of d.due) {
    const li = el('li', 'due-item');
    const left = el('div');
    const name = el('span', 'due-title', r.name);
    left.append(name, metaLine([
      r.kind === 'TOPIC' ? 'Topic' : 'Design problem',
      `Revision ${r.revision}`,
      r.overdueDays > 0 ? el('span', 'overdue', `${plural(r.overdueDays, 'day')} late`) : 'due today',
    ]));
    const go = el('button', 'btn primary', r.kind === 'TOPIC' ? 'Take quiz' : 'Revise');
    go.type = 'button';
    go.addEventListener('click', () => {
      const key = `${r.kind === 'TOPIC' ? 'topic' : 'problem'}:${r.key}`;
      openPanels.set(key, r.kind === 'TOPIC' ? 'quiz' : 'design');
      openSources.set(key, 'do');
      openGroupOf(r);
      render(state);
      requestAnimationFrame(() => document.querySelector(`[data-row="${key}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
    });
    li.append(left, go);
    list.append(li);
  }
}

function openGroupOf(r) {
  if (r.kind === 'TOPIC') {
    const t = state.topics.find((x) => x.key === r.key);
    if (t) openGroups.add(`t:${t.section}`);
  } else {
    const p = state.problems.find((x) => x.key === r.key);
    if (p) openGroups.add(`p:${p.level}`);
  }
}

function metaLine(parts) {
  const m = el('div', 'meta');
  parts.forEach((p, i) => {
    if (i) m.append(el('span', 'sep', '·'));
    m.append(typeof p === 'string' ? document.createTextNode(p) : p);
  });
  return m;
}

// ---------- tables
function chooseDefaultGroups(d) {
  if (groupsChosen) return;
  groupsChosen = true;
  const t = d.topics.find((x) => !x.item);
  if (t) openGroups.add(`t:${t.section}`);
  const p = d.problems.find((x) => !x.item);
  if (p) openGroups.add(`p:${p.level}`);
}

function renderTopics(d) {
  chooseDefaultGroups(d);
  const root = $('topics');
  root.replaceChildren();
  const studied = d.topics.filter((t) => t.item).length;
  $('topics-summary').textContent = `${studied} studied · ${d.stats.topicsRevised} fully revised · ${d.topics.length - studied} to do`;
  const groups = new Map();
  for (const t of d.topics) {
    if (!groups.has(t.section)) groups.set(t.section, []);
    groups.get(t.section).push(t);
  }
  for (const [section, items] of groups) {
    const head = ['Topic', 'Studied', 'Revision 1', 'Revision 2', 'Revision 3', ''];
    root.append(group(`t:${section}`, section, items.filter((t) => t.item).length, items.length, head,
      items.map(topicRow), 'sd-topic'));
  }
}

function renderProblems(d) {
  chooseDefaultGroups(d);
  const root = $('problems');
  root.replaceChildren();
  const done = d.problems.filter((p) => p.item).length;
  $('problems-summary').textContent = `${done} designed · ${d.stats.problemsRevised} fully revised · ${d.problems.length - done} to do`;
  for (const level of ['EASY', 'MEDIUM', 'HARD']) {
    const items = d.problems.filter((p) => p.level === level);
    const head = ['Problem', 'Designed', 'Revision 1', 'Revision 2', ''];
    root.append(group(`p:${level}`, LEVEL[level], items.filter((p) => p.item).length, items.length, head,
      items.map(problemRow), 'sd-problem'));
  }
}

function group(id, name, doneCount, total, head, rows, kindClass) {
  const box = el('details', 'cat');
  box.open = openGroups.has(id);
  const summary = el('summary');
  summary.append(el('span', 'cat-name', name), el('span', 'cat-count', `${doneCount} / ${total}`));
  const bar = el('span', 'cat-bar');
  bar.setAttribute('aria-hidden', 'true');
  const fill = el('span', 'cat-bar-fill');
  fill.style.width = `${(doneCount / total) * 100}%`;
  bar.append(fill);
  summary.append(bar);
  const list = el('ul', `cat-list ${kindClass}`);
  const h = el('li', 'row row-head');
  h.setAttribute('aria-hidden', 'true');
  head.forEach((t) => h.append(el('span', '', t)));
  list.append(h, ...rows);
  box.append(summary, list);
  box.addEventListener('toggle', () => { if (box.open) openGroups.add(id); else openGroups.delete(id); });
  return box;
}

const isDue = (item) => Boolean(item.nextDueOn) && item.nextDueOn <= state.today;
const slotAttempts = (item, k) => item.attempts.filter((a) => a.revision === k);
const slotDone = (item, k) => {
  const list = slotAttempts(item, k);
  if (k === 0) return list[0];
  return list.find((a) => a.rating !== 'AGAIN') || null;
};

function nameLink(name, url) {
  const a = el('a', 'row-title', name);
  a.href = url; a.target = '_blank'; a.rel = 'noopener';
  a.title = `${name}: open on Hello Interview`;
  return a;
}

function topicRow(t) {
  const key = `topic:${t.key}`;
  const item = t.item;
  const li = el('li', item ? 'row done' : 'row');
  li.dataset.row = key;
  const title = nameLink(t.name, t.url);
  const { panel, bind } = panelToggle(key, item, t, item ? 'history' : 'quiz', item ? 'History' : 'Quiz');

  if (!item) {
    const quizBtn = el('button', 'btn link', 'Take quiz');
    quizBtn.type = 'button';
    quizBtn.title = state.plan.newOpen ? 'After reading it: 5 questions to mark it studied'
      : 'Practice only: new topics stopped on day ' + state.plan.lastNewDayNo;
    bind(quizBtn, 'quiz', 'first');
    const first = el('div', 'tcell solved');
    first.append(quizBtn);
    li.append(title, first, ...[1, 2, 3].map(() => el('div', 'tcell rev muted', '—')), el('div', 'actions'), panel);
    return li;
  }
  const cells = [0, 1, 2, 3].map((k) => stageCell(item, k, bind, 'quiz'));
  li.append(title, ...cells, compactCell(item, bind, 'quiz'), actions(item, t.name), panel);
  return li;
}

function problemRow(p) {
  const key = `problem:${p.key}`;
  const item = p.item;
  const li = el('li', item ? 'row done' : 'row');
  li.dataset.row = key;
  const title = nameLink(p.name, p.url);
  if (!p.scored) title.append(el('span', 'soon-tag', 'score soon'));
  const { panel, bind } = panelToggle(key, item, p, item ? 'history' : 'design', item ? 'History' : 'Design');

  if (!item) {
    const go = el('button', 'btn link', 'Design it');
    go.type = 'button';
    if (!state.plan.newOpen) {
      go.disabled = true;
      go.title = `New problems stopped on day ${state.plan.lastNewDayNo}, so every revision fits before the plan ends.`;
    }
    bind(go, 'design', 'first');
    const first = el('div', 'tcell solved');
    first.append(go);
    li.append(title, first, el('div', 'tcell rev muted', '—'), el('div', 'tcell rev muted', '—'), el('div', 'actions'), panel);
    return li;
  }
  const cells = [0, 1, 2].map((k) => stageCell(item, k, bind, 'design'));
  li.append(title, ...cells, compactCell(item, bind, 'design'), actions(item, p.name), panel);
  return li;
}

function actions(item, name) {
  const undo = el('button', 'btn icon delete');
  undo.type = 'button';
  undo.title = `Delete "${name}" and its history`;
  undo.setAttribute('aria-label', `Delete ${name}`);
  undo.append(binIcon());
  undo.addEventListener('click', async () => {
    if (!confirm(`Delete "${name}" and all its history? It goes back to "to do".`)) return;
    try { await api(`/api/sd/items/${item.id}`, { method: 'DELETE' }); await load(); toast(`"${name}" deleted.`); }
    catch (e) { toast(e.message); }
  });
  const box = el('div', 'actions');
  box.append(undo);
  return box;
}

function attemptLabel(item, a) {
  const labels = a.revision === 0 ? FIRST_LABEL : REVIEW_LABEL;
  if (item.kind === 'TOPIC' && a.quizTotal) return `${a.quizScore}/${a.quizTotal} · ${labels[a.rating]}`;
  const best = bestOf(a);
  return best == null ? labels[a.rating] : `${labels[a.rating]} · ${best}/10`;
}

const bestOf = (a) => {
  const s = (a.answers || []).map((x) => x.score).filter((x) => x != null);
  return s.length ? Math.max(...s) : null;
};

/** A Studied / Designed / Revision k cell. */
function stageCell(item, k, bind, doMode) {
  const cell = el('div', k === 0 ? 'tcell solved' : 'tcell rev');
  const done = k === 0 || k <= item.revisionsDone ? slotDone(item, k) : null;
  if (done) {
    const tries = k === 0 ? 1 : slotAttempts(item, k).length;
    const b = el('button', 'st-btn');
    b.type = 'button';
    b.append(el('span', 'st-date', short(done.attemptedOn)),
      el('span', `st-rating r-${done.rating.toLowerCase()}`, attemptLabel(item, done) + (tries > 1 ? ` · ${tries} tries` : '')));
    b.title = `${k === 0 ? (item.kind === 'TOPIC' ? 'Studied' : 'Designed') : `Revision ${k}`} on ${fmt(done.attemptedOn)}. Open the history.`;
    bind(b, 'history', `slot:${k}`);
    cell.append(b);
  } else if (k === item.revisionsDone + 1 && !item.fullyRevised) {
    const tries = slotAttempts(item, k).length;
    if (isDue(item)) {
      const b = el('button', `btn st-due${item.overdueDays > 0 ? ' overdue' : ''}`, item.kind === 'TOPIC' ? 'Quiz' : 'Revise');
      b.type = 'button';
      b.title = item.overdueDays > 0 ? `Due ${fmt(item.nextDueOn)}, ${plural(item.overdueDays, 'day')} ago` : 'Due today';
      bind(b, doMode, 'do');
      cell.append(b);
      if (item.overdueDays > 0) cell.append(el('span', 'st-late', `${item.overdueDays}d late`));
    } else {
      cell.append(el('span', 'st-next', `due ${short(item.nextDueOn)}`));
    }
    if (tries) cell.append(el('span', 'st-tries', `↻ ${tries}`));
  } else {
    cell.classList.add('muted');
    cell.textContent = '—';
  }
  return cell;
}

/** On phones the revision cells collapse into dots and the next step. */
function compactCell(item, bind, doMode) {
  const c = el('div', 'tcell compact');
  // On phones, where the date columns are hidden: one small tab per finished sitting.
  const chips = el('span', 'chips');
  for (let k = 0; k <= item.revisions; k++) {
    const done = k === 0 || k <= item.revisionsDone ? slotDone(item, k) : null;
    const first = item.kind === 'TOPIC' ? 'Studied' : 'Designed';
    if (done) {
      const b = el('button', 'chip', k === 0 ? first : `R${k}`);
      b.type = 'button';
      b.title = `${k === 0 ? first : `Revision ${k}`} on ${fmt(done.attemptedOn)}`;
      bind(b, 'history', `slot:${k}`);
      chips.append(b);
    } else if (k > 0) chips.append(el('span', 'chip todo', `R${k}`));
  }
  c.append(chips);
  if (item.fullyRevised) c.append(el('span', 'next', '✓ Fully revised'));
  else if (isDue(item)) {
    const go = el('button', `btn link next${item.overdueDays > 0 ? ' overdue' : ''}`,
      item.overdueDays > 0 ? `Revise · ${plural(item.overdueDays, 'day')} late` : 'Revise today');
    go.type = 'button';
    bind(go, doMode, 'do');
    c.append(go);
  } else c.append(el('span', 'next', `next ${short(item.nextDueOn)}`));
  return c;
}

// ---------- the panel under a row
function panelToggle(key, item, entry, mode, label) {
  const panel = el('div', 'panel');
  panel.id = `sd-panel-${++panelSeq}`;
  const button = el('button', 'btn ghost', label);
  button.type = 'button';
  button.setAttribute('aria-controls', panel.id);
  const isTopic = key.startsWith('topic:');

  const mark = (node) => {
    const on = openPanels.has(key) && openSources.get(key) === node.dataset.panelSource;
    node.classList.toggle('is-open', on);
    node.setAttribute('aria-expanded', String(on));
    if (node.dataset.openTitle) node.title = on ? 'Click again to close' : node.dataset.openTitle;
  };
  const close = () => {
    openPanels.delete(key);
    openSources.delete(key);
    if (isTopic) { quizzes.delete(entry.key); quizResults.delete(entry.key); }
  };
  const draw = () => {
    const current = openPanels.get(key);
    panel.hidden = !current;
    button.setAttribute('aria-expanded', String(Boolean(current)));
    button.textContent = current ? 'Close' : label;
    panel.parentElement?.querySelectorAll('[data-panel-source]').forEach(mark);
    panel.replaceChildren();
    if (!current) return;
    // A date cell opens just that sitting (like a tab); the History button opens everything.
    const src = openSources.get(key) || '';
    const only = current === 'history' && src.startsWith('slot:') && !quizResults.has(entry.key) ? Number(src.slice(5)) : null;
    if (item && only !== null) {
      panel.append(historyBox(entry, item, false, only));
      return;
    }
    if (isTopic) {
      if (current === 'quiz' || quizResults.has(entry.key)) panel.append(quizBox(entry, item));
      else if (item && isDue(item)) panel.append(startButton(`Take the revision ${item.revisionsDone + 1} quiz`, () => { openPanels.set(key, 'quiz'); draw(); }));
      else if (item) panel.append(startButton('Practice quiz (not saved)', () => { openPanels.set(key, 'quiz'); draw(); }, 'btn'));
    } else if (current === 'design' && (!item || isDue(item))) {
      panel.append(designForm(entry, item));
    } else if (item && isDue(item)) {
      panel.append(startButton(`Start revision ${item.revisionsDone + 1}`, () => { openPanels.set(key, 'design'); draw(); }));
    }
    if (item) panel.append(historyBox(entry, item, current !== 'history'));
  };
  /** Makes {@code node} open the panel (in mode {@code m}) and, clicked again, close it. */
  const bind = (node, m, source) => {
    node.dataset.panelSource = source;
    if (node.title) node.dataset.openTitle = node.title;
    node.setAttribute('aria-controls', panel.id);
    node.addEventListener('click', () => {
      if (openPanels.has(key) && openSources.get(key) === source) close();
      else { openSources.set(key, source); openPanels.set(key, m); }
      draw();
    });
    mark(node);
  };
  button.dataset.panelSource = 'button';
  button.addEventListener('click', () => {
    if (openPanels.has(key)) close();
    else { openPanels.set(key, mode); openSources.set(key, 'button'); }
    draw();
  });
  draw();
  return { button, panel, bind };
}

function startButton(text, onClick, cls = 'btn primary') {
  const b = el('button', `${cls} revise-now`, text);
  b.type = 'button';
  b.addEventListener('click', onClick);
  return b;
}

// ---------- topic quiz
function quizBox(topic, item) {
  const box = el('section', 'revise sd-quiz');
  const result = quizResults.get(topic.key);
  if (result) {
    box.append(quizResultView(topic, result));
    return box;
  }
  let quiz = quizzes.get(topic.key);
  if (!quiz) {
    box.append(el('p', 'muted', 'Loading questions…'));
    api(`/api/sd/topics/${encodeURIComponent(topic.key)}/quiz`).then((q) => {
      quizzes.set(topic.key, { ...q, chosen: {}, notes: '' });
      render(state);
    }).catch((e) => { box.replaceChildren(el('p', 'form-error', e.message)); });
    return box;
  }
  const heading = {
    FIRST: ['Quiz: mark as studied', 'Answer after reading the topic. Your score sets your first revision.'],
    REVIEW: [`Revision ${quiz.revision} of 3`, 'From memory: no peeking at the article. Your score sets the next revision.'],
    PRACTICE: ['Practice quiz', item ? 'Not due yet, so this won\'t be saved or change your schedule.'
      : `New topics stopped on day ${state.plan.lastNewDayNo}. Practice only; nothing is saved.`],
  }[quiz.mode];
  const link = el('a', 'drawing-link', 'Read it on Hello Interview ↗');
  link.href = topic.url; link.target = '_blank'; link.rel = 'noopener';
  box.append(el('h3', '', heading[0]), el('p', 'hint', heading[1]), link);

  quiz.questions.forEach((q, n) => {
    const qBox = el('fieldset', 'sd-q');
    qBox.append(el('legend', '', `${n + 1}. ${q.q}`));
    const opts = el('div', 'q-options');
    q.options.forEach((o) => {
      const lab = el('label', 'q-option');
      const input = el('input');
      input.type = 'radio'; input.name = `q-${topic.key}-${q.index}`; input.value = o;
      input.checked = quiz.chosen[q.index] === o;
      input.addEventListener('change', () => { quiz.chosen[q.index] = o; submit.disabled = !allAnswered(); });
      lab.append(input, el('span', '', o));
      opts.append(lab);
    });
    qBox.append(opts);
    box.append(qBox);
  });

  const notes = el('textarea');
  notes.rows = 3; notes.maxLength = 2000; notes.value = quiz.notes;
  notes.placeholder = 'Anything worth remembering: a trade-off, a number, when to use it';
  notes.addEventListener('input', () => { quiz.notes = notes.value; });
  if (quiz.mode !== 'PRACTICE') box.append(field('Notes to remember', notes, 'optional'));

  const allAnswered = () => quiz.questions.every((q) => quiz.chosen[q.index]);
  const error = errorLine();
  const submit = el('button', 'btn primary', quiz.mode === 'PRACTICE' ? 'Check answers' : 'Submit');
  submit.type = 'button';
  submit.disabled = !allAnswered();
  submit.addEventListener('click', async () => {
    error.hidden = true;
    submit.disabled = true;
    try {
      const res = await api(`/api/sd/topics/${encodeURIComponent(topic.key)}/quiz`, {
        method: 'POST',
        body: JSON.stringify({
          answers: quiz.questions.map((q) => ({ index: q.index, choice: quiz.chosen[q.index] })),
          notes: quiz.notes.trim(),
        }),
      });
      quizzes.delete(topic.key);
      quizResults.set(topic.key, res);
      if (res.saved) await load(); else render(state);
    } catch (e) {
      error.textContent = e.message; error.hidden = false; submit.disabled = false;
    }
  });
  const row = el('div', 'notes-actions');
  row.append(submit);
  box.append(row, error);
  return box;
}

function quizResultView(topic, r) {
  const wrap = el('div', 'sd-result');
  const label = (r.mode === 'FIRST' ? FIRST_LABEL : REVIEW_LABEL)[r.rating];
  const head = el('div', 'sd-score-head');
  head.append(el('span', 'sd-big', `${r.correct}/${r.total}`),
    el('span', `st-rating r-${r.rating.toLowerCase()}`, r.saved ? label : `${r.percent}% · practice`));
  wrap.append(head);
  if (r.saved && r.item) {
    const it = r.item;
    let msg;
    if (it.fullyRevised) msg = `All ${it.revisions} revisions done: "${topic.name}" is fully revised.`;
    else if (r.rating === 'AGAIN') msg = `Below 50%, so this revision comes back ${inDays(daysBetween(state.today, it.nextDueOn))} (${fmt(it.nextDueOn)}). Reread the topic first.`;
    else msg = `${r.mode === 'FIRST' ? 'Marked as studied.' : 'Revision done.'} Next quiz ${inDays(daysBetween(state.today, it.nextDueOn))} (${fmt(it.nextDueOn)}).`;
    wrap.append(el('p', 'sd-next', msg));
  } else {
    wrap.append(el('p', 'sd-next', 'Practice: nothing was saved.'));
  }
  const list = el('ol', 'sd-graded');
  for (const q of r.questions) {
    const li = el('li', q.right ? 'right' : 'wrong');
    li.append(el('p', 'sd-gq', q.q));
    if (q.right) li.append(el('p', 'sd-ga', `✓ ${q.answer}`));
    else li.append(el('p', 'sd-gw', `✗ ${q.chosen}`), el('p', 'sd-ga', `✓ ${q.answer}`));
    li.append(el('p', 'sd-why', q.why));
    list.append(li);
  }
  wrap.append(list);
  const again = el('button', 'btn', 'Close');
  again.type = 'button';
  again.addEventListener('click', () => {
    quizResults.delete(topic.key);
    openPanels.set(`topic:${topic.key}`, 'history');
    render(state);
  });
  const row = el('div', 'notes-actions');
  row.append(again);
  wrap.append(row);
  return wrap;
}

// ---------- design problems
/** First design or a due revision: timer, five parts, drawing link, notes, Analyse, Claude, then your rating. */
function designForm(problem, item) {
  const first = !item;
  const k = first ? 0 : item.revisionsDone + 1;
  const tries = first ? 0 : slotAttempts(item, k).length;
  const draftKey = first ? `new:${problem.key}` : `rev:${item.id}:${k}`;
  const d = draft(draftKey);
  const box = el('section', 'revise sd-design');
  box.append(el('h3', '', first ? `Design ${problem.name}` : `Revision ${k} of ${item.revisions}${tries ? ` · try ${tries + 1}` : ''}`),
    el('p', 'hint', first
      ? 'Treat it like the interview: set the timer, then work through the five parts. Short notes are fine.'
      : 'Design it again from scratch. Your earlier designs are below if you get stuck.'));

  const editor = sectionsEditor(draftKey, problem, d.sections || {});
  const drawing = el('input');
  drawing.type = 'url'; drawing.maxLength = 500; drawing.placeholder = 'https://excalidraw.com/#json=…';
  drawing.value = d.drawing || '';
  const notes = el('textarea');
  notes.rows = 2; notes.maxLength = 2000; notes.value = d.notes || '';
  notes.placeholder = 'What you missed, what to remember next time';
  const remember = () => saveDraft(draftKey, { ...draft(draftKey), drawing: drawing.value, notes: notes.value });
  drawing.addEventListener('input', remember);
  notes.addEventListener('input', remember);
  box.append(timerBar(draftKey), editor.node, drawingField(drawing),
    field(first ? 'Notes' : 'What did you notice this time?', notes, 'optional'));
  editor.drawing = () => drawing.value.trim();

  box.append(el('p', 'rate-prompt', 'How did it go?'));
  const answer = el('div', 'answer');
  answer.setAttribute('role', 'group');
  const error = errorLine();
  const ratings = first ? FIRST_RATINGS : REVIEW_RATINGS;
  const gaps = first ? state.firstGaps.PROBLEM : item.previewGaps;
  const buttons = ratings.map(([rating, label, hint]) => {
    const b = el('button', `btn rate rate-${rating.toLowerCase()}`);
    b.type = 'button';
    const gap = gaps ? gaps[rating] : undefined;
    b.append(el('span', 'rate-name', label));
    if (gap === 0) b.append(el('span', 'rate-gap', '✓ done'));
    else if (gap) b.append(el('span', 'rate-gap', gapLabel(gap)));
    b.title = gap === 0 ? `${hint}. Completes all revisions.` : gap ? `${hint}. Next ${inDays(gap)}.` : hint;
    return b;
  });
  buttons.forEach((b, i) => b.addEventListener('click', async () => {
    error.hidden = true;
    const url = drawing.value.trim();
    if (url && !/^https:\/\/\S+$/.test(url)) { error.textContent = 'The drawing link must start with https://'; error.hidden = false; return; }
    const sections = editor.value();
    if (first && !notes.value.trim() && !url && !hasText(sections)) {
      error.textContent = 'Write your design, add notes or link your drawing first.'; error.hidden = false; return;
    }
    buttons.forEach((x) => (x.disabled = true));
    const rating = ratings[i][0];
    try {
      const body = JSON.stringify({ rating, notes: notes.value.trim(), excalidrawUrl: url, sections: hasText(sections) ? sections : null });
      const updated = first
        ? await api(`/api/sd/problems/${encodeURIComponent(problem.key)}/done`, { method: 'POST', body })
        : await api(`/api/sd/items/${item.id}/reviews`, { method: 'POST', body });
      dropDraft(draftKey);
      openPanels.set(`problem:${problem.key}`, 'history');
      const gap = updated.nextDueOn ? daysBetween(state.today, updated.nextDueOn) : 0;
      toast(updated.fullyRevised ? `All revisions done: "${problem.name}" is fully revised.`
        : rating === 'AGAIN' && !first ? `No problem. Try revision ${k} again ${inDays(gap)}.`
          : `Saved. Revision ${updated.revisionsDone + 1} ${inDays(gap)} (${fmt(updated.nextDueOn)}).`);
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

const hasText = (s) => Object.values(s).some((v) => v && v.trim());

/**
 * The five parts of a design, with Analyse (coverage score), Analyse with Claude and the
 * reference design. Returns { node, value() }.
 */
function sectionsEditor(draftKey, problem, initial, extraActions = []) {
  const node = el('div', 'sd-sections');
  const inputs = {};
  for (const [name, label, hint] of SECTIONS) {
    const t = el('textarea', 'sd-part');
    t.rows = name === 'highLevel' || name === 'deepDives' ? 6 : 3;
    t.maxLength = 4000;
    t.placeholder = hint;
    t.value = initial[name] || '';
    t.addEventListener('input', () => {
      const current = draft(draftKey);
      saveDraft(draftKey, { ...current, sections: { ...(current.sections || {}), [name]: t.value } });
    });
    inputs[name] = t;
    node.append(field(label, t));
  }
  const value = () => Object.fromEntries(Object.entries(inputs).map(([k, t]) => [k, t.value]));
  const set = (s) => { for (const [k, t] of Object.entries(inputs)) t.value = s[k] || ''; };

  const slot = el('div', 'analysis-slot');
  const error = errorLine();
  const analyse = el('button', 'btn', 'Analyse');
  analyse.type = 'button';
  analyse.title = problem.scored ? 'Coverage score against the key points of a strong answer' : 'Scoring for this problem is coming soon';
  analyse.addEventListener('click', async () => {
    error.hidden = true;
    if (!hasText(value())) { error.textContent = 'Write at least one part first.'; error.hidden = false; return; }
    analyse.disabled = true;
    try {
      const s = await api('/api/sd/score', { method: 'POST', body: JSON.stringify({ problemKey: problem.key, sections: value() }) });
      scores.set(draftKey, s);
      slot.replaceChildren(scoreCard(s, problem));
    } catch (e) { error.textContent = e.message; error.hidden = false; }
    finally { analyse.disabled = false; }
  });
  const claude = el('button', 'btn claude-btn', 'Analyse with Claude ↗');
  claude.type = 'button';
  claude.title = 'Copies a review prompt with your design and opens Claude in a new tab. Paste with Ctrl+V (⌘V on Mac).';
  claude.addEventListener('click', () => analyseWithClaude(problem, value(), node.closest('.sd-design, .attempt')?.querySelector('input[type=url]')?.value, error));
  const actions = el('div', 'notes-actions');
  actions.append(analyse, claude, ...extraActions);
  node.append(actions, error, slot);
  if (scores.has(draftKey)) slot.append(scoreCard(scores.get(draftKey), problem));
  return { node, value, set, slot, error };
}

/** The coverage score: what you covered, what you missed, and the reference design behind a toggle. */
function scoreCard(s, problem) {
  const card = el('div', 'analysis sd-score');
  card.append(el('p', 'analysis-title', 'Coverage score'));
  if (!s.scored) {
    card.append(el('p', 'sd-soon', 'Scoring and a reference design for this problem are coming in the next update. '
      + 'You can still write, save and rate it, and "Analyse with Claude" works for every problem.'));
    return card;
  }
  const head = el('div', 'analysis-head');
  const metric = (label, value) => {
    const m = el('div', 'metric');
    m.append(el('span', 'metric-label', label), el('span', 'metric-value', value));
    return m;
  };
  head.append(metric('Score', `${s.score} / 10`), metric('Structure', `${s.structure} / 2`), metric('Key points', `${s.rubric} / 8`));
  card.append(head);
  const bar = el('div', 'bar');
  const fill = el('div', 'bar-fill');
  fill.style.width = `${s.score * 10}%`;
  bar.append(fill);
  card.append(bar, el('p', 'sd-parts', `${s.partsWritten} of 5 parts written (30+ characters each).`));
  if (s.missed.length) card.append(pointList('What a strong answer also covers', s.missed, 'missed'));
  if (s.hits.length) {
    const d = el('details', 'analysis-why');
    d.append(el('summary', '', `Covered (${s.hits.length})`), pointList(null, s.hits, 'hit'));
    card.append(d);
  }
  card.append(el('p', 'rec-hedge', 'This counts which key points you mention, not how well you explain them. '
    + 'For a real review, use "Analyse with Claude".'));
  card.append(referenceToggle(problem));
  return card;
}

function pointList(title, points, cls) {
  const wrap = el('div', `sd-points ${cls}`);
  if (title) wrap.append(el('p', 'why-head', title));
  const ul = el('ul');
  points.forEach((p) => ul.append(el('li', '', p)));
  wrap.append(ul);
  return wrap;
}

const references = new Map();
function referenceToggle(problem) {
  const d = el('details', 'talking sd-ref');
  d.append(el('summary', '', 'Show a reference design'));
  const body = el('div');
  d.append(body);
  d.addEventListener('toggle', async () => {
    if (!d.open || body.childElementCount) return;
    body.append(el('p', 'muted small', 'Loading…'));
    try {
      if (!references.has(problem.key)) references.set(problem.key, await api(`/api/sd/problems/${encodeURIComponent(problem.key)}`));
      body.replaceChildren(referenceView(references.get(problem.key)));
    } catch (e) { body.replaceChildren(el('p', 'form-error', e.message)); }
  });
  return d;
}

function referenceView(p) {
  const wrap = el('div', 'sd-ref-body');
  const r = p.reference;
  if (!r) { wrap.append(el('p', 'muted', 'No reference design yet.')); return wrap; }
  const part = (title, items) => { if (items?.length) wrap.append(pointList(title, items, 'ref')); };
  part('Functional requirements', r.functional);
  part('Non-functional requirements', r.nonFunctional);
  part('Core entities', r.entities);
  part('API', r.api);
  part('High-level design', r.highLevel);
  if (r.deepDives?.length) {
    wrap.append(el('p', 'why-head', 'Deep dives'));
    for (const dd of r.deepDives) {
      const x = el('div', 'sd-dive');
      x.append(el('strong', '', dd.title), el('p', '', dd.body));
      wrap.append(x);
    }
  }
  const link = el('a', 'drawing-link', 'Compare with the Hello Interview breakdown ↗');
  link.href = p.url; link.target = '_blank'; link.rel = 'noopener';
  wrap.append(link, el('p', 'rec-hedge', 'One good answer, written for this tracker. Interviews reward reasoning about trade-offs, so other designs can be just as strong.'));
  return wrap;
}

// ---------- Analyse with Claude: copy a review prompt, open Claude, paste there
function claudePrompt(problemName, s, drawing) {
  const part = (label, text) => `## ${label}\n${text && text.trim() ? text.trim() : '(not written)'}`;
  return [
    `I'm practising for system design interviews. Please review my design for "${problemName}" the way a senior interviewer at a top tech company would.`,
    '',
    'Please:',
    '1. Score it out of 10 and say what level it would pass at (junior, mid, senior).',
    '2. For each part below: what\'s strong, what\'s missing or wrong, and the follow-up question you\'d ask.',
    '3. List the three changes that would improve it most.',
    '4. Sketch what an excellent answer would add, especially in the deep dives.',
    '',
    part('Requirements', s.requirements),
    '',
    part('Core entities', s.entities),
    '',
    part('API', s.api),
    '',
    part('High-level design', s.highLevel),
    '',
    part('Deep dives', s.deepDives),
    ...(drawing && drawing.trim() ? ['', `(I also drew a diagram: ${drawing.trim()}. You may not be able to open it; judge the text.)`] : []),
  ].join('\n');
}

function analyseWithClaude(problem, sections, drawing, error) {
  error.hidden = true;
  if (!hasText(sections)) { error.textContent = 'Write at least one part first.'; error.hidden = false; return; }
  const prompt = claudePrompt(problem.name, sections, drawing);
  // Open the tab straight away, inside the click, so pop-up blockers allow it.
  const tab = window.open(CLAUDE_URL, '_blank');
  if (tab) tab.opener = null;
  const copied = navigator.clipboard?.writeText
    ? navigator.clipboard.writeText(prompt).then(() => true, () => false)
    : Promise.resolve(false);
  copied.then((ok) => {
    if (ok) {
      toast('Prompt copied. In the Claude tab, paste it with Ctrl+V (⌘V on Mac) and send. Not signed in? Claude will ask you to first.', 7000);
    } else {
      showPromptToCopy(prompt, error);
    }
    if (!tab && ok) toast('Prompt copied. Your browser blocked the new tab: open claude.ai/new and paste it there.', 7000);
  });
}

/** When the clipboard isn't available: show the prompt, selected, to copy by hand. */
function showPromptToCopy(prompt, near) {
  const box = el('div', 'sd-copy');
  const t = el('textarea', 'code-input');
  t.readOnly = true; t.rows = 8; t.value = prompt;
  box.append(el('p', 'hint', 'Copy this prompt (Ctrl+C / ⌘C), then paste it into Claude:'), t);
  near.after(box);
  t.focus(); t.select();
}

// ---------- history
function historyBox(entry, item, collapsed, only = null) {
  const wrap = el('div', 'history');
  const body = el('div', 'history-body');
  for (const a of item.attempts) if (only === null || a.revision === only) body.append(attemptBlock(entry, item, a));
  if (only !== null) { wrap.append(body); return wrap; }
  if (collapsed) {
    const d = el('details', 'history-toggle');
    d.append(el('summary', '', `Earlier attempts (${item.attempts.length})`), body);
    wrap.append(d);
  } else wrap.append(body);
  return wrap;
}

function attemptTitle(item, a) {
  if (a.revision === 0) return item.kind === 'TOPIC' ? 'Studied' : 'Designed';
  return `Revision ${a.revision}${a.tryNo > 1 ? ` · try ${a.tryNo}` : ''}`;
}

function attemptBlock(entry, item, a) {
  const box = el('section', a.editable ? 'attempt editable' : 'attempt');
  const head = el('div', 'attempt-head');
  head.append(el('strong', '', attemptTitle(item, a)), el('span', 'meta', `${fmt(a.attemptedOn)} · ${attemptLabel(item, a)}`));
  if (a.editable) head.append(el('span', 'editable-tag', 'Editable until midnight'));
  else {
    const lock = el('span', 'frozen-tag', 'Frozen');
    lock.title = `Attempts can only be changed on their own day (${fmt(a.attemptedOn)}).`;
    head.append(lock);
  }
  box.append(head);
  if (a.editable) box.append(notesEditor(a));
  else {
    if (a.notes) box.append(el('p', 'notes-text', a.notes));
    if (a.excalidrawUrl) {
      const link = el('a', 'drawing-link', 'Open drawing in Excalidraw ↗');
      link.href = a.excalidrawUrl; link.target = '_blank'; link.rel = 'noopener';
      box.append(link);
    }
    if (!a.notes && !a.excalidrawUrl && item.kind === 'TOPIC') box.append(el('p', 'muted small', 'No notes.'));
  }
  if (item.kind === 'PROBLEM') {
    a.answers.forEach((v) => box.append(answerBlock(entry, a, v)));
    if (a.editable) box.append(answerEditor(entry, a));
  }
  return box;
}

function notesEditor(a) {
  const form = el('form', 'notes-form');
  form.noValidate = true;
  const notes = el('textarea');
  notes.rows = 3; notes.maxLength = 2000; notes.value = a.notes || '';
  const drawing = el('input');
  drawing.type = 'url'; drawing.maxLength = 500; drawing.placeholder = 'https://excalidraw.com/#json=…';
  drawing.value = a.excalidrawUrl || '';
  const error = errorLine();
  const save = el('button', 'btn', 'Save notes');
  save.type = 'submit';
  const status = el('span', 'save-status');
  const actions = el('div', 'notes-actions');
  actions.append(save, status);
  form.append(field('Notes to remember', notes, 'optional'), drawingField(drawing), actions, error);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    error.hidden = true;
    if (drawing.value.trim() && !/^https:\/\/\S+$/.test(drawing.value.trim())) {
      error.textContent = 'The drawing link must start with https://'; error.hidden = false; return;
    }
    save.disabled = true;
    try {
      await api(`/api/sd/attempts/${a.id}`, { method: 'PATCH', body: JSON.stringify({ notes: notes.value.trim(), excalidrawUrl: drawing.value.trim() }) });
      status.textContent = 'Saved';
      await load();
    } catch (err) {
      error.textContent = err.message; error.hidden = false;
      if (/frozen/i.test(err.message)) await load();
    } finally { save.disabled = false; }
  });
  return form;
}

/** A saved design version: collapsed, its parts and score inside. Only Delete, on its day. */
function answerBlock(problem, a, v) {
  const box = el('details', 'version');
  const summary = el('summary');
  summary.append(el('span', 'v-name', `Version ${v.versionNo}`),
    v.score != null ? el('span', 'v-cx', `${v.score} / 10`) : el('span', 'v-cx muted', 'not scored'));
  box.append(summary);
  const parts = el('div', 'sd-saved');
  for (const [name, label] of SECTIONS) {
    const text = v.sections[name];
    if (!text) continue;
    parts.append(el('p', 'why-head', label), el('p', 'notes-text', text));
  }
  box.append(parts);
  if (v.score != null) {
    const s = { scored: true, score: v.score, structure: v.structure, rubric: Math.round((v.score - v.structure) * 10) / 10,
      partsWritten: SECTIONS.filter(([n]) => (v.sections[n] || '').trim().length >= 30).length, hits: v.hits, missed: v.missed };
    box.append(scoreCard(s, problem));
  }
  const act = el('div', 'notes-actions');
  const claude = el('button', 'btn claude-btn', 'Analyse with Claude ↗');
  claude.type = 'button';
  const error = errorLine();
  claude.addEventListener('click', () => analyseWithClaude(problem, v.sections, a.excalidrawUrl, error));
  act.append(claude);
  if (a.editable) {
    const del = el('button', 'btn ghost danger', 'Delete');
    del.type = 'button';
    del.addEventListener('click', async () => {
      if (!confirm(`Delete version ${v.versionNo}?`)) return;
      del.disabled = true;
      try { await api(`/api/sd/answers/${v.id}`, { method: 'DELETE' }); await load(); }
      catch (e) { error.textContent = e.message; error.hidden = false; del.disabled = false; }
    });
    act.append(del);
  }
  box.append(act, error);
  return box;
}

/** The design box under today's saved versions: Analyse, Save (as the next version), Delete (clears it). */
function answerEditor(problem, a) {
  const box = el('div', 'new-version');
  const draftKey = `ver:${a.id}`;
  const full = a.answers.length >= 5;
  const nextNo = a.answers.length ? Math.max(...a.answers.map((v) => v.versionNo)) + 1 : 1;
  const save = el('button', 'btn primary', 'Save');
  save.type = 'button';
  const clear = el('button', 'btn ghost danger', 'Delete');
  clear.type = 'button';
  clear.title = 'Clear this box (nothing saved is affected)';
  // The next version starts from the latest one, so you can improve it using the points you missed.
  const last = a.answers[a.answers.length - 1];
  const editor = sectionsEditor(draftKey, problem, draft(draftKey).sections || (last ? last.sections : {}), [save, clear]);
  box.append(el('span', 'code-label', last ? `Version ${nextNo} · starts from version ${last.versionNo}` : 'Your design'),
    timerBar(draftKey), editor.node);
  if (full) {
    save.disabled = true;
    save.title = 'You have 5 versions (the most). Delete one to save another.';
  }
  clear.addEventListener('click', () => {
    if (hasText(editor.value()) && !confirm('Clear this design?')) return;
    editor.set({});
    dropDraft(draftKey);
    editor.slot.replaceChildren();
    editor.error.hidden = true;
  });
  save.addEventListener('click', async () => {
    editor.error.hidden = true;
    if (!hasText(editor.value())) { editor.error.textContent = 'Write at least one part first.'; editor.error.hidden = false; return; }
    const same = (x) => SECTIONS.every(([n]) => (x.sections[n] || '').trim() === (editor.value()[n] || '').trim());
    if (a.answers.some(same)) { editor.error.textContent = 'You already saved this exact design. Change it first.'; editor.error.hidden = false; return; }
    save.disabled = true;
    save.textContent = 'Saving…';
    try {
      await api(`/api/sd/attempts/${a.id}/answers`, { method: 'POST', body: JSON.stringify({ sections: editor.value() }) });
      dropDraft(draftKey);
      toast('Saved as a new version, with its score.');
      await load();
    } catch (e) {
      editor.error.textContent = e.message; editor.error.hidden = false;
      save.disabled = false; save.textContent = 'Save';
    }
  });
  return box;
}

// ---------- the 45-minute timer
function timerBar(key) {
  const bar = el('div', 'sd-timer');
  const time = el('span', 'sd-time');
  time.dataset.timer = key;
  const toggle = el('button', 'btn', 'Start 45-min timer');
  toggle.type = 'button';
  const reset = el('button', 'btn ghost', 'Reset');
  reset.type = 'button';
  const draw = () => {
    const t = timers.get(key);
    toggle.textContent = !t ? 'Start 45-min timer' : t.startedAt ? 'Pause' : 'Resume';
    reset.hidden = !t;
    paintTimer(time, key);
  };
  toggle.addEventListener('click', () => {
    const t = timers.get(key) || { elapsed: 0, startedAt: null };
    if (t.startedAt) { t.elapsed += Date.now() - t.startedAt; t.startedAt = null; }
    else t.startedAt = Date.now();
    timers.set(key, t);
    draw();
  });
  reset.addEventListener('click', () => { timers.delete(key); draw(); });
  bar.append(time, toggle, reset);
  draw();
  return bar;
}

function paintTimer(node, key) {
  const t = timers.get(key);
  const used = t ? Math.floor((t.elapsed + (t.startedAt ? Date.now() - t.startedAt : 0)) / 1000) : 0;
  const left = TIMER_SECONDS - used;
  const mmss = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  node.textContent = left >= 0 ? mmss(left) : `+${mmss(-left)} over`;
  node.classList.toggle('over', left < 0);
  node.classList.toggle('running', Boolean(t && t.startedAt));
}

setInterval(() => {
  document.querySelectorAll('[data-timer]').forEach((n) => {
    const t = timers.get(n.dataset.timer);
    if (t && t.startedAt) paintTimer(n, n.dataset.timer);
  });
}, 1000);

// ---------- helpers
/** A link field with an "Open ↗" link beside it, shown once the box holds an https:// link. */
function drawingField(input) {
  const row = el('div', 'url-row');
  const open = el('a', 'btn open-link', 'Open ↗');
  open.target = '_blank'; open.rel = 'noopener';
  open.title = 'Open the drawing in a new tab';
  const sync = () => {
    const url = input.value.trim();
    const ok = /^https:\/\/\S+$/.test(url);
    open.hidden = !ok;
    if (ok) open.href = url; else open.removeAttribute('href');
  };
  input.addEventListener('input', sync);
  sync();
  row.append(input, open);
  const wrap = el('div', 'field');
  const span = el('span', '', 'Excalidraw link');
  span.append(el('em', '', 'optional'));
  wrap.append(span, row);
  return wrap;
}

function field(label, control, optional) {
  const wrap = el('label', 'field');
  const span = el('span', '', label);
  if (optional) span.append(el('em', '', optional));
  wrap.append(span, control);
  return wrap;
}

function errorLine() {
  const p = el('p', 'form-error');
  p.setAttribute('role', 'alert');
  p.hidden = true;
  return p;
}

function binIcon() {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('width', '16');
  svg.setAttribute('height', '16');
  svg.setAttribute('aria-hidden', 'true');
  for (const d of ['M3 6h18', 'M8 6V4h8v2', 'M19 6l-1 14H6L5 6', 'M10 11v6', 'M14 11v6']) {
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', d);
    path.setAttribute('fill', 'none');
    path.setAttribute('stroke', 'currentColor');
    path.setAttribute('stroke-width', '2');
    path.setAttribute('stroke-linecap', 'round');
    path.setAttribute('stroke-linejoin', 'round');
    svg.append(path);
  }
  return svg;
}

function el(tag, cls = '', text) {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined) node.textContent = text;
  return node;
}

let toastTimer;
function toast(msg, ms = 3500) {
  const t = $('toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), ms);
}

$('sign-out').addEventListener('click', async () => {
  await api('/api/auth/signout', { method: 'POST' }).catch(() => {});
  location.href = '/auth.html#signin';
});

api('/api/me').then((me) => { $('who').textContent = me.username; }).catch(() => {});
load();
