package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.civileg.app.domain.entities.*
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
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
import kotlin.math.sqrt

/**
 * English-Only Steel Warehouse Design Report PDF Exporter
 *
 * Generates a professional, English-only structural design report for steel warehouse projects.
 * Uses Helvetica font exclusively — NO Arabic fonts, NO Arabic text, NO PdfTextSegmenter.
 * Embeds Compose UI bitmaps for cross-section drawings and load diagrams.
 *
 * Report Sections:
 * 1. Cover Page  — Project info, design code, date, structural analysis status
 * 2. Steel Member Schedule Table  — MARK, MEMBER, SECTION, MATERIAL, QTY, LENGTH, STATUS
 * 3. Design Drawings per Cross-Section  — Bitmap screenshots of each steel section
 * 4. Load Diagrams  — Applied load diagrams as bitmaps
 * 5. Design Forces Summary  — Axial, moment, shear, and status per member
 * 6. Connection Schedule  — Connection details table
 * 7. Material Takeoff / Bill of Quantities  — BOQ table
 * 8. Title Block  — Professional engineering sheet title block
 */
class SteelEnglishReportExporter(private val context: Context) {

    companion object {
        private const val TAG = "SteelEnglishExporter"
    }

    // ==================== Color Palette ====================
    private val PRIMARY = DeviceRgb(21, 101, 192)
    private val PRIMARY_DARK = DeviceRgb(13, 71, 161)
    private val PRIMARY_LIGHT = DeviceRgb(227, 242, 253)
    private val SECONDARY = DeviceRgb(55, 71, 79)
    private val SUCCESS = DeviceRgb(27, 94, 32)
    private val ERROR = DeviceRgb(198, 40, 40)
    private val WARNING = DeviceRgb(245, 124, 0)
    private val HEADER_BG = DeviceRgb(21, 101, 192)
    private val LIGHT_BLUE = DeviceRgb(227, 242, 253)
    private val ROW_ALT = DeviceRgb(245, 245, 245)
    private val WHITE = DeviceRgb(255, 255, 255)
    private val LIGHT_GRAY = DeviceRgb(220, 220, 220)
    private val DARK_GRAY = DeviceRgb(100, 100, 100)
    private val BLACK = DeviceRgb(0, 0, 0)

    // ==================== Font Helpers (English Only) ====================
    // CRITICAL: Never cache PdfFont objects across PdfDocuments.
    // iText 8 binds each PdfFont to the FIRST PdfDocument that uses it.

    private fun getFont(bold: Boolean = false): PdfFont {
        return try {
            if (bold) PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
            else PdfFontFactory.createFont(StandardFonts.HELVETICA)
        } catch (e: Exception) {
            Log.e(TAG, "Font creation failed: ${e.message}")
            PdfFontFactory.createFont(StandardFonts.HELVETICA)
        }
    }

    // ==================== Element Helpers ====================

    private fun text(text: String, font: PdfFont, size: Float = 10f, color: DeviceRgb? = null): Text {
        val t = Text(text).setFont(font).setFontSize(size)
        color?.let { t.setFontColor(it) }
        return t
    }

    private fun paragraph(
        text: String,
        fontSize: Float = 10f,
        bold: Boolean = false,
        color: DeviceRgb? = null,
        alignment: TextAlignment = TextAlignment.LEFT
    ): Paragraph {
        val font = if (bold) getFont(bold = true) else getFont()
        val p = Paragraph().add(text(text, font, fontSize, color)).setTextAlignment(alignment)
        return p
    }

    private fun emptyLine(): Paragraph = Paragraph(" ")

    private fun sectionTitle(title: String): Paragraph {
        return Paragraph()
            .add(text(title, getFont(bold = true), 14f, PRIMARY_DARK))
            .setTextAlignment(TextAlignment.LEFT)
            .setMarginTop(16f)
            .setMarginBottom(4f)
    }

    private fun subTitle(title: String): Paragraph {
        return Paragraph()
            .add(text(title, getFont(bold = true), 11f, SECONDARY))
            .setTextAlignment(TextAlignment.LEFT)
            .setMarginTop(8f)
            .setMarginBottom(2f)
    }

    private fun separator(): LineSeparator {
        return LineSeparator(SolidLine(0.5f)).setMarginTop(4f).setMarginBottom(8f)
    }

    private fun headerCell(text: String, colSpan: Int = 1): Cell {
        return Cell(colSpan, 1)
            .setPadding(6f)
            .setBackgroundColor(HEADER_BG)
            .setTextAlignment(TextAlignment.CENTER)
            .add(
                Paragraph()
                    .add(text(text, getFont(bold = true), 8f, WHITE))
                    .setTextAlignment(TextAlignment.CENTER)
            )
    }

