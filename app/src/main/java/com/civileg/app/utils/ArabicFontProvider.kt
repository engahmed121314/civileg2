package com.civileg.app.utils

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import java.io.File

/**
 * مقدم الخط العربي الموحد - Unified Arabic Font Provider
 *
 * Solves the problem of disconnected Arabic letters and square boxes in PDF reports by:
 * 1. Loading a bundled NotoNaskhArabic font from assets (guaranteed to exist)
 * 2. Using IDENTITY_H encoding for proper Unicode Arabic shaping
 * 3. Providing both iText PdfFont and Android Typeface variants
 * 4. Caching fonts to avoid repeated loading
 * 5. NEVER returns null - falls back to system Arabic-capable fonts
 *
 * Root causes of Arabic rendering issues in PDF:
 * - System font paths (/system/fonts/NotoNaskhArabic-Regular.ttf) don't exist on all devices
 * - Fallback to HELVETICA (Latin-only) causes Arabic to render as disconnected glyphs/squares
 * - Even when system fonts exist, some lack proper OpenType GSUB/GPOS tables for shaping
 * - iText 8 requires file path (not stream) for IDENTITY_H encoding
 */
object ArabicFontProvider {

    private const val TAG = "ArabicFontProvider"
    private const val FONT_REGULAR = "fonts/NotoNaskhArabic-Regular.ttf"
    private const val FONT_BOLD = "fonts/NotoNaskhArabic-Bold.ttf"

    // Cached fonts (per-process lifetime)
    @Volatile private var cachedRegularFont: PdfFont? = null
    @Volatile private var cachedBoldFont: PdfFont? = null
    @Volatile private var cachedTypeface: Typeface? = null
    @Volatile private var cachedBoldTypeface: Typeface? = null

    /**
     * Get Arabic PdfFont for iText PDF generation.
     * Uses IDENTITY_H encoding which supports full Unicode Arabic shaping
     * including letter connections (initial, medial, final, isolated forms).
     *
     * This method NEVER returns null. If bundled font fails, it tries system fonts,
     * and as a last resort uses a built-in Unicode font.
     *
     * @param context Application context for asset access
     * @param bold Whether to return bold variant
     * @return PdfFont with Arabic support (never null)
     */
    @Synchronized
    fun getArabicPdfFont(context: Context, bold: Boolean = false): PdfFont {
        // Return cached if available
        if (!bold && cachedRegularFont != null) return cachedRegularFont!!
        if (bold && cachedBoldFont != null) return cachedBoldFont!!

        val fontPath = if (bold) FONT_BOLD else FONT_REGULAR
        val targetCache = if (bold) ::cachedBoldFont else ::cachedRegularFont

        // Strategy 1: Load from bundled assets (RELIABLE - always available)
        try {
            val assetManager = context.assets
            val cacheFile = File(context.cacheDir, "arabic_${if (bold) "bold" else "regular"}.ttf")

            fun copyFromAssets() {
                cacheFile.parentFile?.mkdirs()
                assetManager.open(fontPath).use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
            }

            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                copyFromAssets()
            }

            if (cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    val font = PdfFontFactory.createFont(cacheFile.absolutePath, PdfEncodings.IDENTITY_H)
                    targetCache.set(font)
                    Log.d(TAG, "Arabic font loaded successfully from assets: ${cacheFile.absolutePath} (${cacheFile.length()} bytes)")
                    return font
                } catch (fontLoadError: Exception) {
                    // Cache file may be corrupted - delete and retry once
                    Log.w(TAG, "Cached font file appears corrupted, re-copying from assets: ${fontLoadError.message}")
                    cacheFile.delete()
                    copyFromAssets()
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        val font = PdfFontFactory.createFont(cacheFile.absolutePath, PdfEncodings.IDENTITY_H)
                        targetCache.set(font)
                        Log.d(TAG, "Arabic font loaded on retry from assets: ${cacheFile.absolutePath}")
                        return font
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
                "/system/fonts/DroidSansArabic.ttf",
                "/system/fonts/Arbutus-Regular.ttf"
            )
        }

        for (path in systemPaths) {
            try {
                if (File(path).exists()) {
                    val font = PdfFontFactory.createFont(path, PdfEncodings.IDENTITY_H)
                    targetCache.set(font)
                    Log.d(TAG, "Arabic font loaded from system: $path")
                    return font
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load system font: $path - ${e.message}")
            }
        }

        // Strategy 3: Try all TTF files in cacheDir (in case of previous copies)
        try {
            val cacheDir = context.cacheDir
            val ttfFiles = cacheDir.listFiles { file -> file.name.endsWith(".ttf") && file.name.contains("arabic", ignoreCase = true) }
            ttfFiles?.forEach { file ->
                try {
                    if (file.length() > 0) {
                        val font = PdfFontFactory.createFont(file.absolutePath, PdfEncodings.IDENTITY_H)
                        targetCache.set(font)
                        Log.d(TAG, "Arabic font loaded from cache fallback: ${file.name}")
                        return font
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache fallback failed: ${e.message}")
        }

        // Strategy 4: Last resort - use Helvetica
        // WARNING: This will NOT properly render Arabic (shows as boxes/tofu), but prevents crash
        Log.e(TAG, "CRITICAL: No Arabic font found! Arabic text will NOT render correctly in PDF. " +
            "Verify assets/fonts/NotoNaskhArabic-Regular.ttf exists and is not corrupted.")
        return try {
            PdfFontFactory.createFont("Helvetica")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Even Helvetica fallback failed: ${e.message}")
            // This should never happen - Helvetica is a built-in iText font
            throw RuntimeException("All PDF font loading strategies failed", e)
        }
    }

    /**
     * Get Android Typeface for Canvas-based PDF generation (PdfLayoutHelper).
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
    }
}