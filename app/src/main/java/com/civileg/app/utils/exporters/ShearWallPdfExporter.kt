package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.civileg.app.R
import com.civileg.app.domain.*
import com.civileg.app.utils.ArabicFontProvider
import com.civileg.app.utils.ArabicShaper
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.utils.PdfTextSegmenter
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.BaseDirection
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * PDF exporter for shear wall design reports.
 * Generates professional bilingual (Arabic/English) PDF reports covering:
 * - Cover / header
 * - Wall geometry & material properties
 * - Flexural design (moment & axial capacity)
 * - Shear design (Vc + Vs)
 * - Boundary elements (standard / special confined)
 * - Coupling beam (diagonal reinforcement)
 * - Reinforcement schedule (vertical, horizontal, boundary)
 * - Wall elevation & cross-section drawings
 */
class ShearWallPdfExporter(private val context: Context) {

    // ── Colour palette ──────────────────────────────────────────────
    private val PRIMARY = DeviceRgb(21, 101, 192)
    private val SECONDARY = DeviceRgb(55, 71, 79)
    private val SUCCESS = DeviceRgb(46, 125, 50)
    private val ERROR = DeviceRgb(198, 40, 40)
    private val WARNING = DeviceRgb(245, 124, 0)
    private val HEADER_BG = DeviceRgb(21, 101, 192)
    private val ROW_ALT = DeviceRgb(245, 245, 245)
    private val WHITE = DeviceRgb(255, 255, 255)

    // ── Font management (fresh per PDF) ─────────────────────────────
    private fun arabicFont(): PdfFont = ArabicFontProvider.getArabicPdfFont(context, bold = false)
    private fun arabicBoldFont(): PdfFont = ArabicFontProvider.getArabicPdfFont(context, bold = true)
    private fun helvetica(bold: Boolean = false): PdfFont = try {
        PdfFontFactory.createFont(if (bold) StandardFonts.HELVETICA_BOLD else StandardFonts.HELVETICA)
    } catch (_: Exception) {
        PdfFontFactory.createFont(StandardFonts.HELVETICA)
    }

    private var currentLanguage: String = LocaleHelper.getLocale(context)
    fun setLanguage(lang: String): ShearWallPdfExporter { this.currentLanguage = lang; return this }
    private val isEnglish get() = currentLanguage != "ar"
    private fun isArabic(text: String) = ArabicFontProvider.containsArabic(text)
    private fun t(ar: String, en: String): String = if (isEnglish) en else ar

    // ── Formatting ───────────────────────────────────────────────────
    private fun Double.format(decimals: Int = 2): String =
        String.format(Locale.US, "%.${decimals}f", this)

    // ── Text helpers ────────────────────────────────────────────────
    private fun styledParagraph(
        text: String, fontSize: Float = 10f, bold: Boolean = false,
        color: DeviceRgb? = null, alignment: TextAlignment? = null
    ): Paragraph {
        val arFont = if (bold) arabicBoldFont() else arabicFont()
        val laFont = helvetica(bold)
        return PdfTextSegmenter.buildMixedParagraph(
            text = text, arabicFont = arFont, latinFont = laFont,
            fontSize = fontSize, color = color, alignment = alignment
        )
    }

