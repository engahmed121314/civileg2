package com.civileg.app.ui.splash

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
    // Wait for submitted content plus a short dwell. A commit callback is not
    // compositor proof, so real launch pixels remain the acceptance evidence.
    private var contentReadyAtMs: Long? = null
    private var dwellScheduled = false
    private val consentCompleted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val contentDrawObserved = java.util.concurrent.atomic.AtomicBoolean(false)
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private var postedGate: Runnable? = null
    private lateinit var contentRoot: View

    // Observation only (no behavior change): timestamps and the navigation
    // REASON, to establish from the next launch log whether the draw gate or
    // the failsafe navigated, and whether a frame commit preceded Main START.
    // Logging failures are swallowed and never affect navigation.
    private var frameCommitCallback: Runnable? = null
    private val frameCommitted = java.util.concurrent.atomic.AtomicBoolean(false)
    private var commitObserver: ViewTreeObserver? = null
    // Message construction happens INSIDE the guard (lazy lambda), so a
    // formatting/state-read failure can never escape into real paths.
    private inline fun navLog(msg: () -> String) {
        try {
            Log.i("SplashNav", "t=${SystemClock.elapsedRealtime()} thread=${Thread.currentThread().name} ${msg()}")
        } catch (_: Throwable) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navLog { "onCreate wallClockMs=${System.currentTimeMillis()} sdk=${Build.VERSION.SDK_INT}" }

        // Full-bleed Treatment B: artwork extends behind the system
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
                navLog { "first onDraw dispatch" }
                // Observational only: registered from the first onDraw dispatch;
                // it reports the commit of an unspecified current or subsequent
                // frame, NOT a guarantee about the first frame. Its lifetime
                // ends at onDestroy (not at navigation) so a commit after Main
                // START can still be seen. A callback that never ran means
                // unobserved, not "no frame".
                try {
                    val vto = contentRoot.viewTreeObserver
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        navLog { "frame-commit callback not registered: unsupported (sdk<29)" }
                    } else if (!contentRoot.isHardwareAccelerated) {
                        navLog { "frame-commit callback not registered: view not hardware accelerated" }
                    } else if (!vto.isAlive) {
                        navLog { "frame-commit callback not registered: observer dead" }
                    } else {
                        val commit = Runnable {
                            frameCommitted.set(true)
                            runOnUiThread {
                                contentReadyAtMs = SystemClock.elapsedRealtime()
                                navLog { "frame committed; visible dwell begins" }
                                maybeNavigateWhenReady()
                            }
                        }
                        vto.registerFrameCommitCallback(commit)
                        frameCommitCallback = commit
                        commitObserver = vto
                        navLog { "frame-commit callback registered (hw accelerated)" }
                    }
                } catch (t: Throwable) {
                    navLog { "frame-commit callback registration failed: ${t.javaClass.simpleName}" }
                }
                val gate = Runnable {
                    if (contentRoot.viewTreeObserver.isAlive) {
                        contentRoot.viewTreeObserver.removeOnDrawListener(this)
                    }
                    if (drawListener === this) drawListener = null
                    postedGate = null
                    navLog { "posted gate ran" }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !contentRoot.isHardwareAccelerated) {
                        // Old/software devices have no commit API. Use a following
                        // animation turn as an explicitly weaker fallback.
                        contentRoot.postOnAnimation {
                            contentReadyAtMs = SystemClock.elapsedRealtime()
                            maybeNavigateWhenReady()
                        }
                    }
                    maybeNavigateWhenReady()
                }
                postedGate = gate
                contentRoot.post(gate)
            }
        }
        drawListener = listener
        contentRoot.viewTreeObserver.addOnDrawListener(listener)

        // Bounded no-hang fallback remains, including failed/no-frame starts.
        // This is a 5-second maximum after content setup, not a claimed visible duration.
        lifecycleScope.launch {
            delay(5000)
            navLog { "failsafe fired" }
            navigateToMain("failsafe")
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
                navLog { "consent completed" }
                runOnUiThread {
                    consentCompleted.set(true)
                    maybeNavigateWhenReady()
                }
            }
        } catch (t: Throwable) {
            Log.w("SplashActivity", "Ads consent init skipped safely: ${t.message}")
            consentCompleted.set(true)
            maybeNavigateWhenReady()
        }
    }

    override fun onDestroy() {
        try {
            val cb = frameCommitCallback
            val vto = commitObserver
            if (cb != null && vto != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (vto.isAlive) {
                    vto.unregisterFrameCommitCallback(cb)
                    if (!frameCommitted.get()) navLog { "onDestroy: unregister attempted, frame-commit callback had not run (unobserved, not proof of no frame)" }
                } else {
                    navLog { "onDestroy: registered observer dead; callback not unregistered (observer discarded)" }
                }
            }
        } catch (_: Throwable) {}
        frameCommitCallback = null
        commitObserver = null
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

    private fun maybeNavigateWhenReady() {
        if (!consentCompleted.get() || isNavigated.get() || isFinishing || isDestroyed) return
        val readyAt = contentReadyAtMs ?: return
        val remaining = (600L - (SystemClock.elapsedRealtime() - readyAt)).coerceAtLeast(0L)
        if (remaining == 0L) {
            navigateToMain("committed-content-dwell")
        } else if (!dwellScheduled) {
            dwellScheduled = true
            lifecycleScope.launch {
                delay(remaining)
                dwellScheduled = false
                maybeNavigateWhenReady()
            }
        }
    }

    private fun navigateToMain(reason: String) {
        // ATTEMPT record (before the latch) so a losing late call is not
        // misattributed to the winner. Observation only.
        navLog { "navigate ATTEMPT reason=$reason alreadyNavigated=${isNavigated.get()} consent=${consentCompleted.get()} drawObserved=${contentDrawObserved.get()} finishing=$isFinishing destroyed=$isDestroyed commitSeen=${frameCommitted.get()}" }
        if (isNavigated.getAndSet(true)) return
        // A late consent callback or failsafe must not navigate a dead activity.
        if (isFinishing || isDestroyed) return
        postedGate?.let { contentRoot.removeCallbacks(it) }
        postedGate = null
        if (contentRoot.viewTreeObserver.isAlive) {
            drawListener?.let { contentRoot.viewTreeObserver.removeOnDrawListener(it) }
        }
        drawListener = null
        navLog { "navigate WINNER reason=$reason, starting Main" }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
