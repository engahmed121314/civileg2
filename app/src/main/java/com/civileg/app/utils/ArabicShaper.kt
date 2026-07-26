package com.civileg.app.utils

/**
 * مشكّل الحروف العربية - Arabic Letter Shaper
 *
 * ROOT CAUSE OF ARABIC PDF BUG:
 * iText 8 open-source AGPL version does NOT apply Arabic letter shaping.
 * Without shaping, each Arabic letter is rendered in its ISOLATED form
 * (Unicode 0x0621-0x064A), which means letters appear DISCONNECTED
 * (no contextual joining) — the user sees "squares" or "garbled text"
 * instead of properly connected Arabic words.
 *
 * The commercial iText typography module solves this, but it's paid.
 * This utility solves it for FREE by manually converting each Arabic
 * base letter to its Presentation Form (0xFE80-0xFEFF) based on
 * contextual position (isolated / initial / medial / final).
 *
 * The font NotoNaskhArabic (bundled in assets/fonts/) contains all
 * 140 Arabic Presentation Forms-B glyphs + 8 Lam-Alef ligature glyphs,
 * so the conversion is fully supported.
 *
 * BIDI REORDERING:
 * iText's BaseDirection.RIGHT_TO_LEFT already handles the visual
 * RTL reordering at the Paragraph level. So we only need to do the
 * letter shaping here. The text remains in logical order in the string.
 *
 * ALGORITHM:
 * For each Arabic letter X at position i:
 *   - prevJoins: does the previous letter connect to X?
 *       TRUE iff chars[i-1] is dual-joining AND is Arabic
 *   - nextJoins: does X connect to the next letter?
 *       TRUE iff X is dual-joining AND chars[i+1] exists AND
 *       chars[i+1] is dual-joining OR right-joining
 *   - Form selection:
 *       prevJoins && nextJoins → MEDIAL
 *       prevJoins && !nextJoins → FINAL
 *       !prevJoins && nextJoins → INITIAL
 *       !prevJoins && !nextJoins → ISOLATED
 *
 *   For right-joining letters (ALEF, WAW, etc.):
 *       prevJoins → FINAL
 *       else      → ISOLATED
 *
 *   For non-joining letters (HAMZA): always ISOLATED
 *
 * SPECIAL CASES:
 *   - LAM (0x0644) followed by ALEF variant → Lam-Alef ligature
 *   - TATWEEL (0x0640) — kept as-is (joining extender)
 *   - Diacritics (0x064B-0x065F) — kept as-is
 *   - Superscript ALEF (0x0670) — kept as-is
 *
 * REFERENCES:
 *   - Unicode Standard Annex #9 (Arabic Bidi Algorithm)
 *   - Unicode Standard §9.2 (Arabic)
 *   - Arabic Presentation Forms-B block (U+FE70–U+FEFF)
 */
object ArabicShaper {

    // Joining types
    private const val TYPE_DUAL = 'D'      // dual-joining (connects both sides)
    private const val TYPE_RIGHT = 'R'     // right-joining (connects to previous only)
    private const val TYPE_NON = 'U'       // non-joining
    private const val TYPE_OTHER = 'N'     // non-Arabic / not in our table

