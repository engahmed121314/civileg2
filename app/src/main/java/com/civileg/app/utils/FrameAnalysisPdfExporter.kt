package com.civileg.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.civileg.app.R
import com.civileg.app.domain.entities.*
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

    fun generateFrameAnalysisPdf(
        context: Context,
        nodes: List<FrameNode>,
        members: List<FrameMember>,
        nodalLoads: List<NodalLoad>,
        memberLoads: List<MemberLoad>,
        settings: FrameAnalysisSettings,
        result: FrameAnalysisResult?
    ): File {
        val file = File(context.getExternalFilesDir(null), "FrameAnalysis_${System.currentTimeMillis()}.pdf")
        val writer = PdfWriter(file)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc, PageSize.A4)
        document.setMargins(36f, 36f, 36f, 36f)

        // Arabic font
        val fontPath = "${context.filesDir}/fonts/NotoNaskhArabic-Regular.ttf"
        val boldFontPath = "${context.filesDir}/fonts/NotoNaskhArabic-Bold.ttf"
        val arabicFont: PdfFont = if (File(fontPath).exists()) {
            PdfFontFactory.createFont(fontPath, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED)
        } else PdfFontFactory.createFont("Helvetica")
        val arabicBold: PdfFont = if (File(boldFontPath).exists()) {
            PdfFontFactory.createFont(boldFontPath, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED)
        } else PdfFontFactory.createFont("Helvetica-Bold")

        // === Page 1: Frame Geometry + Drawing ===
        // Title
        document.add(Paragraph("Frame Structural Analysis Report")
            .setFont(arabicBold).setFontSize(20f).setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(5f))
        document.add(Paragraph("تقرير تحليل وتصميم الإطار")
            .setFont(arabicFont).setFontSize(16f).setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(5f))
        document.add(Paragraph("Design Code: ${settings.designCode.version}")
            .setFont(arabicFont).setFontSize(11f).setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(15f))

        // Frame drawing as bitmap
        val bitmap = generateFrameBitmap(nodes, members, memberLoads, nodalLoads, result, DiagramType.BMD)
        if (bitmap != null) {
            val imgData = com.itextpdf.io.image.ImageDataFactory.create(bitmap.toByteArray(Bitmap.CompressFormat.PNG, 100))
            val img = com.itextpdf.layout.element.Image(imgData)
            img.setWidth(UnitValue.createPercentValue(100f))
            img.setAutoScaleHeight(true)
            document.add(img)
        }

        // === Nodes Table ===
        document.add(Paragraph("Nodes / العقد").setFont(arabicBold).setFontSize(14f).setMarginTop(15f))
        val nodeTable = Table(UnitValue.createPercentArray(floatArrayOf(15f, 25f, 25f, 35f))).useAllAvailableWidth()
        nodeTable.addHeaderCell(Cell().add(Paragraph("ID").setFont(arabicBold).setFontSize(9f)))
        nodeTable.addHeaderCell(Cell().add(Paragraph("X (m)").setFont(arabicBold).setFontSize(9f)))
        nodeTable.addHeaderCell(Cell().add(Paragraph("Y (m)").setFont(arabicBold).setFontSize(9f)))
        nodeTable.addHeaderCell(Cell().add(Paragraph("Support").setFont(arabicBold).setFontSize(9f)))
        for (node in nodes) {
            nodeTable.addCell(Cell().add(Paragraph("${node.id}").setFont(arabicFont).setFontSize(8f)))
            nodeTable.addCell(Cell().add(Paragraph(String.format("%.2f", node.x)).setFont(arabicFont).setFontSize(8f)))
            nodeTable.addCell(Cell().add(Paragraph(String.format("%.2f", node.y)).setFont(arabicFont).setFontSize(8f)))
            nodeTable.addCell(Cell().add(Paragraph(node.support.displayNameEn).setFont(arabicFont).setFontSize(8f)))
        }
        document.add(nodeTable)

        // === Members Table ===
        document.add(Paragraph("Members / الأعضاء").setFont(arabicBold).setFontSize(14f).setMarginTop(15f))
        val memTable = Table(UnitValue.createPercentArray(floatArrayOf(8f, 20f, 12f, 12f, 15f, 33f))).useAllAvailableWidth()
        memTable.addHeaderCell(Cell().add(Paragraph("#").setFont(arabicBold).setFontSize(9f)))
        memTable.addHeaderCell(Cell().add(Paragraph("Name").setFont(arabicBold).setFontSize(9f)))
        memTable.addHeaderCell(Cell().add(Paragraph("I → J").setFont(arabicBold).setFontSize(9f)))
        memTable.addHeaderCell(Cell().add(Paragraph("Type").setFont(arabicBold).setFontSize(9f)))
        memTable.addHeaderCell(Cell().add(Paragraph("Material").setFont(arabicBold).setFontSize(9f)))
        memTable.addHeaderCell(Cell().add(Paragraph("Section").setFont(arabicBold).setFontSize(9f)))
        for (m in members) {
            memTable.addCell(Cell().add(Paragraph("${m.id}").setFont(arabicFont).setFontSize(8f)))
            memTable.addCell(Cell().add(Paragraph(m.name.ifEmpty { "M${m.id}" }).setFont(arabicFont).setFontSize(8f)))
            memTable.addCell(Cell().add(Paragraph("${m.nodeI}→${m.nodeJ}").setFont(arabicFont).setFontSize(8f)))
            memTable.addCell(Cell().add(Paragraph(m.memberType.displayNameEn).setFont(arabicFont).setFontSize(8f)))
            memTable.addCell(Cell().add(Paragraph(m.materialType.displayNameEn).setFont(arabicFont).setFontSize(8f)))
            val secDesc = if (m.materialType == FrameMaterialType.Concrete && m.concreteSection != null)
                "b=${m.concreteSection!!.width} h=${m.concreteSection!!.depth} mm"
            else m.steelSectionName ?: "N/A"
            memTable.addCell(Cell().add(Paragraph(secDesc).setFont(arabicFont).setFontSize(8f)))
        }
        document.add(memTable)

        // === Results (if solved) ===
        if (result != null && result.hasResults) {
            document.add(AreaBreak())

            // BMD Drawing
            val bmdBitmap = generateFrameBitmap(nodes, members, memberLoads, nodalLoads, result, DiagramType.BMD)
            if (bmdBitmap != null) {
                document.add(Paragraph("Bending Moment Diagram (BMD)").setFont(arabicBold).setFontSize(14f).setMarginTop(10f))
                val imgBmd = com.itextpdf.layout.element.Image(
                    com.itextpdf.io.image.ImageDataFactory.create(bmdBitmap.toByteArray(Bitmap.CompressFormat.PNG, 100))
                )
                imgBmd.setWidth(UnitValue.createPercentValue(100f))
                imgBmd.setAutoScaleHeight(true)
                document.add(imgBmd)
            }

            // SFD Drawing
            val sfdBitmap = generateFrameBitmap(nodes, members, memberLoads, nodalLoads, result, DiagramType.SFD)
            if (sfdBitmap != null) {
                document.add(AreaBreak())
                document.add(Paragraph("Shear Force Diagram (SFD)").setFont(arabicBold).setFontSize(14f).setMarginTop(10f))
                val imgSfd = com.itextpdf.layout.element.Image(
                    com.itextpdf.io.image.ImageDataFactory.create(sfdBitmap.toByteArray(Bitmap.CompressFormat.PNG, 100))
                )
                imgSfd.setWidth(UnitValue.createPercentValue(100f))
                imgSfd.setAutoScaleHeight(true)
                document.add(imgSfd)
            }

            // Member End Forces Table
            document.add(Paragraph("Member End Forces / القوى الداخلية").setFont(arabicBold).setFontSize(14f).setMarginTop(15f))
            val forcesTable = Table(UnitValue.createPercentArray(floatArrayOf(15f, 17f, 17f, 17f, 17f, 17f))).useAllAvailableWidth()
            forcesTable.addHeaderCell(Cell().add(Paragraph("Member").setFont(arabicBold).setFontSize(8f)))
            forcesTable.addHeaderCell(Cell().add(Paragraph("NI (kN)").setFont(arabicBold).setFontSize(8f)))
            forcesTable.addHeaderCell(Cell().add(Paragraph("VI (kN)").setFont(arabicBold).setFontSize(8f)))
            forcesTable.addHeaderCell(Cell().add(Paragraph("MI (kN.m)").setFont(arabicBold).setFontSize(8f)))
            forcesTable.addHeaderCell(Cell().add(Paragraph("NJ (kN)").setFont(arabicBold).setFontSize(8f)))
            forcesTable.addHeaderCell(Cell().add(Paragraph("MJ (kN.m)").setFont(arabicBold).setFontSize(8f)))
            for (mf in result.memberEndForces) {
                val name = members.find { it.id == mf.memberId }?.name ?: "#${mf.memberId}"
                forcesTable.addCell(Cell().add(Paragraph(name).setFont(arabicFont).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.fi_x)).setFont(arabicFont).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.fi_y)).setFont(arabicFont).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.mi_z)).setFont(arabicFont).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.fj_y)).setFont(arabicFont).setFontSize(7f)))
                forcesTable.addCell(Cell().add(Paragraph(fmt(mf.mj_z)).setFont(arabicFont).setFontSize(7f)))
            }
            document.add(forcesTable)

            // Concrete Design Results
            if (result.concreteDesignResults.isNotEmpty()) {
                document.add(AreaBreak())
                document.add(Paragraph("Concrete Design Results / نتائج التصميم الخرساني").setFont(arabicBold).setFontSize(14f))
                for (cr in result.concreteDesignResults) {
                    document.add(Paragraph("${cr.memberName} (#${cr.memberId}) - ${cr.memberType.displayNameEn}").setFont(arabicBold).setFontSize(11f).setMarginTop(8f))
                    document.add(Paragraph("Section: ${cr.section.width} x ${cr.section.depth} mm | f'c = ${cr.section.fcu} MPa | fy = ${cr.section.fy} MPa").setFont(arabicFont).setFontSize(9f))
                    document.add(Paragraph("Max M = ${fmt(cr.maxMoment)} kN.m | Max V = ${fmt(cr.maxShear)} kN | N = ${fmt(cr.axialForce)} kN").setFont(arabicFont).setFontSize(9f))
                    document.add(Paragraph("As req = ${String.format("%.0f", cr.asRequired)} mm² | Bottom: ${cr.numBarsBot}Ø${cr.barDia.toInt()} = ${String.format("%.0f", cr.asBot)} mm²").setFont(arabicFont).setFontSize(9f))
                    if (cr.stirrupDia > 0)
                        document.add(Paragraph("Stirrups: Ø${cr.stirrupDia.toInt()} @ ${cr.stirrupSpacing.toInt()} mm").setFont(arabicFont).setFontSize(9f))
                    document.add(Paragraph("Utilization - Moment: ${String.format("%.0f", cr.momentUtilization * 100)}% | Shear: ${String.format("%.0f", cr.shearUtilization * 100)}% | ${if (cr.isSafe) "SAFE ✓" else "UNSAFE ✗"}").setFont(arabicFont).setFontSize(9f))
                }
            }

            // Steel Design Results
            if (result.steelDesignResults.isNotEmpty()) {
                document.add(AreaBreak())
                document.add(Paragraph("Steel Design Results / نتائج التصميم المعدني").setFont(arabicBold).setFontSize(14f))
                for (sr in result.steelDesignResults) {
                    document.add(Paragraph("${sr.memberName} (#${sr.memberId}) - ${sr.memberType.displayNameEn}").setFont(arabicBold).setFontSize(11f).setMarginTop(8f))
                    document.add(Paragraph("Selected: ${sr.selectedSection} | W = ${sr.sectionWeight} kg/m | Ix = ${sr.sectionIx} cm⁴").setFont(arabicFont).setFontSize(9f))
                    document.add(Paragraph("Max M = ${fmt(sr.maxMoment)} kN.m | Max V = ${fmt(sr.maxShear)} kN | N = ${fmt(sr.axialForce)} kN").setFont(arabicFont).setFontSize(9f))
                    document.add(Paragraph("Utilization - Flexure: ${String.format("%.0f", sr.flexuralUtilization * 100)}% | Shear: ${String.format("%.0f", sr.shearUtilization * 100)}% | Combined: ${String.format("%.0f", sr.combinedUtilization * 100)}% | ${if (sr.isSafe) "SAFE ✓" else "UNSAFE ✗"}").setFont(arabicFont).setFontSize(9f))
                }
            }
        }

        document.close()
        return file
    }

    // ========================================================================
    // Bitmap Generation for PDF
    // ========================================================================

    private fun generateFrameBitmap(
        nodes: List<FrameNode>, members: List<FrameMember>,
        memberLoads: List<MemberLoad>, nodalLoads: List<NodalLoad>,
        result: FrameAnalysisResult?, diagramType: DiagramType
    ): Bitmap? {
        if (nodes.isEmpty()) return null

        val w = 800; val h = 500
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; color = Color.BLACK }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; color = Color.DKGRAY }
        val diagramPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Calculate bounds
        val xMin = nodes.minOf { it.x } - 1; val xMax = nodes.maxOf { it.x } + 1
        val yMin = nodes.minOf { it.y } - 1; val yMax = nodes.maxOf { it.y } + 1
        val xSpan = max(xMax - xMin, 1.0); val ySpan = max(yMax - yMin, 1.0)
        val pad = 60f
        val scale = min((w - 2 * pad) / xSpan, (h - 2 * pad) / ySpan) * 0.8f
        val ox = pad + ((w - 2 * pad) - xSpan * scale) / 2f - xMin * scale
        val oy = pad + ((h - 2 * pad) - ySpan * scale) / 2f + yMax * scale

        fun sx(x: Double) = (x * scale + ox).toFloat()
        fun sy(y: Double) = (-y * scale + oy).toFloat()

        // Grid
        paint.color = Color.parseColor("#E0E0E0"); paint.strokeWidth = 1f
        for (x in ceil(xMin).toInt()..floor(xMax).toInt()) {
            canvas.drawLine(sx(x.toDouble()), 0f, sx(x.toDouble()), h.toFloat(), paint)
        }
        for (y in ceil(yMin).toInt()..floor(yMax).toInt()) {
            canvas.drawLine(0f, sy(y.toDouble()), w.toFloat(), sy(y.toDouble()), paint)
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
                val dScale = 30f / maxVal.toFloat()

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

    private fun fmt(d: Double): String = String.format("%.2f", d)

    private fun Bitmap.toByteArray(format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        this.compress(format, quality, stream)
        return stream.toByteArray()
    }
}

