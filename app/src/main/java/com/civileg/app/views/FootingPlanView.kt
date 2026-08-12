package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Professional Footing Plan View
 * Features: Dimension lines, realistic hatching, rebar representation, and safety status.
 */
class FootingPlanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var footingLength = 2000f
    var footingWidth = 2000f
    var footingThickness = 400f
    var columnLength = 600f
    var columnWidth = 300f
    var barsX = 12
    var barsY = 12
    var barDia = 16
    var isSafe = true

    private val footingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val colPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#78909C")
        style = Paint.Style.FILL
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BF360C")
        strokeWidth = 2.5f
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
        color = Color.parseColor("#CFD8DC")
        strokeWidth = 1f
    }

    fun updateFromCalculation(
        fL: Float, fW: Float, fT: Float, cL: Float, cW: Float,
        bX: Int, bY: Int, bDia: Int, sPressure: Double, aPressure: Double,
        pStress: Double, aShear: Double, safe: Boolean
    ) {
        this.footingLength = fL
        this.footingWidth = fW
        this.footingThickness = fT
        this.columnLength = cL
        this.columnWidth = cW
        this.barsX = bX
        this.barsY = bY
        this.barDia = bDia
        this.isSafe = safe
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val padding = 120f
        val scale = min((width - 2 * padding) / footingLength, (height - 2 * padding) / footingWidth) * 0.85f
        
        val drawL = footingLength * scale
        val drawW = footingWidth * scale
        
        val left = (width - drawL) / 2
        val top = (height - drawW) / 2
        val right = left + drawL
        val bottom = top + drawW
        
        // 1. Draw Footing Body with Hatching
        canvas.drawRect(left, top, right, bottom, footingPaint)
        drawConcreteHatching(canvas, left, top, right, bottom)
        canvas.drawRect(left, top, right, bottom, borderPaint)
        
        // 2. Draw Reinforcement Bars
        val coverPx = 50f * scale // typical cover
        val rebarAreaLeft = left + coverPx
        val rebarAreaTop = top + coverPx
        val rebarAreaRight = right - coverPx
        val rebarAreaBottom = bottom - coverPx
        
        // Bars in X direction (Longitudinal)
        if (barsX > 1) {
            val stepX = (rebarAreaRight - rebarAreaLeft) / (barsX - 1)
            for (i in 0 until barsX) {
                val x = rebarAreaLeft + i * stepX
                canvas.drawLine(x, rebarAreaTop, x, rebarAreaBottom, barPaint)
            }
        }
        
        // Bars in Y direction (Transverse)
        if (barsY > 1) {
            val stepY = (rebarAreaBottom - rebarAreaTop) / (barsY - 1)
            for (i in 0 until barsY) {
                val y = rebarAreaTop + i * stepY
                canvas.drawLine(rebarAreaLeft, y, rebarAreaRight, y, barPaint)
            }
        }
        
        // 3. Draw Column
        val colDrawL = columnLength * scale
        val colDrawW = columnWidth * scale
        val cLeft = left + (drawL - colDrawL) / 2
        val cTop = top + (drawW - colDrawW) / 2
        canvas.drawRect(cLeft, cTop, cLeft + colDrawL, cTop + colDrawW, colPaint)
        canvas.drawRect(cLeft, cTop, cLeft + colDrawL, cTop + colDrawW, borderPaint)
        
        // 4. Dimensions
        drawDimension(canvas, left, top - 40f, right, top - 40f, "L = ${footingLength.toInt()} mm", true)
        drawDimension(canvas, left - 40f, top, left - 40f, bottom, "W = ${footingWidth.toInt()} mm", false)

        // 5. Header & Safety
        drawHeader(canvas)
    }

    private fun drawConcreteHatching(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        val step = 30f
        canvas.save()
        canvas.clipRect(l, t, r, b)
        var i = l - (b - t)
        while (i < r) {
            canvas.drawLine(i, t, i + (b - t), b, hatchPaint)
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
            canvas.rotate(-90f, x1 - 10f, (y1 + y2) / 2)
            canvas.drawText(text, x1 - 10f, (y1 + y2) / 2, dimensionPaint)
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

        canvas.drawText("T = ${footingThickness.toInt()} mm | ${barsX}Ø${barDia} T&B", 40f, 145f, textPaint.apply { textSize = 22f; color = Color.DKGRAY })
    }
}
