package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.civileg.app.R
import com.civileg.app.db.Design
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.ArabicFontProvider
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.utils.ArabicShaper
import com.civileg.app.utils.PdfTextSegmenter
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.BaseDirection
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * مصدّر PDF الشامل - Comprehensive PDF Exporter
 *
 * Generates professional Arabic/English bilingual structural design reports
 * with proper RTL text shaping, connected Arabic letters, and engineering formatting.
 *
 * Uses ArabicFontProvider to guarantee correct Arabic rendering via bundled
 * NotoNaskhArabic font with IDENTITY_H encoding and OpenType shaping.
 */
class ComprehensivePdfExporter(private val context: Context) {

    // ==================== Color Palette ====================
    private val PRIMARY = DeviceRgb(21, 101, 192)
    private val SECONDARY = DeviceRgb(55, 71, 79)
    private val SUCCESS = DeviceRgb(46, 125, 50)
    private val ERROR = DeviceRgb(198, 40, 40)
    private val WARNING = DeviceRgb(245, 124, 0)
    private val LIGHT_BG = DeviceRgb(232, 245, 253)
    private val HEADER_BG = DeviceRgb(21, 101, 192)
    private val ROW_ALT = DeviceRgb(245, 245, 245)
    private val WHITE = DeviceRgb(255, 255, 255)

    // ==================== Font Management ====================
    // CRITICAL: NEVER cache PdfFont objects across PDFs. iText 8 binds each PdfFont
    // to the FIRST PdfDocument that uses it; after that document is closed, the
    // cached font becomes invalid and any subsequent use throws:
    //   "Pdf indirect object belongs to other PDF document. Copy object to current pdf document."
    //
    // This is especially critical for this class because it is bound as @Singleton in Hilt DI
    // (AppModule.kt) — a single instance is shared across the entire app. With cached fonts,
    // the first PDF export would succeed but EVERY subsequent export would fail.
    //
    // Each call below returns a FRESH PdfFont. ArabicFontProvider caches the underlying
    // FontProgram (parsed TTF — the expensive part) so creating fresh PdfFont wrappers is cheap.
    private fun arabicFont(): PdfFont = ArabicFontProvider.getArabicPdfFont(context, bold = false)
    private fun arabicBoldFont(): PdfFont = ArabicFontProvider.getArabicPdfFont(context, bold = true)
    private fun helveticaFont(bold: Boolean = false): PdfFont = try {
        PdfFontFactory.createFont(if (bold) StandardFonts.HELVETICA_BOLD else StandardFonts.HELVETICA)
    } catch (_: Exception) {
        PdfFontFactory.createFont(StandardFonts.HELVETICA)
    }

    private var currentLanguage: String = LocaleHelper.getLocale(context)

    fun setLanguage(lang: String): ComprehensivePdfExporter {
        this.currentLanguage = lang
        return this
    }

    private val isEnglish get() = currentLanguage != "ar"

    // ==================== Text Helpers ====================
    private fun isArabic(text: String) = ArabicFontProvider.containsArabic(text)

    /**
     * Returns Arabic text only when language is Arabic.
     * Used for Arabic-only content in concatenation (legacy bilingual pattern).
     */
    private fun ar(text: String): String = if (isEnglish) "" else text

    /**
     * Bilingual text helper - returns the appropriate language version.
     * @param ar Arabic text
     * @param en English text
     */
    private fun t(ar: String, en: String): String = if (isEnglish) en else ar

    private fun styledParagraph(
        text: String,
        fontSize: Float = 10f,
        bold: Boolean = false,
        color: DeviceRgb? = null,
        alignment: TextAlignment? = null,
        rtl: Boolean? = null
    ): Paragraph {
        // CRITICAL FIX (2026-07-26): Use PdfTextSegmenter to properly render mixed
        // Arabic/Latin text. Previous approach used the Arabic font for the ENTIRE
        // text when any Arabic was detected. But the bundled NotoNaskhArabic static
        // font only contains 15 Latin chars (digits + basic punctuation) — so Latin
        // letters in mixed text rendered as TOFU (□), producing "encrypted-looking"
        // output. Now we split into segments and use the appropriate font per segment.
        val arabicFont = if (bold) arabicBoldFont() else arabicFont()
        val latinFont = helveticaFont(bold)
        return PdfTextSegmenter.buildMixedParagraph(
            text = text,
            arabicFont = arabicFont,
            latinFont = latinFont,
            fontSize = fontSize,
            color = color,
            alignment = alignment
        )
    }

    private fun rtlParagraph(text: String, fontSize: Float = 10f, bold: Boolean = false, color: DeviceRgb? = null): Paragraph {
        return styledParagraph(text, fontSize, bold, color, TextAlignment.RIGHT, true)
    }

