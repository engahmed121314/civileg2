package com.civileg.app.utils.exporters

import com.civileg.app.domain.drawing.DesignDrawingDocument
import com.civileg.app.domain.drawing.PdfRenderer

import android.graphics.Bitmap
import android.util.Log
import com.civileg.app.domain.entities.GenericSafetyCheck
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.civileg.app.utils.EnglishFontProvider
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.OverflowWrapPropertyValue
import com.itextpdf.layout.properties.Property
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Professional English PDF Report Generator
 *
 * Generates comprehensive, professional structural design reports in English only.
 * Uses the shared embedded English technical font when initialized.
 * Missing methodology is reported explicitly, never replaced with generic equations.
 *
 * Reports include:
 * - Professional cover page with project info
 * - Design Parameters section
 * - Calculation Methodology with code-specific equations
 * - Detailed Results section
 * - Safety Checks (PASS/FAIL) table
 * - Engineering Drawing attachment
 * - Footer with page numbers and disclaimer
 */
object ProfessionalEnglishPdfReporter {

    private const val TAG = "EnglishPdfReporter"

    // Color Palette
    private val PRIMARY = DeviceRgb(21, 101, 192)
    private val PRIMARY_LIGHT = DeviceRgb(227, 242, 253)
    private val SECONDARY = DeviceRgb(55, 71, 79)
    private val SUCCESS = DeviceRgb(27, 94, 32)
    private val ERROR = DeviceRgb(183, 28, 28)
    private val WARNING = DeviceRgb(230, 81, 0)
    private val HEADER_BG = DeviceRgb(21, 101, 192)
    private val ROW_ALT = DeviceRgb(245, 245, 245)
    private val LIGHT_GRAY = DeviceRgb(220, 220, 220)
    private val CALC_BG = DeviceRgb(248, 248, 255)
    private val EQUATION_COLOR = DeviceRgb(0, 0, 139)

    // ==================== Data Classes ====================

    enum class ReportType {
        BEAM, COLUMN, SLAB, FOOTING, TANK, RETAINING_WALL, STAIR, STEEL, FRAME_ANALYSIS, SEISMIC, GENERIC
    }

    /** Evidence supplied by the producer, never a certification by this renderer. */
    enum class MethodEvidenceState(val label: String) {
        UNVERIFIED("Unverified"), PARTIAL("Partial"), SUPPLIED_FOR_REVIEW("Supplied for review")
    }

    data class SafetyCheck(
        val name: String,
        val calculated: Double,
        val limit: Double,
        val unit: String,
        val passed: Boolean,
        val reference: String? = null
    )

    data class BbsRow(
        val memberMark: String,
        val barMark: String,
        val diameter: Int,
        val shapeCode: Int,
        val count: Int,
        val totalLengthMm: Double,
        val weightKg: Double
    )

    data class BoqRow(
        val item: String,
        val description: String,
        val unit: String,
        val quantity: Double
    )

    data class CalculationStep(
        val stepNumber: Int,
        val description: String,
        val equation: String,
        val reference: String? = null,
        val result: String? = null
    )

    data class UnsafeDesignRecommendation(
        val failedCheck: String,
        val action: String,
        val reference: String
    ) {
        init {
            require(failedCheck.isNotBlank() && action.isNotBlank() && reference.isNotBlank()) {
                "Recommendations require a failed-check name, an action and a source reference"
            }
        }
    }

