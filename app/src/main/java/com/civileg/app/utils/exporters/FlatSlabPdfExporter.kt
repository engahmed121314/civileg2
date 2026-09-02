package com.civileg.app.utils.exporters

import android.content.Context
import com.civileg.app.domain.FlatSlabInput
import com.civileg.app.domain.FlatSlabResult
import com.civileg.app.utils.PdfDrawingGenerator
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
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
 * Professional PDF Exporter for Flat Slab Projects.
 * Rule 1.3 - Complete vertical chain (Input -> Result -> PDF).
 */
class FlatSlabPdfExporter(private val context: Context) {

    private val PRIMARY = DeviceRgb(0, 131, 143) // Teal for slab
    private val WHITE = DeviceRgb(255, 255, 255)

    fun exportToDownload(input: FlatSlabInput, result: FlatSlabResult, projectName: String, clientName: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Flat_Slab_Report_${timestamp}.pdf"
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outputDir, fileName)

        val writer = PdfWriter(FileOutputStream(file))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(30f, 30f, 30f, 30f)

        // 1. Header
        addHeader(document, projectName, clientName)

        // 2. Input Summary
        addInputSummary(document, input)

        // 3. Technical Results (Moments, Punching)
        addTechnicalResults(document, result)

        // 4. Design Drawing
        addDesignDrawing(document, input, result)

        // 5. Reinforcement Table
        addReinforcementDetails(document, result)

        document.close()
        return file
    }

    private fun addHeader(document: Document, proj: String, client: String) {
        val banner = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        val cell = Cell().setBackgroundColor(PRIMARY).setPadding(15f)
        cell.add(Paragraph("FLAT SLAB DESIGN REPORT").setFontSize(18f).setFontColor(WHITE).setBold().setTextAlignment(TextAlignment.CENTER))
        banner.addCell(cell)
        document.add(banner)
        document.add(Paragraph("Project: $proj | Client: $client").setFontSize(10f).setItalic())
    }

    private fun addInputSummary(document: Document, input: FlatSlabInput) {
        document.add(Paragraph("1. Design Inputs").setBold().setFontSize(12f).setFontColor(PRIMARY))
        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph("Panel Type")))
        table.addCell(Cell().add(Paragraph(input.panelType.displayName)))
        table.addCell(Cell().add(Paragraph("Spans Lx, Ly (mm)")))
        table.addCell(Cell().add(Paragraph("${input.lx} x ${input.ly}")))
        table.addCell(Cell().add(Paragraph("Slab Thickness (mm)")))
        table.addCell(Cell().add(Paragraph(input.slabThickness.toString())))
        table.addCell(Cell().add(Paragraph("Column Size (mm)")))
        table.addCell(Cell().add(Paragraph("${input.columnWidth} x ${input.columnDepth}")))
        document.add(table)
    }

    private fun addTechnicalResults(document: Document, res: FlatSlabResult) {
        document.add(Paragraph("2. Analysis Results").setBold().setFontSize(12f).setFontColor(PRIMARY))
        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph("Factored Load Wu (kN/m2)")))
        table.addCell(Cell().add(Paragraph("%.2f".format(res.totalFactoredLoad))))
        table.addCell(Cell().add(Paragraph("Punching Vu (kN)")))
        table.addCell(Cell().add(Paragraph("%.1f".format(res.punchingShearVu))))
        table.addCell(Cell().add(Paragraph("Punching φVc (kN)")))
        table.addCell(Cell().add(Paragraph("%.1f".format(res.punchingShearVc))))
        table.addCell(Cell().add(Paragraph("Safety Status")))
        table.addCell(Cell().add(Paragraph(if (res.isSafe) "SAFE" else "UNSAFE").setFontColor(if (res.isSafe) DeviceRgb(0, 100, 0) else DeviceRgb(200, 0, 0))))
        document.add(table)
    }

    private fun addDesignDrawing(document: Document, input: FlatSlabInput, res: FlatSlabResult) {
        document.add(Paragraph("3. Design Drawing").setBold().setFontSize(12f).setFontColor(PRIMARY))
        
        val bitmap = PdfDrawingGenerator.generateSlabDrawingByType(
            slabType = com.civileg.app.utils.CalculatorEngine.SlabType.FLAT,
            spanX = input.lx / 1000.0,
            spanY = input.ly / 1000.0,
            thickness = input.slabThickness,
            mainDia = res.columnStripBotRebar.diameter.toDouble(),
            mainSpacing = res.columnStripBotRebar.spacing.toDouble(),
            distDia = res.middleStripBotRebar.diameter.toDouble(),
            distSpacing = res.middleStripBotRebar.spacing.toDouble(),
            cover = input.clearCover,
            dropPanelSize = input.dropSizeX,
            columnSize = input.columnWidth
        )

        val stream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        val image = Image(ImageDataFactory.create(stream.toByteArray()))
        image.setHorizontalAlignment(HorizontalAlignment.CENTER).scaleToFit(500f, 350f)
        document.add(image)
    }

    private fun addReinforcementDetails(document: Document, res: FlatSlabResult) {
        document.add(Paragraph("4. Reinforcement Details").setBold().setFontSize(12f).setFontColor(PRIMARY))
        val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph("Col Strip Bottom")))
        table.addCell(Cell().add(Paragraph("Ø${res.columnStripBotRebar.diameter}@${res.columnStripBotRebar.spacing}mm")))
        table.addCell(Cell().add(Paragraph("Col Strip Top")))
        table.addCell(Cell().add(Paragraph("Ø${res.columnStripTopRebar.diameter}@${res.columnStripTopRebar.spacing}mm")))
        table.addCell(Cell().add(Paragraph("Mid Strip Bottom")))
        table.addCell(Cell().add(Paragraph("Ø${res.middleStripBotRebar.diameter}@${res.middleStripBotRebar.spacing}mm")))
        table.addCell(Cell().add(Paragraph("Punching Reinf.")))
        table.addCell(Cell().add(Paragraph(res.punchingReinforcement?.let { "${it.bars}Ø${it.diameter}@${it.spacing}mm" } ?: "None")))
        document.add(table)
    }
}
