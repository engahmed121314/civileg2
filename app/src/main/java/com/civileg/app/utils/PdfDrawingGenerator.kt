package com.civileg.app.utils

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/**
 * Generates engineering drawing Bitmaps for PDF report embedding.
 * Uses Android Canvas API (not Compose) for direct bitmap generation.
 * Drawing style matches the Professional*Drawing Compose components.
 *
 * ********************************************************************************
 * BILINGUAL SUPPORT (2026-07-27 v2)
 * ********************************************************************************
 * All text on drawings is rendered via [drawBilingualText] which uses StaticLayout
 * for proper Arabic BIDI reordering + HarfBuzz shaping. This ensures Arabic
 * descriptions render correctly (connected letters, right-to-left order) while
 * engineering symbols (fy, fcu, Mu, As) remain in Latin script.
 *
 * Use [t] to pick the right label based on the current app locale.
 */
object PdfDrawingGenerator {

    // Color palette matching Compose drawings
    private val BG_COLOR = Color.parseColor("#1A1A2E")
    private val CONCRETE = Color.parseColor("#6B6B6B")
    private val CONCRETE_TOP = Color.parseColor("#8A8A8A")
    private val CONCRETE_SIDE = Color.parseColor("#505050")
    private val REBAR_BLUE = Color.parseColor("#4A90D9")
    private val TOP_REBAR = Color.parseColor("#7EC8E3")
    private val STIRRUP = Color.parseColor("#9B59B6")
    private val SECONDARY_RED = Color.parseColor("#E74C3C")
    private val DIM_TEXT = Color.WHITE
    private val DIM_LINE = Color.parseColor("#AAAAAA")
    private val HATCH = Color.parseColor("#99AAAAAA")
    private val SUPPORT = Color.parseColor("#CCCCCC")
    private val TABLE_HEADER = Color.parseColor("#33FFFFFF")
    private val TABLE_ALT = Color.parseColor("#1AFFFFFF")
    private val SOIL_BROWN = Color.parseColor("#8B4513")
    private val WATER_BLUE = Color.argb(128, 74, 144, 217)

    /** Arabic typeface (cached, lazy-loaded) for bilingual drawing text */
    @Volatile private var arabicTypeface: Typeface? = null

    private fun getArabicTypeface(): Typeface? {
        if (arabicTypeface != null) return arabicTypeface
        // We need an app context to load assets. Use LocaleHelper's stored context.
        return try {
            val ctx = com.civileg.app.utils.LocaleHelper.getAppContext() ?: return null
            val tf = Typeface.createFromAsset(ctx.assets, "fonts/NotoNaskhArabic-Regular.ttf")
            arabicTypeface = tf
            tf
        } catch (_: Exception) { null }
    }

    /** Pick Arabic or English label based on current locale */
    fun t(ar: String, en: String): String =
        if (LocaleHelper.isArabic()) ar else en

