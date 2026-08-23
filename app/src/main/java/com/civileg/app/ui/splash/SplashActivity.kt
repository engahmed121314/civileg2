package com.civileg.app.ui.splash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.civileg.app.MainActivity
import com.civileg.app.R
import com.civileg.app.utils.AdsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // Initialize Consent and Mobile Ads
        AdsManager.initConsent(this) {
            Log.d("SplashActivity", "Ads consent process completed")
            // Proceed to MainActivity after consent is handled
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        lifecycleScope.launch {
            // Ensure minimum splash time for branding
            delay(1000) 
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}
