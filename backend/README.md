# viva-tools

Go CLI for Team VIVA's backend surface: the **benchmark harness** and the
**CarSky devops helper**. This is *not* a Command Dispatcher / skills API —
that logic now lives in-app (Kotlin, on Android/AAOS). See
[Scope](#scope-why-this-is-not-a-command-dispatcher) below.

## What it does

### `viva-tools harness report`

Parses a captured VIVA_TRACE log (see `vong2/03-contracts.md` §1 for the
exact line format) into per-stage latency stats — p50/p95/min/max over every
adjacent pipeline segment (`speech_start`→`speech_end`, `asr_sent`→`asr_done`,
...), plus the app's own reported `e2e_ms` for cross-checking. Outputs a CSV
and a stdout summary.

```
viva-tools harness report --input path/to/log.txt --out report.csv
viva-tools harness report --adb --serial <device-serial> --out report.csv

# Full evidence set for one run — aggregate + per-turn rows + verdict counts
viva-tools harness report --input run.log --variant full \
  --out report.csv --per-trace traces.csv --verdicts verdicts.csv
```

**Which number is "end-to-end".** `03-contracts.md` §1.3 defines it as
`speech_end → tts_start`, reported as `e2e_computed`. That is the metric the
p95 < 1500 ms commitment is made on. `screen_latency` is the same window
measured to `render_done` instead. The two `*_incl_speech` rows start at
`speech_start` and therefore include however long the driver spoke — they are
kept for continuity with earlier reports and **must not** be quoted as
end-to-end. `e2e_reported_minus_computed` cross-checks the app's own figure
against the harness's recomputation; a non-zero spread means the two
definitions have drifted.

Try it against the bundled fixture, or against the golden logs Long handed
over (which `go test` also asserts on):

```
go run ./cmd/viva-tools harness report --input testdata/sample_trace.log --out report.csv
go run ./cmd/viva-tools harness report --input ../android/voice/fixtures/golden_trace.log --out report.csv
```

### `viva-tools harness compare`

Before/after table for an ablation run (N4a/N4b): same metrics, two captures,
plus the change in verdict counts — which is how "turn SafetyGuard off and
`Deny:G1_SPEED_LOCK` stops firing" becomes a table instead of a hand-replayed
demo.

```
viva-tools harness compare --baseline full.log --candidate no_guard.log \
  --baseline-label guard_on --candidate-label guard_off \
  --out compare.csv --verdicts-out verdicts_compare.csv
```

A metric that exists in only one run is still printed, marked
`not comparable` — in an ablation the disappearance *is* the finding (drop the
`VhalRepository` callback and `hmi_render` never fires at all).

### `viva-tools harness verify`

Runs a benchmark suite against a capture and reports PASS/FAIL per utterance
(V11), plus the p50/p95 over the turns that produced one (V10/V12).

```
viva-tools harness verify --suite suites/benchmark_v1.csv --input run.log \
  --variant quiet --out results.csv --summary-out summary.csv \
  --evidence-dir screenshots/
```

`suites/benchmark_v1.csv` is the shipped 22-utterance suite: the ten core
intents of `03-contracts.md` §3, the five M7 complex situations, the wrong
wake phrase and one of the five commands cut on 29/07.

| Column | Meaning |
|---|---|
| `id` | Stable case id; also the filename stem the results join evidence on |
| `utterance` | What the demo operator says |
| `expect_intent` / `expect_verdict` | Target behaviour, in `§1.2` verdict grammar. A bare kind (`Deny`) accepts any rule; `Deny:G1_SPEED_LOCK` pins the rule |
| `evidence_id` | Screenshot stem looked up under `--evidence-dir` |
| `gate` | Non-empty = blocked on work that has not landed. A failure here is a **known gap**, counted separately and does not make the run red |

Two matching modes. `--match order` (default) pairs the Nth case with the Nth
turn, because the utterance in the log is *what ASR heard*, not what was said —
matching on text would silently drop every misrecognized turn, which is
precisely the data a benchmark exists to measure. `--match utterance` pairs on
normalized text when turns were captured out of order.

Exit code is 1 only when an **ungated** case fails, so this is safe to run in
CI while T5/D7 are still landing.

### `scripts/run_benchmark.ps1`

One run, all artifacts, plus a `run_manifest.txt` carrying the commit, the
suite hash and whether the worktree was dirty — a p95 with no commit next to it
cannot be defended in a write-up.

```powershell
.\scripts\run_benchmark.ps1 -Variant quiet   -Log D:\runs\quiet.log
.\scripts\run_benchmark.ps1 -Variant highway -Adb -Serial <device-serial>
```

For V12 (20 utterances × 3 noise levels) run it once per noise level and
concatenate the three `summary.csv` files.

### `viva-tools carsky ...`

Thin wrapper over the confirmed CarSky REST endpoints
(`docs/platform/Car-Sky-Platform.html`, base path `/api/v1`, auth
`Authorization: Bearer <token>`):

```
viva-tools carsky blueprint export --id <blueprintId> --out backup.json
viva-tools carsky blueprint clone  --id <blueprintId> --backup-out backup.json --clone-out clone.json
viva-tools carsky nodes            --room <roomId> [--out nodes.json]
viva-tools carsky adb-tunnel       --room <roomId>
```

`blueprint clone` implements the safe-editing procedure from
`vong2/04-KE-HOACH-CAP-NHAT-28-07.md` ("An toàn khi sửa blueprint"): it
**always exports a backup before cloning**, and refuses to clone if the
backup export fails.

Response bodies for `nodes`/`adb-tunnel`/blueprint export/clone are printed
or saved as **raw JSON**, not typed structs — the docs confirm the endpoint
paths and auth scheme but not the exact response schema. There's a live
`GET /api/v1/openapi` on the platform; once someone has pulled the real
schema, tighten `internal/infrastructure/carsky` to use typed responses
instead of guessing field names.

**Fault tolerance:** GET requests (`nodes`, `adb-tunnel`, `blueprint export`)
retry up to 3 times with linear backoff on network errors or 5xx responses,
and fail immediately (no retry) on 4xx — a bad token or bad id won't get
fixed by asking again. `blueprint clone` (POST) never auto-retries, since a
duplicate clone on CarSky is worse than a failed call you retry by hand.
Every HTTP call has a configurable timeout (`CARSKY_TIMEOUT_SECONDS`,
default 30s) and `adb logcat` calls time out after 30s so a wedged
device/tunnel can't hang the CLI. See `client_test.go` for the tests that
pin this behavior down (retry counts, no-retry-on-4xx, no-retry-on-POST).

## Configuration

Copy `.env.example` to `.env` and fill in `CARSKY_API_TOKEN` (and
`CARSKY_ROOM_ID` if you want a default room). `.env` is git-ignored.

`CARSKY_BASE_URL` in `.env.example` is a **best guess** — `docs/link.md`
only records the CarSky web UI URL, not necessarily the API host. Confirm
before relying on it.

## Build & test

```
go build ./...       # or: make build
go vet ./...
go test ./...         # or: make test
```

No external dependencies — stdlib only (deliberate, for a hackathon: no
`go mod tidy` network dependency, no vendoring concerns, single static
binary).

## Architecture (clean architecture layering)

```
cmd/viva-tools/            entrypoint — wires nothing itself, just calls cli.Run
internal/domain/           entities + pure parsing rules, zero external deps
internal/usecase/          application logic (harness aggregation/report, devops SafeClone)
internal/interfaces/
  repository/               ports (interfaces) usecases depend on
  cli/                       composition root + presentation (arg parsing, stdout/CSV/JSON formatting)
internal/infrastructure/
  logsource/                 LineSource: file/stdin, or live `adb logcat`
  carsky/                    CarSkyGateway: HTTP client over the CarSky API
  report/                     CSV writer
internal/config/            env-var (+ optional .env) configuration loading
```

Dependency direction is inward only: `domain` knows nothing about anyone;
`usecase` depends on `domain` and on the `repository` interfaces (never on
`infrastructure` directly); `infrastructure` implements those interfaces;
`cli` is the only place that imports concrete infrastructure types and
wires them into usecases.

## Scope: why this is not a Command Dispatcher

The root `PLAN.md`/`CLAUDE.md` in this repo describe an older architecture
(Python/Node "Command Dispatcher" backend, 3-tier intent router with a cloud
LLM fallback). That was cut in the 28/07 replan — see
`vong2/04-KE-HOACH-CAP-NHAT-28-07.md` (Phần 4) and `vong2/03-contracts.md`.
Dispatch, the safety guard, and all four skills (including delivery) now run
in-app as Kotlin. The only backend surface left is: `viva-asr` (a separate
Python service — deliberately not built in Go, see the ASR note below),
this benchmark harness, and CarSky devops plumbing.

**ASR (`viva-asr`) is intentionally not part of this Go project.** Serving
whisper-tiny/PhoWhisper INT8 inference is far better supported in Python
today (`faster-whisper`/CTranslate2) than via Go ONNX bindings, and there's
no time budget left in the hackathon to de-risk an unusual toolchain choice.
