from __future__ import annotations

import struct
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import main as app_main  # noqa: E402
from app.model import ModelState, Transcription  # noqa: E402


class FakeTranscriber:
    """Stands in for faster-whisper so the HTTP contract can be tested without
    a 100 MB wheel or a model on disk."""

    def __init__(self, text: str = "ha dieu hoa xuong 22 do", confidence: float = 0.94):
        self.text = text
        self.confidence = confidence
        self.calls: list[tuple[int, int]] = []  # (sample_count, sample_rate)
        self.raises: Exception | None = None

    def transcribe(self, samples, sample_rate):
        self.calls.append((len(samples), sample_rate))
        if self.raises is not None:
            raise self.raises
        return Transcription(
            text=self.text,
            confidence=self.confidence,
            language="vi",
            segment_count=1,
        )


def pcm16(sample_count: int, value: int = 1000) -> bytes:
    """`sample_count` samples of a constant value, little-endian."""
    return struct.pack(f"<{sample_count}h", *([value] * sample_count))


@pytest.fixture
def fake_transcriber():
    return FakeTranscriber()


@pytest.fixture
def client(fake_transcriber):
    """TestClient with a ready fake model."""
    from fastapi.testclient import TestClient

    app_main.holder.set_ready(fake_transcriber)
    with TestClient(app_main.app) as c:
        yield c


@pytest.fixture
def loading_client():
    """TestClient whose model never becomes ready (startup / crashed model)."""
    from fastapi.testclient import TestClient

    holder = app_main.holder
    with holder._lock:  # noqa: SLF001 — deliberate: no public "unready" setter
        holder._transcriber = None
        holder._state = ModelState.LOADING
        holder._error = None
    # Do not enter the lifespan: we want the state frozen at LOADING.
    return TestClient(app_main.app)