    /**
     * Get the joining type of an Arabic letter.
     * Based on Unicode Standard §9.2 joining types.
     */
    private fun joiningType(c: Char): Char {
        return when (c.code) {
            0x0621 -> TYPE_NON   // HAMZA
            0x0622 -> TYPE_RIGHT // ALEF WITH MADDA ABOVE
            0x0623 -> TYPE_RIGHT // ALEF WITH HAMZA ABOVE
            0x0624 -> TYPE_RIGHT // WAW WITH HAMZA ABOVE
            0x0625 -> TYPE_RIGHT // ALEF WITH HAMZA BELOW
            0x0626 -> TYPE_DUAL  // YEH WITH HAMZA ABOVE
            0x0627 -> TYPE_RIGHT // ALEF
            0x0628 -> TYPE_DUAL  // BEH
            0x0629 -> TYPE_RIGHT // TEH MARBUTA
            0x062A -> TYPE_DUAL  // TEH
            0x062B -> TYPE_DUAL  // THEH
            0x062C -> TYPE_DUAL  // JEEM
            0x062D -> TYPE_DUAL  // HAH
            0x062E -> TYPE_DUAL  // KHAH
            0x062F -> TYPE_RIGHT // DAL
            0x0630 -> TYPE_RIGHT // THAL
            0x0631 -> TYPE_RIGHT // REH
            0x0632 -> TYPE_RIGHT // ZAIN
            0x0633 -> TYPE_DUAL  // SEEN
            0x0634 -> TYPE_DUAL  // SHEEN
            0x0635 -> TYPE_DUAL  // SAD
            0x0636 -> TYPE_DUAL  // DAD
            0x0637 -> TYPE_DUAL  // TAH
            0x0638 -> TYPE_DUAL  // ZAH
            0x0639 -> TYPE_DUAL  // AIN
            0x063A -> TYPE_DUAL  // GHAIN
            0x0641 -> TYPE_DUAL  // FEH
            0x0642 -> TYPE_DUAL  // QAF
            0x0643 -> TYPE_DUAL  // KAF
            0x0644 -> TYPE_DUAL  // LAM
            0x0645 -> TYPE_DUAL  // MEEM
            0x0646 -> TYPE_DUAL  // NOON
            0x0647 -> TYPE_DUAL  // HEH
            0x0648 -> TYPE_RIGHT // WAW
            0x0649 -> TYPE_RIGHT // ALEF MAKSURA
            0x064A -> TYPE_DUAL  // YEH
            // Extended Arabic letters (used in some regions)
            0x066E -> TYPE_DUAL  // BEH WITH DOT BELOW
            0x066F -> TYPE_DUAL  // QAF WITH DOT ABOVE
            0x06A1 -> TYPE_DUAL  // BEH with hamza
            0x06BA -> TYPE_DUAL  // NOON WITH DOT BELOW
            0x06BE -> TYPE_DUAL  // HEH DOACHASHMEE
            0x06C1 -> TYPE_DUAL  // HEH GOAL
            0x06C2 -> TYPE_DUAL  // HEH GOAL WITH HAMZA ABOVE
            0x06CB -> TYPE_DUAL  // VEH
            0x06CE -> TYPE_DUAL  // YEH BARREE
            else -> TYPE_OTHER
        }
    }

    /** Whether the letter accepts a join from the previous letter */
    private fun acceptsFromPrev(c: Char): Boolean {
        val t = joiningType(c)
        return t == TYPE_DUAL || t == TYPE_RIGHT
    }

    /** Whether the letter extends a join to the next letter */
    private fun extendsToNext(c: Char): Boolean {
        return joiningType(c) == TYPE_DUAL
    }

