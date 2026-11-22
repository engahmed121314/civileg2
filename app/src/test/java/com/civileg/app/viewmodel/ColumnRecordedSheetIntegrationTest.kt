package com.civileg.app.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Environment
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.MutableLiveData
import com.civileg.app.BuildConfig
import com.civileg.app.db.DesignRepository
import com.civileg.app.db.Project
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.compose.screens.ColumnScreen
import com.civileg.app.ui.compose.screens.columnCheckedCoordinateInputs
import com.civileg.app.ui.compose.components.drawings.renderColumnSheet
import com.civileg.app.ui.compose.components.drawings.columnDrawingSummary
import com.civileg.app.utils.SettingsManager
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Real default route, production painter and real ExportManager. Not design/takeoff approval. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColumnRecordedSheetIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var host: View
    private lateinit var vm: ColumnViewModel
    private val output get() = File("build/reports/tests/column-full-export/${BuildConfig.FLAVOR}").apply { mkdirs() }

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
        // These are actual legacy route values, not a new engineering benchmark.
        assertEquals(9, recorded.reinforcement.numBars)
        assertEquals(20, recorded.reinforcement.diameter)
    }

    private fun button(tag: String) = compose.onNodeWithTag(tag)
        .onChildren().filterToOne(hasClickAction())
    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("column-content").performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }
    private fun documents() = requireNotNull(host.context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS))
    private fun newReport(before: Set<String>): File? = documents().listFiles()?.firstOrNull {
        it.name.startsWith("Column_Report_") && it.extension == "pdf" && it.name !in before && it.length() > 0
    }
    private fun retainBitmap(name: String, bitmap: Bitmap) {
        File(output, name).outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    private fun inspectReport(file: File, expectedDrawingStatus: String, artifactName: String,
                              expectedBitmap: Bitmap? = null) {
        // Preserve the actual PDF and source capture even when an assertion below fails.
        file.copyTo(File(output, artifactName), overwrite = true)
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
        assertTrue("No synthetic CAD companion is allowed", !File(file.parentFile, file.nameWithoutExtension + ".dxf").exists())
    }

    @Test fun `actual default route keeps nine recorded bars but supplies no fabricated adopted coordinates`() {
        open()
        val snapshot = requireNotNull(vm.uiState.value.completedDesign)
        assertTrue(columnCheckedCoordinateInputs(snapshot).isEmpty())
        assertNull(vm.buildDxfDrawing(snapshot))
        scrollTo("column-adopted-layout-hold")
        compose.onNodeWithTag("column-adopted-layout-hold").assertIsDisplayed()
        scrollTo("column-visible-drawing")
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
            host.draw(Canvas(bitmap))
            File(output, "actual-default-caller-dimensions-only.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        val input = snapshot.input(); val result = snapshot.result()
        val bitmap = Bitmap.createBitmap(400, 777, Bitmap.Config.ARGB_8888)
        val evidence = renderColumnSheet(Canvas(bitmap), 400f, 777f, result.width, result.depth,
            input.heightM * 1000.0, columnCheckedCoordinateInputs(snapshot),
            result.stirrups.diameter.toDouble(), result.stirrups.spacing, 40.0,
            resultSummary = columnDrawingSummary(result))
        assertTrue(evidence.suppliedBars.isEmpty())
        assertTrue(evidence.drawnSectionCenters.isEmpty())
        assertTrue(evidence.allTextFits)
        File(output, "actual-default-dimensions-only-sheet.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    @Test fun `actual PDF button embeds the completed capture through real ExportManager`() {
        open()
        val observedCaptures = mutableListOf<ColumnDrawingCapture?>()
        every { vm.exportToPdf(any(), any(), captureNullable(observedCaptures), any()) } answers { callOriginal() }
        val before = documents().listFiles().orEmpty().map { it.name }.toSet()
        scrollTo("column-pdf-export");
        button("column-pdf-export").performClick()
        try {
            compose.waitUntil(30000) { !vm.uiState.value.isExporting && newReport(before) != null }
        } catch (failure: Throwable) {
            retainColumnUiTimeout(output, documents(), before, "actual-button", vm.uiState.value,
                "captureCalls=${observedCaptures.size}", failure)
            runCatching {
                observedCaptures.singleOrNull()?.bitmap?.let { retainBitmap("timeout-actual-button-capture.png", it) }
            }.onFailure { println("COLUMN_EXPORT_CAPTURE_DIAGNOSTIC_FAILED ${it.javaClass.name}") }
            throw failure
        }
        assertEquals("The real UI must supply one completed capture", 1, observedCaptures.size)
        val observed = requireNotNull(observedCaptures.single()) { "Real UI did not capture its completed sheet" }
        assertEquals(requireNotNull(vm.uiState.value.completedDesign).revisionSha256, observed.revisionSha256)
        val frozen = observed.bitmap
        try {
            inspectReport(requireNotNull(newReport(before)), "Captured completed revision schematic", "actual-captured-full-report.pdf", frozen)
        } finally { frozen.recycle() }
    }

    @Test fun `wrong revision capture reaches the full exporter without a synthetic image`() {
        open()
        val previous = requireNotNull(vm.uiState.value.completedDesign)
        val bitmap = Bitmap.createBitmap(53, 79, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        val stale = ColumnDrawingCapture(previous.revisionSha256, bitmap)
        bitmap.recycle()
        compose.runOnIdle { vm.updateInputs(width = "350"); vm.calculateManual() }
        compose.waitForIdle()
        val current = requireNotNull(vm.uiState.value.completedDesign)
        assertNotEquals(previous.revisionSha256, current.revisionSha256)
        var callback = false
        var generated: File? = null
        val before = documents().listFiles().orEmpty().map { it.name }.toSet()
        compose.runOnIdle { vm.exportToPdf(host.context, current, stale) { generated = it; callback = true } }
        try {
            compose.waitUntil(30000) { callback }
        } catch (failure: Throwable) {
            retainColumnUiTimeout(output, documents(), before, "stale-capture", vm.uiState.value,
                "entered=$callback generated=${generated?.name}", failure)
            throw failure
        }
        inspectReport(requireNotNull(generated), "UNAVAILABLE", "actual-wrong-revision-full-report.pdf")
    }

    @Test fun `missing capture still creates a truthful full report without synthetic reinforcement fallback`() {
        open()
        val snapshot = requireNotNull(vm.uiState.value.completedDesign)
        var callback = false
        var generated: File? = null
        val before = documents().listFiles().orEmpty().map { it.name }.toSet()
        compose.runOnIdle {
            vm.exportToPdf(host.context, snapshot, null) { generated = it; callback = true }
        }
        try {
            compose.waitUntil(30000) { callback }
        } catch (failure: Throwable) {
            retainColumnUiTimeout(output, documents(), before, "missing-capture", vm.uiState.value,
                "entered=$callback generated=${generated?.name}", failure)
            throw failure
        }
        inspectReport(requireNotNull(generated), "UNAVAILABLE", "actual-no-capture-full-report.pdf")
    }
}
