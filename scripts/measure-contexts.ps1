<#
.SYNOPSIS
    복원된 과거 컨텍스트의 토큰 수를 API 키 없이 **차분(paired difference)**으로 측정한다.

.DESCRIPTION
    count_tokens는 ANTHROPIC_API_KEY를 요구하는데, 이 프로젝트는 구독 계정으로 claude CLI만
    쓰는 구성이라 키가 없다. 대신 CLI 자신의 usage 보고를 차분해서 같은 값을 얻는다.

        A: 꼬리말만            -> in_A = C + tail
        B: 컨텍스트 + 꼬리말   -> in_B = C + context + tail
        B - A                  = context 토큰            (C가 정확히 상쇄된다)

    이 방식이 `in - C`(전역 상수 빼기)보다 나은 이유: C를 추정할 필요가 없다. 짝지어진 두 실행은
    같은 모델·같은 CLI 버전·같은 분에 돌므로 오버헤드가 무엇이든 그대로 상쇄된다.
    (전역 C 회귀 추정은 95% 신뢰구간이 [965, 23,345]로 벌어져 폐기됐다 — docs/measurement.md 2절)

    선행: ./gradlew test --tests '*ContextRebuildTool*' -Drca.tools=true

.NOTES
    구독 계정이면 달러 청구는 없고 사용량(rate limit)만 소모한다. 리포트의 total_cost_usd는
    실제 청구액이 아니라 API 환산 추정치다.
#>
param(
    [string]$Model = "claude-opus-5",
    [string]$CliPath = "claude",
    [string]$ContextDir = "build/contexts",
    [int]$BaselineRuns = 2
)

$ErrorActionPreference = "Stop"

# 분석을 시키지 않고 입력만 재기 위한 꼬리말. A/B 양쪽에 똑같이 붙으므로 차분에서 상쇄된다.
$TAIL = "`n`n---`nSTOP. Do not analyze the text above. Reply with exactly: OK"

$sandbox = Join-Path ([System.IO.Path]::GetTempPath()) ("rca-measure-" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $sandbox | Out-Null

function Get-InputTokens {
    param([string]$PromptText)

    Push-Location $sandbox
    try {
        $raw = $PromptText | & $CliPath -p --model $Model --output-format json
    } finally {
        Pop-Location
    }
    if (-not $raw) { throw "CLI가 빈 응답을 반환했다" }

    $json = $raw | ConvertFrom-Json
    if ($json.is_error) { throw ("CLI 오류: " + $json.result) }

    $u = $json.usage
    $fresh = 0; $read = 0; $create = 0
    if ($u) {
        if ($null -ne $u.input_tokens) { $fresh = [long]$u.input_tokens }
        if ($null -ne $u.cache_read_input_tokens) { $read = [long]$u.cache_read_input_tokens }
        if ($null -ne $u.cache_creation_input_tokens) { $create = [long]$u.cache_creation_input_tokens }
    }
    $turns = -1
    if ($null -ne $json.num_turns) { $turns = [int]$json.num_turns }

    [PSCustomObject]@{
        Total = $fresh + $read + $create
        Fresh = $fresh; CacheRd = $read; CacheCr = $create; Turns = $turns
    }
}

Write-Host "model   : $Model"
Write-Host "sandbox : $sandbox"
Write-Host ""

# --- A. 기준선 (꼬리말만) ---
Write-Host "[A] 기준선 x $BaselineRuns  (CLI 오버헤드 + 꼬리말)"
$baselines = @()
for ($i = 1; $i -le $BaselineRuns; $i++) {
    $r = Get-InputTokens -PromptText $TAIL.TrimStart()
    $baselines += $r.Total
    Write-Host ("  #{0}  in={1,7:N0}  (fresh={2,6:N0} read={3,7:N0} create={4,7:N0})" -f $i, $r.Total, $r.Fresh, $r.CacheRd, $r.CacheCr)
}
$stats = $baselines | Measure-Object -Average -Minimum -Maximum
$baseline = $stats.Average
$spread = $stats.Maximum - $stats.Minimum
Write-Host ("  기준선 = {0:N0} tok  (편차 {1:N0})" -f $baseline, $spread)
if ($baseline -gt 0 -and ($spread / $baseline) -gt 0.05) {
    Write-Host "  ! 기준선 편차 5% 초과 - 차분값에 그만큼 오차가 실린다." -ForegroundColor Yellow
}

# --- B. 컨텍스트별 측정 ---
Write-Host ""
Write-Host "[B] 컨텍스트별 측정"
$rows = @()
Get-ChildItem -Path $ContextDir -Filter *.txt | Sort-Object Name | ForEach-Object {
    $file = $_
    $text = Get-Content -Raw -Encoding UTF8 $file.FullName
    $r = Get-InputTokens -PromptText ($text + $TAIL)
    $tokens = $r.Total - $baseline
    $rows += [PSCustomObject]@{
        traceId   = $file.BaseName
        chars     = $text.Length
        tokens    = [long]$tokens
        tok_char  = [math]::Round($tokens / [double]$text.Length, 3)
        raw_in    = $r.Total   # 그때 기록된 in과 대조하면 오염·드리프트가 드러난다
        turns     = $r.Turns   # 1이 아니면 단일 패스가 아니다

    }
    Write-Host ("  {0}  chars={1,7:N0}  tokens={2,7:N0}  ({3:N3} tok/char)" -f $file.BaseName.Substring(0,8), $text.Length, $tokens, ($tokens / [double]$text.Length))
}

Write-Host ""
Write-Host "===== 결과 ====="
$rows | Format-Table -AutoSize

$csv = "build/context-tokens.csv"
$rows | Export-Csv -Path $csv -NoTypeInformation -Encoding UTF8
Write-Host "CSV: $csv"

if ($rows.Count -gt 1) {
    $ratios = $rows | Measure-Object -Property tok_char -Average -Minimum -Maximum
    Write-Host ("tok/char: 평균 {0:N3}  [{1:N3} ~ {2:N3}]" -f $ratios.Average, $ratios.Minimum, $ratios.Maximum)
    Write-Host "  (코드가 쓰던 chars/4 = 0.250 과 비교할 것)"
}

Remove-Item -Recurse -Force $sandbox -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "결과를 docs/measurement.md '7. 실측 기록'에 옮길 것."
