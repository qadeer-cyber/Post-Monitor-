from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from .. import models
from ..database import get_db
from ..schemas import SourceCreate, SourceOut, SourceUpdate
from ..services.logs import log
from ..services.scanner import run_scan

router = APIRouter()


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
