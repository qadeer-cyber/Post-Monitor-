"""Scan orchestration — ties Facebook scraping, Amazon parsing, dedup and
caption rendering together.

Flow per source:
  fetch page -> parse posts -> for each post with Amazon links:
    resolve short links, extract ASIN, build affiliate URL, build caption,
    hash image+caption, check duplicates, insert if new.

Every non-trivial step writes a LogEntry so the Logs tab in the app has
concrete, user-visible reasons for failures.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import httpx
from sqlalchemy.orm import Session

from .. import models
from ..config import Settings, get_settings
from . import amazon as amazon_svc
from . import dedup, settings_store
from .caption import build_caption
from .facebook import ScrapedPage, ScrapedPost, fetch_page, load_sample_scrape
from .logs import log

logger = logging.getLogger(__name__)

SAMPLE_PATH = Path(__file__).resolve().parents[2] / "sample_data" / "pages.json"


@dataclass
class ScanTally:
    pages_scanned: int = 0
    posts_found: int = 0
    posts_imported: int = 0
    duplicates_skipped: int = 0
    failed: int = 0


def _load_samples() -> dict[str, dict]:
    import json

    if not SAMPLE_PATH.exists():
        return {}
    try:
        return json.loads(SAMPLE_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


def _fetch_image_bytes(url: str | None, client: httpx.Client) -> bytes | None:
    if not url:
        return None
    try:
        r = client.get(url, timeout=15.0)
        if r.status_code == 200 and r.content:
            return r.content
    except httpx.HTTPError:
        return None
    return None


def _process_post(
    db: Session,
    source: models.Source,
    post: ScrapedPost,
    *,
    associate_tag: str,
    http_client: httpx.Client,
    tally: ScanTally,
) -> None:
    if not post.amazon_urls:
        return
    tally.posts_found += 1

    # Try each amazon url in the post; first one that yields an ASIN wins.
    parsed = None
    for url in post.amazon_urls:
        try:
            parsed = amazon_svc.parse_amazon_link(url, associate_tag=associate_tag, client=http_client)
        except Exception as exc:  # pragma: no cover — defensive
            log(
                db,
                category="link",
                level="error",
                message=f"Failed to parse Amazon link: {url}",
                source_id=source.id,
                detail=str(exc),
            )
            continue
        if parsed:
            break

    if not parsed:
        tally.failed += 1
        log(
            db,
            category="link",
            level="warn",
            message=f"No ASIN found in any Amazon link for post {post.source_post_url}",
            source_id=source.id,
            detail=", ".join(post.amazon_urls),
        )
        return

    caption = build_caption(post.description, parsed.affiliate_url)
    cap_hash = dedup.caption_hash(caption)

    if get_settings().test_mode:
        img_bytes = None
    else:
        img_bytes = _fetch_image_bytes(post.image_url, http_client) if post.image_url else None
    img_hash = dedup.image_hash_from_bytes(img_bytes) if img_bytes else None

    existing = dedup.find_duplicate(
        db,
        source_post_url=post.source_post_url,
        asin=parsed.asin,
        caption_hash_value=cap_hash,
        image_hash_value=img_hash,
    )
    if existing is not None:
        tally.duplicates_skipped += 1
        log(
            db,
            category="import",
            message=f"Skipped duplicate for ASIN {parsed.asin}",
            source_id=source.id,
            detail=f"existing post id={existing.id}",
        )
        return

    row = models.Post(
        source_id=source.id,
        source_post_url=post.source_post_url,
        source_page_name=source.name,
        original_description=post.description or "",
        image_url=post.image_url,
        post_time=post.post_time,
        amazon_url=parsed.normalized_url,
        affiliate_url=parsed.affiliate_url,
        asin=parsed.asin,
        marketplace=parsed.marketplace,
        final_caption=caption,
        caption_hash=cap_hash,
        image_hash=img_hash,
    )
    db.add(row)
    source.posts_found = (source.posts_found or 0) + 1
    source.valid_amazon_posts = (source.valid_amazon_posts or 0) + 1
    tally.posts_imported += 1
    log(
        db,
        category="import",
        message=f"Imported new post: ASIN {parsed.asin}",
        source_id=source.id,
        detail=post.source_post_url,
    )


def _scan_source(
    db: Session,
    source: models.Source,
    *,
    settings: Settings,
    effective: dict[str, object],
    samples: dict[str, dict],
    http_client: httpx.Client,
    remaining_budget: int,
    tally: ScanTally,
) -> None:
    # Per-source tallies so we can emit clear per-source log lines at the end.
    before_found = tally.posts_found
    before_imported = tally.posts_imported
    before_failed = tally.failed
    log(
        db,
        category="scan",
        message=f"Scan started for {source.url}",
        source_id=source.id,
    )

    page: ScrapedPage
    if bool(effective.get("test_mode", settings.test_mode)):
        sample = samples.get(source.url)
        if not sample:
            log(
                db,
                category="scan",
                level="warn",
                message="Test mode on but no sample data for source",
                source_id=source.id,
                detail=source.url,
            )
            page = ScrapedPage(page_url=source.url, error="no sample data")
        else:
            page = load_sample_scrape(source.url, sample)
            log(db, category="scan", message="Loaded sample data (test mode)", source_id=source.id)
    else:
        page = fetch_page(
            source.url,
            user_agent=settings.user_agent,
            enable_playwright_fallback=settings.enable_playwright_fallback,
        )
        if page.used_fallback:
            log(db, category="scan", message="Used Playwright fallback", source_id=source.id)
        if page.error:
            tally.failed += 1
            log(
                db,
                category="error",
                level="error",
                message=f"Fetch failed for {source.url}",
                source_id=source.id,
                detail=page.error,
            )
            if not page.posts:
                return

    if page.page_name and not source.name:
        source.name = page.page_name

    source.last_checked_at = datetime.now(timezone.utc)
    tally.pages_scanned += 1

    tag = str(effective.get("amazon_associate_tag") or settings.amazon_associate_tag)
    for post in page.posts:
        if remaining_budget <= 0:
            log(
                db,
                category="import",
                level="warn",
                message="Daily import limit reached — stopping scan",
                source_id=source.id,
            )
            break
        before = tally.posts_imported
        _process_post(db, source, post, associate_tag=tag, http_client=http_client, tally=tally)
        remaining_budget -= tally.posts_imported - before

    posts_found_here = tally.posts_found - before_found
    amazon_extracted_here = tally.posts_imported - before_imported
    failed_here = tally.failed - before_failed
    log(
        db,
        category="scan",
        message=(
            f"Scan finished for {source.url}: "
            f"posts_found={posts_found_here}, "
            f"amazon_posts_extracted={amazon_extracted_here}, "
            f"errors={failed_here}"
        ),
        source_id=source.id,
    )


def run_scan(db: Session, *, source_id: int | None = None) -> models.ScanHistory:
    """Run a scan. If ``source_id`` is provided, only that source is scanned."""
    settings = get_settings()
    effective = settings_store.get_effective(db)

    history = models.ScanHistory(source_id=source_id)
    db.add(history)
    db.flush()
    log(
        db,
        category="scan",
        message=("Starting single-source scan" if source_id else "Starting scan"),
        source_id=source_id,
    )

    samples = _load_samples() if effective.get("test_mode", settings.test_mode) else {}

    q = db.query(models.Source).filter(models.Source.enabled.is_(True))
    if source_id is not None:
        q = q.filter(models.Source.id == source_id)
    sources = q.all()

    tally = ScanTally()
    daily_limit = int(effective.get("daily_import_limit") or settings.daily_import_limit)
    delay = int(effective.get("delay_between_page_scans_seconds") or settings.delay_between_page_scans_seconds)

    with httpx.Client(
        follow_redirects=True,
        timeout=20.0,
        headers={"User-Agent": settings.user_agent},
    ) as http_client:
        for idx, source in enumerate(sources):
            remaining = max(daily_limit - tally.posts_imported, 0)
            if remaining <= 0:
                break
            try:
                _scan_source(
                    db,
                    source,
                    settings=settings,
                    effective=effective,
                    samples=samples,
                    http_client=http_client,
                    remaining_budget=remaining,
                    tally=tally,
                )
            except Exception as exc:
                tally.failed += 1
                log(
                    db,
                    category="error",
                    level="error",
                    message=f"Unhandled error scanning {source.url}",
                    source_id=source.id,
                    detail=str(exc),
                )
            if idx < len(sources) - 1 and delay > 0:
                time.sleep(delay)

    history.finished_at = datetime.now(timezone.utc)
    history.ok = tally.failed == 0
    history.pages_scanned = tally.pages_scanned
    history.posts_found = tally.posts_found
    history.posts_imported = tally.posts_imported
    history.duplicates_skipped = tally.duplicates_skipped
    history.failed = tally.failed
    db.commit()
    db.refresh(history)
    return history
