# SiteEngineerPro - Master Phase Deployment & Synchronization
# Ultimate Professional Workflow v4.0

$ErrorActionPreference = "Stop"

Write-Host "--- [1/4] Running Master Logic Verification ---" -ForegroundColor Cyan
./gradlew test

if ($LASTEXITCODE -ne 0) {
    Write-Error "Logic Verification Failed! Fix errors before push."
    exit $LASTEXITCODE
}

Write-Host "--- [2/4] Final Project Build (Debug) ---" -ForegroundColor Cyan
./gradlew assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Error "Final Build Failed! Synchronizing blocked."
    exit $LASTEXITCODE
}

Write-Host "--- [3/4] Packaging & Staging Professional Overhaul ---" -ForegroundColor Cyan
git add .
$finalCommitMsg = "The Master Phase: Biaxial Bending for Columns, AutoCAD DXF Export, Seismic Ductility Detailing, and Advanced Slab Openings. Full Engineering Suite Synchronized."
git commit -m $finalCommitMsg

Write-Host "--- [4/4] Final Synchronization with GitHub ---" -ForegroundColor Green
git push github update-from-github-master

Write-Host "--- [AUTO] Generating Technical Changelog ---" -ForegroundColor Cyan
$changelog = "BUILD: $(Get-Date -Format 'yyyy-MM-dd HH:mm')`n- Phase 6 Integrated: Execution Logs, Seismic Stability v2, Biaxial Interaction.`n- Database Migration: Schema v7 deployed.`n- AI BBS: Bin packing cutting optimization active."
$changelog | Out-File -FilePath "./CHANGELOG.md" -Append

Write-Host "`nMISSION ACCOMPLISHED! SiteEngineerPro is now a professional-grade engineering system." -ForegroundColor Green
