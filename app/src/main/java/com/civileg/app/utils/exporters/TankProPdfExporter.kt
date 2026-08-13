package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import com.civileg.app.domain.calculations.base.TankResult
import com.civileg.app.utils.CalculatorEngine
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
 * MASTER TANK PDF EXPORTER (Professional Engineering Standard)
 * Generates multi-page technical reports for water retaining structures.
 */
class TankProPdfExporter(private val context: Context) {

    private val PRIMARY = DeviceRgb(0, 48, 73)
    private val ACCENT = DeviceRgb(214, 158, 46)
    private val HEADER_BG = DeviceRgb(33, 37, 41)
    private val LIGHT_BG = DeviceRgb(248, 249, 250)
    private val WHITE = ColorConstants.WHITE

    private fun helvetica(bold: Boolean = false): PdfFont {
        return PdfFontFactory.createFont(if (bold) "Helvetica-Bold" else "Helvetica")
    }

    fun exportToPdf(
        result: TankResult,
        tankInputs: CalculatorEngine.TankInputs,
        clientName: String,
        projectName: String,
        drawings: Map<String, Bitmap?> = emptyMap()
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, "TankReport_$timestamp.pdf")

        val writer = PdfWriter(FileOutputStream(file))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(40f, 40f, 40f, 40f)

        val fontBold = helvetica(true)
        val fontReg = helvetica(false)

        // ── 1. COVER PAGE ──
        val header = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        header.addCell(Cell().setBackgroundColor(PRIMARY).setPadding(25f).add(
            Paragraph("STRUCTURAL DESIGN & ENGINEERING REPORT").setFont(fontBold).setFontSize(20f).setFontColor(WHITE).setTextAlignment(TextAlignment.CENTER)
        ).add(
            Paragraph("WATER RETAINING STRUCTURE - SPECIALIST EDITION").setFont(fontReg).setFontSize(12f).setFontColor(ACCENT).setTextAlignment(TextAlignment.CENTER)
        ))
        document.add(header)
        document.add(Paragraph("\n"))

