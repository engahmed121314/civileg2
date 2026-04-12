package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
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
        color = Color.parseColor("#B71C1C")
        style = Paint.Style.FILL
    }

    private val stirrupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
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
        color = Color.parseColor("#1565C0")
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
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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

        val padding = 100f
        val resultsHeight = 250f
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding - resultsHeight

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
            canvas.drawLine(startX - 20, naY, startX + drawWidth + 20, naY, neutralAxisPaint)
            canvas.drawText("N.A.", startX + drawWidth + 25, naY + 10, labelPaint)
        }

        // Draw dimensions
        if (showDimensions) {
            drawDimensions(canvas, startX, startY, drawWidth, drawHeight)
        }

        // Draw Header Safety Badge
        drawHeaderStatus(canvas)

        // Draw results panel
        drawResultsPanel(canvas, startX, startY + drawHeight + 60)
    }

    private fun drawStressZones(canvas: Canvas, startX: Float, startY: Float, drawWidth: Float, drawHeight: Float, scale: Float) {
        if (neutralAxisDepth > 0) {
            val naY = startY + (neutralAxisDepth.toFloat() * scale)
            // Compression zone (top)
            canvas.drawRect(startX, startY, startX + drawWidth, naY, compressionZonePaint)
            // Tension zone (bottom)
            canvas.drawRect(startX, naY, startX + drawWidth, startY + drawHeight, tensionZonePaint)
        }
    }

    private fun drawStirrups(canvas: Canvas, startX: Float, startY: Float, drawWidth: Float, drawHeight: Float, scale: Float) {
        val stirrupOffset = cover * scale
        val stirrupLeft = startX + stirrupOffset
        val stirrupTop = startY + stirrupOffset
        val stirrupRight = startX + drawWidth - stirrupOffset
        val stirrupBottom = startY + drawHeight - stirrupOffset
        canvas.drawRect(stirrupLeft, stirrupTop, stirrupRight, stirrupBottom, stirrupPaint)
    }

    private fun drawReinforcement(canvas: Canvas, startX: Float, startY: Float, drawWidth: Float, drawHeight: Float, scale: Float) {
        // Draw bottom bars (tension)
        val bottomBarY = startY + drawHeight - cover * scale - (bottomBars.firstOrNull()?.diameter?.toFloat() ?: 0f) * scale / 4
        drawBarGroup(canvas, startX, bottomBarY, drawWidth, bottomBars, scale)

        // Draw top bars (compression)
        val topBarY = startY + cover * scale + (topBars.firstOrNull()?.diameter?.toFloat() ?: 0f) * scale / 4
        drawBarGroup(canvas, startX, topBarY, drawWidth, topBars, scale)
    }

    private fun drawBarGroup(canvas: Canvas, startX: Float, y: Float, drawWidth: Float, bars: List<BarInfo>, scale: Float) {
        val stirrupOffset = cover * scale
        val usableWidth = drawWidth - 2 * stirrupOffset
        
        bars.forEach { barInfo ->
            val barRadius = (barInfo.diameter / 2f) * scale * 0.8f
            val spacing = if (barInfo.count > 1) (usableWidth - 2 * barRadius) / (barInfo.count - 1) else 0f
            
            for (i in 0 until barInfo.count) {
                val barX = if (barInfo.count == 1) startX + drawWidth / 2 
                          else startX + stirrupOffset + barRadius + i * spacing
                canvas.drawCircle(barX, y, barRadius.coerceAtLeast(6f), steelPaint)
            }
        }
    }

    private fun drawDimensions(canvas: Canvas, startX: Float, startY: Float, drawWidth: Float, drawHeight: Float) {
        val arrowSize = 10f
        val labelOffset = 50f

        // Width dimension
        val dimY = startY - labelOffset
        canvas.drawLine(startX, dimY, startX + drawWidth, dimY, dimensionPaint)
        canvas.drawLine(startX, dimY - arrowSize, startX, dimY + arrowSize, dimensionPaint)
        canvas.drawLine(startX + drawWidth, dimY - arrowSize, startX + drawWidth, dimY + arrowSize, dimensionPaint)
        val widthLabel = "b = ${beamWidth.toInt()} mm"
        canvas.drawText(widthLabel, startX + (drawWidth - dimensionTextPaint.measureText(widthLabel)) / 2, dimY - 15, dimensionTextPaint)

        // Height dimension
        val dimX = startX + drawWidth + labelOffset
        canvas.drawLine(dimX, startY, dimX, startY + drawHeight, dimensionPaint)
        canvas.drawLine(dimX - arrowSize, startY, dimX + arrowSize, startY, dimensionPaint)
        canvas.drawLine(dimX - arrowSize, startY + drawHeight, dimX + arrowSize, startY + drawHeight, dimensionPaint)
        val heightLabel = "h = ${beamHeight.toInt()} mm"
        canvas.save()
        canvas.rotate(90f, dimX + 40, startY + drawHeight / 2)
        canvas.drawText(heightLabel, dimX + 40, startY + drawHeight / 2, dimensionTextPaint)
        canvas.restore()
    }

    private fun drawHeaderStatus(canvas: Canvas) {
        val statusText = if (isSafe) "DESIGN SAFE ✓" else "DESIGN UNSAFE ✗"
        val statusColor = if (isSafe) Color.parseColor("#2E7D32") else Color.RED
        val badgePaint = Paint().apply { color = statusColor; alpha = 30 }
        val textStatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
            color = statusColor; textSize = 28f; typeface = Typeface.DEFAULT_BOLD 
        }
        
        val rect = RectF(40f, 40f, 40f + textStatusPaint.measureText(statusText) + 40f, 100f)
        canvas.drawRoundRect(rect, 8f, 8f, badgePaint)
        canvas.drawText(statusText, 60f, 85f, textStatusPaint)
    }

    private fun drawResultsPanel(canvas: Canvas, startX: Float, startY: Float) {
        var yOffset = startY
        val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f }

        canvas.drawText("Calculation Results:", startX, yOffset, labelPaint)
        yOffset += 40

        val momentText = "Moment Capacity (φMn): ${String.format("%.1f", momentCapacity)} kN.m"
        canvas.drawText(momentText, startX, yOffset, if (momentCapacity >= appliedMoment) resultPaint else warningPaint)
        yOffset += 35
        canvas.drawText("Applied Moment (Mu): ${String.format("%.1f", appliedMoment)} kN.m", startX, yOffset, textP)
        yOffset += 40

        val shearText = "Shear Capacity (φVn): ${String.format("%.1f", shearCapacity)} kN"
        canvas.drawText(shearText, startX, yOffset, if (shearCapacity >= appliedShear) resultPaint else warningPaint)
        yOffset += 35
        canvas.drawText("Applied Shear (Vu): ${String.format("%.1f", appliedShear)} kN", startX, yOffset, textP)
        
        if (steelRatio > 0) {
            yOffset += 40
            canvas.drawText("Steel Ratio (ρ): ${String.format("%.3f", steelRatio)}%", startX, yOffset, labelPaint)
        }
    }

    fun updateFromCalculation(
        width: Float, height: Float, coverValue: Float,
        topReinforcement: List<BarInfo>, bottomReinforcement: List<BarInfo>,
        momentCap: Double, shearCap: Double, appMoment: Double, appShear: Double,
        naDepth: Double, ratio: Double, safe: Boolean
    ) {
        this.beamWidth = width
        this.beamHeight = height
        this.cover = coverValue
        this.topBars = topReinforcement
        this.bottomBars = bottomReinforcement
        this.momentCapacity = momentCap
        this.shearCapacity = shearCap
        this.appliedMoment = appMoment
        this.appliedShear = appShear
        this.neutralAxisDepth = naDepth
        this.steelRatio = ratio
        this.isSafe = safe
        invalidate()
    }
}
