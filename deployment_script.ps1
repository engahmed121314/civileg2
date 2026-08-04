# SiteEngineerPro Deployment & GitHub Automation Script
# Professional DevOps Workflow v3.0

$ErrorActionPreference = "Stop"

Write-Host "--- [1/4] Running Structural Design Integrity Tests ---" -ForegroundColor Cyan
./gradlew test

if ($LASTEXITCODE -ne 0) {
    Write-Error "Unit Tests Failed! Aborting push to GitHub."
    exit $LASTEXITCODE
}

Write-Host "--- [2/4] Building Android Application (Assemble Release) ---" -ForegroundColor Cyan
./gradlew assembleDebug

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build Failed! Check Gradle logs."
    exit $LASTEXITCODE
}

Write-Host "--- [3/4] Staging Changes and Committing ---" -ForegroundColor Cyan
git add .
$commitMsg = "Full Project Overhaul: Professional Engineering Audit, Advanced BBS Engine, and Interactive Visuals."
git commit -m $commitMsg

Write-Host "--- [4/4] Pushing to GitHub (Branch: master) ---" -ForegroundColor Green
git push github update-from-github-master

Write-Host "`nDeployment Successful! SiteEngineerPro is now synchronized with GitHub." -ForegroundColor Green
