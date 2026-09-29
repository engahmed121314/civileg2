<# CivilEG2 Assistant — keep-alive supervisor.
Ensures `opencode serve` (port 4096) and the Telegram bot stay running.
- Runs hidden (Scheduled Task uses -WindowStyle Hidden).
- Launches children hidden WITHOUT cmd.exe wrapper -> no flashing console.
- Singleton detection via port + process CommandLine (not window title),
  so it never spawns a new bot every minute.
#>

$ProjectDir = "C:\Users\ahmed\AndroidStudioProjects\civileg2"
$ServeExe   = "$env:LOCALAPPDATA\OpenCode\opencode-cli.exe"
$NodeExe    = "C:\Program Files\nodejs\node.exe"
$BotEntry   = "C:\Users\ahmed\AppData\Roaming\npm\node_modules\@grinev\opencode-telegram-bot\dist\cli.js"
$LogFile    = "$env:APPDATA\opencode-telegram-bot\keepalive.log"

function Log($m) {
    Add-Content $LogFile ("[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $m)
}

function Serve-Up {
    return $null -ne (Get-NetTCPConnection -LocalPort 4097 -State Listen -ErrorAction SilentlyContinue)
}

function Bot-Up {
    # True if a node.exe process is running the telegram bot entry file.
    $procs = Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like "*opencode-telegram-bot*cli.js*" }
    return $null -ne $procs
}

Log "keep-alive started (hidden, singleton mode)"

while ($true) {
    try {
        if (-not (Serve-Up)) {
            if (Test-Path -LiteralPath $ServeExe) {
                Log "serve down -> starting hidden"
                Start-Process -FilePath $ServeExe `
                    -ArgumentList "serve","--port","4097","--hostname","127.0.0.1" `
                    -WorkingDirectory $ProjectDir -WindowStyle Hidden
                Start-Sleep -Seconds 10
            } else {
                Log "serve exe missing: $ServeExe"
            }
        }
        if (-not (Bot-Up)) {
            if ((Test-Path -LiteralPath $NodeExe) -and (Test-Path -LiteralPath $BotEntry)) {
                Log "bot down -> starting hidden"
                Start-Process -FilePath $NodeExe `
                    -ArgumentList "`"$BotEntry`"" `
                    -WorkingDirectory $ProjectDir -WindowStyle Hidden
                Start-Sleep -Seconds 10
            } else {
                Log "bot entry missing: $NodeExe / $BotEntry"
            }
        }
    } catch {
        Log ("supervisor error: " + $_.Exception.Message)
    }
    Start-Sleep -Seconds 60
}
