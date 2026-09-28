# DSA Tracker

A minimalist tracker for working through the **NeetCode 150**, for any number of people. Each person signs up, picks a start date, and each day marks problems done with what they learned (plus an optional Excalidraw link). Every problem gets three revisions, scheduled by an Anki-style algorithm driven by your own ratings and fitted to a 100-day plan; every attempt keeps your notes and saved code versions; a short weekly test checks you can spot the same patterns in new problems, and motivation comes from long-term memory rather than streaks.

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

**Hosting it for other people:** see [DEPLOY.md](DEPLOY.md) (GitHub → Neon Postgres → Render). No email service or domain needed. (Vercel was tried: Spring Boot starts too slowly for it.)

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
- **One database address.** On Render the single app instance connects directly to Neon. (If you ever run many instances, point `DB_URL` at Neon's `-pooler` address and set `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD` to the direct address and the same login, because schema changes need a direct connection.)

## The plan and the review algorithm

A 100-day plan: solve all 150 problems by **day 85**, and give every problem **3 revisions**, all finished by **day 100**. Days 86–100 are for revisions only (marking new problems done is refused after day 85; `app.last-new-problem-day`).

The problems table reads like a spreadsheet: **Problem · NeetCode difficulty · Solved · Revision 1 · Revision 2 · Revision 3**. Each ✓ opens that attempt; the next revision's cell shows its due date, or **Revise** when it's due. On a phone each row collapses to "●●○ · next 5 Oct".

Anki-style (SM-2) gaps driven by **your own ratings**: the same problem can be easy for one person and hard for another, so the schedule follows how it went for you, not NeetCode's label.

**When you mark a problem done**, you rate how it went:

| Your rating | Meaning | First revision | Ease |
|---|---|---|---|
| Forgot | needed a hint or the solution | tomorrow | 2.30 |
| Hard | solved it, with real effort | in 2 days | 2.35 |
| Medium | solved it with normal effort | in 3 days | 2.50 |
| Easy | quick and confident | in 5 days | 2.65 |

**At each revision** you press Again, Hard, Good or Easy; each button shows the gap it would give (d = days late):

| Button | Next gap | Ease |
|---|---|---|
| Again | the same revision again tomorrow (another try; doesn't count as one of the 3) | − 0.20 |
| Hard | (gap + d/4) × 1.2 | − 0.15 |
| Good | (gap + d/2) × ease | + 0.05 while below 2.5 (no "ease hell") |
| Easy | (gap + d) × ease × 1.3 | + 0.15 |

Hard, Good or Easy on the third revision **completes** the problem: fully revised, nothing more due. Medium then Good every time: 3 → 8 → 20 days.

- **The deadline.** Every gap is capped at *days left ÷ revisions left*, so the remaining revisions always fit before day 100. A Medium problem solved on day 85: revisions on days 88, 94 and 100. Problems solved early keep their natural, longer gaps.
- **Workload smoothing.** If 6 revisions are already due on a day, a new one is brought forward by up to a quarter of its gap (at most 3 days). Never later, so the deadline holds. The Plan panel shows the next 14 days as bars against the daily cap.
- **Pace.** The Plan panel shows how many new problems a day you need to finish by day 85, your pace so far, and where that lands.
- **Missing days resets nothing.** A revision stays due until you do it; later gaps then shrink to still fit the deadline.
- A problem is **mature** once its gap reaches 21 days (memory stages). Gaps are capped at 365 days.

## Attempts, notes and saved versions

Every sitting is an **attempt**: the solve day, then each revision (and each retry after Again). Each attempt keeps:

- the date and your rating;
- **notes**: required on the solve day ("What you learned"), optional at revisions ("What did you notice this time?"), plus an optional Excalidraw link;
- **saved versions** of your code, each with **its own analysis**. The code box has **Analyse** (a preview: nothing is saved) and **Save as new version**, which keeps the code as the next version and never touches earlier ones. Up to 5 versions per attempt. A saved version can be analysed later that day, or deleted.

An attempt is **editable on its own day and frozen after** (the server refuses changes with 409), so nothing is ever overwritten across days. A version whose time complexity beats everything you saved before it is marked **Improved**, and the dashboard counts the problems you've improved. Each problem's history starts with a trail like *Solved O(n²) → Rev 1 O(n) → Rev 2 O(n)*.

## Motivation without streaks

Streaks drop to zero after one missed day, which is exactly when people give up, and they reward opening the app rather than remembering. The dashboard shows numbers that never collapse:

| Shown | What it means |
| --- | --- |
| Long-term memory | Problems with a review gap of 21+ days (you'd remember them for 3+ weeks) |
| Memory bar | Every solved problem by stage: learning (< 1 week), strengthening (1–3 weeks), long-term (3+ weeks) |
| Recall rate | Reviews in the last 30 days that weren't Again, e.g. "11 of 14 (79%)" |
| Study days | Total days you've studied; it only goes up |
| This week: 3 of 5 | Days against a flexible weekly target (2 rest days built in; `app.weekly-target-days`) |
| Daily minimum | "One review or one new problem"; ✓ once done |
| Nudge | After one missed day: "try not to miss twice". After longer: "welcome back, nothing was reset" |

The calendar still shows the days you studied, but empty days are just rest days: no red marks.

## Weekly tests

Week *n* of your plan runs for 7 days from your start date. On the week's last day its test unlocks (no deadline).

- Every NeetCode 150 problem belongs to one of **73 patterns** (e.g. "Monotonic stack", "Binary search on the answer").
- For each pattern you practised that week, the test picks one **practice problem**: a *different* LeetCode problem that uses the same technique, never one you've had before (235 in total). At most 3 questions, so a test takes minutes, not an evening; patterns you forgot most come first.
- For each question: **1)** pick which pattern you'd use from four options (look-alikes from the same topic), graded instantly with a one-line explanation; **2)** solve it on LeetCode and report *Solved*, *Solved with a hint* or *Couldn't solve yet*.
- *Couldn't solve yet* brings the NeetCode problem with that pattern back for revision **tomorrow** (unless it's already fully revised).

Practice problems come from NeetCode's own wider list (`.problemSiteData.json`, entries outside the 150), so every LeetCode number and link is real.

## Complexity analysis

- **Code while you write your notes.** The Mark as done window and the revision form have the code box and **Analyse** too, so you can check the complexity before saving; the code is saved as version 1 of that attempt, with its analysis.
- **Your code** in Java, Python, JavaScript or C++ (up to 10,000 characters). Tab indents; Esc then Tab moves on.
- **Analyse** estimates time and space complexity and shows how it got there, with a confidence level:
  - **Built-in estimate** (always available, free, nothing leaves the server): reads the code's structure. Nested loops multiply; fixed loops (26 letters, 4 directions) are O(1); halving loops are O(log n); sliding windows and monotonic stacks are amortised; sort is O(n log n), heap operations O(log n). Recursion is classified as tree traversal, visit-once DFS/BFS, divide and conquer, memoised, backtracking or exponential. Space counts arrays, maps, 2-D tables and recursion depth, not the returned answer. It uses k for the size of each item (Group Anagrams is O(n·k)) and m·n for grids. Tested on 33 NeetCode solutions.
  - **Claude** (optional): set `ANTHROPIC_API_KEY` and Analyse asks Claude instead; if Claude can't be reached, the built-in estimate answers. Limited to 50 analyses per user per day.
- **The working is shown step by step** for *your* code: time steps then space steps, in line order (outer loop before inner), each ending with the total, so you can check the reasoning and spot mistakes.
- **Talking points for an interview**, under the analysis (collapsed): 2–3 points for each of the 150 problems (edge cases, clarifying questions, trade-offs), from `interview-tips.json`. With Claude analysis on, Claude adds points about your own code, such as an edge case it handles or misses.
- **Code must match the problem.** Code saved or analysed under a NeetCode problem must define LeetCode's function for it (`twoSum` for Two Sum) or, for design problems, its class (`LRUCache`), in any of the four languages. Otherwise it's refused with a message saying what's expected. The names are the `entry` field in `best-approaches.json`.
- **Better approach suggestions**, shown separately below your analysis as a nudge (the approach itself stays behind *Show a hint*, so you can try first). After every analysis, the result is compared with the best known approaches for that NeetCode problem (a hand-written list of 1–2 approaches for each of the 150, in `best-approaches.json`: name, time, space and the idea in a sentence or two). You see one of:
  - **Faster approach available**: its complexity, and the idea behind a *Show the idea* toggle (plus the memory it costs, if it uses more than yours).
  - **Less memory, same speed**: e.g. a one-row DP table instead of the full grid.
  - **Matches the best known**, with the approach it matches. For a few problems a rarely-expected faster method (Manacher's algorithm, say) is shown as *Going further*.
  - **Worth a second look** if the analysis claims better than the best known (usually the analysis missing something), or **Fixed-size input** for problems like Valid Sudoku or Reverse Bits, where every loop has a fixed bound and comparing makes no sense.

  To compare complexities written in different styles, each is evaluated at typical sizes (n = 1000, k = 20 for the length of one item) and counts as better only if it is at least 3× smaller: O(n log n) → O(n) counts, O(V + E) vs O(n) doesn't. Two exponential complexities aren't compared. Suggestions are worked out when the page loads, so improving the list improves old analyses too.

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
| GET | `/api/dashboard` | | due revisions, plan (key dates, pace, 14-day workload), memory stages, recall, study days, weekly tests, all 150 problems with their attempt history |
| POST | `/api/catalog/{catalogId}/done` | `{ learnings, excalidrawUrl, code?, codeLanguage?, rating }` | `rating` is AGAIN / HARD / GOOD / EASY (shown as Forgot / Hard / Medium / Easy; missing = GOOD) and sets the first revision; code is saved as version 1 and analysed; 409 after day 85 |
| POST | `/api/analysis/preview` | `{ code, codeLanguage, catalogId? }` | analyses code without saving it (the Mark as done window); with `catalogId`, includes a recommendation |
| POST | `/api/problems/{id}/reviews` | `{ rating, learnings?, code?, codeLanguage? }` | a revision attempt; code is saved as its version 1 and analysed; 409 if not due or fully revised (the older `{ remembered }` still works) |
| PATCH | `/api/attempts/{id}` | `{ learnings, excalidrawUrl }` | an attempt's notes; only on its day, else 409 |
| POST | `/api/attempts/{id}/versions` | `{ code, codeLanguage, analyse }` | Save as new version (max 5); `analyse: true` also analyses it |
| POST | `/api/versions/{id}/analysis` | | analyses a saved version; only on its attempt's day |
| DELETE | `/api/versions/{id}` | | deletes a saved version; only on its attempt's day |
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
                 ComplexityExpression, ApproachRecommender, BestApproaches ← "a better approach exists" (pure Java)
  service/       AuthService, ProblemService, AnalysisService, DashboardService, WeeklyTestService
  web/           Auth/Me/Problem/WeeklyTest controllers, JSON views, error handler
src/main/resources/
  db/migration/V1…V11      V3 = the 150 problems; V4 = accounts + adaptive schedule; V5 = patterns, practice problems, tests; V6 = sessions + version columns; V7 = shared rate limits; V8 = code + analysis; V9 = recovery codes; V10 = ratings; V11 = attempt history
  best-approaches.json    best known approaches for each of the 150 problems (edit to add or improve one)
  interview-tips.json     interview talking points for each of the 150 problems
  static/        http.js (fetch + CSRF header), auth.html/js, index.html + app.js, test.html/js, styles.css
src/test/java/…  unit tests for the pure rules + ApiIntegrationTest, RateLimitIntegrationTest (real Postgres via Testcontainers)
Dockerfile (Render), Dockerfile.vercel + vercel.json (Vercel, kept in case its limits change), docker-compose.yml, .github/workflows/ci.yml, .env.example, DEPLOY.md
```

## Configuration

All settings are environment variables; `.env.example` has production values to copy.

| Setting | Default (local) | Production |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Docker Postgres on localhost:5433 | Neon's direct address in JDBC form (`jdbc:postgresql://…/neondb?sslmode=require`), user and password |
| `DB_POOL_SIZE`, `DB_MIN_IDLE` | 10, 10 | 5 and 0 (lets a scale-to-zero database sleep) |
| `COOKIE_SECURE` | false | true (cookies only over HTTPS) |
| `RATE_LIMITS_ENABLED` | true | true |
| `ANTHROPIC_API_KEY` | (empty: built-in estimate) | optional: a key from console.anthropic.com makes Analyse use Claude |
| `ANALYSIS_MODEL` | `claude-haiku-4-5-20251001` | any Claude model id |
| `PORT`, `WEB_THREADS` | 8080, 200 | set by Render / `50` on Render's free 512 MB plan |

## The NeetCode 150 list

`V3__neetcode_150.sql` was generated from NeetCode's own problem data (`.problemSiteData.json` in [github.com/neetcode-gh/leetcode](https://github.com/neetcode-gh/leetcode)), using the entries flagged `neetcode150`, in roadmap order: 18 categories; 28 Easy, 101 Medium and 21 Hard. Links go to LeetCode.

## Next steps

1. **Deploy** it: [DEPLOY.md](DEPLOY.md).
2. **Package by feature** (optional refactor): group code as `auth/`, `problems/`, `reviews/`, `tests/` instead of by layer once the app grows.
3. **Faster start-up** (for Render's free plan, or to retry Vercel): Spring Boot class-data sharing (CDS) or a GraalVM native image.
4. **Account deletion and data export** (useful for GDPR).
5. **Password reset by email** as well as recovery codes, once there's a domain to send from.
