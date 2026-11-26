package com.civileg.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File
import java.util.Locale

/** Actual route capture and actual export, not an illustrative renderer fixture. */
class DrawingSampleUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun ar(@StringRes id: Int): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale("ar"))
        return context.createConfigurationContext(config).getString(id)
    }
    private fun scroll(text: String) {
        rule.waitUntil(20000) {
            runCatching { rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text)) }
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }
    private fun dismissKnownSystemAnr() {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return
        val launcher = root.findAccessibilityNodeInfosByText("Pixel Launcher isn't responding").isNotEmpty()
        val systemUi = root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty()
        if (launcher || systemUi) {
            // Keep System UI alive; never dismiss an ANR attributed to Civil EG.
            val action = if (systemUi) "Wait" else "Close app"
            root.findAccessibilityNodeInfosByText(action).firstOrNull()
                ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            android.os.SystemClock.sleep(1000)
        }
        val active = instrumentation.uiAutomation.rootInActiveWindow
        assertTrue("An ANR still obstructs the sample", active?.findAccessibilityNodeInfosByText("isn't responding").isNullOrEmpty())
    }
    @Before fun dismissPrompt() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText(ar(R.string.donation_decline)).fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithText(ar(R.string.donation_decline)).performClick()
    }
    @Test fun stairActualPageAndExportedPdf_sameCalculatedCase() {
        val out = File(context.filesDir, "drawing-samples/stair").apply { mkdirs() }
        val documents = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)!!
        documents.mkdirs()
        val existing = documents.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        rule.onNodeWithText(ar(R.string.nav_design_hub)).performClick()
        scroll(ar(R.string.home_stair))
        rule.onNodeWithText(ar(R.string.home_stair)).performClick()
        rule.waitForIdle()
        // Explicit values, independent of UI defaults.
        for ((id,value) in listOf("span" to "4.0", "riser" to "150", "tread" to "300",
            "deadLoad" to "5.0", "liveLoad" to "4.0", "fcu" to "25", "fy" to "360", "stairWidth" to "1200")) {
            rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag("field_$id"))
            rule.onNodeWithTag("field_$id").performTextReplacement(value)
        }
        rule.activity.runOnUiThread {
            rule.activity.currentFocus?.let { view ->
                (rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
        }
        rule.waitForIdle()
        scroll(ar(R.string.stair_design_now))
        rule.onNodeWithText(ar(R.string.stair_design_now)).performClick()
        scroll(ar(R.string.stair_drawing_title))
        rule.onNodeWithText(ar(R.string.stair_drawing_title)).assertIsDisplayed()
        dismissKnownSystemAnr()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            assertTrue("Combined drawing screenshot missing", bitmap != null)
            File(out,"design-page-all.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        // Focused elevation is readable on the actual phone-sized viewport.
        rule.onNodeWithText(ar(R.string.view_elevation)).performClick()
        rule.waitForIdle()
        dismissKnownSystemAnr()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            assertTrue("Actual Android screen screenshot missing", bitmap != null)
            File(out,"design-page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        scroll(ar(R.string.pdf_report))
        // The export is real; do not launch an unrelated emulator PDF viewer.
        val pdfViewer = instrumentation.addMonitor(android.content.IntentFilter(android.content.Intent.ACTION_VIEW).apply {
            addDataType("application/pdf")
        }, null, true)
        rule.onNodeWithText(ar(R.string.pdf_report)).performClick()
        fun completedPdf(): File? = documents.listFiles().orEmpty()
            .filter { it.extension == "pdf" && it.name !in existing && it.length() > 1000 }
            .sortedByDescending { it.lastModified() }.firstOrNull { file ->
                runCatching {
                    val bytes = file.readBytes()
                    check(bytes.takeLast(1024).toByteArray().toString(Charsets.ISO_8859_1).contains("%%EOF"))
                    android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use {
                        check(it.pageCount > 0)
                    }
                }.isSuccess
            }
        rule.waitUntil(90000) { completedPdf() != null }
        val pdf = checkNotNull(completedPdf())
        pdf.copyTo(File(out,"exported-report.pdf"),overwrite=true)
        instrumentation.removeMonitor(pdfViewer)
        File(out,"case.txt").writeText("route=stairs\npath=actual MainActivity > Design Hub > Stair > Design > PDF export\nspan_m=4\nriser_mm=150\ntread_mm=300\ndead_kN_m2=5\nlive_kN_m2=4\nfcu_MPa=25\nfy_MPa=360\nwidth_mm=1200\ncode=app initial selected code, visible in report\nstatus=diagnostic sample, calculation/detailing not accepted\nsource_pdf=${pdf.name}\ntimestamp_ms=${System.currentTimeMillis()}\n")
        assertTrue("Actual page file missing", File(out, "design-page.png").length() > 1000)
        assertTrue("Actual PDF file missing", File(out, "exported-report.pdf").length() > 1000)
    }
}
