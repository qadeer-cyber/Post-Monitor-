from __future__ import annotations

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends
from sqlalchemy import func
from sqlalchemy.orm import Session

from .. import models
from ..database import get_db
from ..schemas import DashboardOut

router = APIRouter()


@router.get("/dashboard", response_model=DashboardOut)
def dashboard(db: Session = Depends(get_db)) -> DashboardOut:
    total_pages = db.query(func.count(models.Source.id)).scalar() or 0

    today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    new_today = (
        db.query(func.count(models.Post.id))
        .filter(models.Post.created_at >= today_start)
        .scalar()
        or 0
    )

    ready = (
        db.query(func.count(models.Post.id))
        .filter(models.Post.status == "queue")
        .scalar()
        or 0
    )

    one_day = today_start - timedelta(days=1)
    dup = (
        db.query(func.coalesce(func.sum(models.ScanHistory.duplicates_skipped), 0))
        .filter(models.ScanHistory.started_at >= one_day)
        .scalar()
        or 0
    )
    failed = (
        db.query(func.coalesce(func.sum(models.ScanHistory.failed), 0))
        .filter(models.ScanHistory.started_at >= one_day)
        .scalar()
        or 0
    )

    last = (
        db.query(func.max(models.ScanHistory.started_at)).scalar()
    )

    return DashboardOut(
        total_monitored_pages=total_pages,
        new_posts_today=new_today,
        ready_posts=ready,
        duplicates_skipped=int(dup),
        failed_imports=int(failed),
        last_scan_at=last,
    )
