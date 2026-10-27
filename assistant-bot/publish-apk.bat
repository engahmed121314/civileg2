@echo off
REM CivilEG2 - build APK, upload it, and send the install link to Telegram.
setlocal
cd /d "%~dp0.."
pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish-apk.ps1" %*
echo.
pause