        val idTable = Table(UnitValue.createPercentArray(floatArrayOf(30f, 70f))).useAllAvailableWidth()
        fun addRow(label: String, value: String) {
            idTable.addCell(Cell().setBackgroundColor(HEADER_BG).setPadding(8f).add(Paragraph(label).setFont(fontBold).setFontSize(9f).setFontColor(WHITE)))
            idTable.addCell(Cell().setBackgroundColor(LIGHT_BG).setPadding(8f).add(Paragraph(value).setFont(fontReg).setFontSize(10f).setFontColor(PRIMARY)))
        }
        addRow("PROJECT", projectName)
        addRow("CLIENT", clientName)
        addRow("SYSTEM", result.structuralSystem)
        addRow("CODE", result.designCode)
        addRow("DATE", SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date()))
        document.add(idTable)
        
        document.add(Paragraph("\n"))
        val statusColor = if (result.isSafe) DeviceRgb(46, 125, 50) else DeviceRgb(198, 40, 40)
        document.add(Paragraph(if (result.isSafe) "STATUS: STRUCTURAL ANALYSIS COMPLETED - SAFE" else "STATUS: REDESIGN REQUIRED")
            .setFont(fontBold).setFontSize(12f).setFontColor(statusColor).setTextAlignment(TextAlignment.CENTER)
            .setPadding(10f).setBorder(com.itextpdf.layout.borders.SolidBorder(statusColor, 1.5f)))

        document.add(AreaBreak())

        // ── 2. GEOMETRY & LOADING ──
        document.add(Paragraph("1. DESIGN BASIS & GEOMETRY").setFont(fontBold).setFontSize(14f).setFontColor(PRIMARY))
        val geomTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))).useAllAvailableWidth()
        fun addG(l: String, v: String) {
            val c = Cell().setPadding(5f).add(Paragraph(l).setFontSize(8f).setFontColor(ColorConstants.GRAY))
            c.add(Paragraph(v).setFont(fontBold).setFontSize(10f))
            geomTable.addCell(c)
        }
        addG("Capacity", "${result.capacityM3.format(1)} m³")
        addG("Height", "${tankInputs.height} m")
        addG("Wall Thick.", "${result.wallThickness.toInt()} mm")
        addG("Base Thick.", "${result.baseThickness.toInt()} mm")
        document.add(geomTable)

        document.add(Paragraph("\nLOADING CALCULATIONS:").setFont(fontBold).setFontSize(11f).setFontColor(PRIMARY))
        result.formulas.forEach { f ->
            val p = Paragraph().setMarginLeft(15f).setFixedLeading(12f)
            if (f.contains("=")) {
                val parts = f.split("=")
                p.add(Text(parts[0] + "= ").setFont(fontBold).setFontSize(9f))
                p.add(Text(parts[1]).setFont(fontReg).setFontSize(9f).setFontColor(PRIMARY))
            } else {
                p.add(Text(f).setFont(fontReg).setFontSize(9f))
            }
            document.add(p)
        }

        drawings["elevation"]?.let { addImage(document, it, "Structural Elevation & Perspective") }

        document.add(AreaBreak())

        // ── 3. REINFORCEMENT SCHEDULE ──
        document.add(Paragraph("2. REINFORCEMENT & DETAILING").setFont(fontBold).setFontSize(14f).setFontColor(PRIMARY))
        val schedule = Table(UnitValue.createPercentArray(floatArrayOf(20f, 30f, 30f, 20f))).useAllAvailableWidth()
        fun sH(t: String) = schedule.addHeaderCell(Cell().setBackgroundColor(HEADER_BG).add(Paragraph(t).setFont(fontBold).setFontSize(9f).setFontColor(WHITE).setTextAlignment(TextAlignment.CENTER)))
        sH("ELEMENT"); sH("REINFORCEMENT"); sH("SPACING"); sH("UR")

        fun addM(name: String, rebar: String, space: String, ur: String) {
            schedule.addCell(Cell().add(Paragraph(name).setFont(fontBold).setFontSize(9f)))
            schedule.addCell(Cell().add(Paragraph(rebar).setFont(fontReg).setFontSize(9f)))
            schedule.addCell(Cell().add(Paragraph(space).setFont(fontReg).setFontSize(9f)))
            schedule.addCell(Cell().add(Paragraph(ur).setFont(fontBold).setFontSize(9f).setTextAlignment(TextAlignment.CENTER)))
        }
        addM("WALL (Vert/Hoop)", result.wallReinforcement.description, "${result.wallReinforcement.spacing.toInt()} mm", "${(result.wallReinforcement.utilizationRatio * 100).toInt()}%")
        addM("BASE SLAB", result.baseReinforcement.description, "${result.baseReinforcement.spacing.toInt()} mm", "Check")
        document.add(schedule)

        drawings["reinforcement"]?.let { addImage(document, it, "Detailed Reinforcement Layout") }

        document.add(AreaBreak())

        // ── 4. STABILITY & SAFETY CHECKS ──
        document.add(Paragraph("3. STRUCTURAL STABILITY & SAFETY").setFont(fontBold).setFontSize(14f).setFontColor(PRIMARY))
        val safety = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f))).useAllAvailableWidth()
        sH("CHECK TYPE"); sH("CALCULATED"); sH("LIMIT"); sH("RESULT")
        
        result.safetyChecks.forEach { c ->
            safety.addCell(Cell().add(Paragraph(c.name).setFontSize(9f)))
            safety.addCell(Cell().add(Paragraph("${c.value.format(2)} ${c.unit}").setFontSize(9f).setTextAlignment(TextAlignment.CENTER)))
            safety.addCell(Cell().add(Paragraph("${c.limit.format(2)} ${c.unit}").setFontSize(9f).setTextAlignment(TextAlignment.CENTER)))
            val resP = Paragraph(if (c.isSafe) "PASS" else "FAIL").setFont(fontBold).setFontSize(9f).setFontColor(if (c.isSafe) DeviceRgb(0, 100, 0) else ColorConstants.RED).setTextAlignment(TextAlignment.CENTER)
            safety.addCell(Cell().add(resP))
        }
        document.add(safety)

        document.add(Paragraph("\nRECOMMENDATIONS:").setFont(fontBold).setFontSize(11f).setFontColor(PRIMARY))
        result.recommendations.forEach { r ->
            document.add(Paragraph("\u2022 $r").setFont(fontReg).setFontSize(9f).setMarginLeft(15f))
        }

        // FOOTER
        document.add(Paragraph("\n\n\n"))
        document.add(Paragraph("Generated by CivilEG Professional Suite | Specialist Engineering Engine").setFontSize(8f).setFontColor(ColorConstants.GRAY).setTextAlignment(TextAlignment.CENTER))

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