    /**
     * Presentation Forms mapping.
     * Key: base letter code point (0x0621-0x064A)
     * Value: intArrayOf(isolated, initial, medial, final)
     * Value of -1 means form not available (use isolated or final fallback)
     *
     * Source: Unicode Arabic Presentation Forms-B block (U+FE70–U+FEFF)
     */
    private val PRESENTATION_FORMS: Map<Int, IntArray> = mapOf(
        0x0621 to intArrayOf(0xFE80, 0xFE80, 0xFE80, 0xFE80),  // HAMZA (isolated only)
        0x0622 to intArrayOf(0xFE81, 0xFE81, 0xFE82, 0xFE82),  // ALEF MADDA
        0x0623 to intArrayOf(0xFE83, 0xFE83, 0xFE84, 0xFE84),  // ALEF HAMZA ABOVE
        0x0624 to intArrayOf(0xFE85, 0xFE85, 0xFE86, 0xFE85),  // WAW HAMZA
        0x0625 to intArrayOf(0xFE87, 0xFE87, 0xFE88, 0xFE87),  // ALEF HAMZA BELOW
        0x0626 to intArrayOf(0xFE89, 0xFE8B, 0xFE8C, 0xFE8A),  // YEH HAMZA
        0x0627 to intArrayOf(0xFE8D, 0xFE8D, 0xFE8E, 0xFE8D),  // ALEF
        0x0628 to intArrayOf(0xFE8F, 0xFE91, 0xFE92, 0xFE90),  // BEH
        0x0629 to intArrayOf(0xFE93, 0xFE93, 0xFE94, 0xFE93),  // TEH MARBUTA
        0x062A to intArrayOf(0xFE95, 0xFE97, 0xFE98, 0xFE96),  // TEH
        0x062B to intArrayOf(0xFE99, 0xFE9B, 0xFE9C, 0xFE9A),  // THEH
        0x062C to intArrayOf(0xFE9D, 0xFE9F, 0xFEA0, 0xFE9E),  // JEEM
        0x062D to intArrayOf(0xFEA1, 0xFEA3, 0xFEA4, 0xFEA2),  // HAH
        0x062E to intArrayOf(0xFEA5, 0xFEA7, 0xFEA8, 0xFEA6),  // KHAH
        0x062F to intArrayOf(0xFEA9, 0xFEA9, 0xFEAA, 0xFEA9),  // DAL
        0x0630 to intArrayOf(0xFEAB, 0xFEAB, 0xFEAC, 0xFEAB),  // THAL
        0x0631 to intArrayOf(0xFEAD, 0xFEAD, 0xFEAE, 0xFEAD),  // REH
        0x0632 to intArrayOf(0xFEAF, 0xFEAF, 0xFEB0, 0xFEAF),  // ZAIN
        0x0633 to intArrayOf(0xFEB1, 0xFEB3, 0xFEB4, 0xFEB2),  // SEEN
        0x0634 to intArrayOf(0xFEB5, 0xFEB7, 0xFEB8, 0xFEB6),  // SHEEN
        0x0635 to intArrayOf(0xFEB9, 0xFEBB, 0xFEBC, 0xFEBA),  // SAD
        0x0636 to intArrayOf(0xFEBD, 0xFEBF, 0xFEC0, 0xFEBE),  // DAD
        0x0637 to intArrayOf(0xFEC1, 0xFEC3, 0xFEC4, 0xFEC2),  // TAH
        0x0638 to intArrayOf(0xFEC5, 0xFEC7, 0xFEC8, 0xFEC6),  // ZAH
        0x0639 to intArrayOf(0xFEC9, 0xFECB, 0xFECC, 0xFECA),  // AIN
        0x063A to intArrayOf(0xFECD, 0xFECF, 0xFED0, 0xFECE),  // GHAIN
        0x0641 to intArrayOf(0xFED1, 0xFED3, 0xFED4, 0xFED2),  // FEH
        0x0642 to intArrayOf(0xFED5, 0xFED7, 0xFED8, 0xFED6),  // QAF
        0x0643 to intArrayOf(0xFED9, 0xFEDB, 0xFEDC, 0xFEDA),  // KAF
        0x0644 to intArrayOf(0xFEDD, 0xFEDF, 0xFEE0, 0xFEDE),  // LAM
        0x0645 to intArrayOf(0xFEE1, 0xFEE3, 0xFEE4, 0xFEE2),  // MEEM
        0x0646 to intArrayOf(0xFEE5, 0xFEE7, 0xFEE8, 0xFEE6),  // NOON
        0x0647 to intArrayOf(0xFEE9, 0xFEEB, 0xFEEC, 0xFEEA),  // HEH
        0x0648 to intArrayOf(0xFEED, 0xFEED, 0xFEEE, 0xFEED),  // WAW
        0x0649 to intArrayOf(0xFEEF, 0xFEEF, 0xFEF0, 0xFEEF),  // ALEF MAKSURA
        0x064A to intArrayOf(0xFEF1, 0xFEF3, 0xFEF4, 0xFEF2),  // YEH
        // Extended letters (use base form fallback if not in font)
        0x066E to intArrayOf(0xFBE2, 0xFBE3, 0xFBE4, 0xFBE3),  // BEH WITH DOT BELOW
        0x066F to intArrayOf(0xFBD3, 0xFBD4, 0xFBD5, 0xFBD4),  // QAF WITH DOT ABOVE
        0x06BA to intArrayOf(0xFB9B, 0xFB9C, 0xFB9D, 0xFB9C),  // NOON WITH DOT BELOW
        0x06BE to intArrayOf(0xFEE9, 0xFEEB, 0xFEEC, 0xFEEA),  // HEH DOACHASHMEE (use HEH)
        0x06C1 to intArrayOf(0xFEE9, 0xFEEB, 0xFEEC, 0xFEEA)   // HEH GOAL (use HEH)
    )

    // Form indices in the IntArray
    private const val IDX_ISOLATED = 0
    private const val IDX_INITIAL = 1
    private const val IDX_MEDIAL = 2
    private const val IDX_FINAL = 3

    /**
     * Skip diacritics (tashkeel) and tatweel when checking for previous/next letter.
     * These marks exist between letters but don't break the joining context.
     */
    private fun isTransparent(c: Char): Boolean {
        val code = c.code
        return code in 0x064B..0x065F ||  // Arabic diacritics (harakat)
               code == 0x0670 ||          // SUPERSCRIPT ALEF
               code == 0x0640 ||          // TATWEEL (kashida)
               code in 0x06D6..0x06DC ||  // Quranic annotation marks
               code in 0x06DF..0x06E8     // More Quranic marks
    }

    /**
     * Find the next non-transparent character (skipping diacritics and tatweel).
     * Returns null if no such character exists.
     */
    private fun nextNonTransparent(chars: CharArray, fromIdx: Int): Char? {
        var i = fromIdx + 1
        while (i < chars.size) {
            if (!isTransparent(chars[i])) return chars[i]
            i++
        }
        return null
    }

    /**
     * Find the previous non-transparent character (skipping diacritics and tatweel).
     * Returns null if no such character exists.
     */
    private fun prevNonTransparent(chars: CharArray, fromIdx: Int): Char? {
        var i = fromIdx - 1
        while (i >= 0) {
            if (!isTransparent(chars[i])) return chars[i]
            i--
        }
        return null
    }

