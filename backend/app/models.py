"""ORM models for Affiliate Post Monitor."""

from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Source(Base):
    __tablename__ = "sources"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    url: Mapped[str] = mapped_column(String(1024), unique=True, nullable=False)
    name: Mapped[str | None] = mapped_column(String(512), nullable=True)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    posts_found: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, nullable=False)

    posts: Mapped[list["Post"]] = relationship(back_populates="source", cascade="all, delete-orphan")


class Post(Base):
    __tablename__ = "posts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    source_id: Mapped[int] = mapped_column(ForeignKey("sources.id", ondelete="CASCADE"), index=True)

    source_post_url: Mapped[str] = mapped_column(String(1024), unique=True, nullable=False)
    source_page_name: Mapped[str | None] = mapped_column(String(512), nullable=True)

    original_description: Mapped[str] = mapped_column(Text, default="", nullable=False)
    image_url: Mapped[str | None] = mapped_column(String(1024), nullable=True)
    post_time: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    amazon_url: Mapped[str] = mapped_column(String(1024), nullable=False)
    affiliate_url: Mapped[str] = mapped_column(String(1024), nullable=False)
    asin: Mapped[str] = mapped_column(String(32), index=True, nullable=False)
    marketplace: Mapped[str] = mapped_column(String(64), default="amazon.com", nullable=False)

    final_caption: Mapped[str] = mapped_column(Text, nullable=False)
    caption_hash: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    image_hash: Mapped[str | None] = mapped_column(String(64), index=True, nullable=True)

    # queue | posted | rejected
    status: Mapped[str] = mapped_column(String(16), default="queue", index=True, nullable=False)
    posted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    rejected_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, nullable=False)

    source: Mapped[Source] = relationship(back_populates="posts")


class ScanHistory(Base):
    __tablename__ = "scan_history"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, nullable=False)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    source_id: Mapped[int | None] = mapped_column(ForeignKey("sources.id", ondelete="SET NULL"), nullable=True)
    ok: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    pages_scanned: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    posts_found: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    posts_imported: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    duplicates_skipped: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    failed: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    notes: Mapped[str | None] = mapped_column(Text, nullable=True)


class LogEntry(Base):
    __tablename__ = "logs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, index=True, nullable=False)
    # scan | import | link | error
    category: Mapped[str] = mapped_column(String(16), index=True, nullable=False)
    # info | warn | error
    level: Mapped[str] = mapped_column(String(16), index=True, default="info", nullable=False)
    message: Mapped[str] = mapped_column(Text, nullable=False)
    source_id: Mapped[int | None] = mapped_column(ForeignKey("sources.id", ondelete="SET NULL"), nullable=True)
    detail: Mapped[str | None] = mapped_column(Text, nullable=True)


class AppSetting(Base):
    __tablename__ = "app_settings"

    key: Mapped[str] = mapped_column(String(64), primary_key=True)
    value: Mapped[str] = mapped_column(Text, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow, nullable=False)
