package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.SettingsManager
import com.civileg.app.utils.SteelDictionary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * Ù…ÙØµØ¯Ù‘Ø± PDF Ø§Ø­ØªØ±Ø§ÙÙŠ Ø´Ø§Ù…Ù„ Ù„Ù„Ù†Ø¸Ø§Ù… Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠ
 * ÙŠØ¯Ø¹Ù…: Ø§Ù„Ø£Ø¹Ù…Ø¯Ø©ØŒ Ø§Ù„ÙƒÙ…Ø±Ø§ØªØŒ Ø§Ù„Ø¨Ù„Ø§Ø·Ø§ØªØŒ ÙˆØ§Ù„Ù…Ù†Ø´Ø¢Øª Ø§Ù„Ù…Ø¹Ø¯Ù†ÙŠØ©
 */
class ComprehensivePdfExporter(private val context: Context) {
    
    companion object {
        private const val PAGE_WIDTH = 595  // A4 width in points
        private const val PAGE_HEIGHT = 842 // A4 height in points
        private const val MARGIN = 40
    }
    
    private val settingsManager = SettingsManager(context)
    private var reportLanguage = settingsManager.language

    // colors...
    private val primaryColor = Color.parseColor("#1976D2")
    private val successColor = Color.parseColor("#4CAF50")
    private val errorColor = Color.parseColor("#F44336")
    private val textColor = Color.parseColor("#212121")
    private val grayColor = Color.parseColor("#757575")
    private val lightGrayColor = Color.parseColor("#E0E0E0")
    
    private var currentY = MARGIN.toFloat()
    private var currentPage: PdfDocument.Page? = null
    private var currentPageNum = 1
    private var currentDocument: PdfDocument? = null
    
    // Define helper to check language
    private fun isAr() = reportLanguage == "ar"

    // Update strings based on language
    private fun getString(ar: String, en: String) = if (isAr()) ar else en

    // ==================== Public Export Methods ====================
    
    fun setLanguage(lang: String) {
        this.reportLanguage = lang
        // Update paints for RTL if Arabic
        if (lang == "ar") {
            headerPaint.textAlign = Paint.Align.RIGHT
            titlePaint.textAlign = Paint.Align.RIGHT
            subtitlePaint.textAlign = Paint.Align.RIGHT
        } else {
            headerPaint.textAlign = Paint.Align.LEFT
            titlePaint.textAlign = Paint.Align.LEFT
            subtitlePaint.textAlign = Paint.Align.LEFT
        }
    }
    
    // ØªØ¹Ø±ÙŠÙ Ø£Ø¯ÙˆØ§Øª Ø§Ù„Ø±Ø³Ù… (Paints)
    private val titlePaint = Paint().apply {
        color = Color.parseColor("#1565C0") // Material Blue 800
        textSize = 22f
        isFakeBoldText = true
        isAntiAlias = true
    }
    
    private val headerPaint = Paint().apply {
        color = Color.BLACK
        textSize = 10f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }
    
    private val subtitlePaint = Paint().apply {
        color = textColor
        textSize = 14f
        isFakeBoldText = true
        isAntiAlias = true
    }
    
    private val bodyPaint = Paint().apply {
        color = textColor
        textSize = 11f
        isAntiAlias = true
    }
    
    private val smallPaint = Paint().apply {
        color = grayColor
        textSize = 9f
        isAntiAlias = true
    }

    // Line & Drawing Paints
    private val paintLineHeavy = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
    private val paintLineNormal = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val paintLineThin = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
    private val paintSteelMember = Paint().apply { color = primaryColor; style = Paint.Style.STROKE; strokeWidth = 2.5f }
    private val colorDraft = Color.parseColor("#455A64")

    // ==================== Ø§Ù„Ø¯ÙˆØ§Ù„ Ø§Ù„Ø¹Ø§Ù…Ø© Ù„Ù„ØªØµØ¯ÙŠØ± ====================
    
