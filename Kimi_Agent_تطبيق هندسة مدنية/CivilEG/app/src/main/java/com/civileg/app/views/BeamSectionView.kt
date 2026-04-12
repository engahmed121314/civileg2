package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.civileg.app.R
import kotlin.math.min

/**
 * Interactive Beam Cross-Section View with detailed visualization
 * Shows concrete section, reinforcement bars, stirrups, and stress distribution
 */
class BeamSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Beam dimensions in mm
    var beamWidth: Float = 300f
    var beamHeight: Float = 600f
    var cover: Float = 40f
    var effectiveDepth: Float = 560f  // d = h - cover

    // Reinforcement
    var topBars: List<BarInfo> = listOf(BarInfo(2, 16))
    var bottomBars: List<BarInfo> = listOf(BarInfo(3, 25))
    var stirrupDiameter: Int = 10
    var stirrupSpacing: Float = 150f

    // Calculation results
    var momentCapacity: Double = 0.0
    var shearCapacity: Double = 0.0
    var appliedMoment: Double = 0.0
    var appliedShear: Double = 0.0
    var neutralAxisDepth: Double = 0.0
    var steelRatio: Double = 0.0
    var isSafe: Boolean = true

    // Display options
    var showStressDistribution: Boolean = true
    var showDimensions: Boolean = true
    var showReinforcementDetails: Boolean = true

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

    private val stirrupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E65100")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val compressionZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        alpha = 100
        style = Paint.Style.FILL
    }

    private val tensionZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336")
        alpha = 100
        style = Paint.Style.FILL
    }

    private val dimensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val dimensionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        textSize = 26f
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        textSize = 26f
    }

    private val neutralAxisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C27B0")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }

    data class BarInfo(val count: Int, val diameter: Int)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 80f
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding - 200f  // Reserve space for results

        // Calculate scale to fit the beam
        val scaleX = availableWidth / beamWidth
        val scaleY = availableHeight / beamHeight
        val scale = min(scaleX, scaleY)

        val drawWidth = beamWidth * scale
        val drawHeight = beamHeight * scale
        val startX = (width - drawWidth) / 2
        val startY = padding + (availableHeight - drawHeight) / 2

        // Draw stress distribution zones
        if (showStressDistribution && neutralAxisDepth > 0) {
            drawStressZones(canvas, startX, startY, drawWidth, drawHeight, scale)
        }

        // Draw concrete section
        canvas.drawRect(startX, startY, startX + drawWidth, startY + drawHeight, concretePaint)
        canvas.drawRect(startX, startY, startX + drawWidth, startY + drawHeight, concreteBorderPaint)

        // Draw stirrups
        drawStirrups(canvas, startX, startY, drawWidth, drawHeight, scale)

        // Draw reinforcement bars
        drawReinforcement(canvas, startX, startY, drawWidth, drawHeight, scale)

        // Draw neutral axis
        if (neutralAxisDepth > 0) {
            val naY = startY + (neutralAxisDepth.toFloat() * scale)
            canvas.drawLine(startX - 20, naY.toFloat(), startX + drawWidth + 20, naY.toFloat(), neutralAxisPaint)
            canvas.drawText("N.A.", startX + drawWidth + 25, naY.toFloat() + 10, labelPaint)
        }

        // Draw dimensions
        if (showDimensions) {
            drawDimensions(canvas, startX, startY, drawWidth, drawHeight)
        }

        // Draw results panel
        drawResultsPanel(canvas, startX, startY + drawHeight + 40)
    }

    private fun drawStressZones(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        drawWidth: Float,
        drawHeight: Float,
        scale: Float
    ) {
        if (neutralAxisDepth > 0) {
            val naY = startY + (neutralAxisDepth.toFloat() * scale)

            // Compression zone (top)
            canvas.drawRect(startX, startY, startX + drawWidth, naY.toFloat(), compressionZonePaint)

            // Tension zone (bottom)
            canvas.drawRect(startX, naY.toFloat(), startX + drawWidth, startY + drawHeight, tensionZonePaint)
        }
    }

    private fun drawStirrups(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        drawWidth: Float,
        drawHeight: Float,
        scale: Float
    ) {
        val stirrupOffset = cover * scale
        val stirrupLeft = startX + stirrupOffset
        val stirrupTop = startY + stirrupOffset
        val stirrupRight = startX + drawWidth - stirrupOffset
        val stirrupBottom = startY + drawHeight - stirrupOffset

        // Draw stirrup rectangle
        val stirrupRect = RectF(stirrupLeft, stirrupTop, stirrupRight, stirrupBottom)
        canvas.drawRect(stirrupRect, stirrupPaint)

        // Draw stirrup hooks
        val hookSize = 15f
        canvas.drawLine(stirrupLeft, stirrupTop, stirrupLeft - hookSize, stirrupTop - hookSize, stirrupPaint)
        canvas.drawLine(stirrupRight, stirrupTop, stirrupRight + hookSize, stirrupTop - hookSize, stirrupPaint)
        canvas.drawLine(stirrupLeft, stirrupBottom, stirrupLeft - hookSize, stirrupBottom + hookSize, stirrupPaint)
        canvas.drawLine(stirrupRight, stirrupBottom, stirrupRight + hookSize, stirrupBottom + hookSize, stirrupPaint)
    }

    private fun drawReinforcement(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        drawWidth: Float,
        drawHeight: Float,
        scale: Float
    ) {
        // Draw bottom bars (tension)
        val bottomBarY = startY + drawHeight - cover * scale
        drawBarGroup(canvas, startX, bottomBarY, drawWidth, bottomBars, scale, true)

        // Draw top bars (compression)
        val topBarY = startY + cover * scale
        drawBarGroup(canvas, startX, topBarY, drawWidth, topBars, scale, false)
    }

    private fun drawBarGroup(
        canvas: Canvas,
        startX: Float,
        y: Float,
        drawWidth: Float,
        bars: List<BarInfo>,
        scale: Float,
        isBottom: Boolean
    ) {
        val availableWidth = drawWidth - 2 * cover * scale
        var currentX = startX + cover * scale + 20f

        bars.forEach { barInfo ->
            val barRadius = (barInfo.diameter / 2f) * scale * 0.5f
            val barSpacing = availableWidth / (barInfo.count + 1)

            for (i in 1..barInfo.count) {
                val barX = startX + cover * scale + barSpacing * i
                canvas.drawCircle(barX, y, barRadius.coerceAtLeast(5f), steelPaint)

                // Draw bar diameter label
                if (showReinforcementDetails && barRadius > 8) {
                    val label = "Ø${barInfo.diameter}"
                    val textWidth = dimensionTextPaint.measureText(label)
                    canvas.drawText(label, barX - textWidth / 2, y + barRadius + 20, labelPaint)
                }
            }
        }
    }

    private fun drawDimensions(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        drawWidth: Float,
        drawHeight: Float
    ) {
        val arrowSize = 10f
        val labelOffset = 50f

        // Width dimension (bottom)
        val dimY = startY + drawHeight + labelOffset
        canvas.drawLine(startX, dimY, startX + drawWidth, dimY, dimensionPaint)
        canvas.drawLine(startX, dimY - arrowSize, startX, dimY + arrowSize, dimensionPaint)
        canvas.drawLine(startX + drawWidth, dimY - arrowSize, startX + drawWidth, dimY + arrowSize, dimensionPaint)

        val widthLabel = "${beamWidth.toInt()} mm"
        val widthTextWidth = dimensionTextPaint.measureText(widthLabel)
        canvas.drawText(widthLabel, startX + (drawWidth - widthTextWidth) / 2, dimY + 35, dimensionTextPaint)

        // Height dimension (right side)
        val dimX = startX + drawWidth + labelOffset
        canvas.drawLine(dimX, startY, dimX, startY + drawHeight, dimensionPaint)
        canvas.drawLine(dimX - arrowSize, startY, dimX + arrowSize, startY, dimensionPaint)
        canvas.drawLine(dimX - arrowSize, startY + drawHeight, dimX + arrowSize, startY + drawHeight, dimensionPaint)

        val heightLabel = "${beamHeight.toInt()} mm"
        canvas.save()
        canvas.rotate(90f, dimX + 35, startY + drawHeight / 2)
        canvas.drawText(heightLabel, dimX + 35, startY + drawHeight / 2 + 10, dimensionTextPaint)
        canvas.restore()

        // Cover dimension
        val coverLabel = "c = ${cover.toInt()} mm"
        canvas.drawText(coverLabel, startX + 10, startY - 15, labelPaint)

        // Effective depth
        if (effectiveDepth > 0) {
            val dLabel = "d = ${effectiveDepth.toInt()} mm"
            canvas.drawText(dLabel, startX + 10, startY + 50, labelPaint)
        }
    }

    private fun drawResultsPanel(canvas: Canvas, startX: Float, startY: Float) {
        var yOffset = startY

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1565C0")
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(context.getString(R.string.calculation_results), startX, yOffset, titlePaint)
        yOffset += 40

        // Moment capacity
        val momentText = "${context.getString(R.string.moment_capacity)}: ${String.format("%.2f", momentCapacity)} kN.m"
        canvas.drawText(momentText, startX, yOffset, if (momentCapacity >= appliedMoment) resultPaint else warningPaint)
        yOffset += 35

        // Applied moment
        canvas.drawText("${context.getString(R.string.applied_moment)}: ${String.format("%.2f", appliedMoment)} kN.m", startX, yOffset, dimensionTextPaint)
        yOffset += 35

        // Shear capacity
        val shearText = "${context.getString(R.string.shear_capacity)}: ${String.format("%.2f", shearCapacity)} kN"
        canvas.drawText(shearText, startX, yOffset, if (shearCapacity >= appliedShear) resultPaint else warningPaint)
        yOffset += 35

        // Neutral axis
        if (neutralAxisDepth > 0) {
            val naText = "${context.getString(R.string.neutral_axis_depth)}: ${String.format("%.1f", neutralAxisDepth)} mm"
            canvas.drawText(naText, startX, yOffset, labelPaint)
            yOffset += 35
        }

        // Steel ratio
        if (steelRatio > 0) {
            val ratioText = "${context.getString(R.string.steel_ratio)}: ${String.format("%.3f", steelRatio)}%"
            canvas.drawText(ratioText, startX, yOffset, labelPaint)
            yOffset += 35
        }

        // Safety status
        val statusPaint = if (isSafe) resultPaint else warningPaint
        val statusText = if (isSafe) context.getString(R.string.design_safe) else context.getString(R.string.design_unsafe)
        canvas.drawText(statusText, startX, yOffset, statusPaint)
    }

    fun updateFromCalculation(
        width: Float,
        height: Float,
        coverValue: Float,
        topReinforcement: List<BarInfo>,
        bottomReinforcement: List<BarInfo>,
        momentCap: Double,
        shearCap: Double,
        appMoment: Double,
        appShear: Double,
        naDepth: Double,
        ratio: Double,
        safe: Boolean
    ) {
        beamWidth = width
        beamHeight = height
        cover = coverValue
        effectiveDepth = height - coverValue - stirrupDiameter - (bottomReinforcement.firstOrNull()?.diameter?.toFloat() ?: 0f) / 2
        topBars = topReinforcement
        bottomBars = bottomReinforcement
        momentCapacity = momentCap
        shearCapacity = shearCap
        appliedMoment = appMoment
        appliedShear = appShear
        neutralAxisDepth = naDepth
        steelRatio = ratio
        isSafe = safe
        invalidate()
    }
}
