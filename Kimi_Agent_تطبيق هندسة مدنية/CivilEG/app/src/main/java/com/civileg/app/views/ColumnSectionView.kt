package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Interactive Column Cross-Section View
 * Supports both rectangular and circular columns with reinforcement visualization
 */
class ColumnSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ColumnType { RECTANGULAR, CIRCULAR }

    // Column properties
    var columnType: ColumnType = ColumnType.RECTANGULAR
    var columnWidth: Float = 400f  // For rectangular
    var columnHeight: Float = 400f  // For rectangular
    var columnDiameter: Float = 400f  // For circular
    var cover: Float = 40f

    // Reinforcement
    var cornerBars: Int = 4
    var cornerBarDiameter: Int = 20
    var sideBarsX: Int = 2  // Bars on each side in X direction
    var sideBarsY: Int = 2  // Bars on each side in Y direction
    var sideBarDiameter: Int = 18
    var tieDiameter: Int = 10
    var isSpiral: Boolean = false

    // Calculation results
    var axialCapacity: Double = 0.0
    var momentCapacityX: Double = 0.0
    var momentCapacityY: Double = 0.0
    var appliedAxial: Double = 0.0
    var appliedMomentX: Double = 0.0
    var appliedMomentY: Double = 0.0
    var isSafe: Boolean = true

    // Paints
    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        style = Paint.Style.FILL
    }

    private val concreteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val steelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.FILL
    }

    private val tiePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E65100")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val spiralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E65100")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val dimensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val dimensionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        textSize = 24f
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        textSize = 24f
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C27B0")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 80f
        val resultsHeight = 180f
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding - resultsHeight

        when (columnType) {
            ColumnType.RECTANGULAR -> drawRectangularColumn(canvas, padding, availableWidth, availableHeight)
            ColumnType.CIRCULAR -> drawCircularColumn(canvas, padding, availableWidth, availableHeight)
        }

        // Draw results panel
        drawResultsPanel(canvas, padding.toFloat(), height - resultsHeight + 20)
    }

    private fun drawRectangularColumn(
        canvas: Canvas,
        padding: Float,
        availableWidth: Float,
        availableHeight: Float
    ) {
        // Calculate scale
        val scaleX = availableWidth / columnWidth
        val scaleY = availableHeight / columnHeight
        val scale = min(scaleX, scaleY)

        val drawWidth = columnWidth * scale
        val drawHeight = columnHeight * scale
        val startX = (width - drawWidth) / 2
        val startY = padding + (availableHeight - drawHeight) / 2

        // Draw concrete section
        canvas.drawRect(startX, startY, startX + drawWidth, startY + drawHeight, concretePaint)
        canvas.drawRect(startX, startY, startX + drawWidth, startY + drawHeight, concreteBorderPaint)

        // Draw center axes
        val centerX = startX + drawWidth / 2
        val centerY = startY + drawHeight / 2
        canvas.drawLine(centerX - drawWidth / 2 - 20, centerY, centerX + drawWidth / 2 + 20, centerY, axisPaint)
        canvas.drawLine(centerX, centerY - drawHeight / 2 - 20, centerX, centerY + drawHeight / 2 + 20, axisPaint)

        // Draw ties
        val tieLeft = startX + cover * scale
        val tieTop = startY + cover * scale
        val tieRight = startX + drawWidth - cover * scale
        val tieBottom = startY + drawHeight - cover * scale
        canvas.drawRect(tieLeft, tieTop, tieRight, tieBottom, tiePaint)

        // Draw corner bars
        val cornerOffset = cover * scale + tieDiameter * scale + cornerBarDiameter * scale / 2
        val cornerRadius = (cornerBarDiameter / 2f) * scale * 0.6f

        val corners = listOf(
            Pair(startX + cornerOffset, startY + cornerOffset),
            Pair(startX + drawWidth - cornerOffset, startY + cornerOffset),
            Pair(startX + cornerOffset, startY + drawHeight - cornerOffset),
            Pair(startX + drawWidth - cornerOffset, startY + drawHeight - cornerOffset)
        )

        corners.forEach { (x, y) ->
            canvas.drawCircle(x, y, cornerRadius.coerceAtLeast(6f), steelPaint)
        }

        // Draw side bars on X faces
        if (sideBarsX > 0) {
            val spacingX = (drawWidth - 2 * cornerOffset) / (sideBarsX + 1)
            for (i in 1..sideBarsX) {
                val x = startX + cornerOffset + spacingX * i
                val sideRadius = (sideBarDiameter / 2f) * scale * 0.6f
                canvas.drawCircle(x, startY + cornerOffset, sideRadius.coerceAtLeast(5f), steelPaint)
                canvas.drawCircle(x, startY + drawHeight - cornerOffset, sideRadius.coerceAtLeast(5f), steelPaint)
            }
        }

        // Draw side bars on Y faces
        if (sideBarsY > 0) {
            val spacingY = (drawHeight - 2 * cornerOffset) / (sideBarsY + 1)
            for (i in 1..sideBarsY) {
                val y = startY + cornerOffset + spacingY * i
                val sideRadius = (sideBarDiameter / 2f) * scale * 0.6f
                canvas.drawCircle(startX + cornerOffset, y, sideRadius.coerceAtLeast(5f), steelPaint)
                canvas.drawCircle(startX + drawWidth - cornerOffset, y, sideRadius.coerceAtLeast(5f), steelPaint)
            }
        }

        // Draw dimensions
        drawRectangularDimensions(canvas, startX, startY, drawWidth, drawHeight)

        // Draw reinforcement legend
        drawReinforcementLegend(canvas, startX, startY + drawHeight + 50)
    }

    private fun drawCircularColumn(
        canvas: Canvas,
        padding: Float,
        availableWidth: Float,
        availableHeight: Float
    ) {
        // Calculate scale
        val scale = min(availableWidth / columnDiameter, availableHeight / columnDiameter)

        val drawDiameter = columnDiameter * scale
        val radius = drawDiameter / 2
        val centerX = width / 2f
        val centerY = padding + availableHeight / 2

        // Draw concrete section
        canvas.drawCircle(centerX, centerY, radius, concretePaint)
        canvas.drawCircle(centerX, centerY, radius, concreteBorderPaint)

        // Draw center axes
        canvas.drawLine(centerX - radius - 20, centerY, centerX + radius + 20, centerY, axisPaint)
        canvas.drawLine(centerX, centerY - radius - 20, centerX, centerY + radius + 20, axisPaint)

        // Draw spiral or ties
        val coreRadius = radius - cover * scale
        if (isSpiral) {
            // Draw spiral representation
            val spiralPath = Path()
            val turns = 3
            val points = 100
            for (i in 0..points) {
                val angle = (i / points.toFloat()) * 2 * Math.PI * turns
                val r = 10f + (coreRadius - 10f) * (i / points.toFloat())
                val x = centerX + r * kotlin.math.cos(angle).toFloat()
                val y = centerY + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) spiralPath.moveTo(x, y) else spiralPath.lineTo(x, y)
            }
            canvas.drawPath(spiralPath, spiralPaint)
        } else {
            // Draw circular ties
            canvas.drawCircle(centerX, centerY, coreRadius, tiePaint)
        }

        // Draw longitudinal bars
        val barRadius = (cornerBarDiameter / 2f) * scale * 0.6f
        val barCircleRadius = coreRadius - barRadius - 5f
        val totalBars = cornerBars

        for (i in 0 until totalBars) {
            val angle = (2 * Math.PI * i / totalBars)
            val x = centerX + barCircleRadius * kotlin.math.cos(angle).toFloat()
            val y = centerY + barCircleRadius * kotlin.math.sin(angle).toFloat()
            canvas.drawCircle(x, y, barRadius.coerceAtLeast(6f), steelPaint)
        }

        // Draw dimensions
        drawCircularDimensions(canvas, centerX, centerY, radius, drawDiameter)

        // Draw reinforcement legend
        drawReinforcementLegend(canvas, centerX - radius, centerY + radius + 50)
    }

    private fun drawRectangularDimensions(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        drawWidth: Float,
        drawHeight: Float
    ) {
        val arrowSize = 10f
        val offset = 50f

        // Width dimension (bottom)
        val dimY = startY + drawHeight + offset
        canvas.drawLine(startX, dimY, startX + drawWidth, dimY, dimensionPaint)
        canvas.drawLine(startX, dimY - arrowSize, startX, dimY + arrowSize, dimensionPaint)
        canvas.drawLine(startX + drawWidth, dimY - arrowSize, startX + drawWidth, dimY + arrowSize, dimensionPaint)

        val widthLabel = "b = ${columnWidth.toInt()} mm"
        val widthTextWidth = dimensionTextPaint.measureText(widthLabel)
        canvas.drawText(widthLabel, startX + (drawWidth - widthTextWidth) / 2, dimY + 35, dimensionTextPaint)

        // Height dimension (right side)
        val dimX = startX + drawWidth + offset
        canvas.drawLine(dimX, startY, dimX, startY + drawHeight, dimensionPaint)
        canvas.drawLine(dimX - arrowSize, startY, dimX + arrowSize, startY, dimensionPaint)
        canvas.drawLine(dimX - arrowSize, startY + drawHeight, dimX + arrowSize, startY + drawHeight, dimensionPaint)

        val heightLabel = "h = ${columnHeight.toInt()} mm"
        canvas.save()
        canvas.rotate(90f, dimX + 35, startY + drawHeight / 2)
        canvas.drawText(heightLabel, dimX + 35, startY + drawHeight / 2 + 10, dimensionTextPaint)
        canvas.restore()

        // Cover label
        canvas.drawText("c = ${cover.toInt()} mm", startX + 10, startY - 15, labelPaint)
    }

    private fun drawCircularDimensions(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        diameter: Float
    ) {
        // Draw diameter line
        canvas.drawLine(centerX - radius, centerY + radius + 40, centerX + radius, centerY + radius + 40, dimensionPaint)
        canvas.drawLine(centerX - radius, centerY + radius + 35, centerX - radius, centerY + radius + 45, dimensionPaint)
        canvas.drawLine(centerX + radius, centerY + radius + 35, centerX + radius, centerY + radius + 45, dimensionPaint)

        val diameterLabel = "Ø = ${columnDiameter.toInt()} mm"
        val textWidth = dimensionTextPaint.measureText(diameterLabel)
        canvas.drawText(diameterLabel, centerX - textWidth / 2, centerY + radius + 75, dimensionTextPaint)

        // Cover label
        canvas.drawText("c = ${cover.toInt()} mm", centerX - radius + 10, centerY - radius - 15, labelPaint)
    }

    private fun drawReinforcementLegend(canvas: Canvas, startX: Float, startY: Float) {
        var yOffset = startY
        val xOffset = startX

        canvas.drawText("${cornerBars}Ø${cornerBarDiameter} (${context.getString(R.string.corner_bars)})", xOffset, yOffset, labelPaint)
        yOffset += 30

        if (sideBarsX > 0 || sideBarsY > 0) {
            val totalSideBars = (sideBarsX + sideBarsY) * 2
            canvas.drawText("${totalSideBars}Ø${sideBarDiameter} (${context.getString(R.string.side_bars)})", xOffset, yOffset, labelPaint)
            yOffset += 30
        }

        val tieType = if (isSpiral) context.getString(R.string.spiral) else context.getString(R.string.ties)
        canvas.drawText("Ø${tieDiameter} $tieType", xOffset, yOffset, labelPaint)
    }

    private fun drawResultsPanel(canvas: Canvas, startX: Float, startY: Float) {
        var yOffset = startY

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1565C0")
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(context.getString(R.string.calculation_results), startX, yOffset, titlePaint)
        yOffset += 35

        // Axial capacity
        val axialText = "${context.getString(R.string.axial_capacity)}: ${String.format("%.1f", axialCapacity)} kN"
        canvas.drawText(axialText, startX, yOffset, if (axialCapacity >= appliedAxial) resultPaint else warningPaint)
        yOffset += 30

        // Moment capacity X
        val mxText = "${context.getString(R.string.moment_capacity_x)}: ${String.format("%.1f", momentCapacityX)} kN.m"
        canvas.drawText(mxText, startX, yOffset, if (momentCapacityX >= appliedMomentX) resultPaint else warningPaint)
        yOffset += 30

        // Moment capacity Y
        val myText = "${context.getString(R.string.moment_capacity_y)}: ${String.format("%.1f", momentCapacityY)} kN.m"
        canvas.drawText(myText, startX + 300, yOffset - 30, if (momentCapacityY >= appliedMomentY) resultPaint else warningPaint)

        // Safety status
        yOffset += 35
        val statusPaint = if (isSafe) resultPaint else warningPaint
        val statusText = if (isSafe) context.getString(R.string.design_safe) else context.getString(R.string.design_unsafe)
        canvas.drawText(statusText, startX, yOffset, statusPaint)
    }

    fun updateFromCalculation(
        type: ColumnType,
        width: Float,
        height: Float,
        diameter: Float,
        coverValue: Float,
        corners: Int,
        cornerDia: Int,
        sideX: Int,
        sideY: Int,
        sideDia: Int,
        tieDia: Int,
        spiral: Boolean,
        axialCap: Double,
        mxCap: Double,
        myCap: Double,
        appAxial: Double,
        appMx: Double,
        appMy: Double,
        safe: Boolean
    ) {
        columnType = type
        columnWidth = width
        columnHeight = height
        columnDiameter = diameter
        cover = coverValue
        cornerBars = corners
        cornerBarDiameter = cornerDia
        sideBarsX = sideX
        sideBarsY = sideY
        sideBarDiameter = sideDia
        tieDiameter = tieDia
        isSpiral = spiral
        axialCapacity = axialCap
        momentCapacityX = mxCap
        momentCapacityY = myCap
        appliedAxial = appAxial
        appliedMomentX = appMx
        appliedMomentY = appMy
        isSafe = safe
        invalidate()
    }
}
