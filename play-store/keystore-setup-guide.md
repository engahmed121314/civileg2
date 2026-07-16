# ============================================================
# Keystore Setup Guide — Civil Engineer Pro
# Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
# ============================================================

## Step 1: Generate a Release Keystore

Run this command from the project root directory:

```bash
keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore civileg-release.jks \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -alias civileg \
  -dname "CN=Eng. Ahmed Magdy, OU=Development, O=AhmedMagdy, L=Cairo, ST=Cairo, C=EG" \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD
```

**IMPORTANT:** Replace YOUR_STORE_PASSWORD and YOUR_KEY_PASSWORD with strong passwords.
Save these passwords securely — you will need them for every release!

## Step 2: Create keystore.properties File

Create a file named `keystore.properties` in the project root (same level as build.gradle.kts):

```properties
storeFile=civileg-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=civileg
keyPassword=YOUR_KEY_PASSWORD
```

## Step 3: Verify keystore.properties is in .gitignore

The file should already be ignored. Verify:

```bash
# Check .gitignore contains keystore.properties and *.jks
grep "keystore" .gitignore
```

If not, add these lines to .gitignore:
```
# Signing
*.jks
*.keystore
keystore.properties
```

## Step 4: Build the Release APK

```bash
./gradlew assembleRelease
```

The signed APK will be at:
`app/build/outputs/apk/release/app-release.apk`

## Step 5: Verify the Signature

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

You should see:
- SHA-256 fingerprint
- Valid until date
- SHA1 fingerprint

## Step 6: Record the SHA-256 Fingerprint

After generating the keystore, get the fingerprint:

```bash
keytool -list -v -keystore civileg-release.jks -alias civileg
```

Copy the SHA-256 fingerprint and store it securely. You may need it for:
- Google Play Console app signing
- Firebase configuration (if used in the future)
- Google Cloud Console API configuration

## Important Notes

⚠️ **BACKUP YOUR KEYSTORE** — If you lose the keystore or its password, you will NOT be able to update your app on Google Play. Store a backup in a secure location (encrypted USB drive, cloud storage with encryption, etc.).

⚠️ **NEVER COMMIT** keystore.properties, *.jks, or passwords to Git.

⚠️ **PASSWORD SECURITY** — Use a unique, strong password for each keystore. Consider using a password manager.