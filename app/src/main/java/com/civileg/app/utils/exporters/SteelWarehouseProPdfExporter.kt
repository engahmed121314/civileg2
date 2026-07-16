package com.civileg.app.utils.exporters

import android.content.Context
import com.civileg.app.R
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.ArabicFontProvider
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.BaseDirection
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * مصدّر PDF احترافي لمشاريع المزارع الفولاذية
 * Professional PDF Exporter for Steel Warehouse Projects
 *
 * Generates comprehensive bilingual (Arabic/English) warehouse design reports
 * with proper Arabic text shaping using bundled NotoNaskhArabic font.
 */
class SteelWarehouseProPdfExporter(private val context: Context) {

    private val PRIMARY = DeviceRgb(21, 101, 192)
    private val SECONDARY = DeviceRgb(55, 71, 79)
    private val SUCCESS = DeviceRgb(46, 125, 50)
    private val ERROR = DeviceRgb(198, 40, 40)
    private val WARNING = DeviceRgb(245, 124, 0)
    private val HEADER_BG = DeviceRgb(33, 37, 41)
    private val LIGHT_BLUE = DeviceRgb(227, 242, 253)
    private val ROW_ALT = DeviceRgb(248, 249, 250)
    private val WHITE = DeviceRgb(255, 255, 255)

    private val arabicFont: PdfFont by lazy { ArabicFontProvider.getArabicPdfFont(context) }
    private val arabicBoldFont: PdfFont by lazy { ArabicFontProvider.getArabicPdfFont(context, bold = true) }

    private fun isArabic(text: String) = ArabicFontProvider.containsArabic(text)

    private fun ar(text: String): String = text

    private fun arParagraph(text: String, fontSize: Float = 10f, bold: Boolean = false, color: DeviceRgb? = null, alignment: TextAlignment? = null): Paragraph {
        val p = Paragraph(text).setFontSize(fontSize)
        if (bold) p.setBold()
        color?.let { p.setFontColor(it) }
        alignment?.let { p.setTextAlignment(it) }
        p.setFont(if (bold) arabicBoldFont else arabicFont)
        p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        return p
    }

    private fun headerCell(text: String, colSpan: Int = 1): Cell {
        val cell = Cell(colSpan, 1).setPadding(5f).setBackgroundColor(HEADER_BG).setTextAlignment(TextAlignment.CENTER)
        val p = Paragraph(text).setFontSize(8f).setBold().setFontColor(WHITE)
        if (isArabic(text)) {
            p.setFont(arabicBoldFont)
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        cell.add(p)
        return cell
    }

    private fun dataCell(text: String, fontSize: Float = 8f, bold: Boolean = false, bg: DeviceRgb? = null, color: DeviceRgb? = null): Cell {
        val cell = Cell().setPadding(3f).setTextAlignment(TextAlignment.CENTER)
        val p = Paragraph(text).setFontSize(fontSize)
        if (bold) p.setBold()
        color?.let { p.setFontColor(it) }
        if (isArabic(text)) {
            p.setFont(arabicFont)
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        cell.add(p)
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    private fun Double.fmt(decimals: Int = 2): String = String.format(Locale.US, "%.${decimals}f", this)

    /**
     * Generate and save a comprehensive steel warehouse PDF report.
     *
     * @return The generated PDF file
     */
    fun exportToDownload(
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        clientAr: String,
        clientEn: String,
        projAr: String,
        projEn: String
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Warehouse_Design_${timestamp}.pdf"
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outputDir, fileName)

        val writer = PdfWriter(FileOutputStream(file))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(30f, 30f, 30f, 30f)

        // ========== PAGE 1: COVER & GENERAL NOTES ==========
        addCoverPage(document, inputs, result, clientAr, clientEn, projAr, projEn)
        document.add(AreaBreak())

        // ========== PAGE 2: GENERAL NOTES & PROJECT SUMMARY ==========
        addGeneralNotes(document, inputs)
        addProjectSummary(document, inputs, result)
        document.add(AreaBreak())

        // ========== PAGE 3: STEEL MEMBER SCHEDULE ==========
        addMemberSchedule(document, inputs, result)
        document.add(AreaBreak())

        // ========== PAGE 4: CONNECTIONS & RECOMMENDATIONS ==========
        addConnectionSchedule(document, result)
        addRecommendations(document, result)
        document.add(AreaBreak())

        // ========== PAGE 5: MATERIAL TAKEOFF & COST ==========
        addMaterialTakeoff(document, result)
        addTitleBlock(document, clientAr, clientEn, projAr, projEn, inputs)

        document.close()
        return file
    }

    // ==================== COVER PAGE ====================
    private fun addCoverPage(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult,
                             clientAr: String, clientEn: String, projAr: String, projEn: String) {
        // Top blue banner
        val banner = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        val bannerCell = Cell().setPadding(15f).setBackgroundColor(PRIMARY).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        bannerCell.add(Paragraph("STRUCTURAL DESIGN & ANALYSIS REPORT")
            .setFontSize(18f).setBold().setFontColor(WHITE).setTextAlignment(TextAlignment.CENTER))
        bannerCell.add(arParagraph("تقرير التصميم والتحليل الإنشائي", 16f, true, WHITE, TextAlignment.CENTER))
        banner.addCell(bannerCell)
        document.add(banner)

        document.add(Paragraph(" "))

        // Project Info Table
        val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f))).useAllAvailableWidth()

