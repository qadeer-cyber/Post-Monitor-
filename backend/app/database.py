"""SQLAlchemy engine + session setup for the SQLite DB."""

from __future__ import annotations

import os
from collections.abc import Iterator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import get_settings


class Base(DeclarativeBase):
    pass


def _make_engine(url: str):
    connect_args = {"check_same_thread": False} if url.startswith("sqlite") else {}
    if url.startswith("sqlite:///"):
        path = url.replace("sqlite:///", "", 1)
        if path and not path.startswith(":memory:"):
            directory = os.path.dirname(os.path.abspath(path))
            if directory:
                os.makedirs(directory, exist_ok=True)
    return create_engine(url, connect_args=connect_args, future=True)


engine = _make_engine(get_settings().database_url)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, future=True)


def get_db() -> Iterator[Session]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    """Create tables. Called on startup.

    Also runs a tiny idempotent SQLite migration that adds columns which were
    introduced after v0.1.0 — SQLAlchemy's ``create_all`` does not alter existing
    tables, so we inspect the live schema and issue ``ALTER TABLE ADD COLUMN``
    when a known column is missing. This keeps old databases on disk working
    after upgrades without requiring a manual wipe.
    """
    from sqlalchemy import inspect, text

    from . import models  # noqa: F401 — ensure models are registered

    Base.metadata.create_all(bind=engine)

    # idempotent additive migrations
    additions: dict[str, list[tuple[str, str]]] = {
        "sources": [
            ("valid_amazon_posts", "INTEGER NOT NULL DEFAULT 0"),
        ],
    }

    inspector = inspect(engine)
    with engine.begin() as conn:
        for table, cols in additions.items():
            if not inspector.has_table(table):
                continue
            existing = {c["name"] for c in inspector.get_columns(table)}
            for name, ddl in cols:
                if name not in existing:
                    conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {name} {ddl}"))
