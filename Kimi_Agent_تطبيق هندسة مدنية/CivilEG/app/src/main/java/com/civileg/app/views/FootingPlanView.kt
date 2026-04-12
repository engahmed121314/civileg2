package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Interactive Footing Plan and Section View
 * Shows isolated footing with column, dimensions, and reinforcement
 */
class FootingPlanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ViewMode { PLAN, SECTION }

    // Footing dimensions in mm
    var footingLength: Float = 2000f
    var footingWidth: Float = 2000f
    var footingThickness: Float = 400f
    var columnLength: Float = 400f
    var columnWidth: Float = 400f
    var columnHeight: Float = 3000f

    // Reinforcement
    var bottomBarsX: Int = 10  // Number of bars in X direction
    var bottomBarsY: Int = 10  // Number of bars in Y direction
    var barDiameter: Int = 16
    var barSpacingX: Float = 180f
    var barSpacingY: Float = 180f

    // Calculation results
    var soilPressure: Double = 0.0
    var allowablePressure: Double = 0.0
    var punchingShearStress: Double = 0.0
    var allowableShearStress: Double = 0.0
    var isSafe: Boolean = true
    var requiredArea: Double = 0.0

    // View mode
    var viewMode: ViewMode = ViewMode.PLAN

    // Paints
    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        style = Paint.Style.FILL
    }

    private val concreteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val columnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        style = Paint.Style.FILL
    }

    private val columnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#424242")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val steelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val steelDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.FILL
    }

    private val dimensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val dimensionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        textSize = 22f
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        textSize = 22f
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C27B0")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (viewMode) {
            ViewMode.PLAN -> drawPlanView(canvas)
            ViewMode.SECTION -> drawSectionView(canvas)
        }
    }

    private fun drawPlanView(canvas: Canvas) {
        val padding = 60f
        val resultsHeight = 150f
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding - resultsHeight

        // Calculate scale
        val scaleX = availableWidth / footingLength
        val scaleY = availableHeight / footingWidth
        val scale = min(scaleX, scaleY)

        val drawLength = footingLength * scale
        val drawWidth = footingWidth * scale
        val startX = (width - drawLength) / 2
        val startY = padding + (availableHeight - drawWidth) / 2

        // Draw footing
        canvas.drawRect(startX, startY, startX + drawLength, startY + drawWidth, concretePaint)
        canvas.drawRect(startX, startY, startX + drawLength, startY + drawWidth, concreteBorderPaint)

        // Draw column
        val colDrawLength = columnLength * scale
        val colDrawWidth = columnWidth * scale
        val colX = startX + (drawLength - colDrawLength) / 2
        val colY = startY + (drawWidth - colDrawWidth) / 2
        canvas.drawRect(colX, colY, colX + colDrawLength, colY + colDrawWidth, columnPaint)
        canvas.drawRect(colX, colY, colX + colDrawLength, colY + colDrawWidth, columnBorderPaint)

        // Draw center lines
        val centerX = startX + drawLength / 2
        val centerY = startY + drawWidth / 2
        canvas.drawLine(centerX, startY - 20, centerX, startY + drawWidth + 20, centerLinePaint)
        canvas.drawLine(startX - 20, centerY, startX + drawLength + 20, centerY, centerLinePaint)

        // Draw reinforcement grid
        drawReinforcementGrid(canvas, startX, startY, drawLength, drawWidth, colX, colY, colDrawLength, colDrawWidth, scale)

        // Draw dimensions
        drawPlanDimensions(canvas, startX, startY, drawLength, drawWidth, colX, colY, colDrawLength, colDrawWidth)

        // Draw results
        drawResultsPanel(canvas, padding, height - resultsHeight + 20)
    }

    private fun drawSectionView(canvas: Canvas) {
        val padding = 60f
        val resultsHeight = 150f
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding - resultsHeight

        // Calculate scale
        val scaleX = availableWidth / footingLength
        val scaleY = availableHeight / (columnHeight + footingThickness)
        val scale = min(scaleX, scaleY * 0.8f)  // Adjust for column height

        val drawLength = footingLength * scale
        val drawThickness = footingThickness * scale
        val drawColHeight = (columnHeight * scale * 0.3f).coerceAtMost(200f)  // Scale down column height
        val drawColWidth = columnWidth * scale

        val startX = (width - drawLength) / 2
        val footingY = padding + availableHeight - drawThickness
        val columnY = footingY - drawColHeight

        // Draw soil/ground
        val groundPaint = Paint().apply {
            color = Color.parseColor("#8D6E63")
            style = Paint.Style.FILL
        }
        canvas.drawRect(startX, footingY + drawThickness, startX + drawLength, footingY + drawThickness + 30, groundPaint)

        // Draw footing section
        canvas.drawRect(startX, footingY, startX + drawLength, footingY + drawThickness, concretePaint)
        canvas.drawRect(startX, footingY, startX + drawLength, footingY + drawThickness, concreteBorderPaint)

        // Draw hatching for footing
        drawHatching(canvas, startX, footingY, drawLength, drawThickness)

        // Draw column section
        val colX = startX + (drawLength - drawColWidth) / 2
        canvas.drawRect(colX, columnY, colX + drawColWidth, footingY, columnPaint)
        canvas.drawRect(colX, columnY, colX + drawColWidth, footingY, columnBorderPaint)

        // Draw reinforcement bars in section
        val barRadius = (barDiameter / 2f) * scale * 0.8f
        val cover = 50f * scale

        // Bottom bars
        val numBars = bottomBarsX
        val spacing = (drawLength - 2 * cover) / (numBars - 1)
        for (i in 0 until numBars) {
            val x = startX + cover + spacing * i
            val y = footingY + drawThickness - cover
            canvas.drawCircle(x, y, barRadius.coerceAtLeast(4f), steelDotPaint)
        }

        // Draw dimensions
        drawSectionDimensions(canvas, startX, footingY, drawLength, drawThickness, columnY, drawColHeight, drawColWidth)

        // Draw results
        drawResultsPanel(canvas, padding, height - resultsHeight + 20)
    }

    private fun drawReinforcementGrid(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawWidth: Float,
        colX: Float, colY: Float,
        colDrawLength: Float, colDrawWidth: Float,
        scale: Float
    ) {
        // Bars in X direction (running along length)
        val cover = 50f * scale
        val barOffset = cover + (barDiameter / 2f) * scale

        // Calculate actual number of bars based on spacing
        val actualBarsY = ((footingWidth - 2 * 50) / barSpacingY).toInt() + 1
        val spacingY = (drawWidth - 2 * cover) / (actualBarsY - 1)

        for (i in 0 until actualBarsY) {
            val y = startY + cover + spacingY * i
            if (y < colY || y > colY + colDrawWidth) {
                // Full length bars
                canvas.drawLine(startX + barOffset, y, startX + drawLength - barOffset, y, steelPaint)
            } else {
                // Bars around column
                canvas.drawLine(startX + barOffset, y, colX - 10, y, steelPaint)
                canvas.drawLine(colX + colDrawLength + 10, y, startX + drawLength - barOffset, y, steelPaint)
            }
        }

        // Bars in Y direction (running along width)
        val actualBarsX = ((footingLength - 2 * 50) / barSpacingX).toInt() + 1
        val spacingX = (drawLength - 2 * cover) / (actualBarsX - 1)

        for (i in 0 until actualBarsX) {
            val x = startX + cover + spacingX * i
            if (x < colX || x > colX + colDrawLength) {
                // Full width bars
                canvas.drawLine(x, startY + barOffset, x, startY + drawWidth - barOffset, steelPaint)
            } else {
                // Bars around column
                canvas.drawLine(x, startY + barOffset, x, colY - 10, steelPaint)
                canvas.drawLine(x, colY + colDrawWidth + 10, x, startY + drawWidth - barOffset, steelPaint)
            }
        }

        // Draw bar labels
        val labelY = startY + drawWidth + 30
        canvas.drawText("Ø${barDiameter} @ ${barSpacingX.toInt()} mm", startX + 10, labelY, labelPaint)
    }

    private fun drawHatching(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val spacing = 15f
        val path = Path()
        var i = 0f
        while (i < w + h) {
            path.moveTo(x + i, y + h)
            path.lineTo(x + i - h, y)
            i += spacing
        }
        canvas.drawPath(path, hatchPaint)
    }

    private fun drawPlanDimensions(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawWidth: Float,
        colX: Float, colY: Float,
        colDrawLength: Float, colDrawWidth: Float
    ) {
        val arrowSize = 8f
        val offset = 40f

        // Footing length
        val dimY1 = startY - offset
        canvas.drawLine(startX, dimY1, startX + drawLength, dimY1, dimensionPaint)
        canvas.drawLine(startX, dimY1 - arrowSize, startX, dimY1 + arrowSize, dimensionPaint)
        canvas.drawLine(startX + drawLength, dimY1 - arrowSize, startX + drawLength, dimY1 + arrowSize, dimensionPaint)

        val lengthLabel = "L = ${footingLength.toInt()} mm"
        val lengthWidth = dimensionTextPaint.measureText(lengthLabel)
        canvas.drawText(lengthLabel, startX + (drawLength - lengthWidth) / 2, dimY1 - 10, dimensionTextPaint)

        // Footing width
        val dimX1 = startX + drawLength + offset
        canvas.drawLine(dimX1, startY, dimX1, startY + drawWidth, dimensionPaint)
        canvas.drawLine(dimX1 - arrowSize, startY, dimX1 + arrowSize, startY, dimensionPaint)
        canvas.drawLine(dimX1 - arrowSize, startY + drawWidth, dimX1 + arrowSize, startY + drawWidth, dimensionPaint)

        val widthLabel = "B = ${footingWidth.toInt()} mm"
        canvas.save()
        canvas.rotate(90f, dimX1 + 30, startY + drawWidth / 2)
        canvas.drawText(widthLabel, dimX1 + 30, startY + drawWidth / 2 + 10, dimensionTextPaint)
        canvas.restore()

        // Column dimensions
        canvas.drawText("${columnLength.toInt()} x ${columnWidth.toInt()}", colX + 5, colY + 25, labelPaint)
    }

    private fun drawSectionDimensions(
        canvas: Canvas,
        startX: Float, footingY: Float,
        drawLength: Float, drawThickness: Float,
        columnY: Float, drawColHeight: Float, drawColWidth: Float
    ) {
        val arrowSize = 8f

        // Footing thickness
        val dimX = startX + drawLength + 30
        canvas.drawLine(dimX, footingY, dimX, footingY + drawThickness, dimensionPaint)
        canvas.drawLine(dimX - arrowSize, footingY, dimX + arrowSize, footingY, dimensionPaint)
        canvas.drawLine(dimX - arrowSize, footingY + drawThickness, dimX + arrowSize, footingY + drawThickness, dimensionPaint)

        canvas.save()
        canvas.rotate(90f, dimX + 30, footingY + drawThickness / 2)
        canvas.drawText("t = ${footingThickness.toInt()} mm", dimX + 30, footingY + drawThickness / 2 + 10, dimensionTextPaint)
        canvas.restore()

        // Column width
        val colCenterX = startX + drawLength / 2
        canvas.drawLine(colCenterX - drawColWidth / 2, columnY - 20, colCenterX + drawColWidth / 2, columnY - 20, dimensionPaint)
        canvas.drawLine(colCenterX - drawColWidth / 2, columnY - 25, colCenterX - drawColWidth / 2, columnY - 15, dimensionPaint)
        canvas.drawLine(colCenterX + drawColWidth / 2, columnY - 25, colCenterX + drawColWidth / 2, columnY - 15, dimensionPaint)

        val colWidthLabel = "${columnWidth.toInt()} mm"
        val textWidth = dimensionTextPaint.measureText(colWidthLabel)
        canvas.drawText(colWidthLabel, colCenterX - textWidth / 2, columnY - 30, dimensionTextPaint)
    }

    private fun drawResultsPanel(canvas: Canvas, startX: Float, startY: Float) {
        var yOffset = startY

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1565C0")
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(context.getString(R.string.calculation_results), startX, yOffset, titlePaint)
        yOffset += 30

        // Soil pressure
        val pressureText = "${context.getString(R.string.soil_pressure)}: ${String.format("%.2f", soilPressure)} kN/m²"
        canvas.drawText(pressureText, startX, yOffset, if (soilPressure <= allowablePressure) resultPaint else warningPaint)
        yOffset += 28

        // Allowable pressure
        canvas.drawText("${context.getString(R.string.allowable_pressure)}: ${String.format("%.2f", allowablePressure)} kN/m²", startX, yOffset, dimensionTextPaint)
        yOffset += 28

        // Punching shear
        val shearText = "${context.getString(R.string.punching_shear)}: ${String.format("%.2f", punchingShearStress)} MPa"
        canvas.drawText(shearText, startX + 300, yOffset - 28, if (punchingShearStress <= allowableShearStress) resultPaint else warningPaint)

        // Safety status
        yOffset += 30
        val statusPaint = if (isSafe) resultPaint else warningPaint
        val statusText = if (isSafe) context.getString(R.string.design_safe) else context.getString(R.string.design_unsafe)
        canvas.drawText(statusText, startX, yOffset, statusPaint)
    }

    fun updateFromCalculation(
        length: Float,
        width: Float,
        thickness: Float,
        colLength: Float,
        colWidth: Float,
        colHeight: Float,
        barsX: Int,
        barsY: Int,
        barDia: Int,
        spacingX: Float,
        spacingY: Float,
        soilPress: Double,
        allowPress: Double,
        punchShear: Double,
        allowShear: Double,
        safe: Boolean,
        reqArea: Double
    ) {
        footingLength = length
        footingWidth = width
        footingThickness = thickness
        columnLength = colLength
        columnWidth = colWidth
        columnHeight = colHeight
        bottomBarsX = barsX
        bottomBarsY = barsY
        barDiameter = barDia
        barSpacingX = spacingX
        barSpacingY = spacingY
        soilPressure = soilPress
        allowablePressure = allowPress
        punchingShearStress = punchShear
        allowableShearStress = allowShear
        isSafe = safe
        requiredArea = reqArea
        invalidate()
    }
}
