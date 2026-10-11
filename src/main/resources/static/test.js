// A weekly test. For each practice problem: mark the pattern (one of the NeetCode categories you
// study by), write what made you recognise it, add your code, get it reviewed by the AI chatbot
// you prefer (the page copies a prompt), paste the feedback back, then record how it went.
// What the tracker files the problem under is only revealed once a question is finished.
import { api } from './http.js';
import { notesBlock } from './reader.js';
const $ = (id) => document.getElementById(id);
const LEVEL = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };
const OUTCOME = { SOLVED: 'Solved', HINT: 'Solved with a hint', NOT_SOLVED: 'Couldn’t solve yet' };
const testId = new URLSearchParams(location.search).get('id');
let test = null;
const LANGUAGES = { JAVA: 'Java', PYTHON: 'Python', JAVASCRIPT: 'JavaScript', CPP: 'C++' };
const FENCE = { JAVA: 'java', PYTHON: 'python', JAVASCRIPT: 'javascript', CPP: 'cpp' };
const CHATBOTS = [['Claude', 'https://claude.ai/new'], ['ChatGPT', 'https://chatgpt.com/'], ['Gemini', 'https://gemini.google.com/app']];

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
  if (item.legacy) return legacyQuestion(item);
  return item.outcome ? finishedQuestion(item) : openQuestion(item);
}

function questionHead(item, box) {
  const head = el('div', 'q-head');
  head.append(el('span', 'q-num', String(item.position)));
  const link = el('a', 'q-title', item.problem.name);
  link.href = item.problem.url; link.target = '_blank'; link.rel = 'noopener';
  head.append(link);
  const meta = [item.problem.leetcodeNumber ? `LeetCode ${item.problem.leetcodeNumber}` : null, LEVEL[item.problem.difficulty]]
    .filter(Boolean).join(' · ');
  head.append(el('span', 'level', meta));
  box.append(head);
}

// ---------- unsaved answers survive a reload (this browser only)
const draftKey = (item) => `dsa.test.${item.id}`;
function loadDraft(item) {
  try { return JSON.parse(localStorage.getItem(draftKey(item))) || null; } catch { return null; }
}
function saveDraft(item, d) {
  try { localStorage.setItem(draftKey(item), JSON.stringify(d)); } catch { /* private mode: fine */ }
}
function dropDraft(item) {
  try { localStorage.removeItem(draftKey(item)); } catch { /* fine */ }
}

