$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not $env:JAVA_HOME) {
    throw "Set JAVA_HOME to a JDK 17 installation first."
}

$sdk = $env:ANDROID_HOME
if (-not $sdk) {
    $sdk = $env:ANDROID_SDK_ROOT
}
if (-not $sdk -or -not (Test-Path $sdk)) {
    throw "Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK path."
}

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:TEMP = "C:\gtmp"
$env:TMP = "C:\gtmp"
$env:GRADLE_OPTS = "-Djava.io.tmpdir=C:\gtmp -Djava.net.preferIPv4Stack=true"
New-Item -ItemType Directory -Force C:\gtmp | Out-Null

$gradle = $env:GRADLE_BIN
if (-not $gradle) {
    $gradle = Join-Path $PSScriptRoot "gradlew.bat"
}

& $gradle testDebugUnitTest assembleRelease --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE"
}

$source = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-arm64-v8a-release.apk"
$release = Join-Path $PSScriptRoot "release"
New-Item -ItemType Directory -Force $release | Out-Null
Copy-Item $source (Join-Path $release "K90PM-NPS-2.1.0-arm64.apk") -Force

Write-Host "APK: $(Join-Path $release 'K90PM-NPS-2.1.0-arm64.apk')"

& (Join-Path $PSScriptRoot "adb-tcp-module\build.ps1")
