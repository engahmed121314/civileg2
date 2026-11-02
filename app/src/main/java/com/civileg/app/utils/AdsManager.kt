package com.civileg.app.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.civileg.app.BuildConfig
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centrally manages Google AdMob advertisements and User Messaging Platform (UMP) consent.
 * Optimized for professional deployment on Google Play and safe fallbacks on all devices (e.g. Honor).
 */
object AdsManager {
    private const val TAG = "AdsManager"
    
    // P4-3: ad-unit IDs come from BuildConfig, fed by local.properties
    // (`admob.banner.id` / `admob.interstitial.id`), defaulting to Google's well-known
    // TEST IDs that serve no real ads. See local.properties.template and
    // play-store/release-checklist.md for the production swap.
    private val BANNER_AD_UNIT_ID: String get() = BuildConfig.BANNER_AD_UNIT_ID
    private val INTERSTITIAL_AD_UNIT_ID: String get() = BuildConfig.INTERSTITIAL_AD_UNIT_ID

    private var interstitialAd: InterstitialAd? = null
    private var isMobileAdsInitializeCalled = AtomicBoolean(false)
    private var consentInformation: ConsentInformation? = null

    /**
     * Initializes the User Messaging Platform (UMP) to handle GDPR and privacy consent.
     * Wrapped with GoogleApiAvailability and try-catch to ensure devices without GMS
     * or network restrictions (e.g. Honor) never crash or block app navigation.
     */
    fun initConsent(activity: Activity, onComplete: () -> Unit) {
        val completed = AtomicBoolean(false)
        fun safeComplete() {
            if (completed.compareAndSet(false, true)) {
                onComplete()
            }
        }

        try {
            // Check Google Play Services availability first
            val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
            if (availability != ConnectionResult.SUCCESS) {
                Log.w(TAG, "Google Play Services not available or disabled ($availability), skipping UMP")
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
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                Log.w(TAG, "Consent form error: ${formError.message}")
                            }
                            try {
                                if (consentInfo.canRequestAds()) {
                                    initializeMobileAds(activity)
                                }
                            } catch (t: Throwable) {
                                Log.w(TAG, "Failed initializing MobileAds after consent: ${t.message}")
                            }
                            safeComplete()
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Error in loadAndShowConsentFormIfRequired: ${t.message}")
                        safeComplete()
                    }
                },
                { requestError ->
                    Log.w(TAG, "Consent request error: ${requestError.message}")
                    try {
                        if (consentInfo.canRequestAds()) {
                            initializeMobileAds(activity)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed initializing MobileAds after consent error: ${t.message}")
                    }
                    safeComplete()
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Consent init exception caught safely: ${t.message}")
            safeComplete()
        }
    }

    private fun initializeMobileAds(context: Context) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) return

        try {
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "MobileAds initialized: $status")
                try {
                    loadInterstitialAd(context)
                } catch (t: Throwable) {
                    Log.w(TAG, "Error preloading interstitial: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MobileAds.initialize caught safely: ${t.message}")
        }
    }

    /**
     * Loads a banner ad into the provided container safely.
     */
    fun loadBanner(container: FrameLayout) {
        try {
            val adView = AdView(container.context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_AD_UNIT_ID
            }
            container.removeAllViews()
            container.addView(adView)

            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
        } catch (t: Throwable) {
            Log.w(TAG, "loadBanner failed safely: ${t.message}")
        }
    }

    /**
     * Loads an interstitial ad in the background safely.
     */
    fun loadInterstitialAd(context: Context) {
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.d(TAG, adError.message)
                        interstitialAd = null
                    }

                    override fun onAdLoaded(ad: InterstitialAd) {
                        Log.d(TAG, "Ad was loaded.")
                        interstitialAd = ad
                    }
                })
        } catch (t: Throwable) {
            Log.w(TAG, "loadInterstitialAd failed safely: ${t.message}")
            interstitialAd = null
        }
    }

    /**
     * Shows the interstitial ad if it's loaded safely.
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        try {
            val ad = interstitialAd
            if (ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Ad was dismissed.")
                        interstitialAd = null
                        try {
                            loadInterstitialAd(activity)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Reloading ad failed: ${t.message}")
                        }
                        onDismissed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d(TAG, "Ad failed to show: ${adError.message}")
                        interstitialAd = null
                        onDismissed()
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Ad showed fullscreen content.")
                    }
                }
                ad.show(activity)
            } else {
                Log.d(TAG, "The interstitial ad wasn't ready yet.")
                onDismissed()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "showInterstitial failed safely: ${t.message}")
            interstitialAd = null
            onDismissed()
        }
    }
}
