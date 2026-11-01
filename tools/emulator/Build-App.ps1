<#
Build the CivilEG2 APK (and optionally the instrumented-test APK).

    powershell -File tools\emulator\Build-App.ps1
    powershell -File tools\emulator\Build-App.ps1 -Variant release
    powershell -File tools\emulator\Build-App.ps1 -AndroidTest

Prints the APK path on success and stores it in artifacts\emulator\last-apk.txt.
#>

param(
    [ValidateSet("debug", "release")]
    [string]$Variant = "debug",
    [switch]$AndroidTest,
    [switch]$NoDaemon
)

. "$PSScriptRoot\emulator-common.ps1"

$variantTitle = (Get-Culture).TextInfo.ToTitleCase($Variant)
$gradleArgs = @(":app:assemble$variantTitle", "--console=plain")
if ($NoDaemon) { $gradleArgs += "--no-daemon" }

Write-Step "Building :app:assemble$variantTitle"
Push-Location $script:RepoRoot
try {
    & ".\gradlew.bat" @gradleArgs
    $exit = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($exit -ne 0) {
    throw "Gradle build failed with exit code $exit"
}

$apk = Join-Path $script:RepoRoot "app\build\outputs\apk\$Variant\app-$Variant.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    throw "APK not found after build: $apk"
}

New-Item -ItemType Directory -Force -Path $script:ArtifactsRoot | Out-Null
Set-Content -LiteralPath (Join-Path $script:ArtifactsRoot "last-apk.txt") -Value $apk -Encoding ASCII
Write-Ok "APK: $apk"

if ($AndroidTest) {
    Write-Step "Building instrumented test APK (:app:assembleDebugAndroidTest)"
    Push-Location $script:RepoRoot
    try {
        & ".\gradlew.bat" ":app:assembleDebugAndroidTest" "--console=plain"
        $testExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($testExit -ne 0) {
        throw "Instrumented-test build failed with exit code $testExit"
    }
    $testApk = Join-Path $script:RepoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    if (Test-Path -LiteralPath $testApk) {
        Write-Ok "Test APK: $testApk"
    } else {
        Write-Warn "Test APK not found at the expected path"
    }
}
