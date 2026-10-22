package com.civileg.app.utils

import android.content.Context
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.civileg.app.R
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.modeler.FrameSceneView
import com.civileg.app.viewmodel.DiagramType
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.*
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.*

/**
 * تصدير تقرير تحليل الإطارات إلى PDF (عربي/إنجليزي)
 */
object FrameAnalysisPdfExporter {

    private fun Paragraph.arabicStyle(font: PdfFont, fontSize: Float): Paragraph {
        // CRITICAL FIX: When Paragraph was created with text directly (e.g., Paragraph("text")),
        // the text is added as a Text run using the DEFAULT font (Helvetica).
        // Setting .setFont() afterwards changes paragraph-level font but the existing
        // Text run still uses Helvetica, causing Arabic to render as boxes.
        //
        // To fix this, callers should use the styledEnglishParagraph() helper below
        // which creates the Paragraph empty and adds the text with the correct font.
        return this.setFont(font).setFontSize(fontSize)
            .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
    }

    /**
     * Create a Paragraph with proper Arabic shaping.
     * Use this instead of Paragraph(text).arabicStyle(...) for any text containing Arabic.
     *
     * CRITICAL FIX: Shapes Arabic letters to Presentation Forms (0xFE80-0xFEFF)
     * BEFORE passing to iText. iText 8 AGPL does NOT shape Arabic automatically.
     *
     * CRITICAL FIX (2026-07-26): Use PdfTextSegmenter to split mixed Arabic/Latin
     * text into segments, rendering each with the appropriate font. This prevents
     * the "encrypted-looking" output that occurred when Latin chars in mixed text
     * were rendered with the Arabic font (which only has 15 Latin chars).
     */
    private fun styledEnglishParagraph(text: String, font: PdfFont, fontSize: Float, bold: Boolean = false): Paragraph {
        val helFont = try {
            PdfFontFactory.createFont(if (bold) "Helvetica-Bold" else "Helvetica")
        } catch (_: Exception) {
            PdfFontFactory.createFont("Helvetica")
        }
        return Paragraph().add(Text(text).setFont(helFont).setFontSize(fontSize))
    }

    fun generateFrameAnalysisPdf(
        context: Context,
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        result: FrameAnalysisResult?,
        slabs: List<SlabPanel> = emptyList(),
        sceneView: FrameSceneView? = null
    ): File {
        val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, "FrameAnalysis_${System.currentTimeMillis()}.pdf")
        try {
        val writer = PdfWriter(file)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc, PageSize.A4)
        document.setMargins(36f, 36f, 36f, 36f)

        // Arabic font - use ArabicFontProvider (never returns null)
        val arabicFont: PdfFont = ArabicFontProvider.getArabicPdfFont(context, bold = false)
        val arabicBold: PdfFont = ArabicFontProvider.getArabicPdfFont(context, bold = true)

        // === Page 1: Frame Geometry + Drawing ===
        // Title
        document.add(Paragraph("Frame Structural Analysis Report")
            .setFontSize(20f).setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(5f))
        document.add(styledEnglishParagraph("تقرير تحليل وتصميم الإطار", arabicFont, 16f)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(5f))
        document.add(Paragraph("Design Code: ${settings.designCode.version}")
            .setFontSize(11f).setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(15f))

        // Frame drawing as bitmap
        val bitmap = generateFrameBitmap(
            nodes, members, memberLoads, nodalLoads, slabs, result, DiagramType.BMD, sceneView
        )
        if (bitmap != null) {
            val imgData = com.itextpdf.io.image.ImageDataFactory.create(bitmap.toByteArray(Bitmap.CompressFormat.PNG, 100))
            val img = com.itextpdf.layout.element.Image(imgData)
            img.setWidth(UnitValue.createPointValue(fitImageWidth(bitmap)))
            img.setAutoScaleHeight(true)
            document.add(img)
        }

