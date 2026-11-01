# Agent Guide — CivilEG2

## Build & test from the command line

```powershell
# Full emulator workflow (boot -> build -> install -> screenshots -> tests)
powershell -File tools\emulator\Test-App.ps1

# Faster loops
powershell -File tools\emulator\Start-Emulator.ps1
powershell -File tools\emulator\Build-App.ps1
powershell -File tools\emulator\Install-App.ps1 -Launch
powershell -File tools\emulator\Capture-Screens.ps1
powershell -File tools\emulator\Run-Tests.ps1
powershell -File tools\emulator\Stop-Emulator.ps1
```

- Emulator AVD: `civileg2_api36` (Android 16 / API 36.1, Pixel 6, headless).
- App id: `com.civileg.app`; launcher: `.ui.splash.SplashActivity`.
- Artifacts: `artifacts\emulator\` (screenshots, test reports, logcat).
- One emulator at a time; never run two Gradle builds concurrently.
- Before reporting UI work as done, run `Test-App.ps1` and check the screenshots
  plus `artifacts\emulator\runs\<timestamp>\report.md`.

## Project facts

- Root Gradle project: `SiteEngineerPro`; module `:app`; `compileSdk=36`, `minSdk=26`, `targetSdk=36`.
- UI is Jetpack Compose (`MainActivity`) with Hilt + Room.
- Unit tests: `app/src/test` (JVM). Instrumented tests: `app/src/androidTest`.
- Existing build issues unrelated to the emulator may exist in the working tree;
  do not "fix" unrelated files without being asked.

## Emulator details

See `tools/emulator/README.md` for every script, artifact path and troubleshooting
step. The setup script is idempotent:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\emulator\Setup-Emulator.ps1
```