/** A question in progress: four steps, all on one card. */
function openQuestion(item) {
  const box = el('section', 'q');
  box.id = `q-${item.id}`;
  questionHead(item, box);
  const saved = { category: item.chosenCategory, recognition: item.recognition, code: item.code, codeLanguage: item.codeLanguage, feedback: '' };
  const d = { ...saved, ...(loadDraft(item) || {}) };
  const remember = () => saveDraft(item, current());

  // 1. Mark the pattern
  const s1 = step('1. Mark the pattern', 'Read the problem on LeetCode first, without hints or the solution.');
  const chips = el('div', 'pat-chips');
  chips.setAttribute('role', 'radiogroup');
  chips.setAttribute('aria-label', 'Pattern');
  for (const c of test.categories) {
    const label = el('label', 'pat-chip');
    const input = el('input');
    input.type = 'radio'; input.name = `cat-${item.id}`; input.value = c;
    input.checked = d.category === c;
    input.addEventListener('change', remember);
    label.append(input, el('span', '', c));
    chips.append(label);
  }
  s1.append(chips);
  box.append(s1);

  // 2. Why, and your code
  const s2 = step('2. What made you recognise it?', 'The clue in the problem: "contiguous subarray", "sorted input", "all combinations", "shortest path"…');
  const why = el('textarea');
  why.rows = 3; why.maxLength = 2000; why.value = d.recognition || '';
  why.placeholder = 'e.g. It asks for the longest substring with a condition, so a window that grows and shrinks';
  why.addEventListener('input', remember);
  s2.append(why);
  const codeHead = el('div', 'code-head');
  const lang = el('select', 'lang-select');
  lang.setAttribute('aria-label', 'Language');
  for (const [k, v] of Object.entries(LANGUAGES)) {
    const o = el('option', '', v); o.value = k; lang.append(o);
  }
  lang.value = d.codeLanguage || lastLanguage();
  const langNote = el('span', 'lang-note');
  codeHead.append(el('span', 'code-label', 'Your code (optional)'), langNote, lang);
  const code = el('textarea', 'code-input');
  code.rows = 10; code.maxLength = 10000; code.spellcheck = false; code.value = d.code || '';
  code.placeholder = 'Paste or type your solution';
  code.addEventListener('keydown', indentWithTab);
  code.addEventListener('input', () => {
    const guess = detectLanguage(code.value);
    if (guess && guess !== lang.value) { lang.value = guess; langNote.textContent = `Detected ${LANGUAGES[guess]}`; }
    remember();
  });
  lang.addEventListener('change', () => { langNote.textContent = ''; remember(); });
  s2.append(codeHead, code);
  box.append(s2);

  // 3. Get it reviewed
  const s3 = step('3. Get it reviewed', 'Copies a review prompt with your pattern, reasoning and code. Paste it into the AI chatbot you use.');
  const error = el('p', 'form-error');
  error.hidden = true;
  const copy = el('button', 'btn primary', 'Copy prompt to analyse');
  copy.type = 'button';
  const open = el('div', 'chatbots');
  open.hidden = true;
  open.append(el('span', 'hint', 'Copied. Open your chatbot and paste (Ctrl+V / ⌘V):'));
  for (const [name, url] of CHATBOTS) {
    const a = el('a', 'btn', `${name} ↗`);
    a.href = url; a.target = '_blank'; a.rel = 'noopener';
    open.append(a);
  }
  const fallback = el('div', 'prompt-copy');
  fallback.hidden = true;
  copy.addEventListener('click', async () => {
    error.hidden = true;
    const a = current();
    const problem = validate(a);
    if (problem) { error.textContent = problem; error.hidden = false; return; }
    const prompt = reviewPrompt(item, a);
    // Copy first, inside the click, so browsers allow it; then save the answer.
    let copied = false;
    try { await navigator.clipboard.writeText(prompt); copied = true; } catch { copied = false; }
    if (copied) { open.hidden = false; fallback.hidden = true; toast('Prompt copied.'); }
    else {
      const t = el('textarea', 'code-input');
      t.readOnly = true; t.rows = 8; t.value = prompt;
      fallback.replaceChildren(el('p', 'hint', 'Copy this prompt (Ctrl+C / ⌘C), then paste it into your chatbot:'), t);
      fallback.hidden = false;
      t.focus(); t.select();
    }
    try { await saveAnswer(item, a); } catch (e) { error.textContent = e.message; error.hidden = false; }
  });
  s3.append(copy, open, fallback, error);
  box.append(s3);

  // 4. Paste the feedback, then move on
  const s4 = step('4. Paste the feedback', 'Paste what the chatbot said, so it stays with this question. Optional, but it’s what makes the test worth revisiting.');
  const fb = el('textarea');
  fb.rows = 6; fb.maxLength = 10000; fb.value = d.feedback || '';
  fb.placeholder = 'Paste the review here';
  fb.addEventListener('input', remember);
  s4.append(fb, el('div', 'q-label q-label-gap', 'How did solving it go?'));
  const row = el('div', 'q-outcomes');
  const err2 = el('p', 'form-error');
  err2.hidden = true;
  for (const [key, label] of Object.entries(OUTCOME)) {
    const b = el('button', 'btn', label);
    b.type = 'button';
    b.addEventListener('click', async () => {
      err2.hidden = true;
      const a = current();
      const problem = validate(a);
      if (problem) { err2.textContent = problem; err2.hidden = false; return; }
      row.querySelectorAll('button').forEach((x) => (x.disabled = true));
      try {
        await saveAnswer(item, a);
        const updated = await api(`/api/tests/items/${item.id}/outcome`, {
          method: 'POST', body: JSON.stringify({ outcome: key, feedback: a.feedback }),
        });
        dropDraft(item);
        if (a.code) rememberLanguage(a.codeLanguage);
        replace(updated);
        moveOn(updated);
      } catch (e) {
        err2.textContent = e.message; err2.hidden = false;
        row.querySelectorAll('button').forEach((x) => (x.disabled = false));
      }
    });
    row.append(b);
  }
  s4.append(row, err2);
  box.append(s4);

  function current() {
    const picked = chips.querySelector('input:checked');
    const c = code.value.trim() ? code.value : '';
    return { category: picked ? picked.value : null, recognition: why.value, code: c, codeLanguage: c ? lang.value : null, feedback: fb.value };
  }
  return box;
}

