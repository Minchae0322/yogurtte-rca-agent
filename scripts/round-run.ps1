# Run one investigation round: start the app, POST /diagnose, save the report, stop the app.
#
# WHY a script: the round protocol requires the question wording and window to be IDENTICAL
# to the round being compared against. Typing them by hand is how a round quietly becomes
# uncomparable - the request body lives in a file instead, and that file is the record of
# what was actually sent.
#
# Tooling config is whatever application.yml / .env say. This script does NOT override
# feature switches: a round measures the current configuration, and mixing switch overrides
# into the runner is how "which config produced this report" gets lost.
#
# NOTE: ASCII only. Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI, so non-ASCII here
# would corrupt the parse. Korean question text lives in the request JSON (UTF-8) instead.
#
#   .\scripts\round-run.ps1 -Request scripts\round5-request.json -Label ap-1-round5

param(
    [Parameter(Mandatory = $true)][string] $Request,
    [Parameter(Mandatory = $true)][string] $Label,
    [string] $OutDir = 'reports/rounds',
    [int] $Port = 8080,
    [int] $StartupTimeoutSec = 240,
    [int] $RequestTimeoutSec = 900
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $repo $OutDir
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$envFile = Join-Path $repo '.env'
if (-not (Test-Path $envFile)) { throw ".env not found: $envFile" }
foreach ($line in Get-Content $envFile -Encoding utf8) {
    $t = $line.Trim()
    if ($t -eq '' -or $t.StartsWith('#')) { continue }
    $i = $t.IndexOf('=')
    if ($i -lt 1) { continue }
    $val = $t.Substring($i + 1).Trim()
    if ($val -eq '') { continue }
    Set-Item -Path ("env:" + $t.Substring(0, $i).Trim()) -Value $val
}

function Wait-ForPort([int] $p, [int] $timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $c = New-Object Net.Sockets.TcpClient
            $c.Connect('127.0.0.1', $p)
            $c.Close()
            return $true
        } catch {
            Start-Sleep -Milliseconds 1500
        }
    }
    return $false
}

if (Wait-ForPort $Port 1) { throw ("port " + $Port + " is already in use - stop the running app first") }
# The app reads SERVER_PORT; without this a non-default -Port would start on 8080 anyway
# and the readiness check would pass against whatever else is listening there.
$env:SERVER_PORT = $Port

$body = [IO.File]::ReadAllBytes((Join-Path $repo $Request))
$log = Join-Path $outDir ($Label + '-app.log')

Write-Host ("=== " + $Label + "  request=" + $Request)
$app = Start-Process -FilePath (Join-Path $repo 'gradlew.bat') `
    -ArgumentList 'bootRun', '--console=plain' `
    -WorkingDirectory $repo -PassThru -NoNewWindow `
    -RedirectStandardOutput $log -RedirectStandardError ($log + '.err')

try {
    if (-not (Wait-ForPort $Port $StartupTimeoutSec)) {
        throw ("app did not open port " + $Port + " within " + $StartupTimeoutSec + "s - see " + $log)
    }
    # The startup line records which implementations actually came up.
    Select-String -Path $log -Pattern 'rca-agent ready' -ErrorAction SilentlyContinue |
        ForEach-Object { Write-Host ("  " + $_.Line.Trim()) }

    Write-Host "  posting /diagnose ..."
    $started = Get-Date
    $res = Invoke-WebRequest -Uri ("http://localhost:" + $Port + "/diagnose") -Method Post `
        -Body $body -ContentType 'application/json; charset=utf-8' `
        -TimeoutSec $RequestTimeoutSec -UseBasicParsing

    # Save the RAW BYTES, not $res.Content. PowerShell 5.1 decodes a response with no
    # charset in Content-Type as ISO-8859-1, which turns every Korean character into
    # mojibake - and writing that string back out as UTF-8 double-encodes it permanently.
    $out = Join-Path $outDir ($Label + '.json')
    [IO.File]::WriteAllBytes($out, $res.RawContentStream.ToArray())
    Write-Host ("  done in " + [int]((Get-Date) - $started).TotalSeconds + "s -> " + $out)
    # ReportStore has already written the canonical .md/.json under reports/; this copy
    # is only for convenience, so point at the real one.
    Get-ChildItem (Join-Path $repo 'reports') -Filter *.md |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1 |
        ForEach-Object { Write-Host ("  canonical: reports/" + $_.Name) }
} finally {
    # bootRun spawns a child JVM; killing the wrapper alone leaves the port held.
    Get-CimInstance Win32_Process -Filter ("ParentProcessId=" + $app.Id) |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
}
