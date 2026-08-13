package com.civileg.app.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized Ad Manager — Professional, Non-Intrusive Ad Placement.
 *
 * Ad Philosophy:
 * - Banner ads: Only at natural content breaks (between sections, NOT at top/bottom)
 * - Native ads: Blended within content lists (every 8-10 items)
 * - Interstitial: ONLY on screen transitions (design → results), NEVER on app launch
 * - Rewarded: User-initiated only (watch ad to unlock premium feature)
 * - No pop-ups, no auto-play video, no sound
 *
 * Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
 */
object AdManager {

    // ═══════════════════════════════════════════════════════════
    // Ad Unit IDs — Replace with your real AdMob IDs before release
    // ═══════════════════════════════════════════════════════════
    private const val TEST_DEVICE_ID = "YOUR_TEST_DEVICE_ID"

    // Real Ad Unit IDs (set these in AdMob console → get them from your AdMob account)
    var BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"       // Test banner
    var INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // Test interstitial
    var NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"       // Test native
    var REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"     // Test rewarded

    // ═══════════════════════════════════════════════════════════
    // Ad Frequency Control — Prevents ad fatigue
    // ═══════════════════════════════════════════════════════════
    private const val INTERSTITIAL_FREQUENCY = 3  // Show every 3rd design completion
    private var designCompletionCount = 0

    // ═══════════════════════════════════════════════════════════
    // Premium State
    // ═══════════════════════════════════════════════════════════
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    fun setPremiumUser(isPremium: Boolean) {
        _isPremiumUser.value = isPremium
    }

    @Composable
    fun isPremium(): Boolean = isPremiumUser.collectAsState().value

    // ═══════════════════════════════════════════════════════════
    // Interstitial Ad — Shown on design result screens only
    // ═══════════════════════════════════════════════════════════
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    /**
     * Load interstitial ad in background. Call this when user starts a design.
     */
    fun loadInterstitialAd(context: Context) {
        if (_isPremiumUser.value) return
        if (isInterstitialLoading) return
        if (interstitialAd != null) return

        isInterstitialLoading = true
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, buildAdRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                }
            }
        )
    }

    /**
     * Show interstitial ad. Call when navigating from design input → results.
     * Respects frequency control.
     */
    fun showInterstitialAd(
        activity: Activity,
        onAdDismissed: () -> Unit = {},
        onAdFailed: () -> Unit = {}
    ) {
        if (_isPremiumUser.value) {
            onAdDismissed()
            return
        }

        designCompletionCount++
        if (designCompletionCount % INTERSTITIAL_FREQUENCY != 0) {
            onAdDismissed()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            onAdFailed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                onAdDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                onAdFailed()
            }
        }
        ad.show(activity)
    }

    // ═══════════════════════════════════════════════════════════
    // Banner Ad Placement Rules
    // ═══════════════════════════════════════════════════════════

    /**
     * Should a native ad be shown at this position in a list?
     * Shows every 8th item, starting from position 7.
     */
    fun shouldShowNativeAdAt(position: Int): Boolean {
        if (_isPremiumUser.value) return false
        return position > 0 && position % 8 == 7
    }

    /**
     * Should a banner be shown between these sections?
     * Only between major sections, not at the very top.
     */
    fun shouldShowBannerBetweenSections(sectionIndex: Int, totalSections: Int): Boolean {
        if (_isPremiumUser.value) return false
        // Show banner after section 1 and 3 (not at start or end)
        return sectionIndex in setOf(1, 3) && sectionIndex < totalSections - 1
    }

    // ═══════════════════════════════════════════════════════════
    // Ad Request Builder
    // ═══════════════════════════════════════════════════════════
    fun buildAdRequest(): AdRequest {
        return AdRequest.Builder().build()
    }

    // ═══════════════════════════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════════════════════════
    fun destroy() {
        interstitialAd = null
        isInterstitialLoading = false
    }
}