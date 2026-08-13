package com.civileg.app.utils.exporters

import android.content.Context
import com.civileg.app.domain.entities.*
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import android.graphics.Bitmap
import com.itextpdf.io.image.ImageDataFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * English-Only PDF Exporter for Steel Warehouse Projects.
 * All text is in English using Helvetica font.
 */
class SteelWarehouseProPdfExporter(private val context: Context) {

    private val PRIMARY = DeviceRgb(21, 101, 192)
    private val SECONDARY = DeviceRgb(55, 71, 79)
    private val SUCCESS = DeviceRgb(46, 125, 50)
    private val ERROR = DeviceRgb(198, 40, 40)
    private val WARNING = DeviceRgb(245, 124, 0)
    private val HEADER_BG = DeviceRgb(33, 37, 41)
    private val LIGHT_BLUE = DeviceRgb(227, 242, 253)
    private val ROW_ALT = DeviceRgb(248, 249, 250)
    private val WHITE = DeviceRgb(255, 255, 255)

    private fun helvetica(bold: Boolean = false): PdfFont =
        com.itextpdf.kernel.font.PdfFontFactory.createFont(
            if (bold) com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD
            else com.itextpdf.io.font.constants.StandardFonts.HELVETICA
        )

    private fun Double.fmt(decimals: Int = 2): String = String.format(Locale.US, "%.${decimals}f", this)

    fun exportToDownload(
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        clientAr: String,
        clientEn: String,
        projAr: String,
        projEn: String
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Warehouse_Design_${timestamp}.pdf"
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outputDir, fileName)

        val writer = PdfWriter(FileOutputStream(file))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(30f, 30f, 30f, 30f)

        addCoverPage(document, inputs, result, clientEn, projEn)
        document.add(AreaBreak())

        addGeneralNotes(document, inputs)
        addProjectSummary(document, inputs, result)
        document.add(AreaBreak())

        addMemberSchedule(document, inputs, result)
        document.add(AreaBreak())

        addSectionDrawings(document, inputs, result)
        document.add(AreaBreak())

        addConnectionSchedule(document, result)
        addRecommendations(document, result)
        document.add(AreaBreak())

        addMaterialTakeoff(document, result)
        addTitleBlock(document, clientEn, projEn, inputs)

