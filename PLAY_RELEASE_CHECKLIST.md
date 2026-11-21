# Play Store Release Checklist — Civil Engineer Pro

Checklist for publishing the **`play` flavor** to Google Play. For the direct
(no-ads) `free` APK release, see [docs/RELEASES.md](docs/RELEASES.md).

The two flavors differ in ways that matter to Play review:

| | `play` flavor | `free` flavor |
|---|---|---|
| Ads (AdMob) | Yes (banner/interstitial/native) | No |
| UMP consent (EEA) | Yes | No |
| Play Billing | Yes (donations) | No |
| `AD_ID` / `BILLING` permissions | Present | Must be absent |

---

## 1. Version and build

- [ ] Bump `versionCode` and `versionName` in `app/build.gradle.kts`
      (current: `versionCode 8`, `versionName "1.8.0"`). Play rejects an
      artifact whose `versionCode` was already uploaded.
- [ ] `targetSdk` is current (currently 36) — Play requires a recent target.
- [ ] Update `CHANGELOG.md`.
- [ ] CI green on master: `./gradlew lintFreeDebug`, `testFreeDebugUnitTest`,
      `jacocoCoverageVerification`, both flavor builds.
- [ ] Code-fidelity spot check (product priority): for each design code
      (ECP, SBC, ACI/AISC), run one reference design per major element type
      and confirm the selected code is what the engines actually apply and
      that results match the code's expected values - the app is a working
      reference for site and technical-office engineers, so wrong or
      mislabeled code output is a release blocker.
- [ ] Build the release bundle locally and smoke-test it on a real device —
      debug behavior is not enough (see R8 section):
      `./gradlew :app:bundlePlayRelease`
      Output: `app/build/outputs/bundle/playRelease/app-play-release.aab`

## 2. Signing

Release signing is configured in `app/build.gradle.kts` and resolves in this
order: `keystore.properties` (local, untracked) → env vars
`KEY_ALIAS` / `KEY_PASSWORD` / `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` (CI).

- [ ] `civileg-release.jks` and `keystore.properties` exist locally and are
      **never committed** (both are untracked; keep them out of git).
- [ ] Back up the keystore and passwords somewhere safe. If Play App Signing
      is **not** enrolled, losing this key means losing the ability to update
      the app. If it is enrolled, the upload key can be reset via Play Support.
- [ ] Verify the certificate fingerprint and record it in
      `local.properties` as `signing.fingerprint.sha256` — the app checks it
      at runtime (`EXPECTED_SIGNER_FINGERPRINT` in `BuildConfig`); an empty
      value skips the check, a wrong value trips the integrity guard:

      ```
      keytool -list -v -keystore civileg-release.jks -alias civileg
      ```

- [ ] For CI releases, confirm repo secrets `KEYSTORE_BASE64`, `KEY_ALIAS`,
      `KEY_PASSWORD`, `KEYSTORE_PASSWORD` are set (see docs/RELEASES.md).
- [ ] Enroll in **Play App Signing** (recommended) before the first upload.

## 3. R8 / ProGuard

Release builds run with `isMinifyEnabled = true` and
`isShrinkResources = true` using `app/proguard-rules.pro`.

- [ ] Test the **release** build (not debug) end to end on a device: design
      flows, PDF/DXF export, Room persistence, Hilt injection, and the play
      flavor's ads + billing. Reflection-based crashes only appear in release.
- [ ] Keep `proguard-rules.pro` coverage current when adding libraries —
      it already keeps Room entities/DAOs/converters, Hilt, Kotlin metadata
      and the domain model; new SDKs usually need their own rules (check the
      SDK's docs).
- [ ] Keep the R8 mapping file from every release
      (`app/build/outputs/mapping/playRelease/mapping.txt`) for deobfuscating
      crashes. Firebase Crashlytics gets it automatically via the Crashlytics
      Gradle plugin — confirm the upload succeeds in the release build log.
- [ ] If `isShrinkResources` strips something the app loads dynamically,
      add a `res/raw/keep.xml` or explicit keep rule.

## 4. Privacy policy

- [ ] **A public privacy policy URL is required** — the `play` flavor ships
      AdMob and Firebase Crashlytics, so Play will not approve without one.
      There is currently no privacy policy URL in this repo; create and host
      one (GitHub Pages from this repository is the simplest option) before
      submission.
- [ ] Add the URL in Play Console → App content → Privacy policy.
- [ ] Link the same URL in-app (Settings is the natural place).
- [ ] The policy must cover: AdMob (personalized/non-personalized ads,
      device identifiers), Firebase Crashlytics (crash data, device IDs),
      Play Billing (purchase records), and Play Integrity.

## 5. Data Safety form (Play Console → App content → Data safety)

Answers must match the **merged manifest and SDK behavior of the `play`
flavor**. Wrong answers are a common rejection/removal cause. As a starting
point, review and confirm:

- [ ] AdMob / Google Play services: collects *Device or other IDs* and
      *Approximate location* for advertising — declared as shared with third
      parties, purpose "Advertising or marketing".
- [ ] Firebase Crashlytics: *Crash logs* and *Diagnostics*, purpose
      "App functionality / Analytics".
- [ ] Play Billing: *Purchase history*, purpose "App functionality".
- [ ] No account creation in-app → no personal identifiers collected directly.
- [ ] Data encrypted in transit: yes (HTTPS).
- [ ] If the `free` flavor is ever published to Play as a separate listing,
      its answers differ (no ads, no billing) — re-run the form per flavor.
- [ ] After any dependency change (check Dependabot PRs), re-verify the form
      still matches the merged dependencies.

## 6. Play Console declarations

- [ ] Ads declaration: **Yes**, the `play` flavor contains ads.
- [ ] Content rating questionnaire (IARC) completed.
- [ ] Target audience: adults (professional engineering tool).
- [ ] UMP/consent: EEA consent flow is built in via the UMP SDK
      (`user-messaging-platform`) — verify it shows on first launch in an EEA
      region (or VPN) and that ad personalization honors the choice.
- [ ] Store listing: title, short/full description, screenshots (phone +
      tablet), feature graphic, app icon — bilingual (EN/AR) since the app is.
- [ ] Category, contact email, and (optionally) the developer phone already
      embedded as `resValue` strings.

## 7. Final pre-submit

- [ ] Upload the `.aab` to an **internal testing** track first; install from
      Play and verify the signature/integrity check passes (a mismatched
      `signing.fingerprint.sha256` fails here).
- [ ] Check the Play pre-launch report for crashes on popular devices.
- [ ] Promote internal → production when clean.

Related: [docs/RELEASES.md](docs/RELEASES.md) (free-flavor APK workflow),
[SECURITY.md](SECURITY.md), [CONTRIBUTING.md](CONTRIBUTING.md).
