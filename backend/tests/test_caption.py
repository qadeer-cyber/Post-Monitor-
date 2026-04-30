from app.services.caption import FTC_LINE, build_caption


def test_caption_includes_all_required_parts():
    cap = build_caption("Great deal!", "https://www.amazon.com/dp/X?tag=me-20")
    assert "Great deal!" in cap
    assert "#ad" in cap
    assert FTC_LINE in cap
    assert "Buy here: https://www.amazon.com/dp/X?tag=me-20" in cap


def test_caption_ordering():
    cap = build_caption("Desc here", "https://a/")
    # description first, then #ad, then FTC, then Buy here last
    i_desc = cap.index("Desc here")
    i_ad = cap.index("#ad")
    i_ftc = cap.index(FTC_LINE)
    i_buy = cap.index("Buy here:")
    assert i_desc < i_ad < i_ftc < i_buy


def test_caption_empty_description_still_valid():
    cap = build_caption("", "https://a/")
    assert cap.startswith("#ad")
    assert "Buy here: https://a/" in cap
