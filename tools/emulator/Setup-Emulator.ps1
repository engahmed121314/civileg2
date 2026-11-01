<#
One-time (idempotent) setup of the CivilEG2 test emulator.

- Installs Android command-line tools if missing (sdkmanager/avdmanager).
- Accepts SDK licenses.
- Installs the API 36.1 Google Play x86_64 system image if missing.
- Creates the AVD "civileg2_api36" (Pixel 6 profile) and applies
  test-friendly hardware settings (4 GB RAM, 6 GB data, no snapshots).
- Verifies hardware acceleration.

Run once, or any time the SDK changes:

    powershell -NoProfile -ExecutionPolicy Bypass -File tools\emulator\Setup-Emulator.ps1
#>

param(
    [string]$AvdName = "civileg2_api36",
    [string]$SystemImage = "system-images;android-36.1;google_apis_playstore;x86_64",
    [switch]$ForceRecreate
)

. "$PSScriptRoot\emulator-common.ps1"

$cmdlineToolsUrl = "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip"
$cmdlineToolsDir = Join-Path $script:SdkRoot "cmdline-tools\latest"

function Ensure-CmdlineTools {
    if (Test-Path -LiteralPath $script:SdkManager) {
        Write-Ok "cmdline-tools already installed"
        return
    }
    Write-Step "Installing Android command-line tools"
    $zip = Join-Path $env:TEMP "commandlinetools-win.zip"
    if (-not (Test-Path -LiteralPath $zip)) {
        $ProgressPreference = "SilentlyContinue"
        Invoke-WebRequest -Uri $cmdlineToolsUrl -OutFile $zip -TimeoutSec 600
    }
    New-Item -ItemType Directory -Force -Path $cmdlineToolsDir | Out-Null
    tar -xf $zip -C $cmdlineToolsDir
    $inner = Join-Path $cmdlineToolsDir "cmdline-tools"
    if (Test-Path -LiteralPath $inner) {
        Get-ChildItem -LiteralPath $inner -Force | Move-Item -Destination $cmdlineToolsDir -Force
        Remove-Item -LiteralPath $inner -Recurse -Force
    }
    if (-not (Test-Path -LiteralPath $script:SdkManager)) {
        throw "cmdline-tools install failed: $script:SdkManager not found"
    }
    Write-Ok "cmdline-tools installed"
}

function Ensure-Licenses {
    Write-Step "Accepting SDK licenses"
    $env:JAVA_HOME = $script:JavaHome
    $answers = (1..40 | ForEach-Object { "y" }) -join "`n"
    $answers | & $script:SdkManager --licenses 2>&1 | Out-Null
}

function Ensure-SystemImage {
    $installed = (& $script:SdkManager --list_installed 2>$null) -join "`n"
    if ($installed -match [regex]::Escape($SystemImage)) {
        Write-Ok "System image installed: $SystemImage"
        return
    }
    Write-Step "Installing system image: $SystemImage (about 2 GB)"
    $env:JAVA_HOME = $script:JavaHome
    & $script:SdkManager $SystemImage 2>&1 | ForEach-Object { if ($_ -match '\d+%') { Write-Host $_ } }
    if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed to install $SystemImage" }
}

function Ensure-Avd {
    $existing = (& $script:EmulatorExe -list-avds 2>$null)
    if (($existing -contains $AvdName) -and (-not $ForceRecreate)) {
        Write-Ok "AVD already exists: $AvdName"
        return
    }
    if ($existing -contains $AvdName) {
        Write-Step "Removing existing AVD $AvdName"
        & $script:AvdManager delete avd -n $AvdName 2>&1 | Out-Null
    }
    Write-Step "Creating AVD $AvdName"
    $env:JAVA_HOME = $script:JavaHome
    "no" | & $script:AvdManager create avd -n $AvdName -k $SystemImage -d pixel_6 --force 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "avdmanager failed to create $AvdName" }
}

function Set-ConfigValue {
    param(
        [string]$Path,
        [hashtable]$Values
    )
    $lines = @(Get-Content -LiteralPath $Path)
    $keys = @($lines | ForEach-Object { ($_ -split '=', 2)[0] })
    $output = New-Object System.Collections.Generic.List[string]
    foreach ($line in $lines) {
        $key = ($line -split '=', 2)[0]
        if ($Values.ContainsKey($key)) {
            $output.Add("$key=$($Values[$key])")
        } else {
            $output.Add($line)
        }
    }
    foreach ($key in $Values.Keys) {
        if ($keys -notcontains $key) { $output.Add("$key=$($Values[$key])") }
    }
    Set-Content -LiteralPath $Path -Value $output -Encoding ASCII
}

function Optimize-AvdConfig {
    $config = Join-Path $env:USERPROFILE ".android\avd\$AvdName.avd\config.ini"
    if (-not (Test-Path -LiteralPath $config)) {
        throw "AVD config not found: $config"
    }
    Write-Step "Applying test-friendly AVD settings"
    Set-ConfigValue -Path $config -Values @{
        "PlayStore.enabled"                    = "yes"
        "hw.ramSize"                           = "3072"
        "vm.heapSize"                          = "512"
        "hw.cpu.ncore"                         = "4"
        "disk.dataPartition.size"              = "2G"
        "disk.cachePartition.size"             = "64M"
        "hw.keyboard"                          = "yes"
        "hw.gpu.enabled"                       = "yes"
        "hw.gpu.mode"                          = "swiftshader_indirect"
        "showDeviceFrame"                      = "no"
        "hw.audioInput"                        = "no"
        "hw.audioOutput"                       = "no"
        "fastboot.forceColdBoot"               = "yes"
        "fastboot.forceFastBoot"               = "no"
        "fastboot.forceChosenSnapshotBoot"     = "no"
        "firstboot.bootFromDownloadableSnapshot" = "no"
        "firstboot.bootFromLocalSnapshot"      = "no"
        "firstboot.saveToLocalSnapshot"        = "no"
    }
    Write-Ok "AVD settings applied"
}

function Test-Acceleration {
    Write-Step "Checking hardware acceleration"
    $check = Join-Path $script:SdkRoot "emulator\emulator-check.exe"
    if (Test-Path -LiteralPath $check) {
        $result = (& $check accel 2>&1) -join "`n"
        Write-Host $result
        if ($result -match 'not installed|not usable|0x') {
            Write-Warn "Acceleration may be unavailable; the emulator will be slow."
        } else {
            Write-Ok "Acceleration available"
        }
    }
}

Ensure-CmdlineTools
Ensure-Licenses
Ensure-SystemImage
Ensure-Avd
Optimize-AvdConfig
Test-Acceleration

Write-Ok "Emulator setup complete."
Write-Host ""
Write-Host "Next:"
Write-Host "  powershell -File tools\emulator\Start-Emulator.ps1 -Windowed"
Write-Host "  powershell -File tools\emulator\Test-App.ps1"
