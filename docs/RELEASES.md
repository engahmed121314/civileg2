# Releasing Civil Engineer Pro

Two ways to publish an installable APK. Both produce the same artifact:
`CivilEngineerPro-<version>.apk`, signed with the release key, `free` flavor
(no ads, no billing).

## 1. GitHub Actions (recommended)

The workflow `.github/workflows/release-apk.yml` runs on any `v*` tag and
uploads `app-free-release.apk` to the GitHub Release automatically.

Required repository secrets:

| Secret | Content |
|---|---|
| `KEYSTORE_BASE64` | `certutil -encode civileg-release.jks tmp.b64` then paste the base64 body |
| `KEY_ALIAS` | `civileg` |
| `KEY_PASSWORD` | release key password |
| `KEYSTORE_PASSWORD` | release keystore password |

Then:

```bash
git tag v1.8.0
git push origin v1.8.0
```

## 2. Local build (used for 1.8.0)

Prerequisites: JDK 17+ (`JAVA_HOME`), Android SDK 36, and the untracked
`civileg-release.jks` + `keystore.properties` at the repository root.
**Never commit either file.**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleFreeRelease
```

Output: `app/build/outputs/apk/free/release/app-free-release.apk`

Verify before publishing:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v -keystore civileg-release.jks -alias civileg
# SHA-256 of the APK certificate must equal signing.fingerprint.sha256 in local.properties
Get-FileHash app\build\outputs\apk\free\release\app-free-release.apk -Algorithm SHA256
```

## 3. Verify the published artifact

```powershell
# APK signature scheme v2 and certificate
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --print-certs .\CivilEngineerPro-1.8.0.apk

# Package id / version
& "$env:ANDROID_HOME\build-tools\36.0.0\aapt2.exe" dump badging .\CivilEngineerPro-1.8.0.apk |
    Select-String 'package: name|sdkVersion|targetSdkVersion'
```

Expected: `package: name='com.civileg.app' versionCode='8' versionName='1.8.0'`,
`minSdkVersion:'26'`, `targetSdkVersion:'36'`.

## 4. Release checklist

- [ ] `:app:testFreeDebugUnitTest` green
- [ ] `:app:assembleFreeDebug` green
- [ ] `:app:assembleFreeRelease` green
- [ ] `versionCode` and `versionName` bumped in `app/build.gradle.kts`
- [ ] `CHANGELOG.md` updated
- [ ] APK signed by `civileg` (fingerprint above)
- [ ] Release notes state that the `free` APK has no ads and no billing
- [ ] No keystore, password or `local.properties` in `git status`
- [ ] Asset uploaded and its SHA-256 published in the release notes
