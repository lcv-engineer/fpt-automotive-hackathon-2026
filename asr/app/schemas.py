"""Response bodies.

The `POST /asr` 200 body is exactly the three fields in 03-contracts.md §2 —
no extras. Long's `AsrClient` decodes it on the app side and a strict decoder
would break on unexpected keys; diagnostics go into response headers instead.
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class AsrResponse(BaseModel):
    text: str
    confidence: float = Field(ge=0.0, le=1.0)
    server_ms: int = Field(ge=0)


class ConfigResponse(BaseModel):
    compute_type: str
    initial_prompt: str | None
    hotwords: str | None
    max_new_tokens: int


class HealthResponse(BaseModel):
    status: str
    model: str
    config: ConfigResponse | None = None


class ErrorResponse(BaseModel):
    error: str
    detail: str | None = None
