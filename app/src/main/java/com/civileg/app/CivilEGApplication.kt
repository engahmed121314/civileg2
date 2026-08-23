package com.civileg.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import dagger.hilt.android.HiltAndroidApp
import com.civileg.app.security.PlaySafetyChecker
import com.civileg.app.utils.LocaleHelper
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class CivilEGApplication : Application() {

    companion object {
        private const val TAG = "CivilEG"
        lateinit var instance: CivilEGApplication
            private set

        const val DEVELOPER_NAME = "Eng. Ahmed Magdy"
        const val DEVELOPER_EMAIL = "eng.ahmedmagdy121314@gmail.com"
        const val DEVELOPER_PHONE = "+201012628353"
        const val VERSION_NAME = "1.0.0"
        const val VERSION_CODE = 1
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize LocaleHelper with app context so enum displayName properties
        // can query the current language without needing a Context parameter.
        LocaleHelper.initApplicationContext(this)

        // AdMob is now initialized via AdsManager in SplashActivity for UMP consent compliance.

        // Initialize security checks
        initializeSecurity()

        // Register activity lifecycle callbacks for screen capture prevention
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                // Prevent screen capture on sensitive screens
                val sensitiveActivities = setOf(
                    "ColumnResultActivity", "BeamResultActivity", "SlabResultActivity",
                    "StairResultActivity", "TankResultActivity", "RetainingWallResultActivity"
                )
                if (sensitiveActivities.any { activity.localClassName.contains(it) }) {
                    activity.window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun initializeSecurity() {
        // Run integrity checks in background — fully wrapped in try-catch to prevent crashes
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val report = PlaySafetyChecker.getSecurityReport(this@CivilEGApplication)

                if (report.isRooted) {
                    Log.w(TAG, "Security Warning: Device appears to be rooted")
                }
                if (report.isEmulator) {
                    Log.i(TAG, "Running on emulator — debug mode only")
                }
                if (report.isHooked) {
                    Log.e(TAG, "Security Alert: Hooking framework detected!")
                }
                if (report.isDebuggerAttached) {
                    Log.w(TAG, "Security Warning: Debugger attached")
                }

                Log.d(TAG, "Device: ${report.deviceModel}, Android ${report.androidVersion}, Secure: ${report.isSecure}")
            } catch (e: Exception) {
                Log.w(TAG, "Security check skipped: ${e.message}")
            }
        }
    }
}
