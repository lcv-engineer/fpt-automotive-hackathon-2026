# viva-asr

Vietnamese speech-to-text HTTP service for the VIVA voice pipeline (task **V6**).
Implements `vong2/03-contracts.md` §2 exactly — two routes, no more:

```
POST /asr
Content-Type: application/octet-stream
X-Sample-Rate: 16000
X-Trace-Id: <traceId>
Body: raw PCM 16-bit LE mono

200 OK
{ "text": "hạ điều hòa xuống 22 độ", "confidence": 0.94, "server_ms": 210 }

GET /health -> {"status":"ok","model":"phowhisper-tiny-int8"}
```

Design rationale, model comparison and the open questions behind it:
[`docs/backend-docs/v6-viva-asr.md`](../docs/backend-docs/v6-viva-asr.md).

## Why it is Python and not part of `backend/`

`backend/` is a Go CLI with a deliberate zero-dependency rule. Serving
INT8 Whisper is far better supported in Python (`faster-whisper`/CTranslate2)
than through Go ONNX bindings, so this is a separate service with its own
Dockerfile — see `backend/README.md` "Scope".

## Run it

### Locally, without Docker

```powershell
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt   # includes faster-whisper
$env:ASR_MODEL_PATH = "tiny"      # or a converted CTranslate2 directory
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8080
```

### Container

```powershell
docker build -t viva-asr:phowhisper-tiny-int8 asr/
docker run --rm -p 8080:8080 viva-asr:phowhisper-tiny-int8
curl http://127.0.0.1:8080/health
```

The build converts `vinai/PhoWhisper-tiny` to CTranslate2 INT8 in a throwaway
stage, so the runtime image ships the model but not torch/transformers.

### Swap the model without touching code

| What you want | How |
|---|---|
| A different HuggingFace Whisper fine-tune | `docker build --build-arg ASR_HF_MODEL=vinai/PhoWhisper-base --build-arg ASR_MODEL_NAME=phowhisper-base-int8 …` |
| A model you converted yourself | `docker run -v D:\models\my-ct2:/models/asr-ct2 -e ASR_MODEL_NAME=my-model …` |
| Different precision | `--build-arg ASR_QUANTIZATION=float16` and `-e ASR_COMPUTE_TYPE=float16` |

Converting by hand:

```
ct2-transformers-converter --model vinai/PhoWhisper-tiny \
  --output_dir phowhisper-tiny-ct2 --quantization int8 \
  --copy_files tokenizer.json preprocessor_config.json
```

Every knob is an environment variable — see `.env.example`.

## Behaviour that is deliberate

| Case | Response | Why |
|---|---|---|
| `X-Sample-Rate` ≠ 16000 | `400`, no resampling | The contract fixes 16 kHz. Silently resampling turns a client bug into an unexplained accuracy drop. |
| Body not a whole number of PCM16 samples, or empty | `400` | Truncated upload, not audio. |
| Body > `ASR_MAX_BODY_BYTES` (30 s) | `413` | That's a whole session, not one utterance — fail fast instead of timing out the app. |
| Audio < `ASR_MIN_AUDIO_MS` (200 ms) | `200` with `text: ""`, `confidence: 0.0` | Whisper hallucinates a sentence for a fraction of a second of noise. An empty result is the honest answer, and the app already has a "mình chưa nghe rõ" prompt. |
| Model still loading, or failed to load | `503` + `Retry-After` | A green health check that lies is worse than none: CarSky would route traffic to a pod that cannot serve. |
| Transcription throws | `500`, service stays up | One bad utterance must not take the container down mid-demo. |

`server_ms` is wall-clock around transcription only. Network time is Long's
`asr_sent → asr_done` minus this number — keeping them separable is the whole
point of the field.

## ⚠️ `confidence` is an approximation, not a model probability

CTranslate2 reports `avg_logprob` (mean token log-probability), not a
calibrated 0–1 score. This service returns a duration-weighted
`exp(avg_logprob)` across segments (`app/model.py: segments_to_confidence`).

SafetyGuard's `G3_LOW_CONFIDENCE` rule (`03-contracts.md` §4) fires below 0.6,
so **validate this number by hand on real Vietnamese utterances before that
threshold is trusted**, and describe it as an approximation in the write-up —
not as "the model's confidence".

## Tests

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pytest -q
```

The HTTP tests run against a fake transcriber, so they need neither
CTranslate2 wheels nor a model on disk — that is why `requirements-http.txt`
is split out of `requirements.txt`.

## Status — what is verified and what is not

| | State |
|---|---|
| HTTP contract (routes, headers, status codes, body shape) | ✅ 20 tests green locally |
| PCM16 decode, confidence aggregation | ✅ unit tested |
| Dockerfile | ⚠️ **not built yet** — no Docker daemon on the dev machine at the time of writing. Build it before relying on it. |
| Real transcription quality / latency (RTF) on CarSky | ❌ not measured. Needs the container node (V7). Do not quote a latency number until it comes out of the harness. |
| CPU architecture of CarSky container nodes | ❌ unconfirmed — the image assumes x86_64 Linux. |
