from __future__ import annotations

from app import main as app_main

from .conftest import pcm16

ONE_SECOND = 16000  # samples at 16 kHz


def _post(client, body: bytes, sample_rate: str | None = "16000", trace_id: str = "t-1"):
    headers = {"Content-Type": "application/octet-stream", "X-Trace-Id": trace_id}
    if sample_rate is not None:
        headers["X-Sample-Rate"] = sample_rate
    return client.post("/asr", content=body, headers=headers)


def test_happy_path_returns_exactly_the_contract_fields(client, fake_transcriber):
    resp = _post(client, pcm16(ONE_SECOND))
    assert resp.status_code == 200
    body = resp.json()
    assert set(body) == {"text", "confidence", "server_ms"}
    assert body["text"] == fake_transcriber.text
    assert body["confidence"] == 0.94
    assert isinstance(body["server_ms"], int) and body["server_ms"] >= 0
    assert resp.headers["X-Trace-Id"] == "t-1"
    # The decoded sample count must reach the model unchanged.
    assert fake_transcriber.calls[-1] == (ONE_SECOND, 16000)


def test_missing_sample_rate_header_is_400(client):
    resp = _post(client, pcm16(ONE_SECOND), sample_rate=None)
    assert resp.status_code == 400
    assert "X-Sample-Rate" in resp.json()["error"]


def test_wrong_sample_rate_is_rejected_not_resampled(client, fake_transcriber):
    resp = _post(client, pcm16(ONE_SECOND), sample_rate="48000")
    assert resp.status_code == 400
    assert fake_transcriber.calls == []  # never reached the model


def test_non_numeric_sample_rate_is_400(client):
    assert _post(client, pcm16(ONE_SECOND), sample_rate="16k").status_code == 400


def test_odd_length_body_is_400(client):
    resp = _post(client, b"\x00\x01\x02")
    assert resp.status_code == 400
    assert resp.json()["error"] == "invalid audio"


def test_empty_body_is_400(client):
    assert _post(client, b"").status_code == 400


def test_oversized_body_is_413(client, fake_transcriber):
    too_many = app_main.settings.max_body_bytes // 2 + 1
    resp = _post(client, pcm16(too_many))
    assert resp.status_code == 413
    assert fake_transcriber.calls == []


def test_too_short_audio_returns_empty_text_not_a_hallucination(client, fake_transcriber):
    # 100 ms — below ASR_MIN_AUDIO_MS. Whisper would invent a sentence here.
    resp = _post(client, pcm16(1600))
    assert resp.status_code == 200
    assert resp.json() == {"text": "", "confidence": 0.0, "server_ms": 0}
    assert fake_transcriber.calls == []


def test_model_failure_is_500_and_does_not_kill_the_service(client, fake_transcriber):
    fake_transcriber.raises = RuntimeError("ct2 exploded")
    assert _post(client, pcm16(ONE_SECOND)).status_code == 500

    fake_transcriber.raises = None
    assert _post(client, pcm16(ONE_SECOND)).status_code == 200


def test_asr_is_503_while_model_is_loading(loading_client):
    resp = _post(loading_client, pcm16(ONE_SECOND))
    assert resp.status_code == 503
    assert resp.headers["Retry-After"] == "5"
