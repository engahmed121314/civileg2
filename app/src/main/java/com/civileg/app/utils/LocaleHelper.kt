package com.civileg.app.utils

import android.content.Context
import android.content.res.Configuration
import java.util.*

object LocaleHelper {
    private const val PREFS_NAME = "locale_prefs"
    private const val SELECTED_LANGUAGE = "app_language"

    fun setLocale(context: Context, language: String) {
        // Persist to SharedPreferences (synchronous, available immediately)
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SELECTED_LANGUAGE, "ar") ?: "ar"
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