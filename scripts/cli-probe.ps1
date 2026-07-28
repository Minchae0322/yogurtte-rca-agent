<#
.SYNOPSIS
    claude CLI의 고정 오버헤드(C)를 실측하고, 프롬프트 캐시 경계를 판별한다.

.DESCRIPTION
    리포트의 `in`에는 CLI 자신의 짐(시스템 프롬프트·툴 정의 등)이 섞여 있다. 이 스크립트는
    ClaudeCliLlmClient와 **같은 조건**(중립 임시 디렉터리 + --model 고정)으로 CLI를 직접 띄워
    두 가지를 잰다.

      A. 빈 프롬프트 N회   -> 컨텍스트가 0일 때의 `in` = 오버헤드 C. 편차도 함께 본다.
                              C가 회차마다 흔들리면 "상수"로 취급할 수 없다는 뜻이다.
      B. 고유 대형 프롬프트 -> 사용자 입력이 input_tokens에 잡히는지 cache_creation에 잡히는지.
      C. B와 동일 프롬프트  -> 그 몫이 cache_read로 되돌아오는지(= 캐시에 실제로 쓰였는지).

    판정 (B/C):
      * B의 input_tokens가 프롬프트 크기만큼 뛴다  -> 사용자 턴은 캐시 밖.
      * B의 cache_creation이 뛴다                  -> 사용자 턴이 캐시에 쓰인다.
      * C에서 그 몫이 cache_read로 나타난다        -> 재조사 시 캐시 이득이 있다.

    자세한 맥락은 docs/measurement.md.

.EXAMPLE
    .\scripts\cli-probe.ps1
    .\scripts\cli-probe.ps1 -Model claude-opus-5 -EmptyRuns 5
#>
param(
    [string]$Model = "claude-opus-5",
    [string]$CliPath = "claude",
    [int]$EmptyRuns = 3,
    [int]$UniqueGuids = 400
)

$ErrorActionPreference = "Stop"

