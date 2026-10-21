$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$releaseDir = Join-Path $repoRoot "release"
$output = Join-Path $releaseDir "adb_tcp_keeper.zip"

New-Item -ItemType Directory -Force $releaseDir | Out-Null

$files = @(
    (Join-Path $PSScriptRoot "module.prop"),
    (Join-Path $PSScriptRoot "service.sh"),
    (Join-Path $PSScriptRoot "README.md")
)

Compress-Archive -Path $files -DestinationPath $output -Force

Write-Host "ADB TCP module: $output"
