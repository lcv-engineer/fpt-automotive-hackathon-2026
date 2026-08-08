#!/usr/bin/env python3
"""Windowed-sinc resampling, stdlib only.

Why this exists: the bench used to resample 22.05 kHz -> 16 kHz by linear
interpolation. That has no anti-alias filter, so everything above 8 kHz in the
source folds back down into the speech band — corrupting exactly the region
Vietnamese consonants live in, and making every WER number downstream a
measurement of the resampler as much as of the model.

No numpy: `bench_tts_samples.py` and `noise_mix.py` are documented as runnable
next to the container without a venv, and that property is worth more than the
speed a vectorised filter would buy on 36 short clips.
"""

from __future__ import annotations

import array
import math

# Taps either side of the centre. 16 puts the first sidelobe far enough down
# for a benchmark; more taps buy accuracy nobody here can measure.
HALF_WIDTH = 16


def _blackman(x: float) -> float:
    """Blackman window over x in [-1, 1]."""
    t = (x + 1.0) * 0.5
    return 0.42 - 0.5 * math.cos(2.0 * math.pi * t) + 0.08 * math.cos(4.0 * math.pi * t)


def _sinc(x: float) -> float:
    if x == 0.0:
        return 1.0
    pix = math.pi * x
    return math.sin(pix) / pix


def resample_sinc(samples: array.array, src_rate: int, dst_rate: int) -> array.array:
    """Resample mono int16 `samples` from `src_rate` to `dst_rate`.

    When downsampling, the sinc is stretched to cut at the destination Nyquist
    so the filter both interpolates and anti-aliases in one pass.
    """
    if src_rate == dst_rate:
        return samples

    ratio = src_rate / dst_rate
    # Downsampling -> lower the cutoff to the destination Nyquist.
    cutoff = min(1.0, dst_rate / src_rate)
    n_in = len(samples)
    n_out = int(n_in / ratio)
    # Widen the kernel by the same factor the cutoff narrowed, so the number of
    # non-negligible taps stays constant.
    half = max(1, int(HALF_WIDTH / cutoff))

    out = array.array("h")
    for i in range(n_out):
        centre = i * ratio
        left = int(math.floor(centre)) - half + 1
        right = int(math.floor(centre)) + half

        acc = 0.0
        norm = 0.0
        for k in range(left, right + 1):
            if k < 0 or k >= n_in:
                continue
            offset = centre - k
            window = _blackman(offset / half)
            weight = cutoff * _sinc(cutoff * offset) * window
            acc += samples[k] * weight
            norm += weight
        value = acc / norm if norm != 0.0 else 0.0
        out.append(int(max(-32768, min(32767, round(value)))))
    return out
