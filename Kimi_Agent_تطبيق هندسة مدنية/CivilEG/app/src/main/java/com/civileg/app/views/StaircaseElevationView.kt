package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Interactive Staircase Elevation View
 * Shows longitudinal section with steps, waist slab, and landing
 */
class StaircaseElevationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Staircase dimensions in mm
    var totalRise: Float = 3000f
    var totalRun: Float = 4000f
    var riserHeight: Float = 150f
    var treadDepth: Float = 250f
    var waistThickness: Float = 150f
    var landingLength: Float = 1200f
    var landingThickness: Float = 180f
    var stairWidth: Float = 1200f

    // Calculation results
    var momentCapacity: Double = 0.0
    var shearCapacity: Double = 0.0
    var appliedMoment: Double = 0.0
    var appliedShear: Double = 0.0
    var isSafe: Boolean = true
    var numberOfSteps: Int = 0

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

    private val landingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        style = Paint.Style.FILL
    }

    private val stepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.FILL
    }

    private val stepBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val steelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.STROKE
        strokeWidth = 3f
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
        textSize = 26f
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

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E63")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 50f
        val resultsHeight = 140f
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding - resultsHeight

        // Calculate scale
        val totalHeight = totalRise + landingThickness
        val scaleX = availableWidth / totalRun
        val scaleY = availableHeight / totalHeight
        val scale = min(scaleX, scaleY)

        val drawRun = totalRun * scale
        val drawRise = totalRise * scale
        val startX = (width - drawRun) / 2
        val startY = padding + availableHeight - drawRise

        // Draw ground line
        canvas.drawRect(startX, startY + drawRise, startX + drawRun, startY + drawRise + 30, groundPaint)

        // Draw lower landing
        val drawLandingLength = landingLength * scale
        val drawLandingThickness = landingThickness * scale
        val drawWaistThickness = waistThickness * scale

        canvas.drawRect(startX, startY + drawRise - drawLandingThickness, startX + drawLandingLength, startY + drawRise, landingPaint)
        canvas.drawRect(startX, startY + drawRise - drawLandingThickness, startX + drawLandingLength, startY + drawRise, concreteBorderPaint)
        drawHatching(canvas, startX, startY + drawRise - drawLandingThickness, drawLandingLength, drawLandingThickness)

        // Draw upper landing
        canvas.drawRect(startX + drawRun - drawLandingLength, startY - drawLandingThickness, startX + drawRun, startY, landingPaint)
        canvas.drawRect(startX + drawRun - drawLandingLength, startY - drawLandingThickness, startX + drawRun, startY, concreteBorderPaint)
        drawHatching(canvas, startX + drawRun - drawLandingLength, startY - drawLandingThickness, drawLandingLength, drawLandingThickness)

        // Draw stair flight (waist slab)
        val flightStartX = startX + drawLandingLength
        val flightEndX = startX + drawRun - drawLandingLength
        val flightLength = flightEndX - flightStartX

        // Draw inclined waist slab
        val waistPath = Path()
        waistPath.moveTo(flightStartX, startY + drawRise - drawLandingThickness)
        waistPath.lineTo(flightEndX, startY - drawLandingThickness)
        waistPath.lineTo(flightEndX, startY - drawLandingThickness - drawWaistThickness)
        waistPath.lineTo(flightStartX, startY + drawRise - drawLandingThickness - drawWaistThickness)
        waistPath.close()
        canvas.drawPath(waistPath, concretePaint)
        canvas.drawPath(waistPath, concreteBorderPaint)

        // Draw hatching for waist slab
        drawSlabHatching(canvas, flightStartX, startY + drawRise - drawLandingThickness - drawWaistThickness,
            flightEndX - flightStartX, drawWaistThickness, flightLength, drawRise)

        // Draw steps
        drawSteps(canvas, flightStartX, startY + drawRise - drawLandingThickness, flightEndX, startY - drawLandingThickness, scale)

        // Draw reinforcement
        drawReinforcement(canvas, startX, startY, drawRun, drawRise, drawLandingLength, drawLandingThickness, drawWaistThickness, scale)

        // Draw dimensions
        drawDimensions(canvas, startX, startY, drawRun, drawRise, drawLandingLength, drawLandingThickness, drawWaistThickness)

        // Draw results
        drawResultsPanel(canvas, padding, height - resultsHeight + 15)
    }

    private fun drawSteps(
        canvas: Canvas,
        flightStartX: Float, flightStartY: Float,
        flightEndX: Float, flightEndY: Float,
        scale: Float
    ) {
        val drawRiserHeight = riserHeight * scale
        val drawTreadDepth = treadDepth * scale
        val numSteps = ((flightEndY - flightStartY) / drawRiserHeight).toInt()

        numberOfSteps = numSteps

        for (i in 0 until numSteps) {
            val stepY = flightStartY - i * drawRiserHeight
            val stepX = flightStartX + i * drawTreadDepth * (flightEndX - flightStartX) / (numSteps * drawTreadDepth)
            val nextStepX = flightStartX + (i + 1) * drawTreadDepth * (flightEndX - flightStartX) / (numSteps * drawTreadDepth)

            if (stepY > flightEndY && stepX < flightEndX) {
                // Draw step (riser + tread)
                val stepPath = Path()
                stepPath.moveTo(stepX, stepY)
                stepPath.lineTo(nextStepX.coerceAtMost(flightEndX), stepY)
                stepPath.lineTo(nextStepX.coerceAtMost(flightEndX), stepY - drawRiserHeight)
                stepPath.lineTo(stepX, stepY - drawRiserHeight)
                stepPath.close()
                canvas.drawPath(stepPath, stepPaint)
                canvas.drawPath(stepPath, stepBorderPaint)
            }
        }
    }

    private fun drawReinforcement(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawRun: Float, drawRise: Float,
        drawLandingLength: Float, drawLandingThickness: Float,
        drawWaistThickness: Float,
        scale: Float
    ) {
        val cover = 20f * scale
        val barRadius = 5f

        // Main reinforcement in landing (bottom)
        var x = startX + cover
        while (x < startX + drawLandingLength - cover) {
            canvas.drawCircle(x, startY + drawRise - cover - barRadius, barRadius, steelDotPaint)
            x += 100 * scale
        }

        // Main reinforcement in waist slab (bottom, following slope)
        val flightStartX = startX + drawLandingLength
        val flightEndX = startX + drawRun - drawLandingLength
        val flightLength = flightEndX - flightStartX
        val flightRise = drawRise - drawLandingThickness

        x = flightStartX + cover
        while (x < flightEndX - cover) {
            val ratio = (x - flightStartX) / flightLength
            val y = startY + drawRise - drawLandingThickness - ratio * flightRise - cover - barRadius
            canvas.drawCircle(x, y, barRadius, steelDotPaint)
            x += 100 * scale
        }

        // Main reinforcement in upper landing (bottom)
        x = startX + drawRun - drawLandingLength + cover
        while (x < startX + drawRun - cover) {
            canvas.drawCircle(x, startY - drawLandingThickness + cover + barRadius, barRadius, steelDotPaint)
            x += 100 * scale
        }

        // Distribution reinforcement (top)
        val midX = startX + drawRun / 2
        canvas.drawCircle(midX, startY + drawRise - drawLandingThickness - drawWaistThickness + cover + barRadius, barRadius, steelDotPaint)
        canvas.drawCircle(midX + 80 * scale, startY + drawRise - drawLandingThickness - drawWaistThickness + cover + barRadius, barRadius, steelDotPaint)
    }

    private fun drawHatching(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val spacing = 12f
        val path = Path()
        var i = 0f
        while (i < w + h) {
            path.moveTo(x + i, y + h)
            path.lineTo(x + i - h, y)
            i += spacing
        }
        canvas.drawPath(path, hatchPaint)
    }

    private fun drawSlabHatching(
        canvas: Canvas,
        x: Float, y: Float,
        w: Float, h: Float,
        flightLength: Float, flightRise: Float
    ) {
        val spacing = 12f
        val angle = kotlin.math.atan2(flightRise.toDouble(), flightLength.toDouble()).toFloat()

        val path = Path()
        var i = 0f
        while (i < w + h) {
            val startXX = x + i * kotlin.math.cos(angle)
            val startYY = y + h + i * kotlin.math.sin(angle)
            path.moveTo(startXX, startYY)
            path.lineTo(startXX - h * kotlin.math.sin(angle), startYY + h * kotlin.math.cos(angle))
            i += spacing
        }
        canvas.drawPath(path, hatchPaint)
    }

    private fun drawDimensions(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawRun: Float, drawRise: Float,
        drawLandingLength: Float, drawLandingThickness: Float,
        drawWaistThickness: Float
    ) {
        val arrowSize = 8f

        // Total run dimension (bottom)
        val dimY1 = startY + drawRise + 50
        canvas.drawLine(startX, dimY1, startX + drawRun, dimY1, dimensionPaint)
        canvas.drawLine(startX, dimY1 - arrowSize, startX, dimY1 + arrowSize, dimensionPaint)
        canvas.drawLine(startX + drawRun, dimY1 - arrowSize, startX + drawRun, dimY1 + arrowSize, dimensionPaint)

        val runLabel = "${context.getString(R.string.total_run)}: ${totalRun.toInt()} mm"
        val runWidth = dimensionTextPaint.measureText(runLabel)
        canvas.drawText(runLabel, startX + (drawRun - runWidth) / 2, dimY1 + 30, dimensionTextPaint)

        // Total rise dimension (right side)
        val dimX1 = startX + drawRun + 35
        canvas.drawLine(dimX1, startY, dimX1, startY + drawRise, dimensionPaint)
        canvas.save()
        canvas.rotate(90f, dimX1 + 30, startY + drawRise / 2)
        canvas.drawText("${context.getString(R.string.total_rise)}: ${totalRise.toInt()} mm", dimX1 + 30, startY + drawRise / 2 + 10, dimensionTextPaint)
        canvas.restore()

        // Step dimensions
        if (numberOfSteps > 0) {
            val stepLabel = "$numberOfSteps ${context.getString(R.string.steps)} (${riserHeight.toInt()}/${treadDepth.toInt()})"
            canvas.drawText(stepLabel, startX + drawLandingLength + 20, startY + drawRise / 2, labelPaint)
        }

        // Waist thickness
        val flightStartX = startX + drawLandingLength
        val flightMidX = flightStartX + (drawRun - 2 * drawLandingLength) / 2
        canvas.drawText("t = ${waistThickness.toInt()} mm", flightMidX, startY + drawRise / 2 - 30, labelPaint)

        // Landing thickness
        canvas.drawText("tₗ = ${landingThickness.toInt()} mm", startX + 10, startY + drawRise - drawLandingThickness - 10, labelPaint)
    }

    private fun drawResultsPanel(canvas: Canvas, startX: Float, startY: Float) {
        var yOffset = startY

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1565C0")
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(context.getString(R.string.calculation_results), startX, yOffset, titlePaint)
        yOffset += 28

        val momentText = "${context.getString(R.string.moment_capacity)}: ${String.format("%.1f", momentCapacity)} kN.m"
        canvas.drawText(momentText, startX, yOffset, if (momentCapacity >= appliedMoment) resultPaint else warningPaint)
        yOffset += 26

        val shearText = "${context.getString(R.string.shear_capacity)}: ${String.format("%.1f", shearCapacity)} kN"
        canvas.drawText(shearText, startX + 280, yOffset - 26, if (shearCapacity >= appliedShear) resultPaint else warningPaint)

        yOffset += 28
        val statusPaint = if (isSafe) resultPaint else warningPaint
        val statusText = if (isSafe) context.getString(R.string.design_safe) else context.getString(R.string.design_unsafe)
        canvas.drawText(statusText, startX, yOffset, statusPaint)
    }

    fun updateFromCalculation(
        rise: Float,
        run: Float,
        riser: Float,
        tread: Float,
        waist: Float,
        landingLen: Float,
        landingThick: Float,
        width: Float,
        momentCap: Double,
        shearCap: Double,
        appMoment: Double,
        appShear: Double,
        safe: Boolean
    ) {
        totalRise = rise
        totalRun = run
        riserHeight = riser
        treadDepth = tread
        waistThickness = waist
        landingLength = landingLen
        landingThickness = landingThick
        stairWidth = width
        momentCapacity = momentCap
        shearCapacity = shearCap
        appliedMoment = appMoment
        appliedShear = appShear
        isSafe = safe
        numberOfSteps = (rise / riser).toInt()
        invalidate()
    }
}
