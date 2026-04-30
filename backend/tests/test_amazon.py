from __future__ import annotations

from app.services.amazon import (
    build_affiliate_url,
    detect_marketplace,
    extract_asin,
    find_amazon_urls,
    is_amazon_host,
    is_short_link,
)


def test_find_amazon_urls_in_text():
    text = (
        "check this out https://www.amazon.com/dp/B09ABC1234?tag=x "
        "and a short one https://amzn.to/3XamplE "
        "(and a non-amazon https://example.com)"
    )
    urls = find_amazon_urls(text)
    assert "https://www.amazon.com/dp/B09ABC1234?tag=x" in urls
    assert "https://amzn.to/3XamplE" in urls
    assert all("example.com" not in u for u in urls)


def test_is_amazon_host_variants():
    assert is_amazon_host("https://www.amazon.com/dp/B01N4M")
    assert is_amazon_host("https://amazon.co.uk/gp/product/B0ABCDEFGH")
    assert is_amazon_host("https://amzn.to/abc")
    assert not is_amazon_host("https://example.com/amazon")


def test_is_short_link():
    assert is_short_link("https://amzn.to/3XamplE")
    assert not is_short_link("https://www.amazon.com/dp/B01ABCDEFG")


def test_extract_asin_dp():
    assert extract_asin("https://www.amazon.com/dp/B09ABC1234") == "B09ABC1234"


def test_extract_asin_gp_product():
    assert extract_asin("https://www.amazon.co.uk/gp/product/B08XYZ0987/") == "B08XYZ0987"


def test_extract_asin_product_path():
    assert extract_asin("https://www.amazon.in/product/B07PRODUCT/ref=nav_bb") == "B07PRODUCT"


def test_extract_asin_none_when_missing():
    assert extract_asin("https://www.amazon.com/s?k=laptops") is None


def test_detect_marketplace_preserves_domain():
    assert detect_marketplace("https://www.amazon.co.uk/dp/X") == "amazon.co.uk"
    assert detect_marketplace("https://amazon.ae/dp/X") == "amazon.ae"


def test_build_affiliate_url_format():
    u = build_affiliate_url("amazon.co.uk", "B09ABC1234", "mytag-21")
    assert u == "https://www.amazon.co.uk/dp/B09ABC1234?tag=mytag-21"


def test_parse_amazon_link_rewrites_tag(monkeypatch):
    from app.services import amazon as amz

    parsed = amz.parse_amazon_link(
        "https://www.amazon.com/dp/B0ABCDEFGH?tag=someone-else-20&ref_=nav",
        associate_tag="mine-20",
    )
    assert parsed is not None
    assert parsed.asin == "B0ABCDEFGH"
    assert parsed.marketplace == "amazon.com"
    assert parsed.affiliate_url == "https://www.amazon.com/dp/B0ABCDEFGH?tag=mine-20"
    # existing tag was stripped
    assert "someone-else-20" not in parsed.normalized_url


def test_parse_amazon_short_link_resolves(monkeypatch):
    from app.services import amazon as amz

    def fake_resolve(url, client=None, timeout=10.0):
        return "https://www.amazon.com/dp/B09SHORT12"

    monkeypatch.setattr(amz, "resolve_short_link", fake_resolve)
    parsed = amz.parse_amazon_link("https://amzn.to/abc", associate_tag="t-20")
    assert parsed is not None
    assert parsed.asin == "B09SHORT12"
    assert parsed.affiliate_url == "https://www.amazon.com/dp/B09SHORT12?tag=t-20"


def test_parse_amazon_link_returns_none_without_asin():
    from app.services import amazon as amz

    assert amz.parse_amazon_link("https://www.amazon.com/s?k=toys", associate_tag="t-20") is None
