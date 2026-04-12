package com.civileg.app.ui.retaining

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.utils.CalculatorEngine

class RetainingWallDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var heightM = 3.0f
    private var stemThicknessMm = 300f
    private var baseWidthM = 2.0f
    private var baseThicknessMm = 400f
    private var reinforcementText: String = ""

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFD8DC")
        style = Paint.Style.FILL
    }
    private val soilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A1887F")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val steelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun setDetails(h: Float, stemT: Float, baseW: Float, baseT: Float, reinf: String) {
        this.heightM = h
        this.stemThicknessMm = stemT
        this.baseWidthM = baseW
        this.baseThicknessMm = baseT
        this.reinforcementText = reinf
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val padding = 100f
        val totalViewH = heightM + (baseThicknessMm / 1000f)
        val scale = (h - 2 * padding) / totalViewH
        
        val drawH = heightM * scale
        val drawBaseT = (baseThicknessMm / 1000f) * scale
        val drawBaseW = baseWidthM * scale
        val drawStemT = (stemThicknessMm / 1000f) * scale

        val startX = (w - drawBaseW) / 2
        val startY = padding

        // Draw Soil on backfill side (Right)
        canvas.drawRect(startX + (drawBaseW * 0.4f) + drawStemT, startY + 20f, w, startY + drawH + drawBaseT, soilPaint)

        // Draw Retaining Wall Shape
        val path = Path()
        val stemLeft = startX + (drawBaseW * 0.3f)
        
        // Base
        path.moveTo(startX, startY + drawH)
        path.lineTo(startX + drawBaseW, startY + drawH)
        path.lineTo(startX + drawBaseW, startY + drawH + drawBaseT)
        path.lineTo(startX, startY + drawH + drawBaseT)
        path.close()
        
        // Stem
        path.moveTo(stemLeft, startY + drawH)
        path.lineTo(stemLeft, startY)
        path.lineTo(stemLeft + drawStemT, startY)
        path.lineTo(stemLeft + drawStemT, startY + drawH)
        
        canvas.drawPath(path, concretePaint)
        canvas.drawPath(path, borderPaint)

        // Reinforcement
        val cover = 40f * (scale / 1000f)
        
        // Main Vertical Steel (Stem back face)
        val reinfX = stemLeft + drawStemT - cover
        canvas.drawLine(reinfX, startY + 20f, reinfX, startY + drawH + drawBaseT - cover, steelPaint)
        
        // Base Steel (Tension side - bottom)
        canvas.drawLine(startX + 20f, startY + drawH + drawBaseT - cover, startX + drawBaseW - 20f, startY + drawH + drawBaseT - cover, steelPaint)

        // Labels
        canvas.drawText("H = ${heightM}m", startX - 10f, startY + drawH/2, textPaint.apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("B = ${baseWidthM}m", startX + drawBaseW/2, startY + drawH + drawBaseT + 40f, textPaint.apply { textAlign = Paint.Align.CENTER })
        canvas.drawText(reinforcementText, startX, startY + drawH + drawBaseT + 80f, textPaint.apply { textAlign = Paint.Align.LEFT; textSize = 24f })
    }
}
