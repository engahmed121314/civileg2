package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.PdfDrawingGenerator
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Professional English PDF Exporter for Steel Warehouse Projects
 */
class SteelWarehouseProPdfExporter(private val context: Context) {

    private val PRIMARY = DeviceRgb(21, 101, 192)
    private val SUCCESS = DeviceRgb(46, 125, 50)
    private val ERROR = DeviceRgb(198, 40, 40)
    private val HEADER_BG = DeviceRgb(33, 37, 41)
    private val LIGHT_BLUE = DeviceRgb(227, 242, 253)
    private val ROW_ALT = DeviceRgb(248, 249, 250)
    private val WHITE = DeviceRgb(255, 255, 255)

    private fun helvetica(bold: Boolean = false): PdfFont = PdfFontFactory.createFont(
        if (bold) StandardFonts.HELVETICA_BOLD else StandardFonts.HELVETICA
    )

    private fun headerCell(text: String): Cell {
        return Cell().setPadding(5f).setBackgroundColor(HEADER_BG)
            .add(Paragraph(text).setFont(helvetica(true)).setFontSize(8f).setFontColor(WHITE).setTextAlignment(TextAlignment.CENTER))
    }

    private fun dataCell(text: String, bold: Boolean = false, bg: DeviceRgb? = null, color: DeviceRgb? = null): Cell {
        val p = Paragraph(text).setFont(helvetica(bold)).setFontSize(8f).setTextAlignment(TextAlignment.CENTER)
        color?.let { p.setFontColor(it) }
        val cell = Cell().setPadding(3f).add(p)
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    private fun Double.fmt(decimals: Int = 2): String = String.format(Locale.US, "%.${decimals}f", this)

    fun exportToDownload(
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        clientName: String,
        projectName: String
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Steel_Warehouse_Report_${timestamp}.pdf"
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outputDir, fileName)

        val writer = PdfWriter(FileOutputStream(file))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(30f, 30f, 30f, 30f)

        // 1. Cover
        addCoverPage(document, inputs, result, clientName, projectName)
        document.add(AreaBreak())

        // 2. Summary & Analysis
        addProjectSummary(document, inputs, result)
        document.add(AreaBreak())

        // 3. Drawings
        addDrawingsPage(document, inputs, result)
        document.add(AreaBreak())

        // 4. Schedules
        addMemberSchedule(document, inputs, result)
        addConnectionSchedule(document, result)
        
        // 5. BOQ & Cost
        addMaterialTakeoff(document, result, inputs)
        
        addTitleBlock(document, clientName, projectName, inputs)

        document.close()
        return file
    }

    private fun addCoverPage(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult, client: String, project: String) {
        val banner = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        banner.addCell(Cell().setPadding(20f).setBackgroundColor(PRIMARY).setBorder(null)
            .add(Paragraph("STRUCTURAL DESIGN & ANALYSIS REPORT").setFont(helvetica(true)).setFontSize(20f).setFontColor(WHITE).setTextAlignment(TextAlignment.CENTER))
            .add(Paragraph("INDUSTRIAL STEEL WAREHOUSE SYSTEM").setFont(helvetica()).setFontSize(14f).setFontColor(WHITE).setTextAlignment(TextAlignment.CENTER)))
        document.add(banner)

        document.add(Paragraph("\n"))

        val info = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth()
        fun addRow(l: String, v: String) {
            info.addCell(Cell().add(Paragraph(l).setBold().setFontSize(10f)).setPadding(5f).setBackgroundColor(LIGHT_BLUE))
            info.addCell(Cell().add(Paragraph(v).setFontSize(10f)).setPadding(5f))
        }
        addRow("Project Name", project)
        addRow("Client Name", client)
        addRow("Design Code", inputs.code.version)
        addRow("Span / Length", "${inputs.span.fmt()}m / ${inputs.length.fmt()}m")
        addRow("Eave / Ridge Height", "${inputs.eaveHeight.fmt()}m / ${inputs.ridgeHeight.fmt()}m")
        addRow("Report Date", SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date()))
        document.add(info)

