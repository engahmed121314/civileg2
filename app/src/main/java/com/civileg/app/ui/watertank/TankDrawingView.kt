package com.civileg.app.ui.watertank

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.utils.CalculatorEngine

class TankDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var lengthM = 5.0f
    private var heightM = 4.0f
    private var wallThicknessMm = 250f
    private var baseThicknessMm = 300f
    private var tankType: CalculatorEngine.TankType = CalculatorEngine.TankType.RECTANGULAR_GROUND
    private var reinforcementText: String = ""

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        style = Paint.Style.FILL
    }
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#402196F3")
        style = Paint.Style.FILL
    }
    private val soilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E63")
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
        textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun setDetails(l: Float, h: Float, wallT: Float, baseT: Float, type: CalculatorEngine.TankType, reinf: String) {
        this.lengthM = l
        this.heightM = h
        this.wallThicknessMm = wallT
        this.baseThicknessMm = baseT
        this.tankType = type
        this.reinforcementText = reinf
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        val padding = 80f
        val drawH = h * 0.6f
        val drawW = w * 0.7f
        val startX = (w - drawW) / 2
        val startY = (h - drawH) / 2
        
        val scale = drawH / heightM
        val tWall = (wallThicknessMm / 1000f) * scale
        val tBase = (baseThicknessMm / 1000f) * scale

        // Draw Soil if Underground
        if (tankType == CalculatorEngine.TankType.UNDERGROUND) {
            canvas.drawRect(0f, startY + 40f, w, h, soilPaint)
        }

        // Draw Water Level
        canvas.drawRect(startX + tWall, startY + 40f, startX + drawW - tWall, startY + drawH - tBase, waterPaint)

        // Draw Tank Section (U-Shape)
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(startX, startY + drawH)
        path.lineTo(startX + drawW, startY + drawH)
        path.lineTo(startX + drawW, startY)
        
        // Inner face
        path.lineTo(startX + drawW - tWall, startY)
        path.lineTo(startX + drawW - tWall, startY + drawH - tBase)
        path.lineTo(startX + tWall, startY + drawH - tBase)
        path.lineTo(startX + tWall, startY)
        path.close()

        canvas.drawPath(path, concretePaint)
        canvas.drawPath(path, borderPaint)

        // Draw Main Reinforcement
        val cover = 40f * (scale / 1000f) 
        
        canvas.drawLine(startX + cover, startY + 10f, startX + cover, startY + drawH - tBase + cover, steelPaint)
        canvas.drawLine(startX + drawW - tWall + cover, startY + 10f, startX + drawW - tWall + cover, startY + drawH - tBase + cover, steelPaint)
        canvas.drawLine(startX + cover, startY + drawH - cover, startX + drawW - cover, startY + drawH - cover, steelPaint)

        // Labels
        canvas.drawText("Type: ${tankType.name}", padding, 50f, textPaint)
        canvas.drawText("Wall: ${wallThicknessMm.toInt()}mm", startX - 20f, startY + drawH/2, textPaint.apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText(reinforcementText, startX, startY + drawH + 60f, textPaint.apply { textAlign = Paint.Align.LEFT; textSize = 26f })
    }
}
