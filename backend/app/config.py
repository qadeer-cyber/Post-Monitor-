"""Runtime configuration.

Values come from environment variables / .env first, then may be overridden
per-install from the Settings table via :func:`get_runtime_settings`.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    amazon_associate_tag: str = "laique248-20"
    database_url: str = "sqlite:///./data/app.db"
    host: str = "0.0.0.0"
    port: int = 8000

    scan_interval_minutes: int = 60
    daily_import_limit: int = 100
    delay_between_page_scans_seconds: int = 5

    test_mode: bool = False
    enable_playwright_fallback: bool = False
    user_agent: str = (
        "Mozilla/5.0 (compatible; AffiliatePostMonitor/1.0; +https://example.com/bot)"
    )
    cors_origins: str = "*"

    def cors_list(self) -> list[str]:
        raw = (self.cors_origins or "").strip()
        if not raw or raw == "*":
            return ["*"]
        return [o.strip() for o in raw.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
