"""Background scheduler that periodically triggers ``run_scan``."""

from __future__ import annotations

import logging

from apscheduler.schedulers.background import BackgroundScheduler

from .database import SessionLocal
from .services import settings_store
from .services.scanner import run_scan

logger = logging.getLogger(__name__)

_scheduler: BackgroundScheduler | None = None
_JOB_ID = "periodic-scan"


def _trigger_scan() -> None:
    db = SessionLocal()
    try:
        run_scan(db)
    except Exception:
        logger.exception("scheduled scan failed")
    finally:
        db.close()


def start_scheduler() -> BackgroundScheduler:
    global _scheduler
    if _scheduler is not None:
        return _scheduler

    db = SessionLocal()
    try:
        effective = settings_store.get_effective(db)
    finally:
        db.close()
    minutes = int(effective.get("scan_interval_minutes") or 30)

    sched = BackgroundScheduler(timezone="UTC")
    sched.add_job(_trigger_scan, "interval", minutes=minutes, id=_JOB_ID, replace_existing=True)
    sched.start()
    _scheduler = sched
    logger.info("scheduler started: every %d minutes", minutes)
    return sched


def stop_scheduler() -> None:
    global _scheduler
    if _scheduler is not None:
        try:
            _scheduler.shutdown(wait=False)
        except Exception:
            pass
        _scheduler = None
