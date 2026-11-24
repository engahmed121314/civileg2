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
    @Before fun dismissPrompt() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText(ar(R.string.donation_decline)).fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithText(ar(R.string.donation_decline)).performClick()
    }
    @Test fun stairActualPageAndExportedPdf_sameCalculatedCase() {
        val out = File(context.getExternalFilesDir(null), "drawing-samples/stair").apply { mkdirs() }
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
        scroll(ar(R.string.stair_design_now))
        rule.onNodeWithText(ar(R.string.stair_design_now)).performClick()
        scroll(ar(R.string.stair_drawing_title))
        rule.onNodeWithText(ar(R.string.stair_drawing_title)).assertIsDisplayed()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            assertTrue("Actual Android screen screenshot missing", bitmap != null)
            File(out,"design-page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        scroll(ar(R.string.pdf_report))
        rule.onNodeWithText(ar(R.string.pdf_report)).performClick()
        rule.waitUntil(60000) {
            documents.listFiles()?.any { it.extension == "pdf" && it.name !in existing && it.length()>1000 } == true
        }
        val pdf = documents.listFiles()!!.filter { it.extension=="pdf" && it.name !in existing }.maxBy { it.lastModified() }
        pdf.copyTo(File(out,"exported-report.pdf"),overwrite=true)
        File(out,"case.txt").writeText("route=stairs\npath=actual MainActivity > Design Hub > Stair > Design > PDF export\nspan_m=4\nriser_mm=150\ntread_mm=300\ndead_kN_m2=5\nlive_kN_m2=4\nfcu_MPa=25\nfy_MPa=360\nwidth_mm=1200\ncode=app initial selected code, visible in report\nstatus=diagnostic sample, calculation/detailing not accepted\nsource_pdf=${pdf.name}\ntimestamp_ms=${System.currentTimeMillis()}\n")
        // Stream bytes from the app process. Android shell cannot read app-owned
        // external storage reliably under scoped storage.
        for (file in out.listFiles().orEmpty()) {
            val destination = "/data/local/tmp/civileg-drawing-samples/stair/${file.name}"
            val pipes = instrumentation.uiAutomation.executeShellCommandRw(
                "mkdir -p /data/local/tmp/civileg-drawing-samples/stair && cat > $destination && echo TRANSFER_OK")
            android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { sink ->
                file.inputStream().use { it.copyTo(sink) }
            }
            val response = android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { it.readBytes().toString(Charsets.UTF_8) }
            assertTrue("Sample transfer failed: ${file.name}", response.contains("TRANSFER_OK"))
        }
    }
}
