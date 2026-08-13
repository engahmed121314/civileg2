package com.civileg.app.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility helper for manual PDF generation using android.graphics.pdf.PdfDocument
 * Handles Arabic shaping, RTL, multi-page management, and basic table drawing.
 */
class PdfLayoutHelper(private val context: Context) {

    companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
    }

    var currentY = MARGIN
    var currentPageNum = 1
    var currentDocument: PdfDocument? = null
    var currentPage: PdfDocument.Page? = null

    // Professional Palette
    val colorPrimary = Color.parseColor("#1565C0")
    val colorSecondary = Color.parseColor("#455A64")
    val colorAccent = Color.parseColor("#FF9800")
    val colorSuccess = Color.parseColor("#2E7D32")
    val colorError = Color.parseColor("#C62828")
    val colorText = Color.parseColor("#212121")
    val colorGray = Color.parseColor("#757575")
    val colorLightGray = Color.parseColor("#F5F5F5")

    fun startNewDocument(document: PdfDocument) {
        currentDocument = document
        currentPageNum = 1
        currentY = MARGIN
    }

    fun startNewPage() {
        finishCurrentPage()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNum).create()
        currentPage = currentDocument?.startPage(pageInfo)
        currentY = MARGIN + 20f // Top padding
        drawPageBorder(currentPage!!.canvas)
        drawPageHeader(currentPage!!.canvas)
    }

    fun finishCurrentPage() {
        currentPage?.let {
            drawPageFooter(it.canvas, currentPageNum)
            currentDocument?.finishPage(it)
            currentPageNum++
        }
    }

    fun checkNewPage(requiredHeight: Float): Canvas {
        if (currentPage == null || currentY + requiredHeight > PAGE_HEIGHT - MARGIN - 40f) {
            startNewPage()
        }
        return currentPage!!.canvas
    }

    private fun getArabicTypeface(): Typeface {
        return ArabicFontProvider.getArabicTypeface(context)
    }

    fun isArabic(text: String): Boolean = ArabicFontProvider.containsArabic(text)

    fun drawText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, width: Float = (PAGE_WIDTH - 2 * MARGIN)) {
        if (text.isEmpty()) return
        
        val workingPaint = Paint(paint)
        if (isArabic(text)) {
            workingPaint.typeface = getArabicTypeface()
        }
        
        val textPaint = TextPaint(workingPaint)
        val alignment = when (paint.textAlign) {
            Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            else -> if (isArabic(text)) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        }
        
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width.toInt())
            .setAlignment(alignment)
            .setLineSpacing(0f, 1.1f)
            .build()
        
        canvas.save()
        val tx = when (paint.textAlign) {
            Paint.Align.RIGHT -> x - width
            Paint.Align.CENTER -> x - width / 2f
            else -> x
        }
        canvas.translate(tx, y)
        staticLayout.draw(canvas)
        canvas.restore()
        
        // Note: This helper doesn't automatically increment currentY to allow manual control
    }

    fun drawInfoRow(canvas: Canvas, label: String, value: String, y: Float, labelPaint: Paint, valuePaint: Paint) {
        val isAr = isArabic(label) || isArabic(value)
        if (isAr) {
            labelPaint.textAlign = Paint.Align.RIGHT
            drawText(canvas, label + ":", (PAGE_WIDTH - MARGIN), y, labelPaint)
            valuePaint.textAlign = Paint.Align.LEFT
            drawText(canvas, value, MARGIN, y, valuePaint)
        } else {
            labelPaint.textAlign = Paint.Align.LEFT
            drawText(canvas, label + ":", MARGIN, y, labelPaint)
            valuePaint.textAlign = Paint.Align.RIGHT
            drawText(canvas, value, (PAGE_WIDTH - MARGIN), y, valuePaint)
        }
    }

    private fun drawPageBorder(canvas: Canvas) {
        val borderPaint = Paint().apply {
            color = colorPrimary
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        canvas.drawRect(MARGIN - 10f, MARGIN - 10f, PAGE_WIDTH - MARGIN + 10f, PAGE_HEIGHT - MARGIN + 10f, borderPaint)
    }

    private fun drawPageHeader(canvas: Canvas) {
        val p = Paint().apply {
            color = colorPrimary
            textSize = 10f
            isFakeBoldText = true
        }
        drawText(canvas, "Civil Engineer Pro", MARGIN, MARGIN - 5f, p)
        
        p.isFakeBoldText = false
        p.color = colorGray
        p.textAlign = Paint.Align.RIGHT
        drawText(canvas, "Technical Design & Feasibility Report", (PAGE_WIDTH - MARGIN), MARGIN - 5f, p)
        
        canvas.drawLine(MARGIN, MARGIN + 5f, PAGE_WIDTH - MARGIN, MARGIN + 5f, Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f })
    }

    private fun drawPageFooter(canvas: Canvas, pageNum: Int) {
        val p = Paint().apply {
            color = colorGray
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        drawText(canvas, "Page $pageNum", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN + 25f, p)
        
        p.textAlign = Paint.Align.LEFT
        drawText(canvas, SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date()), MARGIN, PAGE_HEIGHT - MARGIN + 25f, p)
        
        p.textAlign = Paint.Align.RIGHT
        drawText(canvas, "Generated by CivilEG Engine", (PAGE_WIDTH - MARGIN), PAGE_HEIGHT - MARGIN + 25f, p)
    }
}
