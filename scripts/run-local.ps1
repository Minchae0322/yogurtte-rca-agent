# Load .env into environment variables, then bootRun.
#
# Spring Boot does not read .env - only docker compose does (env_file).
# README's `cp .env.example .env && ./gradlew bootRun` therefore does not work
# locally; this wrapper fills that gap. Docker path is unchanged.
#
# NOTE: ASCII only. Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI, so
# non-ASCII characters here would corrupt the parse.
#
#   .\scripts\run-local.ps1

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repo '.env'

if (-not (Test-Path $envFile)) {
    throw ".env not found: $envFile  (copy .env.example to .env and fill it in)"
}

$loaded = 0
foreach ($line in Get-Content $envFile -Encoding utf8) {
    $t = $line.Trim()
    if ($t -eq '' -or $t.StartsWith('#')) { continue }
    $i = $t.IndexOf('=')
    if ($i -lt 1) { continue }
    $key = $t.Substring(0, $i).Trim()
    $val = $t.Substring($i + 1).Trim()
    # Skip empty values so application.yml defaults are not overwritten with "".
    if ($val -eq '') { continue }
    Set-Item -Path "env:$key" -Value $val
    $loaded++
}

Write-Host "loaded $loaded env vars from .env"
Write-Host ("  RCA_APP_LABEL=" + $(if ($env:RCA_APP_LABEL) { $env:RCA_APP_LABEL } else { "(default: app)" }))
Write-Host ("  RCA_APPS=" + $(if ($env:RCA_APPS) { $env:RCA_APPS } else { "(default: content|auth|chat)" }))
if (-not $env:GRAFANA_TOKEN) {
    Write-Host "  [WARN] GRAFANA_TOKEN is empty - app will start but collection will fail" -ForegroundColor Yellow
}

Set-Location $repo
& (Join-Path $repo 'gradlew.bat') bootRun --console=plain
