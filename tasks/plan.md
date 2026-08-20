# Implementation Plan: VIVA Brain next-step hardening

## Overview

Extend the existing single constrained planner without adding agents. The change fixes silently
swallowed compound commands, lets LLM clarifications resume through a typed enum, protects the
public Brain route with a deployment-provided bearer token, and puts the runtime microphone path on
the existing streaming VAD architecture with one lazily loaded Silero ONNX session.

The published trace summary contract, `minSilenceMs=800`, and both 30-second ASR client timeouts stay
unchanged until Device/Vĩ decisions are available.

## Architecture decisions

- A deterministic compound command whose clauses all match grammar stays on T0; otherwise the whole
  utterance falls back to the constrained planner instead of executing only the first clause.
- A plan contains at most three actions. Actions execute sequentially in spoken order and stop at the
  first deny, confirmation, or failure. Already-applied actions are not rolled back.
- Every action is validated independently, stays inside the T2 allowlist, and traverses the existing
  `CommandGateway -> SafetyGuard` path. `door_lock` remains unavailable to T2.
- LLM clarification state is an enum on the wire and maps to locally owned canonical text. Arbitrary
  model-provided resume text is never concatenated into a later command.
- `/v1/brain/plan` requires `Authorization: Bearer <VIVA_BRAIN_AUTH_TOKEN>`. Missing server or Android
  configuration fails closed; token values are never logged or committed.
- `PcmSourceAudioCapture`, `VadStreamDriver`, and `SileroVadDriverFactory` become the only runtime VAD
  path. The scorer remains stateful but is reset before every session and one voice session runs at a
  time.

## Dependency graph

```text
Typed core results
  -> deterministic compound routing
  -> sequential VoiceAgent execution
  -> Brain wire schema + Android parser
  -> typed LLM clarification resume

Server auth contract
  -> Android auth header

Streaming VAD timing model
  -> runtime capture migration
  -> single lazy ONNX session
```

## Task list

### Phase 1: Multi-action core

- [ ] Add a failing grammar test proving a compound command is not reduced to its first action.
- [ ] Add typed multi-action route/plan results with a hard limit of three actions.
- [ ] Add failing agent tests for ordered execution, stop-on-first-failure, merged HMI state, and
      per-action spoken segments.
- [ ] Implement sequential execution through the existing gateway.

### Checkpoint: Core

- [ ] `:voice-core:testDebugUnitTest` passes.
- [ ] Single-action behavior and negation tests remain green.

### Phase 2: Planner wire and dialogue resume

- [ ] Add failing Python contract tests for two-action plans, size bounds, invalid member actions,
      and continued rejection of `door_lock`.
- [ ] Extend strict Structured Outputs and semantic validation without weakening single-action checks.
- [ ] Add failing Android parser tests for action arrays and typed `resume_prefix`.
- [ ] Add typed LLM clarification resume tests and implementation in `VoiceAgent`.
- [ ] Update ADR/architecture contract documentation.

### Checkpoint: Planner

- [ ] Focused Python Brain tests pass.
- [ ] Android Brain parser and VoiceAgent tests pass.

### Phase 3: Endpoint authentication

- [ ] Add failing endpoint tests for missing, malformed, and valid bearer credentials.
- [ ] Implement constant-time token comparison and fail-closed configuration.
- [ ] Add the Android header/configuration and document secret injection.

### Checkpoint: Security

- [ ] No token value appears in logs, tracked files, or staged diff.
- [ ] Brain tests pass; ASR and health routes remain unchanged.

### Phase 4: One runtime VAD architecture

- [ ] Add timing fields/tests so streaming VAD preserves acoustic end and endpoint-decision time.
- [ ] Add a construction-count regression test proving repeated captures reuse one scorer/session.
- [ ] Migrate `VadUtteranceCapture` to `PcmSourceAudioCapture + VadStreamDriver` and remove its manual
      frame/endpointer/session loop.
- [ ] Keep the 800 ms cabin configuration and existing felt-latency measurement intact.

### Checkpoint: Complete

- [ ] Full `pytest` suite passes.
- [ ] Full `gradlew test` suite passes.
- [ ] Five-axis review finds no unresolved correctness, architecture, security, or performance issue.
- [ ] Each logical increment is committed atomically; unrelated untracked directories are untouched.

## Risks and mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| One failed action leaves earlier state applied | Medium | Explicit partial-success status/copy; stop immediately; no false rollback claim |
| Compound splitter mistakes text inside a media query for another command | High | Accept a split only when every clause independently produces a matched action |
| Old client sees a multi-action response | Low | It fails closed; retain the old single-action wire shape for single plans |
| Shared scorer state leaks between turns | High | Reset at session creation; serialize voice sessions; regression-test reuse/reset |
| Bearer token is extracted from an APK | Medium | Treat as room/deployment access control, use TLS, rotate per deployment; never confuse it with the server-side OpenAI key |

## Open questions deliberately not guessed

- Device A/B must choose `minSilenceMs` 450 vs 600; current value remains 800.
- Product/device measurements must choose the ASR deadline replacing both 30-second client constants.
- Vĩ must approve adding `feltLatencyMs` to the published summary line.