# ClaudeCliLlmClient.createSandbox()와 같은 이유로 레포 밖에서 띄운다: cwd의 CLAUDE.md/.claude/를
# 상속하면 프로브가 재는 오버헤드가 실제 조사의 오버헤드와 달라진다.
$sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ("rca-cli-probe-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $sandbox | Out-Null
Write-Host "sandbox : $sandbox"
Write-Host "model   : $Model"
Write-Host ""

function Invoke-Probe {
    param([string]$Label, [string]$PromptText)

    Push-Location $sandbox
    try {
        $started = Get-Date
        $raw = $PromptText | & $CliPath -p --model $Model --output-format json
        $elapsed = ((Get-Date) - $started).TotalSeconds
    } finally {
        Pop-Location
    }

    if (-not $raw) {
        Write-Warning "$Label : CLI가 빈 응답을 반환했다"
        return $null
    }

    $json = $raw | ConvertFrom-Json
    $u = $json.usage
    $fresh = 0; $read = 0; $create = 0; $out = 0
    if ($u) {
        if ($null -ne $u.input_tokens) { $fresh = [long]$u.input_tokens }
        if ($null -ne $u.cache_read_input_tokens) { $read = [long]$u.cache_read_input_tokens }
        if ($null -ne $u.cache_creation_input_tokens) { $create = [long]$u.cache_creation_input_tokens }
        if ($null -ne $u.output_tokens) { $out = [long]$u.output_tokens }
    }

    $reportedModel = $json.model
    if (-not $reportedModel) {
        if ($json.modelUsage) { $reportedModel = ($json.modelUsage | Get-Member -MemberType NoteProperty | Select-Object -First 1).Name }
    }
    if (-not $reportedModel) { $reportedModel = "(미보고)" }

    $cost = 0.0
    if ($null -ne $json.total_cost_usd) { $cost = [double]$json.total_cost_usd }
    $turns = -1
    if ($null -ne $json.num_turns) { $turns = [int]$json.num_turns }

    [PSCustomObject]@{
        Label    = $Label
        Chars    = $PromptText.Length
        Total_in = $fresh + $read + $create
        Fresh    = $fresh
        CacheRd  = $read
        CacheCr  = $create
        Out      = $out
        Turns    = $turns
        Cost     = [math]::Round($cost, 4)
        Sec      = [math]::Round($elapsed, 1)
        Model    = $reportedModel
    }
}

$results = @()

# --- A. 빈 프롬프트: 오버헤드 C ---
Write-Host "[A] 빈 프롬프트 x $EmptyRuns  (오버헤드 C 측정)"
for ($i = 1; $i -le $EmptyRuns; $i++) {
    $r = Invoke-Probe -Label "empty#$i" -PromptText "Reply with exactly: OK"
    if ($r) { $results += $r; Write-Host ("  #{0}  in={1,7:N0}  fresh={2,6:N0}  read={3,7:N0}  create={4,7:N0}" -f $i, $r.Total_in, $r.Fresh, $r.CacheRd, $r.CacheCr) }
}

# --- B/C. 고유 대형 프롬프트: 캐시 경계 판별 ---
# 매 실행 새로 만든 GUID라 이 텍스트는 어떤 캐시에도 존재한 적이 없다.
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("Below is opaque reference data. Reply with exactly: OK")
for ($i = 0; $i -lt $UniqueGuids; $i++) {
    [void]$sb.AppendLine([System.Guid]::NewGuid().ToString())
}
$bigPrompt = $sb.ToString()

Write-Host ""
Write-Host ("[B] 고유 대형 프롬프트 ({0:N0} chars)  (사용자 턴이 어느 칸에 잡히는가)" -f $bigPrompt.Length)
$b = Invoke-Probe -Label "unique-1st" -PromptText $bigPrompt
if ($b) { $results += $b; Write-Host ("  in={0,7:N0}  fresh={1,6:N0}  read={2,7:N0}  create={3,7:N0}" -f $b.Total_in, $b.Fresh, $b.CacheRd, $b.CacheCr) }

Write-Host ""
Write-Host "[C] 동일 프롬프트 재실행  (그 몫이 cache_read로 돌아오는가)"
$c = Invoke-Probe -Label "unique-2nd" -PromptText $bigPrompt
if ($c) { $results += $c; Write-Host ("  in={0,7:N0}  fresh={1,6:N0}  read={2,7:N0}  create={3,7:N0}" -f $c.Total_in, $c.Fresh, $c.CacheRd, $c.CacheCr) }

Write-Host ""
Write-Host "===== 전체 ====="
$results | Format-Table -AutoSize

# --- 판정 ---
Write-Host "===== 판정 ====="
$empties = $results | Where-Object { $_.Label -like "empty*" }
if ($empties.Count -gt 0) {
    $stats = $empties | Measure-Object -Property Total_in -Average -Minimum -Maximum
    $spread = $stats.Maximum - $stats.Minimum
    Write-Host ("C (오버헤드) = {0:N0} tok   [min {1:N0} / max {2:N0}, 편차 {3:N0}]" -f $stats.Average, $stats.Minimum, $stats.Maximum, $spread)
    if ($stats.Average -gt 0 -and ($spread / $stats.Average) -gt 0.1) {
        Write-Host "  ! 편차가 10%를 넘는다 - C를 상수로 취급하면 안 된다." -ForegroundColor Yellow
    }
}
if ($b) {
    $baseline = ($empties | Measure-Object -Property Total_in -Average).Average
    $delta = $b.Total_in - $baseline
    Write-Host ("대형 프롬프트가 더한 입력 = {0:N0} tok  ({1:N0} chars -> {2:N3} tok/char)" -f $delta, $b.Chars, ($delta / [double]$b.Chars))
    if ($b.Fresh -gt ($delta * 0.5)) {
        Write-Host "  -> 사용자 턴은 캐시 '밖'이다 (input_tokens에 잡힘)."
    } elseif ($b.CacheCr -gt ($delta * 0.5)) {
        Write-Host "  -> 사용자 턴이 캐시에 '쓰인다' (cache_creation에 잡힘)."
    } else {
        Write-Host "  -> 어느 칸으로도 뚜렷하지 않다. 원본 표를 직접 확인할 것." -ForegroundColor Yellow
    }
}
if ($b -and $c) {
    $readGain = $c.CacheRd - $b.CacheRd
    Write-Host ("재실행 시 cache_read 증가 = {0:N0} tok" -f $readGain)
    if ($readGain -gt ($b.Chars * 0.1)) {
        Write-Host "  -> 같은 traceId 재조사는 캐시 이득을 받는다 (N>=2 채우기가 싸다)."
    } else {
        Write-Host "  -> 재실행해도 캐시 이득이 없다. 컨텍스트는 매번 정가다."
    }
}

Write-Host ""
Write-Host "결과를 docs/measurement.md의 '실측 기록'에 옮겨 적을 것."
Remove-Item -Recurse -Force $sandbox -ErrorAction SilentlyContinue
