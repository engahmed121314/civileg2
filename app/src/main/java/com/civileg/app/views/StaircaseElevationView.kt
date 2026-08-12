package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.utils.CalculatorEngine
import kotlin.math.*

/**
 * Professional Staircase Elevation View
 * Features: Engineering hatching, dimensioning, reinforcement detailing, and cost summary.
 */
class StaircaseElevationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var riser: Float = 150f
    var tread: Float = 300f
    var numSteps: Int = 10
    var waistT: Float = 150f
    var isSafe: Boolean = true
    
    var mainSteel: String = "5Ø12/m"
    var distSteel: String = "5Ø10/m"
    var totalCost: Double = 0.0
    var steelWeight: Double = 0.0

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val mainSteelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val dotRebarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        style = Paint.Style.FILL
    }

    private val dimensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        strokeWidth = 2f
        textSize = 24f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 120f
        val footerHeight = 160f
        
        val totalGoing = tread * numSteps
        val totalRise = riser * numSteps
        
        val scale = min((width - 2 * padding) / totalGoing, (height - 2 * padding - footerHeight) / totalRise) * 0.85f

        val startX = (width - totalGoing * scale) / 2
        val startY = height - footerHeight - padding - (height - footerHeight - 2 * padding - totalRise * scale) / 2

        // 1. Draw Concrete Profile
        val path = Path()
        path.moveTo(startX, startY)
        
        var currentX = startX
        var currentY = startY
        
        for (i in 0 until numSteps) {
            path.lineTo(currentX, currentY - riser * scale)
            currentY -= riser * scale
            path.lineTo(currentX + tread * scale, currentY)
            currentX += tread * scale
        }
        
        val angle = atan(riser / tread)
        val waistOffset = waistT * scale / cos(angle)
        
        path.lineTo(currentX, currentY + waistOffset)
        path.lineTo(startX, startY + waistOffset)
        path.close()

        canvas.drawPath(path, concretePaint)
        drawConcreteHatching(canvas, path)
        canvas.drawPath(path, borderPaint)

        // 2. Reinforcement
        val cover = 25f * scale
        val rebarPath = Path()
        // Main bar along waist bottom
        val rStartX = startX + cover * cos(PI/2 - angle).toFloat()
        val rStartY = startY + waistOffset - cover / cos(angle).toFloat()
        val rEndX = currentX - cover * cos(PI/2 - angle).toFloat()
        val rEndY = currentY + waistOffset - cover / cos(angle).toFloat()
        
        rebarPath.moveTo(rStartX, rStartY)
        rebarPath.lineTo(rEndX, rEndY)
        canvas.drawPath(rebarPath, mainSteelPaint)

        // Distribution dots
        val numDots = 8
        for (i in 0 until numDots) {
            val f = i.toFloat() / (numDots - 1)
            val dx = rStartX + f * (rEndX - rStartX)
            val dy = rStartY + f * (rEndY - rStartY)
            canvas.drawCircle(dx, dy - 10f, 5f, dotRebarPaint)
        }

        // 3. Dimensions
        drawDimension(canvas, startX, startY + waistOffset + 40f, currentX, startY + waistOffset + 40f, "G = ${totalGoing.toInt()} mm", true)
        drawDimension(canvas, currentX + 40f, currentY, currentX + 40f, startY + waistOffset, "H = ${(totalRise + waistT).toInt()} mm", false)

        // 4. Header & Footer
        drawHeader(canvas)
        drawFooter(canvas, footerHeight)
    }

    private fun drawConcreteHatching(canvas: Canvas, path: Path) {
        canvas.save()
        canvas.clipPath(path)
        val step = 35f
        val bounds = RectF()
        path.computeBounds(bounds, true)
        var i = bounds.left - bounds.height()
        while (i < bounds.right) {
            canvas.drawLine(i, bounds.top, i + bounds.height(), bounds.bottom, hatchPaint)
            i += step
        }
        canvas.restore()
    }

    private fun drawDimension(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, text: String, horizontal: Boolean) {
        canvas.drawLine(x1, y1, x2, y2, dimensionPaint)
        val tick = 12f
        if (horizontal) {
            canvas.drawLine(x1, y1 - tick, x1, y1 + tick, dimensionPaint)
            canvas.drawLine(x2, y2 - tick, x2, y2 + tick, dimensionPaint)
            canvas.drawText(text, (x1 + x2) / 2 - dimensionPaint.measureText(text) / 2, y1 - 10f, dimensionPaint)
        } else {
            canvas.drawLine(x1 - tick, y1, x1 + tick, y1, dimensionPaint)
            canvas.drawLine(x2 - tick, y2, x2 + tick, y2, dimensionPaint)
            canvas.save()
            canvas.rotate(-90f, x1 + 30f, (y1 + y2) / 2)
            canvas.drawText(text, x1 + 30f, (y1 + y2) / 2, dimensionPaint)
            canvas.restore()
        }
    }

    private fun drawHeader(canvas: Canvas) {
        val statusText = if (isSafe) "DESIGN SAFE ✓" else "UNSAFE DESIGN ✗"
        val statusColor = if (isSafe) Color.parseColor("#2E7D32") else Color.RED
        val badgePaint = Paint().apply { color = statusColor; alpha = 30 }
        val textStatusPaint = Paint(textPaint).apply { color = statusColor; textSize = 28f }
        
        val rect = RectF(40f, 40f, 40f + textStatusPaint.measureText(statusText) + 40f, 100f)
        canvas.drawRoundRect(rect, 8f, 8f, badgePaint)
        canvas.drawText(statusText, 60f, 85f, textStatusPaint)
    }

    private fun drawFooter(canvas: Canvas, footerH: Float) {
        val resStartY = height - footerH + 40f
        canvas.drawText("Main: $mainSteel | Dist: $distSteel", 40f, resStartY, textPaint.apply { textSize = 22f; color = Color.DKGRAY })
        canvas.drawText("Waist: ${waistT.toInt()}mm | R: ${riser.toInt()} | T: ${tread.toInt()}", 40f, resStartY + 40f, textPaint)
        canvas.drawText(String.format(java.util.Locale.getDefault(), "Est. Cost: %.0f EGP", totalCost), 40f, resStartY + 85f, textPaint.apply { color = Color.parseColor("#37474F") })
    }

    fun update(riser: Float, tread: Float, steps: Int, waist: Float, cost: Double, weight: Double, main: String, dist: String, safe: Boolean = true) {
        this.riser = riser
        this.tread = tread
        this.numSteps = steps
        this.waistT = waist
        this.totalCost = cost
        this.steelWeight = weight
        this.mainSteel = main
        this.distSteel = dist
        this.isSafe = safe
        invalidate()
    }
}
