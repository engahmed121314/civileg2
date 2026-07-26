package com.civileg.app.utils

import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.BaseDirection
import com.itextpdf.layout.properties.TextAlignment

/**
 * مجزئ نصوص PDF - PDF Text Segmenter
 *
 * ********************************************************************
 * ROOT CAUSE OF "ENCRYPTED TEXT" PDF BUG (SOLVED 2026-07-26)
 * ********************************************************************
 * SYMPTOM: User reported that PDF reports from every design section
 * showed "encrypted/unreadable encoding in an unknown language".
 *
 * ROOT CAUSE: The bundled NotoNaskhArabic font (a STATIC-weight TTF
 * from Google Fonts) only contains 15 Latin characters (space, digits,
 * comma, period, exclamation, colon). It does NOT contain letters A-Z/a-z
 * or most punctuation.
 *
 * Previous code in styledParagraph() did this:
 *   if (text contains Arabic) {
 *       use ArabicFont for the WHOLE text  // <-- BUG
 *   } else {
 *       use Helvetica
 *   }
 *
 * For mixed text like "Project: مشروع" or "STATUS: SAFE - مطابق للأكواد",
 * the entire text was rendered with the Arabic font. The Arabic letters
 * rendered correctly, but every Latin letter (P, r, o, j, e, c, t, S, T, A, U, S)
 * and most punctuation (: - ! ?) rendered as TOFU (□) or as nothing —
 * producing visually garbled output that looked like "encrypted text".
 *
 * Additionally, the previous bundled font was a VARIABLE font (had `fvar`
 * table) which iText 8.0.5 has limited support for — it could potentially
 * fail to load and silently fall back to Helvetica, which then renders
 * Arabic UTF-8 bytes as Windows-1252 characters (e.g., "الرئيسية" →
 * "Ø§Ù„Ø±Ø¦ÙŠØ³ÙŠØ©") — this is the literal "encrypted-looking" text
 * the user reported.
 *
 * FIX:
 * 1. Replace variable font with static-weight TTF (Regular weight=400,
 *    Bold weight=700, different files, no `fvar` table).
 * 2. For any text containing Arabic, split it into ARABIC and NON-ARABIC
 *    segments. Render Arabic segments with the Arabic font (after shaping
 *    via ArabicShaper). Render non-Arabic segments with Helvetica.
 * 3. Add all Text runs to the SAME Paragraph and set BaseDirection RTL
 *    on the Paragraph so iText's Unicode Bidi Algorithm properly orders
 *    the mixed-direction text.
 *
 * This produces correctly-rendered Arabic letters WITH properly-rendered
 * Latin letters and punctuation in the same paragraph, ordered correctly.
 */
object PdfTextSegmenter {

    /**
     * Check if a character is Arabic (base letter, presentation form, or diacritic).
     */
    private fun isArabicChar(c: Char): Boolean {
        val code = c.code
        return code in 0x0600..0x06FF ||      // Arabic
               code in 0x0750..0x077F ||      // Arabic Supplement
               code in 0x08A0..0x08FF ||      // Arabic Extended-A
               code in 0xFB50..0xFDFF ||      // Arabic Presentation Forms-A
               code in 0xFE70..0xFEFF         // Arabic Presentation Forms-B
    }

    /**
     * A text segment with a uniform script (Arabic or non-Arabic).
     */
    private data class Segment(val text: String, val isArabic: Boolean)

