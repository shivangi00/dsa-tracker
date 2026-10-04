# Putting DSA Tracker online: a step-by-step guide for beginners

This guide takes you from "the app runs on my laptop" to "anyone can use it at a web address". Every step says what to click, what to type, and how to check it worked. Nothing here needs prior experience with hosting.

**Time:** about 1½ hours the first time.
**Cost:** nothing to start. Every service here has a free plan, and you don't need to buy a domain: your site gets a free `…onrender.com` address. Render's always-awake plan is about $7 a month, if you want it later.

> Screens change: the websites below redesign their dashboards now and then. If a button has a slightly different name, look for the closest match. The *order* of the steps won't change.

---

## Contents

0. [The big picture](#0-the-big-picture-read-this-first)
1. [Before you start: tools and accounts](#1-before-you-start-tools-and-accounts)
2. [Check the app works on your laptop](#2-check-the-app-works-on-your-laptop)
3. [Put the code on GitHub](#3-put-the-code-on-github)
4. [Create the database on Neon](#4-create-the-database-on-neon)
5. [Run the app on Render](#5-run-the-app-on-render) (Vercel: [5B](#5b-alternative-vercel-didnt-work-for-this-app))
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
| `mvn spring-boot:run` | **Render** | Runs the Java app and gives it an `https://` address |
| Postgres in Docker | **Neon** | Stores every account, problem, note and review |

How they connect:

```
 you edit code ──push──▶ GitHub ──tests pass──▶ Render builds and runs the app
                                                     │            │
                               visitors' browsers ◀──┘            └──▶ Neon (database)
```

**How the app finds the others: environment variables.** The code contains no passwords or addresses. When it starts, it reads settings such as `DB_URL` and `DB_PASSWORD` from its surroundings. On your laptop they fall back to the local defaults; online, you type them into Render's settings page. The file `.env.example` lists every one. This is why the code can be public on GitHub while your passwords stay private.

**No email needed.** Forgotten passwords are handled with a **recovery code** that each person gets when they sign up (and can make again in Settings). So there's no email service to set up and no domain to buy.

**Why not GitHub Pages?** GitHub Pages can only show fixed files (HTML, CSS, pictures). This app also needs a program running on a server to check passwords and save your progress. So GitHub stores the code and Render runs it.

---

## 1. Before you start: tools and accounts

### 1.1 Tools on your laptop

You already have most of these from building the app. Open **Terminal** (Mac: press ⌘ Space, type "Terminal"; Windows: use "PowerShell") and check each one:

| Tool | Check with | You should see | If it's missing |
| --- | --- | --- | --- |
| Java 25 | `java -version` | `25.x` | `brew install openjdk@25` (Mac) or install "Temurin 25" from adoptium.net |
| Maven | `mvn -version` | `Apache Maven 3.9…` | `brew install maven` |
| Docker Desktop | `docker --version` | `Docker version …` | Download from docker.com, install, and open it once |
| Git | `git --version` | `git version …` | Mac: run `xcode-select --install`. Windows: git-scm.com |

Docker Desktop must be **open** (whale icon in the menu bar) whenever you run the app or the tests locally.

### 1.2 Accounts to create (all free to start)

Create these in this order. For Neon and Render, choose **"Continue with GitHub"** so you have one fewer password.

1. **GitHub**: github.com → Sign up. Use an email you check.
2. **Neon**: neon.com → Sign up → Continue with GitHub.
3. **Render**: render.com → Get Started → sign up with GitHub.

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

**Actions shows "Get started with GitHub Actions" instead of a CI run?** Then GitHub can't find the file that switches testing on: `.github/workflows/ci.yml`. Because its name starts with a dot it's *hidden*, so it's easy to leave behind. macOS Finder doesn't show it, and copying the folder in Finder or uploading through GitHub's website can skip it. Check and fix it like this:

1. On your repo's GitHub page, look at the top of the file list. Is there a **`.github`** folder? If yes, open **Actions** again and refresh; the run may just have been slow to appear.
2. If not, check your laptop. In Terminal, inside the project folder:

   ```bash
   ls -a .github/workflows
   ```

   - It prints `ci.yml`: the file is there but wasn't pushed. Go to step 3.
   - It says "No such file or directory": this folder is an old or incomplete copy. Unzip the latest `dsa-tracker.zip` into a fresh folder (which includes the hidden files) and work from that one. To see hidden files in Finder, press **⌘ Shift .** (full stop).
3. Send it to GitHub:

   ```bash
   git add .github
   git commit -m "Add the CI workflow"
   git push
   ```

4. If `git push` is refused with a message about the **`workflow` scope**, GitHub needs extra permission before your laptop can upload workflow files. Grant it, then push again:

   ```bash
   gh auth refresh -h github.com -s workflow
   git push
   ```

5. Refresh the **Actions** tab: a run called **CI** appears within a minute.

> **If you ever push a secret by accident:** treat it as leaked. Create a new password or key in that service straight away and delete the old one. Deleting the commit isn't enough, because Git keeps history.

---

## 4. Create the database on Neon

Neon runs PostgreSQL for you. It pauses when nobody is using the site and wakes up in about a second when someone arrives, which is how the free plan stays free.

### 4.1 Create a project

1. neon.com → sign in → **New project** (or the setup screen you see after signing up).
2. **Project name:** `dsa-tracker`.
3. **Postgres version:** the newest offered is fine.
4. **Region:** **AWS Europe (London)**, `eu-west-2`. It's close to the app's Render region (Frankfurt) and to you.
5. Click **Create**. Neon creates a database called **`neondb`** and a user (a "role") called **`neondb_owner`**. You'll use both.

### 4.2 Copy the connection string

A **connection string** is one line containing everything needed to reach the database: address, database name, username and password.

1. On the project dashboard, click **Connect**.
2. Check the dropdowns show **Branch: main**, **Database: neondb**, **Role: neondb_owner**.
3. Make sure **Connection pooling** is **OFF** (the address must *not* contain `-pooler`), then copy the string into your notes.

It looks like this (yours will have different letters):

```
postgresql://neondb_owner:npg_AbC123xyz@ep-cool-river-a1b2c3d4.eu-west-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require
```

This is the **direct** connection. Render runs one copy of the app, so it doesn't need Neon's connection pooler, and the direct address works for everything, including creating the tables.

### 4.3 Turn it into the app's three database settings

> **Where do these go?** Nowhere yet. In this step you only **write the three values into your private notes file** (from step 1.3). You'll paste them into Render's settings page in [step 5.1](#51-create-the-web-service), which is how the app receives them.
>
> They do **not** go into any file in the project: not `application.yml`, not `.env.example`, and not a new `.env` file. The app doesn't read `.env` files; online it only reads the values you type into Render. That's what keeps your database password out of GitHub. (On your laptop the app doesn't need them at all: it falls back to the local Docker database.)

Java expects the connection in a slightly different format ("JDBC"), with the user and password as separate settings. Take the string apart:

```
postgresql:// neondb_owner : npg_AbC123xyz @ ep-cool-river-a1b2c3d4.eu-west-2.aws.neon.tech /neondb ?sslmode=require&channel_binding=require
              └── user ──┘   └─ password ─┘   └───────────────────── host ───────────────────┘ └ db ┘
```

Then write these three lines into your notes file (with your own values):

| Setting | Value |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://ep-cool-river-a1b2c3d4.eu-west-2.aws.neon.tech/neondb?sslmode=require` |
| `DB_USER` | `neondb_owner` |
| `DB_PASSWORD` | `npg_AbC123xyz` |

Rules for `DB_URL`:
- Start it with `jdbc:postgresql://`, with nothing before it (no space, no quotes, no `DB_URL=`).
- **Remove** the `user:password@` part; it goes in `DB_USER` and `DB_PASSWORD` instead.
- **Keep** `?sslmode=require`: it encrypts the connection.
- **Drop** `&channel_binding=require`. It's written for other tools, and the plain SSL setting is what the Java driver needs here.

Your notes file should now contain something like this (your values will differ):

```
DB_URL=jdbc:postgresql://ep-cool-river-a1b2c3d4.eu-west-2.aws.neon.tech/neondb?sslmode=require
DB_USER=neondb_owner
DB_PASSWORD=npg_AbC123xyz
```

(The `NAME=value` form is just for your notes. In Render, the name and the value go in separate boxes.)

You don't create any tables. When the app starts for the first time, Flyway runs the migration files `V1`…`V9` and builds everything, including the 150 NeetCode problems.

**Check:** in Neon, open **Tables** (or **SQL Editor**). It's empty for now; after step 5 you'll see tables such as `users` and `catalog_problems` appear.

---

## 5. Run the app on Render

Render builds your repo's `Dockerfile` into a container and keeps **one copy of the app running**, so Spring Boot can take as long as it needs to start. (On Vercel, which starts a fresh copy for visitors and only waits a few seconds, this Java app couldn't start in time: see [5B](#5b-alternative-vercel-didnt-work-for-this-app).)

**Free or paid?** Start with **Free**. It sleeps after 15 minutes with no visitors, and the next visit waits about a minute while it wakes. When people rely on it, switch to **Starter** (about $7 a month): it never sleeps. You can change plan any time, with no other changes.

### 5.1 Create the web service

1. Go to **render.com** → **Get Started** → sign up with **GitHub**.
2. In the dashboard: **Add new** (or **New +**) → **Web Service**.
3. **Connect a repository:** pick `dsa-tracker`. If it isn't listed, click **Configure account** / **Connect GitHub** and allow Render to see it.
4. Fill in the form:

| Field | What to choose |
| --- | --- |
| **Name** | `dsa-tracker` (your address becomes `https://dsa-tracker-….onrender.com`) |
| **Language** / **Runtime** | **Docker**. Render picks this by itself because it finds the `Dockerfile` |
| **Branch** | `main` |
| **Region** | **Frankfurt (EU Central)**, the closest to the UK and to your Neon database in London |
| **Root Directory** | leave empty |
| **Instance Type** | **Free** (or **Starter** to stay awake) |

5. **Environment Variables:** click **Add Environment Variable** for each row. This is where the three values from your notes (step 4.3) go:

| Name | Value |
| --- | --- |
| `DB_URL` | from your notes, step 4.3 (starts with `jdbc:postgresql://`, no `-pooler`) |
| `DB_USER` | from your notes: `neondb_owner` |
| `DB_PASSWORD` | from your notes: your Neon password |
| `DB_POOL_SIZE` | `5` |
| `DB_MIN_IDLE` | `0` |
| `COOKIE_SECURE` | `true` |
| `WEB_THREADS` | `50` |

Type only the value in the Value box: no quotes, no `=` sign, no spaces before or after. **Don't add `PORT`**: Render sets it by itself, and the app reads it.

Why some of these:
- `COOKIE_SECURE=true`: sign-in cookies are sent only over HTTPS (Render gives you HTTPS automatically).
- `DB_POOL_SIZE=5`, `DB_MIN_IDLE=0`: a few database connections, closed when idle, so Neon can pause when the site is quiet.
- `WEB_THREADS=50`: at most 50 requests handled at once. Plenty for this app, and it keeps memory use low on the free plan's 512 MB.

6. Open **Advanced** and set **Health Check Path** to `/actuator/health`. Render then only switches visitors to a new version once it answers "UP", so a broken update never replaces a working site.
7. Leave **Auto-Deploy** on ("On Commit"): every push to `main` redeploys.
8. Click **Deploy Web Service**.

### 5.2 Watch the first deploy

Render shows the log live. First it builds (downloading Java and Maven, then compiling: 5–10 minutes the first time). Then it starts the app. Success looks like this near the end of the log:

```
Started DsaTrackerApplication in 45.3 seconds
==> Your service is live 🎉
```

On the free plan, starting can take a minute or two, because the free instance has only a small share of a CPU. That's normal.

- **Build failed?** Scroll up to the first line with `ERROR`; section 9 lists the usual causes.
- **"Out of memory" or "Exited with status 137"?** The free instance ran out of memory: check `WEB_THREADS` is `50`, or switch to Starter.

### 5.3 First visit

Open the address at the top of the Render page (`https://dsa-tracker-….onrender.com`). If the service was asleep, the first visit waits about a minute. Then:

1. Sign up. **Save the recovery code it shows you** (password manager or a note on your phone), tick the box and continue.
2. Look at Neon → **Tables**: `users`, `problems`, `catalog_problems` and the rest now exist. That was Flyway building the database on first start.

Don't use Render's own free Postgres: free databases there are deleted after 30 days, which is why the database is on Neon.

---

## 5B. Alternative: Vercel (didn't work for this app)

**Tried and failed:** on Vercel the build succeeded, but every page returned a 500 error. Vercel starts a fresh copy of the app for visitors and gives it only a few seconds to be ready. Spring Boot needs longer on Vercel's single CPU, even with lazy initialisation, so Vercel stopped each copy part-way through starting (the logs showed the Spring logo, then nothing). The steps are kept here in case Vercel's limits change; `Dockerfile.vercel` and `vercel.json` stay in the repo for that. If you created a Vercel project, you can delete it: Project → **Settings** → **Advanced** → **Delete Project**.

### 5B.1 Import the repository

1. vercel.com → **Add New…** → **Project**.
2. Under **Import Git Repository**, find `dsa-tracker` and click **Import**. If it's not listed, click **Adjust GitHub App Permissions** and allow Vercel to see the repo.
3. **Application Preset:** it should already say **Container**. Vercel picks this by itself when it finds `Dockerfile.vercel` in your repository. Leave it, and leave **Root Directory** as `./` and any build or output settings untouched: the Dockerfile decides everything.
   - **It says something else** (such as "Other", or a Java or Maven option)? Vercel hasn't found or can't use the Dockerfile. First check your repo on GitHub: `Dockerfile.vercel` must be in the top-level file list, next to `pom.xml`. If it's missing, add it (`git add Dockerfile.vercel`, commit, push) and start the import again.
   - **It's there, but Container still isn't offered?** Container support is in beta and may not be switched on for your account yet. Use Render ([section 5](#5-run-the-app-on-render)) instead.
4. Open **Environment Variables**. This is where the four values from your notes (step 4.3) go, plus a few fixed ones. Add each row below: type the **name** on the left, paste the **value** on the right, click **Add**, and repeat for all 9 rows.

| Name | Value |
| --- | --- |
| `DB_URL` | from your notes, step 4.3 |
| `DB_USER` | from your notes: `neondb_owner` |
| `DB_PASSWORD` | from your notes: your Neon password |
| `DB_POOL_SIZE` | `3` |
| `DB_MIN_IDLE` | `0` |
| `COOKIE_SECURE` | `true` |
| `PORT` | `8080` |
| `SPRING_MAIN_LAZYINITIALIZATION` | `true` |

It looks like this in Vercel, one row per setting:

```
Key                 Value
DB_URL              jdbc:postgresql://ep-cool-river-a1b2c3d4.eu-west-2.aws.neon.tech/neondb?sslmode=require
DB_USER             neondb_owner
DB_PASSWORD         npg_AbC123xyz
DB_POOL_SIZE        3
DB_MIN_IDLE         0
COOKIE_SECURE       true
PORT                8080
SPRING_MAIN_LAZYINITIALIZATION   true
```

Type only the value in the Value box: no quotes, no `=` sign, no spaces before or after.

Why some of these:
- `PORT=8080`: Vercel sends visitors to port 80 unless told otherwise, and the app listens on 8080.
- `COOKIE_SECURE=true`: sign-in cookies are sent only over HTTPS.
- `DB_POOL_SIZE=3` and `DB_MIN_IDLE=0`: keep the database connections small, and let Neon pause when the site is quiet.
- `SPRING_MAIN_LAZYINITIALIZATION=true`: Vercel only waits a short time for a freshly started copy of the app to be ready. This makes Spring start the web server first and set up the rest on the first request, so it's ready in time. The first page after a quiet spell is a little slower instead.

Copy-paste mistakes are the most common deploy problem: a missing character, or an invisible space at the end. Paste carefully, and double-check the passwords.

5. Click **Deploy**.

### 5B.2 Watch the first build

Vercel shows the build log. The first build takes 5–10 minutes, because it downloads Java and Maven and compiles everything; later builds are faster. Success ends with a screenshot of your site and a **Congratulations** message.

- **Build failed?** Scroll the log up to the first red line; section 9 lists the usual causes.
- Your address is shown on the project page, something like **`https://dsa-tracker-yourname.vercel.app`**.

### 5B.3 First visit

Open your address. The first visit after a quiet spell takes a few seconds while Java starts up; that's the "wake-up" wait, and it's normal. Then:

1. Sign up. **Save the recovery code it shows you** (password manager or a note on your phone), tick the box and continue.
2. Look at Neon → **Tables**: `users`, `problems`, `catalog_problems` and the rest now exist. That was Flyway building the database on first start.

### What to know about Vercel's free plan (Hobby)

- **Wake-ups:** after 5 minutes with no visitors, the next visitor waits a few seconds.
- **Limits:** 4 hours of active CPU a month, plenty for a small group. Check **Usage** in the dashboard now and then. If a limit is reached, Vercel pauses the site until next month rather than charging you.
- **Personal, non-commercial use only.** Adding ads, payments, or paid work on it requires the Pro plan (about $20 a month).
- **Preview deployments:** every branch or pull request gets its own test address, so you can try a change before it goes live.

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

The free `…onrender.com` address works fine. If you buy a domain later (about £10 a year from Porkbun, Namecheap or Cloudflare), you can use something like `tracker.shivangi-dsa.dev` instead:

1. Render → your service → **Settings** → **Custom Domains** → **Add Custom Domain** → `tracker.shivangi-dsa.dev`.
2. Render shows one DNS record (usually a **CNAME**). Add it in your registrar's DNS settings: same type, name and value.
3. Wait for Render to show the domain as **Verified**. It gets the HTTPS certificate for you. Nothing in the app needs to change.

### 7.2 Let Claude do the complexity analysis

"Analyse" already works with the built-in estimate. For sharper answers on unusual code:

1. console.anthropic.com → sign up → **Billing**: add a little credit and set a **monthly spend limit**, so costs can never surprise you.
2. **API Keys** → **Create key** → copy it (starts with `sk-ant-`).
3. Render → your service → **Environment** → add `ANTHROPIC_API_KEY` = the key → **Save Changes** (Render redeploys by itself).

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

GitHub runs the tests, and Render builds and releases the new version automatically, in about 5–10 minutes. If the build fails or the health check never says "UP", Render keeps the previous version running, so visitors never see a broken site.

**Going back to an older version.** Render → your service → **Events** → find the last good deploy → **Rollback**.

**Deploying again without a code change** (after changing a setting, say): Render → **Manual Deploy** → **Deploy latest commit**.

**Seeing what went wrong.** Render → your service → **Logs** shows the build and the running app's messages, live.

**Changing a setting or password.** Update the environment variable in Render → **Environment** → **Save Changes**; Render redeploys by itself. To change a leaked or old password: create the new one in that service, update the variable, redeploy, then delete the old one.

**Changing the database structure.** Add a new migration file, such as `V10__describe_it.sql`. **Never edit a migration that has already run online**: Flyway checks them, and the app refuses to start if one has changed.

**Backups.** Neon keeps a short history you can restore from. For your own copy, run this now and then (it needs the Postgres tools: `brew install libpq`):

```bash
pg_dump "postgresql://neondb_owner:PASSWORD@DIRECT-HOST/neondb?sslmode=require" > backup-$(date +%F).sql
```

Use the connection string from step 4.2 (without `-pooler`).

**Someone lost their password and their recovery code.** Without the code it can't be reset from the site, by design. If you know it's genuinely them, you can delete their account in Neon's SQL Editor (`DELETE FROM users WHERE username = '…';`) so they can sign up again; their progress goes with it.

**Watching the free limits.** Monthly, glance at Render → **Billing** (the free plan includes 750 instance hours a month, enough for one service all month) and Neon → **Usage** (0.5 GB storage, 100 compute hours). When one gets tight, that service's paid plan is the fix; no code changes needed.

---

## 9. Troubleshooting

Find the symptom, then check the likely causes in order.

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `git push` asks for a password, or says "Authentication failed" | Terminal isn't logged in to GitHub | Run `gh auth login` (step 3.2), then push again |
| `git push` rejected: "fetch first" or "non-fast-forward" | The GitHub repo was created with a README | Easiest: delete the repo on GitHub, recreate it with nothing ticked (3.1), push again |
| `mvn test`: `ContainerFetch Can't get Docker image` or `Could not find a valid Docker environment` | Docker Desktop isn't open, or the project uses a Testcontainers version too old for Docker Desktop 4.52+ | Open Docker Desktop and wait for "Engine running"; check `pom.xml` sets `testcontainers.version` to `1.21.4`; run `mvn clean test`. Still failing? Open `target/surefire-reports/dev.shivangi.dsatracker.ApiIntegrationTest.txt` and send me the `Caused by:` lines |
| GitHub **Actions** shows "Get started with GitHub Actions" | The hidden `.github/workflows/ci.yml` file never reached GitHub | Follow the steps under 3.4: check with `ls -a .github/workflows`, then `git add .github`, commit, push |
| `git push` refused: "…without `workflow` scope" | Your GitHub login can't upload workflow files yet | `gh auth refresh -h github.com -s workflow`, then push again |
| GitHub **Actions** shows a red cross | A test failed, or it didn't compile | Click the run → the red step → read the first error. Run `mvn test` locally to reproduce it |
| Render doesn't offer **Docker** as the language | `Dockerfile` isn't in the top level of the repo | Check it's in the top-level file list on GitHub, next to `pom.xml`; push it if not |
| Build fails during `mvn … package` | Compile error in the code | Read the first `[ERROR]` line in the build log; run `mvn package` locally |
| Render log: "Exited with status 137" or "out of memory" | The free instance's 512 MB ran out | Check `WEB_THREADS` = `50`; if it keeps happening, switch to the Starter plan |
| Render log: "No open ports detected" or the deploy times out | The app didn't start, so it never opened its port | Scroll up the log for the first `ERROR` or `APPLICATION FAILED TO START`: usually a database setting (rows below) |
| First visit takes about a minute | The free instance was asleep | Normal on the free plan; Starter never sleeps |
| **On Vercel:** build succeeded, but every page is a **500** error, and the logs stop part-way through start-up (the Spring logo, "Tomcat initialized") without `Started DsaTrackerApplication` | Vercel stopped the app before it finished starting | Use Render (section 5). This app starts too slowly for Vercel |
| **On Vercel:** site shows **404 NOT_FOUND** | `PORT` isn't `8080` | Add `PORT` = `8080`, redeploy |
| Logs: `'url' must start with "jdbc"` | `DB_URL` isn't in JDBC form | In Render → **Environment**, set `DB_URL` to exactly `jdbc:postgresql://<host>/neondb?sslmode=require`: no `postgresql://user:password@`, no `DB_URL=`, no spaces or quotes (4.3) |
| Logs: `SCRAM-based authentication, but no password was provided` | A `SPRING_FLYWAY_URL` setting is left over from an older version of this guide | Delete `SPRING_FLYWAY_URL` in Render → **Environment** (`DB_URL` alone is enough), or, if you keep it, also add `SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD` with the same values as `DB_USER` and `DB_PASSWORD` |
| Logs: `password authentication failed for user` | Wrong `DB_USER` or `DB_PASSWORD` | Copy them again from Neon (4.2); watch for spaces at the end |
| Logs: `The connection attempt failed` or `UnknownHostException` | Mistake in `DB_URL` | Check it starts with `jdbc:postgresql://`, has no `user:password@`, and ends with `/neondb?sslmode=require` |
| Logs: `SSL` or `ssl off` errors | `?sslmode=require` missing | Add it to both URLs |
| Logs: `Validate failed: Migration checksum mismatch` | A migration file was edited after it ran | Undo the edit; put changes in a new `V10__…sql` file |
| Logs: `Unable to obtain connection` on the first request after hours idle | Neon was waking up | Normal once; reload. If it's constant, check `DB_URL` |
| Every page says "Your page is out of date. Reload it" | Stale security token after an update | Reload the page (⌘R or F5) |
| You sign in, but get sent back to the sign-in page | Secure cookies over plain `http` | `COOKIE_SECURE=true` only works on `https`. Online that's automatic; locally, leave it unset |
| App won't start after you pasted the database settings | A value is in the wrong box, or has quotes or spaces | In Render → **Environment**, check each **name** is exactly as in 5.1 and each **value** has no quotes, `=` or spaces; save |
| Forgot password: "That username and recovery code don't match" | A typo, or an old code (each code works once) | Check the username; use the newest code you saved. Case, spaces and dashes don't matter |
| Lost your recovery code but still signed in somewhere | | Settings → **Create a new recovery code** (asks for your current password) |
| Lost both the password and the recovery code | | The account can't be recovered from the site; see "Someone lost their password…" in section 8 |
| "Too many attempts. Try again in N minutes." | The rate limit, working as intended | Wait the time shown |
| Analyse: "Notes are frozen" | That problem was solved on an earlier day | Expected: notes only change on the solve day |
| Everything worked, then suddenly stopped | A free-plan limit was reached | Check Render → **Billing** and Neon → **Usage** |

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
| **Connection pool** | A few open database connections the app reuses, instead of opening a new one per request |
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
