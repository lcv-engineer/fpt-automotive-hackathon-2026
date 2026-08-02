from __future__ import annotations

import struct

import pytest

from app.audio import InvalidAudioError, duration_ms, pcm16le_to_float32
from app.model import segments_to_confidence


def test_decodes_little_endian_pcm16():
    body = struct.pack("<4h", 0, 16384, -16384, -32768)
    assert pcm16le_to_float32(body) == pytest.approx([0.0, 0.5, -0.5, -1.0])


def test_full_scale_negative_stays_within_range():
    # -32768 / 32767 would be < -1.0; the model expects [-1, 1].
    assert min(pcm16le_to_float32(struct.pack("<h", -32768))) >= -1.0


def test_odd_length_body_is_rejected():
    with pytest.raises(InvalidAudioError):
        pcm16le_to_float32(b"\x01\x02\x03")


def test_empty_body_is_rejected():
    with pytest.raises(InvalidAudioError):
        pcm16le_to_float32(b"")


def test_duration_ms_matches_sample_rate():
    assert duration_ms(16000, 16000) == pytest.approx(1000.0)
    assert duration_ms(8000, 16000) == pytest.approx(500.0)


def test_confidence_is_duration_weighted():
    # A 4 s confident segment must dominate a 0.2 s bad one.
    conf = segments_to_confidence([(-0.05, 4.0), (-3.0, 0.2)])
    assert 0.85 < conf < 1.0


def test_confidence_of_no_segments_is_zero():
    assert segments_to_confidence([]) == 0.0


def test_confidence_is_clamped_to_unit_interval():
    # avg_logprob > 0 should never happen, but a clamp is cheaper than a
    # 500 from pydantic's ge/le validation in front of the driver.
    assert segments_to_confidence([(0.5, 1.0)]) == 1.0
