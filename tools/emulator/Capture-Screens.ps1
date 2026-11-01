<#
Capture screenshots of the app running on the emulator.

    powershell -File tools\emulator\Capture-Screens.ps1
    powershell -File tools\emulator\Capture-Screens.ps1 -Windowed? (not used)
    powershell -File tools\emulator\Capture-Screens.ps1 -TapText "الكمرات"

Default: launches the app, waits, then taps each bottom-navigation tab
(Home / Design / Steel / Tools / More) and saves a PNG per tab, plus UI
dumps and logcat, under artifacts\emulator\screens\<timestamp>\.

Use -TapText to open a specific page by its visible label before capturing.
Use -NoLaunch to capture whatever is already on screen.
#>

param(
    [string]$Serial,
    [string]$OutDir,
    [int]$WaitSeconds = 10,
    [string]$TapText,
    [switch]$NoLaunch,
    [switch]$KeepUiDumps
)

. "$PSScriptRoot\emulator-common.ps1"

$serial = Get-Serial -Serial $Serial
if (-not $serial) { throw "No emulator running. Start one with tools\emulator\Start-Emulator.ps1" }
if (-not (Test-DeviceOnline -Serial $serial)) { throw "Device $serial is not online" }

if (-not $OutDir) {
    $OutDir = Join-Path $script:ArtifactsRoot ("screens\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Get-Slug([string]$Text, [int]$Index) {
    if (-not $Text) { return ("{0:D2}" -f $Index) }
    $slug = ($Text -replace '[^\p{L}\p{N}]+', '-').Trim('-')
    if ($slug.Length -gt 40) { $slug = $slug.Substring(0, 40) }
    if (-not $slug) { $slug = ("{0:D2}" -f $Index) }
    return ("{0:D2}-{1}" -f $Index, $slug)
}

function Get-BottomNavNodes {
    param([string]$DumpPath, [int]$ScreenHeight)
    $result = @()
    try {
        [xml]$xml = Get-Content -LiteralPath $DumpPath -Raw
        $threshold = [int]($ScreenHeight * 0.78)
        foreach ($node in $xml.SelectNodes("//node")) {
            $desc = $node.GetAttribute("content-desc")
            if (-not $desc) { continue }
            if ($node.GetAttribute("bounds") -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { continue }
            $x1 = [int]$Matches[1]; $y1 = [int]$Matches[2]
            $x2 = [int]$Matches[3]; $y2 = [int]$Matches[4]
            $cy = [int](($y1 + $y2) / 2)
            if ($cy -lt $threshold) { continue }
            $result += [pscustomobject]@{
                Description = $desc
                X = [int](($x1 + $x2) / 2)
                Y = $cy
            }
        }
    } catch {
        Write-Warn "Could not parse UI dump: $($_.Exception.Message)"
    }
    return $result | Sort-Object X
}

if (-not $NoLaunch) {
    Start-App -Serial $serial
    Write-Step "Waiting $WaitSeconds s for the app to settle"
    Start-Sleep -Seconds $WaitSeconds
}

$size = Get-ScreenSize -Serial $serial
$index = 0

function Capture([string]$Name) {
    $file = Join-Path $OutDir ("{0}.png" -f $Name)
    Save-Screenshot -Serial $serial -Path $file
    Write-Host "    $file"
}

Write-Step "Capturing initial screen"
Capture ("{0:D2}-launch" -f $index)
$index++

# Optional targeted tap.
if ($TapText) {
    $dumpPath = Join-Path $OutDir "ui-target.xml"
    Save-UiDump -Serial $serial -Path $dumpPath
    [xml]$xml = Get-Content -LiteralPath $dumpPath -Raw
    $found = $null
    foreach ($node in $xml.SelectNodes("//node")) {
        $text = $node.GetAttribute("text")
        $desc = $node.GetAttribute("content-desc")
        if (($text -and $text -like "*$TapText*") -or ($desc -and $desc -like "*$TapText*")) {
            if ($node.GetAttribute("bounds") -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                $found = @{
                    X = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
                    Y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
                }
                break
            }
        }
    }
    if ($found) {
        Write-Step "Tapping '$TapText' at $($found.X),$($found.Y)"
        Tap -Serial $serial -X $found.X -Y $found.Y
        Start-Sleep -Seconds 3
        Capture (Get-Slug $TapText $index)
        $index++
    } else {
        Write-Warn "Text not found on screen: $TapText"
    }
}

if (-not $TapText) {
    # Discover the bottom-navigation tabs from the accessibility tree.
    $dumpPath = Join-Path $OutDir "ui-tabs.xml"
    Save-UiDump -Serial $serial -Path $dumpPath
    $tabs = @(Get-BottomNavNodes -DumpPath $dumpPath -ScreenHeight $size.Height)
    $unique = @()
    $seen = @{}
    foreach ($tab in $tabs) {
        if (-not $seen.ContainsKey($tab.Description)) {
            $seen[$tab.Description] = $true
            $unique += $tab
        }
    }

    if ($unique.Count -ge 3) {
        Write-Step "Found $($unique.Count) bottom tabs"
        foreach ($tab in $unique) {
            Write-Step "Tab: $($tab.Description)"
            Tap -Serial $serial -X $tab.X -Y $tab.Y
            Start-Sleep -Seconds 3
            Capture (Get-Slug $tab.Description $index)
            $index++
        }
    } else {
        Write-Warn "Bottom tabs not detected; falling back to fixed positions"
        $y = $size.Height - 150
        $xs = @(0.1, 0.3, 0.5, 0.7, 0.9) | ForEach-Object { [int]($size.Width * $_) }
        foreach ($x in $xs) {
            Tap -Serial $serial -X $x -Y $y
            Start-Sleep -Seconds 2
            Capture ("{0:D2}-tab" -f $index)
            $index++
        }
    }
}

# Final UI dump + logcat for debugging.
Save-UiDump -Serial $serial -Path (Join-Path $OutDir "ui-final.xml")
if (-not $KeepUiDumps) {
    Get-ChildItem -LiteralPath $OutDir -Filter "ui-*.xml" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne "ui-final.xml" } |
        Remove-Item -Force -ErrorAction SilentlyContinue
}
& $script:Adb -s $serial logcat -d -t 800 2>$null | Out-File -LiteralPath (Join-Path $OutDir "logcat.txt") -Encoding utf8

Write-Ok "Screenshots saved to $OutDir"
