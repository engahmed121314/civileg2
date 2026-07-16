package com.civileg.app.utils

import android.content.Context
import android.content.res.Configuration
import java.util.*

object LocaleHelper {
    private const val PREFS_NAME = "app_settings"
    private const val SELECTED_LANGUAGE = "language"

    fun setLocale(context: Context, language: String) {
        // Persist to SharedPreferences (same store as SettingsManager)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SELECTED_LANGUAGE, language).apply()

        // Apply locale to the activity's resources directly
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun getLocale(context: Context): String {
        // Read from unified app_settings (same as SettingsManager)
        // Also check legacy locale_prefs for migration
        val unifiedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unifiedLang = unifiedPrefs.getString(SELECTED_LANGUAGE, null)
        if (unifiedLang != null) return unifiedLang

        // Migrate from old locale_prefs if exists
        val legacyPrefs = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
        val legacyLang = legacyPrefs.getString("app_language", "ar") ?: "ar"
        // Migrate to unified store
        unifiedPrefs.edit().putString(SELECTED_LANGUAGE, legacyLang).apply()
        return legacyLang
    }

    /**
     * Wrap a base Context with the correct locale configuration.
     * Use in Activity.attachBaseContext() for proper per-activity locale.
     */
    fun wrapContext(base: Context): Context {
        val lang = getLocale(base)
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return base.createConfigurationContext(config)
    }

    /**
     * Apply saved locale to an Activity's resources.
     * Call this in onCreate() BEFORE setContent().
     */
    fun applySavedLocale(context: Context) {
        val lang = getLocale(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}