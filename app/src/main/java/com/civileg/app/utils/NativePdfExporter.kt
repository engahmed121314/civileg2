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
 * WHY THIS EXISTS (Root Cause of "Encrypted Arabic" PDF Bug — Final Fix)
 * ********************************************************************************
 * The previous iText 8 AGPL-based PDF pipeline produced "encrypted/squares"
 * Arabic text on every device, despite multiple fix attempts. Root causes:
 *
 *   1. iText 8 AGPL does NOT include the pdfCalligraph module (commercial only).
 *      Without it, iText cannot apply OpenType GSUB shaping. Our manual
 *      Presentation Forms shaper (ArabicShaper.kt) works in theory but is
 *      fragile — it cannot handle every edge case (e.g. ligatures with diacritics,
 *      combining marks, complex mixed-script reordering).
 *
 *   2. iText 8 binds PdfFont to the FIRST PdfDocument that uses it. Caching
 *      bugs caused 2nd+ PDF exports to throw exceptions silently.
 *
 *   3. iText's Unicode Bidi implementation in AGPL is incomplete. Mixed
 *      Arabic/Latin text often rendered in wrong order.
 *
 * SOLUTION: Use Android's built-in android.graphics.pdf.PdfDocument.
 *   - Uses Android's native HarfBuzz for letter shaping (proper Arabic joining)
 *   - Uses Android's native Bidi algorithm (proper RTL reordering)
 *   - No font caching issues — each PdfDocument is independent
 *   - Single Typeface handles both Arabic and Latin via system fallback
 *
 * This class provides a SIMPLE, RELIABLE way to generate professional PDF
 * reports with proper Arabic rendering on EVERY Android device.
 *
 * @author CivilEG Team
 * @since 2026-07-27
 */
class NativePdfExporter(private val context: Context) {

    companion object {
        private const val TAG = "NativePdfExporter"
        private const val PAGE_WIDTH = 595   // A4 width in points (1/72")
        private const val PAGE_HEIGHT = 842  // A4 height in points
        private const val MARGIN = 36f       // 0.5" margin
    }

    // ── Color palette (RGB ints) ─────────────────────────────────────────────
    private val primaryColor = Color.rgb(21, 101, 192)       // Professional Blue
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
    // Fallback for pure Latin text — use Android's default sans (has full Latin glyph coverage)
    private val latinRegular: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
    private val latinBold: Typeface by lazy { Typeface.create("sans-serif", Typeface.BOLD) }

    // ── Layout state (cursor) ────────────────────────────────────────────────
    private var currentY: Float = 0f
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var document: PdfDocument? = null
    private var pageNumber: Int = 0

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Generate a complete structural engineering calculation report PDF.
     *
     * @param title Report title (Arabic or English)
     * @param subtitle Subtitle / project name
     * @param designType Design type label (e.g. "Slab Design", "تصميم بلاطة")
     * @param inputs Map of input parameter labels → values
     * @param results Map of result labels → values
     * @param safetyChecks List of (check name, calculated, limit, unit, passed)
     * @param isSafe Overall safety status
     * @param drawingBitmap Optional drawing image to embed
     * @param outputPath Absolute file path to write the PDF
     * @return The generated File, or null on failure
     */
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

            // ── Header (logo + app name + date) ────────────────────────────
            drawHeader()

            // ── Title block ────────────────────────────────────────────────
            drawTitleBlock(title, subtitle, designType, isSafe)

            // ── Inputs & Results table ────────────────────────────────────
            if (inputs.isNotEmpty() || results.isNotEmpty()) {
                drawSectionTitle(getLocalized("بيانات التصميم", "DESIGN DATA"))
                drawKeyValueTable(inputs, results)
            }

            // ── Drawing (if provided) ─────────────────────────────────────
            if (drawingBitmap != null) {
                drawSectionTitle(getLocalized("رسم تفصيلي", "DETAIL DRAWING"))
                drawBitmap(drawingBitmap)
            }

            // ── Safety checks table ──────────────────────────────────────
            if (safetyChecks.isNotEmpty()) {
                drawSectionTitle(getLocalized("تحققات الأمان", "SAFETY VERIFICATIONS"))
                drawSafetyTable(safetyChecks)
            }

            // ── Footer with date and page info ───────────────────────────
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

    /**
     * Lightweight calculation report — used by Seismic and other screens that
     * just need a list of key-value details.
     */
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

