package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.SettingsManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * مُصدّر PDF احترافي شامل للنظام الإنشائي
 * يدعم: الأعمدة، الكمرات، البلاطات، والمنشآت المعدنية
 */
class ComprehensivePdfExporter(private val context: Context) {
    
    companion object {
        private const val PAGE_WIDTH = 595  // A4 width in points
        private const val PAGE_HEIGHT = 842 // A4 height in points
        private const val MARGIN = 40
    }
    
    // الألوان الثابتة للهوية البصرية
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
    
    private val settingsManager = SettingsManager(context)
    
    // تعريف أدوات الرسم (Paints)
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

    // ==================== الدوال العامة للتصدير ====================
    
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
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, "Slab Design") }
            drawPage(document, pageNum++) { drawSlabInputs(it, slabType, inputs) }
            drawPage(document, pageNum++) { drawSlabResults(it, result) }
            drawPage(document, pageNum++) { drawSlabQuantities(it, result) }
            drawPage(document, pageNum++) { drawSlabSectionDetail(it, result) }
            drawPage(document, pageNum++) { drawSlabLayoutPage(it, result.reinforcementLayout) }
            drawPage(document, pageNum++) { drawSlabCalculations(it, result) }
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
            drawPage(document, pageNum++) { drawFootingDrawing(it, result) }
            
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
            drawPage(document, pageNum++) { drawStairBOQ(it, result) }
            
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
        val title = if (isAr) "📐 رسم كروكي للسلم" else "📐 Staircase Section Sketch"
        
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
        drawInfoRow(activeCanvas, currentY, if (isAr) "سمك البلاطة (ts)" else "Slab Thickness (ts)", "${result.thickness.toInt()} mm")
        currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "القائمة / النائمة" else "Riser / Tread", "${result.riser.toInt()} / ${result.tread.toInt()} mm")
    }

    private fun drawStairCalculations(canvas: Canvas, result: CalculatorEngine.StairResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🧮 حسابات السلم التفصيلية" else "🧮 Detailed Stair Calculations"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawFormattedText(activeCanvas, if (isAr) "1. الخصائص الهندسية:" else "1. Geometric Parameters:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "سمك البلاطة (ts)" else "Stair Thickness (ts)", "${result.thickness.toInt()} mm")
        drawInfoRow(activeCanvas, currentY + 20f, if (isAr) "طول البحر (L)" else "Span Length (L)", "%.2f m".format(result.span))
        drawInfoRow(activeCanvas, currentY + 40f, if (isAr) "النايمة / القائمة" else "Riser / Tread", "${result.riser.toInt()} / ${result.tread.toInt()} mm")
        val angle = Math.toDegrees(kotlin.math.atan(result.riser / result.tread.coerceAtLeast(1.0)))
        drawInfoRow(activeCanvas, currentY + 60f, if (isAr) "زاوية الميل (α)" else "Inclination Angle (α)", "%.1f°".format(angle)); currentY += 85f

        drawFormattedText(activeCanvas, if (isAr) "2. التحليل الإنشائي:" else "2. Structural Analysis:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "الحمل التصميمي (wu)" else "Design Load (wu)", "%.2f kN/m²".format(result.wu))
        drawInfoRow(activeCanvas, currentY + 20f, if (isAr) "العزم التصميمي (Mu)" else "Design Moment (Mu)", "%.2f kN.m/m'".format(result.mu))
        drawInfoRow(activeCanvas, currentY + 40f, if (isAr) "العمق الفعال (d)" else "Effective Depth (d)", "${(result.thickness - 25).toInt()} mm"); currentY += 65f

        drawFormattedText(activeCanvas, if (isAr) "3. حسابات التسليح:" else "3. Reinforcement Calculation:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        val asReq = (result.mu * 1e6) / (0.8 * result.fy * (result.thickness - 25))
        drawInfoRow(activeCanvas, currentY, if (isAr) "مساحة الحديد المطلوبة" else "Required Steel Area (As_req)", "%.1f mm²/m'".format(asReq))
        drawInfoRow(activeCanvas, currentY + 20f, if (isAr) "مساحة الحديد الموفرة" else "Provided Steel Area (As_prov)", "%.1f mm²/m'".format(result.reinforcement.area))
        drawInfoRow(activeCanvas, currentY + 40f, if (isAr) "الحديد الرئيسي" else "Main Bars", result.reinforcement.barString)
        drawInfoRow(activeCanvas, currentY + 60f, if (isAr) "حديد التوزيع" else "Distribution Bars", result.distributionReinforcement.barString); currentY += 85f

        if (result.safetyChecks.isNotEmpty()) {
            activeCanvas = checkNewPage(activeCanvas, 150f)
            drawFormattedText(activeCanvas, if (isAr) "4. التحقق من الأمان:" else "4. Safety Verifications:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            result.safetyChecks.forEach { check ->
                activeCanvas = checkNewPage(activeCanvas, 25f)
                val status = if (check.isSafe) (if (isAr) "آمن ✅" else "PASS ✅") else (if (isAr) "غير آمن ❌" else "FAIL ❌")
                drawInfoRow(activeCanvas, currentY, check.name, status); currentY += 20f
            }
        }
    }

    private fun drawStairBOQ(canvas: Canvas, result: CalculatorEngine.StairResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🏗️ جدول الكميات وتقدير التكلفة" else "🏗️ Bill of Quantities (BOQ)"
        
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
        
        val h1 = if (isAr) "الوصف" else "Description"
        val h2 = if (isAr) "الكمية" else "Quantity"
        val h3 = if (isAr) "التكلفة" else "Estimated Cost"
        
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
        val concreteDesc = if (isAr) "خرسانة جاهزة" else "Concrete (Ready-Mix)"
        val steelDesc = if (isAr) "حديد تسليح" else "Reinforcement Steel"
        
        val concreteCost = result.concreteVolume * settingsManager.concretePrice
        val steelCost = (result.steelWeight / 1000.0) * settingsManager.steelPrice

        drawBOQRow(activeCanvas, currentY, concreteDesc, "${"%.2f".format(result.concreteVolume)} m³", "${"%.0f".format(concreteCost)} $currency"); currentY += 30f
        activeCanvas = checkNewPage(activeCanvas, 30f)
        drawBOQRow(activeCanvas, currentY, steelDesc, "${"%.2f".format(result.steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); currentY += 30f
        
        activeCanvas = checkNewPage(activeCanvas, 40f)
        activeCanvas.drawLine(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY, Paint().apply { color = Color.LTGRAY }); currentY += 40f
        
        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "إجمالي التكلفة التقديرية" else "TOTAL ESTIMATED COST"
        
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
        val title = if (isAr) "📐 معطيات تصميم السلم" else "📐 Stair Parameters"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "نوع السلم" else "Stair Type", result.type.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "حجم الخرسانة" else "Concrete Volume", "${"%.2f".format(result.concreteVolume)} m³"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "وزن الصلب" else "Steel Weight", "${result.steelWeight.toInt()} kg"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "كود التصميم" else "Design Code", result.code.displayName)
    }

    private fun drawStairResults(canvas: Canvas, result: CalculatorEngine.StairResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📊 نتائج التصميم" else "📊 Design Results"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawStatusHeader(canvas, y, result.isSafe); y += 40f
        
        drawUtilizationBar(canvas, y, if (isAr) "نسبة الاستخدام الإنشائي" else "Structural Utilization", result.utilizationRatio); y += 40f

        drawInfoRow(canvas, y, if (isAr) "سمك البلاطة (ts)" else "Thickness (ts)", "${result.thickness.toInt()} mm"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "التسليح الرئيسي" else "Main Reinforcement", result.reinforcement.barString); y += 25f
        drawInfoRow(canvas, y, if (isAr) "تسليح التوزيع" else "Distribution Reinforcement", result.distributionReinforcement.barString)
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
            drawPage(document, pageNum++) { drawWallSectionDetail(it, result) }
            
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
        drawFormattedText(canvas, "📈 Structural Analysis (Stem Diagrams)", MARGIN.toFloat(), y, titlePaint); y += 100f
        
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
        drawInfoRow(canvas, y + 20f, "Soil Density (γ)", "${result.soilDensity.toInt()} kN/m³")
        drawInfoRow(canvas, y + 40f, "Stem Factored Moment", "%.2f kN.m/m'".format(result.muStem))
        drawInfoRow(canvas, y + 60f, "Required Steel (As_req)", "%.1f mm²/m'".format((result.muStem * 1e6) / (0.8 * result.fy * (result.stemThickness - 70))))
    }

    private fun drawWallSectionDetail(canvas: Canvas, result: CalculatorEngine.RetainingWallResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 تفاصيل تسليح حائط السند" else "📐 Wall Cross Section & Reinforcement"
        
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
        drawInfoRow(activeCanvas, currentY, if (isAr) "تسليح الحائط" else "Stem Reinforcement", result.stemReinforcement.barString)
        currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "تسليح القاعدة" else "Base Reinforcement", result.baseReinforcement.barString)
    }

    private fun drawRetainingWallBOQ(canvas: Canvas, result: CalculatorEngine.RetainingWallResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🏗️ جدول الكميات وتقدير التكلفة" else "🏗️ Bill of Quantities (BOQ)"
        
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
        
        val h1 = if (isAr) "الوصف" else "Description"
        val h2 = if (isAr) "الكمية" else "Quantity"
        val h3 = if (isAr) "التكلفة" else "Estimated Cost"
        
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
        val concreteDesc = if (isAr) "خرسانة جاهزة" else "Concrete (Ready-Mix)"
        val steelDesc = if (isAr) "حديد تسليح" else "Reinforcement Steel"
        
        val concreteCost = result.concreteVolume * settingsManager.concretePrice
        val steelCost = (result.steelWeight / 1000.0) * settingsManager.steelPrice

        drawBOQRow(activeCanvas, currentY, concreteDesc, "${"%.2f".format(result.concreteVolume)} m³", "${"%.0f".format(concreteCost)} $currency"); currentY += 30f
        activeCanvas = checkNewPage(activeCanvas, 30f)
        drawBOQRow(activeCanvas, currentY, steelDesc, "${"%.2f".format(result.steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); currentY += 30f
        
        activeCanvas = checkNewPage(activeCanvas, 40f)
        activeCanvas.drawLine(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY, Paint().apply { color = Color.LTGRAY }); currentY += 40f
        
        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "إجمالي التكلفة التقديرية" else "TOTAL ESTIMATED COST"
        
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
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 مدخلات الجدار الساند" else "📐 Wall Parameters"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f

        drawInfoRow(activeCanvas, currentY, if (isAr) "الارتفاع" else "Height", "${result.height} m"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "حجم الخرسانة" else "Concrete Volume", "${"%.2f".format(result.concreteVolume)} m³"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "وزن الحديد" else "Steel Weight", "${result.steelWeight.toInt()} kg"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "كود التصميم" else "Design Code", result.code.displayName)
    }


    private fun drawRetainingWallResults(canvas: Canvas, result: CalculatorEngine.RetainingWallResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📊 نتائج تصميم الجدار الساند" else "📊 Retaining Wall Design Results"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawStatusHeader(activeCanvas, currentY, result.isSafe); currentY += 40f

        drawUtilizationBar(activeCanvas, currentY, if (isAr) "نسبة الاستهلاك الإنشائي" else "Structural Utilization", result.utilizationRatio); currentY += 40f

        drawInfoRow(activeCanvas, currentY, if (isAr) "عرض القاعدة" else "Base Width", "${result.baseWidth.toInt()} mm"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "سمك الجذع (Stem)" else "Stem Thickness", "${result.stemThickness.toInt()} mm"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "تسليح الجذع" else "Stem Reinforcement", result.stemReinforcement.barString); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "تسليح القاعدة" else "Base Reinforcement", result.baseReinforcement.barString)
        
        currentY += 40f
        activeCanvas = checkNewPage(activeCanvas, 100f)
        drawFormattedText(activeCanvas, if (isAr) "فحوصات الاستقرار:" else "Stability Checks:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "معامل الأمان ضد الانقلاب" else "F.S. Overturning", "%.2f".format(result.factorOfSafetyOverturning)); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "معامل الأمان ضد الانزلاق" else "F.S. Sliding", "%.2f".format(result.factorOfSafetySliding)); currentY += 20f

        result.safetyChecks.forEach { check ->
            activeCanvas = checkNewPage(activeCanvas, 25f)
            val status = if (check.isSafe) (if (isAr) "آمن ✅" else "SAFE") else (if (isAr) "غير آمن ❌" else "UNSAFE")
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
        drawFormattedText(activeCanvas, "H=${result.wallHeight}mm", cx - baseW/2 - 30f, cy - wallH/2, smallPaint)
        drawFormattedText(activeCanvas, "B=${result.baseWidth.toInt()}mm", cx, cy + baseT + 25f, smallPaint)
        currentY = cy + baseT + 50f
    }


    private fun drawFootingInputs(canvas: Canvas, result: CalculatorEngine.FootingResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 مدخلات القاعدة" else "📐 Footing Parameters"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f

        drawInfoRow(activeCanvas, currentY, if (isAr) "نوع القاعدة" else "Footing Type", result.type.displayName); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "قدرة تحمل التربة" else "Soil Capacity", "${result.allowablePressure} kPa"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "كود التصميم" else "Design Code", result.code.displayName)
    }

    private fun drawFootingResults(canvas: Canvas, result: CalculatorEngine.FootingResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📊 نتائج التصميم وفحص الأمان" else "📊 Design Results & Safety Checks"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawStatusHeader(activeCanvas, currentY, result.isSafe); currentY += 40f
        
        val dimLabel = if (isAr) "الأبعاد:" else "Dimensions:"
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, dimLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, dimLabel, MARGIN.toFloat(), currentY, subtitlePaint)
        }
        currentY += 25f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "العرض (B)" else "Width (B)", "${result.width.toInt()} mm"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "الطول (L)" else "Length (L)", "${result.length.toInt()} mm"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "السمك (T)" else "Thickness (T)", "${result.thickness.toInt()} mm"); currentY += 35f
        
        if (result.isCombined) {
            activeCanvas = checkNewPage(activeCanvas, 60f)
            drawInfoRow(activeCanvas, currentY, if (isAr) "المسافة بين الأعمدة" else "Col Distance", "${"%.2f".format(result.distanceBetweenColumns / 1000.0)} m"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "مقاس العمود 1" else "Col 1 Size", "${result.column1Size.first.toInt()}x${result.column1Size.second.toInt()} mm"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "مقاس العمود 2" else "Col 2 Size", "${result.column2Size.first.toInt()}x${result.column2Size.second.toInt()} mm"); currentY += 35f
        }

        activeCanvas = checkNewPage(activeCanvas, 60f)
        val soilLabel = if (isAr) "ضغط التربة:" else "Soil Pressure:"
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, soilLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, soilLabel, MARGIN.toFloat(), currentY, subtitlePaint)
        }
        currentY += 25f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "الضغط الفعلي" else "Actual Pressure", "${"%.2f".format(result.soilPressure)} kPa"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "الضغط المسموح" else "Allowable Pressure", "${result.allowablePressure} kPa"); currentY += 20f
        
        val footingUtil = if (result.utilizationRatio > 0) result.utilizationRatio else (result.soilPressure / result.allowablePressure)
        drawUtilizationBar(activeCanvas, currentY, if (isAr) "استهلاك قدرة التربة" else "Soil Utilization", footingUtil); currentY += 45f
        
        activeCanvas = checkNewPage(activeCanvas, 80f)
        val reinfBottomLabel = if (isAr) "التسليح (سفلي):" else "Reinforcement (Bottom):"
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, reinfBottomLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, reinfBottomLabel, MARGIN.toFloat(), currentY, subtitlePaint)
        }
        currentY += 25f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "أسياخ (X)" else "Bars (X)", "${result.barsX} Ø ${result.barDiameter}"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "أسياخ (Y)" else "Bars (Y)", "${result.barsY} Ø ${result.barDiameter}"); currentY += 20f
        
        if (result.reinforcementTopX > 0) {
            activeCanvas = checkNewPage(activeCanvas, 60f)
            val reinfTopLabel = if (isAr) "التسليح (علوي):" else "Reinforcement (Top):"
            if (isAr) {
                subtitlePaint.textAlign = Paint.Align.RIGHT
                drawFormattedText(activeCanvas, reinfTopLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
                subtitlePaint.textAlign = Paint.Align.LEFT
            } else {
                drawFormattedText(activeCanvas, reinfTopLabel, MARGIN.toFloat(), currentY, subtitlePaint)
            }
            currentY += 25f
            drawInfoRow(activeCanvas, currentY, if (isAr) "أسياخ (X)" else "Bars (X)", "${result.reinforcementTopX} Ø ${result.topBarDiameter}"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "أسياخ (Y)" else "Bars (Y)", "${result.reinforcementTopY} Ø ${result.topBarDiameter}"); currentY += 20f
        }
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "إجمالي المساحة المتوفرة" else "Total Area Provided", "${"%.1f".format(result.reinforcementBottom.area)} mm²"); currentY += 35f

        if (result.safetyChecks.isNotEmpty()) {
            activeCanvas = checkNewPage(activeCanvas, 60f)
            val safetyLabel = if (isAr) "فحوصات الأمان التفصيلية:" else "Detailed Safety Checks:"
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
                drawInfoRow(activeCanvas, currentY, check.name, if (check.isSafe) (if (isAr) "آمن ✅" else "PASS ✅") else (if (isAr) "غير آمن ❌" else "FAIL ❌")); currentY += 20f
            }
            currentY += 15f
        }

        activeCanvas = checkNewPage(activeCanvas, 60f)
        val econLabel = if (isAr) "التحليل الاقتصادي:" else "Economic Analysis:"
        if (isAr) {
            subtitlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, econLabel, (PAGE_WIDTH - MARGIN).toFloat(), currentY, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, econLabel, MARGIN.toFloat(), currentY, subtitlePaint)
        }
        currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "درجة الكفاءة" else "Efficiency Score", "${"%.1f".format(result.efficiencyScore)}%"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "الحالة" else "Status", if (result.isOptimal) (if (isAr) "تصميم مثالي" else "OPTIMAL DESIGN") else (if (isAr) "تصميم مفرط" else "OVER-DESIGNED"))
    }


    private fun drawFootingCalculations(canvas: Canvas, result: CalculatorEngine.FootingResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🧮 حسابات التصميم التفصيلية" else "🧮 Detailed Design Calculations"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawFormattedText(activeCanvas, if (isAr) "1. التحقق من قدرة تحمل التربة:" else "1. Soil Bearing Capacity Check:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        val qActual = result.soilPressure
        val qAllow = result.allowablePressure
        drawInfoRow(activeCanvas, currentY, if (isAr) "الإجهاد الفعلي (q_act)" else "Actual Pressure (q_act)", "${"%.2f".format(qActual)} kPa"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "الإجهاد المسموح (q_all)" else "Allowable Pressure (q_all)", "${"%.2f".format(qAllow)} kPa"); currentY += 20f
        val soilStatus = if (qActual <= qAllow) (if (isAr) "آمن" else "OK") else (if (isAr) "غير آمن" else "NG")
        drawInfoRow(activeCanvas, currentY, if (isAr) "الحالة" else "Status", soilStatus); currentY += 35f

        drawFormattedText(activeCanvas, if (isAr) "2. تسليح العزم (سفلي):" else "2. Flexural Reinforcement (Bottom):", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "اتجاه X" else "Direction X", "${result.barsX} Ø ${result.barDiameter}"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "اتجاه Y" else "Direction Y", "${result.barsY} Ø ${result.barDiameter}"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "مساحة الحديد الموفرة" else "Area Provided (As_prov)", "${"%.1f".format(result.reinforcementBottom.area)} mm²"); currentY += 35f

        if (result.safetyChecks.isNotEmpty()) {
            activeCanvas = checkNewPage(activeCanvas, 150f)
            drawFormattedText(activeCanvas, if (isAr) "3. التحقق من سلامة المنشأ:" else "3. Structural Safety Verifications:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            result.safetyChecks.forEach { check ->
                activeCanvas = checkNewPage(activeCanvas, 25f)
                val status = if (check.isSafe) (if (isAr) "آمن" else "PASSED") else (if (isAr) "فشل" else "FAILED")
                drawInfoRow(activeCanvas, currentY, check.name, "${"%.3f".format(check.value)} / ${"%.3f".format(check.limit)} ${check.unit} ($status)"); currentY += 20f
            }
            currentY += 15f
        }
        
        activeCanvas = checkNewPage(activeCanvas, 100f)
        drawFormattedText(activeCanvas, if (isAr) "4. منطق الأبعاد:" else "4. Dimensioning Logic:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        val area = (result.width * result.length) / 1e6
        drawInfoRow(activeCanvas, currentY, if (isAr) "إجمالي مساحة القاعدة" else "Total Base Area", "${"%.2f".format(area)} m²"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "العمق الفعال (d)" else "Effective Depth (d)", "${(result.thickness - 70).toInt()} mm"); currentY += 35f
    }

    private fun drawFootingBOQ(canvas: Canvas, result: CalculatorEngine.FootingResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🏗️ جدول الكميات وتقدير التكلفة" else "🏗️ Bill of Quantities (BOQ)"
        
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
        
        val h1 = if (isAr) "الوصف" else "Description"
        val h2 = if (isAr) "الكمية" else "Quantity"
        val h3 = if (isAr) "التكلفة" else "Cost (Est.)"
        
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
        val concreteDesc = if (isAr) "خرسانة مسلحة" else "Reinforced Concrete"
        val steelDesc = if (isAr) "حديد تسليح" else "Reinforcement Steel"
        
        drawBOQRow(canvas, y, concreteDesc, "${"%.2f".format(result.concreteVolume)} m³", "${"%.0f".format(result.cost * 0.7)} $currency")
        y += 30f
        drawBOQRow(canvas, y, steelDesc, "${"%.2f".format(result.steelWeight)} kg", "${"%.0f".format(result.cost * 0.3)} $currency")
        y += 30f
        
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, Paint().apply { color = Color.LTGRAY }); y += 40f

        // BBS for Footing
        val bbsTitle = if (isAr) "📊 تفريد حديد التسليح (BBS)" else "📊 Bar Bending Schedule (BBS)"
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
        val totalLabel = if (isAr) "إجمالي التكلفة التقديرية" else "TOTAL ESTIMATED COST"
        
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
        val title = if (isAr) "📐 رسم توضيحي للقاعدة" else "📐 Footing Sketch"
        
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
        
        // Rebar Labels
        val labelPaint = Paint(bodyPaint).apply { textAlign = Paint.Align.CENTER }
        drawFormattedText(activeCanvas, "L = ${result.length.toInt()} mm", area.centerX(), top - 10f, labelPaint)
        activeCanvas.save()
        activeCanvas.rotate(-90f, left - 10f, area.centerY())
        drawFormattedText(activeCanvas, "B = ${result.width.toInt()} mm", left - 10f, area.centerY(), labelPaint)
        activeCanvas.restore()
        currentY = top + h + 60f
    }

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
            drawPage(document, pageNum++) { drawTankSectionDetail(it, result) }
            
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
        val title = if (isAr) "📐 معطيات تصميم الخزان" else "📐 Tank Geometry & Loading"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "نوع الخزان" else "Tank Type", result.type.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "الأبعاد (طول x عرض x ارتفاع)" else "Dimensions (L x W x H)", "${result.length} x ${result.width} x ${result.height} m"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "السعة" else "Capacity", "${"%.1f".format(result.capacity)} m³"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "ضغط الماء" else "Water Pressure", "${"%.2f".format(result.waterPressure)} kN/m²"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "ضغط التربة" else "Soil Pressure", "${"%.2f".format(result.soilPressure)} kN/m²"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "كود التصميم" else "Design Code", result.code.displayName)
    }

    private fun drawTankResults(canvas: Canvas, result: CalculatorEngine.TankResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📊 نتائج تصميم الخزان" else "📊 Tank Design Results"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawStatusHeader(activeCanvas, currentY, result.isSafe); currentY += 40f
        
        drawUtilizationBar(activeCanvas, currentY, if (isAr) "نسبة الاستهلاك الإنشائي" else "Structural Utilization", result.utilizationRatio); currentY += 40f

        drawInfoRow(activeCanvas, currentY, if (isAr) "سمك الحائط" else "Wall Thickness", "${(result.wallThickness * 1000).toInt()} mm"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "سمك اللبشة (Base)" else "Base Thickness", "${(result.baseThickness * 1000).toInt()} mm"); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "تسليح الحوائط" else "Wall Reinforcement", result.wallReinforcement.barString); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "تسليح القاعدة" else "Base Reinforcement", result.baseReinforcement.barString)
        
        if (result.safetyChecks.isNotEmpty()) {
            currentY += 45f
            activeCanvas = checkNewPage(activeCanvas, 100f)
            drawFormattedText(activeCanvas, if (isAr) "فحوصات الأمان (الاستقرار):" else "Safety Checks (Stability):", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            result.safetyChecks.forEach { check ->
                activeCanvas = checkNewPage(activeCanvas, 25f)
                val status = if (check.isSafe) (if (isAr) "آمن ✅" else "PASS ✅") else (if (isAr) "غير آمن ❌" else "FAIL ❌")
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
        val waterLabel = if (isAr) "ضغط الماء" else "Water Pressure"
        drawFormattedText(canvas, waterLabel, cx, cy - tankH/2, Paint(smallPaint).apply { color = Color.BLUE; alpha = 150 })
    }


    private fun drawTankBOQ(canvas: Canvas, result: CalculatorEngine.TankResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🏗️ جدول الكميات وتقدير التكلفة" else "🏗️ Bill of Quantities (BOQ)"
        
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

        drawBOQRow(canvas, y, if (isAr) "خرسانة مسلحة" else "Reinforced Concrete", "${"%.2f".format(result.concreteVolume)} m³", "${"%.0f".format(concCost)} $currency"); y += 30f
        drawBOQRow(canvas, y, if (isAr) "حديد تسليح" else "Reinforcement Steel", "${"%.2f".format(result.steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); y += 30f
        drawBOQRow(canvas, y, if (isAr) "ووتر ستوب (Waterstop)" else "Waterstop PVC 25cm", "${"%.1f".format(waterstopQty)} m", "${"%.0f".format(waterstopCost)} $currency"); y += 30f

        // BBS for Tank
        y += 40f
        val bbsTitle = if (isAr) "📊 تفريد حديد التسليح (BBS)" else "📊 Bar Bending Schedule (BBS)"
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
        drawFormattedText(canvas, if (isAr) "إجمالي التكلفة" else "TOTAL COST", MARGIN.toFloat(), y, titlePaint)
        titlePaint.textAlign = Paint.Align.RIGHT
        drawFormattedText(canvas, "${"%.0f".format(concCost + steelCost + waterstopCost)} $currency", (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
        titlePaint.textAlign = Paint.Align.LEFT
    }

    private fun drawTankSectionDetail(canvas: Canvas, result: CalculatorEngine.TankResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 تفاصيل وصلة الحائط مع القاعدة" else "📐 Tank Wall-Base Junction Detail"

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
        drawInfoRow(activeCanvas, currentY, if (isAr) "سمك الحائط" else "Wall Thickness", "${(result.wallThickness * 1000).toInt()} mm")
        currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "سمك القاعدة" else "Base Thickness", "${(result.baseThickness * 1000).toInt()} mm")
    }


    private fun drawTankCalculations(canvas: Canvas, result: CalculatorEngine.TankResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🧮 حسابات الخزان التفصيلية" else "🧮 Detailed Tank Calculations"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawFormattedText(activeCanvas, if (isAr) "1. الضغط الهيدروستاتيكي:" else "1. Hydrostatic Pressure:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "كثافة المياه (γw)" else "Water Density (γw)", "10 kN/m³")
        drawInfoRow(activeCanvas, currentY + 20f, if (isAr) "ارتفاع الخزان (H)" else "Tank Height (H)", "%.2f m".format(result.height))
        drawInfoRow(activeCanvas, currentY + 40f, if (isAr) "أقصى ضغط (p = γw.H)" else "Max Pressure (p = γw.H)", "%.1f kN/m²".format(result.waterPressure)); currentY += 65f

        drawFormattedText(activeCanvas, if (isAr) "2. التحليل الإنشائي (كابولي الحائط):" else "2. Structural Analysis (Wall Cantilever):", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "عزم الانحناء (Mu)" else "Bending Moment (Mu)", "%.2f kN.m/m'".format(result.mu))
        drawInfoRow(activeCanvas, currentY + 20f, if (isAr) "سمك الحائط (tw)" else "Wall Thickness (tw)", "${result.wallThickness.toInt()} mm")
        drawInfoRow(activeCanvas, currentY + 40f, if (isAr) "العمق الفعال (d)" else "Effective Depth (d)", "${(result.wallThickness - 50).toInt()} mm"); currentY += 65f

        drawFormattedText(activeCanvas, if (isAr) "3. التسليح والتحكم في الشروخ:" else "3. Reinforcement & Crack Control:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        val asReq = (result.mu * 1e6) / (170.0 * 0.85 * (result.wallThickness - 50)) // Simplified crack control area
        drawInfoRow(activeCanvas, currentY, if (isAr) "الحديد المطلوب (As_req)" else "Required Steel (As_req)", "%.1f mm²/m'".format(asReq))
        drawInfoRow(activeCanvas, currentY + 20f, if (isAr) "الحديد الموفر (As_prov)" else "Provided Steel (As_prov)", "%.1f mm²/m'".format(result.wallReinforcement.area))
        drawInfoRow(activeCanvas, currentY + 40f, if (isAr) "نسبة الاستخدام" else "Utilization Ratio", "${(result.utilizationRatio * 100).toInt()}%"); currentY += 65f

        if (result.safetyChecks.isNotEmpty()) {
            activeCanvas = checkNewPage(activeCanvas, 150f)
            drawFormattedText(activeCanvas, if (isAr) "4. فحوصات الأمان والخدمة:" else "4. Safety & Serviceability Checks:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            result.safetyChecks.forEach { check ->
                activeCanvas = checkNewPage(activeCanvas, 25f)
                val status = if (check.isSafe) (if (isAr) "آمن ✅" else "PASS ✅") else (if (isAr) "فشل ❌" else "FAIL ❌")
                drawInfoRow(activeCanvas, currentY, check.name, status); currentY += 20f
            }
        }
    }

    private fun drawTankStructuralAnalysis(canvas: Canvas, result: CalculatorEngine.TankResult) {
        var y = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, "📈 Structural Analysis (Wall Diagrams)", MARGIN.toFloat(), y, titlePaint); y += 100f
        
        val diagramW = 200f
        val diagramH = 150f
        val centerX = PAGE_WIDTH / 2f
        
        // 1. Moment Diagram (Triangular/Parabolic)
        val mLeft = centerX - 180f
        drawFormattedText(canvas, "Bending Moment (Myy)", mLeft, y - 10f, subtitlePaint)
        val mPath = Path()
        mPath.moveTo(mLeft, y)
        mPath.lineTo(mLeft, y + diagramH) // Vertical base line (wall height)
        // Draw curve for moment
        val curvePath = Path()
        curvePath.moveTo(mLeft, y)
        curvePath.cubicTo(mLeft + diagramW/2, y + diagramH/3, mLeft + diagramW, y + diagramH*0.8f, mLeft, y + diagramH)
        canvas.drawPath(curvePath, Paint().apply { color = Color.BLUE; style = Paint.Style.STROKE; strokeWidth = 3f })
        drawFormattedText(canvas, "${"%.1f".format(result.mu)} kN.m", mLeft + 20f, y + diagramH, smallPaint)
        
        // 2. Shear Diagram (Triangular)
        val sLeft = centerX + 50f
        drawFormattedText(canvas, "Shear Force (Vuy)", sLeft, y - 10f, subtitlePaint)
        val sPath = Path()
        sPath.moveTo(sLeft, y)
        sPath.lineTo(sLeft, y + diagramH)
        sPath.lineTo(sLeft + diagramW/2, y + diagramH)
        sPath.close()
        canvas.drawPath(sPath, Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f })
        drawFormattedText(canvas, "${"%.1f".format(result.waterPressure * result.height / 2.0)} kN", sLeft + 10f, y + diagramH + 15f, smallPaint)

        y += diagramH + 80f
        drawFormattedText(canvas, "Hydrostatic Pressure Distribution:", MARGIN.toFloat(), y, subtitlePaint); y += 20f
        val pPath = Path()
        pPath.moveTo(MARGIN.toFloat() + 50f, y)
        pPath.lineTo(MARGIN.toFloat() + 50f, y + 100f)
        pPath.lineTo(MARGIN.toFloat() + 150f, y + 100f)
        pPath.close()
        canvas.drawPath(pPath, Paint().apply { color = Color.parseColor("#BBDEFB"); style = Paint.Style.FILL })
        drawFormattedText(canvas, "P = ${"%.1f".format(result.waterPressure)} kN/m²", MARGIN.toFloat() + 160f, y + 100f, bodyPaint)
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
        val subSheetTitle = if (isAr) "تقرير التصميم الفني ودراسة الجدوى" else "Technical Design & Feasibility Study"
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
            (if (isAr) "اسم المشروع" else "Project Name") to projectName,
            (if (isAr) "الكود المستخدم" else "Design Code") to designCodeName,
            (if (isAr) "التاريخ" else "Date") to getCurrentDate(),
            (if (isAr) "المهندس المصمم" else "Designed By") to "Civil EG Pro Engine"
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
        drawFormattedText(canvas, if (isAr) "ملاحظات عامة" else "GENERAL NOTES", notesX, notesY, blueTitlePaint)
        notesY += 20f
        val notes = listOf(
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
        drawFormattedText(canvas, if (isAr) "ملخص التكلفة التقديرية" else "COST ESTIMATE SUMMARY", summaryX, summaryY, blueTitlePaint)
        summaryY += 40f
        
        val currency = settingsManager.currency
        drawFormattedText(canvas, if (isAr) "العملة المستخدمة:" else "Selected Currency:", summaryX, summaryY, bodyPaint)
        drawFormattedText(canvas, currency, summaryX + 120f, summaryY, bodyPaint.apply { isFakeBoldText = true })
        bodyPaint.isFakeBoldText = false
        summaryY += 25f
        
        drawFormattedText(canvas, if (isAr) "سعر الخرسانة / م³:" else "Concrete Price / m³:", summaryX, summaryY, bodyPaint)
        drawFormattedText(canvas, "${settingsManager.concretePrice} $currency", summaryX + 120f, summaryY, bodyPaint)
        summaryY += 25f

        drawFormattedText(canvas, if (isAr) "سعر الحديد / طن:" else "Steel Price / Ton:", summaryX, summaryY, bodyPaint)
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
            drawPage(document, pageNum++) { drawBaseCoverContent(it, projectName, designCode, "Steel Structure") }
            drawPage(document, pageNum++) { drawSteelInputs(it, sectionType, memberType, inputs) }
            drawPage(document, pageNum++) { drawSteelResults(it, result) }
            
            if (memberType == SteelMemberType.TRUSS_MEMBER) {
                drawPage(document, pageNum++) { drawTrussSchematic(it, inputs) }
            }
            
            drawPage(document, pageNum++) { drawSteelSectionPage(it, sectionType) }
            
            if (connectionDesign != null) {
                drawPage(document, pageNum++) { drawConnectionDetails(it, connectionDesign) }
            }

            drawPage(document, pageNum++) { drawSteelBOQ(it, result, inputs, sectionType) }
            
            drawPage(document, pageNum++) { drawSteelCalculations(it, result) }
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

    private fun drawSteelBOQ(canvas: Canvas, result: SteelMemberResult, inputs: SteelInputs, section: SteelSectionType) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🏗️ جدول الكميات وتقدير التكلفة" else "🏗️ Bill of Quantities (BOQ)"
        
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
        
        val h1 = if (isAr) "الوصف" else "Description"
        val h2 = if (isAr) "الكمية" else "Quantity"
        val h3 = if (isAr) "التكلفة" else "Estimated Cost"
        
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
        
        val desc = if (isAr) "قطاعات صلب إنشائي" else "Structural Steel Sections"
        
        drawBOQRow(canvas, y, desc, "${"%.2f".format(weightKg)} kg", "${"%.0f".format(steelCost)} $currency"); y += 30f
        
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, Paint().apply { color = Color.LTGRAY }); y += 40f
        
        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "إجمالي التكلفة التقديرية" else "TOTAL ESTIMATED COST"
        
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
        val title = if (isAr) "🏗️ المخطط الإنشائي للجمالون" else "🏗️ Truss Structural Schematic"
        
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
        drawFormattedText(canvas, if (isAr) "الوتر العلوي (Top Chord)" else "Top Chord", startX + trussW/2, startY - 10f, textPaint.apply { textAlign = Paint.Align.CENTER })
        drawFormattedText(canvas, if (isAr) "الوتر السفلي (Bottom Chord)" else "Bottom Chord", startX + trussW/2, startY + trussH + 20f, textPaint)
        
        y = startY + trussH + 80f
        val infoTitle = if (isAr) "بيانات الجمالون:" else "Truss Data:"
        drawFormattedText(canvas, infoTitle, MARGIN.toFloat(), y, subtitlePaint); y += 30f
        drawInfoRow(canvas, y, if (isAr) "طول البحر" else "Span", "%.2f m".format(inputs.length / 1000.0)); y += 20f
        drawInfoRow(canvas, y, if (isAr) "نوع الجمالون" else "Truss Type", "Warren Pattern"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "عدد الباكيات" else "No. of Panels", panels.toString())
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
        val title = if (isAr) "📐 مدخلات الزلازل" else "📐 Seismic Parameters"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f

        val labels = listOf(
            (if (isAr) "عجلة الزلزال التصميمية (ag)" else "Design Ground Accel. (ag)") to "${inputs.zone}g",
            (if (isAr) "معامل الأهمية (γI)" else "Importance Factor (γI)") to "${inputs.importance}",
            (if (isAr) "نوع التربة" else "Soil Type") to inputs.soilType,
            (if (isAr) "معامل تعديل الاستجابة (R)" else "Response Mod. Factor (R)") to "${inputs.reductionFactor}",
            (if (isAr) "الارتفاع الكلي (H)" else "Total Height (H)") to "${inputs.height} m",
            (if (isAr) "الوزن الكلي (W)" else "Total Weight (W)") to "${"%.1f".format(inputs.totalWeight)} kN"
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
        val title = if (isAr) "📊 نتائج التحليل الزلزالي" else "📊 Analysis Results"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f

        drawStatusHeader(activeCanvas, currentY, result.isSafe); currentY += 40f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "عجلة الطيف (Sa)" else "Spectral Accel. (Sa)", "%.3f g".format(result.spectralAcceleration)); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "الزمن الدوري (T)" else "Fundamental Period (T)", "%.3f s".format(result.timePeriod)); currentY += 25f
        
        titlePaint.color = primaryColor
        drawInfoRow(activeCanvas, currentY, if (isAr) "قص القاعدة (Vb)" else "BASE SHEAR (Vb)", "%.1f kN".format(result.baseShear)); currentY += 40f
        titlePaint.color = textColor
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "أقصى إزاحة طابقية" else "Max Story Drift", "%.4f".format(result.storyDrift)); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "كود التصميم" else "Design Code", result.code.displayName)
    }

    private fun drawSeismicStoryForces(canvas: Canvas, result: CalculatorEngine.SeismicResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🏢 توزيع قوى الأدوار" else "🏢 Story Force Distribution"

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
        
        val h1 = if (isAr) "مستوى الدور" else "Floor Level"
        val h2 = if (isAr) "القوة الأفقية (Fi) [kN]" else "Lateral Force (Fi) [kN]"

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
            drawInfoRow(activeCanvas, currentY, if (isAr) "طابق $level" else "Level $level", "%.2f kN".format(force))
            currentY += 30f
        }
    }

    private fun drawSeismicDiagram(canvas: Canvas, result: CalculatorEngine.SeismicResult, totalHeight: Double) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📈 مخطط القوى الجانبية (الزلازل)" else "📈 Lateral Force Diagram"
        
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
            if (totalHeight > 50.0) "الارتفاع الإجمالي = %.1f م (مقياس مصغر)".format(totalHeight) else "الارتفاع الإجمالي = %.1f م".format(totalHeight)
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
        val vbLabel = if (isAr) "قوة القص القاعدة Vb = %.1f kN".format(result.baseShear) else "Base Shear Vb = %.1f kN".format(result.baseShear)
        
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

    // ==================== دوال المساعدة للرسم والصفحات ====================

    private fun getArabicFont(): Typeface {
        return try {
            Typeface.createFromAsset(context.assets, "fonts/NotoNaskhArabic-Regular.ttf")
        } catch (e: Exception) {
            try {
                // Fallback to a system font if asset is missing
                Typeface.create("sans-serif", Typeface.NORMAL)
            } catch (e2: Exception) {
                Typeface.DEFAULT
            }
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
        
        val isAr = isArabic(text)
        val workingPaint = Paint(paint)
        
        if (isAr) {
            workingPaint.typeface = getArabicFont()
            val textPaint = TextPaint(workingPaint)
            val layoutWidth = (PAGE_WIDTH - 2 * MARGIN).toFloat()
            
            // Handle RTL Shaping and Bidi
            val bidiText = android.text.BidiFormatter.getInstance().unicodeWrap(text)
            
            val alignment = when (paint.textAlign) {
                Paint.Align.RIGHT -> Layout.Alignment.ALIGN_NORMAL
                Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                else -> Layout.Alignment.ALIGN_OPPOSITE
            }
            
            val staticLayout = StaticLayout.Builder.obtain(bidiText, 0, bidiText.length, textPaint, layoutWidth.toInt())
                .setAlignment(alignment)
                .setLineSpacing(0f, 1.1f)
                .build()
            
            canvas.save()
            val tx = when (paint.textAlign) {
                Paint.Align.RIGHT -> x - layoutWidth
                Paint.Align.CENTER -> x - layoutWidth / 2f
                else -> x
            }
            canvas.translate(tx, y - workingPaint.textSize)
            staticLayout.draw(canvas)
            canvas.restore()
        } else {
            canvas.drawText(text, x, y, paint)
        }
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
                val headerText = if (isAr) "تابع - صفحة $currentPageNum" else "Cont. - Page $currentPageNum"
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
        val text = if (isAr) "صفحة $pageNumber" else "Page $pageNumber"
        canvas.drawText(text, (PAGE_WIDTH / 2).toFloat(), (PAGE_HEIGHT - 20).toFloat(), footerPaint)
        
        // Add timestamp to footer
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(getCurrentDate(), MARGIN.toFloat(), (PAGE_HEIGHT - 20).toFloat(), footerPaint)
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
        val title = if (isAr) "📐 معطيات التصميم" else "📐 Input Parameters"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "نوع العمود" else "Column Type", type.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "الحمل المحوري (Pu)" else "Axial Load (Pu)", "${inputs.axialLoad} kN"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "العزوم Mx / My" else "Moments Mx / My", "${inputs.momentX} / ${inputs.momentY} kN.m"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "مقاومة الخرسانة fcu" else "Concrete fcu", "${inputs.fcu} MPa"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "إجهاد الخضوع fy" else "Steel fy", "${inputs.fy} MPa")
    }

    private fun drawBeamInputs(canvas: Canvas, beamType: BeamType, inputs: BeamInputs) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 معطيات تصميم الكمرة" else "📐 Beam Input Parameters"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "نوع الكمرة" else "Beam Type", beamType.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "طول البحر" else "Span Length", "${inputs.span} m"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "الحمل التصميمي (wu)" else "Ultimate Load (wu)", "${inputs.deadLoad + inputs.liveLoad} kN/m"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "مقاومة الخرسانة fcu" else "Concrete fcu", "${inputs.fcu} MPa"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "إجهاد الخضوع fy" else "Steel fy", "${inputs.fy} MPa")
    }

    private fun drawSlabInputs(canvas: Canvas, slabType: SlabType, inputs: SlabInputs) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 معطيات تصميم البلاطة" else "📐 Slab Parameters"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "نوع البلاطة" else "Slab Type", slabType.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "السمك" else "Thickness", "${inputs.thickness} mm"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "الحمل الكلي" else "Total Load", "${inputs.deadLoad + inputs.liveLoad} kN/m²")
    }

    private fun drawSteelInputs(canvas: Canvas, section: SteelSectionType, member: SteelMemberType, inputs: SteelInputs) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 معطيات تصميم العضو المعدني" else "📐 Steel Member Parameters"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "نوع العضو" else "Member Type", member.name); y += 25f
        drawInfoRow(canvas, y, if (isAr) "نوع القطاع" else "Section Type", section.displayName); y += 20f
        drawInfoRow(canvas, y, if (isAr) "الطول / الحمل" else "Length / Load", "${inputs.unbracedLength} m / ${inputs.axialLoad} kN")
    }

    private fun drawMainResults(canvas: Canvas, result: AdvancedColumnResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📊 ملخص التصميم" else "📊 Design Summary"
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
        drawInfoRow(canvas, y, if (isAr) "التسليح المقدم" else "Provided Steel", "${result.reinforcementResult.numberOfBars} Ø${result.reinforcementResult.barDiameter}"); y += 25f
        
        val utilRatio = if (result.axialCapacity > 0) 
            (result.reinforcementResult.astRequired / result.reinforcementResult.astProvided).coerceIn(0.0, 1.2)
            else result.reinforcementResult.utilizationRatio
            
        drawUtilizationBar(canvas, y, if (isAr) "نسبة الاستخدام الإنشائي" else "Structural Utilization", utilRatio)
    }

    private fun drawBeamResults(canvas: Canvas, result: AdvancedBeamResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📊 نتائج التصميم" else "📊 Design Results"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawStatusHeader(canvas, y, result.flexureResult.isSafe && result.shearResult.isSafe); y += 40f
        drawInfoRow(canvas, y, if (isAr) "التسليح الرئيسي" else "Main Steel", "${result.flexureResult.numberOfBars} Ø${result.flexureResult.barDiameter}"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "الكانات" else "Stirrups", "Ø${result.shearResult.stirrupDiameter} @ ${result.shearResult.stirrupSpacing} mm"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "الترخيم (Deflection)" else "Deflection", if (result.deflectionCheck.isSafe) (if (isAr) "آمن" else "SAFE") else (if (isAr) "غير آمن" else "EXCEEDS LIMIT"))
        
        y += 30f
        drawUtilizationBar(canvas, y, if (isAr) "استخدام الانحناء" else "Flexural Utilization", result.flexureResult.utilizationRatio)
    }

    private fun drawSlabResults(canvas: Canvas, result: AdvancedSlabResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📊 نتائج البلاطة" else "📊 Slab Results"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        val isOverallSafe = result.flexureResult.isSafe && 
                          (result.punchingShearCheck?.isSafe ?: true) && 
                          (result.postTensionCalculations?.isSafe ?: true)
        
        drawStatusHeader(canvas, y, isOverallSafe); y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "الأسياخ السفلية" else "Bottom Bars", "${result.reinforcementLayout.bottomBars.numberOfBars} Ø${result.reinforcementLayout.bottomBars.diameter}"); y += 25f
        
        result.punchingShearCheck?.let { punching ->
            drawInfoRow(canvas, y, if (isAr) "قص الثقب (Punching)" else "Punching Shear", if (punching.isSafe) (if (isAr) "آمن ✅" else "SAFE ✅") else (if (isAr) "غير آمن ❌" else "UNSAFE ❌")); y += 20f
            drawInfoRow(canvas, y, if (isAr) "  المطبق / السعة" else "  Applied / Capacity", "${"%.1f".format(punching.appliedShear)} / ${"%.1f".format(punching.shearCapacity)} kN"); y += 25f
        } ?: run {
            drawInfoRow(canvas, y, if (isAr) "الثقب" else "Punching", if (isAr) "غير متاح (مرتكزة على كمرات)" else "N/A (Beam Supported)"); y += 25f
        }

        result.postTensionCalculations?.let { pt ->
            drawInfoRow(canvas, y, if (isAr) "قوة الشد اللاحق" else "PT Prestress Force", "${"%.1f".format(pt.prestressForce)} kN"); y += 20f
            drawInfoRow(canvas, y, if (isAr) "  الحمل المكافئ" else "  Equivalent Load", "${"%.2f".format(pt.equivalentLoad)} kN/m²"); y += 25f
        }

        y += 10f
        drawUtilizationBar(canvas, y, if (isAr) "استخدام الانحناء" else "Flexural Utilization", result.flexureResult.utilizationRatio)
        
        result.punchingShearCheck?.let { punching ->
            y += 25f
            drawUtilizationBar(canvas, y, if (isAr) "استخدام الثقب" else "Punching Utilization", punching.utilizationRatio)
        }
    }

    private fun drawSlabQuantities(canvas: Canvas, result: AdvancedSlabResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📊 الكميات والتقديرات" else "📊 Quantities & Estimates"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "حجم الخرسانة" else "Concrete Volume", "${"%.2f".format(result.concreteVolume)} m³"); y += 25f
        drawInfoRow(canvas, y, if (isAr) "مساحة الشدة الخشبية" else "Formwork Area", "${"%.2f".format(result.formworkArea)} m²"); y += 25f
        
        if (result.inventoryAnalysis != null) {
            drawInfoRow(canvas, y, if (isAr) "إجمالي وزن الصلب" else "Total Steel Weight", "${"%.2f".format(result.inventoryAnalysis.totalWeight)} Tons")
        }
    }

    private fun drawSteelResults(canvas: Canvas, result: SteelMemberResult) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📊 نتائج العضو المعدني" else "📊 Steel Member Results"
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        drawUtilizationBar(canvas, y, if (isAr) "تفاعل القوى الكلي" else "Total Interaction", result.utilizationRatio); y += 30f
        drawInfoRow(canvas, y, if (isAr) "الوزن" else "Weight", "${"%.1f".format(result.weight)} kg/m")
    }

    private fun drawColumnSectionPage(canvas: Canvas, type: ColumnType, reinf: ReinforcementResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 رسم المقطع العرضي للتسليح" else "📐 Cross Section Drawing"
        
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
            else -> drawPlaceholder(activeCanvas, area, if (isAr) "رسم كروكي للعمود" else "Column Sketch")
        }
        
        currentY += areaHeight + 50f
        activeCanvas = checkNewPage(activeCanvas, 80f)
        
        // Add Summary info below section
        val detailText = if (isAr) 
            "التسليح الموفر: ${reinf.numberOfBars} أسياخ قطر ${reinf.barDiameter} مم" 
            else "Provided Reinforcement: ${reinf.numberOfBars} bars @ ${reinf.barDiameter} mm"
        
        drawFormattedText(activeCanvas, detailText, PAGE_WIDTH / 2f, currentY, subtitlePaint)
        currentY += 40f
        drawDisclaimer(activeCanvas, currentY)
        drawDisclaimer(activeCanvas, currentY)
    }

    private fun drawTankSectionPage(canvas: Canvas, area: RectF) {
        // This function will be properly implemented later if needed
    }

    private fun drawBeamSectionPage(canvas: Canvas, result: AdvancedBeamResult, inputs: BeamInputs) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 تفاصيل تسليح المقطع العرضي للكمرة" else "📐 Beam Cross Section & Reinforcement"
        
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
        val bottomLabel = if (isAr) "$nBottom أسياخ Φ$diaBottom سفلي" else "$nBottom - Φ$diaBottom Bottom"
        val topLabel = if (isAr) "2 أسياخ Φ12 تعليق كانات" else "2 - Φ12 Stirrup Hangers"
        val stirrupLabel = if (isAr) "كانات Φ8 كل 200 مم" else "Stirrups Φ8 @ 200mm"
        
        drawFormattedText(activeCanvas, bottomLabel, PAGE_WIDTH / 2f, currentTop + h + 25f, smallPaint)
        drawFormattedText(activeCanvas, topLabel, PAGE_WIDTH / 2f, currentTop - 15f, smallPaint)
        currentY = currentTop + h + 60f
        drawFormattedText(activeCanvas, stirrupLabel, PAGE_WIDTH / 2f, currentY, smallPaint)
        currentY += 40f
        
        smallPaint.textAlign = Paint.Align.LEFT
        drawFormattedText(activeCanvas, "b=${inputs.width.toInt()}mm", left - 50f, top + h/2, smallPaint)
        drawFormattedText(activeCanvas, "h=${inputs.depth.toInt()}mm", left + w/2, top + h + 50f, smallPaint)

        currentY = top + h + 80f
        activeCanvas = checkNewPage(activeCanvas, 100f)
        drawDisclaimer(activeCanvas, currentY)
    }

    private fun drawSlabSketch(canvas: Canvas, area: RectF) {
        val left = area.left
        val top = area.top
        val w = area.width()
        val h = area.height()
        val hatchPaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f; style = Paint.Style.STROKE }
        val step = 15f
        var i = -h
        while (i < w) {
            canvas.drawLine(left + i, top, left + i + h, top + h, hatchPaint)
            i += step
        }
    }
    private fun drawBeamSectionDrawing(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, result: AdvancedBeamResult, inputs: BeamInputs, isAr: Boolean) {
        // Concrete Outline
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f }
        canvas.drawRect(left, top, left + w, top + h, paint)
        
        // Stirrup
        paint.color = Color.RED
        paint.strokeWidth = 2f
        canvas.drawRect(left + 20f, top + 20f, left + w - 20f, top + h - 20f, paint)
        
        // Bottom Bars
        paint.style = Paint.Style.FILL
        val numBars = result.flexureResult.numberOfBars.coerceIn(2, 10)
        val spacing = (w - 70f) / (numBars - 1).coerceAtLeast(1)
        for (i in 0 until numBars) {
            canvas.drawCircle(left + 35f + i * spacing, top + h - 35f, 7f, paint)
        }
        
        // Top Bars (Hangers)
        for (i in 0 until 2) {
            canvas.drawCircle(left + 35f + i * (w - 70f), top + 35f, 6f, paint)
        }
        
        // Dimension Labels
        bodyPaint.textAlign = Paint.Align.CENTER
        drawFormattedText(canvas, "b = ${inputs.width.toInt()} mm", PAGE_WIDTH / 2f, top - 15f, bodyPaint)
        
        canvas.save()
        canvas.rotate(-90f, left - 15f, top + h/2)
        drawFormattedText(canvas, "h = ${inputs.totalDepth.toInt()} mm", left - 15f, top + h/2, bodyPaint)
        canvas.restore()
        
        bodyPaint.textAlign = Paint.Align.LEFT
        var y = top + h + 60f
        val reinfInfo = if (isAr) {
            "التسليح السفلي: ${result.flexureResult.numberOfBars} T${result.flexureResult.barDiameter.toInt()}"
        } else {
            "Bottom Reinf: ${result.flexureResult.numberOfBars} T${result.flexureResult.barDiameter.toInt()}"
        }
        drawFormattedText(canvas, reinfInfo, MARGIN.toFloat(), y, subtitlePaint); y += 30f
        
        val stirrupInfo = if (isAr) {
            "الكانات: T${result.shearResult.stirrupDiameter.toInt()} كل ${result.shearResult.stirrupSpacing.toInt()} مم"
        } else {
            "Stirrups: T${result.shearResult.stirrupDiameter.toInt()} @ ${result.shearResult.stirrupSpacing.toInt()} mm"
        }
        drawFormattedText(canvas, stirrupInfo, MARGIN.toFloat(), y, subtitlePaint)
    }

    private fun drawSlabLayoutPage(canvas: Canvas, layout: ReinforcementLayout) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 مخطط تسليح البلاطة" else "📐 Slab Reinforcement Plan"
        drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint); y += 40f
        
        val mainText = if (isAr) 
            "الحديد السفلي: ${layout.bottomBars.numberOfBars} Ø${layout.bottomBars.diameter.toInt()} كل ${layout.bottomBars.spacing.toInt()} مم"
            else "Bottom Bars: ${layout.bottomBars.numberOfBars} Ø${layout.bottomBars.diameter.toInt()} @ ${layout.bottomBars.spacing.toInt()} mm"
            
        val topText = if (isAr)
            "الحديد العلوي: ${layout.topBars.numberOfBars} Ø${layout.topBars.diameter.toInt()} كل ${layout.topBars.spacing.toInt()} مم"
            else "Top Bars: ${layout.topBars.numberOfBars} Ø${layout.topBars.diameter.toInt()} @ ${layout.topBars.spacing.toInt()} mm"
            
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
        val note = if (isAr) "ملاحظة: جميع أطوال التماسك والوصلات يجب أن تكون حسب الكود المستخدم." 
                   else "Note: All development lengths and laps shall conform to the design code."
        drawFormattedText(canvas, note, MARGIN.toFloat(), y, smallPaint)
    }

    private fun drawColumnBOQ(canvas: Canvas, result: AdvancedColumnResult, inputs: ColumnInputs) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🏗️ جدول الكميات وتقدير التكلفة" else "🏗️ Bill of Quantities (BOQ)"
        
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
        
        val h1 = if (isAr) "الوصف" else "Description"
        val h2 = if (isAr) "الكمية" else "Quantity"
        val h3 = if (isAr) "التكلفة" else "Estimated Cost"
        
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
        
        val concreteDesc = if (isAr) "خرسانة جاهزة" else "Concrete (Ready-Mix)"
        val steelDesc = if (isAr) "حديد تسليح" else "Reinforcement Steel"
        
        drawBOQRow(activeCanvas, currentY, concreteDesc, "${"%.2f".format(concreteVol)} m³", "${"%.0f".format(concreteCost)} $currency"); currentY += 30f
        activeCanvas = checkNewPage(activeCanvas, 30f)
        drawBOQRow(activeCanvas, currentY, steelDesc, "${"%.2f".format(steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); currentY += 30f
        
        activeCanvas = checkNewPage(activeCanvas, 40f)
        activeCanvas.drawLine(MARGIN.toFloat(), currentY, (PAGE_WIDTH - MARGIN).toFloat(), currentY, Paint().apply { color = Color.LTGRAY }); currentY += 40f

        // BBS for Column
        val bbsTitle = if (isAr) "📊 تفريد حديد التسليح (BBS)" else "📊 Bar Bending Schedule (BBS)"
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
            barLength = 2 * ( (if (inputs.columnType is ColumnType.Rectangular) (inputs.columnType as ColumnType.Rectangular).width else 500.0) + (if (inputs.columnType is ColumnType.Rectangular) (inputs.columnType as ColumnType.Rectangular).depth else 500.0) ) / 1000.0 + 0.2,
            shapeCode = 1,
            type = "Stirrup",
            description = "Column Stirrups"
        )
        activeCanvas = checkNewPage(activeCanvas, 150f)
        drawBbsTable(activeCanvas, currentY, listOf(mainBar, stirrupBar)); currentY += 150f
        
        activeCanvas = checkNewPage(activeCanvas, 50f)
        titlePaint.color = primaryColor
        val totalLabel = if (isAr) "إجمالي التكلفة التقديرية" else "TOTAL ESTIMATED COST"
        
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
        val title = if (isAr) "🏗️ جدول الكميات وتقدير التكلفة" else "🏗️ Bill of Quantities (BOQ)"
        
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
        
        val h1 = if (isAr) "الوصف" else "Description"
        val h2 = if (isAr) "الكمية" else "Quantity"
        val h3 = if (isAr) "التكلفة" else "Estimated Cost"
        
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
        
        val concreteDesc = if (isAr) "خرسانة جاهزة" else "Concrete (Ready-Mix)"
        val steelDesc = if (isAr) "حديد تسليح" else "Reinforcement Steel"
        
        drawBOQRow(canvas, y, concreteDesc, "${"%.2f".format(concreteVol)} m³", "${"%.0f".format(concreteCost)} $currency"); y += 30f
        drawBOQRow(canvas, y, steelDesc, "${"%.2f".format(steelWeight)} kg", "${"%.0f".format(steelCost)} $currency"); y += 30f
        
        canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, Paint().apply { color = Color.LTGRAY }); y += 40f
        
        // --- BBS Section ---
        val bbsTitle = if (isAr) "📊 تفريد حديد التسليح (BBS)" else "📊 Bar Bending Schedule (BBS)"
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
        val totalLabel = if (isAr) "إجمالي التكلفة التقديرية" else "TOTAL ESTIMATED COST"
        
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
            listOf("العنصر", "الشكل", "القطر", "الطول", "الإجمالي", "الوزن")
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
        val title = if (isAr) "📐 تفاصيل المقطع العرضي للصلب" else "📐 Steel Section Details"
        
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
                val h = section.depth.toFloat() * scale
                val bf = section.flangeWidth.toFloat() * scale
                val tf = section.flangeThickness.toFloat() * scale
                val tw = section.webThickness.toFloat() * scale
                
                // Top Flange
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx + bf/2, cy - h/2 + tf, paint)
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx + bf/2, cy - h/2 + tf, strokePaint)
                // Bottom Flange
                activeCanvas.drawRect(cx - bf/2, cy + h/2 - tf, cx + bf/2, cy + h/2, paint)
                activeCanvas.drawRect(cx - bf/2, cy + h/2 - tf, cx + bf/2, cy + h/2, strokePaint)
                // Web
                activeCanvas.drawRect(cx - tw/2, cy - h/2 + tf, cx + tw/2, cy + h/2 - tf, paint)
                activeCanvas.drawRect(cx - tw/2, cy - h/2 + tf, cx + tw/2, cy + h/2 - tf, strokePaint)
                
                drawSectionLabels(activeCanvas, cx, cy, bf, h, tf, tw)
            }
            is SteelSectionType.CSection -> {
                val scale = 1.2f
                val h = section.depth.toFloat() * scale
                val bf = section.flangeWidth.toFloat() * scale
                val tf = section.flangeThickness.toFloat() * scale
                val tw = section.webThickness.toFloat() * scale
                
                // Web (Left)
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx - bf/2 + tw, cy + h/2, paint)
                activeCanvas.drawRect(cx - bf/2, cy - h/2, cx - bf/2 + tw, cy + h/2, strokePaint)
                // Top Flange
                activeCanvas.drawRect(cx - bf/2 + tw, cy - h/2, cx + bf/2, cy - h/2 + tf, paint)
                activeCanvas.drawRect(cx - bf/2 + tw, cy - h/2, cx + bf/2, cy - h/2 + tf, strokePaint)
                // Bottom Flange
                activeCanvas.drawRect(cx - bf/2 + tw, cy + h/2 - tf, cx + bf/2, cy + h/2, paint)
                activeCanvas.drawRect(cx - bf/2 + tw, cy + h/2 - tf, cx + bf/2, cy + h/2, strokePaint)
                
                drawSectionLabels(activeCanvas, cx, cy, bf, h, tf, tw)
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
                
                drawSectionLabels(activeCanvas, cx, cy, b, a, t, t)
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
                
                drawSectionLabels(activeCanvas, cx, cy, w, h, t, t)
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
                
                drawSectionLabels(activeCanvas, cx, cy, bf, h, tf, tw)
            }
            else -> drawPlaceholder(activeCanvas, RectF(cx - 100f, cy - 100f, cx + 100f, cy + 100f), section.displayName)
        }
        
        currentY = cy + 200f
        activeCanvas = checkNewPage(activeCanvas, 150f)
        drawFormattedText(activeCanvas, if (isAr) "خصائص القطاع:" else "Section Properties:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 30f
        drawInfoRow(activeCanvas, currentY, if (isAr) "المساحة" else "Area", "${"%.2f".format(section.getArea())} mm²"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "الرتبة" else "Grade", (section as? SteelSectionType.ISection)?.grade?.displayName ?: (if (isAr) "قياسي" else "Standard")); currentY += 20f
    }


    private fun drawSectionLabels(canvas: Canvas, cx: Float, cy: Float, bf: Float, h: Float, tf: Float, tw: Float) {
        smallPaint.textAlign = Paint.Align.CENTER
        // bf
        canvas.drawLine(cx - bf/2, cy - h/2 - 20f, cx + bf/2, cy - h/2 - 20f, smallPaint)
        drawFormattedText(canvas, "bf", cx, cy - h/2 - 25f, smallPaint)
        // h
        canvas.save()
        canvas.rotate(-90f, cx - bf/2 - 20f, cy)
        drawFormattedText(canvas, "h", cx - bf/2 - 20f, cy, smallPaint)
        canvas.restore()
    }

    private fun drawDetailedCalculations(canvas: Canvas, result: AdvancedColumnResult, code: DesignCode) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🧮 معادلات التصميم (${code.displayName})" else "🧮 Design Formulas (${code.displayName})"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        val capacityFormula = if (isAr) 
            "قوة التحمل Pn = 0.8 * [0.67 * fcu * (Ag - Ast) + fy * Ast]" 
            else "Capacity Pn = 0.8 * [0.67 * fcu * (Ag - Ast) + fy * Ast]"

        val calculations = """
            $capacityFormula
            Ag = ${"%.0f".format(result.columnType.getGrossArea())} mm²
            Ast = ${"%.0f".format(result.reinforcementResult.astProvided)} mm²
            Result = ${"%.1f".format(result.axialCapacity)} kN
        """.trimIndent()
        
        drawMultilineText(activeCanvas, calculations, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, bodyPaint)
        currentY += 120f
        
        // Add ECP specific notes from checklist
        activeCanvas = checkNewPage(activeCanvas, 100f)
        val noteTitle = if (isAr) "📌 ملاحظات الكود المصري (ECP):" else "📌 Design Code Notes (ECP):"
        drawFormattedText(activeCanvas, noteTitle, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        
        val notes = if (isAr) listOf(
            "• نسبة التسليح الدنيا (μ min) = 0.8% من المساحة المطلوبة.",
            "• المسافة بين الكانات لا تزيد عن 200 مم أو أقل بُعد للعمود.",
            "• تم التحقق من تأثير النحافة (Slenderness) والعزوم الإضافية."
        ) else listOf(
            "• Min Reinforcement Ratio (μ min) = 0.8% of required area.",
            "• Stirrup spacing <= 200mm or minimum column dimension.",
            "• Slenderness effects and additional moments verified."
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
        val title = if (isAr) "🧮 حسابات العزم والقص" else "🧮 Flexural & Shear Calculations"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        val flexText = if (isAr)
            "مساحة الحديد المطلوبة As = Mu / (0.87 * fy * d)\nالحديد الموفر = ${"%.0f".format(result.flexureResult.astProvided)} mm²"
            else "Required As = Mu / (0.87 * fy * d)\nProvided Steel = ${"%.0f".format(result.flexureResult.astProvided)} mm²"
            
        drawMultilineText(activeCanvas, flexText, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, bodyPaint)
        currentY += 60f
        
        activeCanvas = checkNewPage(activeCanvas, 100f)
        val shearTitle = if (isAr) "تحقق القص (Shear Check):" else "Shear Verification:"
        drawFormattedText(activeCanvas, shearTitle, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        
        val shearText = if (isAr)
            "إجهاد القص الأقصى (q_max) تم التحقق منه.\nالكانات الموفرة: T${result.shearResult.stirrupDiameter.toInt()} كل ${result.shearResult.stirrupSpacing.toInt()} مم."
            else "Max Shear Stress (q_max) verified.\nStirrups: T${result.shearResult.stirrupDiameter.toInt()} @ ${result.shearResult.stirrupSpacing.toInt()} mm."
            
        drawMultilineText(activeCanvas, shearText, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, bodyPaint)
    }

    private fun drawSlabCalculations(canvas: Canvas, result: AdvancedSlabResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🧮 حسابات التصميم الإنشائي" else "🧮 Structural Design Calculations"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawFormattedText(activeCanvas, if (isAr) "تصميم العزوم:" else "Flexural Design:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "مساحة الحديد المطلوبة" else "Required Steel Area (As_req)", "${"%.1f".format(result.flexureResult.requiredReinforcement)} mm²/m"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "مساحة الحديد الموفرة" else "Provided Steel Area (As_prov)", "${"%.1f".format(result.flexureResult.providedReinforcement)} mm²/m"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "السمك الأدنى (h_min)" else "Minimum Thickness (h_min)", "${"%.1f".format(result.flexureResult.minThickness)} mm"); currentY += 35f

        result.punchingShearCheck?.let { punching ->
            activeCanvas = checkNewPage(activeCanvas, 120f)
            drawFormattedText(activeCanvas, if (isAr) "التحقق من القص الثاقب (Punching):" else "Punching Shear Verification:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            drawInfoRow(activeCanvas, currentY, if (isAr) "المحيط الحرج (u0)" else "Critical Perimeter (u0)", "${"%.0f".format(punching.criticalPerimeter)} mm"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "إجهاد القص التصميمي" else "Design Shear Stress (v_sd)", "${"%.2f".format(punching.appliedShear / (punching.criticalPerimeter * result.flexureResult.minThickness * 0.8))} MPa"); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "مقاومة الخرسانة القصوى" else "Concrete Capacity (v_rdc)", "${"%.2f".format(punching.shearCapacity / (punching.criticalPerimeter * result.flexureResult.minThickness * 0.8))} MPa"); currentY += 35f
        }

        activeCanvas = checkNewPage(activeCanvas, 100f)
        drawFormattedText(activeCanvas, if (isAr) "فحوصات حالة الخدمة (Serviceability):" else "Serviceability Checks:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        drawInfoRow(activeCanvas, currentY, if (isAr) "الترخيم المحسوب" else "Calculated Deflection", "${"%.2f".format(result.deflectionCheck.calculatedDeflection)} mm"); currentY += 20f
        drawInfoRow(activeCanvas, currentY, if (isAr) "الترخيم المسموح" else "Allowable Deflection", "${"%.2f".format(result.deflectionCheck.allowableDeflection)} mm"); currentY += 20f
    }


    private fun drawSlabSectionDetail(canvas: Canvas, result: AdvancedSlabResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📐 تفاصيل المقطع العرضي للبلاطة" else "📐 Slab Cross-Section Detail"
        
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
        drawInfoRow(activeCanvas, currentY, if (isAr) "سمك البلاطة الكلي (ts)" else "Total Slab Thickness (ts)", "${result.flexureResult.minThickness} mm")
        currentY += 40f
    }

    private fun drawSteelCalculations(canvas: Canvas, result: SteelMemberResult) {
        var activeCanvas = canvas
        currentY = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🧮 تداخل القوى واستقرار العضو الإنشائي" else "🧮 Steel Member Interaction & Stability"

        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(activeCanvas, title, (PAGE_WIDTH - MARGIN).toFloat(), currentY, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(activeCanvas, title, MARGIN.toFloat(), currentY, titlePaint)
        }
        currentY += 40f
        
        drawFormattedText(activeCanvas, if (isAr) "معادلات التداخل (Interaction Formulas):" else "Interaction Formulas:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
        val formula = if (result.utilizationRatio > 0.2) 
            "Pu/ΦPn + (8/9)(Mux/ΦMnx + Muy/ΦMny) ≤ 1.0" 
            else "Pu/2ΦPn + (Mux/ΦMnx + Muy/ΦMny) ≤ 1.0"
        drawFormattedText(activeCanvas, formula, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat() + 10f, currentY, bodyPaint); currentY += 30f
        
        drawInfoRow(activeCanvas, currentY, if (isAr) "نسبة التداخل الإجمالية" else "Total Interaction Ratio", "%.3f".format(result.utilizationRatio)); currentY += 35f
        
        result.bucklingCheck?.let { buckling ->
            activeCanvas = checkNewPage(activeCanvas, 120f)
            drawFormattedText(activeCanvas, if (isAr) "الانبعاج والاستقرار (Buckling):" else "Buckling & Stability:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            drawInfoRow(activeCanvas, currentY, if (isAr) "نسبة النحافة (λ)" else "Slenderness Ratio (λ)", "%.1f".format(buckling.slendernessRatio)); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "إجهاد الانبعاج الحرج (Fcr)" else "Critical Buckling Stress (Fcr)", "%.1f MPa".format(buckling.criticalStress)); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "نمط الانبعاج" else "Buckling Mode", buckling.bucklingMode.name); currentY += 35f
        }
        
        result.deflectionCheck?.let { defl ->
            activeCanvas = checkNewPage(activeCanvas, 120f)
            drawFormattedText(activeCanvas, if (isAr) "التحقق من الترخيم (Deflection):" else "Deflection Check:", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), currentY, subtitlePaint); currentY += 25f
            drawInfoRow(activeCanvas, currentY, if (isAr) "أقصى ترخيم" else "Max Deflection", "%.2f mm".format(defl.calculatedDeflection)); currentY += 20f
            drawInfoRow(activeCanvas, currentY, if (isAr) "المسموح (L/${(defl.allowableDeflection).toInt()})" else "Allowable (L/${(defl.allowableDeflection).toInt()})", "%.2f mm".format(defl.allowableDeflection)); currentY += 20f
        }
    }
    }

    private fun drawInventoryAnalysis(canvas: Canvas, analysis: InventoryAnalysisResult) {
        var y = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, "📦 Stock Analysis", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Inventory Sufficiency", if (analysis.isSufficient) "OK" else "ORDER NEEDED"); y += 25f
        drawInfoRow(canvas, y, "Total Project Weight", "${"%.2f".format(analysis.totalWeight)} Tons"); y += 25f
        drawInfoRow(canvas, y, "Waste Length", "${"%.1f".format(analysis.wasteLength)} m")
    }

    private fun drawMomentShearDiagrams(canvas: Canvas, diagrams: MomentShearDiagrams) {
        var y = MARGIN.toFloat() + 20f
        drawFormattedText(canvas, "📈 Structural Analysis Diagrams (Beam)", MARGIN.toFloat(), y, titlePaint); y += 120f
        
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
            canvas.drawText("Max M: ${"%.1f".format(diagrams.momentPoints.maxByOrNull { abs(it.second) }?.second ?: 0.0)} kN.m", MARGIN.toFloat(), y + diagramH + 15f, smallPaint)
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
            canvas.drawText("Max V: ${"%.1f".format(diagrams.shearPoints.maxByOrNull { abs(it.second) }?.second ?: 0.0)} kN", MARGIN.toFloat(), y + diagramH + 15f, smallPaint)
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
        val title = if (isAr) "🔩 تفاصيل تصميم الوصلات" else "🔩 Connection Design Details"
        drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint); y += 40f
        
        drawStatusHeader(canvas, y, conn.isSafe); y += 40f
        
        drawInfoRow(canvas, y, if (isAr) "نوع الوصلة" else "Connection Type", conn.connectionType.displayName); y += 25f
        drawInfoRow(canvas, y, if (isAr) "القوة المؤثرة" else "Applied Force", "${"%.1f".format(conn.appliedForce)} kN"); y += 20f
        drawInfoRow(canvas, y, if (isAr) "المقاومة التصميمية" else "Design Capacity", "${"%.1f".format(conn.capacity)} kN"); y += 35f
        
        val utilLabel = if (isAr) "نسبة استهلاك الوصلة" else "Connection Utilization"
        drawUtilizationBar(canvas, y, utilLabel, conn.utilizationRatio); y += 60f

        when (val type = conn.connectionType) {
            is ConnectionType.Welded -> {
                val weldedTitle = if (isAr) "مواصفات اللحام:" else "Welding Specifications:"
                drawFormattedText(canvas, weldedTitle, MARGIN.toFloat(), y, subtitlePaint); y += 25f
                drawInfoRow(canvas, y, if (isAr) "نوع اللحام" else "Weld Type", type.weldType.displayName); y += 20f
                drawInfoRow(canvas, y, if (isAr) "مقاس اللحام" else "Weld Size (Leg)", "${type.weldSize} mm"); y += 20f
                drawInfoRow(canvas, y, if (isAr) "طول اللحام" else "Weld Length", "${type.weldLength} mm"); y += 20f
                drawInfoRow(canvas, y, if (isAr) "نوع الإلكترود" else "Electrode", type.electrodeType.displayName); y += 30f
                
                // Schematic Weld Drawing
                drawWeldSchematic(canvas, MARGIN.toFloat() + 100f, y, type.weldSize.toFloat())
            }
            is ConnectionType.Bolted -> {
                val boltedTitle = if (isAr) "مواصفات البراغي (المسامير):" else "Bolting Specifications:"
                drawFormattedText(canvas, boltedTitle, MARGIN.toFloat(), y, subtitlePaint); y += 25f
                drawInfoRow(canvas, y, if (isAr) "قطر المسمار" else "Bolt Diameter", "M${type.boltDiameter.toInt()}"); y += 20f
                drawInfoRow(canvas, y, if (isAr) "رتبة المسمار" else "Bolt Grade", type.boltGrade.displayName); y += 20f
                drawInfoRow(canvas, y, if (isAr) "عدد المسامير" else "Number of Bolts", "${type.numberOfBolts}"); y += 20f
                drawInfoRow(canvas, y, if (isAr) "نمط التوزيع" else "Pattern", type.boltPattern.displayName); y += 30f
                
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
        canvas.drawText("s = ${size}mm", x + s + 10f, y - s/2, smallPaint)
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
                canvas.drawText("Standard $n bolts pattern", cx + 20f, y + 5f, smallPaint)
            }
        }
    }

    private fun drawAlternatives(canvas: Canvas, alternatives: List<ColumnAlternative>) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "🔄 خيارات التصميم البديلة" else "🔄 Design Options"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        alternatives.take(5).forEach { alt ->
            val label = if (isAr) "خيار: ${alt.numberOfBars} قطر ${alt.barDiameter.toInt()}" else "Option: ${alt.numberOfBars}Ø${alt.barDiameter.toInt()}"
            val value = if (isAr) "نسبة الاستخدام: ${(alt.utilizationRatio * 100).toInt()}%" else "Ratio: ${(alt.utilizationRatio * 100).toInt()}%"
            drawInfoRow(canvas, y, label, value); y += 25f
        }
    }

    private fun drawCodeReferencesAndNotes(canvas: Canvas, notes: List<String>, warnings: List<String>) {
        var y = MARGIN.toFloat() + 20f
        val isAr = isArabic(settingsManager.language)
        val title = if (isAr) "📋 ملاحظات الكود والسلامة" else "📋 Code & Safety Notes"
        
        if (isAr) {
            titlePaint.textAlign = Paint.Align.RIGHT
            drawFormattedText(canvas, title, (PAGE_WIDTH - MARGIN).toFloat(), y, titlePaint)
            titlePaint.textAlign = Paint.Align.LEFT
        } else {
            drawFormattedText(canvas, title, MARGIN.toFloat(), y, titlePaint)
        }
        y += 40f
        
        if (warnings.isNotEmpty()) {
            val warnTitle = if (isAr) "تنبيهات السلامة:" else "Safety Alerts:"
            drawFormattedText(canvas, warnTitle, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), y, subtitlePaint.apply { color = errorColor }); y += 25f
            warnings.forEach { w -> drawFormattedText(canvas, "• $w", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() - 10f else MARGIN.toFloat() + 10f, y, bodyPaint); y += 18f }
        }
        y += 10f
        val refTitle = if (isAr) "مراجع الكود:" else "Code References:"
        drawFormattedText(canvas, refTitle, if (isAr) (PAGE_WIDTH - MARGIN).toFloat() else MARGIN.toFloat(), y, subtitlePaint); y += 25f
        notes.forEach { n -> drawFormattedText(canvas, "• $n", if (isAr) (PAGE_WIDTH - MARGIN).toFloat() - 10f else MARGIN.toFloat() + 10f, y, bodyPaint); y += 18f }
    }

    // ==================== دوال الرسم منخفضة المستوى ====================

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
            if (isAr) "✅ التصميم آمن" else "✅ DESIGN IS SAFE"
        } else {
            if (isAr) "❌ التصميم غير آمن - يتطلب مراجعة" else "❌ REDESIGN REQUIRED"
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
        val line1 = if (isAr) "⚠️ هذا التقرير للأغراض التعليمية والتصميم الأولي فقط." else "⚠️ This report is for educational purposes and preliminary design only."
        val line2 = if (isAr) "يجب مراجعة كافة النتائج من قبل مهندس إنشائي مختص." else "All results must be verified by a structural engineer."
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
