"""Model loading and transcription.

`faster-whisper` (CTranslate2) is imported lazily inside the loader so the HTTP
layer — and its tests — can run without the wheel installed. Anything that
needs the real engine goes through `Transcriber`, which a fake can implement.
"""

from __future__ import annotations

import logging
import math
import threading
import time
from dataclasses import dataclass
from enum import Enum
from typing import Protocol

from .config import Settings

log = logging.getLogger("viva.asr")


@dataclass(frozen=True)
class Transcription:
    text: str
    confidence: float
    # Only for logs/headers — the JSON body stays exactly the three fields
    # 03-contracts.md §2 specifies.
    language: str | None = None
    segment_count: int = 0


class Transcriber(Protocol):
    def transcribe(self, samples: list[float], sample_rate: int) -> Transcription: ...


class ModelState(str, Enum):
    LOADING = "loading"
    READY = "ready"
    FAILED = "failed"


def segments_to_confidence(avg_logprobs_and_durations: list[tuple[float, float]]) -> float:
    """Collapse per-segment `avg_logprob` into one 0..1 confidence.

    ⚠️ This is an APPROXIMATION, not a calibrated probability. CTranslate2
    reports `avg_logprob` (mean token log-probability, negative); `exp()` maps
    it back to a per-token likelihood. Segments are weighted by duration so a
    long confident segment is not outvoted by a 200 ms filler.

    SafetyGuard's `G3_LOW_CONFIDENCE` threshold (0.6 in 03-contracts.md §4)
    rides on this number — validate it by hand on real Vietnamese samples
    before trusting it, and say so in the write-up rather than presenting it
    as a model-reported probability.
    """
    if not avg_logprobs_and_durations:
        return 0.0
    total_duration = sum(d for _, d in avg_logprobs_and_durations)
    if total_duration <= 0:
        # Degenerate timestamps: fall back to an unweighted mean rather than
        # dividing by zero or silently returning 0.
        values = [math.exp(lp) for lp, _ in avg_logprobs_and_durations]
        return max(0.0, min(1.0, sum(values) / len(values)))
    weighted = sum(math.exp(lp) * d for lp, d in avg_logprobs_and_durations)
    return max(0.0, min(1.0, weighted / total_duration))


class FasterWhisperTranscriber:
    """Thin wrapper over `faster_whisper.WhisperModel`.

    One model instance is shared across requests on purpose: CTranslate2 has
    its own worker queue (`num_workers`) and is documented as safe to call
    concurrently, so there is no lock here.
    """

    def __init__(self, settings: Settings):
        from faster_whisper import WhisperModel  # imported here, see module docstring

        kwargs = {
            "device": settings.device,
            "compute_type": settings.compute_type,
            "num_workers": settings.num_workers,
        }
        if settings.cpu_threads > 0:
            kwargs["cpu_threads"] = settings.cpu_threads
        self._model = WhisperModel(settings.model_path, **kwargs)
        self._settings = settings

    def transcribe(self, samples: list[float], sample_rate: int) -> Transcription:
        import numpy as np

        audio = np.asarray(samples, dtype=np.float32)
        segments, info = self._model.transcribe(
            audio,
            language=self._settings.language,
            beam_size=self._settings.beam_size,
            # The app already ran Silero VAD before sending (L3), so a second
            # VAD pass here would only add latency and risk cutting the tail.
            vad_filter=False,
        )

        texts: list[str] = []
        scored: list[tuple[float, float]] = []
        for seg in segments:  # generator — this is where decoding actually runs
            texts.append(seg.text)
            duration = max(0.0, float(seg.end) - float(seg.start))
            scored.append((float(seg.avg_logprob), duration))

        return Transcription(
            text=" ".join(t.strip() for t in texts).strip(),
            confidence=segments_to_confidence(scored),
            language=getattr(info, "language", None),
            segment_count=len(scored),
        )


class ModelHolder:
    """Owns the transcriber and the load lifecycle.

    Loading happens on a background thread so the container answers
    `GET /health` with a truthful "loading" instead of hanging the port until
    a multi-hundred-MB model is on disk — CarSky/monitoring needs to tell
    "still starting" apart from "wedged".
    """

    def __init__(self, settings: Settings, factory=FasterWhisperTranscriber):
        self._settings = settings
        self._factory = factory
        self._lock = threading.Lock()
        self._state = ModelState.LOADING
        self._error: str | None = None
        self._transcriber: Transcriber | None = None
        self._load_ms: float | None = None

    def start_loading(self) -> threading.Thread:
        thread = threading.Thread(target=self._load, name="asr-model-load", daemon=True)
        thread.start()
        return thread

    def ensure_loading(self) -> threading.Thread | None:
        """Start the background load unless a transcriber is already in place.

        Tests (and any future in-process host) call `set_ready()` with a fake
        before startup; without this guard the real loader would race them and
        overwrite a working transcriber with a FAILED state.
        """
        with self._lock:
            if self._transcriber is not None:
                return None
        return self.start_loading()

    def _load(self) -> None:
        started = time.perf_counter()
        try:
            transcriber = self._factory(self._settings)
        except Exception as exc:  # noqa: BLE001 — reported through /health, not swallowed
            log.exception("VIVA_ASR model load failed")
            with self._lock:
                self._state = ModelState.FAILED
                self._error = f"{type(exc).__name__}: {exc}"
            return
        elapsed_ms = (time.perf_counter() - started) * 1000.0
        with self._lock:
            self._transcriber = transcriber
            self._state = ModelState.READY
            self._load_ms = elapsed_ms
        log.info("VIVA_ASR model ready in %.0f ms: %s", elapsed_ms, self._settings.model_name)

    def set_ready(self, transcriber: Transcriber) -> None:
        """Inject an already-built transcriber (tests, and future in-process use)."""
        with self._lock:
            self._transcriber = transcriber
            self._state = ModelState.READY
            self._error = None

    @property
    def state(self) -> ModelState:
        with self._lock:
            return self._state

    @property
    def error(self) -> str | None:
        with self._lock:
            return self._error

    @property
    def load_ms(self) -> float | None:
        with self._lock:
            return self._load_ms

    def require_ready(self) -> Transcriber:
        with self._lock:
            if self._state is not ModelState.READY or self._transcriber is None:
                raise ModelNotReadyError(self._state, self._error)
            return self._transcriber


class ModelNotReadyError(RuntimeError):
    def __init__(self, state: ModelState, error: str | None):
        self.state = state
        self.error = error
        super().__init__(f"model not ready (state={state.value}, error={error})")
