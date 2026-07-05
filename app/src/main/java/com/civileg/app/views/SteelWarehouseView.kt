package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.civileg.app.utils.CalculatorEngine

class SteelWarehouseView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var result: CalculatorEngine.SteelWarehouseResult? = null
    private val framePath = Path()

    private val linePaint = Paint().apply {
        color = "#000080".toColorInt()
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val dimensionPaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    fun setDesignResult(res: CalculatorEngine.SteelWarehouseResult) {
        this.result = res
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val res = result ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 100f

        val startX = padding
        val endX = w - padding
        val groundY = h - padding
        
        // Use eaveHeight and totalHeight from result
        val eaveY = (groundY - (res.eaveHeight * 20.0)).toFloat()
        val ridgeY = (groundY - (res.totalHeight * 20.0)).toFloat()
        val centerX = w / 2

        canvas.drawLine(startX - 20f, groundY, endX + 20f, groundY, linePaint)

        framePath.reset()
        framePath.moveTo(startX, groundY)
        framePath.lineTo(startX, eaveY)
        framePath.lineTo(centerX, ridgeY)
        framePath.lineTo(endX, eaveY)
        framePath.lineTo(endX, groundY)
        canvas.drawPath(framePath, linePaint)

        for (i in 1..4) {
            val ratio = i / 5f
            val px = startX + (centerX - startX) * ratio
            val py = eaveY + (ridgeY - eaveY) * ratio
            canvas.drawCircle(px, py, 5f, linePaint)
        }

        canvas.drawText("Span: ${res.span} m", centerX, groundY + 40f, textPaint)
        canvas.drawText("${res.columnSection}", startX + 60f, (groundY + eaveY) / 2, textPaint)
        canvas.drawText("${res.rafterSection}", (startX + centerX) / 2, (eaveY + ridgeY) / 2 - 20f, textPaint)
    }
}
