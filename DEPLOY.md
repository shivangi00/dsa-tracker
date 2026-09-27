# Putting DSA Tracker online

This takes about an hour the first time. Each service does one job:

| Service | Job | Cost |
| --- | --- | --- |
| **GitHub** | Holds the code (a public repo is fine) and runs the tests on every push | Free |
| **Neon** | The PostgreSQL database | Free tier: 0.5 GB storage, 100 compute-hours a month, sleeps when idle, doesn't expire |
| **Resend** | Sends the password-reset emails | Free tier: 100 emails a day, 3,000 a month |
| **Vercel** (option A) | Runs the app from `Dockerfile.vercel`, on HTTPS, in London | Hobby: free for personal, non-commercial use |
| **Render** (option B) | Runs the app from `Dockerfile`, always on | Free to try; about $7 a month to stay awake |
| A domain name | Needed by Resend to email anyone but you | About £10 a year |

Prices and limits were checked in September 2026 and change often, so check each pricing page before you rely on them.

> **Why not GitHub Pages?** Pages serves fixed files only (HTML, CSS, JS). This app also has a Java server and a database: something has to check passwords, save progress and send emails. So the code lives on GitHub and the server runs on Vercel or Render.

---

## 1. Put the code on GitHub

1. Create a new repository on github.com (for example `dsa-tracker`). It can be public: there are no secrets in the code.
2. In the project folder:

   ```bash
   git init
   git add .
   git commit -m "DSA Tracker"
   git branch -M main
   git remote add origin https://github.com/<you>/dsa-tracker.git
   git push -u origin main
   ```

3. Open the **Actions** tab. The CI workflow (`.github/workflows/ci.yml`) builds the app, runs every test (including the integration tests against a real Postgres in Docker) and builds the Docker image. A green tick means it's safe to deploy.

**Never commit secrets.** `.gitignore` already excludes `.env` files. Real passwords and keys go only into your host's environment settings (step 4). If you push a secret by mistake, treat it as leaked and make a new one straight away: deleting the commit doesn't remove it from the history.

## 2. Create the database (Neon)

1. Sign up at neon.com and create a project in **AWS London (eu-west-2)**, next to Vercel's London region. (Using Render instead? Pick Frankfurt, next to Render's Frankfurt region.)
2. Create a database called `dsatracker`.
3. Click **Connect** and copy two addresses:
   - with **Connection pooling ON**: the host contains `-pooler`. The app uses this one, so several copies of the app can share Neon's connection pool.
   - with **Connection pooling OFF**: the direct host. Migrations use this one, because Neon's docs say schema changes need a direct connection.
4. Turn them into JDBC settings (same user and password for both):

   ```
   DB_URL=jdbc:postgresql://<pooled host>/dsatracker?sslmode=require
   SPRING_FLYWAY_URL=jdbc:postgresql://<direct host>/dsatracker?sslmode=require
   DB_USER=<user>
   DB_PASSWORD=<password>
   ```

You don't create any tables: when the app starts, Flyway runs `V1`…`V7` and builds everything, including the 150 problems.

**Backups:** Neon keeps a short history you can restore from. For your own copy, run `pg_dump "<direct connection string>" > backup.sql` now and then.

## 3. Set up email (Resend)