        document.close()
        return file
    }

    // ==================== COVER PAGE ====================
    private fun addCoverPage(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult,
                             clientEn: String, projEn: String) {
        val banner = Table(UnitValue.createPercentArray(floatArrayOf(100f))).useAllAvailableWidth()
        val bannerCell = Cell().setPadding(15f).setBackgroundColor(PRIMARY).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        bannerCell.add(Paragraph("STRUCTURAL DESIGN & ANALYSIS REPORT")
            .setFontSize(18f).setBold().setFontColor(WHITE).setTextAlignment(TextAlignment.CENTER)
            .setFont(helvetica(true)))
        bannerCell.add(Paragraph("Steel Warehouse Project")
            .setFontSize(14f).setFontColor(DeviceRgb(200, 220, 255)).setTextAlignment(TextAlignment.CENTER)
            .setFont(helvetica(false)))
        banner.addCell(bannerCell)
        document.add(banner)

        document.add(Paragraph(" "))

        // Project Info Table
        val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f))).useAllAvailableWidth()

        fun addInfoRow(label: String, value: String, rowIdx: Int) {
            val bg = if (rowIdx % 2 == 0) LIGHT_BLUE else null
            val labelCell = Cell().setPadding(6f)
            labelCell.add(Paragraph(label).setFontSize(9f).setBold().setFont(helvetica(true)))
            labelCell.setTextAlignment(TextAlignment.LEFT)
            bg?.let { labelCell.setBackgroundColor(it) }
            infoTable.addCell(labelCell)

            val valueCell = Cell().setPadding(6f)
            valueCell.add(Paragraph(value).setFontSize(9f).setFont(helvetica(false)))
            bg?.let { valueCell.setBackgroundColor(it) }
            infoTable.addCell(valueCell)
        }

        var row = 0
        addInfoRow("Project", projEn, row++); row++
        addInfoRow("Client", clientEn, row++); row++
        addInfoRow("Design Code", inputs.code.version, row++); row++
        addInfoRow("Span", "${inputs.span.fmt()} m", row++); row++
        addInfoRow("Length", "${inputs.length.fmt()} m", row++); row++
        addInfoRow("Eave Height", "${inputs.eaveHeight.fmt()} m", row++); row++
        addInfoRow("Ridge Height", "${inputs.ridgeHeight.fmt()} m", row++); row++
        addInfoRow("Bay Spacing", "${inputs.baySpacing.fmt()} m", row++); row++
        addInfoRow("Roof Slope", "${(inputs.slope * 100).fmt(1)}%", row++); row++
        addInfoRow("Design Date", SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date()), row)

        document.add(infoTable)
        document.add(Paragraph(" "))

        // Status Banner
        val statusText = if (result.safetyStatus) {
            "STRUCTURAL ANALYSIS PASSED - Design is safe and code-compliant"
        } else {
            "REVIEW REQUIRED - Structural review needed"
        }
        val statusColor = if (result.safetyStatus) SUCCESS else ERROR
        val statusP = Paragraph(statusText)
            .setFontSize(11f).setBold().setFontColor(statusColor)
            .setTextAlignment(TextAlignment.CENTER)
            .setFont(helvetica(true))
            .setPadding(8f)
        document.add(statusP)
    }

    // ==================== GENERAL NOTES ====================
    private fun addGeneralNotes(document: Document, inputs: SteelWarehouseInputs) {
        document.add(Paragraph("General Notes").setFontSize(12f).setBold().setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(true)))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val notes = listOf(
            "1. All dimensions are in METERS unless otherwise noted.",
            "2. Steel members designed per ${inputs.code.version} code.",
            "3. Welding per AWS D1.1 specifications (6mm minimum fillet weld).",
            "4. High strength bolts ASTM A325 or equivalent.",
            "5. Roof slope 1% - 10% for water drainage as per design.",
            "6. Material grade: Main frame S355/St-52, Secondary S235/St-37.",
            "7. All connections shall be designed for the full design forces.",
            "8. Anti-corrosion protection as per project specifications.",
            "9. Fabrication and erection tolerances per AISC Code of Practice.",
            "10. Design based on assumed loading; verify site conditions."
        )

        val notesTable = Table(UnitValue.createPercentArray(floatArrayOf(5f, 95f))).useAllAvailableWidth()
        notes.forEachIndexed { i, note ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            notesTable.addCell(dataCell("${i + 1}", bg = bg))
            val noteCell = Cell().setPadding(3f)
            noteCell.add(Paragraph(note).setFontSize(7f).setFont(helvetica(false)))
            bg?.let { noteCell.setBackgroundColor(it) }
            notesTable.addCell(noteCell)
        }
        document.add(notesTable)
        document.add(Paragraph(" "))
    }

    // ==================== PROJECT SUMMARY ====================
    private fun addProjectSummary(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        document.add(Paragraph("Project & Material Summary").setFontSize(12f).setBold().setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(true)))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val summary = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()

        fun addSummaryCell(label: String, value: String, rowIdx: Int) {
            val bg = if (rowIdx % 2 == 0) LIGHT_BLUE else null
            val lc = Cell().setPadding(5f)
            lc.add(Paragraph(label).setFontSize(8f).setBold().setFont(helvetica(true)))
            bg?.let { lc.setBackgroundColor(it) }
            summary.addCell(lc)

            val vc = Cell().setPadding(5f)
            vc.add(Paragraph(value).setFontSize(9f).setFont(helvetica(false)))
            bg?.let { vc.setBackgroundColor(it) }
            summary.addCell(vc)
        }

        var row = 0
        addSummaryCell("Total Steel Weight", "${result.totalWeight.fmt(1)} Tons", row++); row++
        addSummaryCell("Weight per m2", "${result.weightPerM2.fmt(1)} kg/m2", row++); row++
        addSummaryCell("Cost per m2", "${result.costPerM2.fmt(0)} EGP/m2", row++); row++
        addSummaryCell("Total Estimated Cost", "${result.estimatedTotalCost.fmt(0)} EGP", row++); row++
        addSummaryCell("Net Profit", "${result.netProfit.fmt(0)} EGP", row++); row++
        addSummaryCell("Return on Investment (ROI)", "${result.roi.fmt(1)}%", row++); row++
        addSummaryCell("Cladding Area", "${result.totalCladdingArea.fmt(1)} m2", row)

        document.add(summary)
        document.add(Paragraph(" "))
    }

    // ==================== MEMBER SCHEDULE ====================
    private fun addMemberSchedule(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        document.add(Paragraph("Steel Member Schedule").setFontSize(12f).setBold().setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(true)))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(8f, 15f, 25f, 18f, 12f, 12f, 10f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell("MARK"))
        table.addHeaderCell(headerCell("MEMBER"))
        table.addHeaderCell(headerCell("SECTION"))
        table.addHeaderCell(headerCell("MATERIAL"))
        table.addHeaderCell(headerCell("QTY"))
        table.addHeaderCell(headerCell("LENGTH"))
        table.addHeaderCell(headerCell("STATUS"))

        val numBays = (inputs.length / inputs.baySpacing).toInt().coerceAtLeast(1)
        val members = listOf(
            Triple("C1", "Columns", result.mainFrame.columnSection),
            Triple("R1", "Rafters", result.mainFrame.rafterSection),
            Triple("P1", "Purlins", result.secondaryMembers.purlinSection),
            Triple("G1", "Girts", result.secondaryMembers.girtSection),
            Triple("B1", "Bracing", result.secondaryMembers.bracingSection)
        )

        members.forEachIndexed { i, (mark, member, section) ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            table.addCell(dataCell(mark, bold = true, bg = bg))
            table.addCell(dataCell(member, bg = bg))
            table.addCell(dataCell(section.displayName, bold = true, bg = bg))
            table.addCell(dataCell("ASTM A572 Gr.50", bg = bg))

            val qty = when (mark) {
                "C1" -> (numBays + 1) * 2
                "R1" -> numBays * 2
                "P1" -> result.secondaryMembers.purlinCount
                "G1" -> result.secondaryMembers.purlinCount
                else -> numBays * 2
            }
            table.addCell(dataCell("$qty", bg = bg))

            val len = when (mark) {
                "C1" -> "${inputs.eaveHeight.fmt(1)} m"
                "R1" -> "${kotlin.math.sqrt((inputs.span / 2.0).let { it * it } + (inputs.ridgeHeight - inputs.eaveHeight).let { it * it }).fmt(2)} m"
                "P1" -> "${inputs.baySpacing.fmt(1)} m"
                "G1" -> "${inputs.baySpacing.fmt(1)} m"
                else -> "-"
            }
            table.addCell(dataCell(len, bg = bg))
            table.addCell(dataCell("OK", color = SUCCESS, bg = bg))
        }

        document.add(table)

        // Design Forces Summary
        document.add(Paragraph(" "))
        document.add(Paragraph("Design Forces Summary").setFontSize(10f).setBold().setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(true)))

        val forces = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f))).useAllAvailableWidth()
        forces.addHeaderCell(headerCell("AXIAL (kN)"))
        forces.addHeaderCell(headerCell("MOMENT (kN.m)"))
        forces.addHeaderCell(headerCell("SHEAR (kN)"))
        forces.addHeaderCell(headerCell("STATUS"))
        forces.addCell(dataCell("${result.mainFrame.maxAxial.fmt(1)}", bold = true))
        forces.addCell(dataCell("${result.mainFrame.maxMoment.fmt(1)}", bold = true))
        forces.addCell(dataCell("${result.mainFrame.maxShear.fmt(1)}", bold = true))
        forces.addCell(dataCell(if (result.safetyStatus) "PASS" else "REVIEW", bold = true, color = if (result.safetyStatus) SUCCESS else ERROR))
        document.add(forces)
        document.add(Paragraph(" "))
    }

    // ==================== SECTION DRAWINGS ====================
    private fun addSectionDrawings(document: Document, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        document.add(Paragraph("Steel Cross-Section Drawings").setFontSize(12f).setBold().setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(true)))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val sections = listOf(
            Triple("C1", "Column Section", result.mainFrame.columnSection),
            Triple("R1", "Rafter Section", result.mainFrame.rafterSection),
            Triple("P1", "Purlin Section", result.secondaryMembers.purlinSection),
            Triple("G1", "Girt Section", result.secondaryMembers.girtSection),
            Triple("B1", "Bracing Section", result.secondaryMembers.bracingSection)
        )

        val gen = com.civileg.app.utils.PdfDrawingGenerator
        var addedAny = false

        sections.forEachIndexed { index, (mark, description, section) ->
            try {
                val bitmap = generateWarehouseSectionBitmap(gen, section)
                if (bitmap != null) {
                    if (index > 0) document.add(AreaBreak())
                    addedAny = true

                    document.add(Paragraph("$mark - $description: ${section.sectionName}")
                        .setFontSize(10f).setBold().setFontColor(SECONDARY)
                        .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(true)))
                    document.add(Paragraph(" "))

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
                    val imageData = ImageDataFactory.create(stream.toByteArray())
                    val img = com.itextpdf.layout.element.Image(imageData)
                    img.setMaxWidth(450f)
                    img.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                    document.add(img)
                    document.add(Paragraph(" "))

                    // Properties mini-table under each drawing
                    val propsTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
                    addPropRow(propsTable, "Area", "${"%.1f".format(section.area / 100.0)} cm2", 0)
                    addPropRow(propsTable, "Ix", "${"%.0f".format(section.ix / 1e4)} cm4", 1)
                    addPropRow(propsTable, "Weight", "${"%.1f".format(section.weight)} kg/m", 2)
                    document.add(propsTable)
                    document.add(Paragraph(" "))
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!addedAny) {
            document.add(Paragraph("[Section drawings generation skipped - no valid sections]")
                .setFontSize(9f).setFontColor(WARNING).setTextAlignment(TextAlignment.CENTER).setFont(helvetica(false)))
        }
    }

    private fun addPropRow(table: Table, label: String, value: String, idx: Int) {
        val bg = if (idx % 2 == 0) LIGHT_BLUE else null
        val lc = Cell().setPadding(3f)
        lc.add(Paragraph(label).setFontSize(8f).setBold().setFont(helvetica(true)))
        bg?.let { lc.setBackgroundColor(it) }
        table.addCell(lc)
        val vc = Cell().setPadding(3f)
        vc.add(Paragraph(value).setFontSize(8f).setFont(helvetica(false)))
        bg?.let { vc.setBackgroundColor(it) }
        table.addCell(vc)
    }

    private fun generateWarehouseSectionBitmap(
        gen: com.civileg.app.utils.PdfDrawingGenerator,
        section: SteelSectionType
    ): Bitmap? {
        return when (section) {
            is SteelSectionType.ISection -> gen.generateAccurateSteelSection(
                sectionName = section.sectionName, sectionTypeName = section.displayName,
                h = section.h, bf = section.bf, tw = section.tw, tf = section.tf,
                gradeName = section.grade.displayName, fy = section.grade.fy, fu = section.grade.fu,
                area = section.getArea(), ix = section.ix, sx = section.sx, zx = section.zx,
                weight = section.weight, rootR = section.rootRadius
            )
            is SteelSectionType.CSection -> gen.generateAccurateSteelSection(
                sectionName = section.sectionName, sectionTypeName = section.displayName,
                h = section.h, bf = section.bf, tw = section.tw, tf = section.tf,
                gradeName = section.grade.displayName, fy = section.grade.fy, fu = section.grade.fu,
                area = section.getArea(), ix = section.ix, sx = section.sx, zx = section.zx,
                weight = section.weight, rootR = section.rootRadius
            )
            is SteelSectionType.CHS -> gen.generateAccurateSteelSection(
                sectionName = section.sectionName, sectionTypeName = section.displayName,
                h = section.outerDiameter, bf = section.outerDiameter, tw = section.thickness, tf = section.thickness,
                gradeName = section.grade.displayName, fy = section.grade.fy, fu = section.grade.fu,
                area = section.getArea(), ix = section.ix, sx = section.sx, zx = section.zx,
                weight = section.weight, outerDia = section.outerDiameter
            )
            is SteelSectionType.RHS -> gen.generateAccurateSteelSection(
                sectionName = section.sectionName, sectionTypeName = section.displayName,
                h = section.height, bf = section.width, tw = section.thickness, tf = section.thickness,
                gradeName = section.grade.displayName, fy = section.grade.fy, fu = section.grade.fu,
                area = section.getArea(), ix = section.ix, sx = section.sx, zx = section.zx,
                weight = section.weight, rhsW = section.width, rhsH = section.height, rhsT = section.thickness
            )
            is SteelSectionType.LSection -> gen.generateAccurateSteelSection(
                sectionName = section.sectionName, sectionTypeName = section.displayName,
                h = section.legA, bf = section.legB, tw = section.thickness, tf = section.thickness,
                gradeName = section.grade.displayName, fy = section.grade.fy, fu = section.grade.fu,
                area = section.getArea(), ix = section.ix, sx = section.sx, zx = section.zx,
                weight = section.weight, legA = section.legA, legB = section.legB, angleThk = section.thickness
            )
            is SteelSectionType.TSection -> gen.generateAccurateSteelSection(
                sectionName = section.sectionName, sectionTypeName = section.displayName,
                h = section.webDepth + section.flangeThickness, bf = section.flangeWidth,
                tw = section.webThickness, tf = section.flangeThickness,
                gradeName = section.grade.displayName, fy = section.grade.fy, fu = section.grade.fu,
                area = section.getArea(), ix = section.ix, sx = section.sx, zx = section.zx,
                weight = section.weight
            )
            is SteelSectionType.PlateGirder -> gen.generateAccurateSteelSection(
                sectionName = section.sectionName, sectionTypeName = section.displayName,
                h = section.h, bf = maxOf(section.bfTop, section.bfBot),
                tw = section.tw, tf = maxOf(section.tfTop, section.tfBot),
                gradeName = section.grade.displayName, fy = section.grade.fy, fu = section.grade.fu,
                area = section.getArea(), ix = section.ix, sx = section.sx, zx = section.zx,
                weight = section.weight,
                bfTop = section.bfTop, bfBot = section.bfBot, tfTop = section.tfTop, tfBot = section.tfBot
            )
            is SteelSectionType.Pipe -> gen.generateAccurateSteelSection(
                sectionName = section.sectionName, sectionTypeName = section.displayName,
                h = section.outerDiameter, bf = section.outerDiameter,
                tw = section.wallThickness, tf = section.wallThickness,
                gradeName = section.grade.displayName, fy = section.grade.fy, fu = section.grade.fu,
                area = section.getArea(), ix = section.ix, sx = section.sx, zx = section.zx,
                weight = section.weight, outerDia = section.outerDiameter
            )
            is SteelSectionType.BuiltUp -> gen.generateAccurateSteelSection(
                sectionName = section.sectionName, sectionTypeName = section.displayName,
                h = section.depth, bf = section.width, tw = 0.0, tf = 0.0,
                gradeName = "Built-up", fy = 355.0, fu = 510.0,
                area = section.getArea(), ix = section.ix, sx = section.sx, zx = section.zx,
                weight = section.weight
            )
        }
    }

    // ==================== CONNECTIONS ====================
    private fun addConnectionSchedule(document: Document, result: SteelWarehouseAnalysisResult) {
        if (result.connections.isEmpty()) return

        document.add(Paragraph("Connection Schedule").setFontSize(12f).setBold().setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(true)))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(20f, 20f, 20f, 20f, 20f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell("CONNECTION"))
        table.addHeaderCell(headerCell("TYPE"))
        table.addHeaderCell(headerCell("CAPACITY (kN)"))
        table.addHeaderCell(headerCell("DEMAND (kN)"))
        table.addHeaderCell(headerCell("STATUS"))

        result.connections.forEachIndexed { i, conn ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            table.addCell(dataCell(conn.name, bg = bg))
            table.addCell(dataCell(conn.type::class.simpleName ?: "N/A", bg = bg))
            table.addCell(dataCell(conn.capacity.fmt(1), bg = bg))
            table.addCell(dataCell(conn.demand.fmt(1), bg = bg))
            table.addCell(dataCell(
                if (conn.isSafe) "PASS" else "FAIL",
                color = if (conn.isSafe) SUCCESS else ERROR, bg = bg
            ))
        }
        document.add(table)
        document.add(Paragraph(" "))
    }

    // ==================== RECOMMENDATIONS ====================
    private fun addRecommendations(document: Document, result: SteelWarehouseAnalysisResult) {
        if (result.recommendations.isEmpty()) return

        document.add(Paragraph("Design Recommendations").setFontSize(12f).setBold().setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(true)))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        result.recommendations.forEachIndexed { i, rec ->
            document.add(Paragraph("${i + 1}. $rec").setFontSize(8f).setFont(helvetica(false)))
        }
        document.add(Paragraph(" "))
    }

    // ==================== MATERIAL TAKEOFF ====================
    private fun addMaterialTakeoff(document: Document, result: SteelWarehouseAnalysisResult) {
        document.add(Paragraph("Bill of Quantities").setFontSize(12f).setBold().setFontColor(PRIMARY)
            .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(true)))
        document.add(LineSeparator(SolidLine(1f)).setMarginBottom(5f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(5f, 40f, 25f, 30f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell("#"))
        table.addHeaderCell(headerCell("ITEM"))
        table.addHeaderCell(headerCell("QUANTITY"))
        table.addHeaderCell(headerCell("NOTES"))

        var idx = 1
        result.materialTakeoff.forEach { (key, value) ->
            val bg = if (idx % 2 == 0) ROW_ALT else null
            table.addCell(dataCell("$idx", bg = bg))
            table.addCell(dataCell(key, bg = bg))
            table.addCell(dataCell(value.fmt(2), bg = bg))
            table.addCell(dataCell("-", bg = bg))
            idx++
        }

        val bg = if (idx % 2 == 0) ROW_ALT else null
        table.addCell(dataCell("", bg = bg))
        table.addCell(dataCell("TOTAL COST", bold = true, bg = LIGHT_BLUE))
        table.addCell(dataCell("${result.estimatedTotalCost.fmt(0)} EGP", bold = true, bg = LIGHT_BLUE))
        table.addCell(dataCell("Incl. Tax", bg = LIGHT_BLUE))

        document.add(table)
        document.add(Paragraph(" "))
    }

    // ==================== TITLE BLOCK ====================
    private fun addTitleBlock(document: Document, clientEn: String, projEn: String, inputs: SteelWarehouseInputs) {
        document.add(Paragraph(" "))

        val titleBlock = Table(UnitValue.createPercentArray(floatArrayOf(30f, 20f, 25f, 25f))).useAllAvailableWidth()
        titleBlock.setBorder(com.itextpdf.layout.borders.SolidBorder(2f))

        // Project cell
        val projCell = Cell(1, 1).setPadding(5f)
        projCell.add(Paragraph("PROJECT").setFontSize(6f).setBold().setFontColor(ColorConstants.GRAY).setFont(helvetica(true)))
        projCell.add(Paragraph(projEn).setFontSize(8f).setBold().setFont(helvetica(true)))
        titleBlock.addCell(projCell)

        // Client cell
        val clientCell = Cell(1, 1).setPadding(5f)
        clientCell.add(Paragraph("CLIENT").setFontSize(6f).setBold().setFontColor(ColorConstants.GRAY).setFont(helvetica(true)))
        clientCell.add(Paragraph(clientEn).setFontSize(8f).setBold().setFont(helvetica(true)))
        titleBlock.addCell(clientCell)

        // Designer cell
        val designCell = Cell(1, 1).setPadding(5f)
        designCell.add(Paragraph("DESIGNED BY").setFontSize(6f).setBold().setFontColor(ColorConstants.GRAY).setFont(helvetica(true)))
        designCell.add(Paragraph("Civil EG Pro Engine").setFontSize(8f).setBold().setFont(helvetica(true)))
        titleBlock.addCell(designCell)

        // Date & Code cell
        val dateCell = Cell(1, 1).setPadding(5f)
        dateCell.add(Paragraph("DATE / CODE").setFontSize(6f).setBold().setFontColor(ColorConstants.GRAY).setFont(helvetica(true)))
        dateCell.add(Paragraph("${SimpleDateFormat("MMM yyyy", Locale.US).format(Date())} | ${inputs.code.version}").setFontSize(8f).setBold().setFont(helvetica(true)))
        dateCell.add(Paragraph("SHEET: S-01 Rev.0").setFontSize(7f).setFont(helvetica(false)))
        titleBlock.addCell(dateCell)

        document.add(titleBlock)

        // Footer disclaimer
        document.add(Paragraph(" "))
        document.add(Paragraph("Generated by Civil EG Pro | This report is for reference only - must be reviewed by a qualified engineer.")
            .setFontSize(7f).setFontColor(DeviceRgb(211, 211, 211))
            .setTextAlignment(TextAlignment.CENTER).setFont(helvetica(false)))
    }

    // ==================== HELPERS ====================
    private fun headerCell(text: String, colSpan: Int = 1): Cell {
        val cell = Cell(colSpan, 1).setPadding(5f).setBackgroundColor(HEADER_BG).setTextAlignment(TextAlignment.CENTER)
        cell.add(Paragraph(text).setFontSize(8f).setBold().setFontColor(WHITE).setFont(helvetica(true)))
        return cell
    }

    private fun dataCell(text: String, fontSize: Float = 8f, bold: Boolean = false, bg: DeviceRgb? = null, color: DeviceRgb? = null): Cell {
        val cell = Cell().setPadding(3f).setTextAlignment(TextAlignment.CENTER)
        val p = Paragraph(text).setFontSize(fontSize).setFont(helvetica(bold))
        if (bold) p.setBold()
        color?.let { p.setFontColor(it) }
        cell.add(p)
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }
}