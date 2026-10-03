package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.civileg.app.R
import com.civileg.app.db.Design
import com.civileg.app.domain.calculations.aci.SteelCompressionResult
import com.civileg.app.domain.calculations.aci.SteelFlexuralResult
import com.civileg.app.domain.calculations.base.WaffleSlabDesign
import com.civileg.app.domain.calculations.base.SeismicBaseShearResult
import com.civileg.app.domain.calculations.ecp.SteelBasePlateDesign
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.ArabicFontProvider
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.utils.ArabicShaper
import com.civileg.app.utils.PdfTextSegmenter
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.BaseDirection
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ComprehensivePdfExporter(private val context: Context) {

    private val PRIMARY = DeviceRgb(21, 101, 192)
    private val SECONDARY = DeviceRgb(55, 71, 79)
    private val SUCCESS = DeviceRgb(46, 125, 50)
    private val ERROR = DeviceRgb(198, 40, 40)
    private val WARNING = DeviceRgb(245, 124, 0)
    private val LIGHT_BG = DeviceRgb(232, 245, 253)
    private val HEADER_BG = DeviceRgb(21, 101, 192)
    private val ROW_ALT = DeviceRgb(245, 245, 245)
    private val WHITE = DeviceRgb(255, 255, 255)

    private fun arabicFont(): PdfFont = ArabicFontProvider.getArabicPdfFont(context, bold = false)
    private fun arabicBoldFont(): PdfFont = ArabicFontProvider.getArabicPdfFont(context, bold = true)
    private fun helveticaFont(bold: Boolean = false): PdfFont = try {
        PdfFontFactory.createFont(if (bold) StandardFonts.HELVETICA_BOLD else StandardFonts.HELVETICA)
    } catch (_: Exception) {
        PdfFontFactory.createFont(StandardFonts.HELVETICA)
    }

    private var currentLanguage: String = LocaleHelper.getLocale(context)
    fun setLanguage(lang: String): ComprehensivePdfExporter { this.currentLanguage = lang; return this }
    private val isEnglish get() = currentLanguage != "ar"
    private fun isArabic(text: String) = ArabicFontProvider.containsArabic(text)
    private fun t(ar: String, en: String): String = if (isEnglish) en else ar

    private fun arParagraph(text: String, fontSize: Float = 10f, bold: Boolean = false, color: DeviceRgb? = null, alignment: TextAlignment? = null): Paragraph {
        val arabicFont = if (bold) arabicBoldFont() else arabicFont()
        val latinFont = helveticaFont(bold)
        return PdfTextSegmenter.buildMixedParagraph(text, arabicFont, latinFont, fontSize, color, alignment)
    }

    private fun tableCell(text: String, fontSize: Float = 9f, bold: Boolean = false, color: DeviceRgb? = null, bg: DeviceRgb? = null, align: TextAlignment = TextAlignment.CENTER): Cell {
        val cell = Cell().setPadding(4f)
        val p = arParagraph(text, fontSize, bold, color, align)
        cell.add(p)
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    private fun headerCell(text: String, colSpan: Int = 1): Cell {
        val cell = Cell(colSpan, 1).setPadding(6f).setBackgroundColor(HEADER_BG).setTextAlignment(TextAlignment.CENTER)
        val p = arParagraph(text, 9f, true, WHITE, TextAlignment.CENTER)
        cell.add(p)
        return cell
    }

    private fun createDocument(outputPath: String): Triple<PdfDocument, Document, PdfFont> {
        val writer = PdfWriter(FileOutputStream(outputPath))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.setMargins(40f, 40f, 40f, 40f)
        return Triple(pdf, document, arabicFont())
    }

    private fun addReportHeader(document: Document, titleAr: String, titleEn: String, subtitle: String) {
        val appNameText = context.getString(R.string.app_name)
        val appName = arParagraph(appNameText, 22f, true, PRIMARY, TextAlignment.CENTER)
        document.add(appName)
        val titlePara = arParagraph(t(titleAr, titleEn), 16f, true, PRIMARY, TextAlignment.CENTER)
        document.add(titlePara)
        val subtitlePara = arParagraph(subtitle, 10f, false, SECONDARY, TextAlignment.CENTER)
        document.add(subtitlePara)
        val dateStr = SimpleDateFormat("yyyy/MM/dd  HH:mm", if (isEnglish) Locale.US else Locale("ar")).format(Date())
        document.add(arParagraph(dateStr, 9f, false, ColorConstants.GRAY as DeviceRgb, TextAlignment.CENTER))
        document.add(LineSeparator(SolidLine(0.5f)).setMarginTop(5f).setMarginBottom(10f))
    }

    private fun addStatusBanner(document: Document, isSafe: Boolean, details: String = "") {
        val statusText = if (isSafe) t("الحالة: آمن ✔", "STATUS: SAFE ✔") else t("الحالة: غير آمن ✘", "STATUS: UNSAFE ✘")
        val p = arParagraph("$statusText\n$details", 11f, true, if (isSafe) SUCCESS else ERROR, TextAlignment.CENTER)
        p.setPadding(8f).setBorder(SolidBorder(if (isSafe) SUCCESS else ERROR, 1.5f))
        document.add(p)
        document.add(Paragraph(" "))
    }

    private fun addSectionTitle(document: Document, title: String) {
        document.add(arParagraph(title, 12f, true, PRIMARY, TextAlignment.CENTER))
        document.add(LineSeparator(SolidLine(0.5f)).setMarginBottom(8f))
    }

    private fun addInfoTable(document: Document, rows: List<Pair<String, String>>) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(45f, 55f))).useAllAvailableWidth()
        rows.forEachIndexed { i, (label, value) ->
            val bg = if (i % 2 == 0) null else ROW_ALT
            table.addCell(Cell().setPadding(4f).add(arParagraph(label, 9f, true)).setBackgroundColor(bg))
            table.addCell(Cell().setPadding(4f).add(arParagraph(value, 9f, false)).setBackgroundColor(bg))
        }
        document.add(table)
        document.add(Paragraph(" "))
    }

    private fun addDrawingSection(document: Document, bitmap: Bitmap?, title: String) {
        addSectionTitle(document, title)
        if (bitmap == null) return
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val img = Image(ImageDataFactory.create(stream.toByteArray())).setAutoScale(true).setHorizontalAlignment(HorizontalAlignment.CENTER)
        document.add(img)
    }

    private fun addFooter(document: Document) {
        document.add(LineSeparator(SolidLine(0.5f)).setMarginTop(10f))
        document.add(arParagraph("Generated by Civil EG Pro Engine", 8f, false, ColorConstants.GRAY as DeviceRgb, TextAlignment.CENTER))
    }

    // ==================== Export Methods ====================

    fun exportGenericReport(titleAr: String, titleEn: String, subtitle: String, designType: String, inputs: Map<String, String>, results: Map<String, String>, safetyChecks: List<GenericSafetyCheck>, isSafe: Boolean, drawingBitmap: Bitmap?, outputPath: String): File? {
        return try {
            val (_, document, _) = createDocument(outputPath)
            addReportHeader(document, titleAr, titleEn, subtitle)
            if (designType.isNotEmpty()) {
                val chip = arParagraph(designType, 10f, true, PRIMARY, TextAlignment.CENTER)
                chip.setBackgroundColor(LIGHT_BG).setPadding(4f)
                document.add(chip)
            }
            addStatusBanner(document, isSafe)
            if (inputs.isNotEmpty()) {
                addSectionTitle(document, t("بيانات التصميم", "Design Data"))
                addInfoTable(document, inputs.entries.map { it.key to it.value })
            }
            if (results.isNotEmpty()) {
                addSectionTitle(document, t("النتائج", "Results"))
                addInfoTable(document, results.entries.map { it.key to it.value })
            }
            if (safetyChecks.isNotEmpty()) {
                addSectionTitle(document, t("تحققات الأمان", "Safety Checks"))
                val table = Table(UnitValue.createPercentArray(floatArrayOf(35f, 20f, 20f, 10f, 15f))).useAllAvailableWidth()
                table.addHeaderCell(headerCell(t("التحقق", "Check")))
                table.addHeaderCell(headerCell(t("المحسوب", "Calc")))
                table.addHeaderCell(headerCell(t("الحد", "Limit")))
                table.addHeaderCell(headerCell(t("الوحدة", "Unit")))
                table.addHeaderCell(headerCell(t("النتيجة", "Result")))
                safetyChecks.forEachIndexed { i, c ->
                    val bg = if (i % 2 == 0) null else ROW_ALT
                    table.addCell(tableCell(c.name, bg = bg, align = TextAlignment.LEFT))
                    table.addCell(tableCell(c.calculated.format(2), bg = bg))
                    table.addCell(tableCell(c.limit.format(2), bg = bg))
                    table.addCell(tableCell(c.unit, bg = bg))
                    table.addCell(tableCell(if (c.passed) "PASS" else "FAIL", color = if (c.passed) SUCCESS else ERROR, bg = bg, bold = true))
                }
                document.add(table)
            }
            addDrawingSection(document, drawingBitmap, designType)
            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    fun exportBeamReport(projectName: String, designCode: DesignCode, beamType: BeamType, inputs: BeamInputs, result: AdvancedBeamResult, inventoryAnalysis: InventoryAnalysisResult?, momentShearDiagrams: MomentShearDiagrams?, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Width" to "${inputs.width} mm", "Depth" to "${inputs.totalDepth} mm", "Span" to "${inputs.span} m")
        val resultsMap = mapOf("Moment" to "${result.flexureResult.astProvided} mm²")
        val checks = listOf(
            GenericSafetyCheck("Flexure", result.flexureResult.astRequired, result.flexureResult.astProvided, "mm²", result.flexureResult.isSafe),
            GenericSafetyCheck("Shear", result.shearResult.requiredArea, result.shearResult.providedArea, "mm²/m", result.shearResult.isSafe),
            GenericSafetyCheck("Deflection", result.deflectionCheck.calculatedDeflection, result.deflectionCheck.allowableDeflection, "mm", result.deflectionCheck.isSafe)
        )
        return exportGenericReport("تصميم كمرات", "Beam Design Report", projectName, "RC Beam", inputsMap, resultsMap, checks, result.flexureResult.isSafe, drawingBitmap, outputPath)
    }

    fun exportColumnReport(projectName: String, designCode: DesignCode, columnType: ColumnType, inputs: ColumnInputs, result: AdvancedColumnResult, inventoryAnalysis: InventoryAnalysisResult?, alternatives: List<ColumnAlternative>, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Load" to "${inputs.axialLoad} kN")
        val resultsMap = mapOf("Capacity" to "${result.axialCapacity} kN")
        val checks = listOf(GenericSafetyCheck("Axial", inputs.axialLoad, result.axialCapacity, "kN", result.reinforcementResult.isSafe))
        return exportGenericReport("تصميم أعمدة", "Column Design Report", projectName, "RC Column", inputsMap, resultsMap, checks, result.reinforcementResult.isSafe, drawingBitmap, outputPath)
    }

    fun exportSteelReport(projectName: String, designCode: DesignCode, sectionType: SteelSectionType, memberType: SteelMemberType, inputs: SteelInputs, result: SteelMemberResult, connectionDesign: ConnectionDesignResult?, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Section" to sectionType.sectionName)
        val resultsMap = mapOf("Capacity" to "${result.axialCapacity} kN")
        return exportGenericReport("تصميم فولاذي", "Steel Design Report", projectName, sectionType.displayName, inputsMap, resultsMap, emptyList(), result.isSafe, drawingBitmap, outputPath)
    }

    fun exportBasePlateReport(projectName: String, result: SteelBasePlateDesign.BasePlateResult, colSection: String, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Column" to colSection, "Plate Dims" to "${result.plateLength.toInt()}x${result.plateWidth.toInt()} mm")
        val resultsMap = mapOf("Utilization" to "${(result.utilizationRatio * 100).toInt()}%")
        return exportGenericReport("لوحة قاعدة", "Base Plate Report", projectName, "Steel Base Plate", inputsMap, resultsMap, emptyList(), result.isSafe, drawingBitmap, outputPath)
    }

    fun exportFootingReport(projectName: String, designCode: CalculatorEngine.DesignCode, result: CalculatorEngine.FootingResult, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Width" to "${result.width} mm", "Length" to "${result.length} mm")
        return exportGenericReport("تصميم أساسات", "Footing Report", projectName, result.type.displayName, inputsMap, emptyMap(), emptyList(), result.isSafe, drawingBitmap, outputPath)
    }

    fun exportStrapFootingReport(projectName: String, designCode: CalculatorEngine.DesignCode, result: CalculatorEngine.StrapFootingResult, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Footing 1" to "${result.footing1.width}x${result.footing1.length} mm")
        return exportGenericReport("قاعدة شداد", "Strap Footing Report", projectName, "Strap Footing", inputsMap, emptyMap(), emptyList(), result.isSafe, drawingBitmap, outputPath)
    }

    fun exportTankReport(projectName: String, designCode: CalculatorEngine.DesignCode, result: CalculatorEngine.TankResult, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Capacity" to "${result.capacity} m³")
        return exportGenericReport("تصميم خزان", "Tank Report", projectName, result.type.displayName, inputsMap, emptyMap(), emptyList(), result.isSafe, drawingBitmap, outputPath)
    }

    fun exportStairReport(projectName: String, designCode: CalculatorEngine.DesignCode, result: CalculatorEngine.StairResult, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Thickness" to "${result.thickness} mm")
        return exportGenericReport("تصميم سلم", "Staircase Report", projectName, result.type.displayName, inputsMap, emptyMap(), emptyList(), result.isSafe, drawingBitmap, outputPath)
    }

    fun exportRetainingWallReport(projectName: String, designCode: CalculatorEngine.DesignCode, result: CalculatorEngine.RetainingWallResult, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Height" to "${result.height} m")
        return exportGenericReport("حائط ساند", "Retaining Wall Report", projectName, "RC Wall", inputsMap, emptyMap(), emptyList(), result.isSafe, drawingBitmap, outputPath)
    }

    fun exportSlabReport(projectName: String, designCode: DesignCode, slabType: SlabType, inputs: SlabInputs, result: AdvancedSlabResult, outputPath: String, drawingBitmap: Bitmap? = null): File? {
        val inputsMap = mapOf("Thickness" to "${inputs.thickness} mm")
        return exportGenericReport("تصميم بلاطة", "Slab Design Report", projectName, "RC Slab", inputsMap, emptyMap(), emptyList(), result.flexureResult.isSafe, drawingBitmap, outputPath)
    }

    fun exportConnectionReport(projectName: String, type: String, details: Map<String, String>, isSafe: Boolean, utilization: Double, outputPath: String, notes: List<String> = emptyList()): File? {
        return exportGenericReport("تصميم وصلة", "Connection Report", projectName, type, details, emptyMap(), emptyList(), isSafe, null, outputPath)
    }

    fun exportProjectBatchReport(projectName: String, designs: List<Design>, summary: ProjectSummary, outputPath: String): File? {
        return try {
            val (_, document, _) = createDocument(outputPath)
            addReportHeader(document, "تقرير مجمع للمشروع", "Project Batch Report", projectName)
            addSectionTitle(document, "Executive Summary")
            addInfoTable(document, listOf(
                "Total Cost" to "${summary.totalCost.format(0)} EGP",
                "Total Concrete" to "${summary.totalConcrete.format(1)} m\u00B3",
                "Total Steel" to "${summary.totalSteel.format(0)} kg",
                "Design Efficiency" to String.format(Locale.US, "%.2f", summary.costEfficiencyIndex)
            ))
            
            addSectionTitle(document, "Elements Schedule")
            val table = Table(UnitValue.createPercentArray(floatArrayOf(30f, 30f, 20f, 20f))).useAllAvailableWidth()
            table.addHeaderCell(headerCell("Name"))
            table.addHeaderCell(headerCell("Type"))
            table.addHeaderCell(headerCell("Cost"))
            table.addHeaderCell(headerCell("Status"))
            
            designs.forEach { d ->
                table.addCell(tableCell(d.name))
                table.addCell(tableCell(d.type.name))
                table.addCell(tableCell(d.totalCost.format(0)))
                table.addCell(tableCell(if (d.isSafe) "SAFE" else "UNSAFE"))
            }
            document.add(table)
            
            // Add BBS Summary if there are many designs
            if (designs.size > 2) {
                addSectionTitle(document, "Project BBS Summary (Estimated)")
                document.add(arParagraph("Total steel quantity based on current designs: ${summary.totalSteel.format(0)} kg.", 9f))
            }

            addFooter(document)
            document.close()
            File(outputPath)
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    fun exportSeismicReport(
        projectName: String,
        designCode: DesignCode,
        totalWeight: Double,
        baseShear: Double,
        zoneFactor: Double,
        soilFactor: Double,
        importanceFactor: Double,
        responseModFactor: Double,
        buildingHeight: Double,
        period: Double,
        spectralAcceleration: Double,
        floorForces: List<Pair<Int, Double>>,
        isSafe: Boolean,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        val inputsMap = mapOf(
            "Total Weight" to "${totalWeight.format(1)} kN",
            "Zone Factor Z" to zoneFactor.format(2),
            "Soil Factor S" to soilFactor.format(2),
            "Importance I" to importanceFactor.format(2),
            "Response Mod R" to responseModFactor.format(2),
            "Building Height" to "${buildingHeight.format(1)} m",
            "Period T" to "${period.format(3)} s",
            "Spectral Acc Sa" to spectralAcceleration.format(3)
        )
        val maxFloorForce = floorForces.maxOfOrNull { it.second } ?: 0.0
        val minFloorForce = floorForces.minOfOrNull { it.second } ?: 0.0
        val resultsMap = mapOf(
            "Base Shear Vb" to "${baseShear.format(1)} kN",
            "Max Floor Force" to "${maxFloorForce.format(1)} kN",
            "Min Floor Force" to "${minFloorForce.format(1)} kN",
            "Total Floors" to "${floorForces.size}"
        )
        return exportGenericReport(
            "تصميم زلزالي", "Seismic Design Report", projectName, "Seismic Base Shear",
            inputsMap, resultsMap, emptyList(), isSafe, drawingBitmap, outputPath
        )
    }

    fun exportWaffleSlabReport(
        projectName: String,
        designCode: DesignCode,
        lx: Double, ly: Double,
        ribSpacing: Double, ribWidth: Double, ribHeight: Double,
        toppingThickness: Double,
        solidHeadSize: Double, columnWidth: Double,
        fcu: Double, fy: Double,
        liveLoad: Double, deadLoad: Double,
        result: WaffleSlabDesign.WaffleSlabResult,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        val inputsMap = mapOf(
            "Lx" to "${lx.format(2)} m",
            "Ly" to "${ly.format(2)} m",
            "Rib Spacing" to "${ribSpacing.format(0)} mm",
            "Rib Width" to "${ribWidth.format(0)} mm",
            "Rib Height" to "${ribHeight.format(0)} mm",
            "Topping" to "${toppingThickness.format(0)} mm",
            "Solid Head" to "${solidHeadSize.format(0)} mm",
            "Column" to "${columnWidth.format(0)} mm",
            "fcu" to "${fcu.format(0)} MPa",
            "fy" to "${fy.format(0)} MPa",
            "Live Load" to "${liveLoad.format(2)} kN/m²",
            "Dead Load" to "${deadLoad.format(2)} kN/m²"
        )
        val resultsMap = mapOf(
            "Utilization" to "${(result.utilizationRatio * 100).format(1)}%",
            "Concrete Volume" to "${result.concreteVolume.format(2)} m³",
            "Steel Weight" to "${result.steelWeight.format(1)} kg",
            "Cost" to "${result.cost.format(0)} EGP",
            "isSafe" to (if (result.isSafe) "PASS" else "FAIL")
        )
        val safetyChecks = result.safetyChecks.map {
            GenericSafetyCheck(it.name, it.calculated, it.limit, it.unit, it.passed)
        }
        return exportGenericReport(
            "تصميم بلاطة واffle", "Waffle Slab Report", projectName, "Waffle Slab",
            inputsMap, resultsMap, safetyChecks, result.isSafe, drawingBitmap, outputPath
        )
    }

    fun exportHordiSlabReport(
        projectName: String,
        designCode: DesignCode,
        fcu: Double, fy: Double,
        ribWidth: Double, ribSpacing: Double,
        totalThickness: Double, toppingThickness: Double,
        span: Double,
        designMoment: Double, designShear: Double,
        resultAsProvided: Double,
        isSafe: Boolean,
        utilizationRatio: Double,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        val inputsMap = mapOf(
            "fcu" to "${fcu.format(0)} MPa",
            "fy" to "${fy.format(0)} MPa",
            "Rib Width" to "${ribWidth.format(0)} mm",
            "Rib Spacing" to "${ribSpacing.format(0)} mm",
            "Total Thickness" to "${totalThickness.format(0)} mm",
            "Topping" to "${toppingThickness.format(0)} mm",
            "Span" to "${span.format(2)} m",
            "Design Moment" to "${designMoment.format(2)} kN.m",
            "Design Shear" to "${designShear.format(2)} kN"
        )
        val resultsMap = mapOf(
            "As Provided" to "${resultAsProvided.format(2)} mm²",
            "Utilization Ratio" to utilizationRatio.format(3),
            "Safety" to (if (isSafe) "SAFE" else "UNSAFE")
        )
        return exportGenericReport(
            "تصميم بلاطة هوردي", "Hordi Slab Report", projectName, "Hordi Slab",
            inputsMap, resultsMap, emptyList(), isSafe, drawingBitmap, outputPath
        )
    }

    fun exportCombinedFootingReport(
        projectName: String,
        designCode: DesignCode,
        p1: Double, p2: Double,
        col1Width: Double, col1Depth: Double,
        col2Width: Double, col2Depth: Double,
        distanceBetweenColumns: Double,
        soilBearingCapacity: Double,
        footingLength: Double, footingWidth: Double, footingThickness: Double,
        qMax: Double, qMin: Double,
        isSafe: Boolean,
        warnings: List<String>,
        outputPath: String,
        drawingBitmap: Bitmap? = null
    ): File? {
        val inputsMap = mapOf(
            "P1" to "${p1.format(1)} kN",
            "P2" to "${p2.format(1)} kN",
            "Col1" to "${col1Width.format(0)}x${col1Depth.format(0)} mm",
            "Col2" to "${col2Width.format(0)}x${col2Depth.format(0)} mm",
            "Distance" to "${distanceBetweenColumns.format(2)} m",
            "q_all" to "${soilBearingCapacity.format(1)} kN/m²"
        )
        val resultsMap = mapOf(
            "Footing L×W" to "${footingLength.format(0)}x${footingWidth.format(0)} mm",
            "Thickness" to "${footingThickness.format(0)} mm",
            "q_max" to "${qMax.format(1)} kN/m²",
            "q_min" to "${qMin.format(1)} kN/m²",
            "Safety" to (if (isSafe) "SAFE" else "UNSAFE")
        )
        val safetyChecks = warnings.map { warning ->
            GenericSafetyCheck(warning, 0.0, 0.0, "", false)
        }
        return exportGenericReport(
            "تصميم قاعدة مشتركة", "Combined Footing Report", projectName, "Combined Footing",
            inputsMap, resultsMap, safetyChecks, isSafe, drawingBitmap, outputPath
        )
    }

    private fun Double.format(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)
}