The "forgot password" email only reaches real inboxes through a real email provider. (Locally, emails go to Mailpit at http://localhost:8025, a fake inbox that only works while `docker compose up -d` is running.)

1. Buy a domain if you don't have one (any registrar).
2. Sign up at resend.com → **Domains** → add your domain and add the DNS records it shows (SPF and DKIM) at your registrar. Wait for **Verified**. This proves you own the domain, so your emails don't land in spam.
3. **API Keys** → create a key with sending access. Copy it; it starts with `re_`.
4. Your mail settings:

   ```
   MAIL_HOST=smtp.resend.com
   MAIL_PORT=587
   MAIL_USERNAME=resend
   MAIL_PASSWORD=re_...your key...
   MAIL_SMTP_AUTH=true
   MAIL_STARTTLS=true
   MAIL_FROM=DSA Tracker <no-reply@yourdomain.com>
   ```

Without a verified domain, Resend only sends test emails to your own address, so password reset won't work for other users.

## 4A. Run the app on Vercel

Vercel finds `Dockerfile.vercel`, builds it, and runs it as an autoscaling function. `vercel.json` puts it in London (`lhr1`).

1. Sign up at vercel.com with GitHub → **Add New → Project** → import `dsa-tracker`.
2. **Environment Variables**: add everything from `.env.example`:

   | Variable | Value |
   | --- | --- |
   | `DB_URL`, `SPRING_FLYWAY_URL`, `DB_USER`, `DB_PASSWORD` | from step 2 |
   | `DB_POOL_SIZE` | `3` (per copy of the app) |
   | `DB_MIN_IDLE` | `0` (lets Neon sleep when the site is quiet) |
   | `MAIL_*` | from step 3 |
   | `APP_BASE_URL` | your address, e.g. `https://dsa-tracker.vercel.app` (no trailing slash) |
   | `COOKIE_SECURE` | `true` |
   | `LOG_RESET_LINKS` | `false` |
   | `PORT` | `8080` (Vercel sends traffic to port 80 unless told otherwise) |

3. **Deploy**. The first build takes a few minutes. Then open the address and sign up.

Every push to `main` redeploys; every pull request gets its own preview address.

**What to expect on Vercel**

- **Container images are in Beta** on Vercel and may need to be enabled for your account. If the Dockerfile isn't picked up, use option B.
- **Wake-up wait.** After 5 minutes without visitors, Vercel stops the app. The next visitor waits while Java starts, typically several seconds. `Dockerfile.vercel` uses JVM settings that start faster to keep this short.
- **Free-plan limits.** Hobby includes 4 hours of active CPU and 360 GB-hours of memory a month. Each wake-up costs a few seconds of CPU. That's plenty for a small group of users; watch **Usage** in the dashboard as it grows. When the limits are reached, Vercel pauses the site rather than billing you.
- **Personal use only on Hobby.** A free tool is fine. Ads, payments or paid work on it need the Pro plan ($20 a month).
- **Several copies are fine.** Sign-ins (Spring Session) and rate-limit counts (the `rate_limits` table) live in Postgres, so it doesn't matter which copy answers a request.

## 4B. Or run it on Render

Render keeps one copy always running, so there are no wake-up waits (on the paid plan).

1. render.com → **New → Web Service** → pick the repo. Render uses `Dockerfile`.
2. Region **Frankfurt**; instance **Free** to try, **Starter** (about $7 a month) for real users.
3. Environment: the same variables as the table above, except `PORT` (Render sets it) and with `DB_POOL_SIZE=5`.
4. **Advanced → Health Check Path**: `/actuator/health`, so a broken deploy never replaces a working one.

The free instance sleeps after 15 minutes idle and wakes slowly (a minute or more on its small CPU). Don't use Render's free Postgres: free databases there expire after 30 days.

## 5. Check it works

- [ ] `https://<your-app>/actuator/health` shows `{"status":"UP"}`
- [ ] Sign up, mark a problem done, sign out and back in: it's still done
- [ ] "Forgot password" with your email: the email arrives and the link opens the reset form on your real address
- [ ] Sign in wrong 11 times in a row: the 11th gets "Too many attempts"
- [ ] DevTools → Application → Cookies: `SESSION` is `HttpOnly`, `Secure` and `SameSite=Strict`
- [ ] A second browser with another account sees none of the first account's data

## 6. Optional: your own address

Vercel: Project → **Settings → Domains**. Render: Service → **Settings → Custom Domains**. Add `tracker.yourdomain.com`, create the DNS record shown, and the host gets the HTTPS certificate for you. Then change `APP_BASE_URL`.

## Keeping it healthy

- **Logs:** Vercel → Project → Logs (or Render → Logs). Failed emails show up as `Couldn't send the reset email`.
- **Secrets:** to rotate one, create the new one, update the environment variable (this redeploys), then delete the old one.
- **Schema changes:** add a new migration (`V8__...sql`). Never edit one that has already run in production; Flyway refuses to start if an applied file changes.
- **Scaling up later:**
  - Raise the Neon plan when the free compute hours or 0.5 GB of storage get tight.
  - Move from Vercel Hobby to Pro, or to an always-on Render instance, when wake-up waits start to annoy people.
  - The app keeps no state in memory between requests, so more copies need no code changes.

## Sources

- Vercel containers: https://vercel.com/docs/functions/container-images, https://vercel.com/blog/dockerfile-on-vercel
- Vercel limits and Hobby terms: https://vercel.com/docs/functions/limitations, https://vercel.com/docs/limits/fair-use-guidelines
- Vercel client IP headers: https://vercel.com/docs/headers/request-headers
- Render free tier: https://render.com/articles/platforms-with-a-real-free-tier-for-developers-in-2026, https://justinmckelvey.com/blog/is-render-free, https://bex.co/blog/2026/09/23/render-free-postgres-30-day-expiry
- Neon: https://neon.com/pricing, https://neon.com/docs/introduction/regions, https://neon.com/docs/connect/connection-pooling
- Resend: https://resend.com/blog/new-free-tier, https://resend.com/docs/knowledge-base/account-quotas-and-limits, https://resend.com/docs/send-with-smtp
