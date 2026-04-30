from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from ..database import get_db
from ..schemas import SettingsOut, SettingsUpdate
from ..services import settings_store

router = APIRouter()


@router.get("/settings", response_model=SettingsOut)
def get_settings(db: Session = Depends(get_db)) -> SettingsOut:
    return SettingsOut(**settings_store.get_effective(db))


@router.patch("/settings", response_model=SettingsOut)
def patch_settings(payload: SettingsUpdate, db: Session = Depends(get_db)) -> SettingsOut:
    updated = settings_store.update(db, payload.model_dump(exclude_none=True))
    db.commit()
    return SettingsOut(**updated)
