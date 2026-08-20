from __future__ import annotations

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
        headers={"X-Trace-Id": "trace-123"},
    )

    assert response.status_code == 200
    assert response.headers["X-Trace-Id"] == "trace-123"
    assert response.json() == VALID_PLAN.model_dump()
    assert planner.calls == [("trong xe ngột ngạt quá", "trace-123")]


def test_brain_plan_is_disabled_without_a_server_side_api_key(client, monkeypatch):
    planner = FakeBrainPlanner()
    planner.raises = BrainPlannerDisabledError("OPENAI_API_KEY missing")
    monkeypatch.setattr(app_main, "brain_planner", planner)

    response = client.post(
        "/v1/brain/plan",
        json={"text": "giúp mình làm mát", "trace_id": "trace-disabled"},
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
    )

    assert response.status_code == 422
    assert planner.calls == []
