package com.civileg.app.domain.drawing.adapters

import com.civileg.app.domain.drawing.*
import com.civileg.app.domain.entities.HordiCalculatedSectionSnapshot
import com.civileg.app.utils.EnglishFontProvider
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.Property
import com.itextpdf.layout.properties.OverflowWrapPropertyValue
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

/** One bounded, adopted section cut. It is never a complete-member drawing package. */
class HordiSectionDrawing internal constructor(
    val revisionId: String,
    val memberMark: String,
    val methodReference: String,
    val requiredSteelMm2: Double,
    val providedSteelMm2: Double,
    val placementBasis: String,
    incompleteChecks: List<String>,
    val view: DesignDrawingView
) {
    val incompleteChecks: List<String> = Collections.unmodifiableList(incompleteChecks.toList())
    val executionExportAllowed: Boolean get() = false
}

object HordiCalculatedSectionAdapter {
    private fun number(value: Double): String = String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

    /** No design is solved here. Every dot uses its supplied index, coordinate and diameter. */
    fun build(output: HordiCalculatedSectionSnapshot, memberMark: String): HordiSectionDrawing {
        require(output.revisionId.matches(Regex("[0-9a-fA-F]{64}"))) { "A frozen input/result revision digest is required" }
        require(memberMark.isNotBlank() && memberMark.length <= 64)
        require(output.methodReference.isNotBlank())
        require(output.requiredSteelMm2.isFinite() && output.requiredSteelMm2 >= 0.0)
        require(output.providedSteelMm2.isFinite() && output.providedSteelMm2 > 0.0)
        val section = output.section
        require(section.geometryVersion == 1) { "Unsupported section coordinate version" }
        require(listOf(section.ribWidthMm, section.enteredRibPitchMm, section.totalDepthMm,
            section.toppingThicknessMm, section.analyticalFlangeWidthMm).all { it.isFinite() && it > 0.0 })
        require(section.enteredRibPitchMm > section.ribWidthMm)
        require(section.toppingThicknessMm < section.totalDepthMm)
        require(section.analyticalFlangeWidthMm <= section.enteredRibPitchMm)
        require(section.tensionFace == "BOTTOM" || section.tensionFace == "TOP")
        require(section.placementBasis.isNotBlank())
        require(listOf(section.coverToOutsideLinksMm, section.linkDiameterMm, section.linkSpacingMm).all { it.isFinite() && it >= 0.0 })
        require(section.linkLegs >= 0)
        val bars = requireNotNull(section.bars) { "Calculated bar coordinates are unavailable; no substitute bars may be drawn" }
        require(bars.isNotEmpty() && bars.size <= 4096)
        require(bars.map { it.layerIndex to it.indexInLayer }.distinct().size == bars.size) { "Duplicate bar indices" }
        bars.forEach {
            require(it.layerIndex >= 0 && it.indexInLayer >= 0)
            require(listOf(it.xFromWebLeftMm, it.yFromBottomMm, it.diameterMm).all { n -> n.isFinite() })
            require(it.diameterMm > 0.0)
            val radius = it.diameterMm / 2.0
            require(it.xFromWebLeftMm - radius >= 0.0 && it.xFromWebLeftMm + radius <= section.ribWidthMm)
            require(it.yFromBottomMm - radius >= 0.0 && it.yFromBottomMm + radius <= section.totalDepthMm)
        }
        val coordinateArea = bars.sumOf { PI * it.diameterMm * it.diameterMm / 4.0 }
        require(abs(coordinateArea - output.providedSteelMm2) <= max(1e-6, output.providedSteelMm2 * 1e-8)) {
            "Bar-coordinate area disagrees with the frozen result"
        }

        val pitch = section.enteredRibPitchMm
        val depth = section.totalDepthMm
        val webLeft = (pitch - section.ribWidthMm) / 2.0
        val webRight = webLeft + section.ribWidthMm
        val toppingBottom = depth - section.toppingThicknessMm
        val primitives = mutableListOf<Primitive>()
        fun line(x1: Double, y1: Double, x2: Double, y2: Double, layer: DrawingLayer = DrawingLayer.OUTLINE) {
            primitives += Primitive.Line(Point2D(x1, y1), Point2D(x2, y2), layer)
        }
        fun text(value: String, x: Double, y: Double, height: Double = 12.0) {
            primitives += Primitive.Text(value, Point2D(x, y), height, DrawingLayer.TEXT)
        }
        // Physical topping uses entered pitch, never analytical effective flange width.
        line(0.0, depth, pitch, depth)
        line(0.0, toppingBottom, webLeft, toppingBottom)
        line(webRight, toppingBottom, pitch, toppingBottom)
        primitives += Primitive.Polyline(listOf(Point2D(webLeft, toppingBottom), Point2D(webLeft, 0.0),
            Point2D(webRight, 0.0), Point2D(webRight, toppingBottom)), false, DrawingLayer.OUTLINE)
        // Repeating-cell cut markers, explicitly not invented free panel edges.
        line(0.0, toppingBottom - 8.0, 0.0, depth + 8.0, DrawingLayer.CENTERLINE)
        line(pitch, toppingBottom - 8.0, pitch, depth + 8.0, DrawingLayer.CENTERLINE)
        bars.forEach {
            primitives += Primitive.RebarDot(Point2D(webLeft + it.xFromWebLeftMm, it.yFromBottomMm),
                it.diameterMm, if (section.tensionFace == "TOP") DrawingLayer.REBAR_TOP else DrawingLayer.REBAR_BOTTOM)
        }
        primitives += Primitive.Dimension(Point2D(0.0, depth + 35.0), Point2D(pitch, depth + 35.0),
            Point2D(pitch * 0.23, depth + 45.0), "Pitch ${number(pitch)} mm")
        primitives += Primitive.Dimension(Point2D(webLeft, -35.0), Point2D(webRight, -35.0),
            Point2D(webLeft, -23.0), "Web ${number(section.ribWidthMm)} mm")
        primitives += Primitive.Dimension(Point2D(pitch + 45.0, 0.0), Point2D(pitch + 45.0, depth),
            Point2D(pitch + 60.0, depth * 0.50), "h ${number(depth)} mm")
        // Reserve baseline spacing for the renderer's readable 12sp floor at a
        // 320dp phone width (288dp after card padding). This changes annotation
        // placement only, never the supplied section dimensions or coordinates.
        val annotationGap = max(50.0, max((pitch + 255.0) / 16.0, (depth + 150.0) / 8.0))
        val webDimensionTextY = -23.0
        val pitchDimensionTextY = depth + 45.0
        text("t = ${number(section.toppingThicknessMm)} mm", 0.0, pitchDimensionTextY + annotationGap)
        text("Analytical b_eff = ${number(section.analyticalFlangeWidthMm)} mm", 0.0, pitchDimensionTextY + 2.0 * annotationGap)
        text("Repeating-cell cuts, not free edges.", 0.0, webDimensionTextY - annotationGap)
        text("Tension ${section.tensionFace}; ${bars.size} adopted bars.", 0.0, webDimensionTextY - 2.0 * annotationGap)
        val scene = DrawingScene(
            "Calculated one-pitch rib section, adopted bar centres, not as-built",
            Rect2D(-25.0, webDimensionTextY - 2.0 * annotationGap - 40.0,
                pitch + 230.0, pitchDimensionTextY + 2.0 * annotationGap + 40.0), primitives
        )
        val warnings = (output.incompleteChecks + section.incompleteGeometry).distinct()
        return HordiSectionDrawing(output.revisionId, memberMark, output.methodReference,
            output.requiredSteelMm2, output.providedSteelMm2, section.placementBasis,
            warnings, DesignDrawingView("rib-section", DrawingViewKind.SECTION, scene))
    }