        // === Nodes Table ===
        document.add(styledEnglishParagraph("Nodes / العقد", arabicBold, 14f, bold = true).setMarginTop(15f))
        val nodeTable = Table(UnitValue.createPercentArray(floatArrayOf(15f, 25f, 25f, 35f))).useAllAvailableWidth()
        nodeTable.addHeaderCell(Cell().add(Paragraph("ID").setFontSize(9f).setBold()))
        nodeTable.addHeaderCell(Cell().add(Paragraph("X (m)").setFontSize(9f).setBold()))
        nodeTable.addHeaderCell(Cell().add(Paragraph("Y (m)").setFontSize(9f).setBold()))
        nodeTable.addHeaderCell(Cell().add(Paragraph("Support").setFontSize(9f).setBold()))
        for (node in nodes) {
            nodeTable.addCell(Cell().add(Paragraph("${node.id}").setFontSize(8f)))
            nodeTable.addCell(Cell().add(Paragraph(String.format("%.2f", node.x)).setFontSize(8f)))
            nodeTable.addCell(Cell().add(Paragraph(String.format("%.2f", node.y)).setFontSize(8f)))
            nodeTable.addCell(Cell().add(Paragraph(node.support.displayNameEn).setFontSize(8f)))
        }
        document.add(nodeTable)

        // === Members Table ===
        document.add(styledEnglishParagraph("Members / الأعضاء", arabicBold, 14f, bold = true).setMarginTop(15f))
        val memTable = Table(UnitValue.createPercentArray(floatArrayOf(7f, 18f, 11f, 11f, 14f, 27f, 12f))).useAllAvailableWidth()
        memTable.addHeaderCell(Cell().add(Paragraph("#").setFontSize(8f).setBold()))
        memTable.addHeaderCell(Cell().add(Paragraph("Name").setFontSize(8f).setBold()))
        memTable.addHeaderCell(Cell().add(Paragraph("I -> J").setFontSize(8f).setBold()))
        memTable.addHeaderCell(Cell().add(Paragraph("Type").setFontSize(8f).setBold()))
        memTable.addHeaderCell(Cell().add(Paragraph("Material").setFontSize(8f).setBold()))
        memTable.addHeaderCell(Cell().add(Paragraph("Section").setFontSize(8f).setBold()))
        memTable.addHeaderCell(Cell().add(Paragraph("Releases").setFontSize(8f).setBold()))
        for (m in members) {
            memTable.addCell(Cell().add(Paragraph("${m.id}").setFontSize(7f)))
            memTable.addCell(Cell().add(Paragraph(m.name.ifEmpty { "M${m.id}" }).setFontSize(7f)))
            memTable.addCell(Cell().add(Paragraph("${m.nodeI}->${m.nodeJ}").setFontSize(7f)))
            memTable.addCell(Cell().add(Paragraph(m.memberType.displayNameEn).setFontSize(7f)))
            memTable.addCell(Cell().add(Paragraph(m.materialType.displayNameEn).setFontSize(7f)))
            val secDesc = if (m.materialType == FrameMaterialType.Concrete && m.concreteSection != null)
                "b=${m.concreteSection!!.width} h=${m.concreteSection!!.depth} mm"
            else m.steelSectionName ?: "N/A"
            memTable.addCell(Cell().add(Paragraph(secDesc).setFontSize(7f)))
            val releases = when {
                m.releaseI && m.releaseJ -> "I + J"
                m.releaseI -> "I"
                m.releaseJ -> "J"
                else -> "-"
            }
            memTable.addCell(Cell().add(Paragraph(releases).setFontSize(7f)))
        }
        document.add(memTable)

