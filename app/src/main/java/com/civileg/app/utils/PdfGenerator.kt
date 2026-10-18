package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import com.civileg.app.R
import com.civileg.app.domain.entities.*
import com.itextpdf.io.font.PdfEncodings
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale

object PdfGenerator {

    private val PRIMARY_COLOR = DeviceRgb(21, 101, 192) // Professional Blue
    private val SECONDARY_COLOR = DeviceRgb(69, 90, 100)
    private val SUCCESS_COLOR = DeviceRgb(46, 125, 50)
    private val ERROR_COLOR = DeviceRgb(198, 40, 40)
    
    // CRITICAL: NEVER cache PdfFont objects. iText 8 binds a PdfFont to the FIRST
    // PdfDocument that uses it; after that document is closed, the cached font
    // becomes invalid and any subsequent use throws:
    //   "Pdf indirect object belongs to other PDF document. Copy object to current pdf document."
    // This applies to BOTH embedded fonts (Arabic) AND Standard-14 fonts (Helvetica).
    // Always create a fresh font per PDF — FontProgram caching in ArabicFontProvider
    // ensures this stays cheap.

    private fun getArabicFont(context: Context): PdfFont {
        return ArabicFontProvider.getArabicPdfFont(context)
    }

    /** Create a FRESH Helvetica font for use in a single PdfDocument. */
    private fun freshHelvetica(bold: Boolean = false): PdfFont {
        return try {
            PdfFontFactory.createFont(if (bold) StandardFonts.HELVETICA_BOLD else StandardFonts.HELVETICA)
        } catch (_: Exception) {
            PdfFontFactory.createFont(StandardFonts.HELVETICA)
        }
    }

    private fun containsArabic(text: String): Boolean {
        return ArabicFontProvider.containsArabic(text)
    }

    private fun isEnglish(context: Context): Boolean {
        return LocaleHelper.getLocale(context) != "ar"
    }

    /**
     * Returns the appropriate text based on current language setting.
     */
    private fun t(context: Context, ar: String, en: String): String {
        return if (isEnglish(context)) en else ar
    }

    /**
     * CRITICAL FIX: Use BilingualPdfHelper to render text with proper Arabic shaping.
     * Previously text was split into Arabic/Latin segments which BREAKS iText's bidi
     * algorithm and caused disconnected letters and squares in PDFs.
     *
     * Now we use a SINGLE Text run with the Arabic font (which also has Latin glyphs)
     * and let iText's Unicode Bidi Algorithm handle ordering automatically.
     */
    private fun createStyledParagraph(text: String, font: PdfFont?, fontSize: Float = 12f, isBold: Boolean = false): Paragraph {
        // Determine color from caller context — default to no override
        return createStyledParagraphColored(text, font, fontSize, isBold, null)
    }

    private fun createStyledParagraphColored(
        text: String,
        font: PdfFont?,
        fontSize: Float = 12f,
        isBold: Boolean = false,
        color: DeviceRgb?
    ): Paragraph {
        // CRITICAL FIX (2026-07-26): Use PdfTextSegmenter to properly render mixed
        // Arabic/Latin text. Previous approach used the Arabic font for the ENTIRE
        // text when any Arabic was detected. But the bundled NotoNaskhArabic static
        // font only contains 15 Latin chars (digits + basic punctuation) — so Latin
        // letters in mixed text rendered as TOFU (□), producing "encrypted-looking"
        // output. Now we split into segments and use the appropriate font per segment.
        //
        // The Arabic font passed in `font` is used for Arabic segments. Latin segments
        // use a fresh Helvetica (or Helvetica-Bold if isBold=true) so they render
        // correctly even though the Arabic font lacks Latin glyphs.
        val arabicFont = font ?: freshHelvetica(false)
        val latinFont = freshHelvetica(isBold)
        return PdfTextSegmenter.buildMixedParagraph(
            text = text,
            arabicFont = arabicFont,
            latinFont = latinFont,
            fontSize = fontSize,
            color = color
        )
    }

    private fun addArabicCell(table: Table, text: String, font: PdfFont?, isBold: Boolean = false, fontSize: Float = 9f) {
        val p = createStyledParagraph(text, font, fontSize, isBold)
        val cell = Cell().add(p)
        // Ensure cell base direction matches text
        if (containsArabic(text)) {
            cell.setTextAlignment(TextAlignment.RIGHT)
        }
        table.addCell(cell)
    }

