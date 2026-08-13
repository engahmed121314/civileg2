package com.civileg.app.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.utils.CalculatorEngine
import java.util.Locale
import kotlin.math.*

class StaircaseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    
    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        alpha = 30
        style = Paint.Style.FILL
    }
    
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val rebarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    var riserHeight: Float = 150f
        set(value) {
            field = value
            invalidate()
        }
    var treadWidth: Float = 300f
        set(value) {
            field = value
            invalidate()
        }
    var numSteps: Int = 12
        set(value) {
            field = value
            invalidate()
        }
    
    var stairType: CalculatorEngine.StairType = CalculatorEngine.StairType.SINGLE_FLIGHT
        set(value) {
            field = value
            invalidate()
        }

    var landingWidth: Float = 1200f
        set(value) {
            field = value
            invalidate()
        }

    var mainReinforcementText: String = ""
        set(value) {
            field = value
            invalidate()
        }
    
    var concreteVolume: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
        
    var isSafe: Boolean = true
        set(value) {
            field = value
            invalidate()
        }
    
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val padding = 80f
        val availableWidth = width.toFloat() - 2 * padding
        val availableHeight = height.toFloat() - 2 * padding
        
        val totalWidth = when(stairType) {
            CalculatorEngine.StairType.SINGLE_FLIGHT -> (treadWidth * numSteps)
            CalculatorEngine.StairType.DOUBLE_FLIGHT -> (treadWidth * (numSteps/2)) + landingWidth
            else -> (treadWidth * numSteps)
        }
        val totalHeight = riserHeight * numSteps
        
        val scaleX = if (totalWidth > 0) availableWidth / totalWidth else 1f
        val scaleY = if (totalHeight > 0) availableHeight / totalHeight else 1f
        val scale = minOf(scaleX, scaleY) * 0.85f
        
        val startX = padding + (availableWidth - totalWidth * scale) / 2
        val startY = height.toFloat() - padding - 40f
        
        drawFlight(canvas, startX, startY, scale)
        drawAnnotations(canvas, padding)
    }

    private fun drawFlight(canvas: Canvas, startX: Float, startY: Float, scale: Float) {
        path.reset()
        path.moveTo(startX, startY)
        
        var currentX = startX
        var currentY = startY

        for (i in 0 until numSteps) {
            currentX += treadWidth * scale
            path.lineTo(currentX, currentY)
            currentY -= riserHeight * scale
            path.lineTo(currentX, currentY)
        }

        if (stairType == CalculatorEngine.StairType.DOUBLE_FLIGHT) {
            currentX += landingWidth * scale
            path.lineTo(currentX, currentY)
        }
        
        val waistT = 150f * scale
        path.lineTo(currentX, currentY + waistT)
        path.lineTo(startX + waistT, startY)
        path.close()
        
        canvas.drawPath(path, paintFill)
        canvas.drawPath(path, paintLine)
        
        val rebarPath = Path()
        val cover = 25f * scale
        rebarPath.moveTo(startX + cover, startY - cover)
        
        var rX = startX + cover
        var rY = startY - cover
        for (i in 0 until numSteps) {
            rX += treadWidth * scale
            rebarPath.lineTo(rX - cover, rY)
            rY -= riserHeight * scale
            rebarPath.lineTo(rX - cover, rY + cover)
        }
        canvas.drawPath(rebarPath, rebarPaint)
    }

    private fun drawAnnotations(canvas: Canvas, padding: Float) {
        canvas.drawText("Type: ${stairType.name}", padding, height.toFloat() - 15f, paintText)
        
        if (mainReinforcementText.isNotEmpty()) {
            val resultPaint = Paint(paintText).apply {
                color = if (isSafe) Color.parseColor("#2E7D32") else Color.RED
                textSize = 32f
            }
            canvas.drawText("Main Rebar: $mainReinforcementText", padding, padding, resultPaint)
            canvas.drawText("Conc. Vol: ${String.format(Locale.US, "%.2f", concreteVolume)} m³", padding, padding + 40f, resultPaint)
        }
    }
}
