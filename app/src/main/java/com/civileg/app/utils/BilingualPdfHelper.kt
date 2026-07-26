package com.civileg.app.utils

import android.content.Context
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.BaseDirection
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue

/**
 * مساعد نصوص PDF ثنائي اللغة - Bilingual PDF Text Helper
 *
 * ********************************************************************
 * CRITICAL FIX (2026-07-26): PdfFont Caching Bug
 * ********************************************************************
 * PREVIOUS BUG: PdfFont objects were cached in regularFont / boldFont
 * fields and reused across multiple PdfDocuments. iText 8 binds a PdfFont
 * to the FIRST PdfDocument that uses it. After that document is closed,
 * the cached PdfFont becomes invalid and any attempt to use it in a NEW
 * PdfDocument throws:
 *
 *   "Pdf indirect object belongs to other PDF document. Copy object to current pdf document."
 *
 * This caused PDF reports from any design section to FAIL on the 2nd+
 * attempt. The fix is to NEVER cache PdfFont objects — always get a fresh
 * one via ArabicFontProvider (which caches the underlying FontProgram
 * but creates a fresh PdfFont wrapper per call).
 *
 * ********************************************************************
 * Arabic Shaping Note
 * ********************************************************************
 * iText 8 AGPL does NOT include the pdfCalligraph module required for
 * automatic Arabic letter shaping. We use ArabicShaper to manually
 * convert base Arabic letters (0x0621-0x064A) to their contextual
 * Presentation Forms (0xFE70-0xFEFF) which the bundled NotoNaskhArabic
 * font supports directly. This produces properly CONNECTED Arabic letters
 * instead of disconnected squares.
 *
 * Text is added as a SINGLE Text run with the Arabic font (which also
 * has Latin glyphs) so iText's Unicode Bidi Algorithm properly orders
 * mixed Arabic/Latin text. BaseDirection.RIGHT_TO_LEFT is set on the
 * Paragraph (not on individual Text runs).
 */
object BilingualPdfHelper {

    /**
     * Get a FRESH Arabic-capable PdfFont for use in a NEW PdfDocument.
     *
     * CRITICAL: Never cache the returned PdfFont — it becomes invalid
     * after the PdfDocument that uses it is closed. Always call this
     * method when starting a new PDF.
     */
    fun getFont(context: Context, bold: Boolean = false): PdfFont {
        return ArabicFontProvider.getArabicPdfFont(context, bold)
    }

