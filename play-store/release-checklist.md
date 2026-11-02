# ============================================================
# Play Store Release Checklist — Civil Engineer Pro
# Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com | +201012628353
# ============================================================

## Pre-Release Checklist

### 1. Signing & Build
- [ ] Generate release keystore (see keystore-setup-guide.md)
- [ ] Create keystore.properties file (NOT committed to git)
- [ ] Set `signing.fingerprint.sha256` in local.properties (see §1b below) so
      AppIntegrityChecker verifies the release signature at runtime
- [ ] Build release APK: `./gradlew assembleRelease`
- [ ] Verify APK is signed correctly: `apksigner verify --print-certs app/build/outputs/apk/release/*.apk`
- [ ] Test the signed APK on a physical device

### 1b. Production Configuration (AdMob IDs + signer fingerprint)
All production IDs are configured in **local.properties only** (never committed —
see `local.properties.template` for every supported key). Defaults are Google's
TEST IDs: test ads, zero revenue, safe for development.
- [ ] `admob.app.id` — AdMob App ID (`~` format) from the AdMob console
- [ ] `admob.banner.id`, `admob.interstitial.id`, `admob.native.id`,
      `admob.rewarded.id` — real ad-unit IDs (`/` format)
- [ ] `signing.fingerprint.sha256` — SHA-256 of the release keystore certificate:
      `keytool -list -v -keystore your-release-key.jks -alias your-alias`
      (colon-separated or bare hex, any case — normalized at compare time)
- [ ] Rebuild AFTER editing local.properties — the values are baked into
      BuildConfig at compile time, not read at runtime

### 2. Google Play Console Setup
- [ ] Create Google Play Developer account ($25 one-time fee)
- [ ] Create new application: "Civil Engineer Pro"
- [ ] Set package name: com.civileg.app
- [ ] Set default language: English (add Arabic as secondary)
- [ ] Fill in app details (use store-listing.md content)

### 3. Store Listing
- [ ] App name: Civil Engineer Pro
- [ ] Short description (EN + AR)
- [ ] Full description (EN + AR)
- [ ] App icon (512x512 PNG, no transparency)
- [ ] Feature graphic (1024x500 PNG)
- [ ] Phone screenshots (minimum 2, max 8, 16:9 aspect ratio)
- [ ] 7-inch tablet screenshots (optional)
- [ ] 10-inch tablet screenshots (optional)
- [ ] App category: Tools
- [ ] Tags: structural engineering, civil engineering, design calculator
- [ ] Contact email: eng.ahmedmagdy121314@gmail.com
- [ ] Website: (optional — GitHub repo)

### 4. Content Rating
- [ ] Complete content rating questionnaire (see content-rating-answers.txt)
- [ ] Expected rating: "Everyone"
- [ ] IAP rating: Not applicable (no in-app purchases)

### 5. Privacy Policy
- [ ] Host privacy-policy.html online (GitHub Pages recommended)
- [ ] Host privacy-policy-ar.html online
- [ ] Add privacy policy URL in Play Console
- [ ] Link must be publicly accessible (not password-protected)

### 6. App Content
- [ ] Target API Level: 36 (Android 16) ✓
- [ ] Min SDK: 26 (Android 8.0) ✓
- [x] Ads SDK present (AdMob banner/interstitial + UMP consent) — the Play
      Console **Data Safety form MUST declare** Google ad serving (ad ID,
      diagnostics; "Yes" to serving ads / sharing data for advertising); UMP
      consent flow is already integrated for EEA/UK users
- [ ] No analytics SDK that shares data beyond the ads SDK ✓
- [ ] Permissions declared and justified ✓
- [ ] No hardcoded API keys or secrets ✓ (IDs live in git-ignored local.properties)

### 7. Testing
- [ ] Test on Android 8.0 (minSdk)
- [ ] Test on Android 16 (targetSdk 36)
- [ ] Test in both Arabic and English
- [ ] Test all design modules (beam, column, slab, footing, stair, tank, retaining wall)
- [ ] Test PDF export in both languages
- [ ] Test on a rooted device (security check should warn)
- [ ] Test with no internet connection (offline mode)

### 8. Release
- [ ] Upload signed APK/AAB to Play Console
- [ ] Set release notes (EN + AR)
- [ ] Create release track (Internal Testing → Closed → Open → Production)
- [ ] Monitor crash reports after release

## Post-Release
- [ ] Monitor Google Play Console for crash reports (ANRs)
- [ ] Respond to user reviews
- [ ] Plan next version based on feedback