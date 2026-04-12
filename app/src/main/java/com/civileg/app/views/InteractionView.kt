package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Professional P-M Interaction Diagram View
 * Features: Grid lines, smooth curve, safety zone visualization, and applied load point.
 */
class InteractionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class InteractionPoint(val P: Float, val M: Float)

    private var points: List<InteractionPoint> = emptyList()
    private var appliedP: Float = 0f
    private var appliedM: Float = 0f

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBDEFB")
        alpha = 50
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        style = Paint.Style.FILL
        setShadowLayer(5f, 0f, 0f, Color.RED)
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ECEFF1")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546E7A")
        textSize = 20f
    }

    fun setDiagramData(curvePoints: List<InteractionPoint>, p: Float, m: Float) {
        this.points = curvePoints
        this.appliedP = p
        this.appliedM = m
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return

        val paddingL = 100f
        val paddingR = 60f
        val paddingT = 80f
        val paddingB = 100f
        
        val w = width.toFloat() - paddingL - paddingR
        val h = height.toFloat() - paddingT - paddingB

        val maxP = (points.maxByOrNull { it.P }?.P ?: 1000f) * 1.1f
        val minP = 0f
        val maxM = (points.maxByOrNull { it.M }?.M ?: 500f) * 1.1f
        
        val pRange = maxP - minP
        val mRange = if (maxM == 0f) 1f else maxM

        fun scaleX(m: Float): Float = paddingL + (m / mRange * w)
        fun scaleY(p: Float): Float = paddingT + h - ((p - minP) / (if (pRange == 0f) 1f else pRange) * h)

        // 1. Draw Grid
        val gridSteps = 5
        for (i in 0..gridSteps) {
            val gx = paddingL + (i.toFloat() / gridSteps) * w
            val gy = paddingT + (i.toFloat() / gridSteps) * h
            canvas.drawLine(gx, paddingT, gx, paddingT + h, gridPaint)
            canvas.drawLine(paddingL, gy, paddingL + w, gy, gridPaint)
            
            // Grid Labels
            val pVal = minP + (gridSteps - i) * (pRange / gridSteps)
            val mVal = i * (mRange / gridSteps)
            canvas.drawText("${pVal.toInt()}", 10f, gy + 8f, labelPaint)
            canvas.drawText("${mVal.toInt()}", gx - 15f, paddingT + h + 35f, labelPaint)
        }

        // 2. Draw Curve and Safe Area
        val path = Path()
        val fillPath = Path()
        points.forEachIndexed { index, pt ->
            val x = scaleX(pt.M)
            val y = scaleY(pt.P)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(scaleX(0f), scaleY(0f))
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(scaleX(0f), scaleY(0f))
        fillPath.close()
        
        canvas.drawPath(fillPath, areaPaint)
        canvas.drawPath(path, curvePaint)

        // 3. Draw Axes
        canvas.drawLine(scaleX(0f), paddingT - 20f, scaleX(0f), paddingT + h, axisPaint) // P axis
        canvas.drawLine(paddingL, scaleY(0f), paddingL + w + 20f, scaleY(0f), axisPaint) // M axis

        // 4. Draw Applied Point
        val appX = scaleX(appliedM)
        val appY = scaleY(appliedP)
        canvas.drawCircle(appX, appY, 10f, pointPaint)
        
        // 5. Labels & Status
        canvas.drawText("P (kN)", scaleX(0f) - 40f, paddingT - 40f, textPaint)
        canvas.drawText("M (kNm)", paddingL + w - 40f, scaleY(0f) + 60f, textPaint)
        
        val isSafe = isPointInside(appliedM, appliedP)
        val statusText = if (isSafe) "DESIGN SAFE ✓" else "UNSAFE DESIGN ✗"
        val statusColor = if (isSafe) Color.parseColor("#2E7D32") else Color.RED
        
        val badgePaint = Paint().apply { color = statusColor; alpha = 30 }
        val textStatusPaint = Paint(textPaint).apply { color = statusColor; textSize = 30f }
        val rect = RectF(paddingL, 20f, paddingL + textStatusPaint.measureText(statusText) + 40f, 70f)
        canvas.drawRoundRect(rect, 8f, 8f, badgePaint)
        canvas.drawText(statusText, paddingL + 20f, 55f, textStatusPaint)
    }

    private fun isPointInside(m: Float, p: Float): Boolean {
        if (points.isEmpty()) return false
        val sortedPoints = points.sortedBy { it.M }
        for (i in 0 until sortedPoints.size - 1) {
            val p1 = sortedPoints[i]
            val p2 = sortedPoints[i+1]
            if (m >= p1.M && m <= p2.M) {
                val ratio = (m - p1.M) / (p2.M - p1.M)
                val curveP = p1.P + ratio * (p2.P - p1.P)
                return p <= curveP
            }
        }
        return false
    }
}