    /**
     * Split text into alternating Arabic and non-Arabic segments.
     *
     * Consecutive characters of the same script type are grouped into one segment.
     * Whitespace and punctuation are kept with whatever segment they're adjacent to
     * (specifically: a space between two Arabic segments joins the Arabic side).
     *
     * @param text Input text (may be Arabic, Latin, mixed, or empty)
     * @return List of segments; never empty (returns one segment for empty input)
     */
    private fun segment(text: String): List<Segment> {
        if (text.isEmpty()) return listOf(Segment("", false))

        val segments = mutableListOf<Segment>()
        val current = StringBuilder()
        var currentIsArabic = isArabicChar(text[0])

        for (c in text) {
            val cIsArabic = isArabicChar(c)
            // Whitespace and punctuation inherit the current segment's script
            val inheritCurrent = c.isWhitespace() || (c.code in 0x20..0x40) || (c.code in 0x5B..0x60) || (c.code in 0x7B..0x7E)
            val effectiveArabic = if (inheritCurrent) currentIsArabic else cIsArabic

            if (effectiveArabic != currentIsArabic && current.isNotEmpty()) {
                segments.add(Segment(current.toString(), currentIsArabic))
                current.clear()
                currentIsArabic = effectiveArabic
            } else {
                currentIsArabic = effectiveArabic
            }
            current.append(c)
        }
        if (current.isNotEmpty()) {
            segments.add(Segment(current.toString(), currentIsArabic))
        }
        return segments
    }

    /**
     * Build a Paragraph with proper per-segment font selection for mixed Arabic/Latin text.
     *
     * - Arabic segments are shaped (ArabicShaper.shapeIfArabic) and rendered with the Arabic font
     * - Non-Arabic segments (Latin, digits, punctuation) are rendered with the Latin font
     * - All segments are added as Text runs to the SAME Paragraph
     * - Paragraph BaseDirection is set to RIGHT_TO_LEFT so iText's Unicode Bidi
     *   Algorithm properly orders mixed-direction text
     *
     * @param text The text to render (may be Arabic, Latin, mixed, or empty)
     * @param arabicFont A FRESH PdfFont for Arabic (from ArabicFontProvider)
     * @param latinFont A FRESH PdfFont for Latin (Helvetica or similar)
     * @param fontSize Font size in points
     * @param bold Whether text should be bold (note: boldness is controlled by which font variant
     *             is passed in — caller should pass arabicBoldFont() / helveticaBoldFont() if needed)
     * @param color Optional text color
     * @param alignment Optional text alignment (defaults to RIGHT for Arabic text, LEFT for Latin)
     * @return A Paragraph with proper per-segment rendering
     */
    fun buildMixedParagraph(
        text: String,
        arabicFont: PdfFont,
        latinFont: PdfFont,
        fontSize: Float = 10f,
        color: DeviceRgb? = null,
        alignment: TextAlignment? = null
    ): Paragraph {
        val p = Paragraph().setFontSize(fontSize)
        color?.let { p.setFontColor(it) }

        if (text.isEmpty()) {
            return p
        }

        val segments = segment(text)
        val hasArabic = segments.any { it.isArabic }

        for (seg in segments) {
            if (seg.text.isEmpty()) continue
            val run = if (seg.isArabic) {
                // Pass BASE Arabic characters directly to iText — do NOT pre-shape.
                // iText 8 + IDENTITY_H encoding will apply the font's OpenType GSUB
                // tables for Arabic letter shaping (initial/medial/final/isolated forms).
                // Pre-shaping to Presentation Forms (FE70-FEFF) via ArabicShaper causes
                // DOUBLE-SHAPING when iText also applies GSUB, producing garbled output.
                // Even if iText doesn't apply GSUB, base Arabic letters are still
                // READABLE (just disconnected) — much better than garbled text.
                Text(seg.text).setFont(arabicFont)
            } else {
                Text(seg.text).setFont(latinFont)
            }
            run.setFontSize(fontSize)
            p.add(run)
        }

        if (hasArabic) {
            p.setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
            if (alignment == null) {
                p.setTextAlignment(TextAlignment.RIGHT)
            }
        } else {
            if (alignment == null) {
                p.setTextAlignment(TextAlignment.LEFT)
            }
        }

        if (alignment != null) {
            p.setTextAlignment(alignment)
        }

        return p
    }
}
