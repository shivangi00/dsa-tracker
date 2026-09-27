// Sign up, sign in, forgot password and reset password, on one page.
// The server repeats every check here; these only give faster feedback.
import { api as request } from './http.js';

const api = (path, options) => request(path, options, { redirectOn401: false });

const $ = (id) => document.getElementById(id);
const forms = ['signup', 'signin', 'forgot', 'reset'];
const USERNAME = /^[A-Za-z0-9_.-]{3,30}$/;


// ---------- which view is showing
function show(view, note) {
  forms.forEach((f) => ($(f).hidden = f !== view));
  $('tabs').hidden = !(view === 'signup' || view === 'signin');
  if (view === 'signup' || view === 'signin') {
    document.querySelector(`input[name=tab][value=${view}]`).checked = true;
  }
  setNote(note);
  document.title = `${view === 'signup' ? 'Sign up' : view === 'signin' ? 'Sign in' : 'Reset password'} · DSA Tracker`;
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
$('to-forgot').addEventListener('click', (e) => { e.preventDefault(); show('forgot'); });
document.querySelectorAll('[data-back]').forEach((a) =>
  a.addEventListener('click', (e) => { e.preventDefault(); history.replaceState(null, '', '/auth.html'); show('signin'); }));

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
  if (!f.email.checkValidity()) return showError(f, 'Enter a valid email address');
  if (f.password.value.length < 8) return showError(f, 'Passwords are at least 8 characters');
  if (f.password.value !== f.confirmPassword.value) return showError(f, 'The passwords don’t match');
  if (!f.startDate.value) return showError(f, 'Pick a start date');

  submitting(f, async () => {
    await api('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify({
        username: f.username.value.trim(),
        email: f.email.value.trim(),
        password: f.password.value,
        confirmPassword: f.confirmPassword.value,
        startDate: f.startDate.value,
      }),
    });
    location.href = '/';
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

// ---------- forgot password
const forgot = $('forgot');
forgot.addEventListener('submit', (ev) => {
  ev.preventDefault();
  if (!forgot.email.checkValidity() || !forgot.email.value) return showError(forgot, 'Enter a valid email address');
  submitting(forgot, async () => {
    const { message } = await api('/api/auth/forgot', {
      method: 'POST',
      body: JSON.stringify({ email: forgot.email.value.trim() }),
    });
    forgot.reset();
    show('signin', message);
  });
});

// ---------- reset password (from the emailed link: /auth.html?reset=TOKEN)
const reset = $('reset');
const token = new URLSearchParams(location.search).get('reset');
reset.addEventListener('submit', (ev) => {
  ev.preventDefault();
  if (reset.password.value.length < 8) return showError(reset, 'Passwords are at least 8 characters');
  if (reset.password.value !== reset.confirmPassword.value) return showError(reset, 'The passwords don’t match');
  submitting(reset, async () => {
    await api('/api/auth/reset', {
      method: 'POST',
      body: JSON.stringify({ token, password: reset.password.value, confirmPassword: reset.confirmPassword.value }),
    });
    history.replaceState(null, '', '/auth.html');   // drop the token from the address bar
    reset.reset();
    show('signin', 'Password updated. Sign in with your new password.');
  });
});

// ---------- start
if (token) {
  show('reset');
} else {
  // Already signed in? Go straight to the tracker.
  fetch('/api/me').then((r) => { if (r.ok) location.href = '/'; }).catch(() => {});
  show(location.hash === '#signin' ? 'signin' : 'signup');
}
