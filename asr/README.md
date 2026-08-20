# viva-asr

Vietnamese speech-to-text service for the VIVA voice pipeline (task **V6**) plus an optional,
server-side constrained LLM planner for VIVA Brain. The ASR contract remains unchanged:

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

When configured with a server-side OpenAI key, a third route supplies the Brain slow path:

```text
POST /v1/brain/plan
Content-Type: application/json

{"text":"trong xe ngột ngạt quá","trace_id":"demo-1"}
```

The route uses `gpt-5.4-mini-2026-03-17` and strict Structured Outputs. It returns only an allowlisted
intent proposal, clarification, or unsupported result. It has no vehicle tool and cannot execute an
action. A compound request may return a bounded list of two or three independently validated actions;
Android executes them in order through the existing gateway and Body SafetyGuard.
Clarifications may carry one closed-enum `resume_prefix`; arbitrary model-authored resume text is not
part of the wire contract.

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

Enable the optional Brain planner in the server process:

```powershell
$env:OPENAI_API_KEY = "<server-side key>"
$env:VIVA_BRAIN_MODEL = "gpt-5.4-mini-2026-03-17"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8080
```

Never put `OPENAI_API_KEY` in Android resources, Gradle properties, BuildConfig, an APK, or a tracked
file. Without the key, `/v1/brain/plan` deliberately returns `503` while ASR continues to work.

### Container

```powershell
docker build -t viva-asr:phowhisper-tiny-int8 asr/
docker run --rm -p 8080:8080 viva-asr:phowhisper-tiny-int8
curl http://127.0.0.1:8080/health
```

For a Brain-enabled container, inject the key through the deployment secret store or `-e
OPENAI_API_KEY`; do not bake it into the image.

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

The Brain provider tests use `httpx.MockTransport`; they prove the OpenAI request contract and
fail-closed parsing without spending tokens or requiring a real key. A live smoke test remains a
separate deployment check.

## Status — what is verified and what is not

| | State |
|---|---|
| HTTP contract (routes, headers, status codes, body shape) | ✅ 20 tests green locally |
| PCM16 decode, confidence aggregation | ✅ unit tested |
| Dockerfile | ✅ **builds and runs** (04/08). It took four attempts and three real version constraints — see below. |
| Transcription on real Vietnamese audio | ✅ 36 clips through the running container: RTF median **0.167**, `server_ms` p50 **439** / p95 **667**, WER 0.411 on synthesised speech. Evidence: `evidence/asr/`. |
| Latency on **CarSky** | ❌ not measured — the numbers above are this dev machine's CPU. Needs the container node (V7). |
| CPU architecture of CarSky container nodes | ❌ unconfirmed — the image assumes x86_64 Linux. |

### Three version constraints found by building it, not by reading docs

Each cost one failed build. They are pinned with the reason in `Dockerfile` and
`requirements.txt`; do not "tidy them up" without rebuilding.

| Constraint | Symptom if violated |
|---|---|
| `ctranslate2 >= 4.6` | `ImportError: libctranslate2-*.so: cannot enable executable stack` — 4.4/4.5 wheels carry an exec-stack flag the WSL2 kernel refuses. Verified per version: 4.4.0 ✗, 4.6.0 ✓, 4.8.1 ✓ |
| `transformers` 5.x | `TypeError: WhisperForConditionalGeneration.__init__() got an unexpected keyword argument 'dtype'` — the ct2 4.8 converter uses the new kwarg name |
| `torch >= 2.6` | `ValueError: ... vulnerability issue in torch.load ... upgrade torch to at least v2.6` (CVE-2025-32434). This one is a property of **the model**: `vinai/PhoWhisper-tiny` ships `pytorch_model.bin`, not safetensors. Swap to a safetensors model and the constraint disappears. |
| `huggingface_hub < 1.0` | Container starts, then `/health` stays 503 with `ModuleNotFoundError: No module named 'requests'` — hub 1.x dropped `requests`, faster-whisper 1.1.1 still imports it |

### ⚠️ How to read the accuracy number

WER 0.411 sounds bad; the transcripts say something more specific. Errors are
mostly near-homophones (`đặt` → `đặc`, `độ C` → `đỗ xê`) and a frequently dropped
first word. Two consequences:

- The clips are **synthesised speech**, downsampled 22.05 → 16 kHz by linear
  interpolation — crude, and it can only hurt. Real 16 kHz audio should do better.
- The intent router matches on keywords, so **WER overstates the damage**. The
  number that matters for this product is intent accuracy end-to-end, and that
  comes from the harness (V10/V12), not from here.
