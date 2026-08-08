import pytest
from app.config import Settings
from app.model import build_transcribe_kwargs

def test_build_transcribe_kwargs_no_hotwords():
    settings = Settings(hotwords=None, max_new_tokens=0, initial_prompt=None, beam_size=1)
    kwargs = build_transcribe_kwargs(settings)
    assert "hotwords" not in kwargs
    assert "max_new_tokens" not in kwargs
    assert "initial_prompt" not in kwargs
    assert kwargs.get("beam_size") == 1
    assert kwargs.get("vad_filter") is False

def test_build_transcribe_kwargs_with_hotwords():
    settings = Settings(hotwords="điều hòa", max_new_tokens=64, initial_prompt="test prompt", beam_size=1)
    kwargs = build_transcribe_kwargs(settings)
    assert kwargs["hotwords"] == "điều hòa"
    assert kwargs["max_new_tokens"] == 64
    assert kwargs["initial_prompt"] == "test prompt"
