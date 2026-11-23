package com.civileg.app.ui.splash

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.civileg.app.MainActivity
import com.civileg.app.R
import com.civileg.app.ads.AdManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private val isNavigated = java.util.concurrent.atomic.AtomicBoolean(false)
    // Presentation gate (diagnostic-driven, minimal): navigation now requires
    // BOTH the consent flow to be complete AND the content root's first draw
    // DISPATCH to have been observed. OnDrawListener is a draw-dispatch
    // notification (it fires as the view tree's draw dispatch runs; it is not
    // "after the root's traversal", and the posted turn that follows is still
    // not compositor presentation). No completed/presented frame is ever
    // claimed here - that remains verified by recording pixels. No fixed
    // dwell, no consent/ad/nav flow change beyond the gate.
    private val consentCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val contentDrawObserved = java.util.concurrent.atomic.AtomicBoolean(false)
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private var postedGate: Runnable? = null
    private lateinit var contentRoot: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-bleed approved Treatment B: artwork extends behind the system
        // bars. SplashOverlayLayout applies systemBars+displayCutout insets to
        // itself (never to the background) and anchors overlays to the artwork.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        // Light (white) system-bar icons for the dark-blue artwork, set before
        // the first content is shown. Actual launch pixels showed dark status
        // icons/clock on the dark artwork for the whole splash (low contrast);
        // the theme's windowLightStatusBar=false did not produce light icons
        // on the observed window, so appearance is set explicitly here. Scoped
        // to this activity's window only: MainActivity sets its own appearance
        // via its theme, so Main is not affected.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_splash)

        // One-shot observed-draw-dispatch gate. Listeners are never removed
        // and finish() never runs inside draw dispatch: both the removal and
        // the navigation decision are POSTED to the next UI turn. Removals
        // are guarded by observer liveness (a dead observer rejects them).
        contentRoot = findViewById(android.R.id.content)
        val listener = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (!contentDrawObserved.compareAndSet(false, true)) return
                val gate = Runnable {
                    if (contentRoot.viewTreeObserver.isAlive) {
                        contentRoot.viewTreeObserver.removeOnDrawListener(this)
                    }
                    if (drawListener === this) drawListener = null
                    postedGate = null
                    maybeNavigateWhenReady()
                }
                postedGate = gate
                contentRoot.post(gate)
            }
        }
        drawListener = listener
        contentRoot.viewTreeObserver.addOnDrawListener(listener)

        // Failsafe timeout: ensure user is never blocked on splash screen (unblock).
        // ORIGINAL semantics preserved: after 1500ms it navigates regardless,
        // so a never-drawn window can never wedge the app behind the starting
        // window; the gate only makes the NORMAL path wait for the first
        // observed draw. lifecycleScope cancels at onDestroy, so this never
        // fires into a destroyed activity.
        lifecycleScope.launch {
            delay(1500)
            navigateToMain()
        }

        try {
            // Requests UMP consent, then initializes AdMob if ads may be requested.
            // free flavor: onComplete runs inline on this (main) thread; play
            // flavor: UMP callback thread is not contractually main - hop so
            // the ready-check and any navigation always run on the UI thread.
            // ORIGINAL semantics preserved: consent still completes exactly
            // once and the catch below still bypasses the gate entirely.
            AdManager.initConsent(this) {
                Log.d("SplashActivity", "Ads consent process completed")
                runOnUiThread {
                    consentCompleted.set(true)
                    maybeNavigateWhenReady()
                }
            }
        } catch (t: Throwable) {
            Log.w("SplashActivity", "Ads consent init skipped safely: ${t.message}")
            navigateToMain()
        }
    }

    override fun onDestroy() {
        // Never leave the one-shot listener or the posted gate behind: a late
        // traversal or posted turn must not touch a dead activity.
        if (::contentRoot.isInitialized) {
            postedGate?.let { contentRoot.removeCallbacks(it) }
            if (contentRoot.viewTreeObserver.isAlive) {
                drawListener?.let { contentRoot.viewTreeObserver.removeOnDrawListener(it) }
            }
        }
        postedGate = null
        drawListener = null
        super.onDestroy()
    }

    // Navigates only when consent completed AND the content root's first draw
    // was observed. Either side may arrive first; the failsafe bypasses this.
    private fun maybeNavigateWhenReady() {
        if (consentCompleted.get() && contentDrawObserved.get()) navigateToMain()
    }

    private fun navigateToMain() {
        if (isNavigated.getAndSet(true)) return
        // A late consent callback or failsafe must not navigate a dead activity.
        if (isFinishing || isDestroyed) return
        postedGate?.let { contentRoot.removeCallbacks(it) }
        postedGate = null
        if (contentRoot.viewTreeObserver.isAlive) {
            drawListener?.let { contentRoot.viewTreeObserver.removeOnDrawListener(it) }
        }
        drawListener = null
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
