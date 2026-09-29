@echo off
REM CivilEG2 Assistant Bot — full stack launcher (Windows)
REM Starts: 1) opencode serve (port 4096)  2) opencode-telegram bot
setlocal

set OPENCODE_BIN=%LOCALAPPDATA%\OpenCode\opencode-cli.exe
if not exist "%OPENCODE_BIN%" (
    echo [ERROR] opencode CLI not found at %OPENCODE_BIN%
    pause
    exit /b 1
)

echo [1/2] Starting opencode serve on port 4096 (hidden, no popup) ...
powershell -NoProfile -WindowStyle Hidden -Command "Start-Process -FilePath '%OPENCODE_BIN%' -ArgumentList 'serve','--port','4096','--hostname','127.0.0.1' -WindowStyle Hidden"
timeout /t 5 /nobreak >nul

echo [2/2] Starting Telegram assistant bot (hidden, no popup) ...
echo     Bot: @Civieg22_bot  ^|  stop via Task Manager or Scheduled Task.
powershell -NoProfile -WindowStyle Hidden -Command "Start-Process -FilePath 'C:\Program Files\nodejs\node.exe' -ArgumentList '\"C:\Users\ahmed\AppData\Roaming\npm\node_modules\@grinev\opencode-telegram-bot\dist\cli.js\"' -WindowStyle Hidden"
echo Started hidden. This window can be closed.
