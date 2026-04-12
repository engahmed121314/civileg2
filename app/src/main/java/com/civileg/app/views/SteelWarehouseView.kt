package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import com.civileg.app.utils.CalculatorEngine

class SteelWarehouseView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var result: CalculatorEngine.SteelWarehouseResult? = null
    private val framePath = Path()

    private val linePaint = Paint().apply {
        color = "#000080".toColorInt()
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val dimensionPaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    @Suppress("unused")
    fun setDesignResult(res: CalculatorEngine.SteelWarehouseResult) {
        this.result = res
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val res = result ?: return

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 100f

        // حساب الإحداثيات النسبية (Scaling)
        val startX = padding
        val endX = w - padding
        val groundY = h - padding
        val eaveY = (groundY - (res.eaveHeight * 20.0)).toFloat()
        val ridgeY = (groundY - (res.totalHeight * 20.0)).toFloat()
        val centerX = w / 2

        // رسم الأرضية
        canvas.drawLine(startX - 20f, groundY, endX + 20f, groundY, linePaint)

        // رسم الإطار الرئيسي (Main Frame)
        framePath.reset()
        framePath.moveTo(startX, groundY) // قاعدة العمود الأيسر
        framePath.lineTo(startX, eaveY)   // قمة العمود الأيسر
        framePath.lineTo(centerX, ridgeY) // قمة الجمالون (Ridge)
        framePath.lineTo(endX, eaveY)     // قمة العمود الأيمن
        framePath.lineTo(endX, groundY)   // قاعدة العمود الأيمن
        canvas.drawPath(framePath, linePaint)

        // رسم المدادات (Purlins) - تمثيل تخطيطي
        for (i in 1..4) {
            val ratio = i / 5f
            val px = startX + (centerX - startX) * ratio
            val py = eaveY + (ridgeY - eaveY) * ratio
            canvas.drawCircle(px, py, 5f, linePaint) // نقاط المدادات
        }

        // كتابة البيانات على الرسم
        canvas.drawText("Span: ${res.span} ft", centerX, groundY + 40f, textPaint)
        canvas.drawText("Eave: ${res.eaveHeight} ft", startX - 40f, (groundY + eaveY) / 2, textPaint)
        canvas.drawText(res.columnSection, startX + 60f, (groundY + eaveY) / 2, textPaint)
        canvas.drawText(res.rafterSection, (startX + centerX) / 2, (eaveY + ridgeY) / 2 - 20f, textPaint)

        // رسم تفصيلي للوصلة (Connection Detail representation)
        drawConnectionDetail(canvas, startX, eaveY)
    }

    private fun drawConnectionDetail(canvas: Canvas, x: Float, y: Float) {
        // رسم "الزوم" على الوصلة
        canvas.drawCircle(x, y, 30f, dimensionPaint)
        canvas.drawText("Detail 1", x, y - 40f, textPaint)
    }
}
