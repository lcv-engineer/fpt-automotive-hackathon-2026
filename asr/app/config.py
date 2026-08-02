"""Configuration for the viva-asr service.

Everything is read from environment variables so the same image can serve a
different model without a code change (V6: "swap duoc model"). See
`.env.example` for the full list and `README.md` for how to convert a model.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

# 03-contracts.md §2 pins the wire format at 16 kHz mono PCM16. The service
# refuses anything else rather than resampling silently — a client sending
# 48 kHz is a bug on the client, and hiding it here makes it surface later as
# "ASR quality is bad" instead of a clear 400.
REQUIRED_SAMPLE_RATE = 16000

_BYTES_PER_SAMPLE = 2


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise ValueError(f"{name} must be an integer, got {raw!r}") from exc


@dataclass(frozen=True)
class Settings:
    """Runtime settings, resolved once at import of `app.main`."""

    # Path to a CTranslate2 model directory (what `ct2-transformers-converter`
    # writes), or a plain faster-whisper model id like "tiny". The Dockerfile
    # bakes a converted PhoWhisper into /models and points this at it.
    model_path: str = "/models/phowhisper-tiny-ct2"

    # Human-readable name reported by GET /health. Kept separate from
    # model_path because the path is an implementation detail and the health
    # field is evidence that shows up in the benchmark write-up (N5 identity:
    # "ghi model/version/config/commit cho ca hai duong").
    model_name: str = "phowhisper-tiny-int8"

    device: str = "cpu"
    compute_type: str = "int8"
    cpu_threads: int = 0  # 0 = let CTranslate2 decide
    num_workers: int = 1

    language: str = "vi"
    # Greedy decoding by default: beam search costs latency, and the p95 budget
    # for the whole edge path is 1500 ms (03-contracts.md §1.3).
    beam_size: int = 1

    # 30 s of 16 kHz mono PCM16. A client that sends more is streaming a whole
    # session instead of one utterance; fail fast with 413 instead of timing
    # out the app.
    max_body_bytes: int = 30 * REQUIRED_SAMPLE_RATE * _BYTES_PER_SAMPLE

    # Shortest audio we bother sending to the model. Below this the result is
    # noise, and Whisper happily hallucinates a sentence for 50 ms of silence.
    min_audio_ms: int = 200

    @staticmethod
    def from_env() -> "Settings":
        return Settings(
            model_path=os.getenv("ASR_MODEL_PATH", Settings.model_path),
            model_name=os.getenv("ASR_MODEL_NAME", Settings.model_name),
            device=os.getenv("ASR_DEVICE", Settings.device),
            compute_type=os.getenv("ASR_COMPUTE_TYPE", Settings.compute_type),
            cpu_threads=_env_int("ASR_CPU_THREADS", Settings.cpu_threads),
            num_workers=_env_int("ASR_NUM_WORKERS", Settings.num_workers),
            language=os.getenv("ASR_LANGUAGE", Settings.language),
            beam_size=_env_int("ASR_BEAM_SIZE", Settings.beam_size),
            max_body_bytes=_env_int("ASR_MAX_BODY_BYTES", Settings.max_body_bytes),
            min_audio_ms=_env_int("ASR_MIN_AUDIO_MS", Settings.min_audio_ms),
        )