    fun exportColumnReport(
        projectName: String,
        designCode: DesignCode,
        columnType: ColumnType,
        inputs: ColumnInputs,
        result: AdvancedColumnResult,
        inventoryAnalysis: InventoryAnalysisResult?,
        alternatives: List<ColumnAlternative>,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, "Column Design") }
            drawPage(document, pageNum++) { drawInputsSection(it, inputs, columnType) }
            drawPage(document, pageNum++) { drawMainResults(it, result) }
            drawPage(document, pageNum++) { drawColumnBOQ(it, result, inputs) }
            drawPage(document, pageNum++) { drawColumnSectionPage(it, columnType, result.reinforcementResult) }
            drawPage(document, pageNum++) { drawDetailedCalculations(it, result, designCode) }
            
            if (inventoryAnalysis != null) {
                drawPage(document, pageNum++) { drawInventoryAnalysis(it, inventoryAnalysis) }
            }
            
            if (alternatives.isNotEmpty()) {
                drawPage(document, pageNum++) { drawAlternatives(it, alternatives) }
            }
            
            drawPage(document, pageNum++) { drawProfessionalEngineeringTips(it, "COLUMN") }
            drawPage(document, pageNum) { drawCodeReferencesAndNotes(it, result.codeNotes, result.warnings) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback for extreme calculation errors (NaN/Inf)
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    fun exportBeamReport(
        projectName: String,
        designCode: DesignCode,
        beamType: BeamType,
        inputs: BeamInputs,
        result: AdvancedBeamResult,
        inventoryAnalysis: InventoryAnalysisResult?,
        momentShearDiagrams: MomentShearDiagrams,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, "Beam Design") }
            drawPage(document, pageNum++) { drawBeamInputs(it, beamType, inputs) }
            drawPage(document, pageNum++) { drawBeamResults(it, result) }
            drawPage(document, pageNum++) { drawBeamBOQ(it, result, inputs) }
            drawPage(document, pageNum++) { drawBeamSectionPage(it, result, inputs) }
            drawPage(document, pageNum++) { drawMomentShearDiagrams(it, momentShearDiagrams) }
            drawPage(document, pageNum++) { drawBeamCalculations(it, result) }
            
            if (inventoryAnalysis != null) {
                drawPage(document, pageNum++) { drawInventoryAnalysis(it, inventoryAnalysis) }
            }
            
            drawPage(document, pageNum++) { drawProfessionalEngineeringTips(it, "BEAM") }
            drawPage(document, pageNum) { drawCodeReferencesAndNotes(it, result.codeNotes, result.warnings) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    fun exportSlabReport(
        projectName: String,
        designCode: DesignCode,
        slabType: SlabType,
        inputs: SlabInputs,
        result: AdvancedSlabResult,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, getString("تصميم البلاطات الخرسانية", "Slab Design Report")) }
            drawPage(document, pageNum++) { drawSlabInputs(it, slabType, inputs) }
            drawPage(document, pageNum++) { drawSlabResults(it, result) }
            drawPage(document, pageNum++) { drawSlabQuantities(it, result) }
            drawPage(document, pageNum++) { drawSlabSectionDetail(it, result) }
            drawPage(document, pageNum++) { drawSlabLayoutPage(it, result.reinforcementLayout) }
            drawPage(document, pageNum++) { drawSlabCalculations(it, result) }
            drawPage(document, pageNum++) { drawProfessionalEngineeringTips(it, "SLAB") }
            drawPage(document, pageNum) { drawCodeReferencesAndNotes(it, result.codeNotes, result.warnings) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    fun exportFootingReport(
        projectName: String,
        designCode: CalculatorEngine.DesignCode,
        result: CalculatorEngine.FootingResult,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, "Footing Design Report") }
            drawPage(document, pageNum++) { drawFootingInputs(it, result) }
            drawPage(document, pageNum++) { drawFootingResults(it, result) }
            drawPage(document, pageNum++) { drawFootingCalculations(it, result) }
            drawPage(document, pageNum++) { drawFootingBOQ(it, result) }
            drawPage(document, pageNum) { drawFootingDrawing(it, result) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    fun exportStairReport(
        projectName: String,
        designCode: CalculatorEngine.DesignCode,
        result: CalculatorEngine.StairResult,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, "Staircase Design Report") }
            drawPage(document, pageNum++) { drawStairInputs(it, result) }
            drawPage(document, pageNum++) { drawStairResults(it, result) }
            drawPage(document, pageNum++) { drawStairSketch(it, result) }
            drawPage(document, pageNum++) { drawStairCalculations(it, result) }
            drawPage(document, pageNum) { drawStairBOQ(it, result) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    private fun drawStairSketch(canvas: Canvas, result: CalculatorEngine.StairResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ø±Ø³Ù… ÙƒØ±ÙˆÙƒÙŠ Ù„Ù„Ø³Ù„Ù…" else "ðŸ“ Staircase Section Sketch"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 60f
        
        activeCanvas = checkNewPage(activeCanvas, 350f)
        val y = currentY
        val cx = PAGE_WIDTH / 2f
        
        val paint = Paint().apply { color = Color.parseColor("#EEEEEE"); style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        val rebarPaint = Paint().apply { color = Color.RED; strokeWidth = 1.5f; style = Paint.Style.STROKE }
        
        // Draw 3 steps
        val riser = 30f
        val tread = 50f
        val ts = 25f
        
        val path = Path().apply {
            moveTo(cx - 150f, y + 150f)
            lineTo(cx - 150f + tread, y + 150f)
            lineTo(cx - 150f + tread, y + 150f - riser)
            lineTo(cx - 150f + 2 * tread, y + 150f - riser)
            lineTo(cx - 150f + 2 * tread, y + 150f - 2 * riser)
            lineTo(cx - 150f + 3 * tread, y + 150f - 2 * riser)
            lineTo(cx - 150f + 3 * tread, y + 150f - 2 * riser + ts)
            lineTo(cx - 150f + ts, y + 150f + ts)
            close()
        }
        activeCanvas.drawPath(path, paint)
        activeCanvas.drawPath(path, strokePaint)
        
        // Reinforcement (Bottom)
        activeCanvas.drawLine(cx - 150f + 10f, y + 150f + ts - 5f, cx - 150f + 3 * tread - 5f, y + 150f - 2 * riser + ts - 5f, rebarPaint)
        
        currentY = y + 200f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø³Ù…Ùƒ Ø§Ù„Ø¨Ù„Ø§Ø·Ø© (ts)" else "Slab Thickness (ts)", "${result.thickness.toInt()} mm")
        currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ù‚Ø§Ø¦Ù…Ø© / Ø§Ù„Ù†Ø§Ø¦Ù…Ø©" else "Riser / Tread", "${result.riser.toInt()} / ${result.tread.toInt()} mm")
    }

    private fun drawStairCalculations(canvas: Canvas, result: CalculatorEngine.StairResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ§® Ø­Ø³Ø§Ø¨Ø§Øª Ø§Ù„Ø³Ù„Ù… Ø§Ù„ØªÙØµÙŠÙ„ÙŠØ©" else "ðŸ§® Detailed Stair Calculations"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawFormattedText(activeCanvas, if (isAr) "1. Ø§Ù„Ø®ØµØ§Ø¦Øµ Ø§Ù„Ù‡Ù†Ø¯Ø³ÙŠØ©:" else "1. Geometric Parameters:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø³Ù…Ùƒ Ø§Ù„Ø¨Ù„Ø§Ø·Ø© (ts)" else "Stair Thickness (ts)", "${result.thickness.toInt()} mm")
        drawInfoRow(activeCanvas, currentY + 20f, if (isAr) "Ø·ÙˆÙ„ Ø§Ù„Ø¨Ø­Ø± (L)" else "Span Length (L)", "%.2f m".format(result.span))
        drawInfoRow(activeCanvas, currentY + 40f, if (isAr) "Ø§Ù„Ù†Ø§ÙŠÙ…Ø© / Ø§Ù„Ù‚Ø§Ø¦Ù…Ø©" else "Riser / Tread", "${result.riser.toInt()} / ${result.tread.toInt()} mm")
        val angle = Math.toDegrees(atan(result.riser / result.tread.coerceAtLeast(1.0)))
        drawInfoRow(activeCanvas, currentY + 60f, if (isAr) "Ø²Ø§ÙˆÙŠØ© Ø§Ù„Ù…ÙŠÙ„ (Î±)" else "Inclination Angle (Î±)", "%.1fÂ°".format(angle)); currentY += 85f

        drawFormattedText(activeCanvas, if (isAr) "2. Ø§Ù„ØªØ­Ù„ÙŠÙ„ Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠ:" else "2. Structural Analysis:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø­Ù…Ù„ Ø§Ù„ØªØµÙ…ÙŠÙ…ÙŠ (wu)" else "Design Load (wu)", "%.2f kN/mÂ²".format(result.wu))
        drawInfoRow(activeCanvas, currentY + 20f, if (isAr) "Ø§Ù„Ø¹Ø²Ù… Ø§Ù„ØªØµÙ…ÙŠÙ…ÙŠ (Mu)" else "Design Moment (Mu)", "%.2f kN.m/m'".format(result.mu))
        drawInfoRow(activeCanvas, currentY + 40f, if (isAr) "Ø§Ù„Ø¹Ù…Ù‚ Ø§Ù„ÙØ¹Ø§Ù„ (d)" else "Effective Depth (d)", "${(result.thickness - 25).toInt()} mm"); currentY += 65f

        drawFormattedText(activeCanvas, if (isAr) "3. Ø­Ø³Ø§Ø¨Ø§Øª Ø§Ù„ØªØ³Ù„ÙŠØ­:" else "3. Reinforcement Calculation:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        val asReq = (result.mu * 1e6) / (0.8 * result.fy * (result.thickness - 25))
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ù…Ø·Ù„ÙˆØ¨Ø©" else "Required Steel Area (As_req)", "%.1f mmÂ²/m'".format(asReq))
        drawInfoRow(activeCanvas, currentY + 20f, if (isAr) "Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ù…ÙˆÙØ±Ø©" else "Provided Steel Area (As_prov)", "%.1f mmÂ²/m'".format(result.reinforcement.area))
        drawInfoRow(activeCanvas, currentY + 40f, if (isAr) "Ø§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠ" else "Main Bars", result.reinforcement.barString)
        drawInfoRow(activeCanvas, currentY + 60f, if (isAr) "Ø­Ø¯ÙŠØ¯ Ø§Ù„ØªÙˆØ²ÙŠØ¹" else "Distribution Bars", result.distributionReinforcement.barString); currentY += 85f

        if (result.safetyChecks.isNotEmpty()) {
            activeCanvas = checkNewPage(activeCanvas, 150f)
            drawFormattedText(activeCanvas, if (isAr) "4. Ø§Ù„ØªØ­Ù‚Ù‚ Ù…Ù† Ø§Ù„Ø£Ù…Ø§Ù†:" else "4. Safety Verifications:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            result.safetyChecks.forEach { check ->
                activeCanvas = checkNewPage(activeCanvas, 25f)
                val status = if (check.isSafe) (if (isAr) "Ø¢Ù…Ù† âœ…" else "PASS âœ…") else (if (isAr) "ØºÙŠØ± Ø¢Ù…Ù† âŒ" else "FAIL âŒ")
                drawInfoRow(activeCanvas, currentY, check.name, status); currentY += 20f
            }
        }
    }

    private fun drawStairBOQ(canvas: Canvas, result: CalculatorEngine.StairResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ—ï¸ Ø¬Ø¯ÙˆÙ„ Ø§Ù„ÙƒÙ…ÙŠØ§Øª ÙˆØªÙ‚Ø¯ÙŠØ± Ø§Ù„ØªÙƒÙ„ÙØ©" else "ðŸ—ï¸ Bill of Quantities (BOQ)"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 50f
        
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 3f
        val bgPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        activeCanvas.drawRect(MARGIN.toFloat(), currentY - 5f, (PAGE_WIDTH - MARGIN).toFloat(), currentY + 30f, bgPaint)
        
        val h1 = if (isAr) "Ø§Ù„ÙˆØµÙ" else "Description"
        val h2 = if (isAr) "Ø§Ù„ÙƒÙ…ÙŠØ©" else "Quantity"
        val h3 = if (isAr) "Ø§Ù„ØªÙƒÙ„ÙØ©" else "Estimated Cost"
        
        subtitlePaint.textSize = 12f
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, h1, (PAGE_WIDTH - MARGIN) - 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h2, (PAGE_WIDTH - MARGIN) - colWidth - 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h3, (PAGE_WIDTH - MARGIN) - 2 * colWidth - 10f, currentY + 20f, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, h1, MARGIN + 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h2, MARGIN + colWidth + 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h3, MARGIN + 2 * colWidth + 10f, currentY + 20f, subtitlePaint)
        }
        currentY += 50f
        
        val currency = settingsManager.currency
        val concreteDesc = if (isAr) "Ø®Ø±Ø³Ø§Ù†Ø© Ø¬Ø§Ù‡Ø²Ø©" else "Concrete (Ready-Mix)"
        val steelDesc = if (isAr) "Ø­Ø¯ÙŠØ¯ ØªØ³Ù„ÙŠØ­" else "Reinforcement Steel"
        
        val concreteCost = result.concreteVolume * settingsManager.concretePrice
        val steelCost = (result.steelWeight / 1000.0) * settingsManager.steelPrice

        drawBOQRow(activeCanvas, currentY, concreteDesc, "${"%.2f".format(result.concreteVolume)} mÂ³", "${"%.0f".format(concreteCost)} $currency"); currentY += 30f
        activeCanvas = checkNewPage(activeCanvas, 30f)
        drawBOQRow(activeCanvas, currentY, steelDesc, "${"%.2f".format(result.steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); currentY += 30f
        
        activeCanvas = checkNewPage(activeCanvas, 40f)
        activeCanvas.drawLine(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY, Paint().apply { color = Color.LTGRAY }); currentY += 40f
        
        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„ØªÙƒÙ„ÙØ© Ø§Ù„ØªÙ‚Ø¯ÙŠØ±ÙŠØ©" else "TOTAL ESTIMATED COST"
        
        activeCanvas = checkNewPage(activeCanvas, 40f)
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, totalLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
            drawFormattedText(activeCanvas, "${"%.2f".format(concreteCost + steelCost)} $currency", MARGIN.toFloat(), currentY, titlePaint)
        } else {
            drawFormattedText(activeCanvas, totalLabel, MARGIN.toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, "${"%.2f".format(concreteCost + steelCost)} $currency", (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        }
        
        titlePaint.color = textColor
        currentY += 60f
        drawDisclaimer(activeCanvas, currentY)
    }

    private fun drawStairInputs(canvas: Canvas, result: CalculatorEngine.StairResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ù…Ø¹Ø·ÙŠØ§Øª ØªØµÙ…ÙŠÙ… Ø§Ù„Ø³Ù„Ù…" else "ðŸ“ Stair Parameters"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„Ø³Ù„Ù…" else "Stair Type", result.type.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø­Ø¬Ù… Ø§Ù„Ø®Ø±Ø³Ø§Ù†Ø©" else "Concrete Volume", "${"%.2f".format(result.concreteVolume)} mÂ³"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "ÙˆØ²Ù† Ø§Ù„ØµÙ„Ø¨" else "Steel Weight", "${result.steelWeight.toInt()} kg"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "ÙƒÙˆØ¯ Ø§Ù„ØªØµÙ…ÙŠÙ…" else "Design Code", result.code.displayName)
    }

    private fun drawStairResults(canvas: Canvas, result: CalculatorEngine.StairResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“Š Ù†ØªØ§Ø¦Ø¬ Ø§Ù„ØªØµÙ…ÙŠÙ…" else "ðŸ“Š Design Results"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawStatusHeader(canvas, y, result.isSafe); y += 40f
        
        drawUtilizationBar(canvas, y, if (isAr) "Ù†Ø³Ø¨Ø© Ø§Ù„Ø§Ø³ØªØ®Ø¯Ø§Ù… Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠ" else "Structural Utilization", result.utilizationRatio); y += 40f

        drawInfoRow(canvas, y, if (isAr) "Ø³Ù…Ùƒ Ø§Ù„Ø¨Ù„Ø§Ø·Ø© (ts)" else "Thickness (ts)", "${result.thickness.toInt()} mm"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„ØªØ³Ù„ÙŠØ­ Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠ" else "Main Reinforcement", result.reinforcement.barString); y += 25f
        drawInfoRow(canvas, y, if (isAr) "ØªØ³Ù„ÙŠØ­ Ø§Ù„ØªÙˆØ²ÙŠØ¹" else "Distribution Reinforcement", result.distributionReinforcement.barString)
    }

    fun exportRetainingWallReport(
        projectName: String,
        designCode: CalculatorEngine.DesignCode,
        result: CalculatorEngine.RetainingWallResult,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, "Retaining Wall Design Report") }
            drawPage(document, pageNum++) { drawRetainingWallInputs(it, result) }
            drawPage(document, pageNum++) { drawRetainingWallResults(it, result) }
            drawPage(document, pageNum++) { drawRetainingWallBOQ(it, result) }
            drawPage(document, pageNum++) { drawWallAnalysisDiagrams(it, result) }
            drawPage(document, pageNum) { drawWallSectionDetail(it, result) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    private fun drawWallAnalysisDiagrams(canvas: Canvas, result: CalculatorEngine.RetainingWallResult) {
        var y = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, "ðŸ“ˆ Structural Analysis (Stem Diagrams)", MARGIN.toFloat(), y, titlePaint); y += 100f
        
        val centerX = PAGE_WIDTH / 2f
        val diagramH = 150f
        
        // 1. Earth Pressure Diagram
        val pLeft = 80f
        drawFormattedText(canvas, "Earth Pressure (Active)", pLeft, y - 10f, subtitlePaint)
        val pPath = Path()
        pPath.moveTo(pLeft, y)
        pPath.lineTo(pLeft, y + diagramH)
        val pWidth = (result.pa * 2 / result.height.coerceAtLeast(1.0)).toFloat() * 2.0f // Scaled representation
        pPath.lineTo(pLeft + pWidth.coerceIn(50f, 150f), y + diagramH)
        pPath.close()
        canvas.drawPath(pPath, Paint().apply { color = Color.parseColor("#FFE0B2"); style = Paint.Style.FILL })
        drawFormattedText(canvas, "pa = %.1f kN/m'".format(result.pa), pLeft + 10f, y + diagramH + 15f, smallPaint)

        // 2. Bending Moment Diagram
        val mLeft = centerX + 50f
        drawFormattedText(canvas, "Stem Moment (kN.m)", mLeft, y - 10f, subtitlePaint)
        val mPath = Path()
        mPath.moveTo(mLeft, y)
        mPath.lineTo(mLeft, y + diagramH)
        val curve = Path()
        curve.moveTo(mLeft, y)
        curve.quadTo(mLeft + 120f, y + diagramH * 0.7f, mLeft, y + diagramH)
        canvas.drawPath(curve, Paint().apply { color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 3f })
        drawFormattedText(canvas, "Mu = %.1f kN.m".format(result.muStem), mLeft + 10f, y + diagramH + 15f, smallPaint)

        // Detailed Calculations Table
        y += diagramH + 60f
        drawFormattedText(canvas, "Calculation Summary:", MARGIN.toFloat(), y, subtitlePaint); y += 25f
        drawInfoRow(canvas, y, "Coeff. Active Pressure (Ka)", "%.3f".format(result.ka))
        drawInfoRow(canvas, y + 20f, "Soil Density (Î³)", "${result.soilDensity.toInt()} kN/mÂ³")
        drawInfoRow(canvas, y + 40f, "Stem Factored Moment", "%.2f kN.m/m'".format(result.muStem))
        drawInfoRow(canvas, y + 60f, "Required Steel (As_req)", "%.1f mmÂ²/m'".format((result.muStem * 1e6) / (0.8 * result.fy * (result.stemThickness - 70))))
    }

    private fun drawWallSectionDetail(canvas: Canvas, result: CalculatorEngine.RetainingWallResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ ØªÙØ§ØµÙŠÙ„ ØªØ³Ù„ÙŠØ­ Ø­Ø§Ø¦Ø· Ø§Ù„Ø³Ù†Ø¯" else "ðŸ“ Wall Cross Section & Reinforcement"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 60f
        
        activeCanvas = checkNewPage(activeCanvas, 350f)
        val y = currentY
        val h = 250f
        val baseW = 180f
        val stemT = 30f
        val startX = PAGE_WIDTH / 2f - 50f
        
        // Concrete hatch
        val hatchPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 1f }
        val wallPath = Path().apply {
            moveTo(startX, y)
            lineTo(startX + stemT, y)
            lineTo(startX + stemT, y + h)
            lineTo(startX + baseW - 60f, y + h)
            lineTo(startX + baseW - 60f, y + h + 30f)
            lineTo(startX - 60f, y + h + 30f)
            lineTo(startX - 60f, y + h)
            lineTo(startX, y + h)
            close()
        }
        
        activeCanvas.save()
        activeCanvas.clipPath(wallPath)
        val step = 15f
        for (i in -500 until 1000 step step.toInt()) {
            activeCanvas.drawLine(i.toFloat(), y, i + 500f, y + 500f, hatchPaint)
        }
        activeCanvas.restore()

        val outlinePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        activeCanvas.drawPath(wallPath, outlinePaint)
        
        // Main Reinforcement (Stem Rear) - Steel is Red
        val rebarPaint = Paint().apply { color = Color.RED; strokeWidth = 3f; style = Paint.Style.STROKE }
        activeCanvas.drawLine(startX + stemT - 8f, y + 10f, startX + stemT - 8f, y + h + 22f, rebarPaint)
        activeCanvas.drawLine(startX + 5f, y + 10f, startX + 5f, y + h + 25f, rebarPaint) // Main Vertical
        
        // Hooks
        val hookPath = Path()
        hookPath.moveTo(startX + 5f, y + h + 25f)
        hookPath.lineTo(startX + 40f, y + h + 25f)
        activeCanvas.drawPath(hookPath, rebarPaint)
        
        // Secondary/Base reinf
        activeCanvas.drawLine(startX - 55f, y + h + 25f, startX + baseW - 65f, y + h + 25f, rebarPaint)
        
        // Labels
        bodyPaint.textSize = 12f
        drawFormattedText(activeCanvas, "H=${result.height}m", startX - 80f, y + h/2, bodyPaint)
        drawFormattedText(activeCanvas, "Main Reinf: ${result.stemReinforcement.barString}", startX + stemT + 10f, y + 100f, bodyPaint)

        currentY = y + h + 60f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ØªØ³Ù„ÙŠØ­ Ø§Ù„Ø­Ø§Ø¦Ø·" else "Stem Reinforcement", result.stemReinforcement.barString)
        currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ØªØ³Ù„ÙŠØ­ Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "Base Reinforcement", result.baseReinforcement.barString)
    }

    private fun drawRetainingWallBOQ(canvas: Canvas, result: CalculatorEngine.RetainingWallResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ—ï¸ Ø¬Ø¯ÙˆÙ„ Ø§Ù„ÙƒÙ…ÙŠØ§Øª ÙˆØªÙ‚Ø¯ÙŠØ± Ø§Ù„ØªÙƒÙ„ÙØ©" else "ðŸ—ï¸ Bill of Quantities (BOQ)"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 50f
        
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 3f
        val bgPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        activeCanvas.drawRect(MARGIN.toFloat(), currentY - 5f, (PAGE_WIDTH - MARGIN).toFloat(), currentY + 30f, bgPaint)
        
        val h1 = if (isAr) "Ø§Ù„ÙˆØµÙ" else "Description"
        val h2 = if (isAr) "Ø§Ù„ÙƒÙ…ÙŠØ©" else "Quantity"
        val h3 = if (isAr) "Ø§Ù„ØªÙƒÙ„ÙØ©" else "Estimated Cost"
        
        subtitlePaint.textSize = 12f
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, h1, (PAGE_WIDTH - MARGIN) - 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h2, (PAGE_WIDTH - MARGIN) - colWidth - 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h3, (PAGE_WIDTH - MARGIN) - 2 * colWidth - 10f, currentY + 20f, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, h1, MARGIN + 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h2, MARGIN + colWidth + 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h3, MARGIN + 2 * colWidth + 10f, currentY + 20f, subtitlePaint)
        }
        currentY += 50f
        
        val currency = settingsManager.currency
        val concreteDesc = if (isAr) "Ø®Ø±Ø³Ø§Ù†Ø© Ø¬Ø§Ù‡Ø²Ø©" else "Concrete (Ready-Mix)"
        val steelDesc = if (isAr) "Ø­Ø¯ÙŠØ¯ ØªØ³Ù„ÙŠØ­" else "Reinforcement Steel"
        
        val concreteCost = result.concreteVolume * settingsManager.concretePrice
        val steelCost = (result.steelWeight / 1000.0) * settingsManager.steelPrice

        drawBOQRow(activeCanvas, currentY, concreteDesc, "${"%.2f".format(result.concreteVolume)} mÂ³", "${"%.0f".format(concreteCost)} $currency"); currentY += 30f
        activeCanvas = checkNewPage(activeCanvas, 30f)
        drawBOQRow(activeCanvas, currentY, steelDesc, "${"%.2f".format(result.steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); currentY += 30f
        
        activeCanvas = checkNewPage(activeCanvas, 40f)
        activeCanvas.drawLine(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY, Paint().apply { color = Color.LTGRAY }); currentY += 40f
        
        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„ØªÙƒÙ„ÙØ© Ø§Ù„ØªÙ‚Ø¯ÙŠØ±ÙŠØ©" else "TOTAL ESTIMATED COST"
        
        activeCanvas = checkNewPage(activeCanvas, 40f)
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, totalLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
            drawFormattedText(activeCanvas, "${"%.2f".format(concreteCost + steelCost)} $currency", MARGIN.toFloat(), currentY, titlePaint)
        } else {
            drawFormattedText(activeCanvas, totalLabel, MARGIN.toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, "${"%.2f".format(concreteCost + steelCost)} $currency", (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        }

        titlePaint.color = textColor
        currentY += 60f
        drawDisclaimer(activeCanvas, currentY)
    }

    private fun drawRetainingWallInputs(canvas: Canvas, result: CalculatorEngine.RetainingWallResult) {
        val activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ù…Ø¯Ø®Ù„Ø§Øª Ø§Ù„Ø¬Ø¯Ø§Ø± Ø§Ù„Ø³Ø§Ù†Ø¯" else "ðŸ“ Wall Parameters"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f

        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø§Ø±ØªÙØ§Ø¹" else "Height", "${result.height} m"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø­Ø¬Ù… Ø§Ù„Ø®Ø±Ø³Ø§Ù†Ø©" else "Concrete Volume", "${"%.2f".format(result.concreteVolume)} mÂ³"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ÙˆØ²Ù† Ø§Ù„Ø­Ø¯ÙŠØ¯" else "Steel Weight", "${result.steelWeight.toInt()} kg"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ÙƒÙˆØ¯ Ø§Ù„ØªØµÙ…ÙŠÙ…" else "Design Code", result.code.displayName)
    }


    private fun drawRetainingWallResults(canvas: Canvas, result: CalculatorEngine.RetainingWallResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“Š Ù†ØªØ§Ø¦Ø¬ ØªØµÙ…ÙŠÙ… Ø§Ù„Ø¬Ø¯Ø§Ø± Ø§Ù„Ø³Ø§Ù†Ø¯" else "ðŸ“Š Retaining Wall Design Results"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawStatusHeader(activeCanvas, currentY, result.isSafe); currentY += 40f

        drawUtilizationBar(activeCanvas, currentY, if (isAr) "Ù†Ø³Ø¨Ø© Ø§Ù„Ø§Ø³ØªÙ‡Ù„Ø§Ùƒ Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠ" else "Structural Utilization", result.utilizationRatio); currentY += 40f

        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø¹Ø±Ø¶ Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "Base Width", "${result.baseWidth.toInt()} mm"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø³Ù…Ùƒ Ø§Ù„Ø¬Ø°Ø¹ (Stem)" else "Stem Thickness", "${result.stemThickness.toInt()} mm"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ØªØ³Ù„ÙŠØ­ Ø§Ù„Ø¬Ø°Ø¹" else "Stem Reinforcement", result.stemReinforcement.barString); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ØªØ³Ù„ÙŠØ­ Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "Base Reinforcement", result.baseReinforcement.barString)
        
        currentY += 40f
        activeCanvas = checkNewPage(activeCanvas, 100f)
        drawFormattedText(activeCanvas, if (isAr) "ÙØ­ÙˆØµØ§Øª Ø§Ù„Ø§Ø³ØªÙ‚Ø±Ø§Ø±:" else "Stability Checks:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù…Ø¹Ø§Ù…Ù„ Ø§Ù„Ø£Ù…Ø§Ù† Ø¶Ø¯ Ø§Ù„Ø§Ù†Ù‚Ù„Ø§Ø¨" else "F.S. Overturning", "%.2f".format(result.factorOfSafetyOverturning)); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù…Ø¹Ø§Ù…Ù„ Ø§Ù„Ø£Ù…Ø§Ù† Ø¶Ø¯ Ø§Ù„Ø§Ù†Ø²Ù„Ø§Ù‚" else "F.S. Sliding", "%.2f".format(result.factorOfSafetySliding)); currentY += 20f

        result.safetyChecks.forEach { check ->
            activeCanvas = checkNewPage(activeCanvas, 25f)
            val status = if (check.isSafe) (if (isAr) "Ø¢Ù…Ù† âœ…" else "SAFE") else (if (isAr) "ØºÙŠØ± Ø¢Ù…Ù† âŒ" else "UNSAFE")
            drawInfoRow(activeCanvas, currentY, check.name, status); currentY += 20f
        }

        // Add visual sketch for Retaining Wall
        activeCanvas = checkNewPage(activeCanvas, 300f)
        drawRetainingWallSketch(activeCanvas, result)
    }

    private fun drawRetainingWallSketch(canvas: Canvas, result: CalculatorEngine.RetainingWallResult) {
        var activeCanvas = canvas
        activeCanvas = checkNewPage(activeCanvas, 300f)
        val cy = currentY + 150f
        val cx = PAGE_WIDTH / 2f
        val paint = Paint().apply { color = Color.parseColor("#E0E0E0"); style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        
        val wallH = 180f
        val wallT = 25f
        val baseW = 120f
        val baseT = 20f
        
        // Draw Stem
        activeCanvas.drawRect(cx - wallT/2, cy - wallH, cx + wallT/2, cy, paint)
        activeCanvas.drawRect(cx - wallT/2, cy - wallH, cx + wallT/2, cy, strokePaint)
        
        // Draw Base
        activeCanvas.drawRect(cx - baseW/2, cy, cx + baseW/2, cy + baseT, paint)
        activeCanvas.drawRect(cx - baseW/2, cy, cx + baseW/2, cy + baseT, strokePaint)
        
        // Rebar lines (Red)
        val rebarPaint = Paint().apply { color = Color.RED; strokeWidth = 1.5f }
        activeCanvas.drawLine(cx + wallT/2 - 5f, cy - wallH + 10f, cx + wallT/2 - 5f, cy + baseT - 5f, rebarPaint) // Stem main bar
        activeCanvas.drawLine(cx - baseW/2 + 5f, cy + baseT - 5f, cx + baseW/2 - 5f, cy + baseT - 5f, rebarPaint) // Base bottom bar
        
        smallPaint.textAlign = Paint.Align.CENTER
        drawFormattedText(activeCanvas, "H=${result.height}mm", cx - baseW/2 - 30f, cy - wallH/2, smallPaint)
        drawFormattedText(activeCanvas, "B=${result.baseWidth.toInt()}mm", cx, cy + baseT + 25f, smallPaint)
        currentY = cy + baseT + 50f
    }


    private fun drawFootingInputs(canvas: Canvas, result: CalculatorEngine.FootingResult) {
        val activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ù…Ø¯Ø®Ù„Ø§Øª Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "ðŸ“ Footing Parameters"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f

        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "Footing Type", result.type.displayName); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù‚Ø¯Ø±Ø© ØªØ­Ù…Ù„ Ø§Ù„ØªØ±Ø¨Ø©" else "Soil Capacity", "${result.allowablePressure} kPa"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ÙƒÙˆØ¯ Ø§Ù„ØªØµÙ…ÙŠÙ…" else "Design Code", result.code.displayName)
    }

    private fun drawFootingResults(canvas: Canvas, result: CalculatorEngine.FootingResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“Š Ù†ØªØ§Ø¦Ø¬ Ø§Ù„ØªØµÙ…ÙŠÙ… ÙˆÙØ­Øµ Ø§Ù„Ø£Ù…Ø§Ù†" else "ðŸ“Š Design Results & Safety Checks"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawStatusHeader(activeCanvas, currentY, result.isSafe); currentY += 40f
        
        val dimLabel = if (isAr) "Ø§Ù„Ø£Ø¨Ø¹Ø§Ø¯:" else "Dimensions:"
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, dimLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, dimLabel, MARGIN.toFloat(), currentY, subtitlePaint)
        }
        currentY += 25f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø¹Ø±Ø¶ (B)" else "Width (B)", "${result.width.toInt()} mm"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø·ÙˆÙ„ (L)" else "Length (L)", "${result.length.toInt()} mm"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø³Ù…Ùƒ (T)" else "Thickness (T)", "${result.thickness.toInt()} mm"); currentY += 35f
        
        if (result.isCombined) {
            activeCanvas = checkNewPage(activeCanvas, 60f)
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ù…Ø³Ø§ÙØ© Ø¨ÙŠÙ† Ø§Ù„Ø£Ø¹Ù…Ø¯Ø©" else "Col Distance", "${"%.2f".format(result.distanceBetweenColumns / 1000.0)} m"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ù…Ù‚Ø§Ø³ Ø§Ù„Ø¹Ù…ÙˆØ¯ 1" else "Col 1 Size", "${result.column1Size.first.toInt()}x${result.column1Size.second.toInt()} mm"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ù…Ù‚Ø§Ø³ Ø§Ù„Ø¹Ù…ÙˆØ¯ 2" else "Col 2 Size", "${result.column2Size.first.toInt()}x${result.column2Size.second.toInt()} mm"); currentY += 35f
        }

        activeCanvas = checkNewPage(activeCanvas, 60f)
        val soilLabel = if (isAr) "Ø¶ØºØ· Ø§Ù„ØªØ±Ø¨Ø©:" else "Soil Pressure:"
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, soilLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, soilLabel, MARGIN.toFloat(), currentY, subtitlePaint)
        }
        currentY += 25f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø¶ØºØ· Ø§Ù„ÙØ¹Ù„ÙŠ" else "Actual Pressure", "${"%.2f".format(result.soilPressure)} kPa"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø¶ØºØ· Ø§Ù„Ù…Ø³Ù…ÙˆØ­" else "Allowable Pressure", "${result.allowablePressure} kPa"); currentY += 20f
        
        val footingUtil = if (result.utilizationRatio > 0) result.utilizationRatio else (result.soilPressure / result.allowablePressure)
        drawUtilizationBar(activeCanvas, currentY, if (isAr) "Ø§Ø³ØªÙ‡Ù„Ø§Ùƒ Ù‚Ø¯Ø±Ø© Ø§Ù„ØªØ±Ø¨Ø©" else "Soil Utilization", footingUtil); currentY += 45f
        
        activeCanvas = checkNewPage(activeCanvas, 80f)
        val reinfBottomLabel = if (isAr) "Ø§Ù„ØªØ³Ù„ÙŠØ­ (Ø³ÙÙ„ÙŠ):" else "Reinforcement (Bottom):"
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, reinfBottomLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, reinfBottomLabel, MARGIN.toFloat(), currentY, subtitlePaint)
        }
        currentY += 25f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø£Ø³ÙŠØ§Ø® (X)" else "Bars (X)", "${result.barsX} Ã˜ ${result.barDiameter}"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø£Ø³ÙŠØ§Ø® (Y)" else "Bars (Y)", "${result.barsY} Ã˜ ${result.barDiameter}"); currentY += 20f
        
        if (result.reinforcementTopX > 0) {
            activeCanvas = checkNewPage(activeCanvas, 60f)
            val reinfTopLabel = if (isAr) "Ø§Ù„ØªØ³Ù„ÙŠØ­ (Ø¹Ù„ÙˆÙŠ):" else "Reinforcement (Top):"
            if (isAr) {
                subtitlePaint.textAlign = Paint.Align.RIGHT
                drawFormattedText(activeCanvas, reinfTopLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
                subtitlePaint.textAlign = Paint.Align.LEFT
            } else {
                drawFormattedText(activeCanvas, reinfTopLabel, MARGIN.toFloat(), currentY, subtitlePaint)
            }
            currentY += 25f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ø£Ø³ÙŠØ§Ø® (X)" else "Bars (X)", "${result.reinforcementTopX} Ã˜ ${result.topBarDiameter}"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ø£Ø³ÙŠØ§Ø® (Y)" else "Bars (Y)", "${result.reinforcementTopY} Ã˜ ${result.topBarDiameter}"); currentY += 20f
        }
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ù…ØªÙˆÙØ±Ø©" else "Total Area Provided", "${"%.1f".format(result.reinforcementBottom.area)} mmÂ²"); currentY += 35f

        if (result.safetyChecks.isNotEmpty()) {
            activeCanvas = checkNewPage(activeCanvas, 60f)
            val safetyLabel = if (isAr) "ÙØ­ÙˆØµØ§Øª Ø§Ù„Ø£Ù…Ø§Ù† Ø§Ù„ØªÙØµÙŠÙ„ÙŠØ©:" else "Detailed Safety Checks:"
            if (isAr) {
                subtitlePaint.textAlign = Paint.Align.RIGHT
                drawFormattedText(activeCanvas, safetyLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
                subtitlePaint.textAlign = Paint.Align.LEFT
            } else {
                drawFormattedText(activeCanvas, safetyLabel, MARGIN.toFloat(), currentY, subtitlePaint)
            }
            currentY += 25f
            result.safetyChecks.forEach { check ->
                activeCanvas = checkNewPage(activeCanvas, 20f)
                drawInfoRow(activeCanvas, currentY, check.name, if (check.isSafe) (if (isAr) "Ø¢Ù…Ù† âœ…" else "PASS âœ…") else (if (isAr) "ØºÙŠØ± Ø¢Ù…Ù† âŒ" else "FAIL âŒ")); currentY += 20f
            }
            currentY += 15f
        }

        activeCanvas = checkNewPage(activeCanvas, 60f)
        val econLabel = if (isAr) "Ø§Ù„ØªØ­Ù„ÙŠÙ„ Ø§Ù„Ø§Ù‚ØªØµØ§Ø¯ÙŠ:" else "Economic Analysis:"
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, econLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, econLabel, MARGIN.toFloat(), currentY, subtitlePaint)
        }
        currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø¯Ø±Ø¬Ø© Ø§Ù„ÙƒÙØ§Ø¡Ø©" else "Efficiency Score", "${"%.1f".format(result.efficiencyScore)}%"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø­Ø§Ù„Ø©" else "Status", if (result.isOptimal) (if (isAr) "ØªØµÙ…ÙŠÙ… Ù…Ø«Ø§Ù„ÙŠ" else "OPTIMAL DESIGN") else (if (isAr) "ØªØµÙ…ÙŠÙ… Ù…ÙØ±Ø·" else "OVER-DESIGNED"))
    }


    private fun drawFootingCalculations(canvas: Canvas, result: CalculatorEngine.FootingResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ§® Ø­Ø³Ø§Ø¨Ø§Øª Ø§Ù„ØªØµÙ…ÙŠÙ… Ø§Ù„ØªÙØµÙŠÙ„ÙŠØ©" else "ðŸ§® Detailed Design Calculations"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawFormattedText(activeCanvas, if (isAr) "1. Ø§Ù„ØªØ­Ù‚Ù‚ Ù…Ù† Ù‚Ø¯Ø±Ø© ØªØ­Ù…Ù„ Ø§Ù„ØªØ±Ø¨Ø©:" else "1. Soil Bearing Capacity Check:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        val qActual = result.soilPressure
        val qAllow = result.allowablePressure
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø¥Ø¬Ù‡Ø§Ø¯ Ø§Ù„ÙØ¹Ù„ÙŠ (q_act)" else "Actual Pressure (q_act)", "${"%.2f".format(qActual)} kPa"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø¥Ø¬Ù‡Ø§Ø¯ Ø§Ù„Ù…Ø³Ù…ÙˆØ­ (q_all)" else "Allowable Pressure (q_all)", "${"%.2f".format(qAllow)} kPa"); currentY += 20f
        val soilStatus = if (qActual <= qAllow) (if (isAr) "Ø¢Ù…Ù†" else "OK") else (if (isAr) "ØºÙŠØ± Ø¢Ù…Ù†" else "NG")
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø­Ø§Ù„Ø©" else "Status", soilStatus); currentY += 35f

        drawFormattedText(activeCanvas, if (isAr) "2. ØªØ³Ù„ÙŠØ­ Ø§Ù„Ø¹Ø²Ù… (Ø³ÙÙ„ÙŠ):" else "2. Flexural Reinforcement (Bottom):", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§ØªØ¬Ø§Ù‡ X" else "Direction X", "${result.barsX} Ã˜ ${result.barDiameter}"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§ØªØ¬Ø§Ù‡ Y" else "Direction Y", "${result.barsY} Ã˜ ${result.barDiameter}"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ù…ÙˆÙØ±Ø©" else "Area Provided (As_prov)", "${"%.1f".format(result.reinforcementBottom.area)} mmÂ²"); currentY += 35f

        if (result.safetyChecks.isNotEmpty()) {
            activeCanvas = checkNewPage(activeCanvas, 150f)
            drawFormattedText(activeCanvas, if (isAr) "3. Ø§Ù„ØªØ­Ù‚Ù‚ Ù…Ù† Ø³Ù„Ø§Ù…Ø© Ø§Ù„Ù…Ù†Ø´Ø£:" else "3. Structural Safety Verifications:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            result.safetyChecks.forEach { check ->
                activeCanvas = checkNewPage(activeCanvas, 25f)
                val status = if (check.isSafe) (if (isAr) "Ø¢Ù…Ù†" else "PASSED") else (if (isAr) "ÙØ´Ù„" else "FAILED")
                drawInfoRow(activeCanvas, currentY, check.name, "${"%.3f".format(check.value)} / ${"%.3f".format(check.limit)} ${check.unit} ($status)"); currentY += 20f
            }
            currentY += 15f
        }
        
        activeCanvas = checkNewPage(activeCanvas, 100f)
        drawFormattedText(activeCanvas, if (isAr) "4. Ù…Ù†Ø·Ù‚ Ø§Ù„Ø£Ø¨Ø¹Ø§Ø¯:" else "4. Dimensioning Logic:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        val area = (result.width * result.length) / 1e6
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "Total Base Area", "${"%.2f".format(area)} mÂ²"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø¹Ù…Ù‚ Ø§Ù„ÙØ¹Ø§Ù„ (d)" else "Effective Depth (d)", "${(result.thickness - 70).toInt()} mm"); currentY += 35f
    }

    private fun drawFootingBOQ(canvas: Canvas, result: CalculatorEngine.FootingResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ—ï¸ Ø¬Ø¯ÙˆÙ„ Ø§Ù„ÙƒÙ…ÙŠØ§Øª ÙˆØªÙ‚Ø¯ÙŠØ± Ø§Ù„ØªÙƒÙ„ÙØ©" else "ðŸ—ï¸ Bill of Quantities (BOQ)"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 50f
        
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 3f
        
        // Table Header
        val bgPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        canvas.drawRect(MARGIN.toFloat(), y - 5f, (PAGE_WIDTH - MARGIN).toFloat(), y + 30f, bgPaint)
        
        val h1 = if (isAr) "Ø§Ù„ÙˆØµÙ" else "Description"
        val h2 = if (isAr) "Ø§Ù„ÙƒÙ…ÙŠØ©" else "Quantity"
        val h3 = if (isAr) "Ø§Ù„ØªÙƒÙ„ÙØ©" else "Cost (Est.)"
        
        subtitlePaint.textSize = 12f
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, h1, (PAGE_WIDTH - MARGIN) - 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h2, (PAGE_WIDTH - MARGIN) - colWidth - 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h3, (PAGE_WIDTH - MARGIN) - 2 * colWidth - 10f, y + 20f, subtitlePaint)
        } else {
            subtitlePaint.textAlign = Paint.Align.LEFT
            drawFormattedText(canvas, h1, MARGIN + 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h2, MARGIN + colWidth + 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h3, MARGIN + 2 * colWidth + 10f, y + 20f, subtitlePaint)
        }
        y += 50f
        
        val currency = settingsManager.currency
        val concreteDesc = if (isAr) "Ø®Ø±Ø³Ø§Ù†Ø© Ù…Ø³Ù„Ø­Ø©" else "Reinforced Concrete"
        val steelDesc = if (isAr) "Ø­Ø¯ÙŠØ¯ ØªØ³Ù„ÙŠØ­" else "Reinforcement Steel"
        
        drawBOQRow(canvas, y, concreteDesc, "${"%.2f".format(result.concreteVolume)} mÂ³", "${"%.0f".format(result.cost * 0.7)} $currency")
        y += 30f
        drawBOQRow(canvas, y, steelDesc, "${"%.2f".format(result.steelWeight)} kg", "${"%.0f".format(result.cost * 0.3)} $currency")
        y += 30f
        
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, Paint().apply { color = Color.LTGRAY }); y += 40f

        // BBS for Footing
        val bbsTitle = if (isAr) "ðŸ“Š ØªÙØ±ÙŠØ¯ Ø­Ø¯ÙŠØ¯ Ø§Ù„ØªØ³Ù„ÙŠØ­ (BBS)" else "ðŸ“Š Bar Bending Schedule (BBS)"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, bbsTitle, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, bbsTitle, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        val numBarsX = ((result.width * 1000) / result.reinforcementBottom.spacing).toInt() + 1
        val numBarsY = ((result.length * 1000) / result.reinforcementBottom.spacing).toInt() + 1
        
        val barX = result.reinforcementBottom.copy(
            barLength = result.length + 0.15, // Adding hooks/lap approx
            shapeCode = 1,
            numBars = numBarsX
        )
        val barY = result.reinforcementBottom.copy(
            barLength = result.width + 0.15,
            shapeCode = 1,
            numBars = numBarsY
        )
        drawBbsTable(canvas, y, listOf(barX, barY)); y += 150f
        
        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„ØªÙƒÙ„ÙØ© Ø§Ù„ØªÙ‚Ø¯ÙŠØ±ÙŠØ©" else "TOTAL ESTIMATED COST"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, totalLabel, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
            drawFormattedText(canvas, "${"%.2f".format(result.cost)} $currency", MARGIN.toFloat(), y, titlePaint)
        } else {
            drawFormattedText(canvas, totalLabel, MARGIN.toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, "${"%.2f".format(result.cost)} $currency", (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        }
        
        titlePaint.color = textColor
        y += 100f
        drawDisclaimer(canvas, y)
    }

    private fun drawBOQRow(canvas: Canvas, y: Float, desc: String, qty: String, cost: String) {
        val isAr = isArabic(desc)
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 3f
        val originalAlign = bodyPaint.textAlign
        
        if (isAr) {
            bodyPaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, desc, (PAGE_WIDTH - MARGIN) - 10f, y, bodyPaint)
            drawFormattedText(canvas, qty, (PAGE_WIDTH - MARGIN) - colWidth - 10f, y, bodyPaint)
            drawFormattedText(canvas, cost, (PAGE_WIDTH - MARGIN) - 2 * colWidth - 10f, y, bodyPaint)
        } else {
            bodyPaint.textAlign = Paint.Align.LEFT
            drawFormattedText(canvas, desc, MARGIN + 10f, y, bodyPaint)
            drawFormattedText(canvas, qty, MARGIN + colWidth + 10f, y, bodyPaint)
            drawFormattedText(canvas, cost, MARGIN + 2 * colWidth + 10f, y, bodyPaint)
        }
        bodyPaint.textAlign = originalAlign
    }

    private fun drawFootingDrawing(canvas: Canvas, result: CalculatorEngine.FootingResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ø±Ø³Ù… ØªÙˆØ¶ÙŠØ­ÙŠ Ù„Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "ðŸ“ Footing Sketch"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        activeCanvas = checkNewPage(activeCanvas, 400f)
        val area = RectF(MARGIN.toFloat(), currentY, PAGE_WIDTH - MARGIN.toFloat(), currentY + 350f)
        val scale = min(area.width() / result.length.toFloat(), area.height() / result.width.toFloat()) * 0.7f
        val w = result.length.toFloat() * scale
        val h = result.width.toFloat() * scale
        val left = area.centerX() - w/2
        val top = area.centerY() - h/2
        
        // Footing Outline & Hatching
        val hatchPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 1f }
        activeCanvas.save()
        activeCanvas.clipRect(left, top, left + w, top + h)
        val step = 15f
        for (i in -h.toInt()..w.toInt() step step.toInt()) {
            activeCanvas.drawLine(left + i, top, left + i + h, top + h, hatchPaint)
        }
        activeCanvas.restore()
        
        activeCanvas.drawRect(left, top, left + w, top + h, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f })
        
        val outlinePaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 3f }
        canvas.drawRect(left, top, left + w, top + h, outlinePaint)
        
        // Draw Columns
        val columnPaint = Paint().apply { 
            style = Paint.Style.FILL
            color = Color.DKGRAY
        }
        
        if (result.isCombined) {
            val c1L = result.column1Size.first.toFloat()
            val c1B = result.column1Size.second.toFloat()
            val c2L = result.column2Size.first.toFloat()
            val c2B = result.column2Size.second.toFloat()
            
            val c1w = (c1L / result.length.toFloat()) * w
            val c1h = (c1B / result.width.toFloat()) * h
            val c2w = (c2L / result.length.toFloat()) * w
            val c2h = (c2B / result.width.toFloat()) * h
            
            val dist = (result.distanceBetweenColumns.toFloat() / result.length.toFloat()) * w
            
            val startX = if (result.isNeighbor) left + c1w/2 else left + (w - (dist + c1w/2 + c2w/2)) / 2
            canvas.drawRect(startX - c1w/2, top + (h - c1h)/2, startX + c1w/2, top + (h + c1h)/2, columnPaint)
            canvas.drawRect(startX + dist - c2w/2, top + (h - c2h)/2, startX + dist + c2w/2, top + (h + c2h)/2, columnPaint)
            
            // Dimension line for distance
            columnPaint.style = Paint.Style.STROKE; columnPaint.strokeWidth = 1f
            canvas.drawLine(startX, top + h/2, startX + dist, top + h/2, columnPaint)
            drawFormattedText(canvas, "${result.distanceBetweenColumns.toInt()} mm", startX + dist/2, top + h/2 - 5f, smallPaint)
        } else {
            // Center single column or move to edge if neighbor
            val cL = result.column1Size.first.toFloat().coerceAtLeast(500f)
            val cB = result.column1Size.second.toFloat().coerceAtLeast(300f)
            val cw = (cL / result.length.toFloat()) * w
            val ch = (cB / result.width.toFloat()) * h
            
            val colLeft = if (result.isNeighbor) left else area.centerX() - cw/2
            canvas.drawRect(colLeft, area.centerY() - ch/2, colLeft + cw, area.centerY() + ch/2, columnPaint)
        }
        
        // Draw rebar lines (Bottom)
        val rebarPaint = Paint().apply {
            color = Color.parseColor("#C62828") // Material Red 800
            strokeWidth = 1.5f
        }
        val numX = result.barsX.coerceAtMost(20)
        val numY = result.barsY.coerceAtMost(20)
        
        for (i in 0 until numX) {
            val offset = (i + 1) * (w / (numX + 1))
            canvas.drawLine(left + offset, top + 5f, left + offset, top + h - 5f, rebarPaint)
        }
        for (i in 0 until numY) {
            val offsetH = (i + 1) * (h / (numY + 1))
            canvas.drawLine(left + 5f, top + offsetH, left + w - 5f, top + offsetH, rebarPaint)
        }

        // Labels
        val labelPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER }
        drawFormattedText(canvas, "L = ${result.length.toInt()} mm", area.centerX(), top - 10f, labelPaint)
        canvas.save()
        canvas.rotate(-90f, left - 10f, area.centerY())
        drawFormattedText(canvas, "B = ${result.width.toInt()} mm", left - 10f, area.centerY(), labelPaint)
        canvas.restore()
        currentY = top + h + 60f
    }

    private fun drawBaseCoverContent(canvas: Canvas, projectName: String, designCode: Any, reportType: String) {
        val designCodeName = when (designCode) {
            is DesignCode -> designCode.displayName
            is CalculatorEngine.DesignCode -> designCode.displayName
            is String -> designCode
            else -> designCode.toString()
        }
        drawBaseCoverContentInternal(canvas, projectName, designCodeName, reportType)
    }

    fun exportTankReport(
        projectName: String,
        designCode: CalculatorEngine.DesignCode,
        result: CalculatorEngine.TankResult,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, "Tank Design Report") }
            drawPage(document, pageNum++) { drawTankInputs(it, result) }
            drawPage(document, pageNum++) { drawTankResults(it, result) }
            drawPage(document, pageNum++) { drawTankBOQ(it, result) }
            drawPage(document, pageNum) { drawTankSectionDetail(it, result) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    private fun drawTankInputs(canvas: Canvas, result: CalculatorEngine.TankResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ù…Ø¹Ø·ÙŠØ§Øª ØªØµÙ…ÙŠÙ… Ø§Ù„Ø®Ø²Ø§Ù†" else "ðŸ“ Tank Geometry & Loading"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„Ø®Ø²Ø§Ù†" else "Tank Type", result.type.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„Ø£Ø¨Ø¹Ø§Ø¯ (Ø·ÙˆÙ„ x Ø¹Ø±Ø¶ x Ø§Ø±ØªÙØ§Ø¹)" else "Dimensions (L x W x H)", "${result.length} x ${result.width} x ${result.height} m"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„Ø³Ø¹Ø©" else "Capacity", "${"%.1f".format(result.capacity)} mÂ³"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø¶ØºØ· Ø§Ù„Ù…Ø§Ø¡" else "Water Pressure", "${"%.2f".format(result.waterPressure)} kN/mÂ²"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø¶ØºØ· Ø§Ù„ØªØ±Ø¨Ø©" else "Soil Pressure", "${"%.2f".format(result.soilPressure)} kN/mÂ²"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "ÙƒÙˆØ¯ Ø§Ù„ØªØµÙ…ÙŠÙ…" else "Design Code", result.code.displayName)
    }

    private fun drawTankResults(canvas: Canvas, result: CalculatorEngine.TankResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“Š Ù†ØªØ§Ø¦Ø¬ ØªØµÙ…ÙŠÙ… Ø§Ù„Ø®Ø²Ø§Ù†" else "ðŸ“Š Tank Design Results"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawStatusHeader(activeCanvas, currentY, result.isSafe); currentY += 40f
        
        drawUtilizationBar(activeCanvas, currentY, if (isAr) "Ù†Ø³Ø¨Ø© Ø§Ù„Ø§Ø³ØªÙ‡Ù„Ø§Ùƒ Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠ" else "Structural Utilization", result.utilizationRatio); currentY += 40f

        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø³Ù…Ùƒ Ø§Ù„Ø­Ø§Ø¦Ø·" else "Wall Thickness", "${(result.wallThickness * 1000).toInt()} mm"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø³Ù…Ùƒ Ø§Ù„Ù„Ø¨Ø´Ø© (Base)" else "Base Thickness", "${(result.baseThickness * 1000).toInt()} mm"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ØªØ³Ù„ÙŠØ­ Ø§Ù„Ø­ÙˆØ§Ø¦Ø·" else "Wall Reinforcement", result.wallReinforcement.barString); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ØªØ³Ù„ÙŠØ­ Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "Base Reinforcement", result.baseReinforcement.barString)
        
        if (result.safetyChecks.isNotEmpty()) {
            currentY += 45f
            activeCanvas = checkNewPage(activeCanvas, 100f)
            drawFormattedText(activeCanvas, if (isAr) "ÙØ­ÙˆØµØ§Øª Ø§Ù„Ø£Ù…Ø§Ù† (Ø§Ù„Ø§Ø³ØªÙ‚Ø±Ø§Ø±):" else "Safety Checks (Stability):", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            result.safetyChecks.forEach { check ->
                activeCanvas = checkNewPage(activeCanvas, 25f)
                val status = if (check.isSafe) (if (isAr) "Ø¢Ù…Ù† âœ…" else "PASS âœ…") else (if (isAr) "ØºÙŠØ± Ø¢Ù…Ù† âŒ" else "FAIL âŒ")
                drawInfoRow(activeCanvas, currentY, check.name, status); currentY += 20f
            }
        }

        // Add visual sketch for Tank
        activeCanvas = checkNewPage(activeCanvas, 300f)
        drawTankOverviewSketch(activeCanvas, result)
    }

    private fun drawTankOverviewSketch(canvas: Canvas, result: CalculatorEngine.TankResult) {
        val cx = PAGE_WIDTH / 2f
        val cy = currentY + 150f
        val paint = Paint().apply { color = Color.parseColor("#F5F5F5"); style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        val waterPaint = Paint().apply { color = Color.parseColor("#E3F2FD"); style = Paint.Style.FILL }
        
        val tankW = 200f
        val tankH = 150f
        val wallT = 15f
        val baseT = 18f
        
        // 1. Draw Water inside
        canvas.drawRect(cx - tankW/2 + wallT, cy - tankH + 20f, cx + tankW/2 - wallT, cy, waterPaint)
        
        // 2. Draw Walls and Base
        val path = Path().apply {
            moveTo(cx - tankW/2, cy - tankH)
            lineTo(cx - tankW/2, cy + baseT)
            lineTo(cx + tankW/2, cy + baseT)
            lineTo(cx + tankW/2, cy - tankH)
            lineTo(cx + tankW/2 - wallT, cy - tankH)
            lineTo(cx + tankW/2 - wallT, cy)
            lineTo(cx - tankW/2 + wallT, cy)
            lineTo(cx - tankW/2 + wallT, cy - tankH)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.drawPath(path, strokePaint)
        
        // 3. Reinforcement (Simplified)
        val rebarPaint = Paint().apply { color = Color.RED; strokeWidth = 1.2f; style = Paint.Style.STROKE }
        // Horizontal base rebar
        canvas.drawLine(cx - tankW/2 + 5f, cy + baseT - 5f, cx + tankW/2 - 5f, cy + baseT - 5f, rebarPaint)
        // Vertical wall rebar
        canvas.drawLine(cx - tankW/2 + 5f, cy - tankH + 10f, cx - tankW/2 + 5f, cy + baseT - 5f, rebarPaint)
        canvas.drawLine(cx + tankW/2 - 5f, cy - tankH + 10f, cx + tankW/2 - 5f, cy + baseT - 5f, rebarPaint)
        
        // 4. Labels
        smallPaint.textAlign = Paint.Align.CENTER
        drawFormattedText(canvas, "H=${result.height}m", cx - tankW/2 - 30f, cy - tankH/2, smallPaint)
        drawFormattedText(canvas, "L=${result.length}m", cx, cy + baseT + 25f, smallPaint)
        
        val isAr = isArabic(settingsManager.language)
        val waterLabel = if (isAr) "Ø¶ØºØ· Ø§Ù„Ù…Ø§Ø¡" else "Water Pressure"
        drawFormattedText(canvas, waterLabel, cx, cy - tankH/2, Paint(smallPaint).apply { color = Color.BLUE; alpha = 150 })
    }


    private fun drawTankBOQ(canvas: Canvas, result: CalculatorEngine.TankResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ—ï¸ Ø¬Ø¯ÙˆÙ„ Ø§Ù„ÙƒÙ…ÙŠØ§Øª ÙˆØªÙ‚Ø¯ÙŠØ± Ø§Ù„ØªÙƒÙ„ÙØ©" else "ðŸ—ï¸ Bill of Quantities (BOQ)"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 50f
        
        val currency = settingsManager.currency
        val concCost = result.concreteVolume * settingsManager.concretePrice
        val steelCost = (result.steelWeight / 1000.0) * settingsManager.steelPrice
        val waterstopPrice = 150.0 // Placeholder or from settings if available
        val waterstopQty = (result.length + result.width) * 2
        val waterstopCost = waterstopQty * waterstopPrice

        drawBOQRow(canvas, y, if (isAr) "Ø®Ø±Ø³Ø§Ù†Ø© Ù…Ø³Ù„Ø­Ø©" else "Reinforced Concrete", "${"%.2f".format(result.concreteVolume)} mÂ³", "${"%.0f".format(concCost)} $currency"); y += 30f
        drawBOQRow(canvas, y, if (isAr) "Ø­Ø¯ÙŠØ¯ ØªØ³Ù„ÙŠØ­" else "Reinforcement Steel", "${"%.2f".format(result.steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); y += 30f
        drawBOQRow(canvas, y, if (isAr) "ÙˆÙˆØªØ± Ø³ØªÙˆØ¨ (Waterstop)" else "Waterstop PVC 25cm", "${"%.1f".format(waterstopQty)} m", "${"%.0f".format(waterstopCost)} $currency"); y += 30f

        // BBS for Tank
        y += 40f
        val bbsTitle = if (isAr) "ðŸ“Š ØªÙØ±ÙŠØ¯ Ø­Ø¯ÙŠØ¯ Ø§Ù„ØªØ³Ù„ÙŠØ­ (BBS)" else "ðŸ“Š Bar Bending Schedule (BBS)"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, bbsTitle, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, bbsTitle, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        val wallBar = result.wallReinforcement.copy(barLength = result.height + 0.5, shapeCode = 1)
        val baseBar = result.baseReinforcement.copy(barLength = (result.length + result.width) / 2.0, shapeCode = 1)
        drawBbsTable(canvas, y, listOf(wallBar, baseBar)); y += 150f
        
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, Paint().apply { color = Color.LTGRAY }); y += 40f
        drawFormattedText(canvas, if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„ØªÙƒÙ„ÙØ©" else "TOTAL COST", MARGIN.toFloat(), y, titlePaint)
        titlePaint.textAlign = Paint.Align.RIGHT
        drawFormattedText(canvas, "${"%.0f".format(concCost + steelCost + waterstopCost)} $currency", (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
        titlePaint.textAlign = Paint.Align.LEFT
    }

    private fun drawTankSectionDetail(canvas: Canvas, result: CalculatorEngine.TankResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ ØªÙØ§ØµÙŠÙ„ ÙˆØµÙ„Ø© Ø§Ù„Ø­Ø§Ø¦Ø· Ù…Ø¹ Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "ðŸ“ Tank Wall-Base Junction Detail"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 60f

        activeCanvas = checkNewPage(activeCanvas, 350f)
        val y = currentY
        val startX = 150f
        val wallT = 40f
        val baseH = 40f
        val wallH = 150f
        
        val paint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
        val concretePaint = Paint().apply { color = Color.parseColor("#EEEEEE"); style = Paint.Style.FILL }
        
        val path = Path().apply {
            moveTo(startX, y)
            lineTo(startX + wallT, y)
            lineTo(startX + wallT, y + wallH)
            lineTo(startX + wallT + 150f, y + wallH)
            lineTo(startX + wallT + 150f, y + wallH + baseH)
            lineTo(startX - 50f, y + wallH + baseH)
            lineTo(startX - 50f, y + wallH)
            lineTo(startX, y + wallH)
            close()
        }
        activeCanvas.drawPath(path, concretePaint)
        activeCanvas.drawPath(path, paint)
        
        // Waterstop
        val wsPaint = Paint().apply { color = Color.BLUE; strokeWidth = 4f }
        activeCanvas.drawLine(startX + wallT/2, y + wallH - 10f, startX + wallT/2, y + wallH + 10f, wsPaint)
        drawFormattedText(activeCanvas, "Waterstop", startX + wallT + 10f, y + wallH, smallPaint)
        
        // Reinforcement
        val rebarPaint = Paint().apply { color = Color.RED; strokeWidth = 2f; style = Paint.Style.STROKE }
        activeCanvas.drawLine(startX + 10f, y + 10f, startX + 10f, y + wallH + baseH - 10f, rebarPaint) // Outer wall
        activeCanvas.drawLine(startX + 10f, y + wallH + baseH - 10f, startX + wallT + 140f, y + wallH + baseH - 10f, rebarPaint) // Bottom base
        
        currentY += wallH + baseH + 40f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø³Ù…Ùƒ Ø§Ù„Ø­Ø§Ø¦Ø·" else "Wall Thickness", "${(result.wallThickness * 1000).toInt()} mm")
        currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø³Ù…Ùƒ Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©" else "Base Thickness", "${(result.baseThickness * 1000).toInt()} mm")
    }



    private fun drawBaseCoverContentInternal(canvas: Canvas, projectName: String, designCodeName: String, reportType: String) {
        var y = 60f
        val isAr = isArabic(settingsManager.language)
        
        // Main Logo/Title
        titlePaint.textAlign = Paint.Align.LEFT
        titlePaint.textSize = 24f
        titlePaint.color = Color.parseColor("#1565C0")
        drawFormattedText(canvas, "Civil Engineer Pro", MARGIN.toFloat(), y, titlePaint)
        
        // Header Right Info
        headerPaint.textAlign = Paint.Align.RIGHT
        val sheetTitle = reportType.uppercase()
        val subSheetTitle = if (isAr) "ØªÙ‚Ø±ÙŠØ± Ø§Ù„ØªØµÙ…ÙŠÙ… Ø§Ù„ÙÙ†ÙŠ ÙˆØ¯Ø±Ø§Ø³Ø© Ø§Ù„Ø¬Ø¯ÙˆÙ‰" else "Technical Design & Feasibility Study"
        drawFormattedText(canvas, sheetTitle, (PAGE_WIDTH - MARGIN).toFloat(), y - 10f, headerPaint)
        headerPaint.textSize = 9f
        headerPaint.isFakeBoldText = false
        drawFormattedText(canvas, subSheetTitle, (PAGE_WIDTH - MARGIN).toFloat(), y + 5f, headerPaint)
        
        y += 20f
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, Paint().apply { strokeWidth = 1.5f; color = Color.BLACK })
        y += 60f

        // --- Summary Table ---
        val summaryYStart = y
        val summaryRowHeight = 25f
        val summaryPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.LTGRAY }
        
        val summaryData = listOf(
            (if (isAr) "Ø§Ø³Ù… Ø§Ù„Ù…Ø´Ø±ÙˆØ¹" else "Project Name") to projectName,
            (if (isAr) "Ø§Ù„ÙƒÙˆØ¯ Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù…" else "Design Code") to designCodeName,
            (if (isAr) "Ø§Ù„ØªØ§Ø±ÙŠØ®" else "Date") to getCurrentDate(),
            (if (isAr) "Ø§Ù„Ù…Ù‡Ù†Ø¯Ø³ Ø§Ù„Ù…ØµÙ…Ù…" else "Designed By") to "Civil EG Pro Engine"
        )

        summaryData.forEachIndexed { index, pair ->
            val rowY = summaryYStart + index * summaryRowHeight
            canvas.drawRect(MARGIN.toFloat(), rowY, (PAGE_WIDTH - MARGIN).toFloat(), rowY + summaryRowHeight, summaryPaint)
            
            bodyPaint.isFakeBoldText = true
            drawFormattedText(canvas, pair.first + ":", MARGIN + 10f, rowY + 17f, bodyPaint)
            bodyPaint.isFakeBoldText = false
            drawFormattedText(canvas, pair.second, MARGIN + 150f, rowY + 17f, bodyPaint)
        }
        y += (summaryData.size * summaryRowHeight) + 40f

        // Feasibility & Summary Box
        val boxTop = y
        val boxWidth = PAGE_WIDTH - 2 * MARGIN
        val boxHeight = 180f
        val splitX = MARGIN + boxWidth * 0.6f
        
        val boxPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRect(MARGIN.toFloat(), boxTop, (PAGE_WIDTH - MARGIN).toFloat(), boxTop + boxHeight, boxPaint)
        
        // --- Added Branding Footer ---
        val brandingFooterY = PAGE_HEIGHT - MARGIN.toFloat()
        canvas.drawLine(MARGIN.toFloat(), brandingFooterY - 40f, (PAGE_WIDTH - MARGIN).toFloat(), brandingFooterY - 40f, smallPaint)
        drawFormattedText(canvas, "Civil Engineer Pro - Structural Solutions", MARGIN.toFloat(), brandingFooterY - 20f, smallPaint)
        smallPaint.textAlign = Paint.Align.RIGHT
        drawFormattedText(canvas, "Signature: ________________", (PAGE_WIDTH - MARGIN).toFloat(), brandingFooterY - 20f, smallPaint)
        smallPaint.textAlign = Paint.Align.LEFT
        // -----------------------------
        canvas.drawLine(splitX, boxTop, splitX, boxTop + boxHeight, boxPaint)
        
        // Left Side of Box (General Notes)
        val notesX = MARGIN + 15f
        var notesY = boxTop + 25f
        val blueTitlePaint = Paint(subtitlePaint).apply { color = Color.parseColor("#1565C0"); textSize = 13f }
        drawFormattedText(canvas, if (isAr) "Ù…Ù„Ø§Ø­Ø¸Ø§Øª Ø¹Ø§Ù…Ø©" else "GENERAL NOTES", notesX, notesY, blueTitlePaint)
        notesY += 20f
        val notes = if (isAr) listOf(
            "1. Ø¬Ù…ÙŠØ¹ Ø§Ù„Ø£Ø¨Ø¹Ø§Ø¯ Ø¨Ø§Ù„Ù…ØªØ± Ù…Ø§ Ù„Ù… ÙŠØ°ÙƒØ± Ø®Ù„Ø§Ù Ø°Ù„Ùƒ.",
            "2. Ø®ÙˆØ§Øµ Ø§Ù„Ù…ÙˆØ§Ø¯ Ø·Ø¨Ù‚Ø§Ù‹ Ù„Ù„Ù…ÙˆØ§ØµÙØ§Øª Ø§Ù„Ù‚ÙŠØ§Ø³ÙŠØ© Ø§Ù„Ù…Ø¹ØªÙ…Ø¯Ø©.",
            "3. ØªÙ… Ø¥Ø¬Ø±Ø§Ø¡ Ø§Ù„Ø­Ø³Ø§Ø¨Ø§Øª Ø¨Ø§Ø³ØªØ®Ø¯Ø§Ù… Ø·Ø±ÙŠÙ‚Ø© Ø­Ø§Ù„Ø§Øª Ø§Ù„Ø­Ø¯ÙˆØ¯.",
            "4. ÙŠØ¬Ø¨ Ø§Ù„Ø§Ù„ØªØ²Ø§Ù… Ø¨Ø§Ù„ØªÙ†ÙÙŠØ° Ø·Ø¨Ù‚Ø§Ù‹ Ù„Ù„Ø±Ø³ÙˆÙ…Ø§Øª Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠØ©.",
            "5. ÙŠØ¬Ø¨ Ø§Ù„ØªØ£ÙƒØ¯ Ù…Ù† Ù‚Ø¯Ø±Ø© ØªØ­Ù…Ù„ Ø§Ù„ØªØ±Ø¨Ø© ÙÙŠ Ø§Ù„Ù…ÙˆÙ‚Ø¹.",
            "6. Ø±ØªØ¨Ø© Ø§Ù„Ø®Ø±Ø³Ø§Ù†Ø©: C25/30 Ø£Ùˆ Ø­Ø³Ø¨ Ø§Ù„Ù†ØªØ§Ø¦Ø¬ Ø§Ù„Ù…Ø°ÙƒÙˆØ±Ø©."
        ) else listOf(
            "1. All dimensions are in METERS unless otherwise noted.",
            "2. Material properties as per specified code standards.",
            "3. Calculations performed using limit state design methods.",
            "4. Construction must follow structural drawings and specs.",
            "5. Soil bearing capacity must be verified on site.",
            "6. Concrete Grade: C25/30 or as specified in results."
        )
        bodyPaint.textSize = 9f
        notes.forEach { note ->
            drawFormattedText(canvas, note, notesX, notesY, bodyPaint)
            notesY += 15f
        }
        
        // Right Side of Box (Summary)
        val summaryX = splitX + 15f
        var summaryY = boxTop + 25f
        drawFormattedText(canvas, if (isAr) "Ù…Ù„Ø®Øµ Ø§Ù„ØªÙƒÙ„ÙØ© Ø§Ù„ØªÙ‚Ø¯ÙŠØ±ÙŠØ©" else "COST ESTIMATE SUMMARY", summaryX, summaryY, blueTitlePaint)
        summaryY += 40f
        
        val currency = settingsManager.currency
        drawFormattedText(canvas, if (isAr) "Ø§Ù„Ø¹Ù…Ù„Ø© Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù…Ø©:" else "Selected Currency:", summaryX, summaryY, bodyPaint)
        drawFormattedText(canvas, currency, summaryX + 120f, summaryY, bodyPaint.apply { isFakeBoldText = true })
        bodyPaint.isFakeBoldText = false
        summaryY += 25f
        
        drawFormattedText(canvas, if (isAr) "Ø³Ø¹Ø± Ø§Ù„Ø®Ø±Ø³Ø§Ù†Ø© / Ù…Â³:" else "Concrete Price / mÂ³:", summaryX, summaryY, bodyPaint)
        drawFormattedText(canvas, "${settingsManager.concretePrice} $currency", summaryX + 120f, summaryY, bodyPaint)
        summaryY += 25f

        drawFormattedText(canvas, if (isAr) "Ø³Ø¹Ø± Ø§Ù„Ø­Ø¯ÙŠØ¯ / Ø·Ù†:" else "Steel Price / Ton:", summaryX, summaryY, bodyPaint)
        drawFormattedText(canvas, "${settingsManager.steelPrice} $currency", summaryX + 120f, summaryY, bodyPaint)
        
        y = boxTop + boxHeight + 60f
        
        // Table Header
        val tableTop = y
        val colWidths = floatArrayOf(80f, 180f, 220f, 150f)
        var currentXStart = MARGIN.toFloat()
        val headerColor = Color.parseColor("#455A64")
        val headerTextPaint = Paint(bodyPaint).apply { color = Color.WHITE; isFakeBoldText = true; textSize = 10f }
        
        val headerLabels = listOf("MARK", "MEMBER", "SECTION TYPE", "MATERIAL")
        canvas.drawRect(MARGIN.toFloat(), tableTop, (PAGE_WIDTH - MARGIN).toFloat(), tableTop + 25f, Paint().apply { color = headerColor })
        
        headerLabels.forEachIndexed { i, label ->
            drawFormattedText(canvas, label, currentXStart + 10f, tableTop + 17f, headerTextPaint)
            currentXStart += colWidths[i]
        }
        
        // Placeholder Row
        y = tableTop + 25f
        canvas.drawRect(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y + 20f, boxPaint)
        drawFormattedText(canvas, "R1", MARGIN + 10f, y + 14f, bodyPaint)
        drawFormattedText(canvas, "Structural Element", MARGIN + 80f + 10f, y + 14f, bodyPaint)
        
        // Footer Title Block
        val footerY = PAGE_HEIGHT - MARGIN - 60f
        canvas.drawRect(MARGIN.toFloat(), footerY, (PAGE_WIDTH - MARGIN).toFloat(), footerY + 50f, boxPaint)
        canvas.drawLine(MARGIN + 250f, footerY, MARGIN + 250f, footerY + 50f, boxPaint)
        canvas.drawLine(MARGIN + 400f, footerY, MARGIN + 400f, footerY + 50f, boxPaint)
        canvas.drawLine(MARGIN + 550f, footerY, MARGIN + 550f, footerY + 50f, boxPaint)
        
        val smallLabelPaint = Paint(bodyPaint).apply { textSize = 7f; isFakeBoldText = true }
        drawFormattedText(canvas, "PROJECT:", MARGIN + 5f, footerY + 12f, smallLabelPaint)
        drawFormattedText(canvas, projectName, MARGIN + 5f, footerY + 28f, bodyPaint.apply { textSize = 10f; isFakeBoldText = true })
        
        drawFormattedText(canvas, "DESIGNED BY:", MARGIN + 255f, footerY + 12f, smallLabelPaint)
        drawFormattedText(canvas, "Civil EG Pro Engine", MARGIN + 255f, footerY + 28f, bodyPaint)
        
        drawFormattedText(canvas, "DESIGN CODE:", MARGIN + 405f, footerY + 12f, smallLabelPaint)
        drawFormattedText(canvas, designCodeName, MARGIN + 405f, footerY + 28f, bodyPaint)
        
        drawFormattedText(canvas, "DATE:", MARGIN + 555f, footerY + 12f, smallLabelPaint)
        drawFormattedText(canvas, getCurrentDate(), MARGIN + 555f, footerY + 28f, bodyPaint)
    }

    fun exportSteelReport(
        projectName: String,
        designCode: DesignCode,
        sectionType: SteelSectionType,
        memberType: SteelMemberType,
        inputs: SteelInputs,
        result: SteelMemberResult,
        connectionDesign: ConnectionDesignResult?,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, getString("تصميم المنشآت المعدنية", "Steel Structure Design")) }
            drawPage(document, pageNum++) { drawSteelInputs(it, sectionType, memberType, inputs) }
            drawPage(document, pageNum++) { drawSteelResults(it, result) }
            
            drawPage(document, pageNum++) { drawSteelSectionCatalog(it, designCode) } // NEW: Catalog
            drawPage(document, pageNum++) { drawSteelConnectionsCatalog(it) } // NEW: Bolts & Welds Catalog
            drawPage(document, pageNum++) { drawSteelSectionPage(it, sectionType) }
            
            if (memberType == SteelMemberType.TRUSS_MEMBER) {
                drawPage(document, pageNum++) { drawTrussSchematic(it, inputs) }
            }
            
            drawPage(document, pageNum++) { drawSteelAnalysisDiagrams(it, inputs, result) }

            drawPage(document, pageNum++) { drawSteelCriticalZones(it, inputs, result) } // NEW: Critical Zones

            if (connectionDesign != null) {
                drawPage(document, pageNum++) { drawConnectionDetails(it, connectionDesign) }
                drawPage(document, pageNum++) { drawBasePlateDetail(it) }
            }

            drawPage(document, pageNum++) { drawSteelBOQ(it, result, inputs, sectionType) }
            drawPage(document, pageNum++) { drawSteelCalculations(it, result) }
            drawPage(document, pageNum++) { drawProfessionalEngineeringTips(it, "STEEL") }
            drawPage(document, pageNum) { drawCodeReferencesAndNotes(it, result.codeNotes, result.warnings) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    private fun drawSteelSectionCatalog(canvas: Canvas, code: DesignCode) {
        currentY = MARGIN.toFloat() + 20f
        val title = getString("📚 قاموس القطاعات المعدنية القياسية", "📚 Standard Steel Sections Catalog")
        drawFormattedText(canvas, title, if (isAr()) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, titlePaint)
        currentY += 60f

        val codeType = when(code) {
            DesignCode.ECP -> SteelDictionary.DesignCodeType.ECP
            DesignCode.ACI -> SteelDictionary.DesignCodeType.ACI
            DesignCode.SBC -> SteelDictionary.DesignCodeType.SBC
        }
        
        val sections = SteelDictionary.getAllSectionsByCode(codeType)
        
        val tableTop = currentY
        val colWidths = floatArrayOf(150f, 200f, 80f, 80f)
        
        // Header
        val headerColor = Color.parseColor("#1565C0")
        canvas.drawRect(MARGIN.toFloat(), tableTop, (PAGE_WIDTH - MARGIN).toFloat(), tableTop + 30f, Paint().apply { color = headerColor })
        val hPaint = Paint(bodyPaint).apply { color = Color.WHITE; isFakeBoldText = true; textSize = 10f }
        
        if (isAr()) {
            var x = (PAGE_WIDTH - MARGIN).toFloat()
            drawFormattedText(canvas, "القطاع", x - 5f, tableTop + 22f, hPaint); x -= colWidths[0]
            drawFormattedText(canvas, "الأبعاد (mm)", x - 5f, tableTop + 22f, hPaint); x -= colWidths[1]
            drawFormattedText(canvas, "المساحة", x - 5f, tableTop + 22f, hPaint); x -= colWidths[2]
            drawFormattedText(canvas, "الوزن", x - 5f, tableTop + 22f, hPaint)
        } else {
            var x = MARGIN.toFloat()
            drawFormattedText(canvas, "Section", x + 5f, tableTop + 22f, hPaint); x += colWidths[0]
            drawFormattedText(canvas, "Dimensions", x + 5f, tableTop + 22f, hPaint); x += colWidths[1]
            drawFormattedText(canvas, "Area", x + 5f, tableTop + 22f, hPaint); x += colWidths[2]
            drawFormattedText(canvas, "Wt (kg/m)", x + 5f, tableTop + 22f, hPaint)
        }
        
        currentY += 40f
        sections.take(20).forEach { section ->
            if (currentY > PAGE_HEIGHT - MARGIN - 50f) return@forEach // Simple break for catalog size
            
            canvas.drawLine(MARGIN.toFloat(), currentY + 20f, (PAGE_WIDTH - MARGIN).toFloat(), currentY + 20f, Paint().apply { color = Color.LTGRAY })
            
            if (isAr()) {
                var x = (PAGE_WIDTH - MARGIN).toFloat()
                drawFormattedText(canvas, section.name, x - 5f, currentY + 15f, bodyPaint); x -= colWidths[0]
                drawFormattedText(canvas, section.dimensions, x - 5f, currentY + 15f, bodyPaint); x -= colWidths[1]
                val area = (section.weightKgM / 7850.0) * 1e6
                drawFormattedText(canvas, "%.0f".format(area), x - 5f, currentY + 15f, bodyPaint); x -= colWidths[2]
                drawFormattedText(canvas, section.weightKgM.toString(), x - 5f, currentY + 15f, bodyPaint)
            } else {
                var x = MARGIN.toFloat()
                drawFormattedText(canvas, section.name, x + 5f, currentY + 15f, bodyPaint); x += colWidths[0]
                drawFormattedText(canvas, section.dimensions, x + 5f, currentY + 15f, bodyPaint); x += colWidths[1]
                val area = (section.weightKgM / 7850.0) * 1e6
                drawFormattedText(canvas, "%.0f".format(area), x + 5f, currentY + 15f, bodyPaint); x += colWidths[2]
                drawFormattedText(canvas, section.weightKgM.toString(), x + 5f, currentY + 15f, bodyPaint)
            }
            currentY += 30f
        }
    }

    private fun drawSteelConnectionsCatalog(canvas: Canvas) {
        currentY = MARGIN.toFloat() + 20f
        val title = getString("📚 قاموس المسامير واللحامات", "📚 Bolts & Welds Technical Dictionary")
        drawFormattedText(canvas, title, if (isAr()) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, titlePaint)
        currentY += 60f

        // 1. Bolts Section
        drawFormattedText(canvas, getString("🔩 مواصفات البراغي (Bolts):", "🔩 Bolting Specifications:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 30f
        
        val boltData = SteelDictionary.boltData
        boltData.entries.take(5).forEach { (key, data) ->
            val desc = if (isAr()) "قطر: ${data["d"]}, مساحة: ${data["An"]}" else "Dia: ${data["d"]}, Area: ${data["An"]}"
            drawInfoRow(canvas, currentY, "Grade $key", desc)
            currentY += 25f
        }

        currentY += 20f
        
        // 2. Welding Section
        drawFormattedText(canvas, getString("👨‍🏭 مواصفات اللحام (Welding):", "👨‍🏭 Welding Specifications:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 30f
        
        val weldData = SteelDictionary.weldingData
        weldData.forEach { (key, data) ->
            val value = if (isAr()) (data["ar"] ?: "") else (data["en"] ?: "")
            drawInfoRow(canvas, currentY, key, value)
            currentY += 25f
        }
    }

    private fun drawSteelAnalysisDiagrams(canvas: Canvas, inputs: SteelInputs, result: SteelMemberResult) {
        currentY = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, getString("📈 التحليل الإنشائي والقوى الداخلية", "📈 Structural Analysis & Internal Forces"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 80f
        
        val diagW = PAGE_WIDTH - 2 * MARGIN
        val diagH = 100f
        
        // 1. Bending Moment Diagram
        drawFormattedText(canvas, getString("توزيع عزم الانحناء (kN.m)", "Bending Moment Diagram (kN.m)"), MARGIN.toFloat(), currentY - 10f, subtitlePaint)
        drawDiagramContainer(canvas, MARGIN.toFloat(), currentY, diagW.toFloat(), diagH)
        val mPath = Path()
        mPath.moveTo(MARGIN.toFloat(), currentY + diagH/2)
        mPath.quadTo(MARGIN + diagW/2f, currentY + diagH + 40f, MARGIN + diagW.toFloat(), currentY + diagH/2)
        canvas.drawPath(mPath, Paint().apply { color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 3f })
        drawFormattedText(canvas, "M_max = ${inputs.moment} kN.m", MARGIN.toFloat() + 10f, currentY + diagH + 15f, smallPaint)
        
        currentY += diagH + 60f
        
        // 2. Shear Force Diagram
        drawFormattedText(canvas, getString("قوى القص (kN)", "Shear Force Diagram (kN)"), MARGIN.toFloat(), currentY - 10f, subtitlePaint)
        drawDiagramContainer(canvas, MARGIN.toFloat(), currentY, diagW.toFloat(), diagH)
        val sPath = Path()
        val midY = currentY + diagH/2
        sPath.moveTo(MARGIN.toFloat(), midY - 30f)
        sPath.lineTo(MARGIN + diagW/2f, midY - 30f)
        sPath.lineTo(MARGIN + diagW/2f, midY + 30f)
        sPath.lineTo(MARGIN + diagW.toFloat(), midY + 30f)
        canvas.drawPath(sPath, Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f })
        drawFormattedText(canvas, "V_max = ${inputs.shear} kN", MARGIN.toFloat() + 10f, currentY + diagH + 15f, smallPaint)

        currentY += diagH + 60f
        
        // 3. Axial Load Indicator
        drawFormattedText(canvas, getString("تأثير الأحمال المحورية (kN)", "Axial Load Analysis (kN)"), MARGIN.toFloat(), currentY - 10f, subtitlePaint)
        val barMaxW = diagW - 100f
        val axialRatio = (inputs.axialLoad / result.axialCapacity.coerceAtLeast(1.0)).coerceIn(0.1, 1.0).toFloat()
        
        // Background bar
        canvas.drawRect(MARGIN.toFloat(), currentY, MARGIN + barMaxW, currentY + 25f, Paint().apply { color = Color.LTGRAY })
        // Active bar
        canvas.drawRect(MARGIN.toFloat(), currentY, MARGIN + barMaxW * axialRatio, currentY + 25f, Paint().apply { color = if(axialRatio > 0.9) Color.RED else Color.parseColor("#4CAF50") })
        
        drawFormattedText(canvas, "P_ult = ${inputs.axialLoad} kN", MARGIN + barMaxW + 10f, currentY + 18f, bodyPaint)
        drawFormattedText(canvas, "Capacity = ${"%.1f".format(result.axialCapacity)} kN", MARGIN.toFloat(), currentY + 45f, smallPaint)
    }

    private fun drawSteelCriticalZones(canvas: Canvas, inputs: SteelInputs, result: SteelMemberResult) {
        currentY = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, getString("🔍 تحليل مناطق الإجهادات الحرجة", "🔍 Critical Stress Zones Analysis"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 60f

        val boxW = PAGE_WIDTH - 2 * MARGIN
        val boxH = 150f
        
        // Background for zones
        canvas.drawRect(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY + boxH, Paint().apply { color = Color.parseColor("#F9F9F9") })
        canvas.drawRect(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY + boxH, Paint().apply { style = Paint.Style.STROKE; color = Color.BLACK })
        
        val zoneY = currentY + 30f
        val zonePaint = Paint(bodyPaint).apply { isFakeBoldText = true }
        
        // 1. Strong Load Areas (Max Utilization)
        drawFormattedText(canvas, getString("🔥 مناطق الأحمال القوية (حرجة):", "🔥 High Load Zones (Critical):"), MARGIN + 20f, zoneY, zonePaint)
        val highLoadMsg = if (result.utilizationRatio > 0.8) 
            getString("منتصف العضو / مناطق الاتصال (إجهاد عالٍ)", "Mid-span / Connection points (High Stress)")
            else getString("لا يوجد مناطق حرجة حالياً", "No critical zones detected for current load")
        drawFormattedText(canvas, highLoadMsg, MARGIN + 40f, zoneY + 25f, bodyPaint)
        
        // 2. Weak Load Areas (Reserve Capacity)
        drawFormattedText(canvas, getString("❄️ مناطق الأحمال الضعيفة (آمنة):", "❄️ Low Load Zones (High Reserve):"), MARGIN + 20f, zoneY + 65f, zonePaint)
        val lowLoadMsg = getString("المناطق الطرفية / نقاط الانقلاب", "End sections / Inflection points")
        drawFormattedText(canvas, lowLoadMsg, MARGIN + 40f, zoneY + 90f, bodyPaint)
        
        currentY += boxH + 40f
        
        // Table for weights breakdown
        drawFormattedText(canvas, getString("⚖️ تفاصيل الأوزان والأحمال:", "⚖️ Weight & Loading Breakdown:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 30f
        drawInfoRow(canvas, currentY, getString("وزن المتر الطولي", "Weight per Meter"), "%.2f kg/m".format(result.weight))
        drawInfoRow(canvas, currentY + 25f, getString("إجمالي وزن العضو", "Total Member Weight"), "%.2f kg".format(result.weight * inputs.length / 1000.0))
        drawInfoRow(canvas, currentY + 50f, getString("الحمل المحوري الأقصى", "Max Axial Capacity"), "%.1f kN".format(result.axialCapacity))
    }


    private fun drawSteelMemberSchedule(canvas: Canvas, result: CalculatorEngine.SteelWarehouseResult) {
        var y = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, "ðŸ“‹ STEEL MEMBER SCHEDULE", MARGIN.toFloat(), y, titlePaint); y += 60f
        
        val headerColor = Color.parseColor("#455A64")
        val paint = Paint().apply { color = headerColor; style = Paint.Style.FILL }
        canvas.drawRect(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y + 30f, paint)
        
        val textPaint = Paint().apply { color = Color.WHITE; textSize = 14f; isFakeBoldText = true }
        drawFormattedText(canvas, "MARK", MARGIN + 10f, y + 20f, textPaint)
        drawFormattedText(canvas, "MEMBER", MARGIN + 100f, y + 20f, textPaint)
        drawFormattedText(canvas, "SECTION", MARGIN + 250f, y + 20f, textPaint)
        drawFormattedText(canvas, "MATERIAL", MARGIN + 400f, y + 20f, textPaint)
        
        y += 30f
        val bodyPaint = Paint().apply { color = Color.BLACK; textSize = 12f }
        val members = listOf(
            Triple("C1", "COLUMN", result.columnSection),
            Triple("R1", "RAFTER", result.rafterSection),
            Triple("P1", "PURLIN", result.purlinSection),
            Triple("B1", "BOLTS", result.boltType)
        )
        
        members.forEach { (mark, type, section) ->
            canvas.drawLine(MARGIN.toFloat(), y + 30f, (PAGE_WIDTH - MARGIN).toFloat(), y + 30f, Paint().apply { color = Color.LTGRAY })
            drawFormattedText(canvas, mark, MARGIN + 10f, y + 20f, bodyPaint)
            drawFormattedText(canvas, type, MARGIN + 100f, y + 20f, bodyPaint)
            drawFormattedText(canvas, section, MARGIN + 250f, y + 20f, bodyPaint)
            drawFormattedText(canvas, "ASTM A36 / St-37", MARGIN + 400f, y + 20f, bodyPaint)
            y += 30f
        }
    }

    private fun drawWarehouseGeneralArrangement(canvas: Canvas, result: CalculatorEngine.SteelWarehouseResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ—ï¸ Ø§Ù„Ù…Ø®Ø·Ø· Ø§Ù„Ø¹Ø§Ù… Ù„Ù„Ù…Ù†Ø´Ø£ Ø§Ù„Ù…Ø¹Ø¯Ù†ÙŠ (GA)" else "ðŸ—ï¸ Steel Warehouse General Arrangement"
        drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint); y += 60f

        val cx = PAGE_WIDTH / 2f
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.5f }
        val blueColPaint = Paint().apply { color = Color.BLUE; style = Paint.Style.FILL }
        
        // --- 1. PLAN VIEW ---
        drawFormattedText(canvas, "PLAN VIEW", MARGIN.toFloat(), y, subtitlePaint); y += 30f
        val planW = PAGE_WIDTH - 2 * MARGIN
        val planH = 120f
        canvas.drawRect(MARGIN.toFloat(), y, MARGIN.toFloat() + planW, y + planH, paint)
        
        val numBays = (result.length / result.spacing).toInt().coerceAtLeast(1)
        val bayPixelWidth = planW / numBays
        
        for (i in 0..numBays) {
            val gx = MARGIN + i * bayPixelWidth
            canvas.drawRect(gx - 5f, y - 5f, gx + 5f, y + 5f, blueColPaint)
            canvas.drawRect(gx - 5f, y + planH - 5f, gx + 5f, y + planH + 5f, blueColPaint)
        }
        // Bracing
        canvas.drawLine(MARGIN.toFloat(), y, MARGIN.toFloat() + bayPixelWidth, y + planH, paint)
        canvas.drawLine(MARGIN.toFloat() + bayPixelWidth, y, MARGIN.toFloat(), y + planH, paint)
        
        drawFormattedText(canvas, "${result.span}m", cx, y + planH + 20f, smallPaint)
        y += planH + 60f

        // --- 2. FRONT ELEVATION ---
        drawFormattedText(canvas, "FRONT ELEVATION", MARGIN.toFloat(), y, subtitlePaint); y += 30f
        val elevSpan = 300f
        val hCol = 100f
        val hRidge = 40f
        val ex = cx - elevSpan/2
        
        canvas.drawLine(ex - 20f, y + hCol, ex + elevSpan + 20f, y + hCol, paint) // Ground
        canvas.drawLine(ex, y + hCol, ex, y, paint) // Left Col
        canvas.drawLine(ex + elevSpan, y + hCol, ex + elevSpan, y, paint) // Right Col
        
        val path = Path().apply {
            moveTo(ex, y)
            lineTo(cx, y - hRidge)
            lineTo(ex + elevSpan, y)
        }
        canvas.drawPath(path, paint)
        
        drawFormattedText(canvas, "Eave: +${result.eaveHeight}m", ex + elevSpan + 10f, y, smallPaint)
        y += hCol + 60f

        // --- 3. CROSS SECTION A-A ---
        drawFormattedText(canvas, "CROSS SECTION A-A", MARGIN.toFloat(), y, subtitlePaint); y += 40f
        canvas.drawLine(ex + 20f, y + 80f, ex + 20f, y, paint)
        canvas.drawLine(ex + elevSpan - 20f, y + 80f, ex + elevSpan - 20f, y, paint)
        val secPath = Path().apply {
            moveTo(ex + 20f, y)
            lineTo(cx, y - 30f)
            lineTo(ex + elevSpan - 20f, y)
        }
        canvas.drawPath(secPath, paint)
        
        drawFormattedText(canvas, "R1 (${result.rafterSection})", cx, y - 45f, smallPaint.apply { textAlign = Paint.Align.CENTER })
        drawFormattedText(canvas, "C1 (${result.columnSection})", ex, y + 40f, smallPaint.apply { textAlign = Paint.Align.LEFT })
        
        // Base Plate & Bolt
        canvas.drawRect(ex + 15f, y + 75f, ex + 25f, y + 85f, paint)
        canvas.drawCircle(ex + 20f, y + 80f, 2f, paint)
        drawFormattedText(canvas, "Bolt: ${result.boltType}", ex + 35f, y + 85f, smallPaint)

        // --- 4. LOAD DIAGRAM ---
        val lx = PAGE_WIDTH - MARGIN - 100f
        val ly = y
        val arrowPaint = Paint().apply { color = Color.RED; strokeWidth = 2f }
        canvas.drawLine(lx, ly, lx + 60f, ly, arrowPaint)
        canvas.drawLine(lx + 50f, ly - 5f, lx + 60f, ly, arrowPaint)
        canvas.drawLine(lx + 50f, ly + 5f, lx + 60f, ly, arrowPaint)
        drawFormattedText(canvas, "W = Load", lx, ly - 10f, smallPaint)
    }

    private fun drawBasePlateDetail(canvas: Canvas) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ ØªÙØ§ØµÙŠÙ„ Ù‚Ø§Ø¹Ø¯Ø© Ø§Ù„Ø¹Ù…ÙˆØ¯ (Base Plate)" else "ðŸ“ Column Base Plate Detail"
        drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint); y += 60f
        
        val cx = PAGE_WIDTH / 2f
        val bpW = 150f
        val bpH = 150f
        
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        
        // Draw Base Plate
        canvas.drawRect(cx - bpW/2, y, cx + bpW/2, y + bpH, paint)
        
        // Draw Bolts
        val boltRadius = 6f
        val offset = 25f
        canvas.drawCircle(cx - bpW/2 + offset, y + offset, boltRadius, Paint().apply { style = Paint.Style.FILL })
        canvas.drawCircle(cx + bpW/2 - offset, y + offset, boltRadius, Paint().apply { style = Paint.Style.FILL })
        canvas.drawCircle(cx - bpW/2 + offset, y + bpH - offset, boltRadius, Paint().apply { style = Paint.Style.FILL })
        canvas.drawCircle(cx + bpW/2 - offset, y + bpH - offset, boltRadius, Paint().apply { style = Paint.Style.FILL })
        
        // Draw Column Profile (I-Section)
        val iw = 60f
        val ih = 80f
        val tf = 8f
        val tw = 6f
        val colY = y + bpH/2
        canvas.drawRect(cx - iw/2, colY - ih/2, cx + iw/2, colY - ih/2 + tf, Paint().apply { style = Paint.Style.FILL; color = Color.GRAY })
        canvas.drawRect(cx - iw/2, colY + ih/2 - tf, cx + iw/2, colY + ih/2, Paint().apply { style = Paint.Style.FILL; color = Color.GRAY })
        canvas.drawRect(cx - tw/2, colY - ih/2 + tf, cx + tw/2, colY + ih/2 - tf, Paint().apply { style = Paint.Style.FILL; color = Color.GRAY })

        y += bpH + 40f
        drawFormattedText(canvas, if (isAr) "Ù…ÙˆØ§ØµÙØ§Øª Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø©:" else "Base Plate Specs:", MARGIN.toFloat(), y, subtitlePaint); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ù…Ù‚Ø§Ø³ Ø§Ù„Ù„ÙˆØ­Ø©" else "Plate Size", "400x400x20 mm"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ø¹Ø¯Ø¯ Ø§Ù„Ù…Ø³Ø§Ù…ÙŠØ±" else "Anchor Bolts", "4 x M24 Grade 8.8")
    }

    private fun drawProfessionalEngineeringTips(canvas: Canvas, module: String) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ’¡ Ù†ØµØ§Ø¦Ø­ Ù‡Ù†Ø¯Ø³ÙŠØ© Ø§Ø­ØªØ±Ø§ÙÙŠØ©" else "ðŸ’¡ Professional Engineering Tips"
        drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint); y += 50f
        
        val tips = when(module) {
            "STEEL" -> if (isAr) listOf(
                "â€¢ ØªØ£ÙƒØ¯ Ù…Ù† ÙƒÙØ§ÙŠØ© Ø§Ù„ØªØ±Ø¨ÙŠØ· (Bracing) Ù„Ù…Ù†Ø¹ Ø§Ù„Ø§Ù†Ø¨Ø¹Ø§Ø¬ Ø§Ù„Ø¬Ø§Ù†Ø¨ÙŠ.",
                "â€¢ ÙŠÙØ¶Ù„ Ø§Ø³ØªØ®Ø¯Ø§Ù… ÙˆØµÙ„Ø§Øª Ø§Ù„Ø¨Ø±Ø§ØºÙŠ (Bolted) Ù„ØªØ³Ù‡ÙŠÙ„ Ø§Ù„ØªØ±ÙƒÙŠØ¨ ÙÙŠ Ø§Ù„Ù…ÙˆÙ‚Ø¹.",
                "â€¢ ÙŠØ¬Ø¨ Ø­Ù…Ø§ÙŠØ© Ø§Ù„Ù…Ù†Ø´Ø£ Ù…Ù† Ø§Ù„ØµØ¯Ø£ Ø¨Ø§Ø³ØªØ®Ø¯Ø§Ù… Ø¯Ù‡Ø§Ù†Ø§Øª Ù…Ù‚Ø§ÙˆÙ…Ø© Ù„Ù„Ø­Ø±ÙŠÙ‚ ÙˆØ§Ù„ØªØ¢ÙƒÙ„."
            ) else listOf(
                "â€¢ Ensure adequate bracing to prevent lateral torsional buckling.",
                "â€¢ Prefer bolted connections for faster and easier site assembly.",
                "â€¢ Protect the structure with fire-rated and anti-corrosion coatings."
            )
            "COLUMN" -> if (isAr) listOf(
                "â€¢ ØªØ£ÙƒØ¯ Ù…Ù† Ø§Ø³ØªÙ…Ø±Ø§Ø±ÙŠØ© Ø§Ù„ÙƒØ§Ù†Ø§Øª Ø¯Ø§Ø®Ù„ Ù…Ù†Ø·Ù‚Ø© Ø§ØªØµØ§Ù„ Ø§Ù„Ø¹Ù…ÙˆØ¯ Ø¨Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§.",
                "â€¢ ÙŠÙØ¶Ù„ ØªÙˆØ²ÙŠØ¹ Ø§Ù„Ø£Ø³ÙŠØ§Ø® Ø¨Ø§Ù†ØªØ¸Ø§Ù… Ù„Ø¶Ù…Ø§Ù† Ù…Ù‚Ø§ÙˆÙ…Ø© Ø§Ù„Ø¹Ø²ÙˆÙ… ÙÙŠ ÙƒØ§ÙØ© Ø§Ù„Ø§ØªØ¬Ø§Ù‡Ø§Øª.",
                "â€¢ ÙŠØ¬Ø¨ Ø£Ù„Ø§ ØªÙ‚Ù„ Ù†Ø³Ø¨Ø© Ø§Ù„ØªØ³Ù„ÙŠØ­ Ø¹Ù† Ø§Ù„Ø­Ø¯ Ø§Ù„Ø£Ø¯Ù†Ù‰ Ø·Ø¨Ù‚Ø§Ù‹ Ù„Ù„ÙƒÙˆØ¯ (0.8%)."
            ) else listOf(
                "â€¢ Ensure stirrups continue through the beam-column joint area.",
                "â€¢ Distribute bars uniformly for better multi-directional moment resistance.",
                "â€¢ Reinforcement ratio should not fall below code minimum (0.8%)."
            )
            "BEAM" -> if (isAr) listOf(
                "â€¢ ÙŠÙˆØµÙ‰ Ø¨Ø§Ø³ØªØ®Ø¯Ø§Ù… Ø­Ø¯ÙŠØ¯ ØªØ¹Ù„ÙŠÙ‚ Ø§Ù„ÙƒØ§Ù†Ø§Øª Ø¨Ù‚Ø·Ø± Ù„Ø§ ÙŠÙ‚Ù„ Ø¹Ù† 10 Ù…Ù….",
                "â€¢ ØªØ£ÙƒØ¯ Ù…Ù† ØªÙˆÙÙŠØ± Ø·ÙˆÙ„ Ø§Ù„ØªÙ…Ø§Ø³Ùƒ Ø§Ù„ÙƒØ§ÙÙŠ Ø¹Ù†Ø¯ Ø§Ù„Ø±ÙƒØ§Ø¦Ø².",
                "â€¢ ÙÙŠ Ø§Ù„ÙƒØ§Ù…ÙŠØ±Ø§Øª Ø§Ù„Ø¹Ù…ÙŠÙ‚Ø© (Deep Beams)ØŒ ÙŠØ¬Ø¨ Ø¥Ø¶Ø§ÙØ© Ø­Ø¯ÙŠØ¯ Ø§Ù†ÙƒÙ…Ø§Ø´ Ø¬Ø§Ù†Ø¨ÙŠ."
            ) else listOf(
                "â€¢ Stirrup hangers should be at least 10mm in diameter.",
                "â€¢ Ensure adequate development length at supports.",
                "â€¢ For deep beams, side face reinforcement (shrinkage steel) is mandatory."
            )
            "SLAB" -> if (isAr) listOf(
                "â€¢ ØªØ£ÙƒØ¯ Ù…Ù† ØªÙƒØ«ÙŠÙ Ø§Ù„ØªØ³Ù„ÙŠØ­ Ø­ÙˆÙ„ Ø§Ù„ÙØªØ­Ø§Øª Ù„ØªÙ‚Ù„ÙŠÙ„ Ø§Ù„Ø´Ø±ÙˆØ®.",
                "â€¢ ÙÙŠ Ø§Ù„Ø¨Ù„Ø§Ø·Ø§Øª Ø§Ù„Ù…Ø³Ø·Ø­Ø© (Flat Slabs)ØŒ ØªØ­Ù‚Ù‚ Ù…Ù† Ù…Ù‚Ø§ÙˆÙ…Ø© Ø§Ù„Ø«Ù‚Ø¨ (Punching) Ø¨Ø¯Ù‚Ø©.",
                "â€¢ ÙŠÙØ¶Ù„ Ø§Ø³ØªØ®Ø¯Ø§Ù… ÙƒØ±Ø§Ø³ÙŠ Ø­Ø¯ÙŠØ¯ Ù„Ø¶Ù…Ø§Ù† ÙˆØ¶Ø¹ Ø§Ù„Ø±Ù‚Ø© Ø§Ù„Ø¹Ù„ÙˆÙŠØ© ÙÙŠ Ù…ÙƒØ§Ù†Ù‡Ø§ Ø§Ù„ØµØ­ÙŠØ­."
            ) else listOf(
                "â€¢ Reinforce around openings to minimize crack propagation.",
                "â€¢ In Flat Slabs, carefully verify punching shear capacity.",
                "â€¢ Use high-quality rebar chairs to maintain top mesh position during casting."
            )
            else -> emptyList()
        }
        
        tips.forEach { tip ->
            drawFormattedText(canvas, tip, MARGIN.toFloat(), y, bodyPaint)
            y += 30f
        }
        
        y += 40f
        drawFormattedText(canvas, if (isAr) "ØªÙ… Ø¥Ø¹Ø¯Ø§Ø¯ Ù‡Ø°Ø§ Ø§Ù„ØªÙ‚Ø±ÙŠØ± Ø¢Ù„ÙŠØ§Ù‹ Ø¨ÙˆØ§Ø³Ø·Ø© Ù…Ø­Ø±Ùƒ Civil EG" else "Report generated automatically by Civil EG Engine", PAGE_WIDTH/2f, y, smallPaint.apply { textAlign = Paint.Align.CENTER })
    }

    private fun drawSteelBOQ(canvas: Canvas, result: SteelMemberResult, inputs: SteelInputs, section: SteelSectionType) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ—ï¸ Ø¬Ø¯ÙˆÙ„ Ø§Ù„ÙƒÙ…ÙŠØ§Øª ÙˆØªÙ‚Ø¯ÙŠØ± Ø§Ù„ØªÙƒÙ„ÙØ©" else "ðŸ—ï¸ Bill of Quantities (BOQ)"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 50f
        
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 3f
        val bgPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        canvas.drawRect(MARGIN.toFloat(), y - 5f, (PAGE_WIDTH - MARGIN).toFloat(), y + 30f, bgPaint)
        
        val h1 = if (isAr) "Ø§Ù„ÙˆØµÙ" else "Description"
        val h2 = if (isAr) "Ø§Ù„ÙƒÙ…ÙŠØ©" else "Quantity"
        val h3 = if (isAr) "Ø§Ù„ØªÙƒÙ„ÙØ©" else "Estimated Cost"
        
        subtitlePaint.textSize = 12f
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, h1, (PAGE_WIDTH - MARGIN) - 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h2, (PAGE_WIDTH - MARGIN) - colWidth - 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h3, (PAGE_WIDTH - MARGIN) - 2 * colWidth - 10f, y + 20f, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, h1, MARGIN + 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h2, MARGIN + colWidth + 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h3, MARGIN + 2 * colWidth + 10f, y + 20f, subtitlePaint)
        }
        y += 50f
        
        // Weight calculation: Area (mm2) * Length (mm) * 7850 kg/m3 / 1e9
        val weightKg = (section.getArea() * inputs.length * 7.85e-6)
        val steelCost = (weightKg / 1000.0) * settingsManager.steelPrice
        val currency = settingsManager.currency
        
        val desc = if (isAr) "Ù‚Ø·Ø§Ø¹Ø§Øª ØµÙ„Ø¨ Ø¥Ù†Ø´Ø§Ø¦ÙŠ" else "Structural Steel Sections"
        
        drawBOQRow(canvas, y, desc, "${"%.2f".format(weightKg)} kg", "${"%.0f".format(steelCost)} $currency"); y += 30f
        
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, Paint().apply { color = Color.LTGRAY }); y += 40f
        
        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„ØªÙƒÙ„ÙØ© Ø§Ù„ØªÙ‚Ø¯ÙŠØ±ÙŠØ©" else "TOTAL ESTIMATED COST"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, totalLabel, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
            drawFormattedText(canvas, "${"%.2f".format(steelCost)} $currency", MARGIN.toFloat(), y, titlePaint)
        } else {
            drawFormattedText(canvas, totalLabel, MARGIN.toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, "${"%.2f".format(steelCost)} $currency", (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        }
        
        titlePaint.color = textColor
        y += 60f
        drawDisclaimer(canvas, y)
    }

    private fun drawTrussSchematic(canvas: Canvas, inputs: SteelInputs) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ—ï¸ Ø§Ù„Ù…Ø®Ø·Ø· Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠ Ù„Ù„Ø¬Ù…Ø§Ù„ÙˆÙ†" else "ðŸ—ï¸ Truss Structural Schematic"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 80f

        val trussW = PAGE_WIDTH - 2 * MARGIN - 100f
        val trussH = 120f
        val startX = MARGIN + 50f
        val startY = y + 50f
        
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }
        
        val panels = 6
        val panelW = trussW / panels
        
        // Draw Bottom Chord
        canvas.drawLine(startX, startY + trussH, startX + trussW, startY + trussH, paint)
        // Draw Top Chord
        canvas.drawLine(startX, startY, startX + trussW, startY, paint)
        
        // Draw Verticals and Diagonals (Warren Truss Pattern)
        for (i in 0..panels) {
            val px = startX + i * panelW
            canvas.drawLine(px, startY, px, startY + trussH, paint) // Vertical
            
            if (i < panels) {
                if (i % 2 == 0) {
                    canvas.drawLine(px, startY + trussH, px + panelW, startY, paint)
                } else {
                    canvas.drawLine(px, startY, px + panelW, startY + trussH, paint)
                }
            }
        }
        
        // Annotations
        val textPaint = Paint(bodyPaint).apply { textSize = 10f; color = Color.GRAY }
        drawFormattedText(canvas, if (isAr) "Ø§Ù„ÙˆØªØ± Ø§Ù„Ø¹Ù„ÙˆÙŠ (Top Chord)" else "Top Chord", startX + trussW/2, startY - 10f, textPaint.apply { textAlign = Paint.Align.CENTER })
        drawFormattedText(canvas, if (isAr) "Ø§Ù„ÙˆØªØ± Ø§Ù„Ø³ÙÙ„ÙŠ (Bottom Chord)" else "Bottom Chord", startX + trussW/2, startY + trussH + 20f, textPaint)
        
        y = startY + trussH + 80f
        val infoTitle = if (isAr) "Ø¨ÙŠØ§Ù†Ø§Øª Ø§Ù„Ø¬Ù…Ø§Ù„ÙˆÙ†:" else "Truss Data:"
        drawFormattedText(canvas, infoTitle, MARGIN.toFloat(), y, subtitlePaint); y += 30f
        drawInfoRow(canvas, y, if (isAr) "Ø·ÙˆÙ„ Ø§Ù„Ø¨Ø­Ø±" else "Span", "%.2f m".format(inputs.length / 1000.0)); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„Ø¬Ù…Ø§Ù„ÙˆÙ†" else "Truss Type", "Warren Pattern"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ø¹Ø¯Ø¯ Ø§Ù„Ø¨Ø§ÙƒÙŠØ§Øª" else "No. of Panels", panels.toString())
    }


    fun exportSeismicReport(
        projectName: String,
        designCode: CalculatorEngine.DesignCode,
        inputs: CalculatorEngine.SeismicInput,
        result: CalculatorEngine.SeismicResult,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, "Seismic Analysis Report") }
            drawPage(document, pageNum++) { drawSeismicInputs(it, inputs) }
            drawPage(document, pageNum++) { drawSeismicResults(it, result) }
            drawPage(document, pageNum++) { drawSeismicStoryForces(it, result) }
            drawPage(document, pageNum) { drawSeismicDiagram(it, result, inputs.height) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    private fun drawSeismicInputs(canvas: Canvas, inputs: CalculatorEngine.SeismicInput) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ù…Ø¯Ø®Ù„Ø§Øª Ø§Ù„Ø²Ù„Ø§Ø²Ù„" else "ðŸ“ Seismic Parameters"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f

        val labels = listOf(
            (if (isAr) "Ø¹Ø¬Ù„Ø© Ø§Ù„Ø²Ù„Ø²Ø§Ù„ Ø§Ù„ØªØµÙ…ÙŠÙ…ÙŠØ© (ag)" else "Design Ground Accel. (ag)") to "${inputs.zone}g",
            (if (isAr) "Ù…Ø¹Ø§Ù…Ù„ Ø§Ù„Ø£Ù‡Ù…ÙŠØ© (Î³I)" else "Importance Factor (Î³I)") to "${inputs.importance}",
            (if (isAr) "Ù†ÙˆØ¹ Ø§Ù„ØªØ±Ø¨Ø©" else "Soil Type") to inputs.soilType,
            (if (isAr) "Ù…Ø¹Ø§Ù…Ù„ ØªØ¹Ø¯ÙŠÙ„ Ø§Ù„Ø§Ø³ØªØ¬Ø§Ø¨Ø© (R)" else "Response Mod. Factor (R)") to "${inputs.reductionFactor}",
            (if (isAr) "Ø§Ù„Ø§Ø±ØªÙØ§Ø¹ Ø§Ù„ÙƒÙ„ÙŠ (H)" else "Total Height (H)") to "${inputs.height} m",
            (if (isAr) "Ø§Ù„ÙˆØ²Ù† Ø§Ù„ÙƒÙ„ÙŠ (W)" else "Total Weight (W)") to "${"%.1f".format(inputs.totalWeight)} kN"
        )

        labels.forEach { pair ->
            activeCanvas = checkNewPage(activeCanvas, 25f)
            drawInfoRow(activeCanvas, currentY, pair.first, pair.second)
            currentY += 25f
        }
    }

    private fun drawSeismicResults(canvas: Canvas, result: CalculatorEngine.SeismicResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“Š Ù†ØªØ§Ø¦Ø¬ Ø§Ù„ØªØ­Ù„ÙŠÙ„ Ø§Ù„Ø²Ù„Ø²Ø§Ù„ÙŠ" else "ðŸ“Š Analysis Results"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f

        drawStatusHeader(activeCanvas, currentY, result.isSafe); currentY += 40f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø¹Ø¬Ù„Ø© Ø§Ù„Ø·ÙŠÙ (Sa)" else "Spectral Accel. (Sa)", "%.3f g".format(result.spectralAcceleration)); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø²Ù…Ù† Ø§Ù„Ø¯ÙˆØ±ÙŠ (T)" else "Fundamental Period (T)", "%.3f s".format(result.timePeriod)); currentY += 25f
        
        titlePaint.color = primaryColor
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù‚Øµ Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø© (Vb)" else "BASE SHEAR (Vb)", "%.1f kN".format(result.baseShear)); currentY += 40f
        titlePaint.color = textColor
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø£Ù‚ØµÙ‰ Ø¥Ø²Ø§Ø­Ø© Ø·Ø§Ø¨Ù‚ÙŠØ©" else "Max Story Drift", "%.4f".format(result.storyDrift)); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "ÙƒÙˆØ¯ Ø§Ù„ØªØµÙ…ÙŠÙ…" else "Design Code", result.code.displayName)
    }

    private fun drawSeismicStoryForces(canvas: Canvas, result: CalculatorEngine.SeismicResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ¢ ØªÙˆØ²ÙŠØ¹ Ù‚ÙˆÙ‰ Ø§Ù„Ø£Ø¯ÙˆØ§Ø±" else "ðŸ¢ Story Force Distribution"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 50f
        
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 2f
        val bgPaint = Paint().apply { color = Color.parseColor("#E0E0E0") }
        activeCanvas.drawRect(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY + 30f, bgPaint)
        
        val h1 = if (isAr) "Ù…Ø³ØªÙˆÙ‰ Ø§Ù„Ø¯ÙˆØ±" else "Floor Level"
        val h2 = if (isAr) "Ø§Ù„Ù‚ÙˆØ© Ø§Ù„Ø£ÙÙ‚ÙŠØ© (Fi) [kN]" else "Lateral Force (Fi) [kN]"

        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, h1, (PAGE_WIDTH - MARGIN) - 10f, currentY + 22f, subtitlePaint)
            drawFormattedText(activeCanvas, h2, (PAGE_WIDTH - MARGIN) - colWidth - 10f, currentY + 22f, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, h1, MARGIN + 10f, currentY + 22f, subtitlePaint)
            drawFormattedText(activeCanvas, h2, MARGIN + colWidth + 10f, currentY + 22f, subtitlePaint)
        }
        currentY += 50f
        
        result.forcesPerFloor.toSortedMap(reverseOrder()).forEach { (level, force) ->
            activeCanvas = checkNewPage(activeCanvas, 30f)
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ø·Ø§Ø¨Ù‚ $level" else "Level $level", "%.2f kN".format(force))
            currentY += 30f
        }
    }

    private fun drawSeismicDiagram(canvas: Canvas, result: CalculatorEngine.SeismicResult, totalHeight: Double) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ˆ Ù…Ø®Ø·Ø· Ø§Ù„Ù‚ÙˆÙ‰ Ø§Ù„Ø¬Ø§Ù†Ø¨ÙŠØ© (Ø§Ù„Ø²Ù„Ø§Ø²Ù„)" else "ðŸ“ˆ Lateral Force Diagram"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 60f
        
        val diagramH = 400f // Increased height for better scaling
        val isRtl = isAr
        val startX = if (isRtl) (PAGE_WIDTH - 150f - 80f) else 150f
        val startY = y + diagramH
        
        val paint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
        
        // Dynamic scaling for height: if building > 50m, use a different visual scale representation
        val heightLabel = if (isAr) {
            if (totalHeight > 50.0) "Ø§Ù„Ø§Ø±ØªÙØ§Ø¹ Ø§Ù„Ø¥Ø¬Ù…Ø§Ù„ÙŠ = %.1f Ù… (Ù…Ù‚ÙŠØ§Ø³ Ù…ØµØºØ±)".format(totalHeight) else "Ø§Ù„Ø§Ø±ØªÙØ§Ø¹ Ø§Ù„Ø¥Ø¬Ù…Ø§Ù„ÙŠ = %.1f Ù…".format(totalHeight)
        } else {
            if (totalHeight > 50.0) "Total Height H = %.1f m (Scaled)".format(totalHeight) else "Total Height H = %.1f m".format(totalHeight)
        }
        
        // Draw Building Outline (Simplified)
        val buildingWidth = 80f
        canvas.drawRect(startX, y, startX + buildingWidth, startY, paint)
        
        // Floor lines
        val numFloors = result.forcesPerFloor.size
        for (i in 0..numFloors) {
            val floorLineY = startY - (i.toFloat() / numFloors) * diagramH
            canvas.drawLine(startX, floorLineY, startX + buildingWidth, floorLineY, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        }
        
        // Draw Lateral Forces
        val forcePaint = Paint().apply { color = Color.RED; strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true }
        val maxForce = result.forcesPerFloor.values.maxOrNull() ?: 1.0
        val forceScale = 120f / maxForce.toFloat() // Scale arrow lengths
        
        result.forcesPerFloor.forEach { (level, force) ->
            val floorY = startY - (level.toFloat() / numFloors) * diagramH
            if (isRtl) {
                val arrowEnd = startX - force.toFloat() * forceScale
                canvas.drawLine(startX, floorY, arrowEnd, floorY, forcePaint)
                // Arrow head (Pointing Left)
                canvas.drawLine(arrowEnd, floorY, arrowEnd + 8f, floorY - 4f, forcePaint)
                canvas.drawLine(arrowEnd, floorY, arrowEnd + 8f, floorY + 4f, forcePaint)
                
                val forceText = "F$level = %.1f kN".format(force)
                smallPaint.textAlign = Paint.Align.RIGHT
                drawFormattedText(canvas, forceText, arrowEnd - 5f, floorY + 4f, smallPaint)
            } else {
                val arrowEnd = startX + buildingWidth + force.toFloat() * forceScale
                canvas.drawLine(startX + buildingWidth, floorY, arrowEnd, floorY, forcePaint)
                // Arrow head (Pointing Right)
                canvas.drawLine(arrowEnd, floorY, arrowEnd - 8f, floorY - 4f, forcePaint)
                canvas.drawLine(arrowEnd, floorY, arrowEnd - 8f, floorY + 4f, forcePaint)
                
                val forceText = "F$level = %.1f kN".format(force)
                smallPaint.textAlign = Paint.Align.LEFT
                drawFormattedText(canvas, forceText, arrowEnd + 5f, floorY + 4f, smallPaint)
            }
        }
        
        // Base Shear and Height labels
        var labelY = startY + 40f
        val footerPaint = Paint(subtitlePaint).apply { textSize = 14f; color = Color.BLACK }
        val vbLabel = if (isAr) "Ù‚ÙˆØ© Ø§Ù„Ù‚Øµ Ø§Ù„Ù‚Ø§Ø¹Ø¯Ø© Vb = %.1f kN".format(result.baseShear) else "Base Shear Vb = %.1f kN".format(result.baseShear)
        
        if (isAr) {
            footerPaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, vbLabel, (PAGE_WIDTH - MARGIN).toFloat(), labelY, footerPaint)
            labelY += 25f
            drawFormattedText(canvas, heightLabel, (PAGE_WIDTH - MARGIN).toFloat(), labelY, footerPaint)
        } else {
            footerPaint.textAlign = Paint.Align.LEFT
            drawFormattedText(canvas, vbLabel, MARGIN.toFloat(), labelY, footerPaint)
            labelY += 25f
            drawFormattedText(canvas, heightLabel, MARGIN.toFloat(), labelY, footerPaint)
        }
    }

    // ==================== Ø¯ÙˆØ§Ù„ Ø§Ù„Ù…Ø³Ø§Ø¹Ø¯Ø© Ù„Ù„Ø±Ø³Ù… ÙˆØ§Ù„ØµÙØ­Ø§Øª ====================

    private fun getArabicFont(): Typeface {
        return try {
            // Try to use a system font that usually supports Arabic well
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.create("sans-serif", Typeface.NORMAL)
            } else {
                Typeface.DEFAULT
            }
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }

    private fun isArabic(text: String): Boolean {
        return text.any { it.code in 0x0600..0x06FF }
    }

    /**
     * Draws text with proper Arabic support if needed.
     * Note: Native Canvas.drawText doesn't handle Arabic shaping/Bidi perfectly by default on all Android versions
     * when using custom fonts, but Typeface.createFromAsset usually handles shaping.
     * For complex RTL, we might need Bidi class or StaticLayout.
     */
    private fun drawFormattedText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        if (text.isEmpty()) return
        
        val workingPaint = Paint(paint)
        val isArabicText = isArabic(text)
        
        if (isArabicText) {
            workingPaint.typeface = getArabicFont()
        }
        
        val textPaint = TextPaint(workingPaint)
        val layoutWidth = (PAGE_WIDTH - 2 * MARGIN).toFloat()
        
        val alignment = when (paint.textAlign) {
            Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            else -> if (isArabicText) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        }
        
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, layoutWidth.toInt())
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.1f)
            .build()
        
        canvas.save()
        
        // Adjust translation for StaticLayout
        val tx = when (paint.textAlign) {
            Paint.Align.RIGHT -> x - layoutWidth
            Paint.Align.CENTER -> x - layoutWidth / 2f
            else -> x
        }
        
        // StaticLayout draws from its top, so we subtract text size from y if it's meant to be baseline-ish
        canvas.translate(tx, y - workingPaint.textSize)
        staticLayout.draw(canvas)
        canvas.restore()
    }

    private fun checkNewPage(canvas: Canvas, requiredHeight: Float): Canvas {
        var activeCanvas = canvas
        if (currentY + requiredHeight > PAGE_HEIGHT - MARGIN - 30f) { // Adjusted for footer space
            currentDocument?.let { doc ->
                currentPage?.let { 
                    drawFooter(it.canvas, currentPageNum)
                    doc.finishPage(it) 
                }
                currentPageNum++
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
                currentPage = doc.startPage(pageInfo)
                activeCanvas = currentPage!!.canvas
                currentY = MARGIN.toFloat() + 20f
                val isAr = isArabic(settingsManager.language)
                val headerText = if (isAr) "ØªØ§Ø¨Ø¹ - ØµÙØ­Ø© $currentPageNum" else "Cont. - Page $currentPageNum"
                drawHeader(activeCanvas, headerText)
            }
        }
        return activeCanvas
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val isAr = isArabic(settingsManager.language)
        val text = if (isAr) "ØµÙØ­Ø© $pageNumber" else "Page $pageNumber"
        drawFormattedText(canvas, text, (PAGE_WIDTH / 2).toFloat(), (PAGE_HEIGHT - 20).toFloat(), footerPaint)
        
        // Add timestamp to footer
        footerPaint.textAlign = Paint.Align.LEFT
        drawFormattedText(canvas, getCurrentDate(), MARGIN.toFloat(), (PAGE_HEIGHT - 20).toFloat(), footerPaint)
    }

    private fun drawHeader(canvas: Canvas, subtitle: String) {
        val y = 35f
        val isAr = isArabic(settingsManager.language)
        
        headerPaint.textSize = 12f
        headerPaint.color = primaryColor
        headerPaint.isFakeBoldText = true
        
        if (isAr) {
            headerPaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, "Civil Engineer Pro", (PAGE_WIDTH - MARGIN).toFloat(), y, headerPaint)
            headerPaint.textAlign = Paint.Align.LEFT
            headerPaint.color = Color.GRAY
            headerPaint.isFakeBoldText = false
            drawFormattedText(canvas, subtitle, MARGIN.toFloat(), y, headerPaint)
        } else {
            headerPaint.textAlign = Paint.Align.LEFT
            drawFormattedText(canvas, "Civil Engineer Pro", MARGIN.toFloat(), y, headerPaint)
            headerPaint.textAlign = Paint.Align.RIGHT
            headerPaint.color = Color.GRAY
            headerPaint.isFakeBoldText = false
            drawFormattedText(canvas, subtitle, (PAGE_WIDTH - MARGIN).toFloat(), y, headerPaint)
        }
        
        canvas.drawLine(MARGIN.toFloat(), y + 8f, (PAGE_WIDTH - MARGIN).toFloat(), y + 8f, Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f })
    }


    private fun drawPage(document: PdfDocument, pageNum: Int, drawBlock: (Canvas) -> Unit) {
        currentDocument = document
        currentPageNum = pageNum
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        currentPage = document.startPage(pageInfo)
        currentY = MARGIN.toFloat()
        drawBlock(currentPage!!.canvas)
        document.finishPage(currentPage)
        currentPage = null
    }

    private fun drawInputsSection(canvas: Canvas, inputs: ColumnInputs, type: ColumnType) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ù…Ø¹Ø·ÙŠØ§Øª Ø§Ù„ØªØµÙ…ÙŠÙ…" else "ðŸ“ Input Parameters"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„Ø¹Ù…ÙˆØ¯" else "Column Type", type.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„Ø­Ù…Ù„ Ø§Ù„Ù…Ø­ÙˆØ±ÙŠ (Pu)" else "Axial Load (Pu)", "${inputs.axialLoad} kN"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„Ø¹Ø²ÙˆÙ… Mx / My" else "Moments Mx / My", "${inputs.momentX} / ${inputs.momentY} kN.m"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ù…Ù‚Ø§ÙˆÙ…Ø© Ø§Ù„Ø®Ø±Ø³Ø§Ù†Ø© fcu" else "Concrete fcu", "${inputs.fcu} MPa"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ø¥Ø¬Ù‡Ø§Ø¯ Ø§Ù„Ø®Ø¶ÙˆØ¹ fy" else "Steel fy", "${inputs.fy} MPa")
    }

    private fun drawBeamInputs(canvas: Canvas, beamType: BeamType, inputs: BeamInputs) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ù…Ø¹Ø·ÙŠØ§Øª ØªØµÙ…ÙŠÙ… Ø§Ù„ÙƒÙ…Ø±Ø©" else "ðŸ“ Beam Input Parameters"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„ÙƒÙ…Ø±Ø©" else "Beam Type", beamType.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø·ÙˆÙ„ Ø§Ù„Ø¨Ø­Ø±" else "Span Length", "${inputs.span} m"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„Ø­Ù…Ù„ Ø§Ù„ØªØµÙ…ÙŠÙ…ÙŠ (wu)" else "Ultimate Load (wu)", "${inputs.deadLoad + inputs.liveLoad} kN/m"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ù…Ù‚Ø§ÙˆÙ…Ø© Ø§Ù„Ø®Ø±Ø³Ø§Ù†Ø© fcu" else "Concrete fcu", "${inputs.fcu} MPa"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ø¥Ø¬Ù‡Ø§Ø¯ Ø§Ù„Ø®Ø¶ÙˆØ¹ fy" else "Steel fy", "${inputs.fy} MPa")
    }

    private fun drawSlabInputs(canvas: Canvas, slabType: SlabType, inputs: SlabInputs) {
        var y = MARGIN.toFloat() + 20f
        val title = getString("📌 معطيات تصميم البلاطة", "📌 Slab Parameters")
        drawFormattedText(canvas, title, if (isAr()) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), y, titlePaint)
        y += 40f
        
        drawInfoRow(canvas, y, getString("نوع البلاطة", "Slab Type"), slabType.displayName); y += 25f
        drawInfoRow(canvas, y, getString("السمك الكلي", "Thickness"), "${inputs.thickness} mm"); y += 20f
        drawInfoRow(canvas, y, getString("الحمل التصميمي", "Total Load"), "${inputs.deadLoad + inputs.liveLoad} kN/m²")
    }

    private fun drawSlabResults(canvas: Canvas, result: AdvancedSlabResult) {
        var y = MARGIN.toFloat() + 20f
        val title = getString("📊 نتائج تصميم البلاطة", "📊 Slab Results")
        drawFormattedText(canvas, title, if (isAr()) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), y, titlePaint)
        y += 40f
        
        val isOverallSafe = result.flexureResult.isSafe && 
                          (result.punchingShearCheck?.isSafe ?: true)
        
        drawStatusHeader(canvas, y, isOverallSafe); y += 40f
        
        drawInfoRow(canvas, y, getString("الحديد السفلي", "Bottom Bars"), "${result.reinforcementLayout.bottomBars.numberOfBars} Ø${result.reinforcementLayout.bottomBars.diameter}"); y += 25f
        
        result.punchingShearCheck?.let { punching ->
            drawInfoRow(canvas, y, getString("قص الثقب (Punching)", "Punching Shear"), if (punching.isSafe) getString("آمن ✅", "SAFE ✅") else getString("غير آمن ❌", "UNSAFE ❌")); y += 20f
            drawInfoRow(canvas, y, getString("  المطبق / السعة", "  Applied / Capacity"), "${"%.1f".format(punching.appliedShear)} / ${"%.1f".format(punching.shearCapacity)} kN"); y += 25f
        }

        y += 10f
        drawUtilizationBar(canvas, y, getString("استخدام الانحناء", "Flexural Utilization"), result.flexureResult.utilizationRatio)
    }

    private fun drawSteelInputs(canvas: Canvas, section: SteelSectionType, member: SteelMemberType, inputs: SteelInputs) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ù…Ø¹Ø·ÙŠØ§Øª ØªØµÙ…ÙŠÙ… Ø§Ù„Ø¹Ø¶Ùˆ Ø§Ù„Ù…Ø¹Ø¯Ù†ÙŠ" else "ðŸ“ Steel Member Parameters"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„Ø¹Ø¶Ùˆ" else "Member Type", member.name); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„Ù‚Ø·Ø§Ø¹" else "Section Type", section.displayName); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„Ø·ÙˆÙ„ / Ø§Ù„Ø­Ù…Ù„" else "Length / Load", "${inputs.unbracedLength} m / ${inputs.axialLoad} kN")
    }

    private fun drawMainResults(canvas: Canvas, result: AdvancedColumnResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“Š Ù…Ù„Ø®Øµ Ø§Ù„ØªØµÙ…ÙŠÙ…" else "ðŸ“Š Design Summary"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        val isSafe = result.reinforcementResult.isSafe && (result.biaxialCheck?.isSafe ?: true)
        drawStatusHeader(canvas, y, isSafe); y += 40f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„ØªØ³Ù„ÙŠØ­ Ø§Ù„Ù…Ù‚Ø¯Ù…" else "Provided Steel", "${result.reinforcementResult.numberOfBars} Ã˜${result.reinforcementResult.barDiameter}"); y += 25f
        
        val utilRatio = if (result.axialCapacity > 0) 
            (result.reinforcementResult.astRequired / result.reinforcementResult.astProvided).coerceIn(0.0, 1.2)
            else result.reinforcementResult.utilizationRatio
            
        drawUtilizationBar(canvas, y, if (isAr) "Ù†Ø³Ø¨Ø© Ø§Ù„Ø§Ø³ØªØ®Ø¯Ø§Ù… Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠ" else "Structural Utilization", utilRatio)
    }

    private fun drawBeamResults(canvas: Canvas, result: AdvancedBeamResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“Š Ù†ØªØ§Ø¦Ø¬ Ø§Ù„ØªØµÙ…ÙŠÙ…" else "ðŸ“Š Design Results"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawStatusHeader(canvas, y, result.flexureResult.isSafe && result.shearResult.isSafe); y += 40f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„ØªØ³Ù„ÙŠØ­ Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠ" else "Main Steel", "${result.flexureResult.numberOfBars} Ã˜${result.flexureResult.barDiameter}"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„ÙƒØ§Ù†Ø§Øª" else "Stirrups", "Ã˜${result.shearResult.stirrupDiameter} @ ${result.shearResult.stirrupSpacing} mm"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„ØªØ±Ø®ÙŠÙ… (Deflection)" else "Deflection", if (result.deflectionCheck.isSafe) (if (isAr) "Ø¢Ù…Ù†" else "SAFE") else (if (isAr) "ØºÙŠØ± Ø¢Ù…Ù†" else "EXCEEDS LIMIT"))
        
        y += 30f
        drawUtilizationBar(canvas, y, if (isAr) "Ø§Ø³ØªØ®Ø¯Ø§Ù… Ø§Ù„Ø§Ù†Ø­Ù†Ø§Ø¡" else "Flexural Utilization", result.flexureResult.utilizationRatio)
    }

    private fun drawSlabQuantities(canvas: Canvas, result: AdvancedSlabResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“Š Ø§Ù„ÙƒÙ…ÙŠØ§Øª ÙˆØ§Ù„ØªÙ‚Ø¯ÙŠØ±Ø§Øª" else "ðŸ“Š Quantities & Estimates"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "Ø­Ø¬Ù… Ø§Ù„Ø®Ø±Ø³Ø§Ù†Ø©" else "Concrete Volume", "${"%.2f".format(result.concreteVolume)} mÂ³"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ø´Ø¯Ø© Ø§Ù„Ø®Ø´Ø¨ÙŠØ©" else "Formwork Area", "${"%.2f".format(result.formworkArea)} mÂ²"); y += 25f
        
        if (result.inventoryAnalysis != null) {
            drawInfoRow(canvas, y, if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ ÙˆØ²Ù† Ø§Ù„ØµÙ„Ø¨" else "Total Steel Weight", "${"%.2f".format(result.inventoryAnalysis.totalWeight)} Tons")
        }
    }

    private fun drawSteelResults(canvas: Canvas, result: SteelMemberResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“Š Ù†ØªØ§Ø¦Ø¬ Ø§Ù„Ø¹Ø¶Ùˆ Ø§Ù„Ù…Ø¹Ø¯Ù†ÙŠ" else "ðŸ“Š Steel Member Results"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawUtilizationBar(canvas, y, if (isAr) "ØªÙØ§Ø¹Ù„ Ø§Ù„Ù‚ÙˆÙ‰ Ø§Ù„ÙƒÙ„ÙŠ" else "Total Interaction", result.utilizationRatio); y += 30f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„ÙˆØ²Ù†" else "Weight", "${"%.1f".format(result.weight)} kg/m")
    }

    private fun drawColumnSectionPage(canvas: Canvas, type: ColumnType, reinf: ReinforcementResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ø±Ø³Ù… Ø§Ù„Ù…Ù‚Ø·Ø¹ Ø§Ù„Ø¹Ø±Ø¶ÙŠ Ù„Ù„ØªØ³Ù„ÙŠØ­" else "ðŸ“ Cross Section Drawing"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 60f
        
        val areaHeight = 400f
        activeCanvas = checkNewPage(activeCanvas, areaHeight + 100f)
        val area = RectF(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY + areaHeight)
        
        when (type) {
            is ColumnType.Rectangular -> drawRectangularSection(activeCanvas, area, type, reinf)
            is ColumnType.Circular -> drawCircularSection(activeCanvas, area, type, reinf)
            is ColumnType.LShaped -> {
                val cx = area.centerX()
                val cy = area.centerY()
                val paint = Paint().apply { color = Color.parseColor("#F5F5F5"); style = Paint.Style.FILL }
                val strokePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
                
                // Drawing L-Shape (Simple schematic)
                val scale = 0.5f
                val lw = type.legWidth.toFloat() * scale
                val ld = type.legDepth.toFloat() * scale
                val t = type.thickness.toFloat() * scale
                
                val path = Path().apply {
                    moveTo(cx - lw/2, cy - ld/2)
                    lineTo(cx - lw/2 + t, cy - ld/2)
                    lineTo(cx - lw/2 + t, cy + ld/2 - t)
                    lineTo(cx + lw/2, cy + ld/2 - t)
                    lineTo(cx + lw/2, cy + ld/2)
                    lineTo(cx - lw/2, cy + ld/2)
                    close()
                }
                activeCanvas.drawPath(path, paint)
                activeCanvas.drawPath(path, strokePaint)
                
                // Simplified reinforcement dots for L-shape
                val dotPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
                activeCanvas.drawCircle(cx - lw/2 + 10f, cy - ld/2 + 10f, 4f, dotPaint)
                activeCanvas.drawCircle(cx - lw/2 + 10f, cy + ld/2 - 10f, 4f, dotPaint)
                activeCanvas.drawCircle(cx + lw/2 - 10f, cy + ld/2 - 10f, 4f, dotPaint)

                drawFormattedText(activeCanvas, "L-Section Reinforcement Detail", cx, cy + ld/2 + 40f, smallPaint)
            }
            else -> drawPlaceholder(activeCanvas, area, if (isAr) "Ø±Ø³Ù… ÙƒØ±ÙˆÙƒÙŠ Ù„Ù„Ø¹Ù…ÙˆØ¯" else "Column Sketch")
        }
        
        currentY += areaHeight + 50f
        activeCanvas = checkNewPage(activeCanvas, 80f)
        
        // Add Summary info below section
        val detailText = if (isAr) 
            "Ø§Ù„ØªØ³Ù„ÙŠØ­ Ø§Ù„Ù…ÙˆÙØ±: ${reinf.numberOfBars} Ø£Ø³ÙŠØ§Ø® Ù‚Ø·Ø± ${reinf.barDiameter} Ù…Ù…" 
            else "Provided Reinforcement: ${reinf.numberOfBars} bars @ ${reinf.barDiameter} mm"
        
        drawFormattedText(activeCanvas, detailText, PAGE_WIDTH / 2f, currentY, subtitlePaint)
        currentY += 40f
        drawDisclaimer(activeCanvas, currentY)
        drawDisclaimer(activeCanvas, currentY)
    }


    private fun drawBeamSectionPage(canvas: Canvas, result: AdvancedBeamResult, inputs: BeamInputs) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ ØªÙØ§ØµÙŠÙ„ ØªØ³Ù„ÙŠØ­ Ø§Ù„Ù…Ù‚Ø·Ø¹ Ø§Ù„Ø¹Ø±Ø¶ÙŠ Ù„Ù„ÙƒÙ…Ø±Ø©" else "ðŸ“ Beam Cross Section & Reinforcement"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 60f
        
        val w = 180f
        val h = 300f
        val left = PAGE_WIDTH / 2f - w / 2
        val top = currentY + 40f
        
        activeCanvas = checkNewPage(activeCanvas, h + 150f)
        val currentTop = if (activeCanvas != canvas) currentY + 40f else top

        // 1. Draw Concrete Section
        val concretePaint = Paint().apply { color = Color.parseColor("#F5F5F5"); style = Paint.Style.FILL }
        val strokePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        activeCanvas.drawRect(left, currentTop, left + w, currentTop + h, concretePaint)
        activeCanvas.drawRect(left, currentTop, left + w, currentTop + h, strokePaint)
        
        // 2. Draw Stirrup (Rectangle inside with cover)
        val cover = 15f
        val rebarPaint = Paint().apply { color = Color.parseColor("#B71C1C"); strokeWidth = 2.5f; style = Paint.Style.STROKE }
        activeCanvas.drawRect(left + cover, currentTop + cover, left + w - cover, currentTop + h - cover, rebarPaint)
        
        // 3. Draw Bottom Reinforcement (Main)
        val nBottom = result.flexureResult.numberOfBars
        val diaBottom = result.flexureResult.barDiameter
        val dotPaint = Paint().apply { color = Color.parseColor("#B71C1C"); style = Paint.Style.FILL }
        
        val bottomY = currentTop + h - cover - 10f
        val spacing = (w - 2 * cover - 20f) / (nBottom - 1).coerceAtLeast(1)
        for (i in 0 until nBottom) {
            val dotX = left + cover + 10f + i * spacing
            activeCanvas.drawCircle(dotX, bottomY, 4f, dotPaint)
        }
        
        // 4. Draw Top Reinforcement (Stirrup Hangers)
        val nTop = 2 // Typical hangers
        val topY = currentTop + cover + 10f
        for (i in 0 until nTop) {
            val dotX = if (i == 0) left + cover + 10f else left + w - cover - 10f
            activeCanvas.drawCircle(dotX, topY, 4f, dotPaint)
        }
        
        // 5. Labels
        smallPaint.textAlign = Paint.Align.CENTER
        val bottomLabel = if (isAr) "$nBottom Ø£Ø³ÙŠØ§Ø® Î¦$diaBottom Ø³ÙÙ„ÙŠ" else "$nBottom - Î¦$diaBottom Bottom"
        val topLabel = if (isAr) "2 Ø£Ø³ÙŠØ§Ø® Î¦12 ØªØ¹Ù„ÙŠÙ‚ ÙƒØ§Ù†Ø§Øª" else "2 - Î¦12 Stirrup Hangers"
        val stirrupLabel = if (isAr) "ÙƒØ§Ù†Ø§Øª Î¦8 ÙƒÙ„ 200 Ù…Ù…" else "Stirrups Î¦8 @ 200mm"
        
        drawFormattedText(activeCanvas, bottomLabel, PAGE_WIDTH / 2f, currentTop + h + 25f, smallPaint)
        drawFormattedText(activeCanvas, topLabel, PAGE_WIDTH / 2f, currentTop - 15f, smallPaint)
        currentY = currentTop + h + 60f
        drawFormattedText(activeCanvas, stirrupLabel, PAGE_WIDTH / 2f, currentY, smallPaint)
        currentY += 40f
        
        smallPaint.textAlign = Paint.Align.LEFT
        drawFormattedText(activeCanvas, "b=${inputs.width.toInt()}mm", left - 50f, currentTop + h/2, smallPaint)
        drawFormattedText(activeCanvas, "h=${inputs.totalDepth.toInt()}mm", left + w/2, currentTop + h + 50f, smallPaint)

        currentY = currentTop + h + 80f
        activeCanvas = checkNewPage(activeCanvas, 100f)
        drawDisclaimer(activeCanvas, currentY)
    }


    private fun drawSlabLayoutPage(canvas: Canvas, layout: ReinforcementLayout) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ Ù…Ø®Ø·Ø· ØªØ³Ù„ÙŠØ­ Ø§Ù„Ø¨Ù„Ø§Ø·Ø©" else "ðŸ“ Slab Reinforcement Plan"
        drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint); y += 40f
        
        val mainText = if (isAr) 
            "Ø§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ø³ÙÙ„ÙŠ: ${layout.bottomBars.numberOfBars} Ã˜${layout.bottomBars.diameter.toInt()} ÙƒÙ„ ${layout.bottomBars.spacing.toInt()} Ù…Ù…"
            else "Bottom Bars: ${layout.bottomBars.numberOfBars} Ã˜${layout.bottomBars.diameter.toInt()} @ ${layout.bottomBars.spacing.toInt()} mm"
            
        val topText = if (isAr)
            "Ø§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ø¹Ù„ÙˆÙŠ: ${layout.topBars.numberOfBars} Ã˜${layout.topBars.diameter.toInt()} ÙƒÙ„ ${layout.topBars.spacing.toInt()} Ù…Ù…"
            else "Top Bars: ${layout.topBars.numberOfBars} Ã˜${layout.topBars.diameter.toInt()} @ ${layout.topBars.spacing.toInt()} mm"
            
        drawFormattedText(canvas, mainText, MARGIN.toFloat(), y, bodyPaint); y += 20f
        drawFormattedText(canvas, topText, MARGIN.toFloat(), y, bodyPaint); y += 60f
        
        // Drawing a representative slab panel
        val panelW = 400f
        val panelH = 300f
        val startX = (PAGE_WIDTH - panelW) / 2f
        val startY = y
        
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.DKGRAY
        }
        
        // Slab boundary
        canvas.drawRect(startX, startY, startX + panelW, startY + panelH, paint)
        
        // Draw reinforcement lines
        paint.color = Color.BLUE
        paint.strokeWidth = 1.5f
        
        // Bottom bars (representative)
        for (i in 0..8) {
            val bx = startX + 20f + i * (panelW - 40f) / 8
            canvas.drawLine(bx, startY + 10f, bx, startY + panelH - 10f, paint)
        }
        
        // Top bars (cross - representative)
        paint.color = Color.RED
        for (i in 0..6) {
            val by = startY + 20f + i * (panelH - 40f) / 6
            canvas.drawLine(startX + 10f, by, startX + panelW - 10f, by, paint)
        }
        
        // Dimension lines
        paint.color = Color.GRAY
        paint.strokeWidth = 1f
        canvas.drawLine(startX, startY - 10f, startX + panelW, startY - 10f, paint)
        drawFormattedText(canvas, "L = 5.00 m", startX + panelW/2 - 20f, startY - 15f, smallPaint)
        
        canvas.drawLine(startX - 10f, startY, startX - 10f, startY + panelH, paint)
        canvas.save()
        canvas.rotate(-90f, startX - 15f, startY + panelH/2)
        drawFormattedText(canvas, "W = 4.00 m", startX - 15f, startY + panelH/2, smallPaint)
        canvas.restore()
        
        y += panelH + 40f
        val note = if (isAr) "Ù…Ù„Ø§Ø­Ø¸Ø©: Ø¬Ù…ÙŠØ¹ Ø£Ø·ÙˆØ§Ù„ Ø§Ù„ØªÙ…Ø§Ø³Ùƒ ÙˆØ§Ù„ÙˆØµÙ„Ø§Øª ÙŠØ¬Ø¨ Ø£Ù† ØªÙƒÙˆÙ† Ø­Ø³Ø¨ Ø§Ù„ÙƒÙˆØ¯ Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù…." 
                   else "Note: All development lengths and laps shall conform to the design code."
        drawFormattedText(canvas, note, MARGIN.toFloat(), y, smallPaint)
    }

    private fun drawColumnBOQ(canvas: Canvas, result: AdvancedColumnResult, inputs: ColumnInputs) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ—ï¸ Ø¬Ø¯ÙˆÙ„ Ø§Ù„ÙƒÙ…ÙŠØ§Øª ÙˆØªÙ‚Ø¯ÙŠØ± Ø§Ù„ØªÙƒÙ„ÙØ©" else "ðŸ—ï¸ Bill of Quantities (BOQ)"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 50f
        
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 3f
        val bgPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        activeCanvas.drawRect(MARGIN.toFloat(), currentY - 5f, (PAGE_WIDTH - MARGIN).toFloat(), currentY + 30f, bgPaint)
        
        val h1 = if (isAr) "Ø§Ù„ÙˆØµÙ" else "Description"
        val h2 = if (isAr) "Ø§Ù„ÙƒÙ…ÙŠØ©" else "Quantity"
        val h3 = if (isAr) "Ø§Ù„ØªÙƒÙ„ÙØ©" else "Estimated Cost"
        
        subtitlePaint.textSize = 12f
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, h1, (PAGE_WIDTH - MARGIN) - 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h2, (PAGE_WIDTH - MARGIN) - colWidth - 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h3, (PAGE_WIDTH - MARGIN) - 2 * colWidth - 10f, currentY + 20f, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, h1, MARGIN + 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h2, MARGIN + colWidth + 10f, currentY + 20f, subtitlePaint)
            drawFormattedText(activeCanvas, h3, MARGIN + 2 * colWidth + 10f, currentY + 20f, subtitlePaint)
        }
        currentY += 50f
        
        val height = inputs.unsupportedLength
        val concreteVol = result.concreteVolumePerMeter * height
        
        // Approximate steel weight calculation
        val nBars = result.reinforcementResult.numberOfBars
        val dia = result.reinforcementResult.barDiameter
        val mainSteelWeight = (nBars * (dia * dia / 162.0) * height)
        val stirrupWeight = (4 * 0.3 * (8.0 * 8.0 / 162.0) * (height / 0.2)) // Approx
        val steelWeight = (mainSteelWeight + stirrupWeight) * 1.10
        
        val concreteCost = concreteVol * settingsManager.concretePrice
        val steelCost = (steelWeight / 1000.0) * settingsManager.steelPrice
        val currency = settingsManager.currency
        
        val concreteDesc = if (isAr) "Ø®Ø±Ø³Ø§Ù†Ø© Ø¬Ø§Ù‡Ø²Ø©" else "Concrete (Ready-Mix)"
        val steelDesc = if (isAr) "Ø­Ø¯ÙŠØ¯ ØªØ³Ù„ÙŠØ­" else "Reinforcement Steel"
        
        drawBOQRow(activeCanvas, currentY, concreteDesc, "${"%.2f".format(concreteVol)} mÂ³", "${"%.0f".format(concreteCost)} $currency"); currentY += 30f
        activeCanvas = checkNewPage(activeCanvas, 30f)
        drawBOQRow(activeCanvas, currentY, steelDesc, "${"%.2f".format(steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); currentY += 30f
        
        activeCanvas = checkNewPage(activeCanvas, 40f)
        activeCanvas.drawLine(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY, Paint().apply { color = Color.LTGRAY }); currentY += 40f

        // BBS for Column
        val bbsTitle = if (isAr) "ðŸ“Š ØªÙØ±ÙŠØ¯ Ø­Ø¯ÙŠØ¯ Ø§Ù„ØªØ³Ù„ÙŠØ­ (BBS)" else "ðŸ“Š Bar Bending Schedule (BBS)"
        activeCanvas = checkNewPage(activeCanvas, 50f)
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, bbsTitle, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, bbsTitle, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        // Use a generic ReinforcementBar for BBS
        val mainBar = CalculatorEngine.ReinforcementBar(
            numBars = nBars,
            diameter = dia.toInt(),
            barLength = height + 0.6,
            shapeCode = 1,
            type = "Main",
            description = "Column Main Steel"
        )
        val stirrupBar = CalculatorEngine.ReinforcementBar(
            numBars = (height / 0.2).toInt() + 1,
            diameter = 8,
            barLength = 2 * ( (if (inputs.columnType is ColumnType.Rectangular) inputs.columnType.width else 500.0) + (if (inputs.columnType is ColumnType.Rectangular) inputs.columnType.depth else 500.0) ) / 1000.0 + 0.2,
            shapeCode = 1,
            type = "Stirrup",
            description = "Column Stirrups"
        )
        activeCanvas = checkNewPage(activeCanvas, 150f)
        drawBbsTable(activeCanvas, currentY, listOf(mainBar, stirrupBar)); currentY += 150f
        
        activeCanvas = checkNewPage(activeCanvas, 50f)
        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„ØªÙƒÙ„ÙØ© Ø§Ù„ØªÙ‚Ø¯ÙŠØ±ÙŠØ©" else "TOTAL ESTIMATED COST"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, totalLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
            drawFormattedText(activeCanvas, "${"%.2f".format(concreteCost + steelCost)} $currency", MARGIN.toFloat(), currentY, titlePaint)
        } else {
            drawFormattedText(activeCanvas, totalLabel, MARGIN.toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, "${"%.2f".format(concreteCost + steelCost)} $currency", (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        }
        
        titlePaint.color = textColor
        currentY += 60f
        drawDisclaimer(activeCanvas, currentY)
    }


    private fun drawBeamBOQ(canvas: Canvas, result: AdvancedBeamResult, inputs: BeamInputs) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ—ï¸ Ø¬Ø¯ÙˆÙ„ Ø§Ù„ÙƒÙ…ÙŠØ§Øª ÙˆØªÙ‚Ø¯ÙŠØ± Ø§Ù„ØªÙƒÙ„ÙØ©" else "ðŸ—ï¸ Bill of Quantities (BOQ)"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 50f
        
        val colWidth = (PAGE_WIDTH - 2 * MARGIN) / 3f
        val bgPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        canvas.drawRect(MARGIN.toFloat(), y - 5f, (PAGE_WIDTH - MARGIN).toFloat(), y + 30f, bgPaint)
        
        val h1 = if (isAr) "Ø§Ù„ÙˆØµÙ" else "Description"
        val h2 = if (isAr) "Ø§Ù„ÙƒÙ…ÙŠØ©" else "Quantity"
        val h3 = if (isAr) "Ø§Ù„ØªÙƒÙ„ÙØ©" else "Estimated Cost"
        
        subtitlePaint.textSize = 12f
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, h1, (PAGE_WIDTH - MARGIN) - 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h2, (PAGE_WIDTH - MARGIN) - colWidth - 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h3, (PAGE_WIDTH - MARGIN) - 2 * colWidth - 10f, y + 20f, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, h1, MARGIN + 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h2, MARGIN + colWidth + 10f, y + 20f, subtitlePaint)
            drawFormattedText(canvas, h3, MARGIN + 2 * colWidth + 10f, y + 20f, subtitlePaint)
        }
        y += 50f
        
        val span = inputs.span
        val width = inputs.width
        val height = inputs.totalDepth
        val concreteVol = (width * height * span) / 1e9 // mm3 to m3
        
        val steelWeight = result.inventoryAnalysis?.totalWeight?.times(1000.0) ?: (concreteVol * 120.0)
        
        val concreteCost = concreteVol * settingsManager.concretePrice
        val steelCost = (steelWeight / 1000.0) * settingsManager.steelPrice
        val currency = settingsManager.currency
        
        val concreteDesc = if (isAr) "Ø®Ø±Ø³Ø§Ù†Ø© Ø¬Ø§Ù‡Ø²Ø©" else "Concrete (Ready-Mix)"
        val steelDesc = if (isAr) "Ø­Ø¯ÙŠØ¯ ØªØ³Ù„ÙŠØ­" else "Reinforcement Steel"
        
        drawBOQRow(canvas, y, concreteDesc, "${"%.2f".format(concreteVol)} mÂ³", "${"%.0f".format(concreteCost)} $currency"); y += 30f
        drawBOQRow(canvas, y, steelDesc, "${"%.2f".format(steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); y += 30f
        
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, Paint().apply { color = Color.LTGRAY }); y += 40f
        
        // --- BBS Section ---
        val bbsTitle = if (isAr) "ðŸ“Š ØªÙØ±ÙŠØ¯ Ø­Ø¯ÙŠØ¯ Ø§Ù„ØªØ³Ù„ÙŠØ­ (BBS)" else "ðŸ“Š Bar Bending Schedule (BBS)"
        drawFormattedText(canvas, bbsTitle, MARGIN.toFloat(), y, titlePaint); y += 40f
        
        // Mapping ReinforcementResult to CalculatorEngine.ReinforcementBar for BBS
        val mainBar = CalculatorEngine.ReinforcementBar(
            numBars = result.flexureResult.numberOfBars,
            diameter = result.flexureResult.barDiameter.toInt(),
            barLength = span / 1000.0 + 0.4, // Simplified: span + hooks
            shapeCode = 1
        )
        
        val stirrups = CalculatorEngine.ReinforcementBar(
            numBars = (span / result.shearResult.stirrupSpacing).toInt(),
            diameter = result.shearResult.stirrupDiameter.toInt(),
            barLength = (width * 2 + height * 2) / 1000.0 + 0.1, // Perimeter + hooks
            shapeCode = 51 // Closed stirrup
        )
        
        drawBbsTable(canvas, y, listOf(mainBar, stirrups).filter { it.diameter > 0 }); y += 150f

        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø§Ù„ØªÙƒÙ„ÙØ© Ø§Ù„ØªÙ‚Ø¯ÙŠØ±ÙŠØ©" else "TOTAL ESTIMATED COST"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, totalLabel, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
            drawFormattedText(canvas, "${"%.2f".format(concreteCost + steelCost)} $currency", MARGIN.toFloat(), y, titlePaint)
        } else {
            drawFormattedText(canvas, totalLabel, MARGIN.toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, "${"%.2f".format(concreteCost + steelCost)} $currency", (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        }

        titlePaint.color = textColor
        y += 60f
        drawDisclaimer(canvas, y)
    }

    private fun drawBbsTable(canvas: Canvas, startY: Float, bars: List<CalculatorEngine.ReinforcementBar>) {
        var y = startY
        val isAr = isArabic(settingsManager.language)
        val cols = if (isAr) {
            listOf("Ø§Ù„Ø¹Ù†ØµØ±", "Ø§Ù„Ø´ÙƒÙ„", "Ø§Ù„Ù‚Ø·Ø±", "Ø§Ù„Ø·ÙˆÙ„", "Ø§Ù„Ø¥Ø¬Ù…Ø§Ù„ÙŠ", "Ø§Ù„ÙˆØ²Ù†")
        } else {
            listOf("Pos", "Shape", "Dia", "Length", "Total L", "Weight")
        }
        val colWidths = listOf(50f, 90f, 50f, 80f, 90f, 90f)
        
        // Table Header Background
        val headerBgPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        canvas.drawRect(MARGIN.toFloat(), y - 20f, (PAGE_WIDTH - MARGIN).toFloat(), y + 10f, headerBgPaint)
        
        val headerPaint = Paint(subtitlePaint).apply { 
            color = Color.DKGRAY
            textSize = 12f
            isFakeBoldText = true
        }
        
        if (isAr) {
            var currentX = (PAGE_WIDTH - MARGIN).toFloat()
            cols.forEachIndexed { i, text ->
                headerPaint.textAlign = Paint.Align.RIGHT
                drawFormattedText(canvas, text, currentX - 5f, y, headerPaint)
                currentX -= colWidths[i]
            }
        } else {
            var currentX = MARGIN.toFloat()
            cols.forEachIndexed { i, text ->
                headerPaint.textAlign = Paint.Align.LEFT
                drawFormattedText(canvas, text, currentX + 5f, y, headerPaint)
                currentX += colWidths[i]
            }
        }
        
        y += 15f
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, Paint().apply { color = Color.GRAY; strokeWidth = 1f })
        y += 20f

        bodyPaint.textSize = 11f
        bars.forEachIndexed { index, bar ->
            if (isAr) {
                var currentX = (PAGE_WIDTH - MARGIN).toFloat()
                bodyPaint.textAlign = Paint.Align.RIGHT
                
                // Pos
                drawFormattedText(canvas, "${index + 1}", currentX - 5f, y, bodyPaint)
                currentX -= colWidths[0]
                
                // Shape
                drawBarShape(canvas, currentX - colWidths[1], y - 5f, colWidths[1], 20f, bar.shapeCode)
                currentX -= colWidths[1]
                
                // Dia
                drawFormattedText(canvas, "${bar.diameter}", currentX - 5f, y, bodyPaint)
                currentX -= colWidths[2]
                
                // Length
                drawFormattedText(canvas, "${"%.2f".format(bar.barLength)}m", currentX - 5f, y, bodyPaint)
                currentX -= colWidths[3]
                
                // Total L
                drawFormattedText(canvas, "${"%.2f".format(bar.totalLength)}m", currentX - 5f, y, bodyPaint)
                currentX -= colWidths[4]
                
                // Weight
                val weight = bar.totalLength * (bar.diameter * bar.diameter / 162.0)
                drawFormattedText(canvas, "${"%.1f".format(weight)}kg", currentX - 5f, y, bodyPaint)
            } else {
                var currentX = MARGIN.toFloat()
                bodyPaint.textAlign = Paint.Align.LEFT
                
                // Pos
                drawFormattedText(canvas, "${index + 1}", currentX + 5f, y, bodyPaint)
                currentX += colWidths[0]
                
                // Shape
                drawBarShape(canvas, currentX, y - 5f, colWidths[1], 20f, bar.shapeCode)
                currentX += colWidths[1]
                
                // Dia
                drawFormattedText(canvas, "${bar.diameter}", currentX + 5f, y, bodyPaint)
                currentX += colWidths[2]
                
                // Length
                drawFormattedText(canvas, "${"%.2f".format(bar.barLength)}m", currentX + 5f, y, bodyPaint)
                currentX += colWidths[3]
                
                // Total L
                drawFormattedText(canvas, "${"%.2f".format(bar.totalLength)}m", currentX + 5f, y, bodyPaint)
                currentX += colWidths[4]
                
                // Weight
                val weight = bar.totalLength * (bar.diameter * bar.diameter / 162.0)
                drawFormattedText(canvas, "${"%.1f".format(weight)}kg", currentX + 5f, y, bodyPaint)
            }
            
            y += 25f
        }
        
        bodyPaint.textSize = 12f // Reset
    }

    private fun drawBarShape(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, shapeCode: Int) {
        val paint = Paint().apply {
            color = Color.parseColor("#1976D2")
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            isAntiAlias = true
        }
        val padding = 8f
        val drawW = width - 2 * padding
        val drawH = height - 2 * padding
        val centerX = x + width / 2
        val centerY = y
        
        when (shapeCode) {
            1 -> { // Straight bar
                canvas.drawLine(centerX - drawW/2, centerY, centerX + drawW/2, centerY, paint)
                // Small hooks or end marks
                canvas.drawLine(centerX - drawW/2, centerY - 3f, centerX - drawW/2, centerY + 3f, paint)
                canvas.drawLine(centerX + drawW/2, centerY - 3f, centerX + drawW/2, centerY + 3f, paint)
            }
            51 -> { // Closed stirrup
                val rectW = drawW * 0.6f
                val rectH = drawH * 1.2f
                canvas.drawRect(centerX - rectW/2, centerY - rectH/2, centerX + rectW/2, centerY + rectH/2, paint)
                // Hook representation
                canvas.drawLine(centerX - rectW/2, centerY - rectH/2, centerX - rectW/2 + 5f, centerY - rectH/2 - 5f, paint)
                canvas.drawLine(centerX - rectW/2 + 3f, centerY - rectH/2, centerX - rectW/2 + 8f, centerY - rectH/2 - 5f, paint)
            }
            else -> {
                // Default L-Shape or similar for other codes
                canvas.drawLine(centerX - drawW/2, centerY + drawH/2, centerX + drawW/2, centerY + drawH/2, paint)
                canvas.drawLine(centerX + drawW/2, centerY + drawH/2, centerX + drawW/2, centerY - drawH/2, paint)
            }
        }
    }


    private fun drawSteelSectionPage(canvas: Canvas, section: SteelSectionType) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ ØªÙØ§ØµÙŠÙ„ Ø§Ù„Ù…Ù‚Ø·Ø¹ Ø§Ù„Ø¹Ø±Ø¶ÙŠ Ù„Ù„ØµÙ„Ø¨" else "ðŸ“ Steel Section Details"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 60f
        
        val cx = PAGE_WIDTH / 2f
        val cy = currentY + 150f
        val paint = Paint().apply { color = Color.parseColor("#455A64"); style = Paint.Style.FILL; isAntiAlias = true }
        val strokePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }

        activeCanvas = checkNewPage(activeCanvas, 350f)

        when (section) {
            is SteelSectionType.ISection -> {
                val scale = 1.2f
                val h = section.h.toFloat() * scale
                val bf = section.bf.toFloat() * scale
                val tf = section.tf.toFloat() * scale
                val tw = section.tw.toFloat() * scale
                
                // Top Flange
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx + bf/2, cy - h/2 + tf, paint)
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx + bf/2, cy - h/2 + tf, strokePaint)
                // Bottom Flange
                activeCanvas.drawRect(cx - bf/2, cy + h/2 - tf, cx + bf/2, cy + h/2, paint)
                activeCanvas.drawRect(cx - bf/2, cy + h/2 - tf, cx + bf/2, cy + h/2, strokePaint)
                // Web
                activeCanvas.drawRect(cx - tw/2, cy - h/2 + tf, cx + tw/2, cy + h/2 - tf, paint)
                activeCanvas.drawRect(cx - tw/2, cy - h/2 + tf, cx + tw/2, cy + h/2 - tf, strokePaint)
                
                drawSectionLabels(activeCanvas, cx, cy, bf, h)
            }
            is SteelSectionType.CSection -> {
                val scale = 1.2f
                val h = section.h.toFloat() * scale
                val bf = section.bf.toFloat() * scale
                val tf = section.tf.toFloat() * scale
                val tw = section.tw.toFloat() * scale
                
                // Web (Left)
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx - bf/2 + tw, cy + h/2, paint)
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx - bf/2 + tw, cy + h/2, strokePaint)
                // Top Flange
                activeCanvas.drawRect(cx - bf/2 + tw, cy - h/2, cx + bf/2, cy - h/2 + tf, paint)
                activeCanvas.drawRect(cx - bf/2 + tw, cy - h/2, cx + bf/2, cy - h/2 + tf, strokePaint)
                // Bottom Flange
                activeCanvas.drawRect(cx - bf/2 + tw, cy + h/2 - tf, cx + bf/2, cy + h/2, paint)
                activeCanvas.drawRect(cx - bf/2 + tw, cy + h/2 - tf, cx + bf/2, cy + h/2, strokePaint)
                
                drawSectionLabels(activeCanvas, cx, cy, bf, h)
            }
            is SteelSectionType.LSection -> {
                val scale = 1.5f
                val a = section.legA.toFloat() * scale
                val b = section.legB.toFloat() * scale
                val t = section.thickness.toFloat() * scale
                
                activeCanvas.drawRect(cx - b/2, cy + a/2 - t, cx + b/2, cy + a/2, paint)
                activeCanvas.drawRect(cx - b/2, cy - a/2, cx - b/2 + t, cy + a/2 - t, paint)
                activeCanvas.drawRect(cx - b/2, cy + a/2 - t, cx + b/2, cy + a/2, strokePaint)
                activeCanvas.drawRect(cx - b/2, cy - a/2, cx - b/2 + t, cy + a/2 - t, strokePaint)
                
                drawSectionLabels(activeCanvas, cx, cy, b, a)
            }
            is SteelSectionType.CHS -> {
                val scale = 1.5f
                val d = section.outerDiameter.toFloat() * scale
                val t = section.thickness.toFloat() * scale
                val strokeOnlyPaint = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = t }
                activeCanvas.drawCircle(cx, cy, (d - t)/2, strokeOnlyPaint)
                activeCanvas.drawCircle(cx, cy, d/2, strokePaint)
                activeCanvas.drawCircle(cx, cy, d/2 - t, strokePaint)
                
                smallPaint.textAlign = Paint.Align.CENTER
                drawFormattedText(activeCanvas, "D=${section.outerDiameter}", cx, cy - d/2 - 10f, smallPaint)
                drawFormattedText(activeCanvas, "t=${section.thickness}", cx, cy + d/2 + 20f, smallPaint)
            }
            is SteelSectionType.RHS -> {
                val scale = 1.2f
                val w = section.width.toFloat() * scale
                val h = section.height.toFloat() * scale
                val t = section.thickness.toFloat() * scale
                val strokeOnlyPaint = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = t }
                activeCanvas.drawRect(cx - w/2 + t/2, cy - h/2 + t/2, cx + w/2 - t/2, cy + h/2 - t/2, strokeOnlyPaint)
                activeCanvas.drawRect(cx - w/2, cy - h/2, cx + w/2, cy + h/2, strokePaint)
                activeCanvas.drawRect(cx - w/2 + t, cy - h/2 + t, cx + w/2 - t, cy + h/2 - t, strokePaint)
                
                drawSectionLabels(activeCanvas, cx, cy, w, h)
            }
            is SteelSectionType.TSection -> {
                val scale = 1.2f
                val bf = section.flangeWidth.toFloat() * scale
                val tf = section.flangeThickness.toFloat() * scale
                val dw = section.webDepth.toFloat() * scale
                val tw = section.webThickness.toFloat() * scale
                val h = dw + tf
                
                // Flange (Top)
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx + bf/2, cy - h/2 + tf, paint)
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx + bf/2, cy - h/2 + tf, strokePaint)
                // Web (Vertical)
                activeCanvas.drawRect(cx - tw/2, cy - h/2 + tf, cx + tw/2, cy + h/2, paint)
                activeCanvas.drawRect(cx - tw/2, cy - h/2 + tf, cx + tw/2, cy + h/2, strokePaint)
                
                drawSectionLabels(activeCanvas, cx, cy, bf, h)
            }
            else -> drawPlaceholder(activeCanvas, RectF(cx - 100f, cy - 100f, cx + 100f, cy + 100f), section.displayName)
        }
        
        currentY = cy + 200f
        activeCanvas = checkNewPage(activeCanvas, 150f)
        drawFormattedText(activeCanvas, if (isAr) "Ø®ØµØ§Ø¦Øµ Ø§Ù„Ù‚Ø·Ø§Ø¹:" else "Section Properties:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 30f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ù…Ø³Ø§Ø­Ø©" else "Area", "${"%.2f".format(section.getArea())} mmÂ²"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø±ØªØ¨Ø©" else "Grade", (section as? SteelSectionType.ISection)?.grade?.displayName ?: (if (isAr) "Ù‚ÙŠØ§Ø³ÙŠ" else "Standard")); currentY += 20f
    }


    fun exportWarehouseReport(
        projectName: String,
        designCode: DesignCode,
        inputs: SteelWarehouseInputs,
        result: SteelWarehouseAnalysisResult,
        outputPath: String
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        
        try {
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, getString("تقرير تصميم مستودع معدني", "Steel Warehouse Design Report")) }
            
            // Sheet 1: General Arrangement & Visuals
            drawPage(document, pageNum++) { drawWarehouseVisualsSheet(it, inputs, result) }
            
            // Sheet 1.1: Structural Skeleton & Perspective
            drawPage(document, pageNum++) { drawWarehouseSkeletonSheet(it, inputs) }

            // Sheet 2: Technical Data & Sections
            drawPage(document, pageNum++) { drawWarehouseDetailsSheet(it, inputs, result) }
            
            // Sheet 2.1: Connection Details (Professional Sketches)
            drawPage(document, pageNum++) { drawWarehouseConnectionSketches(it) }

            // Sheet 3: Structural Analysis & Loads
            drawPage(document, pageNum++) { drawWarehouseAnalysisSheet(it, inputs, result) }

            // Sheet 3.1: Detailed Design Calculations & Formulas
            drawPage(document, pageNum++) { drawWarehouseDetailedCalculations(it, designCode, result) }
            
            // Sheet 4: Connections (Bolts & Welds)
            drawPage(document, pageNum++) { drawWarehouseConnectionsSheet(it, result) }
            
            // Sheet 5: Dictionary & Standards
            drawPage(document, pageNum++) { drawSteelSectionCatalog(it, designCode) }
            drawPage(document, pageNum++) { drawSteelConnectionsCatalog(it) }

            // Sheet 6: BOQ & Cost Analysis
            drawPage(document, pageNum++) { drawWarehouseBOQSheet(it, result) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    private fun drawWarehouseSkeletonSheet(canvas: Canvas, inputs: SteelWarehouseInputs) {
        currentY = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, getString("🏗️ منظور الهيكل الإنشائي", "🏗️ Structural Skeleton Perspective"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 80f
        
        val centerX = PAGE_WIDTH / 2f
        val centerY = currentY + 250f
        val scale = 15f
        
        // Simple Isometric Schematic
        val p = Paint(paintLineNormal).apply { color = Color.rgb(45, 110, 190); strokeWidth = 2f }
        val pGrid = Paint(p).apply { color = Color.LTGRAY; strokeWidth = 1f; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) }
        
        val sw = (inputs.span * scale).toFloat()
        val sl = (inputs.length * scale * 0.5f).toFloat() // Perspective shortening
        val sh = (inputs.eaveHeight * scale).toFloat()
        val rh = (inputs.ridgeHeight * scale).toFloat()
        
        fun project(x: Float, y: Float, z: Float): PointF {
            // Isometric projection: x' = (x - z) * cos(30), y' = (x + z) * sin(30) - y
            val px = centerX + (x - z) * 0.866f
            val py = centerY + (x + z) * 0.5f - y
            return PointF(px, py)
        }
        
        // Draw Grids on Ground
        for (i in 0..5) {
            val z = i * (sl / 5f)
            val p1 = project(0f, 0f, z); val p2 = project(sw, 0f, z)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, pGrid)
        }
        for (i in 0..2) {
            val x = i * (sw / 2f)
            val p1 = project(x, 0f, 0f); val p2 = project(x, 0f, sl)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, pGrid)
        }
        
        // Draw Frames
        val numFrames = 4
        for (i in 0 until numFrames) {
            val z = i * (sl / (numFrames - 1))
            val bL = project(0f, 0f, z); val eL = project(0f, sh, z)
            val bR = project(sw, 0f, z); val eR = project(sw, sh, z)
            val rd = project(sw/2f, rh, z)
            
            canvas.drawLine(bL.x, bL.y, eL.x, eL.y, p)
            canvas.drawLine(bR.x, bR.y, eR.x, eR.y, p)
            canvas.drawLine(eL.x, eL.y, rd.x, rd.y, p)
            canvas.drawLine(rd.x, rd.y, eR.x, eR.y, p)
            
            // Connect Purlins between frames
            if (i < numFrames - 1) {
                val zNext = (i + 1) * (sl / (numFrames - 1))
                val eLNext = project(0f, sh, zNext); val rdNext = project(sw/2f, rh, zNext); val eRNext = project(sw, sh, zNext)
                canvas.drawLine(eL.x, eL.y, eLNext.x, eLNext.y, pGrid)
                canvas.drawLine(rd.x, rd.y, rdNext.x, rdNext.y, pGrid)
                canvas.drawLine(eR.x, eR.y, eRNext.x, eRNext.y, pGrid)
            }
        }
        
        currentY += 500f
        drawProfessionalEngineeringTips(canvas, getString("هذا المنظور يوضح توزيع الإطارات والمدادات والتربيط الطولي للمستودع.", "This perspective shows the distribution of frames, purlins, and longitudinal bracing."))
    }

    private fun drawWarehouseConnectionSketches(canvas: Canvas) {
        currentY = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, getString("🔩 تفاصيل الوصلات الإنشائية", "🔩 Typical Structural Details"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 80f
        
        val quadW = (PAGE_WIDTH - 2 * MARGIN) / 2f
        
        // Detail 1: Column-Rafter Moment Connection
        drawConnectionDetail1(canvas, MARGIN.toFloat(), currentY, quadW)
        
        // Detail 2: Apex/Ridge Connection
        drawConnectionDetail2(canvas, MARGIN + quadW + 20f, currentY, quadW)
        
        currentY += 400f
        drawDisclaimer(canvas, currentY)
    }

    private fun drawConnectionDetail1(canvas: Canvas, x: Float, y: Float, w: Float) {
        val p = Paint(paintLineNormal).apply { strokeWidth = 3f }
        val pBlue = Paint(p).apply { color = Color.rgb(45, 110, 190); strokeWidth = 8f }
        
        drawFormattedText(canvas, "DETAIL 1: COLUMN-RAFTER", x, y - 20f, subtitlePaint)
        canvas.drawRect(x, y, x + w, y + 300f, Paint().apply { color = Color.rgb(250, 250, 250); style = Paint.Style.FILL })
        
        // Column
        canvas.drawLine(x + 60f, y + 20f, x + 60f, y + 280f, pBlue)
        canvas.drawLine(x + 100f, y + 20f, x + 100f, y + 280f, pBlue)
        
        // End Plate
        canvas.drawRect(x + 100f, y + 40f, x + 115f, y + 220f, Paint().apply { color = Color.BLACK; style = Paint.Style.FILL })
        
        // Rafter (Inclined)
        val path = Path().apply {
            moveTo(x + 115f, y + 50f); lineTo(x + 250f, y + 20f)
            moveTo(x + 115f, y + 180f); lineTo(x + 250f, y + 150f)
        }
        canvas.drawPath(path, pBlue)
        
        // Bolts
        val pBolt = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for (i in 0..3) {
            canvas.drawCircle(x + 107f, y + 70f + i * 40f, 6f, pBolt)
        }
        
        smallPaint.textAlign = Paint.Align.LEFT
        drawFormattedText(canvas, "End Plate 20mm", x + 130f, y + 220f, smallPaint)
        drawFormattedText(canvas, "High Strength Bolts M24", x + 130f, y + 245f, smallPaint)
    }

    private fun drawConnectionDetail2(canvas: Canvas, x: Float, y: Float, w: Float) {
        val p = Paint(paintLineNormal).apply { strokeWidth = 3f }
        val pBlue = Paint(p).apply { color = Color.rgb(45, 110, 190); strokeWidth = 8f }
        
        drawFormattedText(canvas, "DETAIL 2: APEX JOINT", x, y - 20f, subtitlePaint)
        canvas.drawRect(x, y, x + w, y + 300f, Paint().apply { color = Color.rgb(250, 250, 250); style = Paint.Style.FILL })
        
        // Apex Ridge
        val cx = x + w/2f
        canvas.drawLine(cx - 100f, y + 100f, cx, y + 40f, pBlue)
        canvas.drawLine(cx + 100f, y + 100f, cx, y + 40f, pBlue)
        canvas.drawLine(cx - 100f, y + 180f, cx, y + 120f, pBlue)
        canvas.drawLine(cx + 100f, y + 180f, cx, y + 120f, pBlue)
        
        // Gusset / End Plates
        canvas.drawLine(cx - 5f, y + 30f, cx - 5f, y + 140f, p)
        canvas.drawLine(cx + 5f, y + 30f, cx + 5f, y + 140f, p)
        
        // Bolts
        val pBolt = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        canvas.drawCircle(cx - 15f, y + 60f, 5f, pBolt); canvas.drawCircle(cx + 15f, y + 60f, 5f, pBolt)
        canvas.drawCircle(cx - 15f, y + 100f, 5f, pBolt); canvas.drawCircle(cx + 15f, y + 100f, 5f, pBolt)
        
        drawFormattedText(canvas, "Ridge Purlin Z200", cx - 40f, y + 220f, smallPaint)
    }

    private fun drawWarehouseVisualsSheet(canvas: Canvas, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        currentY = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, getString("📐 المساقط الأفقية والواجهات", "📐 GA Drawings & Elevations"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 60f
        
        // Split page into 4 quadrants for different views
        val quadW = (PAGE_WIDTH - 2 * MARGIN) / 2f
        val quadH = 150f
        
        // 1. Plan View (Top Left)
        drawWarehousePlanQuadrant(canvas, MARGIN.toFloat(), currentY, quadW, quadH, inputs)
        
        // 2. Front Elevation (Top Right)
        drawWarehouseFrontQuadrant(canvas, MARGIN + quadW + 20f, currentY, quadW, quadH, inputs)
        
        currentY += quadH + 80f
        
        // 3. Side Elevation (Bottom Left)
        drawWarehouseSideQuadrant(canvas, MARGIN.toFloat(), currentY, quadW, quadH, inputs)
        
        // 4. Typical Section (Bottom Right)
        drawWarehouseSectionQuadrant(canvas, MARGIN + quadW + 20f, currentY, quadW, quadH, inputs, result)
    }

    private fun drawSectionLabels(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float) {
        val paint = Paint(smallPaint).apply { textAlign = Paint.Align.CENTER }
        // Horizontal label (B)
        drawFormattedText(canvas, "B", cx, cy - h / 2 - 15f, paint)
        // Vertical label (H)
        canvas.save()
        canvas.rotate(-90f, cx - w / 2 - 25f, cy)
        drawFormattedText(canvas, "H", cx - w / 2 - 25f, cy, paint)
        canvas.restore()
    }

    private fun drawWarehousePlanQuadrant(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, inputs: SteelWarehouseInputs) {
        val paint = Paint(paintLineNormal).apply { color = Color.BLACK; style = Paint.Style.STROKE }
        drawFormattedText(canvas, getString("المسقط الأفقي (Plan)", "PLAN VIEW"), x, y - 10f, subtitlePaint)
        canvas.drawRect(x, y, x + w, y + h, paint)
        
        val numBays = (inputs.length / inputs.baySpacing).toInt().coerceAtLeast(1)
        val bayW = w / numBays
        for (i in 0..numBays) {
            val gx = x + i * bayW
            canvas.drawLine(gx, y, gx, y + h, Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f })
            // Bracing indication in first/last bays
            if (i == 0 || i == numBays - 1) {
                canvas.drawLine(gx, y, gx + bayW, y + h, Paint().apply { color = primaryColor; strokeWidth = 1f; alpha = 100 })
                canvas.drawLine(gx + bayW, y, gx, y + h, Paint().apply { color = primaryColor; strokeWidth = 1f; alpha = 100 })
            }
        }
    }

    private fun drawWarehouseFrontQuadrant(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, inputs: SteelWarehouseInputs) {
        drawFormattedText(canvas, getString("الواجهة الأمامية (Front)", "FRONT ELEVATION"), x, y - 10f, subtitlePaint)
        val groundY = y + h - 10f
        val eh = (inputs.eaveHeight / inputs.ridgeHeight * h * 0.7).toFloat()
        val rh = h * 0.7f
        val spanW = w * 0.8f
        val sx = x + (w - spanW) / 2
        
        val path = Path().apply {
            moveTo(sx, groundY); lineTo(sx, groundY - eh)
            lineTo(sx + spanW/2, groundY - rh); lineTo(sx + spanW, groundY - eh)
            lineTo(sx + spanW, groundY)
        }
        canvas.drawPath(path, paintLineHeavy)
    }

    private fun drawWarehouseSideQuadrant(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, inputs: SteelWarehouseInputs) {
        drawFormattedText(canvas, getString("الواجهة الجانبية (Side)", "SIDE ELEVATION"), x, y - 10f, subtitlePaint)
        val groundY = y + h - 10f
        canvas.drawLine(x, groundY, x + w, groundY, paintLineNormal)
        val numBays = (inputs.length / inputs.baySpacing).toInt().coerceAtLeast(1)
        val bayW = w / numBays
        for (i in 0..numBays) {
            val gx = x + i * bayW
            canvas.drawLine(gx, groundY, gx, groundY - 40f, paintSteelMember)
        }
    }

    private fun drawWarehouseSectionQuadrant(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        drawFormattedText(canvas, getString("القطاع العرضي A-A", "CROSS SECTION A-A"), x, y - 10f, subtitlePaint)
        val groundY = y + h - 10f
        val eh = (inputs.eaveHeight / inputs.ridgeHeight * h * 0.7).toFloat()
        val rh = h * 0.7f
        val spanW = w * 0.8f
        val sx = x + (w - spanW) / 2
        
        canvas.drawLine(sx, groundY, sx, groundY - eh, paintSteelMember)
        canvas.drawLine(sx + spanW, groundY, sx + spanW, groundY - eh, paintSteelMember)
        canvas.drawLine(sx, groundY - eh, sx + spanW/2, groundY - rh, paintSteelMember)
        canvas.drawLine(sx + spanW, groundY - eh, sx + spanW/2, groundY - rh, paintSteelMember)
        
        // Mezzanine Floors
        if (inputs.numberOfMezzanines > 0) {
            val pMezz = Paint(paintLineThin).apply { color = Color.RED; strokeWidth = 1f }
            for (i in 1..inputs.numberOfMezzanines) {
                val mh = (inputs.mezzanineHeight * i / inputs.ridgeHeight * h * 0.7).toFloat()
                canvas.drawLine(sx, groundY - mh, sx + spanW, groundY - mh, pMezz)
                drawFormattedText(canvas, "FL ${i}", sx + spanW + 5f, groundY - mh, smallPaint)
            }
        }

        // Tags
        smallPaint.textAlign = Paint.Align.CENTER
        drawFormattedText(canvas, "C1: ${result.mainFrame.columnSection.sectionName}", x + w/2, groundY + 15f, smallPaint)
    }

    private fun drawWarehouseDetailsSheet(canvas: Canvas, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        currentY = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, getString("📋 تفاصيل العناصر والقطاعات", "📋 Member & Section Schedules"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 60f
        
        // Mezzanine Info
        if (inputs.numberOfMezzanines > 0) {
            drawFormattedText(canvas, getString("بيانات الميزانين (Mezzanine Data):", "Mezzanine Floor Data:"), MARGIN.toFloat(), currentY, subtitlePaint)
            currentY += 30f
            drawInfoRow(canvas, currentY, getString("عدد الأدوار", "Floor Count"), "${inputs.numberOfMezzanines}")
            drawInfoRow(canvas, currentY + 25f, getString("إجمالي مساحة الميزانين", "Total Mezzanine Area"), "%.1f m²".format(result.mezzanineArea))
            drawInfoRow(canvas, currentY + 50f, getString("وزن حديد الميزانين", "Mezzanine Steel Weight"), "%.2f Tons".format(result.mezzanineSteelWeight))
            currentY += 100f
        }

        // Main Frame Table
        drawFormattedText(canvas, getString("العناصر الأساسية (Primary Members):", "Primary Frame Schedule:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 30f
        
        val rows = listOf(
            Triple("C1", getString("عمود رئيسي", "Main Column"), result.mainFrame.columnSection),
            Triple("R1", getString("عارضة (Rafter)", "Main Rafter"), result.mainFrame.rafterSection)
        )
        
        drawSectionTable(canvas, rows)
        currentY += 120f
        
        // Secondary Members
        drawFormattedText(canvas, getString("العناصر الثانوية (Secondary):", "Secondary Members:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 30f
        drawInfoRow(canvas, currentY, getString("المدادات (Purlins)", "Purlin Section"), result.secondaryMembers.purlinSection.sectionName)
        drawInfoRow(canvas, currentY + 25f, getString("مدادات الجوانب", "Girt Section"), result.secondaryMembers.girtSection.sectionName)
        drawInfoRow(canvas, currentY + 50f, getString("التربيط (Bracing)", "Bracing Section"), result.secondaryMembers.bracingSection.sectionName)
    }

    private fun drawSectionTable(canvas: Canvas, rows: List<Triple<String, String, SteelSectionType>>) {
        val tableTop = currentY
        val colWidths = floatArrayOf(80f, 150f, 250f)
        var xStart = MARGIN.toFloat()
        
        canvas.drawRect(MARGIN.toFloat(), tableTop, (PAGE_WIDTH - MARGIN).toFloat(), tableTop + 25f, Paint().apply { color = colorDraft })
        val hPaint = Paint(bodyPaint).apply { color = Color.WHITE; isFakeBoldText = true }
        
        drawFormattedText(canvas, "MARK", xStart + 5f, tableTop + 17f, hPaint); xStart += colWidths[0]
        drawFormattedText(canvas, "TYPE", xStart + 5f, tableTop + 17f, hPaint); xStart += colWidths[1]
        drawFormattedText(canvas, "SECTION", xStart + 5f, tableTop + 17f, hPaint)
        
        currentY += 25f
        rows.forEach { (mark, type, section) ->
            canvas.drawLine(MARGIN.toFloat(), currentY + 20f, (PAGE_WIDTH - MARGIN).toFloat(), currentY + 20f, paintLineThin)
            drawFormattedText(canvas, mark, MARGIN + 5f, currentY + 15f, bodyPaint)
            drawFormattedText(canvas, type, MARGIN + 85f, currentY + 15f, bodyPaint)
            drawFormattedText(canvas, section.sectionName, MARGIN + 235f, currentY + 15f, bodyPaint.apply { isFakeBoldText = true })
            currentY += 25f
        }
    }

    private fun drawWarehouseAnalysisSheet(canvas: Canvas, inputs: SteelWarehouseInputs, result: SteelWarehouseAnalysisResult) {
        currentY = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, getString("📊 تحليل الإجهادات والأحمال", "📊 Stress Analysis & Loading"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 60f
        
        // Load Explanation
        if (inputs.numberOfMezzanines > 0) {
            val pNote = Paint(bodyPaint).apply { color = Color.rgb(200, 0, 0); textSize = 11f }
            drawFormattedText(canvas, getString("ملاحظة: تم احتساب أحمال الميزانين الإضافية في تصميم الأعمدة والقواعد.", "Note: Mezzanine gravity loads are factored into column and footing design."), MARGIN.toFloat(), currentY, pNote)
            currentY += 40f
        }

        // 1. Stress Utilization Ratios
        drawFormattedText(canvas, getString("نسب استهلاك القوى (Utilization):", "Stress Utilization Ratios:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 35f
        drawUtilizationBar(canvas, currentY, getString("تداخل العزوم (Axial + Bending)", "Moment Interaction"), result.mainFrame.utilizationMoment); currentY += 25f
        drawUtilizationBar(canvas, currentY, getString("إجهاد القص (Shear Stress)", "Shear Stress Ratio"), result.mainFrame.utilizationShear); currentY += 25f
        drawUtilizationBar(canvas, currentY, getString("القوى المحورية (Axial Force)", "Axial Force Ratio"), result.mainFrame.utilizationAxial); currentY += 45f

        // 2. Critical Forces
        drawFormattedText(canvas, getString("القوى التصميمية القصوى (Max Forces):", "Maximum Design Forces:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 30f
        drawInfoRow(canvas, currentY, getString("أقصى عزم (M max)", "Max Moment (M max)"), "%.1f kN.m".format(result.mainFrame.maxMoment)); currentY += 20f
        drawInfoRow(canvas, currentY, getString("أقصى قص (V max)", "Max Shear (V max)"), "%.1f kN".format(result.mainFrame.maxShear)); currentY += 20f
        drawInfoRow(canvas, currentY, getString("أقصى قوة محورية (P max)", "Max Axial (P max)"), "%.1f kN".format(result.mainFrame.maxAxial)); currentY += 45f

        // 3. Deflection Check
        drawFormattedText(canvas, getString("التحقق من الترخيم (Deflection):", "Deflection Check:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 30f
        drawInfoRow(canvas, currentY, getString("الترخيم المحسوب", "Calculated Deflection"), "%.2f mm".format(result.mainFrame.maxDeflection))
        drawInfoRow(canvas, currentY + 25f, getString("الترخيم المسموح", "Allowable Deflection"), "%.2f mm".format(result.mainFrame.allowableDeflection))
    }

    private fun drawWarehouseConnectionsSheet(canvas: Canvas, result: SteelWarehouseAnalysisResult) {
        currentY = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, getString("🔗 تفاصيل الوصلات (المسامير واللحام)", "🔗 Connection Details (Bolts & Welds)"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 60f
        
        drawFormattedText(canvas, getString("1. وصلة القاعدة (Base Plate):", "1. Base Plate Connection:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 30f
        drawInfoRow(canvas, currentY, getString("نوع البراغي", "Anchor Bolts"), "4xM24 Grade 8.8")
        drawInfoRow(canvas, currentY + 25f, getString("مقاس اللوحة", "Plate Size"), "400x400x20 mm")
        
        currentY += 80f
        drawFormattedText(canvas, getString("2. وصلات الجمالون (Frame Joints):", "2. Frame Joints:"), MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 30f
        drawInfoRow(canvas, currentY, getString("لحام الموقع", "Site Welding"), "6mm Fillet (E70XX)")
        drawInfoRow(canvas, currentY + 25f, getString("مسامير الربط", "Connection Bolts"), "High Strength M20")
    }

    private fun drawWarehouseBOQSheet(canvas: Canvas, result: SteelWarehouseAnalysisResult) {
        currentY = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, getString("💰 تحليل الكميات والتكلفة", "💰 BOQ & Cost Estimation"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 60f
        
        drawInfoRow(canvas, currentY, getString("إجمالي وزن الحديد", "Total Steel Weight"), "%.2f Tons".format(result.totalWeight))
        drawInfoRow(canvas, currentY + 30f, getString("معدل وزن المتر المسطح", "Weight per Area"), "%.1f kg/m²".format(result.weightPerM2))
        
        currentY += 80f
        titlePaint.color = primaryColor
        drawFormattedText(canvas, getString("التكلفة التقديرية الإجمالية", "TOTAL ESTIMATED PROJECT COST"), MARGIN.toFloat(), currentY, titlePaint)
        currentY += 40f
        val currency = settingsManager.currency
        drawFormattedText(canvas, "%,.0f $currency".format(result.estimatedTotalCost), PAGE_WIDTH/2f, currentY, titlePaint.apply { textSize = 28f; textAlign = Paint.Align.CENTER })
        
        currentY += 100f
        drawDisclaimer(canvas, currentY)
    }

    private fun drawWarehouseDetailedCalculations(canvas: Canvas, designCode: DesignCode, result: SteelWarehouseAnalysisResult) {
        currentY = MARGIN.toFloat() + 20f
        val isAr = isAr()
        val codeName = if (designCode.toString().contains("AISC")) "AISC 360-16" else "ECP 205"
        val title = if (isAr) "📋 الحسابات التصميمية التفصيلية ($codeName)" else "📋 Detailed Design Calculations ($codeName)"
        drawFormattedText(canvas, title, MARGIN.toFloat(), currentY, titlePaint)
        currentY += 60f

        // 1. Design Methodology
        drawFormattedText(canvas, if (isAr) "1. منهجية التصميم (Methodology):" else "1. Design Methodology:", MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 25f
        val methodology = if (isAr) {
            "• تم التصميم وفقاً لطريقة معامل المقاومة والحمل (LRFD).\n" +
            "• الكود المرجعي: $codeName.\n" +
            "• تم التحقق من استقرار المنشأ (P-Delta effects) والترخيم (Serviceability)."
        } else {
            "• Design method: Load and Resistance Factor Design (LRFD).\n" +
            "• Reference Code: $codeName.\n" +
            "• Stability (P-Delta) and Serviceability limits verified."
        }
        drawMultilineText(canvas, methodology, MARGIN.toFloat() + 10f, currentY, bodyPaint)
        currentY += 80f

        // 2. Section Classification
        drawFormattedText(canvas, if (isAr) "2. تصنيف القطاعات (Section Classification):" else "2. Section Classification:", MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 25f
        val classification = if (isAr) {
            "• نسبة العرض للسمك (λ = b/t) لشفة العمود والرافعة.\n" +
            "• حدود القطاع المدمج (λp) وغير المدمج (λr) وفقاً لجدول B4.1 من AISC.\n" +
            "• الحالة: جميع القطاعات مدمجة (Compact Sections)."
        } else {
            "• Width-to-thickness ratio (λ = b/t) for flange and web.\n" +
            "• Limits for Compact (λp) and Non-Compact (λr) per AISC Table B4.1.\n" +
            "• Result: All sections classified as COMPACT."
        }
        drawMultilineText(canvas, classification, MARGIN.toFloat() + 10f, currentY, bodyPaint)
        currentY += 80f

        // 3. Member Interaction Formulas
        drawFormattedText(canvas, if (isAr) "3. معادلات تداخل القوى (Interaction Formulas):" else "3. Member Interaction Formulas:", MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 35f
        
        // Render Formula (Simplified LaTeX style)
        val formula = "Pu / (Φ Pn) + 8/9 * [ Mux / (Φ Mnx) + Muy / (Φ Mny) ] ≤ 1.0"
        val paintFormula = Paint(bodyPaint).apply { textSize = 14f; isFakeBoldText = true; color = primaryColor }
        canvas.drawRect(MARGIN + 5f, currentY - 20f, PAGE_WIDTH - MARGIN - 5f, currentY + 15f, Paint().apply { color = Color.parseColor("#F5F5F5") })
        drawFormattedText(canvas, formula, PAGE_WIDTH / 2f, currentY, paintFormula.apply { textAlign = Paint.Align.CENTER })
        currentY += 45f

        val interactionEx = if (isAr) {
            "• يتم تطبيق هذه المعادلة للتحقق من الجمع بين قوى الضغط المحورية وعزوم الانحناء.\n" +
            "• Φ = 0.9 (معامل خفض المقاومة للحديد)."
        } else {
            "• This interaction equation combines axial compression and flexural stresses.\n" +
            "• Φ = 0.9 (Resistance factor for structural steel)."
        }
        drawMultilineText(canvas, interactionEx, MARGIN.toFloat() + 10f, currentY, bodyPaint)
        currentY += 70f

        // 4. Shear & Deflection
        drawFormattedText(canvas, if (isAr) "4. القص والترخيم (Shear & Deflection):" else "4. Shear & Deflection:", MARGIN.toFloat(), currentY, subtitlePaint)
        currentY += 25f
        val shearDef = if (isAr) {
            "• Vn = 0.6 * Fy * Aw * Cv (سعة القص للويب).\n" +
            "• حدود الترخيم المسموح بها: L/180 للرافعات (Rafters) و H/150 للأعمدة (Columns)."
        } else {
            "• Vn = 0.6 * Fy * Aw * Cv (Web shear capacity).\n" +
            "• Deflection limits: L/180 for Rafters, H/150 for Columns."
        }
        drawMultilineText(canvas, shearDef, MARGIN.toFloat() + 10f, currentY, bodyPaint)
        
        // 5. Multi-story / Mezzanine Check
        if (result.mezzanineArea > 0) {
            currentY += 80f
            drawFormattedText(canvas, if (isAr) "5. تحليل الميزانين (Mezzanine Analysis):" else "5. Mezzanine Analysis:", MARGIN.toFloat(), currentY, subtitlePaint)
            currentY += 25f
            val mezzText = if (isAr) {
                "• تم تصميم الميزانين لمقاومة أحمال حية قدرها ${result.mezzanineArea} kN/m².\n" +
                "• تم توزيع أحمال الميزانين كأحمال مركزة (Point Loads) على الأعمدة الرئيسية.\n" +
                "• تم التحقق من الانبعاج الجانبي للأعمدة تحت تأثير الأحمال الرأسية المركبة."
            } else {
                "• Mezzanine designed for live loads of ${result.mezzanineArea} kN/m².\n" +
                "• Mezzanine loads applied as concentrated point loads on main columns.\n" +
                "• Column buckling verified under combined axial gravity loads."
            }
            drawMultilineText(canvas, mezzText, MARGIN.toFloat() + 10f, currentY, bodyPaint)
        }
        
        currentY += 80f
        drawProfessionalEngineeringTips(canvas, if (isAr) "ملاحظة: تم تصميم كافة الوصلات لضمان نقل العزوم بالكامل (Moment Connections)." else "Note: All joints designed as fully restrained Moment Connections.")
    }

        private fun drawDetailedCalculations(canvas: Canvas, result: AdvancedColumnResult, code: DesignCode) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ§® Ù…Ø¹Ø§Ø¯Ù„Ø§Øª Ø§Ù„ØªØµÙ…ÙŠÙ… (${code.displayName})" else "ðŸ§® Design Formulas (${code.displayName})"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        val capacityFormula = if (isAr) 
            "Ù‚ÙˆØ© Ø§Ù„ØªØ­Ù…Ù„ Pn = 0.8 * [0.67 * fcu * (Ag - Ast) + fy * Ast]" 
            else "Capacity Pn = 0.8 * [0.67 * fcu * (Ag - Ast) + fy * Ast]"

        val calculations = """
            $capacityFormula
            Ag = ${"%.0f".format(result.columnType.getGrossArea())} mmÂ²
            Ast = ${"%.0f".format(result.reinforcementResult.astProvided)} mmÂ²
            Result = ${"%.1f".format(result.axialCapacity)} kN
        """.trimIndent()
        
        drawMultilineText(activeCanvas, calculations, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, bodyPaint)
        currentY += 120f
        
        // Add ECP specific notes from checklist
        activeCanvas = checkNewPage(activeCanvas, 100f)
        val noteTitle = if (isAr) "ðŸ“Œ Ù…Ù„Ø§Ø­Ø¸Ø§Øª Ø§Ù„ÙƒÙˆØ¯ Ø§Ù„Ù…ØµØ±ÙŠ (ECP):" else "ðŸ“Œ Design Code Notes (ECP):"
        drawFormattedText(activeCanvas, noteTitle, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        
        val notes = if (isAr) listOf(
            "â€¢ Ù†Ø³Ø¨Ø© Ø§Ù„ØªØ³Ù„ÙŠØ­ Ø§Ù„Ø¯Ù†ÙŠØ§ (Î¼ min) = 0.8% Ù…Ù† Ø§Ù„Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ù…Ø·Ù„ÙˆØ¨Ø©.",
            "â€¢ Ø§Ù„Ù…Ø³Ø§ÙØ© Ø¨ÙŠÙ† Ø§Ù„ÙƒØ§Ù†Ø§Øª Ù„Ø§ ØªØ²ÙŠØ¯ Ø¹Ù† 200 Ù…Ù… Ø£Ùˆ Ø£Ù‚Ù„ Ø¨ÙØ¹Ø¯ Ù„Ù„Ø¹Ù…ÙˆØ¯.",
            "â€¢ ØªÙ… Ø§Ù„ØªØ­Ù‚Ù‚ Ù…Ù† ØªØ£Ø«ÙŠØ± Ø§Ù„Ù†Ø­Ø§ÙØ© (Slenderness) ÙˆØ§Ù„Ø¹Ø²ÙˆÙ… Ø§Ù„Ø¥Ø¶Ø§ÙÙŠØ©."
        ) else listOf(
            "â€¢ Min Reinforcement Ratio (Î¼ min) = 0.8% of required area.",
            "â€¢ Stirrup spacing <= 200mm or minimum column dimension.",
            "â€¢ Slenderness effects and additional moments verified."
        )
        
        notes.forEach { note ->
            drawFormattedText(activeCanvas, note, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, bodyPaint)
            currentY += 20f
        }
    }

    private fun drawBeamCalculations(canvas: Canvas, result: AdvancedBeamResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ§® Ø­Ø³Ø§Ø¨Ø§Øª Ø§Ù„Ø¹Ø²Ù… ÙˆØ§Ù„Ù‚Øµ" else "ðŸ§® Flexural & Shear Calculations"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        val flexText = if (isAr)
            "Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ù…Ø·Ù„ÙˆØ¨Ø© As = Mu / (0.87 * fy * d)\nØ§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ù…ÙˆÙØ± = ${"%.0f".format(result.flexureResult.astProvided)} mmÂ²"
            else "Required As = Mu / (0.87 * fy * d)\nProvided Steel = ${"%.0f".format(result.flexureResult.astProvided)} mmÂ²"
            
        drawMultilineText(activeCanvas, flexText, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, bodyPaint)
        currentY += 60f
        
        activeCanvas = checkNewPage(activeCanvas, 100f)
        val shearTitle = if (isAr) "ØªØ­Ù‚Ù‚ Ø§Ù„Ù‚Øµ (Shear Check):" else "Shear Verification:"
        drawFormattedText(activeCanvas, shearTitle, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        
        val shearText = if (isAr)
            "Ø¥Ø¬Ù‡Ø§Ø¯ Ø§Ù„Ù‚Øµ Ø§Ù„Ø£Ù‚ØµÙ‰ (q_max) ØªÙ… Ø§Ù„ØªØ­Ù‚Ù‚ Ù…Ù†Ù‡.\nØ§Ù„ÙƒØ§Ù†Ø§Øª Ø§Ù„Ù…ÙˆÙØ±Ø©: T${result.shearResult.stirrupDiameter.toInt()} ÙƒÙ„ ${result.shearResult.stirrupSpacing.toInt()} Ù…Ù…."
            else "Max Shear Stress (q_max) verified.\nStirrups: T${result.shearResult.stirrupDiameter.toInt()} @ ${result.shearResult.stirrupSpacing.toInt()} mm."
            
        drawMultilineText(activeCanvas, shearText, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, bodyPaint)
    }

    private fun drawSlabCalculations(canvas: Canvas, result: AdvancedSlabResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ§® Ø­Ø³Ø§Ø¨Ø§Øª Ø§Ù„ØªØµÙ…ÙŠÙ… Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠ" else "ðŸ§® Structural Design Calculations"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawFormattedText(activeCanvas, if (isAr) "ØªØµÙ…ÙŠÙ… Ø§Ù„Ø¹Ø²ÙˆÙ…:" else "Flexural Design:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ù…Ø·Ù„ÙˆØ¨Ø©" else "Required Steel Area (As_req)", "${"%.1f".format(result.flexureResult.requiredReinforcement)} mmÂ²/m"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù…Ø³Ø§Ø­Ø© Ø§Ù„Ø­Ø¯ÙŠØ¯ Ø§Ù„Ù…ÙˆÙØ±Ø©" else "Provided Steel Area (As_prov)", "${"%.1f".format(result.flexureResult.providedReinforcement)} mmÂ²/m"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ø³Ù…Ùƒ Ø§Ù„Ø£Ø¯Ù†Ù‰ (h_min)" else "Minimum Thickness (h_min)", "${"%.1f".format(result.flexureResult.minThickness)} mm"); currentY += 35f

        result.punchingShearCheck?.let { punching ->
            activeCanvas = checkNewPage(activeCanvas, 120f)
            drawFormattedText(activeCanvas, if (isAr) "Ø§Ù„ØªØ­Ù‚Ù‚ Ù…Ù† Ø§Ù„Ù‚Øµ Ø§Ù„Ø«Ø§Ù‚Ø¨ (Punching):" else "Punching Shear Verification:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ù…Ø­ÙŠØ· Ø§Ù„Ø­Ø±Ø¬ (u0)" else "Critical Perimeter (u0)", "${"%.0f".format(punching.criticalPerimeter)} mm"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ø¥Ø¬Ù‡Ø§Ø¯ Ø§Ù„Ù‚Øµ Ø§Ù„ØªØµÙ…ÙŠÙ…ÙŠ" else "Design Shear Stress (v_sd)", "${"%.2f".format(punching.appliedShear / (punching.criticalPerimeter * result.flexureResult.minThickness * 0.8))} MPa"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ù…Ù‚Ø§ÙˆÙ…Ø© Ø§Ù„Ø®Ø±Ø³Ø§Ù†Ø© Ø§Ù„Ù‚ØµÙˆÙ‰" else "Concrete Capacity (v_rdc)", "${"%.2f".format(punching.shearCapacity / (punching.criticalPerimeter * result.flexureResult.minThickness * 0.8))} MPa"); currentY += 35f
        }

        activeCanvas = checkNewPage(activeCanvas, 100f)
        drawFormattedText(activeCanvas, if (isAr) "ÙØ­ÙˆØµØ§Øª Ø­Ø§Ù„Ø© Ø§Ù„Ø®Ø¯Ù…Ø© (Serviceability):" else "Serviceability Checks:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„ØªØ±Ø®ÙŠÙ… Ø§Ù„Ù…Ø­Ø³ÙˆØ¨" else "Calculated Deflection", "${"%.2f".format(result.deflectionCheck.calculatedDeflection)} mm"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„ØªØ±Ø®ÙŠÙ… Ø§Ù„Ù…Ø³Ù…ÙˆØ­" else "Allowable Deflection", "${"%.2f".format(result.deflectionCheck.allowableDeflection)} mm"); currentY += 20f
    }


    private fun drawSlabSectionDetail(canvas: Canvas, result: AdvancedSlabResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“ ØªÙØ§ØµÙŠÙ„ Ø§Ù„Ù…Ù‚Ø·Ø¹ Ø§Ù„Ø¹Ø±Ø¶ÙŠ Ù„Ù„Ø¨Ù„Ø§Ø·Ø©" else "ðŸ“ Slab Cross-Section Detail"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 60f
        
        val centerX = PAGE_WIDTH / 2f
        val slabW = 400f
        val slabH = 40f
        val startX = centerX - slabW / 2
        
        activeCanvas = checkNewPage(activeCanvas, 300f)
        val y = currentY
        
        val concretePaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.FILL }
        val outlinePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        val rebarPaint = Paint().apply { color = Color.RED; strokeWidth = 2f }
        
        // Draw Main Slab
        activeCanvas.drawRect(startX, y, startX + slabW, y + slabH, concretePaint)
        activeCanvas.drawRect(startX, y, startX + slabW, y + slabH, outlinePaint)
        
        when (val type = result.slabType) {
            is SlabType.FlatSlab -> {
                // Draw Drop Panel
                val dpW = slabW * 0.4f
                val dpH = type.dropPanelThickness.toFloat() / 2f // Scale
                activeCanvas.drawRect(centerX - dpW/2, y + slabH, centerX + dpW/2, y + slabH + dpH, concretePaint)
                activeCanvas.drawRect(centerX - dpW/2, y + slabH, centerX + dpW/2, y + slabH + dpH, outlinePaint)
                
                // Draw Column
                val colW = type.columnSize.toFloat() / 5f // Scale
                activeCanvas.drawRect(centerX - colW/2, y + slabH + dpH, centerX + colW/2, y + slabH + dpH + 60f, concretePaint)
                activeCanvas.drawRect(centerX - colW/2, y + slabH + dpH, centerX + colW/2, y + slabH + dpH + 60f, outlinePaint)
                
                drawFormattedText(activeCanvas, "Drop Panel: ${type.dropPanelThickness}mm", centerX + dpW/2 + 10f, y + slabH + dpH/2, smallPaint)
            }
            is SlabType.PostTensioned -> {
                // Draw Tendon Profile (Parabolic)
                val tendonPath = Path()
                tendonPath.moveTo(startX, y + 10f)
                tendonPath.quadTo(centerX, y + slabH - 10f, startX + slabW, y + 10f)
                rebarPaint.color = Color.BLUE
                rebarPaint.style = Paint.Style.STROKE
                activeCanvas.drawPath(tendonPath, rebarPaint)
                drawFormattedText(activeCanvas, "PT Tendon Profile", centerX, y - 5f, smallPaint.apply { textAlign = Paint.Align.CENTER })
            }
            else -> {}
        }
        
        // Bottom Reinforcement
        rebarPaint.color = Color.RED
        activeCanvas.drawLine(startX + 10f, y + slabH - 8f, startX + slabW - 10f, y + slabH - 8f, rebarPaint)
        
        currentY += 200f
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ø³Ù…Ùƒ Ø§Ù„Ø¨Ù„Ø§Ø·Ø© Ø§Ù„ÙƒÙ„ÙŠ (ts)" else "Total Slab Thickness (ts)", "${result.flexureResult.minThickness} mm")
        currentY += 40f
    }

    private fun drawSteelCalculations(canvas: Canvas, result: SteelMemberResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ§® ØªØ¯Ø§Ø®Ù„ Ø§Ù„Ù‚ÙˆÙ‰ ÙˆØ§Ø³ØªÙ‚Ø±Ø§Ø± Ø§Ù„Ø¹Ø¶Ùˆ Ø§Ù„Ø¥Ù†Ø´Ø§Ø¦ÙŠ" else "ðŸ§® Steel Member Interaction & Stability"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawFormattedText(activeCanvas, if (isAr) "Ù…Ø¹Ø§Ø¯Ù„Ø§Øª Ø§Ù„ØªØ¯Ø§Ø®Ù„ (Interaction Formulas):" else "Interaction Formulas:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        val formula = if (result.utilizationRatio > 0.2) 
            "Pu/Î¦Pn + (8/9)(Mux/Î¦Mnx + Muy/Î¦Mny) â‰¤ 1.0" 
            else "Pu/2Î¦Pn + (Mux/Î¦Mnx + Muy/Î¦Mny) â‰¤ 1.0"
        drawFormattedText(activeCanvas, formula, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat() + 10f, currentY, bodyPaint); currentY += 30f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "Ù†Ø³Ø¨Ø© Ø§Ù„ØªØ¯Ø§Ø®Ù„ Ø§Ù„Ø¥Ø¬Ù…Ø§Ù„ÙŠØ©" else "Total Interaction Ratio", "%.3f".format(result.utilizationRatio)); currentY += 35f
        
        result.bucklingCheck?.let { buckling ->
            activeCanvas = checkNewPage(activeCanvas, 120f)
            drawFormattedText(activeCanvas, if (isAr) "Ø§Ù„Ø§Ù†Ø¨Ø¹Ø§Ø¬ ÙˆØ§Ù„Ø§Ø³ØªÙ‚Ø±Ø§Ø± (Buckling):" else "Buckling & Stability:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ù†Ø³Ø¨Ø© Ø§Ù„Ù†Ø­Ø§ÙØ© (Î»)" else "Slenderness Ratio (Î»)", "%.1f".format(buckling.slendernessRatio)); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ø¥Ø¬Ù‡Ø§Ø¯ Ø§Ù„Ø§Ù†Ø¨Ø¹Ø§Ø¬ Ø§Ù„Ø­Ø±Ø¬ (Fcr)" else "Critical Buckling Stress (Fcr)", "%.1f MPa".format(buckling.criticalStress)); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ù†Ù…Ø· Ø§Ù„Ø§Ù†Ø¨Ø¹Ø§Ø¬" else "Buckling Mode", buckling.bucklingMode.name); currentY += 35f
        }
        
        result.deflectionCheck?.let { defl ->
            activeCanvas = checkNewPage(activeCanvas, 120f)
            drawFormattedText(activeCanvas, if (isAr) "Ø§Ù„ØªØ­Ù‚Ù‚ Ù…Ù† Ø§Ù„ØªØ±Ø®ÙŠÙ… (Deflection):" else "Deflection Check:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ø£Ù‚ØµÙ‰ ØªØ±Ø®ÙŠÙ…" else "Max Deflection", "%.2f mm".format(defl.calculatedDeflection)); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "Ø§Ù„Ù…Ø³Ù…ÙˆØ­ (L/${(defl.allowableDeflection).toInt()})" else "Allowable (L/${(defl.allowableDeflection).toInt()})", "%.2f mm".format(defl.allowableDeflection)); currentY += 20f
        }
    }

    private fun drawInventoryAnalysis(canvas: Canvas, analysis: InventoryAnalysisResult) {
        var y = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, "ðŸ“¦ Stock Analysis", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Inventory Sufficiency", if (analysis.isSufficient) "OK" else "ORDER NEEDED"); y += 25f
        drawInfoRow(canvas, y, "Total Project Weight", "${"%.2f".format(analysis.totalWeight)} Tons"); y += 25f
        drawInfoRow(canvas, y, "Waste Length", "${"%.1f".format(analysis.wasteLength)} m")
    }

    private fun drawMomentShearDiagrams(canvas: Canvas, diagrams: MomentShearDiagrams) {
        var y = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, "ðŸ“ˆ Structural Analysis Diagrams (Beam)", MARGIN.toFloat(), y, titlePaint); y += 120f
        
        val diagramW = PAGE_WIDTH - 2 * MARGIN
        val diagramH = 120f
        
        // --- 1. Moment Diagram ---
        drawFormattedText(canvas, "Bending Moment Diagram (BMD)", MARGIN.toFloat(), y - 10f, subtitlePaint)
        drawDiagramContainer(canvas, MARGIN.toFloat(), y, diagramW.toFloat(), diagramH)
        
        if (diagrams.momentPoints.isNotEmpty()) {
            val maxM = diagrams.momentPoints.maxOf { abs(it.second) }.coerceAtLeast(1.0)
            val path = Path()
            val startX = MARGIN.toFloat()
            val baselineY = y + diagramH / 2f
            
            diagrams.momentPoints.forEachIndexed { i, pt ->
                val px = startX + (pt.first.toFloat() / diagrams.momentPoints.last().first.toFloat()) * diagramW
                val py = baselineY + (pt.second.toFloat() / maxM.toFloat()) * (diagramH / 2f)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, Paint().apply { color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 2.5f })
            drawFormattedText(canvas, "Max M: ${"%.1f".format(diagrams.momentPoints.maxByOrNull { abs(it.second) }?.second ?: 0.0)} kN.m", MARGIN.toFloat(), y + diagramH + 15f, smallPaint)
        }
        
        y += diagramH + 80f
        
        // --- 2. Shear Diagram ---
        drawFormattedText(canvas, "Shear Force Diagram (SFD)", MARGIN.toFloat(), y - 10f, subtitlePaint)
        drawDiagramContainer(canvas, MARGIN.toFloat(), y, diagramW.toFloat(), diagramH)
        
        if (diagrams.shearPoints.isNotEmpty()) {
            val maxV = diagrams.shearPoints.maxOf { abs(it.second) }.coerceAtLeast(1.0)
            val path = Path()
            val startX = MARGIN.toFloat()
            val baselineY = y + diagramH / 2f
            
            diagrams.shearPoints.forEachIndexed { i, pt ->
                val px = startX + (pt.first.toFloat() / diagrams.shearPoints.last().first.toFloat()) * diagramW
                val py = baselineY - (pt.second.toFloat() / maxV.toFloat()) * (diagramH / 2f)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f })
            drawFormattedText(canvas, "Max V: ${"%.1f".format(diagrams.shearPoints.maxByOrNull { abs(it.second) }?.second ?: 0.0)} kN", MARGIN.toFloat(), y + diagramH + 15f, smallPaint)
        }
    }

    private fun drawDiagramContainer(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val paint = Paint().apply { color = lightGrayColor; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRect(x, y, x + w, y + h, paint)
        // Baseline
        paint.color = Color.DKGRAY
        canvas.drawLine(x, y + h/2, x + w, y + h/2, paint)
    }

    private fun drawConnectionDetails(canvas: Canvas, conn: ConnectionDesignResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ”© ØªÙØ§ØµÙŠÙ„ ØªØµÙ…ÙŠÙ… Ø§Ù„ÙˆØµÙ„Ø§Øª" else "ðŸ”© Connection Design Details"
        drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint); y += 40f
        
        drawStatusHeader(canvas, y, conn.isSafe); y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„ÙˆØµÙ„Ø©" else "Connection Type", conn.connectionType.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„Ù‚ÙˆØ© Ø§Ù„Ù…Ø¤Ø«Ø±Ø©" else "Applied Force", "${"%.1f".format(conn.appliedForce)} kN"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "Ø§Ù„Ù…Ù‚Ø§ÙˆÙ…Ø© Ø§Ù„ØªØµÙ…ÙŠÙ…ÙŠØ©" else "Design Capacity", "${"%.1f".format(conn.capacity)} kN"); y += 35f
        
        val utilLabel = if (isAr) "Ù†Ø³Ø¨Ø© Ø§Ø³ØªÙ‡Ù„Ø§Ùƒ Ø§Ù„ÙˆØµÙ„Ø©" else "Connection Utilization"
        drawUtilizationBar(canvas, y, utilLabel, conn.utilizationRatio); y += 60f

        when (val type = conn.connectionType) {
            is ConnectionType.Welded -> {
                val weldedTitle = if (isAr) "Ù…ÙˆØ§ØµÙØ§Øª Ø§Ù„Ù„Ø­Ø§Ù…:" else "Welding Specifications:"
                drawFormattedText(canvas, weldedTitle, MARGIN.toFloat(), y, subtitlePaint); y += 25f
                drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„Ù„Ø­Ø§Ù…" else "Weld Type", type.weldType.displayName); y += 20f
                drawInfoRow(canvas, y, if (isAr) "Ù…Ù‚Ø§Ø³ Ø§Ù„Ù„Ø­Ø§Ù…" else "Weld Size (Leg)", "${type.weldSize} mm"); y += 20f
                drawInfoRow(canvas, y, if (isAr) "Ø·ÙˆÙ„ Ø§Ù„Ù„Ø­Ø§Ù…" else "Weld Length", "${type.weldLength} mm"); y += 20f
                drawInfoRow(canvas, y, if (isAr) "Ù†ÙˆØ¹ Ø§Ù„Ø¥Ù„ÙƒØªØ±ÙˆØ¯" else "Electrode", type.electrodeType.displayName); y += 30f
                
                // Schematic Weld Drawing
                drawWeldSchematic(canvas, MARGIN.toFloat() + 100f, y, type.weldSize.toFloat())
            }
            is ConnectionType.Bolted -> {
                val boltedTitle = if (isAr) "Ù…ÙˆØ§ØµÙØ§Øª Ø§Ù„Ø¨Ø±Ø§ØºÙŠ (Ø§Ù„Ù…Ø³Ø§Ù…ÙŠØ±):" else "Bolting Specifications:"
                drawFormattedText(canvas, boltedTitle, MARGIN.toFloat(), y, subtitlePaint); y += 25f
                drawInfoRow(canvas, y, if (isAr) "Ù‚Ø·Ø± Ø§Ù„Ù…Ø³Ù…Ø§Ø±" else "Bolt Diameter", "M${type.boltDiameter.toInt()}"); y += 20f
                drawInfoRow(canvas, y, if (isAr) "Ø±ØªØ¨Ø© Ø§Ù„Ù…Ø³Ù…Ø§Ø±" else "Bolt Grade", type.boltGrade.displayName); y += 20f
                drawInfoRow(canvas, y, if (isAr) "Ø¹Ø¯Ø¯ Ø§Ù„Ù…Ø³Ø§Ù…ÙŠØ±" else "Number of Bolts", "${type.numberOfBolts}"); y += 20f
                drawInfoRow(canvas, y, if (isAr) "Ù†Ù…Ø· Ø§Ù„ØªÙˆØ²ÙŠØ¹" else "Pattern", type.boltPattern.displayName); y += 30f
                
                // Schematic Bolt Drawing
                drawBoltPatternSchematic(canvas, PAGE_WIDTH / 2f, y, type.numberOfBolts, type.boltPattern)
            }
            else -> {}
        }
    }

    private fun drawWeldSchematic(canvas: Canvas, x: Float, y: Float, size: Float) {
        val paint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.FILL }
        val s = size * 5f // Scale for visibility
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + s, y)
        path.lineTo(x, y - s)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = Color.DKGRAY; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        canvas.drawPath(path, paint)
        drawFormattedText(canvas, "s = ${size}mm", x + s + 10f, y - s/2, smallPaint)
    }

    private fun drawBoltPatternSchematic(canvas: Canvas, cx: Float, y: Float, n: Int, pattern: BoltPattern) {
        val paint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.FILL }
        val r = 8f
        val spacing = 30f
        
        when (pattern) {
            BoltPattern.SINGLE_ROW -> {
                for (i in 0 until n) {
                    canvas.drawCircle(cx, y + i * spacing, r, paint)
                }
            }
            BoltPattern.DOUBLE_ROW -> {
                for (i in 0 until (n + 1) / 2) {
                    canvas.drawCircle(cx - spacing/2, y + i * spacing, r, paint)
                    if (2 * i + 1 < n) {
                        canvas.drawCircle(cx + spacing/2, y + i * spacing, r, paint)
                    }
                }
            }
            else -> {
                canvas.drawCircle(cx, y, r, paint)
                drawFormattedText(canvas, "Standard $n bolts pattern", cx + 20f, y + 5f, smallPaint)
            }
        }
    }

    private fun drawAlternatives(canvas: Canvas, alternatives: List<ColumnAlternative>) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ”„ Ø®ÙŠØ§Ø±Ø§Øª Ø§Ù„ØªØµÙ…ÙŠÙ… Ø§Ù„Ø¨Ø¯ÙŠÙ„Ø©" else "ðŸ”„ Design Options"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        alternatives.take(5).forEach { alt ->
            val label = if (isAr) "Ø®ÙŠØ§Ø±: ${alt.numberOfBars} Ù‚Ø·Ø± ${alt.barDiameter.toInt()}" else "Option: ${alt.numberOfBars}Ã˜${alt.barDiameter.toInt()}"
            val value = if (isAr) "Ù†Ø³Ø¨Ø© Ø§Ù„Ø§Ø³ØªØ®Ø¯Ø§Ù…: ${(alt.utilizationRatio * 100).toInt()}%" else "Ratio: ${(alt.utilizationRatio * 100).toInt()}%"
            drawInfoRow(canvas, y, label, value); y += 25f
        }
    }

    private fun drawCodeReferencesAndNotes(canvas: Canvas, notes: List<String>, warnings: List<String>) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "ðŸ“‹ Ù…Ù„Ø§Ø­Ø¸Ø§Øª Ø§Ù„ÙƒÙˆØ¯ ÙˆØ§Ù„Ø³Ù„Ø§Ù…Ø©" else "ðŸ“‹ Code & Safety Notes"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        if (warnings.isNotEmpty()) {
            val warnTitle = if (isAr) "ØªÙ†Ø¨ÙŠÙ‡Ø§Øª Ø§Ù„Ø³Ù„Ø§Ù…Ø©:" else "Safety Alerts:"
            drawFormattedText(canvas, warnTitle, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), y, subtitlePaint.apply { color = errorColor }); y += 25f
            warnings.forEach { w -> drawFormattedText(canvas, "â€¢ $w", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() - 10f else MARGIN.toFloat() + 10f, y, bodyPaint); y += 18f }
        }
        y += 10f
        val refTitle = if (isAr) "Ù…Ø±Ø§Ø¬Ø¹ Ø§Ù„ÙƒÙˆØ¯:" else "Code References:"
        drawFormattedText(canvas, refTitle, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), y, subtitlePaint); y += 25f
        notes.forEach { n -> drawFormattedText(canvas, "â€¢ $n", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() - 10f else MARGIN.toFloat() + 10f, y, bodyPaint); y += 18f }
    }

    // ==================== Ø¯ÙˆØ§Ù„ Ø§Ù„Ø±Ø³Ù… Ù…Ù†Ø®ÙØ¶Ø© Ø§Ù„Ù…Ø³ØªÙˆÙ‰ ====================

    private fun drawInfoRow(canvas: Canvas, y: Float, label: String, value: String) {
        val isAr = isArabic(settingsManager.language) || isArabic(label)
        val originalAlign = bodyPaint.textAlign
        val originalSmallAlign = smallPaint.textAlign
        
        if (isAr) {
            // Arabic Mirroring: Label (Right), Value (Left)
            smallPaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, label, (PAGE_WIDTH - MARGIN).toFloat(), y, smallPaint)
            
            bodyPaint.textAlign = Paint.Align.LEFT
            bodyPaint.isFakeBoldText = true
            drawFormattedText(canvas, value, MARGIN.toFloat(), y, bodyPaint)
        } else {
            // LTR: Label (Left), Value (Right)
            smallPaint.textAlign = Paint.Align.LEFT
            drawFormattedText(canvas, label, MARGIN.toFloat(), y, smallPaint)
            
            bodyPaint.textAlign = Paint.Align.RIGHT
            bodyPaint.isFakeBoldText = true
            drawFormattedText(canvas, value, (PAGE_WIDTH - MARGIN).toFloat(), y, bodyPaint)
        }
        
        bodyPaint.textAlign = originalAlign
        smallPaint.textAlign = originalSmallAlign
        bodyPaint.isFakeBoldText = false
    }

    private fun drawStatusHeader(canvas: Canvas, y: Float, isSafe: Boolean) {
        val isAr = isArabic(settingsManager.language)
        val color = if (isSafe) successColor else errorColor
        val text = if (isSafe) {
            if (isAr) "âœ… Ø§Ù„ØªØµÙ…ÙŠÙ… Ø¢Ù…Ù†" else "âœ… DESIGN IS SAFE"
        } else {
            if (isAr) "âŒ Ø§Ù„ØªØµÙ…ÙŠÙ… ØºÙŠØ± Ø¢Ù…Ù† - ÙŠØªØ·Ù„Ø¨ Ù…Ø±Ø§Ø¬Ø¹Ø©" else "âŒ REDESIGN REQUIRED"
        }
        val originalAlign = titlePaint.textAlign
        val originalColor = titlePaint.color
        titlePaint.color = color
        titlePaint.textAlign = Paint.Align.CENTER
        drawFormattedText(canvas, text, PAGE_WIDTH / 2f, y, titlePaint)
        titlePaint.textAlign = originalAlign
        titlePaint.color = originalColor
    }

    private fun drawUtilizationBar(canvas: Canvas, y: Float, label: String, ratio: Double) {
        drawFormattedText(canvas, label, MARGIN.toFloat(), y, bodyPaint)
        val barW = 100f; val barH = 12f; val left = PAGE_WIDTH - MARGIN.toFloat() - barW
        canvas.drawRect(left, y - 10f, left + barW, y - 10f + barH, Paint().apply { color = lightGrayColor })
        
        // Use the new color logic: Red (>1.0), Orange (>0.9), Green (>0.4), Blue (else)
        val barColor = when {
            ratio > 1.0 -> errorColor // Red
            ratio > 0.9 -> Color.parseColor("#FF9800") // Orange
            ratio > 0.4 -> successColor // Green
            else -> primaryColor // Blue
        }
        
        val fillRectW = (barW * ratio.coerceIn(0.0, 1.0)).toFloat()
        canvas.drawRect(left, y - 10f, left + fillRectW, y - 10f + barH, Paint().apply { color = barColor })
        drawFormattedText(canvas, "${(ratio * 100).toInt()}%", left - 35f, y, smallPaint)
    }

    private fun drawRectangularSection(canvas: Canvas, area: RectF, col: ColumnType.Rectangular, reinf: ReinforcementResult) {
        val scale = min(area.width() / col.width.toFloat(), area.height() / col.depth.toFloat()) * 0.6f
        val w = col.width.toFloat() * scale; val h = col.depth.toFloat() * scale
        val l = area.centerX() - w/2; val t = area.centerY() - h/2
        
        // Concrete hatch (Professional diagonal lines)
        val hatchPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 1f; isAntiAlias = true }
        canvas.save()
        canvas.clipRect(l, t, l + w, t + h)
        val step = 15f
        var i = -h
        while (i < w) {
            canvas.drawLine(l + i, t, l + i + h, t + h, hatchPaint)
            i += step
        }
        canvas.restore()
        
        // Concrete Outline
        canvas.drawRect(l, t, l + w, t + h, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f })
        
        // Stirrup (Kana)
        val cover = 40f * scale
        val stirrupPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 1.5f }
        canvas.drawRect(l + cover, t + cover, l + w - cover, t + h - cover, stirrupPaint)
        
        // Rebars
        val positions = calculateRebarPositions(col.width, col.depth, reinf.numberOfBars, l, t, w, h, cover)
        val rebarPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true }
        positions.forEach { p -> canvas.drawCircle(p.x, p.y, 6f, rebarPaint) }
        
        // Dimensions
        bodyPaint.textSize = 12f
        drawFormattedText(canvas, "b = ${col.width.toInt()} mm", area.centerX(), t - 10f, bodyPaint.apply { textAlign = Paint.Align.CENTER })
        canvas.save()
        canvas.rotate(-90f, l - 10f, area.centerY())
        drawFormattedText(canvas, "h = ${col.depth.toInt()} mm", l - 10f, area.centerY(), bodyPaint)
        canvas.restore()
        bodyPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawCircularSection(canvas: Canvas, area: RectF, col: ColumnType.Circular, reinf: ReinforcementResult) {
        val scale = (min(area.width(), area.height()) / col.diameter.toFloat()) * 0.6f
        val radius = (col.diameter.toFloat() * scale) / 2
        val cx = area.centerX(); val cy = area.centerY()
        
        // Concrete hatch (Professional diagonal lines for circle)
        val hatchPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 1f; isAntiAlias = true }
        canvas.save()
        val path = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        canvas.clipPath(path)
        val step = 15f
        var i = -radius
        while (i < radius * 2) {
            canvas.drawLine(cx - radius + i, cy - radius, cx - radius + i + radius * 2, cy + radius, hatchPaint)
            i += step
        }
        canvas.restore()
        
        // Concrete Outline
        canvas.drawCircle(cx, cy, radius, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f })
        
        // Spiral/Stirrup (Red for reinforcement)
        val cover = 40f * scale
        val inner = radius - cover
        canvas.drawCircle(cx, cy, inner, Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 1.5f })
        
        // Rebars
        val rebarPaint = Paint().apply { color = Color.RED; style = Paint.Style.FILL; isAntiAlias = true }
        for (i in 0 until reinf.numberOfBars) {
            val a = i * 2 * PI / reinf.numberOfBars
            canvas.drawCircle(cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat(), 6f, rebarPaint)
        }
        
        drawFormattedText(canvas, "D = ${col.diameter.toInt()} mm", cx, cy - radius - 15f, bodyPaint.apply { textAlign = Paint.Align.CENTER })
        bodyPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawPlaceholder(canvas: Canvas, area: RectF, text: String = "Section Drawing") {
        drawFormattedText(canvas, text, area.centerX(), area.centerY(), smallPaint.apply { textAlign = Paint.Align.CENTER })
    }

    private fun drawDisclaimer(canvas: Canvas, y: Float) {
        val isAr = isArabic(settingsManager.language)
        val paint = Paint().apply { color = textColor; textSize = 9f; textAlign = Paint.Align.CENTER }
        val line1 = if (isAr) "âš ï¸ Ù‡Ø°Ø§ Ø§Ù„ØªÙ‚Ø±ÙŠØ± Ù„Ù„Ø£ØºØ±Ø§Ø¶ Ø§Ù„ØªØ¹Ù„ÙŠÙ…ÙŠØ© ÙˆØ§Ù„ØªØµÙ…ÙŠÙ… Ø§Ù„Ø£ÙˆÙ„ÙŠ ÙÙ‚Ø·." else "âš ï¸ This report is for educational purposes and preliminary design only."
        val line2 = if (isAr) "ÙŠØ¬Ø¨ Ù…Ø±Ø§Ø¬Ø¹Ø© ÙƒØ§ÙØ© Ø§Ù„Ù†ØªØ§Ø¦Ø¬ Ù…Ù† Ù‚Ø¨Ù„ Ù…Ù‡Ù†Ø¯Ø³ Ø¥Ù†Ø´Ø§Ø¦ÙŠ Ù…Ø®ØªØµ." else "All results must be verified by a structural engineer."
        drawFormattedText(canvas, line1, PAGE_WIDTH / 2f, y, paint)
        drawFormattedText(canvas, line2, PAGE_WIDTH / 2f, y + 15f, paint)
    }

    private fun calculateRebarPositions(w: Double, d: Double, n: Int, l: Float, t: Float, rw: Float, rh: Float, c: Float): List<PointF> {
        val list = mutableListOf<PointF>()
        
        // Corners
        val p1 = PointF(l + c, t + c)           // Top-Left
        val p2 = PointF(l + rw - c, t + c)      // Top-Right
        val p3 = PointF(l + rw - c, t + rh - c) // Bottom-Right
        val p4 = PointF(l + c, t + rh - c)      // Bottom-Left
        
        if (n <= 4) {
            list.add(p1); list.add(p2); list.add(p3); list.add(p4)
            return list.take(n)
        }

        // For n > 4, we distribute along edges. 
        // Typically columns have even number of bars.
        // n = 6: 2 each long side, 1 each short side OR 3 each long side
        // Simplest: Distribute n/2 on each side if rectangular
        
        // Let's assume n is even and >= 4
        val barsPerSide = n / 2 // This is for 2 rows (top/bottom or left/right)
        
        // We'll place bars on the "top" and "bottom" edges (width-wise) if rw >= rh, else left/right
        if (rw >= rh) {
            val spacing = (rw - 2 * c) / (barsPerSide - 1)
            for (i in 0 until barsPerSide) {
                list.add(PointF(l + c + i * spacing, t + c))
                list.add(PointF(l + c + i * spacing, t + rh - c))
            }
        } else {
            val spacing = (rh - 2 * c) / (barsPerSide - 1)
            for (i in 0 until barsPerSide) {
                list.add(PointF(l + c, t + c + i * spacing))
                list.add(PointF(l + rw - c, t + c + i * spacing))
            }
        }

        return list.take(n)
    }

    private fun getCurrentDate() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    private fun drawMultilineText(c: Canvas, text: String, x: Float, y: Float, p: Paint) {
        var cy = y; text.split("\n").forEach { line -> drawFormattedText(c, line, x, cy, p); cy += p.textSize * 1.5f }
    }
}