    private fun createCanvas(width: Int, height: Int): Pair<Bitmap, Canvas> {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG_COLOR)
        return Pair(bitmap, canvas)
    }

    private fun createPaint(color: Int, strokeWidth: Float = 1f, textSize: Float = 20f, bold: Boolean = false): Paint {
        return Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            this.textSize = textSize
            this.isAntiAlias = true
            this.style = Paint.Style.STROKE
            if (bold) this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    private fun fillPaint(color: Int): Paint {
        return Paint().apply {
            this.color = color
            this.isAntiAlias = true
            this.style = Paint.Style.FILL
        }
    }

    private fun textPaint(color: Int = DIM_TEXT, size: Float = 20f, bold: Boolean = false): Paint {
        return Paint().apply {
            this.color = color
            this.textSize = size
            this.isAntiAlias = true
            this.style = Paint.Style.FILL
            // Use Arabic typeface if text contains Arabic characters
            val defaultTf = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            else Typeface.SANS_SERIF
            this.typeface = defaultTf
        }
    }

    /**
     * Build a TextPaint that uses the Arabic-capable font when text contains Arabic.
     * This is critical for proper letter joining + BIDI reordering via StaticLayout.
     */
    private fun bilingualTextPaint(text: String, color: Int = DIM_TEXT, size: Float = 20f, bold: Boolean = false): TextPaint {
        return TextPaint().apply {
            this.color = color
            this.textSize = size
            this.isAntiAlias = true
            this.style = Paint.Style.FILL
            val hasArabic = ArabicFontProvider.containsArabic(text)
            this.typeface = when {
                hasArabic && getArabicTypeface() != null -> {
                    if (bold) Typeface.create(getArabicTypeface(), Typeface.BOLD)
                    else getArabicTypeface()
                }
                bold -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                else -> Typeface.SANS_SERIF
            }
        }
    }

    /**
     * Draw bilingual text using StaticLayout for proper Arabic BIDI + shaping.
     * CRITICAL: Do NOT use Canvas.drawText for Arabic — it produces wrong joining
     * context (treats text as LTR) resulting in "encrypted-looking" squares.
     */
    private fun Canvas.drawBilingualText(
        text: String,
        x: Float,
        y: Float,
        color: Int = DIM_TEXT,
        size: Float = 20f,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        if (text.isEmpty()) return
        val paint = bilingualTextPaint(text, color, size, bold)
        // Layout width = max available width to the right edge
        val layoutWidth = (this.width - x).toInt().coerceAtLeast(1)
        val sl = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, layoutWidth)
            .setAlignment(
                when (align) {
                    Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
                    Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> if (ArabicFontProvider.containsArabic(text)) Layout.Alignment.ALIGN_OPPOSITE
                            else Layout.Alignment.ALIGN_NORMAL
                }
            )
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
        // Compute drawX for alignment
        val drawX = when (align) {
            Paint.Align.CENTER -> x - sl.width / 2f
            Paint.Align.RIGHT -> x - sl.width
            else -> if (ArabicFontProvider.containsArabic(text)) x - sl.width else x
        }
        this.save()
        // y is the baseline; StaticLayout draws from top, so offset by ascent
        this.translate(drawX, y - paint.ascent() - paint.descent() / 2f)
        sl.draw(this)
        this.restore()
    }

    // ========== TEXT HELPER ==========
    private fun Canvas.drawTextCentered(text: String, x: Float, y: Float, paint: Paint) {
        // Use bilingual rendering for proper Arabic shaping
        drawBilingualText(text, x, y, paint.color, paint.textSize, bold = paint.typeface?.style == Typeface.BOLD, align = Paint.Align.CENTER)
    }

    // ========== DIMENSION LINES ==========
    private fun Canvas.drawHDim(x1: Float, x2: Float, y: Float, text: String, offset: Float = 20f, paint: Paint? = null) {
        val p = paint ?: createPaint(DIM_TEXT, 1f)
        val dimY = y + offset
        val extLen = 8f
        val arrowSize = 5f

        drawLine(x1, y + 2f, x1, dimY + extLen, p)
        drawLine(x2, y + 2f, x2, dimY + extLen, p)
        drawLine(x1 + arrowSize, dimY, x2 - arrowSize, dimY, p)

        // Left arrow
        drawPath(Path().apply {
            moveTo(x1, dimY); lineTo(x1 + arrowSize * 2, dimY - arrowSize); lineTo(x1 + arrowSize * 2, dimY + arrowSize); close()
        }, p)
        // Right arrow
        drawPath(Path().apply {
            moveTo(x2, dimY); lineTo(x2 - arrowSize * 2, dimY - arrowSize); lineTo(x2 - arrowSize * 2, dimY + arrowSize); close()
        }, p)

        val tp = textPaint(DIM_TEXT, 18f)
        drawTextCentered(text, (x1 + x2) / 2f, dimY + extLen + 16f, tp)
    }

    private fun Canvas.drawVDim(y1: Float, y2: Float, x: Float, text: String, offset: Float = 20f) {
        val p = createPaint(DIM_TEXT, 1f)
        val dimX = x + offset
        val extLen = 8f
        val arrowSize = 5f

        drawLine(x + 2f, y1, dimX + extLen, y1, p)
        drawLine(x + 2f, y2, dimX + extLen, y2, p)
        drawLine(dimX, y1 + arrowSize, dimX, y2 - arrowSize, p)

        val tp = textPaint(DIM_TEXT, 18f)
        drawText(text, dimX + extLen + 4f, (y1 + y2) / 2f + 6f, tp)
    }

    // ========== REBAR CIRCLE ==========
    private fun Canvas.drawRebar(cx: Float, cy: Float, r: Float, color: Int = REBAR_BLUE) {
        drawCircle(cx, cy, r, fillPaint(color))
        drawCircle(cx, cy, r * 0.35f, fillPaint(Color.argb(128, Color.red(color), Color.green(color), Color.blue(color))))
    }

    // ========== HATCH PATTERN ==========
    private fun Canvas.drawHatch(x: Float, y: Float, w: Float, h: Float, spacing: Float = 12f) {
        val p = createPaint(HATCH, 0.8f)
        var i = 0
        while (i * spacing < w + h) {
            val sx = x + i * spacing
            val sy = y + h
            val ex = sx + h
            val ey = y
            drawLine(
                sx.coerceIn(x, x + w), maxOf(y, sy - (sx - x).coerceAtLeast(0f)).coerceAtMost(y + h),
                ex.coerceIn(x, x + w), minOf(y + h, ey + (x + w - ex).coerceAtLeast(0f)).coerceAtLeast(y),
                p
            )
            i++
        }
    }

    // ========== GENERATE BEAM DRAWING ==========
    fun generateBeamDrawing(
        beamWidth: Double, beamDepth: Double, span: Double,
        mainRebarDia: Double, mainRebarCount: Int,
        stirrupDia: Double, stirrupSpacing: Double,
        cover: Double = 50.0,
        hasTopSteel: Boolean = false, topRebarDia: Double = 0.0, topRebarCount: Int = 0
    ): Bitmap {
        val W = 1200; val H = 700
        val (bitmap, canvas) = createCanvas(W, H)

        // Layout
        val marginL = 80f; val marginR = 80f; val marginT = 50f
        val mainBottom = H * 0.55f
        val availW = W - marginL - marginR - 60f
        val availH = mainBottom - marginT - 40f
        val scaleW = availW / span.toFloat()
        val scaleH = availH / beamDepth.toFloat()
        val scale = min(scaleW, scaleH) * 0.75f

        val bDrawW = span.toFloat() * scale
        val bDrawH = beamDepth.toFloat() * scale
        val bDrawD = beamWidth.toFloat() * scale * 0.3f
        val bLeft = marginL + 40f
        val bTop = marginT + 40f + bDrawD * 0.2f
        val bRight = bLeft + bDrawW
        val bBottom = bTop + bDrawH

        // 3D beam body
        // Front face
        canvas.drawRect(bLeft, bTop, bRight, bBottom, fillPaint(CONCRETE))
        // Top face (3D)
        canvas.drawPath(Path().apply {
            moveTo(bLeft, bTop); lineTo(bLeft + bDrawD * 0.3f, bTop - bDrawD * 0.2f)
            lineTo(bRight + bDrawD * 0.3f, bTop - bDrawD * 0.2f); lineTo(bRight, bTop); close()
        }, fillPaint(CONCRETE_TOP))
        // Right side face
        canvas.drawPath(Path().apply {
            moveTo(bRight, bTop); lineTo(bRight + bDrawD * 0.3f, bTop - bDrawD * 0.2f)
            lineTo(bRight + bDrawD * 0.3f, bBottom - bDrawD * 0.2f); lineTo(bRight, bBottom); close()
        }, fillPaint(CONCRETE_SIDE))

        // Outline
        val outlineP = createPaint(Color.WHITE, 1.5f)
        canvas.drawRect(bLeft, bTop, bRight, bBottom, outlineP)

        // Supports (pin left, roller right)
        val supportSize = 20f
        // Left pin
        canvas.drawPath(Path().apply {
            moveTo(bLeft - supportSize/2, bBottom); lineTo(bLeft + supportSize/2, bBottom)
            lineTo(bLeft, bBottom + supportSize); close()
        }, fillPaint(SUPPORT))
        canvas.drawLine(bLeft - supportSize, bBottom + supportSize, bLeft + supportSize, bBottom + supportSize, outlineP)

        // Right roller
        canvas.drawPath(Path().apply {
            moveTo(bRight - supportSize/2, bBottom); lineTo(bRight + supportSize/2, bBottom)
            lineTo(bRight, bBottom + supportSize * 0.7f); close()
        }, fillPaint(SUPPORT))
        canvas.drawCircle(bRight - 6f, bBottom + supportSize * 0.7f + 4f, 3f, fillPaint(SUPPORT))
        canvas.drawCircle(bRight + 6f, bBottom + supportSize * 0.7f + 4f, 3f, fillPaint(SUPPORT))
        canvas.drawLine(bRight - supportSize, bBottom + supportSize, bRight + supportSize, bBottom + supportSize, outlineP)

        // Main reinforcement (bottom)
        val rebarR = maxOf(mainRebarDia.toFloat() / 2f * scale * 0.5f, 3f)
        val rebarY = bBottom - cover.toFloat() * scale - rebarR
        val spacing = (bDrawW - 2 * cover.toFloat() * scale) / maxOf(mainRebarCount - 1, 1)
        for (i in 0 until mainRebarCount) {
            val rx = bLeft + cover.toFloat() * scale + i * spacing
            canvas.drawRebar(rx, rebarY, rebarR, REBAR_BLUE)
        }

        // Stirrups
        val stirrupP = createPaint(STIRRUP, 1.5f)
        val stirrupY1 = bTop + cover.toFloat() * scale
        val stirrupY2 = bBottom - cover.toFloat() * scale
        val stirrupX1 = bLeft + cover.toFloat() * scale
        val stirrupX2 = bRight - cover.toFloat() * scale
        val stirrupSpacingPx = stirrupSpacing.toFloat() * scale
        var sx = bLeft + stirrupSpacingPx
        while (sx < bRight - stirrupSpacingPx) {
            canvas.drawRect(sx - 2f, stirrupY1, sx + 2f, stirrupY2, stirrupP)
            sx += stirrupSpacingPx
        }

        // Top reinforcement
        if (hasTopSteel && topRebarCount > 0) {
            val topR = maxOf(topRebarDia.toFloat() / 2f * scale * 0.5f, 3f)
            val topY = bTop + cover.toFloat() * scale + topR
            val topSpacing = (bDrawW - 2 * cover.toFloat() * scale) / maxOf(topRebarCount - 1, 1)
            for (i in 0 until topRebarCount) {
                val rx = bLeft + cover.toFloat() * scale + i * topSpacing
                canvas.drawRebar(rx, topY, topR, TOP_REBAR)
            }
        }

        // Dimensions
        canvas.drawHDim(bLeft, bRight, bBottom + supportSize + 20f, "${span.toInt()} mm")
        canvas.drawVDim(bTop, bBottom, bLeft - 20f, "${beamDepth.toInt()} mm")
        canvas.drawVDim(bTop, bTop + cover.toFloat() * scale, bRight + 20f, "cover=${cover.toInt()}")

        // Title
        val titleP = textPaint(Color.WHITE, 24f, true)
        canvas.drawTextCentered("BEAM SECTION & ELEVATION", W / 2f, 30f, titleP)

        // Cross-section inset (bottom-left)
        val csX = 80f; val csY = H * 0.60f; val csScale = 0.4f
        val csW = beamWidth.toFloat() * scale * csScale
        val csH = beamDepth.toFloat() * scale * csScale

        canvas.drawRect(csX, csY, csX + csW, csY + csH, fillPaint(CONCRETE))
        canvas.drawRect(csX, csY, csX + csW, csY + csH, outlineP)

        // Cross-section stirrup
        val csCover = cover.toFloat() * scale * csScale
        canvas.drawRect(csX + csCover, csY + csCover, csX + csW - csCover, csY + csH - csCover, createPaint(STIRRUP, 1.5f))

        // Cross-section bars
        val csRebarR = maxOf(mainRebarDia.toFloat() * csScale * 0.3f, 3f)
        val csBarY = csY + csH - csCover - csRebarR
        for (i in 0 until minOf(mainRebarCount, 6)) {
            val rx = if (mainRebarCount <= 2) {
                csX + csW / 2f
            } else {
                csX + csCover + csRebarR + i * (csW - 2 * csCover - 2 * csRebarR) / maxOf(mainRebarCount - 1, 1)
            }
            canvas.drawRebar(rx, csBarY, csRebarR, REBAR_BLUE)
        }

        // Section label
        canvas.drawTextCentered("Section A-A", csX + csW / 2f, csY - 8f, textPaint(DIM_TEXT, 16f))

        // Reinforcement table (bottom-right)
        drawRebarTable(canvas, 
            x = W * 0.5f, y = H * 0.60f,
            data = listOf(
                listOf("Mark", "Dia", "No.", "Spacing", "Length"),
                listOf("B1", "${mainRebarDia.toInt()}mm", "$mainRebarCount", "-", "${span.toInt()}mm"),
                listOf("S1", "${stirrupDia.toInt()}mm", "-", "${stirrupSpacing.toInt()}mm", "-")
            ) + if (hasTopSteel && topRebarCount > 0) listOf(listOf("T1", "${topRebarDia.toInt()}mm", "$topRebarCount", "-", "${(span*0.3).toInt()}mm")) else emptyList()
        )

        // Title block
        drawTitleBlock(canvas, W - 280f, H - 60f, 280f, 60f, "Beam Detail")

        return bitmap
    }

    // ========== GENERATE COLUMN DRAWING ==========
    fun generateColumnDrawing(
        columnWidth: Double, columnDepth: Double, columnHeight: Double,
        numBars: Int, barDia: Double, tieDia: Double, tieSpacing: Double,
        cover: Double = 40.0,
        isSpiral: Boolean = false,
        spiralPitch: Double = 0.0,
        sectionType: String = "Rectangular"
    ): Bitmap {
        val W = 1200; val H = 800
        val (bitmap, canvas) = createCanvas(W, H)

        val outlineP = createPaint(Color.WHITE, 1.5f)
        val titleP = textPaint(Color.WHITE, 24f, true)

        // 3D Elevation (left side)
        val elevLeft = 100f; val elevW = 200f
        val elevTop = 80f; val elevH = min(columnHeight.toFloat(), 500f)
        val depth3D = 40f

        // Front face
        canvas.drawRect(elevLeft, elevTop, elevLeft + elevW, elevTop + elevH, fillPaint(CONCRETE))
        // Top face
        canvas.drawPath(Path().apply {
            moveTo(elevLeft, elevTop); lineTo(elevLeft + depth3D, elevTop - depth3D * 0.3f)
            lineTo(elevLeft + elevW + depth3D, elevTop - depth3D * 0.3f); lineTo(elevLeft + elevW, elevTop); close()
        }, fillPaint(CONCRETE_TOP))
        // Side face
        canvas.drawPath(Path().apply {
            moveTo(elevLeft + elevW, elevTop); lineTo(elevLeft + elevW + depth3D, elevTop - depth3D * 0.3f)
            lineTo(elevLeft + elevW + depth3D, elevTop + elevH - depth3D * 0.3f); lineTo(elevLeft + elevW, elevTop + elevH); close()
        }, fillPaint(CONCRETE_SIDE))

        canvas.drawRect(elevLeft, elevTop, elevLeft + elevW, elevTop + elevH, outlineP)

        // Visible bars on front face
        val barR = maxOf(barDia.toFloat() * 0.4f, 3f)
        val coverPx = cover.toFloat()
        // Corner bars (front face - 4 bars)
        val corners = listOf(
            Offset(elevLeft + coverPx, elevTop + coverPx),
            Offset(elevLeft + elevW - coverPx, elevTop + coverPx),
            Offset(elevLeft + coverPx, elevTop + elevH - coverPx),
            Offset(elevLeft + elevW - coverPx, elevTop + elevH - coverPx)
        )
        corners.forEach { canvas.drawRebar(it.x, it.y, barR, REBAR_BLUE) }

        // Ties
        val tieP = createPaint(STIRRUP, 1.5f)
        var ty = elevTop + coverPx + 20f
        while (ty < elevTop + elevH - coverPx) {
            canvas.drawRect(elevLeft + coverPx, ty, elevLeft + elevW - coverPx, ty + 3f, tieP)
            ty += tieSpacing.toFloat() * (elevH / columnHeight.toFloat())
        }

        // Floor slabs at top/bottom
        canvas.drawRect(elevLeft - 60f, elevTop - 15f, elevLeft + elevW + depth3D + 60f, elevTop, fillPaint(CONCRETE_TOP))
        canvas.drawRect(elevLeft - 60f, elevTop - 15f, elevLeft + elevW + depth3D + 60f, elevTop, outlineP)
        canvas.drawRect(elevLeft - 60f, elevTop + elevH, elevLeft + elevW + depth3D + 60f, elevTop + elevH + 15f, fillPaint(CONCRETE_TOP))
        canvas.drawRect(elevLeft - 60f, elevTop + elevH, elevLeft + elevW + depth3D + 60f, elevTop + elevH + 15f, outlineP)

        canvas.drawTextCentered("COLUMN ELEVATION", elevLeft + elevW / 2f, elevTop - 30f, titleP)

        // Cross-section (right side)
        val csCx = W * 0.65f; val csCy = H * 0.35f
        val isCircular = sectionType.contains("Circular", ignoreCase = true) ||
                          sectionType.contains("CIRCULAR", ignoreCase = true)

        if (isCircular) {
            // ── Circular cross-section ──
            val csRadius = columnWidth.toFloat() * 0.4f  // use width as diameter
            canvas.drawCircle(csCx, csCy, csRadius, fillPaint(CONCRETE))
            canvas.drawCircle(csCx, csCy, csRadius, outlineP)

            // Circular tie
            val tieRadius = csRadius - cover.toFloat()
            canvas.drawCircle(csCx, csCy, tieRadius, createPaint(STIRRUP, 1.5f))

            // Bars in circular pattern
            val csBarR = maxOf(barDia.toFloat() * 0.5f, 4f)
            val barCircleR = tieRadius - csBarR - 2f
            val barPositionsCircular = mutableListOf<Offset>()
            for (i in 0 until numBars) {
                val angle = 2 * Math.PI * i / numBars - Math.PI / 2
                barPositionsCircular.add(Offset(
                    csCx + (barCircleR * kotlin.math.cos(angle)).toFloat(),
                    csCy + (barCircleR * kotlin.math.sin(angle)).toFloat()
                ))
            }
            barPositionsCircular.forEachIndexed { idx, pos ->
                canvas.drawRebar(pos.x, pos.y, csBarR, REBAR_BLUE)
                val mark = getCircleNumber(idx + 1)
                canvas.drawTextCentered(mark, pos.x, pos.y - csBarR - 6f, textPaint(DIM_TEXT, 14f, true))
            }

            // Section dimensions
            canvas.drawHDim(csCx - csRadius, csCx + csRadius, csCy + csRadius + 15f, "Ø${columnWidth.toInt()} mm", 20f)
            canvas.drawTextCentered("SECTION A-A", csCx, csCy - csRadius - 20f, titleP)
        } else {
            // ── Rectangular cross-section (original logic) ──
            val csW2 = columnWidth.toFloat() * 0.8f
            val csH2 = columnDepth.toFloat() * 0.8f
            val csLeft = csCx - csW2 / 2f; val csTop2 = csCy - csH2 / 2f

            canvas.drawRect(csLeft, csTop2, csLeft + csW2, csTop2 + csH2, fillPaint(CONCRETE))
            canvas.drawRect(csLeft, csTop2, csLeft + csW2, csTop2 + csH2, outlineP)

            // Tie in section
            val csCover = cover.toFloat()
            canvas.drawRect(csLeft + csCover, csTop2 + csCover, csLeft + csW2 - csCover, csTop2 + csH2 - csCover, createPaint(STIRRUP, 1.5f))

            // Bars in section
            val effW = csW2 - 2 * csCover
            val effH = csH2 - 2 * csCover
            val csBarR = maxOf(barDia.toFloat() * 0.5f, 4f)
            val barPositions = mutableListOf<Offset>()
            barPositions.add(Offset(csLeft + csCover + csBarR, csTop2 + csCover + csBarR))
            barPositions.add(Offset(csLeft + csW2 - csCover - csBarR, csTop2 + csCover + csBarR))
            barPositions.add(Offset(csLeft + csCover + csBarR, csTop2 + csH2 - csCover - csBarR))
            barPositions.add(Offset(csLeft + csW2 - csCover - csBarR, csTop2 + csH2 - csCover - csBarR))

            val remaining = numBars - 4
            if (remaining > 0) {
                val perSide = remaining / 4
                val extra = remaining % 4
                for (side in 0 until 4) {
                    val count = perSide + if (side < extra) 1 else 0
                    for (i in 1..count) {
                        val t = i.toFloat() / (count + 1)
                        when (side) {
                            0 -> barPositions.add(Offset(csLeft + csCover + csBarR + t * (effW - 2 * csBarR), csTop2 + csCover + csBarR))
                            1 -> barPositions.add(Offset(csLeft + csW2 - csCover - csBarR, csTop2 + csCover + csBarR + t * (effH - 2 * csBarR)))
                            2 -> barPositions.add(Offset(csLeft + csCover + csBarR + t * (effW - 2 * csBarR), csTop2 + csH2 - csCover - csBarR))
                            3 -> barPositions.add(Offset(csLeft + csCover + csBarR, csTop2 + csCover + csBarR + t * (effH - 2 * csBarR)))
                        }
                    }
                }
            }

            barPositions.forEachIndexed { idx, pos ->
                canvas.drawRebar(pos.x, pos.y, csBarR, REBAR_BLUE)
                val mark = getCircleNumber(idx + 1)
                canvas.drawTextCentered(mark, pos.x, pos.y - csBarR - 6f, textPaint(DIM_TEXT, 14f, true))
            }

            // Section dimensions
            canvas.drawHDim(csLeft, csLeft + csW2, csTop2 + csH2 + 15f, "${columnWidth.toInt()} mm", 20f)
            canvas.drawVDim(csTop2, csTop2 + csH2, csLeft + csW2 + 15f, "${columnDepth.toInt()} mm", 20f)
            canvas.drawTextCentered("SECTION A-A", csCx, csTop2 - 20f, titleP)
        }

        // Rebar table
        val tieTypeLabel = if (isSpiral) "Spiral" else "Ties"
        val tieSpacingLabel = if (isSpiral) "${spiralPitch.toInt()}mm" else "${tieSpacing.toInt()}mm"
        drawRebarTable(canvas, 
            x = 100f, y = H * 0.65f,
            data = listOf(
                listOf("Mark", "Dia", "No.", "Type", "Spacing"),
                listOf("M1", "${barDia.toInt()}mm", "$numBars", "Main", "-"),
                listOf("T1", "${tieDia.toInt()}mm", "-", tieTypeLabel, tieSpacingLabel)
            )
        )

        drawTitleBlock(canvas, W - 280f, H - 60f, 280f, 60f, "Column Detail")

        return bitmap
    }

    // ========== GENERATE SLAB DRAWING ==========
    // Legacy function — kept for backward compatibility. Renders a generic
    // solid-slab-style drawing. For type-specific drawings, use
    // [generateSlabDrawingByType] instead.
    fun generateSlabDrawing(
        spanX: Double, spanY: Double, thickness: Double,
        mainDia: Double, mainSpacing: Double,
        distDia: Double, distSpacing: Double,
        cover: Double = 25.0
    ): Bitmap {
        val W = 1400; val H = 900
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 2f)
        val dimP = createPaint(DIM_TEXT, 1f)

        // ===== PLAN VIEW (top-left, larger area) =====
        val planL = 80f; val planT = 80f
        val maxPlanW = 550f; val maxPlanH = 420f
        val scale = min(maxPlanW / spanX.toFloat(), maxPlanH / spanY.toFloat()) * 0.85f
        val planW = spanX.toFloat() * scale; val planH = spanY.toFloat() * scale

        // Slab outline with fill
        canvas.drawRect(planL, planT, planL + planW, planT + planH, fillPaint(CONCRETE))
        canvas.drawRect(planL, planT, planL + planW, planT + planH, outlineP)

        // Concrete hatch pattern
        canvas.drawHatch(planL, planT, planW, planH, 18f)

        // Main bars (short span Lx) run ALONG Lx → they span the X axis on plan → VERTICAL lines
        // Lx = spanX (horizontal dimension), main bars cross this direction → vertical lines
        val mainP = createPaint(REBAR_BLUE, 2.5f)
        val scaledMainSpacing = mainSpacing.toFloat() * scale
        val visibleMainSpacing = maxOf(scaledMainSpacing, 15f) // minimum visible spacing
        var mx_main = planL + visibleMainSpacing / 2f
        var mainBarCount = 0
        while (mx_main < planL + planW - visibleMainSpacing / 2f && mainBarCount < 40) {
            canvas.drawLine(mx_main, planT + 8f, mx_main, planT + planH - 8f, mainP)
            mx_main += visibleMainSpacing
            mainBarCount++
        }

        // Distribution bars (long span Ly) run ALONG Ly → they span the Y axis on plan → HORIZONTAL lines
        // Ly = spanY (vertical dimension), distribution bars cross this direction → horizontal lines
        val distP = createPaint(STIRRUP, 2f)
        val scaledDistSpacing = distSpacing.toFloat() * scale
        val visibleDistSpacing = maxOf(scaledDistSpacing, 15f)
        var my_dist = planT + visibleDistSpacing / 2f
        var distBarCount = 0
        while (my_dist < planT + planH - visibleDistSpacing / 2f && distBarCount < 40) {
            canvas.drawLine(planL + 8f, my_dist, planL + planW - 8f, my_dist, distP)
            my_dist += visibleDistSpacing
            distBarCount++
        }

        // Support indicators (edges)
        val supportP = createPaint(SUPPORT, 2f)
        val hatchP = createPaint(HATCH, 0.8f)
        // Bottom support (hatched)
        for (i in 0..8) {
            val sx = planL + i * planW / 8f
            canvas.drawLine(sx, planT + planH, sx + 10f, planT + planH + 12f, hatchP)
        }
        canvas.drawLine(planL, planT + planH + 12f, planL + planW, planT + planH + 12f, supportP)
        // Top support
        for (i in 0..8) {
            val sx = planL + i * planW / 8f
            canvas.drawLine(sx, planT, sx + 10f, planT - 12f, hatchP)
        }
        canvas.drawLine(planL, planT - 12f, planL + planW, planT - 12f, supportP)

        // Plan dimensions (bilingual labels)
        canvas.drawHDim(planL, planL + planW, planT + planH + 25f, "${(spanX * 1000).toInt()} mm (Lx)", offset = 25f)
        canvas.drawVDim(planT, planT + planH, planL - 25f, "${(spanY * 1000).toInt()} mm (Ly)", offset = 25f)

        // Plan title (bilingual)
        canvas.drawTextCentered(t("مسقط تسليح البلاطة", "SLAB REINFORCEMENT PLAN"), planL + planW / 2f, planT - 35f, textPaint(DIM_TEXT, 22f, true))

        // Legend in plan area (bilingual)
        val legX = planL + 10f; val legY = planT + planH - 55f
        canvas.drawLine(legX, legY, legX + 25f, legY, mainP)
        canvas.drawBilingualText(
            t("رئيسي $mainDia mm @ $mainSpacing mm", "Main ${mainDia.toInt()}mm @ ${mainSpacing.toInt()}mm"),
            legX + 30f, legY + 8f, DIM_TEXT, 14f
        )
        canvas.drawLine(legX, legY + 18f, legX + 25f, legY + 18f, distP)
        canvas.drawBilingualText(
            t("توزيع $distDia mm @ $distSpacing mm", "Dist ${distDia.toInt()}mm @ ${distSpacing.toInt()}mm"),
            legX + 30f, legY + 26f, DIM_TEXT, 14f
        )

        // ===== CROSS SECTION B-B (right side) =====
        val secL = W * 0.52f; val secT = 100f
        val secViewW = planW * 0.75f
        // Scale section height to be visible (thickness is in mm, spans in m)
        val secScale = secViewW / (spanX.toFloat())
        val secH = thickness.toFloat() / 1000f * secScale * 4f // exaggerate thickness for visibility
        val actualSecH = maxOf(secH, 40f) // minimum visible height

        // Concrete body
        canvas.drawRect(secL, secT, secL + secViewW, secT + actualSecH, fillPaint(CONCRETE))
        canvas.drawRect(secL, secT, secL + secViewW, secT + actualSecH, outlineP)

        // Cover zone
        val covPx = cover.toFloat() / 1000f * secScale * 4f
        val visCover = maxOf(covPx, 8f)

        // Bottom main bars in section (circles)
        val barR = maxOf(mainDia.toFloat() * 0.4f, 4f)
        val numBarsInSection = minOf((spanX * 1000 / mainSpacing).toInt(), 12)
        val barStartX = secL + visCover + barR
        val barEndX = secL + secViewW - visCover - barR
        for (i in 0 until numBarsInSection) {
            val bx = if (numBarsInSection <= 1) secL + secViewW / 2f
                      else barStartX + i * (barEndX - barStartX) / (numBarsInSection - 1)
            canvas.drawRebar(bx, secT + actualSecH - visCover - barR, barR, REBAR_BLUE)
        }

        // Top distribution bars in section (smaller circles)
        val distBarR = maxOf(distDia.toFloat() * 0.35f, 3f)
        val numDistBars = minOf((spanX * 1000 / distSpacing).toInt(), 8)
        val distStartX = secL + visCover + distBarR
        val distEndX = secL + secViewW - visCover - distBarR
        for (i in 0 until numDistBars) {
            val dx = if (numDistBars <= 1) secL + secViewW / 2f
                      else distStartX + i * (distEndX - distStartX) / (numDistBars - 1)
            canvas.drawRebar(dx, secT + visCover + distBarR, distBarR, TOP_REBAR)
        }

        // Section dimensions
        canvas.drawHDim(secL, secL + secViewW, secT + actualSecH + 20f, "${(spanX * 1000).toInt()} mm")
        canvas.drawVDim(secT, secT + actualSecH, secL + secViewW + 20f, "t=${thickness.toInt()}mm", offset = 25f)
        // Cover dimension
        val coverDimX = secL + secViewW + 10f
        canvas.drawLine(coverDimX - 5f, secT, coverDimX + 5f, secT, dimP)
        canvas.drawLine(coverDimX, secT, coverDimX, secT + visCover, dimP)
        canvas.drawLine(coverDimX - 5f, secT + visCover, coverDimX + 5f, secT + visCover, dimP)
        canvas.drawBilingualText("cover=${cover.toInt()}", coverDimX + 8f, secT + visCover / 2f + 5f, DIM_TEXT, 14f)

        // Section title (bilingual)
        canvas.drawTextCentered(t("قطاع B-B", "SECTION B-B"), secL + secViewW / 2f, secT - 20f, textPaint(DIM_TEXT, 18f, true))

        // ===== REINFORCEMENT SCHEDULE TABLE (bottom) =====
        drawRebarTable(canvas,
            x = 80f, y = H * 0.6f,
            data = listOf(
                listOf(t("الرمز", "Mark"), t("القطر", "Dia (mm)"), t("الاتجاه", "Direction"), t("التباعد", "Spacing (mm)"), t("الطبقة", "Layer"), t("الطول", "Length (mm)")),
                listOf("M1", "${mainDia.toInt()}", t("البحر القصير (Lx)", "Short span (Lx)"), "@ ${mainSpacing.toInt()} c/c", t("سفلي", "Bottom"), "${(spanX * 1000).toInt()}"),
                listOf("D1", "${distDia.toInt()}", t("البحر الطويل (Ly)", "Long span (Ly)"), "@ ${distSpacing.toInt()} c/c", t("علوي", "Top"), "${(spanY * 1000).toInt()}")
            )
        )

        // ===== TITLE BLOCK =====
        drawTitleBlock(canvas, W - 320f, H - 70f, 320f, 70f, t("تفاصيل تسليح البلاطة", "Slab Reinforcement Detail"))

        // Scale note (bilingual)
        canvas.drawBilingualText(t("المقياس: غير مطابق للمقياس - للمرجعية فقط", "Scale: Not to scale - For reference only"), 80f, H - 20f, DIM_TEXT, 12f)

        return bitmap
    }