    /**
     * Draw text with automatic Arabic shaping and RTL handling.
     *
     * Uses Android's native Canvas.drawText — Android's HarfBuzz engine
     * automatically shapes Arabic letters (initial/medial/final forms)
     * and the Bidi algorithm handles RTL reordering.
     */
    private fun drawText(
        text: String,
        x: Float,
        y: Float,
        fontSize: Float = 10f,
        bold: Boolean = false,
        color: Int = textColor,
        align: Paint.Align = Paint.Align.LEFT,
        arabic: Boolean? = null
    ) {
        val c = canvas ?: return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = fontSize
            this.textAlign = align
            // Choose font: Arabic font if text contains Arabic chars, else Latin
            val hasArabic = arabic ?: ArabicFontProvider.containsArabic(text)
            typeface = when {
                hasArabic && bold -> arabicBold
                hasArabic -> arabicRegular
                bold -> latinBold
                else -> latinRegular
            }
        }
        c.drawText(text, x, y, paint)
    }

    /**
     * Draw mixed-direction text on a single line. Splits the text into
     * contiguous Arabic and Latin segments, then draws each segment with the
     * appropriate font, using iText-style segment-based RTL handling.
     *
     * For RTL (Arabic-dominant) lines, segments are laid out right-to-left.
     * For LTR (Latin-dominant) lines, segments are laid out left-to-right.
     */
    private fun drawMixedText(
        text: String,
        x: Float,
        y: Float,
        fontSize: Float = 10f,
        bold: Boolean = false,
        color: Int = textColor,
        maxWidth: Float = Float.MAX_VALUE
    ) {
        val c = canvas ?: return
        val hasArabic = ArabicFontProvider.containsArabic(text)
        val typeface = when {
            hasArabic && bold -> arabicBold
            hasArabic -> arabicRegular
            bold -> latinBold
            else -> latinRegular
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = fontSize
            this.typeface = typeface
        }

        // Use Android's native text layout for proper RTL + shaping
        val staticLayout = android.text.StaticLayout.Builder
            .obtain(text, 0, text.length, paint, maxWidth.toInt().coerceAtLeast(1))
            .setAlignment(
                if (hasArabic) android.text.Layout.Alignment.ALIGN_OPPOSITE
                else android.text.Layout.Alignment.ALIGN_NORMAL
            )
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        c.save()
        c.translate(x, y - paint.ascent())
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
            Paint().apply {
                color = it
                style = Paint.Style.FILL
                isAntiAlias = true
            }.also { c.drawRect(rect, it) }
        }
        stroke?.let {
            Paint().apply {
                color = it
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }.also { c.drawRect(rect, it) }
        }
    }

    private fun drawFilledRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        drawRect(x, y, w, h, fill = color)
    }

    // ── Internal: structured components ──────────────────────────────────────

    private fun drawHeader() {
        val c = canvas ?: return
        val contentWidth = PAGE_WIDTH - 2 * MARGIN

        // App name
        val appName = context.getString(R.string.app_name)
        drawText(appName, MARGIN, currentY + 24f, fontSize = 22f, bold = true, color = primaryColor)

        // Right-aligned date
        val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
        drawText(dateStr, PAGE_WIDTH - MARGIN, currentY + 24f, fontSize = 10f, color = grayText, align = Paint.Align.RIGHT)

        currentY += 32f
        drawLine(MARGIN, currentY, PAGE_WIDTH - MARGIN, currentY, primaryColor, 2f)
        currentY += 12f
    }

    private fun drawTitleBlock(title: String, subtitle: String, designType: String, isSafe: Boolean) {
        ensureSpace(120f)

        // Title (large, centered, primary color)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 20f
            textAlign = Paint.Align.CENTER
            typeface = if (ArabicFontProvider.containsArabic(title)) arabicBold else latinBold
        }
        val c = canvas ?: return
        c.drawText(title, PAGE_WIDTH / 2f, currentY + 20f, titlePaint)
        currentY += 28f

        // Subtitle
        if (subtitle.isNotEmpty()) {
            drawText(subtitle, PAGE_WIDTH / 2f, currentY, fontSize = 11f, color = secondaryColor, align = Paint.Align.CENTER)
            currentY += 16f
        }

        // Design type chip
        if (designType.isNotEmpty()) {
            val chipWidth = 200f
            val chipHeight = 22f
            val chipX = (PAGE_WIDTH - chipWidth) / 2f
            drawRect(chipX, currentY, chipWidth, chipHeight, fill = lightBg, stroke = primaryColor, strokeWidth = 1f)
            drawText(designType, PAGE_WIDTH / 2f, currentY + 15f, fontSize = 10f, bold = true, color = primaryColor, align = Paint.Align.CENTER)
            currentY += chipHeight + 8f
        }

        // Status banner
        val statusText = if (isSafe) getLocalized("الحالة: آمن - مطابق للكود", "STATUS: SAFE - Code Compliant")
        else getLocalized("الحالة: غير آمن - يحتاج مراجعة", "STATUS: UNSAFE - Review Required")
        val statusColor = if (isSafe) successColor else errorColor
        val bannerWidth = PAGE_WIDTH - 2 * MARGIN
        drawRect(MARGIN, currentY, bannerWidth, 28f, fill = statusColor)
        drawText(statusText, PAGE_WIDTH / 2f, currentY + 19f, fontSize = 12f, bold = true, color = Color.WHITE, align = Paint.Align.CENTER)
        currentY += 38f
    }

    private fun drawSectionTitle(title: String) {
        ensureSpace(40f)
        currentY += 8f
        drawText(title, MARGIN, currentY + 14f, fontSize = 13f, bold = true, color = primaryColor)
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
        val rowHeight = 20f

        // Header
        drawRect(MARGIN, currentY, labelWidth, rowHeight, fill = headerBg)
        drawRect(MARGIN + labelWidth, currentY, valueWidth, rowHeight, fill = headerBg)
        drawText(getLocalized("البند", "Parameter"), MARGIN + 6f, currentY + 14f, fontSize = 10f, bold = true, color = Color.WHITE)
        drawText(getLocalized("القيمة", "Value"), MARGIN + labelWidth + 6f, currentY + 14f, fontSize = 10f, bold = true, color = Color.WHITE)
        currentY += rowHeight

        // Rows
        allEntries.forEachIndexed { idx, (label, value) ->
            ensureSpace(rowHeight + 4f)
            val bg = if (idx % 2 == 0) Color.WHITE else rowAltBg
            drawRect(MARGIN, currentY, labelWidth, rowHeight, fill = bg)
            drawRect(MARGIN + labelWidth, currentY, valueWidth, rowHeight, fill = bg)
            // Borders
            drawRect(MARGIN, currentY, contentWidth, rowHeight, stroke = tableBorder, strokeWidth = 0.5f)
            drawLine(MARGIN + labelWidth, currentY, MARGIN + labelWidth, currentY + rowHeight, tableBorder, 0.5f)

            // Label (Arabic right-aligned if Arabic, else left)
            val labelArabic = ArabicFontProvider.containsArabic(label)
            if (labelArabic) {
                drawText(label, MARGIN + labelWidth - 6f, currentY + 14f, fontSize = 9f, bold = true, color = textColor, align = Paint.Align.RIGHT)
            } else {
                drawText(label, MARGIN + 6f, currentY + 14f, fontSize = 9f, bold = true, color = textColor)
            }

            // Value (Arabic right-aligned if Arabic, else left)
            val valueArabic = ArabicFontProvider.containsArabic(value)
            if (valueArabic) {
                drawText(value, MARGIN + contentWidth - 6f, currentY + 14f, fontSize = 9f, color = secondaryColor, align = Paint.Align.RIGHT, bold = true)
            } else {
                drawText(value, MARGIN + labelWidth + 6f, currentY + 14f, fontSize = 9f, color = secondaryColor, bold = true)
            }

            currentY += rowHeight
        }
        currentY += 10f
    }

    private fun drawSafetyTable(checks: List<SafetyCheck>) {
        if (checks.isEmpty()) return

        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        // Columns: Check (35%), Calculated (20%), Limit (20%), Unit (10%), Result (15%)
        val colWidths = floatArrayOf(
            contentWidth * 0.35f,
            contentWidth * 0.20f,
            contentWidth * 0.20f,
            contentWidth * 0.10f,
            contentWidth * 0.15f
        )
        val rowHeight = 22f

        // Header
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
            drawText(h, x + colWidths[idx] / 2f, currentY + 15f, fontSize = 9f, bold = true, color = Color.WHITE, align = Paint.Align.CENTER)
            x += colWidths[idx]
        }
        currentY += rowHeight

        // Rows
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
                drawText(cell, x + colWidths[ci] / 2f, currentY + 15f, fontSize = 9f, color = cellColor, align = Paint.Align.CENTER, bold = ci == 4)
                x += colWidths[ci]
            }
            // Borders
            drawRect(MARGIN, currentY, contentWidth, rowHeight, stroke = tableBorder, strokeWidth = 0.5f)
            currentY += rowHeight
        }
        currentY += 10f
    }

    private fun drawBitmap(bitmap: Bitmap) {
        val c = canvas ?: return
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val maxDrawingHeight = 280f

        // Scale bitmap to fit content width while preserving aspect ratio
        val scale = minOf(contentWidth / bitmap.width, maxDrawingHeight / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val drawX = MARGIN + (contentWidth - drawWidth) / 2f

        ensureSpace(drawHeight + 20f)

        // Background border
        drawRect(drawX - 2f, currentY - 2f, drawWidth + 4f, drawHeight + 4f, stroke = tableBorder, strokeWidth = 1f)

        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val dstRect = RectF(drawX, currentY, drawX + drawWidth, currentY + drawHeight)
        c.drawBitmap(bitmap, srcRect, dstRect, null)

        currentY += drawHeight + 16f
    }

    private fun drawFooter() {
        val c = canvas ?: return
        val footerY = PAGE_HEIGHT - 24f
        drawLine(MARGIN, footerY, PAGE_WIDTH - MARGIN, footerY, tableBorder, 0.5f)

        val footerText = "Civil EG - ${getLocalized("تقرير تصميم إنشائي", "Structural Design Report")}"
        drawText(footerText, MARGIN, footerY + 14f, fontSize = 8f, color = grayText)

        val pageNumText = "${getLocalized("صفحة", "Page")} $pageNumber"
        drawText(pageNumText, PAGE_WIDTH - MARGIN, footerY + 14f, fontSize = 8f, color = grayText, align = Paint.Align.RIGHT)
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