    /**
     * Create a fresh Helvetica font for pure-English text.
     * Standard 14 fonts are not bound to a specific PdfDocument, so
     * caching is safe here — but we still create fresh per call for
     * consistency with the Arabic font pattern.
     */
    fun getLatinFont(bold: Boolean = false): PdfFont {
        return if (bold) {
            try { PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD) }
            catch (_: Exception) { PdfFontFactory.createFont(StandardFonts.HELVETICA) }
        } else {
            PdfFontFactory.createFont(StandardFonts.HELVETICA)
        }
    }

    /**
     * Create a properly-shaped paragraph for any text containing Arabic characters.
     *
     * IMPORTANT: Caller must pass a FRESH font obtained from getFont() for the
     * current PdfDocument. Never reuse a PdfFont across documents.
     *
     * @param context Application context
     * @param text The text to render (may be Arabic, English, or mixed)
     * @param fontSize Font size in points
     * @param bold Whether to use bold weight
     * @param color Optional text color (DeviceRgb)
     * @param alignment Optional text alignment
     * @return A Paragraph with proper RTL shaping applied
     */
    fun styledParagraph(
        context: Context,
        text: String,
        fontSize: Float = 10f,
        bold: Boolean = false,
        color: DeviceRgb? = null,
        alignment: TextAlignment? = null
    ): Paragraph {
        // Always create a FRESH font — never reuse across PdfDocuments
        val font = getFont(context, bold)
        return styledParagraphWithFont(text, font, fontSize, bold, color, alignment)
    }

    /**
     * Create a styled paragraph using an EXISTING font (avoid creating multiple
     * fonts for the same PdfDocument). The font MUST be fresh for this PdfDocument.
     *
     * CRITICAL FIX (2026-07-26): Use PdfTextSegmenter to split mixed Arabic/Latin text
     * into segments. Arabic segments use the provided font; Latin segments use a fresh
     * Helvetica (created here). This prevents the "encrypted-looking" output that
     * occurred when Latin chars in mixed text were rendered with the Arabic font
     * (which only has 15 Latin chars).
     */
    fun styledParagraphWithFont(
        text: String,
        font: PdfFont,
        fontSize: Float = 10f,
        bold: Boolean = false,
        color: DeviceRgb? = null,
        alignment: TextAlignment? = null
    ): Paragraph {
        val latinFont = if (bold) getLatinFont(true) else getLatinFont(false)
        return PdfTextSegmenter.buildMixedParagraph(
            text = text,
            arabicFont = font,
            latinFont = latinFont,
            fontSize = fontSize,
            color = color,
            alignment = alignment
        )
    }

    /**
     * Create a table cell with proper Arabic shaping.
     * Uses a FRESH font from ArabicFontProvider (safe per PdfDocument).
     */
    fun styledCell(
        context: Context,
        text: String,
        fontSize: Float = 9f,
        bold: Boolean = false,
        color: DeviceRgb? = null,
        bg: DeviceRgb? = null,
        alignment: TextAlignment = TextAlignment.CENTER
    ): Cell {
        val cell = Cell().setPadding(4f)
        val p = styledParagraph(context, text, fontSize, bold, color, alignment)
        if (ArabicFontProvider.containsArabic(text)) {
            cell.setTextAlignment(TextAlignment.RIGHT)
        }
        cell.add(p)
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    /**
     * Create a styled cell using an EXISTING font (avoid creating multiple
     * fonts per cell — pass one fresh font per PdfDocument and reuse it).
     */
    fun styledCellWithFont(
        text: String,
        font: PdfFont,
        fontSize: Float = 9f,
        bold: Boolean = false,
        color: DeviceRgb? = null,
        bg: DeviceRgb? = null,
        alignment: TextAlignment = TextAlignment.CENTER
    ): Cell {
        val cell = Cell().setPadding(4f)
        val p = styledParagraphWithFont(text, font, fontSize, bold, color, alignment)
        if (ArabicFontProvider.containsArabic(text)) {
            cell.setTextAlignment(TextAlignment.RIGHT)
        }
        cell.add(p)
        bg?.let { cell.setBackgroundColor(it) }
        return cell
    }

    /**
     * Create a header cell with white text on dark background.
     */
    fun headerCell(
        context: Context,
        text: String,
        bg: DeviceRgb = DeviceRgb(21, 101, 192),
        colSpan: Int = 1
    ): Cell {
        val cell = Cell(1, colSpan)
            .setPadding(6f)
            .setBackgroundColor(bg)
            .setTextAlignment(TextAlignment.CENTER)
        val p = styledParagraph(
            context, text,
            fontSize = 9f,
            bold = true,
            color = DeviceRgb(255, 255, 255),
            alignment = TextAlignment.CENTER
        )
        cell.add(p)
        return cell
    }

    /**
     * Create a header cell using an EXISTING font.
     */
    fun headerCellWithFont(
        text: String,
        font: PdfFont,
        bg: DeviceRgb = DeviceRgb(21, 101, 192),
        colSpan: Int = 1
    ): Cell {
        val cell = Cell(1, colSpan)
            .setPadding(6f)
            .setBackgroundColor(bg)
            .setTextAlignment(TextAlignment.CENTER)
        val p = styledParagraphWithFont(
            text, font,
            fontSize = 9f,
            bold = true,
            color = DeviceRgb(255, 255, 255),
            alignment = TextAlignment.CENTER
        )
        cell.add(p)
        return cell
    }
}
