package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.utils.ContinuousBeamAnalysis

class AnalysisDiagramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { BMD, SFD }

    var mode: Mode = Mode.BMD
        set(value) {
            field = value
            invalidate()
        }

    private var result: ContinuousBeamAnalysis.AnalysisResult? = null

    private val bmdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val sfdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 40
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
    }

    fun setData(result: ContinuousBeamAnalysis.AnalysisResult) {
        this.result = result
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val res = result ?: return
        if (res.points.isEmpty()) return

        val padding = 50f
        val w = width - 2 * padding
        val h = height - 2 * padding
        val centerY = padding + h / 2

        val maxX = res.points.last().x
        val maxVal = if (mode == Mode.BMD) {
            res.points.maxOf { Math.abs(it.moment) }.coerceAtLeast(1.0)
        } else {
            res.points.maxOf { Math.abs(it.shear) }.coerceAtLeast(1.0)
        }

        val scaleX = w / maxX
        val scaleY = (h / 2) / maxVal

        // Draw Base Axis
        canvas.drawLine(padding, centerY, padding + w, centerY, axisPaint)

        val path = Path()
        val fillPath = Path()
        
        val firstPoint = res.points.first()
        val firstX = padding + (firstPoint.x * scaleX).toFloat()
        val firstY = if (mode == Mode.BMD) {
            centerY + (firstPoint.moment * scaleY).toFloat()
        } else {
            centerY - (firstPoint.shear * scaleY).toFloat()
        }
        
        path.moveTo(firstX, firstY)
        fillPath.moveTo(firstX, centerY)
        fillPath.lineTo(firstX, firstY)

        res.points.forEach { pt ->
            val px = padding + (pt.x * scaleX).toFloat()
            val py = if (mode == Mode.BMD) {
                centerY + (pt.moment * scaleY).toFloat()
            } else {
                centerY - (pt.shear * scaleY).toFloat()
            }
            path.lineTo(px, py)
            fillPath.lineTo(px, py)
        }

        fillPath.lineTo(padding + w, centerY)
        fillPath.close()

        fillPaint.color = if (mode == Mode.BMD) Color.BLUE else Color.RED
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, if (mode == Mode.BMD) bmdPaint else sfdPaint)

        // Draw values at peaks
        // (Simplified: just draw max/min values)
        if (mode == Mode.BMD) {
            val maxM = res.points.maxByOrNull { it.moment }
            val minM = res.points.minByOrNull { it.moment }
            
            maxM?.let {
                canvas.drawText(String.format("%.1f", it.moment), padding + (it.x * scaleX).toFloat(), centerY + (it.moment * scaleY).toFloat() + 30, textPaint)
            }
            minM?.let {
                if (Math.abs(it.moment) > 0.1)
                canvas.drawText(String.format("%.1f", it.moment), padding + (it.x * scaleX).toFloat(), centerY + (it.moment * scaleY).toFloat() - 10, textPaint)
            }
        } else {
            val maxV = res.points.maxByOrNull { it.shear }
            val minV = res.points.minByOrNull { it.shear }
            maxV?.let {
                canvas.drawText(String.format("%.1f", it.shear), padding + (it.x * scaleX).toFloat(), centerY - (it.shear * scaleY).toFloat() - 10, textPaint)
            }
            minV?.let {
                canvas.drawText(String.format("%.1f", it.shear), padding + (it.x * scaleX).toFloat(), centerY - (it.shear * scaleY).toFloat() + 30, textPaint)
            }
        }
        
        // Label
        canvas.drawText(if (mode == Mode.BMD) "Bending Moment Diagram (kN.m)" else "Shear Force Diagram (kN)", padding, padding + 30, textPaint)
    }
}