function step(title, hint) {
  const s = el('div', 'q-step');
  s.append(el('div', 'q-label', title));
  if (hint) s.append(el('p', 'q-hint', hint));
  return s;
}

function validate(a) {
  if (!a.category) return 'Mark a pattern first.';
  if (!a.recognition.trim()) return 'Write what made you recognise the pattern.';
  return null;
}

async function saveAnswer(item, a) {
  const updated = await api(`/api/tests/items/${item.id}/answer`, {
    method: 'POST',
    body: JSON.stringify({ category: a.category, recognition: a.recognition.trim(), code: a.code, codeLanguage: a.codeLanguage }),
  });
  test.items = test.items.map((i) => (i.id === updated.id ? updated : i));
  return updated;
}

/** After finishing a question: scroll to the next unfinished one, or the summary. */
function moveOn(done) {
  const next = test.items.find((i) => !i.outcome && i.position > done.position) || test.items.find((i) => !i.outcome);
  if (test.items.every((i) => i.outcome)) {
    test.completed = true;
    renderSummary();
    $('summary').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } else if (next) {
    $(`q-${next.id}`).scrollIntoView({ behavior: 'smooth', block: 'start' });
    toast(`Saved. On to question ${next.position}.`);
  }
}

/** The prompt to paste into an AI chatbot. */
function reviewPrompt(item, a) {
  const p = item.problem;
  const title = `${p.leetcodeNumber ? `LeetCode ${p.leetcodeNumber}: ` : ''}${p.name} (${LEVEL[p.difficulty]})`;
  const lines = [
    `I'm practising pattern recognition for coding interviews (the NeetCode 150 roadmap). Please review my attempt at ${title}: ${p.url}`,
    '',
    `The pattern I'd use: ${a.category}`,
    `What made me recognise it: ${a.recognition.trim()}`,
    '',
  ];
  if (a.code) {
    lines.push(`My code (${LANGUAGES[a.codeLanguage]}):`, '```' + FENCE[a.codeLanguage], a.code.replace(/\s+$/, ''), '```');
  } else {
    lines.push("I haven't written code for it yet.");
  }
  lines.push('',
    'Please:',
    `1. Is "${a.category}" the right pattern? If another fits better, name it and the clue in the problem that points to it.`,
    '2. Is my reasoning sound? Which clue should I learn to spot next time?',
    a.code ? '3. Check my code: bugs, missed edge cases, and whether it would pass all tests.' : '3. Outline the steps I should code, without writing the full solution.',
    a.code ? '4. Give its time and space complexity, and the optimal complexity if mine isn\'t optimal.' : '4. Give the time and space complexity the optimal approach achieves.',
    '5. Explain the key idea of the optimal approach in a few lines (not a full solution unless my code is wrong).',
    '6. End with a one-line verdict I can save, e.g. "Pattern ✓ · Code ✓ · O(n) time, O(1) space".');
  return lines.join('\n');
}

