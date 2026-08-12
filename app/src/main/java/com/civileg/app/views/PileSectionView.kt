package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.utils.CalculatorEngine
import kotlin.math.min

/**
 * Professional Pile Section View
 * Features: Deep foundation visualization, spiral reinforcement, hatching, and dimensioning.
 */
class PileSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var dia: Float = 600f // mm
    var length: Float = 15000f // mm
    var isSafe: Boolean = true
    var steel: CalculatorEngine.ReinforcementBar? = null

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val rebarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val spiralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        style = Paint.Style.STROKE
        strokeWidth = 2f
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

    private val soilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E63")
        alpha = 60
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 120f
        val footerHeight = 120f
        // Vertical elevation view
        val scale = min((width - 2 * padding) / (dia * 3f), (height - 2 * padding - footerHeight) / length) * 0.9f

        val drawD = dia * scale
        val drawL = length * scale
        val startX = (width - drawD) / 2
        val startY = (height - footerHeight - drawL) / 2 + 40f

        // 1. Draw Ground Level & Soil
        canvas.drawRect(0f, startY, width.toFloat(), height.toFloat(), soilPaint)

        // 2. Draw Pile Body with Hatching
        val pileRect = RectF(startX, startY, startX + drawD, startY + drawL)
        canvas.drawRect(pileRect, concretePaint)
        drawConcreteHatching(canvas, pileRect)
        canvas.drawRect(pileRect, borderPaint)

        // 3. Draw Main Rebars
        val cover = 50f * scale
        canvas.drawLine(startX + cover, startY, startX + cover, startY + drawL - cover, rebarPaint)
        canvas.drawLine(startX + drawD - cover, startY, startX + drawD - cover, startY + drawL - cover, rebarPaint)

        // 4. Draw Spiral Ties (Zigzag representation)
        val step = 400f * scale
        var currentY = startY + cover
        while (currentY < startY + drawL - cover) {
            canvas.drawLine(startX + cover, currentY, startX + drawD - cover, currentY + step / 2, spiralPaint)
            canvas.drawLine(startX + drawD - cover, currentY + step / 2, startX + cover, currentY + step, spiralPaint)
            currentY += step
        }

        // 5. Annotations & Dimensions
        drawDimension(canvas, startX, startY - 30f, startX + drawD, startY - 30f, "D = ${dia.toInt()} mm", true)
        drawDimension(canvas, startX + drawD + 50f, startY, startX + drawD + 50f, startY + drawL, "L = ${(length / 1000).toInt()} m", false)

        // 6. Status & Header
        drawHeaderStatus(canvas)
    }

    private fun drawConcreteHatching(canvas: Canvas, rect: RectF) {
        canvas.save()
        canvas.clipRect(rect)
        val step = 30f
        var i = rect.left - rect.height()
        while (i < rect.right) {
            canvas.drawLine(i, rect.top, i + rect.height(), rect.bottom, hatchPaint)
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

    private fun drawHeaderStatus(canvas: Canvas) {
        val statusText = if (isSafe) "DESIGN SAFE ✓" else "UNSAFE DESIGN ✗"
        val statusColor = if (isSafe) Color.parseColor("#2E7D32") else Color.RED
        val badgePaint = Paint().apply { color = statusColor; alpha = 30 }
        val textStatusPaint = Paint(textPaint).apply { color = statusColor; textSize = 28f }
        
        val rect = RectF(40f, 40f, 40f + textStatusPaint.measureText(statusText) + 40f, 100f)
        canvas.drawRoundRect(rect, 8f, 8f, badgePaint)
        canvas.drawText(statusText, 60f, 85f, textStatusPaint)

        steel?.let {
            canvas.drawText("${it.numBars}Ø${it.diameter} | L=${(length/1000).toInt()}m", 40f, 145f, textPaint.apply { textSize = 22f; color = Color.DKGRAY })
        }
    }

    fun update(dia: Double, length: Double, steel: CalculatorEngine.ReinforcementBar, safe: Boolean = true) {
        this.dia = dia.toFloat()
        this.length = length.toFloat() * 1000
        this.steel = steel
        this.isSafe = safe
        invalidate()
    }
}
