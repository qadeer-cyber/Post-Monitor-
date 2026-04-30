from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from .. import models
from ..database import get_db
from ..schemas import LogOut

router = APIRouter()


@router.get("/logs", response_model=list[LogOut])
def list_logs(
    db: Session = Depends(get_db),
    category: str | None = Query(None, pattern=r"^(scan|import|link|error)$"),
    level: str | None = Query(None, pattern=r"^(info|warn|error)$"),
    limit: int = Query(200, ge=1, le=1000),
    offset: int = Query(0, ge=0),
) -> list[LogOut]:
    q = db.query(models.LogEntry)
    if category:
        q = q.filter(models.LogEntry.category == category)
    if level:
        q = q.filter(models.LogEntry.level == level)
    rows = q.order_by(models.LogEntry.created_at.desc()).offset(offset).limit(limit).all()
    return [LogOut.model_validate(r) for r in rows]