        fun addInfoRow(label: String, value: String, rowIdx: Int) {
            val bg = if (rowIdx % 2 == 0) LIGHT_BLUE else null
            val labelCell = Cell().setPadding(6f)
            val lp = Paragraph(label).setFontSize(9f).setBold()
            if (arabicFont != null && isArabic(label)) {
                lp.setFont(arabicBoldFont ?: arabicFont)
                lp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            }
            labelCell.add(lp)
            labelCell.setTextAlignment(TextAlignment.RIGHT)
            bg?.let { labelCell.setBackgroundColor(it) }
            infoTable.addCell(labelCell)

            val valueCell = Cell().setPadding(6f)
            val vp = Paragraph(value).setFontSize(9f)
            if (arabicFont != null && isArabic(value)) {
                vp.setFont(arabicFont)
                vp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            }
            valueCell.add(vp)
            bg?.let { valueCell.setBackgroundColor(it) }
            infoTable.addCell(valueCell)
        }

        var row = 0
        addInfoRow("المشروع | Project", "$projAr - $projEn", row++); row++
        addInfoRow("العميل | Client", "$clientAr - $clientEn", row++); row++
        addInfoRow("الكود التصميمي | Design Code", inputs.code.version, row++); row++
        addInfoRow("البحر | Span", "${inputs.span.fmt()} m", row++); row++
        addInfoRow("الطول | Length", "${inputs.length.fmt()} m", row++); row++
        addInfoRow("ارتفاع القاعدة | Eave Height", "${inputs.eaveHeight.fmt()} m", row++); row++
        addInfoRow("ارتفاع القمة | Ridge Height", "${inputs.ridgeHeight.fmt()} m", row++); row++
        addInfoRow("مسافة البي | Bay Spacing", "${inputs.baySpacing.fmt()} m", row++); row++
        addInfoRow("ميل السقف | Roof Slope", "${(inputs.slope * 100).fmt(1)}%", row++); row++
        addInfoRow("تاريخ التصميم | Date", SimpleDateFormat("yyyy/MM/dd", Locale("ar")).format(Date()), row)

        document.add(infoTable)
        document.add(Paragraph(" "))