    data class ReportConfig(
        val projectName: String = "Civil EG Project",
        val engineerName: String = "Structural Engineer",
        val companyName: String = "Civil EG",
        val projectNumber: String = "",
        val date: String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date()),
        val revision: String = "Rev. A",
        val showSignature: Boolean = false
    )

    // ==================== Font Helpers (English Only) ====================

    /**
     * Get a FRESH font. Never cache across PdfDocuments.
     * Uses the embedded Unicode font (Greek/math/symbol coverage) when
     * EnglishFontProvider is initialized; base-14 Helvetica otherwise.
     */
    private fun getFont(bold: Boolean = false): PdfFont = EnglishFontProvider.getFont(bold)

    // ==================== Element Helpers ====================

    private fun createDocument(outputPath: String): Triple<PdfDocument, Document, PdfFont> {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        val writer = PdfWriter(FileOutputStream(outputPath))
        val pdf = PdfDocument(writer)
        val document = Document(pdf, com.itextpdf.kernel.geom.PageSize.A4)
        document.setMargins(50f, 50f, 50f, 50f)
        return Triple(pdf, document, getFont())
    }

    private fun createPdfFont(): PdfFont = getFont()

    private fun createBoldFont(): PdfFont = getFont(bold = true)

    private fun text(text: String, font: PdfFont, size: Float = 10f, color: DeviceRgb? = null): Text {
        val t = Text(text).setFont(font).setFontSize(size)
        color?.let { t.setFontColor(it) }
        return t
    }

    /**
     * Long unbroken tokens (formula strings, marks, ids) wrap mid-token
     * instead of overflowing the cell and spilling off the page edge.
     */
    private fun Paragraph.breakAnywhere(): Paragraph = apply {
        setProperty(Property.OVERFLOW_WRAP, OverflowWrapPropertyValue.ANYWHERE)
    }

    private fun paragraph(text: String, fontSize: Float = 10f, bold: Boolean = false,
                          color: DeviceRgb? = null, alignment: TextAlignment? = null): Paragraph {
        val font = if (bold) createBoldFont() else createPdfFont()
        val p = Paragraph().add(text(text, font, fontSize, color))
        alignment?.let { p.setTextAlignment(it) } ?: p.setTextAlignment(TextAlignment.LEFT)
        return p.breakAnywhere()
    }

    private fun emptyLine(): Paragraph = Paragraph(" ")

    private fun headerCell(text: String, colSpan: Int = 1): Cell {
        val font = createBoldFont()
        return Cell(colSpan, 1)
            .setPadding(6f)
            .setBackgroundColor(HEADER_BG)
            .setTextAlignment(TextAlignment.CENTER)
            .add(Paragraph().add(text(text, font, 9f, DeviceRgb(255, 255, 255))).setTextAlignment(TextAlignment.CENTER).breakAnywhere())
    }

    private fun dataCell(text: String, fontSize: Float = 9f, bold: Boolean = false,
                         bg: DeviceRgb? = null, align: TextAlignment = TextAlignment.CENTER): Cell {
        val font = if (bold) createBoldFont() else createPdfFont()
        val cell = Cell().setPadding(4f).setTextAlignment(align)
        cell.add(Paragraph().add(text(text, font, fontSize)).setTextAlignment(align).breakAnywhere())
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    private fun labelCell(text: String, bg: DeviceRgb? = null): Cell {
        return dataCell(text, 9f, bold = true, bg = bg, align = TextAlignment.LEFT)
    }

    private fun valueCell(text: String, bg: DeviceRgb? = null): Cell {
        return dataCell(text, 9f, false, bg = bg, align = TextAlignment.LEFT)
    }

    private fun sectionTitle(title: String): Paragraph {
        return Paragraph().add(text(title, createBoldFont(), 13f, PRIMARY))
            .setTextAlignment(TextAlignment.LEFT)
            .setMarginTop(12f)
            .setMarginBottom(4f)
    }

    private fun subTitle(title: String): Paragraph {
        return Paragraph().add(text(title, createBoldFont(), 11f, SECONDARY))
            .setTextAlignment(TextAlignment.LEFT)
            .setMarginTop(8f)
            .setMarginBottom(2f)
    }

    private fun separator(): LineSeparator {
        return LineSeparator(SolidLine(0.5f)).setMarginTop(4f).setMarginBottom(8f)
    }

    // ==================== Document Sections ====================

    private fun addCoverPage(document: Document, config: ReportConfig,
                             reportTitle: String, subtitle: String, designCode: String) {
        val font = createPdfFont()
        val boldFont = createBoldFont()

        // Company Name
        document.add(Paragraph().add(text(config.companyName, boldFont, 28f, PRIMARY))
            .setTextAlignment(TextAlignment.CENTER).setMarginTop(60f))
        document.add(emptyLine())

        // Report Type Title
        document.add(Paragraph().add(text(reportTitle, boldFont, 20f, SECONDARY))
            .setTextAlignment(TextAlignment.CENTER))
        document.add(emptyLine())

        // Subtitle
        document.add(Paragraph().add(text(subtitle, font, 12f, SECONDARY))
            .setTextAlignment(TextAlignment.CENTER))
        document.add(emptyLine())

        // Design Code Badge
        document.add(Paragraph().add(text("Design Code: $designCode", boldFont, 11f, PRIMARY))
            .setTextAlignment(TextAlignment.CENTER))

        // Line separator
        document.add(LineSeparator(SolidLine(2f)).setMarginTop(20f).setMarginBottom(20f))

        // Project Info Table
        val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth()
        infoTable.addCell(labelCell("Project Name:")).addCell(valueCell(config.projectName))
        infoTable.addCell(labelCell("Engineer:")).addCell(valueCell(config.engineerName))
        infoTable.addCell(labelCell("Date:")).addCell(valueCell(config.date))
        infoTable.addCell(labelCell("Revision:")).addCell(valueCell(config.revision))
        if (config.projectNumber.isNotEmpty()) {
            infoTable.addCell(labelCell("Project No.:")).addCell(valueCell(config.projectNumber))
        }
        document.add(infoTable)

        document.add(emptyLine())
        document.add(LineSeparator(SolidLine(1f)).setMarginTop(20f).setMarginBottom(10f))
        document.add(Paragraph().add(text("Generated by Civil EG Pro Application", font, 9f, SECONDARY))
            .setTextAlignment(TextAlignment.CENTER))
    }

    private fun addDesignParameters(document: Document, inputs: Map<String, String>) {
        document.add(sectionTitle("1. Design Parameters"))
        document.add(separator())

        val table = Table(UnitValue.createPercentArray(floatArrayOf(45f, 55f))).useAllAvailableWidth()
        var rowIndex = 0
        for ((key, value) in inputs) {
            val bg = if (rowIndex % 2 == 0) null else ROW_ALT
            table.addCell(labelCell(key, bg))
            table.addCell(valueCell(value, bg))
            rowIndex++
        }
        document.add(table)
        document.add(emptyLine())
    }

    private fun addCalculationMethodology(document: Document, designCode: String,
                                          steps: List<CalculationStep>) {
        document.add(sectionTitle("2. Calculation Methodology & Verification"))
        document.add(separator())
        // Printed only when the caller actually supplied a code: a report whose
        // engine/code choice is unknown must not claim a basis it never used.
        if (designCode.isNotBlank()) {
            document.add(Paragraph().add(text("Applicable design basis: $designCode", createPdfFont(), 10f, SECONDARY)))
        }
        document.add(emptyLine())

        if (steps.isNotEmpty()) {
            // Use provided custom steps
            for (step in steps) {
                addCalculationStep(document, step)
            }
        } else {
            document.add(paragraph(
                "Calculation trace not supplied. Equations, substitutions and code clauses used by the calculation cannot be verified from this report.",
                color = WARNING
            ))
        }
        document.add(emptyLine())
    }

    private fun addCalculationStep(document: Document, step: CalculationStep) {
        val font = createPdfFont()
        val boldFont = createBoldFont()
        val stepTable = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        stepTable.setBackgroundColor(CALC_BG)
        // Keep a step's heading with its body: an orphaned "Step N" header at a
        // page bottom with the equation on the next page reads as a defect.
        stepTable.setKeepTogether(true)

        // Step number and description
        val descPara = Paragraph()
            .add(text("Step ${step.stepNumber}: ", boldFont, 10f, PRIMARY))
            .add(text(step.description, boldFont, 10f, SECONDARY))
            .breakAnywhere()
        stepTable.addCell(Cell().setPadding(6f).add(descPara))

        // Equation
        if (step.equation.isNotEmpty()) {
            val eqPara = Paragraph()
                .add(text("  ", font, 9f))
                .add(text(step.equation, font, 10f, EQUATION_COLOR))
                .add(text("  ", font, 9f))
                .breakAnywhere()
            stepTable.addCell(Cell().setPadding(4f).add(eqPara))
        }

        // Reference
        if (step.reference != null && step.reference.isNotEmpty()) {
            val refPara = Paragraph()
                .add(text("  Ref: ", boldFont, 8f, DeviceRgb(150, 150, 150)))
                .add(text(step.reference, font, 8f, DeviceRgb(150, 150, 150)))
                .breakAnywhere()
            stepTable.addCell(Cell().setPadding(4f).add(refPara))
        }

        // Result
        if (step.result != null && step.result.isNotEmpty()) {
            val resPara = Paragraph()
                .add(text("  Result: ", boldFont, 9f, SUCCESS))
                .add(text(step.result, font, 9f, SUCCESS))
                .breakAnywhere()
            stepTable.addCell(Cell().setPadding(4f).add(resPara))
        }

        document.add(stepTable)
        document.add(Paragraph(" "))  // spacing
    }

    private fun addResultsSection(document: Document, results: Map<String, String>) {
        document.add(sectionTitle("3. Design Results"))
        document.add(separator())

        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        var rowIndex = 0
        for ((key, value) in results) {
            val bg = if (rowIndex % 2 == 0) null else ROW_ALT
            table.addCell(labelCell(key, bg))
            table.addCell(valueCell(value, bg))
            rowIndex++
        }
        document.add(table)
        document.add(emptyLine())
    }

    private fun addSafetyChecks(document: Document, safetyChecks: List<SafetyCheck>, isSafe: Boolean) {
        document.add(sectionTitle("4. Safety Verification"))
        document.add(separator())

        // Status Banner
        val hasFiniteEvidence = safetyChecks.isNotEmpty() && safetyChecks.all {
            it.calculated.isFinite() && it.limit.isFinite()
        }
        val failed = safetyChecks.any { !it.passed }
        val statusText = when {
            failed || !isSafe -> "REVIEW REQUIRED - Calculation reports an unsafe result or a failed check"
            !hasFiniteEvidence -> "CHECK EVIDENCE INCOMPLETE - No overall compliance conclusion"
            else -> "SUPPLIED CHECKS PASS - Limited to the checks listed below"
        }
        val statusColor = when {
            failed || !isSafe -> ERROR
            !hasFiniteEvidence -> WARNING
            else -> SUCCESS
        }
        val banner = Paragraph().add(text(statusText, createBoldFont(), 11f, statusColor))
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(8f)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(statusColor, 2f))
        document.add(banner)
        document.add(emptyLine())

        if (safetyChecks.isNotEmpty()) {
            val table = Table(UnitValue.createPercentArray(floatArrayOf(35f, 18f, 18f, 10f, 19f))).useAllAvailableWidth()

            // Header Row
            table.addHeaderCell(headerCell("Check"))
            table.addHeaderCell(headerCell("Calculated"))
            table.addHeaderCell(headerCell("Limit"))
            table.addHeaderCell(headerCell("Unit"))
            table.addHeaderCell(headerCell("Status"))

            // Data Rows
            safetyChecks.forEachIndexed { i, check ->
                val bg = if (i % 2 == 0) null else ROW_ALT
                val nameCell = dataCell(check.name, 9f, false, bg, TextAlignment.LEFT)
                if (!check.reference.isNullOrBlank()) {
                    nameCell.add(Paragraph().add(text(check.reference, createPdfFont(), 7f, SECONDARY))
                        .setTextAlignment(TextAlignment.LEFT))
                }
                table.addCell(nameCell)

                val calcStr = if (check.calculated.isNaN() || check.calculated.isInfinite()) "---"
                else String.format(Locale.US, "%.2f", check.calculated)
                table.addCell(dataCell(calcStr, 9f, false, bg))

                val limitStr = if (check.limit.isNaN() || check.limit.isInfinite()) "---"
                else String.format(Locale.US, "%.2f", check.limit)
                table.addCell(dataCell(limitStr, 9f, false, bg))

                table.addCell(dataCell(check.unit, 8f, false, bg))

                val statusStr = if (check.passed) "PASS" else "FAIL"
                val statusClr = if (check.passed) SUCCESS else ERROR
                table.addCell(Cell().setPadding(4f).also {
                    bg?.let { color -> it.setBackgroundColor(color) }
                    it.add(Paragraph().add(text(statusStr, createBoldFont(), 9f, statusClr)).setTextAlignment(TextAlignment.CENTER))
                })
            }
            document.add(table)
        }
        document.add(emptyLine())
    }

    private fun addBbsSection(document: Document, rows: List<BbsRow>) {
        if (rows.isEmpty()) return
        document.add(AreaBreak())
        document.add(sectionTitle("6. Bar Bending Schedule"))
        document.add(separator())

        val table = Table(UnitValue.createPercentArray(floatArrayOf(12f, 12f, 9f, 9f, 9f, 20f, 14f, 15f)))
            .useAllAvailableWidth()
        table.addHeaderCell(headerCell("Member"))
        table.addHeaderCell(headerCell("Bar"))
        table.addHeaderCell(headerCell("Dia (mm)"))
        table.addHeaderCell(headerCell("Shape"))
        table.addHeaderCell(headerCell("Qty"))
        table.addHeaderCell(headerCell("Length/bar (mm)"))
        table.addHeaderCell(headerCell("Weight (kg)"))
        table.addHeaderCell(headerCell("Total (kg)"))

        var totalWeight = 0.0
        rows.forEachIndexed { index, row ->
            val bg = if (index % 2 == 0) null else ROW_ALT
            totalWeight += row.weightKg
            table.addCell(dataCell(row.memberMark, 8f, false, bg, TextAlignment.LEFT))
            table.addCell(dataCell(row.barMark, 8f, false, bg, TextAlignment.LEFT))
            table.addCell(dataCell(row.diameter.toString(), 8f, false, bg))
            table.addCell(dataCell(row.shapeCode.toString(), 8f, false, bg))
            table.addCell(dataCell(row.count.toString(), 8f, false, bg))
            table.addCell(dataCell(String.format(Locale.US, "%.0f", row.totalLengthMm), 8f, false, bg))
            val perBar = if (row.count > 0) row.weightKg / row.count else row.weightKg
            table.addCell(dataCell(String.format(Locale.US, "%.2f", perBar), 8f, false, bg))
            table.addCell(dataCell(String.format(Locale.US, "%.2f", row.weightKg), 8f, false, bg))
        }
        document.add(table)
        document.add(Paragraph().add(
            text(String.format(Locale.US, "Total steel: %.2f kg", totalWeight), createBoldFont(), 9f, PRIMARY)
        ).setTextAlignment(TextAlignment.RIGHT))
        document.add(emptyLine())
    }

    private fun addBoqSection(document: Document, rows: List<BoqRow>) {
        if (rows.isEmpty()) return
        document.add(sectionTitle("7. Bill of Quantities"))
        document.add(separator())

        val table = Table(UnitValue.createPercentArray(floatArrayOf(10f, 55f, 15f, 20f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell("Item"))
        table.addHeaderCell(headerCell("Description"))
        table.addHeaderCell(headerCell("Unit"))
        table.addHeaderCell(headerCell("Quantity"))

        rows.forEachIndexed { index, row ->
            val bg = if (index % 2 == 0) null else ROW_ALT
            table.addCell(dataCell(row.item, 8f, false, bg, TextAlignment.LEFT))
            table.addCell(dataCell(row.description, 8f, false, bg, TextAlignment.LEFT))
            table.addCell(dataCell(row.unit, 8f, false, bg))
            table.addCell(dataCell(String.format(Locale.US, "%.2f", row.quantity), 8f, false, bg))
        }
        document.add(table)
        document.add(emptyLine())
    }

    private fun addSuppliedRecommendations(
        document: Document,
        recommendations: List<UnsafeDesignRecommendation>,
        isSafe: Boolean
    ) {
        if (recommendations.isEmpty()) {
            if (!isSafe) document.add(paragraph(
                "Source-based corrective actions were not supplied. No automatic redesign recommendation has been inferred.",
                9f, color = WARNING
            ))
            return
        }
        document.add(subTitle("Source-based corrective actions"))
        recommendations.forEach {
            document.add(paragraph("${it.failedCheck}: ${it.action}"))
            document.add(paragraph("Reference: ${it.reference}", 8f, color = SECONDARY))
        }
    }

    private fun addDrawingDocument(document: Document, pdf: PdfDocument, drawings: DesignDrawingDocument) {
        document.add(sectionTitle("5. Drawing evidence"))
        document.add(paragraph("Member: ${drawings.memberMark} | Revision: ${drawings.revisionId}", 9f))
        document.add(paragraph("Method: ${drawings.methodReference}", 9f))
        drawings.incompleteChecks.forEach { document.add(paragraph("OPEN CHECK: $it", 9f, color = WARNING)) }
        drawings.orderedViews().forEach { document.add(paragraph("${it.id}: ${it.scene.title}", 8f, color = SECONDARY)) }
        drawings.orderedViews().forEach { view ->
            document.add(AreaBreak())
            document.add(sectionTitle("${view.kind.title} - ${drawings.memberMark}"))
            document.add(paragraph("Revision: ${drawings.revisionId} | Units: mm | View: ${view.id}", 8f, color = SECONDARY))
            document.add(paragraph("Method and open checks: see drawing evidence. Open checks: ${drawings.incompleteChecks.size}", 8f, color = SECONDARY))
            // Reserve one full page per view, independent of the number of extra sections.
            val form = PdfRenderer.createForm(view.scene, pdf, 480f, 540f)
            document.add(Image(form).setHorizontalAlignment(HorizontalAlignment.CENTER))
            if (drawings.incompleteChecks.isNotEmpty()) {
                document.add(paragraph("OPEN CHECKS: ${drawings.incompleteChecks.size}. See the full drawing evidence record.", 8f, color = WARNING))
            }
        }
        // Following report tables or footer must not share the final drawing page.
        document.add(AreaBreak())
    }

    private fun addDrawingSection(document: Document, bitmap: Bitmap?, title: String) {
        document.add(sectionTitle("5. Engineering Drawing"))
        document.add(paragraph("Legacy single-image drawing. Separate Plan, Side, Elevation and member sections have not been supplied.", 9f, color = WARNING))
        document.add(separator())

        if (bitmap == null) {
            document.add(Paragraph().add(text(
                "No drawing image was supplied for this report. Export from the element screen to include the generated detail drawing.",
                createPdfFont(), 9f, SECONDARY
            )).setTextAlignment(TextAlignment.CENTER))
            document.add(emptyLine())
            return
        }

        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setAutoScale(true)
            img.setMaxWidth(480f)
            img.setHorizontalAlignment(HorizontalAlignment.CENTER)
            document.add(img)
            document.add(Paragraph().add(text(title, createPdfFont(), 9f, SECONDARY))
                .setTextAlignment(TextAlignment.CENTER))
        } catch (e: Exception) {
            Log.e(TAG, "Drawing render error: ${e.message}")
            document.add(Paragraph().add(text("[Drawing render error: ${e.message}]",
                createPdfFont(), 9f, SECONDARY)).setTextAlignment(TextAlignment.CENTER))
        }
        document.add(emptyLine())
    }

    private fun addFooter(document: Document, config: ReportConfig) {
        document.add(emptyLine())
        document.add(separator())
        val font = createPdfFont()
        document.add(Paragraph().add(text("Generated by ${config.companyName} Pro Application - ${config.date}",
            font, 8f, SECONDARY)).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph().add(
            text("This report is for reference only and must be reviewed by a qualified engineer before construction.",
            font, 7f, LIGHT_GRAY)).setTextAlignment(TextAlignment.CENTER))
    }

    private fun addPhotoGallery(document: Document, photos: List<Bitmap>, title: String = "Site Inspection Photographs") {
        if (photos.isEmpty()) return
        document.add(AreaBreak())
        document.add(sectionTitle("Site Inspection Photographs"))
        document.add(separator())
        photos.forEachIndexed { i, bitmap ->
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val img = Image(ImageDataFactory.create(stream.toByteArray()))
                img.setAutoScale(true)
                img.setMaxWidth(380f)
                img.setHorizontalAlignment(HorizontalAlignment.CENTER)
                document.add(img)
                document.add(Paragraph().add(text("Figure ${i + 1}", createPdfFont(), 8f, SECONDARY))
                    .setTextAlignment(TextAlignment.CENTER))
                document.add(emptyLine())
            } catch (e: Exception) {
                Log.e(TAG, "Photo render error: ${e.message}")
            }
        }
    }

    private fun addSignatureBlock(document: Document, config: ReportConfig) {
        document.add(emptyLine())
        document.add(sectionTitle("Approval & Signature"))
        document.add(separator())
        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        table.addCell(signatureCell("Prepared by (Inspector)", config.engineerName))
        table.addCell(signatureCell("Reviewed / Approved by (Engineer)", ""))
        document.add(table)
    }

    private fun signatureCell(role: String, name: String): Cell {
        val cell = Cell().setPadding(8f).setBorder(com.itextpdf.layout.borders.SolidBorder(LIGHT_GRAY, 0.5f))
        cell.add(Paragraph().add(text(role, createBoldFont(), 9f, PRIMARY)))
        cell.add(Paragraph().add(text("Name: $name", createPdfFont(), 9f)))
        cell.add(Paragraph().add(text("\n________________________", createPdfFont(), 9f)))
        cell.add(Paragraph().add(text("Signature / Date", createPdfFont(), 8f, SECONDARY)))
        return cell
    }

    // ==================== Main Report Generation Method ====================

    /**
     * Generate a comprehensive professional PDF report in English.
     *
     * @param reportType Type of structural element being designed
     * @param title Report title (e.g., "Reinforced Concrete Beam Design Report")
     * @param subtitle Subtitle line
     * @param designCode Design code name (e.g., "ACI 318M-14")
     * @param inputs Map of design input parameters
     * @param results Map of design result values
     * @param safetyChecks List of safety verification checks
     * @param isSafe Overall design safety status
     * @param drawingBitmap Optional engineering drawing bitmap
     * @param calculationSteps Actual calculation trace; an empty list is explicitly marked as missing
     * @param config Report configuration (project name, date, etc.)
     * @param outputPath Absolute path for output PDF file
     * @return Generated File or null on failure
     */
    fun generateReport(
        reportType: ReportType,
        title: String,
        subtitle: String,
        designCode: String,
        inputs: Map<String, String>,
        results: Map<String, String>,
        safetyChecks: List<SafetyCheck>,
        isSafe: Boolean,
        drawingBitmap: Bitmap? = null,
        photos: List<Bitmap> = emptyList(),
        calculationSteps: List<CalculationStep> = emptyList(),
        bbsRows: List<BbsRow> = emptyList(),
        boqRows: List<BoqRow> = emptyList(),
        config: ReportConfig = ReportConfig(),
        outputPath: String,
        drawingDocument: DesignDrawingDocument? = null,
        recommendations: List<UnsafeDesignRecommendation> = emptyList(),
        methodEvidenceState: MethodEvidenceState = MethodEvidenceState.UNVERIFIED
    ): File? {
        return try {
            val (pdfDoc, document, _) = createDocument(outputPath)

            // Add page numbers
            addPageNumbers(pdfDoc)

            // 1. Cover Page (page 1)
            addCoverPage(document, config, title, subtitle, designCode)
            document.add(AreaBreak())  // New page

            // 2. Design Parameters (page 2)
            addDesignParameters(document, inputs)

            // 3. Calculation Methodology
            document.add(paragraph("METHOD EVIDENCE: ${methodEvidenceState.label}. Supplied data is not independent code-compliance certification.", 9f, color = WARNING))
            addCalculationMethodology(document, designCode, calculationSteps)

            // 4. Results
            addResultsSection(document, results)

            // 5. Safety Checks
            addSafetyChecks(document, safetyChecks, isSafe)

            addSuppliedRecommendations(document, recommendations, isSafe && safetyChecks.none { !it.passed })

            // A drawing document takes precedence over the old single screenshot.
            if (drawingDocument != null) {
                addDrawingDocument(document, pdfDoc, drawingDocument)
            } else {
                if (drawingBitmap != null) document.add(AreaBreak())
                addDrawingSection(document, drawingBitmap, "$title - Engineering Detail")
            }

            // 6. Bar Bending Schedule
            addBbsSection(document, bbsRows)

            // 7. Bill of Quantities
            addBoqSection(document, boqRows)

            // 8. Site Photographs (optional)
            addPhotoGallery(document, photos)

            // 9. Approval & Signature (optional)
            if (config.showSignature) {
                addSignatureBlock(document, config)
            }

            // 9. Footer
            addFooter(document, config)

            document.close()
            File(outputPath)
        } catch (e: Exception) {
            Log.e(TAG, "Report generation failed: ${e.message}", e)
            try {
                Log.e(TAG, "Stack trace:", e)
            } catch (_: Exception) {}
            null
        } catch (t: Throwable) {
            Log.e(TAG, "Report generation crashed: ${t.message}", t)
            null
        }
    }

    /**
     * Convenience method — generates report using the same signature as the old
     * ComprehensivePdfExporter.exportGenericReport for easy migration.
     */
    fun generateReportLegacy(
        titleAr: String,  // Ignored (English only)
        titleEn: String,
        subtitle: String,
        designType: String,
        inputs: Map<String, String>,
        results: Map<String, String>,
        safetyChecks: List<GenericSafetyCheck>,
        isSafe: Boolean,
        drawingBitmap: Bitmap?,
        outputPath: String,
        calculationSteps: List<CalculationStep> = emptyList(),
        bbsRows: List<BbsRow> = emptyList(),
        boqRows: List<BoqRow> = emptyList(),
        drawingDocument: DesignDrawingDocument? = null,
        recommendations: List<UnsafeDesignRecommendation> = emptyList(),
        methodEvidenceState: MethodEvidenceState = MethodEvidenceState.UNVERIFIED
    ): File? {
        val convertedChecks = safetyChecks.map {
            SafetyCheck(it.name, it.calculated, it.limit, it.unit, it.passed, it.reference)
        }

        // Determine report type from designType string
        val reportType = when {
            designType.contains("Beam", true) -> ReportType.BEAM
            designType.contains("Column", true) -> ReportType.COLUMN
            designType.contains("Slab", true) -> ReportType.SLAB
            designType.contains("Footing", true) -> ReportType.FOOTING
            designType.contains("Tank", true) -> ReportType.TANK
            designType.contains("Retaining", true) -> ReportType.RETAINING_WALL
            designType.contains("Stair", true) -> ReportType.STAIR
            designType.contains("Steel", true) -> ReportType.STEEL
            designType.contains("Frame", true) -> ReportType.FRAME_ANALYSIS
            designType.contains("Seismic", true) -> ReportType.SEISMIC
            else -> ReportType.GENERIC
        }

        // Extract design code from inputs if available
        val codeStr = inputs["Design Code"]?.takeIf { it.isNotBlank() } ?: "Not supplied"

        return generateReport(
            reportType = reportType,
            title = titleEn,
            subtitle = subtitle,
            designCode = codeStr,
            inputs = inputs,
            results = results,
            safetyChecks = convertedChecks,
            isSafe = isSafe,
            drawingBitmap = drawingBitmap,
            calculationSteps = calculationSteps,
            bbsRows = bbsRows,
            boqRows = boqRows,
            drawingDocument = drawingDocument,
            recommendations = recommendations,
            methodEvidenceState = methodEvidenceState,
            outputPath = outputPath
        )
    }

    // ==================== Page Number Handler ====================

    private fun addPageNumbers(pdfDoc: PdfDocument) {
        val font = try {
            EnglishFontProvider.getFont()
        } catch (_: Exception) {
            return
        }
        val footerColor = DeviceRgb(120, 120, 120)
        pdfDoc.addEventHandler(com.itextpdf.kernel.events.PdfDocumentEvent.END_PAGE) { event ->
            val docEvent = event as? com.itextpdf.kernel.events.PdfDocumentEvent ?: return@addEventHandler
            val page = docEvent.page ?: return@addEventHandler
            try {
                val pageSize = page.pageSize
                val canvas = com.itextpdf.kernel.pdf.canvas.PdfCanvas(
                    page.newContentStreamAfter(),
                    page.resources,
                    docEvent.document
                )
                // END_PAGE fires after the NEXT page may already exist, so
                // document.numberOfPages is off by one and duplicates the last
                // number. Use the event page's own index instead.
                val pageNumber = docEvent.document.getPageNumber(page)
                canvas.beginText()
                    .setFontAndSize(font, 8f)
                    .setColor(footerColor, true)
                    .moveText((pageSize.width - 70f).toDouble(), 20.0)
                    .showText("Page $pageNumber")
                    .endText()
                canvas.beginText()
                    .setFontAndSize(font, 8f)
                    .setColor(footerColor, true)
                    .moveText((pageSize.width / 2f - 60f).toDouble(), 20.0)
                    .showText("CivilEG - Structural Design")
                    .endText()
                canvas.release()
            } catch (_: Exception) {
            }
        }
    }
}
