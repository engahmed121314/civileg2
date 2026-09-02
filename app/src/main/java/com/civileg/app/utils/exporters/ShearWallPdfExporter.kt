package com.civileg.app.utils.exporters

import android.content.Context
import com.civileg.app.domain.ShearWallInput
import com.civileg.app.domain.ShearWallResult
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
 * Professional PDF Exporter for Shear Wall Projects.
 * Rule 1.3 - Complete vertical chain (Input -> Result -> PDF).
 */
class ShearWallPdfExporter(private val context: Context) {

    private val PRIMARY = DeviceRgb(123, 31, 162) // Purple for shear wall
    private val WHITE = DeviceRgb(255, 255, 255)

    fun exportToDownload(input: ShearWallInput, result: ShearWallResult, projectName: String, clientName: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Shear_Wall_Report_${timestamp}.pdf"
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

        // 3. Flexural & Shear Results
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
        cell.add(Paragraph("SHEAR WALL DESIGN REPORT").setFontSize(18f).setFontColor(WHITE).setBold().setTextAlignment(TextAlignment.CENTER))
        banner.addCell(cell)
        document.add(banner)
        document.add(Paragraph("Project: $proj | Client: $client").setFontSize(10f).setItalic())
    }

    private fun addInputSummary(document: Document, input: ShearWallInput) {
        document.add(Paragraph("1. Design Inputs").setBold().setFontSize(12f).setFontColor(PRIMARY))
        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph("Wall Type")))
        table.addCell(Cell().add(Paragraph(input.wallType.displayName)))
        table.addCell(Cell().add(Paragraph("Length (mm)")))
        table.addCell(Cell().add(Paragraph(input.wallLength.toString())))
        table.addCell(Cell().add(Paragraph("Thickness (mm)")))
        table.addCell(Cell().add(Paragraph(input.wallThickness.toString())))
        table.addCell(Cell().add(Paragraph("Story Height (mm)")))
        table.addCell(Cell().add(Paragraph(input.wallHeight.toString())))
        document.add(table)
    }

    private fun addTechnicalResults(document: Document, res: ShearWallResult) {
        document.add(Paragraph("2. Analysis Results").setBold().setFontSize(12f).setFontColor(PRIMARY))
        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph("Moment Capacity (kN.m)")))
        table.addCell(Cell().add(Paragraph("%.1f".format(res.momentCapacity))))
        table.addCell(Cell().add(Paragraph("Shear Capacity (kN)")))
        table.addCell(Cell().add(Paragraph("%.1f".format(res.shearCapacity))))
        table.addCell(Cell().add(Paragraph("Utilization Ratio")))
        table.addCell(Cell().add(Paragraph("%.0f%%".format(res.utilizationRatio * 100))))
        document.add(table)
    }

    private fun addDesignDrawing(document: Document, input: ShearWallInput, res: ShearWallResult) {
        document.add(Paragraph("3. Design Drawing").setBold().setFontSize(12f).setFontColor(PRIMARY))
        
        val bitmap = PdfDrawingGenerator.generateShearWallDrawing(
            wallLengthMm = input.wallLength,
            wallThicknessMm = input.wallThickness,
            wallHeightMm = input.wallHeight,
            verticalDiaMm = res.verticalReinforcement.diameter,
            verticalSpacingMm = res.verticalReinforcement.spacing,
            horizontalDiaMm = res.horizontalReinforcement.diameter,
            horizontalSpacingMm = res.horizontalReinforcement.spacing,
            boundaryElementLabel = res.boundaryElementType.displayName,
            designCode = "ACI/ECP"
        )

        val stream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        val image = Image(ImageDataFactory.create(stream.toByteArray()))
        image.setHorizontalAlignment(HorizontalAlignment.CENTER).scaleToFit(500f, 350f)
        document.add(image)
    }

    private fun addReinforcementDetails(document: Document, res: ShearWallResult) {
        document.add(Paragraph("4. Reinforcement Details").setBold().setFontSize(12f).setFontColor(PRIMARY))
        val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph("Vertical Steel")))
        table.addCell(Cell().add(Paragraph("Ø${res.verticalReinforcement.diameter}@${res.verticalReinforcement.spacing}mm")))
        table.addCell(Cell().add(Paragraph("Horizontal Steel")))
        table.addCell(Cell().add(Paragraph("Ø${res.horizontalReinforcement.diameter}@${res.horizontalReinforcement.spacing}mm")))
        table.addCell(Cell().add(Paragraph("Boundary Element")))
        table.addCell(Cell().add(Paragraph(res.boundaryElementReinforcement?.let { "Ø${it.diameter}@${it.spacing}mm" } ?: "None")))
        document.add(table)
    }
}
