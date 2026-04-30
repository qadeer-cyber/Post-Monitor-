"""Amazon link detection, ASIN extraction, short-link resolution and
affiliate-tag rewriting.

The goal is to be forgiving about real-world messy URLs (query strings,
tracking params, locale sub-paths, etc.) while preserving the correct
marketplace so the user's associate tag stays on the right regional store.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from urllib.parse import parse_qsl, quote, urlencode, urlparse, urlunparse

import httpx

SUPPORTED_DOMAINS: tuple[str, ...] = (
    "amazon.com",
    "amazon.co.uk",
    "amazon.in",
    "amazon.ae",
    "amazon.ca",
    "amazon.com.au",
    "amazon.de",
    "amazon.fr",
    "amazon.it",
    "amazon.es",
    "amazon.co.jp",
    "amazon.com.mx",
    "amazon.com.br",
    "amazon.sg",
    "amazon.nl",
    "amazon.se",
    "amazon.pl",
)

SHORT_DOMAINS: tuple[str, ...] = ("amzn.to", "amzn.eu", "a.co")

# Primary ASIN patterns per spec:  /dp/{ASIN}, /gp/product/{ASIN}, /product/{ASIN}
ASIN_PATH_RE = re.compile(
    r"/(?:dp|gp/product|gp/aw/d|product|exec/obidos/asin|o/ASIN)/([A-Z0-9]{10})(?:[/?]|$)",
    re.IGNORECASE,
)
# Fallback: bare /ASIN/XXXXXXXXXX/
ASIN_ALT_RE = re.compile(r"/ASIN/([A-Z0-9]{10})(?:[/?]|$)", re.IGNORECASE)
# A standalone 10-char ASIN in a query string (e.g. ?asin=...)
ASIN_QUERY_RE = re.compile(r"(?:^|[?&])asin=([A-Z0-9]{10})", re.IGNORECASE)

# Very permissive URL finder — we post-filter by host.
URL_RE = re.compile(r"https?://[^\s<>\"')]+", re.IGNORECASE)


@dataclass(frozen=True)
class ParsedAmazonLink:
    original_url: str
    normalized_url: str
    affiliate_url: str
    asin: str
    marketplace: str  # e.g. "amazon.com"


def _host(url: str) -> str:
    try:
        netloc = urlparse(url).netloc.lower()
    except Exception:
        return ""
    if netloc.startswith("www."):
        netloc = netloc[4:]
    # strip port
    return netloc.split(":", 1)[0]


def is_amazon_host(url: str) -> bool:
    host = _host(url)
    return host in SUPPORTED_DOMAINS or host in SHORT_DOMAINS


def is_short_link(url: str) -> bool:
    return _host(url) in SHORT_DOMAINS


def find_amazon_urls(text: str) -> list[str]:
    """Return deduped Amazon URLs (incl. short links) from arbitrary text."""
    seen: set[str] = set()
    out: list[str] = []
    for m in URL_RE.finditer(text or ""):
        raw = m.group(0).rstrip(").,;!?\"'")
        if not is_amazon_host(raw):
            continue
        if raw in seen:
            continue
        seen.add(raw)
        out.append(raw)
    return out


def extract_asin(url: str) -> str | None:
    """Best-effort ASIN extraction. Returns None if not found."""
    if not url:
        return None
    parsed = urlparse(url)
    path = parsed.path or ""
    m = ASIN_PATH_RE.search(path)
    if m:
        return m.group(1).upper()
    m = ASIN_ALT_RE.search(path)
    if m:
        return m.group(1).upper()
    m = ASIN_QUERY_RE.search(parsed.query or "")
    if m:
        return m.group(1).upper()
    return None


def detect_marketplace(url: str) -> str:
    host = _host(url)
    if host in SUPPORTED_DOMAINS:
        return host
    # Unknown host -> fall back to amazon.com so we still produce a usable link.
    return "amazon.com"


def build_affiliate_url(marketplace: str, asin: str, tag: str) -> str:
    """Build a clean canonical affiliate URL:  https://{marketplace}/dp/{ASIN}?tag={tag}"""
    asin_safe = quote(asin, safe="")
    tag_safe = quote(tag, safe="")
    return f"https://www.{marketplace}/dp/{asin_safe}?tag={tag_safe}"


def _strip_tracking(url: str) -> str:
    """Remove common tracking params but keep the path intact."""
    parsed = urlparse(url)
    drop_prefixes = ("utm_", "pf_rd_", "pd_rd_", "ref_")
    drop_exact = {"ref", "tag", "linkCode", "linkId", "ascsubtag", "ascsubtagreserved"}
    kept = [
        (k, v)
        for k, v in parse_qsl(parsed.query, keep_blank_values=False)
        if not any(k.startswith(p) for p in drop_prefixes) and k not in drop_exact
    ]
    return urlunparse(parsed._replace(query=urlencode(kept)))


def resolve_short_link(url: str, *, client: httpx.Client | None = None, timeout: float = 10.0) -> str:
    """Follow HEAD redirects on amzn.to / a.co short links. Returns the final URL.

    If the network call fails the original URL is returned — the caller decides
    whether that's an error.
    """
    if not is_short_link(url):
        return url
    owns_client = client is None
    if client is None:
        client = httpx.Client(follow_redirects=True, timeout=timeout)
    try:
        try:
            resp = client.head(url)
            final = str(resp.url)
        except httpx.HTTPError:
            resp = client.get(url)
            final = str(resp.url)
        return final
    finally:
        if owns_client:
            client.close()


def parse_amazon_link(
    url: str,
    *,
    associate_tag: str,
    client: httpx.Client | None = None,
) -> ParsedAmazonLink | None:
    """Full pipeline: resolve short links -> extract ASIN -> build affiliate URL.

    Returns None only when no ASIN can be determined.
    """
    if not url:
        return None
    resolved = resolve_short_link(url, client=client) if is_short_link(url) else url
    asin = extract_asin(resolved)
    if not asin:
        return None
    marketplace = detect_marketplace(resolved)
    normalized = _strip_tracking(resolved)
    affiliate = build_affiliate_url(marketplace, asin, associate_tag)
    return ParsedAmazonLink(
        original_url=url,
        normalized_url=normalized,
        affiliate_url=affiliate,
        asin=asin,
        marketplace=marketplace,
    )