        // Status Banner
        val statusText = if (result.safetyStatus) {
            "STRUCTURAL ANALYSIS PASSED | التصميم الإنشائي آمن ومطابق للكود"
        } else {
            "REVIEW REQUIRED | يحتاج مراجعة إنشائية"
        }
        val statusP = Paragraph(statusText).setFontSize(11f).setBold()
            .setFontColor(if (result.safetyStatus) SUCCESS else ERROR)
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(8f)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(if (result.safetyStatus) SUCCESS else ERROR, 2f))
        document.add(statusP)
    }

    // ==================== GENERAL NOTES ====================
    private fun addGeneralNotes(document: Document, inputs: SteelWarehouseInputs) {
        document.add(arParagraph("ملاحظات عامة - General Notes", 12f, true, PRIMARY, TextAlignment.CENTER))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val notes = listOf(
            "1. جميع الأبعاد بالمتر ما لم يُذكر غير ذلك.",
            "2. تصميم الأعضاء الفولاذية طبقاً للكود ${inputs.code.version}.",
            "3. اللحام طبقاً لمواصفات AWS D1.1 (حد أدنى 6mm).",
            "4. البراغي عالية الشد ASTM A325 أو ما يعادلها.",
            "5. ميل السقف 1% - 10% لتصريف المياه حسب التصميم.",
            "6. درجة المادة: الإطار الرئيسي S355/St-52، الثانوي S235/St-37.",
            "7. All dimensions are in METERS unless otherwise noted.",
            "8. Steel members designed per ${inputs.code.version} code.",
            "9. Welding per AWS D1.1 (6mm minimum fillet weld).",
            "10. High strength bolts ASTM A325 or equivalent."
        )

        val notesTable = Table(UnitValue.createPercentArray(floatArrayOf(5f, 95f))).useAllAvailableWidth()
        notes.forEachIndexed { i, note ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            notesTable.addCell(dataCell("${i + 1}", bg = bg))
            val noteCell = Cell().setPadding(3f).setTextAlignment(TextAlignment.LEFT)
            val np = Paragraph(note).setFontSize(7f)
            if (arabicFont != null && isArabic(note)) {
                np.setFont(arabicFont)
                np.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            }
            noteCell.add(np)
            bg?.let { noteCell.setBackgroundColor(it) }
            notesTable.addCell(noteCell)
        }
        document.add(notesTable)
        document.add(Paragraph(" "))
    }

    // ==================== PROJECT SUMMARY ====================
    private fun addProjectSummary(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        document.add(arParagraph("ملخص المشروع والمواد - Project & Material Summary", 12f, true, PRIMARY, TextAlignment.CENTER))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val summary = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()

        fun addSummaryCell(label: String, value: String, rowIdx: Int) {
            val bg = if (rowIdx % 2 == 0) LIGHT_BLUE else null
            val lc = Cell().setPadding(5f)
            val lp = Paragraph(label).setFontSize(8f).setBold()
            if (arabicFont != null && isArabic(label)) {
                lp.setFont(arabicBoldFont ?: arabicFont)
                lp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            }
            lc.add(lp)
            bg?.let { lc.setBackgroundColor(it) }
            summary.addCell(lc)

            val vc = Cell().setPadding(5f)
            val vp = Paragraph(value).setFontSize(9f).setBold()
            if (arabicFont != null && isArabic(value)) {
                vp.setFont(arabicFont)
                vp.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            }
            vc.add(vp)
            bg?.let { vc.setBackgroundColor(it) }
            summary.addCell(vc)
        }

        var row = 0
        addSummaryCell("الوزن الكلي للفولاذ | Total Steel Weight", "${result.totalWeight.fmt(1)} Tons", row++); row++
        addSummaryCell("الوزن لكل م\u00B2 | Weight per m\u00B2", "${result.weightPerM2.fmt(1)} kg/m\u00B2", row++); row++
        addSummaryCell("التكلفة لكل م\u00B2 | Cost per m\u00B2", "${result.costPerM2.fmt(0)} EGP/m\u00B2", row++); row++
        addSummaryCell("التكلفة الإجمالية | Total Estimated Cost", "${result.estimatedTotalCost.fmt(0)} EGP", row++); row++
        addSummaryCell("صافي الربح | Net Profit", "${result.netProfit.fmt(0)} EGP", row++); row++
        addSummaryCell("العائد على الاستثمار | ROI", "${result.roi.fmt(1)}%", row++); row++
        addSummaryCell("مساحة الكسوة | Cladding Area", "${result.totalCladdingArea.fmt(1)} m\u00B2", row)

        document.add(summary)
        document.add(Paragraph(" "))
    }

    // ==================== MEMBER SCHEDULE ====================
    private fun addMemberSchedule(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        document.add(arParagraph("جدول القطاعات الإنشائية - Steel Member Schedule", 12f, true, PRIMARY, TextAlignment.CENTER))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(8f, 15f, 25f, 18f, 12f, 12f, 10f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell("MARK"))
        table.addHeaderCell(headerCell("MEMBER"))
        table.addHeaderCell(headerCell("SECTION"))
        table.addHeaderCell(headerCell("MATERIAL"))
        table.addHeaderCell(headerCell("QTY"))
        table.addHeaderCell(headerCell("LENGTH"))
        table.addHeaderCell(headerCell("STATUS"))

        val numBays = (inputs.length / inputs.baySpacing).toInt().coerceAtLeast(1)
        val members = listOf(
            Triple("C1", "أعمدة | Columns", result.mainFrame.columnSection),
            Triple("R1", "روافع | Rafters", result.mainFrame.rafterSection),
            Triple("P1", "بورمات | Purlins", result.secondaryMembers.purlinSection),
            Triple("G1", "جيرتس | Girts", result.secondaryMembers.girtSection),
            Triple("B1", "تقوية | Bracing", result.secondaryMembers.bracingSection)
        )

        members.forEachIndexed { i, (mark, member, section) ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            table.addCell(dataCell(mark, bold = true, bg = bg))
            table.addCell(dataCell(member, bg = bg))
            table.addCell(dataCell(section.displayName, bold = true, bg = bg))
            table.addCell(dataCell("ASTM A572 Gr.50", bg = bg))

            val qty = when (mark) {
                "C1" -> (numBays + 1) * 2
                "R1" -> numBays * 2
                "P1" -> result.secondaryMembers.purlinCount
                "G1" -> result.secondaryMembers.purlinCount
                else -> numBays * 2
            }
            table.addCell(dataCell("$qty", bg = bg))

            val len = when (mark) {
                "C1" -> "${inputs.eaveHeight.fmt(1)} m"
                "R1" -> "${inputs.span.fmt(1)} m"
                "P1" -> "${inputs.baySpacing.fmt(1)} m"
                "G1" -> "${inputs.baySpacing.fmt(1)} m"
                else -> "-"
            }
            table.addCell(dataCell(len, bg = bg))
            table.addCell(dataCell("OK", color = SUCCESS, bg = bg))
        }

        document.add(table)

        // Design Forces Summary
        document.add(Paragraph(" "))
        document.add(arParagraph("القوى التصميمية - Design Forces Summary", 10f, true, PRIMARY, TextAlignment.CENTER))

        val forces = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))).useAllAvailableWidth()
        forces.addHeaderCell(headerCell("AXIAL (kN)"))
        forces.addHeaderCell(headerCell("MOMENT (kN.m)"))
        forces.addHeaderCell(headerCell("SHEAR (kN)"))
        forces.addHeaderCell(headerCell(ar("الحالة") + " | STATUS"))
        forces.addCell(dataCell("${result.mainFrame.maxAxial.fmt(1)}", bold = true))
        forces.addCell(dataCell("${result.mainFrame.maxMoment.fmt(1)}", bold = true))
        forces.addCell(dataCell("${result.mainFrame.maxShear.fmt(1)}", bold = true))
        forces.addCell(dataCell(if (result.safetyStatus) "PASS" else "REVIEW", bold = true, color = if (result.safetyStatus) SUCCESS else ERROR))
        document.add(forces)
        document.add(Paragraph(" "))
    }

    // ==================== CONNECTIONS ====================
    private fun addConnectionSchedule(document: Document, result: SteelWarehouseAnalysisResult) {
        if (result.connections.isEmpty()) return

        document.add(arParagraph("جدول الوصلات - Connection Schedule", 12f, true, PRIMARY, TextAlignment.CENTER))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(20f, 20f, 20f, 20f, 20f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell(ar("الوصلة") + " | Connection"))
        table.addHeaderCell(headerCell("TYPE"))
        table.addHeaderCell(headerCell("CAPACITY (kN)"))
        table.addHeaderCell(headerCell("DEMAND (kN)"))
        table.addHeaderCell(headerCell("STATUS"))

        result.connections.forEachIndexed { i, conn ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            table.addCell(dataCell(conn.name, bg = bg))
            table.addCell(dataCell(conn.type::class.simpleName ?: "N/A", bg = bg))
            table.addCell(dataCell(conn.capacity.fmt(1), bg = bg))
            table.addCell(dataCell(conn.demand.fmt(1), bg = bg))
            table.addCell(dataCell(
                if (conn.isSafe) "PASS" else "FAIL",
                color = if (conn.isSafe) SUCCESS else ERROR, bg = bg
            ))
        }
        document.add(table)
        document.add(Paragraph(" "))
    }

    // ==================== RECOMMENDATIONS ====================
    private fun addRecommendations(document: Document, result: SteelWarehouseAnalysisResult) {
        if (result.recommendations.isEmpty()) return

        document.add(arParagraph("توصيات التصميم - Design Recommendations", 12f, true, PRIMARY, TextAlignment.CENTER))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        result.recommendations.forEachIndexed { i, rec ->
            val p = Paragraph("${i + 1}. $rec").setFontSize(8f)
            if (arabicFont != null && isArabic(rec)) {
                p.setFont(arabicFont)
                p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            }
            document.add(p)
        }
        document.add(Paragraph(" "))
    }

    // ==================== MATERIAL TAKEOFF ====================
    private fun addMaterialTakeoff(document: Document, result: SteelWarehouseAnalysisResult) {
        document.add(arParagraph("جدول الكميات والتكلفة - Bill of Quantities", 12f, true, PRIMARY, TextAlignment.CENTER))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(5f, 40f, 25f, 30f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell("#"))
        table.addHeaderCell(ar("البند") + " | ITEM")
        table.addHeaderCell(ar("الكمية") + " | QUANTITY")
        table.addHeaderCell(ar("ملاحظات") + " | NOTES")

        var idx = 1
        result.materialTakeoff.forEach { (key, value) ->
            val bg = if (idx % 2 == 0) ROW_ALT else null
            table.addCell(dataCell("$idx", bg = bg))
            table.addCell(dataCell(key, bg = bg))
            table.addCell(dataCell(value.fmt(2), bg = bg))
            table.addCell(dataCell("-", bg = bg))
            idx++
        }

        // Add cost rows
        val bg = if (idx % 2 == 0) ROW_ALT else null
        table.addCell(dataCell("", bg = bg))
        table.addCell(dataCell(ar("التكلفة الإجمالية") + " | TOTAL COST", bold = true, bg = LIGHT_BLUE))
        table.addCell(dataCell("${result.estimatedTotalCost.fmt(0)} EGP", bold = true, bg = LIGHT_BLUE))
        table.addCell(dataCell(ar("شامل الضريبة") + " | Incl. Tax", bg = LIGHT_BLUE))

        document.add(table)
        document.add(Paragraph(" "))
    }

    // ==================== TITLE BLOCK ====================
    private fun addTitleBlock(document: Document, clientAr: String, clientEn: String, projAr: String, projEn: String, inputs: SteelWarehouseInputs) {
        document.add(Paragraph(" "))

        val titleBlock = Table(UnitValue.createPercentArray(floatArrayOf(30f, 20f, 25f, 25f))).useAllAvailableWidth()
        titleBlock.setBorder(com.itextpdf.layout.borders.SolidBorder(2f))

        // Project cell
        val projCell = Cell(1, 1).setPadding(5f)
        val projLabelP = Paragraph("PROJECT / المشروع").setFontSize(6f).setBold().setFontColor(ColorConstants.GRAY)
        if (arabicFont != null) {
            projLabelP.setFont(arabicFont)
            projLabelP.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        projCell.add(projLabelP)
        val projText = Paragraph("$projEn").setFontSize(8f).setBold()
        projCell.add(projText)
        val projArP = Paragraph(projAr).setFontSize(7f)
        if (arabicFont != null) {
            projArP.setFont(arabicFont)
            projArP.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        projCell.add(projArP)
        titleBlock.addCell(projCell)

        // Client cell
        val clientCell = Cell(1, 1).setPadding(5f)
        val clientLabelP = Paragraph("CLIENT / العميل").setFontSize(6f).setBold().setFontColor(ColorConstants.GRAY)
        if (arabicFont != null) {
            clientLabelP.setFont(arabicFont)
            clientLabelP.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        clientCell.add(clientLabelP)
        val clientText = Paragraph(clientEn).setFontSize(8f).setBold()
        clientCell.add(clientText)
        val clientArP = Paragraph(clientAr).setFontSize(7f)
        if (arabicFont != null) {
            clientArP.setFont(arabicFont)
            clientArP.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        clientCell.add(clientArP)
        titleBlock.addCell(clientCell)

        // Designer cell
        val designCell = Cell(1, 1).setPadding(5f)
        designCell.add(Paragraph("DESIGNED BY").setFontSize(6f).setBold().setFontColor(ColorConstants.GRAY))
        designCell.add(Paragraph("Civil EG Pro Engine").setFontSize(8f).setBold())
        titleBlock.addCell(designCell)

        // Date & Code cell
        val dateCell = Cell(1, 1).setPadding(5f)
        dateCell.add(Paragraph("DATE / CODE").setFontSize(6f).setBold().setFontColor(ColorConstants.GRAY))
        dateCell.add(Paragraph("${SimpleDateFormat("MMM yyyy", Locale.US).format(Date())} | ${inputs.code.version}").setFontSize(8f).setBold())
        dateCell.add(Paragraph("SHEET: S-01 Rev.0").setFontSize(7f))
        titleBlock.addCell(dateCell)

        document.add(titleBlock)

        // Footer disclaimer
        document.add(Paragraph(" "))
        val footer = Paragraph(
            "Generated by Civil EG Pro | ${ar("هذا التقرير لأغراض مرجعية فقط - يجب مراجعته بواسطة مهندس مؤهل")}"
        ).setFontSize(7f).setFontColor(ColorConstants.LIGHT_GRAY).setTextAlignment(TextAlignment.CENTER)
        document.add(footer)
    }
}