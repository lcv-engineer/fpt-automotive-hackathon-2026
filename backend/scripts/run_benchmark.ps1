<#
.SYNOPSIS
  One benchmark run: capture (optional), report, verify, and stamp identity.

.DESCRIPTION
  Produces the artifact set V10/V12 ask for, in one directory per run:

    report.csv        aggregate latency (p50/p95 per stage)
    traces.csv        one row per turn
    verdicts.csv      verdict/rule counts
    results.csv       PASS/FAIL per suite case
    summary.csv       one line — concatenate these across noise levels
    run_manifest.txt  commit, time, variant, suite, log — N6 artifact identity
    capture.log       the raw log this run was computed from

  The manifest exists because a p95 with no commit next to it cannot be
  defended in a write-up. Every number in the CSVs traces back to capture.log,
  and capture.log traces back to a commit.

.EXAMPLE
  # From a log already captured off the Device
  .\run_benchmark.ps1 -Variant quiet -Log D:\runs\quiet.log

.EXAMPLE
  # Capture from a connected Device (open the CarSky adb tunnel first — V5)
  .\run_benchmark.ps1 -Variant highway -Adb -Serial emulator-5554
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Variant,

    [string]$Log,
    [switch]$Adb,
    [string]$Serial = "",
    [string]$Suite = "suites/benchmark_v1.csv",
    [string]$OutRoot = "runs",
    [string]$EvidenceDir = ""
)

$ErrorActionPreference = "Stop"
$backend = Split-Path -Parent $PSScriptRoot
Push-Location $backend

try {
    if (-not $Adb -and -not $Log) {
        throw "Need -Log <path> or -Adb"
    }

    # Vietnamese utterances are evidence (03-contracts.md §1.1); a console in
    # the OEM code page turns them into mojibake in every CSV written here.
    $OutputEncoding = [System.Text.UTF8Encoding]::new()
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $outDir = Join-Path $OutRoot "$stamp-$Variant"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $capture = Join-Path $outDir "capture.log"
    if ($Adb) {
        $adbArgs = @()
        if ($Serial) { $adbArgs += @("-s", $Serial) }
        $adbArgs += @("logcat", "-d", "-s", "VIVA_TRACE:I")
        Write-Host "Capturing: adb $($adbArgs -join ' ')"
        & adb @adbArgs | Out-File -FilePath $capture -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw "adb logcat failed with exit code $LASTEXITCODE" }
    }
    else {
        Copy-Item -Path $Log -Destination $capture
    }

    go run ./cmd/viva-tools harness report `
        --input $capture --variant $Variant `
        --out (Join-Path $outDir "report.csv") `
        --per-trace (Join-Path $outDir "traces.csv") `
        --verdicts (Join-Path $outDir "verdicts.csv")

    $verifyArgs = @(
        "run", "./cmd/viva-tools", "harness", "verify",
        "--suite", $Suite, "--input", $capture, "--variant", $Variant,
        "--out", (Join-Path $outDir "results.csv"),
        "--summary-out", (Join-Path $outDir "summary.csv")
    )
    if ($EvidenceDir) { $verifyArgs += @("--evidence-dir", $EvidenceDir) }
    & go @verifyArgs
    # Exit code 1 here means an ungated case regressed. That is a finding to
    # record, not a reason to throw away the run's artifacts.
    $verifyExit = $LASTEXITCODE

    $commit = (& git rev-parse --short HEAD 2>$null)
    $dirty = if ((& git status --porcelain) -ne $null) { "DIRTY — numbers are not reproducible from this commit" } else { "clean" }
    $suiteHash = (Get-FileHash -Path $Suite -Algorithm SHA256).Hash.Substring(0, 12)

    @(
        "variant       : $Variant"
        "captured_at   : $(Get-Date -Format o)"
        "git_commit    : $commit"
        "worktree      : $dirty"
        "suite         : $Suite (sha256 $suiteHash)"
        "log_source    : $(if ($Adb) { "adb logcat $Serial" } else { $Log })"
        "verify_exit   : $verifyExit (0 = no ungated regression)"
        ""
        "ASR model/version and APK build id are NOT filled in automatically —"
        "add them by hand before this run is quoted anywhere (N6 identity)."
    ) | Out-File -FilePath (Join-Path $outDir "run_manifest.txt") -Encoding utf8

    Write-Host ""
    Write-Host "Artifacts -> $outDir"
    Get-ChildItem $outDir | Select-Object Name, Length | Format-Table | Out-Host
    exit $verifyExit
}
finally {
    Pop-Location
}
