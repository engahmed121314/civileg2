<#
Capture screenshots of CivilEG2 at several simulated window sizes so the
responsive layouts (phone / medium tablet / expanded tablet) can be reviewed.

    powershell -File tools\emulator\Capture-Responsive.ps1
    powershell -File tools\emulator\Capture-Responsive.ps1 -SkipBuild
    powershell -File tools\emulator\Capture-Responsive.ps1 -OutDir artifacts\emulator\responsive\run1

Sizes simulated via `adb shell wm size|density`:
  phone    1080x2400 @440dpi -> 411dp wide  (COMPACT)
  tablet-m 1600x2560 @320dpi -> 800dp wide  (MEDIUM, rail expected)
  tablet-x 1920x2560 @320dpi -> 960dp wide  (EXPANDED, rail expected)

The device size/density is restored to `auto` at the end.
#>

param(
    [string]$Serial,
    [string]$OutDir,
    [switch]$SkipBuild,
    [switch]$KeepDeviceSize
)

. "$PSScriptRoot\emulator-common.ps1"

$ErrorActionPreference = "Stop"

$serial = Get-Serial -Serial $Serial
if (-not $serial) { throw "No emulator running. Start one with tools\emulator\Start-Emulator.ps1" }
if (-not (Test-DeviceOnline -Serial $serial)) { throw "Device $serial is not online" }

if (-not $OutDir) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutDir = Join-Path $script:ArtifactsRoot "responsive\$stamp"
}
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
Write-Step "Screenshots -> $OutDir"

if (-not $SkipBuild) {
    Write-Step "Building debug APK"
    & "$script:RepoRoot\gradlew.bat" :app:assembleDebug --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed (exit $LASTEXITCODE)" }
}

$apk = Join-Path $script:RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "APK not found: $apk" }

Write-Step "Installing $apk"
& $script:Adb -s $serial install -r $apk | Out-Null

$profiles = @(
    @{ Name = "phone";    Size = "1080x2400"; Density = "440" },
    @{ Name = "tablet-m"; Size = "1600x2560"; Density = "320" },
    @{ Name = "tablet-x"; Size = "1920x2560"; Density = "320" }
)

# Tabs of the bottom navigation as laid out on a phone (labels come from strings).
$tabs = @("home", "design", "steel", "tools", "more")

foreach ($p in $profiles) {
    Write-Step "Profile $($p.Name): $($p.Size) @ $($p.Density)dpi"
    & $script:Adb -s $serial shell wm size "$($p.Size)" | Out-Null
    & $script:Adb -s $serial shell wm density "$($p.Density)" | Out-Null
    Start-Sleep -Seconds 2

    # Force a configuration change so the app recomposes for the new window.
    & $script:Adb -s $serial shell am force-stop com.civileg.app | Out-Null
    Start-Sleep -Seconds 1
    & $script:Adb -s $serial shell monkey -p com.civileg.app -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds 8

    & $script:Adb -s $serial shell wm size | ForEach-Object { "    $_" }
    & $script:Adb -s $serial shell wm density | ForEach-Object { "    $_" }

    Save-Screenshot -Serial $serial -Path (Join-Path $OutDir "$($p.Name)-home.png")
    Save-UiDump -Serial $serial -Path (Join-Path $OutDir "$($p.Name)-home.xml")

    # Capture a second view after a brief wait
    Start-Sleep -Seconds 2
    Save-Screenshot -Serial $serial -Path (Join-Path $OutDir "$($p.Name)-second.png")
}

if (-not $KeepDeviceSize) {
    Write-Step "Restoring device size/density to auto"
    & $script:Adb -s $serial shell wm size reset | Out-Null
    & $script:Adb -s $serial shell wm density reset | Out-Null
}

Write-Step "Done. Open $OutDir"
