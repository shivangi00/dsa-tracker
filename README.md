# DSA Tracker

A minimalist tracker for working through the **NeetCode 150**, for any number of people. Each person signs up, picks a start date, and each day marks problems done with what they learned (plus an optional Excalidraw link). Reviews are scheduled by an adaptive spaced-repetition algorithm, a weekly test checks you can spot the same patterns in new problems, and motivation comes from long-term memory rather than streaks.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security · PostgreSQL 16 · Flyway · plain HTML/CSS/JS (no build step) · Spring Session (sessions in Postgres) · Docker · GitHub Actions CI

## Run it

You need Java 21, Maven and Docker.

```bash
docker compose up -d          # Postgres on localhost:5433
mvn spring-boot:run           # starts the app; Flyway creates and upgrades the tables
```

- App: http://localhost:8080 (you'll land on the sign-up page)
- Tests: `mvn test` (the integration tests start a throwaway Postgres in Docker, so Docker must be running)
- No Java on your machine? `docker compose --profile app up --build` runs the app in Docker too

**Hosting it for other people:** see [DEPLOY.md](DEPLOY.md) (GitHub → Neon Postgres → Vercel, or Render). No email service or domain needed.

The **first account** you create takes over any problems logged before accounts existed.

## Accounts

| Feature | How it works |
| --- | --- |
| Sign up | Username (3–30 letters, numbers, `_ . -`; checked live as you type; not case-sensitive), password (8–72 characters) typed twice, start date. Then a **recovery code** is shown once, with Copy and Download buttons |
| Sign in | Username + password. Signs you in with a session cookie that lasts 14 days |
| Forgot password | Username + recovery code + new password. The code works once: using it sets the new password, issues a new code and signs the account out everywhere else. No email needed |
| Recovery code | 16 characters like `K7QM-2XPA-9RTB-HW4N` (80 random bits, no look-alike letters; case, spaces and dashes don't matter). Settings → **Create a new recovery code** replaces it (asks for the password). Accounts without one see a reminder |
| Start date | Chosen at sign-up, changeable under **Settings**. It's day 1 of your heatmap; days before it never count as absent |
| Privacy | Every query is filtered by the signed-in user, so nobody sees anyone else's data |

## Security

| Threat | Defence |
| --- | --- |
| Stolen database | Passwords and recovery codes are stored only as BCrypt hashes; sessions hold no password or hash |
| Password or recovery-code guessing, sign-up spam | Per-IP limits (sign in 10 per 5 min, sign up 5/hour, recover 10/hour) plus per-account limits that hold even across many IPs (20 sign-ins and 5 recovery attempts per account per hour). 429 + `Retry-After`. Counts are shared in Postgres |
| Someone else signed in with a stolen password | Recovering the account ends every existing session for it |
| Another site submitting forms as you (CSRF) | Every POST/PATCH/DELETE needs an `X-XSRF-TOKEN` header matching the `XSRF-TOKEN` cookie, plus `SameSite=Strict` cookies |
| Injected scripts (XSS) | Content-Security-Policy allows scripts and styles only from this site; the UI builds the page with `textContent`, never raw HTML from users |
| Clickjacking | `frame-ancestors 'none'` |
| Session fixation / theft | New session id at sign-in; cookie is `HttpOnly`, `SameSite=Strict`, `Secure` in production |
| Finding out who has an account | "Forgot password" gives the same error, in the same time, for an unknown username as for a wrong code |
| Seeing other people's data | Every query is filtered by the signed-in user id; another user's id gets 404, never 403 |
| Leaked secrets | No secrets in the repo; everything comes from environment variables (`.env.example` lists them) |

## Many users at once

- **Stateless app servers.** Sessions (Spring Session JDBC) and rate-limit counts (`rate_limits`) live in Postgres, not in the server's memory, so the app can restart or run as several copies (as Vercel does when busy) without signing anyone out or splitting the counts.
- **Threads and a connection pool.** Tomcat handles up to 200 requests in parallel; HikariCP shares a small pool of database connections (`DB_POOL_SIZE`) among them.
- **Optimistic locking.** Problems, tests, test items and users carry a `version` column. If two tabs change the same row at once, the second save gets 409 "reload and try again" instead of silently overwriting.
- **Database rules as the last line.** Unique indexes (username, one catalog problem per user) and CHECK constraints catch races that the Java checks can't; they surface as 409.
- **Idempotent actions.** Marking a problem done twice or reviewing twice in a day is refused, so double-clicks and retries are harmless.
- **Graceful shutdown.** In-flight requests finish before a deploy stops the old copy.
- **Pooled database connections.** In production the app connects through Neon's connection pooler, so many app copies don't exhaust the database's connections; migrations use a direct connection.

## The review algorithm

Adaptive spaced repetition, similar to Anki's SM-2 but with only two answers: **Remembered** or **Forgot**.

| Situation | Next gap | Example |
| --- | --- | --- |
| You solve it (mark done) | 1 day | Day 1 → review day 2 |
| First successful review | 4 days | day 2 → day 6 |
| Each later success | gap × ease (ease starts at 2.25) | 4 → 9 → 20 → 45 … |
| Success, but late | gap × ease + half the days you were late | gap 20, 16 days late → 53 |
| Forgot | a fifth of the gap, kept between 3 and 7 days; ease − 0.2 (never below 1.3) | gap 20 → 4 days |

- **Missing days resets nothing.** A review stays due until you do it; the answer then decides the next gap.
- **Relearning is fast:** after forgetting, the gap climbs back (4 → 8 → 16 → 33) in a few steps.
- A problem is **mature** once its gap reaches 21 days. Gaps are capped at 365 days.

## Motivation without streaks

Streaks drop to zero after one missed day, which is exactly when people give up, and they reward opening the app rather than remembering. The dashboard shows numbers that never collapse:

| Shown | What it means |
| --- | --- |
| Long-term memory | Problems with a review gap of 21+ days (you'd remember them for 3+ weeks) |
| Memory bar | Every solved problem by stage: learning (< 1 week), strengthening (1–3 weeks), long-term (3+ weeks) |
| Recall rate | Reviews remembered in the last 30 days, e.g. "11 of 14 (79%)" |
| Study days | Total days you've studied; it only goes up |
| This week: 3 of 5 | Days against a flexible weekly target (2 rest days built in; `app.weekly-target-days`) |
| Daily minimum | "One review or one new problem"; ✓ once done |
| Nudge | After one missed day: "try not to miss twice". After longer: "welcome back, nothing was reset" |

The calendar still shows the days you studied, but empty days are just rest days: no red marks.

## Weekly tests

Week *n* of your plan runs for 7 days from your start date. On the week's last day its test unlocks (no deadline).

- Every NeetCode 150 problem belongs to one of **73 patterns** (e.g. "Monotonic stack", "Binary search on the answer").
- For each pattern you practised that week, the test picks one **practice problem**: a *different* LeetCode problem that uses the same technique, never one you've had before (235 in total). Up to 5 questions; patterns you forgot most come first.
- For each question: **1)** pick which pattern you'd use from four options (look-alikes from the same topic), graded instantly with a one-line explanation; **2)** solve it on LeetCode and report *Solved*, *Solved with a hint* or *Couldn't solve yet*.
- *Couldn't solve yet* brings the NeetCode problem with that pattern back for review **tomorrow**.

Practice problems come from NeetCode's own wider list (`.problemSiteData.json`, entries outside the 150), so every LeetCode number and link is real.

## Notes, code and complexity analysis

- **Editable on the day, frozen after.** On the day you solve a problem, its notes panel is a form: what you learned, the Excalidraw link and your code. At midnight (London time) it freezes into a read-only record of what you understood that day. The server enforces this (409 after the day), not just the page.
- **Your code** in Java, Python, JavaScript or C++ (up to 10,000 characters). Tab indents; Esc then Tab moves on.
- **Analyse** estimates time and space complexity and shows how it got there, with a confidence level:
  - **Built-in estimate** (always available, free, nothing leaves the server): reads the code's structure. Nested loops multiply; fixed loops (26 letters, 4 directions) are O(1); halving loops are O(log n); sliding windows and monotonic stacks are amortised; sort is O(n log n), heap operations O(log n). Recursion is classified as tree traversal, visit-once DFS/BFS, divide and conquer, memoised, backtracking or exponential. Space counts arrays, maps, 2-D tables and recursion depth, not the returned answer. It uses k for the size of each item (Group Anagrams is O(n·k)) and m·n for grids. Tested on 33 NeetCode solutions.
  - **Claude** (optional): set `ANTHROPIC_API_KEY` and Analyse asks Claude instead; if Claude can't be reached, the built-in estimate answers. Limited to 50 analyses per user per day.
- Changing the code clears its old analysis, since that analysis described different code.

## API

| Method | Path | Body | Notes |
| --- | --- | --- | --- |
| GET | `/api/auth/username-available?username=x` | | `{ available }` |
| POST | `/api/auth/signup` | `{ username, password, confirmPassword, startDate }` | 201 `{ me, recoveryCode }`, signs you in |
| POST | `/api/auth/signin` | `{ username, password }` | 401 "Wrong username or password" |
| POST | `/api/auth/signout` | | 204 |
| POST | `/api/auth/recover` | `{ username, recoveryCode, password, confirmPassword }` | `{ me, recoveryCode }` (the new code); signs out other sessions and signs you in; 400 if they don't match |
| GET / PATCH | `/api/me` | `{ startDate }` | your account: `{ username, startDate, hasRecoveryCode }` |
| POST | `/api/me/recovery-code` | `{ password }` | `{ recoveryCode }`: a new code; the old one stops working |
| GET | `/api/dashboard` | | due reviews, memory stages, recall, study days, weekly tests, all 150 problems with your progress |
| POST | `/api/catalog/{catalogId}/done` | `{ learnings, excalidrawUrl }` | first review tomorrow |
| POST | `/api/problems/{id}/reviews` | `{ remembered: true \| false }` | returns the new gap and due date |
| PATCH | `/api/problems/{id}/notes` | `{ learnings, excalidrawUrl, code, codeLanguage }` | only on the day it was solved, else 409 |
| POST | `/api/problems/{id}/analysis` | | analyses the saved code; only on the day it was solved |
| DELETE | `/api/problems/{id}` | | undo "done" |
| POST | `/api/tests/week/{n}` | | opens week n's test (creates it the first time); 409 before it unlocks |
| GET | `/api/tests/{id}` | | the test; right answers only for questions you've answered |
| POST | `/api/tests/items/{id}/pattern` | `{ patternId }` | step 1, once per question |
| POST | `/api/tests/items/{id}/outcome` | `{ outcome: SOLVED \| HINT \| NOT_SOLVED }` | step 2; the last one completes the test |

Everything except `/api/auth/**` needs a signed-in session (otherwise 401). Errors are JSON with a readable `detail`.

## Where things live

```
src/main/java/dev/shivangi/dsatracker/
  security/      SecurityConfig, CsrfCookieFilter, RateLimiter, DatabaseRateLimiter, RateLimitRules, RateLimitFilter, RecoveryCodes, AuthUser
  domain/        AppUser, CatalogProblem, Pattern, PracticeProblem, Problem, RevisionAttempt,
                 WeeklyTest, WeeklyTestItem, repositories
  repetition/    SpacedRepetitionPolicy, ScheduleState          ← the review algorithm (pure Java)
  consistency/   ConsistencyCalculator, ActivityRepository      ← study days, weekly target (pure Java + SQL)
  weekly/        TestBuilder                                    ← picks test questions (pure Java)
  analysis/      CodeStructure, HeuristicComplexityAnalyser, Cx ← the built-in complexity estimate (pure Java)
                 ClaudeComplexityAnalyser, FallbackComplexityAnalyser  ← optional Claude, with the estimate as backup
  service/       AuthService, ProblemService, AnalysisService, DashboardService, WeeklyTestService
  web/           Auth/Me/Problem/WeeklyTest controllers, JSON views, error handler
src/main/resources/
  db/migration/V1…V9      V3 = the 150 problems; V4 = accounts + adaptive schedule; V5 = patterns, practice problems, tests; V6 = sessions + version columns; V7 = shared rate limits; V8 = code + analysis; V9 = recovery codes
  static/        http.js (fetch + CSRF header), auth.html/js, index.html + app.js, test.html/js, styles.css
src/test/java/…  unit tests for the pure rules + ApiIntegrationTest, RateLimitIntegrationTest (real Postgres via Testcontainers)
Dockerfile (Render), Dockerfile.vercel + vercel.json (Vercel), docker-compose.yml, .github/workflows/ci.yml, .env.example, DEPLOY.md
```

## Configuration

All settings are environment variables; `.env.example` has production values to copy.

| Setting | Default (local) | Production |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Docker Postgres on localhost:5433 | Neon's pooled address, with `?sslmode=require` |
| `SPRING_FLYWAY_URL` | (same as `DB_URL`) | Neon's direct address, for migrations |
| `DB_POOL_SIZE`, `DB_MIN_IDLE` | 10, 10 | 3 on Vercel / 5 on Render, and 0 (lets a scale-to-zero database sleep) |
| `COOKIE_SECURE` | false | true (cookies only over HTTPS) |
| `RATE_LIMITS_ENABLED` | true | true |
| `ANTHROPIC_API_KEY` | (empty: built-in estimate) | optional: a key from console.anthropic.com makes Analyse use Claude |
| `ANALYSIS_MODEL` | `claude-haiku-4-5-20251001` | any Claude model id |
| `PORT`, `WEB_THREADS` | 8080, 200 | `8080` on Vercel, set by Render / leave |

## The NeetCode 150 list

`V3__neetcode_150.sql` was generated from NeetCode's own problem data (`.problemSiteData.json` in [github.com/neetcode-gh/leetcode](https://github.com/neetcode-gh/leetcode)), using the entries flagged `neetcode150`, in roadmap order: 18 categories; 28 Easy, 101 Medium and 21 Hard. Links go to LeetCode.

## Next steps

1. **Deploy** it: [DEPLOY.md](DEPLOY.md).
2. **Package by feature** (optional refactor): group code as `auth/`, `problems/`, `reviews/`, `tests/` instead of by layer once the app grows.
3. **Wake-up time** on Vercel: if it becomes a problem, try Spring Boot's class-data sharing (CDS) or move to an always-on instance.
4. **Account deletion and data export** (useful for GDPR).
5. **Password reset by email** as well as recovery codes, once there's a domain to send from.
