package com.civileg.app.security

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * App Integrity Checker — prevents tampering, repackaging, and unauthorized modifications.
 * Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
 */
object AppIntegrityChecker {

    // Expected signing certificate fingerprint (SHA-256) — set during release build
    private const val EXPECTED_SIGNER_FINGERPRINT = ""

    private val SUSPICIOUS_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/system/app/KingUser.apk",
        "/system/app/KingRoot.apk",
        "/system/app/SuperSU.apk",
        "/system/etc/init.d/99SuperSUDaemon",
        "/dev/com.koushikdutta.superuser.daemon/",
        "/system/app/busybox.apk",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/",
        "/system/app/Supersu.apk",
        "/system/xbin/daemonsu",
        "/system/etc/.su_backup",
        "/data/property/me.piebridge.stoproot",
        "/cache/.disable_su",
        "/data/data/com.koushikdutta.superuser",
        "/data/data/eu.chainfire.supersu",
        "/data/data/com.noshufou.android.su",
        "/data/data/com.thirdparty.superuser",
        "/data/data/com.koushikdutta.superuser/databases",
        "/system/app/Superuser.apk",
        "/system/bin/.ext/.su"
    )

    private val SUSPICIOUS_PACKAGES = arrayOf(
        "com.koushikdutta.superuser",
        "com.noshufou.android.su",
        "eu.chainfire.supersu",
        "com.topjohnwu.magisk",
        "com.thirdparty.superuser",
        "de.robv.android.xposed",
        "de.robv.android.xposed.installer",
        "org.lsposed.manager",
        "com.tsng.hidemyapplist",
        "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus"
    )

    private val EMULATOR_INDICATORS = arrayOf(
        "goldfish", "ranchu", "vbox", "genymotion",
        "droid4x", "nox", "bluestacks", "memu"
    )

    /**
     * Run all integrity checks and return true if the device is safe.
     */
    fun isDeviceSecure(context: Context): Boolean {
        return !isRooted(context) && !isEmulator() && !isDebuggerAttached() && !isHooked(context)
    }

    /**
     * Check for root access using multiple detection methods.
     */
    fun isRooted(context: Context): Boolean {
        // Method 1: Check for SU binary
        for (path in SUSPICIOUS_PATHS) {
            if (File(path).exists()) return true
        }

        // Method 2: Check which command
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/which", "su"))
            if (process.inputStream.bufferedReader().readText().isNotEmpty()) return true
        } catch (_: Exception) {}

        // Method 3: Check for root-related packages
        val pm = context.packageManager
        for (pkg in SUSPICIOUS_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (_: Exception) {}
        }

        // Method 4: Try executing su command
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = process.outputStream
            os.write("exit\n".toByteArray())
            os.flush()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if running on an emulator.
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.MODEL.contains("Android SDK", ignoreCase = true) ||
                Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
                Build.PRODUCT.contains("sdk", ignoreCase = true) ||
                Build.PRODUCT.contains("emulator", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                EMULATOR_INDICATORS.any { Build.HINGERPRINT.contains(it, ignoreCase = true) } ||
                EMULATOR_INDICATORS.any { Build.PRODUCT.contains(it, ignoreCase = true) } ||
                (Build.BOARD.lowercase(Locale.getDefault()).let { board ->
                    board.contains("unknown") || board.isEmpty()
                }))
    }

    /**
     * Check if a debugger is attached.
     */
    fun isDebuggerAttached(): Boolean {
        return android.os.Debug.isDebuggerConnected() || android.os.Debug.waitingForDebugger()
    }

    /**
     * Check for Xposed / LSPosed / Frida hooking frameworks.
     */
    fun isHooked(context: Context): Boolean {
        // Check for Xposed
        try {
            throw Exception("hook detection")
        } catch (e: Exception) {
            val stackTrace = e.stackTrace
            for (element in stackTrace) {
                if (element.className.contains("de.robv.android.xposed") ||
                    element.className.contains("org.lsposed") ||
                    element.className.contains("com.saurik.substrate")) {
                    return true
                }
            }
        }

        // Check for suspicious libraries loaded
        try {
            val libs = File("/proc/self/maps").readText()
            if (libs.contains("frida") || libs.contains("xposed") || libs.contains("substrate")) {
                return true
            }
        } catch (_: Exception) {}

        // Check for hooking packages
        val pm = context.packageManager
        for (pkg in SUSPICIOUS_PACKAGES) {
            if (pkg.contains("xposed") || pkg.contains("lsposed")) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    return true
                } catch (_: Exception) {}
            }
        }

        return false
    }

    /**
     * Get the APK signing certificate fingerprint for verification.
     */
    fun getApkFingerprint(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures ?: emptyArray()
            }

            if (signatures.isNotEmpty()) {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(signatures[0].toByteArray())
                digest.joinToString(":") { "%02X".format(it) }
            } else {
                "UNKNOWN"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}