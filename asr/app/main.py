"""viva-asr — HTTP ASR service for the VIVA voice pipeline.

Contract: `vong2/03-contracts.md` §2. Two routes, nothing else:

    POST /asr     raw PCM16 LE mono body, X-Sample-Rate + X-Trace-Id headers
    GET  /health  {"status": "ok", "model": "..."}

Design notes live in `docs/backend-docs/v6-viva-asr.md`.
"""

from __future__ import annotations

import logging
import time
from contextlib import asynccontextmanager

import anyio
from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse

from .audio import InvalidAudioError, duration_ms, pcm16le_to_float32
from .config import REQUIRED_SAMPLE_RATE, Settings
from .model import ModelHolder, ModelNotReadyError, ModelState
from .schemas import AsrResponse, HealthResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
log = logging.getLogger("viva.asr")

settings = Settings.from_env()
holder = ModelHolder(settings)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Load off the event loop: the port must accept connections (and answer
    # /health with "loading") while a few hundred MB of weights come off disk.
    holder.ensure_loading()
    yield


app = FastAPI(title="viva-asr", version="1.0.0", lifespan=lifespan)


def _error(status_code: int, error: str, detail: str | None = None, **headers: str) -> JSONResponse:
    body = {"error": error}
    if detail:
        body["detail"] = detail
    return JSONResponse(status_code=status_code, content=body, headers=headers or None)


@app.get("/health", response_model=HealthResponse)
def health() -> Response:
    state = holder.state
    config_dict = {
        "compute_type": settings.compute_type,
        "initial_prompt": settings.initial_prompt,
        "hotwords": settings.hotwords,
        "max_new_tokens": settings.max_new_tokens,
    }
    if state is ModelState.READY:
        return JSONResponse(
            status_code=200,
            content={"status": "ok", "model": settings.model_name, "config": config_dict},
        )
    # Never answer 200 while the model is absent: a green health check that
    # lies is worse than no health check, because CarSky would route traffic
    # to a pod that cannot serve.
    payload = {"status": state.value, "model": settings.model_name, "config": config_dict}
    if state is ModelState.FAILED and holder.error:
        payload["detail"] = holder.error
    return JSONResponse(status_code=503, content=payload, headers={"Retry-After": "5"})


@app.post("/asr", response_model=AsrResponse)
async def asr(request: Request) -> Response:
    trace_id = request.headers.get("X-Trace-Id", "-")
    common_headers = {"X-Trace-Id": trace_id, "X-Asr-Model": settings.model_name}

    raw_rate = request.headers.get("X-Sample-Rate")
    if raw_rate is None:
        return _error(400, "missing X-Sample-Rate header", **common_headers)
    try:
        sample_rate = int(raw_rate)
    except ValueError:
        return _error(400, "X-Sample-Rate must be an integer", f"got {raw_rate!r}", **common_headers)
    if sample_rate != REQUIRED_SAMPLE_RATE:
        # Deliberately not resampling: 03-contracts.md §2 fixes 16 kHz, and a
        # silent resample would turn a client bug into a quality mystery.
        return _error(
            400,
            f"unsupported sample rate {sample_rate}",
            f"this service accepts {REQUIRED_SAMPLE_RATE} Hz PCM16 LE mono only",
            **common_headers,
        )

    declared = request.headers.get("Content-Length")
    if declared is not None and declared.isdigit() and int(declared) > settings.max_body_bytes:
        return _error(
            413,
            "audio too large",
            f"{declared} bytes exceeds limit {settings.max_body_bytes}; send one utterance, not a session",
            **common_headers,
        )

    body = await request.body()
    if len(body) > settings.max_body_bytes:
        return _error(
            413,
            "audio too large",
            f"{len(body)} bytes exceeds limit {settings.max_body_bytes}",
            **common_headers,
        )

    try:
        samples = pcm16le_to_float32(body)
    except InvalidAudioError as exc:
        return _error(400, "invalid audio", str(exc), **common_headers)

    audio_ms = duration_ms(len(samples), sample_rate)
    if audio_ms < settings.min_audio_ms:
        # Whisper invents a sentence for a fraction of a second of noise. An
        # empty text with confidence 0 is the honest answer; the app already
        # has a "did not hear that" prompt for it.
        log.info(
            "VIVA_ASR|%s|too_short|audio_ms=%.0f|min_ms=%d",
            trace_id,
            audio_ms,
            settings.min_audio_ms,
        )
        return JSONResponse(
            status_code=200,
            content={"text": "", "confidence": 0.0, "server_ms": 0},
            headers={**common_headers, "X-Asr-Note": "audio-too-short"},
        )

    try:
        transcriber = holder.require_ready()
    except ModelNotReadyError as exc:
        return _error(
            503,
            "model not ready",
            str(exc),
            **{**common_headers, "Retry-After": "5"},
        )

    started = time.perf_counter()
    try:
        # CTranslate2 releases the GIL and has its own worker queue, so a
        # worker thread per request is fine and keeps the event loop free.
        result = await anyio.to_thread.run_sync(transcriber.transcribe, samples, sample_rate)
    except Exception as exc:  # noqa: BLE001 — one bad utterance must not kill the service
        log.exception("VIVA_ASR|%s|transcribe_failed", trace_id)
        return _error(500, "transcription failed", f"{type(exc).__name__}: {exc}", **common_headers)
    # Wall clock around transcription only — HTTP decode time belongs to the
    # network leg, and Long's LatencyTrace measures that separately
    # (asr_sent -> asr_done minus server_ms).
    server_ms = int(round((time.perf_counter() - started) * 1000.0))

    log.info(
        "VIVA_ASR|%s|ok|server_ms=%d|audio_ms=%.0f|conf=%.2f|chars=%d|segments=%d",
        trace_id,
        server_ms,
        audio_ms,
        result.confidence,
        len(result.text),
        result.segment_count,
    )
    return JSONResponse(
        status_code=200,
        content={
            "text": result.text,
            "confidence": round(result.confidence, 4),
            "server_ms": server_ms,
        },
        headers={**common_headers, "X-Asr-Rtf": f"{(server_ms / audio_ms):.3f}" if audio_ms else "0"},
    )
