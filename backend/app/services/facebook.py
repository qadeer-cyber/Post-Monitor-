"""Public Facebook Page scraping.

Strategy:
  1. Fetch the page's HTML with plain httpx.
  2. Read Open Graph tags (og:title, og:description, og:image, og:url).
  3. Walk links and scripts for anything that looks like a post permalink
     and any Amazon URLs appearing in the HTML text.
  4. If step 2/3 yields nothing useful AND Playwright fallback is enabled,
     retry with a headless browser.

The parser intentionally stays shallow and respectful — it does NOT try to
bypass login walls, solve captchas, or hit any private endpoint. It scrapes
only public HTML for pages the user explicitly added.
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone

import httpx
from bs4 import BeautifulSoup

from .amazon import URL_RE, find_amazon_urls

logger = logging.getLogger(__name__)


@dataclass
class ScrapedPost:
    source_post_url: str
    description: str = ""
    image_url: str | None = None
    post_time: datetime | None = None
    amazon_urls: list[str] = field(default_factory=list)


@dataclass
class ScrapedPage:
    page_url: str
    page_name: str | None = None
    posts: list[ScrapedPost] = field(default_factory=list)
    used_fallback: bool = False
    error: str | None = None


def _meta(soup: BeautifulSoup, prop: str) -> str | None:
    tag = soup.find("meta", attrs={"property": prop}) or soup.find("meta", attrs={"name": prop})
    if tag and tag.get("content"):
        return tag["content"].strip()
    return None


POST_PERMALINK_RE = re.compile(
    r"(?:https?:)?//(?:www\.|m\.|web\.)?facebook\.com/(?:[^/\s\"']+/(?:posts|videos|photos)/[^\s\"'?#]+|"
    r"permalink\.php\?story_fbid=[^\s\"']+|"
    r"story\.php\?story_fbid=[^\s\"']+|"
    r"[^/\s\"']+/pfbid[0-9A-Za-z]+)",
    re.IGNORECASE,
)


def _extract_permalinks(html: str) -> list[str]:
    urls: set[str] = set()
    for m in POST_PERMALINK_RE.finditer(html or ""):
        raw = m.group(0)
        if raw.startswith("//"):
            raw = "https:" + raw
        urls.add(raw)
    return list(urls)


def parse_page_html(html: str, page_url: str) -> ScrapedPage:
    """Structured parse. Each unique permalink found becomes one ScrapedPost,
    with any Amazon URLs discovered in the HTML text attached to it.

    When no permalinks are found but the page itself has Amazon links in its
    OG description, we emit a single synthetic post pointing at the page URL
    so the caller can still produce a caption draft.
    """
    soup = BeautifulSoup(html or "", "lxml")
    page = ScrapedPage(page_url=page_url)
    page.page_name = _meta(soup, "og:site_name") or _meta(soup, "og:title")

    page_desc = _meta(soup, "og:description") or ""
    page_image = _meta(soup, "og:image")

    text = soup.get_text(" ", strip=True) if soup else ""
    amazon_in_page = find_amazon_urls(text + " " + html)

    permalinks = _extract_permalinks(html)

    if permalinks:
        for link in permalinks:
            page.posts.append(
                ScrapedPost(
                    source_post_url=link,
                    description=page_desc,
                    image_url=page_image,
                    post_time=None,
                    amazon_urls=list(amazon_in_page),
                )
            )
    elif amazon_in_page:
        page.posts.append(
            ScrapedPost(
                source_post_url=page_url,
                description=page_desc,
                image_url=page_image,
                post_time=None,
                amazon_urls=list(amazon_in_page),
            )
        )
    return page


def fetch_page(
    url: str,
    *,
    user_agent: str,
    timeout: float = 20.0,
    enable_playwright_fallback: bool = False,
) -> ScrapedPage:
    """Fetch and parse a public FB page URL. Raises only on unexpected errors —
    HTTP failures are captured in ``ScrapedPage.error``."""
    headers = {"User-Agent": user_agent, "Accept-Language": "en-US,en;q=0.9"}
    try:
        with httpx.Client(follow_redirects=True, timeout=timeout, headers=headers) as client:
            resp = client.get(url)
        if resp.status_code >= 400:
            out = ScrapedPage(page_url=url, error=f"HTTP {resp.status_code} from {url}")
            return _maybe_playwright(out, url, enable_playwright_fallback, user_agent, timeout)
        html = resp.text
    except httpx.HTTPError as exc:
        out = ScrapedPage(page_url=url, error=f"network error: {exc}")
        return _maybe_playwright(out, url, enable_playwright_fallback, user_agent, timeout)

    parsed = parse_page_html(html, url)
    if not parsed.posts:
        return _maybe_playwright(parsed, url, enable_playwright_fallback, user_agent, timeout)
    return parsed


def _maybe_playwright(
    base: ScrapedPage,
    url: str,
    enabled: bool,
    user_agent: str,
    timeout: float,
) -> ScrapedPage:
    if not enabled:
        return base
    try:
        html = _render_with_playwright(url, user_agent=user_agent, timeout_ms=int(timeout * 1000))
    except Exception as exc:
        logger.warning("playwright fallback failed: %s", exc)
        base.error = (base.error + " | " if base.error else "") + f"playwright: {exc}"
        return base
    parsed = parse_page_html(html, url)
    parsed.used_fallback = True
    if base.error:
        parsed.error = base.error
    return parsed


def _render_with_playwright(url: str, *, user_agent: str, timeout_ms: int) -> str:
    from playwright.sync_api import sync_playwright  # imported lazily

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        try:
            ctx = browser.new_context(user_agent=user_agent)
            page = ctx.new_page()
            page.goto(url, timeout=timeout_ms, wait_until="domcontentloaded")
            html = page.content()
        finally:
            browser.close()
    return html


# ---------------------------------------------------------------------------
# Test-mode loader
# ---------------------------------------------------------------------------

def _utc(ts: str | None) -> datetime | None:
    if not ts:
        return None
    try:
        return datetime.fromisoformat(ts).astimezone(timezone.utc)
    except ValueError:
        return None


def load_sample_scrape(page_url: str, sample: dict) -> ScrapedPage:
    """Materialize a ScrapedPage from a test-mode sample_data entry."""
    page = ScrapedPage(page_url=page_url, page_name=sample.get("page_name"))
    for p in sample.get("posts", []):
        text = p.get("description", "")
        amazon_urls = list(dict.fromkeys(p.get("amazon_urls", []) or find_amazon_urls(text)))
        page.posts.append(
            ScrapedPost(
                source_post_url=p["source_post_url"],
                description=text,
                image_url=p.get("image_url"),
                post_time=_utc(p.get("post_time")),
                amazon_urls=amazon_urls,
            )
        )
    return page
