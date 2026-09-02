package com.civileg.app.utils.exporters

import android.content.Context
import com.civileg.app.domain.calculations.GeneralEstimationEngine
import com.civileg.app.utils.ArabicFontProvider
import com.civileg.app.utils.PdfTextSegmenter
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * PDF Exporter for General Estimations (Plaster, Paint, Bricks).
 * Targeted at non-engineer users.
 */
class GeneralEstimationPdfExporter(private val context: Context) {

    private val BLUE = DeviceRgb(21, 101, 192)
    private val WHITE = DeviceRgb(255, 255, 255)

    fun exportToDownload(
        plaster: GeneralEstimationEngine.PlasterResult?,
        paint: GeneralEstimationEngine.PaintResult?,
        bricks: GeneralEstimationEngine.BrickResult?,
        projectName: String
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Estimation_Report_${timestamp}.pdf"
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outputDir, fileName)

        val writer = PdfWriter(FileOutputStream(file))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(40f, 40f, 40f, 40f)

        // Banner
        val banner = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        banner.addCell(Cell().setBackgroundColor(BLUE).setPadding(10f).add(
            Paragraph("GENERAL ESTIMATION REPORT").setFontColor(WHITE).setBold().setTextAlignment(TextAlignment.CENTER)
        ))
        document.add(banner)
        document.add(Paragraph("Project: $projectName").setItalic().setFontSize(10f))

        // 1. Plaster & Paint
        if (plaster != null) {
            document.add(Paragraph("Plastering & Painting").setBold().setFontColor(BLUE))
            val t = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
            t.addCell("Area")
            t.addCell("${plaster.area} m2")
            t.addCell("Cement Required")
            t.addCell("${plaster.cementBags} Bags")
            t.addCell("Sand Required")
            t.addCell("${String.format("%.2f", plaster.sandM3)} m3")
            if (paint != null) {
                t.addCell("Paint (3 Coats)")
                t.addCell("${String.format("%.1f", paint.liters)} Liters")
            }
            document.add(t)
        }

        // 2. Brickwork
        if (bricks != null) {
            document.add(Paragraph("\nBrickwork").setBold().setFontColor(BLUE))
            val t = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
            t.addCell("Total Bricks")
            t.addCell("${bricks.totalBricks} Nos")
            t.addCell("Mortar Cement")
            t.addCell("${bricks.cementBags} Bags")
            t.addCell("Mortar Sand")
            t.addCell("${String.format("%.2f", bricks.sandM3)} m3")
            document.add(t)
        }

        document.add(Paragraph("\n\nDisclaimer: These are rough estimations for planning only.").setFontSize(8f).setItalic())

        document.close()
        return file
    }
}
