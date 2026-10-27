<#
.SYNOPSIS
    Builds the CivilEG2 debug APK, uploads it to an external host (catbox.moe),
    and sends the install link + the APK file to the configured Telegram chat.

.EXAMPLE
    pwsh -File assistant-bot/publish-apk.ps1
    pwsh -File assistant-bot/publish-apk.ps1 -SkipBuild
    pwsh -File assistant-bot/publish-apk.ps1 -NoDocument

.NOTES
    Reads BOT_TOKEN and ALLOWED_CHAT_ID from assistant-bot/config.env.
    Never prints the token.
#>
param(
    [switch]$SkipBuild,
    [switch]$NoDocument,
    [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Get-Config {
    param([string]$Path)
    $cfg = @{}
    if (-not (Test-Path $Path)) { throw "Missing config file: $Path" }
    Get-Content $Path | Where-Object { $_ -match '=' -and $_ -notmatch '^\s*#' } | ForEach-Object {
        $kv = $_ -split '=', 2
        $cfg[$kv[0].Trim()] = $kv[1].Trim().Trim('"')
    }
    return $cfg
}

$cfg = Get-Config (Join-Path $PSScriptRoot "config.env")
$token = $cfg['BOT_TOKEN']
$chat = $cfg['ALLOWED_CHAT_ID']
if (-not $token -or -not $chat) { throw "BOT_TOKEN / ALLOWED_CHAT_ID missing in config.env" }

if (-not $SkipBuild) {
    Write-Host "Building debug APK..." -ForegroundColor Cyan
    & .\gradlew.bat :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
}

$apk = (Resolve-Path $ApkPath).Path
$apkItem = Get-Item $apk
$sizeMb = [math]::Round($apkItem.Length / 1MB, 2)
Write-Host "APK: $apk ($sizeMb MB)" -ForegroundColor Cyan

$ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"

function Invoke-Upload {
    param(
        [string]$File,
        [string]$Endpoint,
        [string[]]$ExtraFields = @()
    )
    $tmp = Join-Path $env:TEMP ("upload-" + [guid]::NewGuid().ToString("N") + ".txt")
    try {
        $args = @("-4", "-sS", "-A", $ua, "--max-time", "900", "-o", $tmp, "-w", "%{http_code}")
        $args += "-F", "reqtype=fileupload"
        foreach ($f in $ExtraFields) { $args += "-F", $f }
        $args += "-F", "fileToUpload=@$File", $Endpoint
        $code = & curl.exe @args
        if ($LASTEXITCODE -eq 0 -and "$code" -eq "200") {
            $u = (Get-Content $tmp -Raw -ErrorAction SilentlyContinue)
            if ($u) {
                $u = $u.Trim()
                if ($u -match '^https?://') { return $u }
            }
        }
        return $null
    } finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

$link = $null
for ($i = 1; $i -le 3 -and -not $link; $i++) {
    Write-Host "Upload attempt $i/3 (catbox.moe)..." -ForegroundColor Cyan
    $link = Invoke-Upload -File $apk -Endpoint "https://catbox.moe/user/api.php"
    if (-not $link -and $i -lt 3) { Start-Sleep -Seconds 5 }
}
if (-not $link) {
    for ($i = 1; $i -le 2 -and -not $link; $i++) {
        Write-Host "Fallback upload $i/2 (litterbox 72h)..." -ForegroundColor Yellow
        $link = Invoke-Upload -File $apk -Endpoint "https://litterbox.catbox.moe/resources/internals/api.php" -ExtraFields @("time=72h")
        if (-not $link -and $i -lt 2) { Start-Sleep -Seconds 5 }
    }
}
if (-not $link) { throw "All upload attempts failed" }
Write-Host "Link: $link" -ForegroundColor Green

$testCount = ""
$resultsDir = Join-Path $repoRoot "app/build/test-results/testDebugUnitTest"
if (Test-Path $resultsDir) {
    $tests = 0; $fail = 0
    Get-ChildItem $resultsDir -Filter *.xml | ForEach-Object {
        [xml]$x = Get-Content $_.FullName
        $tests += [int]$x.testsuite.tests
        $fail += [int]$x.testsuite.failures + [int]$x.testsuite.errors
    }
    $testCount = "`n🧪 الاختبارات: $($tests - $fail)/$tests ناجحة"
}

$text = "✅ تم بناء APK جديد لتطبيق CivilEG2`n`n📦 الحجم: $sizeMb MB`n🔗 رابط التثبيت المباشر:`n$link$testCount`n`n📱 للتثبيت: افتح الرابط ثم اسمح بـ«تثبيت من مصادر غير معروفة» إن طُلب.`n🕒 $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
$body = @{ chat_id = $chat; text = $text; disable_web_page_preview = $false } | ConvertTo-Json -Compress
$resp = curl.exe -4 -sS --max-time 60 -X POST "https://api.telegram.org/bot$token/sendMessage" `
    -H "Content-Type: application/json" -d $body 2>&1
$j = $resp | ConvertFrom-Json
if (-not $j.ok) { throw "Telegram sendMessage failed: $($j.description)" }
Write-Host "Telegram message sent (id=$($j.result.message_id))" -ForegroundColor Green

if (-not $NoDocument -and $apkItem.Length -lt 50MB) {
    Write-Host "Sending APK document to Telegram..." -ForegroundColor Cyan
    $resp = curl.exe -4 -sS --max-time 900 -F "chat_id=$chat" `
        -F "caption=CivilEG2 debug build - $sizeMb MB" `
        -F "document=@$apk;type=application/vnd.android.package-archive" `
        "https://api.telegram.org/bot$token/sendDocument" 2>&1
    $j = $resp | ConvertFrom-Json
    if ($j.ok) { Write-Host "Telegram document sent (id=$($j.result.message_id))" -ForegroundColor Green }
    else { Write-Warning "sendDocument failed: $($j.description)" }
}

Write-Host "Done: $link" -ForegroundColor Green
