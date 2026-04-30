"""Tiny DB-backed logger used by the scanner and API routes."""

from __future__ import annotations

from sqlalchemy.orm import Session

from .. import models


def log(
    db: Session,
    *,
    category: str,
    message: str,
    level: str = "info",
    source_id: int | None = None,
    detail: str | None = None,
) -> models.LogEntry:
    entry = models.LogEntry(
        category=category,
        level=level,
        message=message,
        source_id=source_id,
        detail=detail,
    )
    db.add(entry)
    db.flush()
    return entry
