package com.civileg.app.ui

import android.content.Context
import com.civileg.app.domain.entities.DesignCode

/**
 * Single source of truth for the user's "Default Design Code" setting
 * (Settings > Default Design Code).
 *
 * The Settings screen persists the choice through [com.civileg.app.data.repository.ProjectRepositoryImpl]
 * (DataStore) **and** through this synchronous SharedPreferences store so every design
 * screen can read the current default synchronously inside `remember { }` blocks
 * (DataStore is Flow-based and awkward to read at composition init time).
 *
 * SharedPreferences name and key intentionally match [com.civileg.app.utils.SettingsManager]
 * ("app_settings" / "design_code") so the legacy synchronous readers (e.g. ColumnViewModel)
 * observe the same value.
 *
 * [init] is called from `CivilEGApplication.onCreate()`; the cache is additionally
 * refreshed on every `set()` (Settings) and whenever SettingsViewModel syncs from DataStore.
 */
object DesignCodeStore {
    const val PREFS_NAME = "app_settings"
    const val KEY_DESIGN_CODE = "design_code"

    @Volatile
    private var cached: DesignCode? = null

    /** Load the persisted value into the in-memory cache. Call once at app start. */
    fun init(context: Context) {
        load(context)
    }

    /** Current default, using the process cache; falls back to ECP when uninitialized. */
    fun current(): DesignCode = cached ?: DesignCode.ECP

    fun load(context: Context): DesignCode {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DESIGN_CODE, DesignCode.ECP.name)
        val code = runCatching { DesignCode.valueOf(raw ?: DesignCode.ECP.name) }
            .getOrDefault(DesignCode.ECP)
        cached = code
        return code
    }

    fun set(context: Context, code: DesignCode) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DESIGN_CODE, code.name)
            .apply()
        cached = code
    }
}

/**
 * Synchronous read of the user's saved default design code (ECP / ACI / SBC).
 * Screens seed their per-session code picker with this value; the user can still
 * override it per design session. Safe to call inside `remember { }` blocks.
 */
fun defaultDesignCode(): DesignCode = DesignCodeStore.current()
