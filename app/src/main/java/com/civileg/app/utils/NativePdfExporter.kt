package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.civileg.app.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NativePdfExporter — Android-native PDF generator using android.graphics.pdf.PdfDocument.
 *
 * ********************************************************************************
 * CRITICAL FIX (2026-07-27 v2): StaticLayout for BIDI + Shaping
 * ********************************************************************************
 * The previous version used `Canvas.drawText(text, x, y, paint)` for text rendering.
 * This was the ROOT CAUSE of the "squares and chopped Arabic characters" bug:
 *
 *   - Canvas.drawText DOES shape Arabic letters via HarfBuzz (so each letter is OK)
 *   - BUT it does NOT perform BIDI reordering → Arabic text drawn LEFT-TO-RIGHT
 *   - The joining context is WRONG (Canvas thinks prev char is on the left, but Arabic
 *     expects it on the right) → letters get wrong initial/medial/final forms
 *   - Visually: Arabic appears as disconnected shapes / "encrypted / squares"
 *
 * FIX: Use `StaticLayout` for ALL text drawing. StaticLayout uses Android's full
 * text-rendering pipeline (HarfBuzz + Bidi + LineBreaker) and properly handles:
 *   - RTL reordering (Arabic reads right-to-left)
 *   - Contextual joining (correct initial/medial/final/isolated letter forms)
 *   - Mixed-direction text (Arabic + Latin + numbers in same line)
 *
 * This produces correctly-rendered Arabic on EVERY Android device.
 *
 * ********************************************************************************
 * Bilingual Report Strategy
 * ********************************************************************************
 * Per user requirement: When language is "ar", report shows Arabic for descriptive
 * labels, English for engineering symbols (Mu, As, fy, fcu, V, M, etc.) and drawings.
 * When language is "en", report is fully English.
 */
class NativePdfExporter(private val context: Context) {

    companion object {
        private const val TAG = "NativePdfExporter"
        private const val PAGE_WIDTH = 595   // A4 width in points (1/72")
        private const val PAGE_HEIGHT = 842  // A4 height in points
        private const val MARGIN = 36f       // 0.5" margin
    }

    // ── Color palette (RGB ints) ─────────────────────────────────────────────
    private val primaryColor = Color.rgb(21, 101, 192)
    private val secondaryColor = Color.rgb(55, 71, 79)
    private val successColor = Color.rgb(46, 125, 50)
    private val errorColor = Color.rgb(198, 40, 40)
    private val warningColor = Color.rgb(245, 124, 0)
    private val lightBg = Color.rgb(232, 245, 253)
    private val headerBg = Color.rgb(21, 101, 192)
    private val rowAltBg = Color.rgb(245, 245, 245)
    private val tableBorder = Color.rgb(150, 150, 150)
    private val textColor = Color.rgb(33, 33, 33)
    private val grayText = Color.rgb(117, 117, 117)

    // ── Typefaces (cached — safe, not tied to a specific PdfDocument) ────────
    private val arabicRegular: Typeface by lazy {
        try { Typeface.createFromAsset(context.assets, "fonts/NotoNaskhArabic-Regular.ttf") }
        catch (e: Exception) { Log.w(TAG, "Arabic regular font load failed: ${e.message}"); Typeface.DEFAULT }
    }
    private val arabicBold: Typeface by lazy {
        try { Typeface.createFromAsset(context.assets, "fonts/NotoNaskhArabic-Bold.ttf") }
        catch (e: Exception) { Log.w(TAG, "Arabic bold font load failed: ${e.message}"); Typeface.DEFAULT_BOLD }
    }
    private val latinRegular: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
    private val latinBold: Typeface by lazy { Typeface.create("sans-serif", Typeface.BOLD) }

    // ── Layout state (cursor) ────────────────────────────────────────────────
    private var currentY: Float = 0f
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var document: PdfDocument? = null
    private var pageNumber: Int = 0

    // ── Public API ───────────────────────────────────────────────────────────

