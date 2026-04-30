from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from .. import models
from ..database import get_db
from ..schemas import PostOut

router = APIRouter()


@router.get("/queue", response_model=list[PostOut])
def queue(
    db: Session = Depends(get_db),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
) -> list[PostOut]:
    rows = (
        db.query(models.Post)
        .filter(models.Post.status == "queue")
        .order_by(models.Post.created_at.desc())
        .offset(offset)
        .limit(limit)
        .all()
    )
    return [PostOut.model_validate(r) for r in rows]


@router.get("/posted", response_model=list[PostOut])
def posted(
    db: Session = Depends(get_db),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
) -> list[PostOut]:
    rows = (
        db.query(models.Post)
        .filter(models.Post.status == "posted")
        .order_by(models.Post.posted_at.desc())
        .offset(offset)
        .limit(limit)
        .all()
    )
    return [PostOut.model_validate(r) for r in rows]
