from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import Settings  # noqa: E402
from app.model import build_transcribe_kwargs  # noqa: E402


def test_initial_prompt_defaults_to_none_when_env_absent(monkeypatch):
    monkeypatch.delenv("ASR_INITIAL_PROMPT", raising=False)
    assert Settings.from_env().initial_prompt is None


def test_initial_prompt_read_from_env(monkeypatch):
    monkeypatch.setenv("ASR_INITIAL_PROMPT", "điều hòa, quạt gió, độ C")
    assert Settings.from_env().initial_prompt == "điều hòa, quạt gió, độ C"


def test_blank_initial_prompt_is_none_not_empty_string(monkeypatch):
    # An empty prompt is not the same as no prompt: faster-whisper would
    # tokenize "" and prepend a useless empty context.
    monkeypatch.setenv("ASR_INITIAL_PROMPT", "   ")
    assert Settings.from_env().initial_prompt is None


def test_kwargs_omit_initial_prompt_when_unset():
    settings = Settings(initial_prompt=None)
    kwargs = build_transcribe_kwargs(settings)
    assert "initial_prompt" not in kwargs
    assert kwargs["language"] == "vi"
    assert kwargs["beam_size"] == 1
    # The app already ran Silero VAD before sending; a second pass would only
    # add latency and risk cutting the tail.
    assert kwargs["vad_filter"] is False


def test_kwargs_include_initial_prompt_when_set():
    settings = Settings(initial_prompt="điều hòa, quạt gió")
    kwargs = build_transcribe_kwargs(settings)
    assert kwargs["initial_prompt"] == "điều hòa, quạt gió"