    private fun tableCell(
        text: String,
        fontSize: Float = 9f,
        bold: Boolean = false,
        color: DeviceRgb? = null,
        bg: DeviceRgb? = null,
        align: TextAlignment = TextAlignment.CENTER
    ): Cell {
        val cell = Cell().setPadding(4f)
        val p = styledParagraph(text, fontSize, bold, color, align)
        cell.add(p)
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    private fun rtlCell(text: String, fontSize: Float = 9f, bold: Boolean = false, bg: DeviceRgb? = null): Cell {
        val cell = Cell().setPadding(4f).setTextAlignment(TextAlignment.RIGHT)
        // CRITICAL FIX: Shape Arabic to Presentation Forms before iText rendering.
        val p = Paragraph().setFontSize(fontSize)
        val font = if (bold) arabicBoldFont() else arabicFont()
        val shapedText = ArabicShaper.shapeIfArabic(text)
        val textRun = com.itextpdf.layout.element.Text(shapedText).setFont(font).setFontSize(fontSize)
        if (bold) textRun.setBold()
        p.add(textRun)
        if (isArabic(text)) {
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        cell.add(p)
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    private fun headerCell(text: String, colSpan: Int = 1): Cell {
        val cell = Cell(colSpan, 1)
            .setPadding(6f)
            .setBackgroundColor(HEADER_BG)
            .setTextAlignment(TextAlignment.CENTER)
        // CRITICAL FIX: Shape Arabic to Presentation Forms before iText rendering.
        val shapedText = ArabicShaper.shapeIfArabic(text)
        val p = Paragraph().setFontSize(9f)
        val textRun = com.itextpdf.layout.element.Text(shapedText)
            .setFont(arabicBoldFont())
            .setFontSize(9f)
            .setBold()
            .setFontColor(WHITE)
        p.add(textRun)
        if (isArabic(text)) {
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        cell.add(p)
        return cell
    }

    // ==================== Document Structure ====================
    private fun createDocument(outputPath: String): Triple<PdfDocument, Document, PdfFont> {
        val writer = PdfWriter(FileOutputStream(outputPath))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(40f, 40f, 40f, 40f)
        return Triple(pdf, document, arabicFont())
    }

    private fun addReportHeader(document: Document, titleAr: String, titleEn: String, subtitle: String, font: PdfFont) {
        // App name - use single Text run with proper font
        val appNameText = ArabicShaper.shapeIfArabic(context.getString(R.string.app_name))
        val appName = Paragraph()
            .setTextAlignment(TextAlignment.CENTER)
        val appNameRun = com.itextpdf.layout.element.Text(appNameText)
            .setFont(if (isEnglish) helveticaFont() else arabicBoldFont())
            .setFontSize(22f)
            .setBold()
            .setFontColor(PRIMARY)
        appName.add(appNameRun)
        if (!isEnglish) appName.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        document.add(appName)

        // Subtitle line
        val subLineRaw = if (isEnglish) "Civil EG - Advanced Structural Design" else "Civil EG - التصميم الإنشائي المتقدم"
        val subLine = ArabicShaper.shapeIfArabic(subLineRaw)
        val subPara = Paragraph().setTextAlignment(TextAlignment.CENTER)
        val subRun = com.itextpdf.layout.element.Text(subLine)
            .setFont(if (isEnglish) helveticaFont() else arabicFont())
            .setFontSize(10f)
            .setFontColor(ColorConstants.GRAY)
        subPara.add(subRun)
        if (!isEnglish) subPara.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        document.add(subPara)

        document.add(LineSeparator(SolidLine(2f)).setMarginTop(5f).setMarginBottom(10f))

        // Report title - show only the appropriate language
        val titleText = t(titleAr, titleEn)
        val titlePara = styledParagraph(titleText, 16f, true, PRIMARY, TextAlignment.CENTER)
        document.add(titlePara)

        // Subtitle (passed-in)
        val subtitlePara = Paragraph().setTextAlignment(TextAlignment.CENTER)
        val shapedSubtitle = ArabicShaper.shapeIfArabic(subtitle)
        val subtitleRun = com.itextpdf.layout.element.Text(shapedSubtitle)
            .setFont(if (isEnglish) helveticaFont() else arabicFont())
            .setFontSize(10f)
            .setFontColor(SECONDARY)
        subtitlePara.add(subtitleRun)
        if (!isEnglish) subtitlePara.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        document.add(subtitlePara)

        // Date
        val dateLocale = if (isEnglish) Locale.US else Locale("ar")
        val dateStr = SimpleDateFormat("yyyy/MM/dd  HH:mm", dateLocale).format(Date())
        val datePara = Paragraph().setTextAlignment(TextAlignment.CENTER)
        val dateRun = com.itextpdf.layout.element.Text(dateStr)
            .setFont(if (isEnglish) helveticaFont() else arabicFont())
            .setFontSize(9f)
            .setFontColor(ColorConstants.GRAY)
        datePara.add(dateRun)
        if (!isEnglish) datePara.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        document.add(datePara)

        document.add(LineSeparator(SolidLine(0.5f)).setMarginTop(5f).setMarginBottom(10f))
    }

    private fun addStatusBanner(document: Document, isSafe: Boolean, details: String = "") {
        val statusText = if (isSafe) {
            if (details.isNotEmpty()) "${t("الحالة: آمن - مطابق للكود", "STATUS: SAFE - Code Compliant")}\n$details"
            else "${t("الحالة: آمن - مطابق للكود ✔", "STATUS: SAFE ✔")}"
        } else {
            if (details.isNotEmpty()) "${t("الحالة: غير آمن - يحتاج مراجعة", "STATUS: UNSAFE - Design Review Required")}\n$details"
            else "${t("الحالة: غير آمن - يحتاج مراجعة ✘", "STATUS: UNSAFE ✘")}"
        }
        val p = styledParagraph(statusText, 11f, true,
            if (isSafe) SUCCESS else ERROR,
            TextAlignment.CENTER
        )
        p.setPadding(8f)
        p.setBorder(com.itextpdf.layout.borders.SolidBorder(if (isSafe) SUCCESS else ERROR, 1.5f))
        document.add(p)
        document.add(Paragraph(" "))
    }

    private fun addSectionTitle(document: Document, titleAr: String, titleEn: String) {
        val text = t(titleAr, titleEn)
        val p = styledParagraph(text, 12f, true, PRIMARY, TextAlignment.CENTER)
        document.add(p)
        document.add(LineSeparator(SolidLine(0.5f)).setMarginBottom(8f))
    }

    private fun addInfoTable(document: Document, rows: List<Pair<String, String>>, font: PdfFont) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(45f, 55f))).useAllAvailableWidth()
        var rowIndex = 0
        for ((label, value) in rows) {
            val bg = if (rowIndex % 2 == 0) null else ROW_ALT
            val labelAr = isArabic(label)
            val valueAr = isArabic(value)

            val labelCell = Cell().setPadding(4f)
            if (labelAr) labelCell.setTextAlignment(TextAlignment.RIGHT)
            val lp = Paragraph().setFontSize(9f)
            // CRITICAL FIX: Shape Arabic to Presentation Forms before iText rendering.
            val shapedLabel = ArabicShaper.shapeIfArabic(label)
            val lRun = com.itextpdf.layout.element.Text(shapedLabel).setFont(arabicBoldFont()).setFontSize(9f).setBold()
            lp.add(lRun)
            if (labelAr) lp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            labelCell.add(lp)
            bg?.let { labelCell.setBackgroundColor(it) }
            table.addCell(labelCell)

            val valueCell = Cell().setPadding(4f)
            if (valueAr) valueCell.setTextAlignment(TextAlignment.RIGHT)
            val vp = Paragraph().setFontSize(9f)
            // CRITICAL FIX: Shape Arabic to Presentation Forms before iText rendering.
            val shapedValue = ArabicShaper.shapeIfArabic(value)
            val vRun = com.itextpdf.layout.element.Text(shapedValue).setFont(arabicFont()).setFontSize(9f)
            vp.add(vRun)
            if (valueAr) vp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            valueCell.add(vp)
            bg?.let { valueCell.setBackgroundColor(it) }
            table.addCell(valueCell)

            rowIndex++
        }
        document.add(table)
        document.add(Paragraph(" "))
    }

    private fun addDrawingSection(document: Document, bitmap: Bitmap?, title: String) {
        // Always show the drawing section title so users can see a drawing was intended
        document.add(Paragraph(" "))
        addSectionTitle(document, t("الرسم الهندسي", "Engineering Drawing"), title)
        if (bitmap == null) {
            // Show placeholder instead of silently skipping
            document.add(styledParagraph(
                t("[الرسم غير متاح - تأكد من اكتمال بيانات التصميم]", "[Drawing not available - ensure design data is complete]"),
                9f, color = ColorConstants.GRAY as DeviceRgb,
                alignment = TextAlignment.CENTER
            ))
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
                t("[خطأ في عرض الرسم: ${e.message}]", "[Drawing render error: ${e.message}]"),
                9f, color = ColorConstants.GRAY as DeviceRgb,
                alignment = TextAlignment.CENTER
            ))
        }
        document.add(Paragraph(" "))
    }

    private fun addFooter(document: Document) {
        document.add(Paragraph(" "))
        document.add(LineSeparator(SolidLine(0.5f)).setMarginTop(10f).setMarginBottom(5f))
        val footerText = if (isEnglish) {
            "Generated by Civil EG Pro - ${SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())}"
        } else {
            "تم إنشاء هذا التقرير تلقائياً بواسطة تطبيق Civil EG Pro\nGenerated by Civil EG Pro - ${SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())}"
        }
        val footer = styledParagraph(footerText, 8f, color = ColorConstants.GRAY as DeviceRgb, alignment = TextAlignment.CENTER)
        document.add(footer)
        // CRITICAL FIX (Bug #1): Use styledParagraph instead of raw Paragraph(text)
        // Raw Paragraph(text) bypasses ArabicShaper AND uses default Helvetica — Arabic
        // letters would render as disconnected squares. Mixed Latin/Arabic would also
        // fail because Arabic font lacks Latin letters.
        document.add(styledParagraph(
            t("هذا التقرير لأغراض مرجعية فقط - يجب مراجعته بواسطة مهندس مؤهل قبل التنفيذ",
                "This report is for reference only - must be reviewed by a qualified engineer before execution."),
            7f, color = ColorConstants.LIGHT_GRAY as DeviceRgb, alignment = TextAlignment.CENTER
        ))
    }

    // ==================== Method 1: Beam Report ====================
    fun exportBeamReport(
        projectName: String,
        designCode: DesignCode,
        beamType: BeamType,
        inputs: BeamInputs,
        result: AdvancedBeamResult,
        inventoryAnalysis: InventoryAnalysisResult?,
        momentShearDiagrams: MomentShearDiagrams,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        return try {
            val (_, document, font) = createDocument(outputPath)

            addReportHeader(document,
                t("تقرير تصميم كمرات خرسانية", "Reinforced Concrete Beam Design Report"),
                "Reinforced Concrete Beam Design Report",
                "${designCode.version} | ${beamType::class.simpleName}",
                font
            )

            addStatusBanner(document, result.flexureResult.isSafe,
                "Utilization: ${(result.flexureResult.utilizationRatio * 100).format(1)}%"
            )

            // Design Parameters
            addSectionTitle(document, t("معاملات التصميم", "Design Parameters"), "Design Parameters")
            addInfoTable(document, listOf(
                t("اسم المشروع", "Project Name") to projectName,
                t("الكود التصميمي", "Design Code") to designCode.version,
                t("نوع الكمرة", "Beam Type") to (beamType::class.simpleName ?: "N/A"),
                t("العرض", "Width") to "${inputs.width.format(0)} mm",
                t("الارتفاع الكلي", "Total Depth") to "${inputs.totalDepth.format(0)} mm",
                t("العمق الفعال", "Effective Depth") to "${inputs.effectiveDepth.format(0)} mm",
                t("البحر", "Span") to "${inputs.span.format(2)} m",
                t("الحمل الميت", "Dead Load") to "${inputs.deadLoad.format(2)} kN/m\u00B2",
                t("الحمل الحي", "Live Load") to "${inputs.liveLoad.format(2)} kN/m\u00B2",
                t("مقاومة الخرسانة", "Concrete Strength") to "f'c = ${inputs.fcu.format(0)} MPa",
                t("مقاومة الحديد", "Steel Strength") to "fy = ${inputs.fy.format(0)} MPa",
                t("العزم التصميمي", "Design Moment") to "${inputs.designMoment.format(2)} kN.m",
                t("القص التصميمي", "Design Shear") to "${inputs.designShear.format(2)} kN"
            ), font)

            // Flexure Design
            addSectionTitle(document, t("نتائج التصميم - الانحناء", "Flexure Design Results"), "Flexure Design Results")
            val fr = result.flexureResult
            addInfoTable(document, listOf(
                t("مساحة الحديد المطلوبة", "Required Steel Area") to "${fr.astRequired.format(1)} mm\u00B2",
                t("مساحة الحديد المزود", "Provided Steel Area") to "${fr.astProvided.format(1)} mm\u00B2",
                t("قطر الحديد", "Bar Diameter") to "\u00D8${fr.barDiameter.toInt()} mm",
                t("عدد الحديد", "Number of Bars") to "${fr.numberOfBars} \u03C6${fr.barDiameter.toInt()}",
                t("قطر الكانات", "Ties Diameter") to "\u00D8${fr.tiesDiameter.toInt()} mm",
                t("مسافة الكانات", "Ties Spacing") to "@ ${fr.tiesSpacing.toInt()} mm",
                t("نسبة الاستخدام", "Utilization Ratio") to "${(fr.utilizationRatio * 100).format(1)}%",
                t("الحالة", "Status") to if (fr.isSafe) t("آمن ✔", "Safe ✔") else t("غير آمن ✘", "Unsafe ✘")
            ), font)

            // Shear Design
            addSectionTitle(document, t("نتائج التصميم - القص", "Shear Design Results"), "Shear Design Results")
            val sr = result.shearResult
            addInfoTable(document, listOf(
                t("الحالة", "Status") to if (sr.isSafe) t("آمن ✔", "Safe ✔") else t("غير آمن ✘", "Unsafe ✘"),
                t("قطر الكانات", "Stirrup Diameter") to "\u00D8${sr.stirrupDiameter.toInt()} mm",
                t("مسافة الكانات", "Stirrup Spacing") to "@ ${sr.stirrupSpacing.toInt()} mm",
                t("نسبة الاستخدام", "Utilization Ratio") to "${(sr.utilizationRatio * 100).format(1)}%"
            ), font)

            // Deflection Check
            addSectionTitle(document, t("التحقق من الهبوط", "Deflection Check"), "Deflection Check")
            val dc = result.deflectionCheck
            addInfoTable(document, listOf(
                t("الهبوط المحسوب", "Calculated Deflection") to "${dc.calculatedDeflection.format(2)} mm",
                t("الهبوط المسموح", "Allowable Deflection") to "${dc.allowableDeflection.format(2)} mm",
                t("الحالة", "Status") to if (dc.isSafe) t("مطابق ✔", "OK ✔") else t("غير مطابق ✘", "NG ✘")
            ), font)

            // Warnings & Notes
            if (result.warnings.isNotEmpty() || result.codeNotes.isNotEmpty()) {
                addSectionTitle(document, t("ملاحظات وتحذيرات", "Warnings & Code Notes"), "Warnings & Code Notes")
                (result.warnings + result.codeNotes).forEach { note ->
                    val p = styledParagraph("\u2022 $note", 9f, color = if (note.contains("تحذير", true) || note.contains("warning", true)) WARNING else SECONDARY)
                    document.add(p)
                }
            }

            addDrawingSection(document, drawingBitmap, "Beam Reinforcement Detail")
            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Method 2: Column Report ====================
    fun exportColumnReport(
        projectName: String,
        designCode: DesignCode,
        columnType: ColumnType,
        inputs: ColumnInputs,
        result: AdvancedColumnResult,
        inventoryAnalysis: InventoryAnalysisResult?,
        alternatives: List<ColumnAlternative>,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        return try {
            val (_, document, font) = createDocument(outputPath)

            addReportHeader(document,
                t("تقرير تصميم أعمدة خرسانية", "Reinforced Concrete Column Design Report"),
                "Reinforced Concrete Column Design Report",
                "${designCode.version} | ${columnType::class.simpleName}",
                font
            )

            addStatusBanner(document, result.reinforcementResult.isSafe,
                "Capacity: ${result.axialCapacity.format(1)} kN | Applied: ${inputs.axialLoad.format(1)} kN"
            )

            // Design Parameters
            addSectionTitle(document, t("معاملات التصميم", "Design Parameters"), "Design Parameters")
            val colDims = when (columnType) {
                is ColumnType.Rectangular -> "${columnType.width.format(0)} x ${columnType.depth.format(0)} mm"
                is ColumnType.Circular -> "\u00D8${columnType.diameter.format(0)} mm"
                else -> columnType::class.simpleName ?: "N/A"
            }
            addInfoTable(document, listOf(
                t("اسم المشروع", "Project Name") to projectName,
                t("الكود التصميمي", "Design Code") to designCode.version,
                t("نوع العمود", "Column Type") to colDims,
                t("الارتفاع غير مسنود", "Unsupported Height") to "${inputs.unsupportedLength.format(2)} m",
                t("الحمل المحوري", "Axial Load") to "${inputs.axialLoad.format(1)} kN",
                t("العزم حول X", "Moment about X") to "${inputs.momentX.format(2)} kN.m",
                t("العزم حول Y", "Moment about Y") to "${inputs.momentY.format(2)} kN.m",
                t("مقاومة الخرسانة", "Concrete Strength") to "f'c = ${inputs.fcu.format(0)} MPa",
                t("مقاومة الحديد", "Steel Strength") to "fy = ${inputs.fy.format(0)} MPa"
            ), font)

            // Reinforcement Design
            addSectionTitle(document, t("نتائج التصميم", "Design Results"), "Design Results")
            val rr = result.reinforcementResult
            addInfoTable(document, listOf(
                t("القدرة المحورية", "Axial Capacity") to "${result.axialCapacity.format(1)} kN",
                t("قدرة الانحناء X", "Flexural Capacity X") to "${result.momentCapacityX.format(2)} kN.m",
                t("قدرة الانحناء Y", "Flexural Capacity Y") to "${result.momentCapacityY.format(2)} kN.m",
                t("نسبة النحافة", "Slenderness Ratio") to "${result.slendernessRatio.format(1)}",
                t("عمود رفيع", "Slender Column") to if (result.isSlender) t("نعم", "Yes") else t("لا", "No"),
                t("الطول الفعال", "Effective Length") to "${result.effectiveLength.format(0)} mm",
                t("مساحة الحديد المطلوبة", "Required Steel Area") to "${rr.astRequired.format(1)} mm\u00B2",
                t("مساحة الحديد المزود", "Provided Steel Area") to "${rr.astProvided.format(1)} mm\u00B2",
                t("قطر الحديد الرئيسي", "Main Bar Diameter") to "\u00D8${rr.barDiameter.toInt()} mm",
                t("عدد الحديد", "Number of Bars") to "${rr.numberOfBars} \u03C6${rr.barDiameter.toInt()}",
                t("قطر الكانات", "Ties Diameter") to "\u00D8${rr.tiesDiameter.toInt()} mm",
                t("مسافة الكانات", "Ties Spacing") to "@ ${rr.tiesSpacing.toInt()} mm",
                t("وزن الحديد/م.ط", "Steel Weight/m") to "${result.steelWeightPerMeter.format(2)} kg/m",
                t("حجم الخرسانة/م.ط", "Concrete Vol./m") to "${result.concreteVolumePerMeter.format(4)} m\u00B3/m"
            ), font)

            // Alternatives table
            if (alternatives.isNotEmpty()) {
                addSectionTitle(document, t("بدائل التسليح", "Reinforcement Alternatives"), "Reinforcement Alternatives")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(t("قطر الحديد", "Bar Dia.")))
                table.addHeaderCell(headerCell(t("عدد الحديد", "No. of Bars")))
                table.addHeaderCell(headerCell(t("المساحة", "Area (mm\u00B2)")))
                table.addHeaderCell(headerCell(t("الحالة", "Status")))
                alternatives.forEachIndexed { i, alt ->
                    val bg = if (i % 2 == 0) null else ROW_ALT
                    table.addCell(tableCell("\u00D8${alt.barDiameter.toInt()}", bg = bg))
                    table.addCell(tableCell("${alt.numberOfBars}", bg = bg))
                    table.addCell(tableCell(alt.totalArea.format(1), bg = bg))
                    table.addCell(tableCell(
                        if (alt.isSafe) t("آمن", "Safe") + " \u2714" else t("غير آمن", "Unsafe") + " \u2718",
                        color = if (alt.isSafe) SUCCESS else ERROR, bg = bg
                    ))
                }
                document.add(table)
                document.add(Paragraph(" "))
            }

            // Warnings & Notes
            if (result.warnings.isNotEmpty() || result.codeNotes.isNotEmpty()) {
                addSectionTitle(document, t("ملاحظات", "Code Notes"), "Code Notes")
                (result.warnings + result.codeNotes).forEach { note ->
                    document.add(styledParagraph("\u2022 $note", 9f, color = SECONDARY))
                }
            }

            addDrawingSection(document, drawingBitmap, "Column Reinforcement Detail")
            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Method 3: Slab Report ====================
    fun exportSlabReport(
        projectName: String,
        designCode: DesignCode,
        slabType: SlabType,
        inputs: SlabInputs,
        result: AdvancedSlabResult,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        return try {
            val (pdfDoc, document, font) = createDocument(outputPath)

            // Determine slab type name
            val slabTypeNameAr = when (slabType) {
                is SlabType.Solid -> "بلاطة صلبة"
                is SlabType.FlatPlate -> "بلاطة مسطحة"
                is SlabType.Hordi -> "بلاطة هوردي"
                is SlabType.Waffle -> "بلاطة مجازين"
                else -> slabType::class.simpleName ?: "N/A"
            }
            val slabTypeNameEn = when (slabType) {
                is SlabType.Solid -> "Solid Slab"
                is SlabType.FlatPlate -> "Flat Plate Slab"
                is SlabType.Hordi -> "Ribbed Slab (Hordi)"
                is SlabType.Waffle -> "Waffle Slab"
                else -> slabType::class.simpleName ?: "N/A"
            }

            addReportHeader(document,
                t("تقرير تصميم بلاطات خرسانية", "Reinforced Concrete Slab Design Report"),
                "Reinforced Concrete Slab Design Report",
                "${designCode.version} | ${t(slabTypeNameAr, slabTypeNameEn)}",
                font
            )

            addStatusBanner(document, result.flexureResult.isSafe,
                if (result.flexureResult.utilizationRatio > 0) "Utilization: ${(result.flexureResult.utilizationRatio * 100).format(1)}%" else ""
            )

            // ===== 1. Design Parameters =====
            addSectionTitle(document, t("معاملات التصميم", "Design Parameters"), "Design Parameters")
            
            // Slab geometry info
            val ratio = if (inputs.longSpan > 0) inputs.shortSpan / inputs.longSpan else 1.0
            val isOneWay = ratio < 0.5
            val slabDirection = t("اتجاه واحد", "One-Way")
            val slabDirectionBi = if (isOneWay) slabDirection else t("اتجاهين", "Two-Way")
            
            addInfoTable(document, listOf(
                t("اسم المشروع", "Project Name") to projectName,
                t("الكود التصميمي", "Design Code") to designCode.version,
                t("نوع البلاطة", "Slab Type") to t(slabTypeNameAr, slabTypeNameEn),
                t("اتجاه الانحناء", "Bending Direction") to slabDirectionBi,
                t("نسبة البحور Lx/Ly", "Span Ratio Lx/Ly") to "${ratio.format(3)}",
                t("السمك الفعلي", "Actual Thickness") to "${inputs.thickness.format(0)} mm",
                t("السمك الأدنى المطلوب", "Min. Required Thickness") to "${result.flexureResult.minThickness.format(0)} mm",
                t("البحر القصير Lx", "Short Span Lx") to "${inputs.shortSpan.format(2)} m",
                t("البحر الطويل Ly", "Long Span Ly") to "${inputs.longSpan.format(2)} m",
                t("الحمل الميت DL", "Dead Load DL") to "${inputs.deadLoad.format(2)} kN/m\u00B2",
                t("الحمل الحي LL", "Live Load LL") to "${inputs.liveLoad.format(2)} kN/m\u00B2",
                t("مقاومة الخرسانة", "Concrete Strength") to "f'cu = ${inputs.fcu.format(0)} MPa",
                t("مقاومة الحديد", "Steel Strength") to "fy = ${inputs.fy.format(0)} MPa"
            ), font)

            // ===== 2. Load Calculations =====
            addSectionTitle(document, t("حسابات الأحمال", "Load Calculations"), "Load Calculations")
            val wu = 1.4 * inputs.deadLoad + 1.6 * inputs.liveLoad
            val spanRatioStr = if (isOneWay) {
                "Lx = ${inputs.shortSpan.format(2)} m (One-Way)"
            } else {
                "Lx/Ly = ${ratio.format(2)} (Two-Way, alpha/beta factors applied)"
            }
            addInfoTable(document, listOf(
                t("معامل التحميل الميت", "Dead Load Factor") to "1.4 (per ${designCode.version})",
                t("معامل التحميل الحي", "Live Load Factor") to "1.6 (per ${designCode.version})",
                t("الحمل التصميمي Wu", "Design Load Wu") to "${wu.format(2)} kN/m\u00B2",
                t("المعادلة", "Equation") to "Wu = 1.4*DL + 1.6*LL = 1.4*${inputs.deadLoad.format(1)} + 1.6*${inputs.liveLoad.format(1)} = ${wu.format(2)} kN/m\u00B2",
                t("طريقة الحساب", "Method") to spanRatioStr
            ), font)

            // ===== 3. Flexure Design Results =====
            addSectionTitle(document, t("نتائج التصميم - الانحناء", "Flexure Design Results"), "Flexure Design Results")
            val fd = result.flexureResult
            addInfoTable(document, listOf(
                t("مساحة الحديد المطلوبة As_req", "Required Steel As_req") to "${fd.requiredReinforcement.format(1)} mm\u00B2/m",
                t("مساحة الحديد المزود As_prov", "Provided Steel As_prov") to "${fd.providedReinforcement.format(1)} mm\u00B2/m",
                t("نسبة الاستخدام", "Utilization Ratio") to "${(fd.utilizationRatio * 100).format(1)}%",
                t("الحالة", "Status") to if (fd.isSafe) t("آمن - مطابق للكود ✔", "Safe - Code Compliant ✔") else t("غير آمن - يحتاج مراجعة ✘", "Unsafe - Review Required ✘"),
                t("حجم الخرسانة", "Concrete Volume") to "${result.concreteVolume.format(3)} m\u00B3",
                t("مساحة القالب", "Formwork Area") to "${result.formworkArea.format(2)} m\u00B2"
            ), font)

            // ===== 4. Reinforcement Details =====
            addSectionTitle(document, t("تفاصيل التسليح", "Reinforcement Details"), "Reinforcement Details")
            val rl = result.reinforcementLayout
            
            // Professional reinforcement schedule table
            val rebarTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 20f, 20f, 20f, 15f))).useAllAvailableWidth()
            rebarTable.addHeaderCell(headerCell(t("الوصف", "Description")))
            rebarTable.addHeaderCell(headerCell(t("القطر", "Dia.")))
            rebarTable.addHeaderCell(headerCell(t("المسافة", "Spacing")))
            rebarTable.addHeaderCell(headerCell(t("الاتجاه", "Direction")))
            rebarTable.addHeaderCell(headerCell(t("الطبقة", "Layer")))
            
            val bottomBars = rl.bottomBars
            val topBars = rl.topBars
            
            rebarTable.addCell(tableCell(t("حديد أساسي سفلي", "Main Bottom"), bg = ROW_ALT))
            rebarTable.addCell(tableCell("\u03C6${bottomBars.diameter.format(0)} mm", bg = ROW_ALT))
            rebarTable.addCell(tableCell("@ ${bottomBars.spacing.format(0)} mm", bg = ROW_ALT))
            rebarTable.addCell(tableCell(if (isOneWay) "Lx" else "Lx (Short)", bg = ROW_ALT))
            rebarTable.addCell(tableCell(t("سفلي", "Bottom"), bg = ROW_ALT))
            
            rebarTable.addCell(tableCell(t("حديد أساسي علوي", "Main Top")))
            rebarTable.addCell(tableCell("\u03C6${topBars.diameter.format(0)} mm"))
            rebarTable.addCell(tableCell("@ ${topBars.spacing.format(0)} mm"))
            rebarTable.addCell(tableCell(if (isOneWay) "Lx" else "Ly (Long)"))
            rebarTable.addCell(tableCell(t("علوي", "Top")))
            
            if (rl.distributionBars != null) {
                val dist = rl.distributionBars
                rebarTable.addCell(tableCell(t("حديد التوزيع", "Distribution"), bg = ROW_ALT))
                rebarTable.addCell(tableCell("\u03C6${dist.diameter.format(0)} mm", bg = ROW_ALT))
                rebarTable.addCell(tableCell("@ ${dist.spacing.format(0)} mm", bg = ROW_ALT))
                rebarTable.addCell(tableCell(if (isOneWay) "Ly (perp.)" else "Both", bg = ROW_ALT))
                rebarTable.addCell(tableCell(t("سفلي", "Bottom"), bg = ROW_ALT))
            }
            
            document.add(rebarTable)
            document.add(Paragraph(" "))

            // ===== 5. Shear & Punching Checks =====
            addSectionTitle(document, t("التحقق من القص والاختراق", "Shear & Punching Checks"), "Shear & Punching Checks")
            addInfoTable(document, listOf(
                t("القص - الحالة", "Shear - Status") to if (result.shearCheck.isSafe) t("آمن ✔", "Safe ✔") else t("غير آمن ✘", "Unsafe ✘"),
                t("القص - القوة المطبقة", "Shear - Applied Force") to "${result.shearCheck.appliedShear.format(1)} kN",
                t("القص - القدرة القصية", "Shear - Shear Capacity") to "${result.shearCheck.shearCapacity.format(1)} kN",
                t("القص - نسبة الاستخدام", "Shear - Utilization") to "${(result.shearCheck.utilizationRatio * 100).format(1)}%"
            ) + (result.punchingShearCheck?.let {
                listOf(
                    t("الاختراق - الحالة", "Punching - Status") to if (it.isSafe) t("آمن ✔", "Safe ✔") else t("غير آمن ✘", "Unsafe ✘"),
                    t("الاختراق - القوة المطبقة", "Punching - Applied Force") to "${it.appliedShear.format(1)} kN",
                    t("الاختراق - القدرة القصية", "Punching - Shear Capacity") to "${it.shearCapacity.format(1)} kN",
                    t("الاختراق - نسبة الاستخدام", "Punching - Utilization") to "${(it.utilizationRatio * 100).format(1)}%"
                )
            } ?: emptyList()), font)

            // ===== 6. Deflection Check =====
            if (result.deflectionCheck != null) {
                addSectionTitle(document, t("التحقق من الهبوط", "Deflection Check"), "Deflection Check")
                val dc = result.deflectionCheck
                addInfoTable(document, listOf(
                    t("الهبوط المحسوب", "Calculated Deflection") to "${dc.calculatedDeflection.format(2)} mm",
                    t("الهبوط المسموح", "Allowable Deflection") to "${dc.allowableDeflection.format(2)} mm",
                    t("النسبة", "Ratio") to "${(dc.ratio * 100).format(1)}%",
                    t("الحالة", "Status") to if (dc.isSafe) t("مطابق ✔", "OK ✔") else t("غير مطابق ✘", "NG ✘")
                ), font)
            }

            // ===== 7. Quantities =====
            addSectionTitle(document, t("الكميات والتكلفة", "Quantities & Cost Estimate"), "Quantities & Cost Estimate")
            addInfoTable(document, listOf(
                t("حجم الخرسانة", "Concrete Volume") to "${result.concreteVolume.format(3)} m\u00B3",
                t("مساحة القالب", "Formwork Area") to "${result.formworkArea.format(2)} m\u00B2"
            ), font)

            // ===== 8. Warnings & Code Notes =====
            if (result.warnings.isNotEmpty() || result.codeNotes.isNotEmpty()) {
                addSectionTitle(document, t("ملاحظات وتحذيرات", "Warnings & Code Notes"), "Warnings & Code Notes")
                (result.warnings + result.codeNotes).forEach { note ->
                    val isWarning = note.contains("تحذير", true) || note.contains("warning", true) || note.contains("خطر", true)
                    document.add(styledParagraph("\u2022 $note", 9f, color = if (isWarning) WARNING else SECONDARY))
                }
            }

            // ===== 9. Engineering Drawing =====
            addDrawingSection(document, drawingBitmap, "Slab Reinforcement Layout")
            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Method 4: Steel Report ====================
    fun exportSteelReport(
        projectName: String,
        designCode: DesignCode,
        sectionType: SteelSectionType,
        memberType: SteelMemberType,
        inputs: SteelInputs,
        result: SteelMemberResult,
        connectionDesign: ConnectionDesignResult?,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        return try {
            val (_, document, font) = createDocument(outputPath)

            addReportHeader(document,
                t("تقرير تصميم عضو فولاذي", "Steel Member Design Report"),
                "Steel Member Design Report",
                "${designCode.version} | ${memberType.name}",
                font
            )

            addStatusBanner(document, result.isSafe,
                "Section: ${sectionType.displayName} | Utilization: ${(result.utilizationRatio * 100).format(1)}%"
            )

            // Section Properties
            addSectionTitle(document, t("خصائص القطاع", "Section Properties"), "Section Properties")
            addInfoTable(document, listOf(
                t("نوع القطاع", "Section Type") to sectionType.displayName,
                t("نوع العضو", "Member Type") to memberType.name,
                t("المساحة", "Area") to "${(sectionType.getArea() / 100.0).format(2)} cm\u00B2",
                t("الوزن", "Weight") to "${result.weight.format(2)} kg/m",
                t("الطول غير مسنود", "Unbraced Length") to "${inputs.unbracedLength.format(0)} mm",
                t("الحمل المحوري", "Axial Load") to "${inputs.axialLoad.format(1)} kN",
                t("العزم", "Moment") to "${inputs.moment.format(2)} kN.m",
                t("القص", "Shear") to "${inputs.shear.format(1)} kN"
            ), font)

            // Capacity Checks
            addSectionTitle(document, t("نتائج التصميم", "Design Results"), "Design Results")
            addInfoTable(document, listOf(
                t("القدرة المحورية", "Axial Capacity") to "${result.axialCapacity.format(1)} kN",
                t("القدرة الانحنائية", "Flexural Capacity") to "${result.flexuralCapacity.format(2)} kN.m",
                t("القدرة القصية", "Shear Capacity") to "${result.shearCapacity.format(1)} kN",
                t("نسبة الاستخدام", "Utilization Ratio") to "${(result.utilizationRatio * 100).format(1)}%",
                t("التكلفة", "Cost") to "${result.cost.format(2)} EGP/m"
            ), font)

            // Connection Design
            if (connectionDesign != null) {
                addSectionTitle(document, t("تصميم الوصلات", "Connection Design"), "Connection Design")
                addInfoTable(document, listOf<Pair<String, String>>(
                    t("نوع الوصلة", "Connection Type") to (connectionDesign.connectionType::class.simpleName ?: "N/A"),
                    t("القدرة", "Capacity") to "${connectionDesign.capacity.format(1)} kN",
                    t("القوة المطبقة", "Applied Force") to "${connectionDesign.appliedForce.format(1)} kN",
                    t("نسبة الاستخدام", "Utilization Ratio") to "${(connectionDesign.utilizationRatio * 100).format(1)}%",
                    t("الحالة", "Status") to if (connectionDesign.isSafe) t("آمن ✔", "Safe ✔") else t("غير آمن ✘", "Unsafe ✘")
                ), font)
                if (connectionDesign.detailedCalculations.isNotEmpty()) {
                    document.add(styledParagraph(connectionDesign.detailedCalculations, 8f, color = SECONDARY))
                }
            }

            // Warnings
            if (result.warnings.isNotEmpty() || result.codeNotes.isNotEmpty()) {
                addSectionTitle(document, t("ملاحظات", "Notes"), "Notes")
                (result.warnings + result.codeNotes).forEach { note ->
                    document.add(styledParagraph("\u2022 $note", 9f, color = WARNING))
                }
            }

            addDrawingSection(document, drawingBitmap, "Steel Member Section")
            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Method 5: Footing Report ====================
    fun exportFootingReport(
        projectName: String,
        designCode: CalculatorEngine.DesignCode,
        result: CalculatorEngine.FootingResult,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        return try {
            val (_, document, font) = createDocument(outputPath)
            val codeStr = when (designCode) {
                CalculatorEngine.DesignCode.EGYPTIAN -> "ECP 203-2020"
                CalculatorEngine.DesignCode.ACI -> "ACI 318-19"
                CalculatorEngine.DesignCode.SAUDI -> "SBC 304-2018"
            }

            addReportHeader(document,
                t("تقرير تصميم أساسات", "Footing Design Report"),
                "Footing Design Report",
                "$codeStr | ${result.type.displayName}",
                font
            )

            addStatusBanner(document, result.isSafe,
                "Soil: ${result.soilPressure.format(1)} / ${result.allowablePressure.format(1)} kPa"
            )

            addSectionTitle(document, t("معاملات التصميم", "Design Parameters"), "Design Parameters")
            addInfoTable(document, listOf(
                t("اسم المشروع", "Project Name") to projectName,
                t("نوع الأساس", "Footing Type") to result.type.displayName,
                t("العرض", "Width") to "${result.width.format(0)} mm",
                t("الطول", "Length") to "${result.length.format(0)} mm",
                t("السمك", "Thickness") to "${result.thickness.format(0)} mm",
                t("ضغط التربة المسموح", "Allowable Soil Pressure") to "${result.allowablePressure.format(1)} kPa",
                t("ضغط التربة الفعلي", "Actual Soil Pressure") to "${result.soilPressure.format(1)} kPa"
            ), font)

            addSectionTitle(document, t("نتائج التسليح", "Reinforcement Results"), "Reinforcement Results")
            addInfoTable(document, listOf(
                t("حديد القاع X", "Bottom Steel X") to "${result.barsX} \u03C6${result.barDiameter} @ ${result.reinforcementBottom.spacing.toInt()}mm",
                t("حديد القاع Y", "Bottom Steel Y") to "${result.barsY} \u03C6${result.barDiameter} @ ${result.reinforcementBottom.spacing.toInt()}mm",
                t("حجم الخرسانة", "Concrete Volume") to "${result.concreteVolume.format(3)} m\u00B3",
                t("وزن الحديد", "Steel Weight") to "${result.steelWeight.format(1)} kg",
                t("التكلفة", "Cost") to "${result.cost.format(0)} EGP",
                t("نسبة الاستخدام", "Utilization Ratio") to "${(result.utilizationRatio * 100).format(1)}%"
            ), font)

            // Safety Checks
            if (result.safetyChecks.isNotEmpty()) {
                addSectionTitle(document, t("تحققات الأمان", "Safety Checks"), "Safety Checks")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(t("التحقق", "Check")))
                table.addHeaderCell(headerCell(t("القيمة", "Value")))
                table.addHeaderCell(headerCell(t("الحد", "Limit")))
                table.addHeaderCell(headerCell(t("النتيجة", "Result")))
                result.safetyChecks.forEachIndexed { i, check ->
                    val bg = if (i % 2 == 0) null else ROW_ALT
                    table.addCell(tableCell(check.name, bg = bg))
                    table.addCell(tableCell("${check.value.format(2)} ${check.unit}", bg = bg))
                    table.addCell(tableCell("${check.limit.format(2)} ${check.unit}", bg = bg))
                    table.addCell(tableCell(
                        if (check.isSafe) "PASS \u2714" else "FAIL \u2718",
                        color = if (check.isSafe) SUCCESS else ERROR, bg = bg
                    ))
                }
                document.add(table)
            }

            addDrawingSection(document, drawingBitmap, "Footing Reinforcement Detail")
            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Method 6: Tank Report ====================
    fun exportTankReport(
        projectName: String,
        designCode: CalculatorEngine.DesignCode,
        result: CalculatorEngine.TankResult,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        return try {
            val (_, document, font) = createDocument(outputPath)
            val codeStr = when (designCode) {
                CalculatorEngine.DesignCode.EGYPTIAN -> "ECP 203-2020"
                CalculatorEngine.DesignCode.ACI -> "ACI 318-19"
                CalculatorEngine.DesignCode.SAUDI -> "SBC 304-2018"
            }

            addReportHeader(document,
                t("تقرير تصميم خزان مياه", "Water Tank Design Report"),
                "Water Tank Design Report",
                "$codeStr | ${result.type.displayName}",
                font
            )

            addStatusBanner(document, result.isSafe)

            addSectionTitle(document, t("معاملات التصميم", "Design Parameters"), "Design Parameters")
            addInfoTable(document, listOf(
                t("اسم المشروع", "Project Name") to projectName,
                t("نوع الخزان", "Tank Type") to result.type.displayName,
                t("الطول", "Length") to "${result.length.format(2)} m",
                t("العرض", "Width") to "${result.width.format(2)} m",
                t("الارتفاع", "Height") to "${result.height.format(2)} m",
                t("سمك الحائط", "Wall Thickness") to "${result.wallThickness.format(0)} mm",
                t("سمك القاعدة", "Base Thickness") to "${result.baseThickness.format(0)} mm",
                t("مقاومة الخرسانة", "Concrete Strength") to "f'c = ${result.fcu.format(0)} MPa",
                t("مقاومة الحديد", "Steel Strength") to "fy = ${result.fy.format(0)} MPa",
                t("ضغط المياه", "Water Pressure") to "${result.waterPressure.format(1)} kN/m\u00B2"
            ), font)

            addSectionTitle(document, t("نتائج التسليح", "Reinforcement Results"), "Reinforcement Results")
            addInfoTable(document, listOf(
                t("تسليح الحائط", "Wall Reinforcement") to "${result.wallReinforcement.barString} @ ${result.wallReinforcement.spacing.toInt()}mm",
                t("تسليح القاعدة", "Base Reinforcement") to "${result.baseReinforcement.barString} @ ${result.baseReinforcement.spacing.toInt()}mm",
                t("حجم الخرسانة", "Concrete Volume") to "${result.concreteVolume.format(3)} m\u00B3",
                t("وزن الحديد", "Steel Weight") to "${result.steelWeight.format(1)} kg",
                t("التكلفة", "Cost") to "${result.cost.format(0)} EGP",
                t("السعة", "Capacity") to "${result.capacity.format(1)} m\u00B3"
            ), font)

            // Safety Checks
            if (result.safetyChecks.isNotEmpty()) {
                addSectionTitle(document, t("تحققات الأمان", "Safety Checks"), "Safety Checks")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(t("التحقق", "Check")))
                table.addHeaderCell(headerCell(t("القيمة", "Value")))
                table.addHeaderCell(headerCell(t("الحد", "Limit")))
                table.addHeaderCell(headerCell(t("النتيجة", "Result")))
                result.safetyChecks.forEachIndexed { i, check ->
                    val bg = if (i % 2 == 0) null else ROW_ALT
                    table.addCell(tableCell(check.name, bg = bg))
                    table.addCell(tableCell("${check.value.format(2)} ${check.unit}", bg = bg))
                    table.addCell(tableCell("${check.limit.format(2)} ${check.unit}", bg = bg))
                    table.addCell(tableCell(
                        if (check.isSafe) "PASS \u2714" else "FAIL \u2718",
                        color = if (check.isSafe) SUCCESS else ERROR, bg = bg
                    ))
                }
                document.add(table)
            }

            addDrawingSection(document, drawingBitmap, "Tank Reinforcement Detail")
            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Method 7: Stair Report ====================
    fun exportStairReport(
        projectName: String,
        designCode: CalculatorEngine.DesignCode,
        result: CalculatorEngine.StairResult,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        return try {
            val (_, document, font) = createDocument(outputPath)
            val codeStr = when (designCode) {
                CalculatorEngine.DesignCode.EGYPTIAN -> "ECP 203-2020"
                CalculatorEngine.DesignCode.ACI -> "ACI 318-19"
                CalculatorEngine.DesignCode.SAUDI -> "SBC 304-2018"
            }

            addReportHeader(document,
                t("تقرير تصميم سلم", "Staircase Design Report"),
                "Staircase Design Report",
                "$codeStr | ${result.type.displayName}",
                font
            )

            addStatusBanner(document, result.isSafe)

            addSectionTitle(document, t("معاملات التصميم", "Design Parameters"), "Design Parameters")
            addInfoTable(document, listOf(
                t("اسم المشروع", "Project Name") to projectName,
                t("نوع السلم", "Stair Type") to result.type.displayName,
                t("السمك", "Thickness") to "${result.thickness.format(0)} mm",
                t("البحر", "Span") to "${result.span.format(2)} m",
                t("العارضة", "Riser") to "${result.riser.format(0)} mm",
                t("الدرجة", "Tread") to "${result.tread.format(0)} mm",
                t("الحمل الموحد", "Factored Load") to "${result.wu.format(2)} kN/m\u00B2",
                t("العزم التصميمي", "Design Moment") to "${result.mu.format(2)} kN.m",
                t("مقاومة الخرسانة", "Concrete Strength") to "f'c = ${result.fcu.format(0)} MPa",
                t("مقاومة الحديد", "Steel Strength") to "fy = ${result.fy.format(0)} MPa"
            ), font)

            addSectionTitle(document, t("نتائج التسليح", "Reinforcement Results"), "Reinforcement Results")
            addInfoTable(document, listOf(
                t("التسليح الرئيسي", "Main Reinforcement") to "${result.reinforcement.barString} @ ${result.reinforcement.spacing.toInt()}mm",
                t("تسليح التوزيع", "Distribution Reinforcement") to "${result.distributionReinforcement.numBars}\u03C6${result.distributionReinforcement.diameter} @ ${result.distributionReinforcement.spacing}mm",
                t("حجم الخرسانة", "Concrete Volume") to "${result.concreteVolume.format(3)} m\u00B3",
                t("وزن الحديد", "Steel Weight") to "${result.steelWeight.format(1)} kg",
                t("التكلفة", "Cost") to "${result.cost.format(0)} EGP"
            ), font)

            // Safety Checks
            if (result.safetyChecks.isNotEmpty()) {
                addSectionTitle(document, t("تحققات الأمان", "Safety Checks"), "Safety Checks")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(t("التحقق", "Check")))
                table.addHeaderCell(headerCell(t("القيمة", "Value")))
                table.addHeaderCell(headerCell(t("الحد", "Limit")))
                table.addHeaderCell(headerCell(t("النتيجة", "Result")))
                result.safetyChecks.forEachIndexed { i, check ->
                    val bg = if (i % 2 == 0) null else ROW_ALT
                    table.addCell(tableCell(check.name, bg = bg))
                    table.addCell(tableCell("${check.value.format(2)} ${check.unit}", bg = bg))
                    table.addCell(tableCell("${check.limit.format(2)} ${check.unit}", bg = bg))
                    table.addCell(tableCell(
                        if (check.isSafe) "PASS \u2714" else "FAIL \u2718",
                        color = if (check.isSafe) SUCCESS else ERROR, bg = bg
                    ))
                }
                document.add(table)
            }

            addDrawingSection(document, drawingBitmap, "Stair Reinforcement Detail")
            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Method 8: Retaining Wall Report ====================
    fun exportRetainingWallReport(
        projectName: String,
        designCode: CalculatorEngine.DesignCode,
        result: CalculatorEngine.RetainingWallResult,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        return try {
            val (_, document, font) = createDocument(outputPath)
            val codeStr = when (designCode) {
                CalculatorEngine.DesignCode.EGYPTIAN -> "ECP 203-2020"
                CalculatorEngine.DesignCode.ACI -> "ACI 318-19"
                CalculatorEngine.DesignCode.SAUDI -> "SBC 304-2018"
            }

            addReportHeader(document,
                t("تقرير تصميم حائط ساند", "Retaining Wall Design Report"),
                "Retaining Wall Design Report",
                codeStr,
                font
            )

            addStatusBanner(document, result.isSafe)

            addSectionTitle(document, t("معاملات التصميم", "Design Parameters"), "Design Parameters")
            addInfoTable(document, listOf(
                t("اسم المشروع", "Project Name") to projectName,
                t("ارتفاع الحائط", "Wall Height") to "${result.height.format(2)} m",
                t("سمك الجذع", "Stem Thickness") to "${result.stemThickness.format(0)} mm",
                t("عرض القاعدة", "Base Width") to "${result.baseWidth.format(0)} mm",
                t("كثافة التربة", "Soil Density") to "${result.soilDensity.format(1)} kN/m\u00B3",
                t("زاوية الاحتكاك الداخلي", "Internal Friction Angle") to "${Math.toDegrees(Math.asin((1.0 - result.ka.toDouble()) / (1.0 + result.ka.toDouble()))).format(1)}\u00B0",
                t("معامل الضغط النشط", "Active Pressure Coeff.") to "Ka = ${result.ka.format(3)}",
                t("قوة التربة النشطة", "Active Earth Force") to "${result.pa.format(1)} kN/m",
                t("مقاومة الخرسانة", "Concrete Strength") to "f'c = ${result.fcu.format(0)} MPa",
                t("مقاومة الحديد", "Steel Strength") to "fy = ${result.fy.format(0)} MPa"
            ), font)

            // Stability Checks
            addSectionTitle(document, t("تحققات الاستقرار", "Stability Checks"), "Stability Checks")
            addInfoTable(document, listOf(
                t("معامل الأمان ضد الانقلاب", "F.S. Overturning") to "F.S = ${result.factorOfSafetyOverturning.format(2)} (Min: 2.0)",
                t("معامل الأمان ضد الانزلاق", "F.S. Sliding") to "F.S = ${result.factorOfSafetySliding.format(2)} (Min: 1.5)"
            ), font)

            // Reinforcement
            addSectionTitle(document, t("نتائج التسليح", "Reinforcement Results"), "Reinforcement Results")
            addInfoTable(document, listOf(
                t("تسليح الجذع", "Stem Reinforcement") to "${result.stemReinforcement.numBars}\u03C6${result.stemReinforcement.diameter} @ ${result.stemReinforcement.spacing}mm",
                t("تسليح القاعدة", "Base Reinforcement") to "${result.baseReinforcement.barString} @ ${result.baseReinforcement.spacing.toInt()}mm",
                t("حجم الخرسانة", "Concrete Volume") to "${result.concreteVolume.format(3)} m\u00B3",
                t("وزن الحديد", "Steel Weight") to "${result.steelWeight.format(1)} kg",
                t("التكلفة", "Cost") to "${result.cost.format(0)} EGP"
            ), font)

            // Safety Checks
            if (result.safetyChecks.isNotEmpty()) {
                addSectionTitle(document, t("تحققات الأمان", "Safety Checks"), "Safety Checks")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(t("التحقق", "Check")))
                table.addHeaderCell(headerCell(t("القيمة", "Value")))
                table.addHeaderCell(headerCell(t("الحد", "Limit")))
                table.addHeaderCell(headerCell(t("النتيجة", "Result")))
                result.safetyChecks.forEachIndexed { i, check ->
                    val bg = if (i % 2 == 0) null else ROW_ALT
                    table.addCell(tableCell(check.name, bg = bg))
                    table.addCell(tableCell("${check.value.format(2)} ${check.unit}", bg = bg))
                    table.addCell(tableCell("${check.limit.format(2)} ${check.unit}", bg = bg))
                    table.addCell(tableCell(
                        if (check.isSafe) "PASS \u2714" else "FAIL \u2718",
                        color = if (check.isSafe) SUCCESS else ERROR, bg = bg
                    ))
                }
                document.add(table)
            }

            addDrawingSection(document, drawingBitmap, "Retaining Wall Section")
            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Generic Report (Map-based) ====================
    //
    // ********************************************************************************
    // CRITICAL FIX (2026-07-27 v4): Migration away from NativePdfExporter
    // ********************************************************************************
    // ROOT CAUSE of "ALL pages crash on PDF export except Frame Analysis":
    // NativePdfExporter uses Android's native android.graphics.pdf.PdfDocument +
    // Canvas API. Despite extensive defensive code (try/catch Throwable, dimension
    // validation, NaN guards), it still triggers NATIVE crashes (SIGSEGV in Skia)
    // on certain devices/Android versions when:
    //   - Drawing bitmap via Canvas.drawBitmap with scaled RectF
    //   - StaticLayout.draw() on a PdfDocument canvas
    //   - Typeface creation from assets on a background thread
    //
    // Native crashes CANNOT be caught by Java try/catch — they kill the process.
    //
    // FrameAnalysisPdfExporter uses iText 8 (PdfWriter/PdfDocument/Document) and
    // does NOT crash. ComprehensivePdfExporter also uses iText 8 — same safe path.
    //
    // SOLUTION: Add this generic Map-based export method that all 8 ViewModels
    // (Beam/Column/Slab/Footing/Tank/RetainingWall/Stair/Steel) can call instead
    // of NativePdfExporter. This method uses iText 8 exclusively — no native
    // Canvas/PdfDocument API — so it cannot trigger native crashes.
    //
    // The method accepts the SAME parameter shape as NativePdfExporter.generateReport
    // so ViewModels need minimal changes (just swap the exporter class).
    // ********************************************************************************

    data class GenericSafetyCheck(
        val name: String,
        val calculated: Double,
        val limit: Double,
        val unit: String,
        val passed: Boolean
    )

    /**
     * Generic PDF report generator that accepts Map-based inputs (instead of
     * domain-specific types). This is the SAFE replacement for NativePdfExporter.
     *
     * Uses iText 8 exclusively — no Android-native PdfDocument/Canvas — so it
     * cannot trigger native Skia crashes.
     *
     * Arabic text is properly shaped via PdfTextSegmenter.buildMixedParagraph()
     * which converts Arabic base letters to Presentation Forms (FE70-FEFF) before
     * passing to iText. This produces correctly connected Arabic letters without
     * needing the commercial pdfCalligraph module.
     *
     * @param titleAr Arabic title (shown when locale=ar)
     * @param titleEn English title (shown when locale=en, also used as fallback)
     * @param subtitle Subtitle line under the title
     * @param designType Design type chip text (e.g., "Beam (Simply Supported)")
     * @param inputs Map of parameter name → value (design inputs)
     * @param results Map of parameter name → value (design results)
     * @param safetyChecks List of safety verification rows
     * @param isSafe Overall safety status (drives the status banner color)
     * @param drawingBitmap Optional drawing bitmap to embed
     * @param outputPath Absolute path where the PDF should be written
     * @return The generated File, or null on failure
     */
    fun exportGenericReport(
        titleAr: String,
        titleEn: String,
        subtitle: String,
        designType: String,
        inputs: Map<String, String>,
        results: Map<String, String>,
        safetyChecks: List<GenericSafetyCheck>,
        isSafe: Boolean,
        drawingBitmap: Bitmap?,
        outputPath: String
    ): File? {
        return try {
            // Ensure parent directory exists
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            val (_, document, font) = createDocument(outputPath)

            addReportHeader(document, titleAr, titleEn, subtitle, font)

            // Design type chip
            if (designType.isNotEmpty()) {
                val chipPara = styledParagraph(designType, 10f, true, PRIMARY, TextAlignment.CENTER)
                chipPara.setBackgroundColor(LIGHT_BG)
                chipPara.setBorder(com.itextpdf.layout.borders.SolidBorder(PRIMARY, 0.5f))
                chipPara.setPadding(4f)
                document.add(chipPara)
                document.add(Paragraph(" "))
            }

            addStatusBanner(document, isSafe)

            // Design Data section (inputs)
            if (inputs.isNotEmpty()) {
                addSectionTitle(document, t("بيانات التصميم", "Design Data"), "Design Data")
                addInfoTable(document, inputs.entries.map { it.key to it.value }, font)
            }

            // Results section
            if (results.isNotEmpty()) {
                addSectionTitle(document, t("النتائج", "Results"), "Results")
                addInfoTable(document, results.entries.map { it.key to it.value }, font)
            }

            // Safety Checks section
            if (safetyChecks.isNotEmpty()) {
                addSectionTitle(document, t("تحققات الأمان", "Safety Checks"), "Safety Checks")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(35f, 20f, 20f, 10f, 15f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(t("التحقق", "Check")))
                table.addHeaderCell(headerCell(t("المحسوب", "Calculated")))
                table.addHeaderCell(headerCell(t("الحد", "Limit")))
                table.addHeaderCell(headerCell(t("الوحدة", "Unit")))
                table.addHeaderCell(headerCell(t("النتيجة", "Result")))
                safetyChecks.forEachIndexed { i, check ->
                    val bg = if (i % 2 == 0) null else ROW_ALT
                    table.addCell(tableCell(check.name, bg = bg, align = TextAlignment.LEFT))
                    table.addCell(tableCell(formatDouble(check.calculated), bg = bg))
                    table.addCell(tableCell(formatDouble(check.limit), bg = bg))
                    table.addCell(tableCell(check.unit, bg = bg))
                    table.addCell(tableCell(
                        if (check.passed) t("آمن", "SAFE") else t("غير آمن", "FAIL"),
                        color = if (check.passed) SUCCESS else ERROR, bg = bg, bold = true
                    ))
                }
                document.add(table)
            }

            // Drawing section
            addDrawingSection(document, drawingBitmap, designType)

            addFooter(document)
            document.close()
            outputFile
        } catch (e: Exception) {
            Log.e("ComprehensivePdfExporter", "exportGenericReport failed: ${e.message}", e)
            null
        } catch (t: Throwable) {
            Log.e("ComprehensivePdfExporter", "exportGenericReport crashed: ${t.message}", t)
            null
        }
    }

    /** Format a Double for display: NaN/Infinity → "—", integers without decimals, else 2 decimals. */
    private fun formatDouble(v: Double): String {
        return if (v.isNaN() || v.isInfinite()) "—"
        else if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v)
    }

    fun exportProjectBatchReport(
        projectName: String,
        designs: List<Design>,
        summary: ProjectSummary,
        outputPath: String
    ): File? {
        return try {
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            val (_, document, font) = createDocument(outputPath)

            addReportHeader(document, t("تقرير مجمع للمشروع", "Project Batch Report"), "Project Batch Report", projectName, font)
            
            addSectionTitle(document, t("ملخص المشروع", "Project Executive Summary"), "Project Executive Summary")
            addInfoTable(document, listOf(
                t("إجمالي التكلفة", "Total Est. Cost") to "${summary.totalCost.format(0)} EGP",
                t("إجمالي الخرسانة", "Total Concrete") to "${summary.totalConcrete.format(1)} m\u00B3",
                t("إجمالي الحديد", "Total Steel") to "${summary.totalSteel.format(0)} kg",
                t("عدد العناصر", "Elements Count") to "${summary.designCount}"
            ), font)

            addSectionTitle(document, t("جدول العناصر المصممة", "Elements Schedule"), "Elements Schedule")
            val table = Table(UnitValue.createPercentArray(floatArrayOf(30f, 25f, 25f, 20f))).useAllAvailableWidth()
            table.addHeaderCell(headerCell(t("اسم العنصر", "Element Name")))
            table.addHeaderCell(headerCell(t("النوع", "Type")))
            table.addHeaderCell(headerCell(t("التكلفة", "Cost")))
            table.addHeaderCell(headerCell(t("الحالة", "Status")))
            
            designs.forEachIndexed { i, d ->
                val bg = if (i % 2 == 0) null else ROW_ALT
                table.addCell(tableCell(d.name, bg = bg, align = TextAlignment.LEFT))
                table.addCell(tableCell(d.type.name, bg = bg))
                table.addCell(tableCell(d.totalCost.format(0), bg = bg))
                table.addCell(tableCell(
                    if (d.isSafe) "SAFE \u2714" else "UNSAFE \u2718",
                    color = if (d.isSafe) SUCCESS else ERROR, bg = bg
                ))
            }
            document.add(table)

            addFooter(document)
            document.close()
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== Helpers ====================
    private fun Double.format(decimals: Int): String {
        return String.format(Locale.US, "%.${decimals}f", this)
    }
}
