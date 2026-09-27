// Sign up, sign in and password recovery (with a recovery code), on one page.
// The server repeats every check here; these only give faster feedback.
import { api as request } from './http.js';

const api = (path, options) => request(path, options, { redirectOn401: false });

const $ = (id) => document.getElementById(id);
const forms = ['signup', 'signin', 'recover', 'code'];
const USERNAME = /^[A-Za-z0-9_.-]{3,30}$/;


// ---------- which view is showing
function show(view, note) {
  forms.forEach((f) => ($(f).hidden = f !== view));
  $('tabs').hidden = !(view === 'signup' || view === 'signin');
  if (view === 'signup' || view === 'signin') {
    document.querySelector(`input[name=tab][value=${view}]`).checked = true;
  }
  setNote(note);
  const titles = { signup: 'Sign up', signin: 'Sign in', recover: 'Reset password', code: 'Your recovery code' };
  document.title = `${titles[view]} · DSA Tracker`;
  const first = $(view).querySelector('input');
  if (first) first.focus();
}

function setNote(text) {
  $('note').hidden = !text;
  $('note').textContent = text || '';
}

function showError(form, message) {
  const err = form.querySelector('.form-error');
  err.textContent = message;
  err.hidden = !message;
}

async function submitting(form, work) {
  const btn = form.querySelector('button[type=submit]');
  showError(form, '');
  btn.disabled = true;
  try {
    await work();
  } catch (e) {
    showError(form, e.message);
  } finally {
    btn.disabled = false;
  }
}

document.querySelectorAll('input[name=tab]').forEach((r) =>
  r.addEventListener('change', () => show(r.value)));
$('to-forgot').addEventListener('click', (e) => { e.preventDefault(); show('recover'); });
document.querySelectorAll('[data-back]').forEach((a) =>
  a.addEventListener('click', (e) => { e.preventDefault(); show('signin'); }));

// ---------- sign up
const signup = $('signup');
signup.startDate.value = new Date().toLocaleDateString('en-CA');   // today as YYYY-MM-DD

// Live username check, 300 ms after typing stops.
let usernameTimer;
let usernameOk = false;
signup.username.addEventListener('input', () => {
  clearTimeout(usernameTimer);
  const hint = $('username-hint');
  const value = signup.username.value.trim();
  usernameOk = false;
  hint.className = 'hint';
  if (!value) { hint.textContent = ''; return; }
  if (!USERNAME.test(value)) {
    hint.textContent = '3–30 characters: letters, numbers, _ . or -';
    hint.className = 'hint bad';
    return;
  }
  hint.textContent = 'Checking…';
  usernameTimer = setTimeout(async () => {
    try {
      const { available } = await api(`/api/auth/username-available?username=${encodeURIComponent(value)}`);
      if (signup.username.value.trim() !== value) return;   // typed more since
      usernameOk = available;
      hint.textContent = available ? '✓ Available' : '✗ Taken, try another';
      hint.className = available ? 'hint good' : 'hint bad';
    } catch {
      hint.textContent = '';
    }
  }, 300);
});

function checkMatch() {
  const hint = $('match-hint');
  const { password, confirmPassword } = signup;
  if (!confirmPassword.value) { hint.textContent = ''; return; }
  const same = password.value === confirmPassword.value;
  hint.textContent = same ? '✓ Passwords match' : '✗ Passwords don’t match';
  hint.className = same ? 'hint good' : 'hint bad';
}
signup.password.addEventListener('input', checkMatch);
signup.confirmPassword.addEventListener('input', checkMatch);

signup.addEventListener('submit', (ev) => {
  ev.preventDefault();
  const f = signup;
  if (!USERNAME.test(f.username.value.trim())) return showError(f, 'Choose a username of 3–30 letters, numbers, _ . or -');
  if (f.password.value.length < 8) return showError(f, 'Passwords are at least 8 characters');
  if (f.password.value !== f.confirmPassword.value) return showError(f, 'The passwords don’t match');
  if (!f.startDate.value) return showError(f, 'Pick a start date');

  submitting(f, async () => {
    const { me, recoveryCode } = await api('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify({
        username: f.username.value.trim(),
        password: f.password.value,
        confirmPassword: f.confirmPassword.value,
        startDate: f.startDate.value,
      }),
    });
    f.reset();
    showCode(me.username, recoveryCode, false);
  });
});

