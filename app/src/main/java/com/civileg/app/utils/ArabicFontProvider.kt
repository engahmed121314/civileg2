package com.civileg.app.utils

import android.content.Context
import android.graphics.Typeface
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import java.io.File

/**
 * مقدم الخط العربي الموحد - Unified Arabic Font Provider
 *
 * Solves the problem of disconnected Arabic letters in PDF reports by:
 * 1. Loading a bundled NotoNaskhArabic font from assets (guaranteed to exist)
 * 2. Using IDENTITY_H encoding for proper Unicode Arabic shaping
 * 3. Providing both iText PdfFont and Android Typeface variants
 * 4. Caching fonts to avoid repeated loading
 *
 * The root cause of disconnected Arabic letters was:
 * - System font paths (/system/fonts/NotoNaskhArabic-Regular.ttf) don't exist on all devices
 * - Fallback to HELVETICA (Latin-only) causes Arabic to render as disconnected glyphs
 * - Even when system fonts exist, some lack proper OpenType GSUB/GPOS tables for shaping
 *
 * This provider guarantees correct Arabic rendering by bundling a proper Arabic font.
 */
object ArabicFontProvider {

    private const val FONT_REGULAR = "fonts/NotoNaskhArabic-Regular.ttf"
    private const val FONT_BOLD = "fonts/NotoNaskhArabic-Bold.ttf"

    // Cached fonts (per-process lifetime)
    @Volatile private var cachedRegularFont: PdfFont? = null
    @Volatile private var cachedBoldFont: PdfFont? = null
    @Volatile private var cachedTypeface: Typeface? = null
    @Volatile private var cachedBoldTypeface: Typeface? = null

    // Error state - prevent retrying after known failure
    @Volatile private var fontLoadAttempted = false
    @Volatile private var typefaceLoadAttempted = false

    /**
     * Get Arabic PdfFont for iText PDF generation.
     * Uses IDENTITY_H encoding which supports full Unicode Arabic shaping
     * including letter connections (initial, medial, final, isolated forms).
     *
     * @param context Application context for asset access
     * @param bold Whether to return bold variant
     * @return PdfFont with Arabic support, or null if loading fails
     */
    @Synchronized
    fun getArabicPdfFont(context: Context, bold: Boolean = false): PdfFont? {
        if (!bold && cachedRegularFont != null) return cachedRegularFont
        if (bold && cachedBoldFont != null) return cachedBoldFont

        val fontPath = if (bold) FONT_BOLD else FONT_REGULAR
        val targetCache = if (bold) ::cachedBoldFont else ::cachedRegularFont

        // Strategy 1: Load from bundled assets (RELIABLE - always available)
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open(fontPath)
            // Copy to cache file (iText needs a file path, not stream, for IDENTITY_H)
            val cacheFile = File(context.cacheDir, "arabic_${if (bold) "bold" else "regular"}.ttf")
            if (!cacheFile.exists()) {
                cacheFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
            }
            inputStream.close()

            val font = PdfFontFactory.createFont(cacheFile.absolutePath, PdfEncodings.IDENTITY_H)
            targetCache.set(font)
            return font
        } catch (e: Exception) {
            // Assets not available (shouldn't happen in normal app operation)
            e.printStackTrace()
        }

        // Strategy 2: System fonts (device-dependent, less reliable)
        val systemPaths = arrayOf(
            "/system/fonts/NotoNaskhArabic-Regular.ttf",
            "/system/fonts/NotoNaskhArabic-Bold.ttf",
            "/system/fonts/DroidSansArabic.ttf",
            "/system/fonts/Arbutus-Regular.ttf",
            "/system/fonts/NotoSansArabic-Regular.ttf"
        )

        for (path in systemPaths) {
            try {
                if (File(path).exists()) {
                    val font = PdfFontFactory.createFont(path, PdfEncodings.IDENTITY_H)
                    targetCache.set(font)
                    return font
                }
            } catch (e: Exception) {
                continue
            }
        }

        fontLoadAttempted = true
        return null
    }

    /**
     * Get Android Typeface for Canvas-based PDF generation (PdfLayoutHelper).
     *
     * @param context Application context for asset access
     * @param bold Whether to return bold variant
     * @return Typeface with Arabic shaping support
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
            targetCache.set(typeface)
            return typeface
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Strategy 2: System font
        try {
            val typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            // Android's sans-serif usually supports Arabic on modern devices
            targetCache.set(typeface)
            return typeface
        } catch (e: Exception) {
            // Final fallback
        }

        typefaceLoadAttempted = true
        return if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /**
     * Check if text contains Arabic characters.
     * Covers Arabic Unicode blocks: Arabic (0600-06FF),
     * Arabic Supplement (0750-077F), Arabic Extended-A (08A0-08FF),
     * Arabic Presentation Forms (FB50-FDFF, FE70-FEFF).
     */
    fun containsArabic(text: String): Boolean {
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
     * Clear cached fonts (useful for testing or theme changes).
     */
    fun clearCache() {
        cachedRegularFont = null
        cachedBoldFont = null
        cachedTypeface = null
        cachedBoldTypeface = null
        fontLoadAttempted = false
        typefaceLoadAttempted = false
    }
}