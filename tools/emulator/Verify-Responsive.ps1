<#
Single-shot CivilEG2 verification: starts ONE emulator instance and uses it
for the whole flow (never starts a second same-AVD instance, which previously
killed both qemu processes via snapshot lock conflict).

    powershell -File tools\emulator\Verify-Responsive.ps1
#>

param(
    [int]$BootTimeoutSeconds = 900
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\emulator-common.ps1"

Push-Location $script:RepoRoot

try {
    # ── 1. Start ONE emulator and wait for boot ─────────────────────────
    Write-Step "=== Step 1: start emulator (single instance) ==="
    & (Join-Path $PSScriptRoot "Start-Emulator.ps1") -TimeoutSeconds $BootTimeoutSeconds -ColdBoot
    if ($LASTEXITCODE -ne 0) { throw "Failed to start emulator" }
    $serial = Get-EmulatorSerial
    if (-not $serial) { throw "No emulator after start" }
    Write-Ok "Using emulator: $serial"

    # ── 2. Build app + instrumented test APK ────────────────────────────
    Write-Step "=== Step 2: build ==="
    & (Join-Path $PSScriptRoot "Build-App.ps1") -AndroidTest
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }

    # ── 3. Install + launch ─────────────────────────────────────────────
    Write-Step "=== Step 3: install + launch ==="
    & (Join-Path $PSScriptRoot "Install-App.ps1") -Serial $serial -Launch
    if ($LASTEXITCODE -ne 0) { throw "Install/launch failed" }

    # ── 4. Screenshots: default window (phone class) ────────────────────
    Write-Step "=== Step 4: capture screens (default size) ==="
    $runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $runDir = Join-Path $script:ArtifactsRoot "runs\$runStamp"
    $screensDir = Join-Path $runDir "screens"
    New-Item -ItemType Directory -Force -Path $runDir, $screensDir | Out-Null
    & (Join-Path $PSScriptRoot "Capture-Screens.ps1") -Serial $serial -OutDir $screensDir -WaitSeconds 8
    if ($LASTEXITCODE -ne 0) { Write-Warn "Capture-Screens exited $LASTEXITCODE" }

    # ── 5. Responsive size classes: phone / tablet-m / tablet-x ─────────
    Write-Step "=== Step 5: capture responsive size classes ==="
    & (Join-Path $PSScriptRoot "Capture-Responsive.ps1") -Serial $serial -SkipBuild -OutDir (Join-Path $runDir "responsive")
    if ($LASTEXITCODE -ne 0) { Write-Warn "Capture-Responsive exited $LASTEXITCODE" }

    # ── 6. Unit tests ───────────────────────────────────────────────────
    Write-Step "=== Step 6: unit tests ==="
    & ".\gradlew.bat" :app:testDebugUnitTest --console=plain
    $testExit = $LASTEXITCODE

    # ── 7. Report ───────────────────────────────────────────────────────
    $report = @"
# CivilEG2 Responsive UI Verification Report
Date: $(Get-Date)
Emulator: $serial (civileg2_api36, API 36.1)
Build: :app:assembleDebug + :app:assembleDebugAndroidTest — OK
Install/launch: OK

Screenshots: $screensDir
Responsive screens: $runDir/responsive

Unit tests exit code: $testExit

Profiles captured:
- phone    1080x2400 @440dpi (~411dp, COMPACT, bottom bar)
- tablet-m 1600x2560 @320dpi (800dp, MEDIUM, rail)
- tablet-x 1920x2560 @320dpi (960dp, EXPANDED, rail)

Notes: emulator is a single headless qemu instance; size/density restored to auto at the end.
"@
    $reportPath = Join-Path $runDir "report.md"
    Set-Content -LiteralPath $reportPath -Value $report -Encoding utf8
    Write-Ok "Report: $reportPath"

    if ($testExit -ne 0) { throw "Unit tests failed (exit $testExit)" }
    Write-Ok "=== VERIFICATION COMPLETE ==="
}
finally {
    Pop-Location
    # Restore device size/density if an emulator is still around.
    $s = Get-EmulatorSerial
    if ($s) {
        & $script:Adb -s $s shell wm size reset 2>$null | Out-Null
        & $script:Adb -s $s shell wm density reset 2>$null | Out-Null
    }
}
