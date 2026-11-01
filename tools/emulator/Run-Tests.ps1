<#
Run the app test suites on the emulator.

    powershell -File tools\emulator\Run-Tests.ps1
    powershell -File tools\emulator\Run-Tests.ps1 -SkipUnit
    powershell -File tools\emulator\Run-Tests.ps1 -Filter "com.civileg.app.AppSmokeTest"
    powershell -File tools\emulator\Run-Tests.ps1 -UnitFilter "*ColumnDesignEngine*"

Unit tests run on the JVM; instrumented tests require a booted emulator
(start it with Start-Emulator.ps1 first). Results are summarized and stored
under artifacts\emulator\tests\<timestamp>\.
#>

param(
    [string]$Serial,
    [switch]$SkipUnit,
    [switch]$SkipInstrumented,
    [string]$Filter,
    [string]$UnitFilter,
    [switch]$NoDaemon
)

. "$PSScriptRoot\emulator-common.ps1"

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $script:ArtifactsRoot "tests\$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Get-TestSummary {
    param([string]$Root)
    $summary = [pscustomobject]@{
        Total = 0; Failures = 0; Errors = 0; Skipped = 0; Files = 0
        FailedTests = @()
    }
    if (-not (Test-Path -LiteralPath $Root)) { return $summary }
    $files = @(Get-ChildItem -LiteralPath $Root -Recurse -Filter "TEST-*.xml" -ErrorAction SilentlyContinue)
    $summary.Files = $files.Count
    foreach ($file in $files) {
        try {
            [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
            $suite = $xml.testsuite
            if (-not $suite) { continue }
            $summary.Total += [int]$suite.tests
            $summary.Failures += [int]$suite.failures
            $summary.Errors += [int]$suite.errors
            $summary.Skipped += [int]$suite.skipped
            foreach ($case in $suite.testcase) {
                $failed = $false
                if ($case.failure) { $failed = $true }
                if ($case.error) { $failed = $true }
                if ($failed) {
                    $summary.FailedTests += ("{0}.{1}" -f $case.classname, $case.name)
                }
            }
        } catch {
            Write-Warn "Could not parse $($file.FullName)"
        }
    }
    return $summary
}

function Show-Summary {
    param([string]$Title, $Summary)
    Write-Host ""
    Write-Host "--- $Title ---"
    Write-Host ("tests={0} failures={1} errors={2} skipped={3} files={4}" -f `
        $Summary.Total, $Summary.Failures, $Summary.Errors, $Summary.Skipped, $Summary.Files)
    foreach ($name in $Summary.FailedTests) {
        Write-Host "  FAILED: $name" -ForegroundColor Red
    }
}

$exitCode = 0
$unitSummary = $null
$instrumentedSummary = $null

if (-not $SkipUnit) {
    Write-Step "Running unit tests (:app:testDebugUnitTest)"
    $gradleArgs = @(":app:testDebugUnitTest", "--console=plain")
    if ($UnitFilter) { $gradleArgs += @("--tests", $UnitFilter) }
    if ($NoDaemon) { $gradleArgs += "--no-daemon" }
    Push-Location $script:RepoRoot
    try { & ".\gradlew.bat" @gradleArgs } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { $exitCode = 1 }
    $unitSummary = Get-TestSummary -Root (Join-Path $script:RepoRoot "app\build\test-results\testDebugUnitTest")
    Show-Summary -Title "Unit tests" -Summary $unitSummary
    Copy-Item -Path (Join-Path $script:RepoRoot "app\build\reports\tests\testDebugUnitTest") -Destination (Join-Path $outDir "unit-html") -Recurse -Force -ErrorAction SilentlyContinue
}

if (-not $SkipInstrumented) {
    $serial = Get-Serial -Serial $Serial
    if (-not $serial -or -not (Test-DeviceOnline -Serial $serial)) {
        Write-Warn "No online emulator; skipping instrumented tests. Start one with Start-Emulator.ps1"
        $exitCode = 1
    } else {
        Write-Step "Running instrumented tests on $serial (:app:connectedDebugAndroidTest)"
        $gradleArgs = @(":app:connectedDebugAndroidTest", "--console=plain")
        if ($Filter) {
            $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.class=$Filter"
        }
        if ($NoDaemon) { $gradleArgs += "--no-daemon" }
        Push-Location $script:RepoRoot
        try { & ".\gradlew.bat" @gradleArgs } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) { $exitCode = 1 }
        $instrumentedSummary = Get-TestSummary -Root (Join-Path $script:RepoRoot "app\build\outputs\androidTest-results")
        Show-Summary -Title "Instrumented tests" -Summary $instrumentedSummary
        Copy-Item -Path (Join-Path $script:RepoRoot "app\build\reports\androidTests\connected") -Destination (Join-Path $outDir "instrumented-html") -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$report = [pscustomobject]@{
    timestamp = (Get-Date).ToString("s")
    serial = (Get-Serial -Serial $Serial)
    unit = $unitSummary
    instrumented = $instrumentedSummary
}
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $outDir "results.json") -Encoding utf8

Write-Host ""
if ($exitCode -eq 0) {
    Write-Ok "All executed tests passed. Artifacts: $outDir"
} else {
    Write-Warn "Some tests failed (or instrumented tests were skipped). Artifacts: $outDir"
}
exit $exitCode