/** A finished question: your answer, the review you pasted, and what the tracker files it under. */
function finishedQuestion(item) {
  const box = el('section', 'q done');
  box.id = `q-${item.id}`;
  questionHead(item, box);
  const body = el('div', 'q-step');

  const verdict = el('div', `q-verdict ${item.chosenCategory === item.correctCategory ? 'good' : 'note'}`);
  if (item.chosenCategory === item.correctCategory) {
    verdict.append(el('b', '', `✓ ${item.chosenCategory}. `), document.createTextNode(`The tracker files it there too: ${item.correctPatternName}. ${item.idea}`));
  } else {
    verdict.append(el('b', '', `You marked ${item.chosenCategory}. `),
      document.createTextNode(`The tracker files it under ${item.correctCategory} (${item.correctPatternName}): ${item.idea} Check what the review said: some problems fit more than one pattern.`));
  }
  body.append(verdict);
  body.append(el('p', 'q-anchor', `Same pattern as ${item.anchorName}, which you solved this week.`));

  body.append(el('div', 'q-label q-label-gap', 'What made you recognise it'), notesBlock(item.recognition));
  if (item.code) {
    const d = el('details', 'q-fold');
    d.append(el('summary', '', `Your code (${LANGUAGES[item.codeLanguage] || 'code'})`));
    const pre = el('pre', 'code-view');
    pre.append(el('code', '', item.code));
    d.append(pre);
    body.append(d);
  }
  const fbBox = el('details', 'q-fold');
  fbBox.open = Boolean(item.feedback);
  fbBox.append(el('summary', '', item.feedback ? 'AI feedback' : 'AI feedback (none pasted)'));
  if (item.feedback) fbBox.append(notesBlock(item.feedback));
  body.append(fbBox);

  const done = el('p', 'q-result', `Recorded: ${OUTCOME[item.outcome]}.`);
  if (item.outcome === 'NOT_SOLVED') done.textContent += ` ${item.anchorName} comes back for review tomorrow to strengthen this pattern.`;
  body.append(done);
  box.append(body);
  return box;
}

// ---------- code box helpers (the same behaviour as the dashboard's)
function lastLanguage() {
  try { return localStorage.getItem('dsa.codeLanguage') || 'JAVA'; } catch { return 'JAVA'; }
}
function rememberLanguage(lang) {
  try { if (lang) localStorage.setItem('dsa.codeLanguage', lang); } catch { /* fine */ }
}
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

function detectLanguage(src) {
  const code = src.replace(/(["'`])(?:\\.|(?!\1).)*\1/g, '""');   // strings can say anything
  const score = { PYTHON: 0, JAVA: 0, JAVASCRIPT: 0, CPP: 0 };
  const add = (lang, re, n = 1) => { if (re.test(code)) score[lang] += n; };
  add('PYTHON', /^\s*def\s+\w+\s*\(.*\)\s*(->\s*[^:]+)?:\s*$/m, 3);
  add('PYTHON', /^\s*class\s+\w+(\s*\([^)]*\))?\s*:\s*$/m, 2);
  add('PYTHON', /\bself\b/, 2);
  add('PYTHON', /^\s*(elif|for .+ in .+:|while .+:|if .+:)\s*$/m, 2);
  add('PYTHON', /\b(None|True|False|len\(|range\(|enumerate\(|List\[|Optional\[)/, 1);
  add('JAVA', /\b(public|private|protected)\s+(static\s+)?[\w<>\[\],\s]+\s+\w+\s*\(/, 3);
  add('JAVA', /\b(HashMap|ArrayList|HashSet|Deque|PriorityQueue|Integer|String)\b/, 1);
  add('JAVA', /\bnew\s+\w+(<[^>]*>)?\s*[\[(]/, 1);
  add('JAVA', /\bSystem\.out\b|\.length\b(?!\()/, 1);
  add('CPP', /#include\b|\bstd::|\busing namespace\b/, 4);
  add('CPP', /\bvector\s*<|\bunordered_(map|set)\b|\bpublic\s*:/, 3);
  add('CPP', /\bauto\b|->\w|\bnullptr\b/, 1);
  add('JAVASCRIPT', /\b(var|let|const)\s+\w+\s*=\s*(function\b|\([^)]*\)\s*=>|\w+\s*=>)/, 4);
  add('JAVASCRIPT', /\bfunction\s+\w+\s*\(|=>|===|!==|\bconsole\.log\b/, 2);
  add('JAVASCRIPT', /\bnew\s+(Map|Set)\s*\(|\.push\(|\blet\b/, 1);
  const ranked = Object.entries(score).sort((x, y) => y[1] - x[1]);
  return ranked[0][1] >= 2 && ranked[0][1] > ranked[1][1] ? ranked[0][0] : null;
}
/** A question answered with the old four-option quiz (before this change): shown as it was. */
function legacyQuestion(item) {
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
  const recognised = (i) => (i.legacy ? i.chosenPatternId === i.correctPatternId : i.chosenCategory === i.correctCategory);
  const right = test.items.filter(recognised).length;
  const count = (o) => test.items.filter((i) => i.outcome === o).length;
  const revisit = test.items.filter((i) => !recognised(i) || i.outcome === 'NOT_SOLVED');

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
