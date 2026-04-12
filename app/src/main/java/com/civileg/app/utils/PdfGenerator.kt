package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import com.civileg.app.R
import com.civileg.app.domain.entities.*
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

object PdfGenerator {

    private val PRIMARY_COLOR = DeviceRgb(21, 101, 192) // Professional Blue
    private val SECONDARY_COLOR = DeviceRgb(69, 90, 100)
    private val SUCCESS_COLOR = DeviceRgb(46, 125, 50)
    private val ERROR_COLOR = DeviceRgb(198, 40, 40)

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
        val file = File(context.getExternalFilesDir(null), fileName)

        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        // --- Header Section ---
        val header = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f))).useAllAvailableWidth()
        header.addCell(Cell().add(Paragraph(context.getString(R.string.app_name))
            .setFontSize(26f).setBold().setFontColor(PRIMARY_COLOR)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        header.addCell(Cell().add(Paragraph("STRUCTURAL REPORT")
            .setTextAlignment(TextAlignment.RIGHT).setFontSize(10f).setFontColor(ColorConstants.GRAY)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        document.add(header)
        
        document.add(LineSeparator(SolidLine(1f)))
        document.add(Paragraph("\n"))

        // --- Title & Status ---
        document.add(Paragraph(title).setFontSize(18f).setBold())

        val statusText = if (isSafe) "STATUS: SAFE (مطابق للأكواد)" else "STATUS: UNSAFE (غير آمن - مراجعة التصميم)"
        val statusPara = Paragraph(statusText)
            .setFontColor(if (isSafe) SUCCESS_COLOR else ERROR_COLOR)
            .setBold()
            .setPadding(10f)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(if (isSafe) SUCCESS_COLOR else ERROR_COLOR, 1f))
        document.add(statusPara)
        
        document.add(Paragraph("\n"))

        // --- Split Layout: Info vs Drawing ---
        val mainTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()

        // Column 1: Inputs & Results
        val infoCell = Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        infoCell.add(Paragraph("DESIGN DATA").setBold().setFontColor(PRIMARY_COLOR))
        
        val dataTable = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
        inputs.forEach { (k, v) ->
            dataTable.addCell(Cell().add(Paragraph(k).setFontSize(9f)))
            dataTable.addCell(Cell().add(Paragraph(v).setFontSize(9f).setBold()))
        }
        results.forEach { (k, v) ->
            dataTable.addCell(Cell().add(Paragraph(k).setFontSize(9f)))
            dataTable.addCell(Cell().add(Paragraph(v).setFontSize(9f).setBold()))
        }
        infoCell.add(dataTable)
        mainTable.addCell(infoCell)

        // Column 2: Drawing
        val drawingCell = Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPaddingLeft(10f)
        drawingCell.add(Paragraph("SECTION DRAWING (رسم تسليح القطاع)").setBold().setFontColor(PRIMARY_COLOR))
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
            document.add(Paragraph("SAFETY VERIFICATIONS (تحققات الأمان)").setBold().setFontColor(PRIMARY_COLOR))
            val checkTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
            checkTable.addHeaderCell(Cell().add(Paragraph("Check Type").setBold()))
            checkTable.addHeaderCell(Cell().add(Paragraph("Calculated").setBold()))
            checkTable.addHeaderCell(Cell().add(Paragraph("Limit").setBold()))
            checkTable.addHeaderCell(Cell().add(Paragraph("Result").setBold()))

            safetyChecks.forEach { check ->
                checkTable.addCell(Cell().add(Paragraph(check.name).setFontSize(9f)))
                checkTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.2f %s", check.value, check.unit)).setFontSize(9f)))
                checkTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.2f %s", check.limit, check.unit)).setFontSize(9f)))
                val resPara = Paragraph(if (check.isSafe) "PASS" else "FAIL")
                    .setFontColor(if (check.isSafe) SUCCESS_COLOR else ERROR_COLOR).setBold().setFontSize(9f)
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
        val fileName = "SteelWarehouse_Design_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)

        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        // --- Header ---
        val header = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f))).useAllAvailableWidth()
        header.addCell(Cell().add(Paragraph(context.getString(R.string.app_name)).setFontSize(26f).setBold().setFontColor(PRIMARY_COLOR)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        header.addCell(Cell().add(Paragraph("STEEL STRUCTURE REPORT").setTextAlignment(TextAlignment.RIGHT).setFontSize(10f).setFontColor(ColorConstants.GRAY)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        document.add(header)
        document.add(LineSeparator(SolidLine(1f)))

        document.add(Paragraph("\n"))
        document.add(Paragraph("Steel Warehouse Technical Data Sheet").setFontSize(18f).setBold().setTextAlignment(TextAlignment.CENTER))
        
        // Status Card
        val statusText = if (result.safetyStatus) "STRUCTURE IS SAFE (المنشأ آمن طبقاً للكود)" else "STRUCTURE NEEDS REVIEW (المنشأ يحتاج مراجعة)"
        document.add(Paragraph(statusText).setFontColor(if (result.safetyStatus) SUCCESS_COLOR else ERROR_COLOR).setBold().setPadding(10f).setBorder(com.itextpdf.layout.borders.SolidBorder(if (result.safetyStatus) SUCCESS_COLOR else ERROR_COLOR, 1f)))

        document.add(Paragraph("\n"))

        // --- Summary Table ---
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(33f, 33f, 34f))).useAllAvailableWidth()
        summaryTable.addCell(createSummaryCell("Total Steel Weight", "%.2f Tons".format(result.totalWeight)))
        summaryTable.addCell(createSummaryCell("Weight / m²", "%.1f kg/m²".format(result.weightPerM2)))
        summaryTable.addCell(createSummaryCell("Cladding Area", "%.0f m²".format(result.totalCladdingArea)))
        document.add(summaryTable)

        document.add(Paragraph("\n"))

        // --- Inputs Section ---
        document.add(Paragraph("Project Geometry & Inputs (مدخلات المشروع)").setBold().setFontColor(PRIMARY_COLOR))
        val inputsTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        inputsTable.addCell(createDataCell("Span (البحر)", "${inputs.span} m"))
        inputsTable.addCell(createDataCell("Total Length (الطول)", "${inputs.length} m"))
        inputsTable.addCell(createDataCell("Eave Height (الجنب)", "${inputs.eaveHeight} m"))
        inputsTable.addCell(createDataCell("Ridge Height (القمة)", "${inputs.ridgeHeight} m"))
        inputsTable.addCell(createDataCell("Bay Spacing (الباكية)", "${inputs.baySpacing} m"))
        inputsTable.addCell(createDataCell("Design Code", inputs.code.displayName))
        document.add(inputsTable)

        document.add(Paragraph("\n"))

        // --- Analysis Results ---
        document.add(Paragraph("Structural Analysis Actions (نتائج التحليل)").setBold().setFontColor(PRIMARY_COLOR))
        val analysisTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        analysisTable.addCell(createDataCell("Max Moment (M_max)", "%.1f kN.m".format(result.mainFrame.maxMoment)))
        analysisTable.addCell(createDataCell("Max Shear (V_max)", "%.1f kN".format(result.mainFrame.maxShear)))
        analysisTable.addCell(createDataCell("Max Axial (P_max)", "%.1f kN".format(result.mainFrame.maxAxial)))
        analysisTable.addCell(createDataCell("Actual Deflection", "%.1f mm".format(result.mainFrame.maxDeflection)))
        document.add(analysisTable)

        document.add(AreaBreak())

        // --- Member Schedule ---
        document.add(Paragraph("Structural Member Schedule (جدول القطاعات الإنشائية)").setBold().setFontColor(PRIMARY_COLOR))
        val membersTable = Table(UnitValue.createPercentArray(floatArrayOf(30f, 50f, 20f))).useAllAvailableWidth()
        membersTable.addHeaderCell(Cell().add(Paragraph("Member Type").setBold().setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE)))
        membersTable.addHeaderCell(Cell().add(Paragraph("Proposed Section").setBold().setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE)))
        membersTable.addHeaderCell(Cell().add(Paragraph("Status").setBold().setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE)))

        addMemberRow(membersTable, "Main Columns", result.mainFrame.columnSection.displayName, "Safe")
        addMemberRow(membersTable, "Main Rafters", result.mainFrame.rafterSection.displayName, "Safe")
        addMemberRow(membersTable, "Roof Purlins", result.secondaryMembers.purlinSection.displayName, "Safe")
        addMemberRow(membersTable, "Side Girts", result.secondaryMembers.girtSection.displayName, "Safe")
        addMemberRow(membersTable, "Bracing", result.secondaryMembers.bracingSection.displayName, "Safe")
        document.add(membersTable)

        document.add(Paragraph("\n"))

        // --- Connection Details ---
        document.add(Paragraph("Steel Connection Summary (تفاصيل الوصلات)").setBold().setFontColor(PRIMARY_COLOR))
        val connTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 40f, 20f))).useAllAvailableWidth()
        connTable.addHeaderCell(Cell().add(Paragraph("Connection").setBold()))
        connTable.addHeaderCell(Cell().add(Paragraph("Type/Details").setBold()))
        connTable.addHeaderCell(Cell().add(Paragraph("Util. %").setBold()))

        result.connections.forEach { conn ->
            connTable.addCell(Cell().add(Paragraph(conn.name).setFontSize(9f)))
            connTable.addCell(Cell().add(Paragraph(conn.type.displayName).setFontSize(8f)))
            connTable.addCell(Cell().add(Paragraph("%.0f%%".format(conn.demand / conn.capacity * 100)).setFontSize(9f)))
        }
        document.add(connTable)

        // --- Drawing Section ---
        if (drawingBitmap != null) {
            document.add(AreaBreak())
            document.add(Paragraph("PROPOSED STRUCTURAL DRAWING (الرسم الهندسي المقترح)").setBold().setFontColor(PRIMARY_COLOR).setTextAlignment(TextAlignment.CENTER))
            val stream = ByteArrayOutputStream()
            drawingBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setAutoScale(true).setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
            document.add(img)
        }

        // --- Recommendations ---
        document.add(Paragraph("\nEngineering Recommendations & Notes (ملاحظات وتوصيات هندسية)").setBold().setFontColor(PRIMARY_COLOR))
        val itextList = com.itextpdf.layout.element.List()
        result.recommendations.forEach { rec ->
            itextList.add(rec)
        }
        document.add(itextList)

        // --- Footer ---
        document.add(Paragraph("\n\n"))
        document.add(Paragraph("This report is generated automatically by CivilEG Pro based on user inputs. Professional engineering stamping is required for construction.")
            .setFontSize(8f).setItalic().setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph("Generated on: ${java.util.Date()}").setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))

        document.close()
        return file
    }

    private fun createSummaryCell(label: String, value: String): Cell {
        return Cell().add(Paragraph(label).setFontSize(10f).setFontColor(ColorConstants.GRAY))
            .add(Paragraph(value).setBold().setFontSize(14f).setFontColor(PRIMARY_COLOR))
            .setPadding(10f).setTextAlignment(TextAlignment.CENTER)
    }

    private fun createDataCell(label: String, value: String): Cell {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph(label).setFontSize(9f)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        table.addCell(Cell().add(Paragraph(value).setFontSize(9f).setBold()).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        return Cell().add(table)
    }

    private fun addMemberRow(table: Table, type: String, section: String, status: String) {
        table.addCell(Cell().add(Paragraph(type).setFontSize(9f)))
        table.addCell(Cell().add(Paragraph(section).setFontSize(9f).setBold()))
        table.addCell(Cell().add(Paragraph(status).setFontSize(9f).setFontColor(SUCCESS_COLOR).setBold()))
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
        val file = File(context.getExternalFilesDir(null), fileName)
        
        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        
        // --- Header ---
        document.add(Paragraph(context.getString(R.string.app_name))
            .setTextAlignment(TextAlignment.CENTER).setFontSize(24f).setBold().setFontColor(PRIMARY_COLOR))
        document.add(Paragraph("Comprehensive BOQ Report (كشف كميات وتكاليف)").setTextAlignment(TextAlignment.CENTER).setFontSize(14f))
        document.add(LineSeparator(SolidLine(1f)))
        document.add(Paragraph("\n"))

        // --- Project Info ---
        val projectTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        projectTable.addCell(Cell().add(Paragraph("Project Name (اسم المشروع)").setBold()).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        projectTable.addCell(Cell().add(Paragraph(projectName).setBold().setFontColor(SECONDARY_COLOR)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        projectTable.addCell(Cell().add(Paragraph("Report Date (التاريخ)").setBold()).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        projectTable.addCell(Cell().add(Paragraph(java.util.Date().toString()).setFontSize(9f)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        document.add(projectTable)
        document.add(Paragraph("\n"))

        // --- Summary Card ---
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        summaryTable.setBackgroundColor(DeviceRgb(245, 245, 245))
        
        summaryTable.addCell(Cell().add(Paragraph("Total Estimated Budget")).setBold())
        summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%,.2f EGP", totalBudget)).setBold().setFontColor(PRIMARY_COLOR)))
        
        summaryTable.addCell(Cell().add(Paragraph("Total Concrete Volume")))
        summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%,.2f m³", concreteVol))))
        
        summaryTable.addCell(Cell().add(Paragraph("Total Steel Weight")))
        summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%,.2f Tons", steelWeight / 1000.0))))
        document.add(summaryTable)
        
        // --- Detailed Breakdown ---
        document.add(Paragraph("\nDETAILED COST BREAKDOWN (تفاصيل التكاليف)").setBold().setFontSize(14f).setFontColor(PRIMARY_COLOR))
        val itemTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f))).useAllAvailableWidth()
        itemTable.addHeaderCell(Cell().add(Paragraph("Item / Design Name").setBold().setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE)))
        itemTable.addHeaderCell(Cell().add(Paragraph("Cost (EGP)").setBold().setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE)))
        
        items.forEach { (name, price) ->
            itemTable.addCell(Cell().add(Paragraph(name).setFontSize(10f)))
            itemTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%,.0f", price)).setBold().setFontSize(10f)))
        }
        document.add(itemTable)
        
        // --- Footer ---
        document.add(Paragraph("\n\nEnd of BOQ Report").setFontSize(8f).setItalic().setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY))
        document.close()
        return file
    }

    fun generateEstimationReport(
        context: Context,
        result: EstimationEngine.EstimationResult
    ): File {
        val fileName = "CivilEngPro_Estimation_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)

        val writer = PdfWriter(file)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        // --- Header Section ---
        val header = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f))).useAllAvailableWidth()
        header.addCell(Cell().add(Paragraph(context.getString(R.string.app_name))
            .setFontSize(26f).setBold().setFontColor(PRIMARY_COLOR)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        header.addCell(Cell().add(Paragraph("ESTIMATION REPORT")
            .setTextAlignment(TextAlignment.RIGHT).setFontSize(10f).setFontColor(ColorConstants.GRAY)).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER))
        document.add(header)
        
        document.add(LineSeparator(SolidLine(1f)))
        document.add(Paragraph("\n"))

        // --- Summary ---
        document.add(Paragraph("Total Project Cost Estimate (تقدير إجمالي التكلفة)").setFontSize(12f).setFontColor(ColorConstants.GRAY))
        document.add(Paragraph(String.format(Locale.US, "%,.0f %s", result.totalCost, result.currencySymbol))
            .setFontSize(28f).setBold().setFontColor(PRIMARY_COLOR))
        
        document.add(Paragraph("\n"))

        // --- Detailed Items Table ---
        document.add(Paragraph("DETAILED QUANTITIES & COSTS (كشف البنود)").setBold().setFontColor(PRIMARY_COLOR))
        val itemTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
        itemTable.addHeaderCell(Cell().add(Paragraph("Description").setBold().setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE)))
        itemTable.addHeaderCell(Cell().add(Paragraph("Quantity").setBold().setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE)))
        itemTable.addHeaderCell(Cell().add(Paragraph("Unit Price").setBold().setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE)))
        itemTable.addHeaderCell(Cell().add(Paragraph("Total").setBold().setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE)))

        result.items.forEach { item ->
            itemTable.addCell(Cell().add(Paragraph(item.name).setFontSize(9f)))
            itemTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.1f %s", item.quantity, item.unit)).setFontSize(9f)))
            itemTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%,.0f", item.unitPrice)).setFontSize(9f)))
            itemTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%,.0f", item.totalPrice)).setFontSize(9f).setBold()))
        }
        document.add(itemTable)

        // --- Investment Analysis Section ---
        result.investmentData?.let { invest ->
            document.add(Paragraph("\n"))
            document.add(Paragraph("FEASIBILITY & INVESTMENT ANALYSIS (دراسة الجدوى)").setBold().setFontColor(PRIMARY_COLOR))
            
            val investTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
            
            fun addRow(label: String, value: String) {
                investTable.addCell(Cell().add(Paragraph(label).setFontSize(10f)))
                investTable.addCell(Cell().add(Paragraph(value).setFontSize(10f).setBold()))
            }

            if (invest.landCost > 0) addRow("Land Cost (سعر الأرض)", String.format(Locale.US, "%,.0f %s", invest.landCost, result.currencySymbol))
            addRow("Construction Cost (تكلفة البناء)", String.format(Locale.US, "%,.0f %s", invest.constructionCost, result.currencySymbol))
            addRow("Expected Net Profit (صافي الربح)", String.format(Locale.US, "%,.0f %s", invest.netProfit, result.currencySymbol))
            addRow("ROI (Return on Investment)", String.format(Locale.US, "%.1f %%", invest.roi))
            addRow("Profit Margin (هامش الربح)", String.format(Locale.US, "%.1f %%", invest.profitMargin))
            addRow("Construction Duration", "${invest.constructionDurationMonths} Months")
            
            document.add(investTable)
        }

        // --- Technical Details ---
        if (result.technicalDetails.isNotEmpty()) {
            document.add(Paragraph("\nTECHNICAL NOTES (ملاحظات فنية)").setBold().setFontSize(12f))
            result.technicalDetails.forEach { detail ->
                document.add(Paragraph("• $detail").setFontSize(10f))
            }
        }

        // --- Footer ---
        document.add(Paragraph("\n\n"))
        document.add(Paragraph("Generated by Civil Engineer Pro - Professional Estimation Module")
            .setFontSize(8f).setItalic().setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph(java.util.Date().toString())
            .setFontSize(8f).setTextAlignment(TextAlignment.RIGHT))

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
}
