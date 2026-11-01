<#
Boot the CivilEG2 test emulator and wait until Android is fully ready.

    powershell -File tools\emulator\Start-Emulator.ps1              # headless
    powershell -File tools\emulator\Start-Emulator.ps1 -Windowed    # visible window
    powershell -File tools\emulator\Start-Emulator.ps1 -ColdBoot    # clean boot (no snapshot)
    powershell -File tools\emulator\Start-Emulator.ps1 -WipeData    # factory reset

Exit code 0 = emulator booted and ready. The serial is printed and saved to
artifacts\emulator\current-serial.txt for the other scripts.
#>

param(
    [string]$AvdName = "civileg2_api36",
    [int]$Port = 5554,
    [switch]$Windowed,
    [switch]$ColdBoot,
    [switch]$WipeData,
    [int]$TimeoutSeconds = 600
)

. "$PSScriptRoot\emulator-common.ps1"

$serial = "emulator-$Port"

# Already running?
$existing = Get-EmulatorSerial
if ($existing -and (-not $WipeData)) {
    Write-Step "An emulator is already running ($existing); waiting for it to be ready"
    if (Wait-ForBoot -Serial $existing -TimeoutSeconds $TimeoutSeconds) {
        Write-Ok "Emulator already running: $existing"
        Initialize-Device -Serial $existing
        Save-CurrentSerial -Serial $existing
        exit 0
    }
    throw "Emulator $existing is running but did not finish booting. Stop it with Stop-Emulator.ps1 or wait."
}

$avds = @(& $script:EmulatorExe -list-avds 2>$null)
if ($avds -notcontains $AvdName) {
    throw "AVD '$AvdName' not found. Run tools\emulator\Setup-Emulator.ps1 first."
}

New-Item -ItemType Directory -Force -Path $script:ArtifactsRoot | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$stdoutLog = Join-Path $script:ArtifactsRoot "emulator-$stamp.out.log"
$stderrLog = Join-Path $script:ArtifactsRoot "emulator-$stamp.err.log"

$emuArgs = @(
    "-avd", $AvdName,
    "-port", "$Port",
    "-no-audio",
    "-no-boot-anim",
    "-netdelay", "none",
    "-netspeed", "full",
    "-gpu", "swiftshader_indirect",
    "-camera-back", "none",
    "-camera-front", "none",
    "-no-metrics"
)
if (-not $Windowed) { $emuArgs += "-no-window" }
if ($ColdBoot -or $WipeData) {
    $emuArgs += @("-no-snapshot-load", "-no-snapshot-save")
}
if ($WipeData) { $emuArgs += "-wipe-data" }

Write-Step "Starting emulator '$AvdName' on port $Port (headless=$(-not $Windowed))"
Write-Host "    log: $stdoutLog"

if ($Windowed) {
    # Interactive use: a normal child process is fine (and shows the window).
    $proc = Start-Process -FilePath $script:EmulatorExe `
        -ArgumentList $emuArgs `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru
} else {
    # Headless/agent use: launch detached from this shell via WMI, so the
    # emulator survives after the calling command/session ends.
    $launcher = Join-Path $script:ArtifactsRoot "start-emulator-$stamp.bat"
    $emuLine = '"' + $script:EmulatorExe + '" ' + ($emuArgs -join ' ') +
        ' > "' + $stdoutLog + '" 2> "' + $stderrLog + '"'
    Set-Content -LiteralPath $launcher -Value @("@echo off", $emuLine) -Encoding ASCII
    $create = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
        CommandLine      = 'cmd.exe /c "' + $launcher + '"'
        CurrentDirectory = $script:RepoRoot
    }
    if ($create.ReturnValue -ne 0) {
        throw "Failed to start emulator via WMI (return $($create.ReturnValue))"
    }
}

function Test-EmulatorProcess {
    $procs = Get-CimInstance Win32_Process -Filter "Name='qemu-system-x86_64-headless.exe' OR Name='qemu-system-x86_64.exe'" -ErrorAction SilentlyContinue
    foreach ($p in $procs) {
        if ($p.CommandLine -and $p.CommandLine -like "*$AvdName*") { return $true }
    }
    return $false
}

# Wait for the ADB device to appear.
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$online = $false
$graceUntil = (Get-Date).AddSeconds(60)
while ((Get-Date) -lt $deadline) {
    if ((-not $Windowed) -and (Get-Date) -gt $graceUntil -and -not (Test-EmulatorProcess)) {
        $tail = ""
        if (Test-Path -LiteralPath $stderrLog) {
            $tail = (Get-Content -LiteralPath $stderrLog -Tail 30) -join "`n"
        }
        throw "Emulator process is not running. See $stdoutLog`n$tail"
    }
    $devices = (& $script:Adb devices 2>$null) -join "`n"
    if ($devices -match ([regex]::Escape($serial) + '\s+device')) {
        $online = $true
        break
    }
    Start-Sleep -Seconds 3
}

if (-not $online) {
    throw "Emulator did not appear on ADB within $TimeoutSeconds seconds. See $stdoutLog"
}

Write-Step "Waiting for Android boot to complete (first boot can take a few minutes)"
if (-not (Wait-ForBoot -Serial $serial -TimeoutSeconds $TimeoutSeconds)) {
    throw "Android did not finish booting within $TimeoutSeconds seconds. See $stdoutLog"
}

Initialize-Device -Serial $serial
Save-CurrentSerial -Serial $serial
Start-Sleep -Seconds 2

Write-Ok "Emulator ready: $serial"
Write-Host "    Screenshot: powershell -File tools\emulator\Capture-Screens.ps1"
Write-Host "    Stop:       powershell -File tools\emulator\Stop-Emulator.ps1"
