package com.civileg.app.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.*

object LocaleHelper {
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"
    
    fun setLocale(context: Context, language: String): Context {
        persist(context, language)
        return updateResources(context, language)
    }
    
    fun getLocale(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
        return prefs.getString(SELECTED_LANGUAGE, "en") ?: "en"
    }
    
    private fun persist(context: Context, language: String) {
        val prefs: SharedPreferences = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(SELECTED_LANGUAGE, language).apply()
    }
    
    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        
        val config = context.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        
        return context.createConfigurationContext(config)
    }
}