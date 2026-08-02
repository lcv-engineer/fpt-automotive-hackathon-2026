from __future__ import annotations

from app import main as app_main


def test_health_ok_reports_model_name(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    body = resp.json()
    # 03-contracts.md §2 pins these two field names.
    assert body["status"] == "ok"
    assert body["model"] == app_main.settings.model_name


def test_health_is_503_while_model_is_loading(loading_client):
    resp = loading_client.get("/health")
    assert resp.status_code == 503
    assert resp.json()["status"] == "loading"
    assert resp.headers["Retry-After"] == "5"
