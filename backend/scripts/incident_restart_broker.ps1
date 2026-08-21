# Incident response 21/08: BCM Gateway ket vong lap provider "AlreadyExists".
# 1) Chup baseline phase + log CUA CA 22 NODE truoc khi dung toi bat cu thu gi.
# 2) Restart node Central Broker (VSS) de xoa provider registration con sot.
# 3) Theo doi 3 phut: BCM het AlreadyExists chua, co node nao roi khoi Running khong.

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot  = 'D:\fpt-automative-hackathon'
$OutDir    = Join-Path $RepoRoot 'evidence\carsky\broker-restart-20260821'
$BeforeDir = Join-Path $OutDir 'before'
$AfterDir  = Join-Path $OutDir 'after'
New-Item -ItemType Directory -Force -Path $BeforeDir, $AfterDir | Out-Null

$envMap = @{}
Get-Content (Join-Path $RepoRoot 'backend\.env') -Encoding UTF8 | ForEach-Object {
    $t = $_.Trim(); if ($t -eq '' -or $t.StartsWith('#')) { return }
    $i = $t.IndexOf('='); if ($i -lt 1) { return }
    $envMap[$t.Substring(0, $i).Trim()] = $t.Substring($i + 1).Trim()
}
$base = $envMap['CARSKY_BASE_URL'].TrimEnd('/')
$room = $envMap['CARSKY_ROOM_ID']
$h    = @{ Authorization = "Bearer $($envMap['CARSKY_API_TOKEN'])"; Accept = 'application/json' }

function Get-Nodes {
    $r = Invoke-WebRequest -Method GET -Uri "$base/deployments/$room/nodes" -Headers $h -SkipHttpErrorCheck -TimeoutSec 60
    if ([int]$r.StatusCode -ge 400) { throw "GET nodes: HTTP $([int]$r.StatusCode) $($r.Content)" }
    return $r.Content | ConvertFrom-Json
}

# BAY: goi GET /logs/{node} KHONG kem ?container tra HTTP 200 nhung do la log cua
# INIT CONTAINER (2 dong "[dbc] Downloading ... .dbc"), khong phai log cua script.
# Khong phai loi nen fallback "doc container tu thong bao loi" khong bao gio chay.
# => Voi script-node phai truyen thang ?container=script-node.
function Get-NodeLog([string] $nodeKey, [string] $nodeType) {
    if ($nodeType -eq 'script-node') {
        $r = Invoke-WebRequest -Method GET -Uri "$base/deployments/$room/logs/$nodeKey`?container=script-node" -Headers $h -SkipHttpErrorCheck -TimeoutSec 60
        return $r.Content
    }
    # Cac nodeType khac chua biet ten container: doc tu chinh thong bao loi
    # ("choose one of: [a b]") roi thu lai voi ten dau tien.
    $r = Invoke-WebRequest -Method GET -Uri "$base/deployments/$room/logs/$nodeKey" -Headers $h -SkipHttpErrorCheck -TimeoutSec 60
    $m = [regex]::Match($r.Content, 'choose one of: \[([^\]]+)\]')
    if (-not $m.Success) { return $r.Content }
    $container = ($m.Groups[1].Value -split ' ')[0]
    $r2 = Invoke-WebRequest -Method GET -Uri "$base/deployments/$room/logs/$nodeKey`?container=$container" -Headers $h -SkipHttpErrorCheck -TimeoutSec 60
    return $r2.Content
}

function Save-Snapshot([string] $dir, $nodes) {
    $nodes | ConvertTo-Json -Depth 8 | Set-Content -Path (Join-Path $dir 'nodes.json') -Encoding UTF8
    foreach ($n in $nodes) {
        $safe = ($n.name -replace '[^A-Za-z0-9._-]', '_')
        try { Get-NodeLog $n.name $n.nodeType | Set-Content -Path (Join-Path $dir "log-$safe.json") -Encoding UTF8 }
        catch { "FETCH FAILED: $_" | Set-Content -Path (Join-Path $dir "log-$safe.json") -Encoding UTF8 }
    }
}

