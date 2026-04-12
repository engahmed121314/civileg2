package com.civileg.app.ui.column

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.R

class ColumnDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var widthM = 0.3
    private var depthM = 0.3
    private var barDiameter = 16
    private var tiesSpacing = 20.0

    private val paintRect = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
    }
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val paintBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(139, 69, 19)
        style = Paint.Style.FILL
    }
    private val paintTie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    fun setDimensions(width: Double, depth: Double, barDiameter: Int, tiesSpacing: Double) {
        this.widthM = width
        this.depthM = depth
        this.barDiameter = barDiameter
        this.tiesSpacing = tiesSpacing
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val rectLeft = 0f
        val rectTop = 0f
        val rectRight = w
        val rectBottom = h

        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, paintRect)
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, paintBorder)

        // Draw main bars
        val barRadius = barDiameter / 2f
        val spacingX = w / 3
        val spacingY = h / 3
        for (i in 0..2) {
            for (j in 0..2) {
                val x = spacingX * (i + 0.5f)
                val y = spacingY * (j + 0.5f)
                canvas.drawCircle(x, y, barRadius, paintBar)
            }
        }

        // Draw ties
        val tiePath = Path()
        tiePath.moveTo(rectLeft + 10, rectTop + 10)
        tiePath.lineTo(rectRight - 10, rectTop + 10)
        tiePath.lineTo(rectRight - 10, rectBottom - 10)
        tiePath.lineTo(rectLeft + 10, rectBottom - 10)
        tiePath.close()
        canvas.drawPath(tiePath, paintTie)

        // Text info
        val textPaint = Paint().apply { color = Color.BLACK; textSize = 30f; isFakeBoldText = true }
        canvas.drawText("${context.getString(R.string.width)} = ${String.format("%.2f", widthM)} m", 20f, h - 20f, textPaint)
        canvas.drawText("${context.getString(R.string.col_depth)} = ${String.format("%.2f", depthM)} m", 20f, h - 60f, textPaint)
    }
}
