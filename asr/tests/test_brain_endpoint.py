from __future__ import annotations

import pytest

from app import main as app_main
from app.brain import BrainPlan, BrainPlannerDisabledError, BrainProviderError


VALID_PLAN = BrainPlan(
    kind="action",
    intent_name="hvac_set_temp",
    value=22.0,
    level=None,
    lock=None,
    on=None,
    delta=None,
    query=None,
    order_id=None,
    prompt_vi=None,
    confidence=0.91,
)
TEST_BRAIN_TOKEN = "test-brain-token"
AUTH_HEADERS = {"Authorization": f"Bearer {TEST_BRAIN_TOKEN}"}


@pytest.fixture(autouse=True)
def configured_brain_auth(monkeypatch):
    monkeypatch.setattr(app_main, "brain_auth_token", TEST_BRAIN_TOKEN, raising=False)


class FakeBrainPlanner:
    def __init__(self, result: BrainPlan = VALID_PLAN):
        self.model = "fake-brain-model"
        self.result = result
        self.calls: list[tuple[str, str]] = []
        self.raises: Exception | None = None

    async def plan(self, text: str, trace_id: str) -> BrainPlan:
        self.calls.append((text, trace_id))
        if self.raises is not None:
            raise self.raises
        return self.result


def test_brain_plan_returns_a_strict_typed_proposal(client, monkeypatch):
    planner = FakeBrainPlanner()
    monkeypatch.setattr(app_main, "brain_planner", planner)

    response = client.post(
        "/v1/brain/plan",
        json={"text": "trong xe ngột ngạt quá", "trace_id": "trace-123"},
        headers={**AUTH_HEADERS, "X-Trace-Id": "trace-123"},
    )

    assert response.status_code == 200
    assert response.headers["X-Trace-Id"] == "trace-123"
    expected = VALID_PLAN.model_dump()
    expected.pop("actions")
    expected.pop("resume_prefix")
    assert response.json() == expected
    assert planner.calls == [("trong xe ngột ngạt quá", "trace-123")]


def test_brain_plan_is_disabled_without_a_server_side_api_key(client, monkeypatch):
    planner = FakeBrainPlanner()
    planner.raises = BrainPlannerDisabledError("OPENAI_API_KEY missing")
    monkeypatch.setattr(app_main, "brain_planner", planner)

    response = client.post(
        "/v1/brain/plan",
        json={"text": "giúp mình làm mát", "trace_id": "trace-disabled"},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 503
    assert response.json()["error"] == "brain planner unavailable"
    assert "OPENAI_API_KEY" not in response.text


def test_brain_plan_hides_provider_failures(client, monkeypatch):
    planner = FakeBrainPlanner()
    planner.raises = BrainProviderError("upstream body contains sensitive details")
    monkeypatch.setattr(app_main, "brain_planner", planner)

    response = client.post(
        "/v1/brain/plan",
        json={"text": "giúp mình làm mát", "trace_id": "trace-provider"},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 502
    assert response.json()["error"] == "brain provider failed"
    assert "sensitive" not in response.text


def test_brain_plan_rejects_oversized_transcript_before_calling_model(client, monkeypatch):
    planner = FakeBrainPlanner()
    monkeypatch.setattr(app_main, "brain_planner", planner)

    response = client.post(
        "/v1/brain/plan",
        json={"text": "x" * 501, "trace_id": "trace-long"},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 422
    assert planner.calls == []


def test_single_action_wire_shape_stays_backward_compatible(client, monkeypatch):
    monkeypatch.setattr(app_main, "brain_planner", FakeBrainPlanner())

    response = client.post(
        "/v1/brain/plan",
        json={"text": "làm mát xe", "trace_id": "trace-legacy"},
        headers=AUTH_HEADERS,
    )

    assert "actions" not in response.json()


def test_brain_endpoint_returns_a_bounded_action_list(client, monkeypatch):
    multi = BrainPlan.model_validate(
        {
            "kind": "actions",
            "intent_name": None,
            "value": None,
            "level": None,
            "lock": None,
            "on": None,
            "delta": None,
            "query": None,
            "order_id": None,
            "prompt_vi": None,
            "confidence": 0.88,
            "actions": [
                {
                    "intent_name": "cabin_lights",
                    "value": None,
                    "level": None,
                    "lock": None,
                    "on": True,
                    "delta": None,
                    "query": None,
                    "order_id": None,
                    "confidence": 0.91,
                },
                {
                    "intent_name": "media_next",
                    "value": None,
                    "level": None,
                    "lock": None,
                    "on": None,
                    "delta": None,
                    "query": None,
                    "order_id": None,
                    "confidence": 0.88,
                },
            ],
        }
    )
    monkeypatch.setattr(app_main, "brain_planner", FakeBrainPlanner(multi))

    response = client.post(
        "/v1/brain/plan",
        json={"text": "bật đèn rồi chuyển bài", "trace_id": "trace-multi"},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 200
    assert response.json()["kind"] == "actions"
    assert [item["intent_name"] for item in response.json()["actions"]] == [
        "cabin_lights",
        "media_next",
    ]


def test_brain_endpoint_returns_typed_resume_prefix_only_for_clarification(client, monkeypatch):
    clarification = BrainPlan.model_validate(
        {
            "kind": "clarification",
            "intent_name": None,
            "value": None,
            "level": None,
            "lock": None,
            "on": None,
            "delta": None,
            "query": None,
            "order_id": None,
            "prompt_vi": "Bạn muốn đặt nhiệt độ bao nhiêu độ?",
            "confidence": 0.7,
            "resume_prefix": "temperature",
        }
    )
    monkeypatch.setattr(app_main, "brain_planner", FakeBrainPlanner(clarification))

    response = client.post(
        "/v1/brain/plan",
        json={"text": "làm mát giúp mình", "trace_id": "trace-resume"},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 200
    assert response.json()["resume_prefix"] == "temperature"


@pytest.mark.parametrize(
    "headers",
    [
        {},
        {"Authorization": "Basic not-supported"},
        {"Authorization": "Bearer wrong-token"},
    ],
)
def test_brain_endpoint_rejects_missing_or_invalid_bearer_before_planning(
    client,
    monkeypatch,
    headers,
):
    planner = FakeBrainPlanner()
    monkeypatch.setattr(app_main, "brain_planner", planner)

    response = client.post(
        "/v1/brain/plan",
        json={"text": "làm mát xe", "trace_id": "trace-unauthorized"},
        headers=headers,
    )

    assert response.status_code == 401
    assert response.headers["WWW-Authenticate"] == "Bearer"
    assert TEST_BRAIN_TOKEN not in response.text
    assert planner.calls == []


def test_brain_endpoint_fails_closed_when_server_auth_is_not_configured(client, monkeypatch):
    planner = FakeBrainPlanner()
    monkeypatch.setattr(app_main, "brain_planner", planner)
    monkeypatch.setattr(app_main, "brain_auth_token", "", raising=False)

    response = client.post(
        "/v1/brain/plan",
        json={"text": "làm mát xe", "trace_id": "trace-no-auth-config"},
        headers=AUTH_HEADERS,
    )

    assert response.status_code == 503
    assert response.json()["error"] == "brain planner unavailable"
    assert planner.calls == []
