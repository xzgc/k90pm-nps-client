$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$apk = Join-Path $PSScriptRoot "release\K90PM-NPS-2.1.0-arm64.apk"
if (-not (Test-Path $apk)) {
    $apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-arm64-v8a-release.apk"
}
if (-not (Test-Path $apk)) {
    throw "APK not found. Run .\build.ps1 first."
}

adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "adb install failed with exit code $LASTEXITCODE"
}

adb shell am start -n com.k90pm.npsclient/com.duanlab.npsclient.MainActivity