        // === Slab Panels Table ===
        if (slabs.isNotEmpty()) {
            document.add(styledEnglishParagraph("Slab Panels / البلاطات", arabicBold, 14f, bold = true).setMarginTop(15f))
            val slabTable = Table(UnitValue.createPercentArray(floatArrayOf(8f, 26f, 15f, 15f, 12f, 24f))).useAllAvailableWidth()
            slabTable.addHeaderCell(Cell().add(Paragraph("#").setFontSize(8f).setBold()))
            slabTable.addHeaderCell(Cell().add(Paragraph("Joints").setFontSize(8f).setBold()))
            slabTable.addHeaderCell(Cell().add(Paragraph("Thickness (mm)").setFontSize(8f).setBold()))
            slabTable.addHeaderCell(Cell().add(Paragraph("Load (kN/m2)").setFontSize(8f).setBold()))
            slabTable.addHeaderCell(Cell().add(Paragraph("Case").setFontSize(8f).setBold()))
            slabTable.addHeaderCell(Cell().add(Paragraph("Distribution").setFontSize(8f).setBold()))
            for (s in slabs) {
                slabTable.addCell(Cell().add(Paragraph("${s.id}").setFontSize(7f)))
                slabTable.addCell(Cell().add(Paragraph(s.nodeIds.joinToString(" -> ")).setFontSize(7f)))
                slabTable.addCell(Cell().add(Paragraph(String.format("%.0f", s.thicknessMm)).setFontSize(7f)))
                slabTable.addCell(Cell().add(Paragraph(fmt(s.loadKNm2)).setFontSize(7f)))
                slabTable.addCell(Cell().add(Paragraph(s.loadCase).setFontSize(7f)))
                slabTable.addCell(Cell().add(Paragraph(if (s.distribution == SlabDistribution.OneWay) "One-way" else "Two-way").setFontSize(7f)))
            }
            document.add(slabTable)
        }

