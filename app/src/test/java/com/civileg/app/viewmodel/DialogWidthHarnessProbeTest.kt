package com.civileg.app.viewmodel

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/**
 * Bounded harness-control probe for the Save-dialog AppNotIdle failures.
 *
 * Independent lead (not an established cause): robolectric#8460 reports
 * Dialog+TextField freezing Compose idleness under width qualifiers above
 * ~w320dp on Robolectric 4.10.3-4.16.1 (ours: 4.14.1, SDK 34, NATIVE
 * graphics). That remains a HYPOTHESIS for our failures, not a universal
 * cause; this probe only measures whether our exact harness combination
 * reproduces the width dependence.
 *
 * This probe adds NO product changes and touches NO existing test. Three
 * minimal same-environment fixtures isolate the harness interaction, now
 * under deterministic manual clock control (autoAdvance=false + frame pumps):
 *  1. w400dp Dialog+OutlinedTextField  -> froze under the auto-advancing
 *     clock; if it STILL fails under 4 manual frame pumps, only THIS
 *     frame-delivery strategy is falsified - no categorical cause is claimed
 *     and the recorded bounds remain the evidence
 *  2. w320dp Dialog+OutlinedTextField  -> expected to pass (below threshold)
 *  3. w400dp Dialog with plain Text    -> expected to pass (no TextField)
 *
 * The host is an explicit Android compose rule over ComponentActivity
 * (manifest-supported), NOT createComposeRule (which exposes no activity).
 * Actual host window and laid-out dialog-content bounds are recorded in a
 * finally block, so diagnostics survive even when setContent/waitForIdle
 * fails - the failure itself is the signal under test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogWidthHarnessProbeTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Records ACTUAL host and dialog bounds. Every read is PASSIVE (no
     * synchronizing Compose fetches: ShadowDialog.getLatestDialog() only
     * returns the latest shown dialog, never idles the composer) and
     * independently wrapped, and the ENTIRE diagnostic including file
     * creation/writing is caught and reported, so a diagnostics failure can
     * never replace the original test failure. Safe after a failed
     * setup/idling: never rethrows.
     */
    private fun logActualDimensions(label: String) {
        runCatching {
            val out = StringBuilder()
            out.appendLine("PROBE $label")
            runCatching {
                val decor = compose.activity.window.decorView
                out.appendLine("host decorView=${decor.width}x${decor.height}px attached=${decor.isAttachedToWindow}")
            }.onFailure { out.appendLine("host decorView unreadable: ${it.javaClass.simpleName}: ${it.message}") }
            runCatching {
                val metrics = compose.activity.resources.displayMetrics
                out.appendLine(
                    "host metrics=${metrics.widthPixels}x${metrics.heightPixels}px " +
                        "densityDpi=${metrics.densityDpi} widthDp=${"%.1f".format(metrics.widthPixels / metrics.density)}"
                )
            }.onFailure { out.appendLine("host metrics unreadable: ${it.javaClass.simpleName}: ${it.message}") }
            runCatching {
                val dialog = ShadowDialog.getLatestDialog()
                if (dialog == null) {
                    out.appendLine("dialog: none shown")
                } else {
                    val dw = dialog.window?.decorView
                    out.appendLine(
                        "dialog showing=${dialog.isShowing} decorView=" +
                            (dw?.let { "${it.width}x${it.height}px attached=${it.isAttachedToWindow}" } ?: "none")
                    )
                }
            }.onFailure { out.appendLine("dialog bounds unreadable: ${it.javaClass.simpleName}: ${it.message}") }
            val text = out.toString()
            runCatching {
                File("build/reports/tests/dialog-probe").apply { mkdirs() }
                    .resolve("$label.txt").writeText(text)
            }.onFailure { println("PROBE $label: evidence file write failed: ${it.javaClass.simpleName}: ${it.message}") }
            println(text)
        }.onFailure {
            println("PROBE $label: diagnostics failed entirely: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun setDialogContent(withTextField: Boolean) {
        // Deterministic, TEST-ONLY frame delivery: manual compose clock set
        // BEFORE setContent, then frames pumped manually. If a fixture still
        // fails after this, only this strategy is falsified - the recorded
        // bounds in the finally block remain the evidence either way.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("probe") },
                text = {
                    if (withTextField) {
                        OutlinedTextField(
                            value = "x",
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth().testTag("probe-content")
                        )
                    } else {
                        Text("plain dialog body", modifier = Modifier.testTag("probe-content"))
                    }
                }
            )
        }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h800dp")
    fun `probe w400 dialog with outlined text field`() {
        try {
            setDialogContent(withTextField = true)
            compose.waitForIdle()
        } finally {
            logActualDimensions("w400-textfield")
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h800dp")
    fun `probe w320 dialog with outlined text field`() {
        try {
            setDialogContent(withTextField = true)
            compose.waitForIdle()
        } finally {
            logActualDimensions("w320-textfield")
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h800dp")
    fun `probe w400 dialog with plain text`() {
        try {
            setDialogContent(withTextField = false)
            compose.waitForIdle()
        } finally {
            logActualDimensions("w400-plaintext")
        }
    }
}
