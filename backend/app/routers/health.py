from __future__ import annotations

from fastapi import APIRouter

from .. import __version__
from ..config import get_settings
from ..schemas import HealthOut

router = APIRouter()


@router.get("/health", response_model=HealthOut)
def health() -> HealthOut:
    s = get_settings()
    return HealthOut(
        version=__version__,
        test_mode=s.test_mode,
        playwright_fallback=s.enable_playwright_fallback,
    )
