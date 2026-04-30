# Affiliate Post Monitor – Backend

FastAPI + SQLite backend. Watches public Facebook Pages, extracts Amazon links
from new posts, rewrites them to your associate tag, and prepares ready-to-copy
captions. Manual posting only — no Facebook login, no auto-posting.

## Quickstart

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env    # then edit AMAZON_ASSOCIATE_TAG etc.
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

API docs at `http://localhost:8000/docs`.

### Test mode (no network)

Set `TEST_MODE=true` in `.env` (or toggle "Test mode" in the app's Settings
screen). Scans will load fixtures from `sample_data/pages.json` instead of
hitting Facebook. This is the safest way to try the full pipeline locally.

### Playwright fallback

For pages where plain HTML parsing misses content, enable the browser
fallback:

```bash
pip install playwright
python -m playwright install chromium
# then set ENABLE_PLAYWRIGHT_FALLBACK=true in .env
```

The fallback is skipped unless the basic HTML parse finds zero posts.

## Endpoints

All endpoints are under `/api`:

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Version + feature flags |
| POST | `/api/sources` | Add a public FB page URL |
| GET | `/api/sources` | List watched pages |
| PATCH | `/api/sources/{id}` | Rename / enable / disable |
| DELETE | `/api/sources/{id}` | Remove a page |
| POST | `/api/scan` | Scan all enabled sources now |
| POST | `/api/sources/{id}/scan` | Scan a single source now |
| GET | `/api/dashboard` | Totals for the Dashboard screen |
| GET | `/api/queue` | Ready posts (status=queue) |
| GET | `/api/posted` | Already-posted archive |
| GET | `/api/posts/{id}` | Post detail |
| POST | `/api/posts/{id}/mark-posted` | Mark as posted |
| POST | `/api/posts/{id}/reject` | Drop from queue |
| GET | `/api/logs` | Scan / import / link / error log |
| GET | `/api/settings` | Effective settings |
| PATCH | `/api/settings` | Update settings |

## Running tests

```bash
pip install pytest
pytest -q
```

The tests cover link parsing, ASIN extraction, caption rendering, dedup
hashing, the `/api/scan` pipeline end-to-end in test mode, and all REST
endpoints.