    fun generateReport(
        title: String,
        subtitle: String = "",
        designType: String = "",
        inputs: Map<String, String> = emptyMap(),
        results: Map<String, String> = emptyMap(),
        safetyChecks: List<SafetyCheck> = emptyList(),
        isSafe: Boolean = true,
        drawingBitmap: Bitmap? = null,
        outputPath: String
    ): File? {
        return try {
            val doc = PdfDocument()
            document = doc
            startNewPage(doc)

            drawHeader()
            drawTitleBlock(title, subtitle, designType, isSafe)

            if (inputs.isNotEmpty() || results.isNotEmpty()) {
                drawSectionTitle(getLocalized("بيانات التصميم", "DESIGN DATA"))
                drawKeyValueTable(inputs, results)
            }

            if (drawingBitmap != null) {
                drawSectionTitle(getLocalized("رسم تفصيلي", "DETAIL DRAWING"))
                drawBitmap(drawingBitmap)
            }

            if (safetyChecks.isNotEmpty()) {
                drawSectionTitle(getLocalized("تحققات الأمان", "SAFETY VERIFICATIONS"))
                drawSafetyTable(safetyChecks)
            }

            drawFooter()
            finishPage()

            val file = File(outputPath)
            FileOutputStream(file).use { out -> doc.writeTo(out) }
            doc.close()
            Log.i(TAG, "PDF generated successfully: ${file.absolutePath} (${file.length()} bytes)")
            file
        } catch (e: Exception) {
            Log.e(TAG, "PDF generation failed: ${e.message}", e)
            try { document?.close() } catch (_: Exception) {}
            null
        }
    }

    fun generateCalculationReport(
        title: String,
        details: Map<String, String>,
        outputPath: String
    ): File? {
        return try {
            val doc = PdfDocument()
            document = doc
            startNewPage(doc)

            drawHeader()
            drawTitleBlock(title, "", "", true)

            if (details.isNotEmpty()) {
                drawSectionTitle(getLocalized("التفاصيل", "DETAILS"))
                drawKeyValueTable(details, emptyMap())
            }

            drawFooter()
            finishPage()

            val file = File(outputPath)
            FileOutputStream(file).use { out -> doc.writeTo(out) }
            doc.close()
            file
        } catch (e: Exception) {
            Log.e(TAG, "Calc report generation failed: ${e.message}", e)
            try { document?.close() } catch (_: Exception) {}
            null
        }
    }

    // ── Internal: page management ────────────────────────────────────────────

    private fun startNewPage(doc: PdfDocument) {
        finishPage()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create()
        val newPage = doc.startPage(pageInfo)
        page = newPage
        canvas = newPage.canvas
        currentY = MARGIN
    }

    private fun finishPage() {
        page?.let { document?.finishPage(it) }
        page = null
        canvas = null
    }

    private fun ensureSpace(required: Float) {
        if (currentY + required > PAGE_HEIGHT - MARGIN - 20f) {
            drawFooter()
            startNewPage(document!!)
        }
    }

    // ── Internal: drawing primitives ─────────────────────────────────────────
    //
    // CRITICAL: All text drawing uses StaticLayout for proper Arabic BIDI
    // reordering + HarfBuzz shaping. Canvas.drawText alone does NOT do BIDI.

