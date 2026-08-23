package com.civileg.app.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centrally manages Google AdMob advertisements and User Messaging Platform (UMP) consent.
 * Optimized for professional deployment on Google Play.
 */
object AdsManager {
    private const val TAG = "AdsManager"
    
    // Test Ad Unit IDs (Replace with your own production IDs in gradle.properties or secure store)
    private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private var interstitialAd: InterstitialAd? = null
    private var isMobileAdsInitializeCalled = AtomicBoolean(false)
    private lateinit var consentInformation: ConsentInformation

    /**
     * Initializes the User Messaging Platform (UMP) to handle GDPR and privacy consent.
     * This must be called at app startup before initializing Mobile Ads.
     */
    fun initConsent(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds(activity)
                    }
                    onComplete()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent request error: ${requestError.message}")
                if (consentInformation.canRequestAds()) {
                    initializeMobileAds(activity)
                }
                onComplete()
            }
        )
    }

    private fun initializeMobileAds(context: Context) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) return
        
        MobileAds.initialize(context) { status ->
            Log.d(TAG, "MobileAds initialized: $status")
            loadInterstitialAd(context)
        }
    }

    /**
     * Loads a banner ad into the provided container.
     */
    fun loadBanner(container: FrameLayout) {
        val adView = AdView(container.context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
        }
        container.removeAllViews()
        container.addView(adView)
        
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    /**
     * Loads an interstitial ad in the background.
     */
    fun loadInterstitialAd(context: Context) {
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
    }

    /**
     * Shows the interstitial ad if it's loaded.
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad was dismissed.")
                    interstitialAd = null
                    loadInterstitialAd(activity)
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Ad failed to show.")
                    interstitialAd = null
                    onDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad showed fullscreen content.")
                }
            }
            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "The interstitial ad wasn't ready yet.")
            onDismissed()
        }
    }
}
