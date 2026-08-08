#!/usr/bin/env python3
"""Smoke-benchmark viva-asr with the Vietnamese TTS clips already in the repo.

Why these clips: `android/voice/src/main/res/raw/tts_*.wav` are the pre-rendered
fallback prompts, and `android/voice/scripts/tts_prompts.tsv` gives the exact
sentence for each one — so there is ground truth to compare against without
recording anything new.

    python scripts/bench_tts_samples.py --url http://127.0.0.1:8080 --out bench.csv

⚠️ Read the numbers for what they are:

  * The clips are **synthesised speech**, not a driver in a cabin. Word error
    here is a floor, not a prediction of real-world accuracy.
  * They are 22.05 kHz and the service accepts only 16 kHz, so this script
    resamples with a windowed-sinc filter (`resample.py`). That removes the
    aliasing the old linear interpolation introduced — but the clips are still
    synthesised speech, so accuracy here remains a floor, not a prediction.
  * RTF is measured on whatever CPU this runs on. A CarSky container node is a
    different machine; do not quote this number as the platform's latency.

Stdlib only, so it runs next to the container without a venv.
"""

from __future__ import annotations

import argparse
import array
import csv
import json
import re
import statistics
import sys
import time
import unicodedata
import urllib.error
import urllib.request
import uuid
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from resample import resample_sinc  # noqa: E402

TARGET_RATE = 16000


def load_ground_truth(tsv: Path) -> dict[str, str]:
    truth: dict[str, str] = {}
    with tsv.open(encoding="utf-8") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        for row in reader:
            name = (row.get("raw_name") or "").strip()
            text = (row.get("text_vi") or "").strip()
            if name and text:
                truth[name] = text
    return truth


def read_wav_mono16(path: Path) -> tuple[array.array, int]:
    with wave.open(str(path), "rb") as wav:
        if wav.getnchannels() != 1 or wav.getsampwidth() != 2:
            raise ValueError(f"{path.name}: need mono 16-bit, got "
                             f"{wav.getnchannels()}ch/{wav.getsampwidth() * 8}-bit")
        frames = wav.readframes(wav.getnframes())
        samples = array.array("h")
        samples.frombytes(frames)
        if sys.byteorder == "big":
            samples.byteswap()
        return samples, wav.getframerate()


def resample_linear(samples: array.array, src_rate: int, dst_rate: int) -> array.array:
    """Kept under the old name because `noise_mix.py` imports it.

    No longer linear: it delegates to the windowed-sinc filter. The old
    interpolation had no anti-alias stage and folded everything above 8 kHz
    back into the speech band.
    """
    return resample_sinc(samples, src_rate, dst_rate)


def normalize(text: str) -> list[str]:
    """Fold case, punctuation and unicode form so WER measures words, not typography."""
    folded = unicodedata.normalize("NFC", text.lower())
    folded = re.sub(r"[^\w\s]", " ", folded, flags=re.UNICODE)
    return folded.split()


def word_error_rate(reference: str, hypothesis: str) -> float:
    """Standard Levenshtein WER over whitespace tokens."""
    ref, hyp = normalize(reference), normalize(hypothesis)
    if not ref:
        return 0.0 if not hyp else 1.0
    prev = list(range(len(hyp) + 1))
    for i, r in enumerate(ref, start=1):
        cur = [i] + [0] * len(hyp)
        for j, h in enumerate(hyp, start=1):
            cur[j] = min(
                prev[j] + 1,        # deletion
                cur[j - 1] + 1,     # insertion
                prev[j - 1] + (r != h),  # substitution
            )
        prev = cur
    return prev[len(hyp)] / len(ref)


def transcribe(url: str, pcm: bytes, timeout: float) -> tuple[dict, float]:
    request = urllib.request.Request(
        url.rstrip("/") + "/asr",
        data=pcm,
        method="POST",
        headers={
            "Content-Type": "application/octet-stream",
            "X-Sample-Rate": str(TARGET_RATE),
            "X-Trace-Id": str(uuid.uuid4()),
        },
    )
    started = time.perf_counter()
    with urllib.request.urlopen(request, timeout=timeout) as resp:
        body = json.loads(resp.read())
    return body, (time.perf_counter() - started) * 1000.0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:8080")
    parser.add_argument("--raw-dir", default="../android/voice/src/main/res/raw")
    parser.add_argument("--prompts", default="../android/voice/scripts/tts_prompts.tsv")
    parser.add_argument("--out", default="asr-bench.csv")
    parser.add_argument("--limit", type=int, default=0, help="0 = every clip with ground truth")
    parser.add_argument("--timeout", type=float, default=120.0)
    args = parser.parse_args()

    raw_dir = Path(args.raw_dir)
    truth = load_ground_truth(Path(args.prompts))
    clips = sorted(p for p in raw_dir.glob("tts_*.wav") if p.stem in truth)
    if args.limit:
        clips = clips[: args.limit]
    if not clips:
        print(f"Khong tim thay clip nao co ground truth trong {raw_dir}", file=sys.stderr)
        return 1

    rows = []
    for path in clips:
        samples, rate = read_wav_mono16(path)
        resampled = resample_linear(samples, rate, TARGET_RATE)
        audio_ms = len(resampled) / TARGET_RATE * 1000.0
        try:
            body, round_trip_ms = transcribe(args.url, resampled.tobytes(), args.timeout)
        except urllib.error.HTTPError as exc:
            print(f"{path.name}: HTTP {exc.code} {exc.read().decode('utf-8', 'replace')}", file=sys.stderr)
            return 1
        except urllib.error.URLError as exc:
            print(f"Khong goi duoc {args.url}: {exc.reason}", file=sys.stderr)
            return 1

        reference = truth[path.stem]
        hypothesis = body.get("text", "")
        server_ms = int(body.get("server_ms", 0))
        rows.append({
            "clip": path.stem,
            "reference": reference,
            "hypothesis": hypothesis,
            "wer": round(word_error_rate(reference, hypothesis), 4),
            "confidence": body.get("confidence", 0.0),
            "audio_ms": round(audio_ms, 1),
            "server_ms": server_ms,
            "round_trip_ms": round(round_trip_ms, 1),
            "rtf": round(server_ms / audio_ms, 4) if audio_ms else 0.0,
        })
        print(f"  {path.stem:<32} WER={rows[-1]['wer']:.2f} "
              f"server={server_ms}ms rtf={rows[-1]['rtf']:.2f}  “{hypothesis}”")

    with open(args.out, "w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    wers = [r["wer"] for r in rows]
    rtfs = [r["rtf"] for r in rows]
    servers = sorted(r["server_ms"] for r in rows)
    p95 = servers[max(0, min(len(servers) - 1, round(0.95 * len(servers)) - 1))]
    print()
    print(f"clips        : {len(rows)}")
    print(f"WER trung binh: {statistics.mean(wers):.3f}   (median {statistics.median(wers):.3f})")
    print(f"WER = 0 tuyet doi: {sum(1 for w in wers if w == 0)}/{len(rows)} clip")
    print(f"RTF trung binh: {statistics.mean(rtfs):.3f}   (median {statistics.median(rtfs):.3f})")
    print(f"server_ms    : p50={statistics.median(servers):.0f}  p95={p95}  max={servers[-1]}")
    print(f"CSV -> {args.out}")
    print()
    print("Nho: clip la giong TTS tong hop, resample 22.05k->16k bang windowed-sinc,")
    print("va RTF do tren CPU may nay — khong phai so cua container node CarSky.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
