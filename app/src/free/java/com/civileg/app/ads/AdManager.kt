package com.civileg.app.ads

import android.app.Activity
import android.util.Log

/**
 * `free` distribution flavor: the app with no ads at all.
 *
 * The real implementation lives in `src/play/java/com/civileg/app/ads/AdManager.kt` and
 * needs play-services-ads. This stand-in keeps the same call sites compiling without the
 * SDK, so the `free` variant ships without AdMob, UMP, the AD_ID permission or any
 * network calls to an ad server.
 *
 * Only the members that `main` sources actually call are declared — adding a new call to
 * a play-only member (e.g. `loadBanner`) fails compilation here, which is the signal that
 * the call site needs a `free` behaviour as well.
 */
object AdManager {

    private const val TAG = "AdManager"

    /**
     * No consent to collect and no SDK to initialize, so navigation continues immediately.
     * Still dispatched off the caller's stack to keep the same contract as the real one.
     */
    fun initConsent(activity: Activity, onComplete: () -> Unit) {
        Log.d(TAG, "free build: ads disabled, skipping consent")
        onComplete()
    }

    /** Kept so premium persistence (AdPreferences) compiles; a no-op without ads. */
    fun setPremiumUser(isPremium: Boolean) = Unit
}
