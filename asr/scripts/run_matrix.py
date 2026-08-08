#!/usr/bin/env python3
"""Run the full V13 benchmark matrix: 3 models × 2 biasing × 4 noise levels.

Automates the loop that Task 5 describes:
  for each model in tiny, base, small:
    for each biasing in bias, nobias:
      start container → wait /health → bench all noise levels → stop

Uses bench_noise_levels.py for the corpus, which handles interleaving,
warmup, and drift checks. Each (model, biasing) pair produces a full set
of CSV/SVG/manifest outputs in its own subdirectory.

    cd asr && python scripts/run_matrix.py

NOT stdlib-only — this is operational tooling, not measurement code.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

MODELS = ["tiny", "base", "small"]
BIASING = ["bias", "nobias"]
CONTAINER_NAME = "viva-asr-bench"
PORT = 8080
HEALTH_TIMEOUT = 180  # seconds to wait for model load


def wait_health(url: str, timeout: float) -> dict:
    """Poll GET /health until status=ok or timeout."""
    deadline = time.time() + timeout
    last_error = ""
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(f"{url}/health", timeout=5) as resp:
                body = json.loads(resp.read())
                if body.get("status") == "ok":
                    return body
                last_error = f"status={body.get('status')}"
        except (urllib.error.URLError, OSError, json.JSONDecodeError) as exc:
            last_error = str(exc)
        time.sleep(2)
    raise TimeoutError(f"/health not ready after {timeout}s: {last_error}")


def stop_container():
    subprocess.run(
        ["docker", "stop", CONTAINER_NAME],
        capture_output=True, timeout=30,
    )
    time.sleep(2)


def start_container(model: str, biasing: str, cpu_threads: int = 0) -> dict:
    """Start a viva-asr container and return /health body."""
    stop_container()

    image = f"viva-asr:{model}"
    env_args = []
    if biasing == "nobias":
        env_args += ["-e", "ASR_INITIAL_PROMPT="]
    if cpu_threads > 0:
        env_args += ["-e", f"ASR_CPU_THREADS={cpu_threads}"]

    cmd = [
        "docker", "run", "-d", "--rm",
        "-p", f"{PORT}:{PORT}",
        "--name", CONTAINER_NAME,
    ] + env_args + [image]

    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        raise RuntimeError(f"docker run failed: {result.stderr}")

    url = f"http://127.0.0.1:{PORT}"
    health = wait_health(url, HEALTH_TIMEOUT)
    print(f"  [OK] {image} ({biasing}) ready: {health.get('model')}")
    return health


def run_bench(
    url: str,
    corpus: Path,
    prompts: Path,
    out_dir: Path,
    label: str,
    timeout: float,
) -> int:
    """Run bench_noise_levels.py and return exit code."""
    scripts_dir = Path(__file__).resolve().parent
    # bench_noise_levels reads paths from corpus-index.csv which are relative to
    # the repo root, so we must run from there.
    repo_root = scripts_dir.parents[1]
    cmd = [
        sys.executable,
        str(scripts_dir / "bench_noise_levels.py"),
        "--url", url,
        "--corpus", str(corpus),
        "--prompts", str(prompts),
        "--out-dir", str(out_dir),
        "--label", label,
        "--timeout", str(timeout),
    ]
    result = subprocess.run(cmd, timeout=1200, cwd=str(repo_root))
    return result.returncode


def read_v12_csv(path: Path) -> list[dict]:
    """Read the v12-noise-levels.csv output."""
    if not path.exists():
        return []
    with path.open(encoding="utf-8") as f:
        return list(csv.DictReader(f))


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, round(fraction * len(ordered)) - 1))
    return ordered[index]


def aggregate_level(rows: list[dict], level: str) -> dict:
    """Compute summary stats for one noise level from bench CSV rows."""
    level_rows = [r for r in rows if r["level"] == level]
    if not level_rows:
        return {"n": 0}
    wers = [float(r["wer"]) for r in level_rows]
    server_ms = [int(r["server_ms"]) for r in level_rows if not r.get("error")]
    confidences = [float(r.get("confidence", 0)) for r in level_rows if not r.get("error")]
    errors = sum(1 for r in level_rows if r.get("error"))
    return {
        "n": len(level_rows),
        "errors": errors,
        "wer_mean": round(statistics.mean(wers), 4) if wers else 0,
        "wer_zero": sum(1 for w in wers if w == 0),
        "server_p50": round(percentile(server_ms, 0.50), 1) if server_ms else 0,
        "server_p95": round(percentile(server_ms, 0.95), 1) if server_ms else 0,
        "server_max": max(server_ms) if server_ms else 0,
        "confidence_mean": round(statistics.mean(confidences), 4) if confidences else 0,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", default="evidence/asr/corpus-human",
                        help="corpus dir with corpus-index.csv")
    parser.add_argument("--prompts", default="asr/scripts/corpus_prompts.tsv")
    parser.add_argument("--out-dir", default="evidence/asr/v13")
    parser.add_argument("--models", default=",".join(MODELS))
    parser.add_argument("--timeout", type=float, default=120.0)
    parser.add_argument("--cpu-threads", type=int, default=0,
                        help="override ASR_CPU_THREADS (0=let CT2 decide)")
    parser.add_argument("--skip-existing", action="store_true")
    args = parser.parse_args()

    corpus = Path(args.corpus)
    prompts = Path(args.prompts)
    out_base = Path(args.out_dir)
    out_base.mkdir(parents=True, exist_ok=True)
    models = [m.strip() for m in args.models.split(",")]
    url = f"http://127.0.0.1:{PORT}"

    # Verify images exist
    for model in models:
        result = subprocess.run(
            ["docker", "image", "inspect", f"viva-asr:{model}"],
            capture_output=True, timeout=10,
        )
        if result.returncode != 0:
            print(f"Image viva-asr:{model} not found — build it first.", file=sys.stderr)
            return 1

    levels = ["clean", "quiet", "cabin", "highway"]
    all_results: list[dict] = []

    for model in models:
        for biasing in BIASING:
            label = f"{model}-{biasing}"
            run_dir = out_base / label

            if args.skip_existing and (run_dir / "v12-noise-levels.csv").exists():
                print(f"\n  → {label} exists, loading cached results")
                rows = read_v12_csv(run_dir / "v12-noise-levels.csv")
                for level in levels:
                    stats = aggregate_level(rows, level)
                    all_results.append({"model": model, "biasing": biasing, "level": level, **stats})
                continue

            print(f"\n{'='*60}")
            print(f"  {label}")
            print(f"{'='*60}")

            try:
                health = start_container(model, biasing, args.cpu_threads)
            except (RuntimeError, TimeoutError) as exc:
                print(f"  [FAIL] Cannot start {label}: {exc}", file=sys.stderr)
                for level in levels:
                    all_results.append({
                        "model": model, "biasing": biasing, "level": level,
                        "n": 0, "error": str(exc),
                    })
                continue

            run_dir.mkdir(parents=True, exist_ok=True)
            rc = run_bench(url, corpus, prompts, run_dir, label, args.timeout)
            if rc != 0:
                print(f"  [FAIL] Bench failed for {label} (exit {rc})", file=sys.stderr)

            rows = read_v12_csv(run_dir / "v12-noise-levels.csv")
            for level in levels:
                stats = aggregate_level(rows, level)
                all_results.append({"model": model, "biasing": biasing, "level": level, **stats})

            stop_container()

    # Print summary table
    print(f"\n{'='*100}")
    print("  V13 MATRIX SUMMARY — corpus giong that 16 kHz, CPU may dev")
    print(f"{'='*100}")
    print(f"{'model':<8}{'bias':<8}{'level':<9}{'n':>4}{'err':>5}{'WER':>8}{'WER=0':>6}"
          f"{'p50ms':>8}{'p95ms':>8}{'maxms':>8}{'conf':>8}")
    print("-" * 100)
    for r in all_results:
        if r.get("n", 0) == 0:
            err_msg = r.get("error", "no data")[:40]
            print(f"{r['model']:<8}{r['biasing']:<8}{r['level']:<9}  ERROR: {err_msg}")
            continue
        print(f"{r['model']:<8}{r['biasing']:<8}{r['level']:<9}"
              f"{r.get('n',0):>4}{r.get('errors',0):>5}{r.get('wer_mean',0):>8.3f}"
              f"{r.get('wer_zero',0):>6}{r.get('server_p50',0):>8.0f}"
              f"{r.get('server_p95',0):>8.0f}{r.get('server_max',0):>8}"
              f"{r.get('confidence_mean',0):>8.3f}")

    # Selection rule
    print(f"\n{'='*100}")
    print("  SELECTION RULE (spec §4.1):")
    print("  Chon bac model LON NHAT co server_ms p95 < 1100ms tren clean+bias")
    print(f"{'='*100}")
    chosen = None
    for model in reversed(models):  # small → base → tiny
        clean_bias = [r for r in all_results
                      if r["model"] == model and r["biasing"] == "bias"
                      and r["level"] == "clean" and r.get("n", 0) > 0]
        if clean_bias:
            p95 = clean_bias[0].get("server_p95", 99999)
            wer = clean_bias[0].get("wer_mean", 1.0)
            status = "[OK] LOT" if p95 < 1100 else "[FAIL] VUOT"
            print(f"  {model}: p95={p95:.0f}ms  WER={wer:.3f}  {status}")
            if p95 < 1100 and chosen is None:
                chosen = model

    if chosen:
        print(f"\n  → CHON: phowhisper-{chosen}-int8")
    else:
        print("\n  [WARN] KHONG BAC NAO LOT — xem xet ASR_CPU_THREADS hoac bao doi")

    # Write summary CSV
    summary_path = out_base / "v13-matrix-summary.csv"
    if all_results:
        keys = ["model", "biasing", "level", "n", "errors", "wer_mean", "wer_zero",
                "server_p50", "server_p95", "server_max", "confidence_mean"]
        with summary_path.open("w", encoding="utf-8", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=keys, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(all_results)
        print(f"\nSummary CSV → {summary_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
