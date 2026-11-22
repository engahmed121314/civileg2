package com.civileg.app.domain.drawing

import com.civileg.app.utils.EnglishFontProvider
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject
import java.io.ByteArrayOutputStream
import kotlin.math.*

/** Vector rendering of the same positioned scene used by the screen. No member design here. */
object PdfRenderer {
    private const val MM_TO_PT = 72.0 / 25.4

    fun renderToPdfBytes(scene: DrawingScene): ByteArray {
        val output = ByteArrayOutputStream()
        PdfDocument(PdfWriter(output)).use { pdf ->
            val size = PageSize((SheetComposer.SHEET_WIDTH_MM * MM_TO_PT).toFloat(),
                (SheetComposer.SHEET_HEIGHT_MM * MM_TO_PT).toFloat())
            val page = pdf.addNewPage(size)
            drawScene(PdfCanvas(page), pdf, scene, Rectangle(28f, 28f, size.width - 56f, size.height - 56f))
        }
        return output.toByteArray()
    }

    /** A form stays vector when inserted into the report's dedicated drawing page. */
    fun createForm(scene: DrawingScene, pdf: PdfDocument, widthPt: Float, heightPt: Float): PdfFormXObject {
        val form = PdfFormXObject(Rectangle(widthPt, heightPt))
        drawScene(PdfCanvas(form, pdf), pdf, scene, Rectangle(widthPt, heightPt))
        return form
    }

    fun drawScene(canvas: PdfCanvas, pdf: PdfDocument, scene: DrawingScene, target: Rectangle) {
        DrawingGeometry.validate(scene)
        val viewport = DrawingGeometry.fit(scene.bounds, target.width.toDouble(), target.height.toDouble(), 6.0)
        val font = EnglishFontProvider.getFont()
        fun mapped(p: Point2D): Point2D = viewport.map(p).let { Point2D(it.x + target.x, it.y + target.y) }
        fun line(a: Point2D, b: Point2D) {
            val p1 = mapped(a); val p2 = mapped(b)
            canvas.moveTo(p1.x, p1.y).lineTo(p2.x, p2.y).stroke()
        }
        fun path(points: List<Point2D>, closed: Boolean = false) {
            val first = mapped(points.first())
            canvas.moveTo(first.x, first.y)
            points.drop(1).forEach { val p = mapped(it); canvas.lineTo(p.x, p.y) }
            if (closed) canvas.closePath()
            canvas.stroke()
        }
        fun label(value: String, position: Point2D, height: Double, angle: Double = 0.0, halo: Boolean = true) {
            val text = value
            require(text.none { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }) {
                "Report drawing labels must be supplied in English"
            }
            require(text.all { font.containsGlyph(it.code) }) { "Drawing label contains unsupported report-font characters" }
            val p = mapped(position)
            val size = (height * viewport.scale).toFloat()
            val radians = Math.toRadians(angle)
            val width = font.getWidth(text, size).toDouble()
            for (x in listOf(0.0, width)) for (y in listOf(-size * 0.25, size.toDouble())) {
                val px = p.x + x * cos(radians) - y * sin(radians)
                val py = p.y + x * sin(radians) + y * cos(radians)
                require(px >= target.x && px <= target.x + target.width && py >= target.y && py <= target.y + target.height) {
                    "Drawing label exceeds the drawing page; expand scene bounds or reposition labels"
                }
            }
            canvas.saveState().concatMatrix(cos(radians), sin(radians), -sin(radians), cos(radians), p.x, p.y)
            if (halo) {
                canvas.saveState().setFillColor(DeviceRgb(255, 255, 255))
                    .rectangle(-1.0, -size * 0.25, font.getWidth(text, size).toDouble() + 2.0, size * 1.3)
                    .fill().restoreState()
            }
            canvas.beginText().setFontAndSize(font, size).moveText(0.0, 0.0).showText(text).endText().restoreState()
        }
        canvas.saveState()
        try {
            for (p in scene.primitives) {
                val rgb = DrawingGeometry.color(p.layer)
                val color = DeviceRgb((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
                canvas.setStrokeColor(color).setFillColor(color)
                // Line weights are physical mm, independent of model-to-page scale.
                canvas.setLineWidth((p.layer.lineweightMm * MM_TO_PT).toFloat())
                if (p.layer.linetype == "CENTER") canvas.setLineDash(floatArrayOf(7f, 2f, 1f, 2f), 0f)
                else canvas.setLineDash(0f)
                when (p) {
                    is Primitive.Line -> line(p.p1, p.p2)
                    is Primitive.Polyline -> path(p.points, p.isClosed)
                    is Primitive.Circle -> { val c = mapped(p.center); canvas.circle(c.x, c.y, p.radius * viewport.scale).stroke() }
                    is Primitive.Arc -> path(DrawingGeometry.arcPoints(p))
                    is Primitive.Text -> label(p.text, p.position, p.heightMm, p.rotationDeg, p.halo || p.pillBackground)
                    is Primitive.Dimension -> {
                        line(p.p1, p.p2)
                        // Endpoint ticks, not invented structural dimensions.
                        val tick = 2.5 / viewport.scale
                        for (point in listOf(p.p1, p.p2)) line(Point2D(point.x - tick, point.y - tick), Point2D(point.x + tick, point.y + tick))
                        label(p.text, p.textPosition, 9.0 / viewport.scale)
                    }
                    is Primitive.RebarDot -> { val c = mapped(p.center); canvas.circle(c.x, c.y, p.diameterMm / 2 * viewport.scale).fill() }
                    is Primitive.Leader -> {
                        path(p.points)
                        label(p.text, p.points.last(), 9.0 / viewport.scale)
                    }
                }
            }
        } finally { canvas.restoreState() }
    }
}
