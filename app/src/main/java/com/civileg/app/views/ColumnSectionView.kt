package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.R
import java.util.Locale
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
        color = Color.parseColor("#B71C1C")
        style = Paint.Style.FILL
    }

    private val tiePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val spiralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E65100")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val dimensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
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
        typeface = Typeface.DEFAULT_BOLD
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
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
        val resultsHeight = 220f
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding - resultsHeight

        when (columnType) {
            ColumnType.RECTANGULAR -> drawRectangularColumn(canvas, padding, availableWidth, availableHeight)
            ColumnType.CIRCULAR -> drawCircularColumn(canvas, padding, availableWidth, availableHeight)
        }

        // Draw Header Safety Badge
        drawHeaderStatus(canvas)

        // Draw results panel
        drawResultsPanel(canvas, padding, height - resultsHeight + 40f)
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
        val barRadius = (cornerBarDiameter / 2f) * scale * 0.8f
        val offset = cover * scale + tieDiameter * scale / 2 + barRadius

        val corners = listOf(
            Pair(startX + offset, startY + offset),
            Pair(startX + drawWidth - offset, startY + offset),
            Pair(startX + offset, startY + drawHeight - offset),
            Pair(startX + drawWidth - offset, startY + drawHeight - offset)
        )

        corners.forEach { (x, y) ->
            canvas.drawCircle(x, y, barRadius.coerceAtLeast(6f), steelPaint)
        }

        // Draw side bars
        if (sideBarsX > 0) {
            val usableW = drawWidth - 2 * offset
            val step = usableW / (sideBarsX + 1)
            for (i in 1..sideBarsX) {
                val x = startX + offset + i * step
                canvas.drawCircle(x, startY + offset, barRadius * 0.9f, steelPaint)
                canvas.drawCircle(x, startY + drawHeight - offset, barRadius * 0.9f, steelPaint)
            }
        }
        if (sideBarsY > 0) {
            val usableH = drawHeight - 2 * offset
            val step = usableH / (sideBarsY + 1)
            for (i in 1..sideBarsY) {
                val y = startY + offset + i * step
                canvas.drawCircle(startX + offset, y, barRadius * 0.9f, steelPaint)
                canvas.drawCircle(startX + drawWidth - offset, y, barRadius * 0.9f, steelPaint)
            }
        }

        // Dimensions
        drawRectangularDimensions(canvas, startX, startY, drawWidth, drawHeight)
    }

    private fun drawCircularColumn(canvas: Canvas, padding: Float, availableWidth: Float, availableHeight: Float) {
        val scale = min(availableWidth / columnDiameter, availableHeight / columnDiameter)
        val radius = (columnDiameter * scale) / 2
        val centerX = width / 2f
        val centerY = padding + availableHeight / 2

        canvas.drawCircle(centerX, centerY, radius, concretePaint)
        canvas.drawCircle(centerX, centerY, radius, concreteBorderPaint)

        val coreRadius = radius - cover * scale
        if (isSpiral) {
            canvas.drawCircle(centerX, centerY, coreRadius, spiralPaint)
        } else {
            canvas.drawCircle(centerX, centerY, coreRadius, tiePaint)
        }

        val totalBars = cornerBars + (sideBarsX + sideBarsY) * 2
        val barRadius = (cornerBarDiameter / 2f) * scale * 0.8f
        val barOrbit = coreRadius - barRadius
        
        for (i in 0 until totalBars) {
            val angle = (2 * Math.PI * i / totalBars)
            val bx = centerX + barOrbit * kotlin.math.cos(angle).toFloat()
            val by = centerY + barOrbit * kotlin.math.sin(angle).toFloat()
            canvas.drawCircle(bx, by, barRadius.coerceAtLeast(6f), steelPaint)
        }

        // Dimensions
        canvas.drawLine(centerX - radius, centerY + radius + 40, centerX + radius, centerY + radius + 40, dimensionPaint)
        val dimText = "Ø = ${columnDiameter.toInt()} mm"
        canvas.drawText(dimText, centerX - dimensionTextPaint.measureText(dimText)/2, centerY + radius + 75, dimensionTextPaint)
    }

    private fun drawRectangularDimensions(canvas: Canvas, startX: Float, startY: Float, drawWidth: Float, drawHeight: Float) {
        val offset = 50f
        // Width
        canvas.drawLine(startX, startY - offset, startX + drawWidth, startY - offset, dimensionPaint)
        val wText = "b = ${columnWidth.toInt()} mm"
        canvas.drawText(wText, startX + (drawWidth - dimensionTextPaint.measureText(wText))/2, startY - offset - 10, dimensionTextPaint)
        // Height
        val dimX = startX + drawWidth + offset
        canvas.drawLine(dimX, startY, dimX, startY + drawHeight, dimensionPaint)
        canvas.save()
        canvas.rotate(90f, dimX + 40, startY + drawHeight/2)
        canvas.drawText("h = ${columnHeight.toInt()} mm", dimX + 40, startY + drawHeight/2, dimensionTextPaint)
        canvas.restore()
    }

    private fun drawHeaderStatus(canvas: Canvas) {
        val statusText = if (isSafe) "COLUMN SAFE ✓" else "COLUMN UNSAFE ✗"
        val statusColor = if (isSafe) Color.parseColor("#2E7D32") else Color.RED
        val badgePaint = Paint().apply { color = statusColor; alpha = 30 }
        val textStatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusColor; textSize = 28f; typeface = Typeface.DEFAULT_BOLD }
        
        val rect = RectF(40f, 40f, 40f + textStatusPaint.measureText(statusText) + 40f, 100f)
        canvas.drawRoundRect(rect, 8f, 8f, badgePaint)
        canvas.drawText(statusText, 60f, 85f, textStatusPaint)
    }

    private fun drawResultsPanel(canvas: Canvas, startX: Float, startY: Float) {
        var yOffset = startY
        val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 22f }

        canvas.drawText("Calculation Results:", startX, yOffset, labelPaint)
        yOffset += 40

        val axialText = String.format(Locale.getDefault(), "Axial Capacity (φPn): %.1f kN (Applied: %.1f)", axialCapacity, appliedAxial)
        canvas.drawText(axialText, startX, yOffset, if (axialCapacity >= appliedAxial) resultPaint else warningPaint)
        yOffset += 40

        val mxText = String.format(Locale.getDefault(), "Mx Capacity: %.1f kNm (Applied: %.1f)", momentCapacityX, appliedMomentX)
        canvas.drawText(mxText, startX, yOffset, if (momentCapacityX >= appliedMomentX) resultPaint else warningPaint)
        yOffset += 40

        val myText = String.format(Locale.getDefault(), "My Capacity: %.1f kNm (Applied: %.1f)", momentCapacityY, appliedMomentY)
        canvas.drawText(myText, startX, yOffset, if (momentCapacityY >= appliedMomentY) resultPaint else warningPaint)
    }

    fun updateFromCalculation(
        type: ColumnType, width: Float, height: Float, diameter: Float, coverValue: Float,
        corners: Int, cornerDia: Int, sideX: Int, sideY: Int, sideDia: Int, tieDia: Int, spiral: Boolean,
        axialCap: Double, mxCap: Double, myCap: Double, appAxial: Double, appMx: Double, appMy: Double, safe: Boolean
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