    private fun tableCell(
        text: String, fontSize: Float = 9f, bold: Boolean = false,
        color: DeviceRgb? = null, bg: DeviceRgb? = null,
        align: TextAlignment = TextAlignment.CENTER
    ): Cell {
        val cell = Cell().setPadding(4f)
        cell.add(styledParagraph(text, fontSize, bold, color, align))
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    private fun headerCell(text: String, colSpan: Int = 1): Cell {
        val shaped = ArabicShaper.shapeIfArabic(text)
        val cell = Cell(colSpan, 1).setPadding(6f).setBackgroundColor(HEADER_BG)
            .setTextAlignment(TextAlignment.CENTER)
        val p = Paragraph().setFontSize(9f)
        p.add(Text(shaped).setFont(arabicBoldFont()).setFontSize(9f).setBold().setFontColor(WHITE))
        if (isArabic(text)) p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        cell.add(p)
        return cell
    }

    // ── Document helpers ─────────────────────────────────────────────
    private fun createDocument(outputPath: String): Triple<PdfDocument, Document, PdfFont> {
        val writer = PdfWriter(FileOutputStream(outputPath))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(40f, 40f, 40f, 40f)
        return Triple(pdf, document, arabicFont())
    }

    private fun addReportHeader(
        document: Document, titleAr: String, titleEn: String, subtitle: String, font: PdfFont
    ) {
        val appNameText = ArabicShaper.shapeIfArabic(context.getString(R.string.app_name))
        val appName = Paragraph().setTextAlignment(TextAlignment.CENTER)
        appName.add(Text(appNameText)
            .setFont(if (isEnglish) helvetica() else arabicBoldFont())
            .setFontSize(22f).setBold().setFontColor(PRIMARY))
        if (!isEnglish) appName.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        document.add(appName)

        val subLineRaw = if (isEnglish) "Civil EG - Advanced Structural Design"
            else "Civil EG - \u0627\u0644\u062A\u0635\u0645\u064A\u0645 \u0627\u0644\u0625\u0646\u0634\u0627\u0626\u064A \u0627\u0644\u0645\u062A\u0642\u062F\u0645"
        val subPara = Paragraph().setTextAlignment(TextAlignment.CENTER)
        subPara.add(Text(ArabicShaper.shapeIfArabic(subLineRaw))
            .setFont(if (isEnglish) helvetica() else arabicFont())
            .setFontSize(10f).setFontColor(ColorConstants.GRAY))
        if (!isEnglish) subPara.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        document.add(subPara)
        document.add(LineSeparator(SolidLine(2f)).setMarginTop(5f).setMarginBottom(10f))

        document.add(styledParagraph(t(titleAr, titleEn), 16f, true, PRIMARY, TextAlignment.CENTER))

        val subtitlePara = Paragraph().setTextAlignment(TextAlignment.CENTER)
        subtitlePara.add(Text(ArabicShaper.shapeIfArabic(subtitle))
            .setFont(if (isEnglish) helvetica() else arabicFont())
            .setFontSize(10f).setFontColor(SECONDARY))
        if (!isEnglish) subtitlePara.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        document.add(subtitlePara)

        val dateLocale = if (isEnglish) Locale.US else Locale("ar")
        val dateStr = SimpleDateFormat("yyyy/MM/dd  HH:mm", dateLocale).format(Date())
        val datePara = Paragraph().setTextAlignment(TextAlignment.CENTER)
        datePara.add(Text(dateStr)
            .setFont(if (isEnglish) helvetica() else arabicFont())
            .setFontSize(9f).setFontColor(ColorConstants.GRAY))
        if (!isEnglish) datePara.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        document.add(datePara)
        document.add(LineSeparator(SolidLine(0.5f)).setMarginTop(5f).setMarginBottom(10f))
    }

    private fun addStatusBanner(document: Document, isSafe: Boolean, details: String = "") {
        val statusText = if (isSafe) {
            if (details.isNotEmpty()) "${t("ا\u0644\u062D\u0627\u0644\u0629: آ\u0645\u0646", "STATUS: SAFE")} - $details"
            else t("ا\u0644\u062D\u0627\u0644\u0629: آ\u0645\u0646 ✔", "STATUS: SAFE ✔")
        } else {
            if (details.isNotEmpty()) "${t("ا\u0644\u062D\u0627\u0644\u0629: غ\u064A\u0631 آ\u0645\u0646", "STATUS: UNSAFE")} - $details"
            else t("ا\u0644\u062D\u0627\u0644\u0629: غ\u064A\u0631 آ\u0645\u0646 ✘", "STATUS: UNSAFE ✘")
        }
        val p = styledParagraph(statusText, 11f, true,
            if (isSafe) SUCCESS else ERROR, TextAlignment.CENTER)
        p.setPadding(8f)
        p.setBorder(com.itextpdf.layout.borders.SolidBorder(if (isSafe) SUCCESS else ERROR, 1.5f))
        document.add(p)
        document.add(Paragraph(" "))
    }

    private fun addSectionTitle(document: Document, titleAr: String, titleEn: String) {
        document.add(styledParagraph(t(titleAr, titleEn), 12f, true, PRIMARY, TextAlignment.CENTER))
        document.add(LineSeparator(SolidLine(0.5f)).setMarginBottom(8f))
    }

    private fun addInfoTable(document: Document, rows: List<Pair<String, String>>, font: PdfFont) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(45f, 55f))).useAllAvailableWidth()
        rows.forEachIndexed { idx, (label, value) ->
            val bg = if (idx % 2 == 0) null else ROW_ALT
            table.addCell(Cell().setPadding(4f).apply {
                val lp = Paragraph().setFontSize(9f)
                lp.add(Text(ArabicShaper.shapeIfArabic(label))
                    .setFont(arabicBoldFont()).setFontSize(9f).setBold())
                if (isArabic(label)) lp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                add(lp); bg?.let { setBackgroundColor(it) }
            })
            table.addCell(Cell().setPadding(4f).apply {
                val vp = Paragraph().setFontSize(9f)
                vp.add(Text(ArabicShaper.shapeIfArabic(value)).setFont(arabicFont()).setFontSize(9f))
                if (isArabic(value)) vp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                add(vp); bg?.let { setBackgroundColor(it) }
            })
        }
        document.add(table)
        document.add(Paragraph(" "))
    }

    private fun addDrawingSection(document: Document, bitmap: Bitmap?, title: String) {
        document.add(Paragraph(" "))
        addSectionTitle(document, t("ا\u0644\u0631\u0633\u0645 \u0627\u0644\u0647\u0646\u062F\u0633\u064A", "Engineering Drawing"), title)
        if (bitmap == null) {
            document.add(styledParagraph(
                t("[ا\u0644\u0631\u0633\u0645 \u063A\u064A\u0631 \u0645\u062A\u0627\u062D]", "[Drawing not available]"),
                9f, color = ColorConstants.GRAY as DeviceRgb, alignment = TextAlignment.CENTER))
            document.add(Paragraph(" "))
            return
        }
        try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setAutoScale(true)
            img.setHorizontalAlignment(HorizontalAlignment.CENTER)
            document.add(img)
        } catch (e: Exception) {
            document.add(styledParagraph(
                t("[\u062E\u0637\u0623 \u0641\u064A \u0639\u0631\u0636 \u0627\u0644\u0631\u0633\u0645]", "[Drawing render error]"),
                9f, color = ColorConstants.GRAY as DeviceRgb, alignment = TextAlignment.CENTER))
        }
        document.add(Paragraph(" "))
    }

    private fun addFooter(document: Document) {
        document.add(Paragraph(" "))
        document.add(LineSeparator(SolidLine(0.5f)).setMarginTop(10f).setMarginBottom(5f))
        document.add(styledParagraph(
            "Generated by Civil EG Pro - ${SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())}",
            8f, color = ColorConstants.GRAY as DeviceRgb, alignment = TextAlignment.CENTER))
        document.add(styledParagraph(
            t("ه\u0630\u0627 \u0627\u0644\u062A\u0642\u0631\u064A\u0631 \u0644\u0623\u063A\u0631\u0627\u0636 \u0645\u0631\u062C\u0639\u064A\u0629 \u0641\u0642\u0637",
                "This report is for reference only - must be reviewed by a qualified engineer."),
            7f, color = ColorConstants.LIGHT_GRAY as DeviceRgb, alignment = TextAlignment.CENTER))
    }

    // ── Reinforcement schedule table ─────────────────────────────────
    private fun addReinforcementSchedule(document: Document, result: ShearWallResult) {
        val table = Table(UnitValue.createPercentArray(
            floatArrayOf(25f, 22f, 18f, 18f, 17f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell(t("ا\u0644\u0639\u0646\u0635\u0631", "Element")))
        table.addHeaderCell(headerCell(t("ا\u0644\u062A\u0633\u0644\u064A\u062D", "Reinforcement")))
        table.addHeaderCell(headerCell(t("ا\u0644\u0645\u0637\u0644\u0648\u0628 (mm\u00B2)", "Req. (mm\u00B2)")))
        table.addHeaderCell(headerCell(t("ا\u0644\u0645\u0632\u0648\u062F (mm\u00B2)", "Prov. (mm\u00B2)")))
        table.addHeaderCell(headerCell(t("ا\u0644\u062D\u0627\u0644\u0629", "Status")))

        fun addRow(element: String, rebar: RebarResult, bg: DeviceRgb?) {
            table.addCell(tableCell(element, bg = bg))
            table.addCell(tableCell("${rebar.bars}\u00D8${rebar.diameter} @ ${rebar.spacing}", bg = bg))
            table.addCell(tableCell(rebar.requiredArea.format(1), bg = bg))
            table.addCell(tableCell(rebar.providedArea.format(1), bg = bg))
            table.addCell(tableCell(
                if (rebar.ratio >= 1.0) t("آ\u0645\u0646 ✔", "OK ✔") else t("غ\u064A\u0631 \u0645\u0637\u0627\u0628\u0642 ✘", "NG ✘"),
                color = if (rebar.ratio >= 1.0) SUCCESS else ERROR, bg = bg))
        }

        addRow(t("ح\u062F\u064A\u062F \u0639\u0645\u0648\u062F\u064A", "Vertical Rebar"),
            result.verticalReinforcement, null)
        addRow(t("ح\u062F\u064A\u062F \u0623\u0641\u0642\u064A", "Horizontal Rebar"),
            result.horizontalReinforcement, ROW_ALT)

        result.boundaryElementReinforcement?.let { ber ->
            addRow(t("ع\u0646\u0635\u0631 \u062D\u062F\u064A", "Boundary Element"),
                ber, null)
        }

        document.add(table)
        document.add(Paragraph(" "))
    }

    // ── Safety checks table ──────────────────────────────────────────
    private fun addSafetyChecksTable(document: Document, checks: List<ShearWallSafetyCheck>) {
        if (checks.isEmpty()) return
        val table = Table(UnitValue.createPercentArray(
            floatArrayOf(30f, 22f, 22f, 26f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell(t("ا\u0644\u0641\u062D\u0635", "Check")))
        table.addHeaderCell(headerCell(t("ا\u0644\u0645\u062D\u0633\u0648\u0628", "Calculated")))
        table.addHeaderCell(headerCell(t("ا\u0644\u062D\u062F \u0627\u0644\u0645\u0633\u0645\u0648\u062D", "Limit")))
        table.addHeaderCell(headerCell(t("ا\u0644\u062D\u0627\u0644\u0629", "Status")))
        checks.forEachIndexed { i, chk ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            table.addCell(tableCell(chk.name, bg = bg))
            table.addCell(tableCell("${chk.value.format(2)} ${chk.unit}", bg = bg))
            table.addCell(tableCell("${chk.limit.format(2)} ${chk.unit}", bg = bg))
            table.addCell(tableCell(
                if (chk.isSafe) t("م\u0637\u0627\u0628\u0642 ✔", "PASS ✔") else t("غ\u064A\u0631 \u0645\u0637\u0627\u0628\u0642 ✘", "FAIL ✘"),
                color = if (chk.isSafe) SUCCESS else ERROR, bg = bg))
        }
        document.add(table)
        document.add(Paragraph(" "))
    }

    // ══════════════════════════════════════════════════════════════════
    // MAIN EXPORT
    // ══════════════════════════════════════════════════════════════════
    fun export(
        projectName: String,
        designCode: String,
        input: ShearWallInput,
        result: ShearWallResult,
        outputPath: String,
        elevationBitmap: Bitmap? = null,
        sectionBitmap: Bitmap? = null
    ): File? {
        return try {
            val (_, document, font) = createDocument(outputPath)

            // ── Cover / Header ──
            addReportHeader(document,
                t("ت\u0642\u0631\u064A\u0631 \u062A\u0635\u0645\u064A\u0645 \u062C\u062F\u0627\u0631 \u0642\u0635",
                    "Shear Wall Design Report"),
                "Shear Wall Design Report",
                "$designCode | ${input.wallType.displayName}",
                font
            )

            addStatusBanner(document, result.isSafe,
                "Utilization: ${(result.utilizationRatio * 100).format(1)}%")

            // ── 1. Wall Geometry ──
            addSectionTitle(document, t("أ\u0628\u0639\u0627\u062F \u0627\u0644\u062C\u062F\u0627\u0631", "Wall Geometry"), "Wall Geometry")
            addInfoTable(document, listOf(
                t("ا\u0633\u0645 \u0627\u0644\u0645\u0634\u0631\u0648\u0639", "Project Name") to projectName,
                t("ا\u0644\u0643\u0648\u062F \u0627\u0644\u062A\u0635\u0645\u064A\u0645\u064A", "Design Code") to designCode,
                t("ن\u0648\u0639 \u0627\u0644\u062C\u062F\u0627\u0631", "Wall Type") to input.wallType.displayName,
                t("ط\u0648\u0644 \u0627\u0644\u062C\u062F\u0627\u0631 (Lw)", "Wall Length") to "${input.wallLength.format(0)} mm",
                t("س\u0645\u0643 \u0627\u0644\u062C\u062F\u0627\u0631 (tw)", "Wall Thickness") to "${input.wallThickness.format(0)} mm",
                t("ا\u0631\u062A\u0641\u0627\u0639 \u0627\u0644\u0637\u0628\u0642\u0629", "Story Height") to "${input.wallHeight.format(0)} mm",
                t("ع\u062F\u062F \u0627\u0644\u0637\u0648\u0627\u0628\u0642", "Number of Stories") to "${input.numberOfStories}",
                t("ن\u0633\u0628\u0629 \u0627\u0644\u0646\u062D\u0627\u0641\u0629 (H/t)", "Slenderness (H/t)") to "${result.slendernessRatio.format(1)}",
                t("م\u0642\u0627\u0648\u0645\u0629 \u0627\u0644\u062E\u0631\u0633\u0627\u0646\u0629", "Concrete f'c") to "${input.fcu.format(0)} MPa",
                t("م\u0642\u0627\u0648\u0645\u0629 \u0627\u0644\u062D\u062F\u064A\u062F", "Steel fy") to "${input.fy.format(0)} MPa",
                t("م\u0642\u0627\u0648\u0645\u0629 \u0627\u0644\u062D\u062F\u064A\u062F \u0627\u0644\u0623\u0641\u0642\u064A", "Steel fyv") to "${input.fyv.format(0)} MPa",
                t("ا\u0644\u063A\u0637\u0627\u0621 \u0627\u0644\u062E\u0631\u0633\u0627\u0646\u0629", "Clear Cover") to "${input.clearCover.format(0)} mm"
            ), font)

            // ── 2. Flexural Design ──
            addSectionTitle(document, t("ت\u0635\u0645\u064A\u0645 \u0627\u0644\u0627\u0646\u062D\u0646\u0627\u0621", "Flexural Design"), "Flexural Design")
            addInfoTable(document, listOf(
                t("ا\u0644\u062D\u0645\u0644 \u0627\u0644\u0645\u062D\u0648\u0631\u064A", "Axial Load (P)") to "${input.axialLoad.format(1)} kN",
                t("ا\u0644\u0639\u0632\u0645 \u0627\u0644\u0627\u0646\u062D\u0646\u0627\u0626\u064A (M)", "Bending Moment") to "${input.bendingMoment.format(1)} kN.m",
                t("ق\u062F\u0631\u0629 \u0627\u0644\u0639\u0632\u0645 (Mn)", "Moment Capacity") to "${result.momentCapacity.format(1)} kN.m",
                t("ا\u0644\u0642\u062F\u0631\u0629 \u0627\u0644\u0645\u062D\u0648\u0631\u064A\u0629 (Pn)", "Axial Capacity") to "${result.axialCapacity.format(1)} kN",
                t("ع\u0645\u0642 \u0627\u0644\u0636\u063A\u0637", "Compression Depth (a)") to "${result.compressionDepth.format(1)} mm",
                t("ا\u0644\u062D\u0627\u0644\u0629", "Status") to
                    if (result.flexuralOk) t("آ\u0645\u0646 ✔", "OK ✔") else t("غ\u064A\u0631 آ\u0645\u0646 ✘", "NG ✘")
            ), font)

            // ── 3. Shear Design ──
            addSectionTitle(document, t("ت\u0635\u0645\u064A\u0645 \u0627\u0644\u0642\u0635", "Shear Design"), "Shear Design")
            addInfoTable(document, listOf(
                t("ا\u0644\u0642\u0648\u0629 \u0627\u0644\u0642\u0635\u064A\u0629 (Vu)", "Shear Force (Vu)") to "${input.shearForce.format(1)} kN",
                t("ق\u062F\u0631\u0629 \u0627\u0644\u062E\u0631\u0633\u0627\u0646\u0629 (Vc)", "Concrete Shear (Vc)") to "${result.concreteShearCapacity.format(1)} kN",
                t("ق\u062F\u0631\u0629 \u0627\u0644\u062D\u062F\u064A\u062F (Vs)", "Steel Shear (Vs)") to "${result.steelShearCapacity.format(1)} kN",
                t("ا\u0644\u0642\u062F\u0631\u0629 \u0627\u0644\u0625\u062C\u0645\u0627\u0644\u064A\u0629 (Vn)", "Total Shear (Vn)") to "${result.shearCapacity.format(1)} kN",
                t("ا\u0644\u062D\u0627\u0644\u0629", "Status") to
                    if (result.shearOk) t("آ\u0645\u0646 ✔", "OK ✔") else t("غ\u064A\u0631 آ\u0645\u0646 ✘", "NG ✘")
            ), font)

            // ── 4. Boundary Elements ──
            addSectionTitle(document, t("ا\u0644\u0639\u0646\u0627\u0635\u0631 \u0627\u0644\u062D\u062F\u064A\u0629", "Boundary Elements"), "Boundary Elements")
            addInfoTable(document, listOf(
                t("ن\u0648\u0639 \u0627\u0644\u0639\u0646\u0635\u0631 \u0627\u0644\u062D\u062F\u064A", "Boundary Type") to result.boundaryElementType.displayName,
                t("ط\u0648\u0644 \u0645\u0646\u0637\u0642\u0629 \u0627\u0644\u0646\u0647\u0627\u064A\u0629", "End Zone Length") to
                    if (input.endZoneLength > 0) "${input.endZoneLength.format(0)} mm" else t("ت\u0644\u0642\u0627\u0626\u064A", "Auto")
            ), font)

            // ── 5. Coupling Beam (if applicable) ──
            if (result.couplingBeamResult != null) {
                addSectionTitle(document, t("ك\u0645\u0631\u0629 \u0627\u0644\u0631\u0628\u0637", "Coupling Beam"), "Coupling Beam")
                val cb = result.couplingBeamResult!!
                addInfoTable(document, listOf(
                    t("ط\u0648\u0644 \u0627\u0644\u0643\u0645\u0631\u0629", "Beam Length") to "${input.couplingBeamLength.format(0)} mm",
                    t("ا\u0631\u062A\u0641\u0627\u0639 \u0627\u0644\u0643\u0645\u0631\u0629", "Beam Height") to "${input.couplingBeamHeight.format(0)} mm",
                    t("ا\u0644\u0628\u0627\u0631\u0627\u062A \u0627\u0644\u0642\u0637\u0631\u064A\u0629", "Diagonal Bars") to
                        "${cb.diagonalBars} \u00D8${cb.diagonalBarDiameter}",
                    t("ا\u0644\u0623\u0637\u0648\u0627\u0642 \u0627\u0644\u0639\u0631\u0636\u064A\u0629", "Transverse Bars") to
                        "\u00D8${cb.transverseBarsDiameter} @ ${cb.transverseBarsSpacing} mm",
                    t("ن\u0633\u0628\u0629 \u0627\u0644\u0627\u0633\u062A\u062E\u062F\u0627\u0645", "Utilization") to "${(cb.utilizationRatio * 100).format(1)}%",
                    t("ا\u0644\u062D\u0627\u0644\u0629", "Status") to
                        if (cb.isSafe) t("آ\u0645\u0646 ✔", "OK ✔") else t("غ\u064A\u0631 آ\u0645\u0646 ✘", "NG ✘")
                ), font)
            }

            // ── 6. Reinforcement Schedule ──
            addSectionTitle(document, t("ج\u062F\u0648\u0644 \u0627\u0644\u062A\u0633\u0644\u064A\u062D", "Reinforcement Schedule"), "Reinforcement")
            addReinforcementSchedule(document, result)

            // ── 7. Quantities ──
            addSectionTitle(document, t("ا\u0644\u0643\u0645\u064A\u0627\u062A", "Quantities"), "Quantities")
            addInfoTable(document, listOf(
                t("ح\u062C\u0645 \u0627\u0644\u062E\u0631\u0633\u0627\u0646\u0629 / \u0637\u0628\u0642\u0629", "Concrete Vol./Story") to "${result.concreteVolumePerStory.format(3)} m\u00B3",
                t("و\u0632\u0646 \u0627\u0644\u062D\u062F\u064A\u062F / \u0637\u0628\u0642\u0629", "Steel Wt./Story") to "${result.steelWeightPerStory.format(1)} kg"
            ), font)

            // ── 8. Safety Checks Summary ──
            if (result.safetyChecks.isNotEmpty()) {
                addSectionTitle(document, t("م\u0644\u062E\u0635 \u0627\u0644\u0641\u062D\u0648\u0635\u0627\u062A", "Safety Checks"), "Safety Checks")
                addSafetyChecksTable(document, result.safetyChecks)
            }

            // ── 9. Wall Elevation Drawing ──
            addDrawingSection(document, elevationBitmap,
                t("م\u0633\u0642\u0637 \u0648\u0627\u062C\u0647 \u0627\u0644\u062C\u062F\u0627\u0631", "Wall Elevation"))

            // ── 10. Wall Cross-Section Drawing ──
            addDrawingSection(document, sectionBitmap,
                t("م\u0642\u0637\u0639 \u0639\u0631\u0636\u064A \u0644\u0644\u062C\u062F\u0627\u0631", "Wall Cross-Section"))

            // ── Warnings & Notes ──
            if (result.warnings.isNotEmpty() || result.codeNotes.isNotEmpty()) {
                addSectionTitle(document, t("م\u0644\u0627\u062D\u0638\u0627\u062A", "Notes & Warnings"), "Notes & Warnings")
                (result.warnings + result.codeNotes).forEach { note ->
                    document.add(styledParagraph("\u2022 $note", 9f,
                        color = if (note.contains("warning", true) || note.contains("ت\u062D\u0630\u064A\u0631", true)) WARNING else SECONDARY))
                }
            }

            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) {
            Log.e("ShearWallPdfExporter", "export failed: ${e.message}", e)
            null
        }
    }
}