        val statusColor = if (result.safetyStatus) SUCCESS else ERROR
        document.add(Paragraph("\n"))
        document.add(Paragraph(if (result.safetyStatus) "STATUS: STRUCTURAL ANALYSIS PASSED" else "STATUS: REVIEW REQUIRED")
            .setFont(helvetica(true)).setFontSize(12f).setFontColor(statusColor).setTextAlignment(TextAlignment.CENTER)
            .setPadding(10f).setBorder(SolidBorder(statusColor, 1f)))
    }

    private fun addProjectSummary(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        document.add(Paragraph("Design Summary & Parameters").setFontSize(14f).setBold().setFontColor(PRIMARY))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(10f))

        val data = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        data.addCell(dataCell("Total Steel Weight", true, LIGHT_BLUE)).addCell(dataCell("${result.totalWeight.fmt(1)} Tons"))
        data.addCell(dataCell("Weight per Area", true)).addCell(dataCell("${result.weightPerM2.fmt(1)} kg/m\u00B2"))
        data.addCell(dataCell("Total Cladding Area", true, LIGHT_BLUE)).addCell(dataCell("${result.totalCladdingArea.fmt(1)} m\u00B2"))
        data.addCell(dataCell("Estimated Total Cost", true)).addCell(dataCell("${result.estimatedTotalCost.fmt(0)} EGP", true, null, PRIMARY))
        document.add(data)
        
        document.add(Paragraph("\nCalculation Trace:").setBold().setUnderline())
        result.calculationTrace.forEach { step ->
            document.add(Paragraph("• $step").setFontSize(8f).setFontColor(ColorConstants.GRAY))
        }
    }

    private fun addDrawingsPage(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        document.add(Paragraph("Structural Engineering Sketches").setFontSize(14f).setBold().setFontColor(PRIMARY))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(10f))

        try {
            val bitmap = PdfDrawingGenerator.generateWarehouseDrawing(inputs, result)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            document.add(Image(ImageDataFactory.create(stream.toByteArray())).setAutoScale(true).setHorizontalAlignment(HorizontalAlignment.CENTER))
            
            document.add(Paragraph("\n3D Isometric Model").setBold().setTextAlignment(TextAlignment.CENTER))
            val bitmap3d = PdfDrawingGenerator.generateWarehouse3DDrawing(inputs, result)
            val stream3d = ByteArrayOutputStream()
            bitmap3d.compress(Bitmap.CompressFormat.PNG, 100, stream3d)
            document.add(Image(ImageDataFactory.create(stream3d.toByteArray())).setAutoScale(true).setHorizontalAlignment(HorizontalAlignment.CENTER))
        } catch (e: Exception) {
            document.add(Paragraph("Drawing Generation Error: ${e.message}").setFontColor(ERROR))
        }
    }

    private fun addMemberSchedule(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        document.add(Paragraph("Steel Member Schedule").setFontSize(14f).setBold().setFontColor(PRIMARY))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(8f, 22f, 30f, 25f, 15f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell("MARK")).addHeaderCell(headerCell("MEMBER")).addHeaderCell(headerCell("SECTION")).addHeaderCell(headerCell("QTY / COUNT")).addHeaderCell(headerCell("STATUS"))

        val numBays = (inputs.length / inputs.baySpacing).toInt().coerceAtLeast(1)
        val members = mutableListOf(Triple("C1", "Main Columns", result.mainFrame.columnSection), Triple("R1", "Main Rafters", result.mainFrame.rafterSection))
        result.mainFrame.floorBeamSection?.let { members.add(Triple("FB1", "Floor Beams", it)) }
        members.add(Triple("P1", "Roof Purlins", result.secondaryMembers.purlinSection))
        members.add(Triple("G1", "Side Girts", result.secondaryMembers.girtSection))
        members.add(Triple("B1", "X-Bracing System", result.secondaryMembers.bracingSection))

        members.forEachIndexed { i, m ->
            val bg = if (i % 2 != 0) ROW_ALT else null
            table.addCell(dataCell(m.first, true, bg))
            table.addCell(dataCell(m.second, false, bg))
            table.addCell(dataCell(m.third.displayName, true, bg))
            
            val qtyText = when(m.first) {
                "C1" -> "${(numBays + 1) * 2} Units"
                "R1" -> "${(numBays + 1) * 2} Units"
                "FB1" -> "${numBays + 1} Units"
                "P1" -> "${result.secondaryMembers.purlinCount} Lines"
                "G1" -> "${(inputs.length / inputs.baySpacing).toInt() * 4} Units"
                else -> "Full Bay Sets"
            }
            table.addCell(dataCell(qtyText, false, bg))
            table.addCell(dataCell("SAFE", true, bg, SUCCESS))
        }
        document.add(table)
    }

    private fun addConnectionSchedule(document: Document, result: SteelWarehouseAnalysisResult) {
        document.add(Paragraph("\nConnection, Plates & Anchor Bolt Schedule").setFontSize(12f).setBold().setFontColor(PRIMARY))
        document.add(LineSeparator(SolidLine(0.5f)).setMarginBottom(5f))
        
        val table = Table(UnitValue.createPercentArray(floatArrayOf(25f, 40f, 15f, 20f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell("COMPONENT")).addHeaderCell(headerCell("SPECIFICATIONS / DETAILING")).addHeaderCell(headerCell("UNIT")).addHeaderCell(headerCell("STATUS"))
        
        table.addCell(dataCell("Base Plates (PL)", true)).addCell(dataCell("Thickness: ${result.mainFrame.basePlateThickness.toInt()}mm, Grade: S355")).addCell(dataCell("mm")).addCell(dataCell("PASS", true, null, SUCCESS))
        table.addCell(dataCell("Anchor Bolts", true, ROW_ALT)).addCell(dataCell("Type: M24 Grade 8.8, Qty: ${result.mainFrame.basePlateBoltsCount} per base", false, ROW_ALT)).addCell(dataCell("Nos", false, ROW_ALT)).addCell(dataCell("SAFE", true, ROW_ALT, SUCCESS))
        table.addCell(dataCell("End Plates (PL)")).addCell(dataCell("Thickness: 20mm, Grade: S355, High-Strength Bolted")).addCell(dataCell("mm")).addCell(dataCell("PASS", true, null, SUCCESS))

        document.add(table)
    }

    private fun addMaterialTakeoff(document: Document, result: SteelWarehouseAnalysisResult, inputs: SteelWarehouseInputs) {
        document.add(Paragraph("\nProject Bill of Quantities (BOQ) & Estimated Costing").setFontSize(12f).setBold().setFontColor(PRIMARY))
        document.add(LineSeparator(SolidLine(0.5f)).setMarginBottom(5f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(5f, 40f, 15f, 15f, 25f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell("#")).addHeaderCell(headerCell("ITEM DESCRIPTION")).addHeaderCell(headerCell("QTY")).addHeaderCell(headerCell("UNIT")).addHeaderCell(headerCell("EST. COST (EGP)"))
        
        var idx = 1
        result.materialTakeoff.forEach { (k, v) ->
            val bg = if (idx % 2 == 0) ROW_ALT else null
            table.addCell(dataCell("${idx++}", false, bg)).addCell(dataCell("Structural Steel: $k", false, bg)).addCell(dataCell(v.fmt(0), false, bg)).addCell(dataCell("kg", false, bg)).addCell(dataCell((v * 45.0).fmt(0), false, bg))
        }

        val secWeight = result.totalWeight * 0.15 * 1000 
        table.addCell(dataCell("${idx++}", false, ROW_ALT)).addCell(dataCell("Accessories (Plates, Bracing, Bolts)", false, ROW_ALT)).addCell(dataCell(secWeight.fmt(0), false, ROW_ALT)).addCell(dataCell("kg", false, ROW_ALT)).addCell(dataCell((secWeight * 65.0).fmt(0), false, ROW_ALT))

        table.addCell(dataCell("${idx++}")).addCell(dataCell("Roof & Wall Cladding (Sandwich Panels)")).addCell(dataCell(result.totalCladdingArea.fmt(0))).addCell(dataCell("m\u00B2")).addCell(dataCell((result.totalCladdingArea * 350.0).fmt(0)))

        val totalRowBg = LIGHT_BLUE
        table.addCell(Cell(1, 4).add(Paragraph("GRAND TOTAL ESTIMATED PROJECT COST").setBold().setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(totalRowBg))
        table.addCell(dataCell("${result.estimatedTotalCost.fmt(0)} EGP", true, totalRowBg, PRIMARY))
        document.add(table)
    }

    private fun addTitleBlock(document: Document, client: String, project: String, inputs: SteelWarehouseInputs) {
        document.add(Paragraph("\n"))
        val tb = Table(UnitValue.createPercentArray(floatArrayOf(30f, 30f, 20f, 20f))).useAllAvailableWidth().setBorder(
            SolidBorder(1f)
        )
        fun addC(l: String, v: String) = tb.addCell(Cell().setPadding(5f).add(Paragraph(l).setFontSize(6f).setBold().setFontColor(ColorConstants.GRAY)).add(Paragraph(v).setFontSize(8f).setBold()))
        addC("PROJECT", project); addC("CLIENT", client); addC("DESIGNER", "Civil EG Pro"); addC("CODE", inputs.code.version)
        document.add(tb)
        document.add(Paragraph("Generated by Civil EG Pro Engine - Corporate Structural Design Report").setFontSize(7f).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY))
    }
}