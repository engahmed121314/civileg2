package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import com.civileg.app.R
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.ArabicFontProvider
import com.civileg.app.utils.CalculatorEngine
import com.itextpdf.io.font.PdfEncodings
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
    private val arabicFont: PdfFont? by lazy { ArabicFontProvider.getArabicPdfFont(context) }
    private val arabicBoldFont: PdfFont? by lazy { ArabicFontProvider.getArabicPdfFont(context, bold = true) }

    private var currentLanguage: String = "ar"

    fun setLanguage(lang: String): ComprehensivePdfExporter {
        this.currentLanguage = lang
        return this
    }

    // ==================== Text Helpers ====================
    private fun isArabic(text: String) = ArabicFontProvider.containsArabic(text)

    private fun ar(text: String): String = text

    private fun styledParagraph(
        text: String,
        fontSize: Float = 10f,
        bold: Boolean = false,
        color: DeviceRgb? = null,
        alignment: TextAlignment? = null,
        rtl: Boolean? = null
    ): Paragraph {
        val p = Paragraph(text).setFontSize(fontSize)
        if (bold) p.setBold()
        color?.let { p.setFontColor(it) }
        alignment?.let { p.setTextAlignment(it) }

        val useRtl = rtl ?: isArabic(text)
        if (useRtl && arabicFont != null) {
            p.setFont(arabicFont)
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        return p
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
        if (bold) p.setBold()
        if (arabicFont != null && isArabic(text)) {
            p.setFont(arabicFont)
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
        if (arabicFont != null && isArabic(text)) {
            p.setFont(arabicBoldFont ?: arabicFont)
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        cell.add(p)
        return cell
    }

    // ==================== Document Structure ====================
    private fun createDocument(outputPath: String): Triple<PdfDocument, Document, PdfFont?> {
        val writer = PdfWriter(FileOutputStream(outputPath))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(40f, 40f, 40f, 40f)
        return Triple(pdf, document, arabicFont)
    }

    private fun addReportHeader(document: Document, titleAr: String, titleEn: String, subtitle: String, font: PdfFont?) {
        // App name
        val appName = Paragraph(context.getString(R.string.app_name))
            .setFontSize(22f)
            .setBold()
            .setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER)
        if (font != null) {
            appName.setFont(arabicBoldFont ?: font)
            appName.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        document.add(appName)

        // English subtitle
        document.add(Paragraph("Civil EG - Advanced Structural Design")
            .setFontSize(10f)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(ColorConstants.GRAY))

        document.add(LineSeparator(SolidLine(2f)).setMarginTop(5f).setMarginBottom(10f))

        // Report title
        val titlePara = styledParagraph("$titleAr  |  $titleEn", 16f, true, PRIMARY, TextAlignment.CENTER)
        document.add(titlePara)

        // Subtitle
        document.add(Paragraph(subtitle)
            .setFontSize(10f)
            .setFontColor(SECONDARY)
            .setTextAlignment(TextAlignment.CENTER))

        // Date
        val dateStr = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale("ar")).format(Date())
        val datePara = styledParagraph(dateStr, 9f, color = ColorConstants.GRAY, alignment = TextAlignment.CENTER)
        document.add(datePara)

        document.add(LineSeparator(SolidLine(0.5f)).setMarginTop(5f).setMarginBottom(10f))
    }

    private fun addStatusBanner(document: Document, isSafe: Boolean, details: String = "") {
        val text = if (isSafe) {
            if (details.isNotEmpty()) "${ar("الحالة: آمن - مطابق للكود")} | STATUS: SAFE\n$details"
            else "${ar("الحالة: آمن - مطابق للكود")} | STATUS: SAFE \u2714"
        } else {
            if (details.isNotEmpty()) "${ar("الحالة: غير آمن - يحتاج مراجعة")} | STATUS: UNSAFE\n$details"
            else "${ar("الحالة: غير آمن - يحتاج مراجعة")} | STATUS: UNSAFE \u2718"
        }
        val p = styledParagraph(text, 11f, true,
            if (isSafe) SUCCESS else ERROR,
            TextAlignment.CENTER
        )
        p.setPadding(8f)
        p.setBorder(com.itextpdf.layout.borders.SolidBorder(if (isSafe) SUCCESS else ERROR, 1.5f))
        document.add(p)
        document.add(Paragraph(" "))
    }

    private fun addSectionTitle(document: Document, titleAr: String, titleEn: String) {
        val text = "$titleAr  -  $titleEn"
        val p = styledParagraph(text, 12f, true, PRIMARY, TextAlignment.CENTER)
        document.add(p)
        document.add(LineSeparator(SolidLine(0.5f)).setMarginBottom(8f))
    }

    private fun addInfoTable(document: Document, rows: List<Pair<String, String>>, font: PdfFont?) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(45f, 55f))).useAllAvailableWidth()
        var rowIndex = 0
        for ((label, value) in rows) {
            val bg = if (rowIndex % 2 == 0) null else ROW_ALT
            val labelAr = isArabic(label)
            val valueAr = isArabic(value)

            val labelCell = Cell().setPadding(4f)
            if (labelAr) labelCell.setTextAlignment(TextAlignment.RIGHT)
            val lp = Paragraph(label).setFontSize(9f).setBold()
            if (labelAr && font != null) {
                lp.setFont(arabicBoldFont ?: font)
                lp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            }
            labelCell.add(lp)
            bg?.let { labelCell.setBackgroundColor(it) }
            table.addCell(labelCell)

            val valueCell = Cell().setPadding(4f)
            if (valueAr) valueCell.setTextAlignment(TextAlignment.RIGHT)
            val vp = Paragraph(value).setFontSize(9f)
            if (valueAr && font != null) {
                vp.setFont(font)
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
        addSectionTitle(document, ar("الرسم الهندسي"), title)
        try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setAutoScale(true)
            img.setHorizontalAlignment(HorizontalAlignment.CENTER)
            document.add(img)
        } catch (e: Exception) {
            document.add(styledParagraph("[Drawing not available]", 9f, color = ColorConstants.GRAY))
        }
        document.add(Paragraph(" "))
    }

    private fun addFooter(document: Document) {
        document.add(Paragraph(" "))
        document.add(LineSeparator(SolidLine(0.5f)).setMarginTop(10f).setMarginBottom(5f))
        val footer = styledParagraph(
            "${ar("تم إنشاء هذا التقرير تلقائياً بواسطة تطبيق Civil EG Pro")}\nGenerated by Civil EG Pro - ${SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())}",
            8f, color = ColorConstants.GRAY, alignment = TextAlignment.CENTER
        )
        document.add(footer)
        document.add(Paragraph(
            ar("هذا التقرير لأغراض مرجعية فقط - يجب مراجعته بواسطة مهندس مؤهل قبل التنفيذ") +
            " | This report is for reference only - must be reviewed by a qualified engineer before execution."
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
                ar("تقرير تصميم كمرات خرسانية"),
                "Reinforced Concrete Beam Design Report",
                "${designCode.version} | ${beamType::class.simpleName}",
                font
            )

            addStatusBanner(document, result.flexureResult.isSafe,
                "Utilization: ${(result.flexureResult.utilizationRatio * 100).format(1)}%"
            )

            // Design Parameters
            addSectionTitle(document, ar("معاملات التصميم"), "Design Parameters")
            addInfoTable(document, listOf(
                ar("اسم المشروع") to projectName,
                ar("الكود التصميمي") to designCode.version,
                ar("نوع الكمرة") to (beamType::class.simpleName ?: "N/A"),
                ar("العرض") to "${inputs.width.format(0)} mm",
                ar("الارتفاع الكلي") to "${inputs.totalDepth.format(0)} mm",
                ar("العمق الفعال") to "${inputs.effectiveDepth.format(0)} mm",
                ar("البحر") to "${inputs.span.format(2)} m",
                ar("الحمل الميت") to "${inputs.deadLoad.format(2)} kN/m\u00B2",
                ar("الحمل الحي") to "${inputs.liveLoad.format(2)} kN/m\u00B2",
                ar("مقاومة الخرسانة") to "f'c = ${inputs.fcu.format(0)} MPa",
                ar("مقاومة الحديد") to "fy = ${inputs.fy.format(0)} MPa",
                ar("العزم التصميمي") to "${inputs.designMoment.format(2)} kN.m",
                ar("القص التصميمي") to "${inputs.designShear.format(2)} kN"
            ), font)

            // Flexure Design
            addSectionTitle(document, ar("نتائج التصميم - الانحناء"), "Flexure Design Results")
            val fr = result.flexureResult
            addInfoTable(document, listOf(
                ar("مساحة الحديد المطلوبة") to "${fr.astRequired.format(1)} mm\u00B2",
                ar("مساحة الحديد المزود") to "${fr.astProvided.format(1)} mm\u00B2",
                ar("قطر الحديد") to "\u00D8${fr.barDiameter.toInt()} mm",
                ar("عدد الحديد") to "${fr.numberOfBars} \u03C6${fr.barDiameter.toInt()}",
                ar("قطر الكانات") to "\u00D8${fr.tiesDiameter.toInt()} mm",
                ar("مسافة الكانات") to "@ ${fr.tiesSpacing.toInt()} mm",
                ar("نسبة الاستخدام") to "${(fr.utilizationRatio * 100).format(1)}%",
                ar("الحالة") to if (fr.isSafe) ar("آمن \u2714") else ar("غير آمن \u2718")
            ), font)

            // Shear Design
            addSectionTitle(document, ar("نتائج التصميم - القص"), "Shear Design Results")
            val sr = result.shearResult
            addInfoTable(document, listOf(
                ar("الحالة") to if (sr.isSafe) ar("آمن \u2714") else ar("غير آمن \u2718"),
                ar("قطر الكانات") to "\u00D8${sr.stirrupDiameter.toInt()} mm",
                ar("مسافة الكانات") to "@ ${sr.stirrupSpacing.toInt()} mm",
                ar("نسبة الاستخدام") to "${(sr.utilizationRatio * 100).format(1)}%"
            ), font)

            // Deflection Check
            addSectionTitle(document, ar("التحقق من الهبوط"), "Deflection Check")
            val dc = result.deflectionCheck
            addInfoTable(document, listOf(
                ar("الهبوط المحسوب") to "${dc.calculatedDeflection.format(2)} mm",
                ar("الهبوط المسموح") to "${dc.allowableDeflection.format(2)} mm",
                ar("الحالة") to if (dc.isSafe) ar("مطابق \u2714") else ar("غير مطابق \u2718")
            ), font)

            // Warnings & Notes
            if (result.warnings.isNotEmpty() || result.codeNotes.isNotEmpty()) {
                addSectionTitle(document, ar("ملاحظات وتحذيرات"), "Warnings & Code Notes")
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
                ar("تقرير تصميم أعمدة خرسانية"),
                "Reinforced Concrete Column Design Report",
                "${designCode.version} | ${columnType::class.simpleName}",
                font
            )

            addStatusBanner(document, result.reinforcementResult.isSafe,
                "Capacity: ${result.axialCapacity.format(1)} kN | Applied: ${inputs.axialLoad.format(1)} kN"
            )

            // Design Parameters
            addSectionTitle(document, ar("معاملات التصميم"), "Design Parameters")
            val colDims = when (columnType) {
                is ColumnType.Rectangular -> "${columnType.width.format(0)} x ${columnType.depth.format(0)} mm"
                is ColumnType.Circular -> "\u00D8${columnType.diameter.format(0)} mm"
                else -> columnType::class.simpleName ?: "N/A"
            }
            addInfoTable(document, listOf(
                ar("اسم المشروع") to projectName,
                ar("الكود التصميمي") to designCode.version,
                ar("نوع العمود") to colDims,
                ar("الارتفاع غير مسنود") to "${inputs.unsupportedLength.format(2)} m",
                ar("الحمل المحوري") to "${inputs.axialLoad.format(1)} kN",
                ar("العزم حول X") to "${inputs.momentX.format(2)} kN.m",
                ar("العزم حول Y") to "${inputs.momentY.format(2)} kN.m",
                ar("مقاومة الخرسانة") to "f'c = ${inputs.fcu.format(0)} MPa",
                ar("مقاومة الحديد") to "fy = ${inputs.fy.format(0)} MPa"
            ), font)

            // Reinforcement Design
            addSectionTitle(document, ar("نتائج التصميم"), "Design Results")
            val rr = result.reinforcementResult
            addInfoTable(document, listOf(
                ar("القدرة المحورية") to "${result.axialCapacity.format(1)} kN",
                ar("قدرة الانحناء X") to "${result.momentCapacityX.format(2)} kN.m",
                ar("قدرة الانحناء Y") to "${result.momentCapacityY.format(2)} kN.m",
                ar("نسبة النحافة") to "${result.slendernessRatio.format(1)}",
                ar("عمود رفيع") to if (result.isSlender) ar("نعم") else ar("لا"),
                ar("الطول الفعال") to "${result.effectiveLength.format(0)} mm",
                ar("مساحة الحديد المطلوبة") to "${rr.astRequired.format(1)} mm\u00B2",
                ar("مساحة الحديد المزود") to "${rr.astProvided.format(1)} mm\u00B2",
                ar("قطر الحديد الرئيسي") to "\u00D8${rr.barDiameter.toInt()} mm",
                ar("عدد الحديد") to "${rr.numberOfBars} \u03C6${rr.barDiameter.toInt()}",
                ar("قطر الكانات") to "\u00D8${rr.tiesDiameter.toInt()} mm",
                ar("مسافة الكانات") to "@ ${rr.tiesSpacing.toInt()} mm",
                ar("وزن الحديد/م.ط") to "${result.steelWeightPerMeter.format(2)} kg/m",
                ar("حجم الخرسانة/م.ط") to "${result.concreteVolumePerMeter.format(4)} m\u00B3/m"
            ), font)

            // Alternatives table
            if (alternatives.isNotEmpty()) {
                addSectionTitle(document, ar("بدائل التسليح"), "Reinforcement Alternatives")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell("Bar Dia. (\u00D8mm)"))
                table.addHeaderCell(headerCell("No. of Bars"))
                table.addHeaderCell(headerCell("Area (mm\u00B2)"))
                table.addHeaderCell(headerCell(ar("الحالة") + " | Status"))
                alternatives.forEachIndexed { i, alt ->
                    val bg = if (i % 2 == 0) null else ROW_ALT
                    table.addCell(tableCell("\u00D8${alt.barDiameter.toInt()}", bg = bg))
                    table.addCell(tableCell("${alt.numberOfBars}", bg = bg))
                    table.addCell(tableCell(alt.totalArea.format(1), bg = bg))
                    table.addCell(tableCell(
                        if (alt.isSafe) ar("آمن") + " \u2714" else ar("غير آمن") + " \u2718",
                        color = if (alt.isSafe) SUCCESS else ERROR, bg = bg
                    ))
                }
                document.add(table)
                document.add(Paragraph(" "))
            }

            // Warnings & Notes
            if (result.warnings.isNotEmpty() || result.codeNotes.isNotEmpty()) {
                addSectionTitle(document, ar("ملاحظات"), "Code Notes")
                (result.warnings + result.codeNotes).forEach { note ->
                    document.add(styledParagraph("\u2022 $note", 9f, color = SECONDARY))
                }
            }

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
            val (_, document, font) = createDocument(outputPath)

            addReportHeader(document,
                ar("تقرير تصميم بلاطات خرسانية"),
                "Reinforced Concrete Slab Design Report",
                "${designCode.version} | ${slabType::class.simpleName}",
                font
            )

            addStatusBanner(document, result.flexureResult.isSafe)

            // Design Parameters
            addSectionTitle(document, ar("معاملات التصميم"), "Design Parameters")
            addInfoTable(document, listOf(
                ar("اسم المشروع") to projectName,
                ar("الكود التصميمي") to designCode.version,
                ar("نوع البلاطة") to (slabType::class.simpleName ?: "N/A"),
                ar("السمك") to "${inputs.thickness.format(0)} mm",
                ar("البحر القصير") to "${inputs.shortSpan.format(2)} m",
                ar("البحر الطويل") to "${inputs.longSpan.format(2)} m",
                ar("الحمل الميت") to "${inputs.deadLoad.format(2)} kN/m\u00B2",
                ar("الحمل الحي") to "${inputs.liveLoad.format(2)} kN/m\u00B2",
                ar("مقاومة الخرسانة") to "f'c = ${inputs.fcu.format(0)} MPa",
                ar("مقاومة الحديد") to "fy = ${inputs.fy.format(0)} MPa"
            ), font)

            // Flexure Results
            addSectionTitle(document, ar("نتائج التصميم"), "Design Results")
            val fd = result.flexureResult
            addInfoTable(document, listOf(
                ar("حجم الخرسانة") to "${result.concreteVolume.format(3)} m\u00B3",
                ar("مساحة القالب") to "${result.formworkArea.format(2)} m\u00B2"
            ), font)

            // Reinforcement Layout
            addSectionTitle(document, ar("تفاصيل التسليح"), "Reinforcement Details")
            val rl = result.reinforcementLayout
            addInfoTable(document, listOf(
                ar("حديد القاعدة") to "${rl.bottomBars.diameter.format(0)}\u03C6 @ ${rl.bottomBars.spacing.format(0)}mm",
                ar("حديد القمة") to "${rl.topBars.diameter.format(0)}\u03C6 @ ${rl.topBars.spacing.format(0)}mm",
                ar("حديد التوزيع") to "${rl.distributionBars?.let { "${it.diameter.format(0)}\u03C6 @ ${it.spacing.format(0)}mm" } ?: "N/A"}",
                ar("حديد إضافي") to "${rl.additionalBars.joinToString(", ") { "${it.diameter.format(0)}\u03C6 @ ${it.spacing.format(0)}mm" }}"
            ), font)

            // Shear & Punching
            addSectionTitle(document, ar("التحقق من القص والاختراق"), "Shear & Punching Checks")
            addInfoTable(document, listOf(
                ar("القص - الحالة") to if (result.shearCheck.isSafe) ar("آمن \u2714") else ar("غير آمن \u2718"),
                ar("القص - نسبة الاستخدام") to "${(result.shearCheck.utilizationRatio * 100).format(1)}%"
            ) + (result.punchingShearCheck?.let {
                listOf(
                    ar("الاختراق - الحالة") to if (it.isSafe) ar("آمن \u2714") else ar("غير آمن \u2718"),
                    ar("القوة المطبقة") to "${it.appliedShear.format(1)} kN",
                    ar("القدرة القصية") to "${it.shearCapacity.format(1)} kN"
                )
            } ?: emptyList()), font)

            // Warnings
            if (result.warnings.isNotEmpty() || result.codeNotes.isNotEmpty()) {
                addSectionTitle(document, ar("ملاحظات"), "Notes")
                (result.warnings + result.codeNotes).forEach { note ->
                    document.add(styledParagraph("\u2022 $note", 9f, color = SECONDARY))
                }
            }

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
                ar("تقرير تصميم عضو فولاذي"),
                "Steel Member Design Report",
                "${designCode.version} | ${memberType.name}",
                font
            )

            addStatusBanner(document, result.isSafe,
                "Section: ${sectionType.displayName} | Utilization: ${(result.utilizationRatio * 100).format(1)}%"
            )

            // Section Properties
            addSectionTitle(document, ar("خصائص القطاع"), "Section Properties")
            addInfoTable(document, listOf(
                ar("نوع القطاع") to sectionType.displayName,
                ar("نوع العضو") to memberType.name,
                ar("المساحة") to "${(sectionType.getArea() / 100.0).format(2)} cm\u00B2",
                ar("الوزن") to "${result.weight.format(2)} kg/m",
                ar("الطول غير مسنود") to "${inputs.unbracedLength.format(0)} mm",
                ar("الحمل المحوري") to "${inputs.axialLoad.format(1)} kN",
                ar("العزم") to "${inputs.moment.format(2)} kN.m",
                ar("القص") to "${inputs.shear.format(1)} kN"
            ), font)

            // Capacity Checks
            addSectionTitle(document, ar("نتائج التصميم"), "Design Results")
            addInfoTable(document, listOf(
                ar("القدرة المحورية") to "${result.axialCapacity.format(1)} kN",
                ar("القدرة الانحنائية") to "${result.flexuralCapacity.format(2)} kN.m",
                ar("القدرة القصية") to "${result.shearCapacity.format(1)} kN",
                ar("نسبة الاستخدام") to "${(result.utilizationRatio * 100).format(1)}%",
                ar("التكلفة") to "${result.cost.format(2)} EGP/m"
            ), font)

            // Connection Design
            if (connectionDesign != null) {
                addSectionTitle(document, ar("تصميم الوصلات"), "Connection Design")
                addInfoTable(document, listOf(
                    ar("نوع الوصلة") to connectionDesign.connectionType::class.simpleName ?: "N/A",
                    ar("القدرة") to "${connectionDesign.capacity.format(1)} kN",
                    ar("القوة المطبقة") to "${connectionDesign.appliedForce.format(1)} kN",
                    ar("نسبة الاستخدام") to "${(connectionDesign.utilizationRatio * 100).format(1)}%",
                    ar("الحالة") to if (connectionDesign.isSafe) ar("آمن \u2714") else ar("غير آمن \u2718")
                ), font)
                if (connectionDesign.detailedCalculations.isNotEmpty()) {
                    document.add(styledParagraph(connectionDesign.detailedCalculations, 8f, color = SECONDARY))
                }
            }

            // Warnings
            if (result.warnings.isNotEmpty() || result.codeNotes.isNotEmpty()) {
                addSectionTitle(document, ar("ملاحظات"), "Notes")
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
                ar("تقرير تصميم أساسات"),
                "Footing Design Report",
                "$codeStr | ${result.type.displayName}",
                font
            )

            addStatusBanner(document, result.isSafe,
                "Soil: ${result.soilPressure.format(1)} / ${result.allowablePressure.format(1)} kPa"
            )

            addSectionTitle(document, ar("معاملات التصميم"), "Design Parameters")
            addInfoTable(document, listOf(
                ar("اسم المشروع") to projectName,
                ar("نوع الأساس") to result.type.displayName,
                ar("العرض") to "${result.width.format(0)} mm",
                ar("الطول") to "${result.length.format(0)} mm",
                ar("السمك") to "${result.thickness.format(0)} mm",
                ar("ضغط التربة المسموح") to "${result.allowablePressure.format(1)} kPa",
                ar("ضغط التربة الفعلي") to "${result.soilPressure.format(1)} kPa"
            ), font)

            addSectionTitle(document, ar("نتائج التسليح"), "Reinforcement Results")
            addInfoTable(document, listOf(
                ar("حديد القاع X") to "${result.barsX} \u03C6${result.barDiameter} @ 200mm",
                ar("حديد القاع Y") to "${result.barsY} \u03C6${result.barDiameter} @ 200mm",
                ar("حجم الخرسانة") to "${result.concreteVolume.format(3)} m\u00B3",
                ar("وزن الحديد") to "${result.steelWeight.format(1)} kg",
                ar("التكلفة") to "${result.cost.format(0)} EGP",
                ar("نسبة الاستخدام") to "${(result.utilizationRatio * 100).format(1)}%"
            ), font)

            // Safety Checks
            if (result.safetyChecks.isNotEmpty()) {
                addSectionTitle(document, ar("تحققات الأمان"), "Safety Checks")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(ar("التحقق") + " | Check"))
                table.addHeaderCell(headerCell(ar("القيمة") + " | Value"))
                table.addHeaderCell(headerCell(ar("الحد") + " | Limit"))
                table.addHeaderCell(headerCell(ar("النتيجة") + " | Result"))
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
                ar("تقرير تصميم خزان مياه"),
                "Water Tank Design Report",
                "$codeStr | ${result.type.displayName}",
                font
            )

            addStatusBanner(document, result.isSafe)

            addSectionTitle(document, ar("معاملات التصميم"), "Design Parameters")
            addInfoTable(document, listOf(
                ar("اسم المشروع") to projectName,
                ar("نوع الخزان") to result.type.displayName,
                ar("الطول") to "${result.length.format(2)} m",
                ar("العرض") to "${result.width.format(2)} m",
                ar("الارتفاع") to "${result.height.format(2)} m",
                ar("سمك الحائط") to "${result.wallThickness.format(0)} mm",
                ar("سمك القاعدة") to "${result.baseThickness.format(0)} mm",
                ar("مقاومة الخرسانة") to "f'c = ${result.fcu.format(0)} MPa",
                ar("مقاومة الحديد") to "fy = ${result.fy.format(0)} MPa",
                ar("ضغط المياه") to "${result.waterPressure.format(1)} kN/m\u00B2"
            ), font)

            addSectionTitle(document, ar("نتائج التسليح"), "Reinforcement Results")
            addInfoTable(document, listOf(
                ar("تسليح الحائط") to "${result.wallReinforcement.numBars}\u03C6${result.wallReinforcement.diameter} @ ${result.wallReinforcement.spacing}mm",
                ar("تسليح القاعدة") to "${result.baseReinforcement.numBars}\u03C6${result.baseReinforcement.diameter} @ ${result.baseReinforcement.spacing}mm",
                ar("حجم الخرسانة") to "${result.concreteVolume.format(3)} m\u00B3",
                ar("وزن الحديد") to "${result.steelWeight.format(1)} kg",
                ar("التكلفة") to "${result.cost.format(0)} EGP",
                ar("السعة") to "${result.capacity.format(1)} m\u00B3"
            ), font)

            // Safety Checks
            if (result.safetyChecks.isNotEmpty()) {
                addSectionTitle(document, ar("تحققات الأمان"), "Safety Checks")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(ar("التحقق") + " | Check"))
                table.addHeaderCell(headerCell(ar("القيمة") + " | Value"))
                table.addHeaderCell(headerCell(ar("الحد") + " | Limit"))
                table.addHeaderCell(headerCell(ar("النتيجة") + " | Result"))
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
                ar("تقرير تصميم سلم"),
                "Staircase Design Report",
                "$codeStr | ${result.type.displayName}",
                font
            )

            addStatusBanner(document, result.isSafe)

            addSectionTitle(document, ar("معاملات التصميم"), "Design Parameters")
            addInfoTable(document, listOf(
                ar("اسم المشروع") to projectName,
                ar("نوع السلم") to result.type.displayName,
                ar("السمك") to "${result.thickness.format(0)} mm",
                ar("البحر") to "${result.span.format(2)} m",
                ar("العارضة") to "${result.riser.format(0)} mm",
                ar("الدرجة") to "${result.tread.format(0)} mm",
                ar("الحمل الموحد") to "${result.wu.format(2)} kN/m\u00B2",
                ar("العزم التصميمي") to "${result.mu.format(2)} kN.m",
                ar("مقاومة الخرسانة") to "f'c = ${result.fcu.format(0)} MPa",
                ar("مقاومة الحديد") to "fy = ${result.fy.format(0)} MPa"
            ), font)

            addSectionTitle(document, ar("نتائج التسليح"), "Reinforcement Results")
            addInfoTable(document, listOf(
                ar("التسليح الرئيسي") to "${result.reinforcement.numBars}\u03C6${result.reinforcement.diameter} @ ${result.reinforcement.spacing}mm",
                ar("تسليح التوزيع") to "${result.distributionReinforcement.numBars}\u03C6${result.distributionReinforcement.diameter} @ ${result.distributionReinforcement.spacing}mm",
                ar("حجم الخرسانة") to "${result.concreteVolume.format(3)} m\u00B3",
                ar("وزن الحديد") to "${result.steelWeight.format(1)} kg",
                ar("التكلفة") to "${result.cost.format(0)} EGP"
            ), font)

            // Safety Checks
            if (result.safetyChecks.isNotEmpty()) {
                addSectionTitle(document, ar("تحققات الأمان"), "Safety Checks")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(ar("التحقق") + " | Check"))
                table.addHeaderCell(headerCell(ar("القيمة") + " | Value"))
                table.addHeaderCell(headerCell(ar("الحد") + " | Limit"))
                table.addHeaderCell(headerCell(ar("النتيجة") + " | Result"))
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
                ar("تقرير تصميم حائط ساند"),
                "Retaining Wall Design Report",
                codeStr,
                font
            )

            addStatusBanner(document, result.isSafe)

            addSectionTitle(document, ar("معاملات التصميم"), "Design Parameters")
            addInfoTable(document, listOf(
                ar("اسم المشروع") to projectName,
                ar("ارتفاع الحائط") to "${result.height.format(2)} m",
                ar("سمك الجذع") to "${result.stemThickness.format(0)} mm",
                ar("عرض القاعدة") to "${result.baseWidth.format(0)} mm",
                ar("كثافة التربة") to "${result.soilDensity.format(1)} kN/m\u00B3",
                ar("زاوية الاحتكاك الداخلي") to "${Math.toDegrees(Math.atan(result.ka.toDouble())).format(1)}\u00B0",
                ar("معامل الضغط النشط") to "Ka = ${result.ka.format(3)}",
                ar("ضغط التربة النشط") to "${result.pa.format(1)} kN/m\u00B2",
                ar("مقاومة الخرسانة") to "f'c = ${result.fcu.format(0)} MPa",
                ar("مقاومة الحديد") to "fy = ${result.fy.format(0)} MPa"
            ), font)

            // Stability Checks
            addSectionTitle(document, ar("تحققات الاستقرار"), "Stability Checks")
            addInfoTable(document, listOf(
                ar("معامل الأمان ضد الانقلاب") to "F.S = ${result.factorOfSafetyOverturning.format(2)} (Min: 2.0)",
                ar("معامل الأمان ضد الانزلاق") to "F.S = ${result.factorOfSafetySliding.format(2)} (Min: 1.5)"
            ), font)

            // Reinforcement
            addSectionTitle(document, ar("نتائج التسليح"), "Reinforcement Results")
            addInfoTable(document, listOf(
                ar("تسليح الجذع") to "${result.stemReinforcement.numBars}\u03C6${result.stemReinforcement.diameter} @ ${result.stemReinforcement.spacing}mm",
                ar("تسليح القاعدة") to "${result.baseReinforcement.numBars}\u03C6${result.baseReinforcement.diameter} @ ${result.baseReinforcement.spacing}mm",
                ar("حجم الخرسانة") to "${result.concreteVolume.format(3)} m\u00B3",
                ar("وزن الحديد") to "${result.steelWeight.format(1)} kg",
                ar("التكلفة") to "${result.cost.format(0)} EGP"
            ), font)

            // Safety Checks
            if (result.safetyChecks.isNotEmpty()) {
                addSectionTitle(document, ar("تحققات الأمان"), "Safety Checks")
                val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(ar("التحقق") + " | Check"))
                table.addHeaderCell(headerCell(ar("القيمة") + " | Value"))
                table.addHeaderCell(headerCell(ar("الحد") + " | Limit"))
                table.addHeaderCell(headerCell(ar("النتيجة") + " | Result"))
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