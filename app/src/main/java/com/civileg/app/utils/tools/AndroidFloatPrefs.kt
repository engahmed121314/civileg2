package com.civileg.app.utils.tools

import android.content.Context

class AndroidFloatPrefs(context: Context) : FloatPrefs {
    private val sp = context.applicationContext.getSharedPreferences("toolbox_calibration", Context.MODE_PRIVATE)
    override fun getFloat(key: String): Float? = if (sp.contains(key)) sp.getFloat(key, 0f) else null
    override fun putFloat(key: String, value: Float) { sp.edit().putFloat(key, value).apply() }
    override fun remove(key: String) { sp.edit().remove(key).apply() }
}
