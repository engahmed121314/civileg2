package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import com.civileg.app.R
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.ArabicFontProvider
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.LocaleHelper
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
    // ArabicFontProvider.getArabicPdfFont() now NEVER returns null
    private val arabicFont: PdfFont by lazy { ArabicFontProvider.getArabicPdfFont(context, bold = false) }
    private val arabicBoldFont: PdfFont by lazy { ArabicFontProvider.getArabicPdfFont(context, bold = true) }
    private val helveticaFont: PdfFont by lazy { PdfFontFactory.createFont(StandardFonts.HELVETICA) }

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
        val p = Paragraph().setFontSize(fontSize)
        color?.let { p.setFontColor(it) }
        alignment?.let { p.setTextAlignment(it) }

        if (isArabic(text)) {
            // Split text into Arabic and English segments for bilingual support
            val segments = splitBilingualText(text)
            for ((segText, isArabicSeg) in segments) {
                val font = if (isArabicSeg) (if (bold) arabicBoldFont else arabicFont) else helveticaFont
                val run = Text(segText).setFont(font)
                if (isArabicSeg) run.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                p.add(run)
            }
            val useRtl = rtl ?: !isEnglish
            if (useRtl) p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        } else {
            p.add(Text(text).setFont(if (bold) helveticaFont else helveticaFont))
        }
        return p
    }

    private fun splitBilingualText(text: String): List<Pair<String, Boolean>> {
        val segments = mutableListOf<Pair<String, Boolean>>()
        var currentSeg = StringBuilder()
        var currentIsArabic = isArabic(text.firstOrNull()?.toString() ?: "")

        for (char in text) {
            val charIsArabic = isArabic(char.toString())
            if (charIsArabic != currentIsArabic && currentSeg.isNotEmpty()) {
                segments.add(Pair(currentSeg.toString(), currentIsArabic))
                currentSeg = StringBuilder()
                currentIsArabic = charIsArabic
            }
            currentSeg.append(char)
        }
        if (currentSeg.isNotEmpty()) {
            segments.add(Pair(currentSeg.toString(), currentIsArabic))
        }
        return segments
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
        val p = Paragraph(text).setFontSize(fontSize)
        if (bold) {
            p.setBold()
            p.setFont(arabicBoldFont)
        } else {
            p.setFont(arabicFont)
        }
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
        val p = Paragraph(text).setFontSize(9f).setBold().setFontColor(WHITE)
        if (isArabic(text)) {
            p.setFont(arabicBoldFont)
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
        return Triple(pdf, document, arabicFont)
    }

    private fun addReportHeader(document: Document, titleAr: String, titleEn: String, subtitle: String, font: PdfFont) {
        // App name
        val appName = Paragraph(context.getString(R.string.app_name))
            .setFontSize(22f)
            .setBold()
            .setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER)
        if (isEnglish) {
            appName.setFont(helveticaFont)
        } else {
            appName.setFont(arabicBoldFont)
            appName.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        document.add(appName)

        // Subtitle line
        val subLine = if (isEnglish) "Civil EG - Advanced Structural Design" else "Civil EG - التصميم الإنشائي المتقدم"
        document.add(Paragraph(subLine)
            .setFontSize(10f)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(ColorConstants.GRAY))

        document.add(LineSeparator(SolidLine(2f)).setMarginTop(5f).setMarginBottom(10f))

        // Report title - show only the appropriate language
        val titleText = t(titleAr, titleEn)
        val titlePara = styledParagraph(titleText, 16f, true, PRIMARY, TextAlignment.CENTER)
        document.add(titlePara)

        // Subtitle
        document.add(Paragraph(subtitle)
            .setFontSize(10f)
            .setFontColor(SECONDARY)
            .setTextAlignment(TextAlignment.CENTER))

        // Date
        val dateLocale = if (isEnglish) Locale.US else Locale("ar")
        val dateStr = SimpleDateFormat("yyyy/MM/dd  HH:mm", dateLocale).format(Date())
        val datePara = Paragraph(dateStr)
            .setFontSize(9f)
            .setFontColor(ColorConstants.GRAY)
            .setTextAlignment(TextAlignment.CENTER)
        if (isEnglish) datePara.setFont(helveticaFont) else datePara.setFont(arabicFont)
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
            val lp = Paragraph(label).setFontSize(9f).setBold()
            if (labelAr) {
                lp.setFont(arabicBoldFont)
                lp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            }
            labelCell.add(lp)
            bg?.let { labelCell.setBackgroundColor(it) }
            table.addCell(labelCell)

            val valueCell = Cell().setPadding(4f)
            if (valueAr) valueCell.setTextAlignment(TextAlignment.RIGHT)
            val vp = Paragraph(value).setFontSize(9f)
            if (valueAr) {
                vp.setFont(arabicFont)
                vp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            }
            valueCell.add(vp)
            bg?.let { valueCell.setBackgroundColor(it) }
            table.addCell(valueCell)

            rowIndex++
        }
        document.add(table)
        document.add(Paragraph(" "))
    }

    private fun addDrawingSection(document: Document, bitmap: Bitmap?, title: String) {
        if (bitmap == null) return
        document.add(Paragraph(" "))
        addSectionTitle(document, t("الرسم الهندسي", "Engineering Drawing"), title)
        try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setAutoScale(true)
            img.setHorizontalAlignment(HorizontalAlignment.CENTER)
            document.add(img)
        } catch (e: Exception) {
            document.add(styledParagraph(t("[الرسم غير متاح]", "[Drawing not available]"), 9f, color = ColorConstants.GRAY as DeviceRgb))
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
        document.add(Paragraph(
            t("هذا التقرير لأغراض مرجعية فقط - يجب مراجعته بواسطة مهندس مؤهل قبل التنفيذ",
                "This report is for reference only - must be reviewed by a qualified engineer before execution.")
        ).setFontSize(7f).setFontColor(ColorConstants.LIGHT_GRAY).setTextAlignment(TextAlignment.CENTER))
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
                t("حديد القاع X", "Bottom Steel X") to "${result.barsX} \u03C6${result.barDiameter} @ 200mm",
                t("حديد القاع Y", "Bottom Steel Y") to "${result.barsY} \u03C6${result.barDiameter} @ 200mm",
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
                t("تسليح الحائط", "Wall Reinforcement") to "${result.wallReinforcement.numBars}\u03C6${result.wallReinforcement.diameter} @ ${result.wallReinforcement.spacing}mm",
                t("تسليح القاعدة", "Base Reinforcement") to "${result.baseReinforcement.numBars}\u03C6${result.baseReinforcement.diameter} @ ${result.baseReinforcement.spacing}mm",
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
                t("التسليح الرئيسي", "Main Reinforcement") to "${result.reinforcement.numBars}\u03C6${result.reinforcement.diameter} @ ${result.reinforcement.spacing}mm",
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
                t("زاوية الاحتكاك الداخلي", "Internal Friction Angle") to "${Math.toDegrees(Math.atan(result.ka.toDouble())).format(1)}\u00B0",
                t("معامل الضغط النشط", "Active Pressure Coeff.") to "Ka = ${result.ka.format(3)}",
                t("ضغط التربة النشط", "Active Earth Pressure") to "${result.pa.format(1)} kN/m\u00B2",
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
                t("تسليح القاعدة", "Base Reinforcement") to "${result.baseReinforcement.numBars}\u03C6${result.baseReinforcement.diameter} @ ${result.baseReinforcement.spacing}mm",
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

    // ==================== Helpers ====================
    private fun Double.format(decimals: Int): String {
        return String.format(Locale.US, "%.${decimals}f", this)
    }
}