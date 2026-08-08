#!/usr/bin/env python3
"""Validate a hand-recorded corpus before it is allowed near a benchmark.

Recording at 16 kHz directly is the whole point: it removes the resampling
step, and with it the second limitation printed on every number the old TTS
corpus produced. A file that sneaks in at 44.1 kHz would quietly put that
limitation back.

    python scripts/check_corpus.py --dir ../evidence/asr/corpus-human/raw \\
                                   --prompts scripts/corpus_prompts.tsv

Stdlib only, matching the rest of asr/scripts.
"""

from __future__ import annotations

import argparse
import csv
import sys
import wave
from pathlib import Path

REQUIRED_RATE = 16000
REQUIRED_CHANNELS = 1
REQUIRED_SAMPWIDTH = 2  # bytes, i.e. PCM16
MIN_DURATION_MS = 300


def validate_wav(path: Path) -> tuple[bool, str]:
    """Return (ok, reason). `reason` is empty when ok."""
    try:
        with wave.open(str(path), "rb") as wav:
            rate = wav.getframerate()
            channels = wav.getnchannels()
            sampwidth = wav.getsampwidth()
            frames = wav.getnframes()
    except wave.Error as exc:
        return False, f"khong doc duoc WAV: {exc}"

    if rate != REQUIRED_RATE:
        return False, f"sample rate {rate}, can {REQUIRED_RATE}"
    if channels != REQUIRED_CHANNELS:
        return False, f"co {channels} kenh, can mono"
    if sampwidth != REQUIRED_SAMPWIDTH:
        return False, f"sampwidth {sampwidth * 8} bit, can 16 bit"
    duration_ms = frames / rate * 1000.0
    if duration_ms < MIN_DURATION_MS:
        return False, f"clip qua ngan: {duration_ms:.0f} ms < {MIN_DURATION_MS} ms"
    return True, ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dir", required=True, help="thu muc chua WAV da thu")
    parser.add_argument("--prompts", default="scripts/corpus_prompts.tsv")
    args = parser.parse_args()

    prompts: dict[str, str] = {}
    with open(args.prompts, encoding="utf-8") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            name = (row.get("raw_name") or "").strip()
            text = (row.get("text_vi") or "").strip()
            if name and text:
                prompts[name] = text

    directory = Path(args.dir)
    failures = 0
    missing = []
    for name in sorted(prompts):
        path = directory / f"{name}.wav"
        if not path.exists():
            missing.append(name)
            continue
        ok, reason = validate_wav(path)
        status = "OK  " if ok else "LOI "
        if not ok:
            failures += 1
        print(f"  {status}{name:<24} {reason}")

    print()
    print(f"cau trong prompts : {len(prompts)}")
    print(f"file da thu       : {len(prompts) - len(missing)}")
    print(f"file loi dinh dang: {failures}")
    if missing:
        print(f"CHUA THU          : {', '.join(missing)}")
    if failures or missing:
        print()
        print("Corpus CHUA dung duoc. Thu lai o 16 kHz mono 16-bit roi chay lai.")
        return 1
    print()
    print("Corpus hop le. Buoc tiep: sinh 3 muc nhieu bang noise_mix.py")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
