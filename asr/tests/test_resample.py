"""Resampling correctness, stated as the two properties that matter.

A resampler is only useful here if it (a) keeps in-band content intact and
(b) removes content above the destination Nyquist instead of folding it back
into the speech band. Linear interpolation passes (a) and fails (b).
"""

from __future__ import annotations

import array
import cmath
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))

from resample import resample_sinc  # noqa: E402


def tone(freq_hz: float, rate: int, seconds: float, amplitude: int = 12000) -> array.array:
    n = int(rate * seconds)
    return array.array(
        "h",
        (int(amplitude * math.sin(2.0 * math.pi * freq_hz * i / rate)) for i in range(n)),
    )


def magnitude_at(samples: array.array, rate: int, freq_hz: float) -> float:
    """One DFT bin, computed directly. Stdlib only — no numpy in this tree."""
    n = len(samples)
    acc = sum(s * cmath.exp(-2j * math.pi * freq_hz * i / rate) for i, s in enumerate(samples))
    return abs(acc) / n


def test_in_band_tone_survives():
    src = tone(1000.0, 22050, 0.5)
    out = resample_sinc(src, 22050, 16000)

    assert abs(len(out) - int(0.5 * 16000)) <= 2
    # Amplitude 12000 sine -> single-sided bin magnitude ~6000.
    assert magnitude_at(out, 16000, 1000.0) > 4000.0


def test_tone_above_destination_nyquist_is_rejected():
    """9 kHz cannot exist in a 16 kHz signal (Nyquist 8 kHz).

    Linear interpolation folds it down to 7 kHz at near-full amplitude. A
    filtered resampler must leave almost nothing behind.
    """
    src = tone(9000.0, 22050, 0.5)
    out = resample_sinc(src, 22050, 16000)

    folded = magnitude_at(out, 16000, 7000.0)
    assert folded < 600.0, f"aliased image too strong: {folded}"


def test_same_rate_is_identity():
    src = tone(1000.0, 16000, 0.1)
    assert resample_sinc(src, 16000, 16000) is src


def test_output_stays_in_int16_range():
    src = tone(1000.0, 22050, 0.2, amplitude=32700)
    out = resample_sinc(src, 22050, 16000)
    assert all(-32768 <= s <= 32767 for s in out)
