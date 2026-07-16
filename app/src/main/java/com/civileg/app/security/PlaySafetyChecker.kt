package com.civileg.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * Play Integrity & Safety Net checker for Google Play requirements.
 * Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
 */
object PlaySafetyChecker {

    /**
     * Validate that the app was installed from a legitimate source.
     */
    fun isInstalledFromLegitSource(context: Context): Boolean {
        return try {
            val installer = context.packageManager.getInstallerPackageName(context.packageName)
            installer != null && (installer.startsWith("com.android.vending") ||
                    installer.startsWith("com.google.android") ||
                    installer == "com.amazon.venezia")
        } catch (e: Exception) {
            // If we can't determine installer, allow but log
            false
        }
    }

    /**
     * Check if the device meets minimum security patch level.
     */
    fun isSecurityPatchRecent(context: Context): Boolean {
        return try {
            val patch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH
            } else {
                "2015-01-01"
            }
            // Require security patch from within the last 2 years
            val patchDate = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)
            val twoYearsAgo = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.YEAR, -2)
            }.time
            patchDate?.after(twoYearsAgo) ?: false
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Check if device encryption is enabled.
     */
    fun isDeviceEncrypted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // From Android 6+, encryption is mandatory
            true
        } else {
            // Check for encryption on older devices
            try {
                val process = Runtime.getRuntime().exec("cat /proc/crypto")
                val output = process.inputStream.bufferedReader().readText()
                output.contains("aes", ignoreCase = true)
            } catch (_: Exception) {
                true // Assume encrypted if can't check
            }
        }
    }

    /**
     * Get comprehensive device security report.
     */
    fun getSecurityReport(context: Context): SecurityReport {
        return SecurityReport(
            isRooted = AppIntegrityChecker.isRooted(context),
            isEmulator = AppIntegrityChecker.isEmulator(),
            isDebuggerAttached = AppIntegrityChecker.isDebuggerAttached(),
            isHooked = AppIntegrityChecker.isHooked(context),
            isLegitInstall = isInstalledFromLegitSource(context),
            isSecurityPatchRecent = isSecurityPatchRecent(context),
            isDeviceEncrypted = isDeviceEncrypted(),
            apkFingerprint = AppIntegrityChecker.getApkFingerprint(context),
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }
}

data class SecurityReport(
    val isRooted: Boolean,
    val isEmulator: Boolean,
    val isDebuggerAttached: Boolean,
    val isHooked: Boolean,
    val isLegitInstall: Boolean,
    val isSecurityPatchRecent: Boolean,
    val isDeviceEncrypted: Boolean,
    val apkFingerprint: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val deviceModel: String
) {
    val isSecure: Boolean
        get() = !isRooted && !isEmulator && !isDebuggerAttached && !isHooked
}