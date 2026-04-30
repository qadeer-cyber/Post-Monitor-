from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from .. import models
from ..database import get_db
from ..schemas import PostOut
from ..services.logs import log

router = APIRouter()


def _get(db: Session, post_id: int) -> models.Post:
    row = db.get(models.Post, post_id)
    if row is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Post not found")
    return row


@router.get("/posts/{post_id}", response_model=PostOut)
def get_post(post_id: int, db: Session = Depends(get_db)) -> PostOut:
    return PostOut.model_validate(_get(db, post_id))


@router.post("/posts/{post_id}/mark-posted", response_model=PostOut)
def mark_posted(post_id: int, db: Session = Depends(get_db)) -> PostOut:
    row = _get(db, post_id)
    row.status = "posted"
    row.posted_at = datetime.now(timezone.utc)
    log(db, category="import", message=f"Marked post {row.id} as posted", source_id=row.source_id)
    db.commit()
    db.refresh(row)
    return PostOut.model_validate(row)


@router.post("/posts/{post_id}/reject", response_model=PostOut)
def reject_post(post_id: int, db: Session = Depends(get_db)) -> PostOut:
    row = _get(db, post_id)
    row.status = "rejected"
    row.rejected_at = datetime.now(timezone.utc)
    log(db, category="import", message=f"Rejected post {row.id}", source_id=row.source_id)
    db.commit()
    db.refresh(row)
    return PostOut.model_validate(row)
