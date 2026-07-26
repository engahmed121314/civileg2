package com.civileg.app.utils

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.itextpdf.io.font.FontProgram
import com.itextpdf.io.font.FontProgramFactory
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy
import java.io.File

/**
 * مقدم الخط العربي الموحد - Unified Arabic Font Provider
 *
 * ********************************************************************
 * CRITICAL FIX (2026-07-26): PdfFont Caching Bug
 * ********************************************************************
 * PREVIOUS BUG: PdfFont objects were cached and reused across multiple
 * PdfDocuments. iText 8 binds a PdfFont to the FIRST PdfDocument that
 * uses it. After that document is closed, the cached PdfFont becomes
 * invalid and any attempt to use it in a NEW PdfDocument throws:
 *
 *   "Pdf indirect object belongs to other PDF document. Copy object to current pdf document."
 *
 * This caused PDF reports to FAIL on the 2nd+ attempt from ANY design
 * section. The first PDF might work (with garbled text due to missing
 * pdfCalligraph), but every subsequent attempt failed silently.
 *
 * FIX: Cache the FontProgram (parsed TTF data — expensive operation,
 * happens ONCE), but create a FRESH PdfFont for each PDF (cheap wrapper,
 * safe to recreate).
 *
 * ********************************************************************
 * Arabic Shaping Note
 * ********************************************************************
 * iText 8 AGPL does NOT include the pdfCalligraph module required for
 * automatic Arabic letter shaping via OpenType GSUB tables. Without
 * pdfCalligraph, Arabic letters render in ISOLATED form (disconnected,
 * appearing as "encrypted/garbled" text). The companion ArabicShaper
 * utility solves this by manually converting base Arabic letters to
 * their contextual Presentation Forms (0xFE70-0xFEFF), which the
 * bundled NotoNaskhArabic font supports directly.
 */
object ArabicFontProvider {

    private const val TAG = "ArabicFontProvider"
    private const val FONT_REGULAR = "fonts/NotoNaskhArabic-Regular.ttf"
    private const val FONT_BOLD = "fonts/NotoNaskhArabic-Bold.ttf"

    // CRITICAL: Cache FontProgram (parsed TTF data), NOT PdfFont.
    // FontProgram is the expensive part (TTF parsing) and is safe to reuse.
    // PdfFont is a cheap wrapper bound to a specific PdfDocument — must be fresh per PDF.
    @Volatile private var cachedRegularFontProgram: FontProgram? = null
    @Volatile private var cachedBoldFontProgram: FontProgram? = null

    // Typeface (Android Canvas font) — safe to cache, not tied to PdfDocument
    @Volatile private var cachedTypeface: Typeface? = null
    @Volatile private var cachedBoldTypeface: Typeface? = null

