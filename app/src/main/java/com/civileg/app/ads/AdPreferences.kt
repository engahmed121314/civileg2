package com.civileg.app.ads

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.adDataStore: DataStore<Preferences> by preferencesDataStore(name = "ad_preferences")

/**
 * Manages ad-related preferences:
 * - Premium status persistence
 * - Ad frequency preferences
 * - User consent
 *
 * Developer: Eng. Ahmed Magdy | eng.ahmedmagdy121314@gmail.com
 */
class AdPreferences(private val context: Context) {

    companion object {
        private val IS_PREMIUM = booleanPreferencesKey("is_premium_user")
        private val AD_CONSENT_SHOWN = booleanPreferencesKey("ad_consent_shown")
        private val INTERSTITIAL_COUNT = intPreferencesKey("interstitial_count")
        private val TOTAL_ADS_SHOWN = intPreferencesKey("total_ads_shown")
    }

    val isPremiumUser: Flow<Boolean> = context.adDataStore.data
        .map { it[IS_PREMIUM] ?: false }

    val hasShownConsent: Flow<Boolean> = context.adDataStore.data
        .map { it[AD_CONSENT_SHOWN] ?: false }

    suspend fun setPremium(isPremium: Boolean) {
        context.adDataStore.edit { it[IS_PREMIUM] = isPremium }
        AdManager.setPremiumUser(isPremium)
    }

    suspend fun markConsentShown() {
        context.adDataStore.edit { it[AD_CONSENT_SHOWN] = true }
    }

    suspend fun incrementInterstitialCount() {
        context.adDataStore.edit {
            it[INTERSTITIAL_COUNT] = (it[INTERSTITIAL_COUNT] ?: 0) + 1
            it[TOTAL_ADS_SHOWN] = (it[TOTAL_ADS_SHOWN] ?: 0) + 1
        }
    }

    val totalAdsShown: Flow<Int> = context.adDataStore.data
        .map { it[TOTAL_ADS_SHOWN] ?: 0 }
}