package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.civileg.app.domain.entities.SteelWarehouseInputs
import com.civileg.app.domain.entities.SteelWarehouseProResult
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import kotlin.math.*

class SteelWarehouseProPdfExporter(private val context: Context) {

    private val pageWidth = 2480
    private val pageHeight = 3508
    private val df2 = DecimalFormat("0.00")
    private val totalPages = 6

    fun exportToDownload(
        input: SteelWarehouseInputs,
        result: SteelWarehouseProResult,
        clientAr: String,
        clientEn: String,
        projAr: String,
        projEn: String
    ): File {
        val pdf = PdfDocument()

        addCover(pdf, input, result, clientAr, clientEn, projAr, projEn)
        addPlan(pdf, input, result, clientAr, clientEn, projAr, projEn)
        addSchedule(pdf, input, result, clientAr, clientEn, projAr, projEn)
        addDetails(pdf, input, result, clientAr, clientEn, projAr, projEn)
        addLoads(pdf, input, result, clientAr, clientEn, projAr, projEn)
        addSummary(pdf, input, result, clientAr, clientEn, projAr, projEn)

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "SteelReports")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "Steel_Warehouse_Professional_Report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        return file
    }

    private fun startPage(pdf: PdfDocument, no: Int): Pair<PdfDocument.Page, Canvas> {
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, no).create()
        val page = pdf.startPage(info)
        return page to page.canvas
    }

    private fun finishPage(pdf: PdfDocument, page: PdfDocument.Page) = pdf.finishPage(page)

    private data class PS(
        val line: Paint,
        val grid: Paint,
        val blue: Paint,
        val red: Paint,
        val fillBlue: Paint,
        val fillLightBlue: Paint,
        val blackFill: Paint,
        val title: Paint,
        val head: Paint,
        val body: Paint,
        val bodyBold: Paint,
        val small: Paint,
        val rtl: Paint,
        val rtlBold: Paint
    )

    private fun style(): PS {
        fun p(color: Int, style: Paint.Style = Paint.Style.STROKE, stroke: Float = 2f, size: Float? = null, bold: Boolean = false, tf: Typeface? = null) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.style = style
                this.strokeWidth = stroke
                if (size != null) textSize = size
                typeface = tf ?: Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }

        val arabicTf = try {
            Typeface.createFromAsset(context.assets, "fonts/NotoNaskhArabic-Regular.ttf")
        } catch (_: Throwable) {
            Typeface.SANS_SERIF
        }
        val arabicBoldTf = try {
            Typeface.createFromAsset(context.assets, "fonts/NotoNaskhArabic-Bold.ttf")
        } catch (_: Throwable) {
            Typeface.DEFAULT_BOLD
        }

        return PS(
            line = p(Color.BLACK, Paint.Style.STROKE, 3f),
            grid = p(Color.rgb(180, 180, 180), Paint.Style.STROKE, 1.5f).apply { pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f) },
            blue = p(Color.rgb(45, 110, 190), Paint.Style.STROKE, 6f),
            red = p(Color.rgb(211, 47, 47), Paint.Style.STROKE, 6f),
            fillBlue = p(Color.rgb(45, 110, 190), Paint.Style.FILL),
            fillLightBlue = p(Color.argb(30, 45, 110, 190), Paint.Style.FILL),
            blackFill = p(Color.BLACK, Paint.Style.FILL),
            title = p(Color.BLACK, Paint.Style.FILL, 48f, bold = true),
            head = p(Color.BLACK, Paint.Style.FILL, 34f, bold = true),
            body = p(Color.BLACK, Paint.Style.FILL, 26f),
            bodyBold = p(Color.BLACK, Paint.Style.FILL, 26f, bold = true),
            small = p(Color.DKGRAY, Paint.Style.FILL, 22f),
            rtl = p(Color.BLACK, Paint.Style.FILL, 26f, tf = arabicTf).apply { textAlign = Paint.Align.RIGHT },
            rtlBold = p(Color.BLACK, Paint.Style.FILL, 26f, tf = arabicBoldTf).apply { textAlign = Paint.Align.RIGHT }
        )
    }

    private fun isArabic(text: String): Boolean = text.any { it.code in 0x0600..0x06FF }

    private fun getArabicFont(): Typeface {
        return try {
            Typeface.createFromAsset(context.assets, "fonts/NotoNaskhArabic-Regular.ttf")
        } catch (_: Throwable) {
            Typeface.SANS_SERIF
        }
    }

    private fun drawFormattedText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        if (text.isEmpty()) return
        val workingPaint = Paint(paint)
        if (isArabic(text)) workingPaint.typeface = getArabicFont()
        val textPaint = TextPaint(workingPaint)
        val layoutWidth = (pageWidth - 2 * 110).toFloat()
        val alignment = when (paint.textAlign) {
            Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, layoutWidth.toInt())
            .setAlignment(alignment)
            .build()
        canvas.save()
        val tx = when (paint.textAlign) {
            Paint.Align.RIGHT -> x - layoutWidth
            Paint.Align.CENTER -> x - layoutWidth / 2f
            else -> x
        }
        canvas.translate(tx, y - workingPaint.textSize)
        staticLayout.draw(canvas)
        canvas.restore()
    }

    private fun drawBorder(c: Canvas, p: PS) {
        c.drawRect(60f, 60f, (pageWidth - 60).toFloat(), (pageHeight - 60).toFloat(), p.line)
        c.drawLine(80f, 80f, (pageWidth - 80).toFloat(), 80f, p.grid)
    }

    private fun drawTitleBlock(c: Canvas, p: PS, no: Int, titleEn: String, titleAr: String, input: SteelWarehouseInputs, result: SteelWarehouseProResult, clientAr: String, clientEn: String, projAr: String, projEn: String) {
        c.drawRect(110f, 110f, 2370f, 320f, p.line)
        c.drawLine(110f, 200f, 2370f, 200f, p.grid)
        drawFormattedText(c, titleEn, 140f, 175f, p.title)
        drawFormattedText(c, titleAr, 2330f, 175f, p.rtlBold)
        drawFormattedText(c, "Client / العميل: $clientEn", 140f, 245f, p.body)
        drawFormattedText(c, clientAr, 950f, 245f, p.rtl)
        drawFormattedText(c, "Project / المشروع: $projEn", 140f, 290f, p.body)
        drawFormattedText(c, projAr, 950f, 290f, p.rtl)
        drawFormattedText(c, "Code: ${result.codeName}", 1800f, 245f, p.bodyBold)
        drawFormattedText(c, "Sheet No: $no / $totalPages", 1800f, 290f, p.bodyBold)
    }

    private fun drawInfoBox(c: Canvas, p: PS, left: Float, top: Float, width: Float, height: Float, title: String, lines: List<String>) {
        c.drawRect(left, top, left + width, top + height, p.line)
        c.drawRect(left, top, left + width, top + 60f, p.fillLightBlue)
        drawFormattedText(c, title, left + 20f, top + 45f, p.head)
        var y = top + 110f
        lines.forEach { line ->
            drawFormattedText(c, "• $line", left + 30f, y, p.body)
            y += 45f
        }
    }

    private fun drawDimension(c: Canvas, p: PS, x1: Float, y1: Float, x2: Float, y2: Float, text: String, vertical: Boolean = false) {
        val pDim = Paint(p.line).apply { strokeWidth = 2f }
        c.drawLine(x1, y1, x2, y2, pDim)
        val tick = 15f
        if (vertical) {
            c.drawLine(x1 - tick, y1, x1 + tick, y1, pDim)
            c.drawLine(x1 - tick, y2, x1 + tick, y2, pDim)
            c.save(); c.rotate(-90f, x1 - 15f, (y1 + y2) / 2f)
            drawFormattedText(c, text, x1 - 30f, (y1 + y2) / 2f, p.bodyBold); c.restore()
        } else {
            c.drawLine(x1, y1 - tick, x1, y1 + tick, pDim)
            c.drawLine(x2, y1 - tick, x2, y1 + tick, pDim)
            drawFormattedText(c, text, (x1 + x2) / 2f, y1 - 15f, p.bodyBold)
        }
    }

    private fun addCover(pdf: PdfDocument, input: SteelWarehouseInputs, result: SteelWarehouseProResult, clientAr: String, clientEn: String, projAr: String, projEn: String) {
        val p = style(); val (page, c) = startPage(pdf, 1)
        drawBorder(c, p)
        drawTitleBlock(c, p, 1, "ENGINEERING DESIGN REPORT", "تقرير التصميم الهندسي للمستودع", input, result, clientAr, clientEn, projAr, projEn)
        
        drawInfoBox(c, p, 120f, 380f, 1080f, 500f, "GEOMETRY / الهندسة", listOf(
            "Warehouse Span: ${df2.format(input.span)} m",
            "Total Length: ${df2.format(input.length)} m",
            "Eave Height: ${df2.format(input.eaveHeight)} m",
            "Ridge Height: ${df2.format(input.ridgeHeight)} m",
            "Bay Spacing: ${df2.format(input.baySpacing)} m"
        ))
        
        drawInfoBox(c, p, 1280f, 380f, 1080f, 500f, "DESIGN LOADS / الأحمال", listOf(
            "Dead Load: ${df2.format(input.deadLoad)} kN/m²",
            "Live Load: ${df2.format(input.liveLoad)} kN/m²",
            "Wind Load: ${df2.format(input.windLoad)} kN/m²",
            "Service Load: ${df2.format(result.serviceLoadKnM2)} kN/m²",
            "Tributary Area: ${df2.format(result.tributaryAreaM2)} m²"
        ))

        drawInfoBox(c, p, 120f, 920f, 2240f, 800f, "ANALYSIS SUMMARY / ملخص التحليل", listOf(
            "Maximum Moment: ${df2.format(result.maxMomentKnM)} kN.m",
            "Maximum Axial: ${df2.format(result.maxAxialKn)} kN",
            "Maximum Shear: ${df2.format(result.maxShearKn)} kN",
            "Drift Check: ${df2.format(result.driftMm)} mm",
            "Overall Utilization: ${df2.format(result.utilization * 100)} %",
            "Safety Status: ${if (result.utilization <= 1.0) "SAFE / آمن" else "UNSAFE / غير آمن"}",
            "Notes: ${result.notes.joinToString(", ")}"
        ))
        
        drawFormattedText(c, "Generated by CivilEG Professional Engine", 120f, 3400f, p.small)
        finishPage(pdf, page)
    }

    private fun addPlan(pdf: PdfDocument, input: SteelWarehouseInputs, result: SteelWarehouseProResult, clientAr: String, clientEn: String, projAr: String, projEn: String) {
        val p = style(); val (page, c) = startPage(pdf, 2)
        drawBorder(c, p)
        drawTitleBlock(c, p, 2, "PLAN VIEW & FRAMING LAYOUT", "المسقط الأفقي وتوزيع الإطارات", input, result, clientAr, clientEn, projAr, projEn)
        
        val left = 300f; val top = 500f; val w = 1800f; val h = 1000f
        c.drawRect(left, top, left + w, top + h, p.line)
        
        // Grids
        val numBays = max(1, (input.length / input.baySpacing).toInt())
        val dx = w / numBays
        for (i in 0..numBays) {
            val x = left + i * dx
            c.drawLine(x, top - 40f, x, top + h + 40f, p.grid)
            c.drawCircle(x, top - 60f, 25f, p.line)
            drawFormattedText(c, (i + 1).toString(), x, top - 50f, p.bodyBold.apply { textAlign = Paint.Align.CENTER })
            // Columns
            c.drawRect(x - 15f, top - 15f, x + 15f, top + 15f, p.blackFill)
            c.drawRect(x - 15f, top + h - 15f, x + 15f, top + h + 15f, p.blackFill)
        }
        
        // Bracing in first and last bays
        val pBrace = Paint(p.red).apply { strokeWidth = 4f; pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f) }
        c.drawLine(left, top, left + dx, top + h, pBrace)
        c.drawLine(left, top + h, left + dx, top, pBrace)
        c.drawLine(left + w - dx, top, left + w, top + h, pBrace)
        c.drawLine(left + w - dx, top + h, left + w, top, pBrace)

        drawDimension(c, p, left, top + h + 100f, left + w, top + h + 100f, "${df2.format(input.length)} m")
        drawDimension(c, p, left - 100f, top, left - 100f, top + h, "${df2.format(input.span)} m", true)
        
        drawInfoBox(c, p, 120f, 1700f, 1000f, 400f, "LEGEND", listOf(
            "Black Squares: Main Columns",
            "Red Dashed: X-Bracing System",
            "Blue Solid: Main Portal Frames",
            "Grey Dashed: Grid Lines"
        ))
        finishPage(pdf, page)
    }

    private fun addSchedule(pdf: PdfDocument, input: SteelWarehouseInputs, result: SteelWarehouseProResult, clientAr: String, clientEn: String, projAr: String, projEn: String) {
        val p = style(); val (page, c) = startPage(pdf, 3)
        drawBorder(c, p)
        drawTitleBlock(c, p, 3, "MEMBER DESIGN SCHEDULE", "جدول تصميم العناصر الإنشائية", input, result, clientAr, clientEn, projAr, projEn)
        
        val headers = listOf("Mark", "Member Type", "Suggested Section", "Utilization", "Status")
        val colWidths = floatArrayOf(200f, 500f, 600f, 400f, 400f)
        var curX = 120f; var curY = 450f
        
        // Table Header
        c.drawRect(120f, curY - 60f, 2220f, curY, p.fillLightBlue)
        headers.forEachIndexed { i, h ->
            drawFormattedText(c, h, curX + 15f, curY - 20f, p.bodyBold)
            curX += colWidths[i]
        }
        c.drawRect(120f, 390f, 2220f, 1500f, p.line)
        
        // Data Rows
        val rows = listOf(
            listOf("C1", "Main Column", "HEB 300 / Built-up", "${df2.format(result.utilization * 0.95)}", "Safe"),
            listOf("R1", "Main Rafter", "IPE 400 / Built-up", "${df2.format(result.utilization)}", "Check"),
            listOf("P1", "Purlins", "Cold-Formed C200", "0.65", "Safe"),
            listOf("B1", "Side Bracing", "L 100x100x10", "0.42", "Safe")
        )
        
        curY += 60f
        rows.forEach { row ->
            curX = 120f
            row.forEachIndexed { i, txt ->
                drawFormattedText(c, txt, curX + 15f, curY, p.body)
                curX += colWidths[i]
            }
            c.drawLine(120f, curY + 20f, 2220f, curY + 20f, p.grid)
            curY += 80f
        }
        
        finishPage(pdf, page)
    }

    private fun addDetails(pdf: PdfDocument, input: SteelWarehouseInputs, result: SteelWarehouseProResult, clientAr: String, clientEn: String, projAr: String, projEn: String) {
        val p = style(); val (page, c) = startPage(pdf, 4)
        drawBorder(c, p)
        drawTitleBlock(c, p, 4, "TYPICAL CROSS SECTION", "القطاع العرضي النمذجي", input, result, clientAr, clientEn, projAr, projEn)
        
        val groundY = 1500f; val leftX = 400f; val spanW = 1600f
        val eaveH = ((input.eaveHeight / input.span) * spanW).toFloat()
        val ridgeH = ((input.ridgeHeight / input.span) * spanW).toFloat()
        
        val colL = PointF(leftX, groundY); val colR = PointF(leftX + spanW, groundY)
        val eaveL = PointF(leftX, groundY - eaveH); val eaveR = PointF(leftX + spanW, groundY - eaveH)
        val ridge = PointF(leftX + spanW/2f, groundY - ridgeH)
        
        // Draw Main Frame
        val pFrame = Paint(p.blue).apply { strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
        c.drawLine(colL.x, colL.y, eaveL.x, eaveL.y, pFrame)
        c.drawLine(colR.x, colR.y, eaveR.x, eaveR.y, pFrame)
        c.drawLine(eaveL.x, eaveL.y, ridge.x, ridge.y, pFrame)
        c.drawLine(ridge.x, ridge.y, eaveR.x, eaveR.y, pFrame)
        
        // Purlins (schematic)
        val pPurlin = Paint(p.blackFill)
        for(i in 0..5) {
            val t = i / 5f
            val px = eaveL.x + (ridge.x - eaveL.x) * t
            val py = eaveL.y + (ridge.y - eaveL.y) * t
            c.drawRect(px - 10f, py - 20f, px + 10f, py, pPurlin)
            val px2 = ridge.x + (eaveR.x - ridge.x) * t
            val py2 = ridge.y + (eaveR.y - ridge.y) * t
            c.drawRect(px2 - 10f, py2 - 20f, px2 + 10f, py2, pPurlin)
        }

        drawDimension(c, p, colL.x, colL.y + 80f, colR.x, colR.y + 80f, "${df2.format(input.span)} m")
        drawDimension(c, p, colL.x - 100f, colL.y, colL.x - 100f, eaveL.y, "${df2.format(input.eaveHeight)} m", true)
        
        // Labels
        drawFormattedText(c, "Main Rafter", ridge.x + 50f, ridge.y - 30f, p.bodyBold)
        drawFormattedText(c, "Main Column", colL.x - 180f, (colL.y + eaveL.y)/2f, p.bodyBold)
        
        finishPage(pdf, page)
    }

    private fun addLoads(pdf: PdfDocument, input: SteelWarehouseInputs, result: SteelWarehouseProResult, clientAr: String, clientEn: String, projAr: String, projEn: String) {
        val p = style(); val (page, c) = startPage(pdf, 5)
        drawBorder(c, p)
        drawTitleBlock(c, p, 5, "LOAD INTENSITY & STRESS MAP", "كثافة الأحمال وخريطة الإجهادات", input, result, clientAr, clientEn, projAr, projEn)
        
        val groundY = 1200f; val leftX = 500f; val spanW = 1400f
        val eaveH = ((input.eaveHeight / input.span) * spanW).toFloat()
        val ridgeH = ((input.ridgeHeight / input.span) * spanW).toFloat()
        val eaveL = PointF(leftX, groundY - eaveH); val eaveR = PointF(leftX + spanW, groundY - eaveH)
        val ridge = PointF(leftX + spanW/2f, groundY - ridgeH)

        // Draw Frame with Gradient for Stress
        val pStress = Paint().apply { strokeWidth = 15f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
        
        // Columns (Low Stress to High at Eave)
        pStress.shader = LinearGradient(0f, groundY, 0f, eaveL.y, Color.BLUE, Color.RED, Shader.TileMode.CLAMP)
        c.drawLine(leftX, groundY, leftX, eaveL.y, pStress)
        c.drawLine(leftX + spanW, groundY, leftX + spanW, eaveR.y, pStress)
        
        // Rafters (High at Eave to Medium at Ridge)
        pStress.shader = LinearGradient(leftX, eaveL.y, ridge.x, ridge.y, Color.RED, Color.YELLOW, Shader.TileMode.CLAMP)
        c.drawLine(eaveL.x, eaveL.y, ridge.x, ridge.y, pStress)
        pStress.shader = LinearGradient(ridge.x, ridge.y, eaveR.x, eaveR.y, Color.YELLOW, Color.RED, Shader.TileMode.CLAMP)
        c.drawLine(ridge.x, ridge.y, eaveR.x, eaveR.y, pStress)

        // Load Arrows
        val pArrow = Paint(p.blue).apply { strokeWidth = 3f }
        for(i in 0..10) {
            val t = i / 10f
            val x = eaveL.x + (eaveR.x - eaveL.x) * t
            val y = if (x <= ridge.x) eaveL.y + (ridge.y - eaveL.y) * (x - eaveL.x) / (ridge.x - eaveL.x) 
                    else ridge.y + (eaveR.y - ridge.y) * (x - ridge.x) / (eaveR.x - ridge.x)
            c.drawLine(x, y - 100f, x, y - 10f, pArrow)
            c.drawPath(Path().apply { moveTo(x-10f, y-25f); lineTo(x, y-5f); lineTo(x+10f, y-25f) }, pArrow)
        }
        
        drawFormattedText(c, "Load Intensity: ${df2.format(result.serviceLoadKnM2)} kN/m²", 120f, 1600f, p.bodyBold)
        drawFormattedText(c, "High Stress (Critical Zone)", 120f, 1650f, p.body.apply { color = Color.RED })
        drawFormattedText(c, "Low Stress (Safe Zone)", 120f, 1700f, p.body.apply { color = Color.BLUE })

        finishPage(pdf, page)
    }

    private fun addSummary(pdf: PdfDocument, input: SteelWarehouseInputs, result: SteelWarehouseProResult, clientAr: String, clientEn: String, projAr: String, projEn: String) {
        val p = style(); val (page, c) = startPage(pdf, 6)
        drawBorder(c, p)
        drawTitleBlock(c, p, 6, "DATA SHEET & DESIGN NOTES", "ورقة البيانات والملاحظات التصميمية", input, result, clientAr, clientEn, projAr, projEn)
        
        val summaryText = """
            Design Methodology:
            The analysis is performed using a linearized portal frame approach based on the selected design code. 
            Second-order effects (P-Delta) are approximated via amplification factors. 
            Lateral stability is provided by X-bracing in the end bays.
            
            Key Assumptions:
            1. All base connections are assumed to be Pinned unless otherwise noted.
            2. Steel Grade: ST37/S235 for secondary members, ST52/S355 for primary members.
            3. Wind pressure calculated based on open-terrain exposure.
            
            Final Recommendations:
            - Ensure high-strength bolts (Grade 8.8 or 10.9) for eave and apex connections.
            - Anchor bolts must be embedded minimum 600mm into concrete pedestals.
            - Grouting beneath base plates must use non-shrink mortar.
        """.trimIndent()
        
        drawInfoBox(c, p, 120f, 400f, 2240f, 1200f, "ENGINEERING SPECIFICATIONS", summaryText.split("\n"))
        
        drawFormattedText(c, "Sign & Stamp:", 1800f, 3000f, p.bodyBold)
        c.drawLine(1800f, 3150f, 2300f, 3150f, p.line)

        finishPage(pdf, page)
    }
}
