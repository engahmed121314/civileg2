package com.civileg.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.civileg.app.domain.entities.DesignCode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var currency: String
        get() = prefs.getString(KEY_CURRENCY, "EGP") ?: "EGP"
        set(value) = prefs.edit().putString(KEY_CURRENCY, value).apply()

    var currencyRate: Double
        get() = prefs.getFloat(KEY_CURRENCY_RATE, 1.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_CURRENCY_RATE, value.toFloat()).apply()

    var unitSystem: String
        get() = prefs.getString(KEY_UNIT_SYSTEM, "SI") ?: "SI"
        set(value) = prefs.edit().putString(KEY_UNIT_SYSTEM, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ar") ?: "ar"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var defaultDesignCode: DesignCode
        get() = DesignCode.valueOf(prefs.getString(KEY_DESIGN_CODE, DesignCode.ECP.name) ?: DesignCode.ECP.name)
        set(value) = prefs.edit().putString(KEY_DESIGN_CODE, value.name).apply()

    var steelPrice: Double
        get() = prefs.getString(KEY_STEEL_PRICE, "45000")?.toDoubleOrNull() ?: 45000.0
        set(value) = prefs.edit().putString(KEY_STEEL_PRICE, value.toString()).apply()

    var concretePrice: Double
        get() = prefs.getString(KEY_CONCRETE_PRICE, "1500")?.toDoubleOrNull() ?: 1500.0
        set(value) = prefs.edit().putString(KEY_CONCRETE_PRICE, value.toString()).apply()

    var formworkPrice: Double
        get() = prefs.getString(KEY_FORMWORK_PRICE, "300")?.toDoubleOrNull() ?: 300.0
        set(value) = prefs.edit().putString(KEY_FORMWORK_PRICE, value.toString()).apply()

    var isDisclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, value).apply()

    companion object {
        private const val KEY_CURRENCY = "currency"
        private const val KEY_CURRENCY_RATE = "currency_rate"
        private const val KEY_UNIT_SYSTEM = "unit_system"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DESIGN_CODE = "design_code"
        private const val KEY_STEEL_PRICE = "steel_price"
        private const val KEY_CONCRETE_PRICE = "concrete_price"
        private const val KEY_FORMWORK_PRICE = "formwork_price"
        private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"
    }
}
