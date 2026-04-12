package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.calculations.base.*
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
    
    // تعريف أدوات الرسم (Paints)
    private val titlePaint = Paint().apply {
        color = primaryColor
        textSize = 18f
        isFakeBoldText = true
        isAntiAlias = true
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
            drawPage(document, pageNum++) { drawCoverContent(it, projectName, designCode, "Column Design") }
            drawPage(document, pageNum++) { drawInputsSection(it, inputs, columnType) }
            drawPage(document, pageNum++) { drawMainResults(it, result) }
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
            drawPage(document, pageNum++) { drawCoverContent(it, projectName, designCode, "Beam Design") }
            drawPage(document, pageNum++) { drawBeamInputs(it, beamType, inputs) }
            drawPage(document, pageNum++) { drawBeamResults(it, result) }
            drawPage(document, pageNum++) { drawBeamSectionPage(it, result) }
            drawPage(document, pageNum++) { drawMomentShearDiagrams(it, momentShearDiagrams) }
            drawPage(document, pageNum++) { drawBeamCalculations(it, result) }
            
            if (inventoryAnalysis != null) {
                drawPage(document, pageNum++) { drawInventoryAnalysis(it, inventoryAnalysis) }
            }
            
            drawPage(document, pageNum) { drawCodeReferencesAndNotes(it, result.codeNotes, result.warnings) }
            
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
            drawPage(document, pageNum++) { drawCoverContent(it, projectName, designCode, "Slab Design") }
            drawPage(document, pageNum++) { drawSlabInputs(it, slabType, inputs) }
            drawPage(document, pageNum++) { drawSlabResults(it, result) }
            drawPage(document, pageNum++) { drawSlabQuantities(it, result) }
            drawPage(document, pageNum++) { drawSlabLayoutPage(it, result.reinforcementLayout) }
            drawPage(document, pageNum++) { drawSlabCalculations(it, result) }
            drawPage(document, pageNum) { drawCodeReferencesAndNotes(it, result.codeNotes, result.warnings) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
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
            drawPage(document, pageNum++) { drawCoverContent(it, projectName, designCode, "Steel Structure") }
            drawPage(document, pageNum++) { drawSteelInputs(it, sectionType, memberType, inputs) }
            drawPage(document, pageNum++) { drawSteelResults(it, result) }
            drawPage(document, pageNum++) { drawSteelSectionPage(it, sectionType) }
            
            if (connectionDesign != null) {
                drawPage(document, pageNum++) { drawConnectionDetails(it, connectionDesign) }
            }
            
            drawPage(document, pageNum++) { drawSteelCalculations(it, result) }
            drawPage(document, pageNum) { drawCodeReferencesAndNotes(it, result.codeNotes, result.warnings) }
            
            val file = File(outputPath)
            document.writeTo(FileOutputStream(file))
            return file
        } finally {
            document.close()
        }
    }

    // ==================== دوال المساعدة للرسم والصفحات ====================

    private fun drawPage(document: PdfDocument, pageNum: Int, drawBlock: (Canvas) -> Unit) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        val page = document.startPage(pageInfo)
        drawBlock(page.canvas)
        document.finishPage(page)
    }

    private fun drawCoverContent(canvas: Canvas, projectName: String, designCode: DesignCode, reportType: String) {
        var y = 150f
        canvas.drawText("Civil Engineer Pro", PAGE_WIDTH / 2f, y, titlePaint.apply { textAlign = Paint.Align.CENTER; textSize = 28f })
        y += 50f
        
        canvas.drawText(reportType, PAGE_WIDTH / 2f, y, subtitlePaint.apply { textAlign = Paint.Align.CENTER; textSize = 20f })
        y += 60f
        
        canvas.drawLine(MARGIN.toFloat(), y, PAGE_WIDTH - MARGIN.toFloat(), y, Paint().apply { color = primaryColor; strokeWidth = 2f })
        y += 40f
        
        drawInfoRow(canvas, y, "Project:", projectName); y += 30f
        drawInfoRow(canvas, y, "Code:", designCode.displayName); y += 30f
        drawInfoRow(canvas, y, "Date:", getCurrentDate()); y += 80f
        
        drawDisclaimer(canvas, y)
    }

    private fun drawInputsSection(canvas: Canvas, inputs: ColumnInputs, type: ColumnType) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📐 Input Parameters", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Column Type", type.displayName); y += 25f
        drawInfoRow(canvas, y, "Axial Load (Pu)", "${inputs.axialLoad} kN"); y += 20f
        drawInfoRow(canvas, y, "Moments Mx / My", "${inputs.momentX} / ${inputs.momentY} kN.m"); y += 20f
        drawInfoRow(canvas, y, "Concrete fcu", "${inputs.fcu} MPa"); y += 20f
        drawInfoRow(canvas, y, "Steel fy", "${inputs.fy} MPa")
    }

    private fun drawBeamInputs(canvas: Canvas, beamType: BeamType, inputs: BeamInputs) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📐 Beam Parameters", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Beam Type", beamType.displayName); y += 25f
        drawInfoRow(canvas, y, "Section b x h", "${inputs.width} x ${inputs.totalDepth} mm"); y += 20f
        drawInfoRow(canvas, y, "Design Moment", "${inputs.designMoment} kN.m"); y += 20f
        drawInfoRow(canvas, y, "Design Shear", "${inputs.designShear} kN")
    }

    private fun drawSlabInputs(canvas: Canvas, slabType: SlabType, inputs: SlabInputs) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📐 Slab Parameters", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Slab Type", slabType.displayName); y += 25f
        drawInfoRow(canvas, y, "Thickness", "${inputs.thickness} mm"); y += 20f
        drawInfoRow(canvas, y, "Total Load", "${inputs.deadLoad + inputs.liveLoad} kN/m²")
    }

    private fun drawSteelInputs(canvas: Canvas, section: SteelSectionType, member: SteelMemberType, inputs: SteelInputs) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📐 Steel Member Parameters", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Member Type", member.name); y += 25f
        drawInfoRow(canvas, y, "Section Type", section.displayName); y += 20f
        drawInfoRow(canvas, y, "Length / Load", "${inputs.unbracedLength} m / ${inputs.axialLoad} kN")
    }

    private fun drawMainResults(canvas: Canvas, result: AdvancedColumnResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📊 Design Summary", MARGIN.toFloat(), y, titlePaint); y += 40f
        val isSafe = result.reinforcementResult.isSafe && (result.biaxialCheck?.isSafe ?: true)
        drawStatusHeader(canvas, y, isSafe); y += 40f
        drawInfoRow(canvas, y, "Provided Steel", "${result.reinforcementResult.numberOfBars} Ø${result.reinforcementResult.barDiameter}"); y += 25f
        drawUtilizationBar(canvas, y, "Axial Utilization", result.reinforcementResult.utilizationRatio)
    }

    private fun drawBeamResults(canvas: Canvas, result: AdvancedBeamResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📊 Design Results", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Main Steel", "${result.flexureResult.numberOfBars} Ø${result.flexureResult.barDiameter}"); y += 25f
        drawInfoRow(canvas, y, "Stirrups", "Ø${result.shearResult.stirrupDiameter} @ ${result.shearResult.stirrupSpacing} mm"); y += 25f
        drawInfoRow(canvas, y, "Deflection", if (result.deflectionCheck.isSafe) "SAFE" else "EXCEEDS LIMIT")
    }

    private fun drawSlabResults(canvas: Canvas, result: AdvancedSlabResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📊 Slab Results", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Bottom Bars", "${result.reinforcementLayout.bottomBars.numberOfBars} Ø${result.reinforcementLayout.bottomBars.diameter}"); y += 25f
        drawInfoRow(canvas, y, "Punching", if (result.punchingShearCheck?.isSafe == true) "SAFE" else "CHECK REQUIRED")
    }

    private fun drawSlabQuantities(canvas: Canvas, result: AdvancedSlabResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📊 Quantities & Estimates", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Concrete Volume", "${"%.2f".format(result.concreteVolume)} m³"); y += 25f
        drawInfoRow(canvas, y, "Formwork Area", "${"%.2f".format(result.formworkArea)} m²"); y += 25f
        
        if (result.inventoryAnalysis != null) {
            drawInfoRow(canvas, y, "Total Steel Weight", "${"%.2f".format(result.inventoryAnalysis.totalWeight)} Tons")
        }
    }

    private fun drawSteelResults(canvas: Canvas, result: SteelMemberResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📊 Steel Member Results", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawUtilizationBar(canvas, y, "Total Interaction", result.utilizationRatio); y += 30f
        drawInfoRow(canvas, y, "Weight", "${"%.1f".format(result.weight)} kg/m")
    }

    private fun drawColumnSectionPage(canvas: Canvas, type: ColumnType, reinf: ReinforcementResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📐 Cross Section Drawing", MARGIN.toFloat(), y, titlePaint); y += 40f
        val area = RectF(MARGIN.toFloat(), y, PAGE_WIDTH - MARGIN.toFloat(), PAGE_HEIGHT - MARGIN.toFloat() - 50f)
        when (type) {
            is ColumnType.Rectangular -> drawRectangularSection(canvas, area, type, reinf)
            is ColumnType.Circular -> drawCircularSection(canvas, area, type, reinf)
            else -> drawPlaceholder(canvas, area)
        }
    }

    private fun drawBeamSectionPage(canvas: Canvas, result: AdvancedBeamResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📐 Beam Cross Section", MARGIN.toFloat(), y, titlePaint); y += 40f
        val area = RectF(MARGIN.toFloat(), y, PAGE_WIDTH - MARGIN.toFloat(), PAGE_HEIGHT - MARGIN.toFloat() - 50f)
        
        val w = 180f; val h = 280f
        val left = area.centerX() - w/2; val top = area.centerY() - h/2
        canvas.drawRect(left, top, left + w, top + h, Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 2f })
        
        val reinf = result.flexureResult
        val sp = (w - 40f) / (reinf.numberOfBars.coerceAtLeast(2) - 1)
        for (i in 0 until reinf.numberOfBars.coerceAtMost(10)) {
            canvas.drawCircle(left + 20f + i * sp, top + h - 30f, 6f, Paint().apply { color = Color.RED; style = Paint.Style.FILL })
        }
    }

    private fun drawSlabLayoutPage(canvas: Canvas, layout: ReinforcementLayout) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📐 Slab Reinforcement Plan", MARGIN.toFloat(), y, titlePaint); y += 40f
        val text = "Main Direction: ${layout.bottomBars.numberOfBars} Ø${layout.bottomBars.diameter} @ ${layout.bottomBars.spacing} mm\n" +
                   "Top/Secondary: ${layout.topBars.numberOfBars} Ø${layout.topBars.diameter} @ ${layout.topBars.spacing} mm"
        drawMultilineText(canvas, text, MARGIN.toFloat(), y, bodyPaint)
    }

    private fun drawSteelSectionPage(canvas: Canvas, section: SteelSectionType) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📐 Steel Section Details", MARGIN.toFloat(), y, titlePaint); y += 40f
        if (section is SteelSectionType.ISection) {
            val h = 200f; val bf = 120f
            val cx = PAGE_WIDTH / 2f; val cy = PAGE_HEIGHT / 2f
            canvas.drawRect(cx - bf/2, cy - h/2, cx + bf/2, cy - h/2 + 10f, titlePaint)
            canvas.drawRect(cx - bf/2, cy + h/2 - 10f, cx + bf/2, cy + h/2, titlePaint)
            canvas.drawRect(cx - 5f, cy - h/2 + 10f, cx + 5f, cy + h/2 - 10f, titlePaint)
        }
    }

    private fun drawDetailedCalculations(canvas: Canvas, result: AdvancedColumnResult, code: DesignCode) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("🧮 Design Formulas (${code.displayName})", MARGIN.toFloat(), y, titlePaint); y += 40f
        val text = "Capacity Pn = 0.8 * [0.67 * fcu * (Ag - Ast) + fy * Ast]\n" +
                   "Ag = ${"%.0f".format(result.columnType.getGrossArea())} mm²\n" +
                   "Ast = ${"%.0f".format(result.reinforcementResult.astProvided)} mm²\n" +
                   "Result = ${"%.1f".format(result.axialCapacity)} kN"
        drawMultilineText(canvas, text, MARGIN.toFloat(), y, bodyPaint)
    }

    private fun drawBeamCalculations(canvas: Canvas, result: AdvancedBeamResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("🧮 Flexural Calculations", MARGIN.toFloat(), y, titlePaint); y += 40f
        val text = "Required As = Mu / (0.87 * fy * d)\nProvided Steel = ${"%.0f".format(result.flexureResult.astProvided)} mm²"
        drawMultilineText(canvas, text, MARGIN.toFloat(), y, bodyPaint)
    }

    private fun drawSlabCalculations(canvas: Canvas, result: AdvancedSlabResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("🧮 Slab Thickness & Moment", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Minimum Thickness", "${result.flexureResult.minThickness} mm"); y += 25f
        drawInfoRow(canvas, y, "Steel Provided", "${result.reinforcementLayout.bottomBars.numberOfBars} bars")
    }

    private fun drawSteelCalculations(canvas: Canvas, result: SteelMemberResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("🧮 Steel Member Interaction", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawMultilineText(canvas, "Interaction Ratio: Pu/ΦPn + (8/9)(Mu/ΦMn) <= 1.0\nResult: ${"%.3f".format(result.utilizationRatio)}", MARGIN.toFloat(), y, bodyPaint)
    }

    private fun drawInventoryAnalysis(canvas: Canvas, analysis: InventoryAnalysisResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📦 Stock Analysis", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Inventory Sufficiency", if (analysis.isSufficient) "OK" else "ORDER NEEDED"); y += 25f
        drawInfoRow(canvas, y, "Total Project Weight", "${"%.2f".format(analysis.totalWeight)} Tons"); y += 25f
        drawInfoRow(canvas, y, "Waste Length", "${"%.1f".format(analysis.wasteLength)} m")
    }

    private fun drawMomentShearDiagrams(canvas: Canvas, diagrams: MomentShearDiagrams) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📈 Diagrams", MARGIN.toFloat(), y, titlePaint); y += 150f
        canvas.drawRect(MARGIN.toFloat(), y - 100f, PAGE_WIDTH - MARGIN.toFloat(), y, Paint().apply { color = lightGrayColor; style = Paint.Style.STROKE })
        canvas.drawText("Moment (Max: ${diagrams.momentPoints.maxOfOrNull { it.second } ?: 0.0} kNm)", MARGIN.toFloat() + 10f, y - 80f, smallPaint)
    }

    private fun drawConnectionDetails(canvas: Canvas, conn: ConnectionDesignResult) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("🔩 Connection Design", MARGIN.toFloat(), y, titlePaint); y += 40f
        drawInfoRow(canvas, y, "Type", conn.connectionType.displayName); y += 25f
        drawInfoRow(canvas, y, "Capacity vs Applied", "${conn.capacity} / ${conn.appliedForce} kN")
    }

    private fun drawAlternatives(canvas: Canvas, alternatives: List<ColumnAlternative>) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("🔄 Design Options", MARGIN.toFloat(), y, titlePaint); y += 40f
        alternatives.take(5).forEach { alt ->
            drawInfoRow(canvas, y, "Option: ${alt.numberOfBars}Ø${alt.barDiameter.toInt()}", "Ratio: ${(alt.utilizationRatio * 100).toInt()}%"); y += 25f
        }
    }

    private fun drawCodeReferencesAndNotes(canvas: Canvas, notes: List<String>, warnings: List<String>) {
        var y = MARGIN.toFloat() + 20f
        canvas.drawText("📋 Code & Safety Notes", MARGIN.toFloat(), y, titlePaint); y += 40f
        if (warnings.isNotEmpty()) {
            canvas.drawText("Safety Alerts:", MARGIN.toFloat(), y, subtitlePaint.apply { color = errorColor }); y += 25f
            warnings.forEach { w -> canvas.drawText("• $w", MARGIN.toFloat() + 10f, y, bodyPaint); y += 18f }
        }
        y += 10f
        canvas.drawText("Code References:", MARGIN.toFloat(), y, subtitlePaint); y += 25f
        notes.forEach { n -> canvas.drawText("• $n", MARGIN.toFloat() + 10f, y, bodyPaint); y += 18f }
    }

    // ==================== دوال الرسم منخفضة المستوى ====================

    private fun drawInfoRow(canvas: Canvas, y: Float, label: String, value: String) {
        canvas.drawText(label, MARGIN.toFloat(), y, smallPaint)
        canvas.drawText(value, PAGE_WIDTH - MARGIN.toFloat(), y, bodyPaint.apply { textAlign = Paint.Align.RIGHT; isFakeBoldText = true })
    }

    private fun drawStatusHeader(canvas: Canvas, y: Float, isSafe: Boolean) {
        val color = if (isSafe) successColor else errorColor
        val text = if (isSafe) "✅ DESIGN IS SAFE" else "❌ REDESIGN REQUIRED"
        canvas.drawText(text, PAGE_WIDTH / 2f, y, titlePaint.apply { this.color = color; textAlign = Paint.Align.CENTER })
    }

    private fun drawUtilizationBar(canvas: Canvas, y: Float, label: String, ratio: Double) {
        canvas.drawText(label, MARGIN.toFloat(), y, bodyPaint)
        val barW = 100f; val barH = 12f; val left = PAGE_WIDTH - MARGIN.toFloat() - barW
        canvas.drawRect(left, y - 10f, left + barW, y - 10f + barH, Paint().apply { color = lightGrayColor })
        val fillW = (barW * ratio.coerceIn(0.0, 1.0)).toFloat()
        canvas.drawRect(left, y - 10f, left + fillW, y - 10f + barH, Paint().apply { color = if (ratio > 0.9) errorColor else successColor })
        canvas.drawText("${(ratio * 100).toInt()}%", left - 35f, y, smallPaint)
    }

    private fun drawRectangularSection(canvas: Canvas, area: RectF, col: ColumnType.Rectangular, reinf: ReinforcementResult) {
        val scale = min(area.width() / col.width.toFloat(), area.height() / col.depth.toFloat()) * 0.6f
        val w = col.width.toFloat() * scale; val h = col.depth.toFloat() * scale
        val l = area.centerX() - w/2; val t = area.centerY() - h/2
        canvas.drawRect(l, t, l + w, t + h, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f })
        val cover = 40f * scale
        val positions = calculateRebarPositions(col.width, col.depth, reinf.numberOfBars, l, t, w, h, cover)
        positions.forEach { p -> canvas.drawCircle(p.x, p.y, 4f, Paint().apply { color = Color.RED; style = Paint.Style.FILL }) }
    }

    private fun drawCircularSection(canvas: Canvas, area: RectF, col: ColumnType.Circular, reinf: ReinforcementResult) {
        val scale = (min(area.width(), area.height()) / col.diameter.toFloat()) * 0.6f
        val radius = (col.diameter.toFloat() * scale) / 2
        canvas.drawCircle(area.centerX(), area.centerY(), radius, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f })
        val inner = radius - 40f * scale
        for (i in 0 until reinf.numberOfBars) {
            val a = i * 2 * PI / reinf.numberOfBars
            canvas.drawCircle(area.centerX() + inner * cos(a).toFloat(), area.centerY() + inner * sin(a).toFloat(), 4f, Paint().apply { color = Color.RED; style = Paint.Style.FILL })
        }
    }

    private fun drawPlaceholder(canvas: Canvas, area: RectF, text: String = "Section Drawing") {
        canvas.drawText(text, area.centerX(), area.centerY(), smallPaint.apply { textAlign = Paint.Align.CENTER })
    }

    private fun drawDisclaimer(canvas: Canvas, y: Float) {
        val paint = Paint().apply { color = textColor; textSize = 9f; textAlign = Paint.Align.CENTER }
        canvas.drawText("⚠️ This report is for educational purposes and preliminary design only.", PAGE_WIDTH / 2f, y, paint)
        canvas.drawText("All results must be verified by a structural engineer.", PAGE_WIDTH / 2f, y + 15f, paint)
    }

    private fun calculateRebarPositions(w: Double, d: Double, n: Int, l: Float, t: Float, rw: Float, rh: Float, c: Float): List<PointF> {
        val list = mutableListOf<PointF>()
        list.add(PointF(l + c, t + c)); list.add(PointF(l + rw - c, t + c))
        list.add(PointF(l + c, t + rh - c)); list.add(PointF(l + rw - c, t + rh - c))
        return list.take(n)
    }

    private fun getCurrentDate() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    private fun drawMultilineText(c: Canvas, text: String, x: Float, y: Float, p: Paint) {
        var cy = y; text.split("\n").forEach { line -> c.drawText(line, x, cy, p); cy += p.textSize * 1.5f }
    }
}
