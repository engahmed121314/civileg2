package com.civileg.app.ui.slab

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SlabDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lxM = 5f
    private var lyM = 4f
    private var thicknessMm = 150f
    private var barDia = 12
    private var spacingMm = 200f

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val steelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F51B5")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F51B5")
        style = Paint.Style.FILL
    }

    fun setDetails(lx: Float, ly: Float, t: Float, dia: Int, s: Float) {
        this.lxM = lx
        this.lyM = ly
        this.thicknessMm = t
        this.barDia = dia
        this.spacingMm = s
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val margin = 50f
        val drawW = w - 2 * margin
        val drawH = h / 2
        val left = margin
        val top = (h - drawH) / 2
        val right = w - margin
        val bottom = top + drawH

        // Draw Slab Concrete
        canvas.drawRect(left, top, right, bottom, concretePaint)
        canvas.drawRect(left, top, right, bottom, borderPaint)

        // Draw Bottom Reinforcement (Main bars - longitudinal)
        val cover = 25f // scaled cover
        val mainSteelY = bottom - cover
        canvas.drawLine(left + cover, mainSteelY, right - cover, mainSteelY, steelPaint)

        // Draw Secondary Reinforcement (Dots - cross section of bars)
        val dotRadius = 8f
        val numDots = 8
        val dotSpacing = (drawW - 2 * cover) / (numDots - 1)
        for (i in 0 until numDots) {
            val dotX = left + cover + i * dotSpacing
            canvas.drawCircle(dotX, mainSteelY - dotRadius - 2f, dotRadius, dotPaint)
        }
    }
}