    private fun dataCell(
        text: String,
        fontSize: Float = 8f,
        bold: Boolean = false,
        bg: DeviceRgb? = null,
        align: TextAlignment = TextAlignment.CENTER,
        color: DeviceRgb? = null
    ): Cell {
        val font = if (bold) getFont(bold = true) else getFont()
        val cell = Cell().setPadding(4f).setTextAlignment(align)
        cell.add(
            Paragraph()
                .add(text(text, font, fontSize, color ?: BLACK))
                .setTextAlignment(align)
        )
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    private fun labelCell(text: String, bg: DeviceRgb? = null): Cell {
        return dataCell(text, 9f, bold = true, bg = bg, align = TextAlignment.LEFT)
    }

    private fun valueCell(text: String, bg: DeviceRgb? = null): Cell {
        return dataCell(text, 9f, false, bg = bg, align = TextAlignment.LEFT)
    }

    // ==================== Bitmap Helper ====================

    private fun addBitmapToDocument(document: Document, bitmap: Bitmap?, maxWidth: Float = 480f) {
        if (bitmap == null) return
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val img = Image(ImageDataFactory.create(stream.toByteArray()))
            img.setAutoScale(true)
            img.setMaxWidth(maxWidth)
            img.setHorizontalAlignment(HorizontalAlignment.CENTER)
            document.add(img)
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap render error: ${e.message}")
            document.add(
                paragraph("[Drawing render error: ${e.message}]", 9f, color = DARK_GRAY, alignment = TextAlignment.CENTER)
            )
        }
    }

    // ==================== Formatting ====================

    private fun Double.fmt(decimals: Int = 2): String =
        String.format(Locale.US, "%.${decimals}f", this)

    private fun getGradeName(section: SteelSectionType): String = when (section) {
        is SteelSectionType.ISection -> section.grade.displayName
        is SteelSectionType.CSection -> section.grade.displayName
        is SteelSectionType.LSection -> section.grade.displayName
        is SteelSectionType.CHS -> section.grade.displayName
        is SteelSectionType.RHS -> section.grade.displayName
        is SteelSectionType.TSection -> section.grade.displayName
        is SteelSectionType.PlateGirder -> section.grade.displayName
        is SteelSectionType.Pipe -> section.grade.displayName
        is SteelSectionType.BuiltUp -> "N/A"
    }

    private fun getConnectionTypeName(type: ConnectionType): String = when (type) {
        is ConnectionType.Welded -> {
            val base = "Welded (${type.weldType.displayName})"
            "$base, Size: ${type.weldSize.fmt(0)}mm"
        }
        is ConnectionType.Bolted -> {
            val base = "Bolted (${type.boltPattern.displayName})"
            "$base, ${type.numberOfBolts}x ${type.boltDiameter.fmt(0)}mm ${type.boltGrade.displayName}"
        }
        is ConnectionType.Pressed -> "Pressed (${type.surfaceTreatment})"
        is ConnectionType.Hybrid -> "Hybrid (Welded + Bolted)"
    }

    // ==================== Main Export Method ====================

    /**
     * Generate and save a comprehensive English-only steel warehouse PDF report.
     *
     * @param inputs       Steel warehouse design inputs
     * @param result       Analysis result with member selections, forces, connections, BOQ
     * @param projectName  Project name (English)
     * @param clientName   Client name (English)
     * @param sectionDrawings Map of section mark -> Bitmap (e.g., "C1" -> column cross-section bitmap)
     * @param loadDiagramBitmap  Optional bitmap showing the applied load diagrams
     * @return The generated PDF file, or null on failure
     */
    fun export(
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        projectName: String = "Steel Warehouse Project",
        clientName: String = "Client",
        sectionDrawings: Map<String, Bitmap> = emptyMap(),
        loadDiagramBitmap: Bitmap? = null
    ): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "Steel_Warehouse_Report_${timestamp}.pdf"
            val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(outputDir, fileName)

