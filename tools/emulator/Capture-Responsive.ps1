<#
Capture screenshots of CivilEG2 at several simulated window sizes so the
responsive layouts (phone / medium tablet / expanded tablet) can be reviewed.

    powershell -File tools\emulator\Capture-Responsive.ps1
    powershell -File tools\emulator\Capture-Responsive.ps1 -SkipBuild
    powershell -File tools\emulator\Capture-Responsive.ps1 -OutDir artifacts\emulator\responsive\run1

Sizes simulated via `adb shell wm size|density` (equivalent smallest width):
  phone    1080x2400 @440dpi -> ~411dp   (COMPACT, bottom bar)
  tablet-m 1600x2560 @320dpi -> 800dp     (MEDIUM, rail expected)
  tablet-x 1920x2560 @320dpi -> 960dp     (EXPANDED, rail expected)

Captures: home screen + tapped bottom/rail tabs per profile.
The device size/density is restored to `auto` at the end unless -KeepDeviceSize.
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

foreach ($p in $profiles) {
    Write-Step "Profile $($p.Name): $($p.Size) @ $($p.Density)dpi"
    & $script:Adb -s $serial shell wm size "$($p.Size)" | Out-Null
    & $script:Adb -s $serial shell wm density "$($p.Density)" | Out-Null
    Start-Sleep -Seconds 2

    # Force a fresh configuration so Compose recomputes the window class.
    & $script:Adb -s $serial shell am force-stop com.civileg.app | Out-Null
    Start-Sleep -Seconds 1
    & $script:Adb -s $serial shell monkey -p com.civileg.app -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep -Seconds 8

    & $script:Adb -s $serial shell wm size | ForEach-Object { "    $_" }
    & $script:Adb -s $serial shell wm density | ForEach-Object { "    $_" }

    # Home screen
    $dumpPath = Join-Path $OutDir ("ui-{0}-home.xml" -f $p.Name)
    Save-UiDump -Serial $serial -Path $dumpPath
    Save-Screenshot -Serial $serial -Path (Join-Path $OutDir ("{0}-home.png" -f $p.Name))

    # Tap bottom-nav / rail tabs at fixed positions (phone bar at bottom,
    # tablet rail on the left edge). Not all positions will hit a tab on
    # every profile — that is fine; each tap changes the visible screen.
    $size = Get-ScreenSize -Serial $serial
    $yBar = [int]($size.Height * 0.94)
    $xs = @(0.10, 0.30, 0.50, 0.70, 0.90) | ForEach-Object { [int]($size.Width * $_) }
    foreach ($i in 0..($xs.Count - 1)) {
        Tap -Serial $serial -X $xs[$i] -Y $yBar
        Start-Sleep -Seconds 3
        Save-Screenshot -Serial $serial -Path (Join-Path $OutDir ("{0}-tab{1}.png" -f $p.Name, $i))
    }
}

if (-not $KeepDeviceSize) {
    Write-Step "Restoring device size/density to auto"
    & $script:Adb -s $serial shell wm size reset | Out-Null
    & $script:Adb -s $serial shell wm density reset | Out-Null
}

Write-Step "Done. Open $OutDir"
