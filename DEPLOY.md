# Putting DSA Tracker online: a step-by-step guide for beginners

This guide takes you from "the app runs on my laptop" to "anyone can use it at a web address". Every step says what to click, what to type, and how to check it worked. Nothing here needs prior experience with hosting.

**Time:** about 1½ hours the first time.
**Cost:** nothing. Every service here starts on a free plan, and you don't need to buy a domain: your site gets a free `…vercel.app` address.

> Screens change: the websites below redesign their dashboards now and then. If a button has a slightly different name, look for the closest match. The *order* of the steps won't change.

---

## Contents

0. [The big picture](#0-the-big-picture-read-this-first)
1. [Before you start: tools and accounts](#1-before-you-start-tools-and-accounts)
2. [Check the app works on your laptop](#2-check-the-app-works-on-your-laptop)
3. [Put the code on GitHub](#3-put-the-code-on-github)
4. [Create the database on Neon](#4-create-the-database-on-neon)
5. [Run the app on Vercel](#5-run-the-app-on-vercel) (or [on Render](#5b-alternative-run-the-app-on-render))
6. [Check everything works](#6-check-everything-works)
7. [Optional extras](#7-optional-extras): your own web address, Claude analysis, email
8. [Everyday tasks after launch](#8-everyday-tasks-after-launch)
9. [Troubleshooting](#9-troubleshooting)
10. [Glossary](#10-glossary)

---

## 0. The big picture (read this first)

On your laptop, two things run together: the **Java app** and a **Postgres database** (in Docker). Online, each is replaced by a service that runs day and night, plus GitHub to hold the code:

| On your laptop | Online | Its job |
| --- | --- | --- |
| The code in a folder | **GitHub** | Stores the code and runs the tests every time you change it |
| `mvn spring-boot:run` | **Vercel** (or Render) | Runs the Java app and gives it an `https://` address |
| Postgres in Docker | **Neon** | Stores every account, problem, note and review |

How they connect:

```
 you edit code ──push──▶ GitHub ──tests pass──▶ Vercel builds and runs the app
                                                     │            │
                               visitors' browsers ◀──┘            └──▶ Neon (database)
```

**How the app finds the others: environment variables.** The code contains no passwords or addresses. When it starts, it reads settings such as `DB_URL` and `DB_PASSWORD` from its surroundings. On your laptop they fall back to the local defaults; online, you type them into Vercel's settings page. The file `.env.example` lists every one. This is why the code can be public on GitHub while your passwords stay private.

**No email needed.** Forgotten passwords are handled with a **recovery code** that each person gets when they sign up (and can make again in Settings). So there's no email service to set up and no domain to buy.

**Why not GitHub Pages?** GitHub Pages can only show fixed files (HTML, CSS, pictures). This app also needs a program running on a server to check passwords and save your progress. So GitHub stores the code and Vercel runs it.

---

## 1. Before you start: tools and accounts

### 1.1 Tools on your laptop

You already have most of these from building the app. Open **Terminal** (Mac: press ⌘ Space, type "Terminal"; Windows: use "PowerShell") and check each one:

| Tool | Check with | You should see | If it's missing |
| --- | --- | --- | --- |
| Java 21 | `java -version` | `21.x` | `brew install openjdk@21` (Mac) or install "Temurin 21" from adoptium.net |
| Maven | `mvn -version` | `Apache Maven 3.9…` | `brew install maven` |
| Docker Desktop | `docker --version` | `Docker version …` | Download from docker.com, install, and open it once |
| Git | `git --version` | `git version …` | Mac: run `xcode-select --install`. Windows: git-scm.com |

Docker Desktop must be **open** (whale icon in the menu bar) whenever you run the app or the tests locally.

### 1.2 Accounts to create (all free to start)

Create these in this order. For Vercel, Neon and Render, choose **"Continue with GitHub"** so you have one fewer password.

1. **GitHub**: github.com → Sign up. Use an email you check.
2. **Neon**: neon.com → Sign up → Continue with GitHub.
3. **Vercel**: vercel.com → Sign up → choose **Hobby** (free, personal use) → Continue with GitHub.

### 1.3 Keep a private notes file

You'll collect a handful of values (passwords and addresses) along the way. Keep them in a password manager, or in a text file **outside** the project folder, such as `~/Documents/dsa-tracker-secrets.txt`. Never put them inside the project folder, where Git could pick them up.

---

## 2. Check the app works on your laptop

Deploying a broken app just moves the problem somewhere harder to see. Two minutes here saves an hour later.

1. Unzip the latest `dsa-tracker.zip` into a **fresh, empty folder** (not over an old copy). For example: `~/Projects/dsa-tracker`.
2. In Terminal, go into that folder. `cd` means "change directory":

   ```bash
   cd ~/Projects/dsa-tracker
   ls
   ```

   `ls` lists the files. You should see `pom.xml`, `Dockerfile`, `DEPLOY.md` and a `src` folder. If you don't, you're in the wrong folder.
3. Start the local database, then the app:

   ```bash
   docker compose up -d
   mvn spring-boot:run
   ```

4. Open http://localhost:8080 and sign up. You'll see your recovery code: that's the feature working. Tick the box, continue, mark a problem done, and open its Notes. It works: press **Ctrl C** in Terminal to stop the app.
5. Run all the tests. They take a few minutes; the integration tests start their own throwaway database in Docker:

   ```bash
   mvn test
   ```

   Success ends with **`BUILD SUCCESS`**. If you see `BUILD FAILURE`, scroll up to the first `FAILED` or `ERROR` line and send it to me before going further.

---

## 3. Put the code on GitHub

A **repository** ("repo") is a project folder that GitHub keeps, with its full history. You'll create an empty one on GitHub, then send ("push") your folder to it.

### 3.1 Create the empty repository

1. On github.com, click **+** (top right) → **New repository**.
2. **Repository name:** `dsa-tracker`.
3. **Public** or **Private**: either works. Public is fine because there are no secrets in the code, and it's something to show employers.
4. Leave **"Add a README"**, **".gitignore"** and **"license"** all **unticked**. The project already has these, and an extra file would make the first push fail.
5. Click **Create repository**. Keep the page open; it shows your repo's address, such as `https://github.com/yourname/dsa-tracker.git`.

### 3.2 Let your laptop log in to GitHub

GitHub doesn't accept your account password from Terminal. The simplest fix is GitHub's own command-line tool:

```bash
brew install gh          # Windows: winget install GitHub.cli
gh auth login
```

Answer the questions with: **GitHub.com** → **HTTPS** → **Yes** (authenticate Git) → **Login with a web browser**. It shows a code, opens your browser, and you paste the code there. Afterwards `gh auth status` should say "Logged in".

(Prefer clicking to typing? **GitHub Desktop** from desktop.github.com does all of step 3 with buttons: *File → Add local repository*, then *Publish repository*.)

### 3.3 Push the code

In Terminal, inside the project folder:

```bash
git init                      # turn this folder into a Git repository
git add .                     # stage every file (.gitignore leaves out secrets and build output)
git status                    # look before you commit (see below)
git commit -m "DSA Tracker"   # save a snapshot, with a message
git branch -M main            # name the main line of history "main"
git remote add origin https://github.com/YOURNAME/dsa-tracker.git
git push -u origin main       # send it to GitHub
```

Replace `YOURNAME` with your GitHub username.

**Before `git commit`, read the `git status` list.** It should show `src/…`, `pom.xml`, `Dockerfile` and so on. It must **not** show `.env` (a secrets file) or `target/` (build output); `.gitignore` excludes both. If you see either, stop and ask.

### 3.4 Check it worked

- Refresh your repo page on GitHub: your files and README should be there.
- Click the **Actions** tab. A run called **CI** starts automatically. It builds the app, runs every test and builds the Docker image, which takes about 5 minutes. A **green tick** means all good. A **red cross** means something failed: click it, then the failed step, to see the error.

> **If you ever push a secret by accident:** treat it as leaked. Create a new password or key in that service straight away and delete the old one. Deleting the commit isn't enough, because Git keeps history.

---

## 4. Create the database on Neon

Neon runs PostgreSQL for you. It pauses when nobody is using the site and wakes up in about a second when someone arrives, which is how the free plan stays free.

### 4.1 Create a project

1. neon.com → sign in → **New project** (or the setup screen you see after signing up).
2. **Project name:** `dsa-tracker`.
3. **Postgres version:** the newest offered is fine.
4. **Region:** **AWS Europe (London)**, `eu-west-2`. The app will run in London too, and being close makes every page faster.
5. Click **Create**. Neon creates a database called **`neondb`** and a user (a "role") called **`neondb_owner`**. You'll use both.

### 4.2 Copy the two connection strings

A **connection string** is one line containing everything needed to reach the database: address, database name, username and password.

1. On the project dashboard, click **Connect**.
2. Check the dropdowns show **Branch: main**, **Database: neondb**, **Role: neondb_owner**.
3. Turn **Connection pooling ON** and copy the string. Save it in your notes as **"pooled"**.
4. Turn **Connection pooling OFF** and copy again. Save it as **"direct"**.

They look like this (yours will have different letters):

```
postgresql://neondb_owner:npg_AbC123xyz@ep-cool-river-a1b2c3d4-pooler.eu-west-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require
```

The only difference between the two is `-pooler` in the host name. The app uses the **pooled** one for everyday requests, so several copies of the app can share a few database connections. The **direct** one is used once, at start-up, to create and upgrade the tables, because Neon's docs say schema changes need a direct connection.

### 4.3 Turn them into the app's four database settings

> **Where do these go?** Nowhere yet. In this step you only **write the four values into your private notes file** (from step 1.3). You'll paste them into Vercel's settings page in [step 5.1](#51-import-the-repository), which is how the app receives them.
>
> They do **not** go into any file in the project: not `application.yml`, not `.env.example`, and not a new `.env` file. The app doesn't read `.env` files; online it only reads the values you type into Vercel. That's what keeps your database password out of GitHub. (On your laptop the app doesn't need them at all: it falls back to the local Docker database.)

Java expects the connection in a slightly different format ("JDBC"), with the user and password as separate settings. Take the pooled string apart:

```
postgresql:// neondb_owner : npg_AbC123xyz @ ep-cool-river-a1b2c3d4-pooler.eu-west-2.aws.neon.tech /neondb ?sslmode=require&channel_binding=require
              └── user ──┘   └─ password ─┘   └──────────────────────── host ─────────────────────┘ └ db ┘
```

Then write these four lines into your notes file (with your own values):

| Setting | Value |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://ep-cool-river-a1b2c3d4-pooler.eu-west-2.aws.neon.tech/neondb?sslmode=require` |
| `SPRING_FLYWAY_URL` | the same, **without** `-pooler`: `jdbc:postgresql://ep-cool-river-a1b2c3d4.eu-west-2.aws.neon.tech/neondb?sslmode=require` |
| `DB_USER` | `neondb_owner` |
| `DB_PASSWORD` | `npg_AbC123xyz` |

Rules:
- Start both URLs with `jdbc:postgresql://`.
- **Remove** the `user:password@` part from the URLs; it goes in `DB_USER` and `DB_PASSWORD` instead.
- **Keep** `?sslmode=require`: it encrypts the connection.
- **Drop** `&channel_binding=require`. It's written for other tools, and the plain SSL setting is what the Java driver needs here.

Your notes file should now contain something like this (your values will differ):

```
DB_URL=jdbc:postgresql://ep-cool-river-a1b2c3d4-pooler.eu-west-2.aws.neon.tech/neondb?sslmode=require
SPRING_FLYWAY_URL=jdbc:postgresql://ep-cool-river-a1b2c3d4.eu-west-2.aws.neon.tech/neondb?sslmode=require
DB_USER=neondb_owner
DB_PASSWORD=npg_AbC123xyz
```

You don't create any tables. When the app starts for the first time, Flyway runs the migration files `V1`…`V9` and builds everything, including the 150 NeetCode problems.

**Check:** in Neon, open **Tables** (or **SQL Editor**). It's empty for now; after step 5 you'll see tables such as `users` and `catalog_problems` appear.

---

## 5. Run the app on Vercel

Vercel reads `Dockerfile.vercel` from your repo, builds the app into a container, and runs it in London (`vercel.json` sets the region). It starts copies of the app when people are using it and stops them after 5 quiet minutes.

> **Vercel or Render?** Vercel's free plan is quicker to wake up, but its container support is still marked **beta**. If step 5.2 fails with a message about Dockerfiles not being supported, your account doesn't have the beta yet: use [Render (5B)](#5b-alternative-run-the-app-on-render) instead. Everything else in this guide stays the same.

### 5.1 Import the repository

1. vercel.com → **Add New…** → **Project**.
2. Under **Import Git Repository**, find `dsa-tracker` and click **Import**. If it's not listed, click **Adjust GitHub App Permissions** and allow Vercel to see the repo.
3. **Framework Preset:** **Other**. Leave Build Command, Output Directory and Root Directory empty; the Dockerfile decides everything.
4. Open **Environment Variables**. This is where the four values from your notes (step 4.3) go, plus a few fixed ones. Add each row below: type the **name** on the left, paste the **value** on the right, click **Add**, and repeat for all 8 rows.

| Name | Value |
| --- | --- |
| `DB_URL` | from your notes, step 4.3 (pooled, starts with `jdbc:`) |
| `SPRING_FLYWAY_URL` | from your notes, step 4.3 (direct, no `-pooler`) |
| `DB_USER` | from your notes: `neondb_owner` |
| `DB_PASSWORD` | from your notes: your Neon password |
| `DB_POOL_SIZE` | `3` |
| `DB_MIN_IDLE` | `0` |
| `COOKIE_SECURE` | `true` |
| `PORT` | `8080` |

It looks like this in Vercel, one row per setting:

```
Key                 Value
DB_URL              jdbc:postgresql://ep-cool-river-a1b2c3d4-pooler.eu-west-2.aws.neon.tech/neondb?sslmode=require
SPRING_FLYWAY_URL   jdbc:postgresql://ep-cool-river-a1b2c3d4.eu-west-2.aws.neon.tech/neondb?sslmode=require
DB_USER             neondb_owner
DB_PASSWORD         npg_AbC123xyz
DB_POOL_SIZE        3
DB_MIN_IDLE         0
COOKIE_SECURE       true
PORT                8080
```

Type only the value in the Value box: no quotes, no `=` sign, no spaces before or after.

Why some of these:
- `PORT=8080`: Vercel sends visitors to port 80 unless told otherwise, and the app listens on 8080.
- `COOKIE_SECURE=true`: sign-in cookies are sent only over HTTPS.
- `DB_POOL_SIZE=3` and `DB_MIN_IDLE=0`: keep the database connections small, and let Neon pause when the site is quiet.

Copy-paste mistakes are the most common deploy problem: a missing character, or an invisible space at the end. Paste carefully, and double-check the passwords.

5. Click **Deploy**.

### 5.2 Watch the first build

Vercel shows the build log. The first build takes 5–10 minutes, because it downloads Java and Maven and compiles everything; later builds are faster. Success ends with a screenshot of your site and a **Congratulations** message.

- **Build failed?** Scroll the log up to the first red line; section 9 lists the usual causes.
- Your address is shown on the project page, something like **`https://dsa-tracker-yourname.vercel.app`**.

### 5.3 First visit

Open your address. The first visit after a quiet spell takes a few seconds while Java starts up; that's the "wake-up" wait, and it's normal. Then:

1. Sign up. **Save the recovery code it shows you** (password manager or a note on your phone), tick the box and continue.
2. Look at Neon → **Tables**: `users`, `problems`, `catalog_problems` and the rest now exist. That was Flyway building the database on first start.

### What to know about Vercel's free plan (Hobby)

- **Wake-ups:** after 5 minutes with no visitors, the next visitor waits a few seconds.
- **Limits:** 4 hours of active CPU a month, plenty for a small group. Check **Usage** in the dashboard now and then. If a limit is reached, Vercel pauses the site until next month rather than charging you.
- **Personal, non-commercial use only.** Adding ads, payments, or paid work on it requires the Pro plan (about $20 a month).
- **Preview deployments:** every branch or pull request gets its own test address, so you can try a change before it goes live.

---

## 5B. Alternative: run the app on Render

Use this instead of step 5 if Vercel's container beta isn't available to you, or if you want the app always awake (Render's paid plan).

1. render.com → sign up with GitHub → **New +** → **Web Service** → pick `dsa-tracker`.
2. Render detects the `Dockerfile` and sets **Language: Docker**.
3. **Region:** Frankfurt (Render's closest to the UK). **Instance type:** Free to try, or **Starter** (about $7 a month) to stay awake.
4. **Environment variables:** the same as the table in 5.1, with two differences: **don't add `PORT`** (Render sets it itself), and use `DB_POOL_SIZE=5`.
5. **Advanced → Health Check Path:** `/actuator/health`. Render then only switches visitors to a new version once it answers "UP", so a broken update never replaces a working site.
6. **Create Web Service.** Your address is `https://dsa-tracker-….onrender.com`. Render redeploys by itself whenever you change a variable.

Render's free instance sleeps after 15 idle minutes and wakes more slowly than Vercel (a minute or more). Don't use Render's free Postgres; it's deleted after 30 days, which is why the database is on Neon.

---

## 6. Check everything works

Go through this list on the live site. Each line tests a different part.

- [ ] `https://<your-app>/actuator/health` shows `{"status":"UP"}`. The app is running and can reach the database.
- [ ] Sign up, mark a problem done, sign out, sign back in: the problem is still done. Data is saved in Neon.
- [ ] Open the problem's **Notes**, paste some code, press **Analyse**: time and space appear.
- [ ] Sign out → **Forgot password?** → your username, the recovery code you saved, and a new password: you're signed in again and shown a **new** code (save that one; the old one no longer works).
- [ ] Type a wrong password 11 times in a row: the 11th says "Too many attempts". The rate limits work.
- [ ] Open a private browser window and sign up as someone else: that account sees none of your data.
- [ ] In your normal window, open DevTools (⌥⌘I on Mac, F12 on Windows) → **Application** → **Cookies**: `SESSION` shows **HttpOnly**, **Secure** and **SameSite: Strict**.

All ticked? You're live. Share the address.

---

## 7. Optional extras

### 7.1 Use your own web address (needs a domain)

The free `…vercel.app` address works fine. If you buy a domain later (about £10 a year from Porkbun, Namecheap or Cloudflare), you can use something like `tracker.shivangi-dsa.dev` instead:

1. Vercel → Project → **Settings** → **Domains** → add `tracker.shivangi-dsa.dev`.
2. Vercel shows one DNS record (usually a **CNAME**). Add it in your registrar's DNS settings: same type, name and value.
3. Wait for Vercel to show **Valid Configuration**. It gets the HTTPS certificate for you. Nothing in the app needs to change.

### 7.2 Let Claude do the complexity analysis

"Analyse" already works with the built-in estimate. For sharper answers on unusual code:

1. console.anthropic.com → sign up → **Billing**: add a little credit and set a **monthly spend limit**, so costs can never surprise you.
2. **API Keys** → **Create key** → copy it (starts with `sk-ant-`).
3. Vercel → Environment Variables → add `ANTHROPIC_API_KEY` = the key, then **redeploy**.

Results then say "Analysed by Claude"; if Claude can't be reached, the built-in estimate answers instead. Each user can run 50 analyses a day, which keeps costs bounded. Check current prices on Anthropic's pricing page.

### 7.3 Password reset by email (later, with a domain)

Recovery codes need no email, which is why this guide skips it. If you buy a domain later and want "email me a reset link" as well, ask me: it means adding an email provider (such as Resend, whose free plan covers this) and a few DNS records to your domain.

---

## 8. Everyday tasks after launch

**Releasing a change.** Edit the code, check it on your laptop (step 2), then:

```bash
git add .
git commit -m "Describe what you changed"
git push
```

GitHub runs the tests, and Vercel builds and releases the new version automatically, in about 5 minutes. If the build fails, Vercel keeps the previous version running, so visitors never see a broken site.

**Going back to an older version.** Vercel → **Deployments** → find the last good one → **⋯** → **Promote to Production** (or **Instant Rollback**).

**Seeing what went wrong.** Vercel → Project → **Logs** shows messages from the running app; each deployment's **Build Logs** show build problems.

**Changing a setting or password.** Update the environment variable, then **redeploy**. To change a leaked or old password: create the new one in that service, update the variable, redeploy, then delete the old one.

**Changing the database structure.** Add a new migration file, such as `V10__describe_it.sql`. **Never edit a migration that has already run online**: Flyway checks them, and the app refuses to start if one has changed.

**Backups.** Neon keeps a short history you can restore from. For your own copy, run this now and then (it needs the Postgres tools: `brew install libpq`):

```bash
pg_dump "postgresql://neondb_owner:PASSWORD@DIRECT-HOST/neondb?sslmode=require" > backup-$(date +%F).sql
```

Use the **direct** connection string here, not the pooled one.

**Someone lost their password and their recovery code.** Without the code it can't be reset from the site, by design. If you know it's genuinely them, you can delete their account in Neon's SQL Editor (`DELETE FROM users WHERE username = '…';`) so they can sign up again; their progress goes with it.

**Watching the free limits.** Monthly, glance at Vercel → **Usage** and Neon → **Usage** (0.5 GB storage, 100 compute hours). When one gets tight, that service's paid plan is the fix; no code changes needed.

---

## 9. Troubleshooting

Find the symptom, then check the likely causes in order.

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `git push` asks for a password, or says "Authentication failed" | Terminal isn't logged in to GitHub | Run `gh auth login` (step 3.2), then push again |
| `git push` rejected: "fetch first" or "non-fast-forward" | The GitHub repo was created with a README | Easiest: delete the repo on GitHub, recreate it with nothing ticked (3.1), push again |
| `mvn test`: `ContainerFetch Can't get Docker image` or `Could not find a valid Docker environment` | Docker Desktop isn't open, or the project uses a Testcontainers version too old for Docker Desktop 4.52+ | Open Docker Desktop and wait for "Engine running"; check `pom.xml` sets `testcontainers.version` to `1.21.4`; run `mvn clean test`. Still failing? Open `target/surefire-reports/dev.shivangi.dsatracker.ApiIntegrationTest.txt` and send me the `Caused by:` lines |
| GitHub **Actions** shows a red cross | A test failed, or it didn't compile | Click the run → the red step → read the first error. Run `mvn test` locally to reproduce it |
| Vercel build: "Dockerfile … not supported", or it builds a static site | Container beta not enabled on your account | Use Render (5B) |
| Vercel build fails during `mvn … package` | Compile error in the code | Read the first `[ERROR]` line in the build log; run `mvn package` locally |
| Site shows **404 NOT_FOUND** or never loads, but the build succeeded | `PORT` isn't `8080` | Add `PORT` = `8080`, redeploy |
| Logs: `password authentication failed for user` | Wrong `DB_USER` or `DB_PASSWORD` | Copy them again from Neon (4.2); watch for spaces at the end |
| Logs: `The connection attempt failed` or `UnknownHostException` | Mistake in `DB_URL` | Check it starts with `jdbc:postgresql://`, has no `user:password@`, and ends with `/neondb?sslmode=require` |
| Logs: `SSL` or `ssl off` errors | `?sslmode=require` missing | Add it to both URLs |
| Logs: `Validate failed: Migration checksum mismatch` | A migration file was edited after it ran | Undo the edit; put changes in a new `V10__…sql` file |
| Logs: `Unable to obtain connection` on the first request after hours idle | Neon was waking up | Normal once; reload. If it's constant, check `DB_URL` |
| Every page says "Your page is out of date. Reload it" | Stale security token after an update | Reload the page (⌘R or F5) |
| You sign in, but get sent back to the sign-in page | Secure cookies over plain `http` | `COOKIE_SECURE=true` only works on `https`. Online that's automatic; locally, leave it unset |
| App won't start after you pasted the database settings | A value is in the wrong box, or has quotes or spaces | In Vercel → Settings → Environment Variables, check each **name** is exactly as in 5.1 and each **value** has no quotes, `=` or spaces; redeploy |
| Forgot password: "That username and recovery code don't match" | A typo, or an old code (each code works once) | Check the username; use the newest code you saved. Case, spaces and dashes don't matter |
| Lost your recovery code but still signed in somewhere | | Settings → **Create a new recovery code** (asks for your current password) |
| Lost both the password and the recovery code | | The account can't be recovered from the site; see "Someone lost their password…" in section 8 |
| "Too many attempts. Try again in N minutes." | The rate limit, working as intended | Wait the time shown |
| Analyse: "Notes are frozen" | That problem was solved on an earlier day | Expected: notes only change on the solve day |
| Everything worked, then suddenly stopped | A free-plan limit was reached | Check **Usage** in Vercel and Neon |

Still stuck? Copy the **first** error line from the log, plus what you were doing, and send them to me.

---

## 10. Glossary

| Word | Meaning |
| --- | --- |
| **Repository (repo)** | A project folder tracked by Git, with its full history |
| **Commit** | A saved snapshot of the code, with a message saying what changed |
| **Push** | Send your commits from your laptop to GitHub |
| **CI (continuous integration)** | GitHub automatically building and testing the code on every push (the Actions tab) |
| **Deploy** | Put a new version of the app online |
| **Environment variable** | A named setting (like `DB_PASSWORD`) given to the app when it starts, instead of being written in the code |
| **Connection string** | One line with everything needed to reach a database: host, database, user, password, options |
| **JDBC** | The standard way Java programs talk to databases; its URLs start with `jdbc:` |
| **Connection pooling** | Reusing a few open database connections instead of opening a new one per request |
| **Migration** | A numbered SQL file (`V1__…`) that creates or changes tables; Flyway runs each one once |
| **Container / Docker image** | The app plus everything it needs to run (Java, the jar), packed into one unit that runs the same everywhere |
| **DNS** | The internet's address book, mapping names like `tracker.yourdomain.dev` to servers (only needed for your own domain) |
| **API key** | A secret password for programs, such as the app talking to Claude |
| **Recovery code** | A one-time code, shown at sign-up, that resets a forgotten password without email |
| **HTTPS** | Encrypted web connections: the padlock in the address bar |
| **Health check** | A URL (`/actuator/health`) that says whether the app is working |
| **Cold start / wake-up** | The pause while a sleeping app starts again for the first visitor |
| **Rate limit** | A cap on attempts per time window, such as 10 sign-ins per 5 minutes, to stop abuse |

---

## Sources

Checked in September 2026; limits and prices change, so check each page before relying on them.

- Vercel: [container images](https://vercel.com/docs/functions/container-images), [Dockerfile support](https://vercel.com/blog/dockerfile-on-vercel), [function limits](https://vercel.com/docs/functions/limitations), [Hobby plan terms](https://vercel.com/docs/limits/fair-use-guidelines)
- Neon: [pricing](https://neon.com/pricing), [regions](https://neon.com/docs/introduction/regions), [connection pooling](https://neon.com/docs/connect/connection-pooling), [finding your connection string](https://neon.com/faqs/where-find-database-connection-string), [Java guide](https://neon.com/docs/guides/java)
- Render: [free tier](https://render.com/articles/platforms-with-a-real-free-tier-for-developers-in-2026), [free Postgres expiry](https://bex.co/blog/2026/09/23/render-free-postgres-30-day-expiry)
