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
        val CURRENCY = stringPreferencesKey("currency")
        val DEFAULT_CODE = stringPreferencesKey("default_design_code")
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REPORT_LANGUAGE = stringPreferencesKey("report_language")
    }
    
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
}
