<#
Full agent workflow for CivilEG2:
  1. boot the emulator (headless by default)
  2. build debug APK + instrumented-test APK
  3. install and launch the app
  4. capture screenshots of the main tabs
  5. run unit tests and instrumented tests
  6. write a report under artifacts\emulator\runs\<timestamp>\

    powershell -File tools\emulator\Test-App.ps1
    powershell -File tools\emulator\Test-App.ps1 -Windowed
    powershell -File tools\emulator\Test-App.ps1 -SkipUnit -SkipInstrumented
    powershell -File tools\emulator\Test-App.ps1 -SkipBuild -KeepEmulator

Exit code 0 only when every executed test passed.
#>

param(
    [switch]$Windowed,
    [switch]$ColdBoot,
    [switch]$WipeData,
    [switch]$SkipBuild,
    [switch]$SkipScreens,
    [switch]$SkipUnit,
    [switch]$SkipInstrumented,
    [switch]$KeepEmulator,
    [int]$BootTimeoutSeconds = 600
)

. "$PSScriptRoot\emulator-common.ps1"

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $script:ArtifactsRoot "runs\$stamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$startedHere = $false
$serial = Get-EmulatorSerial
if (-not $serial) {
    Write-Step "No emulator running; starting one"
    $startArgs = @("-TimeoutSeconds", "$BootTimeoutSeconds")
    if ($Windowed) { $startArgs += "-Windowed" }
    if ($ColdBoot -or $WipeData) { $startArgs += "-ColdBoot" }
    if ($WipeData) { $startArgs += "-WipeData" }
    & (Join-Path $PSScriptRoot "Start-Emulator.ps1") @startArgs
    if ($LASTEXITCODE -ne 0) { throw "Failed to start emulator" }
    $startedHere = $true
    $serial = Get-EmulatorSerial
} else {
    Write-Ok "Using existing emulator: $serial"
}
if (-not $serial) { throw "No emulator available" }

$exitCode = 0
$apk = $null
$screensDir = $null

try {
    if (-not $SkipBuild) {
        & (Join-Path $PSScriptRoot "Build-App.ps1") -AndroidTest
        if ($LASTEXITCODE -ne 0) { throw "Build failed" }
    }

    $apkFile = Join-Path $script:ArtifactsRoot "last-apk.txt"
    if (Test-Path -LiteralPath $apkFile) {
        $apk = (Get-Content -LiteralPath $apkFile -Raw).Trim()
    }
    if (-not $apk -or -not (Test-Path -LiteralPath $apk)) {
        $apk = Join-Path $script:RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
    }
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "APK not found: $apk. Run Build-App.ps1 first."
    }

    & (Join-Path $PSScriptRoot "Install-App.ps1") -Serial $serial -Apk $apk -Launch
    if ($LASTEXITCODE -ne 0) { throw "Install/launch failed" }

    if (-not $SkipScreens) {
        $screensDir = Join-Path $runDir "screens"
        & (Join-Path $PSScriptRoot "Capture-Screens.ps1") -Serial $serial -OutDir $screensDir
    }

    $runParams = @{ Serial = $serial }
    if ($SkipUnit) { $runParams.SkipUnit = $true }
    if ($SkipInstrumented) { $runParams.SkipInstrumented = $true }
    & (Join-Path $PSScriptRoot "Run-Tests.ps1") @runParams
    if ($LASTEXITCODE -ne 0) { $exitCode = 1 }

    & $script:Adb -s $serial logcat -d -t 1500 2>$null |
        Out-File -LiteralPath (Join-Path $runDir "logcat.txt") -Encoding utf8

    $resultsPath = Get-ChildItem -LiteralPath (Join-Path $script:ArtifactsRoot "tests") -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($resultsPath) {
        Copy-Item -Path (Join-Path $resultsPath.FullName "results.json") -Destination (Join-Path $runDir "results.json") -Force -ErrorAction SilentlyContinue
    }
} catch {
    Write-Warn $_.Exception.Message
    $exitCode = 1
} finally {
    $report = @()
    $report += "# CivilEG2 emulator run"
    $report += ""
    $report += "- Time: $((Get-Date).ToString('s'))"
    $report += "- Emulator: $serial"
    $report += "- APK: $apk"
    $report += "- Screenshots: $screensDir"
    $report += "- Result: $(if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' })"
    $report | Set-Content -LiteralPath (Join-Path $runDir "report.md") -Encoding utf8

    if ($startedHere -and -not $KeepEmulator) {
        Write-Step "Stopping emulator started by this run"
        & (Join-Path $PSScriptRoot "Stop-Emulator.ps1") -Serial $serial
    } elseif ($startedHere -and $KeepEmulator) {
        Write-Ok "Emulator left running: $serial"
    }
}

Write-Host ""
Write-Host "Report: $(Join-Path $runDir 'report.md')"
exit $exitCode