    /**
     * Build a TextPaint with the correct Typeface based on text content.
     * - Arabic text → NotoNaskhArabic font (has full Arabic + limited Latin)
     * - Latin/numeric text → Android sans-serif (full Latin + numbers)
     */
    private fun buildPaint(
        fontSize: Float,
        bold: Boolean,
        color: Int,
        text: String
    ): TextPaint {
        val hasArabic = ArabicFontProvider.containsArabic(text)
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = fontSize
            this.typeface = when {
                hasArabic && bold -> arabicBold
                hasArabic -> arabicRegular
                bold -> latinBold
                else -> latinRegular
            }
            isFakeBoldText = false
        }
    }

    /**
     * Draw text using StaticLayout for proper BIDI + shaping.
     *
     * @param text The text to draw
     * @param x The x anchor position
     * @param y The TOP y position (text will be drawn below this)
     * @param fontSize Font size in points
     * @param bold Bold weight?
     * @param color Text color
     * @param align LEFT / CENTER / RIGHT — alignment relative to x
     */
    private fun drawText(
        text: String,
        x: Float,
        y: Float,
        fontSize: Float = 10f,
        bold: Boolean = false,
        color: Int = textColor,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        val c = canvas ?: return
        if (text.isEmpty()) return

        val paint = buildPaint(fontSize, bold, color, text)
        val hasArabic = ArabicFontProvider.containsArabic(text)

        // Compute layout width — large enough for the text on a single line
        val textWidth = paint.measureText(text).coerceAtLeast(1f)
        // Cap at page width to avoid layout issues
        val layoutWidth = textWidth.toInt().coerceAtMost((PAGE_WIDTH - 2 * MARGIN).toInt())

        val staticLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, layoutWidth)
            .setAlignment(
                when (align) {
                    Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                    Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> if (hasArabic) Layout.Alignment.ALIGN_OPPOSITE
                            else Layout.Alignment.ALIGN_NORMAL
                }
            )
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        // Compute x position based on alignment
        val drawX = when (align) {
            Paint.Align.CENTER -> x - staticLayout.width / 2f
            Paint.Align.RIGHT -> x - staticLayout.width
            else -> if (hasArabic) x - staticLayout.width else x
        }

        c.save()
        // y is the BASELINE position in the original code; StaticLayout draws from top
        // so we offset by the ascent to maintain visual position consistency
        c.translate(drawX, y - paint.ascent() - paint.descent() / 2f)
        staticLayout.draw(c)
        c.restore()
    }

    /**
     * Draw a line of text in a table cell, vertically centered in the row.
     */
    private fun drawCellText(
        text: String,
        cellLeft: Float,
        cellTop: Float,
        cellWidth: Float,
        cellHeight: Float,
        fontSize: Float = 9f,
        bold: Boolean = false,
        color: Int = textColor,
        align: Paint.Align = Paint.Align.CENTER
    ) {
        val c = canvas ?: return
        if (text.isEmpty()) return

        val paint = buildPaint(fontSize, bold, color, text)
        val hasArabic = ArabicFontProvider.containsArabic(text)

        // Layout width = cell width minus padding
        val layoutWidth = (cellWidth - 8f).toInt().coerceAtLeast(1)

        val staticLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, layoutWidth)
            .setAlignment(
                when (align) {
                    Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                    Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> if (hasArabic) Layout.Alignment.ALIGN_OPPOSITE
                            else Layout.Alignment.ALIGN_NORMAL
                }
            )
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        val drawX = when (align) {
            Paint.Align.CENTER -> cellLeft + (cellWidth - staticLayout.width) / 2f
            Paint.Align.RIGHT -> cellLeft + cellWidth - staticLayout.width - 4f
            else -> if (hasArabic) cellLeft + cellWidth - staticLayout.width - 4f
                    else cellLeft + 4f
        }

        // Vertically center: cellTop + (cellHeight - textHeight) / 2
        val textHeight = staticLayout.height
        val drawY = cellTop + (cellHeight - textHeight) / 2f

        c.save()
        c.translate(drawX, drawY)
        staticLayout.draw(c)
        c.restore()
    }

    private fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int = textColor, width: Float = 1f) {
        val c = canvas ?: return
        val paint = Paint().apply {
            this.color = color
            this.style = Paint.Style.STROKE
            this.strokeWidth = width
            this.isAntiAlias = true
        }
        c.drawLine(x1, y1, x2, y2, paint)
    }

    private fun drawRect(x: Float, y: Float, w: Float, h: Float, fill: Int? = null, stroke: Int? = null, strokeWidth: Float = 1f) {
        val c = canvas ?: return
        val rect = RectF(x, y, x + w, y + h)
        fill?.let {
            val p = Paint().apply {
                color = it
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            c.drawRect(rect, p)
        }
        stroke?.let {
            val p = Paint().apply {
                color = it
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }
            c.drawRect(rect, p)
        }
    }

    // ── Internal: structured components ──────────────────────────────────────

    private fun drawHeader() {
        val appName = context.getString(R.string.app_name)
        drawText(appName, MARGIN, currentY + 14f, fontSize = 22f, bold = true, color = primaryColor)

        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
        drawText(dateStr, PAGE_WIDTH - MARGIN, currentY + 14f, fontSize = 10f, color = grayText, align = Paint.Align.RIGHT)

        currentY += 32f
        drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, primaryColor, 2f)
        currentY += 12f
    }

    private fun drawTitleBlock(title: String, subtitle: String, designType: String, isSafe: Boolean) {
        ensureSpace(120f)

        drawText(title, PAGE_WIDTH / 2f, currentY + 14f, fontSize = 20f, bold = true, color = primaryColor, align = Paint.Align.CENTER)
        currentY += 28f

        if (subtitle.isNotEmpty()) {
            drawText(subtitle, PAGE_WIDTH / 2f, currentY, fontSize = 11f, color = secondaryColor, align = Paint.Align.CENTER)
            currentY += 16f
        }

        if (designType.isNotEmpty()) {
            val chipWidth = 220f
            val chipHeight = 22f
            val chipX = (PAGE_WIDTH - chipWidth) / 2f
            drawRect(chipX, currentY, chipWidth, chipHeight, fill = lightBg, stroke = primaryColor, strokeWidth = 1f)
            drawText(designType, PAGE_WIDTH / 2f, currentY + 11f, fontSize = 10f, bold = true, color = primaryColor, align = Paint.Align.CENTER)
            currentY += chipHeight + 8f
        }

        val statusText = if (isSafe) getLocalized("الحالة: آمن - مطابق للكود", "STATUS: SAFE - Code Compliant")
        else getLocalized("الحالة: غير آمن - يحتاج مراجعة", "STATUS: UNSAFE - Review Required")
        val statusColor = if (isSafe) successColor else errorColor
        val bannerWidth = PAGE_WIDTH - 2 * MARGIN
        drawRect(MARGIN, currentY, bannerWidth, 28f, fill = statusColor)
        drawText(statusText, PAGE_WIDTH / 2f, currentY + 14f, fontSize = 12f, bold = true, color = Color.WHITE, align = Paint.Align.CENTER)
        currentY += 38f
    }

    private fun drawSectionTitle(title: String) {
        ensureSpace(40f)
        currentY += 8f
        drawText(title, MARGIN, currentY + 7f, fontSize = 13f, bold = true, color = primaryColor)
        currentY += 18f
        drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, tableBorder, 0.5f)
        currentY += 8f
    }

    private fun drawKeyValueTable(inputs: Map<String, String>, results: Map<String, String>) {
        val allEntries = inputs.entries.toList() + results.entries.toList()
        if (allEntries.isEmpty()) return

        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val labelWidth = contentWidth * 0.55f
        val valueWidth = contentWidth * 0.45f
        val rowHeight = 22f

        // Header
        drawRect(MARGIN, currentY, labelWidth, rowHeight, fill = headerBg)
        drawRect(MARGIN + labelWidth, currentY, valueWidth, rowHeight, fill = headerBg)
        drawCellText(getLocalized("البند", "Parameter"), MARGIN, currentY, labelWidth, rowHeight, fontSize = 10f, bold = true, color = Color.WHITE)
        drawCellText(getLocalized("القيمة", "Value"), MARGIN + labelWidth, currentY, valueWidth, rowHeight, fontSize = 10f, bold = true, color = Color.WHITE)
        currentY += rowHeight

        // Rows
        allEntries.forEachIndexed { idx, (label, value) ->
            ensureSpace(rowHeight + 4f)
            val bg = if (idx % 2 == 0) Color.WHITE else rowAltBg
            drawRect(MARGIN, currentY, labelWidth, rowHeight, fill = bg)
            drawRect(MARGIN + labelWidth, currentY, valueWidth, rowHeight, fill = bg)
            drawRect(MARGIN, currentY, contentWidth, rowHeight, stroke = tableBorder, strokeWidth = 0.5f)
            drawLine(MARGIN + labelWidth, currentY, MARGIN + labelWidth, currentY + rowHeight, tableBorder, 0.5f)

            // Label: Arabic → right-aligned, English → left-aligned
            val labelArabic = ArabicFontProvider.containsArabic(label)
            if (labelArabic) {
                drawCellText(label, MARGIN, currentY, labelWidth, rowHeight, fontSize = 9f, bold = true, color = textColor, align = Paint.Align.RIGHT)
            } else {
                drawCellText(label, MARGIN, currentY, labelWidth, rowHeight, fontSize = 9f, bold = true, color = textColor, align = Paint.Align.LEFT)
            }

            // Value: Arabic → right-aligned, English → left-aligned
            val valueArabic = ArabicFontProvider.containsArabic(value)
            if (valueArabic) {
                drawCellText(value, MARGIN + labelWidth, currentY, valueWidth, rowHeight, fontSize = 9f, color = secondaryColor, bold = true, align = Paint.Align.RIGHT)
            } else {
                drawCellText(value, MARGIN + labelWidth, currentY, valueWidth, rowHeight, fontSize = 9f, color = secondaryColor, bold = true, align = Paint.Align.LEFT)
            }

            currentY += rowHeight
        }
        currentY += 10f
    }

    private fun drawSafetyTable(checks: List<SafetyCheck>) {
        if (checks.isEmpty()) return

        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val colWidths = floatArrayOf(
            contentWidth * 0.35f,
            contentWidth * 0.20f,
            contentWidth * 0.20f,
            contentWidth * 0.10f,
            contentWidth * 0.15f
        )
        val rowHeight = 24f

        val headers = listOf(
            getLocalized("التحقق", "Check"),
            getLocalized("المحسوب", "Calculated"),
            getLocalized("الحد", "Limit"),
            getLocalized("الوحدة", "Unit"),
            getLocalized("النتيجة", "Result")
        )
        var x = MARGIN
        headers.forEachIndexed { idx, h ->
            drawRect(x, currentY, colWidths[idx], rowHeight, fill = headerBg)
            drawCellText(h, x, currentY, colWidths[idx], rowHeight, fontSize = 9f, bold = true, color = Color.WHITE)
            x += colWidths[idx]
        }
        currentY += rowHeight

        checks.forEachIndexed { idx, check ->
            ensureSpace(rowHeight + 4f)
            val bg = if (idx % 2 == 0) Color.WHITE else rowAltBg
            x = MARGIN
            val cells = listOf(
                check.name,
                formatNumber(check.calculated),
                formatNumber(check.limit),
                check.unit,
                if (check.passed) getLocalized("آمن", "SAFE") else getLocalized("غير آمن", "FAIL")
            )
            cells.forEachIndexed { ci, cell ->
                drawRect(x, currentY, colWidths[ci], rowHeight, fill = bg)
                val cellColor = if (ci == 4) (if (check.passed) successColor else errorColor) else textColor
                drawCellText(cell, x, currentY, colWidths[ci], rowHeight, fontSize = 9f, color = cellColor, bold = ci == 4)
                x += colWidths[ci]
            }
            drawRect(MARGIN, currentY, contentWidth, rowHeight, stroke = tableBorder, strokeWidth = 0.5f)
            currentY += rowHeight
        }
        currentY += 10f
    }

    private fun drawBitmap(bitmap: Bitmap) {
        val c = canvas ?: return
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val maxDrawingHeight = 320f

        val scale = minOf(contentWidth / bitmap.width, maxDrawingHeight / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val drawX = MARGIN + (contentWidth - drawWidth) / 2f

        ensureSpace(drawHeight + 20f)

        drawRect(drawX - 2f, currentY - 2f, drawWidth + 4f, drawHeight + 4f, stroke = tableBorder, strokeWidth = 1f)

        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val dstRect = RectF(drawX, currentY, drawX + drawWidth, currentY + drawHeight)
        c.drawBitmap(bitmap, srcRect, dstRect, null)

        currentY += drawHeight + 16f
    }

    private fun drawFooter() {
        val footerY = PAGE_HEIGHT - 24f
        drawLine(MARGIN, footerY, PAGE_WIDTH - MARGIN, footerY, tableBorder, 0.5f)

        val footerText = "Civil EG - ${getLocalized("تقرير تصميم إنشائي", "Structural Design Report")}"
        drawText(footerText, MARGIN, footerY + 7f, fontSize = 8f, color = grayText)

        val pageNumText = "${getLocalized("صفحة", "Page")} $pageNumber"
        drawText(pageNumText, PAGE_WIDTH - MARGIN, footerY + 7f, fontSize = 8f, color = grayText, align = Paint.Align.RIGHT)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun getLocalized(ar: String, en: String): String {
        return if (LocaleHelper.getLocale(context) == "ar") ar else en
    }

    private fun formatNumber(v: Double): String {
        return if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v)
    }

    /** Data class for safety check rows. */
    data class SafetyCheck(
        val name: String,
        val calculated: Double,
        val limit: Double,
        val unit: String,
        val passed: Boolean
    )
}
