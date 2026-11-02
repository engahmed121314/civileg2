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

    private val isNavigated = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Failsafe timeout: ensure user is never blocked on splash screen (unblock)
        lifecycleScope.launch {
            delay(1500)
            navigateToMain()
        }

        try {
            // Initialize Consent and Mobile Ads with safe fallback
            AdsManager.initConsent(this) {
                Log.d("SplashActivity", "Ads consent process completed")
                navigateToMain()
            }
        } catch (t: Throwable) {
            Log.w("SplashActivity", "Ads consent init skipped safely: ${t.message}")
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        if (isNavigated.getAndSet(true)) return
        lifecycleScope.launch {
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}
