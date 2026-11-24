package com.civileg.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File
import java.util.Locale

/** Real MainActivity routes and emulator pixels. Physical calibration is not verified. */
class ToolboxSampleUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun ar(@StringRes id: Int): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale("ar"))
        return context.createConfigurationContext(config).getString(id)
    }
    private fun click(@StringRes id: Int) {
        val text = ar(id)
        rule.waitUntil(20000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(text)[0].performClick()
        rule.waitForIdle()
    }
    private fun rotate(landscape: Boolean) {
        rule.activity.runOnUiThread {
            rule.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val expected = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        rule.waitUntil(20000) { rule.activity.resources.configuration.orientation == expected }
        rule.waitForIdle()
    }
    @Test fun toolboxActualRoutes_portraitAndLandscape() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText(ar(R.string.donation_decline)).fetchSemanticsNodes().isNotEmpty()) click(R.string.donation_decline)
        val out = File(context.getExternalFilesDir(null), "toolbox-samples").apply { mkdirs() }
        click(R.string.nav_tools)
        for ((id, name) in listOf(R.string.home_calculator to "calculator", R.string.home_unit_converter to "converter", R.string.tool_spirit_level to "level", R.string.tool_ruler to "ruler")) {
            click(id)
            for (landscape in listOf(false, true)) {
                rotate(landscape)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                assertTrue("Screenshot unavailable", bitmap != null)
                File(out, "$name-${if (landscape) "landscape" else "portrait"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            rotate(false)
            rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
        }
        File(out, "scope.txt").writeText("Actual MainActivity > Tools hub > four tool routes, portrait and landscape. Emulator visual evidence only. Physical ruler accuracy and real device sensor calibration are NOT verified. Scientific evaluator and conversion arithmetic have separate JVM tests.\n")
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/civileg-toolbox-samples && cp -R ${out.absolutePath}/. /sdcard/Download/civileg-toolbox-samples/").use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
    }
}
