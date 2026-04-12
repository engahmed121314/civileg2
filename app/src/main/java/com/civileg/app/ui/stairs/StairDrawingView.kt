package com.civileg.app.ui.stairs

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.utils.CalculatorEngine

class StairDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var riserHeight: Float = 150f
    var treadWidth: Float = 300f
    var numSteps: Int = 10
    var stairType: CalculatorEngine.StairType = CalculatorEngine.StairType.SINGLE_FLIGHT
    var mainReinforcementText: String = ""
    var concreteVolume: Float = 0f
    var isSafe: Boolean = true

    fun setDetails(riser: Float, tread: Float, reinforcement: String, volume: Float, safe: Boolean) {
        this.riserHeight = riser
        this.treadWidth = tread
        this.mainReinforcementText = reinforcement
        this.concreteVolume = volume
        this.isSafe = safe
        invalidate()
    }

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val steelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 32f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val padding = 60f
        val availableW = width - 2 * padding
        val availableH = height - 2 * padding

        // Scale based on 3 visible steps for visualization
        val stepCount = 3
        val scaleX = availableW / (stepCount * 1.5f)
        val scaleY = availableH / (stepCount * 1.2f)
        val scale = Math.min(scaleX, scaleY)

        val drawStepW = 300f * (scale / 300f) * 100f
        val drawStepH = 150f * (scale / 150f) * 50f

        val path = Path()
        var curX = padding
        var curY = height - padding

        path.moveTo(curX, curY)
        
        // Draw steps
        for (i in 0 until stepCount) {
            path.lineTo(curX, curY - drawStepH)
            curY -= drawStepH
            path.lineTo(curX + drawStepW, curY)
            curX += drawStepW
        }

        // Draw slab bottom
        val slabThick = 150f * (scale / 150f) * 30f
        path.lineTo(curX, curY + slabThick)
        path.lineTo(padding + slabThick, height - padding)
        path.close()

        canvas.drawPath(path, concretePaint)
        canvas.drawPath(path, borderPaint)

        // Draw Steel
        val steelPath = Path()
        steelPath.moveTo(padding + 20f, height - padding - 20f)
        steelPath.lineTo(curX - 20f, curY + slabThick - 20f)
        canvas.drawPath(steelPath, steelPaint)

        // Draw Info
        canvas.drawText(if(isSafe) "SAFE ✓" else "UNSAFE ✗", padding, padding + 40f, textPaint.apply { 
            color = if(isSafe) Color.parseColor("#2E7D32") else Color.RED 
        })
        
        canvas.drawText(mainReinforcementText, padding, padding + 90f, textPaint.apply { 
            color = Color.BLACK; textSize = 28f 
        })
    }
}
