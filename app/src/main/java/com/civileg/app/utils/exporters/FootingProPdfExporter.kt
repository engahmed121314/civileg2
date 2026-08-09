package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import com.civileg.app.domain.calculations.base.FootingDesignResult
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
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
 * MASTER FOOTING PDF EXPORTER
 * Detailed engineering report for isolated, combined, and raft foundations.
 */
class FootingProPdfExporter(private val context: Context) {

    private val PRIMARY = DeviceRgb(0, 48, 73)
    private val ACCENT = DeviceRgb(214, 158, 46)
    private val HEADER_BG = DeviceRgb(33, 37, 41)
    private val LIGHT_BG = DeviceRgb(248, 249, 250)
    private val WHITE = ColorConstants.WHITE

    private fun helvetica(bold: Boolean = false): PdfFont {
        return PdfFontFactory.createFont(if (bold) "Helvetica-Bold" else "Helvetica")
    }

    fun exportToPdf(
        result: FootingDesignResult,
        clientName: String,
        projectName: String,
        drawings: Map<String, Bitmap?> = emptyMap()
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, "FootingReport_$timestamp.pdf")

        val writer = PdfWriter(FileOutputStream(file))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(40f, 40f, 40f, 40f)

        val fontBold = helvetica(true)
        val fontReg = helvetica(false)

        // ── 1. COVER ──
        val header = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        header.addCell(Cell().setBackgroundColor(PRIMARY).setPadding(20f).add(
            Paragraph("FOUNDATION DESIGN & ANALYSIS REPORT").setFont(fontBold).setFontSize(18f).setFontColor(WHITE).setTextAlignment(TextAlignment.CENTER)
        ).add(
            Paragraph("MASTER STRUCTURAL ENGINE - SITEENGINEERPRO").setFont(fontReg).setFontSize(11f).setFontColor(ACCENT).setTextAlignment(TextAlignment.CENTER)
        ))
        document.add(header)
        document.add(Paragraph("\n"))

        val idTable = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f))).useAllAvailableWidth()
        fun addRow(l: String, v: String) {
            idTable.addCell(Cell().setBackgroundColor(HEADER_BG).setPadding(6f).add(Paragraph(l).setFont(fontBold).setFontSize(9f).setFontColor(WHITE)))
            idTable.addCell(Cell().setBackgroundColor(LIGHT_BG).setPadding(6f).add(Paragraph(v).setFont(fontReg).setFontSize(10f).setFontColor(PRIMARY)))
        }
        addRow("PROJECT", projectName)
        addRow("CLIENT", clientName)
        addRow("DESIGN CODE", result.designCodeName)
        addRow("DATE", SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date()))
        document.add(idTable)
        
        document.add(Paragraph("\n"))
        val passed = result.isSafe
        val statusColor = if (passed) DeviceRgb(46, 125, 50) else DeviceRgb(198, 40, 40)
        document.add(Paragraph(if (passed) "STATUS: STRUCTURAL ANALYSIS COMPLETED - SAFE" else "STATUS: REVIEW REQUIRED")
            .setFont(fontBold).setFontSize(13f).setFontColor(statusColor).setTextAlignment(TextAlignment.CENTER)
            .setPadding(10f).setBorder(com.itextpdf.layout.borders.SolidBorder(statusColor, 1.5f)))

        document.add(AreaBreak())

        // ── 2. GEOMETRY & LOADS ──
        document.add(Paragraph("1. DESIGN BASIS & GEOMETRY").setFont(fontBold).setFontSize(14f).setFontColor(PRIMARY))
        val geomTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))).useAllAvailableWidth()
        fun addG(l: String, v: String) {
            val c = Cell().setPadding(5f).add(Paragraph(l).setFontSize(8f).setFontColor(ColorConstants.GRAY))
            c.add(Paragraph(v).setFont(fontBold).setFontSize(10f))
            geomTable.addCell(c)
        }
        addG("Width (B)", "${result.requiredWidth.toInt()} mm")
        addG("Length (L)", "${result.requiredLength.toInt()} mm")
        addG("Thickness (t)", "${result.requiredThickness.toInt()} mm")
        addG("Net Pressure", "${result.soilPressure.format(1)} kPa")
        document.add(geomTable)

        document.add(Paragraph("\nDESIGN CALCULATIONS:").setFont(fontBold).setFontSize(11f).setFontColor(PRIMARY))
        result.formulas.forEach { f ->
            val p = Paragraph().setMarginLeft(15f).setFixedLeading(14f)
            if (f.contains("=")) {
                val parts = f.split("=")
                p.add(Text(parts[0] + "= ").setFont(fontBold).setFontSize(9f))
                p.add(Text(parts[1]).setFont(fontReg).setFontSize(9f).setFontColor(PRIMARY))
            } else {
                p.add(Text(f).setFont(fontReg).setFontSize(9f))
            }
            document.add(p)
        }

        drawings["plan"]?.let { addImage(document, it, "Foundation Plan View & Reinforcement Layout") }

        document.add(AreaBreak())

        // ── 3. SAFETY CHECKS ──
        document.add(Paragraph("2. SAFETY CHECKS SUMMARY").setFont(fontBold).setFontSize(14f).setFontColor(PRIMARY))
        val safety = Table(UnitValue.createPercentArray(floatArrayOf(35f, 20f, 20f, 25f))).useAllAvailableWidth()
        fun sH(t: String) = safety.addHeaderCell(Cell().setBackgroundColor(HEADER_BG).add(Paragraph(t).setFont(fontBold).setFontSize(9f).setFontColor(WHITE).setTextAlignment(TextAlignment.CENTER)))
        sH("CHECK TYPE"); sH("VALUE"); sH("LIMIT"); sH("RESULT")
        
        result.safetyChecks.forEach { c ->
            safety.addCell(Cell().add(Paragraph(c.name).setFontSize(9f)))
            safety.addCell(Cell().add(Paragraph("${c.value.format(2)} ${c.unit}").setFontSize(9f).setTextAlignment(TextAlignment.CENTER)))
            safety.addCell(Cell().add(Paragraph("${c.limit.format(2)} ${c.unit}").setFontSize(9f).setTextAlignment(TextAlignment.CENTER)))
            val ok = c.isSafe
            safety.addCell(Cell().add(Paragraph(if (ok) "PASS" else "FAIL").setFont(fontBold).setFontSize(9f).setFontColor(if (ok) DeviceRgb(0, 100, 0) else ColorConstants.RED).setTextAlignment(TextAlignment.CENTER)))
        }
        document.add(safety)

        document.add(Paragraph("\n3. REINFORCEMENT DETAILS").setFont(fontBold).setFontSize(14f).setFontColor(PRIMARY))
        document.add(Paragraph("Main Bars: ${result.reinforcement.barString}").setFont(fontBold).setFontSize(11f).setMarginLeft(10f))
        
        drawings["section"]?.let { addImage(document, it, "Structural Cross-Section View") }

        // FOOTER
        document.add(Paragraph("\n\n\n"))
        document.add(Paragraph("Generated by CivilEG Professional | Specialist Foundation Design Suite").setFontSize(8f).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))

        document.close()
        return file
    }

    private fun addImage(doc: Document, bmp: Bitmap, label: String) {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val img = Image(ImageDataFactory.create(stream.toByteArray())).setAutoScale(true).setHorizontalAlignment(HorizontalAlignment.CENTER).setMarginTop(15f)
        doc.add(img)
        doc.add(Paragraph(label).setFontSize(8f).setItalic().setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY))
    }

    private fun Double.format(n: Int) = String.format("%.${n}f", this)
}
