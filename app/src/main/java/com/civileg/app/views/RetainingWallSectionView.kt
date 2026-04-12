package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.utils.CalculatorEngine
import kotlin.math.min

/**
 * Professional Retaining Wall Section View
 * Features: Engineering hatching, reinforcement detailing, soil visualization, and dimensioning.
 */
class RetainingWallSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var h: Float = 4000f // mm
    var base: Float = 2500f // mm
    var stemT: Float = 300f // mm
    var baseT: Float = 400f // mm
    var toe: Float = 600f // mm
    var isSafe: Boolean = true
    var rebarDetails: String = ""

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECEFF1")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#37474F")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val rebarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    private val soilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E63")
        alpha = 100
        style = Paint.Style.FILL
    }

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFD8DC")
        strokeWidth = 1f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 120f
        val scale = min((width - 2 * padding) / base, (height - 2 * padding) / h) * 0.85f

        val drawH = h * scale
        val drawB = base * scale
        val drawStem = stemT * scale
        val drawBaseT = baseT * scale
        val drawToe = toe * scale

        val startX = (width - drawB) / 2
        val startY = (height - drawH) / 2

        // 1. Draw Soil
        canvas.drawRect(startX + drawB - drawStem, startY + 50f, width.toFloat(), startY + drawH, soilPaint)

        // 2. Draw Wall Path
        val wallPath = Path().apply {
            moveTo(startX + drawB - drawStem, startY)
            lineTo(startX + drawB, startY)
            lineTo(startX + drawB, startY + drawH)
            lineTo(startX, startY + drawH)
            lineTo(startX, startY + drawH - drawBaseT)
            lineTo(startX + drawB - drawStem, startY + drawH - drawBaseT)
            close()
        }

        canvas.drawPath(wallPath, concretePaint)
        drawConcreteHatching(canvas, wallPath)
        canvas.drawPath(wallPath, borderPaint)

        // 3. Draw Reinforcement (Professional Detailing)
        val cover = 40f * scale
        // Main Vertical Stem Rebar (Back face)
        canvas.drawLine(startX + drawB - cover, startY + cover, startX + drawB - cover, startY + drawH - cover, rebarPaint)
        // Main Horizontal Base Rebar
        canvas.drawLine(startX + cover, startY + drawH - cover, startX + drawB - cover, startY + drawH - cover, rebarPaint)
        
        // Distribution Bars (Dots)
        val dotRadius = 4f
        var dy = startY + cover + 40f
        while (dy < startY + drawH - drawBaseT - cover) {
            canvas.drawCircle(startX + drawB - cover - 10f, dy, dotRadius, dotRebarPaint)
            dy += 100f * scale
        }

        // 4. Dimension Lines
        drawDimension(canvas, startX + drawB, startY, startX + drawB, startY + drawH, "H = ${h.toInt()}mm", false)
        drawDimension(canvas, startX, startY + drawH + 40f, startX + drawB, startY + drawH + 40f, "B = ${base.toInt()}mm", true)
        drawDimension(canvas, startX + drawB - drawStem, startY - 40f, startX + drawB, startY - 40f, "t = ${stemT.toInt()}mm", true)

        // 5. Header & Safety
        drawStatusBadge(canvas)
    }

    private fun drawConcreteHatching(canvas: Canvas, path: Path) {
        canvas.save()
        canvas.clipPath(path)
        val step = 30f
        val bounds = RectF()
        path.computeBounds(bounds, true)
        
        var i = bounds.left - bounds.height()
        while (i < bounds.right) {
            canvas.drawLine(i, bounds.top, i + bounds.height(), bounds.bottom, hatchPaint)
            i += step
        }
        
        // Add some random "triangles" for concrete look
        val random = java.util.Random(42)
        for (j in 0..15) {
            val rx = bounds.left + random.nextFloat() * bounds.width()
            val ry = bounds.top + random.nextFloat() * bounds.height()
            if (isPointInPath(path, rx, ry)) {
                canvas.drawPoint(rx, ry, borderPaint)
            }
        }
        canvas.restore()
    }

    private fun isPointInPath(path: Path, x: Float, y: Float): Boolean {
        val rect = RectF()
        path.computeBounds(rect, true)
        val region = Region()
        region.setPath(path, Region(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt()))
        return region.contains(x.toInt(), y.toInt())
    }

    private fun drawDimension(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, text: String, horizontal: Boolean) {
        canvas.drawLine(x1, y1, x2, y2, dimensionPaint)
        val tick = 15f
        if (horizontal) {
            canvas.drawLine(x1 - tick, y1 + tick, x1 + tick, y1 - tick, dimensionPaint)
            canvas.drawLine(x2 - tick, y2 + tick, x2 + tick, y2 - tick, dimensionPaint)
            canvas.drawText(text, (x1 + x2) / 2 - dimensionPaint.measureText(text) / 2, y1 - 10f, dimensionPaint)
        } else {
            canvas.drawLine(x1 - tick, y1 - tick, x1 + tick, y1 + tick, dimensionPaint)
            canvas.drawLine(x2 - tick, y2 - tick, x2 + tick, y2 + tick, dimensionPaint)
            canvas.save()
            canvas.rotate(-90f, x1 + 30f, (y1 + y2) / 2)
            canvas.drawText(text, x1 + 30f, (y1 + y2) / 2, dimensionPaint)
            canvas.restore()
        }
    }

    private fun drawStatusBadge(canvas: Canvas) {
        val statusText = if (isSafe) "STRUCTURAL SAFE ✓" else "UNSAFE DESIGN ✗"
        val statusColor = if (isSafe) Color.parseColor("#2E7D32") else Color.RED
        val badgePaint = Paint().apply { color = statusColor; alpha = 30 }
        val textStatusPaint = Paint(textPaint).apply { color = statusColor; textSize = 28f }
        
        val rect = RectF(40f, 40f, 40f + textStatusPaint.measureText(statusText) + 40f, 100f)
        canvas.drawRoundRect(rect, 10f, 10f, badgePaint)
        canvas.drawText(statusText, 60f, 85f, textStatusPaint)
    }

    fun update(h: Float, base: Float, stemT: Float, baseT: Float, safe: Boolean = true) {
        this.h = h
        this.base = base
        this.stemT = stemT
        this.baseT = baseT
        this.isSafe = safe
        invalidate()
    }
}
