"""Helpers for storing user-editable settings in the DB.

The fields that can be overridden at runtime (from the app's Settings screen
or PATCH /api/settings) live in the ``app_settings`` table as key/value pairs.
Values are always stored as strings and decoded on read.
"""

from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy.orm import Session

from .. import models
from ..config import get_settings

KEYS = {
    "amazon_associate_tag",
    "scan_interval_minutes",
    "daily_import_limit",
    "delay_between_page_scans_seconds",
    "test_mode",
    "backend_url",
}


def _coerce(key: str, value: str) -> object:
    if key in {"scan_interval_minutes", "daily_import_limit", "delay_between_page_scans_seconds"}:
        try:
            return int(value)
        except ValueError:
            return 0
    if key == "test_mode":
        return value.strip().lower() in {"1", "true", "yes", "on"}
    return value


def get_effective(db: Session) -> dict[str, object]:
    """Return the effective settings: env defaults overlaid with DB overrides."""
    settings = get_settings()
    out: dict[str, object] = {
        "amazon_associate_tag": settings.amazon_associate_tag,
        "scan_interval_minutes": settings.scan_interval_minutes,
        "daily_import_limit": settings.daily_import_limit,
        "delay_between_page_scans_seconds": settings.delay_between_page_scans_seconds,
        "test_mode": settings.test_mode,
        "backend_url": None,
    }
    for row in db.query(models.AppSetting).all():
        if row.key in KEYS:
            out[row.key] = _coerce(row.key, row.value)
    return out


def update(db: Session, patch: dict[str, object]) -> dict[str, object]:
    for key, value in patch.items():
        if value is None or key not in KEYS:
            continue
        stored = str(value)
        row = db.get(models.AppSetting, key)
        if row is None:
            row = models.AppSetting(key=key, value=stored)
            db.add(row)
        else:
            row.value = stored
            row.updated_at = datetime.now(timezone.utc)
    db.flush()
    return get_effective(db)
