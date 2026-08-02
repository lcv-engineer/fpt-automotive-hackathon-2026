# syntax=docker/dockerfile:1
#
# viva-asr — two stages so the runtime image carries the converted model but
# not the ~2 GB of torch/transformers needed to convert it.
#
#   docker build -t viva-asr:phowhisper-tiny-int8 asr/
#   docker run --rm -p 8080:8080 viva-asr:phowhisper-tiny-int8
#
# Swap the model without touching code:
#   docker build --build-arg ASR_HF_MODEL=vinai/PhoWhisper-base \
#                --build-arg ASR_MODEL_NAME=phowhisper-base-int8 -t viva-asr:base asr/
# or mount an already-converted CTranslate2 directory and point ASR_MODEL_PATH
# at it (-v /host/model:/models/asr-ct2).

ARG PYTHON_VERSION=3.11-slim

# ---------------------------------------------------------------- model stage
FROM python:${PYTHON_VERSION} AS model-builder

ARG ASR_HF_MODEL=vinai/PhoWhisper-tiny
ARG ASR_QUANTIZATION=int8

# CPU-only torch: the converter just needs to read the weights, and the CUDA
# wheels are ~2 GB larger for no benefit. GPU quota on CarSky is still an open
# question (04-KE-HOACH-CAP-NHAT-28-07.md), so the runtime defaults to CPU too.
RUN pip install --no-cache-dir \
      --extra-index-url https://download.pytorch.org/whl/cpu \
      ctranslate2==4.5.0 transformers==4.47.1 torch==2.5.1

RUN ct2-transformers-converter \
      --model "${ASR_HF_MODEL}" \
      --output_dir /models/asr-ct2 \
      --quantization "${ASR_QUANTIZATION}" \
      --copy_files tokenizer.json preprocessor_config.json

# -------------------------------------------------------------- runtime stage
FROM python:${PYTHON_VERSION} AS runtime

ARG ASR_MODEL_NAME=phowhisper-tiny-int8
# Build metadata, surfaced for N6 (artifact identity): the image must be able
# to say which commit built it.
ARG VIVA_GIT_COMMIT=unknown

WORKDIR /srv

COPY requirements-http.txt requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY --from=model-builder /models/asr-ct2 /models/asr-ct2

ENV ASR_MODEL_PATH=/models/asr-ct2 \
    ASR_MODEL_NAME=${ASR_MODEL_NAME} \
    ASR_DEVICE=cpu \
    ASR_COMPUTE_TYPE=int8 \
    ASR_LANGUAGE=vi \
    ASR_BEAM_SIZE=1 \
    VIVA_GIT_COMMIT=${VIVA_GIT_COMMIT} \
    PYTHONUNBUFFERED=1

# Non-root: nothing here needs privileges, and CarSky container nodes may
# refuse a root process.
RUN useradd --system --uid 10001 --create-home viva && chown -R viva:viva /srv /models
USER viva

EXPOSE 8080

# /health is 503 until the model is loaded, so this also covers slow starts.
HEALTHCHECK --interval=10s --timeout=3s --start-period=120s --retries=3 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=2).status==200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
