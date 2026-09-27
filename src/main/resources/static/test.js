// A weekly test: for each practice problem, pick the pattern, then solve it and report how it went.
// The server holds the answers and only reveals the right pattern after you've picked one.
import { api } from './http.js';

const $ = (id) => document.getElementById(id);
const LEVEL = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };
const OUTCOME = { SOLVED: 'Solved', HINT: 'Solved with a hint', NOT_SOLVED: 'Couldn’t solve yet' };
const testId = new URLSearchParams(location.search).get('id');
let test = null;

const fmt = (iso) => new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' })
  .format(Date.parse(iso + 'T00:00:00Z'));


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

async function load() {
  if (!testId) { location.href = '/'; return; }
  try {
    test = await api(`/api/tests/${testId}`);
    render();
  } catch (e) {
    $('title').textContent = 'Couldn’t load this test';
    $('dates').textContent = e.message;
  }
}

function render() {
  $('title').textContent = `Week ${test.weekNumber} test`;
  $('dates').textContent = `${fmt(test.weekStart)} – ${fmt(test.weekEnd)} · ${test.items.length} problem${test.items.length === 1 ? '' : 's'}, one for each pattern you practised`;
  const root = $('questions');
  root.replaceChildren(...test.items.map(question));
  renderSummary();
}

function question(item) {
  const box = el('section', 'q');
  box.id = `q-${item.id}`;

  // Header: the practice problem.
  const head = el('div', 'q-head');
  head.append(el('span', 'q-num', String(item.position)));
  const link = el('a', 'q-title', item.problem.name);
  link.href = item.problem.url; link.target = '_blank'; link.rel = 'noopener';
  head.append(link);
  const meta = [item.problem.leetcodeNumber ? `LeetCode ${item.problem.leetcodeNumber}` : null, LEVEL[item.problem.difficulty]]
    .filter(Boolean).join(' · ');
  head.append(el('span', 'level', meta));
  box.append(head);

  // Step 1: which pattern?
  const step1 = el('div', 'q-step');
  step1.append(el('div', 'q-label', '1. Which pattern would you use?'));
  const opts = el('div', 'q-options');
  const answered = item.chosenPatternId != null;
  for (const o of item.options) {
    const label = el('label', 'q-option');
    const input = el('input');
    input.type = 'radio'; input.name = `p-${item.id}`; input.value = o.id;
    input.disabled = answered;
    input.checked = item.chosenPatternId === o.id;
    label.append(input, el('span', '', o.name));
    if (answered && o.id === item.correctPatternId) label.classList.add('right');
    if (answered && o.id === item.chosenPatternId && o.id !== item.correctPatternId) label.classList.add('wrong');
    opts.append(label);
  }
  step1.append(opts);

  if (!answered) {
    const check = el('button', 'btn primary', 'Check');
    check.type = 'button';
    check.addEventListener('click', () => choose(item, box, check));
    step1.append(check);
  } else {
    const right = item.chosenPatternId === item.correctPatternId;
    const verdict = el('div', `q-verdict ${right ? 'good' : 'bad'}`);
    verdict.append(el('b', '', right ? '✓ Right. ' : `✗ It’s ${item.correctPatternName}. `),
      document.createTextNode(item.idea));
    step1.append(verdict);
    step1.append(el('p', 'q-anchor', `Same pattern as ${item.anchorName}, which you solved this week.`));
  }
  box.append(step1);

  // Step 2: solve it, then report.
  const step2 = el('div', `q-step${answered ? '' : ' locked'}`);
  step2.append(el('div', 'q-label', '2. Solve it on LeetCode, then:'));
  if (item.outcome) {
    const done = el('p', 'q-result', `Recorded: ${OUTCOME[item.outcome]}.`);
    if (item.outcome === 'NOT_SOLVED') {
      done.textContent += ` ${item.anchorName} comes back for review tomorrow to strengthen this pattern.`;
    }
    step2.append(done);
  } else {
    const row = el('div', 'q-outcomes');
    for (const [key, label] of Object.entries(OUTCOME)) {
      const b = el('button', 'btn', label);
      b.type = 'button';
      b.disabled = !answered;
      b.addEventListener('click', () => report(item, key, row));
      row.append(b);
    }
    step2.append(row);
  }
  box.append(step2);
  return box;
}

async function choose(item, box, button) {
  const picked = box.querySelector(`input[name=p-${item.id}]:checked`);
  if (!picked) { toast('Pick a pattern first.'); return; }
  button.disabled = true;
  try {
    const updated = await api(`/api/tests/items/${item.id}/pattern`, {
      method: 'POST', body: JSON.stringify({ patternId: Number(picked.value) }),
    });
    replace(updated);
  } catch (e) {
    toast(e.message);
    button.disabled = false;
  }
}

async function report(item, outcome, row) {
  row.querySelectorAll('button').forEach((b) => (b.disabled = true));
  try {
    const updated = await api(`/api/tests/items/${item.id}/outcome`, {
      method: 'POST', body: JSON.stringify({ outcome }),
    });
    replace(updated);
    if (test.items.every((i) => i.outcome)) {
      test.completed = true;
      renderSummary();
      $('summary').scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  } catch (e) {
    toast(e.message);
    row.querySelectorAll('button').forEach((b) => (b.disabled = false));
  }
}

function replace(updated) {
  test.items = test.items.map((i) => (i.id === updated.id ? updated : i));
  $(`q-${updated.id}`).replaceWith(question(updated));
}

function renderSummary() {
  const box = $('summary');
  if (!test.completed) { box.hidden = true; return; }
  const n = test.items.length;
  const right = test.items.filter((i) => i.chosenPatternId === i.correctPatternId).length;
  const count = (o) => test.items.filter((i) => i.outcome === o).length;
  const revisit = test.items.filter((i) => i.chosenPatternId !== i.correctPatternId || i.outcome === 'NOT_SOLVED');

  box.replaceChildren();
  box.append(el('h2', '', 'Done. Nice work showing up for this.'));
  const stats = el('div', 'summary-stats');
  for (const [value, label] of [[`${right}/${n}`, 'patterns recognised'], [count('SOLVED'), 'solved'],
    [count('HINT'), 'with a hint'], [count('NOT_SOLVED'), 'not yet']]) {
    const s = el('div');
    s.append(el('b', '', String(value)), el('span', '', label));
    stats.append(s);
  }
  box.append(stats);
  if (revisit.length) {
    box.append(el('p', 'q-label', 'Worth another look:'));
    const ul = el('ul', 'revisit');
    revisit.forEach((i) => ul.append(el('li', '', `${i.correctPatternName}: review ${i.anchorName}, then retry ${i.problem.name}.`)));
    box.append(ul);
  } else {
    box.append(el('p', 'q-anchor', 'Every pattern recognised and solved. These patterns are sticking.'));
  }
  box.hidden = false;
}

load();