    /**
     * Get Arabic PdfFont for iText PDF generation.
     *
     * CRITICAL: This MUST return a NEW PdfFont instance for each PDF.
     * PdfFont objects become bound to the PdfDocument that first uses them
     * and cannot be reused across documents (iText 8 limitation).
     *
     * To avoid re-parsing the TTF file every call, we cache the FontProgram
     * (parsed font data) and create a fresh PdfFont wrapper from it.
     *
     * Uses IDENTITY_H encoding for proper Unicode Arabic support.
     * Font embedding is FORCE-ENABLED so the font travels with the PDF.
     *
     * This method NEVER returns null. If bundled font fails, it tries system fonts,
     * and as a last resort uses Helvetica (Arabic will not render but PDF won't crash).
     *
     * @param context Application context for asset access
     * @param bold Whether to return bold variant
     * @return PdfFont with Arabic support (never null)
     */
    @Synchronized
    fun getArabicPdfFont(context: Context, bold: Boolean = false): PdfFont {
        val fontProgram = getOrCreateFontProgram(context, bold)

        // Create a FRESH PdfFont from the cached FontProgram.
        // This is the critical fix — never cache the PdfFont itself.
        return try {
            PdfFontFactory.createFont(
                fontProgram,
                PdfEncodings.IDENTITY_H,
                EmbeddingStrategy.PREFER_EMBEDDED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create PdfFont from FontProgram: ${e.message}", e)
            // Last resort: Helvetica (no Arabic support but won't crash)
            try {
                PdfFontFactory.createFont("Helvetica")
            } catch (e2: Exception) {
                throw RuntimeException("All PDF font loading strategies failed", e2)
            }
        }
    }

    /**
     * Get-or-create the cached FontProgram for the requested weight.
     * FontProgram is the parsed TTF data — expensive to create, safe to cache.
     */
    @Synchronized
    private fun getOrCreateFontProgram(context: Context, bold: Boolean): FontProgram {
        val cached = if (bold) cachedBoldFontProgram else cachedRegularFontProgram
        if (cached != null) return cached

        val fontPath = if (bold) FONT_BOLD else FONT_REGULAR
        val targetCache = if (bold) ::cachedBoldFontProgram else ::cachedRegularFontProgram

        // Strategy 1: Load from bundled assets (RELIABLE — always available)
        try {
            val assetManager = context.assets
            val cacheFile = File(context.cacheDir, "arabic_${if (bold) "bold" else "regular"}.ttf")

            fun copyFromAssets() {
                cacheFile.parentFile?.mkdirs()
                assetManager.open(fontPath).use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }

            // Always re-copy to avoid stale/corrupted cache
            cacheFile.delete()
            copyFromAssets()

            if (cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    val program = FontProgramFactory.createFont(cacheFile.absolutePath)
                    targetCache.set(program)
                    Log.d(TAG, "Arabic FontProgram loaded from assets: ${cacheFile.absolutePath} (${cacheFile.length()} bytes)")
                    return program
                } catch (e: Exception) {
                    Log.w(TAG, "Cached font file appears corrupted, re-copying: ${e.message}")
                    cacheFile.delete()
                    copyFromAssets()
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        val program = FontProgramFactory.createFont(cacheFile.absolutePath)
                        targetCache.set(program)
                        Log.d(TAG, "Arabic FontProgram loaded on retry: ${cacheFile.absolutePath}")
                        return program
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Arabic font from assets: ${e.message}", e)
        }

        // Strategy 2: System fonts (device-dependent)
        val systemPaths = if (bold) {
            arrayOf(
                "/system/fonts/NotoNaskhArabic-Bold.ttf",
                "/system/fonts/NotoSansArabic-Bold.ttf",
                "/system/fonts/DroidSansArabic.ttf"
            )
        } else {
            arrayOf(
                "/system/fonts/NotoNaskhArabic-Regular.ttf",
                "/system/fonts/NotoSansArabic-Regular.ttf",
                "/system/fonts/DroidSansArabic.ttf"
            )
        }

        for (path in systemPaths) {
            try {
                if (File(path).exists()) {
                    val program = FontProgramFactory.createFont(path)
                    targetCache.set(program)
                    Log.d(TAG, "Arabic FontProgram loaded from system: $path")
                    return program
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load system font: $path - ${e.message}")
            }
        }

        // Strategy 3: Last resort - return a flag that triggers Helvetica fallback
        // We can't return null here, so we throw and the caller falls back to Helvetica
        Log.e(TAG, "CRITICAL: No Arabic font found! Arabic text will NOT render correctly in PDF.")
        throw RuntimeException("No Arabic font available")
    }

    /**
     * Get Android Typeface for Canvas-based PDF generation (PdfLayoutHelper).
     *
     * Typeface is safe to cache — it's NOT tied to any PdfDocument.
     *
     * @param context Application context for asset access
     * @param bold Whether to return bold variant
     * @return Typeface with Arabic shaping support (never null)
     */
    @Synchronized
    fun getArabicTypeface(context: Context, bold: Boolean = false): Typeface {
        if (!bold && cachedTypeface != null) return cachedTypeface!!
        if (bold && cachedBoldTypeface != null) return cachedBoldTypeface!!

        val fontPath = if (bold) FONT_BOLD else FONT_REGULAR
        val targetCache = if (bold) ::cachedBoldTypeface else ::cachedTypeface

        // Strategy 1: Load from bundled assets
        try {
            val typeface = Typeface.createFromAsset(context.assets, fontPath)
            if (typeface != null) {
                targetCache.set(typeface)
                Log.d(TAG, "Arabic Typeface loaded from assets")
                return typeface
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Arabic Typeface from assets: ${e.message}")
        }

        // Strategy 2: System font
        try {
            val typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            targetCache.set(typeface)
            Log.d(TAG, "Using system sans-serif as Typeface fallback")
            return typeface
        } catch (e: Exception) {
            Log.e(TAG, "System font fallback failed: ${e.message}")
        }

        return if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /**
     * Check if text contains Arabic characters.
     */
    fun containsArabic(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.any { ch ->
            val code = ch.code
            code in 0x0600..0x06FF ||      // Arabic
            code in 0x0750..0x077F ||      // Arabic Supplement
            code in 0x08A0..0x08FF ||      // Arabic Extended-A
            code in 0xFB50..0xFDFF ||      // Arabic Presentation Forms-A
            code in 0xFE70..0xFEFF ||      // Arabic Presentation Forms-B
            code in 0x0620..0x064A ||      // Arabic letters
            code in 0x064B..0x065F         // Arabic diacritics
        }
    }

    /**
     * CRITICAL FIX: Shape Arabic text by converting base letters (0x0621-0x064A)
     * to their Presentation Forms (0xFE80-0xFEFF) based on contextual position
     * (isolated / initial / medial / final).
     *
     * This is the SOLUTION to the long-standing Arabic PDF bug where letters
     * appeared DISCONNECTED or as SQUARES. iText 8 open-source AGPL does NOT
     * apply Arabic shaping automatically (it requires the paid pdfCalligraph module).
     *
     * Manual shaping via Presentation Forms works because the bundled
     * NotoNaskhArabic font contains all 140+ Presentation Forms glyphs.
     *
     * Lam-Alef ligatures are also handled (LAM + ALEF variant → single ligature).
     *
     * @param text Input text (may be Arabic, Latin, or mixed)
     * @return Text with Arabic letters converted to Presentation Forms; Latin preserved
     */
    fun shape(text: String): String {
        return ArabicShaper.shapeIfArabic(text)
    }

    /**
     * Convenience: shape text only if it contains base Arabic letters.
     * Already-shaped text (Presentation Forms) passes through unchanged.
     */
    fun shapeIfArabic(text: String): String {
        return ArabicShaper.shapeIfArabic(text)
    }

    /**
     * Clear cached FontPrograms and Typefaces (useful for testing).
     */
    fun clearCache() {
        cachedRegularFontProgram = null
        cachedBoldFontProgram = null
        cachedTypeface = null
        cachedBoldTypeface = null
    }
}
