package com.civileg.app.utils

import android.content.Context
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.ColorConstants
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
 * CRITICAL FIX for Arabic rendering issues:
 * - Does NOT split bilingual text into segments (this breaks iText's bidi algorithm)
 * - Uses a SINGLE Text run with the Arabic font for any text containing Arabic
 *   (NotoNaskhArabic includes Latin glyphs so this is safe)
 * - Sets BaseDirection.RIGHT_TO_LEFT on the Paragraph (not on individual Text runs)
 *   so iText's Unicode Bidi Algorithm properly orders mixed Arabic/Latin text
 * - Sets proper TextAlignment based on language context
 *
 * This solves the "disconnected letters" and "squares" issues in PDF reports.
 */
object BilingualPdfHelper {

    /** Lazy-initialized fonts — created on first use */
    @Volatile private var regularFont: PdfFont? = null
    @Volatile private var boldFont: PdfFont? = null
    @Volatile private var helveticaFont: PdfFont? = null
    @Volatile private var helveticaBoldFont: PdfFont? = null

    fun initFonts(context: Context) {
        if (regularFont == null) {
            regularFont = ArabicFontProvider.getArabicPdfFont(context, bold = false)
        }
        if (boldFont == null) {
            boldFont = ArabicFontProvider.getArabicPdfFont(context, bold = true)
        }
        if (helveticaFont == null) {
            helveticaFont = try { PdfFontFactory.createFont(StandardFonts.HELVETICA) } catch (_: Exception) { null }
        }
        if (helveticaBoldFont == null) {
            helveticaBoldFont = try { PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD) } catch (_: Exception) { null }
        }
    }

    /** Get the appropriate Arabic-capable font (also has Latin glyphs) */
    fun getFont(context: Context, bold: Boolean = false): PdfFont {
        initFonts(context)
        return if (bold) boldFont!! else regularFont!!
    }

    /** Get Latin-only font for pure-English text (smaller file size) */
    fun getLatinFont(bold: Boolean = false): PdfFont {
        return if (bold) helveticaBoldFont ?: helveticaFont!! else helveticaFont!!
    }

    /**
     * Create a properly-shaped paragraph for any text containing Arabic characters.
     *
     * KEY INSIGHT: iText 8 applies the Unicode Bidi Algorithm AND Arabic shaping
     * automatically when:
     * 1. The font has GSUB/GPOS tables (NotoNaskhArabic does)
     * 2. The Paragraph has BaseDirection.RIGHT_TO_LEFT set
     * 3. The text is added as a SINGLE Text run (not split into segments)
     *
     * Splitting text into Arabic/Latin segments BREAKS the bidi algorithm because
     * iText processes the bidi algorithm at the Paragraph level, not per Text run.
     * This causes letters to appear disconnected or out of order.
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
        val p = Paragraph().setFontSize(fontSize)
        color?.let { p.setFontColor(it) }
        alignment?.let { p.setTextAlignment(it) }

        val hasArabic = ArabicFontProvider.containsArabic(text)

        if (hasArabic) {
            // Use Arabic font (which also has Latin glyphs) for the entire text
            // This lets iText's bidi algorithm properly shape and order mixed text
            val font = getFont(context, bold)
            val run = Text(text).setFont(font)
            p.add(run)
            // Set paragraph base direction to RTL so bidi algorithm processes correctly
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            // Default to right alignment for Arabic unless specified
            if (alignment == null) {
                p.setTextAlignment(TextAlignment.RIGHT)
            }
        } else {
            // Pure Latin/numeric text — use Helvetica
            val font = getLatinFont(bold)
            val run = Text(text).setFont(font)
            p.add(run)
            if (alignment == null) {
                p.setTextAlignment(TextAlignment.LEFT)
            }
        }

        return p
    }

    /**
     * Create a table cell with proper Arabic shaping.
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
        // If Arabic, also set cell base direction for proper layout
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
}
