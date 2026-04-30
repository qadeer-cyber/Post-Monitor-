# Affiliate Post Monitor

Monitor public Facebook Pages you care about, detect new Amazon product posts,
rewrite Amazon links to **your** affiliate tag, and prepare complete,
ready-to-copy captions. The Android app shows the queue; you decide when and
where to publish. **No Facebook login, no auto-posting, no captcha bypass,
no private group scraping.** Manual publishing only.

```
+-------------------+       +---------------------+       +-----------------+
|  FastAPI backend  |  <--  |  Android (Kotlin /  |  -->  |   You copy +    |
|  (scraper + DB)   |       |  Compose / M3)      |       |   paste on FB   |
+-------------------+       +---------------------+       +-----------------+
```

## What it does

1. You add a public Facebook Page URL in the **Sources** tab.
2. The backend periodically fetches each page's public HTML (Playwright
   fallback is available but off by default).
3. When a post links to Amazon, the backend:
   - resolves `amzn.to` short links,
   - extracts the ASIN from `/dp/…`, `/gp/product/…`, or `/product/…` paths,
   - rebuilds the URL with **your** `AMAZON_ASSOCIATE_TAG` on the correct
     marketplace (`amazon.com`, `amazon.co.uk`, `amazon.in`, `amazon.ae`, and
     many others),
   - composes the final caption:

     ```
     {original_description}

     #ad
     As an Amazon Associate, I earn from qualifying purchases.

     Buy here: {affiliate_link}
     ```

4. Duplicate detection runs on the source post URL, ASIN, caption hash, and a
   perceptual image hash — nothing gets into the queue twice.
5. The **Queue** tab in the app shows ready posts with one-tap
   Copy Caption / Save Image / Mark as Posted / Reject / Open Source / Open
   Amazon actions.

## Repository layout

```
backend/           FastAPI + SQLite + APScheduler
  app/             routers, services (facebook, amazon, scanner, dedup, …)
  sample_data/     pages.json fixture used in Test mode
  tests/           pytest suite (link parsing, dedup, captions, API E2E)
  requirements.txt
  .env.example

android/           Kotlin + Jetpack Compose app
  app/src/main/    MainActivity, nav, screens, theme
  gradle/          version catalog, wrapper config
  settings.gradle.kts
  build.gradle.kts

.env.example       root template for copy-to-backend/.env
```

## Backend setup

Needs Python 3.10+.

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env          # then set AMAZON_ASSOCIATE_TAG
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Visit <http://localhost:8000/docs> for the Swagger UI.

### Environment variables

| Key | Default | Notes |
|---|---|---|
| `AMAZON_ASSOCIATE_TAG` | `your-tag-20` | **Required for real use.** |
| `DATABASE_URL` | `sqlite:///./data/app.db` | Any SQLAlchemy URL. |
| `HOST` / `PORT` | `0.0.0.0` / `8000` | Uvicorn bind. |
| `SCAN_INTERVAL_MINUTES` | `30` | Background scheduler tick. |
| `DAILY_IMPORT_LIMIT` | `50` | Cap per 24h to stay polite. |
| `DELAY_BETWEEN_PAGE_SCANS_SECONDS` | `5` | Rate limiting between sources. |
| `TEST_MODE` | `false` | Use `sample_data/pages.json`, no outbound calls. |
| `ENABLE_PLAYWRIGHT_FALLBACK` | `false` | Retry with headless Chromium if HTML parse fails. |
| `USER_AGENT` | bot UA | Sent with every request. |
| `CORS_ORIGINS` | `*` | Comma-separated allow-list. |

### Playwright fallback (optional)

```bash
pip install playwright
python -m playwright install chromium
# then set ENABLE_PLAYWRIGHT_FALLBACK=true
```

### Tests

```bash
cd backend
source .venv/bin/activate
pip install pytest
pytest -q
```

## API

All endpoints are prefixed `/api`:

```
GET     /api/health
GET     /api/sources
POST    /api/sources                     body: {"url": "..."}
PATCH   /api/sources/{id}                body: {"name": "...", "enabled": bool}
DELETE  /api/sources/{id}
POST    /api/scan
POST    /api/sources/{id}/scan
GET     /api/dashboard
GET     /api/queue
GET     /api/posted
GET     /api/posts/{id}
POST    /api/posts/{id}/mark-posted
POST    /api/posts/{id}/reject
GET     /api/logs?category=scan|import|link|error&level=info|warn|error
GET     /api/settings
PATCH   /api/settings
```

## Android app

Jetpack Compose + Material 3, dark-mode by design, glassmorphism cards with
neon blue / green accents. Six bottom-nav destinations plus a Post Detail
screen.

### Requirements

- Android Studio **Iguana (2023.2)** or newer (AGP 8.5.x / Kotlin 2.0.x).
- JDK 17 (Android Studio bundles one).
- Android SDK 34.

### Build

Open `android/` in Android Studio and let the IDE sync. Or from the CLI:

```bash
cd android
# First time only — generates gradle/wrapper/gradle-wrapper.jar
gradle wrapper --gradle-version 8.9

./gradlew :app:assembleDebug         # debug APK
./gradlew :app:assembleRelease       # release APK (unsigned)
```

The output APK is at `android/app/build/outputs/apk/debug/app-debug.apk`.

If you don't have `gradle` installed globally, the easiest path is to install
Android Studio once and use its "Build → Build Bundle(s) / APK(s) → Build
APK(s)" menu — it will download Gradle and the wrapper jar automatically.

### First-run setup in the app

1. Open **Settings** and set **Backend URL**:
   - `http://10.0.2.2:8000/` if you're on the Android emulator, OR
   - `http://<your-machine-ip>:8000/` on a physical device (same Wi-Fi).
2. Set your **Amazon Associate tag** (e.g. `yourtag-20`) and press *Save*.
3. Go to **Sources** and add one or more public Facebook Page URLs.
4. Go to **Dashboard** and press **Scan Now**. Ready posts show up in **Queue**.

### Test mode

Toggle **Test mode** in Settings to make the backend scan bundled sample
pages instead of making any real HTTP calls — great for a first run and for
demos.

## Respectful scraping

- Only pages **you explicitly add** are fetched.
- Short, rate-limited requests with a configurable user-agent.
- A per-source delay (default 5s) between fetches.
- A daily import limit (default 50) as a global throttle.
- Playwright fallback is opt-in and only used when plain HTML parsing finds
  nothing.

## What this app explicitly does NOT do

- Log in to Facebook.
- Post or comment on Facebook on your behalf.
- Click, scroll, or automate any Facebook UI.
- Scrape private or login-walled content.
- Bypass captchas, rate limits, or Terms of Service.

You are the publisher. The app only prepares the copy-and-paste content.

## License

MIT — see `LICENSE`.
