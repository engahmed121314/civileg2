package com.civilengineer.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CivilEngineerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // التهيئة الأساسية للتطبيق
    }
}