package com.civileg.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "civil_engineer_prefs")

/** User track selected during onboarding — switchable anytime from Settings (protocol §2.4). */
enum class UserType { NORMAL, ENGINEER }

/** Immutable snapshot consumed by MainActivity to gate the navigation graphs. */
data class AppBootstrap(
    val language: String,
    val userType: UserType,
    val onboardingComplete: Boolean
)

class PreferencesManager(private val context: Context) {
    
    companion object Keys {
        val CONCRETE_PRICE = doublePreferencesKey("concrete_price")
        val STEEL_PRICE = doublePreferencesKey("steel_price")
        val FORMWORK_PRICE = doublePreferencesKey("formwork_price")
        val CURRENCY = stringPreferencesKey("currency")
        val DEFAULT_CODE = stringPreferencesKey("default_design_code")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REPORT_LANGUAGE = stringPreferencesKey("report_language")
        val IS_PREMIUM = booleanPreferencesKey("is_premium_user")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val USER_TYPE = stringPreferencesKey("user_type")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    // ── Onboarding / bootstrap state (navigation-architecture.md §2) ──

    val appLanguage: Flow<String> = context.dataStore.data
        .map { it[APP_LANGUAGE] ?: "ar" }

    val userType: Flow<UserType> = context.dataStore.data
        .map { prefs ->
            when (prefs[USER_TYPE]?.uppercase()) {
                "NORMAL" -> UserType.NORMAL
                else -> UserType.ENGINEER   // default track for legacy installs
            }
        }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_COMPLETE] ?: false }

    /** Combined snapshot — null never emitted; consumers gate on their own loading flag. */
    val bootstrap: Flow<AppBootstrap> = combine(appLanguage, userType, onboardingComplete) { lang, type, done ->
        AppBootstrap(language = lang, userType = type, onboardingComplete = done)
    }

    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { it[APP_LANGUAGE] = language }
    }

    /** Completes the onboarding flow (called when user type is selected). */
    suspend fun setUserType(type: UserType) {
        context.dataStore.edit {
            it[USER_TYPE] = type.name
            it[ONBOARDING_COMPLETE] = true
        }
    }

    /** Switch track later from Settings without touching onboarding state. */
    suspend fun switchUserType(type: UserType) {
        context.dataStore.edit { it[USER_TYPE] = type.name }
    }

    /** Replay onboarding from Settings (master UX prompt §29). */
    suspend fun resetOnboarding() {
        context.dataStore.edit { it[ONBOARDING_COMPLETE] = false }
    }


    val isPremiumUser: Flow<Boolean> = context.dataStore.data
        .map { true }
    
    val concretePrice: Flow<Double> = context.dataStore.data
        .map { it[CONCRETE_PRICE] ?: 1200.0 }
    
    val steelPrice: Flow<Double> = context.dataStore.data
        .map { it[STEEL_PRICE] ?: 18000.0 }
    
    val formworkPrice: Flow<Double> = context.dataStore.data
        .map { it[FORMWORK_PRICE] ?: 150.0 }
    
    val currency: Flow<String> = context.dataStore.data
        .map { it[CURRENCY] ?: "EGP" }
    
    val defaultDesignCode: Flow<String> = context.dataStore.data
        .map { it[DEFAULT_CODE] ?: "ECP" }

    /** Typed, parse-safe design-code stream (ADR-003 single source). */
    val defaultDesignCodeEnum: Flow<com.civileg.core.calculations.entities.DesignCode> =
        defaultDesignCode.map { raw ->
            com.civileg.core.calculations.entities.DesignCode.entries.firstOrNull {
                it.name.equals(raw.trim(), ignoreCase = true)
            } ?: com.civileg.core.calculations.entities.DesignCode.ECP
        }
    
    val unitSystem: Flow<String> = context.dataStore.data
        .map { it[UNIT_SYSTEM] ?: "SI" }

    val themeMode: Flow<String> = context.dataStore.data
        .map { it[THEME_MODE] ?: "SYSTEM" }
    
    val reportLanguage: Flow<String> = context.dataStore.data
        .map { it[REPORT_LANGUAGE] ?: "ar" }
    
    suspend fun setConcretePrice(price: Double) {
        context.dataStore.edit { it[CONCRETE_PRICE] = price }
    }
    
    suspend fun setSteelPrice(price: Double) {
        context.dataStore.edit { it[STEEL_PRICE] = price }
    }
    
    suspend fun setFormworkPrice(price: Double) {
        context.dataStore.edit { it[FORMWORK_PRICE] = price }
    }
    
    suspend fun setCurrency(currency: String) {
        context.dataStore.edit { it[CURRENCY] = currency }
    }
    
    suspend fun setDefaultDesignCode(code: String) {
        context.dataStore.edit { it[DEFAULT_CODE] = code }
    }
    
    suspend fun setUnitSystem(system: String) {
        context.dataStore.edit { it[UNIT_SYSTEM] = system }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setReportLanguage(lang: String) {
        context.dataStore.edit { it[REPORT_LANGUAGE] = lang }
    }

    suspend fun setPremiumUser(isPremium: Boolean) {
        context.dataStore.edit { it[IS_PREMIUM] = isPremium }
    }
}
