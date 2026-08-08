from __future__ import annotations

import sys
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

from check_corpus import validate_wav  # noqa: E402


def write_wav(path: Path, rate: int, channels: int, sampwidth: int, frames: int = 16000):
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(channels)
        wav.setsampwidth(sampwidth)
        wav.setframerate(rate)
        wav.writeframes(b"\x00\x01" * (frames * channels * (sampwidth // 2)))


def test_accepts_16k_mono_16bit(tmp_path):
    path = tmp_path / "ok.wav"
    write_wav(path, 16000, 1, 2)
    ok, reason = validate_wav(path)
    assert ok, reason


def test_rejects_wrong_sample_rate(tmp_path):
    path = tmp_path / "bad_rate.wav"
    write_wav(path, 44100, 1, 2)
    ok, reason = validate_wav(path)
    assert not ok
    assert "44100" in reason


def test_rejects_stereo(tmp_path):
    path = tmp_path / "stereo.wav"
    write_wav(path, 16000, 2, 2)
    ok, reason = validate_wav(path)
    assert not ok
    assert "mono" in reason.lower()


def test_rejects_clip_shorter_than_min(tmp_path):
    path = tmp_path / "tiny.wav"
    write_wav(path, 16000, 1, 2, frames=800)  # 50 ms
    ok, reason = validate_wav(path)
    assert not ok
    assert "ngan" in reason.lower() or "short" in reason.lower()