// ---------- sign in
const signin = $('signin');
signin.addEventListener('submit', (ev) => {
  ev.preventDefault();
  if (!signin.username.value.trim() || !signin.password.value) {
    return showError(signin, 'Enter your username and password');
  }
  submitting(signin, async () => {
    await api('/api/auth/signin', {
      method: 'POST',
      body: JSON.stringify({ username: signin.username.value.trim(), password: signin.password.value }),
    });
    location.href = '/';
  });
});

// ---------- forgot password: username + recovery code + new password
const recover = $('recover');
recover.addEventListener('submit', (ev) => {
  ev.preventDefault();
  const f = recover;
  if (!f.username.value.trim()) return showError(f, 'Enter your username');
  if (f.recoveryCode.value.replace(/[\s-]/g, '').length !== 16) return showError(f, 'Recovery codes have 16 letters and numbers, like K7QM-2XPA-9RTB-HW4N');
  if (f.password.value.length < 8) return showError(f, 'Passwords are at least 8 characters');
  if (f.password.value !== f.confirmPassword.value) return showError(f, 'The passwords don’t match');
  submitting(f, async () => {
    const { me, recoveryCode } = await api('/api/auth/recover', {
      method: 'POST',
      body: JSON.stringify({
        username: f.username.value.trim(),
        recoveryCode: f.recoveryCode.value.trim(),
        password: f.password.value,
        confirmPassword: f.confirmPassword.value,
      }),
    });
    f.reset();
    showCode(me.username, recoveryCode, true);
  });
});

// ---------- the recovery code, shown once
const codeForm = $('code');
let shownCode = '';
let shownFor = '';

function showCode(username, code, afterRecovery) {
  shownCode = code;
  shownFor = username;
  $('code-value').textContent = code;
  $('code-title').textContent = afterRecovery ? 'Password changed. Here’s your new recovery code' : 'Save your recovery code';
  $('code-help').textContent = afterRecovery
    ? 'Your old code no longer works, and any other devices were signed out. Save this new code: it’s shown only now.'
    : 'If you ever forget your password, this code and your username let you set a new one. It’s shown only now.';
  $('code-saved').checked = false;
  $('code-continue').disabled = true;
  show('code');
}

$('code-copy').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(shownCode);
    $('code-copy').textContent = 'Copied ✓';
    setTimeout(() => ($('code-copy').textContent = 'Copy'), 2000);
  } catch {
    // No clipboard access: select the text so it can be copied by hand
    const range = document.createRange();
    range.selectNodeContents($('code-value'));
    getSelection().removeAllRanges();
    getSelection().addRange(range);
  }
});

$('code-download').addEventListener('click', () => {
  const text = `DSA Tracker recovery code\n\nUsername: ${shownFor}\nRecovery code: ${shownCode}\n\n`
    + `If you forget your password: on the sign-in page choose "Forgot password?" and enter these.\n`
    + `The code works once; you'll get a new one when you use it.\n`;
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = `dsa-tracker-recovery-code-${shownFor}.txt`;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});

$('code-saved').addEventListener('change', (e) => { $('code-continue').disabled = !e.target.checked; });
codeForm.addEventListener('submit', (ev) => {
  ev.preventDefault();
  if ($('code-saved').checked) location.href = '/';
});

// ---------- start
// Already signed in? Go straight to the tracker.
fetch('/api/me').then((r) => { if (r.ok) location.href = '/'; }).catch(() => {});
show(location.hash === '#signin' ? 'signin' : location.hash === '#forgot' ? 'recover' : 'signup');
