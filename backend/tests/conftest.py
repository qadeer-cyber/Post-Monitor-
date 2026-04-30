from __future__ import annotations

import os
import tempfile
from collections.abc import Iterator

import pytest

# Isolate DB + env per test run *before* importing app modules.
_tmpdir = tempfile.mkdtemp(prefix="apm-test-")
os.environ.setdefault("DATABASE_URL", f"sqlite:///{_tmpdir}/test.db")
os.environ.setdefault("AMAZON_ASSOCIATE_TAG", "test-tag-20")
os.environ.setdefault("TEST_MODE", "true")
os.environ.setdefault("ENABLE_PLAYWRIGHT_FALLBACK", "false")
os.environ.setdefault("DELAY_BETWEEN_PAGE_SCANS_SECONDS", "0")
os.environ["DISABLE_SCHEDULER"] = "1"

from fastapi.testclient import TestClient  # noqa: E402

from app.config import get_settings  # noqa: E402
from app.database import Base, SessionLocal, engine  # noqa: E402
from app.main import create_app  # noqa: E402


@pytest.fixture(autouse=True)
def _fresh_db() -> Iterator[None]:
    # rebuild schema for every test for isolation
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    get_settings.cache_clear()
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture
def client() -> Iterator[TestClient]:
    app = create_app()
    with TestClient(app) as c:
        yield c


@pytest.fixture
def db() -> Iterator:
    s = SessionLocal()
    try:
        yield s
    finally:
        s.close()
