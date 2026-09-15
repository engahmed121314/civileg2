package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.civileg.app.R
import java.util.Locale
import kotlin.math.min

/**
 * Professional Slab Detail View
 * Supports Solid, Flat, Ribbed, Waffle, and PT slabs with engineering visualization.
 */
class SlabDetailView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class SlabType { SOLID, FLAT, RIBBED, WAFFLE, HOLLOW_BLOCK, POST_TENSION }

    var type = SlabType.SOLID
    var lx = 5000f
    var ly = 4000f
    var t = 150f
    var dia = 10
    var spacing = 200f
    var isSafe = true
    var ptStrands = 0
    var mainSteelText = ""

    // Calculation results for display
    var momentCapacityX: Double = 0.0
    var momentCapacityY: Double = 0.0
    var appliedMomentX: Double = 0.0
    var appliedMomentY: Double = 0.0
    var deflection: Double = 0.0
    var allowableDeflection: Double = 0.0

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }
    
    private val ribPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        style = Paint.Style.FILL
    }

    private val rebarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#303F9F")
        strokeWidth = 3f
    }

    private val ptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val dimensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        strokeWidth = 2f
        textSize = 24f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 1f
    }

    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }

    fun updateFromCalculation(
        type: SlabType, lx: Float, ly: Float, t: Float,
        dia: Int, spacing: Float, mx: Double, my: Double, safe: Boolean,
        mainSteelText: String, ptStrands: Int = 0,
        mxCap: Double = 0.0, myCap: Double = 0.0,
        defl: Double = 0.0, allowDefl: Double = 0.0
    ) {
        this.type = type
        this.lx = lx
        this.ly = ly
        this.t = t
        this.dia = dia
        this.spacing = spacing
        this.isSafe = safe
        this.mainSteelText = mainSteelText
        this.ptStrands = ptStrands
        this.momentCapacityX = mxCap
        this.momentCapacityY = myCap
        this.appliedMomentX = mx
        this.appliedMomentY = my
        this.deflection = defl
        this.allowableDeflection = allowDefl
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val padding = 120f
        val resultsHeight = 200f
        val scale = min((width - 2 * padding) / lx, (height - 2 * padding - resultsHeight) / ly) * 0.9f
        
        val drawL = lx * scale
        val drawW = ly * scale
        val left = (width - drawL) / 2
        val top = (height - resultsHeight - drawW) / 2 + 40f
        val right = left + drawL
        val bottom = top + drawW

        // 1. Draw Base Slab with Concrete Hatching
        canvas.drawRect(left, top, right, bottom, concretePaint)
        drawConcreteHatching(canvas, left, top, right, bottom)

        // 2. Draw Type Specific Details
        when (type) {
            SlabType.WAFFLE -> drawWafflePattern(canvas, left, top, right, bottom, scale)
            SlabType.RIBBED, SlabType.HOLLOW_BLOCK -> drawRibbedPattern(canvas, left, top, right, bottom, scale)
            SlabType.POST_TENSION -> drawPTPattern(canvas, left, top, right, bottom, scale)
            else -> drawStandardRebar(canvas, left, top, right, bottom, scale)
        }

        // 3. Draw Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#455A64")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(left, top, right, bottom, borderPaint)
        
        // 4. Dimensions
        drawDimension(canvas, left, top - 40f, right, top - 40f, "Lx = ${lx.toInt()} mm", true)
        drawDimension(canvas, left - 40f, top, left - 40f, bottom, "Ly = ${ly.toInt()} mm", false)

        // 5. Header & Results
        drawHeader(canvas)
        drawResultsPanel(canvas, 40f, height - resultsHeight + 40f)
    }

    private fun drawConcreteHatching(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        canvas.save()
        canvas.clipRect(l, t, r, b)
        val step = 40f
        var i = l - (b - t)
        while (i < r) {
            canvas.drawLine(i, t, i + (b - t), b, hatchPaint)
            i += step
        }
        canvas.restore()
    }

    private fun drawStandardRebar(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, scale: Float) {
        val step = (spacing * scale).coerceAtLeast(15f)
        val cover = 30f * scale
        
        // Horizontal Bars
        var y = t + cover
        while (y < b - cover) {
            canvas.drawLine(l + cover, y, r - cover, y, rebarPaint)
            y += step
        }
        
        // Vertical Bars
        var x = l + cover
        while (x < r - cover) {
            canvas.drawLine(x, t + cover, x, b - cover, rebarPaint)
            x += step
        }
    }

    private fun drawRibbedPattern(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, scale: Float) {
        val ribSpacing = 500f * scale
        val ribWidth = 120f * scale
        
        var x = l + ribSpacing
        while (x < r) {
            canvas.drawRect(x - ribWidth/2, t, x + ribWidth/2, b, ribPaint)
            canvas.drawLine(x, t + 10f, x, b - 10f, rebarPaint)
            x += ribSpacing
        }
        // Mesh rebar
        drawStandardRebar(canvas, l, t, r, b, scale / 2) 
    }

    private fun drawWafflePattern(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, scale: Float) {
        val spacing = 700f * scale
        val ribW = 150f * scale
        
        var x = l + spacing
        while (x < r) {
            canvas.drawRect(x - ribW/2, t, x + ribW/2, b, ribPaint)
            x += spacing
        }
        var y = t + spacing
        while (y < b) {
            canvas.drawRect(l, y - ribW/2, r, y + ribW/2, ribPaint)
            y += spacing
        }
    }

    private fun drawPTPattern(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, scale: Float) {
        drawStandardRebar(canvas, l, t, r, b, scale)
        val strandStep = (r - l) / (ptStrands + 1).coerceAtLeast(1)
        for (i in 1..ptStrands) {
            val x = l + i * strandStep
            canvas.drawLine(x, t, x, b, ptPaint)
            canvas.drawCircle(x, t, 8f, ptPaint)
            canvas.drawCircle(x, b, 8f, ptPaint)
        }
    }

    private fun drawDimension(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, text: String, horizontal: Boolean) {
        canvas.drawLine(x1, y1, x2, y2, dimensionPaint)
        val tick = 12f
        if (horizontal) {
            canvas.drawLine(x1, y1 - tick, x1, y1 + tick, dimensionPaint)
            canvas.drawLine(x2, y2 - tick, x2, y2 + tick, dimensionPaint)
            canvas.drawText(text, (x1 + x2) / 2 - dimensionPaint.measureText(text) / 2, y1 - 10f, dimensionPaint)
        } else {
            canvas.drawLine(x1 - tick, y1, x1 + tick, y1, dimensionPaint)
            canvas.drawLine(x2 - tick, y2, x2 + tick, y2, dimensionPaint)
            canvas.save()
            canvas.rotate(-90f, x1 - 10f, (y1 + y2) / 2)
            canvas.drawText(text, x1 - 10f, (y1 + y2) / 2, dimensionPaint)
            canvas.restore()
        }
    }

    private fun drawHeader(canvas: Canvas) {
        val statusText = if (isSafe) "STRUCTURAL SAFE ✓" else "UNSAFE DESIGN ✗"
        val statusColor = if (isSafe) Color.parseColor("#2E7D32") else Color.RED
        val badgePaint = Paint().apply { color = statusColor; alpha = 30 }
        val textStatusPaint = Paint(textPaint).apply { color = statusColor; textSize = 28f }
        
        val rect = RectF(40f, 40f, 40f + textStatusPaint.measureText(statusText) + 40f, 100f)
        canvas.drawRoundRect(rect, 8f, 8f, badgePaint)
        canvas.drawText(statusText, 60f, 85f, textStatusPaint)

        val info = "Type: ${type.name} | $mainSteelText | t=${t.toInt()}mm"
        canvas.drawText(info, 40f, 145f, textPaint.apply { textSize = 22f; color = Color.DKGRAY })
    }

    private fun drawResultsPanel(canvas: Canvas, startX: Float, startY: Float) {
        var yOffset = startY
        val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 22f }

        val mxText = String.format(Locale.getDefault(), "Mx Capacity: %.1f kNm (Applied: %.1f)", momentCapacityX, appliedMomentX)
        canvas.drawText(mxText, startX, yOffset, if (momentCapacityX >= appliedMomentX) resultPaint else warningPaint)
        yOffset += 40

        val myText = String.format(Locale.getDefault(), "My Capacity: %.1f kNm (Applied: %.1f)", momentCapacityY, appliedMomentY)
        canvas.drawText(myText, startX, yOffset, if (momentCapacityY >= appliedMomentY) resultPaint else warningPaint)
        yOffset += 40

        val deflText = String.format(Locale.getDefault(), "Deflection: %.1f mm (Allowable: %.1f)", deflection, allowableDeflection)
        canvas.drawText(deflText, startX, yOffset, if (deflection <= allowableDeflection) resultPaint else warningPaint)
    }
}