    /**
     * Shape Arabic text by converting base letters to their Presentation Forms.
     *
     * Non-Arabic text is left untouched.
     * Lam-Alef ligatures are handled specially (LAM + ALEF variant → single ligature char).
     * Diacritics (tashkeel) and tatweel are preserved as-is.
     *
     * @param text Input text in logical order (may contain Arabic, Latin, numbers, punctuation)
     * @return Shaped text with Arabic letters converted to Presentation Forms
     */
    fun shape(text: String): String {
        if (text.isEmpty()) return text

        val chars = text.toCharArray()
        val n = chars.size
        val sb = StringBuilder(n)
        var i = 0

        while (i < n) {
            val c = chars[i]
            val code = c.code

            // ── Handle Lam-Alef ligature (LAM + ALEF variant) ──────────
            // 0x0644 (LAM) followed by 0x0622/0x0623/0x0625/0x0627 → single ligature char
            if (code == 0x0644 && i + 1 < n) {
                val nextChar = nextNonTransparent(chars, i)
                if (nextChar != null) {
                    val nextCode = nextChar.code
                    val prevChar = prevNonTransparent(chars, i)
                    val prevJoins = prevChar != null && extendsToNext(prevChar)

                    val ligatureChar = when (nextCode) {
                        0x0622 -> if (prevJoins) 0xFEF6 else 0xFEF5  // LAM-ALEF MADDA
                        0x0623 -> if (prevJoins) 0xFEF8 else 0xFEF7  // LAM-ALEF HAMZA ABOVE
                        0x0625 -> if (prevJoins) 0xFEFA else 0xFEF9  // LAM-ALEF HAMZA BELOW
                        0x0627 -> if (prevJoins) 0xFEFC else 0xFEFB  // LAM-ALEF
                        else -> -1
                    }

                    if (ligatureChar != -1) {
                        sb.append(ligatureChar.toChar())
                        // Skip LAM and the ALEF variant, plus any diacritics between them
                        i++  // skip LAM
                        while (i < n && isTransparent(chars[i]) && chars[i].code != nextCode) i++  // skip diacritics
                        if (i < n && chars[i].code == nextCode) i++  // skip ALEF variant
                        continue
                    }
                }
            }

            // ── Handle transparent characters (diacritics, tatweel) ─────
            if (isTransparent(c)) {
                sb.append(c)
                i++
                continue
            }

            // ── Shape Arabic letters ───────────────────────────────────
            val forms = PRESENTATION_FORMS[code]
            if (forms == null) {
                // Non-Arabic character (Latin, digit, punctuation, etc.) — keep as-is
                sb.append(c)
                i++
                continue
            }

            // Determine joining context
            val prevChar = prevNonTransparent(chars, i)
            val nextChar = nextNonTransparent(chars, i)

            val prevJoins = prevChar != null && extendsToNext(prevChar) && acceptsFromPrev(c)
            val nextJoins = extendsToNext(c) && nextChar != null && acceptsFromPrev(nextChar)

            // Select presentation form
            val formIdx = when {
                prevJoins && nextJoins -> IDX_MEDIAL
                prevJoins && !nextJoins -> IDX_FINAL
                !prevJoins && nextJoins -> IDX_INITIAL
                else -> IDX_ISOLATED
            }

            sb.append(forms[formIdx].toChar())
            i++
        }

        return sb.toString()
    }

    /**
     * Convenience: shape text only if it contains Arabic characters.
     * Otherwise return as-is (saves time on pure Latin/numeric text).
     *
     * CRITICAL FIX (2026-07-26): This function now returns text UNCHANGED.
     *
     * Previous implementation manually converted base Arabic letters to
     * Presentation Forms (FE70-FEFF) before passing to iText. However,
     * testing revealed this produced GARBLED output ("encrypted unknown
     * language") because:
     *
     * 1. iText 8 + IDENTITY_H encoding + NotoNaskhArabic font DOES apply
     *    the font's OpenType GSUB tables for Arabic letter shaping when
     *    given BASE Arabic characters (0600-06FF).
     * 2. Pre-shaping to Presentation Forms caused DOUBLE-SHAPING: the
     *    GSUB tables tried to shape already-shaped characters, producing
     *    wrong glyphs that appeared as garbled/encrypted text.
     *
     * By returning base Arabic characters unchanged, iText's layout engine
     * handles shaping correctly via the font's GSUB tables. This produces
     * properly connected Arabic letters with correct contextual forms.
     *
     * The shape() function is preserved for reference/testing but is no
     * longer called in production code.
     */
    fun shapeIfArabic(text: String): String {
        // Return text unchanged — let iText handle Arabic shaping via GSUB
        return text
    }
}
