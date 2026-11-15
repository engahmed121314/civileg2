package com.civileg.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.civileg.app.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single entry point for Google AdMob: UMP consent, SDK init, banner and interstitial.
 *
 * This replaces the two objects that used to do this in parallel
 * (`ads.AdManager` and `utils.AdsManager`). Both kept their own preloaded interstitial,
 * so every session fetched two interstitials and held two `InterstitialAd` instances.
 * There is one ad slot of each kind now, and one place where premium state is read.
 *
 * Ad philosophy (unchanged):
 * - Banner: only at natural content breaks, never at the top of a screen
 * - Native: blended into lists, every 8th item
 * - Interstitial: only on design → results, every 3rd completion
 * - Rewarded: user initiated only
 *
 * Every call is wrapped so a missing Play Services, a blocked network or a consent
 * failure can never block navigation or crash the app.
 *
 * Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
 */
object AdManager {

    private const val TAG = "AdManager"

    // ── Ad unit IDs ────────────────────────────────────────────────
    // Fed by BuildConfig from local.properties (`admob.*.id`), defaulting to Google's
    // well-known TEST IDs, which serve test ads only and earn no revenue.
    // See local.properties.template and play-store/release-checklist.md.
    val BANNER_AD_UNIT_ID: String get() = BuildConfig.BANNER_AD_UNIT_ID
    val INTERSTITIAL_AD_UNIT_ID: String get() = BuildConfig.INTERSTITIAL_AD_UNIT_ID
    val NATIVE_AD_UNIT_ID: String get() = BuildConfig.NATIVE_AD_UNIT_ID
    val REWARDED_AD_UNIT_ID: String get() = BuildConfig.REWARDED_AD_UNIT_ID

    /** Show an interstitial every Nth completed design, not on every one. */
    private const val INTERSTITIAL_FREQUENCY = 3
    private var designCompletionCount = 0

    // ── Premium state ──────────────────────────────────────────────
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    fun setPremiumUser(isPremium: Boolean) {
        _isPremiumUser.value = isPremium
    }

    @Composable
    fun isPremium(): Boolean = isPremiumUser.collectAsState().value

    // ── SDK / ad slots ─────────────────────────────────────────────
    private val isMobileAdsInitialized = AtomicBoolean(false)
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var consentInformation: ConsentInformation? = null

    /**
     * Requests UMP consent, then initializes the SDK if ads may be requested.
     * [onComplete] is invoked exactly once, whatever happens, so callers (the splash
     * screen) are never blocked on ads.
     */
    fun initConsent(activity: Activity, onComplete: () -> Unit) {
        val completed = AtomicBoolean(false)
        fun safeComplete() {
            if (completed.compareAndSet(false, true)) onComplete()
        }

        try {
            val availability =
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
            if (availability != ConnectionResult.SUCCESS) {
                Log.w(TAG, "Google Play Services unavailable ($availability), skipping UMP")
                safeComplete()
                return
            }

            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()

            val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
            consentInformation = consentInfo
            consentInfo.requestConsentInfoUpdate(
                activity,
                params,
                {
                    try {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                            if (error != null) Log.w(TAG, "Consent form error: ${error.message}")
                            try {
                                if (consentInfo.canRequestAds()) initializeMobileAds(activity)
                            } catch (t: Throwable) {
                                Log.w(TAG, "Init after consent failed: ${t.message}")
                            }
                            safeComplete()
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Consent form failed: ${t.message}")
                        safeComplete()
                    }
                },
                { requestError ->
                    Log.w(TAG, "Consent request error: ${requestError.message}")
                    try {
                        if (consentInfo.canRequestAds()) initializeMobileAds(activity)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Init after consent error failed: ${t.message}")
                    }
                    safeComplete()
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Consent init failed safely: ${t.message}")
            safeComplete()
        }
    }

    private fun initializeMobileAds(context: Context) {
        if (isMobileAdsInitialized.getAndSet(true)) return
        try {
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "MobileAds initialized: $status")
                loadInterstitialAd(context)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MobileAds.initialize failed safely: ${t.message}")
        }
    }

    /** Loads a banner into [container]; safe to call with no Play Services or no network. */
    fun loadBanner(container: FrameLayout) {
        if (_isPremiumUser.value) return
        try {
            val adView = AdView(container.context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
            }
            container.removeAllViews()
            container.addView(adView)
            adView.loadAd(buildAdRequest())
        } catch (t: Throwable) {
            Log.w(TAG, "loadBanner failed safely: ${t.message}")
        }
    }

    /** Preloads an interstitial. Call when a design starts, not while the results show. */
    fun loadInterstitialAd(context: Context) {
        if (_isPremiumUser.value) return
        if (isInterstitialLoading || interstitialAd != null) return

        isInterstitialLoading = true
        try {
            InterstitialAd.load(
                context,
                INTERSTITIAL_AD_UNIT_ID,
                buildAdRequest(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialLoading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.d(TAG, "Interstitial failed: ${error.message}")
                        interstitialAd = null
                        isInterstitialLoading = false
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "loadInterstitialAd failed safely: ${t.message}")
            interstitialAd = null
            isInterstitialLoading = false
        }
    }

    /**
     * Shows the interstitial when the frequency budget allows it. Always calls exactly one
     * of [onAdDismissed] / [onAdFailed], so navigation never stalls on an ad.
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

        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitialAd(activity)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Interstitial show failed: ${adError.message}")
                    interstitialAd = null
                    onAdFailed()
                }
            }
            ad.show(activity)
        } catch (t: Throwable) {
            Log.w(TAG, "showInterstitialAd failed safely: ${t.message}")
            interstitialAd = null
            onAdFailed()
        }
    }

    // ── Placement rules ───────────────────────────────────────────

    /** Native ads go every 8th list item, starting at index 7. */
    fun shouldShowNativeAdAt(position: Int): Boolean {
        if (_isPremiumUser.value) return false
        return position > 0 && position % 8 == 7
    }

    /** Banners only between major sections — after section 1 and 3, never first or last. */
    fun shouldShowBannerBetweenSections(sectionIndex: Int, totalSections: Int): Boolean {
        if (_isPremiumUser.value) return false
        return sectionIndex in setOf(1, 3) && sectionIndex < totalSections - 1
    }

    fun buildAdRequest(): AdRequest = AdRequest.Builder().build()

    /** Drops the preloaded interstitial (e.g. when the user buys the ad-free version). */
    fun destroy() {
        interstitialAd = null
        isInterstitialLoading = false
    }
}
