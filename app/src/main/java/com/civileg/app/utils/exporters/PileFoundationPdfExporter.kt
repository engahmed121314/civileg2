package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import com.civileg.app.domain.PileDesignResult
import com.civileg.app.utils.PdfDrawingGenerator
import com.civileg.app.utils.PdfTextSegmenter
import com.civileg.app.utils.ArabicFontProvider
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
 * Professional PDF Exporter for Pile Foundation Projects.
 * Rule 1.3 - Complete vertical chain (Input -> Result -> PDF).
 */
class PileFoundationPdfExporter(private val context: Context) {

    private val PRIMARY = DeviceRgb(46, 125, 50) // Green for foundation
    private val SECONDARY = DeviceRgb(55, 71, 79)
    private val WHITE = DeviceRgb(255, 255, 255)

    fun exportToDownload(result: PileDesignResult, projectName: String, clientName: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Pile_Foundation_Report_${timestamp}.pdf"
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outputDir, fileName)

        val writer = PdfWriter(FileOutputStream(file))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(30f, 30f, 30f, 30f)

        // 1. Header
        addHeader(document, projectName, clientName)

        // 2. Input Summary
        addInputSummary(document, result)

        // 3. Technical Results (Capacity, Settlement)
        addTechnicalResults(document, result)

        // 4. Design Drawing
        addDesignDrawing(document, result)

        // 5. Reinforcement Table
        addReinforcementDetails(document, result)

        document.close()
        return file
    }

    private fun addHeader(document: Document, proj: String, client: String) {
        val banner = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        val cell = Cell().setBackgroundColor(PRIMARY).setPadding(15f)
        cell.add(Paragraph("PILE FOUNDATION DESIGN REPORT").setFontSize(18f).setFontColor(WHITE).setBold().setTextAlignment(TextAlignment.CENTER))
        banner.addCell(cell)
        document.add(banner)
        document.add(Paragraph("Project: $proj | Client: $client").setFontSize(10f).setItalic())
    }

    private fun addInputSummary(document: Document, res: PileDesignResult) {
        document.add(Paragraph("1. Design Inputs").setBold().setFontSize(12f).setFontColor(PRIMARY))
        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph("Pile Type")))
        table.addCell(Cell().add(Paragraph(res.pileType)))
        table.addCell(Cell().add(Paragraph("Diameter (mm)")))
        table.addCell(Cell().add(Paragraph(res.pileDiameterMm.toString())))
        table.addCell(Cell().add(Paragraph("Length (m)")))
        table.addCell(Cell().add(Paragraph(res.pileLengthM.toString())))
        table.addCell(Cell().add(Paragraph("No. of Piles")))
        table.addCell(Cell().add(Paragraph(res.numberOfPiles.toString())))
        document.add(table)
    }

    private fun addTechnicalResults(document: Document, res: PileDesignResult) {
        document.add(Paragraph("2. Analysis Results").setBold().setFontSize(12f).setFontColor(PRIMARY))
        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph("Ult. Capacity (kN)")))
        table.addCell(Cell().add(Paragraph("%.1f".format(res.capacityResult.ultimateCapacity))))
        table.addCell(Cell().add(Paragraph("Settlement (mm)")))
        table.addCell(Cell().add(Paragraph("%.2f".format(res.settlementResult.totalSettlement))))
        table.addCell(Cell().add(Paragraph("Safety Status")))
        table.addCell(Cell().add(Paragraph(if (res.isSafe) "SAFE" else "UNSAFE").setFontColor(if (res.isSafe) DeviceRgb(0, 100, 0) else DeviceRgb(200, 0, 0))))
        document.add(table)
    }

    private fun addDesignDrawing(document: Document, res: PileDesignResult) {
        document.add(Paragraph("3. Design Drawing").setBold().setFontSize(12f).setFontColor(PRIMARY))
        
        // Use PdfDrawingGenerator from the app utils
        val bitmap = PdfDrawingGenerator.generatePileFoundationDrawing(
            numberOfPiles = res.numberOfPiles,
            pattern = res.groupResult.pattern,
            pileDiameterMm = res.pileDiameterMm,
            pileSpacingMm = res.groupResult.spacing,
            capWidthMm = res.capResult.capWidth,
            capLengthMm = res.capResult.capLength,
            capThicknessMm = res.capResult.capThickness,
            columnWidthMm = res.columnWidth,
            columnLengthMm = res.columnLength,
            pileLengthM = res.pileLengthM
        )

        val stream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        val image = Image(ImageDataFactory.create(stream.toByteArray()))
        image.setHorizontalAlignment(HorizontalAlignment.CENTER).scaleToFit(500f, 350f)
        document.add(image)
    }

    private fun addReinforcementDetails(document: Document, res: PileDesignResult) {
        document.add(Paragraph("4. Reinforcement Details").setBold().setFontSize(12f).setFontColor(PRIMARY))
        val table = Table(UnitValue.createPercentArray(floatArrayOf(30f, 70f))).useAllAvailableWidth()
        table.addCell(Cell().add(Paragraph("Pile Rebar")))
        table.addCell(Cell().add(Paragraph(res.pileReinforcement.barString)))
        table.addCell(Cell().add(Paragraph("Cap Reinforcement")))
        table.addCell(Cell().add(Paragraph(res.capResult.flexuralReinforcement.barString)))
        document.add(table)
    }
}
