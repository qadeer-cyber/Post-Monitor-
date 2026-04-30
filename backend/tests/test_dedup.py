from app.services.dedup import caption_hash, image_hash_from_bytes


def test_caption_hash_normalizes_whitespace_and_case():
    a = caption_hash("Hello   World\n#ad")
    b = caption_hash("hello world #ad")
    assert a == b


def test_caption_hash_differs_for_different_content():
    assert caption_hash("one") != caption_hash("two")


def test_image_hash_handles_garbage_without_crash():
    # Pillow will fail -> sha1 fallback
    out = image_hash_from_bytes(b"not-a-real-image")
    assert out is not None
    assert out.startswith("sha1:")


def test_image_hash_none_on_empty():
    assert image_hash_from_bytes(b"") is None
