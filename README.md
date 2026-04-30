# Affiliate Post Monitor

Monitor public Facebook Pages you care about, detect new Amazon product posts,
rewrite Amazon links to **your** affiliate tag, and prepare complete,
ready-to-copy captions. The Android app shows the queue; you decide when and
where to publish. **No Facebook login, no auto-posting, no captcha bypass,
no private group scraping.** Manual publishing only.

## Android-only mode: no backend required

As of v0.2 the app is **fully self-contained**. There is no PC, no VPS, no
Termux, and no FastAPI server in the normal user flow. Everything runs on the
phone:

| Concern | Implementation |
|---|---|
| Source / queue / log storage | **Room** (on-device SQLite) |
| Settings | **DataStore** |
| Hourly background scans | **WorkManager** (15-min minimum on Android) |
| Page fetch + `amzn.to` redirects | **OkHttp** |
| Public-page HTML parsing | **Jsoup** (Open Graph + post permalinks) |
| Amazon link extraction / ASIN / affiliate URL | Pure **Kotlin** |
| Duplicate detection | **3-way**: source post URL + ASIN + caption SHA-256 hash |
| Image saving | **MediaStore** (`Pictures/AffiliatePostMonitor`) |
| Caption copy / share | **ClipboardManager** + `Intent.ACTION_SEND` |

Onboarding is now **2 steps**: welcome + manual-posting notice → confirm
Amazon Associate tag. There is no Backend URL field anywhere in the app.

The `backend/` folder is preserved as **optional / deprecated reference
tooling** (and for the bundled pytest suite of the link-parsing + dedup
logic) — you do **not** need to run it.

## What it does

1. You add a public Facebook Page URL in the **Sources** tab. The app
   previews the page (name, recent posts) before saving.
2. Once an hour, WorkManager runs a scan of every active source.
3. When a post links to Amazon, the app:
   - resolves `amzn.to` short links via OkHttp,
   - extracts the ASIN from `/dp/…`, `/gp/product/…`, or `/product/…` paths
     (or the `?asin=` query string),
   - rebuilds the URL with **your** Amazon Associate tag on the correct
     marketplace (17 supported: `amazon.com`, `.co.uk`, `.in`, `.ae`, `.ca`,
     `.com.au`, `.de`, `.fr`, `.it`, `.es`, `.co.jp`, `.com.mx`, `.com.br`,
     `.sg`, `.nl`, `.se`, `.pl`),
   - composes the final caption:

     ```
     {original_description}

     #ad
     As an Amazon Associate, I earn from qualifying purchases.

     Buy here: {affiliate_link}
     ```

4. Duplicate detection runs on the source post URL, ASIN, and caption hash —
   nothing gets into the queue twice.
5. The **Queue** tab shows ready posts with one-tap
   Copy Caption / Save Image / Mark as Posted / Reject / Open Source / Open
   Amazon actions.

## Repository layout

```
android/                            Standalone Android app (no backend needed)
  app/src/main/kotlin/.../data/
    local/        Room entities + DAOs + database
    amazon/       AmazonLink + Caption (Kotlin port of backend services)
    facebook/     OkHttp + Jsoup public-page scraper
    scanner/      Scanner orchestration (daily limit, inter-source delay)
    Repository.kt Local-only repository the screens talk to
    Prefs.kt      DataStore-backed settings
  app/src/main/kotlin/.../work/
    ScanWorker.kt WorkManager periodic scan worker
  app/src/main/kotlin/.../ui/       Compose screens (M3, dark, glass cards)

backend/                            DEPRECATED reference tooling (optional)
  app/                              FastAPI + SQLite + APScheduler
  sample_data/                      pages.json fixture used in Test mode
  tests/                            pytest suite (link parsing, dedup, captions)
  requirements.txt
  .env.example
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
./gradlew :app:assembleDebug         # debug APK
./gradlew :app:assembleRelease       # release APK (unsigned)
```

The output APK is at `android/app/build/outputs/apk/debug/app-debug.apk`.

If you don't have `gradle` installed globally, the easiest path is to install
Android Studio once and use its "Build → Build Bundle(s) / APK(s) → Build
APK(s)" menu — it will download Gradle and the wrapper jar automatically.

### First-run setup in the app

The app shows a **2-step** onboarding the first time it launches:

1. **Welcome + manual-posting-only notice** — summarises what the app does
   and what it explicitly does **not** do (no FB login, no auto-posting, no
   captcha bypass).
2. **Confirm Amazon Associate tag** — prefilled with the configured default
   (`laique248-20`); change it to yours if needed.

After onboarding: go to **Sources** and add one or more public Facebook Page
URLs, preview to confirm the page is reachable + public, then save. Ready
posts appear in the **Queue** tab automatically once the next hourly scan
runs (or trigger one manually with **Scan Now** on the Dashboard).

### Default settings

| Setting | Default |
|---|---|
| Amazon Associate tag | `laique248-20` |
| Primary marketplace | `amazon.com` (others auto-detected per post) |
| Scan interval | 60 minutes |
| Daily ready-post limit | 100 |
| Delay between page scans | 5 seconds |

All four are editable from the **Settings** tab. Saving the new interval
re-schedules WorkManager immediately.

## Respectful scraping

- Only pages **you explicitly add** are fetched.
- Short, rate-limited requests with a desktop-style user-agent.
- A per-source delay (default 5s) between fetches.
- A daily import limit (default 100) as a global throttle.
- No automatic page discovery, no recommendations, no background crawling
  of any page that isn't explicitly listed in the Sources tab.

## What this app explicitly does NOT do

- Log in to Facebook.
- Post or comment on Facebook on your behalf.
- Click, scroll, or automate any Facebook UI.
- Scrape private or login-walled content.
- Bypass captchas, rate limits, or Terms of Service.

You are the publisher. The app only prepares the copy-and-paste content.

## Backend (deprecated, optional)

The `backend/` folder is kept for reference and for the bundled pytest suite
of the link-parsing + dedup logic. Running it is **not** required for normal
use of the app. If you still want to:

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
pytest -q
```

## License

MIT — see `LICENSE`.
