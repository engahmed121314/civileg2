package com.civileg.app.qa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.civileg.app.db.DesignRepository
import com.civileg.app.db.Project
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.compose.screens.ColumnScreen
import com.civileg.app.utils.SettingsManager
import com.civileg.app.viewmodel.ColumnDrawingCapture
import com.civileg.app.viewmodel.ColumnViewModel
import com.civileg.app.viewmodel.ProjectViewModel
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import io.mockk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real-button capture case on a real emulator, ported 1:1 from the
 * Robolectric integration test (fixture, input, real screen, real recorder,
 * real ExportManager, same assertions). What this observes: exactly one
 * non-null capture reaching the real export call with the exact completed
 * revision, the export finishing (observed !isExporting plus a new report
 * file, the same completion signal as the Robolectric route), and the exported report's
 * embedded image being pixel-identical to the observed frozen bitmap.
 * Retained evidence: exported PDF, observed-capture PNG, PDF image PNG,
 * soft-mask PNG when present, and a screenshot of the visible drawing.
 */
@RunWith(AndroidJUnit4::class)
class ColumnRecordedSheetEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var host: View
    private lateinit var vm: ColumnViewModel
    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val evidenceDir get() = File(
        requireNotNull(targetContext.getExternalFilesDir(null)),
        "capture-emulator-evidence"
    ).apply { mkdirs() }

    private fun open() {
        val settings = mockk<SettingsManager>(relaxed = true)
        every { settings.defaultDesignCode } returns DesignCode.ECP
        vm = spyk(ColumnViewModel(mockk<DesignRepository>(relaxed = true), CalculatorEngine(settings), settings))
        val projects = mockk<ProjectViewModel>()
        every { projects.allProjects } returns MutableLiveData<List<Project>>(emptyList())
        compose.setContent {
            host = LocalView.current
            MaterialTheme { ColumnScreen(vm, projects) }
        }
        compose.runOnIdle { vm.calculateManual() }
        compose.waitForIdle()
        val recorded = requireNotNull(vm.uiState.value.completedDesign).result()
        // Same recorded fixture values as the unit-route case.
        assertEquals(9, recorded.reinforcement.numBars)
        assertEquals(20, recorded.reinforcement.diameter)
    }

    private fun button(tag: String) = compose.onNodeWithTag(tag)
        .onChildren().filterToOne(hasClickAction())
    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("column-content").performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }
    private fun documents() = requireNotNull(targetContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS))
    private fun newReport(before: Set<String>): File? = documents().listFiles()?.firstOrNull {
        it.name.startsWith("Column_Report_") && it.extension == "pdf" && it.name !in before && it.length() > 0
    }
    private fun retainBitmap(name: String, bitmap: Bitmap) {
        File(evidenceDir, name).outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    // Gradle uninstalls the app and the test app the moment instrumentation
    // finishes, wiping the app-private evidence dir before the workflow can
    // pull it, and API 33 scoped storage blocks the shell from app-private
    // external dirs anyway. So everything retained is ALSO mirrored to the
    // shared Downloads collection (app-owned files via the File API survive
    // uninstall and stay pullable). Best-effort: a mirror failure is not a
    // test failure and never masks the original outcome.
    private fun mirrorEvidenceToShared() {
        runCatching {
            val shared = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "capture-emulator-evidence"
            )
            shared.mkdirs()
            evidenceDir.listFiles()?.forEach { f ->
                f.copyTo(File(shared, f.name), overwrite = true)
            }
        }
    }

    @Test
    fun actual_pdf_button_embeds_the_completed_capture_through_real_ExportManager() {
        open()
        val observedCaptures = mutableListOf<ColumnDrawingCapture?>()
        every { vm.exportToPdf(any(), any(), captureNullable(observedCaptures), any()) } answers { callOriginal() }
        val before = documents().listFiles().orEmpty().map { it.name }.toSet()
        scrollTo("column-pdf-export")
        // Pre-click screenshot taken at a known-good idle point: the failure
        // path below never touches Compose (no idling, no scrollTo), so this
        // is the guaranteed visual. If even this captureToImage fails, the
        // screenshot is simply unavailable - that is reported, not hidden.
        var preClickScreenshotSaved = false
        runCatching {
            scrollTo("column-visible-drawing")
            retainBitmap("pre-click-visible-drawing.png",
                compose.onNodeWithTag("column-visible-drawing").captureToImage().asAndroidBitmap())
            preClickScreenshotSaved = true
            scrollTo("column-pdf-export")
        }
        val revisionBeforeClick = vm.uiState.value.completedDesign?.revisionSha256
        try {
            button("column-pdf-export").performClick()
            compose.waitUntil(30_000) { !vm.uiState.value.isExporting && newReport(before) != null }
            runCatching {
                System.err.println(
                    "CAPTURE-WITNESS actual-button: revisionBeforeClick=" + revisionBeforeClick +
                        " revisionAfterWait=" + vm.uiState.value.completedDesign?.revisionSha256 +
                        " captures=" + observedCaptures.size +
                        " isExporting=" + vm.uiState.value.isExporting
                )
            }
            assertEquals("The real UI must supply one completed capture", 1, observedCaptures.size)
            val observed = requireNotNull(observedCaptures.single()) { "Real UI did not capture its completed sheet" }
            assertEquals(requireNotNull(vm.uiState.value.completedDesign).revisionSha256, observed.revisionSha256)
            val frozen = observed.bitmap
            try {
                inspectReport(requireNotNull(newReport(before)), "Captured completed revision schematic", "actual-captured-full-report.pdf", frozen)
            } finally { frozen.recycle() }
        } catch (failure: Throwable) {
            // Whole-body guarded retention: no Compose calls here, only files
            // and bitmaps already in hand. Original throwable rethrown.
            runCatching {
                File(evidenceDir, "actual-button-failure-status.txt").writeText(
                    "isExporting=" + runCatching { vm.uiState.value.isExporting }.getOrNull() +
                        " captureCalls=" + observedCaptures.size +
                        " revisionBeforeClick=" + revisionBeforeClick +
                        " revisionOnFailure=" + runCatching { vm.uiState.value.completedDesign?.revisionSha256 }.getOrNull() +
                        " preClickScreenshotSaved=" + preClickScreenshotSaved +
                        " failure=" + failure.javaClass.name + ": " + failure.message
                )
            }
            runCatching { newReport(before)?.copyTo(File(evidenceDir, "failure-report-snapshot.pdf"), overwrite = true) }
            runCatching {
                observedCaptures.singleOrNull()?.bitmap?.let { retainBitmap("failure-observed-capture.png", it) }
            }
            if (!preClickScreenshotSaved) {
                runCatching {
                    File(evidenceDir, "screenshot-unavailable.txt").writeText(
                        "Pre-click screenshot could not be captured; no screenshot evidence exists for this run.")
                }
            }
            throw failure
        } finally {
            mirrorEvidenceToShared()
        }
    }

    private fun inspectReport(file: File, expectedDrawingStatus: String, artifactName: String,
                              expectedBitmap: Bitmap? = null) {
        // Preserve the actual PDF and source capture even when an assertion below fails.
        file.copyTo(File(evidenceDir, artifactName), overwrite = true)
        val artifactStem = artifactName.substringBeforeLast('.')
        expectedBitmap?.let { retainBitmap("$artifactStem-observed-capture.png", it) }
        assertTrue(vm.uiState.value.errors.toString(), vm.uiState.value.errors.isEmpty())
        val revision = requireNotNull(vm.uiState.value.completedDesign).revisionSha256
        PdfDocument(PdfReader(file)).use { pdf ->
            assertTrue(pdf.numberOfPages > 0)
            val text = (1..pdf.numberOfPages).joinToString(" ") {
                PdfTextExtractor.getTextFromPage(pdf.getPage(it))
            }.replace(Regex("\\s+"), " ")
            assertTrue(text, text.contains(expectedDrawingStatus))
            assertTrue(text, text.contains("Detailing Status"))
            assertTrue(text, text.contains("Quantity Basis"))
            assertTrue(text, text.filterNot { it.isWhitespace() }.contains(revision))
            assertFalse(text, text.contains("Design Complies with Code"))
            assertFalse(text, text.contains("Applicable design basis"))
            assertFalse(text, text.contains("Engineering Detail"))
            assertTrue(text, text.contains("REVIEW HOLD"))
            assertTrue(text, text.contains("Recorded BBS Estimates (UNVERIFIED)", ignoreCase = true))
            assertTrue(text, text.contains("Recorded BOQ Estimates (UNVERIFIED)", ignoreCase = true))
            val renderedImages = mutableListOf<PdfImageXObject>()
            val listener = object : IEventListener {
                override fun getSupportedEvents(): Set<EventType> = setOf(EventType.RENDER_IMAGE)
                override fun eventOccurred(data: IEventData, type: EventType) {
                    if (type == EventType.RENDER_IMAGE) renderedImages += (data as ImageRenderInfo).image
                }
            }
            for (page in 1..pdf.numberOfPages) PdfCanvasProcessor(listener).processPageContent(pdf.getPage(page))
            if (expectedBitmap == null) {
                assertTrue("Null/stale capture must draw no image, not a synthetic fallback", renderedImages.isEmpty())
            } else {
                assertEquals("Only the actual captured schematic is drawn", 1, renderedImages.size)
                val image = renderedImages.single()
                assertEquals(expectedBitmap.width, image.width.toInt())
                assertEquals(expectedBitmap.height, image.height.toInt())
                val bytes = image.imageBytes
                val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                retainBitmap("$artifactStem-pdf-image-resource.png", decoded)
                assertEquals(expectedBitmap.width, decoded.width)
                assertEquals(expectedBitmap.height, decoded.height)
                val expected = IntArray(expectedBitmap.width * expectedBitmap.height)
                val actual = IntArray(expected.size)
                expectedBitmap.getPixels(expected, 0, expectedBitmap.width, 0, 0, expectedBitmap.width, expectedBitmap.height)
                decoded.getPixels(actual, 0, decoded.width, 0, 0, decoded.width, decoded.height)
                // PDF soft masks carry alpha separately; bind every underlying RGB sample.
                assertTrue("Captured schematic must contain nonblank content", expected.toSet().size > 4)
                assertEquals("Every captured RGB pixel must survive the real PDF image path", 0,
                    expected.indices.count { (expected[it] and 0xFFFFFF) != (actual[it] and 0xFFFFFF) })
                val softMask = image.pdfObject.getAsStream(PdfName.SMask)
                if (softMask == null) {
                    assertTrue("Transparency must not be silently discarded", expected.all { (it ushr 24) == 255 })
                } else {
                    val maskBytes = PdfImageXObject(softMask).imageBytes
                    val mask = requireNotNull(BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size))
                    retainBitmap("$artifactStem-pdf-image-alpha-mask.png", mask)
                    assertEquals(expectedBitmap.width, mask.width)
                    assertEquals(expectedBitmap.height, mask.height)
                    val alpha = IntArray(expected.size)
                    mask.getPixels(alpha, 0, mask.width, 0, 0, mask.width, mask.height)
                    assertEquals("Every alpha sample must survive the real PDF soft mask", 0,
                        expected.indices.count { (expected[it] ushr 24) != (alpha[it] and 0xFF) })
                    mask.recycle()
                }
                decoded.recycle()
            }
        }
        // Screenshot of the visible drawing for side-by-side review with the capture.
        runCatching {
            scrollTo("column-visible-drawing")
            retainBitmap("$artifactStem-visible-drawing-screenshot.png",
                compose.onNodeWithTag("column-visible-drawing").captureToImage().asAndroidBitmap())
        }
        assertTrue("No synthetic CAD companion is allowed", !File(file.parentFile, file.nameWithoutExtension + ".dxf").exists())
    }
}
