<#
Re-applies two reliability patches to the globally installed
@grinev/opencode-telegram-bot package (run after `npm update`):

1. start-bot-app.js: drop_pending_updates: true -> false
   Messages sent while the bot/PC was offline are kept instead of discarded.

2. stale-update.js: MAX_MESSAGE_AGE_SECONDS 60 -> 43200 (12 hours)
   The bot normally ignores messages older than 60s; that silently loses
   commands sent during a restart. 12h keeps them actionable.

Idempotent: safe to run repeatedly. Exits 0 when already patched.
#>

$ErrorActionPreference = "Stop"
$pkg = "$env:APPDATA\npm\node_modules\@grinev\opencode-telegram-bot\dist"
$startBot = Join-Path $pkg "app\bootstrap\start-bot-app.js"
$staleUpdate = Join-Path $pkg "bot\middleware\stale-update.js"
$changed = $false

if (Test-Path -LiteralPath $startBot) {
    $s = Get-Content -LiteralPath $startBot -Raw
    if ($s -match "drop_pending_updates:\s*true") {
        $s = $s -replace "drop_pending_updates:\s*true", "drop_pending_updates: false"
        Set-Content -LiteralPath $startBot -Value $s -NoNewline -Encoding utf8
        Write-Output "patched: drop_pending_updates=false"
        $changed = $true
    } elseif ($s -match "drop_pending_updates:\s*false") {
        Write-Output "ok: drop_pending_updates already false"
    } else {
        Write-Output "WARN: drop_pending_updates not found in $startBot"
    }
} else {
    Write-Output "WARN: missing $startBot"
}

if (Test-Path -LiteralPath $staleUpdate) {
    $s = Get-Content -LiteralPath $staleUpdate -Raw
    if ($s -match "MAX_MESSAGE_AGE_SECONDS\s*=\s*60\s*;") {
        $s = $s -replace "MAX_MESSAGE_AGE_SECONDS\s*=\s*60\s*;", "MAX_MESSAGE_AGE_SECONDS = 43200;"
        Set-Content -LiteralPath $staleUpdate -Value $s -NoNewline -Encoding utf8
        Write-Output "patched: MAX_MESSAGE_AGE_SECONDS=43200"
        $changed = $true
    } elseif ($s -match "MAX_MESSAGE_AGE_SECONDS\s*=\s*43200\s*;") {
        Write-Output "ok: MAX_MESSAGE_AGE_SECONDS already 43200"
    } else {
        Write-Output "WARN: MAX_MESSAGE_AGE_SECONDS not found in $staleUpdate"
    }
} else {
    Write-Output "WARN: missing $staleUpdate"
}

if ($changed) { Write-Output "restart the bot to apply the patches." }
exit 0
