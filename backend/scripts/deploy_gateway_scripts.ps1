<#
.SYNOPSIS
  Day noi dung GATEWAY/*.lua len 2 script-node cua CarSky (IVI Gateway, BCM Gateway)
  roi restart tung node, va xac minh bang HE QUA QUAN SAT DUOC.

.DESCRIPTION
  Mac dinh chay DRY-RUN: chi doc, in ra cai gi se doi, KHONG ghi gi len nen tang.
  Phai truyen -Apply moi thuc su ghi.

  Cac bay da biet cua CarSky ma script nay xu ly san
  (nguon: docs/backend-docs/carsky-runbook.md, carsky-official-kb.md):

  1. Route sua node la PATCH /blueprints/nodes/{id}, KHONG phai /nodes/{id}
     (openapi.json khai thieu tien to /blueprints -> 404).           runbook muc 1
  2. POST .../restart/{node} tra 500 voi body RONG nhung VAN CHAY.
     Script khong coi 500 la loi; no phan xet bang `phase` cua node. runbook muc 3
  3. Co HAI blueprint trung ten VIVA-deploy-clone-0803. Ban 7175eb09
     KHONG co node ASR. Script kiem tra node id co that trong blueprint
     dang nham toi truoc khi ghi.                                    runbook muc 0
  4. nodeKey co nhieu he dinh danh (UUID / <room>-nN / key). Script KHONG doan:
     no lay chuoi `name` nguyen van tu GET /deployments/{room}/nodes
     de goi restart.                                                 kb muc 8

  Script nay KHONG bao gio goi DELETE /deployments. Neu restart node khong du
  de nap lai script, phai redeploy nguyen room BANG TAY va chap nhan mat VM
  Android (APK + cau hinh eth1) — xem runbook muc 2.

.PARAMETER Apply
  Thuc su ghi len CarSky. Khong co co nay thi chi dry-run.

.PARAMETER SkipRestart
  Ghi blueprint xong thi dung, khong restart node.

.PARAMETER EnvFile
  Duong dan .env. Mac dinh: backend/.env

.EXAMPLE
  pwsh backend/scripts/deploy_gateway_scripts.ps1
  # dry-run: xem truoc thay doi, khong ghi gi

.EXAMPLE
  pwsh backend/scripts/deploy_gateway_scripts.ps1 -Apply
#>

[CmdletBinding()]
param(
    [switch] $Apply,
    [switch] $SkipRestart,
    [string] $EnvFile
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ── Duong dan ───────────────────────────────────────────────────────
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $EnvFile) { $EnvFile = Join-Path $RepoRoot 'backend\.env' }

$BlueprintId = '6deadb05-c856-4dab-976b-432b0fac0658'

# displayName phai khop chuoi trong GET /deployments/{room}/nodes.
$Targets = @(
    [pscustomobject]@{
        NodeId      = '4e60c4fe-350e-4333-9e50-0bcd5596a609'
        DisplayName = 'IVI Gateway'
        LocalFile   = Join-Path $RepoRoot 'GATEWAY\IVI_GATEWAY.lua'
    },
    [pscustomobject]@{
        NodeId      = '20f50062-dda7-4ebf-a5f7-8bb3a00c00d1'
        DisplayName = 'BCM Gateway'
        LocalFile   = Join-Path $RepoRoot 'GATEWAY\BCM_GATEWAY.lua'
    }
)

# ── Helper ──────────────────────────────────────────────────────────

function Read-DotEnv([string] $Path) {
    if (-not (Test-Path $Path)) { throw "Khong thay $Path" }
    $map = @{}
    foreach ($line in Get-Content -Path $Path -Encoding UTF8) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $i = $t.IndexOf('=')
        if ($i -lt 1) { continue }
        $map[$t.Substring(0, $i).Trim()] = $t.Substring($i + 1).Trim()
    }
    return $map
}

# Tra ve [pscustomobject]@{ Status; Body } — KHONG throw tren HTTP >= 400,
# vi CarSky tra 500 cho restart nhung lenh van chay (runbook muc 3).
function Invoke-CarSky {
    param(
        [Parameter(Mandatory)] [string] $Method,
        [Parameter(Mandatory)] [string] $Path,
        [string] $JsonBody
    )
    $uri = "$script:BaseUrl$Path"
    $headers = @{
        'Authorization' = "Bearer $script:Token"
        'Accept'        = 'application/json'
    }
    # KHONG dat ten bien la $args — do la bien tu dong cua PowerShell.
    $reqArgs = @{
        Method             = $Method
        Uri                = $uri
        Headers            = $headers
        SkipHttpErrorCheck = $true
        TimeoutSec         = 60
    }
    if ($PSBoundParameters.ContainsKey('JsonBody') -and $JsonBody) {
        # Ep UTF-8 tuong minh: cac file lua co ky tu non-ASCII (mui ten, emoji).
        $reqArgs['Body']        = [System.Text.Encoding]::UTF8.GetBytes($JsonBody)
        $reqArgs['ContentType'] = 'application/json; charset=utf-8'
    }
    $resp = Invoke-WebRequest @reqArgs
    return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $resp.Content }
}

# Tra ve ca Raw (nguyen van) lan Data (da parse). Backup phai ghi Raw:
# ConvertFrom-Json roi ConvertTo-Json lai se doi kieu (vd exportedAt thanh
# DateTime cua .NET) — file backup do khong con la thu nen tang tra ve nua.
function Get-BlueprintExport {
    $r = Invoke-CarSky -Method GET -Path "/blueprints/$BlueprintId/export"
    if ($r.Status -ge 400) { throw "Export blueprint that bai: HTTP $($r.Status) — $($r.Body)" }
    return [pscustomobject]@{
        Raw  = $r.Body
        Data = $r.Body | ConvertFrom-Json -Depth 64
    }
}

function Get-DeploymentNodes {
    $r = Invoke-CarSky -Method GET -Path "/deployments/$script:RoomId/nodes"
    if ($r.Status -ge 400) { throw "Doc node cua room that bai: HTTP $($r.Status) — $($r.Body)" }
    return $r.Body | ConvertFrom-Json -Depth 16
}

# Nen ve LF: ban tren nen tang dung LF, working copy co the la CRLF do autocrlf.
# Khong nen thi lan nao so sanh cung "khac nhau".
function ConvertTo-Lf([string] $s) { return $s -replace "`r`n", "`n" }

function Write-Section([string] $Text) {
    Write-Host ''
    Write-Host "── $Text " -NoNewline
    Write-Host ('─' * [Math]::Max(0, 60 - $Text.Length))
}

# ── 0. Cau hinh ─────────────────────────────────────────────────────

Write-Section '0. Cau hinh'

$envMap = Read-DotEnv $EnvFile
foreach ($k in @('CARSKY_BASE_URL', 'CARSKY_API_TOKEN', 'CARSKY_ROOM_ID')) {
    if (-not $envMap.ContainsKey($k) -or [string]::IsNullOrWhiteSpace($envMap[$k])) {
        throw "$k chua duoc dat trong $EnvFile"
    }
}
$script:BaseUrl = $envMap['CARSKY_BASE_URL'].TrimEnd('/')
$script:Token   = $envMap['CARSKY_API_TOKEN']   # KHONG bao gio in ra
$script:RoomId  = $envMap['CARSKY_ROOM_ID']

Write-Host "  base url    : $script:BaseUrl"
Write-Host "  room        : $script:RoomId"
Write-Host "  blueprint   : $BlueprintId"
Write-Host "  token       : <doc tu $EnvFile, khong in>"
Write-Host "  che do      : $(if ($Apply) { 'APPLY — SE GHI LEN CARSKY' } else { 'DRY-RUN — khong ghi gi' })"

foreach ($t in $Targets) {
    if (-not (Test-Path $t.LocalFile)) { throw "Khong thay file: $($t.LocalFile)" }
}

# ── 1. Backup blueprint hien tai ────────────────────────────────────

Write-Section '1. Backup blueprint truoc khi dung toi'

$stamp     = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDir = Join-Path $RepoRoot 'backend\carsky'
$backupPath = Join-Path $backupDir "blueprint-6deadb05-backup-$stamp.json"

$export = Get-BlueprintExport
$before = $export.Data
[System.IO.File]::WriteAllText($backupPath, $export.Raw, (New-Object System.Text.UTF8Encoding $false))
Write-Host "  exportedAt  : $($before.exportedAt)"
Write-Host "  node/edge   : $($before.blueprint.nodes.Count) / $($before.blueprint.edges.Count)"
Write-Host "  backup      : $backupPath (nguyen van response)"

# Bay 3: dam bao dang nham dung blueprint, khong phai ban 7175eb09.
foreach ($t in $Targets) {
    $node = $before.blueprint.nodes | Where-Object { $_.id -eq $t.NodeId }
    if (-not $node) {
        throw "Node $($t.DisplayName) ($($t.NodeId)) KHONG co trong blueprint $BlueprintId. Dung lai — co the dang nham blueprint."
    }
    if ($node.nodeType -ne 'script-node') {
        throw "Node $($t.DisplayName) co nodeType='$($node.nodeType)', khong phai script-node. Dung lai."
    }
}
Write-Host "  -> ca $($Targets.Count) node deu co that va la script-node."

# ── 2. So sanh local vs nen tang ────────────────────────────────────

Write-Section '2. So sanh file trong repo vs script tren nen tang'

$plan = @()
foreach ($t in $Targets) {
    $node    = $before.blueprint.nodes | Where-Object { $_.id -eq $t.NodeId }
    $remote  = ConvertTo-Lf ([string]$node.config.scriptContent)
    $local   = ConvertTo-Lf (Get-Content -Path $t.LocalFile -Raw -Encoding UTF8)

    $same       = ($remote -eq $local)
    $guardLocal = ([regex]::Matches($local,  'SAFETY GUARD')).Count
    $guardRemote= ([regex]::Matches($remote, 'SAFETY GUARD')).Count

    Write-Host ''
    Write-Host "  $($t.DisplayName)  [$($t.NodeId)]"
    Write-Host "    file        : $($t.LocalFile)"
    Write-Host "    dong local  : $(($local  -split "`n").Count)   | 'SAFETY GUARD' x $guardLocal"
    Write-Host "    dong remote : $(($remote -split "`n").Count)   | 'SAFETY GUARD' x $guardRemote"
    Write-Host "    trang thai  : $(if ($same) { 'GIONG NHAU — khong can ghi' } else { 'KHAC — se ghi de' })"

    $plan += [pscustomobject]@{
        Target = $t
        Node   = $node
        Local  = $local
        Same   = $same
    }
}

$toWrite = @($plan | Where-Object { -not $_.Same })
if ($toWrite.Count -eq 0) {
    Write-Host ''
    Write-Host '  Nen tang da khop repo. Khong co gi de ghi.'
    if (-not $SkipRestart) {
        Write-Host '  (Van co the can restart neu node dang chay ban cu — chay lai voi -Apply neu muon.)'
    }
    if (-not $Apply) { return }
}

if (-not $Apply) {
    Write-Host ''
    Write-Host '  DRY-RUN — dung tai day. Them -Apply de thuc su ghi.'
    return
}

# ── 3. PATCH blueprint ──────────────────────────────────────────────

Write-Section '3. PATCH /blueprints/nodes/{id}'

foreach ($p in $toWrite) {
    # Doc-sua-ghi ca cum config de khong lam mat key nao khac.
    $cfg = @{}
    foreach ($prop in $p.Node.config.PSObject.Properties) { $cfg[$prop.Name] = $prop.Value }
    $cfg['script']        = 'inline'
    $cfg['scriptContent'] = $p.Local

    $body = @{ config = $cfg } | ConvertTo-Json -Depth 16 -Compress
    $r = Invoke-CarSky -Method PATCH -Path "/blueprints/nodes/$($p.Target.NodeId)" -JsonBody $body

    Write-Host "  $($p.Target.DisplayName): HTTP $($r.Status)"
    if ($r.Status -ge 400) {
        throw "PATCH that bai cho $($p.Target.DisplayName): HTTP $($r.Status) — $($r.Body)"
    }
}

# ── 4. Xac minh ghi that su lanh ────────────────────────────────────

Write-Section '4. Export lai de xac minh (khong tin ma HTTP)'

$after = (Get-BlueprintExport).Data
$allOk = $true
foreach ($p in $plan) {
    $node   = $after.blueprint.nodes | Where-Object { $_.id -eq $p.Target.NodeId }
    $remote = ConvertTo-Lf ([string]$node.config.scriptContent)
    $ok     = ($remote -eq $p.Local)
    if (-not $ok) { $allOk = $false }
    $guards = ([regex]::Matches($remote, 'SAFETY GUARD')).Count
    Write-Host "  $($p.Target.DisplayName): khop repo = $ok | 'SAFETY GUARD' tren nen tang x $guards"
}
if (-not $allOk) {
    throw 'Blueprint sau khi PATCH KHONG khop file trong repo. Dung lai, dung restart. Backup o: ' + $backupPath
}
Write-Host '  -> blueprint da mang script moi.'

if ($SkipRestart) {
    Write-Host ''
    Write-Host '  -SkipRestart: dung tai day. Node dang chay VAN dung script cu.'
    return
}

# ── 5. Restart tung node ────────────────────────────────────────────

Write-Section '5. Restart node (500 khong phai loi — xet bang phase)'

$nodes = Get-DeploymentNodes
$restartKeys = @{}
foreach ($t in $Targets) {
    $live = $nodes | Where-Object { $_.displayName -eq $t.DisplayName }
    if (-not $live) {
        throw "Khong thay node '$($t.DisplayName)' trong room $script:RoomId. displayName co the da doi — kiem bang: viva-tools carsky nodes"
    }
    if ($live -is [array]) { throw "Co $($live.Count) node ten '$($t.DisplayName)' trong room. Dung lai, phan giai bang tay." }
    $restartKeys[$t.DisplayName] = $live.name
    Write-Host "  $($t.DisplayName): restart key = '$($live.name)' (phase hien tai: $($live.phase))"
}

foreach ($t in $Targets) {
    $key = $restartKeys[$t.DisplayName]
    $r = Invoke-CarSky -Method POST -Path "/deployments/$script:RoomId/restart/$key"
    $note = if ($r.Status -eq 500) { '  (500 body rong la binh thuong — runbook muc 3)' } else { '' }
    Write-Host "  POST restart/$key -> HTTP $($r.Status)$note"
}

# ── 6. Cho phase quay ve Running ────────────────────────────────────

Write-Section '6. Cho node Running (toi da 180s)'

$deadline = (Get-Date).AddSeconds(180)
$names    = $Targets | ForEach-Object { $_.DisplayName }
do {
    Start-Sleep -Seconds 10
    $nodes  = Get-DeploymentNodes
    $watch  = $nodes | Where-Object { $names -contains $_.displayName }
    $line   = ($watch | ForEach-Object { "$($_.displayName)=$($_.phase)" }) -join '  '
    $left   = [int]($deadline - (Get-Date)).TotalSeconds
    Write-Host "  [${left}s con lai] $line"
    $done = @($watch | Where-Object { $_.phase -ne 'Running' }).Count -eq 0
} while (-not $done -and (Get-Date) -lt $deadline)

Write-Host ''
if ($done) {
    Write-Host '  Ca hai node da Running.'
} else {
    Write-Host '  HET GIO ma chua Running het. Xem message cua node:'
    $nodes | Where-Object { $names -contains $_.displayName } |
        ForEach-Object { Write-Host "    $($_.displayName): phase=$($_.phase) message=$($_.message)" }
    Write-Host "  Backup blueprint truoc khi sua: $backupPath"
}

# ── 7. Viec con lai phai lam bang tay ───────────────────────────────

Write-Section '7. Con lai — script khong tu kiem duoc'

Write-Host @"
  Node Running KHONG chung minh script moi da duoc nap. Phai xac minh bang
  hanh vi quan sat duoc:

  a) Guard G1.1 — keo slider Drive Controls cho toc do > 0, roi ra lenh mo khoa
     cua tu app. Cua KHONG duoc mo. Doc log node de thay dong
     '[SAFETY GUARD G1.1 BLOCKED]':
       GET $script:BaseUrl/deployments/$script:RoomId/logs/<nodeKey>?container=user

  b) Guard G1.2 — dat nhiet do 40 degC. Phai bi chan (gioi han 16-32).

  c) EV_BATTERY_LEVEL — hoi phan tram pin tren flavor 'real'. Phai ra so,
     khong con null (id da doi 0x11600204 -> 0x11600309).

  Neu ca ba deu khong doi gi: restart node KHONG du de nap lai script.
  Luc do phai redeploy nguyen room BANG TAY (runbook muc 2) va chap nhan
  mat VM Android — cai lai APK + chay lai khoi lenh mang eth1.

  Muon quay lai ban cu: PATCH lai scriptContent lay tu
    $backupPath
"@
