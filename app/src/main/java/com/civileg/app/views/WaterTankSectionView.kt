package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.civileg.app.R
import com.civileg.app.utils.CalculatorEngine
import kotlin.math.min

/**
 * Professional Water Tank Section View
 * Features: Dimension lines, realistic hatching, interactive reinforcement, and soil/support visualization.
 * Updated to support Light/Dark theme.
 */
class WaterTankSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var tankLength: Float = 5000f
    var tankHeight: Float = 3000f
    var thickness: Float = 250f
    var baseThickness: Float = 400f
    var waterDepth: Float = 2500f
    var isSafe: Boolean = true
    var isOptimal: Boolean = true
    var rebarText: String = ""
    var baseRebarText: String = ""
    var tankType: CalculatorEngine.TankType = CalculatorEngine.TankType.RECTANGULAR_GROUND

    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0288D1")
        alpha = 120
        style = Paint.Style.FILL
    }

    private val rebarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val dimensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        textSize = 24f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val soilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8D6E63")
        alpha = 80
        style = Paint.Style.FILL
    }

    private val hatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
    }

    init {
        updateColors()
    }

    private fun updateColors() {
        val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        concretePaint.color = if (isDark) Color.parseColor("#424242") else Color.parseColor("#F5F5F5")
        borderPaint.color = if (isDark) Color.parseColor("#BDBDBD") else Color.parseColor("#455A64")
        rebarPaint.color = if (isDark) Color.parseColor("#EF5350") else Color.parseColor("#B71C1C")
        dimensionPaint.color = if (isDark) Color.parseColor("#64B5F6") else Color.parseColor("#1565C0")
        textPaint.color = if (isDark) Color.WHITE else Color.BLACK
        hatchPaint.color = if (isDark) Color.parseColor("#616161") else Color.parseColor("#EEEEEE")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateColors()

        val padding = 120f
        val footerHeight = 100f
        val scale = min((width - 2 * padding) / tankLength, (height - 2 * padding - footerHeight) / tankHeight) * 0.8f

        val drawL = tankLength * scale
        val drawH = tankHeight * scale
        val drawT = thickness * scale
        val drawBT = baseThickness * scale
        val drawW = waterDepth * scale

        val startX = (width - drawL) / 2
        val startY = (height - footerHeight - drawH - drawBT) / 2 + 20f

        // 1. Environmental Visualization
        if (tankType == CalculatorEngine.TankType.UNDERGROUND) {
            canvas.drawRect(0f, startY + 50f, startX - drawT - 5f, height.toFloat(), soilPaint)
            canvas.drawRect(startX + drawL + drawT + 5f, startY + 50f, width.toFloat(), height.toFloat(), soilPaint)
        } else if (tankType == CalculatorEngine.TankType.RECTANGULAR_ELEVATED || tankType == CalculatorEngine.TankType.CIRCULAR_ELEVATED) {
            val colW = 40f * scale
            canvas.drawRect(startX + 20f, startY + drawH + drawBT, startX + 20f + colW, height.toFloat(), concretePaint)
            canvas.drawRect(startX + drawL - 20f - colW, startY + drawH + drawBT, startX + drawL - 20f, height.toFloat(), concretePaint)
        }

        // 2. Main Tank Structure
        val tankOuter = RectF(startX - drawT, startY - drawT, startX + drawL + drawT, startY + drawH + drawBT)
        val tankInner = RectF(startX, startY, startX + drawL, startY + drawH)
        
        val tankPath = Path().apply {
            addRect(tankOuter, Path.Direction.CW)
            addRect(tankInner, Path.Direction.CCW)
        }
        canvas.drawPath(tankPath, concretePaint)
        drawStructuralHatching(canvas, tankOuter, tankInner)
        canvas.drawPath(tankPath, borderPaint)

        // 3. Draw Water
        if (waterDepth > 0) {
            val waterRect = RectF(startX, startY + drawH - drawW, startX + drawL, startY + drawH)
            canvas.drawRect(waterRect, waterPaint)
            canvas.drawLine(startX, startY + drawH - drawW, startX + drawL, startY + drawH - drawW, Paint(waterPaint).apply { alpha = 200; strokeWidth = 2f; style = Paint.Style.STROKE })
        }

        // 4. Dimensions
        val isCircular = tankType == CalculatorEngine.TankType.CIRCULAR_GROUND || 
                        tankType == CalculatorEngine.TankType.CIRCULAR_ELEVATED || 
                        tankType == CalculatorEngine.TankType.CIRCULAR_UNDERGROUND
        
        val label = if (isCircular) "Inner Diameter Ø = ${tankLength.toInt()} mm" else "Length L = ${tankLength.toInt()} mm"
        drawDimension(canvas, startX, startY - drawT - 50f, startX + drawL, startY - drawT - 50f, label, true)
        drawDimension(canvas, startX - drawT - 50f, startY, startX - drawT - 50f, startY + drawH, "Height H = ${tankHeight.toInt()} mm", false)
        
        // 5. Mini Plan View (To distinguish between Circular and Rectangular)
        drawMiniPlanView(canvas, isCircular)

        // 6. Reinforcement Bars (Detailed Detailing)
        val cover = 40f * scale
        if (isCircular) {
            // Circular Tank: Show Hoop Reinforcement (Dots on the wall section)
            val hoopPaint = Paint(rebarPaint).apply { color = Color.parseColor("#D32F2F"); style = Paint.Style.FILL }
            var ry = startY + cover
            while (ry < startY + drawH) {
                canvas.drawCircle(startX - drawT/2, ry, 5f, hoopPaint)
                canvas.drawCircle(startX + drawL + drawT/2, ry, 5f, hoopPaint)
                ry += 150f * scale 
            }
            // Vertical distribution bars
            canvas.drawLine(startX - drawT + cover, startY, startX - drawT + cover, startY + drawH + drawBT - cover, rebarPaint)
        } else {
            // Rectangular Tank: Main Bending Reinforcement
            canvas.drawLine(startX - drawT + cover, startY - drawT + cover, startX - drawT + cover, startY + drawH + drawBT - cover, rebarPaint)
            canvas.drawLine(startX + drawL + drawT - cover, startY - drawT + cover, startX + drawL + drawT - cover, startY + drawH + drawBT - cover, rebarPaint)
        }

        // 6. Header Info & Safety
        drawHeaderStatus(canvas)
        drawEconomyBadge(canvas)

        // 7. Hydrostatic Pressure Diagram
        if (waterDepth > 0) {
            drawPressureDiagram(canvas, startX, startY, drawL, drawH, drawW, scale)
        }

        // 8. Calculations Summary Overlay
        drawCalculationsSummary(canvas)
    }

    private fun drawMiniPlanView(canvas: Canvas, isCircular: Boolean) {
        val miniSize = 100f
        val margin = 40f
        val rect = RectF(width - miniSize - margin, margin + 50f, width - margin, margin + miniSize + 50f)
        
        val miniPaint = Paint(concretePaint).apply { alpha = 200 }
        val miniBorder = Paint(borderPaint).apply { strokeWidth = 2f }
        
        if (isCircular) {
            canvas.drawCircle(rect.centerX(), rect.centerY(), miniSize/2, miniPaint)
            canvas.drawCircle(rect.centerX(), rect.centerY(), miniSize/2, miniBorder)
            // Draw hoop rebar dots in plan
            val r = miniSize/2 - 5f
            for (i in 0..7) {
                val angle = Math.toRadians(i * 45.0)
                val dx = rect.centerX() + r * Math.cos(angle).toFloat()
                val dy = rect.centerY() + r * Math.sin(angle).toFloat()
                canvas.drawCircle(dx, dy, 2f, Paint().apply { color = Color.RED; style = Paint.Style.FILL })
            }
        } else {
            canvas.drawRect(rect, miniPaint)
            canvas.drawRect(rect, miniBorder)
            // Draw main rebar lines in plan
            canvas.drawLine(rect.left + 5f, rect.top + 5f, rect.left + 5f, rect.bottom - 5f, Paint().apply { color = Color.RED; strokeWidth = 1f })
            canvas.drawLine(rect.right - 5f, rect.top + 5f, rect.right - 5f, rect.bottom - 5f, Paint().apply { color = Color.RED; strokeWidth = 1f })
        }
        canvas.drawText(if(isCircular) "Plan: Circular" else "Plan: Rect", rect.left, rect.bottom + 25f, dimensionPaint.apply { textSize = 16f })
    }

    private fun drawPressureDiagram(canvas: Canvas, x: Float, y: Float, drawL: Float, drawH: Float, waterH: Float, scale: Float) {
        val pBase = 60f * scale // Max triangle width at base
        val pressureColor = Color.parseColor("#EF5350")
        val pPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pressureColor
            style = Paint.Style.FILL
            alpha = 40
        }
        val pStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pressureColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Left wall pressure triangle
        val leftPath = Path().apply {
            moveTo(x, y + drawH - waterH)
            lineTo(x - pBase, y + drawH)
            lineTo(x, y + drawH)
            close()
        }
        canvas.drawPath(leftPath, pPaint)
        canvas.drawPath(leftPath, pStrokePaint)

        // Labels
        val labelPaint = Paint(dimensionPaint).apply { 
            color = pressureColor
            textSize = 18f 
        }
        canvas.drawText("P_w", x - pBase - 10f, y + drawH + 20f, labelPaint)
    }

    private fun drawCalculationsSummary(canvas: Canvas) {
        val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val summaryPaint = Paint(textPaint).apply {
            textSize = 20f
            color = if (isDark) Color.parseColor("#81C784") else Color.parseColor("#2E7D32")
        }

        val capacity = (tankLength / 1000.0) * (tankLength / 1000.0) * (waterDepth / 1000.0) // Assuming square for visualization
        val pressure = (waterDepth / 1000.0) * 10.0 // kPa (gamma_w * h)
        
        val summaryX = width - 450f
        val summaryY = height - 120f
        
        val economyStatus = if (isOptimal) "ECONOMICAL" else if (isSafe) "OVER-DESIGNED" else "INADEQUATE"
        val economyColor = if (isOptimal) (if (isDark) Color.parseColor("#81C784") else Color.parseColor("#2E7D32")) 
                          else if (isSafe) Color.parseColor("#FFA000") else Color.RED

        canvas.drawText("DESIGN METRICS:", summaryX, summaryY, summaryPaint.apply { isUnderlineText = true })
        canvas.drawText("• Capacity: ${"%.2f".format(capacity)} m³", summaryX, summaryY + 35f, summaryPaint.apply { isUnderlineText = false })
        canvas.drawText("• Hydro-Pressure: ${"%.1f".format(pressure)} kPa", summaryX, summaryY + 70f, summaryPaint)
        canvas.drawText("• Safety: ${if(isSafe) "SECURE ✓" else "CRITICAL ✗"}", summaryX, summaryY + 105f, summaryPaint.apply { 
            color = if(isSafe) summaryPaint.color else Color.RED 
        })
        canvas.drawText("• Economy: $economyStatus", summaryX, summaryY + 140f, summaryPaint.apply { 
            color = economyColor
            typeface = if(isOptimal) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        })
    }

    private fun drawEconomyBadge(canvas: Canvas) {
        if (!isSafe) return
        val badgeText = if (isOptimal) "OPTIMAL DESIGN" else "HIGH COST / WASTEFUL"
        val badgeColor = if (isOptimal) Color.parseColor("#2E7D32") else Color.parseColor("#FFA000")
        val badgePaint = Paint().apply { color = badgeColor; alpha = 50 }
        val textPaint = Paint(textPaint).apply { color = badgeColor; textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC) }
        
        val x = width - textPaint.measureText(badgeText) - 100f
        val y = 80f
        canvas.drawRoundRect(x - 20f, y - 40f, width - 40f, y + 20f, 10f, 10f, badgePaint)
        canvas.drawText(badgeText, x, y, textPaint)
    }

    private fun drawStructuralHatching(canvas: Canvas, outer: RectF, inner: RectF) {
        canvas.save()
        val path = Path().apply {
            addRect(outer, Path.Direction.CW)
            addRect(inner, Path.Direction.CCW)
        }
        canvas.clipPath(path)
        val step = 35f
        var i = outer.left - outer.height()
        while (i < outer.right) {
            canvas.drawLine(i, outer.top, i + outer.height(), outer.bottom, hatchPaint)
            i += step
        }
        canvas.restore()
    }

    private fun drawDimension(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, text: String, horizontal: Boolean) {
        canvas.drawLine(x1, y1, x2, y2, dimensionPaint)
        val tick = 15f
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

    private fun drawHeaderStatus(canvas: Canvas) {
        val statusText = if (isSafe) "STRUCTURAL SAFE ✓" else "UNSAFE DESIGN ✗"
        val statusColor = if (isSafe) Color.parseColor("#2E7D32") else Color.RED
        val badgePaint = Paint().apply { color = statusColor; alpha = 30 }
        val textStatusPaint = Paint(textPaint).apply { color = statusColor; textSize = 28f }
        
        val rect = RectF(40f, 40f, 40f + textStatusPaint.measureText(statusText) + 40f, 100f)
        canvas.drawRoundRect(rect, 8f, 8f, badgePaint)
        canvas.drawText(statusText, 60f, 85f, textStatusPaint)

        if (rebarText.isNotEmpty()) {
            val subTextPaint = Paint(textPaint).apply { 
                textSize = 22f
                color = if ((context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) 
                    Color.LTGRAY else Color.DKGRAY 
            }
            canvas.drawText("Rebar: $rebarText | t=${thickness.toInt()}mm", 40f, 145f, subTextPaint)
        }
    }

    fun update(l: Float, h: Float, t: Float, bt: Float, wd: Float, type: CalculatorEngine.TankType, rebarText: String = "", baseRebar: String = "", safe: Boolean = true, optimal: Boolean = true) {
        this.tankLength = l
        this.tankHeight = h
        this.thickness = t
        this.baseThickness = bt
        this.waterDepth = wd
        this.tankType = type
        this.rebarText = rebarText
        this.baseRebarText = baseRebar
        this.isSafe = safe
        this.isOptimal = optimal
        invalidate()
    }
    
    fun setDetails(l: Float, w_m: Float, h: Float, wallT: Float, baseT: Float) {
        this.tankLength = l * 1000f
        this.tankHeight = h * 1000f
        this.thickness = wallT
        this.baseThickness = baseT
        this.waterDepth = h * 0.9f * 1000f
        invalidate()
    }
}
