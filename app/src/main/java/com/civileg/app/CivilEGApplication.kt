package com.civileg.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CivilEGApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide configurations
    }
}