package com.civileg.app.utils

import android.content.Context
import android.graphics.*
import kotlin.math.*

/**
 * Generates engineering drawing Bitmaps for PDF report embedding.
 * Uses Android Canvas API (not Compose) for direct bitmap generation.
 * Drawing style matches the Professional*Drawing Compose components.
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
            if (bold) this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    // ========== TEXT HELPER ==========
    private fun Canvas.drawTextCentered(text: String, x: Float, y: Float, paint: Paint) {
        paint.textAlign = Paint.Align.CENTER
        drawText(text, x, y, paint)
        paint.textAlign = Paint.Align.LEFT // reset
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
        cover: Double = 40.0
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
        val barsPerSide = maxOf(2, (numBars - 4) / 4)
        // 4 corners always
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
            // Bar mark number
            val mark = getCircleNumber(idx + 1)
            canvas.drawTextCentered(mark, pos.x, pos.y - csBarR - 6f, textPaint(DIM_TEXT, 14f, true))
        }

        // Section dimensions
        canvas.drawHDim(csLeft, csLeft + csW2, csTop2 + csH2 + 15f, "${columnWidth.toInt()} mm", 20f)
        canvas.drawVDim(csTop2, csTop2 + csH2, csLeft + csW2 + 15f, "${columnDepth.toInt()} mm", 20f)
        canvas.drawTextCentered("SECTION A-A", csCx, csTop2 - 20f, titleP)

        // Rebar table
        drawRebarTable(canvas, 
            x = 100f, y = H * 0.65f,
            data = listOf(
                listOf("Mark", "Dia", "No.", "Type", "Spacing"),
                listOf("M1", "${barDia.toInt()}mm", "$numBars", "Main", "-"),
                listOf("T1", "${tieDia.toInt()}mm", "-", "Ties", "${tieSpacing.toInt()}mm")
            )
        )

        drawTitleBlock(canvas, W - 280f, H - 60f, 280f, 60f, "Column Detail")

        return bitmap
    }

    // ========== GENERATE SLAB DRAWING ==========
    fun generateSlabDrawing(
        spanX: Double, spanY: Double, thickness: Double,
        mainDia: Double, mainSpacing: Double,
        distDia: Double, distSpacing: Double
    ): Bitmap {
        val W = 1200; val H = 700
        val (bitmap, canvas) = createCanvas(W, H)
        val outlineP = createPaint(Color.WHITE, 1.5f)

        // Plan view (top-left)
        val planL = 80f; val planT = 70f
        val scale = min(400f / spanX.toFloat(), 300f / spanY.toFloat()) * 0.8f
        val planW = spanX.toFloat() * scale; val planH = spanY.toFloat() * scale

        canvas.drawRect(planL, planT, planL + planW, planT + planH, fillPaint(CONCRETE))
        canvas.drawRect(planL, planT, planL + planW, planT + planH, outlineP)

        // Hatch
        canvas.drawHatch(planL, planT, planW, planH, 15f)

        // Main bars (along X - vertical blue lines)
        val mainP = createPaint(REBAR_BLUE, 2f)
        var mx = planL + 30f
        while (mx < planL + planW - 10f) {
            canvas.drawLine(mx, planT + 10f, mx, planT + planH - 10f, mainP)
            mx += mainSpacing.toFloat() * scale / 5f  // scale down for visibility
        }

        // Distribution bars (along Y - horizontal purple lines)
        val distP = createPaint(STIRRUP, 1.5f)
        var dy = planT + 30f
        while (dy < planT + planH - 10f) {
            canvas.drawLine(planL + 10f, dy, planL + planW - 10f, dy, distP)
            dy += distSpacing.toFloat() * scale / 5f
        }

        canvas.drawHDim(planL, planL + planW, planT + planH + 15f, "${spanX.toInt()} mm (Lx)")
        canvas.drawVDim(planT, planT + planH, planL + planW + 15f, "${spanY.toInt()} mm (Ly)")

        val titleP = textPaint(Color.WHITE, 24f, true)
        canvas.drawTextCentered("SLAB REINFORCEMENT PLAN", planL + planW / 2f, planT - 20f, titleP)

        // Cross-section (right side)
        val secL = W * 0.55f; val secT = 100f
        val secW = planW * 0.8f; val secH = thickness.toFloat() * scale * 2f
        canvas.drawRect(secL, secT, secL + secW, secT + secH, fillPaint(CONCRETE))
        canvas.drawRect(secL, secT, secL + secW, secT + secH, outlineP)

        // Bars in section
        val barR = maxOf(mainDia.toFloat() * 0.3f, 3f)
        var bx = secL + 20f
        while (bx < secL + secW - 10f) {
            canvas.drawRebar(bx, secT + secH - 10f, barR, REBAR_BLUE)
            bx += 25f
        }

        canvas.drawHDim(secL, secL + secW, secT + secH + 15f, "${spanX.toInt()} mm")
        canvas.drawVDim(secT, secT + secH, secL + secW + 15f, "t=${thickness.toInt()}mm")
        canvas.drawTextCentered("SECTION B-B", secL + secW / 2f, secT - 15f, textPaint(DIM_TEXT, 18f, true))

        // Table
        drawRebarTable(canvas,
            x = 80f, y = H * 0.55f,
            data = listOf(
                listOf("Mark", "Dia", "Direction", "Spacing"),
                listOf("M1", "${mainDia.toInt()}mm", "Short span", "${mainSpacing.toInt()}mm c/c"),
                listOf("D1", "${distDia.toInt()}mm", "Long span", "${distSpacing.toInt()}mm c/c")
            )
        )

        drawTitleBlock(canvas, W - 280f, H - 60f, 280f, 60f, "Slab Detail")
        return bitmap
    }

    // ========== GENERATE FOOTING DRAWING ==========
    fun generateFootingDrawing(
        footingLX: Double, footingLY: Double, footingThickness: Double,
        colW: Double, colD: Double,
        rebarXCount: Int, rebarXDia: Double, rebarXSpacing: Double,
        rebarYCount: Int, rebarYDia: Double, rebarYSpatial: Double
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
        canvas.drawTextCentered("FOOTING PLAN", planL + fW / 2f, planT - 20f, titleP)

        // Section (right side)
        val secL = W * 0.52f; val secT = 80f
        val secW2 = fW * 0.7f; val secH2 = footingThickness.toFloat() * scale * 1.5f

        // Soil
        canvas.drawRect(secL - 20f, secT + secH2, secL + secW2 + 20f, secT + secH2 + 30f, fillPaint(Color.parseColor("#8B6914")))
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

        drawTitleBlock(canvas, W - 280f, H - 60f, 280f, 60f, "Footing Detail")
        return bitmap
    }

    // ========== GENERATE STAIR DRAWING ==========
    fun generateStairDrawing(
        totalHeight: Double, totalLength: Double, stairWidth: Double,
        riserHeight: Double, treadWidth: Double, slabThickness: Double,
        mainDia: Double, mainSpacing: Double, distDia: Double = 8.0, distSpacing: Double = 200.0
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

        // Main reinforcement (following slope)
        val rebarP = createPaint(REBAR_BLUE, 2f)
        val nBars = 4
        for (i in 0 until nBars) {
            val offset = (i + 1) * soffitOffset / (nBars + 1)
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
}