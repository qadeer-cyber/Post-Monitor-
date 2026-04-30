"""Duplicate detection helpers.

A post is a duplicate if any of the following matches an existing row:
  * source_post_url
  * ASIN
  * caption hash (sha256 over normalized caption)
  * image perceptual hash (phash from Pillow + imagehash when available)
"""

from __future__ import annotations

import hashlib
import io
import re

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from .. import models


def caption_hash(caption: str) -> str:
    normalized = re.sub(r"\s+", " ", (caption or "").strip().lower())
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def image_hash_from_bytes(data: bytes) -> str | None:
    """Perceptual hash of image bytes. Returns None on any failure."""
    if not data:
        return None
    try:
        import imagehash
        from PIL import Image

        img = Image.open(io.BytesIO(data))
        return str(imagehash.phash(img))
    except Exception:
        # Fall back to a plain sha1 content hash so we still dedup exact copies.
        return "sha1:" + hashlib.sha1(data).hexdigest()


def find_duplicate(
    db: Session,
    *,
    source_post_url: str | None,
    asin: str | None,
    caption_hash_value: str | None,
    image_hash_value: str | None,
) -> models.Post | None:
    conds = []
    if source_post_url:
        conds.append(models.Post.source_post_url == source_post_url)
    if asin:
        conds.append(models.Post.asin == asin)
    if caption_hash_value:
        conds.append(models.Post.caption_hash == caption_hash_value)
    if image_hash_value:
        conds.append(models.Post.image_hash == image_hash_value)
    if not conds:
        return None
    stmt = select(models.Post).where(or_(*conds)).limit(1)
    return db.execute(stmt).scalar_one_or_none()
