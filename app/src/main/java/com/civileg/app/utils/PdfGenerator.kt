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
    
    // ... (rest of the file until generateBOQReport)

    private fun getArabicFont(context: Context): PdfFont {
        return ArabicFontProvider.getArabicPdfFont(context)
    }

    private fun containsArabic(text: String): Boolean {
        return ArabicFontProvider.containsArabic(text)
    }

    private fun createStyledParagraph(text: String, font: PdfFont?, fontSize: Float = 12f, isBold: Boolean = false): Paragraph {
        val p = Paragraph(text).setFontSize(fontSize)
        if (isBold) p.setBold()
        if (font != null && containsArabic(text)) {
            p.setFont(font)
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
        }
        return p
    }

    private fun addArabicCell(table: Table, text: String, font: PdfFont?, isBold: Boolean = false, fontSize: Float = 9f) {
        val p = createStyledParagraph(text, font, fontSize, isBold)
        table.addCell(Cell().add(p))
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

        val statusText = if (isSafe) "STATUS: SAFE (مطابق للأكواد)" else "STATUS: UNSAFE (غير آمن - مراجعة التصميم)"
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
        infoCell.add(createStyledParagraph("DESIGN DATA", arabicFont, 12f, isBold = true).setFontColor(PRIMARY_COLOR))
        
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
        drawingCell.add(createStyledParagraph("SECTION DRAWING (رسم تسليح القطاع)", arabicFont, 12f, isBold = true).setFontColor(PRIMARY_COLOR))
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
            document.add(createStyledParagraph("SAFETY VERIFICATIONS (تحققات الأمان)", arabicFont, 12f, isBold = true).setFontColor(PRIMARY_COLOR))
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
        notesCell.add(createStyledParagraph("GENERAL NOTES (ملاحظات عامة)", arabicFont, 10f, true).setFontColor(PRIMARY_COLOR))
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
        
        addSummaryRow("Total Steel Weight (الوزن الكلي)", "%.2f Tons".format(result.totalWeight))
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
        document.add(createStyledParagraph("STEEL MEMBER SCHEDULE (جدول القطاعات الإنشائية)", arabicFont, 11f, true).setFontColor(PRIMARY_COLOR))
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
        val statusText = if (result.safetyStatus) "STATUS: STRUCTURAL ANALYSIS PASSED (المنشأ آمن)" else "STATUS: REVIEW REQUIRED (مراجعة إنشائية)"
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
        document.add(createStyledParagraph("Geometric Properties (الخصائص الهندسية)", arabicFont, 12f, true).setFontColor(PRIMARY_COLOR))
        
        val propTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        propTable.addCell(createDataCell("Area (A)", "%.2f cm²".format(result.sectionType.getArea()/100.0), arabicFont))
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
        document.add(createStyledParagraph("Design Capacities (سعة التحمل القصوى)", arabicFont, 12f, true).setFontColor(PRIMARY_COLOR))
        
        val capacityTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        capacityTable.addCell(createDataCell("Axial Capacity (Pn)", "%.1f kN".format(result.axialCapacity), arabicFont))
        capacityTable.addCell(createDataCell("Flexural Capacity (Mn)", "%.1f kN.m".format(result.flexuralCapacity), arabicFont))
        capacityTable.addCell(createDataCell("Shear Capacity (Vn)", "%.1f kN".format(result.shearCapacity), arabicFont))
        capacityTable.addCell(createDataCell("Utilization Ratio", "%.2f%%".format(result.utilizationRatio * 100), arabicFont))
        document.add(capacityTable)

        // --- Drawing Section ---
        if (drawingBitmap != null) {
            document.add(Paragraph("\n"))
            document.add(createStyledParagraph("Section Visualization (رسم تخطيطي)", arabicFont, 12f, true).setFontColor(PRIMARY_COLOR))
            val stream = ByteArrayOutputStream()
            drawingBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setMaxWidth(200f).setHorizontalAlignment(HorizontalAlignment.CENTER)
            document.add(img)
        }

        // --- Buckling & Stability ---
        result.bucklingCheck?.let { check ->
            document.add(createStyledParagraph("\nBuckling & Stability Analysis (تحليل الانبعاج)", arabicFont, 12f, true).setFontColor(PRIMARY_COLOR))
            
            val buckTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
            buckTable.addCell(createDataCell("Slenderness Ratio (λ)", "%.2f".format(check.slendernessRatio), arabicFont))
            buckTable.addCell(createDataCell("Critical Stress (Fcr)", "%.1f MPa".format(check.criticalStress), arabicFont))
            buckTable.addCell(createDataCell("Buckling Mode", check.bucklingMode.name, arabicFont))
            buckTable.addCell(createDataCell("Stability Status", if (check.isSafe) "Stable" else "Unstable", arabicFont))
            document.add(buckTable)
        }

        // --- Warnings & Notes ---
        if (result.warnings.isNotEmpty()) {
            document.add(createStyledParagraph("\nWarnings (تحذيرات فنية)", arabicFont, 12f, true).setFontColor(ERROR_COLOR))
            val warningList = com.itextpdf.layout.element.List()
            result.warnings.forEach { warning -> 
                val li = ListItem(warning)
                if (containsArabic(warning) && arabicFont != null) {
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
        
        val pdf = android.graphics.pdf.PdfDocument()
        val helper = PdfLayoutHelper(context)
        helper.startNewDocument(pdf)
        helper.startNewPage()
        
        val canvas = helper.currentPage!!.canvas
        var y = helper.currentY + 20f
        
        val titlePaint = android.graphics.Paint().apply {
            color = helper.colorPrimary
            textSize = 24f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        helper.drawText(canvas, "Comprehensive BOQ Report", PdfLayoutHelper.PAGE_WIDTH / 2f, y, titlePaint)
        y += 30f
        
        val subTitlePaint = android.graphics.Paint().apply {
            color = helper.colorSecondary
            textSize = 14f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        helper.drawText(canvas, "(كشف كميات وتكاليف تقديري)", PdfLayoutHelper.PAGE_WIDTH / 2f, y, subTitlePaint)
        y += 40f
        
        canvas.drawLine(PdfLayoutHelper.MARGIN, y, PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN, y, android.graphics.Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 1f })
        y += 30f
        
        val labelPaint = android.graphics.Paint().apply { textSize = 11f; color = helper.colorGray }
        val valuePaint = android.graphics.Paint().apply { textSize = 11f; isFakeBoldText = true; color = helper.colorText }
        
        helper.drawInfoRow(canvas, "Project Name (اسم المشروع)", projectName, y, labelPaint, valuePaint); y += 25f
        helper.drawInfoRow(canvas, "Report Date (التاريخ)", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()), y, labelPaint, valuePaint); y += 40f
        
        // Summary Table
        val bgPaint = android.graphics.Paint().apply { color = helper.colorLightGray }
        canvas.drawRect(PdfLayoutHelper.MARGIN, y, PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN, y + 80f, bgPaint)
        y += 20f
        
        val mainLabelPaint = android.graphics.Paint().apply { textSize = 12f; isFakeBoldText = true; color = helper.colorText }
        val mainValuePaint = android.graphics.Paint().apply { textSize = 16f; isFakeBoldText = true; color = helper.colorPrimary }
        
        helper.drawInfoRow(canvas, "Total Estimated Budget", String.format(Locale.US, "%,.2f EGP", totalBudget), y, mainLabelPaint, mainValuePaint); y += 30f
        
        val normalLabelPaint = android.graphics.Paint().apply { textSize = 11f; color = helper.colorText }
        helper.drawInfoRow(canvas, "Total Concrete Volume", String.format(Locale.US, "%.2f m³", concreteVol), y, normalLabelPaint, normalLabelPaint); y += 20f
        helper.drawInfoRow(canvas, "Total Steel Weight", String.format(Locale.US, "%.2f Tons", steelWeight / 1000.0), y, normalLabelPaint, normalLabelPaint); y += 60f
        
        // Detailed Breakdown
        val sectionPaint = android.graphics.Paint().apply { textSize = 14f; isFakeBoldText = true; color = helper.colorPrimary }
        helper.drawText(canvas, "DETAILED COST BREAKDOWN (تفاصيل التكاليف)", PdfLayoutHelper.MARGIN, y, sectionPaint)
        y += 30f
        
        val headerBgPaint = android.graphics.Paint().apply { color = helper.colorPrimary }
        canvas.drawRect(PdfLayoutHelper.MARGIN, y, PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN, y + 25f, headerBgPaint)
        
        val headerTextPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 10f; isFakeBoldText = true }
        helper.drawText(canvas, "Item / Design Name", PdfLayoutHelper.MARGIN + 10f, y + 17f, headerTextPaint)
        headerTextPaint.textAlign = android.graphics.Paint.Align.RIGHT
        helper.drawText(canvas, "Cost (EGP)", PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN - 10f, y + 17f, headerTextPaint)
        y += 35f
        
        val itemPaint = android.graphics.Paint().apply { textSize = 10f; color = helper.colorText }
        items.forEach { (name, price) ->
            if (y > PdfLayoutHelper.PAGE_HEIGHT - PdfLayoutHelper.MARGIN - 40f) {
                helper.finishCurrentPage()
                helper.startNewPage()
                y = helper.currentY + 20f
            }
            val currentCanvas = helper.currentPage!!.canvas
            helper.drawText(currentCanvas, name, PdfLayoutHelper.MARGIN + 10f, y, itemPaint)
            itemPaint.textAlign = android.graphics.Paint.Align.RIGHT
            helper.drawText(currentCanvas, String.format(Locale.US, "%,.0f", price), PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN - 10f, y, itemPaint)
            itemPaint.textAlign = android.graphics.Paint.Align.LEFT
            
            currentCanvas.drawLine(PdfLayoutHelper.MARGIN, y + 5f, PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN, y + 5f, android.graphics.Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 0.5f })
            y += 25f
        }
        
        helper.finishCurrentPage()
        pdf.writeTo(FileOutputStream(file))
        pdf.close()
        return file
    }

    fun generateEstimationReport(
        context: Context,
        result: EstimationEngine.EstimationResult
    ): File {
        val fileName = "CivilEngPro_Estimation_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)

        val pdf = android.graphics.pdf.PdfDocument()
        val helper = PdfLayoutHelper(context)
        helper.startNewDocument(pdf)
        helper.startNewPage()
        
        val canvas = helper.currentPage!!.canvas
        var y = helper.currentY + 20f
        
        val titlePaint = android.graphics.Paint().apply {
            color = helper.colorPrimary
            textSize = 24f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        helper.drawText(canvas, "ESTIMATION & FEASIBILITY REPORT", PdfLayoutHelper.PAGE_WIDTH / 2f, y, titlePaint)
        y += 30f
        
        val subTitlePaint = android.graphics.Paint().apply {
            color = helper.colorSecondary
            textSize = 14f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        helper.drawText(canvas, "(تقرير تقدير التكاليف ودراسة الجدوى)", PdfLayoutHelper.PAGE_WIDTH / 2f, y, subTitlePaint)
        y += 40f
        
        canvas.drawLine(PdfLayoutHelper.MARGIN, y, PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN, y, android.graphics.Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 1f })
        y += 40f
        
        val summaryLabelPaint = android.graphics.Paint().apply { textSize = 14f; color = helper.colorGray }
        helper.drawText(canvas, "Total Project Cost Estimate (تقدير إجمالي التكلفة)", PdfLayoutHelper.MARGIN, y, summaryLabelPaint)
        y += 40f
        
        val totalCostPaint = android.graphics.Paint().apply { textSize = 32f; isFakeBoldText = true; color = helper.colorPrimary }
        helper.drawText(canvas, String.format(Locale.US, "%,.0f %s", result.totalCost, result.currencySymbol), PdfLayoutHelper.MARGIN, y, totalCostPaint)
        y += 60f
        
        // Items Table
        val sectionPaint = android.graphics.Paint().apply { textSize = 14f; isFakeBoldText = true; color = helper.colorPrimary }
        helper.drawText(canvas, "DETAILED QUANTITIES & COSTS (كشف البنود)", PdfLayoutHelper.MARGIN, y, sectionPaint)
        y += 30f
        
        val headerBgPaint = android.graphics.Paint().apply { color = helper.colorPrimary }
        canvas.drawRect(PdfLayoutHelper.MARGIN, y, PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN, y + 25f, headerBgPaint)
        
        val headerTextPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 9f; isFakeBoldText = true }
        helper.drawText(canvas, "Description", PdfLayoutHelper.MARGIN + 10f, y + 17f, headerTextPaint)
        helper.drawText(canvas, "Quantity", PdfLayoutHelper.MARGIN + 180f, y + 17f, headerTextPaint)
        helper.drawText(canvas, "Unit Price", PdfLayoutHelper.MARGIN + 280f, y + 17f, headerTextPaint)
        headerTextPaint.textAlign = android.graphics.Paint.Align.RIGHT
        helper.drawText(canvas, "Total", PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN - 10f, y + 17f, headerTextPaint)
        y += 35f
        
        val itemPaint = android.graphics.Paint().apply { textSize = 9f; color = helper.colorText }
        result.items.forEach { item ->
            if (y > PdfLayoutHelper.PAGE_HEIGHT - PdfLayoutHelper.MARGIN - 100f) {
                helper.finishCurrentPage()
                helper.startNewPage()
                y = helper.currentY + 20f
            }
            val currentCanvas = helper.currentPage!!.canvas
            helper.drawText(currentCanvas, item.name, PdfLayoutHelper.MARGIN + 10f, y, itemPaint)
            helper.drawText(currentCanvas, String.format(Locale.US, "%.1f %s", item.quantity, item.unit), PdfLayoutHelper.MARGIN + 180f, y, itemPaint)
            helper.drawText(currentCanvas, String.format(Locale.US, "%,.0f", item.unitPrice), PdfLayoutHelper.MARGIN + 280f, y, itemPaint)
            itemPaint.textAlign = android.graphics.Paint.Align.RIGHT
            helper.drawText(currentCanvas, String.format(Locale.US, "%,.0f", item.totalPrice), PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN - 10f, y, itemPaint)
            itemPaint.textAlign = android.graphics.Paint.Align.LEFT
            
            currentCanvas.drawLine(PdfLayoutHelper.MARGIN, y + 5f, PdfLayoutHelper.PAGE_WIDTH - PdfLayoutHelper.MARGIN, y + 5f, android.graphics.Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 0.5f })
            y += 25f
        }
        
        // Investment Section
        result.investmentData?.let { invest ->
            y += 30f
            if (y > PdfLayoutHelper.PAGE_HEIGHT - PdfLayoutHelper.MARGIN - 200f) {
                helper.finishCurrentPage()
                helper.startNewPage()
                y = helper.currentY + 20f
            }
            val currentCanvas = helper.currentPage!!.canvas
            helper.drawText(currentCanvas, "FEASIBILITY & INVESTMENT ANALYSIS (دراسة الجدوى)", PdfLayoutHelper.MARGIN, y, sectionPaint)
            y += 30f
            
            val labelPaint = android.graphics.Paint().apply { textSize = 11f; color = helper.colorGray }
            val valuePaint = android.graphics.Paint().apply { textSize = 11f; isFakeBoldText = true; color = helper.colorText }
            
            if (invest.landCost > 0) {
                helper.drawInfoRow(currentCanvas, "Land Cost (سعر الأرض)", String.format(Locale.US, "%,.0f %s", invest.landCost, result.currencySymbol), y, labelPaint, valuePaint)
                y += 25f
            }
            helper.drawInfoRow(currentCanvas, "Construction Cost (تكلفة البناء)", String.format(Locale.US, "%,.0f %s", invest.constructionCost, result.currencySymbol), y, labelPaint, valuePaint); y += 25f
            helper.drawInfoRow(currentCanvas, "Expected Net Profit (صافي الربح)", String.format(Locale.US, "%,.0f %s", invest.netProfit, result.currencySymbol), y, labelPaint, valuePaint); y += 25f
            
            val roiPaint = android.graphics.Paint(valuePaint).apply { color = helper.colorSuccess }
            helper.drawInfoRow(currentCanvas, "ROI (Return on Investment)", String.format(Locale.US, "%.1f %%", invest.roi), y, labelPaint, roiPaint); y += 25f
            helper.drawInfoRow(currentCanvas, "Profit Margin (هامش الربح)", String.format(Locale.US, "%.1f %%", invest.profitMargin), y, labelPaint, valuePaint); y += 25f
            helper.drawInfoRow(currentCanvas, "Construction Duration", "${invest.constructionDurationMonths} Months", y, labelPaint, valuePaint); y += 25f
        }

        helper.finishCurrentPage()
        pdf.writeTo(FileOutputStream(file))
        pdf.close()
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
}