    private fun createDataCell(label: String, value: String, font: PdfFont?): Cell {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
        val pLabel = createStyledParagraph(label, font, 9f)
        table.addCell(Cell().add(pLabel).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        val pValue = createStyledParagraph(value, font, 9f, isBold = true)
        table.addCell(Cell().add(pValue).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        return Cell().add(table)
    }

    private fun addMemberRow(table: Table, type: String, section: String, status: String, font: PdfFont?) {
        table.addCell(Cell().add(createStyledParagraph(type, font, 9f)))
        table.addCell(Cell().add(createStyledParagraph(section, font, 9f, isBold = true)))
        val statusP = createStyledParagraph(status, font, 9f, isBold = true)
        if (status.contains("Safe", true) || status.contains("آمن", true)) {
            statusP.setFontColor(SUCCESS_COLOR)
        } else {
            statusP.setFontColor(ERROR_COLOR)
        }
        table.addCell(Cell().add(statusP))
    }

    private fun createSummaryCell(label: String, value: String, font: PdfFont?): Cell {
        val cell = Cell().setPadding(10f).setTextAlignment(TextAlignment.CENTER)
        cell.add(createStyledParagraph(label, font, 10f).setFontColor(ColorConstants.GRAY))
        cell.add(createStyledParagraph(value, font, 14f, isBold = true).setFontColor(PRIMARY_COLOR))
        return cell
    }

    private fun createHeaderCell(text: String, font: PdfFont?): Cell {
        return Cell()
            .add(createStyledParagraph(text, font, 9f, isBold = true))
            .setFontColor(ColorConstants.WHITE)
            .setBackgroundColor(PRIMARY_COLOR)
            .setPadding(6f)
    }

    private fun createReportHeader(document: Document, context: Context, font: PdfFont?, title: String, subtitle: String? = null) {
        val header = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f))).useAllAvailableWidth()
        header.addCell(Cell().add(createStyledParagraph(context.getString(R.string.app_name), font, 22f, isBold = true))
            .setFontColor(PRIMARY_COLOR).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        header.addCell(Cell().add(Paragraph("STRUCTURAL REPORT")
            .setTextAlignment(TextAlignment.RIGHT).setFontSize(10f).setFontColor(ColorConstants.GRAY))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        document.add(header)
        document.add(LineSeparator(SolidLine(1f)))
        document.add(Paragraph("\n"))
        document.add(createStyledParagraph(title, font, 18f, isBold = true).setTextAlignment(TextAlignment.CENTER))
        if (subtitle != null) {
            document.add(createStyledParagraph(subtitle, font, 12f).setFontColor(SECONDARY_COLOR).setTextAlignment(TextAlignment.CENTER))
        }
        document.add(Paragraph("\n"))
    }

    fun generateProfessionalReport(
        context: Context,
        title: String,
        designType: String,
        inputs: Map<String, String>,
        results: Map<String, String>,
        safetyChecks: List<CalculatorEngine.DesignSafetyCheck>,
        isSafe: Boolean,
        drawingBitmap: Bitmap? = null
    ): File {
        val fileName = "CivilEngPro_${designType}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        val arabicFont = getArabicFont(context)

        // --- Header Section ---
        val header = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f))).useAllAvailableWidth()
        val appNamePara = createStyledParagraph(context.getString(R.string.app_name), arabicFont, 26f, isBold = true)
            .setFontColor(PRIMARY_COLOR)
        header.addCell(Cell().add(appNamePara).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        
        header.addCell(Cell().add(Paragraph("STRUCTURAL REPORT")
            .setTextAlignment(TextAlignment.RIGHT).setFontSize(10f).setFontColor(ColorConstants.GRAY)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        document.add(header)
        
        document.add(LineSeparator(SolidLine(1f)))
        document.add(Paragraph("\n"))

        // --- Title & Status ---
        document.add(createStyledParagraph(title, arabicFont, 18f, isBold = true))

        val statusText = if (isSafe) t(context, "STATUS: SAFE (مطابق للأكواد)", "STATUS: SAFE - Code Compliant") else t(context, "STATUS: UNSAFE (غير آمن - مراجعة التصميم)", "STATUS: UNSAFE - Design Review Required")
        val statusPara = createStyledParagraph(statusText, arabicFont, 12f, isBold = true)
            .setFontColor(if (isSafe) SUCCESS_COLOR else ERROR_COLOR)
            .setPadding(10f)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(if (isSafe) SUCCESS_COLOR else ERROR_COLOR, 1f))
        document.add(statusPara)
        
        document.add(Paragraph("\n"))

        // --- Split Layout: Info vs Drawing ---
        val mainTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()

        // Column 1: Inputs & Results
        val infoCell = Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        infoCell.add(createStyledParagraph(t(context, "بيانات التصميم", "DESIGN DATA"), arabicFont, 12f, isBold = true).setFontColor(PRIMARY_COLOR))
        
        val dataTable = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
        inputs.forEach { (k, v) ->
            addArabicCell(dataTable, k, arabicFont, fontSize = 9f)
            addArabicCell(dataTable, v, arabicFont, isBold = true, fontSize = 9f)
        }
        results.forEach { (k, v) ->
            addArabicCell(dataTable, k, arabicFont, fontSize = 9f)
            addArabicCell(dataTable, v, arabicFont, isBold = true, fontSize = 9f)
        }
        infoCell.add(dataTable)
        mainTable.addCell(infoCell)

        // Column 2: Drawing
        val drawingCell = Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPaddingLeft(10f)
        drawingCell.add(createStyledParagraph(t(context, "رسم تسليح القطاع", "SECTION DRAWING"), arabicFont, 12f, isBold = true).setFontColor(PRIMARY_COLOR))
        if (drawingBitmap != null) {
            val stream = ByteArrayOutputStream()
            drawingBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setAutoScale(true)
            drawingCell.add(img)
        } else {
            drawingCell.add(Paragraph("[Drawing Placeholder]").setItalic().setFontColor(ColorConstants.GRAY))
        }
        mainTable.addCell(drawingCell)
        
        document.add(mainTable)
        document.add(Paragraph("\n"))

        // --- Safety Checks Table ---
        if (safetyChecks.isNotEmpty()) {
            document.add(createStyledParagraph(t(context, "تحققات الأمان", "SAFETY VERIFICATIONS"), arabicFont, 12f, isBold = true).setFontColor(PRIMARY_COLOR))
            val checkTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
            
            checkTable.addHeaderCell(Cell().add(createStyledParagraph("Check Type", arabicFont, 9f, true)))
            checkTable.addHeaderCell(Cell().add(createStyledParagraph("Calculated", arabicFont, 9f, true)))
            checkTable.addHeaderCell(Cell().add(createStyledParagraph("Limit", arabicFont, 9f, true)))
            checkTable.addHeaderCell(Cell().add(createStyledParagraph("Result", arabicFont, 9f, true)))

            safetyChecks.forEach { check ->
                addArabicCell(checkTable, check.name, arabicFont, fontSize = 9f)
                checkTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.2f %s", check.value, check.unit)).setFontSize(9f)))
                checkTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.2f %s", check.limit, check.unit)).setFontSize(9f)))
                val resText = if (check.isSafe) "PASS" else "FAIL"
                val resPara = createStyledParagraph(resText, arabicFont, 9f, isBold = true)
                    .setFontColor(if (check.isSafe) SUCCESS_COLOR else ERROR_COLOR)
                checkTable.addCell(Cell().add(resPara))
            }
            document.add(checkTable)
        }

        // --- Footer ---
        document.add(Paragraph("\n\n"))
        document.add(Paragraph("This report is generated based on Structural Design Codes (ECP/ACI/SBC). Professional review is mandatory before execution.")
            .setFontSize(8f).setItalic().setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))
        
        document.add(Paragraph(java.util.Date().toString())
            .setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))

        document.close()
        return file
    }

    fun generateSteelWarehouseReport(
        context: Context,
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        drawingBitmap: Bitmap? = null
    ): File {
        val fileName = "SteelWarehouse_Full_Design_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        val arabicFont = getArabicFont(context)

        // --- 1. Top Header & Logo Area ---
        val header = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
        val appNamePara = createStyledParagraph(context.getString(R.string.app_name), arabicFont, 22f, isBold = true)
            .setFontColor(PRIMARY_COLOR)
        header.addCell(Cell().add(appNamePara).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        
        val reportType = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        reportType.addCell(Cell().add(Paragraph("STEel WAREHOUSE STRUCTURE SHEET").setFontSize(10f).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        reportType.addCell(Cell().add(Paragraph("Technical Design & Feasibility Study").setFontSize(8f).setItalic().setTextAlignment(TextAlignment.RIGHT)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        header.addCell(Cell().add(reportType).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        document.add(header)
        document.add(LineSeparator(SolidLine(1.5f)))
        document.add(Paragraph("\n"))

        // --- 2. General Notes Section (Like the image) ---
        val topSection = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        
        val notesCell = Cell().setPadding(5f).setBorder(com.itextpdf.layout.borders.SolidBorder(0.5f))
        notesCell.add(createStyledParagraph(t(context, "ملاحظات عامة", "GENERAL NOTES"), arabicFont, 10f, true).setFontColor(PRIMARY_COLOR))
        val notes = arrayOf(
            "1. All dimensions are in METERS unless otherwise noted.",
            "2. All steel members shall be fabricated in accordance with ${inputs.code.name} code.",
            "3. All welding shall conform to AWS D1.1 (6mm minimum fillet).",
            "4. All bolts shall be high strength bolts ASTM A325 or equivalent.",
            "5. Provide 1% - 10% slope to roof for drainage as per design.",
            "6. Material Grade: Main frame (S355/St-52), Secondary (S235/St-37)."
        )
        notes.forEach { notesCell.add(Paragraph(it).setFontSize(8f)) }
        topSection.addCell(notesCell)

        // Project Summary Table
        val summaryCell = Cell().setPadding(5f).setBorder(com.itextpdf.layout.borders.SolidBorder(0.5f))
        summaryCell.add(createStyledParagraph("FEASIBILITY & MATERIAL SUMMARY", arabicFont, 10f, true).setFontColor(PRIMARY_COLOR))
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
        
        fun addSummaryRow(label: String, value: String) {
            summaryTable.addCell(Cell().add(createStyledParagraph(label, arabicFont, 8f)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
            summaryTable.addCell(Cell().add(createStyledParagraph(value, arabicFont, 8f, true)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        }
        
        addSummaryRow(t(context, "الوزن الكلي", "Total Steel Weight"), "%.2f Tons".format(result.totalWeight))
        addSummaryRow("Weight per Square Meter", "%.1f kg/m²".format(result.weightPerM2))
        addSummaryRow("Cost per Square Meter", "%.0f EGP/m²".format(result.costPerM2))
        addSummaryRow("Total Estimated Cost", "%,.0f EGP".format(result.estimatedTotalCost))
        addSummaryRow("Estimated Net Profit", "%,.0f EGP".format(result.netProfit))
        addSummaryRow("Expected ROI", "%.1f %%".format(result.roi))
        summaryCell.add(summaryTable)
        topSection.addCell(summaryCell)
        
        document.add(topSection)
        document.add(Paragraph("\n"))

        // --- 3. Steel Member Schedule (The core table in the image) ---
        document.add(createStyledParagraph(t(context, "جدول القطاعات الإنشائية", "STEEL MEMBER SCHEDULE"), arabicFont, 11f, true).setFontColor(PRIMARY_COLOR))
        val scheduleTable = Table(UnitValue.createPercentArray(floatArrayOf(10f, 25f, 45f, 20f))).useAllAvailableWidth()
        
        fun addSchedHeader(text: String) {
            scheduleTable.addHeaderCell(Cell().add(createStyledParagraph(text, arabicFont, 9f, true).setFontColor(ColorConstants.WHITE)).setBackgroundColor(SECONDARY_COLOR))
        }
        
        addSchedHeader("MARK")
        addSchedHeader("MEMBER")
        addSchedHeader("SECTION TYPE")
        addSchedHeader("MATERIAL")

        // Add Main Columns
        scheduleTable.addCell(Cell().add(Paragraph("C1").setFontSize(9f)))
        scheduleTable.addCell(Cell().add(createStyledParagraph("Main Columns", arabicFont, 9f)))
        scheduleTable.addCell(Cell().add(createStyledParagraph(result.mainFrame.columnSection.displayName, arabicFont, 9f, true)))
        scheduleTable.addCell(Cell().add(Paragraph("ASTM A572 Gr 50").setFontSize(8f)))

        // Add Rafters
        scheduleTable.addCell(Cell().add(Paragraph("R1").setFontSize(9f)))
        scheduleTable.addCell(Cell().add(createStyledParagraph("Main Rafters", arabicFont, 9f)))
        scheduleTable.addCell(Cell().add(createStyledParagraph(result.mainFrame.rafterSection.displayName, arabicFont, 9f, true)))
        scheduleTable.addCell(Cell().add(Paragraph("ASTM A572 Gr 50").setFontSize(8f)))

        // Add Purlins
        scheduleTable.addCell(Cell().add(Paragraph("P1").setFontSize(9f)))
        scheduleTable.addCell(Cell().add(createStyledParagraph("Roof Purlins", arabicFont, 9f)))
        scheduleTable.addCell(Cell().add(createStyledParagraph(result.secondaryMembers.purlinSection.displayName, arabicFont, 9f, true)))
        scheduleTable.addCell(Cell().add(Paragraph("ASTM A653 Gr 50").setFontSize(8f)))

        // Add Bracing
        scheduleTable.addCell(Cell().add(Paragraph("B1").setFontSize(9f)))
        scheduleTable.addCell(Cell().add(createStyledParagraph("Bracing System", arabicFont, 9f)))
        scheduleTable.addCell(Cell().add(createStyledParagraph(result.secondaryMembers.bracingSection.displayName, arabicFont, 9f, true)))
        scheduleTable.addCell(Cell().add(Paragraph("ASTM A36").setFontSize(8f)))

        document.add(scheduleTable)
        document.add(Paragraph("\n"))

        // --- 4. Main Drawing / Visualization Area ---
        val drawingSection = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        drawingSection.addCell(Cell().add(createStyledParagraph("TYPICAL CROSS SECTION & 3D ISOMETRIC VIEW", arabicFont, 11f, true).setFontColor(PRIMARY_COLOR)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        
        if (drawingBitmap != null) {
            val stream = ByteArrayOutputStream()
            drawingBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setAutoScale(true).setHorizontalAlignment(HorizontalAlignment.CENTER)
            drawingSection.addCell(Cell().add(img).setPadding(10f).setBorder(com.itextpdf.layout.borders.SolidBorder(0.5f)))
        } else {
            drawingSection.addCell(Cell().add(Paragraph("[ENGINEERING DRAWING NOT GENERATED]").setItalic().setTextAlignment(TextAlignment.CENTER)).setPadding(40f))
        }
        document.add(drawingSection)
        document.add(Paragraph("\n"))

        // --- 5. Project Geometry Details ---
        val geomTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        geomTable.addCell(createDataCell("Building Span (B)", "${inputs.span} m", arabicFont))
        geomTable.addCell(createDataCell("Total Length (L)", "${inputs.length} m", arabicFont))
        geomTable.addCell(createDataCell("Eave Height (h1)", "${inputs.eaveHeight} m", arabicFont))
        geomTable.addCell(createDataCell("Ridge Height (h2)", "${inputs.ridgeHeight} m", arabicFont))
        geomTable.addCell(createDataCell("Bay Spacing (s)", "${inputs.baySpacing} m", arabicFont))
        geomTable.addCell(createDataCell("Roof Slope", "%.1f %%".format(inputs.slope * 100), arabicFont))
        document.add(geomTable)

        // --- 6. Analysis Results & Safety ---
        document.add(Paragraph("\n"))
        val statusText = if (result.safetyStatus) t(context, "المنشأ آمن - التحليل الإنشائي ناجح", "STATUS: STRUCTURAL ANALYSIS PASSED") else t(context, "مراجعة إنشائية مطلوبة", "STATUS: REVIEW REQUIRED")
        val statusPara = createStyledParagraph(statusText, arabicFont, 10f, true)
            .setFontColor(if (result.safetyStatus) SUCCESS_COLOR else ERROR_COLOR)
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(5f).setBorder(com.itextpdf.layout.borders.SolidBorder(if (result.safetyStatus) SUCCESS_COLOR else ERROR_COLOR, 1f))
        document.add(statusPara)

        // --- 7. Title Block (Bottom of page like engineering sheets) ---
        document.add(AreaBreak()) // Title block on new page or bottom of first
        
        val titleBlock = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
        titleBlock.setBorder(com.itextpdf.layout.borders.SolidBorder(1f))
        
        // Project Info
        val projInfoCell = Cell(1, 1).setPadding(5f)
        projInfoCell.add(Paragraph("PROJECT:").setFontSize(7f).setBold())
        projInfoCell.add(createStyledParagraph("Steel Warehouse Industrial Building", arabicFont, 10f, true))
        projInfoCell.add(Paragraph("LOCATION: Industrial Zone - Cairo, Egypt").setFontSize(7f))
        titleBlock.addCell(projInfoCell)
        
        // Designer Info
        val designCell = Cell(1, 1).setPadding(5f)
        designCell.add(Paragraph("DESIGNED BY:").setFontSize(7f))
        designCell.add(createStyledParagraph("Civil EG Pro Engine", arabicFont, 9f, true))
        titleBlock.addCell(designCell)
        
        // Code & Scale
        val codeCell = Cell(1, 1).setPadding(5f)
        codeCell.add(Paragraph("DESIGN CODE:").setFontSize(7f))
        codeCell.add(Paragraph(inputs.code.displayName).setFontSize(9f).setBold())
        codeCell.add(Paragraph("SCALE: AS SHOWN").setFontSize(7f))
        titleBlock.addCell(codeCell)
        
        // Date & Rev
        val dateCell = Cell(1, 1).setPadding(5f)
        dateCell.add(Paragraph("DATE:").setFontSize(7f))
        dateCell.add(Paragraph(SimpleDateFormat("MMM yyyy", Locale.US).format(java.util.Date())).setFontSize(9f).setBold())
        dateCell.add(Paragraph("SHEET: S-01").setFontSize(7f).setBold())
        titleBlock.addCell(dateCell)
        
        document.add(titleBlock)

        document.close()
        return file
    }

    fun generateSteelSectionReport(
        context: Context,
        result: SteelMemberResult,
        drawingBitmap: Bitmap? = null
    ): File {
        val fileName = "SteelSection_Analysis_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        val arabicFont = getArabicFont(context)

        // --- Header ---
        val header = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f))).useAllAvailableWidth()
        val appNamePara = createStyledParagraph(context.getString(R.string.app_name), arabicFont, 24f, true)
            .setFontColor(PRIMARY_COLOR)
        header.addCell(Cell().add(appNamePara).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        header.addCell(Cell().add(Paragraph("STEEL SECTION ANALYSIS").setTextAlignment(TextAlignment.RIGHT).setFontSize(10f).setFontColor(ColorConstants.GRAY)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        document.add(header)
        document.add(LineSeparator(SolidLine(1f)))

        document.add(Paragraph("\n"))
        document.add(Paragraph("Technical Datasheet: ${result.sectionType.displayName}").setFontSize(18f).setBold().setTextAlignment(TextAlignment.CENTER))
        
        // Status Card
        val statusText = if (result.isSafe) "SECTION IS SAFE ✅" else "SECTION IS UNSAFE ❌"
        document.add(Paragraph(statusText).setFontColor(if (result.isSafe) SUCCESS_COLOR else ERROR_COLOR).setBold().setPadding(10f).setBorder(com.itextpdf.layout.borders.SolidBorder(if (result.isSafe) SUCCESS_COLOR else ERROR_COLOR, 1f)).setTextAlignment(TextAlignment.CENTER))

        document.add(Paragraph("\n"))

        // --- Key Properties Table ---
        document.add(createStyledParagraph(t(context, "الخصائص الهندسية", "Geometric Properties"), arabicFont, 12f, true).setFontColor(PRIMARY_COLOR))
        
        val propTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        propTable.addCell(createDataCell("Area (A)", "%.2f cm²".format(result.sectionType.area/100.0), arabicFont))
        propTable.addCell(createDataCell("Weight (W)", "%.2f kg/m".format(result.weight), arabicFont))
        
        val section = result.sectionType
        if (section is SteelSectionType.ISection) {
            propTable.addCell(createDataCell("Depth (h)", "${section.h} mm", arabicFont))
            propTable.addCell(createDataCell("Flange Width (bf)", "${section.bf} mm", arabicFont))
            propTable.addCell(createDataCell("Web Thickness (tw)", "${section.tw} mm", arabicFont))
            propTable.addCell(createDataCell("Flange Thickness (tf)", "${section.tf} mm", arabicFont))
        }
        document.add(propTable)

        document.add(Paragraph("\n"))

        // --- Design Capacities ---
        document.add(createStyledParagraph(t(context, "سعة التحمل القصوى", "Design Capacities"), arabicFont, 12f, true).setFontColor(PRIMARY_COLOR))
        
        val capacityTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        capacityTable.addCell(createDataCell("Axial Capacity (Pn)", "%.1f kN".format(result.axialCapacity), arabicFont))
        capacityTable.addCell(createDataCell("Flexural Capacity (Mn)", "%.1f kN.m".format(result.flexuralCapacity), arabicFont))
        capacityTable.addCell(createDataCell("Shear Capacity (Vn)", "%.1f kN".format(result.shearCapacity), arabicFont))
        capacityTable.addCell(createDataCell("Utilization Ratio", "%.2f%%".format(result.utilizationRatio * 100), arabicFont))
        document.add(capacityTable)

        // --- Drawing Section ---
        if (drawingBitmap != null) {
            document.add(Paragraph("\n"))
            document.add(createStyledParagraph(t(context, "رسم تخطيطي", "Section Visualization"), arabicFont, 12f, true).setFontColor(PRIMARY_COLOR))
            val stream = ByteArrayOutputStream()
            drawingBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setMaxWidth(200f).setHorizontalAlignment(HorizontalAlignment.CENTER)
            document.add(img)
        }

        // --- Buckling & Stability ---
        result.bucklingCheck?.let { check ->
            document.add(createStyledParagraph(t(context, "تحليل الانبعاج", "Buckling & Stability Analysis"), arabicFont, 12f, true).setFontColor(PRIMARY_COLOR))
            
            val buckTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
            buckTable.addCell(createDataCell("Slenderness Ratio (λ)", "%.2f".format(check.slendernessRatio), arabicFont))
            buckTable.addCell(createDataCell("Critical Stress (Fcr)", "%.1f MPa".format(check.criticalStress), arabicFont))
            buckTable.addCell(createDataCell("Buckling Mode", check.bucklingMode.name, arabicFont))
            buckTable.addCell(createDataCell("Stability Status", if (check.isSafe) "Stable" else "Unstable", arabicFont))
            document.add(buckTable)
        }

        // --- Warnings & Notes ---
        if (result.warnings.isNotEmpty()) {
            document.add(createStyledParagraph(t(context, "تحذيرات فنية", "Warnings"), arabicFont, 12f, true).setFontColor(ERROR_COLOR))
            val warningList = com.itextpdf.layout.element.List()
            result.warnings.forEach { warning -> 
                val li = ListItem(warning)
                if (containsArabic(warning)) {
                    li.setFont(arabicFont).setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                }
                warningList.add(li) 
            }
            document.add(warningList)
        }

        // --- Footer ---
        document.add(Paragraph("\n\n"))
        document.add(Paragraph("Detailed technical analysis provided by CivilEG Pro Engine. This report is for engineering guidance only.")
            .setFontSize(8f).setItalic().setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph("Generated: ${java.util.Date()}").setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))

        document.close()
        return file
    }

    fun generateBOQReport(
        context: Context,
        projectName: String,
        totalBudget: Double,
        concreteVol: Double,
        steelWeight: Double,
        items: kotlin.collections.List<Pair<String, Double>>
    ): File {
        val fileName = "CivilEngPro_BOQ_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        val document = Document(PdfDocument(PdfWriter(file)))
        val arabicFont = getArabicFont(context)

        createReportHeader(
            document, context, arabicFont,
            t(context, "كشف كميات وتكاليف تقديري", "Comprehensive BOQ Report"),
            t(context, "تقرير كميات وتكاليف", "Bill of Quantities & Cost Estimate")
        )

        val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        addArabicCell(infoTable, t(context, "اسم المشروع", "Project Name"), arabicFont, fontSize = 10f)
        addArabicCell(infoTable, projectName, arabicFont, isBold = true, fontSize = 10f)
        addArabicCell(infoTable, t(context, "التاريخ", "Report Date"), arabicFont, fontSize = 10f)
        addArabicCell(infoTable, SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()), arabicFont, isBold = true, fontSize = 10f)
        document.add(infoTable)
        document.add(Paragraph("\n"))

        document.add(createStyledParagraph(t(context, "ملخص المشروع", "PROJECT SUMMARY"), arabicFont, 12f, isBold = true).setFontColor(PRIMARY_COLOR))
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(34f, 33f, 33f))).useAllAvailableWidth()
        summaryTable.addCell(createSummaryCell(t(context, "إجمالي الميزانية التقديرية", "Total Estimated Budget"), String.format(Locale.US, "%,.2f EGP", totalBudget), arabicFont))
        summaryTable.addCell(createSummaryCell(t(context, "حجم الخرسانة الكلي", "Total Concrete Volume"), String.format(Locale.US, "%.2f m\u00B3", concreteVol), arabicFont))
        summaryTable.addCell(createSummaryCell(t(context, "وزن الحديد الكلي", "Total Steel Weight"), String.format(Locale.US, "%.2f Tons", steelWeight / 1000.0), arabicFont))
        document.add(summaryTable)
        document.add(Paragraph("\n"))

        document.add(createStyledParagraph(t(context, "تفاصيل التكاليف", "DETAILED COST BREAKDOWN"), arabicFont, 12f, isBold = true).setFontColor(PRIMARY_COLOR))
        val itemTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f))).useAllAvailableWidth()
        itemTable.addHeaderCell(createHeaderCell(t(context, "البند / اسم التصميم", "Item / Design Name"), arabicFont))
        itemTable.addHeaderCell(createHeaderCell(t(context, "التكلفة (ج.م)", "Cost (EGP)"), arabicFont))
        items.forEach { (name, price) ->
            addArabicCell(itemTable, name, arabicFont, fontSize = 9f)
            itemTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%,.0f", price)).setFontSize(9f).setTextAlignment(TextAlignment.RIGHT)))
        }
        document.add(itemTable)

        document.add(Paragraph("\n\n"))
        document.add(Paragraph("This report is generated based on Structural Design Codes (ECP/ACI/SBC). Professional review is mandatory before execution.")
            .setFontSize(8f).setItalic().setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph(Date().toString()).setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))

        document.close()
        return file
    }

    fun generateEstimationReport(
        context: Context,
        result: EstimationEngine.EstimationResult
    ): File {
        val fileName = "CivilEngPro_Estimation_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        val document = Document(PdfDocument(PdfWriter(file)))
        val arabicFont = getArabicFont(context)

        createReportHeader(
            document, context, arabicFont,
            t(context, "تقرير تقدير التكاليف ودراسة الجدوى", "ESTIMATION & FEASIBILITY REPORT"),
            t(context, "دراسة جدوى تقديرية", "Cost Estimate & Feasibility Study")
        )

        document.add(createStyledParagraph(t(context, "تقدير إجمالي التكلفة", "Total Project Cost Estimate"), arabicFont, 12f)
            .setFontColor(ColorConstants.GRAY))
        document.add(createStyledParagraph(String.format(Locale.US, "%,.0f %s", result.totalCost, result.currencySymbol), arabicFont, 26f, isBold = true)
            .setFontColor(PRIMARY_COLOR))
        document.add(Paragraph("\n"))

        document.add(createStyledParagraph(t(context, "كشف البنود", "DETAILED QUANTITIES & COSTS"), arabicFont, 12f, isBold = true)
            .setFontColor(PRIMARY_COLOR))
        val itemTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
        itemTable.addHeaderCell(createHeaderCell("Description", arabicFont))
        itemTable.addHeaderCell(createHeaderCell("Quantity", arabicFont))
        itemTable.addHeaderCell(createHeaderCell("Unit Price", arabicFont))
        itemTable.addHeaderCell(createHeaderCell("Total", arabicFont))
        result.items.forEach { item ->
            addArabicCell(itemTable, item.name, arabicFont, fontSize = 9f)
            itemTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.1f %s", item.quantity, item.unit)).setFontSize(9f)))
            itemTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%,.0f", item.unitPrice)).setFontSize(9f)))
            itemTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%,.0f", item.totalPrice)).setFontSize(9f).setTextAlignment(TextAlignment.RIGHT)))
        }
        document.add(itemTable)

        result.investmentData?.let { invest ->
            document.add(Paragraph("\n"))
            document.add(createStyledParagraph(t(context, "دراسة الجدوى والاستثمار", "FEASIBILITY & INVESTMENT ANALYSIS"), arabicFont, 12f, isBold = true)
                .setFontColor(PRIMARY_COLOR))
            val investTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
            if (invest.landCost > 0) {
                addArabicCell(investTable, t(context, "سعر الأرض", "Land Cost"), arabicFont, fontSize = 10f)
                addArabicCell(investTable, String.format(Locale.US, "%,.0f %s", invest.landCost, result.currencySymbol), arabicFont, isBold = true, fontSize = 10f)
            }
            addArabicCell(investTable, t(context, "تكلفة البناء", "Construction Cost"), arabicFont, fontSize = 10f)
            addArabicCell(investTable, String.format(Locale.US, "%,.0f %s", invest.constructionCost, result.currencySymbol), arabicFont, isBold = true, fontSize = 10f)
            addArabicCell(investTable, t(context, "صافي الربح", "Expected Net Profit"), arabicFont, fontSize = 10f)
            addArabicCell(investTable, String.format(Locale.US, "%,.0f %s", invest.netProfit, result.currencySymbol), arabicFont, isBold = true, fontSize = 10f)
            addArabicCell(investTable, "ROI", arabicFont, fontSize = 10f)
            addArabicCell(investTable, String.format(Locale.US, "%.1f %%", invest.roi), arabicFont, isBold = true, fontSize = 10f)
            addArabicCell(investTable, t(context, "هامش الربح", "Profit Margin"), arabicFont, fontSize = 10f)
            addArabicCell(investTable, String.format(Locale.US, "%.1f %%", invest.profitMargin), arabicFont, isBold = true, fontSize = 10f)
            addArabicCell(investTable, t(context, "مدة التنفيذ", "Construction Duration"), arabicFont, fontSize = 10f)
            addArabicCell(investTable, "${invest.constructionDurationMonths} Months", arabicFont, isBold = true, fontSize = 10f)
            document.add(investTable)
        }

        document.add(Paragraph("\n"))
        document.add(Paragraph(Date().toString()).setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))

        document.close()
        return file
    }
    // Adding legacy methods for compatibility
    fun generateDesignReport(
        context: Context,
        title: String,
        designType: String,
        inputs: Map<String, String>,
        results: Map<String, String>,
        isSafe: Boolean
    ): File {
        return generateProfessionalReport(context, title, designType, inputs, results, emptyList(), isSafe, null)
    }

    /**
     * Generate a PDF report for inventory items.
     * Lists all materials/equipment with quantities, units, alert thresholds, and notes.
     */
    fun generateInventoryReport(
        context: Context,
        items: List<com.civileg.app.db.InventoryItem>,
        title: String
    ): File {
        val fileName = "CivilEG_Inventory_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        val document = Document(PdfDocument(PdfWriter(file)))
        val arabicFont = getArabicFont(context)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        createReportHeader(
            document, context, arabicFont,
            t(context, "تقرير جرد المواد والمعدات", "INVENTORY REPORT"),
            "${t(context, "تم الإنشاء", "Generated")}: ${dateFormat.format(Date())}  \u2022  ${t(context, "إجمالي البنود", "Total Items")}: ${items.size}"
        )

        val headers = if (isEnglish(context)) {
            arrayOf("#", "Item Name", "Type", "Quantity", "Unit", "Alert", "Notes")
        } else {
            arrayOf("#", "اسم البند", "النوع", "الكمية", "الوحدة", "تنبيه", "ملاحظات")
        }
        val table = Table(UnitValue.createPercentArray(floatArrayOf(6f, 24f, 14f, 12f, 10f, 10f, 24f))).useAllAvailableWidth()
        headers.forEach { table.addHeaderCell(createHeaderCell(it, arabicFont)) }

        items.forEachIndexed { idx, item ->
            val isLowStock = item.alertQuantity > 0 && item.quantity <= item.alertQuantity
            val rowCells = ArrayList<Cell>(7)
            rowCells.add(Cell().add(Paragraph("${idx + 1}").setFontSize(8f)))

            val truncatedName = if (item.name.length > 28) item.name.take(25) + "..." else item.name
            rowCells.add(Cell().add(createStyledParagraph(truncatedName, arabicFont, 8f, isBold = isLowStock)))

            val typeName = if (isEnglish(context)) item.type.name else when (item.type) {
                com.civileg.app.db.InventoryType.EQUIPMENT -> "معدات ثقيلة"
                com.civileg.app.db.InventoryType.TOOLS -> "أدوات"
                com.civileg.app.db.InventoryType.RAW_MATERIAL -> "خامات"
                com.civileg.app.db.InventoryType.ACCESSORIES -> "إكسسوارات"
            }
            rowCells.add(Cell().add(createStyledParagraph(typeName, arabicFont, 8f)))

            rowCells.add(Cell().add(Paragraph(String.format(Locale.US, "%.2f", item.quantity)).setFontSize(8f)))
            rowCells.add(Cell().add(createStyledParagraph(item.unit, arabicFont, 8f)))

            val alertText = if (item.alertQuantity > 0) String.format(Locale.US, "%.0f", item.alertQuantity) else "-"
            rowCells.add(Cell().add(Paragraph(alertText).setFontSize(8f)))

            val notes = if (item.notes.length > 25) item.notes.take(22) + "..." else if (item.notes.isEmpty()) "-" else item.notes
            val notesCell = Cell().add(createStyledParagraph(notes, arabicFont, 8f))
            if (isLowStock) notesCell.add(createStyledParagraph(t(context, "⚠ منخفض", "LOW STOCK"), arabicFont, 7f, isBold = true).setFontColor(ERROR_COLOR))
            rowCells.add(notesCell)

            if (isLowStock) rowCells.forEach { it.setFontColor(ERROR_COLOR) }
            rowCells.forEach { table.addCell(it) }
        }
        document.add(table)

        document.add(Paragraph("\n"))
        document.add(createStyledParagraph(t(context, "ملخص حسب النوع", "SUMMARY BY TYPE"), arabicFont, 12f, isBold = true)
            .setFontColor(PRIMARY_COLOR))
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        items.groupBy { it.type }.forEach { (type, itemsOfType) ->
            summaryTable.addCell(Cell().add(createStyledParagraph("• ${type.name}: ${itemsOfType.size}", arabicFont, 9f)))
        }
        document.add(summaryTable)

        val lowStockItems = items.filter { it.alertQuantity > 0 && it.quantity <= it.alertQuantity }
        if (lowStockItems.isNotEmpty()) {
            document.add(Paragraph("\n"))
            document.add(createStyledParagraph(t(context, "تنبيهات المخزون المنخفض", "LOW STOCK ALERTS") + " (${lowStockItems.size})", arabicFont, 12f, isBold = true)
                .setFontColor(ERROR_COLOR))
            val alertTable = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
            lowStockItems.forEach { item ->
                val line = "• ${item.name}: ${String.format(Locale.US, "%.2f", item.quantity)} ${item.unit} (alert: ${String.format(Locale.US, "%.0f", item.alertQuantity)})"
                alertTable.addCell(Cell().add(createStyledParagraph(line, arabicFont, 9f, isBold = true)).setFontColor(ERROR_COLOR))
            }
            document.add(alertTable)
        }

        document.add(Paragraph("\n"))
        document.add(Paragraph(Date().toString()).setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))

        document.close()
        return file
    }

    /** One row of a water-level survey table: point name, type, staff reading, reduced level. */
    data class WaterLevelRow(val point: String, val type: String, val reading: String, val rl: String)

    /**
     * Generic tabular survey report (water level / leveling).
     * iText-based so it shares the Arabic-shaping path of every other report
     * instead of the native android.graphics.pdf.PdfDocument + Canvas pipeline.
     */
    fun generateWaterLevelReport(
        context: Context,
        title: String,
        summary: List<Pair<String, String>>,
        headers: List<String>,
        rows: List<WaterLevelRow>,
        footer: String? = null
    ): File {
        val fileName = "Water_Level_Survey_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        val document = Document(PdfDocument(PdfWriter(file)))
        val arabicFont = getArabicFont(context)

        document.add(createStyledParagraph(title, arabicFont, 18f, isBold = true)
            .setTextAlignment(TextAlignment.CENTER).setFontColor(PRIMARY_COLOR))
        document.add(LineSeparator(SolidLine(1f)))
        document.add(Paragraph("\n"))

        if (summary.isNotEmpty()) {
            val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
            summary.forEach { (label, value) ->
                addArabicCell(summaryTable, label, arabicFont, fontSize = 10f)
                addArabicCell(summaryTable, value, arabicFont, isBold = true, fontSize = 10f)
            }
            document.add(summaryTable)
            document.add(Paragraph("\n"))
        }

        val table = Table(UnitValue.createPercentArray(floatArrayOf(15f, 30f, 25f, 30f))).useAllAvailableWidth()
        headers.take(4).forEach { table.addHeaderCell(createHeaderCell(it, arabicFont)) }
        rows.forEach { row ->
            table.addCell(Cell().add(Paragraph(row.point).setFontSize(9f).setTextAlignment(TextAlignment.CENTER)))
            addArabicCell(table, row.type, arabicFont, fontSize = 9f)
            table.addCell(Cell().add(Paragraph(row.reading).setFontSize(9f).setTextAlignment(TextAlignment.CENTER)))
            table.addCell(Cell().add(Paragraph(row.rl).setFontSize(9f).setTextAlignment(TextAlignment.CENTER)))
        }
        document.add(table)

        if (!footer.isNullOrBlank()) {
            document.add(Paragraph("\n"))
            document.add(createStyledParagraph(footer, arabicFont, 10f, isBold = true).setFontColor(PRIMARY_COLOR))
        }

        document.add(Paragraph("\n"))
        document.add(Paragraph(Date().toString()).setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))

        document.close()
        return file
    }
}
