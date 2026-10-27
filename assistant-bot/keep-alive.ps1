<# CivilEG2 Assistant — keep-alive supervisor.
Ensures `opencode serve` (port 4097) and the Telegram bot stay running.
- Runs hidden (Scheduled Task uses -WindowStyle Hidden).
- Launches children hidden WITHOUT cmd.exe wrapper -> no flashing console.
- Singleton detection via process CommandLine (not window title).
- Health check = live TCP socket to Telegram (149.154.*/91.108.*).
  No getUpdates probing (it produced false negatives and killed healthy bots).
- Requires 2 consecutive failures before restarting a bot.
#>

$ProjectDir = "C:\Users\ahmed\AndroidStudioProjects\civileg2"
$ServeExe   = "$env:APPDATA\npm\node_modules\opencode-ai\bin\opencode.exe"
$NodeExe    = "C:\Program Files\nodejs\node.exe"
$BotEntry   = "C:\Users\ahmed\AppData\Roaming\npm\node_modules\@grinev\opencode-telegram-bot\dist\cli.js"
$LogFile    = "$env:APPDATA\opencode-telegram-bot\keepalive.log"

function Log($m) {
    Add-Content $LogFile ("[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $m)
}

function Serve-Up {
    return $null -ne (Get-NetTCPConnection -LocalPort 4097 -State Listen -ErrorAction SilentlyContinue)
}

function Bot-Procs {
    return @(Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like "*opencode-telegram-bot*cli.js*" })
}

function Bot-TgConnected($procId) {
    $tg = Get-NetTCPConnection -OwningProcess $procId -State Established -ErrorAction SilentlyContinue |
        Where-Object { $_.RemoteAddress -like "149.154.*" -or $_.RemoteAddress -like "91.108.*" }
    return $null -ne $tg
}

function Start-Bot {
    Log "bot down -> starting hidden"
    Start-Process -FilePath $NodeExe `
        -ArgumentList "`"$BotEntry`"" `
        -WorkingDirectory $ProjectDir -WindowStyle Hidden
    Start-Sleep -Seconds 10
}

$failCount = 0
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

        $procs = Bot-Procs
        if ($procs.Count -eq 0) {
            if ((Test-Path -LiteralPath $NodeExe) -and (Test-Path -LiteralPath $BotEntry)) {
                $failCount = 0
                Start-Bot
            } else {
                Log "bot entry missing: $NodeExe / $BotEntry"
            }
        } else {
            if ($procs.Count -gt 1) {
                $keep = ($procs | Sort-Object CreationDate -Descending | Select-Object -First 1)
                foreach ($dup in ($procs | Where-Object { $_.ProcessId -ne $keep.ProcessId })) {
                    Log "duplicate bot PID $($dup.ProcessId) -> killing (keeping $($keep.ProcessId))"
                    Stop-Process -Id $dup.ProcessId -Force -ErrorAction SilentlyContinue
                }
                $procs = @($keep)
            }
            $one = $procs[0]
            $age = ((Get-Date) - $one.CreationDate).TotalSeconds
            if ($age -lt 180) {
                $failCount = 0
            } elseif (Bot-TgConnected $one.ProcessId) {
                $failCount = 0
            } else {
                $failCount++
                Log "bot PID $($one.ProcessId) has no Telegram socket (fail $failCount/2)"
                if ($failCount -ge 2) {
                    Log "bot hung -> restarting"
                    Stop-Process -Id $one.ProcessId -Force -ErrorAction SilentlyContinue
                    Start-Sleep -Seconds 3
                    $failCount = 0
                    Start-Bot
                }
            }
        }
    } catch {
        Log ("supervisor error: " + $_.Exception.Message)
    }
    Start-Sleep -Seconds 60
}
