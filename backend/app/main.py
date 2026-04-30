"""FastAPI entry point for Affiliate Post Monitor."""

from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from . import __version__
from .config import get_settings
from .database import init_db
from .routers import (
    dashboard as dashboard_router,
)
from .routers import (
    health as health_router,
)
from .routers import (
    logs as logs_router,
)
from .routers import (
    posts as posts_router,
)
from .routers import (
    queue as queue_router,
)
from .routers import (
    scan as scan_router,
)
from .routers import (
    settings as settings_router,
)
from .routers import (
    sources as sources_router,
)
from .scheduler import start_scheduler, stop_scheduler

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    if os.getenv("DISABLE_SCHEDULER", "").lower() not in {"1", "true", "yes"}:
        start_scheduler()
    try:
        yield
    finally:
        stop_scheduler()


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title="Affiliate Post Monitor", version=__version__, lifespan=lifespan)

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_list(),
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    prefix = "/api"
    app.include_router(health_router.router, prefix=prefix, tags=["health"])
    app.include_router(sources_router.router, prefix=prefix, tags=["sources"])
    app.include_router(scan_router.router, prefix=prefix, tags=["scan"])
    app.include_router(dashboard_router.router, prefix=prefix, tags=["dashboard"])
    app.include_router(queue_router.router, prefix=prefix, tags=["queue"])
    app.include_router(posts_router.router, prefix=prefix, tags=["posts"])
    app.include_router(logs_router.router, prefix=prefix, tags=["logs"])
    app.include_router(settings_router.router, prefix=prefix, tags=["settings"])

    return app


app = create_app()
