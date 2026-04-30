from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from .. import models
from ..config import get_settings
from ..database import get_db
from ..schemas import (
    SourceCreate,
    SourceOut,
    SourcePreview,
    SourcePreviewPost,
    SourceUpdate,
    SourceValidateIn,
)
from ..services import settings_store
from ..services.facebook import fetch_page, load_sample_scrape
from ..services.logs import log
from ..services.scanner import _load_samples, run_scan

router = APIRouter()


@router.post("/sources/validate", response_model=SourcePreview)
def validate_source(payload: SourceValidateIn, db: Session = Depends(get_db)) -> SourcePreview:
    """Preview a page before saving. Does *not* persist anything — purely a
    read-only check to help the user confirm they've pasted the right URL
    and that the page is actually public and has recent posts.
    """
    url = str(payload.url).rstrip("/")
    settings = get_settings()
    # Honour the DB-level test_mode override the user can flip via
    # PATCH /api/settings, just like run_scan does — otherwise validation
    # could read sample data while a real scan hits the network (or vice versa).
    effective = settings_store.get_effective(db)
    test_mode = bool(effective.get("test_mode", settings.test_mode))

    if test_mode:
        samples = _load_samples()
        sample = samples.get(url)
        if not sample:
            return SourcePreview(
                url=url,
                is_reachable=False,
                is_public=False,
                error="No sample data for this URL (test mode)",
            )
        page = load_sample_scrape(url, sample)
    else:
        page = fetch_page(
            url,
            user_agent=settings.user_agent,
            enable_playwright_fallback=settings.enable_playwright_fallback,
        )

    if page.error and not page.posts and not page.page_name:
        log(
            db,
            category="scan",
            level="warn",
            message=f"Validation failed for {url}",
            detail=page.error,
        )
        db.commit()
        return SourcePreview(
            url=url,
            is_reachable=False,
            is_public=False,
            error=page.error,
        )

    # If we could read OG metadata or extract any post permalinks, treat it
    # as a reachable public page. We do NOT try to defeat login walls — if FB
    # returned a login interstitial there will be no og:site_name and no
    # permalinks, which correctly surfaces here as is_public=False.
    is_public = bool(page.page_name) or bool(page.posts)

    samples_out: list[SourcePreviewPost] = []
    for p in page.posts[:5]:
        samples_out.append(
            SourcePreviewPost(
                url=p.source_post_url,
                description=(p.description or "")[:240] or None,
                image_url=p.image_url,
                has_amazon_link=bool(p.amazon_urls),
            )
        )

    log(
        db,
        category="scan",
        message=f"Validated {url}: public={is_public}, recent_posts={len(page.posts)}",
    )
    db.commit()

    return SourcePreview(
        url=url,
        is_reachable=page.error is None or is_public,
        is_public=is_public,
        page_name=page.page_name,
        recent_posts_count=len(page.posts),
        sample_posts=samples_out,
        error=page.error,
    )


@router.post("/sources", response_model=SourceOut, status_code=status.HTTP_201_CREATED)
def create_source(payload: SourceCreate, db: Session = Depends(get_db)) -> SourceOut:
    url = str(payload.url).rstrip("/")
    existing = db.query(models.Source).filter(models.Source.url == url).one_or_none()
    if existing is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, "Source already exists")
    src = models.Source(url=url, name=payload.name, enabled=payload.enabled)
    db.add(src)
    db.flush()
    log(db, category="import", message=f"Added source {url}")
    db.commit()
    db.refresh(src)
    return SourceOut.model_validate(src)


@router.get("/sources", response_model=list[SourceOut])
def list_sources(db: Session = Depends(get_db)) -> list[SourceOut]:
    rows = db.query(models.Source).order_by(models.Source.id.asc()).all()
    return [SourceOut.model_validate(r) for r in rows]


@router.patch("/sources/{source_id}", response_model=SourceOut)
def update_source(source_id: int, payload: SourceUpdate, db: Session = Depends(get_db)) -> SourceOut:
    src = db.get(models.Source, source_id)
    if src is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Source not found")
    if payload.name is not None:
        src.name = payload.name
    if payload.enabled is not None:
        src.enabled = payload.enabled
    db.commit()
    db.refresh(src)
    return SourceOut.model_validate(src)


@router.delete("/sources/{source_id}", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def delete_source(source_id: int, db: Session = Depends(get_db)) -> Response:
    src = db.get(models.Source, source_id)
    if src is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Source not found")
    db.delete(src)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/sources/{source_id}/scan")
def scan_single_source(source_id: int, db: Session = Depends(get_db)) -> dict:
    src = db.get(models.Source, source_id)
    if src is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Source not found")
    if not src.enabled:
        raise HTTPException(
            status.HTTP_409_CONFLICT,
            "Source is inactive — enable it before running a manual scan.",
        )
    history = run_scan(db, source_id=source_id)
    return {
        "ok": history.ok,
        "pages_scanned": history.pages_scanned,
        "posts_found": history.posts_found,
        "posts_imported": history.posts_imported,
        "duplicates_skipped": history.duplicates_skipped,
        "failed": history.failed,
        "started_at": history.started_at,
        "finished_at": history.finished_at,
    }
