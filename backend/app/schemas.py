"""Pydantic schemas used by the API."""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, HttpUrl, model_validator


class SourceCreate(BaseModel):
    url: HttpUrl
    name: str | None = None
    enabled: bool = True


class SourceUpdate(BaseModel):
    name: str | None = None
    enabled: bool | None = None


class SourceOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    url: str
    name: str | None = None
    enabled: bool
    status: str = "inactive"
    last_checked_at: datetime | None = None
    posts_found: int
    valid_amazon_posts: int = 0
    created_at: datetime

    @model_validator(mode="after")
    def _derive_status(self) -> "SourceOut":
        self.status = "active" if self.enabled else "inactive"
        return self


class SourcePreviewPost(BaseModel):
    url: str
    description: str | None = None
    image_url: str | None = None
    has_amazon_link: bool = False


class SourcePreview(BaseModel):
    url: str
    is_reachable: bool
    is_public: bool
    page_name: str | None = None
    recent_posts_count: int = 0
    sample_posts: list[SourcePreviewPost] = []
    error: str | None = None


class SourceValidateIn(BaseModel):
    url: HttpUrl


class PostOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    source_id: int
    source_page_name: str | None = None
    source_post_url: str
    original_description: str
    image_url: str | None = None
    post_time: datetime | None = None
    amazon_url: str
    affiliate_url: str
    asin: str
    marketplace: str
    final_caption: str
    status: str
    posted_at: datetime | None = None
    rejected_at: datetime | None = None
    created_at: datetime


class DashboardOut(BaseModel):
    total_monitored_pages: int
    total_sources: int
    active_sources: int
    new_posts_today: int
    ready_posts: int
    queue_size: int
    valid_amazon_posts: int
    duplicates_skipped: int
    failed_imports: int
    last_scan_at: datetime | None = None


class ScanResult(BaseModel):
    ok: bool
    pages_scanned: int
    posts_found: int
    posts_imported: int
    duplicates_skipped: int
    failed: int
    started_at: datetime
    finished_at: datetime
    notes: str | None = None


class LogOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    created_at: datetime
    category: str
    level: str
    message: str
    source_id: int | None = None
    detail: str | None = None


class SettingsOut(BaseModel):
    amazon_associate_tag: str
    scan_interval_minutes: int = Field(ge=1)
    daily_import_limit: int = Field(ge=1)
    delay_between_page_scans_seconds: int = Field(ge=0)
    test_mode: bool
    backend_url: str | None = None


class SettingsUpdate(BaseModel):
    amazon_associate_tag: str | None = None
    scan_interval_minutes: int | None = Field(default=None, ge=1)
    daily_import_limit: int | None = Field(default=None, ge=1)
    delay_between_page_scans_seconds: int | None = Field(default=None, ge=0)
    test_mode: bool | None = None
    backend_url: str | None = None


class HealthOut(BaseModel):
    ok: bool = True
    version: str
    test_mode: bool
    playwright_fallback: bool