function Get-MatchCount([string] $dir, [string] $nodeKey, [string] $pattern) {
    $safe = ($nodeKey -replace '[^A-Za-z0-9._-]', '_')
    $p = Join-Path $dir "log-$safe.json"
    if (-not (Test-Path $p)) { return -1 }
    return ([regex]::Matches((Get-Content $p -Raw), $pattern)).Count
}

Write-Host "=== BASELINE $((Get-Date).ToUniversalTime().ToString('s'))Z"
$nodesBefore = Get-Nodes
Write-Host "  $($nodesBefore.Count) node, khong Running: $(@($nodesBefore | Where-Object { $_.phase -ne 'Running' }).Count)"
Save-Snapshot $BeforeDir $nodesBefore
Write-Host "  da luu vao $BeforeDir"

$bcmKey    = ($nodesBefore | Where-Object { $_.displayName -eq 'BCM Gateway' }).name
$brokerKey = ($nodesBefore | Where-Object { $_.displayName -eq 'Central Broker (VSS)' }).name
Write-Host "  BCM Gateway          : $bcmKey"
Write-Host "  Central Broker (VSS) : $brokerKey"
Write-Host "  AlreadyExists trong log BCM truoc khi restart: $(Get-MatchCount $BeforeDir $bcmKey 'AlreadyExists')"
if (-not $brokerKey) { throw 'Khong tim thay node Central Broker (VSS) — dung lai.' }

Write-Host ""
Write-Host "=== RESTART broker $((Get-Date).ToUniversalTime().ToString('s'))Z"
$r = Invoke-WebRequest -Method POST -Uri "$base/deployments/$room/restart/$brokerKey" -Headers $h -SkipHttpErrorCheck -TimeoutSec 60
Write-Host "  POST restart/$brokerKey -> HTTP $([int]$r.StatusCode)  (500 body rong la binh thuong)"

Write-Host ""
Write-Host "=== THEO DOI"
foreach ($i in 1..12) {
    Start-Sleep -Seconds 15
    $nodes  = Get-Nodes
    $bad    = @($nodes | Where-Object { $_.phase -ne 'Running' })
    $bcmLog = Get-NodeLog $bcmKey 'script-node'
    $ae     = ([regex]::Matches($bcmLog, 'AlreadyExists')).Count
    $lines  = ($bcmLog | ConvertFrom-Json).lines
    $lastTs = if ($lines) { ($lines[-1] -split ' ')[0].TrimStart('[') } else { '?' }
    $badTxt = if ($bad.Count -eq 0) { 'tat ca Running' } else { ($bad | ForEach-Object { "$($_.displayName)=$($_.phase)" }) -join ', ' }
    Write-Host ("  [{0,3}s] {1,-42} | BCM AlreadyExists={2,-3} dong cuoi={3}" -f ($i*15), $badTxt, $ae, $lastTs)
}

Write-Host ""
Write-Host "=== SNAPSHOT SAU $((Get-Date).ToUniversalTime().ToString('s'))Z"
$nodesAfter = Get-Nodes
Save-Snapshot $AfterDir $nodesAfter
Write-Host "  da luu vao $AfterDir"
Write-Host ""
Write-Host "  AlreadyExists trong log BCM Gateway:  truoc=$(Get-MatchCount $BeforeDir $bcmKey 'AlreadyExists')  sau=$(Get-MatchCount $AfterDir $bcmKey 'AlreadyExists')"
Write-Host "  Node khong Running sau khi restart broker:"
$after = @($nodesAfter | Where-Object { $_.phase -ne 'Running' })
if ($after.Count -eq 0) { Write-Host "    (khong co)" }
else { $after | ForEach-Object { Write-Host "    $($_.displayName) = $($_.phase)  msg=$($_.message)" } }