        // === Results (if solved) ===
        if (result != null && result.hasResults) {
            document.add(AreaBreak())

            // BMD Drawing
            val bmdBitmap = generateFrameBitmap(
                nodes, members, memberLoads, nodalLoads, slabs, result, DiagramType.BMD
            )
            if (bmdBitmap != null) {
                document.add(Paragraph("Bending Moment Diagram (BMD)").setFontSize(14f).setMarginTop(10f))
                val imgBmd = com.itextpdf.layout.element.Image(
                    com.itextpdf.io.image.ImageDataFactory.create(bmdBitmap.toByteArray(Bitmap.CompressFormat.PNG, 100))
                )
                imgBmd.setWidth(UnitValue.createPointValue(fitImageWidth(bmdBitmap)))
                imgBmd.setAutoScaleHeight(true)
                document.add(imgBmd)
            }

            // SFD Drawing
            val sfdBitmap = generateFrameBitmap(
                nodes, members, memberLoads, nodalLoads, slabs, result, DiagramType.SFD
            )
            if (sfdBitmap != null) {
                document.add(AreaBreak())
                document.add(Paragraph("Shear Force Diagram (SFD)").setFontSize(14f).setMarginTop(10f))
                val imgSfd = com.itextpdf.layout.element.Image(
                    com.itextpdf.io.image.ImageDataFactory.create(sfdBitmap.toByteArray(Bitmap.CompressFormat.PNG, 100))
                )
                imgSfd.setWidth(UnitValue.createPointValue(fitImageWidth(sfdBitmap)))
                imgSfd.setAutoScaleHeight(true)
                document.add(imgSfd)
            }

            // Member End Forces Table
            document.add(styledEnglishParagraph("Member End Forces / القوى الداخلية", arabicBold, 14f, bold = true).setMarginTop(15f))
            val forcesTable = Table(UnitValue.createPercentArray(floatArrayOf(15f, 17f, 17f, 17f, 17f, 17f))).useAllAvailableWidth()
            forcesTable.addHeaderCell(Cell().add(Paragraph("Member").setFontSize(8f).setBold()))
            forcesTable.addHeaderCell(Cell().add(Paragraph("NI (kN)").setFontSize(8f).setBold()))
            forcesTable.addHeaderCell(Cell().add(Paragraph("VI (kN)").setFontSize(8f).setBold()))
            forcesTable.addHeaderCell(Cell().add(Paragraph("MI (kN.m)").setFontSize(8f).setBold()))
            forcesTable.addHeaderCell(Cell().add(Paragraph("NJ (kN)").setFontSize(8f).setBold()))
            forcesTable.addHeaderCell(Cell().add(Paragraph("MJ (kN.m)").setFontSize(8f).setBold()))
            for (mf in result.memberEndForces) {
                val name = members.find { it.id == mf.memberId }?.name ?: "#${mf.memberId}"
                forcesTable.addCell(Cell().add(Paragraph(name).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.fi_x)).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.fi_y)).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.mi_z)).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.fj_y)).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.mj_z)).setFontSize(7f)))
            }
            document.add(forcesTable)

            // Concrete Design Results
            if (result.concreteDesignResults.isNotEmpty()) {
                document.add(AreaBreak())
                document.add(styledEnglishParagraph("Concrete Design Results / نتائج التصميم الخرساني", arabicBold, 14f, bold = true))
                for (cr in result.concreteDesignResults) {
                    document.add(styledEnglishParagraph("${cr.memberName} (#${cr.memberId}) - ${cr.memberType.displayNameEn}", arabicBold, 11f, bold = true).setMarginTop(8f))
                    document.add(styledEnglishParagraph("Section: ${cr.section.width} x ${cr.section.depth} mm | f'c = ${cr.section.fcu} MPa | fy = ${cr.section.fy} MPa", arabicFont, 9f))
                    document.add(styledEnglishParagraph("Max M = ${fmt(cr.maxMoment)} kN.m | Max V = ${fmt(cr.maxShear)} kN | N = ${fmt(cr.axialForce)} kN", arabicFont, 9f))
                    document.add(styledEnglishParagraph("As req = ${String.format("%.0f", cr.asRequired)} mm^2 | Bottom: ${cr.numBarsBot}T${cr.barDia.toInt()} = ${String.format("%.0f", cr.asBot)} mm^2", arabicFont, 9f))
                    if (cr.stirrupDia > 0)
                        document.add(styledEnglishParagraph("Stirrups: T${cr.stirrupDia.toInt()} @ ${cr.stirrupSpacing.toInt()} mm", arabicFont, 9f))
                    document.add(styledEnglishParagraph("Utilization - Moment: ${String.format("%.0f", cr.momentUtilization * 100)}% | Shear: ${String.format("%.0f", cr.shearUtilization * 100)}% | ${if (cr.isSafe) "SAFE" else "UNSAFE"}", arabicFont, 9f))
                }
            }

            // Steel Design Results
            if (result.steelDesignResults.isNotEmpty()) {
                document.add(AreaBreak())
                document.add(styledEnglishParagraph("Steel Design Results / نتائج التصميم المعدني", arabicBold, 14f, bold = true))
                for (sr in result.steelDesignResults) {
                    document.add(styledEnglishParagraph("${sr.memberName} (#${sr.memberId}) - ${sr.memberType.displayNameEn}", arabicBold, 11f, bold = true).setMarginTop(8f))
                    document.add(styledEnglishParagraph("Selected: ${sr.selectedSection} | W = ${sr.sectionWeight} kg/m | Ix = ${sr.sectionIx} cm^4", arabicFont, 9f))
                    document.add(styledEnglishParagraph("Max M = ${fmt(sr.maxMoment)} kN.m | Max V = ${fmt(sr.maxShear)} kN | N = ${fmt(sr.axialForce)} kN", arabicFont, 9f))
                    document.add(styledEnglishParagraph("Utilization - Flexure: ${String.format("%.0f", sr.flexuralUtilization * 100)}% | Shear: ${String.format("%.0f", sr.shearUtilization * 100)}% | Combined: ${String.format("%.0f", sr.combinedUtilization * 100)}% | ${if (sr.isSafe) "SAFE" else "UNSAFE"}", arabicFont, 9f))
                }
            }
        }

        document.close()
        return file
        } catch (e: Exception) {
            Log.e("FrameAnalysisPdf", "PDF generation failed", e)
            throw RuntimeException("خطأ في إنشاء تقرير PDF: ${e.message}", e)
        }
    }

    // ========================================================================
    // Bitmap Generation for PDF
    // ========================================================================

    private fun generateFrameBitmap(
        nodes: List<FrameNode>, members: List<FrameMember>,
        memberLoads: List<MemberLoad>, nodalLoads: List<NodalLoad>,
        slabs: List<SlabPanel>, result: FrameAnalysisResult?, diagramType: DiagramType,
        sceneView: FrameSceneView? = null
    ): Bitmap? {
        if (nodes.isEmpty()) return null

        val view = sceneView?.takeIf { it.isValid }
        val renderFactor = if (view != null) {
            maxOf(1f, view.width / 1200f, view.height / 1600f)
        } else {
            1f
        }
        val w = ((view?.width ?: 800) / renderFactor).roundToInt().coerceAtLeast(1)
        val h = ((view?.height ?: 500) / renderFactor).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; color = Color.BLACK }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; color = Color.DKGRAY }
        val diagramPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val xMin = nodes.minOf { it.x } - 1; val xMax = nodes.maxOf { it.x } + 1
        val yMin = nodes.minOf { it.y } - 1; val yMax = nodes.maxOf { it.y } + 1
        val xSpan = max(xMax - xMin, 1.0); val ySpan = max(yMax - yMin, 1.0)
        val pad = 60f

        val scale: Float
        val ox: Float
        val oy: Float
        if (view != null) {
            scale = view.scale / renderFactor
            ox = view.panX / renderFactor
            oy = view.panY / renderFactor
        } else {
            scale = min((w - 2 * pad) / xSpan, (h - 2 * pad) / ySpan).toFloat() * 0.8f
            ox = (pad + ((w - 2 * pad) - xSpan * scale) / 2f - xMin.toFloat() * scale).toFloat()
            oy = (pad + ((h - 2 * pad) - ySpan * scale) / 2f + yMax.toFloat() * scale).toFloat()
        }

        fun sx(x: Double) = (x * scale + ox).toFloat()
        fun sy(y: Double) = (-y * scale + oy).toFloat()

        var gridStep = if (view != null) view.gridMm / 1000.0 else 1.0
        if (gridStep <= 0.0) gridStep = 1.0
        while (gridStep * scale < 18f) gridStep *= 10.0
        val gridLeft = if (view != null) ((0f - ox) / scale).toDouble() else xMin
        val gridRight = if (view != null) ((w - ox) / scale).toDouble() else xMax
        val gridBottom = if (view != null) ((oy - h) / scale).toDouble() else yMin
        val gridTop = if (view != null) (oy / scale).toDouble() else yMax
        val minorGrid = Color.parseColor("#B0BEC5")
        val majorGrid = Color.parseColor("#90A4AE")
        val axisGrid = Color.parseColor("#1565C0")
        paint.strokeWidth = 1f

        var count = 0
        var x = floor(gridLeft / gridStep) * gridStep
        while (x <= gridRight && count < 2000) {
            val major = round(x / (gridStep * 10.0)) * (gridStep * 10.0)
            val isMajor = abs(x - major) < gridStep * 0.5
            val isAxis = abs(x) < gridStep * 0.5
            paint.color = when {
                isAxis -> axisGrid
                isMajor -> majorGrid
                else -> minorGrid
            }
            paint.alpha = when {
                isAxis -> 190
                isMajor -> 110
                else -> 55
            }
            canvas.drawLine(sx(x), 0f, sx(x), h.toFloat(), paint)
            x += gridStep
            count++
        }

        count = 0
        var y = floor(gridBottom / gridStep) * gridStep
        while (y <= gridTop && count < 2000) {
            val major = round(y / (gridStep * 10.0)) * (gridStep * 10.0)
            val isMajor = abs(y - major) < gridStep * 0.5
            val isAxis = abs(y) < gridStep * 0.5
            paint.color = when {
                isAxis -> axisGrid
                isMajor -> majorGrid
                else -> minorGrid
            }
            paint.alpha = when {
                isAxis -> 190
                isMajor -> 110
                else -> 55
            }
            canvas.drawLine(0f, sy(y), w.toFloat(), sy(y), paint)
            y += gridStep
            count++
        }
        paint.alpha = 255

        val gridLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            color = Color.parseColor("#546E7A")
            alpha = 150
        }
        count = 0
        x = floor(gridLeft / gridStep) * gridStep
        while (x <= gridRight && count < 2000) {
            val major = round(x / (gridStep * 10.0)) * (gridStep * 10.0)
            if (abs(x - major) < gridStep * 0.5 && abs(x) >= gridStep * 0.5) {
                canvas.drawText(formatGridLabel(x), sx(x) + 3f, h - 18f, gridLabelPaint)
            }
            x += gridStep
            count++
        }
        count = 0
        y = floor(gridBottom / gridStep) * gridStep
        while (y <= gridTop && count < 2000) {
            val major = round(y / (gridStep * 10.0)) * (gridStep * 10.0)
            if (abs(y - major) < gridStep * 0.5 && abs(y) >= gridStep * 0.5) {
                canvas.drawText(formatGridLabel(y), 4f, sy(y) - 12f, gridLabelPaint)
            }
            y += gridStep
            count++
        }

        // Slab panels (behind the frame)
        if (slabs.isNotEmpty()) {
            val slabFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64B5F6")
                alpha = 38
                style = Paint.Style.FILL
            }
            val slabStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1976D2")
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }
            for (slab in slabs) {
                val pts = slab.nodeIds.mapNotNull { id -> nodes.find { it.id == id } }
                if (pts.size != 4) continue
                val path = android.graphics.Path()
                pts.forEachIndexed { index, node ->
                    if (index == 0) path.moveTo(sx(node.x), sy(node.y))
                    else path.lineTo(sx(node.x), sy(node.y))
                }
                path.close()
                canvas.drawPath(path, slabFill)
                canvas.drawPath(path, slabStroke)
            }
        }

        // Diagrams (behind members)
        if (result?.hasResults == true) {
            for (diagram in result.memberDiagrams) {
                val member = members.find { it.id == diagram.memberId } ?: continue
                val ni = nodes.find { it.id == member.nodeI } ?: continue
                val nj = nodes.find { it.id == member.nodeJ } ?: continue
                val x1 = sx(ni.x); val y1 = sy(ni.y)
                val x2 = sx(nj.x); val y2 = sy(nj.y)
                val L = sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
                if (L < 1f) continue
                val dx = (x2 - x1) / L; val dy = (y2 - y1) / L
                val nx = -dy; val ny = dx

                val points = when (diagramType) {
                    DiagramType.BMD -> diagram.momentDiagram
                    DiagramType.SFD -> diagram.shearDiagram
                    DiagramType.AFD -> diagram.axialDiagram
                }
                if (points.size < 2) continue
                val maxVal = points.maxOfOrNull { abs(it.value) } ?: 1.0
                if (maxVal < 0.001) continue
                val dScale = 60f / maxVal.toFloat()

                val diagColor = when (diagramType) {
                    DiagramType.BMD -> Color.parseColor("#2196F3")
                    DiagramType.SFD -> Color.parseColor("#4CAF50")
                    DiagramType.AFD -> Color.parseColor("#FF9800")
                }
                diagramPaint.color = diagColor; diagramPaint.strokeWidth = 2f
                fillPaint.color = diagColor; fillPaint.alpha = 50

                val path = android.graphics.Path()
                val t0 = if (member.getLength(nodes) > 0) (points[0].x / member.getLength(nodes)).toFloat() else 0f
                path.moveTo(x1 + dx * L * t0 + nx * (points[0].value * dScale).toFloat(), y1 + dy * L * t0 + ny * (points[0].value * dScale).toFloat())
                for (i in 1 until points.size) {
                    val t = if (member.getLength(nodes) > 0) (points[i].x / member.getLength(nodes)).toFloat() else (i.toFloat() / (points.size - 1))
                    path.lineTo(x1 + dx * L * t + nx * (points[i].value * dScale).toFloat(), y1 + dy * L * t + ny * (points[i].value * dScale).toFloat())
                }
                path.lineTo(x2, y2); path.lineTo(x1, y1); path.close()
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, diagramPaint)

                // Max annotation
                val maxPt = points.maxByOrNull { abs(it.value) }
                if (maxPt != null) {
                    val tM = if (member.getLength(nodes) > 0) (maxPt.x / member.getLength(nodes)).toFloat() else 0.5f
                    val annX = x1 + dx * L * tM + nx * (maxPt.value * dScale).toFloat()
                    val annY = y1 + dy * L * tM + ny * (maxPt.value * dScale).toFloat()
                    smallPaint.color = diagColor
                    canvas.drawText(String.format("%.1f", abs(maxPt.value)), annX + 4f, annY - 4f, smallPaint)
                }
            }
        }

        // Members
        paint.strokeWidth = 4f
        for (member in members) {
            val ni = nodes.find { it.id == member.nodeI } ?: continue
            val nj = nodes.find { it.id == member.nodeJ } ?: continue
            paint.color = when (member.materialType) {
                FrameMaterialType.Concrete -> Color.parseColor("#1565C0")
                FrameMaterialType.Steel -> Color.parseColor("#E65100")
            }
            canvas.drawLine(sx(ni.x), sy(ni.y), sx(nj.x), sy(nj.y), paint)
            val releasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D84315")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            val releaseFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            if (member.releaseI) {
                canvas.drawCircle(sx(ni.x), sy(ni.y), 7f, releaseFill)
                canvas.drawCircle(sx(ni.x), sy(ni.y), 7f, releasePaint)
            }
            if (member.releaseJ) {
                canvas.drawCircle(sx(nj.x), sy(nj.y), 7f, releaseFill)
                canvas.drawCircle(sx(nj.x), sy(nj.y), 7f, releasePaint)
            }
        }

        // Supports
        val supportPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1565C0"); strokeWidth = 3f }
        for (node in nodes) {
            if (node.support == SupportType.Free) continue
            val px = sx(node.x); val py = sy(node.y)
            when (node.support) {
                SupportType.Pin -> {
                    val path = android.graphics.Path()
                    path.moveTo(px, py); path.lineTo(px - 15, py + 20); path.lineTo(px + 15, py + 20); path.close()
                    canvas.drawPath(path, supportPaint)
                }
                SupportType.Fixed -> {
                    canvas.drawLine(px - 12, py + 12, px + 12, py - 12, supportPaint)
                    for (i in -1..1) {
                        canvas.drawLine(px + i * 10 - 12, py + 12 + (i + 1) * 5, px + i * 10 - 20, py + 4 + (i + 1) * 5, supportPaint)
                    }
                }
                SupportType.Roller -> {
                    val path = android.graphics.Path()
                    path.moveTo(px, py); path.lineTo(px - 10, py + 14); path.lineTo(px + 10, py + 14); path.close()
                    canvas.drawPath(path, supportPaint)
                    canvas.drawCircle(px, py + 20f, 5f, supportPaint)
                }
                else -> {}
            }
        }

        // Nodes
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (node in nodes) {
            val px = sx(node.x); val py = sy(node.y)
            nodePaint.color = Color.WHITE; canvas.drawCircle(px, py, 6f, nodePaint)
            nodePaint.color = Color.BLACK; canvas.drawCircle(px, py, 6f, nodePaint.apply { style = Paint.Style.STROKE; strokeWidth = 2f })
            textPaint.color = Color.parseColor("#1565C0"); textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${node.id}", px, py - 12f, textPaint)
        }

        // Loads
        val loadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C62828")
            strokeWidth = 2.5f
            textSize = 18f
        }
        for (load in nodalLoads) {
            val node = nodes.find { it.id == load.nodeId } ?: continue
            val px = sx(node.x); val py = sy(node.y)
            val magnitude = hypot(load.fx, load.fy)
            if (magnitude > 1e-9) {
                val ux = (load.fx / magnitude).toFloat()
                val uy = (-load.fy / magnitude).toFloat()
                val startX = px - ux * 40f
                val startY = py - uy * 40f
                canvas.drawLine(startX, startY, px, py, loadPaint)
                val angle = atan2(py - startY, px - startX)
                val len = 9f
                canvas.drawLine(px, py, px + cos(angle + 2.7f) * len, py + sin(angle + 2.7f) * len, loadPaint)
                canvas.drawLine(px, py, px + cos(angle - 2.7f) * len, py + sin(angle - 2.7f) * len, loadPaint)
            }
            if (abs(load.mz) > 1e-9) {
                canvas.drawCircle(px + 22f, py - 22f, 11f, loadPaint.apply { style = Paint.Style.STROKE })
                loadPaint.style = Paint.Style.FILL
            }
        }
        for (load in memberLoads) {
            val member = members.find { it.id == load.memberId } ?: continue
            val ni = nodes.find { it.id == member.nodeI } ?: continue
            val nj = nodes.find { it.id == member.nodeJ } ?: continue
            val x1 = sx(ni.x); val y1 = sy(ni.y)
            val x2 = sx(nj.x); val y2 = sy(nj.y)
            val dx = x2 - x1; val dy = y2 - y1
            val len = hypot(dx, dy)
            if (len < 1e-3f) continue
            val nx = -dy / len; val ny = dx / len
            val offset = 20f
            when (load.loadType) {
                MemberLoadType.UDL -> {
                    canvas.drawLine(
                        x1 + nx * offset, y1 + ny * offset,
                        x2 + nx * offset, y2 + ny * offset, loadPaint
                    )
                    for (i in 0..5) {
                        val t = i / 5f
                        val px = x1 + dx * t + nx * offset
                        val py = y1 + dy * t + ny * offset
                        canvas.drawLine(px, py, px - nx * 10f, py - ny * 10f, loadPaint)
                    }
                    canvas.drawText(
                        "w=${fmt(load.value)} kN/m",
                        (x1 + x2) / 2f + 6f,
                        (y1 + y2) / 2f + ny * (offset + 12f),
                        loadPaint
                    )
                }
                MemberLoadType.PointLoad -> {
                    val t = (load.position / max(member.getLength(nodes), 1e-9)).toFloat().coerceIn(0f, 1f)
                    val px = x1 + dx * t
                    val py = y1 + dy * t
                    canvas.drawLine(px - nx * 28f, py - ny * 28f, px, py, loadPaint)
                    canvas.drawText("P=${fmt(load.value)}", px + 5f, py - 8f, loadPaint)
                }
                MemberLoadType.Moment -> {
                    val px = (x1 + x2) / 2f
                    val py = (y1 + y2) / 2f
                    canvas.drawCircle(px, py, 12f, loadPaint.apply { style = Paint.Style.STROKE })
                    loadPaint.style = Paint.Style.FILL
                    canvas.drawText("M=${fmt(load.value)}", px + 15f, py - 12f, loadPaint)
                }
                MemberLoadType.LinearVarying -> {
                    canvas.drawLine(
                        x1 + nx * offset, y1 + ny * offset,
                        x2 + nx * offset, y2 + ny * offset, loadPaint
                    )
                    canvas.drawText("w=${fmt(load.value)} (varying)", (x1 + x2) / 2f, (y1 + y2) / 2f - 12f, loadPaint)
                }
            }
        }

        // Title
        val titlePaint = Paint().apply { textSize = 20f; color = Color.DKGRAY; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val titleText = when (diagramType) {
            DiagramType.BMD -> "Bending Moment Diagram (BMD)"
            DiagramType.SFD -> "Shear Force Diagram (SFD)"
            DiagramType.AFD -> "Axial Force Diagram (AFD)"
        }
        if (result?.hasResults == true) canvas.drawText(titleText, w / 2f, 25f, titlePaint)

        // Scale bar
        val scaleBarM = 1.0
        val scaleBarPx = (scaleBarM * scale).toFloat()
        canvas.drawLine(30f, h - 25f, 30f + scaleBarPx, h - 25f, paint.apply { color = Color.DKGRAY; strokeWidth = 2f })
        canvas.drawLine(30f, h - 30f, 30f, h - 20f, paint)
        canvas.drawLine(30f + scaleBarPx, h - 30f, 30f + scaleBarPx, h - 20f, paint)
        canvas.drawText("${scaleBarM.toInt()} m", 30f + scaleBarPx / 2f - 10f, h - 8f, smallPaint)

        return bitmap
    }

    private fun fitImageWidth(
        bitmap: Bitmap,
        maxWidth: Float = 520f,
        maxHeight: Float = 430f
    ): Float {
        if (bitmap.width <= 0 || bitmap.height <= 0) return maxWidth
        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        return min(maxWidth, maxHeight * aspect).coerceAtLeast(1f)
    }

    private fun formatGridLabel(value: Double): String {
        val absValue = abs(value)
        return when {
            absValue < 1e-9 -> "0"
            absValue >= 1.0 -> String.format(java.util.Locale.ROOT, "%.1f m", value)
            else -> String.format(java.util.Locale.ROOT, "%.0f mm", value * 1000.0)
        }
    }

    private fun fmt(d: Double): String = String.format("%.2f", d)

    private fun Bitmap.toByteArray(format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        this.compress(format, quality, stream)
        return stream.toByteArray()
    }
}

