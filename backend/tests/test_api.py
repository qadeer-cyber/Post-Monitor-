from __future__ import annotations


def test_health(client):
    r = client.get("/api/health")
    assert r.status_code == 200
    data = r.json()
    assert data["ok"] is True
    assert data["test_mode"] is True


def test_source_crud(client):
    r = client.post("/api/sources", json={"url": "https://www.facebook.com/SampleDealsPage1"})
    assert r.status_code == 201, r.text
    sid = r.json()["id"]

    # duplicate
    r = client.post("/api/sources", json={"url": "https://www.facebook.com/SampleDealsPage1"})
    assert r.status_code == 409

    r = client.get("/api/sources")
    assert r.status_code == 200
    assert len(r.json()) == 1

    r = client.patch(f"/api/sources/{sid}", json={"enabled": False})
    assert r.status_code == 200
    assert r.json()["enabled"] is False

    r = client.patch(f"/api/sources/{sid}", json={"enabled": True, "name": "Renamed"})
    assert r.status_code == 200
    assert r.json()["name"] == "Renamed"

    r = client.delete(f"/api/sources/{sid}")
    assert r.status_code == 204
    r = client.get("/api/sources")
    assert r.json() == []


def test_scan_in_test_mode_imports_from_samples(client):
    client.post("/api/sources", json={"url": "https://www.facebook.com/SampleDealsPage1"})
    client.post("/api/sources", json={"url": "https://www.facebook.com/SampleDealsPage2"})

    r = client.post("/api/scan")
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["ok"] is True
    # SampleDealsPage1 has 2 posts (second one uses amzn.to -> no ASIN -> 1 fail),
    # SampleDealsPage2 has 1 post. So 2 imports, 1 failed.
    assert body["posts_imported"] >= 1

    queue = client.get("/api/queue").json()
    assert len(queue) >= 1
    first = queue[0]
    assert first["status"] == "queue"
    assert first["affiliate_url"].endswith("?tag=test-tag-20")
    assert "#ad" in first["final_caption"]
    assert "Buy here:" in first["final_caption"]


def test_dashboard_and_logs(client):
    client.post("/api/sources", json={"url": "https://www.facebook.com/SampleDealsPage1"})
    client.post("/api/scan")
    d = client.get("/api/dashboard").json()
    assert d["total_monitored_pages"] == 1
    assert d["ready_posts"] >= 1
    logs = client.get("/api/logs").json()
    assert any(l["category"] == "import" for l in logs)


def test_mark_posted_and_reject(client):
    client.post("/api/sources", json={"url": "https://www.facebook.com/SampleDealsPage1"})
    client.post("/api/scan")
    queue = client.get("/api/queue").json()
    assert queue
    pid = queue[0]["id"]

    r = client.post(f"/api/posts/{pid}/mark-posted")
    assert r.status_code == 200
    assert r.json()["status"] == "posted"

    # now reject another if exists
    queue2 = client.get("/api/queue").json()
    if queue2:
        pid2 = queue2[0]["id"]
        r = client.post(f"/api/posts/{pid2}/reject")
        assert r.status_code == 200
        assert r.json()["status"] == "rejected"

    posted = client.get("/api/posted").json()
    assert any(p["id"] == pid for p in posted)


def test_rescanning_same_source_produces_duplicates_skipped(client):
    client.post("/api/sources", json={"url": "https://www.facebook.com/SampleDealsPage1"})
    first = client.post("/api/scan").json()
    second = client.post("/api/scan").json()
    assert second["duplicates_skipped"] >= first["posts_imported"] - 0


def test_settings_patch(client):
    r = client.patch("/api/settings", json={"amazon_associate_tag": "newtag-21", "scan_interval_minutes": 45})
    assert r.status_code == 200
    data = r.json()
    assert data["amazon_associate_tag"] == "newtag-21"
    assert data["scan_interval_minutes"] == 45
