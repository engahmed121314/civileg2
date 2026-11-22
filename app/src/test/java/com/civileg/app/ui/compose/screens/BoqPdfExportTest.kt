package com.civileg.app.ui.compose.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.R
import com.civileg.app.db.Design
import com.civileg.app.db.DesignType
import com.civileg.app.db.QuantityBasis
import com.civileg.app.domain.entities.BoqCategory
import com.civileg.app.domain.entities.BoqItem
import com.civileg.app.domain.entities.BoqPricing
import com.civileg.app.domain.entities.SavedMaterialPrices
import com.civileg.app.domain.entities.SavedPrice
import com.civileg.app.shared.reporting.PdfGenerator
import com.civileg.app.testutils.saveGeneratedPdfForInspection
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Generates the real BOQ PDF and reads its text back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar")
class BoqPdfExportTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun text(f: File): String = PdfDocument(PdfReader(f)).use { d ->
        (1..d.numberOfPages).joinToString("\n") { PdfTextExtractor.getTextFromPage(d.getPage(it)) }
    }.replace(Regex("\\s+"), " ")

    /** Any code point whose Unicode script is Arabic (letters and presentation forms). Digits are Common, not Arabic. */
    private fun hasArabicScript(s: String): Boolean =
        s.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.ARABIC }

    private fun assertEnglishLatinDate(t: String) {
        val expected = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(java.util.Date())
        assertTrue("Report date $expected missing in: $t", t.contains(expected))
    }

    private fun line(c: BoqCategory, q: Double, unit: String) =
        BoqItem(itemId = c.name, description = c.name, category = c, unit = unit, quantity = q, unitPrice = 1.0, total = q)

    @Test fun designExportPrintsOneTonneAsOneTonNotAThousandthAndUsesTheSelectedCurrency() {
        val raw = listOf(line(BoqCategory.REINFORCEMENT, 1.0, "ton"), line(BoqCategory.CONCRETE, 2.0, "m3"))
        val r = BoqPricing.apply(raw, SavedMaterialPrices(steel = SavedPrice(38000.0, "SAR")), "SAR")
        val file = buildDesignBoqPdf(ctx, "Beam", r.items, r.unpricedCount, "SAR")
        saveGeneratedPdfForInspection(file, "boq-design-export-arabic-ui")
        val t = text(file)
        assertTrue(t, t.contains("1.00 Tons"))
        assertFalse(t, t.contains("0.00 Tons"))
        assertTrue(t, t.contains("38,000.00 SAR"))
        // the app UI is Arabic here, but the report text is English
        assertTrue(ctx.getString(R.string.boq_price_not_set) != "Price not set")
        assertFalse(t, hasArabicScript(t))
        assertFalse(t, t.contains("EGP"))
        assertEnglishLatinDate(t)
        assertTrue(t, t.contains("Total of priced items only (1 without price)"))
        assertTrue(t, t.contains("Price not set"))
    }

    @Test fun projectExportCountsMeasuredOnlyAndMarksUnknownAndUnverifiedRows() {
        fun d(n: String, b: QuantityBasis, c: Double, s: Double, cost: Double) = Design(
            projectId = 1L, type = DesignType.BEAM, name = n, inputData = "{}", results = "{}", isSafe = true,
            codeUsed = "ECP", concreteVolume = c, steelWeight = s, totalCost = cost, quantityBasis = b
        )
        val designs = listOf(
            d("Measured", QuantityBasis.MEASURED, 1.0, 1000.0, 5000.0),
            d("Legacy", QuantityBasis.LEGACY_UNVERIFIED, 9.0, 9000.0, 90000.0),
            d("Unknown", QuantityBasis.UNKNOWN, 0.0, 0.0, 0.0)
        )
        val m = projectBoqExport(ctx, designs)
        assertTrue(m.concrete == 1.0 && m.steelKg == 1000.0 && m.currency == null)
        val file = buildProjectBoqPdf(ctx, "Project", designs)
        saveGeneratedPdfForInspection(file, "boq-project-export-arabic-ui")
        val t = text(file)
        assertTrue(t, t.contains("1.00 Tons"))
        assertTrue(t, t.contains("Quantity unknown"))
        assertTrue(t, t.contains("90,000 *"))
        assertTrue(t, t.contains("Totals include measured designs only."))
        assertTrue(t, t.contains("1 design(s) have unknown quantities"))
        assertFalse(t, t.contains("95,000"))
        // no summed cost headline: stored costs may be in different currencies
        assertTrue(t, t.contains("Total Estimated Budget Not available"))
        assertFalse(t, t.contains("5,000.00"))
        assertTrue(t, t.contains("Cost (currency not recorded)"))
        assertTrue(t, t.contains("5,000"))
        assertEnglishLatinDate(t)
        assertTrue(t, t.contains("Saved design costs have no recorded currency."))
        assertFalse(t, t.contains("EGP"))
        assertFalse(t, hasArabicScript(t))
    }
}
