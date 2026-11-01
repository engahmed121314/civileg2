@echo off
REM CivilEG2 Assistant Bot - start the full stack (server + bot).
REM Preferred: the scheduled task "CivilEG2-Assistant-Bot" (starts at logon).
REM This script starts that task, or falls back to the hidden supervisor.

setlocal
set TASK=CivilEG2-Assistant-Bot
set KEEPALIVE=%~dp0keep-alive.ps1

schtasks /Query /TN "%TASK%" >nul 2>&1
if %errorlevel%==0 (
    echo [1/1] Starting scheduled task "%TASK%" ...
    schtasks /Run /TN "%TASK%" >nul
    echo Done. The supervisor will keep opencode serve and the bot running.
    echo Bot: @Civieg22_bot  ^|  close this window safely.
    timeout /t 4 /nobreak >nul
    exit /b 0
)

echo [1/1] Scheduled task not found - starting hidden supervisor ...
powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "%KEEPALIVE%"
echo Done. Bot: @Civieg22_bot  ^|  close this window safely.
timeout /t 4 /nobreak >nul
