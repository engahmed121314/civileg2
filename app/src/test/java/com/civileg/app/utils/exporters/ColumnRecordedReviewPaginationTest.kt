package com.civileg.app.utils.exporters

import android.graphics.Bitmap
import com.civileg.app.BuildConfig
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo
import com.itextpdf.kernel.geom.Matrix
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Pagination regression fixtures for the recorded-review route only: the
 * schematic image and its caption must share a page, section headings must
 * sit with their first body block, and every engine row must survive
 * pagination. Synthetic content; real PDF layout through iText. Page count
 * is reported for context but never used as proof. The image-dimension
 * assertions below read the embedded image's SOURCE PIXEL DIMENSIONS (display
 * scaling does not resample the embedded stream) - a blank synthetic bitmap
 * cannot witness edge content, so actual RGB/mask content fidelity stays with
 * the real device capture after regeneration, not here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColumnRecordedReviewPaginationTest {
    private val output get() = File("build/reports/tests/column-recorded-review/${BuildConfig.FLAVOR}").apply { mkdirs() }

    private fun pageTexts(pdf: PdfDocument): List<String> =
        (1..pdf.numberOfPages).map {
            PdfTextExtractor.getTextFromPage(pdf.getPage(it)).replace(Regex("\\s+"), " ")
        }

    /** 1-based page numbers embedding at least one image xobject, with its pixel size. */
    private fun imagePages(pdf: PdfDocument): List<Triple<Int, Int, Int>> {
        val found = mutableListOf<Triple<Int, Int, Int>>()
        for (i in 1..pdf.numberOfPages) {
            val xobjects = pdf.getPage(i).resources.getResource(PdfName.XObject) ?: continue
            for (name in xobjects.keySet()) {
                val stream = xobjects.getAsStream(name) ?: continue
                if (PdfName.Image == stream.getAsName(PdfName.Subtype)) {
                    found.add(Triple(i,
                        stream.getAsNumber(PdfName.Width).intValue(),
                        stream.getAsNumber(PdfName.Height).intValue()))
                }
            }
        }
        return found
    }

    /** Token-safe containment: \b keeps "Raw scalar 1" from matching "Raw scalar 10". */
    private fun assertPageHasToken(pageText: String, label: String, context: String) {
        assertTrue("$context - missing token [$label]", pageText.contains(Regex("""\b${Regex.escape(label)}\b""")))
    }

    private fun generate(results: Map<String, String>, fileName: String, steps: List<ProfessionalEnglishPdfReporter.CalculationStep>): File {
        val bitmap = Bitmap.createBitmap(1078, 2210, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.DKGRAY)
        val file = requireNotNull(ProfessionalEnglishPdfReporter.generateReport(
            reportType = ProfessionalEnglishPdfReporter.ReportType.COLUMN,
            title = "SYNTHETIC PAGINATION FIXTURE",
            subtitle = "Keep-together regression only, not an engine result",
            designCode = "ECP 203 requested",
            inputs = mapOf("Code Edition Status" to "Not verified"),
            results = results,
            safetyChecks = emptyList(), isSafe = true,
            calculationSteps = steps,
            drawingBitmap = bitmap,
            outputPath = File(output, fileName).absolutePath,
            presentation = ProfessionalEnglishPdfReporter.ReportPresentation.COLUMN_RECORDED_REVIEW
        ))
        bitmap.recycle()
        return file
    }

    private fun firstStep(index: Int) = ProfessionalEnglishPdfReporter.CalculationStep(index,
        "Synthetic recorded trace marker step-$index", "recorded equation marker $index", "unverified reference marker $index", "${16 + index}")

    @Test fun `recorded schematic keeps its caption and headings keep their first body`() {
        val file = generate(
            results = (1..12).associate { "Raw scalar $it" to "${17 + it}" },
            fileName = "synthetic-pagination-keep.pdf",
            steps = (1..2).map { firstStep(it) })

        PdfDocument(PdfReader(file)).use { pdf ->
            val pages = pageTexts(pdf)
            val images = imagePages(pdf)
            val context = "pages=${pdf.numberOfPages} imagePages=$images"

            // Source pixel dimensions: exactly one embedded image, at its
            // source pixel size. NOT content proof (see class doc).
            assertEquals(context, 1, images.size)
            assertEquals(context, 1078, images[0].second)
            assertEquals(context, 2210, images[0].third)

            // Image and its caption on the SAME page (the page-6 orphan defect).
            val captionPage = pages.indexOfFirst { it.contains("Captured schematic only") } + 1
            assertTrue(context, captionPage > 0)
            assertEquals(context, images[0].first, captionPage)

            // The trace heading binds to the first ACTUAL step block, proven
            // by the step's UNIQUE marker - "Requested code" already sat on
            // the defective heading page and proves nothing.
            val tracePage = pages.indexOfFirst { it.contains("2. Recorded Calculation Trace") } + 1
            assertTrue(context, tracePage > 0)
            assertPageHasToken(pages[tracePage - 1], "Synthetic recorded trace marker step-1", context)

            // Results heading binds to the table; ALL unique rows share its
            // page (token-safe: 1 must not match 10/11/12).
            val resultsPage = pages.indexOfFirst { it.contains("3. Recorded Engine Results") } + 1
            assertTrue(context, resultsPage > 0)
            for (i in 1..12) assertPageHasToken(pages[resultsPage - 1], "Raw scalar $i", context)

            // Emitted display geometry: the actual image CTM written to the
            // content stream (a,b,c,d,e,f with a=display width, d=display
            // height in user units). Bounded assertions: aspect ratio of the
            // emitted display matches the 1078x2210 source (no distortion),
            // and display width stays within the production 480pt width cap.
            // The full CTM-vs-measured-remaining-cap comparison needs the
            // production measured reserve, which this test deliberately does
            // not re-derive; the production path logs selected size, reserve,
            // probe status and occupied bbox for that comparison.
            val ctms = mutableListOf<Matrix>()
            val listener = object : IEventListener {
                override fun eventOccurred(data: IEventData, type: EventType) {
                    if (data is ImageRenderInfo) ctms.add(data.imageCtm)
                }
                override fun getSupportedEvents(): Set<EventType> = setOf(EventType.RENDER_IMAGE)
            }
            val processor = PdfCanvasProcessor(listener)
            processor.processPageContent(pdf.getPage(captionPage))
            assertEquals(context, 1, ctms.size)
            val ctm = ctms[0]
            // iText Matrix is a 3x3 array; indices 0/1/3/4/6/7 are a/b/c/d/e/f
            // (I11/I12/I21/I22/I31/I32). a=display width, d=display height.
            val a = ctm.get(0); val b = ctm.get(1); val c = ctm.get(2); val d = ctm.get(4)
            System.out.println("emitted image CTM a=$a b=$b c=$c d=$d e=${ctm.get(6)} f=${ctm.get(7)} on page $captionPage")
            assertTrue("$context ctm=$ctm", a > 0f && a <= 480.5f)
            assertTrue("$context ctm=$ctm", d > 0f)
            // unrotated: off-diagonal shear terms ~0 before comparing a/d
            assertEquals("$context ctm=$ctm", 0.0, b.toDouble(), 0.01)
            assertEquals("$context ctm=$ctm", 0.0, c.toDouble(), 0.01)
            assertEquals("$context ctm=$ctm", 1078.0 / 2210.0, a.toDouble() / d.toDouble(), 0.02)

            pages.forEachIndexed { index, page -> assertTrue(page, page.contains("Page ${index + 1}")) }
            assertTrue(context, pages.all { it.contains("NOT FOR CONSTRUCTION") })
        }
    }

    @Test fun `engine table longer than a page retains every row under best-effort split`() {
        // 80 unique rows exceed one usable page: keepTogether is best-effort,
        // so the table must SPLIT, and the proof is that every row survives -
        // none clipped, none dropped, no overflow into oblivion.
        val file = generate(
            results = (1..80).associate { "Raw scalar $it" to "${100 + it}" },
            fileName = "synthetic-pagination-longtable.pdf",
            steps = listOf(firstStep(1)))

        PdfDocument(PdfReader(file)).use { pdf ->
            val pages = pageTexts(pdf)
            val context = "pages=${pdf.numberOfPages}"
            val whole = pages.joinToString(" ")
            // Text retention across the split, NOT a pixel overflow proof.
            for (i in 1..80) assertPageHasToken(whole, "Raw scalar $i", context)
            // The rows must actually span more than one page (cheap distinct-
            // row page count), otherwise the fixture never exercised a split.
            val rowPages = pages.mapIndexedNotNull { index, page ->
                if (page.contains(Regex("\\bRaw scalar \\d+\\b"))) index + 1 else null
            }.toSet()
            assertTrue("$context rowPages=$rowPages", rowPages.size > 1)
            // The heading still binds to the table START (first row on its page).
            val resultsPage = pages.indexOfFirst { it.contains("3. Recorded Engine Results") } + 1
            assertTrue(context, resultsPage > 0)
            assertPageHasToken(pages[resultsPage - 1], "Raw scalar 1", context)
            // And the rest of the report still exists after the long table.
            assertTrue(context, whole.contains("4. Recorded Engine Checks") || whole.contains("5. Column Schematic"))
        }
    }
}
