#!/usr/bin/env python3
"""Send one utterance to a running viva-asr and print the response.

Stdlib only, so it runs anywhere Python does — including next to the Device
when someone is debugging V7 without a venv.

    python scripts/send_pcm.py sample.wav
    python scripts/send_pcm.py sample.pcm --url http://10.99.0.2:8080/asr

`.wav` must already be 16 kHz mono 16-bit; the script refuses anything else
rather than resampling, matching what the service itself does.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
import wave

REQUIRED_RATE = 16000


def load_pcm(path: str) -> bytes:
    if path.lower().endswith(".wav"):
        with wave.open(path, "rb") as wav:
            if wav.getnchannels() != 1:
                sys.exit(f"{path}: {wav.getnchannels()} channels, need mono")
            if wav.getsampwidth() != 2:
                sys.exit(f"{path}: {wav.getsampwidth() * 8}-bit samples, need 16-bit")
            if wav.getframerate() != REQUIRED_RATE:
                sys.exit(f"{path}: {wav.getframerate()} Hz, need {REQUIRED_RATE} Hz")
            return wav.readframes(wav.getnframes())
    with open(path, "rb") as raw:
        return raw.read()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audio", help="16 kHz mono 16-bit .wav, or raw .pcm")
    parser.add_argument("--url", default="http://127.0.0.1:8080/asr")
    parser.add_argument("--trace-id", default=None, help="defaults to a fresh uuid4")
    parser.add_argument("--timeout", type=float, default=30.0)
    args = parser.parse_args()

    pcm = load_pcm(args.audio)
    trace_id = args.trace_id or str(uuid.uuid4())
    request = urllib.request.Request(
        args.url,
        data=pcm,
        method="POST",
        headers={
            "Content-Type": "application/octet-stream",
            "X-Sample-Rate": str(REQUIRED_RATE),
            "X-Trace-Id": trace_id,
        },
    )

    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=args.timeout) as resp:
            body = json.loads(resp.read())
            status = resp.status
    except urllib.error.HTTPError as exc:
        print(f"HTTP {exc.code}: {exc.read().decode('utf-8', 'replace')}", file=sys.stderr)
        return 1
    except urllib.error.URLError as exc:
        print(f"cannot reach {args.url}: {exc.reason}", file=sys.stderr)
        return 1
    round_trip_ms = (time.perf_counter() - started) * 1000.0

    audio_ms = len(pcm) / 2 / REQUIRED_RATE * 1000.0
    server_ms = body.get("server_ms", 0)
    print(json.dumps(body, ensure_ascii=False, indent=2))
    print(
        f"trace_id={trace_id} audio_ms={audio_ms:.0f} server_ms={server_ms} "
        f"round_trip_ms={round_trip_ms:.0f} network_ms={round_trip_ms - server_ms:.0f} "
        f"rtf={server_ms / audio_ms if audio_ms else 0:.3f} http={status}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
