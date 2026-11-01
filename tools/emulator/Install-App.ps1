<#
Install the built APK on the running emulator and optionally launch it.

    powershell -File tools\emulator\Install-App.ps1
    powershell -File tools\emulator\Install-App.ps1 -Launch
    powershell -File tools\emulator\Install-App.ps1 -Apk app\build\outputs\apk\release\app-release.apk
#>

param(
    [string]$Apk,
    [string]$Serial,
    [switch]$Launch
)

. "$PSScriptRoot\emulator-common.ps1"

$serial = Get-Serial -Serial $Serial
if (-not $serial) {
    throw "No emulator running. Start one with tools\emulator\Start-Emulator.ps1"
}
if (-not (Test-DeviceOnline -Serial $serial)) {
    throw "Device $serial is not online"
}

if (-not $Apk) {
    $lastApk = Join-Path $script:ArtifactsRoot "last-apk.txt"
    if (Test-Path -LiteralPath $lastApk) {
        $Apk = (Get-Content -LiteralPath $lastApk -Raw).Trim()
    } else {
        $Apk = Join-Path $script:RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
    }
}
if (-not (Test-Path -LiteralPath $Apk)) {
    throw "APK not found: $Apk. Run tools\emulator\Build-App.ps1 first."
}

Write-Step "Installing $Apk on $serial"
& $script:Adb -s $serial install -r -t "$Apk" 2>&1 | ForEach-Object { Write-Host "    $_" }
if ($LASTEXITCODE -ne 0) {
    throw "adb install failed"
}

$path = (& $script:Adb -s $serial shell pm path $script:AppId 2>$null) -join ""
if (-not $path) {
    throw "Package $script:AppId is not installed after adb install"
}
Write-Ok "Installed $script:AppId"

if ($Launch) {
    Start-App -Serial $serial
    Start-Sleep -Seconds 8
    Write-Ok "App launched"
}
