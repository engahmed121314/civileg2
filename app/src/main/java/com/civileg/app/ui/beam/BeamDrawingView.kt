package com.civileg.app.ui.beam

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.civileg.app.R

/**
 * Professional Beam Section Drawing View with Dark/Light Theme support.
 */
class BeamDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var widthMm = 250f
    private var depthMm = 600f
    private var bottomBars = 3
    private var topBars = 2
    private var barDia = 16

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val steelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val stirrupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        updateColors()
    }

    private fun updateColors() {
        val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        concretePaint.color = if (isDark) Color.parseColor("#424242") else Color.parseColor("#F5F5F5")
        borderPaint.color = if (isDark) Color.parseColor("#BDBDBD") else Color.parseColor("#455A64")
        steelPaint.color = if (isDark) Color.parseColor("#EF5350") else Color.parseColor("#8B4513")
        stirrupPaint.color = if (isDark) Color.parseColor("#64B5F6") else Color.parseColor("#1565C0")
        textPaint.color = if (isDark) Color.WHITE else Color.BLACK
    }

    fun setDetails(w: Float, d: Float, bottom: Int, top: Int, dia: Int) {
        this.widthMm = w
        this.depthMm = d
        this.bottomBars = bottom
        this.topBars = top
        this.barDia = dia
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateColors()

        val w = width.toFloat()
        val h = height.toFloat()

        val padding = 60f
        val scale = minOf((w - 2 * padding) / widthMm, (h - 2 * padding) / depthMm) * 0.9f
        
        val drawW = widthMm * scale
        val drawH = depthMm * scale

        val left = (w - drawW) / 2
        val top = (h - drawH) / 2

        // Draw concrete section
        canvas.drawRect(left, top, left + drawW, top + drawH, concretePaint)
        canvas.drawRect(left, top, left + drawW, top + drawH, borderPaint)

        // Draw stirrup
        val cover = 25f * scale
        canvas.drawRect(left + cover, top + cover, left + drawW - cover, top + drawH - cover, stirrupPaint)

        // Draw bottom bars
        val barRadius = (barDia / 2f) * scale * 0.7f
        val bottomY = top + drawH - cover - barRadius - 5f
        if (bottomBars > 0) {
            val spacingX = if (bottomBars > 1) (drawW - 2 * cover - 2 * barRadius) / (bottomBars - 1) else 0f
            for (i in 0 until bottomBars) {
                val x = left + cover + barRadius + i * spacingX
                canvas.drawCircle(x, bottomY, barRadius.coerceAtLeast(6f), steelPaint)
            }
        }

        // Draw top bars
        val topY = top + cover + barRadius + 5f
        if (topBars > 0) {
            val spacingTopX = if (topBars > 1) (drawW - 2 * cover - 2 * barRadius) / (topBars - 1) else 0f
            for (i in 0 until topBars) {
                val x = left + cover + barRadius + i * spacingTopX
                canvas.drawCircle(x, topY, barRadius.coerceAtLeast(6f), steelPaint)
            }
        }

        // Labels
        canvas.drawText("${widthMm.toInt()}x${depthMm.toInt()} mm", left, top - 20f, textPaint)
    }
}
