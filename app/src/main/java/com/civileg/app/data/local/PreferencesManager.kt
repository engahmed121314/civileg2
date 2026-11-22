package com.civileg.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "civil_engineer_prefs")

class PreferencesManager(private val context: Context) {
    
    companion object Keys {
        val CONCRETE_PRICE = doublePreferencesKey("concrete_price")
        val STEEL_PRICE = doublePreferencesKey("steel_price")
        val FORMWORK_PRICE = doublePreferencesKey("formwork_price")
        // Currency in force when the user saved each price. Absent for prices saved before this was recorded.
        val CONCRETE_PRICE_CCY = stringPreferencesKey("concrete_price_currency")
        val STEEL_PRICE_CCY = stringPreferencesKey("steel_price_currency")
        val FORMWORK_PRICE_CCY = stringPreferencesKey("formwork_price_currency")
        val CURRENCY = stringPreferencesKey("currency")
        val DEFAULT_CODE = stringPreferencesKey("default_design_code")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REPORT_LANGUAGE = stringPreferencesKey("report_language")
        val IS_PREMIUM = booleanPreferencesKey("is_premium_user")
        val PROJECT_NAME = stringPreferencesKey("project_name")
        val ENGINEER_NAME = stringPreferencesKey("engineer_name")
        val COMPANY_NAME = stringPreferencesKey("company_name")
    }

    val isPremiumUser: Flow<Boolean> = context.dataStore.data
        .map { it[IS_PREMIUM] ?: false }
    
    val concretePrice: Flow<Double> = context.dataStore.data
        .map { it[CONCRETE_PRICE] ?: 1200.0 }
    
    val steelPrice: Flow<Double> = context.dataStore.data
        .map { it[STEEL_PRICE] ?: 18000.0 }
    
    val formworkPrice: Flow<Double> = context.dataStore.data
        .map { it[FORMWORK_PRICE] ?: 150.0 }
    
    /** What the user actually saved. A price that was never entered is null, never a default. */
    val savedPrices: Flow<com.civileg.app.domain.entities.SavedMaterialPrices> = context.dataStore.data.map { p ->
        fun saved(k: Preferences.Key<Double>, c: Preferences.Key<String>) =
            p[k]?.let { com.civileg.app.domain.entities.SavedPrice(it, p[c]) }
        com.civileg.app.domain.entities.SavedMaterialPrices(
            concrete = saved(CONCRETE_PRICE, CONCRETE_PRICE_CCY),
            steel = saved(STEEL_PRICE, STEEL_PRICE_CCY),
            formwork = saved(FORMWORK_PRICE, FORMWORK_PRICE_CCY)
        )
    }

    val currency: Flow<String> = context.dataStore.data
        .map { it[CURRENCY] ?: "EGP" }
    
    val defaultDesignCode: Flow<String> = context.dataStore.data
        .map { it[DEFAULT_CODE] ?: "ECP" }
    
    val unitSystem: Flow<String> = context.dataStore.data
        .map { it[UNIT_SYSTEM] ?: "SI" }

    val themeMode: Flow<String> = context.dataStore.data
        .map { it[THEME_MODE] ?: "SYSTEM" }
    
    val reportLanguage: Flow<String> = context.dataStore.data
        .map { it[REPORT_LANGUAGE] ?: "ar" }
    
    val projectName: Flow<String> = context.dataStore.data
        .map { it[PROJECT_NAME] ?: "Civil EG Project" }
    
    val engineerName: Flow<String> = context.dataStore.data
        .map { it[ENGINEER_NAME] ?: "Structural Engineer" }
    
    val companyName: Flow<String> = context.dataStore.data
        .map { it[COMPANY_NAME] ?: "Civil EG" }
    
    suspend fun setConcretePrice(price: Double) {
        if (!com.civileg.app.domain.entities.PriceValidation.isValid(price)) return
        context.dataStore.edit { it[CONCRETE_PRICE] = price; it[CONCRETE_PRICE_CCY] = it[CURRENCY] ?: "EGP" }
    }
    
    suspend fun setSteelPrice(price: Double) {
        if (!com.civileg.app.domain.entities.PriceValidation.isValid(price)) return
        context.dataStore.edit { it[STEEL_PRICE] = price; it[STEEL_PRICE_CCY] = it[CURRENCY] ?: "EGP" }
    }
    
    suspend fun setFormworkPrice(price: Double) {
        if (!com.civileg.app.domain.entities.PriceValidation.isValid(price)) return
        context.dataStore.edit { it[FORMWORK_PRICE] = price; it[FORMWORK_PRICE_CCY] = it[CURRENCY] ?: "EGP" }
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

    suspend fun setProjectName(name: String) {
        context.dataStore.edit { it[PROJECT_NAME] = name }
    }

    suspend fun setEngineerName(name: String) {
        context.dataStore.edit { it[ENGINEER_NAME] = name }
    }

    suspend fun setCompanyName(name: String) {
        context.dataStore.edit { it[COMPANY_NAME] = name }
    }

    suspend fun setPremiumUser(isPremium: Boolean) {
        context.dataStore.edit { it[IS_PREMIUM] = isPremium }
    }
}
