<#
Shared helpers for the CivilEG2 Android emulator tooling.
Dot-source this file from the other scripts:

    . "$PSScriptRoot\emulator-common.ps1"

It resolves the Android SDK from local.properties (or ANDROID_HOME /
ANDROID_SDK_ROOT), exposes the tool paths, and provides small wrappers
around adb/emulator that all agent scripts reuse.
#>

$ErrorActionPreference = "Continue"

$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function Get-AndroidSdk {
    $localProps = Join-Path $script:RepoRoot "local.properties"
    if (Test-Path -LiteralPath $localProps) {
        $line = Get-Content -LiteralPath $localProps |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($line) {
            $value = ($line -split '=', 2)[1].Trim()
            # local.properties escapes the Windows drive colon as "C\:".
            $value = $value -replace '\\:', ':' -replace '\\\\', '\'
            if (Test-Path -LiteralPath $value) { return $value }
        }
    }
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
                            (Join-Path $env:LOCALAPPDATA "Android\Sdk"))) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate }
    }
    throw "Android SDK not found. Set sdk.dir in local.properties or ANDROID_HOME."
}

$script:SdkRoot = Get-AndroidSdk
$script:Adb = Join-Path $script:SdkRoot "platform-tools\adb.exe"
$script:EmulatorExe = Join-Path $script:SdkRoot "emulator\emulator.exe"
$script:AvdManager = Join-Path $script:SdkRoot "cmdline-tools\latest\bin\avdmanager.bat"
$script:SdkManager = Join-Path $script:SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
$script:JavaHome = "C:\Program Files\Android\Android Studio\jbr"

$script:AvdName = "civileg2_api36"
$script:AppId = "com.civileg.app"
$script:LauncherActivity = "com.civileg.app/.ui.splash.SplashActivity"
$script:MainActivity = "com.civileg.app/.MainActivity"
$script:ArtifactsRoot = Join-Path $script:RepoRoot "artifacts\emulator"
$script:SerialFile = Join-Path $script:ArtifactsRoot "current-serial.txt"

foreach ($tool in @($script:Adb, $script:EmulatorExe)) {
    if (-not (Test-Path -LiteralPath $tool)) {
        throw "Missing Android tool: $tool. Install the Android SDK emulator and platform-tools."
    }
}

function Write-Step([string]$Message) {
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "OK  $Message" -ForegroundColor Green
}

function Write-Warn([string]$Message) {
    Write-Host "!!  $Message" -ForegroundColor Yellow
}

function Get-EmulatorSerial {
    $lines = & $script:Adb devices 2>$null
    foreach ($line in $lines) {
        if ($line -match '^(emulator-\d+)\s+device') { return $Matches[1] }
    }
    return $null
}

function Get-Serial {
    param([string]$Serial)
    if ($Serial) { return $Serial }
    if (Test-Path -LiteralPath $script:SerialFile) {
        $saved = (Get-Content -LiteralPath $script:SerialFile -Raw).Trim()
        if ($saved) { return $saved }
    }
    return (Get-EmulatorSerial)
}

function Test-DeviceOnline {
    param([string]$Serial)
    if (-not $Serial) { return $false }
    $state = (& $script:Adb -s $Serial get-state 2>$null)
    return ($state -eq "device")
}

function Wait-ForBoot {
    param(
        [string]$Serial,
        [int]$TimeoutSeconds = 480
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-DeviceOnline -Serial $Serial) {
            $boot = (& $script:Adb -s $Serial shell getprop sys.boot_completed 2>$null)
            if ($boot -and $boot.Trim() -eq "1") { return $true }
        }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Initialize-Device {
    param([string]$Serial)
    Write-Step "Preparing device (animations off, stay awake, unlock)"
    & $script:Adb -s $Serial shell settings put global window_animation_scale 0 2>$null | Out-Null
    & $script:Adb -s $Serial shell settings put global transition_animation_scale 0 2>$null | Out-Null
    & $script:Adb -s $Serial shell settings put global animator_duration_scale 0 2>$null | Out-Null
    & $script:Adb -s $Serial shell settings put system screen_off_timeout 2147483647 2>$null | Out-Null
    & $script:Adb -s $Serial shell svc power stayon true 2>$null | Out-Null
    & $script:Adb -s $Serial shell input keyevent 82 2>$null | Out-Null
    & $script:Adb -s $Serial shell wm dismiss-keyguard 2>$null | Out-Null
}

function Save-Screenshot {
    param(
        [string]$Serial,
        [string]$Path
    )
    $remote = "/sdcard/_agent_screenshot.png"
    & $script:Adb -s $Serial shell screencap -p $remote 2>$null | Out-Null
    & $script:Adb -s $Serial pull $remote $Path 2>$null | Out-Null
    & $script:Adb -s $Serial shell rm -f $remote 2>$null | Out-Null
}

function Save-UiDump {
    param(
        [string]$Serial,
        [string]$Path
    )
    $remote = "/sdcard/_agent_ui.xml"
    & $script:Adb -s $Serial shell uiautomator dump $remote 2>$null | Out-Null
    & $script:Adb -s $Serial pull $remote $Path 2>$null | Out-Null
    & $script:Adb -s $Serial shell rm -f $remote 2>$null | Out-Null
}

function Get-ScreenSize {
    param([string]$Serial)
    $out = (& $script:Adb -s $Serial shell wm size 2>$null) -join "`n"
    if ($out -match '(\d+)x(\d+)') {
        return @{ Width = [int]$Matches[1]; Height = [int]$Matches[2] }
    }
    return @{ Width = 1080; Height = 2400 }
}

function Tap {
    param([string]$Serial, [int]$X, [int]$Y)
    & $script:Adb -s $Serial shell input tap $X $Y 2>$null | Out-Null
}

function Start-App {
    param(
        [string]$Serial,
        [string]$Activity = $script:LauncherActivity
    )
    Write-Step "Launching $Activity"
    & $script:Adb -s $Serial shell am force-stop $script:AppId 2>$null | Out-Null
    & $script:Adb -s $Serial shell am start -W -n $Activity 2>$null | Out-Null
}

function Save-CurrentSerial {
    param([string]$Serial)
    New-Item -ItemType Directory -Force -Path $script:ArtifactsRoot | Out-Null
    Set-Content -LiteralPath $script:SerialFile -Value $Serial -Encoding ASCII
}