// ========== GENERATE SLAB DRAWING BY TYPE ==========
    // Type-aware slab drawing generator. Dispatches to specialized drawing
    // functions based on the SlabType enum value.
    //
    // SOLID       → standard two-way solid slab (main + distribution bars)
    // FLAT        → flat slab with drop panels at column locations
    // HOLLOW_BLOCK → Hordi slab with ribs + voids (one-way ribbed)
    // WAFFLE      → two-way ribbed slab with voids in both directions
    // POST_TENSION → flat slab with parabolic tendon profile
    fun generateSlabDrawingByType(
        slabType: com.civileg.app.utils.CalculatorEngine.SlabType,
        spanX: Double, spanY: Double, thickness: Double,
        mainDia: Double, mainSpacing: Double,
        distDia: Double, distSpacing: Double,
        cover: Double = 25.0,
        dropPanelSize: Double = 0.0,
        ribWidth: Double = 100.0,
        ribSpacing: Double = 500.0,
        columnSize: Double = 400.0
    ): Bitmap {
        return when (slabType) {
            com.civileg.app.utils.CalculatorEngine.SlabType.SOLID ->
                generateSolidSlabDrawing(spanX, spanY, thickness, mainDia, mainSpacing, distDia, distSpacing, cover)
            com.civileg.app.utils.CalculatorEngine.SlabType.FLAT ->
                generateFlatSlabDrawing(spanX, spanY, thickness, mainDia, mainSpacing, distDia, distSpacing, cover, dropPanelSize, columnSize)
            com.civileg.app.utils.CalculatorEngine.SlabType.HOLLOW_BLOCK ->
                generateHordiSlabDrawing(spanX, spanY, thickness, mainDia, mainSpacing, distDia, distSpacing, cover, ribWidth, ribSpacing)
            com.civileg.app.utils.CalculatorEngine.SlabType.WAFFLE ->
                generateWaffleSlabDrawing(spanX, spanY, thickness, mainDia, mainSpacing, distDia, distSpacing, cover, ribWidth, ribSpacing)
            com.civileg.app.utils.CalculatorEngine.SlabType.POST_TENSION ->
                generatePostTensionSlabDrawing(spanX, spanY, thickness, mainDia, mainSpacing, distDia, distSpacing, cover)
        }
    }

    // ---- SOLID SLAB ----
    private fun generateSolidSlabDrawing(
        spanX: Double, spanY: Double, thickness: Double,
        mainDia: Double, mainSpacing: Double,
        distDia: Double, distSpacing: Double,
        cover: Double
    ): Bitmap {
        // Delegate to legacy implementation (it draws a proper solid slab)
        return generateSlabDrawing(spanX, spanY, thickness, mainDia, mainSpacing, distDia, distSpacing, cover)
    }

    // ---- FLAT SLAB (with drop panels) ----
    private fun generateFlatSlabDrawing(
        spanX: Double, spanY: Double, thickness: Double,
        mainDia: Double, mainSpacing: Double,
        distDia: Double, distSpacing: Double,
        cover: Double, dropPanelSize: Double, columnSize: Double
    ): Bitmap {
        val W = 1400; val H = 900
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 2f)

        // ===== PLAN VIEW (left, larger) =====
        val planL = 80f; val planT = 80f
        val maxPlanW = 550f; val maxPlanH = 420f
        val scale = min(maxPlanW / spanX.toFloat(), maxPlanH / spanY.toFloat()) * 0.85f
        val planW = spanX.toFloat() * scale
        val planH = spanY.toFloat() * scale

        // Slab body
        canvas.drawRect(planL, planT, planL + planW, planT + planH, fillPaint(CONCRETE))
        canvas.drawRect(planL, planT, planL + planW, planT + planH, outlineP)
        canvas.drawHatch(planL, planT, planW, planH, 22f)

        // Drop panels at column locations (4 corners + center if span > 1 bay)
        val dpSize = (dropPanelSize.toFloat() * scale).coerceAtLeast(40f)
        val colSize = (columnSize.toFloat() * scale).coerceAtLeast(15f)
        val dpColor = Color.parseColor("#8A8A8A")
        val colColor = Color.parseColor("#505050")

        // Place drop panels at 4 corners + center
        val dropPositions = listOf(
            planL to planT,
            planL + planW to planT,
            planL to planT + planH,
            planL + planW to planT + planH,
            planL + planW / 2f to planT + planH / 2f
        )
        dropPositions.forEach { (cx, cy) ->
            // Drop panel (lighter shade)
            canvas.drawRect(cx - dpSize / 2f, cy - dpSize / 2f, cx + dpSize / 2f, cy + dpSize / 2f, fillPaint(dpColor))
            canvas.drawRect(cx - dpSize / 2f, cy - dpSize / 2f, cx + dpSize / 2f, cy + dpSize / 2f, outlineP)
            // Column (darker shade, smaller)
            canvas.drawRect(cx - colSize / 2f, cy - colSize / 2f, cx + colSize / 2f, cy + colSize / 2f, fillPaint(colColor))
            canvas.drawRect(cx - colSize / 2f, cy - colSize / 2f, cx + colSize / 2f, cy + colSize / 2f, outlineP)
        }

        // Main bars (X-direction, vertical lines)
        val mainP = createPaint(REBAR_BLUE, 2f)
        val scaledMainSpacing = mainSpacing.toFloat() * scale
        val visMainSp = maxOf(scaledMainSpacing, 15f)
        var mx = planL + visMainSp / 2f
        var mainBarCount = 0
        while (mx < planL + planW - visMainSp / 2f && mainBarCount < 40) {
            canvas.drawLine(mx, planT + 8f, mx, planT + planH - 8f, mainP)
            mx += visMainSp; mainBarCount++
        }

        // Distribution bars (Y-direction, horizontal lines)
        val distP = createPaint(STIRRUP, 2f)
        val scaledDistSp = distSpacing.toFloat() * scale
        val visDistSp = maxOf(scaledDistSp, 15f)
        var dy = planT + visDistSp / 2f
        var distBarCount = 0
        while (dy < planT + planH - visDistSp / 2f && distBarCount < 40) {
            canvas.drawLine(planL + 8f, dy, planL + planW - 8f, dy, distP)
            dy += visDistSp; distBarCount++
        }

        // Dimensions
        canvas.drawHDim(planL, planL + planW, planT + planH + 25f, "${(spanX * 1000).toInt()} mm (Lx)", offset = 25f)
        canvas.drawVDim(planT, planT + planH, planL - 25f, "${(spanY * 1000).toInt()} mm (Ly)", offset = 25f)
        canvas.drawTextCentered(t("مسقط بلاطة مسطحة مع أبلات التسليح", "FLAT SLAB PLAN — DROP PANELS"), planL + planW / 2f, planT - 35f, textPaint(DIM_TEXT, 22f, true))

        // Legend
        val legX = planL + 10f; val legY = planT + planH - 75f
        canvas.drawRect(legX, legY, legX + 20f, legY + 14f, fillPaint(dpColor))
        canvas.drawBilingualText(t("أبلة تسليح", "Drop Panel"), legX + 26f, legY + 12f, DIM_TEXT, 14f)
        canvas.drawRect(legX, legY + 20f, legX + 20f, legY + 34f, fillPaint(colColor))
        canvas.drawBilingualText(t("عمود", "Column"), legX + 26f, legY + 32f, DIM_TEXT, 14f)
        canvas.drawLine(legX, legY + 44f, legX + 25f, legY + 44f, mainP)
        canvas.drawBilingualText(t("رئيسي ${mainDia.toInt()}@${mainSpacing.toInt()}", "Main ${mainDia.toInt()}@${mainSpacing.toInt()}"), legX + 30f, legY + 50f, DIM_TEXT, 13f)
        canvas.drawLine(legX, legY + 60f, legX + 25f, legY + 60f, distP)
        canvas.drawBilingualText(t("توزيع ${distDia.toInt()}@${distSpacing.toInt()}", "Dist ${distDia.toInt()}@${distSpacing.toInt()}"), legX + 30f, legY + 66f, DIM_TEXT, 13f)

        // ===== CROSS SECTION (right) =====
        val secL = W * 0.55f; val secT = 100f
        val secViewW = planW * 0.75f
        val secScale = secViewW / spanX.toFloat()
        val secH = maxOf(thickness.toFloat() / 1000f * secScale * 4f, 40f)
        val dpH = maxOf((dropPanelSize.toFloat() / 1000f * secScale * 4f), 20f)

        // Drop panel zone (left + right — represents column strip)
        canvas.drawRect(secL, secT, secL + 60f, secT + secH + dpH, fillPaint(Color.parseColor("#8A8A8A")))
        canvas.drawRect(secL + secViewW - 60f, secT, secL + secViewW, secT + secH + dpH, fillPaint(Color.parseColor("#8A8A8A")))

        // Slab body
        canvas.drawRect(secL, secT + dpH, secL + secViewW, secT + secH + dpH, fillPaint(CONCRETE))
        canvas.drawRect(secL, secT + dpH, secL + secViewW, secT + secH + dpH, outlineP)

        // Column below
        val colW = (columnSize.toFloat() * secScale).coerceAtLeast(20f)
        canvas.drawRect(secL + 30f - colW / 2f, secT + secH + dpH, secL + 30f + colW / 2f, secT + secH + dpH + 30f, fillPaint(Color.parseColor("#505050")))
        canvas.drawRect(secL + secViewW - 30f - colW / 2f, secT + secH + dpH, secL + secViewW - 30f + colW / 2f, secT + secH + dpH + 30f, fillPaint(Color.parseColor("#505050")))

        // Bottom bars in section
        val barR = maxOf(mainDia.toFloat() * 0.4f, 4f)
        val numBars = minOf((spanX * 1000 / mainSpacing).toInt(), 12)
        val barStartX = secL + 10f + barR
        val barEndX = secL + secViewW - 10f - barR
        for (i in 0 until numBars) {
            val bx = if (numBars <= 1) secL + secViewW / 2f
                     else barStartX + i * (barEndX - barStartX) / (numBars - 1)
            canvas.drawRebar(bx, secT + secH + dpH - 8f - barR, barR, REBAR_BLUE)
        }

        // Dimensions
        canvas.drawHDim(secL, secL + secViewW, secT + secH + dpH + 50f, "${(spanX * 1000).toInt()} mm")
        canvas.drawVDim(secT, secT + secH + dpH, secL + secViewW + 20f, "t=${thickness.toInt()}mm + DP=${dropPanelSize.toInt()}mm", offset = 25f)
        canvas.drawTextCentered(t("قطاع A-A مع أبلة التسليح", "SECTION A-A (WITH DROP PANEL)"), secL + secViewW / 2f, secT - 20f, textPaint(DIM_TEXT, 18f, true))

        // ===== TABLE =====
        drawRebarTable(canvas,
            x = 80f, y = H * 0.6f,
            data = listOf(
                listOf(t("الرمز", "Mark"), t("القطر", "Dia"), t("الاتجاه", "Direction"), t("التباعد", "Spacing"), t("الطبقة", "Layer"), t("الطول", "Length")),
                listOf("M1", "${mainDia.toInt()}", t("بحر قصير Lx", "Short span Lx"), "@ ${mainSpacing.toInt()} c/c", t("سفلي", "Bottom"), "${(spanX * 1000).toInt()}"),
                listOf("D1", "${distDia.toInt()}", t("بحر طويل Ly", "Long span Ly"), "@ ${distSpacing.toInt()} c/c", t("سفلي", "Bottom"), "${(spanY * 1000).toInt()}")
            )
        )
        drawTitleBlock(canvas, W - 320f, H - 70f, 320f, 70f, t("تفاصيل بلاطة مسطحة", "Flat Slab Detail"))
        canvas.drawBilingualText(t("المقياس: غير مطابق للمقياس", "Scale: Not to scale"), 80f, H - 20f, DIM_TEXT, 12f)
        return bitmap
    }

    // ---- HORDI (HOLLOW BLOCK) SLAB — one-way ribbed ----
    private fun generateHordiSlabDrawing(
        spanX: Double, spanY: Double, thickness: Double,
        mainDia: Double, mainSpacing: Double,
        distDia: Double, distSpacing: Double,
        cover: Double, ribWidth: Double, ribSpacing: Double
    ): Bitmap {
        val W = 1400; val H = 900
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 2f)

        // ===== PLAN VIEW =====
        val planL = 80f; val planT = 80f
        val maxPlanW = 550f; val maxPlanH = 420f
        val scale = min(maxPlanW / spanX.toFloat(), maxPlanH / spanY.toFloat()) * 0.85f
        val planW = spanX.toFloat() * scale
        val planH = spanY.toFloat() * scale

        // Outer slab outline
        canvas.drawRect(planL, planT, planL + planW, planT + planH, fillPaint(Color.parseColor("#3A3A4E")))
        canvas.drawRect(planL, planT, planL + planW, planT + planH, outlineP)

        // Ribs (horizontal lines = ribs running along spanX direction)
        // Each rib is ribWidth mm wide, spaced ribSpacing mm apart
        val ribW = maxOf(ribWidth.toFloat() * scale, 8f)
        val ribSp = maxOf(ribSpacing.toFloat() * scale, 30f)
        val ribP = createPaint(CONCRETE, 0f)
        ribP.style = Paint.Style.FILL

        var ry = planT + 5f
        var ribIdx = 0
        while (ry < planT + planH - 5f && ribIdx < 25) {
            // Rib (concrete strip)
            canvas.drawRect(planL + 5f, ry, planL + planW - 5f, ry + ribW, ribP)
            canvas.drawRect(planL + 5f, ry, planL + planW - 5f, ry + ribW, outlineP)

            // Hollow blocks (voids) between ribs — shown as lighter rectangles
            if (ry + ribW + 5f < planT + planH - 5f) {
                val voidTop = ry + ribW
                val voidBot = minOf(ry + ribSp, planT + planH - 5f)
                if (voidBot > voidTop + 5f) {
                    val voidPaint = fillPaint(Color.parseColor("#1F1F2E"))
                    // Draw multiple void blocks along the rib
                    val blockW = 80f
                    var vx = planL + 15f
                    while (vx + blockW < planL + planW - 15f) {
                        canvas.drawRect(vx, voidTop + 3f, vx + blockW, voidBot - 3f, voidPaint)
                        // Hollow block outline (dashed look — small marks)
                        val dashP = createPaint(Color.parseColor("#666666"), 0.6f)
                        canvas.drawRect(vx, voidTop + 3f, vx + blockW, voidBot - 3f, dashP)
                        vx += blockW + 10f
                    }
                }
            }
            ry += ribSp
            ribIdx++
        }

        // Solid strip at perimeter (perimeter beam)
        val perimW = 20f
        canvas.drawRect(planL, planT, planL + planW, planT + perimW, fillPaint(CONCRETE))
        canvas.drawRect(planL, planT, planL + planW, planT + perimW, outlineP)
        canvas.drawRect(planL, planT + planH - perimW, planL + planW, planT + planH, fillPaint(CONCRETE))
        canvas.drawRect(planL, planT + planH - perimW, planL + planW, planT + planH, outlineP)
        canvas.drawRect(planL, planT, planL + perimW, planT + planH, fillPaint(CONCRETE))
        canvas.drawRect(planL, planT, planL + perimW, planT + planH, outlineP)
        canvas.drawRect(planL + planW - perimW, planT, planL + planW, planT + planH, fillPaint(CONCRETE))
        canvas.drawRect(planL + planW - perimW, planT, planL + planW, planT + planH, outlineP)

        // Main reinforcement in ribs (top — visible as red dots along each rib)
        val ribMainP = createPaint(REBAR_BLUE, 2f)
        ry = planT + 5f
        ribIdx = 0
        while (ry < planT + planH - 5f && ribIdx < 25) {
            // Bottom main bar in rib (dot in middle)
            canvas.drawCircle(planL + planW / 2f, ry + ribW / 2f, 3f, fillPaint(REBAR_BLUE))
            ry += ribSp
            ribIdx++
        }

        // Dimensions
        canvas.drawHDim(planL, planL + planW, planT + planH + 25f, "${(spanX * 1000).toInt()} mm (Lx)", offset = 25f)
        canvas.drawVDim(planT, planT + planH, planL - 25f, "${(spanY * 1000).toInt()} mm (Ly)", offset = 25f)
        canvas.drawTextCentered(t("مسقط بلاطة هردي — كمرات وأبلات", "HORDI SLAB PLAN — RIBS & VOID BLOCKS"), planL + planW / 2f, planT - 35f, textPaint(DIM_TEXT, 22f, true))

        // Legend
        val legX = planL + 10f; val legY = planT + 10f
        canvas.drawRect(legX, legY, legX + 18f, legY + 12f, fillPaint(CONCRETE))
        canvas.drawBilingualText(t("كمرة (ريبة)", "Rib"), legX + 24f, legY + 10f, DIM_TEXT, 13f)
        canvas.drawRect(legX, legY + 18f, legX + 18f, legY + 30f, fillPaint(Color.parseColor("#1F1F2E")))
        canvas.drawBilingualText(t("بلوك مفرغ", "Hollow Block"), legX + 24f, legY + 28f, DIM_TEXT, 13f)
        canvas.drawCircle(legX + 9f, legY + 40f, 3f, fillPaint(REBAR_BLUE))
        canvas.drawBilingualText(t("سيخ رئيسي ${mainDia.toInt()}mm", "Main bar ${mainDia.toInt()}mm"), legX + 24f, legY + 44f, DIM_TEXT, 13f)

        // ===== CROSS SECTION (showing ribs + voids + blocks) =====
        val secL = W * 0.55f; val secT = 100f
        val secViewW = planW * 0.75f
        val secH = maxOf(thickness.toFloat() * 0.3f, 60f)
        val blockH = secH * 0.55f

        // Top concrete flange
        canvas.drawRect(secL, secT, secL + secViewW, secT + (secH - blockH), fillPaint(CONCRETE))
        canvas.drawRect(secL, secT, secL + secViewW, secT + (secH - blockH), outlineP)

        // Ribs + hollow blocks (alternating)
        val numRibs = 5
        val ribSecW = secViewW / (numRibs * 2 - 1) * 0.7f
        val blockSecW = secViewW / (numRibs * 2 - 1) * 1.3f
        var sx = secL
        for (i in 0 until numRibs) {
            // Rib (concrete column down to bottom)
            canvas.drawRect(sx, secT, sx + ribSecW, secT + secH, fillPaint(CONCRETE))
            canvas.drawRect(sx, secT, sx + ribSecW, secT + secH, outlineP)

            // Bottom main bar in rib
            canvas.drawRebar(sx + ribSecW / 2f, secT + secH - 8f, 4f, REBAR_BLUE)

            // Stirrup in rib
            val stirP = createPaint(STIRRUP, 1f)
            canvas.drawRect(sx + 4f, secT + (secH - blockH) + 4f, sx + ribSecW - 4f, secT + secH - 4f, stirP)

            sx += ribSecW
            // Hollow block (void)
            if (i < numRibs - 1) {
                canvas.drawRect(sx, secT + (secH - blockH), sx + blockSecW, secT + secH, fillPaint(Color.parseColor("#1F1F2E")))
                val dashP = createPaint(Color.parseColor("#888888"), 0.8f)
                canvas.drawRect(sx, secT + (secH - blockH), sx + blockSecW, secT + secH, dashP)
                sx += blockSecW
            }
        }

        // Dimensions
        canvas.drawHDim(secL, secL + secViewW, secT + secH + 20f, "${(spanX * 1000).toInt()} mm")
        canvas.drawVDim(secT, secT + secH, secL + secViewW + 20f, "t=${thickness.toInt()}mm", offset = 25f)
        canvas.drawTextCentered(t("قطاع A-A — كمرات وبلوكات", "SECTION A-A — RIBS & BLOCKS"), secL + secViewW / 2f, secT - 20f, textPaint(DIM_TEXT, 18f, true))

        // ===== TABLE =====
        drawRebarTable(canvas,
            x = 80f, y = H * 0.6f,
            data = listOf(
                listOf(t("الرمز", "Mark"), t("القطر", "Dia"), t("الموقع", "Location"), t("التباعد", "Spacing"), t("الطول", "Length")),
                listOf("R1", "${mainDia.toInt()}", t("باطن الريبة", "Rib bottom"), "@ ${ribSpacing.toInt()} mm", "${(spanX * 1000).toInt()}"),
                listOf("T1", "${distDia.toInt()}", t("سقف البلاطة", "Slab top"), "@ ${distSpacing.toInt()} mm", "${(spanY * 1000).toInt()}"),
                listOf("S1", "8", t("روابط الريبة", "Rib stirrups"), "@ 200 mm", "${(spanX * 1000).toInt()}")
            )
        )
        drawTitleBlock(canvas, W - 320f, H - 70f, 320f, 70f, t("تفاصيل بلاطة هردي", "Hordi Slab Detail"))
        canvas.drawBilingualText(t("المقياس: غير مطابق للمقياس", "Scale: Not to scale"), 80f, H - 20f, DIM_TEXT, 12f)
        return bitmap
    }

    // ---- WAFFLE SLAB — two-way ribbed ----
    private fun generateWaffleSlabDrawing(
        spanX: Double, spanY: Double, thickness: Double,
        mainDia: Double, mainSpacing: Double,
        distDia: Double, distSpacing: Double,
        cover: Double, ribWidth: Double, ribSpacing: Double
    ): Bitmap {
        val W = 1400; val H = 900
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 2f)

        // ===== PLAN VIEW =====
        val planL = 80f; val planT = 80f
        val maxPlanW = 550f; val maxPlanH = 420f
        val scale = min(maxPlanW / spanX.toFloat(), maxPlanH / spanY.toFloat()) * 0.85f
        val planW = spanX.toFloat() * scale
        val planH = spanY.toFloat() * scale

        // Background (darker = voids)
        canvas.drawRect(planL, planT, planL + planW, planT + planH, fillPaint(Color.parseColor("#1F1F2E")))
        canvas.drawRect(planL, planT, planL + planW, planT + planH, outlineP)

        // Ribs in BOTH directions (forming a grid)
        val ribSp = maxOf(ribSpacing.toFloat() * scale, 30f)
        val ribW = maxOf(ribWidth.toFloat() * scale, 8f)

        // Vertical ribs (X-direction)
        var vx = planL + ribSp / 2f
        var vCount = 0
        while (vx < planL + planW - ribSp / 2f && vCount < 25) {
            canvas.drawRect(vx - ribW / 2f, planT + 5f, vx + ribW / 2f, planT + planH - 5f, fillPaint(CONCRETE))
            vx += ribSp; vCount++
        }

        // Horizontal ribs (Y-direction)
        var hy = planT + ribSp / 2f
        var hCount = 0
        while (hy < planT + planH - ribSp / 2f && hCount < 25) {
            canvas.drawRect(planL + 5f, hy - ribW / 2f, planL + planW - 5f, hy + ribW / 2f, fillPaint(CONCRETE))
            hy += ribSp; hCount++
        }

        // Solid head zone at column locations (perimeter ~1/4 of span)
        val headW = minOf(planW * 0.2f, 100f)
        // 4 corners
        canvas.drawRect(planL, planT, planL + headW, planT + headW, fillPaint(CONCRETE))
        canvas.drawRect(planL + planW - headW, planT, planL + planW, planT + headW, fillPaint(CONCRETE))
        canvas.drawRect(planL, planT + planH - headW, planL + headW, planT + planH, fillPaint(CONCRETE))
        canvas.drawRect(planL + planW - headW, planT + planH - headW, planL + planW, planT + planH, fillPaint(CONCRETE))

        // Re-draw outline on top
        canvas.drawRect(planL, planT, planL + planW, planT + planH, outlineP)

        // Dimensions
        canvas.drawHDim(planL, planL + planW, planT + planH + 25f, "${(spanX * 1000).toInt()} mm (Lx)", offset = 25f)
        canvas.drawVDim(planT, planT + planH, planL - 25f, "${(spanY * 1000).toInt()} mm (Ly)", offset = 25f)
        canvas.drawTextCentered(t("مسقط بلاطة وافل — كمرات متقاطعة", "WAFFLE SLAB PLAN — TWO-WAY RIBS"), planL + planW / 2f, planT - 35f, textPaint(DIM_TEXT, 22f, true))

        // Legend
        val legX = planL + 10f; val legY = planT + 10f
        canvas.drawRect(legX, legY, legX + 18f, legY + 12f, fillPaint(CONCRETE))
        canvas.drawBilingualText(t("كمرة", "Rib"), legX + 24f, legY + 10f, DIM_TEXT, 13f)
        canvas.drawRect(legX, legY + 18f, legX + 18f, legY + 30f, fillPaint(Color.parseColor("#1F1F2E")))
        canvas.drawBilingualText(t("فراغ", "Void"), legX + 24f, legY + 28f, DIM_TEXT, 13f)

        // ===== CROSS SECTION =====
        val secL = W * 0.55f; val secT = 100f
        val secViewW = planW * 0.75f
        val secH = maxOf(thickness.toFloat() * 0.3f, 60f)
        val blockH = secH * 0.55f

        // Top flange
        canvas.drawRect(secL, secT, secL + secViewW, secT + (secH - blockH), fillPaint(CONCRETE))
        canvas.drawRect(secL, secT, secL + secViewW, secT + (secH - blockH), outlineP)

        // Ribs + voids (alternating)
        val numRibs = 6
        val ribSecW = secViewW / (numRibs * 2 - 1) * 0.6f
        val blockSecW = secViewW / (numRibs * 2 - 1) * 1.4f
        var sx = secL
        for (i in 0 until numRibs) {
            canvas.drawRect(sx, secT, sx + ribSecW, secT + secH, fillPaint(CONCRETE))
            canvas.drawRect(sx, secT, sx + ribSecW, secT + secH, outlineP)
            canvas.drawRebar(sx + ribSecW / 2f, secT + secH - 8f, 4f, REBAR_BLUE)
            sx += ribSecW
            if (i < numRibs - 1) {
                canvas.drawRect(sx, secT + (secH - blockH), sx + blockSecW, secT + secH, fillPaint(Color.parseColor("#1F1F2E")))
                canvas.drawRect(sx, secT + (secH - blockH), sx + blockSecW, secT + secH, createPaint(Color.parseColor("#888888"), 0.8f))
                sx += blockSecW
            }
        }

        // Dimensions
        canvas.drawHDim(secL, secL + secViewW, secT + secH + 20f, "${(spanX * 1000).toInt()} mm")
        canvas.drawVDim(secT, secT + secH, secL + secViewW + 20f, "t=${thickness.toInt()}mm", offset = 25f)
        canvas.drawTextCentered(t("قطاع A-A — وافل", "SECTION A-A — WAFFLE"), secL + secViewW / 2f, secT - 20f, textPaint(DIM_TEXT, 18f, true))

        // ===== TABLE =====
        drawRebarTable(canvas,
            x = 80f, y = H * 0.6f,
            data = listOf(
                listOf(t("الرمز", "Mark"), t("القطر", "Dia"), t("الاتجاه", "Direction"), t("التباعد", "Spacing"), t("الطبقة", "Layer")),
                listOf("RX", "${mainDia.toInt()}", t("بحر X", "X-direction"), "@ ${ribSpacing.toInt()} mm", t("باطن", "Bottom")),
                listOf("RY", "${mainDia.toInt()}", t("بحر Y", "Y-direction"), "@ ${ribSpacing.toInt()} mm", t("باطن", "Bottom")),
                listOf("T", "${distDia.toInt()}", t("سقف", "Top flange"), "@ ${distSpacing.toInt()} mm", t("علوي", "Top"))
            )
        )
        drawTitleBlock(canvas, W - 320f, H - 70f, 320f, 70f, t("تفاصيل بلاطة وافل", "Waffle Slab Detail"))
        canvas.drawBilingualText(t("المقياس: غير مطابق للمقياس", "Scale: Not to scale"), 80f, H - 20f, DIM_TEXT, 12f)
        return bitmap
    }

    // ---- POST-TENSIONED SLAB ----
    private fun generatePostTensionSlabDrawing(
        spanX: Double, spanY: Double, thickness: Double,
        mainDia: Double, mainSpacing: Double,
        distDia: Double, distSpacing: Double,
        cover: Double
    ): Bitmap {
        val W = 1400; val H = 900
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 2f)

        // ===== PLAN VIEW =====
        val planL = 80f; val planT = 80f
        val maxPlanW = 550f; val maxPlanH = 420f
        val scale = min(maxPlanW / spanX.toFloat(), maxPlanH / spanY.toFloat()) * 0.85f
        val planW = spanX.toFloat() * scale
        val planH = spanY.toFloat() * scale

        // Slab body
        canvas.drawRect(planL, planT, planL + planW, planT + planH, fillPaint(CONCRETE))
        canvas.drawRect(planL, planT, planL + planW, planT + planH, outlineP)
        canvas.drawHatch(planL, planT, planW, planH, 22f)

        // Post-tensioning tendons (parabolic profile in plan view shown as banded groups)
        val tendonP = createPaint(SECONDARY_RED, 2.5f)
        val tendonSpacingPx = maxOf(80f, planH / 6f)
        val tendonHalfWidth = planW * 0.45f

        var ty = planT + tendonSpacingPx / 2f
        var tCount = 0
        while (ty < planT + planH - tendonSpacingPx / 2f && tCount < 8) {
            // Banded tendon group (3 lines close together)
            // Float ranges don't support `step` in Kotlin — use explicit list
            for (offset in listOf(-4f, 0f, 4f)) {
                // Parabolic curve approximation (draped tendons)
                val path = Path()
                path.moveTo(planL + planW * 0.05f, ty + offset)
                path.cubicTo(
                    planL + planW * 0.3f, ty + offset - 15f,
                    planL + planW * 0.7f, ty + offset - 15f,
                    planL + planW * 0.95f, ty + offset
                )
                canvas.drawPath(path, tendonP)
            }
            ty += tendonSpacingPx
            tCount++
        }

        // Anchorages at left and right edges (small rectangles)
        val anchorP = fillPaint(Color.parseColor("#FFD700"))
        ty = planT + tendonSpacingPx / 2f
        tCount = 0
        while (ty < planT + planH - tendonSpacingPx / 2f && tCount < 8) {
            canvas.drawRect(planL - 8f, ty - 6f, planL + 4f, ty + 6f, anchorP)
            canvas.drawRect(planL + planW - 4f, ty - 6f, planL + planW + 8f, ty + 6f, anchorP)
            ty += tendonSpacingPx
            tCount++
        }

        // Dimensions
        canvas.drawHDim(planL, planL + planW, planT + planH + 25f, "${(spanX * 1000).toInt()} mm (Lx)", offset = 25f)
        canvas.drawVDim(planT, planT + planH, planL - 25f, "${(spanY * 1000).toInt()} mm (Ly)", offset = 25f)
        canvas.drawTextCentered(t("مسقط بلاطة بست تنشن — ترتيب ال tendons", "POST-TENSION SLAB PLAN — TENDON LAYOUT"), planL + planW / 2f, planT - 35f, textPaint(DIM_TEXT, 22f, true))

        // Legend
        val legX = planL + 10f; val legY = planT + planH - 55f
        canvas.drawLine(legX, legY, legX + 25f, legY, tendonP)
        canvas.drawBilingualText(t("Tendons (parabolic profile)", "Tendons (parabolic profile)"), legX + 30f, legY + 5f, DIM_TEXT, 13f)
        canvas.drawRect(legX, legY + 16f, legX + 18f, legY + 28f, anchorP)
        canvas.drawBilingualText(t("أنكور", "Anchorage"), legX + 24f, legY + 26f, DIM_TEXT, 13f)

        // ===== CROSS SECTION (showing parabolic tendon profile) =====
        val secL = W * 0.55f; val secT = 100f
        val secViewW = planW * 0.75f
        val secH = maxOf(thickness.toFloat() * 0.3f, 60f)

        // Slab body
        canvas.drawRect(secL, secT, secL + secViewW, secT + secH, fillPaint(CONCRETE))
        canvas.drawRect(secL, secT, secL + secViewW, secT + secH, outlineP)

        // Parabolic tendons (draped profile)
        val tendonColor = SECONDARY_RED
        val numTendons = 4
        for (i in 0 until numTendons) {
            val tx = secL + secViewW * (i + 0.5f) / numTendons
            // Parabolic curve: high at supports, low at midspan
            val path = Path()
            path.moveTo(secL, secT + 10f)
            path.cubicTo(
                secL + secViewW * 0.3f, secT + secH - 12f,
                secL + secViewW * 0.7f, secT + secH - 12f,
                secL + secViewW, secT + 10f
            )
            canvas.drawPath(path, createPaint(tendonColor, 1.5f))

            // Tendon dots at midspan
            canvas.drawCircle(tx, secT + secH - 8f, 3f, fillPaint(tendonColor))
        }

        // Dimensions
        canvas.drawHDim(secL, secL + secViewW, secT + secH + 20f, "${(spanX * 1000).toInt()} mm")
        canvas.drawVDim(secT, secT + secH, secL + secViewW + 20f, "t=${thickness.toInt()}mm", offset = 25f)
        canvas.drawTextCentered(t("قطاع A-A — مظهر ال tendons", "SECTION A-A — TENDON PROFILE"), secL + secViewW / 2f, secT - 20f, textPaint(DIM_TEXT, 18f, true))

        // ===== TABLE =====
        drawRebarTable(canvas,
            x = 80f, y = H * 0.6f,
            data = listOf(
                listOf(t("الرمز", "Mark"), t("النوع", "Type"), t("الاتجاه", "Direction"), t("التباعد", "Spacing"), t("القطر", "Dia")),
                listOf("PT1", t("ست تنشن", "Tendon"), t("بحر X", "X-direction"), "@ ${mainSpacing.toInt()} mm", "Ø${mainDia.toInt()}mm (15.2mm strand)"),
                listOf("R1", t("تسليح عادي", "Mild steel"), t("بحر Y", "Y-direction"), "@ ${distSpacing.toInt()} mm", "Ø${distDia.toInt()}mm")
            )
        )
        drawTitleBlock(canvas, W - 320f, H - 70f, 320f, 70f, t("تفاصيل بلاطة بست تنشن", "Post-Tension Slab Detail"))
        canvas.drawBilingualText(t("المقياس: غير مطابق للمقياس", "Scale: Not to scale"), 80f, H - 20f, DIM_TEXT, 12f)
        return bitmap
    }

    // ========== GENERATE FOOTING DRAWING ==========
    fun generateFootingDrawing(
        footingLX: Double, footingLY: Double, footingThickness: Double,
        colW: Double, colD: Double,
        rebarXCount: Int, rebarXDia: Double, rebarXSpacing: Double,
        rebarYCount: Int, rebarYDia: Double, rebarYSpatial: Double,
        footingType: String = "Isolated",
        cover: Double = 70.0,
        soilPressureMax: Double = 0.0,
        soilPressureMin: Double = 0.0
    ): Bitmap {
        val W = 1200; val H = 700
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 1.5f)

        // Plan view
        val scale = min(450f / footingLX.toFloat(), 300f / footingLY.toFloat()) * 0.7f
        val planL = 80f; val planT = 80f
        val fW = footingLX.toFloat() * scale; val fH = footingLY.toFloat() * scale
        val cW = colW.toFloat() * scale; val cH = colD.toFloat() * scale

        canvas.drawRect(planL, planT, planL + fW, planT + fH, fillPaint(CONCRETE))
        canvas.drawRect(planL, planT, planL + fW, planT + fH, outlineP)
        canvas.drawHatch(planL, planT, fW, fH)

        // Column outline in center
        val colL = planL + (fW - cW) / 2f; val colT = planT + (fH - cH) / 2f
        canvas.drawRect(colL, colT, colL + cW, colT + cH, fillPaint(CONCRETE_SIDE))
        canvas.drawRect(colL, colT, colL + cW, colT + cH, outlineP)

        // X-direction bars (vertical blue)
        val barP = createPaint(REBAR_BLUE, 2f)
        for (i in 0 until rebarXCount.coerceAtMost(10)) {
            val bx = planL + 30f + i * (fW - 60f) / maxOf(rebarXCount - 1, 1)
            canvas.drawLine(bx, planT + 5f, bx, planT + fH - 5f, barP)
        }

        // Y-direction bars (horizontal light blue)
        val barP2 = createPaint(TOP_REBAR, 1.5f)
        for (i in 0 until rebarYCount.coerceAtMost(8)) {
            val by = planT + 30f + i * (fH - 60f) / maxOf(rebarYCount - 1, 1)
            canvas.drawLine(planL + 5f, by, planL + fW - 5f, by, barP2)
        }

        // Dimensions
        canvas.drawHDim(planL, planL + fW, planT + fH + 15f, "${footingLX.toInt()} mm (Lx)")
        canvas.drawVDim(planT, planT + fH, planL + fW + 15f, "${footingLY.toInt()} mm (Ly)")

        val titleP = textPaint(Color.WHITE, 22f, true)
        canvas.drawTextCentered("FOOTING PLAN — ${footingType.uppercase()}", planL + fW / 2f, planT - 20f, titleP)

        // Section (right side)
        val secL = W * 0.52f; val secT = 80f
        val secW2 = fW * 0.7f; val secH2 = footingThickness.toFloat() * scale * 1.5f

        // Soil
        canvas.drawRect(secL - 20f, secT + secH2, secL + secW2 + 20f, secT + secH2 + 30f, fillPaint(Color.parseColor("#8B4513")))
        // Concrete
        canvas.drawRect(secL, secT, secL + secW2, secT + secH2, fillPaint(CONCRETE))
        canvas.drawRect(secL, secT, secL + secW2, secT + secH2, outlineP)
        // Column above
        val colW2 = cW * 0.8f
        canvas.drawRect(secL + (secW2 - colW2) / 2f, secT - 40f, secL + (secW2 + colW2) / 2f, secT, fillPaint(CONCRETE_TOP))
        canvas.drawRect(secL + (secW2 - colW2) / 2f, secT - 40f, secL + (secW2 + colW2) / 2f, secT, outlineP)

        // Bottom bars in section
        val bR = maxOf(rebarXDia.toFloat() * 0.3f, 3f)
        for (i in 0 until rebarXCount.coerceAtMost(8)) {
            val bx = secL + 15f + i * (secW2 - 30f) / maxOf(rebarXCount - 1, 1)
            canvas.drawRebar(bx, secT + secH2 - 12f, bR, REBAR_BLUE)
        }

        canvas.drawTextCentered("SECTION A-A", secL + secW2 / 2f, secT - 55f, textPaint(DIM_TEXT, 18f, true))

        // Table
        drawRebarTable(canvas,
            x = 80f, y = H * 0.55f,
            data = listOf(
                listOf("Mark", "Dia", "No.", "Spacing", "Dir"),
                listOf("X1", "${rebarXDia.toInt()}mm", "$rebarXCount", "${rebarXSpacing.toInt()}mm", "X"),
                listOf("Y1", "${rebarYDia.toInt()}mm", "$rebarYCount", "${rebarYSpatial.toInt()}mm", "Y")
            )
        )

        drawTitleBlock(canvas, W - 280f, H - 60f, 280f, 60f, "Footing Detail — ${footingType.uppercase()}")
        return bitmap
    }

    // ========== GENERATE STAIR DRAWING ==========
    fun generateStairDrawing(
        totalHeight: Double, totalLength: Double, stairWidth: Double,
        riserHeight: Double, treadWidth: Double, slabThickness: Double,
        mainDia: Double, mainSpacing: Double, distDia: Double = 8.0, distSpacing: Double = 200.0,
        cover: Double = 25.0
    ): Bitmap {
        val W = 1200; val H = 700
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 1.5f)

        val nRisers = (totalHeight / riserHeight).toInt().coerceAtLeast(3)
        val nTreads = nRisers - 1
        val angle = atan2(totalHeight.toFloat(), totalLength.toFloat())

        // Elevation
        val elevL = 80f; val elevT = 80f
        val scale = min(500f / totalLength.toFloat(), 350f / totalHeight.toFloat()) * 0.8f
        val drawL = totalLength.toFloat() * scale; val drawH = totalHeight.toFloat() * scale
        val treadH = riserHeight.toFloat() * scale; val treadW = treadWidth.toFloat() * scale

        // Stair outline
        val path = Path()
        path.moveTo(elevL, elevT + drawH) // bottom-left
        for (i in 0 until nRisers) {
            path.lineTo(elevL + i * treadW, elevT + drawH - (i + 1) * treadH)
            if (i < nTreads) path.lineTo(elevL + (i + 1) * treadW, elevT + drawH - (i + 1) * treadH)
        }
        // Soffit line
        val soffitOffset = slabThickness.toFloat() * scale
        path.lineTo(elevL + drawL + soffitOffset * sin(angle), elevT + drawH - drawH - soffitOffset * cos(angle))
        path.lineTo(elevL - soffitOffset * sin(angle), elevT + drawH - soffitOffset)
        path.close()

        canvas.drawPath(path, fillPaint(CONCRETE))
        canvas.drawPath(path, outlineP)

        // Main reinforcement (following slope) — offset by cover
        val rebarP = createPaint(REBAR_BLUE, 2f)
        val nBars = 4
        val coverOffset = cover.toFloat() * scale
        for (i in 0 until nBars) {
            val offset = coverOffset + (i + 1) * (soffitOffset - coverOffset) / (nBars + 1)
            val yOff = offset * cos(angle)
            val xOff = offset * sin(angle)
            canvas.drawLine(
                elevL + xOff + 15f, elevT + drawH - yOff - 15f,
                elevL + drawL - xOff - 15f, elevT + drawH - drawH + yOff + 15f, rebarP
            )
        }

        canvas.drawHDim(elevL, elevL + drawL, elevT + drawH + 20f, "${totalLength.toInt()} mm")
        canvas.drawVDim(elevT, elevT + drawH, elevL - 25f, "${totalHeight.toInt()} mm")

        val titleP = textPaint(Color.WHITE, 22f, true)
        canvas.drawTextCentered("STAIRCASE ELEVATION", elevL + drawL / 2f, elevT - 25f, titleP)

        // Section (right side)
        val secL = W * 0.55f; val secT = 100f
        val secW2 = 250f; val secH2 = slabThickness.toFloat() * scale * 3f
        canvas.drawRect(secL, secT, secL + secW2, secT + secH2, fillPaint(CONCRETE))
        canvas.drawRect(secL, secT, secL + secW2, secT + secH2, outlineP)

        val bR = maxOf(mainDia.toFloat() * 0.3f, 3f)
        for (i in 0 until 5) {
            canvas.drawRebar(secL + 30f + i * 40f, secT + secH2 - 10f, bR, REBAR_BLUE)
        }

        canvas.drawTextCentered("SECTION A-A", secL + secW2 / 2f, secT - 15f, textPaint(DIM_TEXT, 18f, true))

        // Table
        drawRebarTable(canvas,
            x = 80f, y = H * 0.58f,
            data = listOf(
                listOf("Mark", "Dia", "Spacing", "Length"),
                listOf("B1", "${mainDia.toInt()}mm", "${mainSpacing.toInt()}mm c/c", "-"),
                listOf("D1", "${distDia.toInt()}mm", "${distSpacing.toInt()}mm c/c", "-")
            )
        )

        drawTitleBlock(canvas, W - 280f, H - 60f, 280f, 60f, "Stair Detail")
        return bitmap
    }

    // ========== HELPER: Table ==========
    private fun drawRebarTable(canvas: Canvas, x: Float, y: Float, data: List<List<String>>) {
        if (data.isEmpty()) return
        val cols = data[0].size
        val colW = 130f
        val rowH = 28f
        val headerH = 32f
        val totalW = cols * colW
        val totalH = headerH + (data.size - 1) * rowH

        // Background
        canvas.drawRect(x, y, x + totalW, y + totalH, fillPaint(Color.parseColor("#11FFFFFF")))
        // Header
        canvas.drawRect(x, y, x + totalW, y + headerH, fillPaint(TABLE_HEADER))

        val tp = textPaint(DIM_TEXT, 16f)
        val boldTp = textPaint(DIM_TEXT, 16f, true)

        // Headers
        for (c in 0 until cols) {
            canvas.drawTextCentered(data[0][c], x + c * colW + colW / 2f, y + headerH / 2f + 5f, boldTp)
        }

        // Rows
        for (r in 1 until data.size) {
            val ry = y + headerH + (r - 1) * rowH
            if (r % 2 == 0) canvas.drawRect(x, ry, x + totalW, ry + rowH, fillPaint(TABLE_ALT))
            for (c in 0 until cols) {
                canvas.drawTextCentered(data[r][c], x + c * colW + colW / 2f, ry + rowH / 2f + 5f, tp)
            }
        }

        // Border
        canvas.drawRect(x, y, x + totalW, y + totalH, createPaint(Color.parseColor("#66FFFFFF"), 1.5f))
    }

    // ========== HELPER: Title Block ==========
    private fun drawTitleBlock(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, title: String) {
        canvas.drawRect(x, y, x + w, y + h, fillPaint(Color.parseColor("#11FFFFFF")))
        canvas.drawRect(x, y, x + w, y + h, createPaint(DIM_LINE, 1.5f))
        canvas.drawLine(x, y + h * 0.33f, x + w, y + h * 0.33f, createPaint(DIM_LINE, 1f))
        canvas.drawLine(x, y + h * 0.66f, x + w, y + h * 0.66f, createPaint(DIM_LINE, 1f))
        canvas.drawLine(x + w * 0.4f, y, x + w * 0.4f, y + h, createPaint(DIM_LINE, 1f))

        canvas.drawText("Project: CivilEG", x + 8f, y + h * 0.22f, textPaint(DIM_TEXT, 14f))
        canvas.drawText(title, x + w * 0.4f + 8f, y + h * 0.22f, textPaint(DIM_TEXT, 14f, true))
        canvas.drawText("Scale: NTS", x + 8f, y + h * 0.55f, textPaint(DIM_TEXT, 13f))
        canvas.drawText("Generated by Civil EG Pro", x + w * 0.4f + 8f, y + h * 0.55f, textPaint(DIM_TEXT, 13f))
        canvas.drawText("Sheet: 1/1", x + 8f, y + h * 0.88f, textPaint(DIM_TEXT, 13f))
    }

    // ========== HELPER: Circle numbers ==========
    private fun getCircleNumber(n: Int): String {
        // Return numbers 1-20 as circled: ①②③④⑤⑥⑦⑧⑨⑩...
        return when (n) {
            1 -> "\u2460"; 2 -> "\u2461"; 3 -> "\u2462"; 4 -> "\u2463"; 5 -> "\u2464"
            6 -> "\u2465"; 7 -> "\u2466"; 8 -> "\u2467"; 9 -> "\u2468"; 10 -> "\u2469"
            else -> n.toString()
        }
    }

    // ========== GENERATE TANK DRAWING ==========
    fun generateTankDrawing(
        tankType: String,
        length: Double, width: Double, height: Double,
        wallThickness: Double, baseThickness: Double,
        verticalRebarDia: Double, verticalRebarSpacing: Double,
        horizontalRebarDia: Double, horizontalRebarSpacing: Double,
        waterLevel: Double = 0.0,
        foundationDepth: Double = 0.0
    ): Bitmap {
        val W = 1200; val H = 900
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 1.5f)
        val titleP = textPaint(Color.WHITE, 22f, true)

        // === LEFT SIDE: Cross-Section View ===
        val csLeft = 80f; val csTop = 80f
        val totalW = width + 2 * wallThickness
        val totalH = height + baseThickness
        val scale = min(420f / totalW.toFloat(), 350f / totalH.toFloat()) * 0.7f

        val drawW = totalW.toFloat() * scale
        val drawH = totalH.toFloat() * scale
        val wtPx = wallThickness.toFloat() * scale
        val btPx = baseThickness.toFloat() * scale
        val hPx = height.toFloat() * scale

        val baseBottom = csTop + drawH
        val baseTop = baseBottom - btPx
        val wallTop = baseTop - hPx
        val leftWallRight = csLeft + wtPx
        val rightWallLeft = csLeft + drawW - wtPx

        // Soil outside/below
        canvas.drawRect(csLeft - 40f, wallTop - 10f, csLeft, baseBottom, fillPaint(SOIL_BROWN))
        canvas.drawRect(rightWallLeft, wallTop - 10f, rightWallLeft + 40f, baseBottom, fillPaint(SOIL_BROWN))
        if (foundationDepth > 0) {
            val fdPx = foundationDepth.toFloat() * scale
            canvas.drawRect(csLeft - 40f, baseBottom, csLeft + drawW + 40f, baseBottom + fdPx, fillPaint(SOIL_BROWN))
        }

        // Base slab concrete
        canvas.drawRect(csLeft, baseTop, csLeft + drawW, baseBottom, fillPaint(CONCRETE))
        canvas.drawRect(csLeft, baseTop, csLeft + drawW, baseBottom, outlineP)
        canvas.drawHatch(csLeft, baseTop, drawW, btPx, 10f)

        // Left wall concrete
        canvas.drawRect(csLeft, wallTop, leftWallRight, baseTop, fillPaint(CONCRETE))
        canvas.drawRect(csLeft, wallTop, leftWallRight, baseTop, outlineP)
        canvas.drawHatch(csLeft, wallTop, wtPx, hPx, 10f)

        // Right wall concrete
        canvas.drawRect(rightWallLeft, wallTop, csLeft + drawW, baseTop, fillPaint(CONCRETE))
        canvas.drawRect(rightWallLeft, wallTop, csLeft + drawW, baseTop, outlineP)
        canvas.drawHatch(rightWallLeft, wallTop, wtPx, hPx, 10f)

        // Water level inside
        if (waterLevel > 0) {
            val wlPx = (waterLevel / height).coerceIn(0.0, 1.0).toFloat() * hPx
            val waterTop = baseTop - wlPx
            canvas.drawRect(leftWallRight, waterTop, rightWallLeft, baseTop, fillPaint(WATER_BLUE))
        }

        // Vertical rebar circles on walls (inner and outer faces)
        val vRebarR = maxOf(verticalRebarDia.toFloat() * 0.4f, 3f)
        val coverPx = 25f
        var vy = wallTop + coverPx + vRebarR
        while (vy < baseTop - coverPx) {
            // Left wall - inner and outer face
            canvas.drawRebar(csLeft + coverPx + vRebarR, vy, vRebarR, REBAR_BLUE)
            canvas.drawRebar(leftWallRight - coverPx - vRebarR, vy, vRebarR, REBAR_BLUE)
            // Right wall - inner and outer face
            canvas.drawRebar(rightWallLeft + coverPx + vRebarR, vy, vRebarR, REBAR_BLUE)
            canvas.drawRebar(csLeft + drawW - coverPx - vRebarR, vy, vRebarR, REBAR_BLUE)
            vy += maxOf(verticalRebarSpacing.toFloat() * scale / 3f, 20f)
        }

        // Horizontal rebar lines across walls
        val hRebarP = createPaint(TOP_REBAR, 1.5f)
        var hy = wallTop + maxOf(horizontalRebarSpacing.toFloat() * scale / 3f, 20f)
        while (hy < baseTop - 10f) {
            canvas.drawLine(csLeft + coverPx, hy, leftWallRight - coverPx, hy, hRebarP)
            canvas.drawLine(rightWallLeft + coverPx, hy, csLeft + drawW - coverPx, hy, hRebarP)
            hy += maxOf(horizontalRebarSpacing.toFloat() * scale / 3f, 20f)
        }

        // Horizontal rebar circles in base slab
        var bx = csLeft + coverPx
        while (bx < csLeft + drawW - coverPx) {
            canvas.drawRebar(bx, baseTop + btPx / 2f, vRebarR, TOP_REBAR)
            bx += maxOf(horizontalRebarSpacing.toFloat() * scale / 3f, 20f)
        }

        // Cross-section dimensions
        canvas.drawHDim(csLeft, csLeft + drawW, baseBottom + 15f, "${totalW.toInt()} mm")
        canvas.drawVDim(wallTop, baseBottom, csLeft - 30f, "${totalH.toInt()} mm")
        canvas.drawHDim(csLeft, leftWallRight, wallTop - 15f, "${wallThickness.toInt()}", 20f)
        canvas.drawVDim(baseTop, baseBottom, csLeft + drawW + 15f, "${baseThickness.toInt()} mm", 20f)

        canvas.drawTextCentered("TANK CROSS-SECTION", csLeft + drawW / 2f, csTop - 25f, titleP)

        // === RIGHT SIDE: Plan View ===
        val planLeft = W * 0.52f; val planTop = 80f
        val planScale = min(400f / length.toFloat(), 280f / width.toFloat()) * 0.7f
        val pW = length.toFloat() * planScale
        val pH = width.toFloat() * planScale
        val pwt = wallThickness.toFloat() * planScale

        // Outer wall outline
        canvas.drawRect(planLeft, planTop, planLeft + pW, planTop + pH, fillPaint(CONCRETE))
        canvas.drawRect(planLeft, planTop, planLeft + pW, planTop + pH, outlineP)
        canvas.drawHatch(planLeft, planTop, pW, pH, 15f)

        // Inner opening (water area)
        canvas.drawRect(planLeft + pwt, planTop + pwt, planLeft + pW - pwt, planTop + pH - pwt, fillPaint(Color.parseColor("#11224466")))

        // Reinforcement pattern along X (vertical blue lines)
        val rebarP = createPaint(REBAR_BLUE, 1.5f)
        var rx = planLeft + pwt + 15f
        while (rx < planLeft + pW - pwt - 5f) {
            canvas.drawLine(rx, planTop + 5f, rx, planTop + pH - 5f, rebarP)
            rx += maxOf(verticalRebarSpacing.toFloat() * planScale / 4f, 15f)
        }

        // Reinforcement pattern along Y (horizontal light blue lines)
        val rebarP2 = createPaint(TOP_REBAR, 1.2f)
        var ry = planTop + pwt + 15f
        while (ry < planTop + pH - pwt - 5f) {
            canvas.drawLine(planLeft + 5f, ry, planLeft + pW - 5f, ry, rebarP2)
            ry += maxOf(horizontalRebarSpacing.toFloat() * planScale / 4f, 15f)
        }

        canvas.drawHDim(planLeft, planLeft + pW, planTop + pH + 15f, "${length.toInt()} mm")
        canvas.drawVDim(planTop, planTop + pH, planLeft + pW + 15f, "${width.toInt()} mm")

        canvas.drawTextCentered("PLAN VIEW", planLeft + pW / 2f, planTop - 25f, titleP)

        // === Rebar Schedule Table ===
        drawRebarTable(canvas,
            x = 80f, y = H * 0.58f,
            data = listOf(
                listOf("Mark", "Dia", "Direction", "Spacing"),
                listOf("V1", "${verticalRebarDia.toInt()}mm", "Vertical", "${verticalRebarSpacing.toInt()}mm c/c"),
                listOf("H1", "${horizontalRebarDia.toInt()}mm", "Horizontal", "${horizontalRebarSpacing.toInt()}mm c/c")
            )
        )

        // Title block
        drawTitleBlock(canvas, W - 280f, H - 60f, 280f, 60f, "Tank Detail")

        return bitmap
    }

    // ========== GENERATE RETAINING WALL DRAWING ==========
    fun generateRetainingWallDrawing(
        wallHeight: Double,
        wallTopThickness: Double, wallBottomThickness: Double,
        baseWidth: Double, baseThickness: Double,
        toeLength: Double, heelLength: Double,
        mainRebarDia: Double, mainRebarSpacing: Double,
        distRebarDia: Double, distRebarSpacing: Double,
        baseRebarDia: Double, baseRebarSpacing: Double,
        cover: Double = 50.0,
        backfillAngle: Double = 0.3,
        hasKey: Boolean = false, keyDepth: Double = 150.0,
        fsOverturning: Double = 2.0,
        fsSliding: Double = 1.5,
        maxBearingPressure: Double = 0.0,
        allowableBearingPressure: Double = 200.0
    ): Bitmap {
        val W = 1200; val H = 800
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 1.5f)
        val titleP = textPaint(Color.WHITE, 22f, true)

        val marginL = 100f; val marginT = 70f
        val totalH = wallHeight + baseThickness
        val scale = min(500f / baseWidth.toFloat(), 500f / totalH.toFloat()) * 0.65f

        val bwPx = baseWidth.toFloat() * scale
        val bhPx = baseThickness.toFloat() * scale
        val whPx = wallHeight.toFloat() * scale
        val wttPx = wallTopThickness.toFloat() * scale
        val wbtPx = wallBottomThickness.toFloat() * scale
        val toePx = toeLength.toFloat() * scale
        val heelPx = heelLength.toFloat() * scale

        // Base slab position
        val baseLeft = marginL
        val baseTop = marginT + whPx
        val baseRight = baseLeft + bwPx
        val baseBottom = baseTop + bhPx

        // Wall position (stem sits on base; back face at toe-heel boundary)
        val wallBottomRight = baseLeft + toePx
        val wallBottomLeft = wallBottomRight - wbtPx
        val wallTopY = marginT

        // Stem top width centered on bottom
        val wallTopLeft = wallBottomLeft + (wbtPx - wttPx) / 2f
        val wallTopRight = wallTopLeft + wttPx

        // Ground line
        val groundY = baseTop
        val groundRight = baseRight + 80f

        // Soil/backfill (right side, behind wall)
        canvas.drawRect(wallBottomRight, wallTopY - 10f, groundRight, groundY, fillPaint(SOIL_BROWN))
        canvas.drawHatch(wallBottomRight, maxOf(wallTopY, groundY - whPx - 30f), groundRight - wallBottomRight, groundY - maxOf(wallTopY, groundY - whPx - 30f), 12f)

        // Soil pressure triangle (active earth pressure)
        val pressureP = fillPaint(Color.parseColor("#FF5722"))
        canvas.drawPath(Path().apply {
            moveTo(wallBottomRight, groundY)
            lineTo(wallBottomRight, wallTopY)
            lineTo(wallBottomRight + (groundY - wallTopY) * tan(backfillAngle.toFloat()), groundY)
            close()
        }, pressureP)

        // Ground line
        canvas.drawLine(baseLeft - 30f, groundY, groundRight, groundY, createPaint(SUPPORT, 2f))

        // Soil below base
        canvas.drawRect(baseLeft - 30f, baseBottom, baseRight + 30f, baseBottom + 40f, fillPaint(SOIL_BROWN))

        // Base slab
        canvas.drawRect(baseLeft, baseTop, baseRight, baseBottom, fillPaint(CONCRETE))
        canvas.drawRect(baseLeft, baseTop, baseRight, baseBottom, outlineP)
        canvas.drawHatch(baseLeft, baseTop, bwPx, bhPx, 10f)

        // Shear key
        if (hasKey) {
            val keyW = 30f
            val keyPx = min(keyDepth.toFloat() * scale * 0.5f, 40f)
            val keyX = wallBottomRight - keyW / 2f
            canvas.drawRect(keyX, baseBottom, keyX + keyW, baseBottom + keyPx, fillPaint(CONCRETE))
            canvas.drawRect(keyX, baseBottom, keyX + keyW, baseBottom + keyPx, outlineP)
        }

        // Trapezoidal stem
        canvas.drawPath(Path().apply {
            moveTo(wallBottomLeft, baseTop)
            lineTo(wallBottomRight, baseTop)
            lineTo(wallTopRight, wallTopY)
            lineTo(wallTopLeft, wallTopY)
            close()
        }, fillPaint(CONCRETE))
        canvas.drawPath(Path().apply {
            moveTo(wallBottomLeft, baseTop)
            lineTo(wallBottomRight, baseTop)
            lineTo(wallTopRight, wallTopY)
            lineTo(wallTopLeft, wallTopY)
            close()
        }, outlineP)
        canvas.drawHatch(wallTopLeft, wallTopY, wttPx, whPx, 10f)

        // Main rebar on tension side (inside face = right side of stem)
        val mRebarR = maxOf(mainRebarDia.toFloat() * 0.4f, 3f)
        val coverPx = cover.toFloat() * scale * 0.3f
        var my = wallTopY + coverPx + mRebarR
        while (my < baseTop - coverPx) {
            val t = (my - wallTopY) / whPx
            val faceX = wallTopRight + t * (wallBottomRight - wallTopRight) - coverPx - mRebarR
            canvas.drawRebar(faceX, my, mRebarR, REBAR_BLUE)
            my += maxOf(mainRebarSpacing.toFloat() * scale / 3f, 18f)
        }

        // Distribution rebar (outside face = left side of stem)
        val dRebarR = maxOf(distRebarDia.toFloat() * 0.35f, 2.5f)
        my = wallTopY + coverPx + dRebarR
        while (my < baseTop - coverPx) {
            val t = (my - wallTopY) / whPx
            val faceX = wallTopLeft + t * (wallBottomLeft - wallTopLeft) + coverPx + dRebarR
            canvas.drawRebar(faceX, my, dRebarR, SECONDARY_RED)
            my += maxOf(distRebarSpacing.toFloat() * scale / 3f, 18f)
        }

        // Base reinforcement (bottom bars)
        val bRebarR = maxOf(baseRebarDia.toFloat() * 0.35f, 2.5f)
        var bx = baseLeft + coverPx
        while (bx < baseRight - coverPx) {
            canvas.drawRebar(bx, baseBottom - coverPx - bRebarR, bRebarR, REBAR_BLUE)
            bx += maxOf(baseRebarSpacing.toFloat() * scale / 3f, 18f)
        }

        // Dimensions
        canvas.drawVDim(wallTopY, baseBottom, wallTopLeft - 35f, "${totalH.toInt()} mm")
        canvas.drawHDim(baseLeft, baseRight, baseBottom + 20f, "${baseWidth.toInt()} mm")
        canvas.drawHDim(baseLeft, baseLeft + toePx, baseBottom + 50f, "Toe: ${toeLength.toInt()}", 20f)
        canvas.drawHDim(baseRight - heelPx, baseRight, baseBottom + 50f, "Heel: ${heelLength.toInt()}", 20f)
        canvas.drawVDim(wallTopY, wallTopY + whPx, wallTopRight + 25f, "t=${wallTopThickness.toInt()}", 15f)

        canvas.drawTextCentered("RETAINING WALL SECTION", (baseLeft + baseRight) / 2f, marginT - 25f, titleP)

        // Rebar table
        drawRebarTable(canvas,
            x = 80f, y = H * 0.70f,
            data = listOf(
                listOf("Mark", "Dia", "Spacing", "Location"),
                listOf("M1", "${mainRebarDia.toInt()}mm", "${mainRebarSpacing.toInt()}mm c/c", "Stem (inside face)"),
                listOf("D1", "${distRebarDia.toInt()}mm", "${distRebarSpacing.toInt()}mm c/c", "Stem (outside face)"),
                listOf("B1", "${baseRebarDia.toInt()}mm", "${baseRebarSpacing.toInt()}mm c/c", "Base (bottom)")
            )
        )

        drawTitleBlock(canvas, W - 280f, H - 60f, 280f, 60f, "Retaining Wall Detail")

        return bitmap
    }

    // ========== GENERATE STEEL MEMBER DRAWING ===========
    // 2026-08-04 v3 — Fully rewritten to match ProfessionalSteelDrawing (Compose Canvas)
    // 4-zone layout: Elevation (top) | Cross-Section (bottom-left) | Connection (bottom-right) | Properties Table (bottom strip)
    // Supports: I-Beam, HSS/RHS, Channel, Angle section types
    fun generateSteelDrawing(
        sectionName: String,
        sectionHeight: Double, flangeWidth: Double,
        webThickness: Double, flangeThickness: Double,
        memberLength: Double,
        isSafe: Boolean,
        utilizationRatio: Double = 0.0,
        // New parameters to match on-screen drawing
        sectionType: String = "I-BEAM",
        radius: Double = 0.0,
        area: Double = 0.0,
        ix: Double = 0.0,
        sx: Double = 0.0,
        zx: Double = 0.0,
        weightPerMeter: Double = 0.0,
        boltDia: Double = 20.0,
        boltCount: Int = 4,
        boltGauge: Double = 90.0,
        boltPitch: Double = 75.0,
        endPlateThickness: Double = 12.0,
        hasStiffener: Boolean = false,
        weldSize: Double = 6.0,
        isColumn: Boolean = false
    ): Bitmap {
        val W = 1400; val H = 900
        val (bitmap, canvas) = createCanvas(W, H)

        // Steel color palette (matching ProfessionalSteelDrawing.kt)
        val steelGray = Color.parseColor("#7F8C8D")
        val steelDarkGray = Color.parseColor("#2C3E50")
        val sectionFill = Color.parseColor("#3D3D3D")
        val boltOrange = Color.parseColor("#F39C12")
        val weldRed = Color.parseColor("#E74C3C")
        val plateGray = Color.parseColor("#95A5A6")
        val dimWhite = Color.WHITE
        val extGray = Color.parseColor("#AAAAAA")
        val tblHeaderBg = Color.parseColor("#33FFFFFF")
        val tblRowAlt = Color.parseColor("#1AFFFFFF")

        val outlineP = createPaint(dimWhite, 1.2f)
        val thinP = createPaint(extGray, 0.8f)
        val dimP = createPaint(extGray, 1f)
        val titleP = textPaint(dimWhite, 22f, true)
        val labelP = textPaint(dimWhite, 16f, true)
        val smallP = textPaint(extGray, 14f)
        val tinyP = textPaint(extGray, 12f)

        // Section type detection
        val isIBeam = sectionType.contains("I-BEAM", true) || sectionType.contains("W-SECTION", true) ||
            sectionType.contains("IPE", true) || sectionType.contains("HEA", true) || sectionType.contains("HEB", true) ||
            sectionType.contains("I/H", true) || sectionType.contains("Plate Girder", true)
        val isHSS = sectionType.contains("HSS", true) || sectionType.contains("RHS", true) ||
            sectionType.contains("SHS", true) || sectionType.contains("Circular Hollow", true) ||
            sectionType.contains("Pipe", true)
        val isChannel = sectionType.contains("CHANNEL", true) || sectionType.contains("UPN", true) || sectionType.contains("C Channel", true)
        val isAngle = sectionType.contains("ANGLE", true) || sectionType.contains("L ", true) || sectionType.contains("L Angle", true)

        // ── Layout zones (matching ProfessionalSteelDrawing) ──
        val margin = 40f
        val tableHeight = 110f
        val tableTop = H - tableHeight - margin

        // Elevation view (top half)
        val elevLeft = margin + 60f
        val elevRight = W - margin - 60f
        val elevTop = margin + 50f
        val elevBottom = H * 0.42f

        // Cross-section (bottom-left)
        val sectLeft = margin + 30f
        val sectRight = W * 0.46f
        val sectTop = elevBottom + 50f
        val sectBottom = tableTop - 15f

        // Connection detail (bottom-right)
        val connLeft = W * 0.54f
        val connRight = W - margin - 30f
        val connTop = sectTop
        val connBottom = sectBottom

        // ════════════════════════════════════════════════════════
        //  1. ELEVATION VIEW
        // ════════════════════════════════════════════════════════
        val viewW = elevRight - elevLeft
        val viewH = elevBottom - elevTop
        val scaleX = viewW / memberLength.toFloat()
        val scaleY = (viewH * 0.4f) / sectionHeight.toFloat()
        val scale = minOf(scaleX, scaleY)

        val sLen = memberLength.toFloat() * scale
        val sDep = sectionHeight.toFloat() * scale
        val sTf = flangeThickness.toFloat() * scale
        val sEp = endPlateThickness.toFloat() * scale * 3f  // Slightly exaggerated for visibility

        val cx = (elevLeft + elevRight) / 2f
        val cy = (elevTop + elevBottom) / 2f
        val mLeft = cx - sLen / 2f
        val mRight = cx + sLen / 2f
        val mTop = cy - sDep / 2f
        val mBot = cy + sDep / 2f

        // Main body rectangle
        canvas.drawRect(mLeft, mTop, mRight, mBot, fillPaint(steelGray))
        canvas.drawRect(mLeft, mTop, mRight, mBot, createPaint(Color.argb(128, 255, 255, 255), 1.2f))

        // Flange lines for I-Beam / Channel
        if (isIBeam || isChannel) {
            canvas.drawLine(mLeft, mTop + sTf, mRight, mTop + sTf, createPaint(steelDarkGray, 1f))
            canvas.drawLine(mLeft, mBot - sTf, mRight, mBot - sTf, createPaint(steelDarkGray, 1f))
        }

        // End plates (left and right)
        canvas.drawRect(mLeft - sEp, mTop - 8f, mLeft, mBot + 8f, fillPaint(plateGray))
        canvas.drawRect(mLeft - sEp, mTop - 8f, mLeft, mBot + 8f, outlineP)
        canvas.drawRect(mRight, mTop - 8f, mRight + sEp, mBot + 8f, fillPaint(plateGray))
        canvas.drawRect(mRight, mTop - 8f, mRight + sEp, mBot + 8f, outlineP)

        // Dimensions
        canvas.drawHDim(mLeft, mRight, mBot + 10f, "${memberLength.toInt()} mm")
        canvas.drawVDim(mTop, mBot, mRight + sEp + 15f, "d=${sectionHeight.toInt()}")

        // Section name label
        canvas.drawTextCentered(sectionName, cx, mTop - 12f, textPaint(Color.parseColor("#00BCD4"), 16f, true))

        // ELEVATION label
        canvas.drawTextCentered("ELEVATION", (elevLeft + elevRight) / 2f, elevTop - 20f, labelP)

        // ════════════════════════════════════════════════════════
        //  2. CROSS-SECTION VIEW (Section A-A)
        // ════════════════════════════════════════════════════════
        val secCx = (sectLeft + sectRight) / 2f
        val secCy = (sectTop + sectBottom) / 2f
        val secScale = minOf((sectRight - sectLeft) * 0.6f / flangeWidth.toFloat(), (sectBottom - sectTop) * 0.6f / sectionHeight.toFloat())
        val sW = flangeWidth.toFloat() * secScale
        val sH = sectionHeight.toFloat() * secScale
        val sFT = flangeThickness.toFloat() * secScale
        val sWT = webThickness.toFloat() * secScale

        val scx = secCx - sW / 2f
        val scy = secCy - sH / 2f

        // Outer rectangle (filled)
        canvas.drawRect(scx, scy, scx + sW, scy + sH, fillPaint(sectionFill))
        canvas.drawRect(scx, scy, scx + sW, scy + sH, createPaint(steelGray, 1.5f))

        // I-beam internal web lines
        if (isIBeam) {
            val halfTw = sWT / 2f
            canvas.drawLine(secCx - halfTw, scy + sFT, secCx - halfTw, scy + sH - sFT, createPaint(steelGray, 1f))
            canvas.drawLine(secCx + halfTw, scy + sFT, secCx + halfTw, scy + sH - sFT, createPaint(steelGray, 1f))
        }

        // Channel: web lines offset to one side
        if (isChannel) {
            val halfTw = sWT / 2f
            canvas.drawLine(scx + sW * 0.3f - halfTw, scy + sFT, scx + sW * 0.3f - halfTw, scy + sH - sFT, createPaint(steelGray, 1f))
            canvas.drawLine(scx + sW * 0.3f + halfTw, scy + sFT, scx + sW * 0.3f + halfTw, scy + sH - sFT, createPaint(steelGray, 1f))
        }

        // HSS/Pipe: inner rectangle (hollow section)
        if (isHSS) {
            val innerW = sW - 2 * sWT
            val innerH = sH - 2 * sWT
            if (innerW > 0 && innerH > 0) {
                canvas.drawRect(scx + sWT, scy + sWT, scx + sWT + innerW, scy + sWT + innerH, createPaint(BG_COLOR, 0.5f))
                canvas.drawRect(scx + sWT, scy + sWT, scx + sWT + innerW, scy + sWT + innerH, thinP)
            }
        }

        // Cross-section dimensions
        canvas.drawTextCentered("b=$flangeWidth", secCx, scy + sH + 18f, smallP)
        // Vertical label "d=xxx" drawn rotated via text
        canvas.drawText("d=$sectionHeight", scx - 18f, secCy + 5f, smallP)

        // SECTION A-A label
        canvas.drawTextCentered("SECTION A-A", secCx, sectTop - 20f, labelP)

        // ════════════════════════════════════════════════════════
        //  3. CONNECTION DETAIL (Bolts on End Plate)
        // ════════════════════════════════════════════════════════
        val connCx = (connLeft + connRight) / 2f
        val connCy = (connTop + connBottom) / 2f
        val connScale = minOf((connRight - connLeft) * 0.7f / flangeWidth.toFloat(), (connBottom - connTop) * 0.7f / sectionHeight.toFloat())
        val cW = flangeWidth.toFloat() * connScale
        val cH = sectionHeight.toFloat() * connScale

        val epx = connCx - cW / 2f
        val epy = connCy - cH / 2f

        // End plate rectangle (transparent fill)
        canvas.drawRect(epx, epy, epx + cW, epy + cH, fillPaint(Color.argb(80, 149, 165, 166)))
        canvas.drawRect(epx, epy, epx + cW, epy + cH, createPaint(plateGray, 1.2f))

        // Center lines (dashed)
        val dashP = createPaint(extGray, 0.8f).apply {
            pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
        }
        canvas.drawLine(connCx, epy - 15f, connCx, epy + cH + 15f, dashP)
        canvas.drawLine(epx - 15f, connCy, epx + cW + 15f, connCy, dashP)

        // Bolts (2 per row, arranged vertically)
        val boltR = 6f
        val safeBoltCount = maxOf(boltCount, 1)
        for (i in 0 until safeBoltCount) {
            val by = epy + 20f + i * (cH - 40f) / maxOf(safeBoltCount - 1, 1)
            // Left bolt
            canvas.drawCircle(connCx - cW * 0.25f, by, boltR, fillPaint(boltOrange))
            canvas.drawCircle(connCx - cW * 0.25f, by, boltR, createPaint(Color.argb(180, 255, 255, 255), 1f))
            // Right bolt
            canvas.drawCircle(connCx + cW * 0.25f, by, boltR, fillPaint(boltOrange))
            canvas.drawCircle(connCx + cW * 0.25f, by, boltR, createPaint(Color.argb(180, 255, 255, 255), 1f))
        }

        // Weld symbol (if weldSize > 0)
        if (weldSize > 0) {
            val weldLabel = "weld=${weldSize.toInt()}mm"
            canvas.drawTextCentered(weldLabel, connCx, epy + cH + 20f, tinyP)
        }

        // Bolt label
        if (boltCount > 0) {
            val boltLabel = "${boltCount}x\u00D8${boltDia.toInt()}"
            canvas.drawTextCentered(boltLabel, connCx, epy - 8f, smallP)
        }

        // CONNECTION label
        canvas.drawTextCentered("CONNECTION", connCx, connTop - 20f, labelP)

        // ════════════════════════════════════════════════════════
        //  4. PROPERTIES TABLE (bottom strip, matching on-screen)
        // ════════════════════════════════════════════════════════
        val tblX = margin
        val tblW = W - margin * 2f

        // Table header background
        canvas.drawRect(tblX, tableTop, tblX + tblW, tableTop + 28f, fillPaint(tblHeaderBg))
        canvas.drawTextCentered("STEEL PROPERTIES: $sectionName", tblX + tblW / 2f, tableTop + 20f, textPaint(dimWhite, 14f, true))

        // Table data rows in 2 columns
        val rowY1 = tableTop + 48f
        val rowY2 = tableTop + 70f
        val colX1 = tblX + 20f
        val colX2 = tblX + tblW / 2f + 20f

        // Convert from mm² to cm² and mm⁴ to cm⁴ for display
        val areaStr = if (area > 0) "%.1f".format(area / 100.0) else "--"
        val ixStr = if (ix > 0) "%.0f".format(ix / 1e8) else "--"
        val wStr = if (weightPerMeter > 0) "%.1f".format(weightPerMeter) else "--"

        canvas.drawText("Area: $areaStr cm\u00B2", colX1, rowY1, smallP)
        canvas.drawText("Inertia Ix: $ixStr cm\u2074", colX2, rowY1, smallP)
        canvas.drawText("Weight: $wStr kg/m", colX1, rowY2, smallP)
        canvas.drawText("Length: ${memberLength.toInt()} mm", colX2, rowY2, smallP)

        // Table border
        canvas.drawRect(tblX, tableTop, tblX + tblW, tableTop + tableHeight, createPaint(Color.argb(50, 170, 170, 170), 1f))

        // ── Title Block ──
        drawTitleBlock(canvas, W - 300f, H - 55f, 300f, 55f, "Steel Detail")

        // ── SAFE/UNSAFE badge ──
        val badgeX = W - 150f
        val badgeY = margin + 10f
        if (isSafe) {
            canvas.drawText("\u2713", badgeX, badgeY + 20f, textPaint(Color.parseColor("#2ECC71"), 36f, true))
            canvas.drawText("SAFE", badgeX + 30f, badgeY + 20f, textPaint(Color.parseColor("#2ECC71"), 18f, true))
        } else {
            canvas.drawText("\u2717", badgeX, badgeY + 20f, textPaint(SECONDARY_RED, 36f, true))
            canvas.drawText("UNSAFE", badgeX + 30f, badgeY + 20f, textPaint(SECONDARY_RED, 18f, true))
        }

        return bitmap
    }

    // ========== GENERATE ADVANCED BEAM DRAWING WITH BMD/SFD ==========
    fun generateBeamDrawingWithDiagrams(
        beamWidth: Double, beamDepth: Double, span: Double,
        mainRebarDia: Double, mainRebarCount: Int,
        stirrupDia: Double, stirrupSpacing: Double,
        cover: Double = 50.0,
        hasTopSteel: Boolean = false, topRebarDia: Double = 0.0, topRebarCount: Int = 0,
        momentPoints: List<Pair<Double, Double>> = emptyList(),
        shearPoints: List<Pair<Double, Double>> = emptyList(),
        maxMoment: Double = 0.0,
        maxShear: Double = 0.0,
        isSafe: Boolean = true
    ): Bitmap {
        val W = 1200; val H = 1400
        val (bitmap, canvas) = createCanvas(W, H)

        val outlineP = createPaint(Color.WHITE, 1.5f)
        val titleP = textPaint(Color.WHITE, 22f, true)
        val subtitleP = textPaint(DIM_TEXT, 16f, true)

        // ============ PART 1: BEAM ELEVATION (top half) ============
        canvas.drawTextCentered("BEAM REINFORCEMENT DETAIL", W / 2f, 30f, titleP)

        val marginL = 80f; val marginR = 80f; val marginT = 60f
        val mainBottom = 420f
        val availW = W - marginL - marginR - 60f
        val availH = mainBottom - marginT - 40f
        val scaleW = availW / span.toFloat()
        val scaleH = availH / beamDepth.toFloat()
        val scale = min(scaleW, scaleH) * 0.75f

        val bDrawW = span.toFloat() * scale
        val bDrawH = beamDepth.toFloat() * scale
        val bDrawD = beamWidth.toFloat() * scale * 0.3f
        val bLeft = marginL + 40f
        val bTop = marginT + 30f
        val bRight = bLeft + bDrawW
        val bBottom = bTop + bDrawH

        // 3D beam body
        canvas.drawRect(bLeft, bTop, bRight, bBottom, fillPaint(CONCRETE))
        canvas.drawPath(Path().apply {
            moveTo(bLeft, bTop); lineTo(bLeft + bDrawD * 0.3f, bTop - bDrawD * 0.2f)
            lineTo(bRight + bDrawD * 0.3f, bTop - bDrawD * 0.2f); lineTo(bRight, bTop); close()
        }, fillPaint(CONCRETE_TOP))
        canvas.drawPath(Path().apply {
            moveTo(bRight, bTop); lineTo(bRight + bDrawD * 0.3f, bTop - bDrawD * 0.2f)
            lineTo(bRight + bDrawD * 0.3f, bBottom - bDrawD * 0.2f); lineTo(bRight, bBottom); close()
        }, fillPaint(CONCRETE_SIDE))
        canvas.drawRect(bLeft, bTop, bRight, bBottom, outlineP)

        // Supports
        val supportSize = 20f
        canvas.drawPath(Path().apply {
            moveTo(bLeft - supportSize/2, bBottom); lineTo(bLeft + supportSize/2, bBottom)
            lineTo(bLeft, bBottom + supportSize); close()
        }, fillPaint(SUPPORT))
        canvas.drawLine(bLeft - supportSize, bBottom + supportSize, bLeft + supportSize, bBottom + supportSize, outlineP)
        canvas.drawPath(Path().apply {
            moveTo(bRight - supportSize/2, bBottom); lineTo(bRight + supportSize/2, bBottom)
            lineTo(bRight, bBottom + supportSize * 0.7f); close()
        }, fillPaint(SUPPORT))
        canvas.drawCircle(bRight - 6f, bBottom + supportSize * 0.7f + 4f, 3f, fillPaint(SUPPORT))
        canvas.drawCircle(bRight + 6f, bBottom + supportSize * 0.7f + 4f, 3f, fillPaint(SUPPORT))
        canvas.drawLine(bRight - supportSize, bBottom + supportSize, bRight + supportSize, bBottom + supportSize, outlineP)

        // Support labels
        canvas.drawTextCentered("Pin", bLeft, bBottom + supportSize + 16f, textPaint(DIM_TEXT, 14f))
        canvas.drawTextCentered("Roller", bRight, bBottom + supportSize + 16f, textPaint(DIM_TEXT, 14f))

        // Load arrows (distributed)
        val arrowP = createPaint(SECONDARY_RED, 1.2f)
        val numArrows = 12
        for (i in 0..numArrows) {
            val ax = bLeft + i * bDrawW / numArrows
            canvas.drawLine(ax, bTop - 25f, ax, bTop - 2f, arrowP)
            // Arrowhead
            canvas.drawPath(Path().apply {
                moveTo(ax, bTop - 2f); lineTo(ax - 3f, bTop - 8f); lineTo(ax + 3f, bTop - 8f); close()
            }, fillPaint(SECONDARY_RED))
        }
        canvas.drawLine(bLeft, bTop - 25f, bRight, bTop - 25f, arrowP)
        canvas.drawTextCentered("w (UDL)", (bLeft + bRight) / 2f, bTop - 32f, textPaint(SECONDARY_RED, 16f, true))

        // Main reinforcement (bottom)
        val rebarR = maxOf(mainRebarDia.toFloat() / 2f * scale * 0.5f, 3f)
        val rebarY = bBottom - cover.toFloat() * scale - rebarR
        val spacing = (bDrawW - 2 * cover.toFloat() * scale) / maxOf(mainRebarCount - 1, 1)
        for (i in 0 until mainRebarCount) {
            val rx = bLeft + cover.toFloat() * scale + i * spacing
            canvas.drawRebar(rx, rebarY, rebarR, REBAR_BLUE)
        }

        // Stirrups with spacing annotation
        val stirrupP = createPaint(STIRRUP, 1.5f)
        val stirrupY1 = bTop + cover.toFloat() * scale
        val stirrupY2 = bBottom - cover.toFloat() * scale
        val stirrupSpacingPx = stirrupSpacing.toFloat() * scale
        var sx = bLeft + stirrupSpacingPx
        var stirrupCount = 0
        while (sx < bRight - stirrupSpacingPx) {
            canvas.drawRect(sx - 2f, stirrupY1, sx + 2f, stirrupY2, stirrupP)
            sx += stirrupSpacingPx
            stirrupCount++
        }

        // Stirrup spacing dimension
        if (stirrupCount > 1) {
            val dimY = bTop - 5f
            canvas.drawHDim(
                bLeft + stirrupSpacingPx, bLeft + stirrupSpacingPx * 2,
                dimY, "@${stirrupSpacing.toInt()}", -18f, createPaint(STIRRUP, 1f)
            )
        }

        // Top reinforcement
        if (hasTopSteel && topRebarCount > 0) {
            val topR = maxOf(topRebarDia.toFloat() / 2f * scale * 0.5f, 3f)
            val topY = bTop + cover.toFloat() * scale + topR
            val topSpacing = (bDrawW - 2 * cover.toFloat() * scale) / maxOf(topRebarCount - 1, 1)
            // Draw top bars at support zones only (L/3 each end)
            val supportZone = bDrawW / 3f
            for (i in 0 until topRebarCount) {
                val rx = bLeft + cover.toFloat() * scale + i * topSpacing
                // Left support zone
                if (rx < bLeft + supportZone) canvas.drawRebar(rx, topY, topR, TOP_REBAR)
                // Right support zone
                if (rx > bRight - supportZone) canvas.drawRebar(rx, topY, topR, TOP_REBAR)
            }
            // Label
            canvas.drawTextCentered("Top bars (L/3 zones)", (bLeft + bLeft + supportZone) / 2f, topY - rebarR - 8f, textPaint(TOP_REBAR, 13f))
        }

        // Dimensions
        canvas.drawHDim(bLeft, bRight, bBottom + supportSize + 28f, "L = ${span.toInt()} mm")
        canvas.drawVDim(bTop, bBottom, bLeft - 25f, "h=${beamDepth.toInt()}")
        canvas.drawVDim(bTop, bTop + cover.toFloat() * scale, bRight + 25f, "c=${cover.toInt()}")

        // Cross-section inset
        val csX = 80f; val csY = 480f; val csScale = 0.45f
        val csW = beamWidth.toFloat() * scale * csScale
        val csH = beamDepth.toFloat() * scale * csScale

        canvas.drawRect(csX, csY, csX + csW, csY + csH, fillPaint(CONCRETE))
        canvas.drawRect(csX, csY, csX + csW, csY + csH, outlineP)

        val csCover = cover.toFloat() * scale * csScale
        canvas.drawRect(csX + csCover, csY + csCover, csX + csW - csCover, csY + csH - csCover, createPaint(STIRRUP, 1.5f))

        val csRebarR = maxOf(mainRebarDia.toFloat() * csScale * 0.3f, 3f)
        val csBarY = csY + csH - csCover - csRebarR
        for (i in 0 until minOf(mainRebarCount, 6)) {
            val rx = if (mainRebarCount <= 2) {
                csX + csW / 2f
            } else {
                csX + csCover + csRebarR + i * (csW - 2 * csCover - 2 * csRebarR) / maxOf(mainRebarCount - 1, 1)
            }
            canvas.drawRebar(rx, csBarY, csRebarR, REBAR_BLUE)
        }
        if (hasTopSteel && topRebarCount > 0) {
            val topR2 = maxOf(topRebarDia.toFloat() * csScale * 0.3f, 3f)
            val topY2 = csY + csCover + topR2
            for (i in 0 until minOf(topRebarCount, 4)) {
                val rx = if (topRebarCount <= 2) {
                    csX + csW / 2f
                } else {
                    csX + csCover + topR2 + i * (csW - 2 * csCover - 2 * topR2) / maxOf(topRebarCount - 1, 1)
                }
                canvas.drawRebar(rx, topY2, topR2, TOP_REBAR)
            }
        }

        canvas.drawTextCentered("Section A-A", csX + csW / 2f, csY - 10f, textPaint(DIM_TEXT, 15f, true))
        canvas.drawVDim(csY, csY + csH, csX + csW + 15f, "b=${beamWidth.toInt()}")
        canvas.drawVDim(csY, csY + csH, csX - 20f, "h=${beamDepth.toInt()}")

        // Reinforcement schedule table
        drawRebarTable(canvas,
            x = W * 0.35f, y = 470f,
            data = listOf(
                listOf("Mark", "Dia", "No.", "Spacing", "Length", "Type"),
                listOf("B1", "${mainRebarDia.toInt()}", "$mainRebarCount", "-", "${span.toInt()} mm", "Main"),
                listOf("S1", "${stirrupDia.toInt()}", "$stirrupCount", "@${stirrupSpacing.toInt()}", "-", "Stirrup")
            ) + if (hasTopSteel && topRebarCount > 0)
                listOf(listOf("T1", "${topRebarDia.toInt()}", "$topRebarCount", "-", "${(span/3).toInt()} mm", "Top"))
            else emptyList()
        )

        // Status badge
        val statusColor = if (isSafe) Color.parseColor("#2ECC71") else SECONDARY_RED
        val statusText = if (isSafe) "SAFE" else "UNSAFE"
        canvas.drawText(statusText, W - 130f, 480f, textPaint(statusColor, 24f, true))
        canvas.drawText("UR: ${"%.0f".format(if (maxMoment > 0) 0.0 else 0.0)}%", W - 140f, 500f, textPaint(DIM_TEXT, 14f))

        // ============ PART 2: BENDING MOMENT DIAGRAM ============
        val bmdTop = 580f
        val bmdHeight = 180f
        val bmdLeft = marginL + 40f
        val bmdWidth = bDrawW

        // BMD background
        canvas.drawRect(bmdLeft - 10f, bmdTop - 10f, bmdLeft + bmdWidth + 10f, bmdTop + bmdHeight + 10f,
            fillPaint(Color.parseColor("#0D1117")))
        canvas.drawRect(bmdLeft - 10f, bmdTop - 10f, bmdLeft + bmdWidth + 10f, bmdTop + bmdHeight + 10f,
            createPaint(Color.parseColor("#333333"), 0.5f))

        canvas.drawText("BENDING MOMENT DIAGRAM (BMD)", bmdLeft + bmdWidth / 2f, bmdTop - 2f,
            textPaint(Color.parseColor("#4A90D9"), 14f, true))

        // Baseline
        canvas.drawLine(bmdLeft, bmdTop + bmdHeight, bmdLeft + bmdWidth, bmdTop + bmdHeight,
            createPaint(DIM_LINE, 0.5f))

        if (momentPoints.isNotEmpty()) {
            val maxM = momentPoints.maxOfOrNull { it.second } ?: 1.0
            val bmdScale = (bmdHeight - 30f) / maxM.toFloat()

            // Filled area
            val bmdFill = fillPaint(Color.parseColor("#1A3A5C"))
            val bmdLine = createPaint(Color.parseColor("#4A90D9"), 2f)
            val bmdPath = Path()
            bmdPath.moveTo(bmdLeft, bmdTop + bmdHeight)

            for (pt in momentPoints) {
                val px = bmdLeft + pt.first.toFloat() / span.toFloat() * bmdWidth
                val py = bmdTop + bmdHeight - pt.second.toFloat() * bmdScale
                bmdPath.lineTo(px, py)
            }
            bmdPath.lineTo(bmdLeft + bmdWidth, bmdTop + bmdHeight)
            bmdPath.close()
            canvas.drawPath(bmdPath, bmdFill)

            // Line
            val linePath = Path()
            for ((i, pt) in momentPoints.withIndex()) {
                val px = bmdLeft + pt.first.toFloat() / span.toFloat() * bmdWidth
                val py = bmdTop + bmdHeight - pt.second.toFloat() * bmdScale
                if (i == 0) linePath.moveTo(px, py) else linePath.lineTo(px, py)
            }
            canvas.drawPath(linePath, bmdLine)

            // Max moment annotation
            val maxPt = momentPoints.maxByOrNull { it.second }
            if (maxPt != null) {
                val px = bmdLeft + maxPt.first.toFloat() / span.toFloat() * bmdWidth
                val py = bmdTop + bmdHeight - maxPt.second.toFloat() * bmdScale
                canvas.drawText("M_max = ${"%.1f".format(maxPt.second)} kN.m", px + 10f, py - 5f,
                    textPaint(Color.parseColor("#4A90D9"), 14f, true))
                // Dashed line from peak to baseline
                canvas.drawLine(px, py, px, bmdTop + bmdHeight,
                    createPaint(Color.parseColor("#4A90D9"), 0.8f).apply {
                        pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
                    })
            }
        }

        // ============ PART 3: SHEAR FORCE DIAGRAM ============
        val sfdTop = bmdTop + bmdHeight + 40f
        val sfdHeight = 180f

        canvas.drawRect(bmdLeft - 10f, sfdTop - 10f, bmdLeft + bmdWidth + 10f, sfdTop + sfdHeight + 10f,
            fillPaint(Color.parseColor("#0D1117")))
        canvas.drawRect(bmdLeft - 10f, sfdTop - 10f, bmdLeft + bmdWidth + 10f, sfdTop + sfdHeight + 10f,
            createPaint(Color.parseColor("#333333"), 0.5f))

        canvas.drawText("SHEAR FORCE DIAGRAM (SFD)", bmdLeft + bmdWidth / 2f, sfdTop - 2f,
            textPaint(Color.parseColor("#E74C3C"), 14f, true))

        // Center line (zero line)
        val sfdCenterY = sfdTop + sfdHeight / 2f
        canvas.drawLine(bmdLeft, sfdCenterY, bmdLeft + bmdWidth, sfdCenterY,
            createPaint(DIM_LINE, 0.5f).apply { pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f) })

        if (shearPoints.isNotEmpty()) {
            val maxV = maxOf(
                shearPoints.maxOfOrNull { kotlin.math.abs(it.second) } ?: 1.0,
                1.0
            )
            val sfdScale = (sfdHeight / 2f - 20f) / maxV.toFloat()

            // Positive area (fill above center line)
            val sfdPosPath = Path()
            sfdPosPath.moveTo(bmdLeft, sfdCenterY)
            for (pt in shearPoints) {
                val px = bmdLeft + pt.first.toFloat() / span.toFloat() * bmdWidth
                val py = sfdCenterY - pt.second.toFloat() * sfdScale
                sfdPosPath.lineTo(px, py.coerceIn(sfdTop + 5f, sfdTop + sfdHeight - 5f))
            }
            sfdPosPath.lineTo(bmdLeft + bmdWidth, sfdCenterY)
            sfdPosPath.close()
            canvas.drawPath(sfdPosPath, fillPaint(Color.parseColor("#3D1111")))

            // Line
            val sfdLine = createPaint(Color.parseColor("#E74C3C"), 2f)
            val sfdPath = Path()
            for ((i, pt) in shearPoints.withIndex()) {
                val px = bmdLeft + pt.first.toFloat() / span.toFloat() * bmdWidth
                val py = sfdCenterY - pt.second.toFloat() * sfdScale
                if (i == 0) sfdPath.moveTo(px, py.coerceIn(sfdTop + 5f, sfdTop + sfdHeight - 5f))
                else sfdPath.lineTo(px, py.coerceIn(sfdTop + 5f, sfdTop + sfdHeight - 5f))
            }
            canvas.drawPath(sfdPath, sfdLine)

            // Max shear annotations
            val maxVPt = shearPoints.maxByOrNull { kotlin.math.abs(it.second) }
            if (maxVPt != null) {
                val px = bmdLeft + maxVPt.first.toFloat() / span.toFloat() * bmdWidth
                val py = sfdCenterY - maxVPt.second.toFloat() * sfdScale
                canvas.drawText("V_max = ${"%.1f".format(maxVPt.second)} kN", px + 10f,
                    py.coerceIn(sfdTop + 15f, sfdTop + sfdHeight - 5f),
                    textPaint(Color.parseColor("#E74C3C"), 14f, true))
            }
        }

        // Labels: + and - sides
        canvas.drawText("+V", bmdLeft - 25f, sfdTop + 20f, textPaint(SECONDARY_RED, 14f, true))
        canvas.drawText("-V", bmdLeft - 25f, sfdTop + sfdHeight - 5f, textPaint(SECONDARY_RED, 14f, true))

        // ============ PART 4: SUMMARY BOX ============
        val sumY = sfdTop + sfdHeight + 30f
        canvas.drawRect(marginL, sumY, W - marginR, sumY + 90f, fillPaint(Color.parseColor("#0D1117")))
        canvas.drawRect(marginL, sumY, W - marginR, sumY + 90f, createPaint(Color.parseColor("#444444"), 0.5f))

        canvas.drawText("STRUCTURAL ANALYSIS SUMMARY", marginL + 20f, sumY + 20f,
            textPaint(Color.WHITE, 16f, true))

        val infoP = textPaint(DIM_TEXT, 14f)
        canvas.drawText("Span: ${span.toInt()} mm  |  b x h: ${beamWidth.toInt()} x ${beamDepth.toInt()} mm",
            marginL + 20f, sumY + 42f, infoP)
        canvas.drawText("M_max: ${"%.1f".format(maxMoment)} kN.m  |  V_max: ${"%.1f".format(maxShear)} kN",
            marginL + 20f, sumY + 60f, infoP)
        canvas.drawText("Main: ${mainRebarCount}\u00D8${mainRebarDia.toInt()}  |  Stirrups: \u00D8${stirrupDia.toInt()} @ ${stirrupSpacing.toInt()} mm",
            marginL + 20f, sumY + 78f, infoP)

        // Status badge
        canvas.drawText(statusText, W - marginR - 80f, sumY + 50f, textPaint(statusColor, 28f, true))

        drawTitleBlock(canvas, W - 280f, H - 55f, 280f, 55f, "Beam Analysis")
        return bitmap
    }

    // ========== GENERATE SEISMIC DRAWING ==========
    // Renders a seismic design summary drawing with:
    //   - Building elevation with floor forces (arrows)
    //   - Force distribution chart (force per floor)
    //   - Response spectrum (Sa vs T)
    fun generateSeismicDrawing(
        totalHeight: Double,
        numFloors: Int,
        floorForces: List<com.civileg.app.domain.calculations.base.SeismicForceDistribution>,
        spectrumValues: List<com.civileg.app.domain.calculations.base.SpectrumValue>,
        baseShear: Double,
        fundamentalPeriod: Double,
        spectralAccel: Double,
        isSafe: Boolean
    ): Bitmap {
        val W = 1400; val H = 900
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 1.5f)

        // ===== PART 1: BUILDING ELEVATION WITH FLOOR FORCES (left) =====
        val elevL = 80f; val elevT = 100f
        val elevW = 280f; val elevH = 540f
        val baseY = elevT + elevH

        // Ground line
        val groundP = createPaint(SOIL_BROWN, 2f)
        canvas.drawLine(elevL - 30f, baseY, elevL + elevW + 30f, baseY, groundP)
        // Soil hatch
        for (i in 0..15) {
            val sx = elevL - 30f + i * 24f
            canvas.drawLine(sx, baseY, sx + 10f, baseY + 14f, createPaint(SOIL_BROWN, 1f))
        }

        // Building outline
        val bldgP = createPaint(CONCRETE_TOP, 2f)
        canvas.drawRect(elevL, elevT, elevL + elevW, baseY, fillPaint(Color.parseColor("#3A3A4E")))
        canvas.drawRect(elevL, elevT, elevL + elevW, baseY, bldgP)

        // Floor lines + force arrows
        if (floorForces.isNotEmpty()) {
            val maxH = floorForces.maxOfOrNull { it.floorHeight } ?: totalHeight
            val totalDrawH = elevH - 20f
            val forceColor = SECONDARY_RED
            val maxForce = floorForces.maxOfOrNull { kotlin.math.abs(it.lateralForce) } ?: 1.0

            // Floor levels + force arrows
            floorForces.forEachIndexed { idx, ff ->
                val yRatio = (ff.floorHeight / maxH.coerceAtLeast(0.1)).toFloat().coerceIn(0f, 1f)
                val floorY = baseY - yRatio * totalDrawH

                // Floor slab line
                canvas.drawLine(elevL - 5f, floorY, elevL + elevW + 5f, floorY, createPaint(DIM_LINE, 1.5f))

                // Force arrow (pointing right, magnitude proportional)
                val arrowLen = (ff.lateralForce / maxForce).toFloat() * 80f
                val arrowY = floorY - 12f
                val arrowStartX = elevL + elevW + 10f
                val arrowEndX = arrowStartX + arrowLen
                canvas.drawLine(arrowStartX, arrowY, arrowEndX, arrowY, createPaint(forceColor, 2.5f))
                // Arrowhead
                canvas.drawPath(android.graphics.Path().apply {
                    moveTo(arrowEndX, arrowY)
                    lineTo(arrowEndX - 8f, arrowY - 4f)
                    lineTo(arrowEndX - 8f, arrowY + 4f)
                    close()
                }, fillPaint(forceColor))

                // Floor label
                canvas.drawBilingualText(
                    "F${ff.floorIndex}: ${"%.1f".format(ff.lateralForce)} kN",
                    arrowEndX + 8f, arrowY + 5f,
                    DIM_TEXT, 13f
                )
            }
        } else {
            // No floor forces — draw floor lines based on numFloors
            val floorH = elevH / numFloors.coerceAtLeast(1)
            for (i in 1 until numFloors) {
                val fy = baseY - i * floorH
                canvas.drawLine(elevL, fy, elevL + elevW, fy, createPaint(DIM_LINE, 1f))
            }
        }

        // Base shear arrow (at ground level, pointing into building)
        val bsArrowLen = 100f
        val bsStartX = elevL - bsArrowLen - 10f
        val bsEndX = elevL - 10f
        canvas.drawLine(bsStartX, baseY - 20f, bsEndX, baseY - 20f, createPaint(SECONDARY_RED, 3f))
        canvas.drawPath(android.graphics.Path().apply {
            moveTo(bsEndX, baseY - 20f)
            lineTo(bsEndX - 12f, baseY - 26f)
            lineTo(bsEndX - 12f, baseY - 14f)
            close()
        }, fillPaint(SECONDARY_RED))
        canvas.drawBilingualText(
            t("V = ${"%.1f".format(baseShear)} kN", "V = ${"%.1f".format(baseShear)} kN"),
            bsStartX - 5f, baseY - 30f,
            SECONDARY_RED, 16f, true
        )

        // Elevation dimensions
        canvas.drawVDim(elevT, baseY, elevL + elevW + 25f, "H = ${"%.1f".format(totalHeight)} m", offset = 25f)

        // Title
        canvas.drawTextCentered(
            t("مسقط المبنى وتوزيع القوى الأفقية", "BUILDING ELEVATION & FORCE DISTRIBUTION"),
            elevL + elevW / 2f, elevT - 30f,
            textPaint(DIM_TEXT, 18f, true)
        )

        // ===== PART 2: FORCE DISTRIBUTION CHART (right top) =====
        val chartL = 460f; val chartT = 100f
        val chartW = 880f; val chartH = 360f

        // Chart background
        canvas.drawRect(chartL, chartT, chartL + chartW, chartT + chartH, fillPaint(Color.parseColor("#0D1117")))
        canvas.drawRect(chartL, chartT, chartL + chartW, chartT + chartH, createPaint(Color.parseColor("#444444"), 0.5f))

        // Axes
        val axisP = createPaint(DIM_LINE, 1f)
        val axisOriginX = chartL + 60f
        val axisOriginY = chartT + chartH - 40f
        val axisMaxX = chartL + chartW - 20f
        val axisMaxY = chartT + 30f

        // Y axis (force)
        canvas.drawLine(axisOriginX, axisOriginY, axisOriginX, axisMaxY, axisP)
        // X axis (floor)
        canvas.drawLine(axisOriginX, axisOriginY, axisMaxX, axisOriginY, axisP)

        // Axis labels
        canvas.drawTextCentered(
            t("القوة الأفقية (kN)", "Lateral Force (kN)"),
            chartL + 30f, chartT + chartH / 2f,
            textPaint(DIM_TEXT, 13f, true)
        )
        // Rotate Y label by drawing it vertically
        canvas.drawTextCentered(
            t("الطابق", "Floor"),
            chartL + chartW / 2f, chartT + chartH - 10f,
            textPaint(DIM_TEXT, 13f, true)
        )

        if (floorForces.isNotEmpty()) {
            val maxF = floorForces.maxOfOrNull { kotlin.math.abs(it.lateralForce) } ?: 1.0
            val chartDrawW = axisMaxX - axisOriginX
            val chartDrawH = axisOriginY - axisMaxY

            // Bar chart: one bar per floor
            val barCount = floorForces.size
            val barW = chartDrawW / (barCount * 1.5f)
            val barGap = barW * 0.5f

            floorForces.forEachIndexed { idx, ff ->
                val barX = axisOriginX + idx * (barW + barGap) + barGap / 2f
                val barH = (ff.lateralForce / maxF).toFloat() * chartDrawH
                val barTop = axisOriginY - barH

                // Bar
                val barColor = if (ff.lateralForce > 0) REBAR_BLUE else SECONDARY_RED
                canvas.drawRect(barX, barTop, barX + barW, axisOriginY, fillPaint(barColor))
                canvas.drawRect(barX, barTop, barX + barW, axisOriginY, outlineP)

                // Value label above bar
                canvas.drawBilingualText(
                    "${"%.0f".format(ff.lateralForce)}",
                    barX + barW / 2f, barTop - 8f,
                    DIM_TEXT, 12f, true,
                    align = android.graphics.Paint.Align.CENTER
                )

                // Floor label below bar
                canvas.drawBilingualText(
                    "F${ff.floorIndex}",
                    barX + barW / 2f, axisOriginY + 18f,
                    DIM_TEXT, 12f,
                    align = android.graphics.Paint.Align.CENTER
                )
            }

            // Y-axis max value
            canvas.drawText("${"%.0f".format(maxF)}", axisOriginX - 30f, axisMaxY + 8f, textPaint(DIM_TEXT, 12f))
            canvas.drawText("0", axisOriginX - 20f, axisOriginY + 5f, textPaint(DIM_TEXT, 12f))
        } else {
            // Empty state
            canvas.drawBilingualText(
                t("لا توجد بيانات قوى", "No force data"),
                chartL + chartW / 2f, chartT + chartH / 2f,
                DIM_TEXT, 16f
            )
        }

        // Chart title
        canvas.drawTextCentered(
            t("توزيع القوى على الطوابق", "FLOOR FORCE DISTRIBUTION"),
            chartL + chartW / 2f, chartT - 12f,
            textPaint(REBAR_BLUE, 16f, true)
        )

        // ===== PART 3: RESPONSE SPECTRUM (right bottom) =====
        val specL = 460f; val specT = 500f
        val specW = 880f; val specH = 360f

        // Background
        canvas.drawRect(specL, specT, specL + specW, specT + specH, fillPaint(Color.parseColor("#0D1117")))
        canvas.drawRect(specL, specT, specL + specW, specT + specH, createPaint(Color.parseColor("#444444"), 0.5f))

        // Axes
        val spOriginX = specL + 60f
        val spOriginY = specT + specH - 40f
        val spMaxX = specL + specW - 20f
        val spMaxY = specT + 30f

        canvas.drawLine(spOriginX, spOriginY, spOriginX, spMaxY, axisP)
        canvas.drawLine(spOriginX, spOriginY, spMaxX, spOriginY, axisP)

        canvas.drawTextCentered(
            t("تسارع طيفي Sa (g)", "Spectral Accel Sa (g)"),
            specL + 30f, specT + specH / 2f,
            textPaint(DIM_TEXT, 13f, true)
        )
        canvas.drawTextCentered(
            t("زمن T (sec)", "Period T (sec)"),
            specL + specW / 2f, specT + specH - 10f,
            textPaint(DIM_TEXT, 13f, true)
        )

        if (spectrumValues.isNotEmpty()) {
            val maxT = spectrumValues.maxOfOrNull { it.period } ?: 1.0
            val maxSa = spectrumValues.maxOfOrNull { it.spectralAcceleration } ?: 1.0

            val spDrawW = spMaxX - spOriginX
            val spDrawH = spOriginY - spMaxY

            // Draw spectrum curve
            val curveP = createPaint(SECONDARY_RED, 2.5f)
            val fillP = fillPaint(Color.parseColor("#1A3A5C"))
            val path = android.graphics.Path()
            val fillPath = android.graphics.Path()
            fillPath.moveTo(spOriginX, spOriginY)

            spectrumValues.forEachIndexed { idx, sv ->
                val px = spOriginX + (sv.period / maxT).toFloat() * spDrawW
                val py = spOriginY - (sv.spectralAcceleration / maxSa).toFloat() * spDrawH
                if (idx == 0) {
                    path.moveTo(px, py)
                    fillPath.lineTo(px, py)
                } else {
                    path.lineTo(px, py)
                    fillPath.lineTo(px, py)
                }
            }
            fillPath.lineTo(spMaxX, spOriginY)
            fillPath.close()
            canvas.drawPath(fillPath, fillP)
            canvas.drawPath(path, curveP)

            // Mark fundamental period
            val tPx = spOriginX + (fundamentalPeriod / maxT).toFloat() * spDrawW
            val dashP = createPaint(DIM_LINE, 1f).apply {
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f)
            }
            canvas.drawLine(tPx, spOriginY, tPx, spMaxY, dashP)
            canvas.drawBilingualText(
                "T₁ = ${"%.3f".format(fundamentalPeriod)} s",
                tPx + 5f, spMaxY + 15f,
                DIM_LINE, 12f
            )

            // Mark spectral accel at fundamental period
            val saY = spOriginY - (spectralAccel / maxSa).toFloat() * spDrawH
            canvas.drawCircle(tPx, saY, 5f, fillPaint(SECONDARY_RED))
            canvas.drawBilingualText(
                "Sa = ${"%.3f".format(spectralAccel)} g",
                tPx + 8f, saY - 8f,
                SECONDARY_RED, 12f, true
            )

            // Axis labels
            canvas.drawText("0", spOriginX - 15f, spOriginY + 5f, textPaint(DIM_TEXT, 12f))
            canvas.drawText("${"%.2f".format(maxT)}", spMaxX - 25f, spOriginY + 18f, textPaint(DIM_TEXT, 12f))
            canvas.drawText("${"%.2f".format(maxSa)}", spOriginX - 35f, spMaxY + 8f, textPaint(DIM_TEXT, 12f))
            canvas.drawText("0", spOriginX - 15f, spOriginY + 5f, textPaint(DIM_TEXT, 12f))
        }

        // Spectrum chart title
        canvas.drawTextCentered(
            t("طيف الاستجابة", "RESPONSE SPECTRUM"),
            specL + specW / 2f, specT - 12f,
            textPaint(SECONDARY_RED, 16f, true)
        )

        // ===== STATUS BADGE =====
        val statusColor = if (isSafe) Color.parseColor("#2ECC71") else SECONDARY_RED
        val statusText = if (isSafe) "SAFE" else "REVIEW"
        canvas.drawText(statusText, W - 130f, 50f, textPaint(statusColor, 28f, true))

        // ===== TITLE BLOCK =====
        drawTitleBlock(canvas, W - 320f, H - 70f, 320f, 70f, t("تحليل زلزالي", "Seismic Analysis"))
        canvas.drawBilingualText(
            t("المقياس: غير مطابق للمقياس - للمرجعية فقط", "Scale: Not to scale - For reference only"),
            80f, H - 20f, DIM_TEXT, 12f
        )
        return bitmap
    }

}