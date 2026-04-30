from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..schemas import ScanResult
from ..services.scanner import run_scan

router = APIRouter()


@router.post("/scan", response_model=ScanResult)
def scan_all(db: Session = Depends(get_db)) -> ScanResult:
    h = run_scan(db)
    return ScanResult(
        ok=h.ok,
        pages_scanned=h.pages_scanned,
        posts_found=h.posts_found,
        posts_imported=h.posts_imported,
        duplicates_skipped=h.duplicates_skipped,
        failed=h.failed,
        started_at=h.started_at,
        finished_at=h.finished_at or h.started_at,
        notes=h.notes,
    )
