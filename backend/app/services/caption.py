"""Build the final ready-to-copy caption exactly per spec."""

from __future__ import annotations

FTC_LINE = "As an Amazon Associate, I earn from qualifying purchases."


def build_caption(original_description: str, affiliate_link: str) -> str:
    """Spec-exact caption format.

    {original_description}

    #ad
    As an Amazon Associate, I earn from qualifying purchases.

    Buy here: {affiliate_link}
    """
    desc = (original_description or "").strip()
    link = (affiliate_link or "").strip()
    parts: list[str] = []
    if desc:
        parts.append(desc)
    parts.append("")
    parts.append("#ad")
    parts.append(FTC_LINE)
    parts.append("")
    parts.append(f"Buy here: {link}")
    return "\n".join(parts).strip() + "\n"
