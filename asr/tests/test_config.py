import os
import pytest
from app.config import Settings

@pytest.fixture(autouse=True)
def clean_env(monkeypatch):
    for k in list(os.environ.keys()):
        if k.startswith("ASR_"):
            monkeypatch.delenv(k, raising=False)
    yield
    for k in list(os.environ.keys()):
        if k.startswith("ASR_"):
            monkeypatch.delenv(k, raising=False)

def test_settings_defaults():
    settings = Settings.from_env()
    assert settings.max_new_tokens == 0
    assert settings.hotwords is None
    assert settings.initial_prompt is None
    assert settings.compute_type == "int8"

def test_settings_parsing_explicit_values():
    os.environ["ASR_MAX_NEW_TOKENS"] = "64"
    os.environ["ASR_HOTWORDS"] = "điều hòa, nhiệt độ"
    os.environ["ASR_INITIAL_PROMPT"] = "" # empty string is treated as empty string but settings might treat it as default None if stripped. Wait, config.py says if raw.strip() == "" returns default (None).
    
    settings = Settings.from_env()
    assert settings.max_new_tokens == 64
    assert settings.hotwords == "điều hòa, nhiệt độ"
    assert settings.initial_prompt is None

def test_settings_parsing_empty_string_to_default():
    os.environ["ASR_HOTWORDS"] = "   "
    settings = Settings.from_env()
    assert settings.hotwords is None

def test_settings_invalid_int():
    os.environ["ASR_MAX_NEW_TOKENS"] = "invalid"
    with pytest.raises(ValueError, match="must be an integer"):
        Settings.from_env()
