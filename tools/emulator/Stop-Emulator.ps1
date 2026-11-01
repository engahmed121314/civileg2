<#
Stop the CivilEG2 test emulator.

    powershell -File tools\emulator\Stop-Emulator.ps1
    powershell -File tools\emulator\Stop-Emulator.ps1 -Serial emulator-5554
    powershell -File tools\emulator\Stop-Emulator.ps1 -All   # every running emulator
#>

param(
    [string]$Serial,
    [switch]$All,
    [int]$TimeoutSeconds = 60
)

. "$PSScriptRoot\emulator-common.ps1"

$targets = @()
if ($All) {
    $devices = (& $script:Adb devices 2>$null) -join "`n"
    $targets = [regex]::Matches($devices, '(emulator-\d+)\s+device') | ForEach-Object { $_.Groups[1].Value }
} else {
    $targets = @(Get-Serial -Serial $Serial)
}

if (-not $targets -or $targets.Count -eq 0 -or -not $targets[0]) {
    Write-Warn "No running emulator found"
    exit 0
}

foreach ($target in ($targets | Select-Object -Unique)) {
    Write-Step "Stopping $target"
    & $script:Adb -s $target emu kill 2>$null | Out-Null
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    $remaining = (& $script:Adb devices 2>$null) -join "`n"
    $stillRunning = $false
    foreach ($target in ($targets | Select-Object -Unique)) {
        if ($remaining -match ([regex]::Escape($target) + '\s+')) { $stillRunning = $true }
    }
    if (-not $stillRunning) { break }
    Start-Sleep -Seconds 2
}

if (Test-Path -LiteralPath $script:SerialFile) {
    Remove-Item -LiteralPath $script:SerialFile -Force -ErrorAction SilentlyContinue
}

Write-Ok "Emulator stopped"
