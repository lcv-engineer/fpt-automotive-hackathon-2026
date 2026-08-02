"""PCM decoding — kept separate from the model so it is testable without
faster-whisper (and therefore without a 100 MB wheel) installed.
"""

from __future__ import annotations

import array
import sys


class InvalidAudioError(ValueError):
    """Raised when the request body is not decodable PCM16 LE mono."""


def pcm16le_to_float32(body: bytes) -> list[float]:
    """Decode raw 16-bit little-endian mono PCM into floats in [-1, 1].

    Returns a plain list; `model.py` converts it to a numpy array only where
    numpy is actually present. That keeps the HTTP layer's tests dependency
    free.
    """
    if not body:
        raise InvalidAudioError("empty body: expected raw PCM16 LE mono audio")
    if len(body) % 2 != 0:
        raise InvalidAudioError(
            f"body length {len(body)} is odd; PCM16 samples are 2 bytes each"
        )

    samples = array.array("h")
    samples.frombytes(body)
    if sys.byteorder == "big":
        samples.byteswap()

    # 32768 (not 32767): PCM16 is asymmetric, -32768 is a valid sample and
    # dividing by 32767 would push it just past -1.0.
    return [s / 32768.0 for s in samples]


def duration_ms(sample_count: int, sample_rate: int) -> float:
    if sample_rate <= 0:
        raise InvalidAudioError(f"sample rate must be positive, got {sample_rate}")
    return sample_count * 1000.0 / sample_rate
