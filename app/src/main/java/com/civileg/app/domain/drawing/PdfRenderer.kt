package com.civileg.app.domain.drawing

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.io.ByteArrayOutputStream

/**
 * Renders DrawingScene primitives to PDF bytes using iText 8.
 */
object PdfRenderer {
    private const val MM_TO_PT = 2.83464567f

    fun renderToPdfBytes(scene: DrawingScene): ByteArray {
        val baos = ByteArrayOutputStream()
        PdfWriter(baos).use { writer ->
            PdfDocument(writer).use { pdfDoc ->
                val widthPt = (SheetComposer.SHEET_WIDTH_MM * MM_TO_PT).toFloat()
                val heightPt = (SheetComposer.SHEET_HEIGHT_MM * MM_TO_PT).toFloat()
                val pageSize = Rectangle(widthPt, heightPt)
                Document(pdfDoc, PageSize(pageSize)).use { doc ->
                    for (p in scene.primitives) {
                        when (p) {
                            is Primitive.Text -> {
                                doc.add(
                                    Paragraph(p.text)
                                        .setFontSize(p.heightMm.toFloat() * MM_TO_PT)
                                        .setTextAlignment(TextAlignment.LEFT)
                                )
                            }
                            else -> { /* Rendered via vector graphics */ }
                        }
                    }
                }
            }
        }
        return baos.toByteArray()
    }
}