    /** Bounded review proof only. This never enables an execution/BBS/BOQ export. */
    fun renderReviewPdfBytes(output: HordiCalculatedSectionSnapshot, memberMark: String): ByteArray {
        val drawing = build(output, memberMark)
        val buffer = ByteArrayOutputStream()
        PdfDocument(PdfWriter(buffer)).use { pdf ->
            Document(pdf, PageSize.A3.rotate()).use { document ->
                document.setMargins(28f, 28f, 28f, 28f)
                val font = EnglishFontProvider.getFont()
                document.setFont(font).setFontSize(9f)
                document.add(Paragraph("CALCULATED RIB SECTION - REVIEW ONLY, NOT FOR CONSTRUCTION").setBold().setFontSize(13f))
                document.add(Paragraph("Member: ${drawing.memberMark} | Revision: ${drawing.revisionId}"))
                document.add(Paragraph("Method: ${drawing.methodReference}"))
                document.add(Paragraph("Adopted calculated arrangement, not as-built. Required As = ${number(drawing.requiredSteelMm2)} mm²; provided As = ${number(drawing.providedSteelMm2)} mm²."))
                fun note(value: String): Paragraph = Paragraph(value).setMarginBottom(3f).apply {
                    setProperty(Property.OVERFLOW_WRAP, OverflowWrapPropertyValue.ANYWHERE)
                }
                val sheet = Table(UnitValue.createPercentArray(floatArrayOf(64f, 36f))).useAllAvailableWidth()
                val graphic = Cell().setPadding(5f).add(Image(PdfRenderer.createForm(drawing.view.scene, pdf, 700f, 580f)))
                val notes = Cell().setPadding(8f)
                notes.add(note("METHOD / MEMBER STATUS: UNVERIFIED FOR EXECUTION. Execution exports remain blocked.").setBold())
                notes.add(note("SUPPLIED LINKS: ${output.section.linkLegs} legs, diameter ${number(output.section.linkDiameterMm)} mm, spacing ${number(output.section.linkSpacingMm)} mm. Closure, bends and first-link offset are not supplied."))
                notes.add(note("Adopted bar centres: x from web left, y from physical bottom (mm). These are coordinates, not a bar bending schedule.").setBold())
                requireNotNull(output.section.bars).forEach { bar ->
                    notes.add(note("L${bar.layerIndex + 1}/B${bar.indexInLayer + 1}: x ${number(bar.xFromWebLeftMm)}, y ${number(bar.yFromBottomMm)}, dia ${number(bar.diameterMm)} mm"))
                }
                drawing.incompleteChecks.forEach { notes.add(note("OPEN: $it")) }
                sheet.addCell(graphic).addCell(notes)
                document.add(sheet)
            }
        }
        return buffer.toByteArray()
    }
}