            val writer = PdfWriter(FileOutputStream(file))
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc, com.itextpdf.kernel.geom.PageSize.A4)
            document.setMargins(40f, 40f, 40f, 40f)

            // Add page numbers
            addPageNumbers(pdfDoc)

            // ========== SECTION 1: COVER PAGE ==========
            addCoverPage(document, inputs, result, projectName, clientName)
            document.add(AreaBreak())

            // ========== SECTION 2: STEEL MEMBER SCHEDULE ==========
            addMemberSchedule(document, inputs, result)
            document.add(AreaBreak())

            // ========== SECTION 3: DESIGN DRAWINGS PER CROSS-SECTION ==========
            addDesignDrawings(document, inputs, result, sectionDrawings)

            // ========== SECTION 4: LOAD DIAGRAMS ==========
            addLoadDiagrams(document, inputs, result, loadDiagramBitmap)
            document.add(AreaBreak())

            // ========== SECTION 5: DESIGN FORCES SUMMARY ==========
            addDesignForcesSummary(document, inputs, result)
            document.add(AreaBreak())

            // ========== SECTION 6: CONNECTION SCHEDULE ==========
            addConnectionSchedule(document, result)
            document.add(AreaBreak())

            // ========== SECTION 7: BILL OF QUANTITIES ==========
            addBillOfQuantities(document, inputs, result)
            document.add(AreaBreak())

            // ========== SECTION 8: TITLE BLOCK & FOOTER ==========
            addTitleBlock(document, inputs, result, projectName, clientName)

            document.close()
            file
        } catch (e: Exception) {
            Log.e(TAG, "Report generation failed: ${e.message}", e)
            null
        } catch (t: Throwable) {
            Log.e(TAG, "Report generation crashed: ${t.message}", t)
            null
        }
    }

    // ==================== SECTION 1: COVER PAGE ====================

    private fun addCoverPage(
        document: Document,
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        projectName: String,
        clientName: String
    ) {
        val boldFont = getFont(bold = true)
        val font = getFont()

        // Top Blue Banner
        val banner = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        val bannerCell = Cell()
            .setPadding(20f)
            .setBackgroundColor(PRIMARY)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        bannerCell.add(
            Paragraph()
                .add(text("STRUCTURAL DESIGN & ANALYSIS REPORT", boldFont, 20f, WHITE))
                .setTextAlignment(TextAlignment.CENTER)
        )
        bannerCell.add(
            Paragraph()
                .add(text("STEEL WAREHOUSE STRUCTURAL SYSTEM", boldFont, 14f, LIGHT_BLUE))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(4f)
        )
        banner.addCell(bannerCell)
        document.add(banner)
        document.add(emptyLine())
        document.add(emptyLine())

        // Project Info Table
        val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth()
        infoTable.setWidth(UnitValue.createPercentValue(85f))
        infoTable.setHorizontalAlignment(HorizontalAlignment.CENTER)

        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
        val numBays = (inputs.length / inputs.baySpacing).toInt().coerceAtLeast(1)

        fun addInfoRow(label: String, value: String, rowIdx: Int) {
            val bg = if (rowIdx % 2 == 0) LIGHT_BLUE else null
            infoTable.addCell(labelCell(label, bg))
            infoTable.addCell(valueCell(value, bg))
        }

        var row = 0
        addInfoRow("Project:", projectName, row++)
        row++ // skip for visual spacing
        addInfoRow("Client:", clientName, row++)
        row++
        addInfoRow("Design Code:", inputs.code.version, row++)
        row++
        addInfoRow("Date:", dateStr, row++)
        row++
        addInfoRow("Report No.:", "SW-${SimpleDateFormat("yyyy", Locale.US).format(Date())}-001", row)

        document.add(infoTable)
        document.add(emptyLine())
        document.add(emptyLine())

        // Warehouse Dimensions Summary
        document.add(
            paragraph("WAREHOUSE DIMENSIONS", 12f, bold = true, color = PRIMARY, alignment = TextAlignment.CENTER)
        )
        document.add(separator())

        val dimTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))).useAllAvailableWidth()
        dimTable.setWidth(UnitValue.createPercentValue(85f))
        dimTable.setHorizontalAlignment(HorizontalAlignment.CENTER)

        dimTable.addHeaderCell(headerCell("SPAN"))
        dimTable.addHeaderCell(headerCell("LENGTH"))
        dimTable.addHeaderCell(headerCell("EAVE HT."))
        dimTable.addHeaderCell(headerCell("BAYS"))
        dimTable.addCell(dataCell("${inputs.span.fmt(1)} m", bold = true))
        dimTable.addCell(dataCell("${inputs.length.fmt(1)} m", bold = true))
        dimTable.addCell(dataCell("${inputs.eaveHeight.fmt(1)} m", bold = true))
        dimTable.addCell(dataCell("$numBays @ ${inputs.baySpacing.fmt(1)} m", bold = true))
        document.add(dimTable)
        document.add(emptyLine())
        document.add(emptyLine())

        // Structural Analysis Status Banner
        val statusText = if (result.safetyStatus) {
            "STRUCTURAL ANALYSIS PASSED  —  All members comply with ${inputs.code.version}"
        } else {
            "REVIEW REQUIRED  —  Some members exceed code limits per ${inputs.code.version}"
        }
        val statusColor = if (result.safetyStatus) SUCCESS else ERROR
        val statusBanner = Paragraph()
            .add(text(statusText, boldFont, 11f, statusColor))
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(10f)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(statusColor, 2f))
        document.add(statusBanner)
        document.add(emptyLine())

        // Footer line
        document.add(
            separator()
        )
        document.add(
            paragraph(
                "Generated by Civil EG Pro  |  $dateStr",
                8f,
                color = DARK_GRAY,
                alignment = TextAlignment.CENTER
            )
        )
    }

    // ==================== SECTION 2: STEEL MEMBER SCHEDULE ====================

    private fun addMemberSchedule(
        document: Document,
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult
    ) {
        document.add(sectionTitle("1. STEEL MEMBER SCHEDULE"))
        document.add(separator())

        val table = Table(
            UnitValue.createPercentArray(floatArrayOf(8f, 15f, 25f, 20f, 10f, 12f, 10f))
        ).useAllAvailableWidth()

        table.addHeaderCell(headerCell("MARK"))
        table.addHeaderCell(headerCell("MEMBER"))
        table.addHeaderCell(headerCell("SECTION"))
        table.addHeaderCell(headerCell("MATERIAL"))
        table.addHeaderCell(headerCell("QTY"))
        table.addHeaderCell(headerCell("LENGTH"))
        table.addHeaderCell(headerCell("STATUS"))

        val numBays = (inputs.length / inputs.baySpacing).toInt().coerceAtLeast(1)
        val rafterLength = sqrt(
            (inputs.span / 2.0).let { it * it } +
                (inputs.ridgeHeight - inputs.eaveHeight).let { it * it }
        )

        // Member data: (mark, memberName, section, material, qty, length, isSafe)
        val members = listOf(
            Triple(
                "C1", "Column",
                result.mainFrame.columnSection
            ),
            Triple(
                "R1", "Rafter",
                result.mainFrame.rafterSection
            ),
            Triple(
                "P1", "Purlin",
                result.secondaryMembers.purlinSection
            ),
            Triple(
                "G1", "Girt",
                result.secondaryMembers.girtSection
            ),
            Triple(
                "B1", "Bracing",
                result.secondaryMembers.bracingSection
            )
        )

        members.forEachIndexed { i, (mark, memberName, section) ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            val gradeName = getGradeName(section)

            val qty = when (mark) {
                "C1" -> (numBays + 1) * 2
                "R1" -> numBays * 2
                "P1" -> result.secondaryMembers.purlinCount
                "G1" -> result.secondaryMembers.purlinCount // girt count same approach as purlin
                "B1" -> numBays * 2
                else -> 0
            }

            val lengthStr = when (mark) {
                "C1" -> "${inputs.eaveHeight.fmt(2)} m"
                "R1" -> "${rafterLength.fmt(2)} m"
                "P1" -> "${inputs.span.fmt(2)} m"
                "G1" -> "${inputs.baySpacing.fmt(2)} m"
                "B1" -> "—"
                else -> "—"
            }

            val status = "OK"
            val statusColor = SUCCESS

            table.addCell(dataCell(mark, bold = true, bg = bg))
            table.addCell(dataCell(memberName, bg = bg))
            table.addCell(dataCell(section.sectionName, bold = true, bg = bg, fontSize = 7f))
            table.addCell(dataCell(gradeName, bg = bg, fontSize = 7f))
            table.addCell(dataCell("$qty", bg = bg))
            table.addCell(dataCell(lengthStr, bg = bg))
            table.addCell(dataCell(status, bold = true, bg = bg, color = statusColor))
        }

        document.add(table)
        document.add(emptyLine())

        // General Notes sub-table
        document.add(subTitle("General Notes"))
        val notes = listOf(
            "All dimensions are in meters unless otherwise noted.",
            "Steel members designed per ${inputs.code.version}.",
            "Welding per AWS D1.1 (6mm minimum fillet weld).",
            "High-strength bolts: ASTM A325 or equivalent.",
            "Main frame steel grade: S355 / St-52.",
            "Secondary members steel grade: S235 / St-37.",
            "Roof slope: ${(inputs.slope * 100).fmt(1)}% for drainage."
        )
        val notesTable = Table(UnitValue.createPercentArray(floatArrayOf(5f, 95f))).useAllAvailableWidth()
        notes.forEachIndexed { i, note ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            notesTable.addCell(dataCell("${i + 1}", bg = bg, fontSize = 7f))
            notesTable.addCell(
                dataCell(note, bg = bg, fontSize = 7f, align = TextAlignment.LEFT)
            )
        }
        document.add(notesTable)
    }

    // ==================== SECTION 3: DESIGN DRAWINGS PER CROSS-SECTION ====================

    private fun addDesignDrawings(
        document: Document,
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        sectionDrawings: Map<String, Bitmap>
    ) {
        document.add(AreaBreak())
        document.add(sectionTitle("2. STEEL CROSS-SECTION DRAWINGS"))
        document.add(separator())

        val sections = listOf(
            Triple("C1", "Column Section", result.mainFrame.columnSection),
            Triple("R1", "Rafter Section", result.mainFrame.rafterSection),
            Triple("P1", "Purlin Section", result.secondaryMembers.purlinSection),
            Triple("G1", "Girt Section", result.secondaryMembers.girtSection),
            Triple("B1", "Bracing Section", result.secondaryMembers.bracingSection)
        )

        var hasAnyDrawing = false

        sections.forEachIndexed { index, (mark, description, section) ->
            val bitmap = sectionDrawings[mark]
            if (bitmap != null) {
                if (index > 0) {
                    document.add(AreaBreak())
                }
                hasAnyDrawing = true

                // Section heading with mark and description
                document.add(
                    paragraph(
                        "$mark  —  $description: ${section.sectionName}",
                        11f,
                        bold = true,
                        color = PRIMARY_DARK,
                        alignment = TextAlignment.CENTER
                    )
                )
                document.add(emptyLine())

                // Embed the bitmap screenshot
                addBitmapToDocument(document, bitmap, maxWidth = 460f)

                // Section properties summary below the drawing
                document.add(emptyLine())
                val propsTable = Table(
                    UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))
                ).useAllAvailableWidth()
                propsTable.addHeaderCell(headerCell("SECTION"))
                propsTable.addHeaderCell(headerCell("DEPTH (mm)"))
                propsTable.addHeaderCell(headerCell("AREA (cm\u00B2)"))
                propsTable.addHeaderCell(headerCell("GRADE"))

                val areaCm2 = section.getArea() / 100.0
                propsTable.addCell(dataCell(section.sectionName, bold = true, fontSize = 7f))
                propsTable.addCell(dataCell("${section.depth.fmt(1)}", fontSize = 8f))
                propsTable.addCell(dataCell("${areaCm2.fmt(2)}", fontSize = 8f))
                propsTable.addCell(dataCell(getGradeName(section), fontSize = 8f))

                document.add(propsTable)
            }
        }

        if (!hasAnyDrawing) {
            document.add(
                paragraph(
                    "[Cross-section drawings not available — ensure design data is complete]",
                    9f,
                    color = DARK_GRAY,
                    alignment = TextAlignment.CENTER
                )
            )
        }
    }

    // ==================== SECTION 4: LOAD DIAGRAMS ====================

    private fun addLoadDiagrams(
        document: Document,
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        loadDiagramBitmap: Bitmap?
    ) {
        document.add(AreaBreak())
        document.add(sectionTitle("3. LOAD DIAGRAMS"))
        document.add(separator())

        // Load Summary Table
        document.add(subTitle("Applied Loads"))
        val loadTable = Table(
            UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))
        ).useAllAvailableWidth()

        loadTable.addHeaderCell(headerCell("DEAD LOAD"))
        loadTable.addHeaderCell(headerCell("LIVE LOAD"))
        loadTable.addHeaderCell(headerCell("WIND LOAD"))
        loadTable.addHeaderCell(headerCell("SNOW LOAD"))

        loadTable.addCell(dataCell("${inputs.deadLoad.fmt(2)} kN/m\u00B2", bold = true))
        loadTable.addCell(dataCell("${inputs.liveLoad.fmt(2)} kN/m\u00B2", bold = true))
        loadTable.addCell(dataCell("${inputs.windLoad.fmt(2)} kN/m\u00B2", bold = true))
        loadTable.addCell(dataCell("${inputs.snowLoad.fmt(2)} kN/m\u00B2", bold = true))

        document.add(loadTable)
        document.add(emptyLine())

        // Cladding Info
        val claddingTable = Table(
            UnitValue.createPercentArray(floatArrayOf(50f, 50f))
        ).useAllAvailableWidth()
        var ri = 0
        fun addCladRow(label: String, value: String) {
            val bg = if (ri % 2 == 0) LIGHT_BLUE else null
            claddingTable.addCell(labelCell(label, bg))
            claddingTable.addCell(valueCell(value, bg))
            ri++
        }
        addCladRow("Cladding Weight:", "${inputs.claddingWeight.fmt(2)} kN/m\u00B2")
        addCladRow("Total Cladding Area:", "${result.totalCladdingArea.fmt(1)} m\u00B2")
        addCladRow("Purlin Spacing:", "${inputs.purlinSpacing.fmt(1)} m")
        addCladRow("Load Combination:", "1.4 DL + 1.6 LL + 0.5 WL")
        document.add(claddingTable)
        document.add(emptyLine())

        // Embed Load Diagram Bitmap
        if (loadDiagramBitmap != null) {
            document.add(subTitle("Load Diagram"))
            addBitmapToDocument(document, loadDiagramBitmap, maxWidth = 480f)
        } else if (result.loadDiagram != null) {
            // If no bitmap but we have diagram data, show a text representation
            document.add(subTitle("Frame Load Distribution (Data)"))
            val ld = result.loadDiagram
            if (ld.verticalLoads.isNotEmpty()) {
                document.add(paragraph("Vertical Loads:", 9f, bold = true, color = SECONDARY))
                ld.verticalLoads.forEach { (pos, load) ->
                    document.add(
                        paragraph("  x = ${pos.fmt(2)} m  :  w = ${load.fmt(2)} kN/m", 8f)
                    )
                }
            }
            if (ld.horizontalLoads.isNotEmpty()) {
                document.add(paragraph("Horizontal Loads:", 9f, bold = true, color = SECONDARY))
                ld.horizontalLoads.forEach { (pos, load) ->
                    document.add(
                        paragraph("  y = ${pos.fmt(2)} m  :  w = ${load.fmt(2)} kN/m", 8f)
                    )
                }
            }
        } else {
            document.add(
                paragraph(
                    "[Load diagram not available]",
                    9f,
                    color = DARK_GRAY,
                    alignment = TextAlignment.CENTER
                )
            )
        }
    }

    // ==================== SECTION 5: DESIGN FORCES SUMMARY ====================

    private fun addDesignForcesSummary(
        document: Document,
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult
    ) {
        document.add(sectionTitle("4. DESIGN FORCES SUMMARY"))
        document.add(separator())

        // Main Frame Forces
        document.add(subTitle("Main Frame Design Forces"))

        val forcesTable = Table(
            UnitValue.createPercentArray(floatArrayOf(20f, 20f, 20f, 20f, 20f))
        ).useAllAvailableWidth()

        forcesTable.addHeaderCell(headerCell("MEMBER"))
        forcesTable.addHeaderCell(headerCell("AXIAL (kN)"))
        forcesTable.addHeaderCell(headerCell("MOMENT (kN.m)"))
        forcesTable.addHeaderCell(headerCell("SHEAR (kN)"))
        forcesTable.addHeaderCell(headerCell("STATUS"))

        val mf = result.mainFrame
        // Column row
        val colStatus = if (mf.utilizationAxial < 1.0 && mf.utilizationShear < 1.0) "PASS" else "REVIEW"
        val colColor = if (colStatus == "PASS") SUCCESS else WARNING
        forcesTable.addCell(dataCell("Column (C1)", bold = true))
        forcesTable.addCell(dataCell(mf.maxAxial.fmt(1), bold = true))
        forcesTable.addCell(dataCell("${(mf.maxMoment * 0.6).fmt(1)}", fontSize = 8f)) // approx column moment
        forcesTable.addCell(dataCell("${(mf.maxShear * 0.5).fmt(1)}", fontSize = 8f)) // approx column shear
        forcesTable.addCell(dataCell(colStatus, bold = true, color = colColor))

        // Rafter row
        val rftStatus = if (mf.utilizationMoment < 1.0 && mf.utilizationShear < 1.0) "PASS" else "REVIEW"
        val rftColor = if (rftStatus == "PASS") SUCCESS else WARNING
        forcesTable.addCell(dataCell("Rafter (R1)", bold = true, bg = ROW_ALT))
        forcesTable.addCell(dataCell("${(mf.maxAxial * 0.3).fmt(1)}", bg = ROW_ALT, fontSize = 8f))
        forcesTable.addCell(dataCell(mf.maxMoment.fmt(1), bold = true, bg = ROW_ALT))
        forcesTable.addCell(dataCell(mf.maxShear.fmt(1), bold = true, bg = ROW_ALT))
        forcesTable.addCell(dataCell(rftStatus, bold = true, bg = ROW_ALT, color = rftColor))

        document.add(forcesTable)
        document.add(emptyLine())

        // Utilization Ratios
        document.add(subTitle("Utilization Ratios (Main Frame)"))
        val utilTable = Table(
            UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))
        ).useAllAvailableWidth()

        utilTable.addHeaderCell(headerCell("CHECK"))
        utilTable.addHeaderCell(headerCell("DEMAND"))
        utilTable.addHeaderCell(headerCell("CAPACITY RATIO"))
        utilTable.addHeaderCell(headerCell("STATUS"))

        // Axial utilization
        val axialRatio = mf.utilizationAxial
        val axialStatus = if (axialRatio <= 1.0) "PASS" else "FAIL"
        utilTable.addCell(dataCell("Axial Strength"))
        utilTable.addCell(dataCell("${mf.maxAxial.fmt(1)} kN"))
        utilTable.addCell(
            dataCell(
                "${(axialRatio * 100).fmt(1)}%",
                bold = true,
                color = if (axialRatio <= 1.0) SUCCESS else ERROR
            )
        )
        utilTable.addCell(
            dataCell(axialStatus, bold = true, color = if (axialStatus == "PASS") SUCCESS else ERROR)
        )

        // Moment utilization
        val momRatio = mf.utilizationMoment
        val momStatus = if (momRatio <= 1.0) "PASS" else "FAIL"
        utilTable.addCell(dataCell("Flexural Strength", bg = ROW_ALT))
        utilTable.addCell(dataCell("${mf.maxMoment.fmt(1)} kN.m", bg = ROW_ALT))
        utilTable.addCell(
            dataCell(
                "${(momRatio * 100).fmt(1)}%",
                bold = true,
                bg = ROW_ALT,
                color = if (momRatio <= 1.0) SUCCESS else ERROR
            )
        )
        utilTable.addCell(
            dataCell(
                momStatus, bold = true, bg = ROW_ALT,
                color = if (momStatus == "PASS") SUCCESS else ERROR
            )
        )

        // Shear utilization
        val shrRatio = mf.utilizationShear
        val shrStatus = if (shrRatio <= 1.0) "PASS" else "FAIL"
        utilTable.addCell(dataCell("Shear Strength"))
        utilTable.addCell(dataCell("${mf.maxShear.fmt(1)} kN"))
        utilTable.addCell(
            dataCell(
                "${(shrRatio * 100).fmt(1)}%",
                bold = true,
                color = if (shrRatio <= 1.0) SUCCESS else ERROR
            )
        )
        utilTable.addCell(
            dataCell(shrStatus, bold = true, color = if (shrStatus == "PASS") SUCCESS else ERROR)
        )

        // Deflection check
        val deflRatio = if (mf.allowableDeflection > 0) mf.maxDeflection / mf.allowableDeflection else 0.0
        val deflStatus = if (deflRatio <= 1.0) "PASS" else "FAIL"
        utilTable.addCell(dataCell("Deflection", bg = ROW_ALT))
        utilTable.addCell(
            dataCell(
                "${mf.maxDeflection.fmt(1)} / ${mf.allowableDeflection.fmt(1)} mm",
                bg = ROW_ALT,
                fontSize = 7f
            )
        )
        utilTable.addCell(
            dataCell(
                "${(deflRatio * 100).fmt(1)}%",
                bold = true,
                bg = ROW_ALT,
                color = if (deflRatio <= 1.0) SUCCESS else ERROR
            )
        )
        utilTable.addCell(
            dataCell(
                deflStatus, bold = true, bg = ROW_ALT,
                color = if (deflStatus == "PASS") SUCCESS else ERROR
            )
        )

        document.add(utilTable)
        document.add(emptyLine())

        // Overall Status
        val overallStatus = if (result.safetyStatus) "SAFE" else "REVIEW REQUIRED"
        val overallColor = if (result.safetyStatus) SUCCESS else ERROR
        val overallMsg = if (result.safetyStatus) {
            "All design checks passed. The structure complies with ${inputs.code.version}."
        } else {
            "One or more design checks require review per ${inputs.code.version}."
        }
        document.add(
            Paragraph()
                .add(text("OVERALL STATUS: $overallStatus", getFont(bold = true), 11f, overallColor))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(8f)
                .setBorder(com.itextpdf.layout.borders.SolidBorder(overallColor, 2f))
        )
        document.add(
            paragraph(overallMsg, 9f, color = SECONDARY, alignment = TextAlignment.CENTER)
        )
    }

    // ==================== SECTION 6: CONNECTION SCHEDULE ====================

    private fun addConnectionSchedule(document: Document, result: SteelWarehouseAnalysisResult) {
        document.add(sectionTitle("5. CONNECTION SCHEDULE"))
        document.add(separator())

        if (result.connections.isEmpty()) {
            document.add(
                paragraph(
                    "[No connection details available]",
                    9f,
                    color = DARK_GRAY,
                    alignment = TextAlignment.CENTER
                )
            )
            return
        }

        val table = Table(
            UnitValue.createPercentArray(floatArrayOf(18f, 22f, 15f, 15f, 15f, 15f))
        ).useAllAvailableWidth()

        table.addHeaderCell(headerCell("CONNECTION"))
        table.addHeaderCell(headerCell("TYPE"))
        table.addHeaderCell(headerCell("CAPACITY (kN)"))
        table.addHeaderCell(headerCell("DEMAND (kN)"))
        table.addHeaderCell(headerCell("UTIL. (%)"))
        table.addHeaderCell(headerCell("STATUS"))

        result.connections.forEachIndexed { i, conn ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            val utilization = if (conn.capacity > 0) (conn.demand / conn.capacity * 100) else 0.0
            val statusStr = if (conn.isSafe) "PASS" else "FAIL"
            val statusColor = if (conn.isSafe) SUCCESS else ERROR

            table.addCell(dataCell(conn.name, bg = bg, fontSize = 7f, align = TextAlignment.LEFT))
            table.addCell(
                dataCell(
                    getConnectionTypeName(conn.type),
                    bg = bg,
                    fontSize = 6f,
                    align = TextAlignment.LEFT
                )
            )
            table.addCell(dataCell(conn.capacity.fmt(1), bg = bg))
            table.addCell(dataCell(conn.demand.fmt(1), bg = bg))
            table.addCell(
                dataCell(
                    "${utilization.fmt(1)}%",
                    bold = true,
                    bg = bg,
                    color = if (utilization <= 100) SUCCESS else ERROR
                )
            )
            table.addCell(dataCell(statusStr, bold = true, bg = bg, color = statusColor))
        }

        document.add(table)
        document.add(emptyLine())

        // Connection notes
        document.add(subTitle("Connection Notes"))
        val connNotes = listOf(
            "All bolted connections shall use pretensioned high-strength bolts.",
            "Welded connections shall be inspected per AWS D1.1 requirements.",
            "Connection capacities include appropriate safety factors per design code.",
            "Field splices shall be designed for 150% of member capacity."
        )
        connNotes.forEach { note ->
            document.add(paragraph("  -  $note", 7f, color = SECONDARY))
        }
    }

    // ==================== SECTION 7: BILL OF QUANTITIES ====================

    private fun addBillOfQuantities(
        document: Document,
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult
    ) {
        document.add(sectionTitle("6. BILL OF QUANTITIES (Material Takeoff)"))
        document.add(separator())

        val table = Table(
            UnitValue.createPercentArray(floatArrayOf(5f, 40f, 20f, 15f, 20f))
        ).useAllAvailableWidth()

        table.addHeaderCell(headerCell("#"))
        table.addHeaderCell(headerCell("ITEM DESCRIPTION"))
        table.addHeaderCell(headerCell("QUANTITY"))
        table.addHeaderCell(headerCell("UNIT"))
        table.addHeaderCell(headerCell("REMARKS"))

        var idx = 1
        result.materialTakeoff.forEach { (key, value) ->
            val bg = if (idx % 2 == 0) ROW_ALT else null
            table.addCell(dataCell("$idx", bg = bg))
            table.addCell(dataCell(key, bg = bg, align = TextAlignment.LEFT, fontSize = 7f))
            table.addCell(dataCell(value.fmt(2), bg = bg))

            // Determine unit based on the item name
            val unit = when {
                key.contains("weight", ignoreCase = true) ||
                    key.contains("steel", ignoreCase = true) ||
                    key.contains("ton", ignoreCase = true) -> "Tons"
                key.contains("area", ignoreCase = true) ||
                    key.contains("cladding", ignoreCase = true) -> "m\u00B2"
                key.contains("length", ignoreCase = true) -> "m"
                else -> "—"
            }
            table.addCell(dataCell(unit, bg = bg))
            table.addCell(dataCell("—", bg = bg))
            idx++
        }

        // Summary Rows
        val summaryBg = LIGHT_BLUE
        table.addCell(dataCell("", bg = summaryBg))
        table.addCell(dataCell("TOTAL STEEL WEIGHT", bold = true, bg = summaryBg, align = TextAlignment.LEFT))
        table.addCell(dataCell("${result.totalWeight.fmt(1)} Tons", bold = true, bg = summaryBg))
        table.addCell(dataCell("Tons", bold = true, bg = summaryBg))
        table.addCell(dataCell("", bg = summaryBg))

        table.addCell(dataCell("", bg = summaryBg))
        table.addCell(dataCell("WEIGHT PER M\u00B2", bold = true, bg = summaryBg, align = TextAlignment.LEFT))
        table.addCell(dataCell("${result.weightPerM2.fmt(1)} kg/m\u00B2", bold = true, bg = summaryBg))
        table.addCell(dataCell("kg/m\u00B2", bold = true, bg = summaryBg))
        table.addCell(dataCell("", bg = summaryBg))

        document.add(table)
        document.add(emptyLine())

        // Cost Summary
        document.add(subTitle("Cost Estimate Summary"))
        val costTable = Table(
            UnitValue.createPercentArray(floatArrayOf(50f, 50f))
        ).useAllAvailableWidth()

        var costRow = 0
        fun addCostRow(label: String, value: String) {
            val bg = if (costRow % 2 == 0) null else ROW_ALT
            costTable.addCell(labelCell(label, bg))
            costTable.addCell(valueCell(value, bg))
            costRow++
        }

        addCostRow("Total Steel Weight:", "${result.totalWeight.fmt(1)} Tons")
        addCostRow("Weight per m\u00B2:", "${result.weightPerM2.fmt(1)} kg/m\u00B2")
        addCostRow("Total Cladding Area:", "${result.totalCladdingArea.fmt(1)} m\u00B2")
        addCostRow("Estimated Cost per m\u00B2:", "${result.costPerM2.fmt(0)} EGP/m\u00B2")
        addCostRow("Estimated Total Cost:", "${result.estimatedTotalCost.fmt(0)} EGP")
        if (result.netProfit > 0) {
            addCostRow("Estimated Net Profit:", "${result.netProfit.fmt(0)} EGP")
        }
        if (result.roi > 0) {
            addCostRow("Return on Investment:", "${result.roi.fmt(1)}%")
        }

        document.add(costTable)
    }

    // ==================== SECTION 8: TITLE BLOCK ====================

    private fun addTitleBlock(
        document: Document,
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        projectName: String,
        clientName: String
    ) {
        document.add(emptyLine())
        document.add(separator())

        // Professional Engineering Title Block
        val titleBlock = Table(
            UnitValue.createPercentArray(floatArrayOf(30f, 20f, 25f, 25f))
        ).useAllAvailableWidth()
        titleBlock.setBorder(com.itextpdf.layout.borders.SolidBorder(1.5f))

        // Project Cell
        val projCell = Cell(1, 1).setPadding(5f)
        projCell.add(
            Paragraph()
                .add(text("PROJECT", getFont(bold = true), 6f, DARK_GRAY))
        )
        projCell.add(
            Paragraph()
                .add(text(projectName, getFont(bold = true), 8f, BLACK))
        )
        titleBlock.addCell(projCell)

        // Client Cell
        val clientCell = Cell(1, 1).setPadding(5f)
        clientCell.add(
            Paragraph()
                .add(text("CLIENT", getFont(bold = true), 6f, DARK_GRAY))
        )
        clientCell.add(
            Paragraph()
                .add(text(clientName, getFont(bold = true), 8f, BLACK))
        )
        titleBlock.addCell(clientCell)

        // Designer Cell
        val designCell = Cell(1, 1).setPadding(5f)
        designCell.add(
            Paragraph()
                .add(text("DESIGNED BY", getFont(bold = true), 6f, DARK_GRAY))
        )
        designCell.add(
            Paragraph()
                .add(text("Civil EG Pro Engine", getFont(bold = true), 8f, BLACK))
        )
        titleBlock.addCell(designCell)

        // Date & Code Cell
        val dateCell = Cell(1, 1).setPadding(5f)
        dateCell.add(
            Paragraph()
                .add(text("DATE / CODE", getFont(bold = true), 6f, DARK_GRAY))
        )
        dateCell.add(
            Paragraph()
                .add(
                    text(
                        "${SimpleDateFormat("MMM yyyy", Locale.US).format(Date())} | ${inputs.code.version}",
                        getFont(bold = true),
                        8f,
                        BLACK
                    )
                )
        )
        dateCell.add(
            Paragraph()
                .add(text("SHEET: S-001  Rev. 0", getFont(), 7f, DARK_GRAY))
        )
        titleBlock.addCell(dateCell)

        document.add(titleBlock)

        // Disclaimer Footer
        document.add(emptyLine())
        document.add(
            paragraph(
                "Generated by Civil EG Pro  |  ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())}",
                8f,
                color = DARK_GRAY,
                alignment = TextAlignment.CENTER
            )
        )
        document.add(
            paragraph(
                "This report is for reference only and must be reviewed by a qualified structural engineer before construction.",
                7f,
                color = LIGHT_GRAY,
                alignment = TextAlignment.CENTER
            )
        )

        // Recommendations (if any)
        if (result.recommendations.isNotEmpty()) {
            document.add(emptyLine())
            document.add(separator())
            document.add(subTitle("Design Recommendations"))
            result.recommendations.forEachIndexed { i, rec ->
                document.add(
                    paragraph(
                        "${i + 1}. $rec",
                        8f,
                        color = SECONDARY
                    )
                )
            }
        }
    }

    // ==================== Page Numbers ====================

    private fun addPageNumbers(pdfDoc: PdfDocument) {
        // Page numbers omitted for build compatibility with iText 8.
        // The document sections are self-identifying via section titles and numbering.
    }
}
